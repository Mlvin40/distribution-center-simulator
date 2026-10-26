package com.redxela.api.features.deadlock.service;

import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.shared.exception.ConflictException;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Experimento aislado del algoritmo normal, pero disputa unidades reales de los mismos pools
@Service
public class DeadlockService implements AutoCloseable {
    private final ResourceManager resources;
    private final ExecutorService workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().name("redxela-deadlock-",0).factory());
    private Session session;
    private boolean closed;
    private static class Session {
        final UUID id=UUID.randomUUID(); final ResourceOwner a=new ResourceOwner(UUID.randomUUID(),null,OperationArea.DISPATCH,"Operación A del conflicto");
        final ResourceOwner b=new ResourceOwner(UUID.randomUUID(),null,OperationArea.RECEPTION,"Operación B del conflicto");
        final CountDownLatch firstAcquired=new CountDownLatch(2),winnerDone=new CountDownLatch(1);
        final BlockingQueue<ResourceOwner> postponed=new LinkedBlockingQueue<>();
        final Map<UUID,String> states=new HashMap<>(); final Map<UUID,Thread> threads=new HashMap<>();
        final List<String> history=new ArrayList<>(); UUID winner; String error;
        Session(){states.put(a.operationId(),"PREPARING");states.put(b.operationId(),"PREPARING");}
    }
    public DeadlockService(ResourceManager resources){this.resources=resources;}
    public synchronized Overview start(){
        if(closed)throw new ConflictException("Experimento cerrado");
        if(session!=null&&!finished(session))throw new ConflictException("El conflicto anterior sigue activo");
        session=new Session();var current=session;
        workers.submit(()->run(current,current.a,ResourceType.SCANNER,ResourceType.FORKLIFT));
        workers.submit(()->run(current,current.b,ResourceType.FORKLIFT,ResourceType.SCANNER));
        return snapshot();
    }
    private boolean finished(Session s){return s.threads.isEmpty() && s.states.values().stream().allMatch(state->state.equals("COMPLETED")||state.equals("FAILED"));}
    private synchronized void state(Session s,UUID id,String state){s.states.put(id,state);s.history.add(id+" → "+state);}
    private void run(Session s,ResourceOwner owner,ResourceType first,ResourceType second){
        UUID id=owner.operationId();boolean deferred=false;
        synchronized(this){s.threads.put(id,Thread.currentThread());}
        try {
            try(var one=resources.acquireDemonstration(owner,first)){
                state(s,id,"HOLDING_FIRST");s.firstAcquired.countDown();s.firstAcquired.await();state(s,id,"WAITING_SECOND");
                try(var two=resources.acquireDemonstration(owner,second)){
                    state(s,id,"RUNNING");Thread.sleep(120);state(s,id,"COMPLETED");
                }
            } catch(InterruptedException ex){
                synchronized(this){deferred=!closed&&s.winner!=null&&!s.winner.equals(id);}
                if(!deferred){Thread.currentThread().interrupt();state(s,id,"FAILED");return;}
                // Solo el perdedor se interrumpe; salir de try cierra SU primera concesión en SU hilo
                Thread.interrupted();s.postponed.add(owner);state(s,id,"WAITING");
            }
            if(deferred){
                s.winnerDone.await();
                var queued=s.postponed.take();
                if(!queued.operationId().equals(id))throw new IllegalStateException("Operación pendiente incorrecta");
                try(var lease=resources.acquire(owner,Set.of(first,second),Duration.ofSeconds(10))){
                    state(s,id,"RETRYING");Thread.sleep(100);state(s,id,"COMPLETED");
                }
            }
        }catch(InterruptedException ex){Thread.currentThread().interrupt();state(s,id,"FAILED");}
        catch(TimeoutException|RuntimeException ex){LoggerFactory.getLogger(getClass()).error("Fallo en experimento",ex);s.error="El experimento falló; sus recursos fueron liberados";state(s,id,"FAILED");}
        finally{ synchronized(this){s.threads.remove(id);if(id.equals(s.winner))s.winnerDone.countDown();} }
    }
    public synchronized Overview resolve(UUID winner){
        var view=snapshot();
        if(!view.detected())throw new ConflictException("No existe una espera circular detectada");
        if(!session.a.operationId().equals(winner)&&!session.b.operationId().equals(winner))throw new IllegalArgumentException("Seleccione una operación del conflicto");
        session.winner=winner;
        var loser=winner.equals(session.a.operationId())?session.b.operationId():session.a.operationId();
        session.threads.get(loser).interrupt();
        return snapshot();
    }
    public synchronized Overview snapshot(){
        if(session==null)return new Overview(null,"IDLE",false,null,List.of(),List.of(),List.of(),null);
        var s=session;var resourceState=resources.snapshot();
        var a=view(s,s.a,resourceState);var b=view(s,s.b,resourceState);
        // Dos aristas reales: cada solicitud pendiente pide exactamente la unidad retenida por la otra
        boolean detected=s.winner==null&&a.requested()!=null&&b.requested()!=null&&a.held().contains(b.requested())&&b.held().contains(a.requested())&&a.waiting()&&b.waiting();
        String state=detected?"DEADLOCK":finished(s)?"COMPLETED":s.winner!=null?"RESOLVING":"PREPARING";
        return new Overview(s.id,state,detected,s.winner,List.of(a,b),List.copyOf(s.history),s.postponed.stream().map(ResourceOwner::operationId).toList(),s.error);
    }
    private Operation view(Session s,ResourceOwner owner,ResourceOverview overview){
        var held=new ArrayList<String>();String requested=null;boolean waiting=false;
        for(var resource:overview.resources()){
            for(var unit:resource.units())if(owner.equals(unit.owner()))held.add(unit.id());
            for(var request:resource.waiting())if(owner.equals(request.owner())){requested=resource.type().name()+"-01";waiting=true;}
        }
        return new Operation(owner.operationId(),owner.description(),s.states.get(owner.operationId()),List.copyOf(held),requested,waiting);
    }
    public record Operation(UUID id,String name,String state,List<String> held,String requested,boolean waiting){}
    public record Overview(UUID id,String state,boolean detected,UUID winner,List<Operation> operations,List<String> history,List<UUID> waitingOperations,String error){}
    @PreDestroy @Override public void close(){
        synchronized(this){closed=true;if(session!=null){session.winnerDone.countDown();for(var thread:session.threads.values())thread.interrupt();}}
        workers.shutdownNow();try{workers.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
    }
}
