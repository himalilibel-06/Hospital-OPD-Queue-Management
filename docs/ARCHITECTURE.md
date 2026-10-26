# SmartOPD – System Architecture & OOP Design Documentation

This document provides a comprehensive architectural blueprint of the SmartOPD Hospital Outpatient Department (OPD) Queue Management System, detailing its layered design, concurrency invariants, security controls, and object-oriented foundations.

---

## 1. Architectural Layers

SmartOPD is structured according to a strict multi-tiered **Layered Architecture** with unidirectional top-down dependencies:

1. **Browser Presentation Layer (HTML5, Vanilla CSS3, Modern JS):**
   - 10 static HTML pages with zero external dependencies (no Node, no React, no CDNs).
   - `api-client.js` executes asynchronous `fetch` requests with bearer token authorization.
   - `api-adapters.js` translates backend DTO records into UI view models.
2. **REST API Controller Layer (`com.smartopd.api.controller`):**
   - Exposes RESTful HTTP endpoints (`AuthController`, `DoctorController`, `PublicController`, `ReceptionController`, `SimulationController`).
   - Validates JSON request payloads (`@Valid`, Bean Validation) and maps domain responses to Java 17 records.
   - Declaratively secured via `AuthInterceptor` using `@RequiresRole({"RECEPTIONIST", "DOCTOR"})`.
3. **Application Service Layer (`com.smartopd.service.QueueService`):**
   - Implements the **Facade Pattern**, acting as the single orchestration hub for all clinical mutations and queries.
   - Serializes mutating transactions through a fair `ReentrantLock` to adhere to SQLite's single-writer concurrency model.
   - Implements **Write-Through Caching**: every queue modification is committed to SQLite before memory state is updated.
4. **In-Memory Domain Queue Engine (`com.smartopd.queue`, `com.smartopd.scoring`, `com.smartopd.model`):**
   - High-speed in-memory triage management via `QueueManager` and `DoctorQueue`.
   - Implements dynamic scoring, wait-time estimation (`WaitTimeEstimator`), and emergency cap selection (`EmergencyCapSelector`).
5. **Persistence & Data Access Layer (`com.smartopd.dao`, `com.smartopd.dao.jdbc`):**
   - Implements the **DAO (Data Access Object) Pattern** with pure JDBC (zero ORM overhead).
   - Manages connection lifecycle and SQLite PRAGMAs (`ConnectionProvider`) and atomic transactional rollback (`TransactionHelper`).
6. **Storage Layer (SQLite 3.4x):**
   - File-based relational database (`data/smartopd.db`) running with Write-Ahead Logging (`PRAGMA journal_mode = WAL`) and foreign key enforcement (`PRAGMA foreign_keys = ON`).

---

## 2. Mermaid Component Diagram

```mermaid
componentDiagram
    package "Frontend Client Layer" {
        [Public Pages: display-board, patient-tracker] as PublicUI
        [Staff Pages: reception-dashboard, queue-manager, doctor-console] as StaffUI
        [JavaScript API Client: api-client.js, api-adapters.js] as JSClient
    }

    package "Spring Boot API Layer" {
        [AuthInterceptor & SessionStore] as SecurityGuard
        [REST Controllers: Auth, Doctor, Public, Reception, Simulation] as Controllers
        [GlobalExceptionHandler] as ExceptionHandler
    }

    package "Service & Domain Layer" {
        [QueueService: Central Write-Through Facade] as QueueService
        [QueueManager & DoctorQueue] as QueueEngine
        [PriorityStrategy: FCFS, Strict, SmartOPD] as ScoringStrategy
        [EmergencyCapSelector & WaitTimeEstimator] as Algorithms
    }

    package "Persistence Layer" {
        [TransactionHelper & ConnectionProvider] as TxProvider
        [DAOs: PatientDao, DoctorDao, EventDao, SettingsDao, UserDao] as DAOs
    }

    database "Relational Storage" {
        [(SQLite Database: data/smartopd.db - WAL Mode)] as SQLite
    }

    PublicUI --> JSClient
    StaffUI --> JSClient
    JSClient --> SecurityGuard : HTTP /api/**
    SecurityGuard --> Controllers
    Controllers --> QueueService
    ExceptionHandler ..> Controllers : Masks Exceptions

    QueueService --> QueueEngine : Fast Lookups
    QueueService --> ScoringStrategy : Strategy Dispatch
    QueueEngine --> Algorithms : Emergency Cap & Waits
    QueueService --> TxProvider : Mutating Locks
    TxProvider --> DAOs : Atomic JDBC Execution
    DAOs --> SQLite : SQL / Relational Constraints
```

---

## 3. Threading & Consistency Model

### The Challenge of SQLite in Multi-Threaded Environments
SQLite utilizes file-level database locking. While Write-Ahead Logging (`PRAGMA journal_mode = WAL`) permits unlimited concurrent readers, **exactly one writer thread may mutate the database at any moment**. If two embedded Tomcat HTTP worker threads attempt simultaneous write transactions, the second thread blocks until SQLite throws an unhandled `SQLITE_BUSY` exception.

### SmartOPD Concurrency Invariant: Single Fair ReentrantLock
To guarantee ACID transaction isolation and eliminate `SQLITE_BUSY` errors entirely:
1. **Serialized Write Transactions:** In [QueueService.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/service/QueueService.java), all state-mutating operations (`registerPatient`, `callNext`, `completeConsultation`, `markNoShow`, `skipPatient`, `advanceClock`) acquire a shared, fair lock:
   ```java
   private final ReentrantLock writeLock = new ReentrantLock(true);
   ```
2. **Write-Through Atomicity:** Every mutation executes inside `transactionHelper.execute(conn -> { ... })` with `conn.setAutoCommit(false)`. If any table insert or update fails, `conn.rollback()` executes immediately.
3. **Fail-Safe In-Memory Recovery:** If an unhandled exception or constraint failure aborts an active transaction, `QueueService` catches the error and executes `rebuildDoctorQueueFromDb()`. This purges any partial in-memory state and reloads all queues, active consultations, and event logs directly from committed SQLite records, guaranteeing 100% memory-database synchronization.
4. **Non-Blocking Concurrent Reads:** Read-only operations (`previewPatient`, `getPublicBoardData`, `getPublicQueueSummary`) bypass the `writeLock` and execute lightweight uncommitted queries across WAL snapshots without blocking staff triage workflows.

---

## 4. Security & Privacy Architecture

- **Cryptographic Credential Storage:** Staff passwords are never stored in plaintext. [PasswordHasher.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/auth/PasswordHasher.java) salts and hashes passwords using PBKDF2WithHmacSHA256 with 120,000 iterations and 16-byte cryptographically secure random salts.
- **Stateless Bearer Tokens:** Upon authentication, [SessionStore.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/api/security/SessionStore.java) issues a 256-bit cryptographically random token (`SecureRandom` + Hex encoding) stored in memory with an 8-hour expiration window.
- **Role-Based Access Control (RBAC):** [AuthInterceptor.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/api/security/AuthInterceptor.java) validates incoming `Authorization: Bearer <token>` headers against endpoint `@RequiresRole` annotations. Doctors are strictly forbidden from performing front-desk triage registrations or accessing queues belonging to other doctors.
- **Brute-Force Account Lockout:** If 5 consecutive failed login attempts occur for any username within a 5-minute sliding window, `SessionStore` locks the account for 60 seconds, returning HTTP `429 Too Many Requests`.
- **Public Tracker Rate Limiting:** [PublicTrackerRateLimiter.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/api/security/PublicTrackerRateLimiter.java) enforces a sliding window limit of 30 requests per minute per client IP on `/api/public/tracker/**`, defending against automated enumeration of sequential patient tokens.
- **Privacy by Design (Strict Zero-PII Public Endpoints):** Public endpoints (`/api/public/board` and `/api/public/tracker/{token}`) strictly exclude full patient names, mobile phone numbers, ages, clinical chief complaints, and severity numbers. The mobile tracker exposes the patient's first name only for personal identification.

---

## 5. Where is Each OOP Concept Shown?

| OOP / Architectural Concept | Real Source File Path | Class & Method Names | Concrete Implementation Details |
| :--- | :--- | :--- | :--- |
| **Abstraction** | [Person.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/Person.java)<br>[Patient.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/Patient.java) | `Person`<br>`Patient`<br>`urgencyBonus()`<br>`fairnessThresholdMinutes()` | `Person` abstracts common human demographics (`id`, `name`, `age`, `gender`, `phone`). `Patient` declares abstract methods `urgencyBonus()` and `fairnessThresholdMinutes()` enforced across all patient categories. |
| **Inheritance** | [Doctor.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/Doctor.java)<br>[EmergencyPatient.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/EmergencyPatient.java)<br>[ScheduledPatient.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/ScheduledPatient.java)<br>[WalkInPatient.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/WalkInPatient.java)<br>[SimulatedPatient.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/simulation/SimulatedPatient.java) | `Doctor extends Person`<br>`EmergencyPatient extends Patient`<br>`ScheduledPatient extends Patient`<br>`WalkInPatient extends Patient`<br>`SimulatedPatient extends Patient` | Code reuse and class hierarchy specialization. `Doctor` inherits identity and validation from `Person`. Clinical subclasses extend `Patient` to specialize triage behaviors. |
| **Dynamic Polymorphism** | [SmartOpdStrategy.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/scoring/SmartOpdStrategy.java) | `calculateScore(Patient, ...)`<br>`isFairnessBoosted(Patient, ...)` | `SmartOpdStrategy` evaluates priority scores without `instanceof` checks by dynamically invoking overridden methods `patient.urgencyBonus(now)` and `patient.fairnessThresholdMinutes(settings)`. |
| **Encapsulation** | [Settings.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/config/Settings.java)<br>[Person.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/Person.java) | `Settings`<br>`setSeverityWeight()`<br>`setPhone()`<br>`setAge()` | Private fields with defensive validation invariants inside setters (e.g. phone must match 10 digits; severity weight must remain between 10 and 500). |
| **Interfaces (Decoupling)** | [PriorityStrategy.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/scoring/PriorityStrategy.java)<br>[AppClock.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/time/AppClock.java)<br>[PatientDao.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/dao/PatientDao.java) | `PriorityStrategy`<br>`AppClock`<br>`PatientDao` | Contracts adhering to the Dependency Inversion Principle (DIP). High-level services depend on abstractions, enabling mock clocks and swappable triage policies. |
| **Strategy Pattern** | [PriorityStrategy.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/scoring/PriorityStrategy.java)<br>[SmartOpdStrategy.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/scoring/SmartOpdStrategy.java)<br>[FcfsStrategy.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/scoring/FcfsStrategy.java)<br>[StrictPriorityStrategy.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/scoring/StrictPriorityStrategy.java) | `PriorityStrategy`<br>`FcfsStrategy`<br>`StrictPriorityStrategy`<br>`SmartOpdStrategy` | Behavioral design pattern allowing hospital triage policies to be interchanged at runtime in both live doctor queues (`DoctorQueue`) and the simulator (`Simulator`). |
| **Factory Method Pattern** | [PatientFactory.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/PatientFactory.java) | `PatientFactory.createPatient()` | Creational design pattern encapsulating patient subclass creation and database row reconstruction while enforcing triage constraints before instantiation. |
| **DAO / Repository Pattern** | [DoctorDao.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/dao/DoctorDao.java)<br>[JdbcDoctorDao.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/dao/jdbc/JdbcDoctorDao.java) | `DoctorDao`<br>`JdbcDoctorDao` | Decouples domain logic from SQL. All JDBC operations (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) are isolated inside dedicated DAO classes. |
| **DTO with Records** | [LoginResponse.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/api/dto/LoginResponse.java)<br>[PublicTrackerResponse.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/api/dto/PublicTrackerResponse.java) | `LoginResponse`<br>`PublicTrackerResponse` | Immutable data carriers using modern Java records to safely serialize HTTP responses and defend against PII leakage. |
| **Singleton Pattern** | [AppConfig.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/config/AppConfig.java) | `AppConfig.queueService()` | Spring `@Configuration` providing a single global singleton of `QueueService` coordinating the single-writer lock and persistent SQLite connection. |
| **Dependency Injection** | [ReceptionController.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/api/controller/ReceptionController.java)<br>[AuthController.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/api/controller/AuthController.java) | `ReceptionController(QueueService)`<br>`AuthController(QueueService, SessionStore)` | Inversion of Control (IoC) via constructor-based dependency injection managed by Spring's application context. |
| **Custom Exception Hierarchy** | [SmartOpdException.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/exception/SmartOpdException.java)<br>[ValidationException.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/exception/ValidationException.java)<br>[DoctorUnavailableException.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/exception/DoctorUnavailableException.java) | `SmartOpdException`<br>`ValidationException`<br>`DoctorUnavailableException` | Clean domain-specific unchecked exception hierarchy extending `RuntimeException` to handle triage and availability constraints gracefully. |
| **Collections Framework** | [DoctorQueue.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/queue/DoctorQueue.java)<br>[QueueManager.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/queue/QueueManager.java) | `DoctorQueue`<br>`QueueManager` | Extensive usage of `List<Patient>`, `Map<String, DoctorQueue>`, `Set<String>`, custom `Comparator<Patient>`, and functional Java Streams (`filter`, `map`, `sorted`). |
| **Immutability & Value Objects** | [Token.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/model/Token.java)<br>[QueueEvent.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/events/QueueEvent.java)<br>[WaitEstimate.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/queue/WaitEstimate.java) | `Token`<br>`QueueEvent`<br>`WaitEstimate` | Final classes with immutable fields ensuring thread safety, predictable hash map keys, and tamper-proof audit trails. |
| **Generics** | [TransactionHelper.java](file:///d:/PROJECTS/Hospital%20OPD%20Queue%20Management/Hospital-OPD-Queue-Management/backend/src/main/java/com/smartopd/dao/TransactionHelper.java) | `execute(TransactionCallback<T>)` | Type-safe transaction coordination returning arbitrary domain entities (`<T>`) without unsafe casting. |
