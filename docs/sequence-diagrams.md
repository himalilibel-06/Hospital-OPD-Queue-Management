# SmartOPD – UML Sequence Diagrams

This document contains Mermaid sequence diagrams tracing the exact runtime execution paths through the SmartOPD system. Every actor, controller, service, domain entity, and DAO method matches the Java source code in `backend/src/main/java/com/smartopd/`.

---

## 1. User Authentication & Brute-Force Lockout Sequence

This diagram details user sign-in via `AuthController.login()`, demonstrating PBKDF2 cryptographic hash verification, token generation, and the brute-force defense lockout flow.

```mermaid
sequenceDiagram
    autonumber
    actor Browser as Staff Browser
    participant AC as AuthController
    participant SS as SessionStore
    participant QS as QueueService
    participant UD as UserDao (JdbcUserDao)
    participant PH as PasswordHasher
    participant DB as SQLite DB

    Browser->>AC: POST /api/auth/login (username, password)
    activate AC
    AC->>SS: checkLockout(username)
    alt Account is currently locked (>5 failures in 5 min)
        SS-->>AC: throws AccountLockedException
        AC-->>Browser: 429 Too Many Requests (Retry after 60s)
    else Account not locked
        AC->>QS: authenticate(username, password)
        activate QS
        QS->>UD: findByUsername(conn, username)
        activate UD
        UD->>DB: SELECT * FROM user_account WHERE username = ?
        DB-->>UD: ResultSet (user, salt, password_hash, role)
        UD-->>QS: Optional<UserAccount>
        deactivate UD

        alt User not found OR password mismatch
            QS->>PH: verifyPassword(password, salt, hash)
            PH-->>QS: false
            QS-->>AC: Optional.empty()
            AC->>SS: recordFailedAttempt(username)
            Note over SS: If 5th failure reached, locks account for 60s
            AC-->>Browser: 401 Unauthorized (Invalid username or password)
        else Credentials valid
            QS->>PH: verifyPassword(password, salt, hash)
            PH-->>QS: true
            QS-->>AC: Optional.of(AuthResult)
            deactivate QS
            AC->>SS: recordSuccess(username)
            AC->>SS: createSession(username, role, displayName, doctorId)
            activate SS
            Note over SS: Generates 256-bit cryptographically secure token
            SS-->>AC: UserSession (token, 8h expiry)
            deactivate SS
            AC-->>Browser: 200 OK (token, role, displayName, doctorId)
        end
    end
    deactivate AC
```

---

## 2. Patient Registration with Live Preview Sequence

This diagram illustrates the dual-stage registration flow: a debounced read-only preview call (`previewPatient`) that leaves persistent storage unchanged, followed by the atomic transactional registration (`registerPatient`).

```mermaid
sequenceDiagram
    autonumber
    actor Page as register-patient.html
    participant RC as ReceptionController
    participant QS as QueueService
    participant TH as TransactionHelper
    participant TSD as TokenSequenceDao
    participant PF as PatientFactory
    participant QM as QueueManager
    participant DQ as DoctorQueue
    participant STRAT as SmartOpdStrategy
    participant PD as PatientDao
    participant ED as EventDao
    participant DB as SQLite DB

    %% 1. Live Preview Phase (Read-Only)
    Note over Page,RC: Stage 1: 300ms Debounced Preview (No State Mutation)
    Page->>RC: POST /api/patients/preview (name, age, phone, cat, sev, docId)
    activate RC
    RC->>QS: previewPatient(request)
    activate QS
    QS->>TSD: SELECT last_value FROM token_sequence (read uncommitted)
    QS->>QM: getDoctorQueue(docId)
    QS->>STRAT: calculateScore() & explain()
    QS-->>RC: RegistrationResult (estimated wait, preview slot, explanation)
    deactivate QS
    RC-->>Page: 200 OK (PatientPreviewResponse)
    deactivate RC

    %% 2. Real Registration Phase (ACID Write-Through)
    Note over Page,RC: Stage 2: Confirmed Submission (Atomic ACID Transaction)
    Page->>RC: POST /api/patients (PatientRegisterRequest)
    activate RC
    RC->>QS: registerPatient(PatientRegistrationRequest)
    activate QS
    QS->>QS: writeLock.lock()
    QS->>TH: execute(conn -> { ... })
    activate TH
    TH->>DB: conn.setAutoCommit(false)

    TH->>TSD: nextValue(conn, deptCode, categoryCode)
    TSD->>DB: UPDATE token_sequence SET last_value = last_value + 1
    TSD-->>TH: int sequenceNumber
    Note over TH: Generates Token (e.g. CARD-W-007)

    TH->>PF: createPatient(request, doctor, token, now)
    PF-->>TH: concrete Patient subclass (WalkInPatient)

    TH->>PD: insert(conn, patient)
    PD->>DB: INSERT INTO patient VALUES (...)

    TH->>QM: captureQueuePositions(now)
    TH->>DQ: addPatient(patient)
    TH->>QM: captureQueuePositions(now)
    TH->>QM: detectPositionChanges(prePositions, postPositions, now)

    loop For each shifted patient
        TH->>ED: append(conn, shiftEvent)
        ED->>DB: INSERT INTO queue_event (MOVED_DOWN)
    end

    TH->>ED: append(conn, regEvent)
    ED->>DB: INSERT INTO queue_event (REGISTERED / EMERGENCY_OVERRIDE)

    TH->>DB: conn.commit()
    TH-->>QS: RegistrationResult
    deactivate TH
    QS->>QS: writeLock.unlock()
    QS-->>RC: RegistrationResult
    deactivate QS
    RC-->>Page: 201 Created (RegistrationResponse with token, wait time, explanation)
    deactivate RC
```

---

## 3. Doctor Call-Next & Consultation Completion Sequence

This diagram shows how a doctor calls the next patient (incorporating the emergency cap anti-starvation intervention) and completes the consultation, triggering running average consultation duration smoothing.

```mermaid
sequenceDiagram
    autonumber
    actor DC as Doctor Console
    participant DocCtrl as DoctorController
    participant QS as QueueService
    participant QM as QueueManager
    participant DQ as DoctorQueue
    participant ECS as EmergencyCapSelector
    participant TH as TransactionHelper
    participant PD as PatientDao
    participant DD as DoctorDao
    participant ED as EventDao
    participant DB as SQLite DB

    %% 1. Call Next
    Note over DC,DocCtrl: Action 1: Doctor Calls Next Patient
    DC->>DocCtrl: POST /api/doctors/{id}/call-next
    activate DocCtrl
    DocCtrl->>QS: callNext(doctorId)
    activate QS
    QS->>QS: writeLock.lock()
    QS->>QM: getDoctorQueue(doctorId)
    QS->>DQ: callNext(now, eventLog)
    activate DQ

    DQ->>ECS: selectNextPatient(waitingPatients, consecutiveEmergencies, maxConsecutive, strategy, now, settings)
    activate ECS
    alt Doctor served >= 2 consecutive emergencies AND top patient < Level 5
        Note over ECS: Emergency Cap Triggered! Yield to longest-waiting boosted patient
        ECS-->>DQ: SelectionResult(boostedPatient, true)
        DQ->>DQ: doctor.resetConsecutiveEmergencies()
    else Normal Priority Call
        ECS-->>DQ: SelectionResult(queue.get(0), false)
        opt Selected is Level 5 or Emergency
            DQ->>DQ: doctor.incrementConsecutiveEmergencies()
        end
    end
    deactivate ECS

    DQ->>DQ: waitingPatients.remove(selected)
    DQ->>DQ: selected.setStatus(IN_CONSULTATION)
    DQ->>DQ: this.activePatient = selected
    DQ-->>QS: Patient called
    deactivate DQ

    QS->>TH: execute(conn -> { ... })
    activate TH
    TH->>PD: update(conn, called)
    PD->>DB: UPDATE patient SET status='IN_CONSULTATION', consult_start=?
    TH->>ED: append(conn, calledEvent)
    ED->>DB: INSERT INTO queue_event (CALLED)
    TH->>DB: conn.commit()
    deactivate TH
    QS->>QS: writeLock.unlock()
    QS-->>DocCtrl: Patient called
    deactivate QS
    DocCtrl-->>DC: 200 OK (QueuePatientDto)
    deactivate DocCtrl

    %% 2. Complete Consultation
    Note over DC,DocCtrl: Action 2: Doctor Completes Consultation
    DC->>DocCtrl: POST /api/doctors/{id}/complete
    activate DocCtrl
    DocCtrl->>QS: completeConsultation(doctorId)
    activate QS
    QS->>QS: writeLock.lock()
    QS->>QM: getDoctorQueue(doctorId)
    QS->>DQ: completeConsultation(now, eventLog)
    activate DQ
    DQ->>DQ: activePatient.setStatus(COMPLETED)
    DQ->>DQ: Duration actualMins = between(consultStart, now)
    Note over DQ: Smoothed Avg: (oldAvg * 0.7) + (actualMins * 0.3)
    DQ->>DQ: doctor.setAvgConsultMinutes(smoothedAvg)
    DQ->>DQ: this.activePatient = null
    DQ-->>QS: Patient completed
    deactivate DQ

    QS->>TH: execute(conn -> { ... })
    activate TH
    TH->>PD: update(conn, completed)
    PD->>DB: UPDATE patient SET status='COMPLETED', consult_end=?
    TH->>DD: update(conn, doctor)
    DD->>DB: UPDATE doctor SET avg_consult_minutes = ?
    TH->>ED: append(conn, completeEvent)
    ED->>DB: INSERT INTO queue_event (COMPLETED)
    TH->>DB: conn.commit()
    deactivate TH
    QS->>QS: writeLock.unlock()
    QS-->>DocCtrl: Patient completed
    deactivate QS
    DocCtrl-->>DC: 200 OK (QueuePatientDto with updated duration)
    deactivate DocCtrl
```

---

## 4. Public Patient Mobile Tracker Sequence (Privacy-by-Design)

This diagram details the unauthenticated public tracker query, demonstrating the sliding-window IP rate limiter and the explicit masking of patient Personally Identifiable Information (PII).

```mermaid
sequenceDiagram
    autonumber
    actor Mobile as Patient Phone Browser
    participant PC as PublicController
    participant RL as PublicTrackerRateLimiter
    participant QS as QueueService
    participant QM as QueueManager
    participant DQ as DoctorQueue
    participant WTE as WaitTimeEstimator
    participant ED as EventDao
    participant DB as SQLite DB

    Mobile->>PC: GET /api/public/tracker/{token}
    activate PC
    PC->>RL: isAllowed(clientIp)
    alt Requests > 30 within 60 seconds
        RL-->>PC: false
        PC-->>Mobile: 429 Too Many Requests (Rate limit exceeded)
    else Rate limit within threshold
        RL-->>PC: true
        PC->>QS: getPublicQueueSummary(token)
        activate QS
        QS->>QM: getPatientByToken(token)
        activate QM
        QM-->>QS: Patient entity
        deactivate QM

        QS->>QM: getDoctorQueue(patient.getDoctorId())
        activate QM
        QM-->>QS: DoctorQueue dq
        deactivate QM

        QS->>WTE: estimate(patient, doctor, dq.activePatient, dq.ordered, now)
        WTE-->>QS: WaitEstimate (waitMinutes, position, estimatedStart)

        QS->>ED: findByToken(conn, token)
        ED->>DB: SELECT * FROM queue_event WHERE token = ? ORDER BY timestamp ASC
        DB-->>ED: List<QueueEvent>
        ED-->>QS: List<QueueEvent>

        Note over QS: Privacy Filter: Mask full name -> firstName only.<br/>Exclude phone, age, gender, severity number, and chief complaint.
        QS-->>PC: PublicTrackerResponse (token, firstName, doctor, room, position, wait, timeline)
        deactivate QS
        PC-->>Mobile: 200 OK (PublicTrackerResponse)
    end
    deactivate PC
```

---

## 5. Demo Clock Advance & Fairness Boost Detection Sequence

This diagram shows how advancing the simulated clock causes waiting patients who cross their fairness aging threshold (45 minutes) to be boosted (+150 bonus points) and persist audit events.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Queue Manager UI
    participant RC as ReceptionController
    participant QS as QueueService
    participant QM as QueueManager
    participant Clock as SimulatedClock
    participant DQ as DoctorQueue
    participant STRAT as SmartOpdStrategy
    participant EL as EventLog
    participant TH as TransactionHelper
    participant ASD as AppStateDao
    participant ED as EventDao
    participant DB as SQLite DB

    Admin->>RC: POST /api/clock/advance (minutes: 50)
    activate RC
    RC->>QS: advanceClock(50)
    activate QS
    QS->>QS: writeLock.lock()
    QS->>QM: captureQueuePositions(now)

    QS->>QM: advanceClock(50)
    activate QM
    QM->>Clock: advance(50)
    Note over Clock: Simulated time increments by 50 minutes

    loop For each waiting patient in all DoctorQueues
        QM->>STRAT: isFairnessBoosted(patient, newNow, settings)
        activate STRAT
        STRAT->>STRAT: waitedMinutes >= patient.fairnessThresholdMinutes(settings)
        Note over STRAT: 50m wait >= 45m threshold -> true
        STRAT-->>QM: true
        deactivate STRAT

        alt Patient not already logged as boosted
            QM->>QM: fairnessBoostLoggedTokens.add(token)
            QM->>EL: log(new QueueEvent(FAIRNESS_BOOST, token, docId, reason, newNow))
            Note over EL: Added to in-memory audit log
        end
    end

    QM->>QM: captureQueuePositions(newNow)
    QM->>QM: detectPositionChanges(prePositions, postPositions, newNow)
    deactivate QM

    QS->>TH: execute(conn -> { ... })
    activate TH
    TH->>ASD: set(conn, "clock_offset_minutes", offset)
    ASD->>DB: UPDATE app_state SET value = ? WHERE key = 'clock_offset_minutes'
    loop For each newly triggered event
        TH->>ED: append(conn, boostEvent)
        ED->>DB: INSERT INTO queue_event (FAIRNESS_BOOST / MOVED_UP)
    end
    TH->>DB: conn.commit()
    deactivate TH

    QS->>QS: writeLock.unlock()
    QS-->>RC: void
    deactivate QS
    RC-->>Admin: 200 OK (ClockResponse with new current time and offset)
    deactivate RC
```

---

## 6. Transactional Failure & In-Memory Recovery Sequence

This diagram details the fail-safe consistency model: when an unexpected database error occurs mid-transaction, SQLite automatically rolls back and `QueueService` rebuilds in-memory queues directly from committed database state.

```mermaid
sequenceDiagram
    autonumber
    actor Staff as Front Desk Client
    participant RC as ReceptionController
    participant QS as QueueService
    participant TH as TransactionHelper
    participant TSD as TokenSequenceDao
    participant PD as PatientDao
    participant ED as EventDao
    participant DB as SQLite DB
    participant GEH as GlobalExceptionHandler

    Staff->>RC: POST /api/patients (PatientRegisterRequest)
    activate RC
    RC->>QS: registerPatient(request)
    activate QS
    QS->>QS: writeLock.lock()
    QS->>TH: execute(conn -> { ... })
    activate TH
    TH->>DB: conn.setAutoCommit(false)

    TH->>TSD: nextValue(conn, dept, cat)
    TSD->>DB: UPDATE token_sequence SET last_value = last_value + 1
    TH->>PD: insert(conn, patient)
    PD->>DB: INSERT INTO patient VALUES (...)

    Note over TH,DB: Disk failure / Constraint violation on Event audit insert!
    TH->>ED: append(conn, regEvent)
    ED->>DB: INSERT INTO queue_event ...
    DB-->>ED: SQLException ("disk I/O error / lock timeout")
    ED-->>TH: throws SQLException

    Note over TH,DB: TransactionHelper executes rollback
    TH->>DB: conn.rollback()
    TH-->>QS: throws DataAccessException
    deactivate TH

    Note over QS: Fail-Safe Recovery Triggered!
    QS->>QS: rebuildDoctorQueueFromDb(request.getDoctorId())
    activate QS
    QS->>DB: SELECT * FROM patient WHERE doctor_id = ? AND status = 'WAITING'
    DB-->>QS: committed waiting patients
    QS->>DB: SELECT * FROM queue_event ORDER BY id DESC LIMIT 100
    DB-->>QS: committed events
    Note over QS: In-memory queues and eventLog restored to pre-failure state
    deactivate QS

    QS->>QS: writeLock.unlock()
    QS-->>RC: throws DataAccessException
    deactivate QS
    RC-->>GEH: uncaught DataAccessException
    activate GEH
    Note over GEH: Defensive error masking: hides SQL query and stack trace
    GEH-->>Staff: 500 Internal Server Error ("Patient registration failed. Please try again.")
    deactivate GEH
    deactivate RC
```
