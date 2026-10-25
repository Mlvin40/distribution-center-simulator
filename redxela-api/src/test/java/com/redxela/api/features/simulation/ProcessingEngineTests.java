package com.redxela.api.features.simulation;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.generation.service.OrderGenerator;
import com.redxela.api.features.generation.model.GeneratorSettings;
import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.*;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.scheduling.service.OrderScheduler;
import com.redxela.api.features.scheduling.model.SchedulingPolicy;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class ProcessingEngineTests {
    final SimulationProperties settings=new SimulationProperties(60,10,3,2,5,20);
    final SimulationClock clock=new SimulationClock(settings);
    final CustomerService customers=new CustomerService(); final ProductCatalog catalog=new ProductCatalog();
    final OrderService orders=new OrderService(customers,catalog,clock,settings);
    final InventoryService inventory=new InventoryService(orders,catalog,settings,clock);
    final OrderIntakeService intake=new OrderIntakeService(orders,inventory);
    final ResourceManager resources=new ResourceManager();
    final OrderScheduler scheduler=new OrderScheduler(orders,resources,clock);
    Order create(int level,OrderSelection...lines){return intake.create(customers.create("Motor",level).id(),List.of(lines),OrderSource.MANUAL);}
    void stock(String product,int units){inventory.receive(UUID.randomUUID(),product,units);}
    void await(BooleanSupplier condition) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(5);assertTrue(condition.getAsBoolean());}
    void released(){assertTrue(resources.snapshot().resources().stream().allMatch(r->r.inUse()==0&&r.waiting().isEmpty()));assertTrue(scheduler.snapshot().claimed().isEmpty());}
    @Test void parallelMultiProductWorkCompletesValidStagesAndConsumesPhysicalInventory() throws Exception {
        stock("PRD-001",20);stock("PRD-004",20);
        var ids=new ArrayList<UUID>();for(int i=0;i<4;i++)ids.add(create(3,new OrderSelection("PRD-001",2),new OrderSelection("PRD-004",2)).id());
        try(var engine=new ProcessingEngine(orders,inventory,scheduler,resources,clock,settings)) {
            engine.start();await(()->engine.snapshot().size()>=2);
            assertTrue(engine.snapshot().stream().allMatch(s->s.progressPercent()<100));
            while(orders.list().stream().filter(o->o.status()==OrderStatus.COMPLETED).count()<4) {
                for(var r:resources.snapshot().resources())assertTrue(r.inUse()<=r.capacity());Thread.sleep(5);
            }
            for(var id:ids) {
                var order=orders.get(id);var states=order.history().stream().map(OrderTransition::status).toList();
                assertTrue(states.containsAll(List.of(OrderStatus.QUALITY_CHECK,OrderStatus.PACKING,OrderStatus.SCANNING,OrderStatus.LOADING,OrderStatus.COMPLETED)));
                assertNotNull(order.completedAt());assertTrue(order.completedAt().isAfter(order.processingStartedAt()));
            }
            assertTrue(engine.events().stream().anyMatch(e->e.type().equals("ASSIGNED")));
        }
        assertEquals(24,inventory.snapshot().onHand());assertEquals(24,inventory.storageSnapshot().occupied());assertEquals(0,inventory.snapshot().reserved());released();
        assertEquals(4,scheduler.snapshot().metrics().getFirst().completedOrders());
    }
    @Test void stoppingReturnsReservationsAndAllowsRestartWithNewWork() throws Exception {
        stock("PRD-001",100);var order=create(3,new OrderSelection("PRD-001",100));
        try(var engine=new ProcessingEngine(orders,inventory,scheduler,resources,clock,settings)) {
            engine.start();await(()->!engine.snapshot().isEmpty());engine.stop();await(()->engine.status().state().equals("STOPPED"));
            assertEquals(OrderStatus.CANCELLED,orders.get(order.id()).status());assertEquals(100,inventory.snapshot().available());released();
            var next=create(1,new OrderSelection("PRD-001",1));engine.start();await(()->orders.get(next.id()).status()==OrderStatus.COMPLETED);
        }
        assertEquals(99,inventory.snapshot().onHand());released();
    }
    @Test void sjfControlsActualSelectionAndWorkDependsOnUnits() throws Exception {
        stock("PRD-001",10);var big=create(3,new OrderSelection("PRD-001",4));var small=create(3,new OrderSelection("PRD-001",1));
        scheduler.changePolicy(SchedulingPolicy.SHORTEST_JOB_FIRST);
        try(var engine=new ProcessingEngine(orders,inventory,scheduler,resources,clock,settings)) {
            engine.start();await(()->orders.get(big.id()).status()==OrderStatus.COMPLETED && orders.get(small.id()).status()==OrderStatus.COMPLETED);
            var starts=scheduler.selections();
            assertEquals(small.id(),starts.getFirst().orderId());
            assertEquals(4*small.baseWorkSimulatedSeconds(),big.baseWorkSimulatedSeconds());
            double bigTime=orders.get(big.id()).history().getLast().simulatedSeconds()-orders.get(big.id()).history().stream().filter(e->e.status()==OrderStatus.PICKING).findFirst().orElseThrow().simulatedSeconds();
            assertTrue(bigTime>=40);
        }
        released();
    }
    @Test void generatorAndManualAdmissionsContinueDuringRealProcessingUnderLoad() throws Exception {
        for(var product:catalog.list())stock(product.id(),100);
        try(var engine=new ProcessingEngine(orders,inventory,scheduler,resources,clock,settings);
            var generator=new OrderGenerator(customers,orders,catalog,settings,intake)) {
            engine.start();generator.start(new GeneratorSettings(.1,42,1,1));
            try(var pool=Executors.newFixedThreadPool(8)){
                var tasks=new ArrayList<Callable<UUID>>();for(int i=0;i<60;i++)tasks.add(()->create(3,new OrderSelection("PRD-001",1)).id());
                var ids=new HashSet<UUID>();for(var result:pool.invokeAll(tasks))ids.add(result.get());assertEquals(60,ids.size());
            }
            await(()->generator.status().generatedOrders()>=30);generator.stop();long count=orders.count();
            await(()->orders.list().stream().filter(o->o.status()==OrderStatus.COMPLETED).count()==count);
            assertEquals(count,orders.list().stream().map(Order::id).distinct().count());
            assertEquals(0,inventory.snapshot().reserved());assertEquals(inventory.snapshot().onHand(),inventory.storageSnapshot().occupied());
        }
        released();
    }
    @Test void failureBeforeConsumeReturnsReservationAndWorkerContinues() throws Exception {
        stock("PRD-001",5);
        var broken=new InventoryService(orders,catalog,settings,clock){@Override public com.redxela.api.features.inventory.model.InventoryReservation consume(UUID id){throw new IllegalStateException("Fallo controlado de despacho");}};
        broken.receive(UUID.randomUUID(),"PRD-002",5);
        var failed=new OrderIntakeService(orders,broken).create(customers.create("Fallo",2).id(),List.of(new OrderSelection("PRD-002",1)),OrderSource.MANUAL);
        try(var engine=new ProcessingEngine(orders,broken,scheduler,resources,clock,settings)){
            engine.start();await(()->orders.get(failed.id()).status()==OrderStatus.FAILED);
            assertEquals(5,broken.snapshot().available());assertEquals(0,broken.snapshot().reserved());
        }
        released();
    }
}
