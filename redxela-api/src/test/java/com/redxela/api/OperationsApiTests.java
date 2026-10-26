package com.redxela.api;

import com.redxela.api.features.metrics.service.SimulationEvents;
import java.net.URI;
import java.net.http.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="redxela.test.operations-isolation=true")
@Timeout(30)
class OperationsApiTests {
    @LocalServerPort int port;
    @Autowired SimulationEvents events;
    final HttpClient http=HttpClient.newHttpClient();
    HttpResponse<String> request(String path,String body)throws Exception{return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").method(body==null?"GET":"POST",body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    JsonNode json(HttpResponse<String> response){assertTrue(response.statusCode()<300,response.body());return new JsonMapper().readTree(response.body());}
    JsonNode get(String path)throws Exception{return json(request(path,null));}
    JsonNode post(String path,String body)throws Exception{return json(request(path,body));}
    void waitUntil(Callable<Boolean> condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!condition.call()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(condition.call());}
    @Test void completePipelineMetricsSnapshotSseRecoveryAndDeadlockRemainAccessibleUnderLoad()throws Exception{
        var customer=post("/customers","{\"name\":\"Integración operativa\",\"serviceLevel\":2}");
        var order=post("/orders","{\"customerId\":\""+customer.get("id").asString()+"\",\"lines\":[{\"productId\":\"PRD-004\",\"quantity\":3}]}");String id=order.get("id").asString();
        assertEquals("WAITING_INVENTORY",order.get("status").asString());
        var before=get("/operations");assertEquals(1,before.at("/metrics/waiting").asLong());assertEquals(0,before.at("/metrics/completed").asLong());assertTrue(before.at("/metrics/averageProcessingSimulatedSeconds").isNull());
        String arrival=UUID.randomUUID().toString();post("/reception/arrivals","{\"arrivalId\":\""+arrival+"\",\"productId\":\"PRD-004\",\"quantity\":3}");
        waitUntil(()->get("/reception/"+arrival).get("status").asString().equals("COMPLETED"));
        assertEquals(3,get("/storage").get("occupied").asLong());assertEquals("READY",get("/orders/"+id).get("status").asString());
        post("/simulation/start","");
        waitUntil(()->get("/orders/"+id).get("status").asString().equals("COMPLETED"));
        var done=get("/operations");assertEquals(1,done.at("/metrics/completed").asLong());assertEquals(0,done.at("/metrics/processing").asLong());
        assertEquals(0,done.at("/storage/occupied").asLong());assertEquals(0,done.at("/inventory/reserved").asLong());assertEquals(0,done.at("/inventory/onHand").asLong());
        assertTrue(done.at("/metrics/averageProcessingSimulatedSeconds").asDouble()>=30);assertTrue(done.at("/metrics/averageWaitSimulatedSeconds").asDouble()>=0);
        assertEquals(1,done.at("/scheduling/metrics/0/completedOrders").asLong());
        for(var value:done.at("/metrics/resourceUtilization").properties())assertTrue(value.getValue().asDouble()>=0&&value.getValue().asDouble()<=1);
        // Dos conexiones nuevas reciben snapshots completos; no necesitan reejecutar eventos previos
        for(int attempt=0;attempt<2;attempt++){
            var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/operations/events")).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200,response.statusCode());assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("text/event-stream"));
            try(var reader=new BufferedReader(new InputStreamReader(response.body()))){String line;boolean found=false;while((line=reader.readLine())!=null){if(line.startsWith("data:")){var snapshot=new JsonMapper().readTree(line.substring(5));assertEquals(1,snapshot.at("/metrics/completed").asLong());assertEquals(snapshot.at("/inventory/onHand").asLong(),snapshot.at("/storage/occupied").asLong());found=true;break;}}assertTrue(found);}
        }
        post("/deadlock/start","");waitUntil(()->get("/deadlock").get("detected").asBoolean());
        assertEquals(200,request("/health",null).statusCode());assertEquals(400,request("/deadlock/resolve","{}").statusCode());
        var winner=get("/deadlock").at("/operations/1/id").asString();post("/deadlock/resolve","{\"operationId\":\""+winner+"\"}");waitUntil(()->get("/deadlock").get("state").asString().equals("COMPLETED"));
        var arrival2=UUID.randomUUID();post("/reception/arrivals","{\"arrivalId\":\""+arrival2+"\",\"productId\":\"PRD-001\",\"quantity\":100}");
        waitUntil(()->get("/inventory").get("onHand").asLong()==100);
        post("/generator/start","{\"intervalSimulatedSeconds\":0.1,\"seed\":42,\"minUnits\":1,\"maxUnits\":1}");
        try{
            for(int i=0;i<25;i++){
                var snapshot=get("/operations");assertEquals(snapshot.at("/inventory/onHand").asLong(),snapshot.at("/storage/occupied").asLong());assertEquals(snapshot.at("/inventory/reserved").asLong(),snapshot.at("/storage/reserved").asLong());
                var m=snapshot.get("metrics");assertEquals(m.get("received").asLong(),m.get("waiting").asLong()+m.get("processing").asLong()+m.get("completed").asLong()+m.get("failed").asLong()+m.get("cancelled").asLong());
                assertEquals(200,request("/health",null).statusCode());Thread.sleep(10);
            }
        }finally{post("/generator/stop","");post("/simulation/stop","");}
        waitUntil(()->get("/simulation").get("state").asString().equals("STOPPED"));
        waitUntil(()->events.observerCount()==0);
    }
    @Test void simultaneousSseDisconnectsRemoveObserversWithoutKillingSenderThreads()throws Exception {
        var failures=new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        var previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->failures.add(error));
        try {
            var requests=new ArrayList<CompletableFuture<HttpResponse<InputStream>>>();
            for(int i=0;i<12;i++) requests.add(http.sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/operations/events")).GET().build(),HttpResponse.BodyHandlers.ofInputStream()));
            for(var future:requests){var response=future.get(5,TimeUnit.SECONDS);assertEquals(200,response.statusCode());response.body().close();}
            waitUntil(()->events.observerCount()==0);
            assertTrue(failures.isEmpty(),failures.toString());
            assertEquals(200,request("/operations",null).statusCode());
        } finally { Thread.setDefaultUncaughtExceptionHandler(previous); }
    }

}
