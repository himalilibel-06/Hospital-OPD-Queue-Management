package com.smartopd.service;

import com.smartopd.auth.PasswordHasher;
import com.smartopd.config.Settings;
import com.smartopd.dao.*;
import com.smartopd.dao.jdbc.*;
import com.smartopd.events.QueueEvent;
import com.smartopd.exception.ConflictException;
import com.smartopd.exception.DoctorUnavailableException;
import com.smartopd.exception.ResourceNotFoundException;
import com.smartopd.exception.ValidationException;
import com.smartopd.model.*;
import com.smartopd.queue.DoctorQueue;
import com.smartopd.queue.QueueManager;
import com.smartopd.queue.RegistrationResult;
import com.smartopd.queue.WaitEstimate;
import com.smartopd.queue.WaitTimeEstimator;
import com.smartopd.scoring.SmartOpdStrategy;
import com.smartopd.time.SimulatedClock;
import com.smartopd.api.security.SessionStore;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import com.smartopd.scoring.PriorityStrategy;

/**
 * QueueService - Central write-through coordinator combining in-memory fast triage
 * with transactional SQLite relational persistence.
 * 
 * ============================================================================
 * OOP & System Architecture Concepts:
 * 1. Facade Pattern: Unified, high-level API over DAOs, QueueManager, and Crypto.
 * 2. Write-Through Caching: Every mutation executes in ONE atomic ACID transaction
 *    persisting patient updates, events, token sequences, and application counters.
 * 3. Fail-Safe Recovery: If a DB write fails, the database is rolled back and the
 *    in-memory queue state is automatically rebuilt from the database.
 * 4. Concurrency & Synchronization: Serializes mutating write transactions via
 *    a fair ReentrantLock (SQLite single-writer model) while allowing non-blocking reads.
 * ============================================================================
 */
public class QueueService {

    private final ConnectionProvider connectionProvider;
    private final TransactionHelper transactionHelper;

    private final DepartmentDao departmentDao = new JdbcDepartmentDao();
    private final DoctorDao doctorDao = new JdbcDoctorDao();
    private final PatientDao patientDao = new JdbcPatientDao();
    private final TokenSequenceDao tokenSequenceDao = new JdbcTokenSequenceDao();
    private final EventDao eventDao = new JdbcEventDao();
    private final SettingsDao settingsDao = new JdbcSettingsDao();
    private final AppStateDao appStateDao = new JdbcAppStateDao();
    private final UserDao userDao = new JdbcUserDao();

    // In-memory queue coordination engine
    private QueueManager queueManager;
    private SimulatedClock clock;
    private Settings settings;

    // Concurrency lock for SQLite single-writer serialization
    private final ReentrantLock writeLock = new ReentrantLock(true);
    private SessionStore sessionStore;

    public QueueService() {
        this(new ConnectionProvider());
    }

    public QueueService(ConnectionProvider connectionProvider) {
        this(connectionProvider, null);
    }

    public QueueService(ConnectionProvider connectionProvider, SessionStore sessionStore) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider cannot be null.");
        }
        this.connectionProvider = connectionProvider;
        this.sessionStore = sessionStore;
        this.transactionHelper = new TransactionHelper(connectionProvider);

        // 1. Initialise schema idempotently
        new SchemaInitializer(connectionProvider).initialize();

        // 2. Initialize default hospital settings if database is empty
        new DefaultsInitializer(connectionProvider).initializeIfEmpty();

        // 3. Load entire state from persistent storage into memory
        reloadAllFromDatabase();
    }

    public void setSessionStore(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    // ------------------------------------------------------------------------
    // In-Memory Rebuilding & State Hydration
    // ------------------------------------------------------------------------

    public void reloadAllFromDatabase() {
        try (Connection conn = connectionProvider.getConnection()) {
            // Settings
            this.settings = settingsDao.load(conn);

            // Clock state
            Optional<String> baseStr = appStateDao.get(conn, "clock_base_instant");
            Optional<String> offsetStr = appStateDao.get(conn, "clock_offset_minutes");
            Instant baseTime;
            if (baseStr.isPresent()) {
                baseTime = Instant.parse(baseStr.get());
            } else {
                baseTime = Instant.now();
                appStateDao.set(conn, "clock_base_instant", baseTime.toString());
                appStateDao.set(conn, "clock_offset_minutes", "0");
            }
            this.clock = new SimulatedClock(baseTime);
            if (offsetStr.isPresent()) {
                long offset = Long.parseLong(offsetStr.get());
                if (offset > 0) {
                    this.clock.advance(offset);
                }
            }

            // Central QueueManager
            this.queueManager = new QueueManager(this.clock, this.settings, new SmartOpdStrategy());
            this.queueManager.resetAll();

            // Doctors and active/waiting queues
            List<Doctor> doctors = doctorDao.findAll(conn);
            for (Doctor d : doctors) {
                Optional<String> emergVal = appStateDao.get(conn, "doctor_consecutive_emergencies_" + d.getId());
                if (emergVal.isPresent()) {
                    try {
                        d.setConsecutiveEmergencies(Integer.parseInt(emergVal.get()));
                    } catch (Exception ignored) {
                    }
                }

                List<Patient> waiting = patientDao.findWaitingByDoctor(conn, d.getId());
                Patient active = null;
                List<Patient> inConsult = patientDao.findByStatus(conn, PatientStatus.IN_CONSULTATION);
                for (Patient p : inConsult) {
                    if (p.getDoctorId() != null && p.getDoctorId().equalsIgnoreCase(d.getId())) {
                        active = p;
                        break;
                    }
                }
                queueManager.loadDoctorQueueState(d, waiting, active);
            }

            // All patient records
            List<Patient> allPatients = patientDao.findAll(conn);
            for (Patient p : allPatients) {
                queueManager.addPatientRecord(p);
            }

            // Event log
            List<QueueEvent> events = eventDao.findAll(conn, 100);
            for (int i = events.size() - 1; i >= 0; i--) {
                queueManager.getEventLog().log(events.get(i));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to reload state from database", e);
        }
    }

    private void rebuildDoctorQueueFromDb(String doctorId) {
        if (doctorId == null) return;
        try (Connection conn = connectionProvider.getConnection()) {
            Optional<Doctor> docOpt = doctorDao.findById(conn, doctorId);
            if (docOpt.isPresent()) {
                Doctor d = docOpt.get();
                Optional<String> emergVal = appStateDao.get(conn, "doctor_consecutive_emergencies_" + d.getId());
                if (emergVal.isPresent()) {
                    try {
                        d.setConsecutiveEmergencies(Integer.parseInt(emergVal.get()));
                    } catch (Exception ignored) {
                    }
                }
                List<Patient> waiting = patientDao.findWaitingByDoctor(conn, doctorId);
                Patient active = null;
                List<Patient> inConsult = patientDao.findByStatus(conn, PatientStatus.IN_CONSULTATION);
                for (Patient p : inConsult) {
                    if (p.getDoctorId() != null && p.getDoctorId().equalsIgnoreCase(d.getId())) {
                        active = p;
                        break;
                    }
                }
                queueManager.loadDoctorQueueState(d, waiting, active);
            }

            // Re-sync all patients
            List<Patient> allPatients = patientDao.findAll(conn);
            queueManager.resetAll();
            for (Doctor d : doctorDao.findAll(conn)) {
                List<Patient> waiting = patientDao.findWaitingByDoctor(conn, d.getId());
                Patient active = null;
                List<Patient> inConsult = patientDao.findByStatus(conn, PatientStatus.IN_CONSULTATION);
                for (Patient p : inConsult) {
                    if (p.getDoctorId() != null && p.getDoctorId().equalsIgnoreCase(d.getId())) {
                        active = p;
                        break;
                    }
                }
                queueManager.loadDoctorQueueState(d, waiting, active);
            }
            for (Patient p : allPatients) {
                queueManager.addPatientRecord(p);
            }

            // Sync in-memory event log with committed DB events
            queueManager.getEventLog().clear();
            List<QueueEvent> events = eventDao.findAll(conn, 100);
            for (int i = events.size() - 1; i >= 0; i--) {
                queueManager.getEventLog().log(events.get(i));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to rebuild doctor queue from database", e);
        }
    }

    // Testing hook to simulate mid-transaction failure
    private java.util.function.Consumer<Connection> preCommitHook = null;

    public void setPreCommitHook(java.util.function.Consumer<Connection> preCommitHook) {
        this.preCommitHook = preCommitHook;
    }

    // ------------------------------------------------------------------------
    // Mutating Operations (Serialized with ReentrantLock & Write-Through ACID)
    // ------------------------------------------------------------------------

    public RegistrationResult registerPatient(PatientRegistrationRequest request) {
        writeLock.lock();
        try {
            if (request == null) throw new ValidationException("Registration request cannot be null.");
            Doctor doctor = queueManager.getDoctor(request.getDoctorId());
            if (doctor == null) {
                throw new ValidationException("Please select an active OPD doctor.");
            }
            if (doctor.getStatus() == DoctorStatus.OFF_DUTY) {
                throw new DoctorUnavailableException("Doctor " + doctor.getName() + " is currently Off Duty. Cannot assign new patients.");
            }

            Instant now = clock.now();
            Category category = request.getCategory() != null ? request.getCategory() : Category.WALK_IN;

            return transactionHelper.execute(conn -> {
                // 1. Atomic token sequence generation
                int seq = tokenSequenceDao.nextValue(conn, doctor.getDeptCode(), category.getCode());
                Token token = new Token(doctor.getDeptCode(), category.getCode(), seq);

                // 2. Polymorphic patient creation & validation
                Patient patient = PatientFactory.createPatient(request, doctor, token, now);

                // 3. Persist patient to database
                patientDao.insert(conn, patient);

                // 4. Capture positions & audit events
                Map<String, Integer> prePositions = queueManager.captureQueuePositions(now);

                DoctorQueue dq = queueManager.getDoctorQueue(doctor.getId());
                dq.addPatient(patient);
                queueManager.addPatientRecord(patient);

                Map<String, Integer> postPositions = queueManager.captureQueuePositions(now);
                List<QueueEvent> shiftEvents = queueManager.detectPositionChanges(prePositions, postPositions, now);
                for (QueueEvent shift : shiftEvents) {
                    eventDao.append(conn, shift);
                }

                EventType eventType = patient.getCategory() == Category.EMERGENCY
                        ? EventType.EMERGENCY_OVERRIDE
                        : EventType.REGISTERED;

                String reasonText = patient.getCategory() == Category.EMERGENCY
                        ? "Emergency override: " + patient.getName() + " (Sev " + patient.getSeverity() + ") registered into " + doctor.getName() + "'s queue."
                        : "Token " + token.getValue() + " registered for " + doctor.getName() + " (" + patient.getCategory().getDisplayName() + ", Sev " + patient.getSeverity() + ").";

                QueueEvent regEvent = new QueueEvent(eventType, token.getValue(), doctor.getId(), reasonText, now);
                eventDao.append(conn, regEvent);
                queueManager.getEventLog().log(regEvent);

                List<Patient> ordered = dq.getOrdered(now);
                WaitEstimate estimate = WaitTimeEstimator.estimate(patient, doctor, dq.getActivePatient(), ordered, now);
                String explanation = queueManager.getStrategy().explain(patient, now, settings);

                if (preCommitHook != null) {
                    preCommitHook.accept(conn);
                }

                return new RegistrationResult(patient, estimate.getPosition(), estimate.getWaitMinutes(), estimate.getEstimatedTimeStr(), explanation);
            });
        } catch (Exception e) {
            if (request != null && request.getDoctorId() != null) {
                rebuildDoctorQueueFromDb(request.getDoctorId());
            }
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new DataAccessException("Patient registration failed", e);
        } finally {
            writeLock.unlock();
        }
    }

    public Patient callNext(String doctorId) {
        writeLock.lock();
        try {
            DoctorQueue queue = queueManager.getDoctorQueue(doctorId);
            if (queue == null) throw new ValidationException("Doctor queue not found for ID: " + doctorId);

            Instant now = clock.now();
            Map<String, Integer> prePositions = queueManager.captureQueuePositions(now);

            Patient called = queue.callNext(now, queueManager.getEventLog());

            try {
                transactionHelper.execute(conn -> {
                    patientDao.update(conn, called);
                    doctorDao.update(conn, queue.getDoctor());
                    appStateDao.set(conn, "doctor_consecutive_emergencies_" + doctorId, String.valueOf(queue.getDoctor().getConsecutiveEmergencies()));

                    // Persist the latest event
                    List<QueueEvent> recent = queueManager.getEvents(doctorId, null, 1);
                    if (!recent.isEmpty()) {
                        eventDao.append(conn, recent.get(0));
                    }

                    Map<String, Integer> postPositions = queueManager.captureQueuePositions(now);
                    List<QueueEvent> shiftEvents = queueManager.detectPositionChanges(prePositions, postPositions, now);
                    for (QueueEvent shift : shiftEvents) {
                        eventDao.append(conn, shift);
                    }
                    return null;
                });
                return called;
            } catch (Exception e) {
                rebuildDoctorQueueFromDb(doctorId);
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new DataAccessException("Call next patient failed", e);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public Patient completeConsultation(String doctorId) {
        writeLock.lock();
        try {
            DoctorQueue queue = queueManager.getDoctorQueue(doctorId);
            if (queue == null) throw new ValidationException("Doctor queue not found for ID: " + doctorId);

            Instant now = clock.now();
            Map<String, Integer> prePositions = queueManager.captureQueuePositions(now);

            Patient completed = queue.completeConsultation(now, queueManager.getEventLog());

            try {
                transactionHelper.execute(conn -> {
                    patientDao.update(conn, completed);
                    doctorDao.update(conn, queue.getDoctor()); // Persist updated avgConsultMinutes!

                    List<QueueEvent> recent = queueManager.getEvents(doctorId, null, 1);
                    if (!recent.isEmpty()) {
                        eventDao.append(conn, recent.get(0));
                    }

                    Map<String, Integer> postPositions = queueManager.captureQueuePositions(now);
                    List<QueueEvent> shiftEvents = queueManager.detectPositionChanges(prePositions, postPositions, now);
                    for (QueueEvent shift : shiftEvents) {
                        eventDao.append(conn, shift);
                    }
                    return null;
                });
                return completed;
            } catch (Exception e) {
                rebuildDoctorQueueFromDb(doctorId);
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new DataAccessException("Complete consultation failed", e);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public Patient markNoShow(String tokenNumber) {
        writeLock.lock();
        try {
            if (tokenNumber == null) throw new ValidationException("Token number cannot be null.");
            Instant now = clock.now();
            Patient target = queueManager.markNoShow(tokenNumber);

            try {
                transactionHelper.execute(conn -> {
                    patientDao.update(conn, target);
                    List<QueueEvent> recent = queueManager.getEventLog().getAllEvents();
                    if (!recent.isEmpty()) {
                        eventDao.append(conn, recent.get(0));
                    }
                    return null;
                });
                return target;
            } catch (Exception e) {
                rebuildDoctorQueueFromDb(target.getDoctorId());
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new DataAccessException("Mark no-show failed", e);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public Patient skipPatient(String tokenNumber, String reason) {
        writeLock.lock();
        try {
            if (tokenNumber == null) throw new ValidationException("Token number cannot be null.");
            Instant now = clock.now();
            Patient target = queueManager.skipPatient(tokenNumber, reason);

            try {
                transactionHelper.execute(conn -> {
                    patientDao.update(conn, target);
                    List<QueueEvent> recent = queueManager.getEventLog().getAllEvents();
                    if (!recent.isEmpty()) {
                        eventDao.append(conn, recent.get(0));
                    }
                    return null;
                });
                return target;
            } catch (Exception e) {
                rebuildDoctorQueueFromDb(target.getDoctorId());
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new DataAccessException("Skip patient failed", e);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void setDoctorStatus(String doctorId, DoctorStatus status) {
        writeLock.lock();
        try {
            Doctor doctor = queueManager.getDoctor(doctorId);
            if (doctor == null) throw new ValidationException("Doctor not found: " + doctorId);
            doctor.setStatus(status);

            transactionHelper.execute(conn -> {
                doctorDao.update(conn, doctor);
                QueueEvent event = new QueueEvent(
                        EventType.CONFIG_CHANGE,
                        "SYSTEM",
                        doctorId,
                        "Doctor " + doctor.getName() + " status changed to " + status.getDisplayName() + ".",
                        clock.now()
                );
                eventDao.append(conn, event);
                queueManager.getEventLog().log(event);
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void advanceClock(long minutes) {
        writeLock.lock();
        try {
            int preEventsCount = queueManager.getEventLog().getAllEvents().size();
            queueManager.advanceClock(minutes);
            int postEventsCount = queueManager.getEventLog().getAllEvents().size();

            transactionHelper.execute(conn -> {
                appStateDao.set(conn, "clock_offset_minutes", String.valueOf(clock.getOffsetMinutes()));
                appStateDao.set(conn, "clock_base_instant", clock.getBaseInstant().toString());

                // Persist any newly triggered fairness boost / position change events
                List<QueueEvent> all = queueManager.getEventLog().getAllEvents();
                int newCount = postEventsCount - preEventsCount;
                for (int i = 0; i < newCount && i < all.size(); i++) {
                    eventDao.append(conn, all.get(i));
                }
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void resetClock() {
        writeLock.lock();
        try {
            queueManager.resetClock();
            transactionHelper.execute(conn -> {
                appStateDao.set(conn, "clock_offset_minutes", "0");
                appStateDao.set(conn, "clock_base_instant", clock.getBaseInstant().toString());
                QueueEvent event = new QueueEvent(EventType.CONFIG_CHANGE, "SYSTEM", "SYSTEM", "Simulated demo clock reset.", clock.now());
                eventDao.append(conn, event);
                queueManager.getEventLog().log(event);
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void updateSettings(Settings newSettings) {
        writeLock.lock();
        try {
            if (newSettings == null) throw new ValidationException("Settings cannot be null.");
            this.settings = newSettings.copy();
            queueManager.updateSettings(this.settings);

            transactionHelper.execute(conn -> {
                settingsDao.save(conn, this.settings);
                QueueEvent event = new QueueEvent(EventType.CONFIG_CHANGE, "SYSTEM", "SYSTEM", "Scoring weights updated.", clock.now());
                eventDao.append(conn, event);
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void resetSettings() {
        writeLock.lock();
        try {
            Settings def = new Settings();
            this.settings = def;
            queueManager.resetSettings();

            transactionHelper.execute(conn -> {
                settingsDao.save(conn, def);
                QueueEvent event = new QueueEvent(EventType.CONFIG_CHANGE, "SYSTEM", "SYSTEM", "Reset scoring weights to system defaults.", clock.now());
                eventDao.append(conn, event);
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void addDoctor(Doctor doctor) {
        writeLock.lock();
        try {
            if (doctor == null) throw new ValidationException("Doctor cannot be null.");
            transactionHelper.execute(conn -> {
                doctorDao.insert(conn, doctor);
                queueManager.registerDoctor(doctor);
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void updateDoctor(Doctor doctor) {
        writeLock.lock();
        try {
            if (doctor == null) throw new ValidationException("Doctor cannot be null.");
            transactionHelper.execute(conn -> {
                doctorDao.update(conn, doctor);
                Doctor existing = queueManager.getDoctor(doctor.getId());
                if (existing != null) {
                    existing.setName(doctor.getName());
                    existing.setRoom(doctor.getRoom());
                    existing.setDepartment(doctor.getDepartment());
                    existing.setDeptCode(doctor.getDeptCode());
                    existing.setWorkingHours(doctor.getWorkingHours());
                    existing.setAvgConsultMinutes(doctor.getAvgConsultMinutes());
                    existing.setEmergencyBufferSlots(doctor.getEmergencyBufferSlots());
                    existing.setStatus(doctor.getStatus());
                }
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void deleteDoctor(String doctorId) {
        writeLock.lock();
        try {
            queueManager.deleteDoctor(doctorId); // Enforces waiting/consultation check
            String removedUsername = transactionHelper.execute(conn -> {
                Optional<UserAccount> userOpt = userDao.findByDoctorId(conn, doctorId);
                String username = null;
                if (userOpt.isPresent()) {
                    username = userOpt.get().getUsername();
                    userDao.deleteByUsername(conn, username);
                }
                doctorDao.delete(conn, doctorId);
                return username;
            });
            if (sessionStore != null && removedUsername != null) {
                sessionStore.invalidateSessionsForUser(removedUsername);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void addDepartment(String code, String name) {
        writeLock.lock();
        try {
            transactionHelper.execute(conn -> {
                departmentDao.insert(conn, new Department(code, name));
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void resetDemoData() {
        writeLock.lock();
        try {
            transactionHelper.execute(conn -> {
                try (var s = conn.createStatement()) {
                    s.execute("DELETE FROM queue_event");
                    s.execute("DELETE FROM patient");
                    s.execute("DELETE FROM doctor");
                    s.execute("DELETE FROM department");
                    s.execute("DELETE FROM token_sequence");
                    s.execute("DELETE FROM settings");
                    s.execute("DELETE FROM app_state");
                    s.execute("DELETE FROM user_account");
                }
                new DefaultsInitializer(connectionProvider).initialize(conn);
                return null;
            });
            reloadAllFromDatabase();
        } finally {
            writeLock.unlock();
        }
    }

    // ------------------------------------------------------------------------
    // Read-Only Queries (No write lock needed)
    // ------------------------------------------------------------------------

    public Optional<AuthResult> authenticate(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        try (Connection conn = connectionProvider.getConnection()) {
            Optional<UserAccount> userOpt = userDao.findByUsername(conn, username);
            if (userOpt.isPresent()) {
                UserAccount u = userOpt.get();
                if (PasswordHasher.verify(password, u.getSalt(), u.getPasswordHash())) {
                    return Optional.of(new AuthResult(u.getUsername(), u.getRole(), u.getDisplayName(), u.getDoctorId()));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Authentication lookup failed", e);
        }
    }

    public List<Patient> getOrderedQueue(String doctorId) {
        DoctorQueue dq = queueManager.getDoctorQueue(doctorId);
        if (dq == null) return Collections.emptyList();
        return dq.getOrdered(clock.now());
    }

    public List<QueueEvent> getEvents(String doctorId, String tokenNumber, int limit) {
        return queueManager.getEvents(doctorId, tokenNumber, limit);
    }

    public List<QueueEvent> getTokenTimeline(String tokenNumber) {
        return queueManager.getTokenTimeline(tokenNumber);
    }

    public Patient getPatientByToken(String tokenNumber) {
        return queueManager.getPatientByToken(tokenNumber);
    }

    public Doctor getDoctor(String doctorId) {
        return queueManager.getDoctor(doctorId);
    }

    public List<Doctor> getDoctors() {
        return queueManager.getDoctors();
    }

    public Settings getSettings() {
        return queueManager.getSettings();
    }

    public Instant getNow() {
        return clock.now();
    }

    public long getClockOffsetMinutes() {
        return clock instanceof SimulatedClock ? ((SimulatedClock) clock).getOffsetMinutes() : 0;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    // ------------------------------------------------------------------------
    // API Support & Query Facade
    // ------------------------------------------------------------------------

    public RegistrationResult previewPatient(PatientRegistrationRequest request) {
        if (request == null) throw new ValidationException("Registration request cannot be null.");
        Doctor doctor = queueManager.getDoctor(request.getDoctorId());
        if (doctor == null) {
            throw new ValidationException("Please select an active OPD doctor.");
        }
        if (doctor.getStatus() == DoctorStatus.OFF_DUTY) {
            throw new DoctorUnavailableException("Doctor " + doctor.getName() + " is currently Off Duty. Cannot assign new patients.");
        }

        Instant now = clock.now();
        Category category = request.getCategory() != null ? request.getCategory() : Category.WALK_IN;

        int nextSeq = 1;
        try (Connection conn = connectionProvider.getConnection()) {
            try (var ps = conn.prepareStatement("SELECT last_value FROM token_sequence WHERE department_code = ? AND category_code = ?")) {
                ps.setString(1, doctor.getDeptCode());
                ps.setString(2, category.getCode());
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        nextSeq = rs.getInt(1) + 1;
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query token sequence for preview", e);
        }

        Token predictedToken = new Token(doctor.getDeptCode(), category.getCode(), nextSeq);
        Patient transientPatient = PatientFactory.createPatient(request, doctor, predictedToken, now);

        DoctorQueue dq = queueManager.getDoctorQueue(doctor.getId());
        List<Patient> candidateQueue = new ArrayList<>(dq != null ? dq.getOrdered(now) : Collections.emptyList());
        candidateQueue.add(transientPatient);

        PriorityStrategy strat = queueManager.getStrategy();
        candidateQueue.sort((p1, p2) -> {
            double s1 = strat.calculateScore(p1, now, settings);
            double s2 = strat.calculateScore(p2, now, settings);
            if (Double.compare(s2, s1) != 0) {
                return Double.compare(s2, s1);
            }
            return p1.getRegisteredAt().compareTo(p2.getRegisteredAt());
        });

        int position = candidateQueue.indexOf(transientPatient) + 1;
        Patient active = dq != null ? dq.getActivePatient() : null;
        WaitEstimate estimate = WaitTimeEstimator.estimate(transientPatient, doctor, active, candidateQueue, now);
        String explanation = strat.explain(transientPatient, now, settings);

        return new RegistrationResult(transientPatient, position, estimate.getWaitMinutes(), estimate.getEstimatedTimeStr(), explanation);
    }

    public List<Department> getDepartments() {
        try (Connection conn = connectionProvider.getConnection()) {
            return departmentDao.findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to fetch departments", e);
        }
    }

    public List<Patient> getPatients(String search) {
        List<Patient> all = queueManager.getAllPatients();
        if (search == null || search.trim().isEmpty()) {
            return all;
        }
        String q = search.trim().toLowerCase();
        return all.stream()
                .filter(p -> (p.getToken() != null && p.getToken().getValue().toLowerCase().contains(q))
                        || (p.getName() != null && p.getName().toLowerCase().contains(q))
                        || (p.getDoctorName() != null && p.getDoctorName().toLowerCase().contains(q)))
                .toList();
    }

    public List<String> getSampleTokens() {
        List<String> samples = new ArrayList<>();
        for (Doctor d : queueManager.getDoctors()) {
            DoctorQueue dq = queueManager.getDoctorQueue(d.getId());
            if (dq != null) {
                for (Patient p : dq.getOrdered(clock.now())) {
                    if (p.getStatus() == PatientStatus.WAITING && p.getToken() != null) {
                        samples.add(p.getToken().getValue());
                        if (samples.size() >= 5) {
                            return samples;
                        }
                    }
                }
            }
        }
        return samples;
    }

    public List<com.smartopd.api.dto.PublicBoardDoctorDto> getPublicBoardData() {
        Instant now = clock.now();
        List<com.smartopd.api.dto.PublicBoardDoctorDto> board = new ArrayList<>();
        for (Doctor doctor : queueManager.getDoctors()) {
            DoctorQueue dq = queueManager.getDoctorQueue(doctor.getId());
            Patient active = dq != null ? dq.getActivePatient() : null;
            String nowServingToken = (active != null && active.getToken() != null) ? active.getToken().getValue() : null;

            List<com.smartopd.api.dto.PublicTokenEstimateDto> next5 = new ArrayList<>();
            if (dq != null) {
                List<Patient> ordered = dq.getOrdered(now);
                for (int i = 0; i < Math.min(5, ordered.size()); i++) {
                    Patient p = ordered.get(i);
                    WaitEstimate est = WaitTimeEstimator.estimate(p, doctor, active, ordered, now);
                    boolean isEmergency = p.getCategory() == Category.EMERGENCY || p.getSeverity() == 5;
                    next5.add(new com.smartopd.api.dto.PublicTokenEstimateDto(
                            p.getToken().getValue(),
                            p.getCategory().getDisplayName(),
                            isEmergency,
                            est.getEstimatedTimeStr()
                    ));
                }
            }

            board.add(new com.smartopd.api.dto.PublicBoardDoctorDto(
                    doctor.getId(),
                    doctor.getName(),
                    doctor.getDepartment(),
                    doctor.getRoom(),
                    doctor.getStatus().getDisplayName(),
                    nowServingToken,
                    next5
            ));
        }
        return board;
    }

    public com.smartopd.api.dto.PublicTrackerResponse getPublicQueueSummary(String tokenNumber) {
        if (tokenNumber == null || tokenNumber.isBlank()) return null;
        Patient patient = queueManager.getPatientByToken(tokenNumber);
        if (patient == null) return null;

        Instant now = clock.now();
        Doctor doctor = queueManager.getDoctor(patient.getDoctorId());
        DoctorQueue dq = doctor != null ? queueManager.getDoctorQueue(doctor.getId()) : null;
        Patient active = dq != null ? dq.getActivePatient() : null;
        List<Patient> ordered = dq != null ? dq.getOrdered(now) : Collections.emptyList();

        WaitEstimate timing = WaitTimeEstimator.estimate(patient, doctor, active, ordered, now);
        boolean isBoosted = queueManager.getStrategy().isFairnessBoosted(patient, now, settings);

        // Sanitize explanation for zero-PII public tracker without severity numbers
        String publicExplanation;
        long waitedMinutes = timing.getWaitMinutes();
        int threshold = patient.fairnessThresholdMinutes(settings);
        if (patient.getSeverity() == 5 || patient.getCategory() == Category.EMERGENCY) {
            publicExplanation = "Urgent medical priority: critical case escalated to top of queue ahead of routine visits.";
        } else if (patient.getSeverity() == 4) {
            publicExplanation = "Higher medical priority with " + waitedMinutes + " mins waiting time.";
        } else if (waitedMinutes >= threshold) {
            publicExplanation = "Fairness boost applied (+" + (int) settings.getFairnessBoost() + " pts): waiting time ("
                    + waitedMinutes + " min) exceeded " + threshold + "-min threshold.";
        } else if (patient.getCategory() == Category.SCHEDULED && patient.getAppointmentTime() != null) {
            publicExplanation = "Scheduled slot (" + patient.getAppointmentTime() + ") with standard priority and "
                    + waitedMinutes + " mins waiting time.";
        } else {
            publicExplanation = "Standard queue priority: standard priority with " + waitedMinutes + " mins in queue.";
        }

        // First name only for privacy-safe greeting
        String firstName = "Patient";
        if (patient.getName() != null && !patient.getName().isBlank()) {
            firstName = patient.getName().trim().split("\\s+")[0];
        }

        boolean isEmergencyInRoom = active != null && (active.getSeverity() == 5 || active.getCategory() == Category.EMERGENCY);
        boolean isEmergencyAhead = false;
        if (patient.getStatus() == PatientStatus.WAITING) {
            int idx = ordered.indexOf(patient);
            if (idx > 0) {
                for (int i = 0; i < idx; i++) {
                    Patient ahead = ordered.get(i);
                    if (ahead.getSeverity() == 5 || ahead.getCategory() == Category.EMERGENCY) {
                        isEmergencyAhead = true;
                        break;
                    }
                }
            }
        }

        int position = patient.getStatus() == PatientStatus.WAITING ? timing.getPosition() : (patient.getStatus() == PatientStatus.IN_CONSULTATION ? 0 : -1);
        int patientsAhead = patient.getStatus() == PatientStatus.WAITING ? Math.max(0, timing.getPosition() - 1) : 0;
        boolean isNext = patient.getStatus() == PatientStatus.WAITING && timing.getPosition() == 1;

        List<QueueEvent> rawTimeline = queueManager.getTokenTimeline(tokenNumber);
        List<com.smartopd.api.dto.PublicTimelineEventDto> timeline = new ArrayList<>();
        for (QueueEvent ev : rawTimeline) {
            String safeReason = ev.getReason();
            if (safeReason != null) {
                if (patient.getName() != null && !patient.getName().isBlank()) {
                    safeReason = safeReason.replace(" (" + patient.getName() + ")", "")
                                           .replace(patient.getName(), firstName);
                }
                safeReason = safeReason.replaceAll("(?i)\\bSev 5\\b", "urgent medical priority")
                                       .replaceAll("(?i)\\bSev 4\\b", "higher medical priority")
                                       .replaceAll("(?i)\\bSev [1-3]\\b", "standard priority")
                                       .replaceAll("(?i)\\bSeverity Level [1-5]\\b", "medical priority")
                                       .replaceAll("(?i)\\bSeverity [1-5]\\b", "medical priority")
                                       .replaceAll("(?i)\\bSeverity\\s*\\d+\\b", "medical priority");
            }
            timeline.add(new com.smartopd.api.dto.PublicTimelineEventDto(
                    ev.getTimestamp().toString(),
                    ev.getType().name(),
                    safeReason
            ));
        }

        return new com.smartopd.api.dto.PublicTrackerResponse(
                patient.getToken().getValue(),
                firstName,
                patient.getDoctorName(),
                patient.getDepartment(),
                patient.getRoom(),
                patient.getCategory().getDisplayName(),
                patient.getStatus().getDisplayName(),
                position,
                patientsAhead,
                timing.getEstimatedTimeStr(),
                timing.getWaitMinutes(),
                publicExplanation,
                isBoosted,
                isNext,
                isEmergencyInRoom || isEmergencyAhead,
                timeline
        );
    }

    public com.smartopd.api.dto.DoctorQueueOverviewDto getDoctorQueueOverview(String doctorId) {
        Doctor doctor = queueManager.getDoctor(doctorId);
        if (doctor == null) throw new ValidationException("Doctor not found: " + doctorId);

        Instant now = clock.now();
        DoctorQueue dq = queueManager.getDoctorQueue(doctorId);
        Patient active = dq != null ? dq.getActivePatient() : null;
        com.smartopd.api.dto.QueuePatientDto nowServingDto = active != null ? toQueuePatientDto(active, 0, now, dq) : null;

        List<com.smartopd.api.dto.QueuePatientDto> waitingList = new ArrayList<>();
        if (dq != null) {
            List<Patient> ordered = dq.getOrdered(now);
            for (int i = 0; i < ordered.size(); i++) {
                waitingList.add(toQueuePatientDto(ordered.get(i), i + 1, now, dq));
            }
        }

        return new com.smartopd.api.dto.DoctorQueueOverviewDto(
                doctor.getId(),
                doctor.getName(),
                doctor.getDepartment(),
                doctor.getDeptCode(),
                doctor.getRoom(),
                doctor.getStatus().getDisplayName(),
                doctor.getAvgConsultMinutes(),
                nowServingDto,
                waitingList
        );
    }

    public List<com.smartopd.api.dto.DoctorQueueOverviewDto> getAllQueuesOverview() {
        List<com.smartopd.api.dto.DoctorQueueOverviewDto> list = new ArrayList<>();
        for (Doctor d : queueManager.getDoctors()) {
            list.add(getDoctorQueueOverview(d.getId()));
        }
        return list;
    }

    public com.smartopd.api.dto.QueuePatientDto toQueuePatientDto(Patient p, int pos, Instant now, DoctorQueue dq) {
        Doctor d = dq != null ? dq.getDoctor() : null;
        Patient active = dq != null ? dq.getActivePatient() : null;
        List<Patient> ordered = dq != null ? dq.getOrdered(now) : Collections.emptyList();
        WaitEstimate est = WaitTimeEstimator.estimate(p, d, active, ordered, now);
        double score = queueManager.getStrategy().calculateScore(p, now, settings);
        boolean boosted = queueManager.getStrategy().isFairnessBoosted(p, now, settings);
        boolean emerg = p.getCategory() == Category.EMERGENCY || p.getSeverity() == 5;
        String explanation = queueManager.getStrategy().explain(p, now, settings);

        return new com.smartopd.api.dto.QueuePatientDto(
                p.getId(),
                p.getToken() != null ? p.getToken().getValue() : null,
                p.getName(),
                p.getAge(),
                p.getGender(),
                p.getPhone(),
                p.getCategory() != null ? p.getCategory().getDisplayName() : null,
                p.getSeverity(),
                p.getRegisteredAt() != null ? p.getRegisteredAt().toString() : null,
                est.getWaitMinutes(),
                score,
                boosted,
                emerg,
                pos,
                est.getEstimatedTimeStr(),
                explanation,
                p.getAppointmentTime(),
                p.getChiefComplaint(),
                p.getStatus() != null ? p.getStatus().getDisplayName() : null,
                p.isSkipped()
        );
    }

    public com.smartopd.api.dto.AnalyticsResponse getAnalytics() {
        Instant now = clock.now();
        List<Patient> patients = queueManager.getAllPatients();
        List<Doctor> doctors = queueManager.getDoctors();

        Map<String, Integer> totalsByCategory = new LinkedHashMap<>();
        totalsByCategory.put("Emergency", 0);
        totalsByCategory.put("Scheduled", 0);
        totalsByCategory.put("Walk-in", 0);

        Map<Integer, Integer> severityDistribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) severityDistribution.put(i, 0);

        int waitingCount = 0;
        int inConsultationCount = 0;
        int completedCount = 0;
        int noShowCount = 0;
        int emergencyOverrideCount = 0;
        int fairnessBoostCount = 0;

        long totalWaitMinutes = 0;
        int waitCalculatedCount = 0;
        Map<String, Long> categoryWaitSums = new HashMap<>();
        Map<String, Integer> categoryWaitCounts = new HashMap<>();
        categoryWaitSums.put("Emergency", 0L);
        categoryWaitSums.put("Scheduled", 0L);
        categoryWaitSums.put("Walk-in", 0L);
        categoryWaitCounts.put("Emergency", 0);
        categoryWaitCounts.put("Scheduled", 0);
        categoryWaitCounts.put("Walk-in", 0);

        long maxWait = 0;
        String maxWaitToken = "—";
        String maxWaitName = "—";
        String maxWaitDoc = "—";
        String maxWaitCat = "—";

        // Hourly buckets 8 to 18
        List<com.smartopd.api.dto.AnalyticsResponse.HourlyBucketDto> hourlyBuckets = new ArrayList<>();
        int[] hourlyCounts = new int[11]; // 8..18
        for (int h = 8; h <= 18; h++) {
            String hourStr = String.format("%02d:00", h);
            String label = (h == 12 ? "12 PM" : (h > 12 ? (h - 12) + " PM" : h + " AM"));
            hourlyBuckets.add(new com.smartopd.api.dto.AnalyticsResponse.HourlyBucketDto(hourStr, label, 0));
        }

        for (Patient p : patients) {
            String catName = p.getCategory() != null ? p.getCategory().getDisplayName() : "Walk-in";
            totalsByCategory.put(catName, totalsByCategory.getOrDefault(catName, 0) + 1);
            severityDistribution.put(p.getSeverity(), severityDistribution.getOrDefault(p.getSeverity(), 0) + 1);

            if (p.getStatus() == PatientStatus.WAITING) waitingCount++;
            else if (p.getStatus() == PatientStatus.IN_CONSULTATION) inConsultationCount++;
            else if (p.getStatus() == PatientStatus.COMPLETED) completedCount++;
            else if (p.getStatus() == PatientStatus.NO_SHOW) noShowCount++;

            if (p.getSeverity() == 5 || p.getCategory() == Category.EMERGENCY) emergencyOverrideCount++;
            if (queueManager.getStrategy().isFairnessBoosted(p, now, settings)) fairnessBoostCount++;

            Instant reg = p.getRegisteredAt() != null ? p.getRegisteredAt() : now;
            long waitMins = 0;
            if (p.getStatus() == PatientStatus.COMPLETED || p.getStatus() == PatientStatus.IN_CONSULTATION) {
                Instant start = p.getConsultationStartTime() != null ? p.getConsultationStartTime() : now;
                waitMins = Math.max(0, Duration.between(reg, start).toMinutes());
            } else if (p.getStatus() == PatientStatus.WAITING) {
                waitMins = Math.max(0, Duration.between(reg, now).toMinutes());
            }

            if (p.getStatus() != PatientStatus.NO_SHOW) {
                totalWaitMinutes += waitMins;
                waitCalculatedCount++;

                categoryWaitSums.put(catName, categoryWaitSums.getOrDefault(catName, 0L) + waitMins);
                categoryWaitCounts.put(catName, categoryWaitCounts.getOrDefault(catName, 0) + 1);

                if (waitMins > maxWait) {
                    maxWait = waitMins;
                    maxWaitToken = p.getToken() != null ? p.getToken().getValue() : "—";
                    maxWaitName = p.getName() != null ? p.getName() : "—";
                    maxWaitDoc = p.getDoctorName() != null ? p.getDoctorName() : "—";
                    maxWaitCat = catName;
                }
            }

            int hour = reg.atZone(java.time.ZoneId.systemDefault()).getHour();
            if (hour >= 8 && hour <= 18) {
                hourlyCounts[hour - 8]++;
            }
        }

        List<com.smartopd.api.dto.AnalyticsResponse.HourlyBucketDto> finalHourly = new ArrayList<>();
        for (int h = 8; h <= 18; h++) {
            String hourStr = String.format("%02d:00", h);
            String label = (h == 12 ? "12 PM" : (h > 12 ? (h - 12) + " PM" : h + " AM"));
            finalHourly.add(new com.smartopd.api.dto.AnalyticsResponse.HourlyBucketDto(hourStr, label, hourlyCounts[h - 8]));
        }

        int averageWaitOverall = waitCalculatedCount > 0 ? (int) Math.round((double) totalWaitMinutes / waitCalculatedCount) : 0;
        Map<String, Integer> averageWaitByCategory = new LinkedHashMap<>();
        for (String c : List.of("Emergency", "Scheduled", "Walk-in")) {
            int count = categoryWaitCounts.getOrDefault(c, 0);
            long sum = categoryWaitSums.getOrDefault(c, 0L);
            averageWaitByCategory.put(c, count > 0 ? (int) Math.round((double) sum / count) : 0);
        }

        List<com.smartopd.api.dto.AnalyticsResponse.DoctorWaitDto> docWaits = new ArrayList<>();
        List<com.smartopd.api.dto.AnalyticsResponse.DoctorUtilisationDto> docUtils = new ArrayList<>();
        int activeDocUtilSum = 0;
        int activeDocCount = 0;

        for (Doctor doc : doctors) {
            List<Patient> docPatients = patients.stream().filter(p -> doc.getId().equalsIgnoreCase(p.getDoctorId())).toList();
            long docWaitSum = 0;
            int docWaitCount = 0;
            int docWaiting = 0;
            int docCompleted = 0;
            int docInConsult = 0;

            for (Patient p : docPatients) {
                if (p.getStatus() == PatientStatus.WAITING) docWaiting++;
                if (p.getStatus() == PatientStatus.COMPLETED) docCompleted++;
                if (p.getStatus() == PatientStatus.IN_CONSULTATION) docInConsult++;

                Instant reg = p.getRegisteredAt() != null ? p.getRegisteredAt() : now;
                long waitMins = 0;
                if (p.getStatus() == PatientStatus.COMPLETED || p.getStatus() == PatientStatus.IN_CONSULTATION) {
                    Instant start = p.getConsultationStartTime() != null ? p.getConsultationStartTime() : now;
                    waitMins = Math.max(0, Duration.between(reg, start).toMinutes());
                    docWaitSum += waitMins;
                    docWaitCount++;
                } else if (p.getStatus() == PatientStatus.WAITING) {
                    waitMins = Math.max(0, Duration.between(reg, now).toMinutes());
                    docWaitSum += waitMins;
                    docWaitCount++;
                }
            }

            int avgDocWait = docWaitCount > 0 ? (int) Math.round((double) docWaitSum / docWaitCount) : 0;
            docWaits.add(new com.smartopd.api.dto.AnalyticsResponse.DoctorWaitDto(
                    doc.getId(), doc.getName(), doc.getDepartment(), doc.getRoom(), avgDocWait, docPatients.size(), docWaiting
            ));

            int utilPercent = 0;
            if (doc.getStatus() == DoctorStatus.OFF_DUTY) {
                utilPercent = 0;
            } else if (doc.getStatus() == DoctorStatus.ON_BREAK) {
                utilPercent = 45;
            } else {
                double expectedCases = 15.0;
                double actualCases = (docCompleted * 1.5) + (docInConsult * 1.0) + (docWaiting > 0 ? 3.0 : 0.0);
                utilPercent = Math.min(98, Math.max(35, (int) Math.round((actualCases / expectedCases) * 100)));
            }

            docUtils.add(new com.smartopd.api.dto.AnalyticsResponse.DoctorUtilisationDto(
                    doc.getId(), doc.getName(), doc.getDepartment(), doc.getRoom(),
                    doc.getStatus().getDisplayName(), utilPercent, docCompleted, docInConsult, docWaiting
            ));

            if (doc.getStatus() != DoctorStatus.OFF_DUTY) {
                activeDocUtilSum += utilPercent;
                activeDocCount++;
            }
        }

        int overallUtil = activeDocCount > 0 ? Math.round((float) activeDocUtilSum / activeDocCount) : 0;

        return new com.smartopd.api.dto.AnalyticsResponse(
                patients.size(),
                totalsByCategory,
                severityDistribution,
                waitingCount,
                inConsultationCount,
                completedCount,
                noShowCount,
                emergencyOverrideCount,
                fairnessBoostCount,
                averageWaitOverall,
                averageWaitByCategory,
                new com.smartopd.api.dto.AnalyticsResponse.LongestWaitDto(maxWait, maxWaitToken, maxWaitName, maxWaitDoc, maxWaitCat),
                finalHourly,
                docWaits,
                docUtils,
                overallUtil
        );
    }

    // ------------------------------------------------------------------------
    // First-Run Setup & Staff Account Management
    // ------------------------------------------------------------------------

    private void validateUsername(String username) {
        if (username == null || !username.matches("^[a-zA-Z0-9._]{3,30}$")) {
            throw new ValidationException("Username must be 3 to 30 characters containing only letters, digits, dots, or underscores.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*[a-zA-Z].*") || !password.matches(".*[0-9].*")) {
            throw new ValidationException("Password must be at least 8 characters long and contain both a letter and a digit.");
        }
    }

    public boolean needsSetup() {
        try (Connection conn = connectionProvider.getConnection()) {
            return userDao.count(conn) == 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check setup status", e);
        }
    }

    public void completeSetup(String username, String displayName, String password) {
        validateUsername(username);
        validatePassword(password);
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new ValidationException("Display name cannot be blank.");
        }

        writeLock.lock();
        try {
            transactionHelper.execute(conn -> {
                int userCount = userDao.count(conn);
                if (userCount > 0) {
                    throw new ConflictException("Setup already completed");
                }

                String salt = PasswordHasher.generateSalt();
                String hash = PasswordHasher.hashPassword(password, salt);
                UserAccount account = new UserAccount(
                        username.trim().toLowerCase(),
                        hash,
                        salt,
                        "RECEPTIONIST",
                        displayName.trim(),
                        null
                );
                userDao.insert(conn, account);
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public List<UserAccount> getStaffAccounts() {
        try (Connection conn = connectionProvider.getConnection()) {
            return userDao.findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query staff accounts", e);
        }
    }

    public UserAccount createStaffAccount(String username, String displayName, String password, String role, String doctorId) {
        validateUsername(username);
        validatePassword(password);
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new ValidationException("Display name cannot be blank.");
        }
        if (role == null || (!role.equals("RECEPTIONIST") && !role.equals("DOCTOR"))) {
            throw new ValidationException("Role must be 'RECEPTIONIST' or 'DOCTOR'.");
        }

        if ("DOCTOR".equals(role)) {
            if (doctorId == null || doctorId.trim().isEmpty()) {
                throw new ValidationException("Doctor ID is required when role is DOCTOR.");
            }
            if (getDoctor(doctorId.trim()) == null) {
                throw new ValidationException("Assigned doctor ID does not exist: " + doctorId);
            }
        } else {
            if (doctorId != null && !doctorId.trim().isEmpty()) {
                throw new ValidationException("Doctor ID must not be provided when role is RECEPTIONIST.");
            }
        }

        writeLock.lock();
        try {
            return transactionHelper.execute(conn -> {
                if (userDao.findByUsername(conn, username.trim()).isPresent()) {
                    throw new ConflictException("Username '" + username.trim() + "' already exists.");
                }
                if ("DOCTOR".equals(role)) {
                    if (userDao.findByDoctorId(conn, doctorId.trim()).isPresent()) {
                        throw new ConflictException("Doctor with ID '" + doctorId.trim() + "' already has an assigned login account.");
                    }
                }

                String salt = PasswordHasher.generateSalt();
                String hash = PasswordHasher.hashPassword(password, salt);
                String docIdVal = "DOCTOR".equals(role) ? doctorId.trim() : null;
                UserAccount account = new UserAccount(
                        username.trim().toLowerCase(),
                        hash,
                        salt,
                        role,
                        displayName.trim(),
                        docIdVal
                );
                userDao.insert(conn, account);
                return account;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void updateStaffPassword(String username, String newPassword) {
        validatePassword(newPassword);
        writeLock.lock();
        try {
            transactionHelper.execute(conn -> {
                Optional<UserAccount> userOpt = userDao.findByUsername(conn, username);
                if (userOpt.isEmpty()) {
                    throw new ResourceNotFoundException("Staff user not found: " + username);
                }
                String salt = PasswordHasher.generateSalt();
                String hash = PasswordHasher.hashPassword(newPassword, salt);
                userDao.updatePassword(conn, username, hash, salt);
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public void deleteStaffAccount(String requesterUsername, String targetUsername) {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new ValidationException("Target username is required.");
        }
        String normalizedTarget = targetUsername.trim().toLowerCase();
        if (requesterUsername != null && requesterUsername.trim().equalsIgnoreCase(normalizedTarget)) {
            throw new ConflictException("Cannot delete your own account.");
        }

        writeLock.lock();
        try {
            transactionHelper.execute(conn -> {
                Optional<UserAccount> userOpt = userDao.findByUsername(conn, normalizedTarget);
                if (userOpt.isEmpty()) {
                    throw new ResourceNotFoundException("Staff user not found: " + targetUsername);
                }
                UserAccount user = userOpt.get();
                if ("RECEPTIONIST".equalsIgnoreCase(user.getRole())) {
                    long receptionistCount = userDao.findAll(conn).stream()
                            .filter(u -> "RECEPTIONIST".equalsIgnoreCase(u.getRole()))
                            .count();
                    if (receptionistCount <= 1) {
                        throw new ConflictException("Cannot delete the last Front Desk account.");
                    }
                }
                userDao.deleteByUsername(conn, normalizedTarget);
                return null;
            });
        } finally {
            writeLock.unlock();
        }
    }

    public boolean verifyCurrentPassword(String username, String currentPassword) {
        if (username == null || currentPassword == null) return false;
        try (Connection conn = connectionProvider.getConnection()) {
            Optional<UserAccount> userOpt = userDao.findByUsername(conn, username);
            if (userOpt.isEmpty()) return false;
            UserAccount user = userOpt.get();
            return PasswordHasher.verify(currentPassword, user.getSalt(), user.getPasswordHash());
        } catch (SQLException e) {
            throw new DataAccessException("Failed to verify current password", e);
        }
    }

    public void updateUserPassword(String username, String newPassword) {
        updateStaffPassword(username, newPassword);
    }
}
