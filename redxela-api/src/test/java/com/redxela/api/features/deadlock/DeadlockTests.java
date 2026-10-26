package com.redxela.api.features.deadlock;

import com.redxela.api.features.deadlock.service.DeadlockService;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.shared.exception.ConflictException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class DeadlockTests {
    void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(2);assertTrue(condition.getAsBoolean());}
    @ParameterizedTest @ValueSource(ints={0,1}) void realCycleResolvesEitherWinnerAndLoserReturnsToQueue(int winnerIndex)throws Exception{
        var resources=new ResourceManager();
        try(var demo=new DeadlockService(resources)){
            for(int attempt=0;attempt<3;attempt++){
                demo.start();await(()->demo.snapshot().detected());var blocked=demo.snapshot();
                assertEquals("DEADLOCK",blocked.state());
                var a=blocked.operations().get(0);var b=blocked.operations().get(1);
                assertTrue(a.held().contains(b.requested()));assertTrue(b.held().contains(a.requested()));
                Thread.sleep(20);assertTrue(demo.snapshot().detected());
                assertThrows(ConflictException.class,demo::start);
                var winner=blocked.operations().get(winnerIndex).id();var loser=blocked.operations().get(1-winnerIndex).id();
                demo.resolve(winner);await(()->demo.snapshot().waitingOperations().contains(loser));await(()->demo.snapshot().state().equals("COMPLETED"));
                var resolved=demo.snapshot();assertEquals(winner,resolved.winner());
                assertTrue(resolved.history().contains(loser+" → WAITING"));assertTrue(resolved.history().contains(loser+" → RETRYING"));
                int winnerFinished=resolved.history().indexOf(winner+" → COMPLETED");int loserRetried=resolved.history().indexOf(loser+" → RETRYING");assertTrue(winnerFinished<loserRetried);
                assertTrue(resolved.operations().stream().allMatch(o->o.state().equals("COMPLETED")&&o.held().isEmpty()&&!o.waiting()));
                assertTrue(resources.snapshot().resources().stream().allMatch(r->r.inUse()==0&&r.waiting().isEmpty()));
                try(var lease=resources.acquire(new ResourceOwner(UUID.randomUUID(),null,OperationArea.DISPATCH,"Operación normal posterior"),Set.of(ResourceType.SCANNER,ResourceType.FORKLIFT),Duration.ZERO)){assertEquals(2,lease.assignments().size());}
            }
        }
    }
    @Test void shutdownInterruptsRealDeadlockAndReturnsPermits()throws Exception{
        var resources=new ResourceManager();var demo=new DeadlockService(resources);demo.start();await(()->demo.snapshot().detected());demo.close();
        assertTrue(resources.snapshot().resources().stream().allMatch(r->r.inUse()==0&&r.waiting().isEmpty()));
    }
}
