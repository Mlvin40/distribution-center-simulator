package com.redxela.api.features.metrics.service;

import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.inventory.model.InventoryOverview;
import com.redxela.api.features.storage.model.StorageOverview;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.resource.model.ResourceOverview;
import com.redxela.api.features.scheduling.service.OrderScheduler;
import com.redxela.api.features.scheduling.model.*;
import com.redxela.api.features.simulation.service.*;
import com.redxela.api.features.simulation.model.ProcessingSnapshot;
import com.redxela.api.features.deadlock.service.DeadlockService;
import com.redxela.api.features.metrics.model.OperationalMetrics;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

// El monitor común fija un corte coherente entre estados, cantidades, reservas y ubicaciones
@Service
public class OperationalSnapshotService {
    private final OrderService orders; private final InventoryService inventory; private final ResourceManager resources;
    private final OrderScheduler scheduler; private final ProcessingEngine engine; private final SimulationClock clock; private final DeadlockService deadlock;
    public OperationalSnapshotService(OrderService orders,InventoryService inventory,ResourceManager resources,OrderScheduler scheduler,ProcessingEngine engine,SimulationClock clock,DeadlockService deadlock){
        this.orders=orders;this.inventory=inventory;this.resources=resources;this.scheduler=scheduler;this.engine=engine;this.clock=clock;this.deadlock=deadlock;
    }
    public Snapshot snapshot(){
        return orders.atomically(()-> {
            var all=orders.list();var completed=all.stream().filter(o->o.status()==OrderStatus.COMPLETED).toList();
            double wait=0,process=0;long missed=0;
            double now=clock.elapsedSeconds();
            for(var order:all){
                double start=order.history().stream().filter(e->e.status()==OrderStatus.PICKING).findFirst().map(OrderTransition::simulatedSeconds).orElse(now);
                var candidate=SchedulingCandidate.from(order);
                if(candidate.targetMissed(start)&&order.status()!=OrderStatus.CANCELLED&&order.status()!=OrderStatus.FAILED)missed++;
                if(order.status()==OrderStatus.COMPLETED){wait+=candidate.waitSeconds(start);process+=order.history().getLast().simulatedSeconds()-start;}
            }
            var active=engine.snapshot();
            long waiting=all.stream().filter(o->Set.of(OrderStatus.WAITING_INVENTORY,OrderStatus.READY,OrderStatus.WAITING_RESOURCES).contains(o.status())).count();
            var stock=inventory.snapshot();var storage=inventory.storageSnapshot();
            var metrics=new OperationalMetrics(all.size(),waiting,active.size(),completed.size(),
                all.stream().filter(o->o.status()==OrderStatus.FAILED).count(),all.stream().filter(o->o.status()==OrderStatus.CANCELLED).count(),
                completed.isEmpty()?null:wait/completed.size(),completed.isEmpty()?null:process/completed.size(),missed,resources.utilization(),storage.occupied(),storage.capacity());
            return new Snapshot(UUID.randomUUID(),Instant.now(),now,engine.status(),metrics,active,orders.recent(100),stock,storage,scheduler.snapshot(),resources.snapshot(),deadlock.snapshot(),engine.events(),scheduler.selections());
        });
    }
    public record Snapshot(UUID id,Instant capturedAt,double simulatedSeconds,ProcessingEngine.Status simulation,OperationalMetrics metrics,
            List<ProcessingSnapshot> active,List<Order> recentOrders,InventoryOverview inventory,StorageOverview storage,SchedulingOverview scheduling,
            ResourceOverview resources,DeadlockService.Overview deadlock,List<ProcessingEngine.Event> events,List<OrderScheduler.Selection> selections){}
}
