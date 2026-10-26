# SmartOPD – UML Class Diagrams

This document contains comprehensive UML class diagrams for the SmartOPD system, reverse-engineered directly from the production Java 17 source code under `backend/src/main/java/com/smartopd/`.

---

## 🧭 Diagram Legend

```mermaid
classDiagram
    direction LR
    class SuperClass
    class SubClass
    class InterfaceType {
        <<interface>>
    }
    class ConcreteClass
    class ClientClass
    class TargetClass

    SubClass --|> SuperClass : Inheritance (extends)
    ConcreteClass ..|> InterfaceType : Implementation (implements)
    ClientClass --> TargetClass : Association (has-a / references)
    ClientClass ..> TargetClass : Dependency (creates / uses)
```

- **Inheritance (`--|>`)**: A subclass inherits state and behavior from an abstract or concrete superclass (e.g., `Patient --|> Person`).
- **Implementation (`..|>`)**: A concrete class satisfies a formal interface contract (e.g., `SmartOpdStrategy ..|> PriorityStrategy`).
- **Association (`-->`)**: A class maintains a reference or field to another class (e.g., `QueueManager --> DoctorQueue`).
- **Dependency (`..>`)**: A class creates, instantiates, or receives an instance as a method parameter (e.g., `PatientFactory ..> Patient`).

---

## 1. Domain Model Class Diagram

```mermaid
classDiagram
    direction TB

    class Person {
        <<abstract>>
        -String id
        -String name
        -int age
        -String gender
        -String phone
        +getId() String
        +getName() String
        +getAge() int
        +getGender() String
        +getPhone() String
    }

    class Doctor {
        -String department
        -String deptCode
        -String room
        -String workingHours
        -int avgConsultMinutes
        -int emergencyBufferSlots
        -DoctorStatus status
        -int consecutiveEmergencies
        +getDepartment() String
        +getDeptCode() String
        +getRoom() String
        +getAvgConsultMinutes() int
        +setAvgConsultMinutes(int) void
        +getStatus() DoctorStatus
        +setStatus(DoctorStatus) void
        +getConsecutiveEmergencies() int
        +incrementConsecutiveEmergencies() void
        +resetConsecutiveEmergencies() void
    }

    class Patient {
        <<abstract>>
        -Token token
        -String doctorId
        -String doctorName
        -String room
        -String department
        -int severity
        -Category category
        -Instant registeredAt
        -String appointmentTime
        -String chiefComplaint
        -PatientStatus status
        -boolean skipped
        -Instant consultationStartTime
        -Instant consultationEndTime
        -Instant noShowTime
        +urgencyBonus(Instant)* double
        +fairnessThresholdMinutes(Settings)* int
        +getToken() Token
        +getSeverity() int
        +getCategory() Category
        +getStatus() PatientStatus
        +setStatus(PatientStatus) void
        +isSkipped() boolean
        +setSkipped(boolean) void
    }

    class EmergencyPatient {
        +urgencyBonus(Instant) double
        +fairnessThresholdMinutes(Settings) int
    }

    class ScheduledPatient {
        +urgencyBonus(Instant) double
        +fairnessThresholdMinutes(Settings) int
    }

    class WalkInPatient {
        +urgencyBonus(Instant) double
        +fairnessThresholdMinutes(Settings) int
    }

    class SimulatedPatient {
        -int index
        -int arrivalMinute
        -Integer appointmentMinute
        -int consultDuration
        -Instant baseInstant
        -int startMinute
        -int finishMinute
        -int waitMinutes
        +urgencyBonus(Instant) double
        +fairnessThresholdMinutes(Settings) int
        +getArrivalMinute() int
        +getWaitMinutes() int
    }

    class Token {
        -String departmentCode
        -String categoryCode
        -int sequenceNumber
        -String value
        +getValue() String
        +getDepartmentCode() String
        +getCategoryCode() String
        +getSequenceNumber() int
    }

    class TokenGenerator {
        +format(String, String, int)$ String
        +parse(String)$ Token
    }

    class PatientRegistrationRequest {
        -String name
        -int age
        -String gender
        -String phone
        -Category category
        -int severity
        -String doctorId
        -String appointmentTime
        -String chiefComplaint
        +getName() String
        +getCategory() Category
        +getSeverity() int
        +getDoctorId() String
    }

    class PatientFactory {
        +createPatient(PatientRegistrationRequest, Doctor, Token, Instant)$ Patient
    }

    class Category {
        <<enumeration>>
        EMERGENCY
        SCHEDULED
        WALK_IN
        +getCode() String
        +getDisplayName() String
    }

    class DoctorStatus {
        <<enumeration>>
        AVAILABLE
        ON_BREAK
        OFF_DUTY
        +getDisplayName() String
    }

    class PatientStatus {
        <<enumeration>>
        WAITING
        IN_CONSULTATION
        COMPLETED
        NO_SHOW
        SKIPPED
        +getDisplayName() String
    }

    class EventType {
        <<enumeration>>
        REGISTERED
        CALLED
        COMPLETED
        NO_SHOW
        SKIPPED
        FAIRNESS_BOOST
        EMERGENCY_OVERRIDE
        MOVED_UP
        MOVED_DOWN
        CONFIG_CHANGE
    }

    Doctor --|> Person : extends
    Patient --|> Person : extends
    EmergencyPatient --|> Patient : extends
    ScheduledPatient --|> Patient : extends
    WalkInPatient --|> Patient : extends
    SimulatedPatient --|> Patient : extends

    Patient --> Token : has
    Patient --> Category : has
    Patient --> PatientStatus : has
    Doctor --> DoctorStatus : has

    PatientFactory ..> Patient : creates
    PatientFactory ..> PatientRegistrationRequest : consumes
    TokenGenerator ..> Token : creates / parses
```

### Explanation
The domain model establishes a clean object hierarchy rooted in the abstract `Person` class, which encapsulates fundamental demographic attributes and validation rules across all hospital actors. `Doctor` extends `Person` with clinical parameters such as consultation duration smoothing and consecutive emergency counters, while `Patient` declares abstract polymorphic hooks (`urgencyBonus` and `fairnessThresholdMinutes`) implemented uniquely by `EmergencyPatient`, `ScheduledPatient`, `WalkInPatient`, and `SimulatedPatient`. Instantiation is strictly decoupled through `PatientFactory` and `TokenGenerator`, ensuring robust triage validation and immutable token formatting before objects enter queues.

---

## 2. Scoring & Strategy Pattern Class Diagram

```mermaid
classDiagram
    direction TB

    class PriorityStrategy {
        <<interface>>
        +getName() String
        +calculateScore(Patient, Instant, Settings) double
        +isFairnessBoosted(Patient, Instant, Settings) boolean
        +explain(Patient, Instant, Settings) String
    }

    class FcfsStrategy {
        +getName() String
        +calculateScore(Patient, Instant, Settings) double
        +isFairnessBoosted(Patient, Instant, Settings) boolean
        +explain(Patient, Instant, Settings) String
    }

    class StrictPriorityStrategy {
        +getName() String
        +calculateScore(Patient, Instant, Settings) double
        +isFairnessBoosted(Patient, Instant, Settings) boolean
        +explain(Patient, Instant, Settings) String
    }

    class SmartOpdStrategy {
        +getName() String
        +calculateScore(Patient, Instant, Settings) double
        +isFairnessBoosted(Patient, Instant, Settings) boolean
        +explain(Patient, Instant, Settings) String
        +getWaitedMinutes(Patient, Instant) long
    }

    class Settings {
        -double severityWeight
        -double waitWeightPerMin
        -double fairnessBoost
        -int fairnessThresholdScheduled
        -int fairnessThresholdWalkin
        -int maxConsecutiveEmergencies
        -int emergencyBufferSlotsDefault
        -int emergencyOverrideSeverity
        -double emergencyOverrideScore
        -double appointmentUrgencyMax
        +getSeverityWeight() double
        +getWaitWeightPerMin() double
        +getFairnessBoost() double
        +getFairnessThresholdScheduled() int
        +getFairnessThresholdWalkin() int
        +getMaxConsecutiveEmergencies() int
        +copy() Settings
        +resetToDefaults() void
    }

    class EmergencyCapSelector {
        +selectNextPatient(List~Patient~, int, int, PriorityStrategy, Instant, Settings)$ SelectionResult
    }

    class SelectionResult {
        <<record>>
        +Patient selectedPatient
        +boolean fairnessCapTriggered
    }

    class DoctorQueue {
        -Doctor doctor
        -List~Patient~ waitingPatients
        -PriorityStrategy strategy
        -Settings settings
        +callNext(Instant, EventLog) Patient
        +getOrdered(Instant) List~Patient~
    }

    class Simulator {
        -PriorityStrategy fcfsStrategy
        -PriorityStrategy strictStrategy
        -PriorityStrategy smartOpdStrategy
        +run(SimulationConfig, Settings) SimulationResult
    }

    FcfsStrategy ..|> PriorityStrategy : implements
    StrictPriorityStrategy ..|> PriorityStrategy : implements
    SmartOpdStrategy ..|> PriorityStrategy : implements

    DoctorQueue --> PriorityStrategy : uses
    DoctorQueue --> Settings : uses
    DoctorQueue ..> EmergencyCapSelector : calls
    Simulator --> PriorityStrategy : uses
    Simulator --> Settings : uses
    Simulator ..> EmergencyCapSelector : calls
    EmergencyCapSelector ..> SelectionResult : returns
    EmergencyCapSelector ..> PriorityStrategy : checks
```

### Explanation
The scoring engine implements the **Strategy Design Pattern** via `PriorityStrategy`, enabling runtime interchangeability between baseline First-Come-First-Served (`FcfsStrategy`), unconstrained clinical triage (`StrictPriorityStrategy`), and the explainable, aging-aware `SmartOpdStrategy`. Algorithmic configuration is encapsulated within the defensive `Settings` entity, which governs weightings, anti-starvation thresholds, and emergency caps. Crucially, the static `EmergencyCapSelector` is shared identically by both the live clinical `DoctorQueue` and the discrete-event `Simulator`, ensuring 100% mathematical consistency across live hospital operations and offline capacity benchmarks.

---

## 3. Queue Engine Class Diagram

```mermaid
classDiagram
    direction TB

    class AppClock {
        <<interface>>
        +now() Instant
        +getOffsetMinutes() long
    }

    class SystemAppClock {
        +now() Instant
        +getOffsetMinutes() long
    }

    class SimulatedClock {
        -Instant baseInstant
        -long offsetMinutes
        +now() Instant
        +advance(long) void
        +reset() void
        +getOffsetMinutes() long
        +getBaseInstant() Instant
    }

    class QueueManager {
        -Map~String, DoctorQueue~ doctorQueues
        -Map~String, Patient~ allPatients
        -PriorityStrategy strategy
        -Settings settings
        -AppClock clock
        -EventLog eventLog
        -Set~String~ fairnessBoostLoggedTokens
        +registerPatient(PatientRegistrationRequest, Doctor, Token) RegistrationResult
        +callNext(String) Patient
        +completeConsultation(String) Patient
        +markNoShow(String) Patient
        +skipPatient(String, String) Patient
        +advanceClock(long) void
        +resetClock() void
        +captureQueuePositions(Instant) Map~String, Integer~
        +detectPositionChanges(Map, Map, Instant) List~QueueEvent~
    }

    class DoctorQueue {
        -Doctor doctor
        -List~Patient~ waitingPatients
        -Patient activePatient
        -PriorityStrategy strategy
        -Settings settings
        +addPatient(Patient) void
        +callNext(Instant, EventLog) Patient
        +completeConsultation(Instant, EventLog) Patient
        +markNoShow(String, Instant, EventLog) Patient
        +skipPatient(String, String, Instant, EventLog) Patient
        +getOrdered(Instant) List~Patient~
        +getActivePatient() Patient
        +getDoctor() Doctor
    }

    class WaitTimeEstimator {
        +estimate(Patient, Doctor, Patient, List~Patient~, Instant)$ WaitEstimate
    }

    class WaitEstimate {
        -int waitMinutes
        -int position
        -Instant estimatedStartTime
        -String estimatedTimeStr
        +getWaitMinutes() int
        +getPosition() int
        +getEstimatedStartTime() Instant
        +getEstimatedTimeStr() String
    }

    class RegistrationResult {
        -Patient patient
        -int position
        -int estimatedWaitMinutes
        -String estimatedTimeStr
        -String explanation
        +getPatient() Patient
        +getPosition() int
        +getEstimatedWaitMinutes() int
        +getEstimatedTimeStr() String
        +getExplanation() String
    }

    class EventLog {
        -List~QueueEvent~ events
        +log(QueueEvent) void
        +getAllEvents() List~QueueEvent~
        +clear() void
    }

    class QueueEvent {
        -long id
        -EventType type
        -String token
        -String doctorId
        -String reason
        -Instant timestamp
        +getId() long
        +getType() EventType
        +getToken() String
        +getDoctorId() String
        +getReason() String
        +getTimestamp() Instant
    }

    SystemAppClock ..|> AppClock : implements
    SimulatedClock ..|> AppClock : implements

    QueueManager --> AppClock : has
    QueueManager --> EventLog : has
    QueueManager --> DoctorQueue : manages
    DoctorQueue --> WaitTimeEstimator : calls
    WaitTimeEstimator ..> WaitEstimate : creates
    QueueManager ..> RegistrationResult : returns
    EventLog --> QueueEvent : contains
```

### Explanation
The queue coordination engine is orchestrated by `QueueManager`, which manages individual `DoctorQueue` instances for each clinical provider and coordinates temporal evaluations through the `AppClock` abstraction (`SimulatedClock` enables interactive time travel during demonstrations). `DoctorQueue` coordinates waiting patient collections, consultation lifecycle states, and position-shift auditing. When patients register or advance in the queue, `WaitTimeEstimator` calculates deterministic consultation start times based on doctor-specific smoothed durations and preceding emergency buffers, capturing the outcome in an immutable `WaitEstimate` and broadcasting changes into `EventLog`.

---

## 4. Persistence Architecture Class Diagram

```mermaid
classDiagram
    direction TB

    class ConnectionProvider {
        +DB_PATH_PROPERTY String
        +DB_PATH_ENV String
        +DEFAULT_DB_PATH String
        -String dbPath
        +resolveDbPath()$ String
        +getConnection() Connection
        +getDbPath() String
    }

    class TransactionHelper {
        -ConnectionProvider connectionProvider
        +execute(TransactionCallback~T~) T
        +executeVoid(VoidTransactionCallback) void
    }

    class SchemaInitializer {
        -ConnectionProvider connectionProvider
        +initialize() void
    }

    class DefaultsInitializer {
        -ConnectionProvider connectionProvider
        +initializeIfEmpty(Connection)$ void
        +initialize(Connection) void
    }

    class PasswordHasher {
        -PBKDF2_ALGO String
        -ITERATIONS int
        -KEY_LENGTH int
        +hashPassword(String, byte[])$ String
        +generateSalt()$ byte[]
        +verifyPassword(String, String, String)$ boolean
    }

    class DepartmentDao {
        <<interface>>
        +findAll(Connection) List~Department~
        +findByCode(Connection, String) Optional~Department~
        +insert(Connection, Department) void
    }
    class JdbcDepartmentDao {
        +findAll(Connection) List~Department~
        +findByCode(Connection, String) Optional~Department~
        +insert(Connection, Department) void
    }

    class DoctorDao {
        <<interface>>
        +findAll(Connection) List~Doctor~
        +findById(Connection, String) Optional~Doctor~
        +insert(Connection, Doctor) void
        +update(Connection, Doctor) void
        +delete(Connection, String) void
    }
    class JdbcDoctorDao {
        +findAll(Connection) List~Doctor~
        +findById(Connection, String) Optional~Doctor~
        +insert(Connection, Doctor) void
        +update(Connection, Doctor) void
        +delete(Connection, String) void
    }

    class PatientDao {
        <<interface>>
        +findAll(Connection) List~Patient~
        +findByToken(Connection, String) Optional~Patient~
        +findWaitingByDoctor(Connection, String) List~Patient~
        +findByStatus(Connection, PatientStatus) List~Patient~
        +insert(Connection, Patient) void
        +update(Connection, Patient) void
    }
    class JdbcPatientDao {
        +findAll(Connection) List~Patient~
        +findByToken(Connection, String) Optional~Patient~
        +findWaitingByDoctor(Connection, String) List~Patient~
        +findByStatus(Connection, PatientStatus) List~Patient~
        +insert(Connection, Patient) void
        +update(Connection, Patient) void
    }

    class TokenSequenceDao {
        <<interface>>
        +nextValue(Connection, String, String) int
        +resetAll(Connection) void
    }
    class JdbcTokenSequenceDao {
        +nextValue(Connection, String, String) int
        +resetAll(Connection) void
    }

    class EventDao {
        <<interface>>
        +append(Connection, QueueEvent) void
        +findAll(Connection, int) List~QueueEvent~
        +findByToken(Connection, String) List~QueueEvent~
    }
    class JdbcEventDao {
        +append(Connection, QueueEvent) void
        +findAll(Connection, int) List~QueueEvent~
        +findByToken(Connection, String) List~QueueEvent~
    }

    class SettingsDao {
        <<interface>>
        +load(Connection) Settings
        +save(Connection, Settings) void
    }
    class JdbcSettingsDao {
        +load(Connection) Settings
        +save(Connection, Settings) void
    }

    class AppStateDao {
        <<interface>>
        +get(Connection, String) Optional~String~
        +set(Connection, String, String) void
    }
    class JdbcAppStateDao {
        +get(Connection, String) Optional~String~
        +set(Connection, String, String) void
    }

    class UserDao {
        <<interface>>
        +findByUsername(Connection, String) Optional~UserAccount~
        +insert(Connection, UserAccount) void
    }
    class JdbcUserDao {
        +findByUsername(Connection, String) Optional~UserAccount~
        +insert(Connection, UserAccount) void
    }

    JdbcDepartmentDao ..|> DepartmentDao : implements
    JdbcDoctorDao ..|> DoctorDao : implements
    JdbcPatientDao ..|> PatientDao : implements
    JdbcTokenSequenceDao ..|> TokenSequenceDao : implements
    JdbcEventDao ..|> EventDao : implements
    JdbcSettingsDao ..|> SettingsDao : implements
    JdbcAppStateDao ..|> AppStateDao : implements
    JdbcUserDao ..|> UserDao : implements

    TransactionHelper --> ConnectionProvider : uses
    SchemaInitializer --> ConnectionProvider : uses
    DefaultsInitializer --> ConnectionProvider : uses
```

### Explanation
Persistence follows the **DAO (Data Access Object) Pattern**, establishing strict separation between business entities and raw JDBC SQL statements executed against SQLite 3.4x. `ConnectionProvider` configures relational foreign keys, Write-Ahead Logging (`PRAGMA journal_mode = WAL`), and busy timeouts, while `TransactionHelper` coordinates atomic commit and automatic rollback blocks. System security is guaranteed via `PasswordHasher` using PBKDF2WithHmacSHA256 with 120,000 rounds and random 16-byte salts, while `SchemaInitializer` and `DefaultsInitializer` ensure database creation and default settings initialization remain 100% idempotent.

---

## 5. Service & API Layer Class Diagram

```mermaid
classDiagram
    direction TB

    class QueueService {
        -ConnectionProvider connectionProvider
        -TransactionHelper transactionHelper
        -QueueManager queueManager
        -SimulatedClock clock
        -Settings settings
        -ReentrantLock writeLock
        +registerPatient(PatientRegistrationRequest) RegistrationResult
        +previewPatient(PatientRegistrationRequest) RegistrationResult
        +callNext(String) Patient
        +completeConsultation(String) Patient
        +markNoShow(String) Patient
        +skipPatient(String, String) Patient
        +setDoctorStatus(String, DoctorStatus) void
        +advanceClock(long) void
        +resetClock() void
        +updateSettings(Settings) void
        +authenticate(String, String) Optional~AuthResult~
        +getPublicBoardData() List~PublicBoardDoctorDto~
        +getPublicQueueSummary(String) PublicTrackerResponse
    }

    class AuthController {
        -QueueService queueService
        -SessionStore sessionStore
        +login(LoginRequest) ResponseEntity
        +logout(HttpServletRequest) ResponseEntity
        +me(HttpServletRequest) ResponseEntity
    }

    class DoctorController {
        -QueueService queueService
        +getDoctorQueue(String) ResponseEntity
        +callNext(String) ResponseEntity
        +completeConsultation(String) ResponseEntity
        +updateDoctorStatus(String, DoctorStatusRequest) ResponseEntity
    }

    class PublicController {
        -QueueService queueService
        -PublicTrackerRateLimiter rateLimiter
        +getPublicBoard() ResponseEntity
        +getPublicTracker(String, HttpServletRequest) ResponseEntity
        +getSampleTokens() ResponseEntity
    }

    class ReceptionController {
        -QueueService queueService
        +registerPatient(PatientRegisterRequest) ResponseEntity
        +previewPatient(PatientRegisterRequest) ResponseEntity
        +getPatient(String) ResponseEntity
        +getQueues() ResponseEntity
        +advanceClock(ClockAdvanceRequest) ResponseEntity
        +resetClock() ResponseEntity
        +updateSettings(SettingsDto) ResponseEntity
        +getAnalytics() ResponseEntity
    }

    class SimulationController {
        -QueueService queueService
        -Simulator simulator
        +getPresets() ResponseEntity
        +runSimulation(SimulationRunRequest) ResponseEntity
    }

    class AuthInterceptor {
        -SessionStore sessionStore
        +preHandle(HttpServletRequest, HttpServletResponse, Object) boolean
    }

    class SessionStore {
        -Map~String, UserSession~ sessions
        -Map~String, FailedLoginRecord~ failedAttempts
        +createSession(String, String, String, String) UserSession
        +getSession(String) Optional~UserSession~
        +invalidateSession(String) void
        +recordFailedAttempt(String) void
        +checkLockout(String) void
    }

    class GlobalExceptionHandler {
        +handleValidation(ValidationException) ResponseEntity
        +handleDoctorUnavailable(DoctorUnavailableException) ResponseEntity
        +handleResourceNotFound(ResourceNotFoundException) ResponseEntity
        +handleLockout(AccountLockedException) ResponseEntity
        +handleRateLimit(RateLimitExceededException) ResponseEntity
        +handleGeneral(Exception) ResponseEntity
    }

    class SmartOpdException {
        <<exception>>
        +SmartOpdException(String)
        +SmartOpdException(String, Throwable)
    }

    class ValidationException {
        <<exception>>
    }
    class DoctorUnavailableException {
        <<exception>>
    }
    class ResourceNotFoundException {
        <<exception>>
    }
    class SlotFullException {
        <<exception>>
    }
    class DuplicateTokenException {
        <<exception>>
    }
    class InvalidSeverityException {
        <<exception>>
    }

    class LoginRequest {
        <<record>>
        +String username
        +String password
    }
    class LoginResponse {
        <<record>>
        +String token
        +String role
        +String displayName
        +String doctorId
    }
    class PatientRegisterRequest {
        <<record>>
        +String name
        +int age
        +String gender
        +String phone
        +String category
        +int severity
        +String doctorId
        +String appointmentTime
        +String chiefComplaint
    }
    class RegistrationResponse {
        <<record>>
        +String token
        +int position
        +int estimatedWaitMinutes
        +String estimatedTimeStr
        +String explanation
    }

    ValidationException --|> SmartOpdException : extends
    DoctorUnavailableException --|> SmartOpdException : extends
    ResourceNotFoundException --|> SmartOpdException : extends
    SlotFullException --|> SmartOpdException : extends
    DuplicateTokenException --|> SmartOpdException : extends
    InvalidSeverityException --|> SmartOpdException : extends

    AuthController --> QueueService : uses
    AuthController --> SessionStore : uses
    DoctorController --> QueueService : uses
    PublicController --> QueueService : uses
    ReceptionController --> QueueService : uses
    SimulationController --> QueueService : uses
    AuthInterceptor --> SessionStore : uses

    AuthController ..> LoginRequest : consumes
    AuthController ..> LoginResponse : returns
    ReceptionController ..> PatientRegisterRequest : consumes
    ReceptionController ..> RegistrationResponse : returns
```

### Explanation
The application service layer is unified behind `QueueService` as a centralized **Facade Pattern**, coordinating write-through SQLite updates serialized with a fair `ReentrantLock` alongside fast in-memory queue lookups. The REST controllers (`AuthController`, `DoctorController`, `PublicController`, `ReceptionController`, `SimulationController`) expose clean HTTP endpoints with strongly-typed Java records as Data Transfer Objects (DTOs). Cross-cutting security is handled declaratively by `AuthInterceptor` (enforcing token validity and `@RequiresRole` checks) and `SessionStore` (managing 256-bit bearer tokens and sliding-window lockout), while `GlobalExceptionHandler` masks database internals and formats standardized RFC 7807 problem responses.

---

## 6. Discrete-Event Simulation Engine Class Diagram

```mermaid
classDiagram
    direction TB

    class SimulationConfig {
        -int numPatients
        -int arrivalSpreadMinutes
        -int numDoctors
        -int avgConsultMinutes
        -double emergencyPercent
        -double scheduledPercent
        -double walkinPercent
        -long randomSeed
        +getNumPatients() int
        +getArrivalSpreadMinutes() int
        +getNumDoctors() int
        +getAvgConsultMinutes() int
        +getEmergencyPercent() double
        +getScheduledPercent() double
        +getWalkinPercent() double
        +getRandomSeed() long
    }

    class SeededRandom {
        -long state
        +next() double
        +nextInt(int, int) int
    }

    class Simulator {
        -PriorityStrategy fcfsStrategy
        -PriorityStrategy strictStrategy
        -PriorityStrategy smartOpdStrategy
        +run(SimulationConfig, Settings) SimulationResult
        +generateCohort(SimulationConfig) List~SimulatedPatient~
        -simulatePolicy(PriorityStrategy, List~SimulatedPatient~, SimulationConfig, Settings) PolicySimulationResult
    }

    class SimulationResult {
        <<record>>
        +int numPatients
        +int numDoctors
        +int totalTimeMinutes
        +double offeredLoadPercent
        +PolicySimulationResult fcfs
        +PolicySimulationResult strictPriority
        +PolicySimulationResult smartOpd
        +List~String~ insights
    }

    class PolicySimulationResult {
        <<record>>
        +String policyName
        +PolicyMetrics metrics
        +List~PatientWaitRecord~ patientWaits
    }

    class PolicyMetrics {
        <<record>>
        +double averageWaitOverall
        +double averageWaitEmergency
        +double averageWaitHighSeverity
        +double averageWaitScheduled
        +double averageWaitWalkIn
        +double p90Wait
        +double maxWaitOverall
        +double maxScheduledWait
        +int starvationCount
        +double doctorIdlePercent
    }

    class PatientWaitRecord {
        <<record>>
        +String id
        +Category category
        +int severity
        +int arrivalMinute
        +int startMinute
        +int waitMinutes
        +boolean starved
    }

    class SimulationReport {
        +main(String[])$ void
    }

    Simulator --> SimulationConfig : takes
    Simulator --> SeededRandom : uses
    Simulator --> SimulationResult : produces
    SimulationResult --> PolicySimulationResult : contains
    PolicySimulationResult --> PolicyMetrics : contains
    PolicySimulationResult --> PatientWaitRecord : contains
    SimulationReport ..> Simulator : executes
```

### Explanation
The simulation engine models outpatient clinic capacity using a pure Java **Discrete-Event Simulation (DES)** architecture capable of processing 500 patient encounters in under 50 milliseconds. `SeededRandom` implements the 32-bit Mulberry32 algorithm to guarantee identical pseudo-random number sequences between Java and JavaScript golden tests. When `Simulator.run` executes, it synthesizes patient arrival queues across configured clinical triage mixes and benchmarks FCFS, Strict Priority, and SmartOPD in parallel, capturing per-patient wait statistics in immutable `PolicySimulationResult` records for comparative telemetry and CSV export.
