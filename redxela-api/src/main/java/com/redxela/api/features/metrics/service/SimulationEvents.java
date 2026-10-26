package com.redxela.api.features.metrics.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.redxela.api.shared.exception.ConflictException;

// Emisión limitada a 1 Hz y aislada del motor; un cliente lento no acumula snapshots en memoria
@Service
public class SimulationEvents implements AutoCloseable {
    private final OperationalSnapshotService snapshots;
    private final Map<SseEmitter,AtomicBoolean> clients=new ConcurrentHashMap<>();
    private final ScheduledExecutorService ticker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("redxela-events").factory());
    private final ThreadPoolExecutor senders=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),Thread.ofPlatform().name("redxela-sse-",0).factory());
    public SimulationEvents(OperationalSnapshotService snapshots){this.snapshots=snapshots;ticker.scheduleWithFixedDelay(this::broadcast,100,1000,TimeUnit.MILLISECONDS);}
    public synchronized SseEmitter subscribe(){
        if(clients.size()>=32)throw new ConflictException("Demasiados observadores; reintente la conexión");
        var emitter=new SseEmitter(60_000L);clients.put(emitter,new AtomicBoolean());
        emitter.onCompletion(()->clients.remove(emitter));emitter.onTimeout(()->{clients.remove(emitter);finish(emitter);});emitter.onError(ex->clients.remove(emitter));
        return emitter;
    }
    private void broadcast(){
        if(clients.isEmpty())return;
        try {
            var snapshot=snapshots.snapshot();
            clients.forEach((emitter,busy)->{
                if(!busy.compareAndSet(false,true))return;
                try{senders.execute(()->{
                    try{emitter.send(SseEmitter.event().name("snapshot").id(snapshot.id().toString()).reconnectTime(1000).data(snapshot));}
                    // Tomcat/Spring completan el contexto tras un error de escritura; no se vuelve a despachar ese contexto
                    catch(IOException|IllegalStateException ex){clients.remove(emitter);}
                    finally{busy.set(false);}
                });}catch(RejectedExecutionException ex){clients.remove(emitter);busy.set(false);finish(emitter);}
            });
        }catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).error("No se pudo publicar snapshot",ex);}
    }
    public int observerCount(){return clients.size();}
    private void finish(SseEmitter emitter) {
        try { emitter.complete(); }
        catch (IllegalStateException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).debug("Contexto SSE ya finalizado por el contenedor"); }
    }
    // Cerrar antes del apagado graceful de Tomcat evita esperar el timeout de conexiones abiertas
    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    @PreDestroy @Override public void close(){ticker.shutdownNow();clients.keySet().forEach(this::finish);clients.clear();senders.shutdownNow();}
}
