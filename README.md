# Robotic Factory Simulator (RFS) - Distributed Architecture

**Course:** 4SLR01 - Object Oriented Software Engineering / Distributed Systems  

## Project Overview

This project evolves a monolithic Robotic Factory Simulator (RFS) into a distributed, concurrent, and event-driven system. The application simulates autonomous robots moving within a factory floor, managing collision avoidance, pathfinding, and object transportation.

The architecture was refactored in four stages to demonstrate specific distributed systems concepts:

1. **Concurrency:** Multi-threaded component behavior with synchronization
2. **Persistence Layer:** Custom TCP/IP server for remote model storage
3. **Service Oriented Architecture:** Spring Boot microservice for remote simulation execution
4. **Event Streaming:** Asynchronous state propagation using Apache Kafka

---

## Architecture & Implementation Details

### 1. Concurrent Programming

**Goal:** Transition from a sequential loop to independent threads for each factory component.

#### Implementation & Challenges

**Thread Management:**  
The `Component` class was modified to implement `Runnable`. The `run()` method executes the `behave()` logic in a loop with a 100ms sleep cycle. The `Factory` class launches these threads upon simulation start using standard Java Threads.

**Synchronization:**  
To prevent race conditions where multiple robots occupy the same coordinate, the `Factory.moveComponent()` method acts as a monitor (using the `synchronized` keyword). This ensures atomic updates to the factory grid state.

**Livelock Resolution:**  
A critical challenge was "Livelocks," where two robots facing each other would indefinitely wait for the other to move.

- **Detection:** Implemented a mechanism in `Robot.java` that tracks consecutive failed moves using a `blockedMoveCounter`
- **Resolution:** When the counter exceeds a threshold (5 attempts), the robot performs a tie-break check using lexicographical comparison of robot names. The "loser" of the tie-break attempts to move to a random free neighboring position (`findRandomFreeNeighbouringPosition`), breaking the symmetry and resolving the lock

### 2. Custom Persistence Protocol (Sockets)

**Goal:** Implement a remote file server using raw Java Sockets instead of local disk I/O.

#### Implementation & Challenges

**Protocol Design:**  
A custom binary protocol was implemented in `FactoryPersistenceServer`:

- **LIST:** String command triggering a directory scan
- **String (Filename):** Triggers a read operation returning a `Factory` object
- **Factory (Object):** Triggers a write operation to disk

**Serialization Issues:**  
The original model contained references to non-serializable Swing UI components (like `Stroke` or `Color` adapters). These fields were marked `transient`, and the model was refactored to ensure data integrity during transport.

**Statelessness:**  
The `RemoteFactoryPersistenceManager` creates a new socket connection for every request. While this introduces overhead compared to a persistent connection, it ensures the server remains stateless and simplifies error handling on the client side.

### 3. Simulation Microservice (REST API)

**Goal:** Offload the simulation computation to a dedicated Spring Boot microservice.

#### Implementation & Challenges

**Polymorphic Deserialization:**  
The `Factory` contains a list of abstract `Component` objects. Standard JSON deserialization fails to instantiate specific subclasses (Robot, Machine, etc.).

- **Solution:** Configured a custom Jackson `ObjectMapper` in `SimulationRegisterModuleConfig` using `PolymorphicTypeValidator`. This embeds type information (e.g., `@class`) in the JSON, allowing the receiver to reconstruct exact subclasses

**Circular Dependencies:**  
The model contains bidirectional relationships (Factory references Components, Components reference Factory).

- **Solution:** Applied `@JsonManagedReference` and `@JsonBackReference` annotations to break the recursion loop during serialization

**State Management:**  
The REST service is not purely stateless; it maintains an `activeSimulations` map to hold the state of running factories between HTTP requests (`/start` and `/run`).

### 4. Event-Driven Communication (Kafka)

**Goal:** Replace synchronous polling with asynchronous event streaming for real-time visualization.

#### Implementation & Challenges

**Notifier Pattern:**  
The Observer pattern was decoupled. `KafkaFactoryModelChangeNotifier` was created to intercept model changes and publish the entire `Factory` state to a Kafka topic (`simulation-{uuid}`).

**Swing Threading Issues:**  
The Kafka `KafkaConsumer` is blocking and cannot run on the UI thread.

- **Solution:** `FactorySimulationEventConsumer` runs in a dedicated background thread. To update the UI safely, `SwingUtilities.invokeLater()` is used to marshal the update payload back to the Event Dispatch Thread (EDT), preventing Swing freezing or rendering artifacts

**Topic Management:**  
To prevent "ghost" replays when restarting a simulation, the `SimulationServiceController` explicitly deletes the specific simulation topic upon a `/reset` call using the Kafka `AdminClient`.

---

## Project Structure

```
fr.tp.inf112.projects.robotsim.model    - Core business logic (Factory, Robot, Shapes)
fr.tp.inf112.projects.robotsim.app      - Swing Client Application & Controllers
fr.tp.inf112.projects.robotsim.server   - Raw Socket Persistence Server
fr.tp.slr201.projects.robotsim.service  - Spring Boot Simulation Microservice
```

---

## Prerequisites

- Java 11 or higher
- Maven
- Docker & Docker Compose (for Kafka/Zookeeper infrastructure)

---

## Installation and Execution Order

The system consists of four distinct parts that must be started in a specific order.

### 1. Start Infrastructure

Navigate to the project root and start the message broker:

```bash
docker-compose up -d
```

Wait for Kafka (port 9092) and Zookeeper (port 22181) to initialize.

### 2. Start Persistence Server

Runs the raw socket server for file storage.

- **Class:** `fr.tp.inf112.projects.robotsim.server.FactoryPersistenceServer`
- **Port:** 8090
- **Note:** Creates a `server_factories` directory in the execution context

### 3. Start Simulation Microservice

Runs the Spring Boot "brain" of the simulation.

- **Class:** `fr.tp.slr201.projects.robotsim.service.SimulationServiceApplication`
- **Port:** 8080
- **Note:** Automatically connects to the Persistence Server and Kafka Broker

### 4. Start Client Application

Runs the GUI visualization.

- **Class:** `fr.tp.inf112.projects.robotsim.app.SimulatorApplication`
- **Arguments:** Accepts `[host] [port]` for the persistence server (defaults to `localhost:8090`)

---

## Testing

A unified test runner is provided in `TestRunner.java`. This custom runner executes the JUnit test classes and provides a summarized output.

### Running the Tests

Execute the `TestRunner` class as a standard Java application:

```bash
java fr.tp.inf112.projects.robotsim.app.test.TestRunner
```

### Test Phases

#### Phase 1: Unit Tests

- **Class:** `RemoteSimulatorControllerTest`
- **Scope:** Validates the logic of the client-side controllers and models in isolation
- **Requirements:** No external servers required

#### Phase 2: Integration Tests

- **Class:** `IntegrationTest`
- **Scope:** Validates the full distributed stack
- **Requirements:** Requires `FactoryPersistenceServer` (8090) and the Microservice (8080) to be running

**Coverage:**

- Server Connectivity Check
- `persist()`: Saving a factory model to the remote server
- `read()`: Loading a factory model from the remote server
- `listFactoryFiles()`: Verifying file listings
- Error handling for non-existent files
