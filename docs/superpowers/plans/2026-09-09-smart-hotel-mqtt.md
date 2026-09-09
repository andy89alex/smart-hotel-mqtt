# Smart Hotel Room Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a hardware-free, MQTT-based smart hotel room controller (Spring Boot) that demonstrates the MQTT concepts a "Java + Spring + MQTT" job interview probes.

**Architecture:** A Mosquitto broker (Docker) sits between a `room-simulator` app that publishes room telemetry/state and a `dashboard` app that subscribes, keeps in-memory state, and pushes live updates to a browser via WebSocket/STOMP. A shared `common` module holds payload records and topic helpers. The simulator uses raw Eclipse Paho (one connection per room, so each room gets its own Last-Will), and the dashboard uses Spring Integration MQTT (single subscription of `hotel/#`).

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Integration MQTT, Eclipse Paho, Spring WebSocket + STOMP, Jackson, Maven (multi-module), Eclipse Mosquitto, JUnit 5, Testcontainers, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-09-smart-hotel-mqtt-design.md`

## Global Constraints

- Java 21, Spring Boot 3.x, Maven multi-module (parent `pom.xml` + modules `common`, `room-simulator`, `dashboard`).
- Broker: Eclipse Mosquitto official Docker image; anonymous access in the base version.
- Topic scheme (verbatim from spec §4), base `hotel/{floor}/{room}/...`:
  - `telemetry/temperature` — retained=false, QoS1 (simulator publishes)
  - `state/light`, `state/ac`, `state/dnd` — retained=true, QoS1 (simulator publishes)
  - `availability` — retained=true, LWT online/offline
  - `cmd/light`, `cmd/ac`, `cmd/dnd` — QoS1 (dashboard publishes, simulator subscribes)
- Dashboard subscribes to `hotel/#`.
- Payloads are compact JSON (see spec §4). All timestamps ISO-8601 UTC.
- Default room count kept small (4–6), configurable.
- Out of scope: database, user auth, fancy UI. TLS/auth on broker is an optional stretch goal only.
- TDD throughout: failing test first, minimal code, frequent commits.

---

### Task 1: Project scaffolding + Mosquitto broker

**Files:**
- Create: `pom.xml` (parent)
- Create: `common/pom.xml`
- Create: `room-simulator/pom.xml`
- Create: `dashboard/pom.xml`
- Create: `broker/mosquitto.conf`
- Create: `docker-compose.yml`
- Create: `.gitignore`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a buildable multi-module Maven project (`mvn -q -DskipTests package` succeeds) and a runnable Mosquitto broker on `localhost:1883`.

- [ ] **Step 1: Create `.gitignore`**

```gitignore
target/
*.class
.idea/
*.iml
.DS_Store
```

- [ ] **Step 2: Create parent `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>
    <groupId>com.example.smarthotel</groupId>
    <artifactId>smart-hotel-mqtt</artifactId>
    <version>0.1.0</version>
    <packaging>pom</packaging>
    <properties>
        <java.version>21</java.version>
    </properties>
    <modules>
        <module>common</module>
        <module>room-simulator</module>
        <module>dashboard</module>
    </modules>
</project>
```

- [ ] **Step 3: Create `common/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example.smarthotel</groupId>
        <artifactId>smart-hotel-mqtt</artifactId>
        <version>0.1.0</version>
    </parent>
    <artifactId>common</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: Create `room-simulator/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example.smarthotel</groupId>
        <artifactId>smart-hotel-mqtt</artifactId>
        <version>0.1.0</version>
    </parent>
    <artifactId>room-simulator</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.example.smarthotel</groupId>
            <artifactId>common</artifactId>
            <version>0.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.eclipse.paho</groupId>
            <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
            <version>1.2.5</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>1.20.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>1.20.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 5: Create `dashboard/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example.smarthotel</groupId>
        <artifactId>smart-hotel-mqtt</artifactId>
        <version>0.1.0</version>
    </parent>
    <artifactId>dashboard</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.integration</groupId>
            <artifactId>spring-integration-mqtt</artifactId>
        </dependency>
        <dependency>
            <groupId>com.example.smarthotel</groupId>
            <artifactId>common</artifactId>
            <version>0.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>1.20.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>1.20.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 6: Create `broker/mosquitto.conf`**

```conf
listener 1883
allow_anonymous true
persistence false
```

- [ ] **Step 7: Create `docker-compose.yml` (broker only for now)**

```yaml
services:
  broker:
    image: eclipse-mosquitto:2
    ports:
      - "1883:1883"
    volumes:
      - ./broker/mosquitto.conf:/mosquitto/config/mosquitto.conf:ro
```

- [ ] **Step 8: Verify the build compiles**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS (three modules produced; simulator/dashboard have no sources yet, which is fine).

- [ ] **Step 9: Verify the broker starts**

Run: `docker compose up -d broker && sleep 3 && docker compose ps`
Expected: `broker` is `running`. Then `docker compose down`.

- [ ] **Step 10: Commit**

```bash
git add pom.xml common/pom.xml room-simulator/pom.xml dashboard/pom.xml broker/mosquitto.conf docker-compose.yml .gitignore
git commit -m "chore: scaffold multi-module project and mosquitto broker"
```

---

### Task 2: Shared payload records + topic helper (`common`)

**Files:**
- Create: `common/src/main/java/com/example/smarthotel/common/RoomId.java`
- Create: `common/src/main/java/com/example/smarthotel/common/Topics.java`
- Create: `common/src/main/java/com/example/smarthotel/common/payload/TemperaturePayload.java`
- Create: `common/src/main/java/com/example/smarthotel/common/payload/StatePayload.java`
- Create: `common/src/main/java/com/example/smarthotel/common/payload/AvailabilityPayload.java`
- Create: `common/src/main/java/com/example/smarthotel/common/payload/CommandPayload.java`
- Create: `common/src/main/java/com/example/smarthotel/common/Json.java`
- Test: `common/src/test/java/com/example/smarthotel/common/TopicsTest.java`
- Test: `common/src/test/java/com/example/smarthotel/common/JsonTest.java`

**Interfaces:**
- Consumes: nothing beyond Jackson.
- Produces:
  - `record RoomId(String floor, String room)`.
  - `Topics.telemetryTemperature(RoomId)`, `Topics.stateLight(RoomId)`, `Topics.stateAc(RoomId)`, `Topics.stateDnd(RoomId)`, `Topics.availability(RoomId)`, `Topics.cmdLight(RoomId)`, `Topics.cmdAc(RoomId)`, `Topics.cmdDnd(RoomId)` — all `static String`.
  - `Topics.ALL_WILDCARD = "hotel/#"`.
  - `Topics.roomIdFromTopic(String topic) -> RoomId` and `Topics.leaf(String topic) -> String` (last segment) and `Topics.category(String topic) -> String` (segment after room: `telemetry`/`state`/`availability`/`cmd`).
  - `record TemperaturePayload(double value, String unit, Instant ts)`.
  - `record StatePayload(boolean on, Instant ts)`.
  - `record AvailabilityPayload(String status, Instant ts)` with constants `AvailabilityPayload.ONLINE`/`OFFLINE` = `"online"`/`"offline"`.
  - `record CommandPayload(boolean on)`.
  - `Json.MAPPER` (ObjectMapper with JavaTimeModule), `Json.toBytes(Object)`, `Json.read(byte[], Class<T>)`.

- [ ] **Step 1: Write failing `TopicsTest`**

```java
package com.example.smarthotel.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopicsTest {
    private final RoomId r = new RoomId("floor2", "room201");

    @Test
    void buildsTopics() {
        assertEquals("hotel/floor2/room201/telemetry/temperature", Topics.telemetryTemperature(r));
        assertEquals("hotel/floor2/room201/state/light", Topics.stateLight(r));
        assertEquals("hotel/floor2/room201/availability", Topics.availability(r));
        assertEquals("hotel/floor2/room201/cmd/dnd", Topics.cmdDnd(r));
    }

    @Test
    void parsesTopic() {
        String t = "hotel/floor2/room201/state/light";
        assertEquals(new RoomId("floor2", "room201"), Topics.roomIdFromTopic(t));
        assertEquals("state", Topics.category(t));
        assertEquals("light", Topics.leaf(t));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl common test -Dtest=TopicsTest`
Expected: FAIL (compilation error: `RoomId`/`Topics` not found).

- [ ] **Step 3: Implement `RoomId` and `Topics`**

`RoomId.java`:
```java
package com.example.smarthotel.common;

public record RoomId(String floor, String room) {}
```

`Topics.java`:
```java
package com.example.smarthotel.common;

public final class Topics {
    public static final String ALL_WILDCARD = "hotel/#";
    private Topics() {}

    private static String base(RoomId r) {
        return "hotel/" + r.floor() + "/" + r.room();
    }

    public static String telemetryTemperature(RoomId r) { return base(r) + "/telemetry/temperature"; }
    public static String stateLight(RoomId r) { return base(r) + "/state/light"; }
    public static String stateAc(RoomId r)    { return base(r) + "/state/ac"; }
    public static String stateDnd(RoomId r)   { return base(r) + "/state/dnd"; }
    public static String availability(RoomId r) { return base(r) + "/availability"; }
    public static String cmdLight(RoomId r) { return base(r) + "/cmd/light"; }
    public static String cmdAc(RoomId r)    { return base(r) + "/cmd/ac"; }
    public static String cmdDnd(RoomId r)   { return base(r) + "/cmd/dnd"; }

    // hotel/{floor}/{room}/{category}[/{leaf}]
    public static RoomId roomIdFromTopic(String topic) {
        String[] p = topic.split("/");
        return new RoomId(p[1], p[2]);
    }
    public static String category(String topic) { return topic.split("/")[3]; }
    public static String leaf(String topic) {
        String[] p = topic.split("/");
        return p[p.length - 1];
    }
}
```

- [ ] **Step 4: Run `TopicsTest` to verify pass**

Run: `mvn -q -pl common test -Dtest=TopicsTest`
Expected: PASS.

- [ ] **Step 5: Write failing `JsonTest`**

```java
package com.example.smarthotel.common;

import com.example.smarthotel.common.payload.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void roundTripsTemperature() {
        var p = new TemperaturePayload(22.4, "C", Instant.parse("2026-09-09T10:00:00Z"));
        byte[] bytes = Json.toBytes(p);
        var back = Json.read(bytes, TemperaturePayload.class);
        assertEquals(p, back);
    }

    @Test
    void roundTripsCommand() {
        var c = new CommandPayload(false);
        assertEquals(c, Json.read(Json.toBytes(c), CommandPayload.class));
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn -q -pl common test -Dtest=JsonTest`
Expected: FAIL (payload records / `Json` not found).

- [ ] **Step 7: Implement payload records + `Json`**

`payload/TemperaturePayload.java`:
```java
package com.example.smarthotel.common.payload;
import java.time.Instant;
public record TemperaturePayload(double value, String unit, Instant ts) {}
```

`payload/StatePayload.java`:
```java
package com.example.smarthotel.common.payload;
import java.time.Instant;
public record StatePayload(boolean on, Instant ts) {}
```

`payload/AvailabilityPayload.java`:
```java
package com.example.smarthotel.common.payload;
import java.time.Instant;
public record AvailabilityPayload(String status, Instant ts) {
    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";
}
```

`payload/CommandPayload.java`:
```java
package com.example.smarthotel.common.payload;
public record CommandPayload(boolean on) {}
```

`Json.java`:
```java
package com.example.smarthotel.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private Json() {}

    public static byte[] toBytes(Object o) {
        try { return MAPPER.writeValueAsBytes(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    public static <T> T read(byte[] data, Class<T> type) {
        try { return MAPPER.readValue(data, type); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
```

- [ ] **Step 8: Run all `common` tests to verify pass**

Run: `mvn -q -pl common test`
Expected: PASS (both test classes green).

- [ ] **Step 9: Commit**

```bash
git add common/src
git commit -m "feat(common): topic helpers, payload records, and JSON mapping"
```

---

### Task 3: Room MQTT connection with availability + LWT (`room-simulator`)

**Files:**
- Create: `room-simulator/src/main/java/com/example/smarthotel/sim/RoomClient.java`
- Test: `room-simulator/src/test/java/com/example/smarthotel/sim/MosquittoContainer.java`
- Test: `room-simulator/src/test/java/com/example/smarthotel/sim/RoomClientAvailabilityTest.java`

**Interfaces:**
- Consumes: `RoomId`, `Topics`, `AvailabilityPayload`, `Json` from `common`.
- Produces: `class RoomClient` with:
  - `RoomClient(String brokerUrl, RoomId roomId)`
  - `void connect() throws MqttException` — connects with LWT set to `availability=offline` (retained), then publishes `availability=online` (retained, QoS1).
  - `void disconnect()` — clean disconnect (triggers no LWT).
  - `void kill()` — `client.disconnectForcibly(0, 0)` to simulate a crash so the broker fires the LWT.
  - `RoomId roomId()`.

- [ ] **Step 1: Add reusable Testcontainers helper**

`MosquittoContainer.java`:
```java
package com.example.smarthotel.sim;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

public class MosquittoContainer extends GenericContainer<MosquittoContainer> {
    public MosquittoContainer() {
        super(DockerImageName.parse("eclipse-mosquitto:2"));
        withExposedPorts(1883);
        withCopyToContainer(
            Transferable.of("listener 1883\nallow_anonymous true\npersistence false\n"),
            "/mosquitto/config/mosquitto.conf");
        waitingFor(Wait.forListeningPort());
    }
    public String brokerUrl() {
        return "tcp://" + getHost() + ":" + getMappedPort(1883);
    }
}
```

- [ ] **Step 2: Write failing `RoomClientAvailabilityTest`**

```java
package com.example.smarthotel.sim;

import com.example.smarthotel.common.RoomId;
import com.example.smarthotel.common.Topics;
import com.example.smarthotel.common.Json;
import com.example.smarthotel.common.payload.AvailabilityPayload;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;

import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RoomClientAvailabilityTest {
    @Container static MosquittoContainer broker = new MosquittoContainer();
    private final RoomId room = new RoomId("floor1", "room101");

    private MqttClient subscribe(String topic, BlockingQueue<String> sink) throws Exception {
        MqttClient sub = new MqttClient(broker.brokerUrl(), "sub-" + System.nanoTime(), null);
        sub.connect();
        sub.subscribe(topic, 1, (t, m) ->
            sink.add(Json.read(m.getPayload(), AvailabilityPayload.class).status()));
        return sub;
    }

    @Test
    void publishesRetainedOnlineOnConnect() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect();

        BlockingQueue<String> sink = new LinkedBlockingQueue<>();
        subscribe(Topics.availability(room), sink); // retained → immediate delivery
        assertEquals("online", sink.poll(5, TimeUnit.SECONDS));
        client.disconnect();
    }

    @Test
    void firesOfflineLwtOnCrash() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect();

        BlockingQueue<String> sink = new LinkedBlockingQueue<>();
        subscribe(Topics.availability(room), sink);
        sink.poll(5, TimeUnit.SECONDS); // drain the retained "online"

        client.kill(); // ungraceful → broker publishes LWT
        assertEquals("offline", sink.poll(5, TimeUnit.SECONDS));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -pl room-simulator test -Dtest=RoomClientAvailabilityTest`
Expected: FAIL (compilation error: `RoomClient` not found). Requires Docker running.

- [ ] **Step 4: Implement `RoomClient`**

```java
package com.example.smarthotel.sim;

import com.example.smarthotel.common.Json;
import com.example.smarthotel.common.RoomId;
import com.example.smarthotel.common.Topics;
import com.example.smarthotel.common.payload.AvailabilityPayload;
import org.eclipse.paho.client.mqttv3.*;

import java.time.Instant;

public class RoomClient {
    private final RoomId roomId;
    private final MqttClient client;

    public RoomClient(String brokerUrl, RoomId roomId) throws MqttException {
        this.roomId = roomId;
        this.client = new MqttClient(brokerUrl,
                "room-" + roomId.floor() + "-" + roomId.room(), null);
    }

    public RoomId roomId() { return roomId; }

    public void connect() throws MqttException {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(false);
        byte[] will = Json.toBytes(new AvailabilityPayload(AvailabilityPayload.OFFLINE, Instant.now()));
        opts.setWill(Topics.availability(roomId), will, 1, true);
        client.connect(opts);
        publishRetained(Topics.availability(roomId),
                new AvailabilityPayload(AvailabilityPayload.ONLINE, Instant.now()));
    }

    protected void publishRetained(String topic, Object payload) throws MqttException {
        MqttMessage msg = new MqttMessage(Json.toBytes(payload));
        msg.setQos(1);
        msg.setRetained(true);
        client.publish(topic, msg);
    }

    public void disconnect() {
        try { client.disconnect(); client.close(); } catch (MqttException ignored) {}
    }

    public void kill() {
        try { client.disconnectForcibly(0, 0); } catch (MqttException ignored) {}
    }

    protected MqttClient raw() { return client; }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl room-simulator test -Dtest=RoomClientAvailabilityTest`
Expected: PASS (both tests).

- [ ] **Step 6: Commit**

```bash
git add room-simulator/src
git commit -m "feat(simulator): per-room MQTT connection with retained availability and LWT"
```

---

### Task 4: Room state + telemetry publishing (`room-simulator`)

**Files:**
- Modify: `room-simulator/src/main/java/com/example/smarthotel/sim/RoomClient.java`
- Test: `room-simulator/src/test/java/com/example/smarthotel/sim/RoomClientStateTest.java`

**Interfaces:**
- Consumes: everything from Task 3, plus `StatePayload`, `TemperaturePayload`.
- Produces, added to `RoomClient`:
  - `void publishAllState()` — publishes current light/ac/dnd as retained QoS1 to `state/*`.
  - `void publishTemperature()` — publishes a `TemperaturePayload` (non-retained, QoS1) to `telemetry/temperature`; value drifts within 18–26 °C.
  - `boolean light()`, `boolean ac()`, `boolean dnd()` accessors (defaults: light false, ac false, dnd false).

- [ ] **Step 1: Write failing `RoomClientStateTest`**

```java
package com.example.smarthotel.sim;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;

import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RoomClientStateTest {
    @Container static MosquittoContainer broker = new MosquittoContainer();
    private final RoomId room = new RoomId("floor1", "room102");

    @Test
    void publishesRetainedStateAndTelemetry() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect();
        client.publishAllState();

        BlockingQueue<Boolean> lightSink = new LinkedBlockingQueue<>();
        MqttClient sub = new MqttClient(broker.brokerUrl(), "sub-" + System.nanoTime(), null);
        sub.connect();
        sub.subscribe(Topics.stateLight(room), 1, (t, m) ->
            lightSink.add(Json.read(m.getPayload(), StatePayload.class).on()));
        assertEquals(Boolean.FALSE, lightSink.poll(5, TimeUnit.SECONDS)); // retained default

        BlockingQueue<Double> tempSink = new LinkedBlockingQueue<>();
        sub.subscribe(Topics.telemetryTemperature(room), 1, (t, m) ->
            tempSink.add(Json.read(m.getPayload(), TemperaturePayload.class).value()));
        client.publishTemperature();
        Double v = tempSink.poll(5, TimeUnit.SECONDS);
        assertNotNull(v);
        assertTrue(v >= 18.0 && v <= 26.0, "temperature in range: " + v);

        client.disconnect();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl room-simulator test -Dtest=RoomClientStateTest`
Expected: FAIL (`publishAllState`/`publishTemperature` not found).

- [ ] **Step 3: Add state + telemetry to `RoomClient`**

Add fields and methods inside `RoomClient` (keep existing code):
```java
    private volatile boolean light = false;
    private volatile boolean ac = false;
    private volatile boolean dnd = false;
    private final java.util.Random rnd = new java.util.Random();
    private double temperature = 22.0;

    public boolean light() { return light; }
    public boolean ac() { return ac; }
    public boolean dnd() { return dnd; }

    public void publishAllState() throws MqttException {
        publishRetained(Topics.stateLight(roomId), new com.example.smarthotel.common.payload.StatePayload(light, java.time.Instant.now()));
        publishRetained(Topics.stateAc(roomId),    new com.example.smarthotel.common.payload.StatePayload(ac, java.time.Instant.now()));
        publishRetained(Topics.stateDnd(roomId),   new com.example.smarthotel.common.payload.StatePayload(dnd, java.time.Instant.now()));
    }

    public void publishTemperature() throws MqttException {
        temperature += (rnd.nextDouble() - 0.5); // drift ±0.5
        if (temperature < 18) temperature = 18;
        if (temperature > 26) temperature = 26;
        double rounded = Math.round(temperature * 10.0) / 10.0;
        MqttMessage msg = new MqttMessage(Json.toBytes(
            new com.example.smarthotel.common.payload.TemperaturePayload(rounded, "C", java.time.Instant.now())));
        msg.setQos(1);
        msg.setRetained(false);
        raw().publish(Topics.telemetryTemperature(roomId), msg);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl room-simulator test -Dtest=RoomClientStateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add room-simulator/src
git commit -m "feat(simulator): publish retained room state and temperature telemetry"
```

---

### Task 5: Command handling (`room-simulator`)

**Files:**
- Modify: `room-simulator/src/main/java/com/example/smarthotel/sim/RoomClient.java`
- Test: `room-simulator/src/test/java/com/example/smarthotel/sim/RoomClientCommandTest.java`

**Interfaces:**
- Consumes: everything from Task 4, plus `CommandPayload`.
- Produces, added to `RoomClient`: `void subscribeToCommands()` — subscribes to `cmd/light`, `cmd/ac`, `cmd/dnd` (QoS1); on a command, updates the matching state field and re-publishes that state topic (retained QoS1). Wired into `connect()` after the online publish so a connected room is immediately controllable.

- [ ] **Step 1: Write failing `RoomClientCommandTest`**

```java
package com.example.smarthotel.sim;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;

import java.time.Instant;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RoomClientCommandTest {
    @Container static MosquittoContainer broker = new MosquittoContainer();
    private final RoomId room = new RoomId("floor1", "room103");

    @Test
    void commandTurnsLightOnAndRepublishesState() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect(); // must also subscribeToCommands internally

        MqttClient peer = new MqttClient(broker.brokerUrl(), "peer-" + System.nanoTime(), null);
        peer.connect();

        BlockingQueue<Boolean> lightSink = new LinkedBlockingQueue<>();
        peer.subscribe(Topics.stateLight(room), 1, (t, m) ->
            lightSink.add(Json.read(m.getPayload(), StatePayload.class).on()));
        lightSink.poll(3, TimeUnit.SECONDS); // drain retained default (false), if present

        MqttMessage cmd = new MqttMessage(Json.toBytes(new CommandPayload(true)));
        cmd.setQos(1);
        peer.publish(Topics.cmdLight(room), cmd);

        Boolean seen = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Boolean v = lightSink.poll(5, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(v)) { seen = v; break; }
        }
        assertEquals(Boolean.TRUE, seen);
        assertTrue(client.light());
        client.disconnect();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl room-simulator test -Dtest=RoomClientCommandTest`
Expected: FAIL (`subscribeToCommands` not wired; `client.light()` stays false).

- [ ] **Step 3: Add command handling to `RoomClient`**

Add method and call it from `connect()`:
```java
    public void subscribeToCommands() throws MqttException {
        client.subscribe(Topics.cmdLight(roomId), 1, (t, m) -> applyCommand("light", m));
        client.subscribe(Topics.cmdAc(roomId),    1, (t, m) -> applyCommand("ac", m));
        client.subscribe(Topics.cmdDnd(roomId),   1, (t, m) -> applyCommand("dnd", m));
    }

    private void applyCommand(String which, MqttMessage m) {
        boolean on = Json.read(m.getPayload(), com.example.smarthotel.common.payload.CommandPayload.class).on();
        try {
            switch (which) {
                case "light" -> { light = on; publishRetained(Topics.stateLight(roomId), state(on)); }
                case "ac"    -> { ac = on;    publishRetained(Topics.stateAc(roomId), state(on)); }
                case "dnd"   -> { dnd = on;   publishRetained(Topics.stateDnd(roomId), state(on)); }
            }
        } catch (MqttException e) { throw new RuntimeException(e); }
    }

    private com.example.smarthotel.common.payload.StatePayload state(boolean on) {
        return new com.example.smarthotel.common.payload.StatePayload(on, java.time.Instant.now());
    }
```

In `connect()`, after the online publish line, add:
```java
        subscribeToCommands();
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl room-simulator test -Dtest=RoomClientCommandTest`
Expected: PASS.

- [ ] **Step 5: Run the whole simulator test suite**

Run: `mvn -q -pl room-simulator test`
Expected: PASS (availability, state, command tests all green).

- [ ] **Step 6: Commit**

```bash
git add room-simulator/src
git commit -m "feat(simulator): handle cmd/* commands and re-publish room state"
```

---

### Task 6: Simulator Spring Boot app wiring N rooms

**Files:**
- Create: `room-simulator/src/main/java/com/example/smarthotel/sim/SimulatorApplication.java`
- Create: `room-simulator/src/main/java/com/example/smarthotel/sim/SimulatorProperties.java`
- Create: `room-simulator/src/main/java/com/example/smarthotel/sim/RoomManager.java`
- Create: `room-simulator/src/main/resources/application.yml`
- Test: `room-simulator/src/test/java/com/example/smarthotel/sim/RoomManagerTest.java`

**Interfaces:**
- Consumes: `RoomClient`, `RoomId`.
- Produces:
  - `record SimulatorProperties(String brokerUrl, List<RoomSpec> rooms, long telemetryIntervalMs)` with nested `record RoomSpec(String floor, String room)` — bound from `simulator.*`.
  - `class RoomManager` (Spring `@Component`): `void startAll()` connects a `RoomClient` per configured room and publishes initial state; a scheduled task every `telemetryIntervalMs` calls `publishTemperature()` on each room; `void stopAll()` disconnects all; `List<RoomClient> rooms()` accessor for tests.

- [ ] **Step 1: Write failing `RoomManagerTest`**

```java
package com.example.smarthotel.sim;

import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RoomManagerTest {
    @Container static MosquittoContainer broker = new MosquittoContainer();

    @Test
    void startsAllConfiguredRooms() throws Exception {
        var props = new SimulatorProperties(
            broker.brokerUrl(),
            List.of(new SimulatorProperties.RoomSpec("floor1", "room101"),
                    new SimulatorProperties.RoomSpec("floor1", "room102")),
            5000L);
        RoomManager manager = new RoomManager(props);
        manager.startAll();
        assertEquals(2, manager.rooms().size());
        manager.tick(); // publishes temperature to all without throwing
        manager.stopAll();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl room-simulator test -Dtest=RoomManagerTest`
Expected: FAIL (`SimulatorProperties`/`RoomManager` not found).

- [ ] **Step 3: Implement `SimulatorProperties`**

```java
package com.example.smarthotel.sim;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(String brokerUrl, List<RoomSpec> rooms, long telemetryIntervalMs) {
    public record RoomSpec(String floor, String room) {}
}
```

- [ ] **Step 4: Implement `RoomManager`**

```java
package com.example.smarthotel.sim;

import com.example.smarthotel.common.RoomId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RoomManager {
    private final SimulatorProperties props;
    private final List<RoomClient> rooms = new ArrayList<>();

    public RoomManager(SimulatorProperties props) { this.props = props; }

    public void startAll() {
        for (var spec : props.rooms()) {
            try {
                RoomClient client = new RoomClient(props.brokerUrl(), new RoomId(spec.floor(), spec.room()));
                client.connect();
                client.publishAllState();
                rooms.add(client);
            } catch (Exception e) { throw new RuntimeException("failed to start " + spec, e); }
        }
    }

    @Scheduled(fixedDelayString = "${simulator.telemetryIntervalMs:5000}")
    public void tick() {
        for (RoomClient r : rooms) {
            try { r.publishTemperature(); } catch (Exception ignored) {}
        }
    }

    public void stopAll() {
        rooms.forEach(RoomClient::disconnect);
        rooms.clear();
    }

    public List<RoomClient> rooms() { return rooms; }
}
```

- [ ] **Step 5: Implement `SimulatorApplication`**

```java
package com.example.smarthotel.sim;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
public class SimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }

    @Bean
    CommandLineRunner startRooms(RoomManager manager) {
        return args -> manager.startAll();
    }
}
```

- [ ] **Step 6: Create `application.yml`**

```yaml
spring:
  main:
    web-application-type: none
simulator:
  brokerUrl: ${BROKER_URL:tcp://localhost:1883}
  telemetryIntervalMs: 5000
  rooms:
    - { floor: floor1, room: room101 }
    - { floor: floor1, room: room102 }
    - { floor: floor2, room: room201 }
    - { floor: floor2, room: room202 }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q -pl room-simulator test -Dtest=RoomManagerTest`
Expected: PASS.

- [ ] **Step 8: Manual smoke test against local broker**

Run:
```bash
docker compose up -d broker
mvn -q -pl room-simulator -am spring-boot:run &
sleep 8
docker run --rm --network host eclipse-mosquitto:2 mosquitto_sub -h localhost -t 'hotel/#' -v -W 3
```
Expected: printed retained `availability`/`state/*` lines plus periodic `telemetry/temperature` for the four rooms. Then stop the app (`kill %1`) and `docker compose down`.

- [ ] **Step 9: Commit**

```bash
git add room-simulator/src room-simulator/src/main/resources/application.yml
git commit -m "feat(simulator): Spring Boot app running N configurable rooms with scheduled telemetry"
```

---

### Task 7: Dashboard MQTT ingest + in-memory room store

**Files:**
- Create: `dashboard/src/main/java/com/example/smarthotel/dashboard/DashboardApplication.java`
- Create: `dashboard/src/main/java/com/example/smarthotel/dashboard/model/RoomView.java`
- Create: `dashboard/src/main/java/com/example/smarthotel/dashboard/RoomStore.java`
- Create: `dashboard/src/main/java/com/example/smarthotel/dashboard/MqttIngest.java`
- Create: `dashboard/src/main/resources/application.yml`
- Test: `dashboard/src/test/java/com/example/smarthotel/dashboard/RoomStoreTest.java`

**Interfaces:**
- Consumes: `Topics`, `RoomId`, payload records, `Json` from `common`.
- Produces:
  - `class RoomView` mutable holder: fields `floor`, `room`, `Double temperature`, `boolean light`, `boolean ac`, `boolean dnd`, `String availability`; `String key()` returns `floor + "/" + room`.
  - `class RoomStore` (`@Component`): `RoomView apply(String topic, byte[] payload)` — parses by `Topics.category(topic)`/`Topics.leaf(topic)`, updates the `RoomView` for that room, returns it; `Collection<RoomView> all()`.
  - `class MqttIngest` — a `@ServiceActivator` on the Spring Integration inbound MQTT channel that calls `RoomStore.apply(...)` then hands the updated `RoomView` to the broadcaster (added in Task 8). For this task it just updates the store; broadcasting is wired in Task 8.

- [ ] **Step 1: Write failing `RoomStoreTest`**

```java
package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import com.example.smarthotel.dashboard.model.RoomView;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class RoomStoreTest {
    private final RoomId room = new RoomId("floor2", "room201");
    private final RoomStore store = new RoomStore();

    @Test
    void appliesAvailabilityStateAndTemperature() {
        store.apply(Topics.availability(room),
            Json.toBytes(new AvailabilityPayload(AvailabilityPayload.ONLINE, Instant.now())));
        store.apply(Topics.stateLight(room),
            Json.toBytes(new StatePayload(true, Instant.now())));
        RoomView v = store.apply(Topics.telemetryTemperature(room),
            Json.toBytes(new TemperaturePayload(23.5, "C", Instant.now())));

        assertEquals("floor2/room201", v.key());
        assertEquals("online", v.availability);
        assertTrue(v.light);
        assertEquals(23.5, v.temperature);
        assertEquals(1, store.all().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl dashboard test -Dtest=RoomStoreTest`
Expected: FAIL (`RoomView`/`RoomStore` not found).

- [ ] **Step 3: Implement `RoomView`**

```java
package com.example.smarthotel.dashboard.model;

public class RoomView {
    public String floor;
    public String room;
    public Double temperature;
    public boolean light;
    public boolean ac;
    public boolean dnd;
    public String availability = "unknown";

    public String key() { return floor + "/" + room; }
}
```

- [ ] **Step 4: Implement `RoomStore`**

```java
package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import com.example.smarthotel.dashboard.model.RoomView;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomStore {
    private final Map<String, RoomView> rooms = new ConcurrentHashMap<>();

    public RoomView apply(String topic, byte[] payload) {
        RoomId id = Topics.roomIdFromTopic(topic);
        RoomView v = rooms.computeIfAbsent(id.floor() + "/" + id.room(), k -> {
            RoomView nv = new RoomView();
            nv.floor = id.floor();
            nv.room = id.room();
            return nv;
        });
        String category = Topics.category(topic);
        switch (category) {
            case "availability" -> v.availability = Json.read(payload, AvailabilityPayload.class).status();
            case "telemetry"   -> v.temperature = Json.read(payload, TemperaturePayload.class).value();
            case "state" -> {
                boolean on = Json.read(payload, StatePayload.class).on();
                switch (Topics.leaf(topic)) {
                    case "light" -> v.light = on;
                    case "ac"    -> v.ac = on;
                    case "dnd"   -> v.dnd = on;
                }
            }
        }
        return v;
    }

    public Collection<RoomView> all() { return rooms.values(); }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl dashboard test -Dtest=RoomStoreTest`
Expected: PASS.

- [ ] **Step 6: Implement Spring Integration inbound + `MqttIngest` + app + config**

`MqttIngest.java` (broadcast hook added in Task 8):
```java
package com.example.smarthotel.dashboard;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class MqttIngest {
    private final RoomStore store;
    public MqttIngest(RoomStore store) { this.store = store; }

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<byte[]> message,
                       @Header(MqttHeaders.RECEIVED_TOPIC) String topic) {
        store.apply(topic, message.getPayload());
    }
}
```

`DashboardApplication.java` (defines the inbound adapter subscribing `hotel/#`):
```java
package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.Topics;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;

@SpringBootApplication
public class DashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardApplication.class, args);
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory(@Value("${dashboard.brokerUrl}") String brokerUrl) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setServerURIs(new String[]{brokerUrl});
        factory.setConnectionOptions(opts);
        return factory;
    }

    @Bean
    public MessageChannel mqttInboundChannel() { return new DirectChannel(); }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter inbound(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
            new MqttPahoMessageDrivenChannelAdapter("dashboard-in", factory, Topics.ALL_WILDCARD);
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }
}
```

`application.yml`:
```yaml
server:
  port: 8080
dashboard:
  brokerUrl: ${BROKER_URL:tcp://localhost:1883}
```

- [ ] **Step 7: Verify dashboard compiles and unit test still passes**

Run: `mvn -q -pl dashboard test -Dtest=RoomStoreTest`
Expected: PASS, and module compiles (Spring Integration beans included).

- [ ] **Step 8: Commit**

```bash
git add dashboard/src dashboard/src/main/resources/application.yml
git commit -m "feat(dashboard): Spring Integration MQTT ingest into in-memory room store"
```

---

### Task 8: Dashboard WebSocket broadcast + command endpoint

**Files:**
- Create: `dashboard/src/main/java/com/example/smarthotel/dashboard/WebSocketConfig.java`
- Create: `dashboard/src/main/java/com/example/smarthotel/dashboard/RoomBroadcaster.java`
- Create: `dashboard/src/main/java/com/example/smarthotel/dashboard/CommandPublisher.java`
- Create: `dashboard/src/main/java/com/example/smarthotel/dashboard/RoomController.java`
- Modify: `dashboard/src/main/java/com/example/smarthotel/dashboard/MqttIngest.java`
- Modify: `dashboard/src/main/java/com/example/smarthotel/dashboard/DashboardApplication.java`
- Test: `dashboard/src/test/java/com/example/smarthotel/dashboard/CommandPublishIntegrationTest.java`

**Interfaces:**
- Consumes: `RoomStore`, `RoomView`, Spring Integration factory bean, `Topics`, `CommandPayload`, `Json`.
- Produces:
  - `RoomBroadcaster.broadcast(RoomView)` — sends to STOMP topic `/topic/rooms`.
  - `CommandPublisher.send(RoomId, String device, boolean on)` — publishes a `CommandPayload` (QoS1) to the matching `cmd/*` topic via a Spring Integration outbound handler bound to channel `mqttOutboundChannel`.
  - `RoomController`: `GET /` serves the page; `GET /api/rooms` returns `store.all()`; `POST /api/rooms/{floor}/{room}/cmd/{device}` body `{"on":true}` → `CommandPublisher.send(...)`.

- [ ] **Step 1: Write failing `CommandPublishIntegrationTest`**

```java
package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;

import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommandPublishIntegrationTest {
    @Container static MosquittoContainer broker = new MosquittoContainer();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("dashboard.brokerUrl", broker::brokerUrl);
    }

    @Autowired CommandPublisher publisher;
    private final RoomId room = new RoomId("floor3", "room301");

    @Test
    void publishesCommandToBroker() throws Exception {
        MqttClient sub = new MqttClient(broker.brokerUrl(), "sub-" + System.nanoTime(), null);
        sub.connect();
        BlockingQueue<Boolean> sink = new LinkedBlockingQueue<>();
        sub.subscribe(Topics.cmdLight(room), 1, (t, m) ->
            sink.add(Json.read(m.getPayload(), CommandPayload.class).on()));

        publisher.send(room, "light", true);
        assertEquals(Boolean.TRUE, sink.poll(5, TimeUnit.SECONDS));
    }
}
```

Reuse the `MosquittoContainer` helper — copy it to `dashboard/src/test/java/com/example/smarthotel/dashboard/MosquittoContainer.java` (same body as Task 3 Step 1, package `com.example.smarthotel.dashboard`).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl dashboard test -Dtest=CommandPublishIntegrationTest`
Expected: FAIL (`CommandPublisher` not found).

- [ ] **Step 3: Add outbound channel + handler to `DashboardApplication`**

Add beans inside `DashboardApplication`:
```java
    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new org.springframework.integration.channel.DirectChannel();
    }

    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "mqttOutboundChannel")
    public org.springframework.messaging.MessageHandler mqttOutbound(MqttPahoClientFactory factory) {
        var handler = new org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler("dashboard-out", factory);
        handler.setAsync(true);
        handler.setDefaultQos(1);
        handler.setTopicExpressionString("headers['mqtt_topic']");
        return handler;
    }
```

- [ ] **Step 4: Implement `CommandPublisher`**

```java
package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.CommandPayload;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class CommandPublisher {
    private final MessageChannel mqttOutboundChannel;
    public CommandPublisher(MessageChannel mqttOutboundChannel) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    public void send(RoomId room, String device, boolean on) {
        String topic = switch (device) {
            case "light" -> Topics.cmdLight(room);
            case "ac"    -> Topics.cmdAc(room);
            case "dnd"   -> Topics.cmdDnd(room);
            default -> throw new IllegalArgumentException("unknown device: " + device);
        };
        mqttOutboundChannel.send(MessageBuilder
            .withPayload(Json.toBytes(new CommandPayload(on)))
            .setHeader("mqtt_topic", topic)
            .build());
    }
}
```

- [ ] **Step 5: Run integration test to verify it passes**

Run: `mvn -q -pl dashboard test -Dtest=CommandPublishIntegrationTest`
Expected: PASS.

- [ ] **Step 6: Add WebSocket config + broadcaster + wire into `MqttIngest`**

`WebSocketConfig.java`:
```java
package com.example.smarthotel.dashboard;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }
}
```

`RoomBroadcaster.java`:
```java
package com.example.smarthotel.dashboard;

import com.example.smarthotel.dashboard.model.RoomView;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoomBroadcaster {
    private final SimpMessagingTemplate messaging;
    public RoomBroadcaster(SimpMessagingTemplate messaging) { this.messaging = messaging; }
    public void broadcast(RoomView view) { messaging.convertAndSend("/topic/rooms", view); }
}
```

Modify `MqttIngest` to broadcast after applying:
```java
package com.example.smarthotel.dashboard;

import com.example.smarthotel.dashboard.model.RoomView;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class MqttIngest {
    private final RoomStore store;
    private final RoomBroadcaster broadcaster;
    public MqttIngest(RoomStore store, RoomBroadcaster broadcaster) {
        this.store = store;
        this.broadcaster = broadcaster;
    }

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<byte[]> message,
                       @Header(MqttHeaders.RECEIVED_TOPIC) String topic) {
        RoomView updated = store.apply(topic, message.getPayload());
        broadcaster.broadcast(updated);
    }
}
```

- [ ] **Step 7: Implement `RoomController`**

```java
package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.RoomId;
import com.example.smarthotel.dashboard.model.RoomView;
import com.example.smarthotel.common.payload.CommandPayload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@Controller
public class RoomController {
    private final RoomStore store;
    private final CommandPublisher publisher;
    public RoomController(RoomStore store, CommandPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    @GetMapping("/")
    public String index() { return "index"; }

    @GetMapping("/api/rooms")
    @ResponseBody
    public Collection<RoomView> rooms() { return store.all(); }

    @PostMapping("/api/rooms/{floor}/{room}/cmd/{device}")
    @ResponseBody
    public void command(@PathVariable String floor, @PathVariable String room,
                        @PathVariable String device, @RequestBody CommandPayload body) {
        publisher.send(new RoomId(floor, room), device, body.on());
    }
}
```

- [ ] **Step 8: Verify full dashboard test suite passes**

Run: `mvn -q -pl dashboard test`
Expected: PASS (`RoomStoreTest`, `CommandPublishIntegrationTest`).

- [ ] **Step 9: Commit**

```bash
git add dashboard/src
git commit -m "feat(dashboard): STOMP broadcast, command REST endpoint, MQTT outbound"
```

---

### Task 9: Dashboard web page (front-desk UI)

**Files:**
- Create: `dashboard/src/main/resources/templates/index.html`
- Modify: `dashboard/pom.xml` (add `spring-boot-starter-thymeleaf`)

**Interfaces:**
- Consumes: `GET /api/rooms`, STOMP endpoint `/ws`, topic `/topic/rooms`, `POST /api/rooms/{floor}/{room}/cmd/{device}`.
- Produces: a single self-contained page rendering one card per room and toggle buttons. No unit test (UI/manual verification in Step 4).

- [ ] **Step 1: Add Thymeleaf dependency to `dashboard/pom.xml`**

Add inside `<dependencies>`:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
```

- [ ] **Step 2: Create `templates/index.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <title>Front Desk — Room Status</title>
  <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
  <style>
    body { font-family: system-ui, sans-serif; margin: 24px; background:#f4f5f7; }
    h1 { font-size: 20px; }
    #rooms { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:12px; }
    .card { background:#fff; border-radius:10px; padding:14px; box-shadow:0 1px 3px rgba(0,0,0,.1); }
    .room { font-weight:600; }
    .off { color:#b00; } .on { color:#0a0; }
    .badge { font-size:12px; padding:2px 8px; border-radius:12px; background:#eee; }
    .offline { background:#fdd; } .online { background:#dfd; }
    button { margin:2px; padding:4px 8px; cursor:pointer; }
    .temp { font-size:24px; }
  </style>
</head>
<body>
  <h1>Front Desk — Live Room Status</h1>
  <div id="rooms"></div>
  <script>
    const cards = {};
    function keyOf(r){ return r.floor + "/" + r.room; }
    function render(r){
      const k = keyOf(r);
      let el = cards[k];
      if(!el){ el = document.createElement('div'); el.className='card'; cards[k]=el;
        document.getElementById('rooms').appendChild(el); }
      const t = (r.temperature==null) ? '—' : r.temperature.toFixed(1)+'°C';
      el.innerHTML =
        '<div class="room">'+r.floor+' / '+r.room+'</div>'+
        '<div><span class="badge '+(r.availability)+'">'+r.availability+'</span></div>'+
        '<div class="temp">'+t+'</div>'+
        '<div>Light: <span class="'+(r.light?'on':'off')+'">'+(r.light?'ON':'OFF')+'</span> '+
          '<button onclick="cmd(\''+r.floor+'\',\''+r.room+'\',\'light\','+(!r.light)+')">toggle</button></div>'+
        '<div>AC: <span class="'+(r.ac?'on':'off')+'">'+(r.ac?'ON':'OFF')+'</span> '+
          '<button onclick="cmd(\''+r.floor+'\',\''+r.room+'\',\'ac\','+(!r.ac)+')">toggle</button></div>'+
        '<div>DND: <span class="'+(r.dnd?'on':'off')+'">'+(r.dnd?'ON':'OFF')+'</span> '+
          '<button onclick="cmd(\''+r.floor+'\',\''+r.room+'\',\'dnd\','+(!r.dnd)+')">toggle</button></div>';
    }
    function cmd(floor, room, device, on){
      fetch('/api/rooms/'+floor+'/'+room+'/cmd/'+device, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({on: on})
      });
    }
    fetch('/api/rooms').then(r=>r.json()).then(list=>list.forEach(render));
    const sock = new SockJS('/ws');
    const stomp = Stomp.over(sock);
    stomp.debug = null;
    stomp.connect({}, () => stomp.subscribe('/topic/rooms', m => render(JSON.parse(m.body))));
  </script>
</body>
</html>
```

- [ ] **Step 3: Verify dashboard still builds**

Run: `mvn -q -pl dashboard -am -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Manual end-to-end check (broker + simulator + dashboard)**

Run:
```bash
docker compose up -d broker
mvn -q -pl room-simulator -am spring-boot:run &
mvn -q -pl dashboard -am spring-boot:run &
sleep 12
```
Open `http://localhost:8080` — expect room cards with live temperature, online badges. Click a Light toggle → the card flips ON within ~1s (command → simulator → state republish → STOMP). Stop a room by killing the simulator (`kill %1`) → within seconds its badge shows `offline` (LWT). Then `kill %2` and `docker compose down`.

- [ ] **Step 5: Commit**

```bash
git add dashboard/pom.xml dashboard/src/main/resources/templates/index.html
git commit -m "feat(dashboard): live front-desk web page over STOMP"
```

---

### Task 10: Full docker-compose orchestration + README

**Files:**
- Modify: `docker-compose.yml` (add simulator + dashboard services)
- Create: `room-simulator/Dockerfile`
- Create: `dashboard/Dockerfile`
- Create: `README.md`

**Interfaces:**
- Consumes: built jars from both Spring Boot modules.
- Produces: `docker compose up` brings up broker + simulator + dashboard; dashboard reachable at `http://localhost:8080`. README documents run steps and the MQTT-concept mapping (spec §5).

- [ ] **Step 1: Create `room-simulator/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/room-simulator-0.1.0.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```

- [ ] **Step 2: Create `dashboard/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/dashboard-0.1.0.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```

- [ ] **Step 3: Extend `docker-compose.yml`**

```yaml
services:
  broker:
    image: eclipse-mosquitto:2
    ports:
      - "1883:1883"
    volumes:
      - ./broker/mosquitto.conf:/mosquitto/config/mosquitto.conf:ro

  room-simulator:
    build: ./room-simulator
    environment:
      BROKER_URL: tcp://broker:1883
    depends_on:
      - broker

  dashboard:
    build: ./dashboard
    ports:
      - "8080:8080"
    environment:
      BROKER_URL: tcp://broker:1883
    depends_on:
      - broker
```

- [ ] **Step 4: Create `README.md`**

````markdown
# Smart Hotel Room Controller (MQTT + Spring Boot)

Hardware-free demo of a hotel room-control system over MQTT. Simulated rooms
publish telemetry/state to a Mosquitto broker; a Spring Boot dashboard shows
all rooms live in the browser and sends commands back.

## Run it

```bash
mvn -q -DskipTests package
docker compose up --build
# open http://localhost:8080
```

- Toggle a room's Light/AC/DND from the dashboard → the simulated room reacts.
- Stop the `room-simulator` container → rooms show `offline` (MQTT Last Will).

## Architecture

- `common` — topic helpers + JSON payload records
- `room-simulator` (Spring Boot, Eclipse Paho) — one MQTT connection per room
  so each room has its own Last-Will; publishes telemetry/state, subscribes to commands
- `dashboard` (Spring Boot, Spring Integration MQTT + STOMP) — subscribes
  `hotel/#`, keeps in-memory state, pushes updates to the browser

## MQTT concepts demonstrated

| Concept | Where |
|---|---|
| Topic hierarchy + `#` wildcard | `hotel/{floor}/{room}/...`; dashboard subscribes `hotel/#` |
| QoS 1 | telemetry + commands |
| Retained messages | room state & availability — a fresh dashboard sees last-known state instantly |
| Last Will & Testament | a crashed room is auto-announced `offline` |
| Spring Integration MQTT | dashboard inbound/outbound adapters |

## Tests

```bash
mvn test   # unit + Testcontainers integration (needs Docker)
```
````

- [ ] **Step 5: Verify the whole stack from clean**

Run:
```bash
mvn -q -DskipTests package
docker compose up --build -d
sleep 15
curl -s http://localhost:8080/api/rooms
```
Expected: JSON array of rooms with temperatures/availability. Then `docker compose down`.

- [ ] **Step 6: Run the complete test suite**

Run: `mvn test`
Expected: BUILD SUCCESS, all modules' tests green (Docker required).

- [ ] **Step 7: Commit**

```bash
git add docker-compose.yml room-simulator/Dockerfile dashboard/Dockerfile README.md
git commit -m "chore: full docker-compose orchestration and project README"
```

---

## Self-Review Notes

- **Spec coverage:** broker/Docker (Task 1, 10) · topic scheme §4 (Task 2) · simulator with per-room LWT + retained + QoS + commands §3 (Tasks 3–6) · dashboard subscribe `hotel/#` + in-memory state + STOMP + commands §3 (Tasks 7–9) · MQTT-concept mapping §5 (README, Task 10) · Testcontainers integration tests §7 (Tasks 3–8) · web UI §3/§6 (Task 9) · `docker compose up` success criterion §2 (Task 10). Out-of-scope items (DB, auth, fancy UI) intentionally absent. TLS/auth stretch goal not planned (optional per spec §8).
- **Type consistency:** `RoomId(floor, room)`, `Topics.*` signatures, payload records, `Json.toBytes/read`, `RoomView` fields, `RoomStore.apply`, `CommandPublisher.send`, and the `mqtt_topic` header used by the outbound handler are consistent across tasks.
- **Placeholders:** none — every code step contains full code.
