package com.redxela.api.features.simulation.service;

import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.scheduling.service.OrderScheduler;
import com.redxela.api.features.scheduling.model.SchedulingPolicy;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.model.ProcessingSnapshot;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static com.redxela.api.features.order.model.OrderStatus.*;

// Cuatro trabajadores seleccionan y ejecutan etapas; nunca se espera un recurso bajo el monitor de pedidos
@Service
public class ProcessingEngine implements AutoCloseable {
    private final OrderService orders;
    private final InventoryService inventory;
    private final OrderScheduler scheduler;
    private final ResourceManager resources;
    private final SimulationClock clock;
    private final SimulationProperties settings;
    private final Map<UUID, Work> active = new HashMap<>();
    private final ArrayDeque<Event> events=new ArrayDeque<>();
    private volatile boolean running;
    private ExecutorService pool;
    public ProcessingEngine(OrderService orders,InventoryService inventory,OrderScheduler scheduler,ResourceManager resources,SimulationClock clock,SimulationProperties settings) {
        this.orders=orders;this.inventory=inventory;this.scheduler=scheduler;this.resources=resources;this.clock=clock;this.settings=settings;
    }
    private static class Work {
        final UUID id; final double total; double done; double segmentStart; double segmentDuration; OrderStatus stage; boolean waiting;
        Work(Order order){id=order.id();total=order.baseWorkSimulatedSeconds();}
        double worked(double now){return Math.min(total,done+(waiting?0:Math.max(0,Math.min(segmentDuration,now-segmentStart))));}
    }
    public synchronized Status start() {
        if(!running && (pool==null || pool.isTerminated())) {
            running=true;pool=Executors.newFixedThreadPool(4,Thread.ofPlatform().name("redxela-processing-",0).factory());
            for(int i=0;i<4;i++) pool.submit(this::worker);
        }
        return status();
    }
    public synchronized Status stop() { running=false;if(pool!=null)pool.shutdownNow();return status(); }
    public synchronized Status status() { return new Status(running?"RUNNING":pool!=null&&!pool.isTerminated()?"STOPPING":"STOPPED",4,settings.timeScale()); }
    public record Status(String state,int workerCount,double timeScale) {}
    public record Event(UUID orderId,String type,OrderStatus stage,double simulatedSeconds,Instant occurredAt,String detail) {}
    private void event(UUID id,String type,OrderStatus stage,String detail) {
        orders.atomically(()->{events.addLast(new Event(id,type,stage,clock.elapsedSeconds(),Instant.now(),detail));while(events.size()>300) events.removeFirst();return null;});
    }
    public List<Event> events(){return orders.atomically(()->List.copyOf(events));}
    public List<ProcessingSnapshot> snapshot() {
        return orders.atomically(()-> {double now=clock.elapsedSeconds();return active.values().stream().filter(w->!Set.of(COMPLETED,CANCELLED,FAILED).contains(orders.get(w.id).status())).map(w->{double worked=w.worked(now);return new ProcessingSnapshot(w.id,w.stage,worked,Math.min(99.9,100*worked/w.total),Math.max(0,w.total-worked),w.waiting);}).sorted(Comparator.comparing(ProcessingSnapshot::orderId)).toList();});
    }
    private void worker() {
        while(running && !Thread.currentThread().isInterrupted()) {
            try {
                var next=scheduler.claimForProcessing();
                if(next.isEmpty()){Thread.sleep(20);continue;}
                try(var lease=next.get()) {process(lease);}
            } catch(InterruptedException ex) {Thread.currentThread().interrupt();break;}
            catch(RuntimeException ex) {LoggerFactory.getLogger(getClass()).error("Fallo del trabajador; selección liberada",ex);}
        }
    }
    private void process(OrderScheduler.DispatchLease lease) throws InterruptedException {
        UUID id=lease.orderId();var order=orders.get(id);var work=new Work(order);
        boolean quality=order.requiredResources().contains(ResourceType.QUALITY_STATION);
        double duration=work.total/(quality?5:4);
        orders.atomically(()->{active.put(id,work);stage(work,PICKING);return null;});
        try {
            event(id,"ASSIGNED",PICKING,lease.assignments().toString());work(work,duration);
            lease.releaseResources();event(id,"RELEASED",PICKING,"Recogida terminada");
            if(quality) executeStage(work,QUALITY_CHECK,Set.of(ResourceType.QUALITY_STATION),duration,lease.policy());
            var packing=order.requiredResources().contains(ResourceType.PACKING_STATION)?Set.of(ResourceType.PACKING_STATION):Set.of(ResourceType.WAREHOUSE_WORKER);
            executeStage(work,PACKING,packing,duration,lease.policy());
            executeStage(work,SCANNING,Set.of(ResourceType.SCANNER),duration,lease.policy());
            executeStage(work,LOADING,Set.of(ResourceType.LOADING_AREA),duration,lease.policy());
            // Consumo y finalización son indivisibles: una interrupción no deja stock consumido con pedido sin completar
            orders.atomically(()->{inventory.consume(id);orders.transition(id,LOADING,COMPLETED);return null;});
            event(id,"COMPLETED",COMPLETED,"Despacho confirmado");
        } catch(InterruptedException ex) {
            inventory.abort(id,CANCELLED);event(id,"CANCELLED",orders.get(id).status(),"Detención cooperativa; reserva devuelta");throw ex;
        } catch(CancellationException ex) {
            inventory.abort(id,CANCELLED);event(id,"CANCELLED",CANCELLED,"Interrupción antes del despacho; reserva devuelta");
            Thread.currentThread().interrupt();throw new InterruptedException("Despacho cancelado");
        } catch(RuntimeException ex) {
            if(orders.get(id).status()!=COMPLETED) inventory.abort(id,FAILED);
            event(id,"FAILED",orders.get(id).status(),"Fallo de ejecución; concesiones liberadas");throw ex;
        } finally {orders.atomically(()->{active.remove(id);return null;});}
    }
    private void stage(Work work,OrderStatus next) {
        orders.atomically(()->{orders.transition(work.id,orders.get(work.id).status(),next);work.stage=next;work.waiting=true;work.segmentDuration=0;return null;});
        event(work.id,"STAGE",next,"Etapa operativa");
    }
    private void executeStage(Work work,OrderStatus stage,Set<ResourceType> requested,double duration,SchedulingPolicy policy) throws InterruptedException {
        stage(work,stage);event(work.id,"WAITING",stage,"Espera de "+requested);
        while(running) {
            try(var lease=resources.acquire(new ResourceOwner(UUID.randomUUID(),work.id,stage==SCANNING?OperationArea.DISPATCH:OperationArea.ORDER_PROCESSING,stage.name()),requested,Duration.ofMillis(200))) {
                double acquired=clock.elapsedSeconds();event(work.id,"ASSIGNED",stage,lease.assignments().toString());
                try {work(work,duration);} finally {scheduler.recordUsage(policy,lease.assignments(),clock.elapsedSeconds()-acquired);event(work.id,"RELEASED",stage,"Recursos de etapa liberados");}
                return;
            } catch(TimeoutException ex) { /* Reintento acotado e interrumpible sin retención parcial */ }
        }
        throw new InterruptedException("Motor detenido");
    }
    private void work(Work work,double duration) throws InterruptedException {
        orders.atomically(()->{work.waiting=false;work.segmentStart=clock.elapsedSeconds();work.segmentDuration=duration;return null;});
        while(clock.elapsedSeconds()-work.segmentStart<duration) {
            if(!running || Thread.currentThread().isInterrupted()) throw new InterruptedException("Trabajo cancelado");
            double remaining=duration-(clock.elapsedSeconds()-work.segmentStart);
            Thread.sleep(Math.max(1,Math.min(25,(long)(1000*remaining/settings.timeScale()))));
        }
        orders.atomically(()->{work.done+=duration;work.segmentDuration=0;work.waiting=true;return null;});
    }
    @PreDestroy @Override public void close() {
        stop();ExecutorService executor; synchronized(this){executor=pool;}
        if(executor!=null) try{if(!executor.awaitTermination(4,TimeUnit.SECONDS))throw new IllegalStateException("Trabajadores no finalizaron");}catch(InterruptedException ex){Thread.currentThread().interrupt();}
    }
}
