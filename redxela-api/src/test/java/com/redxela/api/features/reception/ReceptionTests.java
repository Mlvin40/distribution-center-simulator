package com.redxela.api.features.reception;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.order.service.*;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.reception.service.ReceptionService;
import com.redxela.api.features.reception.model.Arrival.Status;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class ReceptionTests {
    final SimulationProperties settings=new SimulationProperties(600,10,3,2,5,20);
    final ProductCatalog catalog=new ProductCatalog(); final CustomerService customers=new CustomerService();
    final OrderService orders=new OrderService(customers,catalog,new SimulationClock(settings),settings);
    final InventoryService inventory=new InventoryService(orders,catalog,settings,new SimulationClock(settings));
    final ResourceManager resources=new ResourceManager();
    void await(BooleanSupplier condition) throws Exception { long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8); while(!condition.getAsBoolean()&&System.nanoTime()<end) Thread.sleep(5); assertTrue(condition.getAsBoolean()); }
    @Test void fasterProducerPreservesMessagesAndDeduplicatesWhileConsumerReactivatesOrders() throws Exception {
        var order=new OrderIntakeService(orders,inventory).create(customers.create("Espera",3).id(),List.of(new OrderSelection("PRD-001",50)),OrderSource.MANUAL);
        try(var reception=new ReceptionService(inventory,catalog,resources,settings)) {
            reception.start();
            var ids=new ArrayList<UUID>();
            for(int i=0;i<100;i++){var id=UUID.randomUUID();ids.add(id);reception.register(id,"PRD-001",1);reception.register(id,"PRD-001",1);}
            await(()->reception.snapshot().pendingInventory()>5);
            await(()->reception.snapshot().completed()==100);
            assertEquals(100,reception.snapshot().registered()); assertEquals(100,inventory.snapshot().onHand());
            assertEquals(OrderStatus.READY,orders.get(order.id()).status()); assertEquals(100,inventory.storageSnapshot().occupied());
            assertEquals(100,ids.stream().filter(id->reception.get(id).status()==Status.COMPLETED).count());
        }
    }
    @Test void scannerContentionAndInterruptedAreasRecoverWithoutDuplicatingStock() throws Exception {
        try(var reception=new ReceptionService(inventory,catalog,resources,settings)) {
            reception.start(); UUID id=UUID.randomUUID();
            try(var lease=resources.acquire(new ResourceOwner(UUID.randomUUID(),null,OperationArea.DISPATCH,"Despacho"),Set.of(ResourceType.SCANNER),Duration.ZERO)) {
                reception.register(id,"PRD-002",21);
                await(()->resources.snapshot().resources().stream().anyMatch(r->!r.waiting().isEmpty()));
                assertEquals(0,inventory.snapshot().onHand()); reception.close();
            }
            reception.start(); await(()->reception.get(id).status()==Status.COMPLETED);
            assertEquals(21,inventory.snapshot().onHand()); assertEquals(2,inventory.storageSnapshot().locations().stream().filter(l->l.occupied()>0).count());
        }
        assertTrue(resources.snapshot().resources().stream().allMatch(r->r.inUse()==0));
    }
    @Test void fullWarehouseRetainsArrivalUntilSpaceBecomesAvailable() throws Exception {
        inventory.receive(UUID.randomUUID(),"PRD-001",600);
        try(var reception=new ReceptionService(inventory,catalog,resources,settings)) {
            reception.start(); var id=UUID.randomUUID(); reception.register(id,"PRD-002",20);
            await(()->reception.get(id).status()==Status.WAITING_CAPACITY);
            assertEquals(600,inventory.storageSnapshot().occupied());
            var order=new OrderIntakeService(orders,inventory).create(customers.create("Salida",3).id(),List.of(new OrderSelection("PRD-001",20)),OrderSource.MANUAL);
            var previous=OrderStatus.READY;
            for(var next:List.of(OrderStatus.WAITING_RESOURCES,OrderStatus.PICKING,OrderStatus.PACKING,OrderStatus.SCANNING,OrderStatus.LOADING)){orders.transition(order.id(),previous,next);previous=next;}
            inventory.consume(order.id()); await(()->reception.get(id).status()==Status.COMPLETED);
            assertEquals(600,inventory.snapshot().onHand()); assertEquals(600,inventory.storageSnapshot().occupied());
        }
    }
    @Test void saturatedReceptionRejectsExplicitlyAndEveryAcceptedMessageSurvivesShutdown() throws Exception {
        try(var reception=new ReceptionService(inventory,catalog,resources,settings)) {
            reception.start();var accepted=new ArrayList<UUID>();int rejected=0;
            try(var lease=resources.acquire(new ResourceOwner(UUID.randomUUID(),null,OperationArea.DISPATCH,"Escáner ocupado"),Set.of(ResourceType.SCANNER),Duration.ZERO)) {
                for(int i=0;i<500;i++) {
                    var id=UUID.randomUUID();
                    try { reception.register(id,"PRD-001",1);accepted.add(id); }
                    catch(com.redxela.api.shared.exception.ConflictException ex) { rejected++; }
                }
                assertTrue(rejected>0);assertEquals(500,accepted.size()+rejected);
                reception.close();
            }
            reception.start();await(()->reception.snapshot().completed()==accepted.size());
            assertEquals(accepted.size(),inventory.snapshot().onHand());
            assertEquals(accepted.size(),inventory.storageSnapshot().occupied());
            assertTrue(accepted.stream().allMatch(id->reception.get(id).status()==Status.COMPLETED));
        }
    }

}
