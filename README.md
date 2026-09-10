# Smart Hotel Room Controller — MQTT + Spring Boot

A hardware-free demo of a hotel room-control system built around **MQTT**. Simulated
hotel rooms publish their telemetry and state to an MQTT broker; a Spring Boot
front-desk **dashboard** subscribes, keeps live room state, and sends commands back to
the rooms — all runnable on a laptop with **no physical devices and no Docker required
for the tests**.

> Portfolio project focused on demonstrating the MQTT concepts a "Java + Spring + MQTT"
> role cares about: topic design, QoS, retained messages, and Last-Will & Testament.

---

## Architecture

```
┌─────────────────┐         ┌──────────────┐         ┌──────────────────┐
│ room-simulator  │  MQTT   │   MQTT       │  MQTT   │    dashboard     │
│ (Spring Boot)   │────────▶│   broker     │◀────────│  (Spring Boot)   │
│  N virtual rooms│  pub/sub │ (Mosquitto)  │ pub/sub │  + WebSocket→web │
└─────────────────┘         └──────────────┘         └──────────────────┘
                                                              │
                                                        browser (front desk)
```

- **`common`** — shared MQTT topic helpers + JSON payload records.
- **`room-simulator`** (Spring Boot + Eclipse Paho) — spins up N virtual rooms. Each room
  holds **its own MQTT connection** so it gets its **own Last-Will**; it publishes
  telemetry/state and subscribes to commands.
- **`dashboard`** (Spring Boot + Spring Integration MQTT + STOMP/WebSocket) — subscribes to
  `hotel/#`, keeps in-memory room state, and pushes live updates to the browser.

---

## MQTT concepts demonstrated

| Concept | Where it shows up |
|---|---|
| Topic hierarchy + `#` wildcard | `hotel/{floor}/{room}/...`; dashboard subscribes to `hotel/#` |
| QoS 1 | telemetry + all commands |
| **Retained messages** | room state & availability — a freshly-connected dashboard sees the last-known state instantly |
| **Last Will & Testament (LWT)** | a crashed room is auto-announced `offline` by the broker |
| Spring Integration MQTT | dashboard inbound/outbound channel adapters |

### Topic scheme

```
hotel/{floor}/{room}/telemetry/temperature   retained=false  QoS1   (simulator → )
hotel/{floor}/{room}/state/light|ac|dnd       retained=true   QoS1   (simulator → )
hotel/{floor}/{room}/availability             retained=true   LWT    (online/offline)
hotel/{floor}/{room}/cmd/light|ac|dnd         QoS1                   ( → simulator)
```

---

## Tech stack

- Java 21, Spring Boot 3.5.x
- Eclipse Paho (simulator) & Spring Integration MQTT (dashboard)
- Spring WebSocket + STOMP for the live browser view
- JUnit 5 + an **in-process [Moquette](https://github.com/moquette-io/moquette) broker** for
  integration tests (real MQTT, no Docker)
- Maven multi-module build

---

## Running the tests (no broker/Docker needed)

```bash
mvn test
```

Integration tests start a real MQTT broker **in-process** on a random port and verify
pub/sub, retained delivery, and LWT behaviour end-to-end.

## Running it live

**Option A — Docker**

```bash
mvn -q -DskipTests package
docker compose up --build
# open http://localhost:8080
```

**Option B — local Mosquitto (no Docker)**

```bash
brew install mosquitto && brew services start mosquitto   # broker on localhost:1883
mvn -q -DskipTests package
mvn -pl room-simulator spring-boot:run &
mvn -pl dashboard spring-boot:run &
# open http://localhost:8080
```

Then: toggle a room's Light/AC/DND from the dashboard and watch the room react; stop the
`room-simulator` and watch its rooms flip to `offline` (that's the MQTT Last-Will firing).

---

## Simulate a guest operating a switch (room-initiated change)

The dashboard controls rooms by sending commands (`cmd/*`). To also demonstrate the
*reverse* direction — a room changing its own state, as if a guest flipped a physical
switch — the simulator exposes an endpoint. The room updates its own state and reports it
via `state/*`, which the dashboard then reflects (no dashboard action needed):

```bash
# turn room 201's light off, initiated by the room itself
curl -X POST http://localhost:8070/api/guest/floor2/room201/light \
  -H 'Content-Type: application/json' -d '{"on":false}'
```

Path is `/api/guest/{floor}/{room}/{device}` with body `{"on": <bool>}`, device ∈
`light|ac|dnd`. The simulator's web port defaults to `8070` (`SIMULATOR_PORT`).

## Project status

Built with a spec-first, test-driven workflow (design → plan → task-by-task implementation
with review). The project is **complete**:

- ✅ Multi-module scaffold + broker config
- ✅ `common`: topic helpers, payload records, JSON
- ✅ `room-simulator`: per-room MQTT connection with retained availability + Last-Will,
  telemetry/state publishing, command handling, N-room app wiring — packaged as an
  executable jar
- ✅ `dashboard`: MQTT ingest, in-memory store, WebSocket/STOMP push, command endpoint,
  web UI — packaged as an executable jar
- ✅ Docker Compose orchestration (`broker` + `room-simulator` + `dashboard`)

Full test suite (unit + integration, in-process Moquette broker, no Docker) is green
across all modules.

Design and implementation plan live under [`docs/superpowers/`](docs/superpowers/).

---

## License

MIT (or your preference).
