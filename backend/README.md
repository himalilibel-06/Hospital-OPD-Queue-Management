# SmartOPD – Java OOP Backend Core & Persistence Layer

Pure Java 17 core engine and SQLite write-through persistence layer implementing the explainable, fairness-aware Outpatient Department (OPD) queue management system for SmartOPD.

This backend is built strictly using **pure JDK 17, SQLite JDBC, and JUnit 5** with zero heavy frameworks (no Spring, no Hibernate, no JPA). It serves as the single source of truth for college Object-Oriented Programming (OOP) project demonstrations and viva defense.

---

## 🛠️ Prerequisites

1. **Java Development Kit (JDK):** Version 17 or newer (JDK 17, 21, or 22+).
2. **Apache Maven:** Version 3.8+ or 3.9+.
3. **Environment Setup:** Ensure `JAVA_HOME` and `PATH` are set properly in your operating system. After installing JDK or Maven, open a **new terminal window** so environment variables take effect.

---

## 🚀 How to Build and Run Tests

Run the following command from the `backend/` directory:

```bash
mvn clean test
```

Expected output:
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.smartopd.DaoRoundTripAndConstraintsTest (5 tests)
[INFO] Running com.smartopd.QueueEngineTest (5 tests)
[INFO] Running com.smartopd.QueueServiceTransactionAndRestartTest (4 tests)
[INFO] Running com.smartopd.RestApiIntegrationTest (38 tests)
[INFO] Running com.smartopd.SchemaAndSeedPersistenceTest (4 tests)
[INFO] Running com.smartopd.ScoringParityGoldenTest (8 tests)
[INFO] Running com.smartopd.SettingsValidationTest (2 tests)
[INFO] Running com.smartopd.SimulationReportTest (1 test)
[INFO] Running com.smartopd.SimulatorParityTest (14 tests)
[INFO] Running com.smartopd.TokenAndFactoryTest (2 tests)
[INFO] Running com.smartopd.WaitTimeEstimatorTest (1 test)
[INFO] 
[INFO] Results:
[INFO] Tests run: 84, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 📦 How to Build the Self-Contained Executable JAR

Run from the `backend/` directory:
```bash
# Package with tests:
mvn clean package

# Package quickly skipping tests:
mvn package -DskipTests
```
This produces `target/smartopd.jar` (approx. 33.6 MB). The frontend files from `../frontend` are automatically bundled into `BOOT-INF/classes/static/` at build time (excluding test scripts).

To run the JAR anywhere:
```bash
java -jar target/smartopd.jar
```

### Stopping the Server & Port 8080 Conflicts
- **Stop Server:** Press `Ctrl+C` in your terminal.
- **Port 8080 in use:** If port 8080 is blocked by a dangling process, free it via PowerShell:
  ```powershell
  Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force }
  ```

---

## 💾 Database Location & SQLite Architecture

- **Default File Location:** `data/smartopd.db` relative to the working directory (`backend/data/smartopd.db` when run from `backend/`).
- **Custom Location Property:** Set JVM system property `-Dsmartopd.db.path=/path/to/database.db` or environment variable `SMARTOPD_DB_PATH`.
- **SQLite Single-Writer Concurrency:** SQLite allows concurrent readers via Write-Ahead Logging (`PRAGMA journal_mode=WAL`), but serializes write locks. `QueueService` uses a fair `ReentrantLock` to serialize all mutating calls into short ACID transactions, guaranteeing zero `SQLITE_BUSY` contention even under heavy concurrent multi-threading.

---

## 🏗️ System Layering Architecture

```
+---------------------------------------------------------------------------------+
|                                 CLIENT LAYER                                    |
|   (Front Desk Receptionist, OPD Doctor, Guest Patient Tracking, JUnit Tests)    |
+---------------------------------------------------------------------------------+
                                      │
                                      ▼
+---------------------------------------------------------------------------------+
|                                SERVICE LAYER                                    |
|   com.smartopd.service.QueueService (Write-Through Facade, ReentrantLock Mutex) |
|   - Concurrency serialization (SQLite single-writer model)                      |
|   - Transactional unit of work execution & automatic state rollback             |
|   - PBKDF2 authentication via PasswordHasher                                    |
+---------------------------------------------------------------------------------+
               │                                                 │
               ▼                                                 ▼
+---------------------------------------+    +------------------------------------+
|            CORE ENGINE LAYER          |    |          PERSISTENCE LAYER         |
|  com.smartopd.queue.QueueManager      |    |  com.smartopd.dao.TransactionHelper|
|  - DoctorQueue (Priority Ordering)    |    |  com.smartopd.dao.ConnectionProvide|
|  - SmartOpdStrategy (Dynamic Dispatch)|    |  com.smartopd.dao.SchemaInitializer|
|  - WaitTimeEstimator, SimulatedClock  |    |  com.smartopd.dao.DefaultsInitializ|
|  - EventLog (Audit Memory Window)     |    |                                    |
+---------------------------------------+    |  DAO Interfaces (com.smartopd.dao) |
                                             |  - DepartmentDao, DoctorDao        |
                                             |  - PatientDao, TokenSequenceDao    |
                                             |  - EventDao, SettingsDao           |
                                             |  - AppStateDao, UserDao            |
                                             |                                    |
                                             |  JDBC Implementations              |
                                             |  (com.smartopd.dao.jdbc)           |
                                             |  - Parameterized PreparedStatements|
                                             +------------------------------------+
                                                                 │
                                                                 ▼
                                             +------------------------------------+
                                             |          DATABASE LAYER            |
                                             |  SQLite 3 (WAL mode, FK enabled)   |
                                             |  File: backend/data/smartopd.db    |
                                             +------------------------------------+
```

---

## 🗄️ Relational Schema (ER Table Definitions)

All tables are created idempotently via `src/main/resources/schema.sql`:

1. **`department`**:
   - `code` (TEXT PRIMARY KEY): Department code (e.g. `GEN`, `CARD`, `PED`, `ORTH`, `ENT`).
   - `name` (TEXT UNIQUE NOT NULL): Full clinical department name.

2. **`doctor`**:
   - `id` (TEXT PRIMARY KEY): Doctor identifier (e.g. `DOC-101`).
   - `name` (TEXT NOT NULL): Doctor's full name.
   - `department_code` (TEXT NOT NULL, FK -> `department.code`).
   - `room` (TEXT NOT NULL): Consultation room number.
   - `start_time` / `end_time` (TEXT NOT NULL): Working hours (e.g. `09:00`, `16:00`).
   - `avg_consult_minutes` (INTEGER NOT NULL): Smoothed running average consultation duration.
   - `emergency_buffer_slots` (INTEGER NOT NULL): Reserved buffer slots.
   - `status` (TEXT NOT NULL): Availability (`AVAILABLE`, `ON_BREAK`, `OFF_DUTY`).
   - `age`, `gender`, `phone` (Demographics inherited from `Person`).

3. **`patient`**:
   - `id` (TEXT PRIMARY KEY): Patient identifier (e.g. `PAT-101`).
   - `token` (TEXT UNIQUE NOT NULL): Queue token (e.g. `CARD-E-002`).
   - `name`, `age`, `gender`, `phone`: Demographics.
   - `category` (TEXT NOT NULL): `EMERGENCY`, `SCHEDULED`, `WALK_IN`.
   - `severity` (INTEGER NOT NULL, CHECK: `severity >= 1 AND severity <= 5`).
   - `doctor_id` (TEXT NOT NULL, FK -> `doctor.id`).
   - `registered_at` (TEXT NOT NULL): ISO-8601 registration timestamp.
   - `appointment_time` (TEXT NULL): Slot for scheduled appointments (e.g. `10:30`).
   - `chief_complaint` (TEXT): Clinical intake complaint.
   - `status` (TEXT NOT NULL): `WAITING`, `IN_CONSULTATION`, `COMPLETED`, `NO_SHOW`, `SKIPPED`.
   - `skipped` (INTEGER NOT NULL DEFAULT 0, CHECK: `skipped IN (0, 1)`).
   - `consult_start`, `consult_end` (TEXT NULL): Timestamps.

4. **`token_sequence`**:
   - `department_code` (TEXT NOT NULL), `category_code` (TEXT NOT NULL).
   - `last_value` (INTEGER NOT NULL).
   - `PRIMARY KEY (department_code, category_code)`.

5. **`queue_event`**:
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT).
   - `timestamp` (TEXT NOT NULL), `token` (TEXT NOT NULL), `doctor_id` (TEXT NULL).
   - `type` (TEXT NOT NULL), `reason` (TEXT).

6. **`settings`**:
   - `key` (TEXT PRIMARY KEY), `value` (TEXT NOT NULL).
   - Stores scoring weights, threshold minutes, and emergency cap parameters.

7. **`app_state`**:
   - `key` (TEXT PRIMARY KEY), `value` (TEXT NOT NULL).
   - Stores simulated clock base timestamp, minute offset, and per-doctor consecutive emergency counters.

8. **`user_account`**:
   - `username` (TEXT PRIMARY KEY).
   - `password_hash` (TEXT NOT NULL): PBKDF2WithHmacSHA256 hex digest.
   - `salt` (TEXT NOT NULL): Cryptographically random 16-byte hex salt.
   - `role` (TEXT NOT NULL, CHECK: `role IN ('RECEPTIONIST', 'DOCTOR')`).
   - `display_name` (TEXT NOT NULL).
   - `doctor_id` (TEXT NULL, FK -> `doctor.id`).

Indexes:
- `idx_patient_doc_status` on `patient(doctor_id, status)`
- `idx_event_token` on `queue_event(token)`
- `idx_event_doc` on `queue_event(doctor_id)`

---

## 📁 Package Architecture

```
backend/
├── pom.xml                                      # Maven Java 17, SQLite JDBC & JUnit 5 configuration
├── .gitignore                                   # Build artifacts and data/ SQLite database ignore rules
├── README.md                                    # Comprehensive viva documentation and OOP concept mapping
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── schema.sql                       # Relational schema with CHECK constraints and indexes
    │   └── java/com/smartopd/
    │       ├── auth/                            # Cryptographic security
    │       │   └── PasswordHasher.java          # PBKDF2WithHmacSHA256 with per-user salt
    │       ├── config/
    │       │   └── Settings.java                # Central hospital scoring weights and validation
    │       ├── dao/                             # Data Access Object abstractions & transaction helper
    │       │   ├── ConnectionProvider.java      # SQLite connection factory with WAL and PRAGMAs
    │       │   ├── DataAccessException.java     # Unchecked domain persistence exception
    │       │   ├── TransactionHelper.java       # Functional Unit of Work coordinator
    │       │   ├── SchemaInitializer.java       # Idempotent DDL execution from schema.sql
    │       │   ├── DefaultsInitializer.java     # Default settings initialization if database is empty
    │       │   ├── DepartmentDao.java           # Department repository interface
    │       │   ├── DoctorDao.java               # Doctor repository interface
    │       │   ├── PatientDao.java              # Patient repository interface
    │       │   ├── TokenSequenceDao.java        # Atomic token sequence repository interface
    │       │   ├── EventDao.java                # Audit trail repository interface
    │       │   ├── SettingsDao.java             # Settings key-value repository interface
    │       │   ├── AppStateDao.java             # Application simulation state repository interface
    │       │   ├── UserDao.java                 # Staff user account repository interface
    │       │   └── jdbc/                        # Pure JDBC PreparedStatement implementations
    │       │       ├── JdbcDepartmentDao.java
    │       │       ├── JdbcDoctorDao.java
    │       │       ├── JdbcPatientDao.java
    │       │       ├── JdbcTokenSequenceDao.java
    │       │       ├── JdbcEventDao.java
    │       │       ├── JdbcSettingsDao.java
    │       │       ├── JdbcAppStateDao.java
    │       │       └── JdbcUserDao.java
    │       ├── events/                          # Audit logging & event sourcing
    │       │   ├── QueueEvent.java              # Immutable event record
    │       │   └── EventLog.java                # Sliding window (last 100 events) and filtering
    │       ├── exception/                       # Custom unchecked exception hierarchy
    │       │   ├── SmartOpdException.java       # Common unchecked root exception
    │       │   ├── InvalidSeverityException.java
    │       │   ├── DoctorUnavailableException.java
    │       │   ├── DuplicateTokenException.java
    │       │   ├── SlotFullException.java
    │       │   └── ValidationException.java
    │       ├── model/                           # Domain entities & inheritance hierarchy
    │       │   ├── Person.java                  # Abstract base person (encapsulation & validation)
    │       │   ├── Patient.java                 # Abstract patient declaring polymorphic methods
    │       │   ├── EmergencyPatient.java        # Concrete emergency subclass (Sev 4-5 triage)
    │       │   ├── ScheduledPatient.java        # Concrete scheduled appointment subclass
    │       │   ├── WalkInPatient.java           # Concrete routine walk-in subclass
    │       │   ├── Doctor.java                  # Clinical provider entity
    │       │   ├── Department.java              # OPD department entity
    │       │   ├── UserAccount.java             # Staff account entity
    │       │   ├── Token.java                   # Immutable final token value object (CARD-E-014)
    │       │   ├── TokenGenerator.java          # Thread-safe sequence generator
    │       │   ├── Category.java                # Typesafe enum (Emergency, Scheduled, Walk-in)
    │       │   ├── PatientStatus.java           # Lifecycle enum (WAITING, IN_CONSULTATION, etc.)
    │       │   ├── DoctorStatus.java            # Availability enum (AVAILABLE, ON_BREAK, OFF_DUTY)
    │       │   ├── EventType.java               # Audit transition enum
    │       │   ├── PatientRegistrationRequest.java # DTO parameter object
    │       │   └── PatientFactory.java          # Factory Method pattern for validated creation & row hydration
    │       ├── queue/                           # OPD Queue coordination & estimations
    │       │   ├── DoctorQueue.java             # Doctor queue with emergency cap rule & lifecycle
    │       │   ├── EmergencyCapSelector.java    # Reusable emergency cap selector shared with Simulator
    │       │   ├── WaitTimeEstimator.java       # Estimated consultation start time calculation
    │       │   ├── WaitEstimate.java            # Wait estimate value object (handles doctor pauses)
    │       │   ├── RegistrationResult.java      # Encapsulates patient, token, wait, and explanation
    │       │   └── QueueManager.java            # Central facade coordinator & position-change detection
    │       ├── scoring/                         # Strategy Pattern for triage policies
    │       │   ├── PriorityStrategy.java        # Strategy interface (score, isFairnessBoosted, explain)
    │       │   ├── SmartOpdStrategy.java        # Fairness-aware priority aging + emergency override
    │       │   ├── StrictPriorityStrategy.java  # Pure severity priority without anti-starvation
    │       │   └── FcfsStrategy.java            # Baseline first-come, first-served
    │       ├── service/                         # Write-through transactional orchestration
    │       │   ├── QueueService.java            # Write-through facade with ReentrantLock mutex
    │       │   └── AuthResult.java              # Authentication DTO
    │       ├── simulation/                      # Pure Java Discrete-Event Simulation Engine
    │       │   ├── SimulationConfig.java        # Config record with validation, presets & offered load
    │       │   ├── SeededRandom.java            # Mulberry32 32-bit PRNG exact bitwise port
    │       │   ├── SimulatedPatient.java        # Patient extension for synthetic cohort generation
    │       │   ├── PolicyMetrics.java           # Immutable per-policy simulation metrics
    │       │   ├── PatientWaitRecord.java       # Chart data points (arrival, consult, wait minutes)
    │       │   ├── PolicySimulationResult.java  # Per-policy metrics + chart points
    │       │   ├── SyntheticPatientDto.java     # Patient summary for UI table inspection
    │       │   ├── SimulationResult.java        # Aggregated 3-policy result + dynamic insights
    │       │   └── Simulator.java               # Multi-doctor discrete-event simulation engine
    │       ├── time/                            # Decoupled time management (Dependency Inversion)
    │       │   ├── AppClock.java                # Clock interface
    │       │   ├── SystemAppClock.java          # Real hardware clock
    │       │   └── SimulatedClock.java          # Controllable clock with advance() and reset()
    │       └── api/                             # Spring Boot REST API & Security
    │           ├── controller/                  # Public, Auth, Patient, Doctor, Settings, Simulation
    │           ├── dto/                         # Strongly typed Request / Response DTOs
    │           ├── interceptor/                 # RBAC AuthInterceptor & Per-IP RateLimitInterceptor
    │           └── security/                    # Custom @RequiresRole authorization annotations
    └── test/java/com/smartopd/
        ├── QueueEngineTest.java                 # Core Tests: Override, boost, cap rule, no-show, FIFO
        ├── SettingsValidationTest.java          # Core Tests: Settings bounds and resetToDefaults
        ├── TokenAndFactoryTest.java             # Core Tests: Token format & PatientFactory validation
        ├── WaitTimeEstimatorTest.java           # Core Tests: Wait estimation & Paused doctor handling
        ├── ScoringParityGoldenTest.java         # Core Tests: Bit-exact parity with frontend/js/scoring.js
        ├── SchemaAndSeedPersistenceTest.java    # Persistence Tests 1-2: Schema idempotency & Seeding
        ├── DaoRoundTripAndConstraintsTest.java  # Persistence Tests 3-6, 10: DAOs, FK, CHECK, Injection, Auth
        ├── QueueServiceTransactionAndRestartTest.java # Persistence Tests 7-9, 11: Rollback, Restart, Concurrency
        ├── RestApiIntegrationTest.java          # Spring Boot REST API: Public, Auth, RBAC & Doctor endpoints (38 checks)
        └── SimulatorParityTest.java             # Pure Java Simulator: 9 golden runs, determinism & performance (13 checks)
```

---

## 🎓 OOP Concepts to Class Mapping (Viva Defense Guide)

The table below maps core Object-Oriented Programming and design patterns to their concrete demonstration in the SmartOPD codebase:

| OOP Concept | Target Class / Interface | Explanation & Viva Talking Point |
| :--- | :--- | :--- |
| **Abstraction** | `Person.java`, `Patient.java`, `PriorityStrategy.java`, `AppClock.java`, `DepartmentDao.java` | Defines essential domain characteristics and behaviors while hiding low-level implementation details. Clients depend on abstract interfaces rather than concrete classes. |
| **Inheritance** | `Patient extends Person`<br>`Doctor extends Person`<br>`EmergencyPatient extends Patient`<br>`ScheduledPatient extends Patient`<br>`WalkInPatient extends Patient` | Establishes an *"is-a"* relationship where common demographic fields and validations are inherited from `Person`, while `EmergencyPatient`, `ScheduledPatient`, and `WalkInPatient` reuse and extend `Patient`. |
| **Polymorphism** | `patient.urgencyBonus(now)`<br>`patient.fairnessThresholdMinutes(settings)`<br>`strategy.calculateScore(...)` | Dynamic method dispatch: calling `patient.urgencyBonus(now)` executes subclass specialization (`ScheduledPatient` computes appointment proximity bonus, while others return 0.0), and `patient.fairnessThresholdMinutes(settings)` returns category-specific thresholds without `if/else` checks. |
| **Encapsulation** | `Person.java`, `Settings.java`, `DoctorQueue.java`, `Token.java`, `UserAccount.java` | All fields are strictly private with getters and mutators enforcing invariant validation (e.g. 10-digit mobile phone, severity between 1 and 5, age 1–130). |
| **Interface** | `PriorityStrategy.java`, `AppClock.java`, `PatientDao.java`, `DoctorDao.java` | Contract decoupling following the Dependency Inversion Principle (DIP). The service layer depends on DAO and Strategy abstractions. |
| **Strategy Pattern** | `PriorityStrategy.java`<br>`SmartOpdStrategy.java`<br>`StrictPriorityStrategy.java`<br>`FcfsStrategy.java`<br>`EmergencyCapSelector.java` | Behavioral design pattern allowing hospital triage policies to be swapped dynamically at runtime. **Crucially, the exact same strategy implementations and emergency cap selector are reused across BOTH the live clinical OPD queue (`DoctorQueue.java`) and the discrete-event simulation engine (`Simulator.java`)**, eliminating code duplication and ensuring 100% mathematical consistency. |
| **Factory Pattern** | `PatientFactory.java` | Creational design pattern encapsulating patient subclass creation and database row reconstruction while enforcing clinical triage constraints. |
| **Repository / DAO Pattern** | `DepartmentDao`, `DoctorDao`, `PatientDao`, `TokenSequenceDao`, `EventDao`, `SettingsDao`, `AppStateDao`, `UserDao` | Separates domain logic from low-level JDBC SQL access. Guarantees that swapping storage engines does not require touching business logic. |
| **Provider Pattern** | `ConnectionProvider.java` | Manages SQLite connection pooling, path resolution, WAL journal mode, and foreign key activation in a central reusable component. |
| **Command / Unit of Work** | `TransactionHelper.java` | Coordinates atomic transactional blocks through functional callback interfaces (`TransactionCallback<T>`), guaranteeing automatic commit and fail-safe rollback. |
| **Cryptographic Security** | `PasswordHasher.java` | Demonstrates defense in depth by salting and hashing credentials using PBKDF2WithHmacSHA256 (120,000 iterations) with constant-time equality checks. |
| **Exception Hierarchy** | `SmartOpdException.java`<br>`DataAccessException.java`<br>`InvalidSeverityException.java`<br>`DoctorUnavailableException.java`<br>`ValidationException.java` | Domain-specific unchecked exception hierarchy providing clean, fail-fast error handling with human-readable diagnostic messages. |
| **Collections Framework** | `DoctorQueue.java`<br>`QueueManager.java`<br>`EventLog.java` | Demonstrates usage of `List`, `Map`, `Set`, `ArrayList`, thread-safe `ConcurrentHashMap` and `CopyOnWriteArrayList`, Java Streams, and custom `Comparator` sorting. |
| **Immutability & Value Objects** | `Token.java`<br>`QueueEvent.java`<br>`WaitEstimate.java`<br>`AuthResult.java`<br>`PolicyMetrics.java`<br>`SimulationConfig.java` | Final immutable classes and records ensuring thread-safety, predictable hash map keys, audit log integrity, and thread-safe simulation runs. |

---

## 🧪 Comprehensive Automated Test Suite (84 Tests Passed • 100%)

All core, persistence, REST API, and simulation specification checks are fully automated and verified:

1. **Schema Idempotency:** Schema is created successfully; re-running `SchemaInitializer.initialize()` executes safely with no duplicate table errors.
2. **Database Seeding:** Automatically seeds 5 departments, 6 doctors, 12 patients, and 2 staff user accounts (`reception`, `doctor`); subsequent initializations safely skip seeding.
3. **DAO Round-Trip & Subclass Hydration:** Round-trips for doctors, settings, events, and patients; proves that `EmergencyPatient`, `ScheduledPatient`, and `WalkInPatient` are reconstructed as their exact Java subclasses.
4. **Foreign Key & CHECK Constraint Enforcement:** Rejects patient registration with an unknown `doctor_id`; rejects out-of-range clinical severities (0 and 6).
5. **Token Uniqueness:** Inserting duplicate queue tokens triggers a `DataAccessException` due to SQLite UNIQUE constraint enforcement.
6. **SQL Injection Defense:** Registering a patient named `"Robert'); DROP TABLE patient;--"` stores the payload verbatim as plain text via parameterized `PreparedStatement`s; the `patient` table remains intact.
7. **Transaction Rollback:** Forcing a failure mid-way through patient registration rolls back the transaction completely; leaves no patient record, no event record, no token sequence increment, and resets in-memory queues.
8. **System Restart Parity:** Registering patients and advancing the simulated clock 50 minutes persists all state; creating a brand new `QueueService` on the same database file restores identical queue order, tokens, events, settings, and clock offset, while continuing the token sequence.
9. **Consultation Lifecycle Across Restarts:** Calling next patient, completing consultation (with smoothed `avgConsultMinutes`), and marking no-shows survive restart cleanly (completed patients do not re-enter queue).
10. **PBKDF2 Password Authentication:** `authenticate()` succeeds for valid staff accounts and rejects incorrect passwords, confirming stored password digests never equal plaintext.
11. **Concurrency Without SQLITE_BUSY:** 8 threads concurrently register 40 patients simultaneously; all 40 registrations complete with zero lock contention errors, generating 40 unique, sequential tokens.
12. **Public Display Board & Zero PII Enforcement:** `GET /api/public/board` verifies zero patient names, phones, clinical complaints, or severity details leak into public waiting displays.
13. **Patient Tracker Self-Service:** `GET /api/public/tracker/{token}` returns estimated start time, queue position, patients ahead, and chronological timeline. Rate-limited to 30 req/min per IP.
14. **Role-Based Access Control (RBAC):** Missing or invalid tokens return 401; doctors attempting front-desk operations return 403 Forbidden.
15. **Cross-Doctor Ownership Isolation:** Interceptor strictly prevents a doctor from accessing or mutating another doctor's consultation queue.
16. **Session Invalidation on Logout:** Calling `POST /api/auth/logout` invalidates the bearer token, rejecting subsequent requests with 401 Unauthorized.
17. **Live Form Preview:** `POST /api/patients/preview` predicts queue slot, wait minutes, and explanation without writing to SQLite or advancing sequences.
18. **Core Invariant Preservation:** All original core tests (priority scoring, emergency cap, fairness boost, wait estimation) pass unchanged.
19. **Brute-Force Account Lockout:** 6th failed login attempt for a username triggers HTTP 429 Too Many Requests.
20. **Defensive Error Masking:** Error responses strictly return standardized JSON without leaking stack traces, `SQLException`, or SQL statements.
21. **Discrete-Event Simulation Parity (`SimulatorParityTest`):** 9 golden runs across Quiet Day, Busy Day, and Emergency Surge with seeds 1, 7, and 42 produce exact bit-level parity with JavaScript Mulberry32 calculations.
22. **Simulation Validation & Execution Limits:** Enforces patient cohort bounds (50–500), doctor capacity (1–6), 10-second timeout safeguard, and front-desk RBAC authorization on `POST /api/simulation/run`.
23. **Executable Specification Note:** `frontend/js/simulator.js` and `frontend/js/scoring.js` along with `run-tests.js` are retained in the repository as the authoritative executable specification against which backend implementations are verified.

---

## 📱 Mobile Phone Access on Same Wi-Fi (Patient Self-Service Tracker)

The application binds to `0.0.0.0:8080` (`server.address=0.0.0.0` in `application.properties`), enabling any mobile phone connected to the same local Wi-Fi network to track queues in real time.

### Steps to Access from a Mobile Phone:

1. **Find your host PC's IPv4 address:**
   Open PowerShell or Command Prompt on the host PC and run:
   ```powershell
   ipconfig
   ```
   Look for the `IPv4 Address` under your active Wi-Fi adapter (e.g., `192.168.1.45`).

2. **Allow Java through Windows Firewall:**
   - Open **Windows Defender Firewall** > **Allow an app or feature through Windows Defender Firewall**.
   - Ensure **Java(TM) Platform SE binary** or your active JDK `java.exe` is checked for **Private** networks.
   - Alternatively, execute in PowerShell as Administrator:
     ```powershell
     New-NetFirewallRule -DisplayName "SmartOPD Port 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
     ```

3. **Open the Patient Tracker on your phone:**
   Ensure your phone is connected to the same Wi-Fi network as the PC, then open your mobile browser:
   ```
   http://<YOUR_PC_IP>:8080/patient-tracker.html?guest=true
   ```
   Example:
   ```
   http://192.168.1.45:8080/patient-tracker.html?guest=true
   ```
   Patients can enter any token (e.g. `CARD-E-002`, `GEN-W-004`) to monitor their live OPD position and wait estimates from their own phone.

