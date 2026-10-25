package com.redxela.api.features.inventory;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.order.service.*;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import com.redxela.api.shared.exception.ConflictException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class PhysicalStorageTests {
    final CustomerService customers = new CustomerService();
    final ProductCatalog catalog = new ProductCatalog();
    final SimulationProperties settings = new SimulationProperties(60,10,3,2,5,20);
    final OrderService orders = new OrderService(customers,catalog,new SimulationClock(settings),settings);
    final InventoryService inventory = new InventoryService(orders,catalog,settings,new SimulationClock(settings));
    final OrderIntakeService intake = new OrderIntakeService(orders,inventory);
    Order order(int units) { return intake.create(customers.create("Almacén",3).id(),List.of(new OrderSelection("PRD-001",units)),OrderSource.MANUAL); }
    void dispatch(Order order) {
        var previous = OrderStatus.READY;
        for (var next : List.of(OrderStatus.WAITING_RESOURCES,OrderStatus.PICKING,OrderStatus.PACKING,OrderStatus.SCANNING,OrderStatus.LOADING)) {
            orders.transition(order.id(),previous,next); previous=next;
        }
        inventory.consume(order.id());
    }
    void coherent() {
        var physical=inventory.storageSnapshot(); var stock=inventory.snapshot();
        assertEquals(stock.onHand(),physical.occupied()); assertEquals(stock.reserved(),physical.reserved());
        for(var location:physical.locations()) {
            assertTrue(location.occupied()<=location.location().capacity());
            assertTrue(location.reserved()<=location.occupied());
        }
        for(var product:stock.products()) assertEquals(product.onHand(),physical.locations().stream().flatMap(l->l.lots().stream())
            .filter(l->l.productId().equals(product.product().id())).mapToLong(l->l.quantity()).sum());
    }
    @Test void largeLotUsesSeveralLocationsAndFailedReceiptChangesNothing() {
        inventory.receive(UUID.randomUUID(),"PRD-001",55);
        assertEquals(3,inventory.storageSnapshot().locations().stream().filter(l->l.occupied()>0).count());
        var before=inventory.storageSnapshot();
        assertThrows(ConflictException.class,()->inventory.receive(UUID.randomUUID(),"PRD-002",546));
        assertEquals(before,inventory.storageSnapshot()); coherent();
    }
    @Test void lotCanOccupySeparatedHolesAndReservationsKeepSpaceUntilDispatch() {
        inventory.receive(UUID.randomUUID(),"PRD-001",20);
        inventory.receive(UUID.randomUUID(),"PRD-002",20);
        inventory.receive(UUID.randomUUID(),"PRD-001",20);
        var order=order(40);
        assertEquals(60,inventory.storageSnapshot().occupied()); assertEquals(40,inventory.storageSnapshot().reserved());
        dispatch(order);
        var lot=UUID.randomUUID(); inventory.receive(lot,"PRD-003",40);
        var occupied=inventory.storageSnapshot().locations().stream().filter(l->l.lots().stream().anyMatch(s->s.lotId().equals(lot))).toList();
        assertEquals(2,occupied.size()); assertEquals(1,occupied.get(0).location().position()); assertEquals(3,occupied.get(1).location().position());
        coherent();
    }
    @Test void concurrentReceiptsCannotOverbookAndRetriesDoNotAllocateAgain() throws Exception {
        try(var pool=Executors.newFixedThreadPool(12)) {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<100;i++) tasks.add(()-> { try{inventory.receive(UUID.randomUUID(),"PRD-001",10); return true;}catch(ConflictException ex){return false;} });
            int success=0; for(var task:pool.invokeAll(tasks)) if(task.get()) success++;
            assertEquals(60,success);
        }
        assertEquals(600,inventory.storageSnapshot().occupied()); coherent();
    }
    @Test void cancellationReleasesPhysicalReservationAndReceiptIsIdempotent() {
        var id=UUID.randomUUID(); inventory.receive(id,"PRD-001",25); inventory.receive(id,"PRD-001",25);
        var order=order(25); inventory.abort(order.id(),OrderStatus.CANCELLED);
        assertEquals(25,inventory.storageSnapshot().occupied()); assertEquals(0,inventory.storageSnapshot().reserved()); coherent();
    }
}
