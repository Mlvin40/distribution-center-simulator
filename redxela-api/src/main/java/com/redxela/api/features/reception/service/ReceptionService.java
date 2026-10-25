package com.redxela.api.features.reception.service;

import com.redxela.api.features.reception.model.Arrival;
import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.shared.exception.*;
import jakarta.annotation.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static com.redxela.api.features.reception.model.Arrival.Status.*;

// Dos áreas independientes se comunican únicamente con mensajes; las colas conservan el trabajo al detenerse
@Service
public class ReceptionService implements AutoCloseable {
    private final BlockingQueue<UUID> incoming = new LinkedBlockingQueue<>(256);
    private final BlockingQueue<UUID> messages = new LinkedBlockingQueue<>(256);
    private final Map<UUID,Arrival> arrivals = new LinkedHashMap<>();
    private final InventoryService inventory;
    private final ProductCatalog catalog;
    private final ResourceManager resources;
    private final SimulationProperties settings;
    private ExecutorService workers;
    private volatile boolean running;
    public ReceptionService(InventoryService inventory, ProductCatalog catalog, ResourceManager resources, SimulationProperties settings) {
        this.inventory=inventory; this.catalog=catalog; this.resources=resources; this.settings=settings;
    }
    @PostConstruct public synchronized void start() {
        if(running) return;
        running=true;
        workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().name("reception-inventory-",0).factory());
        workers.submit(this::produce); workers.submit(this::consume);
    }
    public synchronized Arrival register(UUID id,String product,long quantity) {
        if(id==null || quantity<=0) throw new IllegalArgumentException("Identificador y cantidad positiva obligatorios");
        catalog.get(product);
        if(quantity>settings.storageCapacity()) throw new ConflictException("Un lote no puede superar la capacidad total del almacén");
        var previous=arrivals.get(id);
        if(previous!=null) {
            if(!previous.productId().equals(product)||previous.quantity()!=quantity) throw new ConflictException("Identificador de llegada usado con otros datos");
            return previous;
        }
        if(!running) throw new ConflictException("Recepción detenida; conserve y reintente la llegada");
        // Publicación y aceptación se coordinan; cola llena rechaza explícitamente sin admitir ni perder el mensaje
        if(incoming.remainingCapacity()==0 || arrivals.values().stream().filter(a->a.status()!=COMPLETED).count()>=512) throw new ConflictException("Recepción saturada; reintente con el mismo identificador");
        var arrival=new Arrival(id,product,quantity,Instant.now(),RECEIVED,0,"Pendiente de escaneo",null);
        arrivals.put(id,arrival); incoming.add(id); return arrival;
    }
    public synchronized Arrival get(UUID id) {
        var arrival=arrivals.get(id); if(arrival==null) throw new NotFoundException("Llegada no encontrada"); return arrival;
    }
    private synchronized void update(UUID id,Arrival.Status status,String message,com.redxela.api.features.inventory.model.InventoryReceipt receipt) {
        var old=get(id); arrivals.put(id,old.update(status,old.attempts()+(status==PROCESSING?1:0),message,receipt));
    }
    public synchronized Overview snapshot() {
        return new Overview(running,incoming.size()+(int)deferred.stream().filter(d->d.queue()==incoming).count(),messages.size()+(int)deferred.stream().filter(d->d.queue()==messages).count(),arrivals.values().stream()
            .sorted(Comparator.comparing(Arrival::registeredAt).reversed()).limit(100).toList(),arrivals.size(),
            arrivals.values().stream().filter(a->a.status()==COMPLETED).count());
    }
    public record Overview(boolean running,int pendingReception,int pendingInventory,List<Arrival> arrivals,long registered,long completed) {}
    private void delay(double seconds) throws InterruptedException { Thread.sleep(Math.max(1,(long)(1000*seconds/settings.timeScale()))); }
    private void produce() {
        UUID current=null;
        try {
            while(running) {
                retryDeferred();
                current=incoming.poll(100,TimeUnit.MILLISECONDS); if(current==null) continue;
                if(get(current).status()==COMPLETED){current=null;continue;}
                update(current,SCANNING,"Espera del escáner compartido",null);
                try(var lease=resources.acquire(new ResourceOwner(current,null,OperationArea.RECEPTION,"Escaneo de llegada"),Set.of(ResourceType.SCANNER),Duration.ofMillis(200))) {
                    delay(2);
                } catch(TimeoutException ex) { preserve(incoming,current); current=null; continue; }
                // offer con timeout permite detener aun cuando Inventario va más lento
                boolean sent=false;
                while(running && !sent) sent=messages.offer(current,100,TimeUnit.MILLISECONDS);
                if(!sent) break;
                UUID sentId=current;current=null;
                synchronized(this) { if(get(sentId).status()==SCANNING) update(sentId,QUEUED,"Mensaje enviado a Inventario",null); }
            }
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
        finally { if(current!=null) preserve(incoming,current); }
    }
    private void consume() {
        UUID current=null;
        try {
            while(running) {
                retryDeferred();
                current=messages.poll(100,TimeUnit.MILLISECONDS); if(current==null) continue; var arrival=get(current);
                if(arrival.status()==COMPLETED){current=null;continue;}
                update(current,PROCESSING,"Inventario procesa el mensaje",null);
                try {
                    delay(5);
                    var receipt=inventory.receive(current,arrival.productId(),arrival.quantity());
                    update(current,COMPLETED,"Existencias y ubicaciones registradas",receipt);
                    current=null;
                } catch(ConflictException ex) {
                    update(current,WAITING_CAPACITY,"Sin espacio físico; mensaje conservado para reintento",null);
                    UUID retry=current; current=null; preserve(messages,retry); delay(6);
                } catch(RuntimeException ex) {
                    LoggerFactory.getLogger(getClass()).error("Error procesando llegada {}",current,ex);
                    update(current,RETRYING,"Error de procesamiento; reintento pendiente",null);
                    UUID retry=current; current=null; preserve(messages,retry); delay(30);
                }
            }
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
        finally { if(current!=null) preserve(messages,current); }
    }
    // Hay a lo sumo 256 mensajes aceptados en cada etapa; si el productor llenó el hueco, se conserva fuera de cola
    private synchronized void preserve(BlockingQueue<UUID> queue,UUID id) {
        if(!queue.offer(id)) deferred.add(new Deferred(queue,id));
    }
    private record Deferred(BlockingQueue<UUID> queue,UUID id) {}
    private final List<Deferred> deferred=new ArrayList<>();
    public synchronized void retryDeferred() { deferred.removeIf(d->d.queue().offer(d.id())); }
    @PreDestroy @Override public void close() {
        ExecutorService pool;
        synchronized(this) { running=false; pool=workers; }
        if(pool!=null) {
            pool.shutdownNow();
            try { if(!pool.awaitTermination(3,TimeUnit.SECONDS)) throw new IllegalStateException("Áreas no finalizaron"); }
            catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
    }
}
