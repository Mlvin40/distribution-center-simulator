package com.redxela.api.features.resource.service;

import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.resource.model.ResourceOverview.*;
import com.redxela.api.shared.exception.ConflictException;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.*;
import org.springframework.stereotype.Service;

// Una única instancia administra también el escáner de Recepción y Despacho
@Service
public class ResourceManager {
    private final long observationOrigin = System.nanoTime();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition changed = lock.newCondition();
    private final EnumMap<ResourceType, Pool> pools = new EnumMap<>(ResourceType.class);
    private final LinkedHashMap<UUID, Request> waiting = new LinkedHashMap<>();
    private final Map<UUID, ResourceLease> active = new HashMap<>();
    private final LinkedHashMap<UUID, Request> demonstrationWaiting = new LinkedHashMap<>();
    private final Map<Thread, UUID> threads = new HashMap<>();

    public ResourceManager() {
        for (var type : ResourceType.values()) pools.put(type, new Pool(type));
    }

    // No se retienen recursos parciales: esperar libera el lock y es interrumpible/acotado
    public ResourceLease acquire(ResourceOwner owner, Set<ResourceType> requested, Duration timeout)
            throws InterruptedException, TimeoutException {
        if (owner == null || requested == null || requested.isEmpty() || timeout == null || timeout.isNegative())
            throw new IllegalArgumentException("Operación, recursos y timeout no negativo son obligatorios");
        var resources = Set.copyOf(requested);
        long remaining;
        try { remaining = timeout.toNanos(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Timeout fuera de rango"); }
        lock.lockInterruptibly();
        boolean registered = false;
        boolean granted = false;
        var thread = Thread.currentThread();
        var request = new Request(owner, resources);
        try {
            // Prohibir adquisiciones anidadas impide introducir hold-and-wait por otra llamada
            if (threads.containsKey(thread) || active.containsKey(owner.operationId()) || waiting.containsKey(owner.operationId()))
                throw new ConflictException("El hilo o la operación ya posee o espera recursos");
            waiting.put(owner.operationId(), request);
            threads.put(thread, owner.operationId());
            registered = true;
            while (!canAcquire(request)) {
                if (remaining <= 0) throw new TimeoutException("Tiempo agotado esperando recursos");
                remaining = changed.awaitNanos(remaining);
            }
            if (Thread.interrupted()) throw new InterruptedException("Adquisición interrumpida");
            var assignments = new ArrayList<ResourceAssignment>();
            try {
                // El mismo lock hace atómica la comprobación, los permisos y sus titulares
                for (var type : ResourceType.values()) {
                    if (resources.contains(type)) assignments.add(pools.get(type).take(owner));
                }
                var lease = new ResourceLease(owner, thread, assignments);
                active.put(owner.operationId(), lease);
                waiting.remove(owner.operationId());
                granted = true;
                // Otros solicitantes pueden avanzar al retirarse esta precedencia de la cola
                changed.signalAll();
                return lease;
            } finally {
                if (!granted) for (var assignment : assignments) pools.get(assignment.type()).release(assignment, owner);
            }
        } finally {
            if (registered && !granted) {
                waiting.remove(owner.operationId());
                threads.remove(thread);
                changed.signalAll();
            }
            lock.unlock();
        }
    }

    private boolean canAcquire(Request request) {
        for (var type : request.resources()) if (pools.get(type).permits.availablePermits() == 0) return false;
        // FIFO entre solicitudes que comparten recursos, permitiendo avanzar conjuntos disjuntos
        for (var earlier : waiting.values()) {
            if (earlier == request) return true;
            if (!Collections.disjoint(earlier.resources(), request.resources())) return false;
        }
        return false;
    }

    public ResourceOverview snapshot() {
        lock.lock();
        try {
            var requests = java.util.stream.Stream.concat(waiting.values().stream(), demonstrationWaiting.values().stream()).map(request -> {
                var unavailable = EnumSet.noneOf(ResourceType.class);
                for (var type : request.resources()) if (pools.get(type).permits.availablePermits() == 0 || (demonstrationWaiting.containsKey(request.owner().operationId()) && pools.get(type).owners.get(type.name()+"-01") != null)) unavailable.add(type);
                return new WaitingOperation(request.owner(), request.resources(), unavailable);
            }).toList();
            var snapshots = new ArrayList<ResourceSnapshot>();
            for (var type : ResourceType.values()) {
                var pool = pools.get(type);
                var units = pool.owners.entrySet().stream().map(entry -> new UnitSnapshot(entry.getKey(),
                        entry.getValue() == null ? UnitStatus.AVAILABLE : UnitStatus.IN_USE, entry.getValue())).toList();
                int available = pool.permits.availablePermits();
                snapshots.add(new ResourceSnapshot(type, type.capacity(), available, type.capacity() - available,
                        units, requests.stream().filter(request -> request.requestedResources().contains(type)).toList()));
            }
            return new ResourceOverview(Instant.now(), snapshots);
        } finally { lock.unlock(); }
    }

    // Vía exclusiva del experimento: solicita unidades identificadas y permite hold-and-wait deliberado
    // Cada concesión se libera por su hilo propietario; el uso normal conserva la adquisición de conjuntos
    public DemonstrationLease acquireDemonstration(ResourceOwner owner, ResourceType type) throws InterruptedException {
        String unit = type.name() + "-01";
        lock.lockInterruptibly();
        try {
            demonstrationWaiting.put(owner.operationId(),new Request(owner,Set.of(type)));
            while(pools.get(type).owners.get(unit)!=null) changed.await();
            if(Thread.interrupted()) throw new InterruptedException();
            var pool=pools.get(type);
            pool.accumulate();
            if(!pool.permits.tryAcquire()) throw new IllegalStateException("Permisos inconsistentes");
            pool.owners.put(unit,owner);
            return new DemonstrationLease(owner,new ResourceAssignment(type,unit),Thread.currentThread());
        } finally {demonstrationWaiting.remove(owner.operationId());lock.unlock();}
    }
    public final class DemonstrationLease implements AutoCloseable {
        private final ResourceOwner owner; private final ResourceAssignment assignment; private final Thread thread; private boolean closed;
        private DemonstrationLease(ResourceOwner owner,ResourceAssignment assignment,Thread thread){this.owner=owner;this.assignment=assignment;this.thread=thread;}
        public ResourceAssignment assignment(){return assignment;}
        @Override public void close(){
            if(Thread.currentThread()!=thread)throw new IllegalStateException("Solo el hilo propietario libera la unidad demostrativa");
            lock.lock();try{if(!closed){pools.get(assignment.type()).release(assignment,owner);closed=true;changed.signalAll();}}finally{lock.unlock();}
        }
    }

    public Map<ResourceType,Double> utilization() {
        lock.lock();try {
            var result = new EnumMap<ResourceType,Double>(ResourceType.class);
            for(var entry:pools.entrySet()) {
                var pool=entry.getValue();pool.accumulate();
                result.put(entry.getKey(),Math.min(1,pool.busyNanos / Math.max(1,(double)(System.nanoTime()-observationOrigin)*entry.getKey().capacity())));
            }
            return Map.copyOf(result);
        }finally{lock.unlock();}
    }
    private record Request(ResourceOwner owner, Set<ResourceType> resources) {}

    private static class Pool {
        private final ResourceType type;
        private final Semaphore permits;
        private long lastChanged = System.nanoTime();
        private double busyNanos;
        private final LinkedHashMap<String, ResourceOwner> owners = new LinkedHashMap<>();
        Pool(ResourceType type) {
            this.type = type;
            permits = new Semaphore(type.capacity());
            for (int i = 1; i <= type.capacity(); i++) owners.put(type.name() + "-%02d".formatted(i), null);
        }
        void accumulate() {
            long now = System.nanoTime(); busyNanos += (double)(now - lastChanged) * (type.capacity() - permits.availablePermits()); lastChanged = now;
        }
        ResourceAssignment take(ResourceOwner owner) {
            accumulate();
            var unit = owners.entrySet().stream().filter(entry -> entry.getValue() == null).findFirst().orElseThrow();
            if (!permits.tryAcquire()) throw new IllegalStateException("Permisos y titulares inconsistentes");
            unit.setValue(owner);
            return new ResourceAssignment(type, unit.getKey());
        }
        void release(ResourceAssignment assignment, ResourceOwner owner) {
            if (!owner.equals(owners.get(assignment.unitId()))) throw new IllegalStateException("Titular de recurso inválido");
            accumulate();
            owners.put(assignment.unitId(), null);
            permits.release();
        }
    }

    // La concesión es la única vía de liberación; usar try-with-resources en el trabajador
    public final class ResourceLease implements AutoCloseable {
        private final ResourceOwner owner;
        private final Thread owningThread;
        private final List<ResourceAssignment> assignments;
        private boolean closed;
        private ResourceLease(ResourceOwner owner, Thread owningThread, List<ResourceAssignment> assignments) {
            this.owner = owner; this.owningThread = owningThread; this.assignments = List.copyOf(assignments);
        }
        public List<ResourceAssignment> assignments() { return assignments; }
        @Override public void close() {
            if (Thread.currentThread() != owningThread)
                throw new IllegalStateException("Solo el hilo propietario puede liberar esta concesión");
            lock.lock();
            try {
                if (closed) return;
                for (var assignment : assignments) pools.get(assignment.type()).release(assignment, owner);
                active.remove(owner.operationId());
                threads.remove(owningThread);
                closed = true;
                changed.signalAll();
            } finally { lock.unlock(); }
        }
    }
}
