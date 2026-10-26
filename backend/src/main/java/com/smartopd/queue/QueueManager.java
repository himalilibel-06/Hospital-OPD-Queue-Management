package com.smartopd.queue;

import com.smartopd.config.Settings;
import com.smartopd.events.EventLog;
import com.smartopd.events.QueueEvent;
import com.smartopd.exception.ValidationException;
import com.smartopd.model.Category;
import com.smartopd.model.Doctor;
import com.smartopd.model.EventType;
import com.smartopd.model.Patient;
import com.smartopd.model.PatientFactory;
import com.smartopd.model.PatientRegistrationRequest;
import com.smartopd.model.Token;
import com.smartopd.model.TokenGenerator;
import com.smartopd.scoring.PriorityStrategy;
import com.smartopd.scoring.SmartOpdStrategy;
import com.smartopd.time.AppClock;
import com.smartopd.time.SimulatedClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * QueueManager - Central coordinator managing doctor queues, patient intake,
 * clock advances, audit logging, and queue position change detection.
 * 
 * ============================================================================
 * OOP Concept: Facade Pattern & Mediator Pattern
 * - Acts as the unified entry point for all OPD queue operations.
 * - Coordinates interactions between DoctorQueue, Settings, Strategy, Clock, and EventLog.
 * ============================================================================
 */
public class QueueManager {
    private final Map<String, Doctor> doctors = new ConcurrentHashMap<>();
    private final Map<String, DoctorQueue> doctorQueues = new ConcurrentHashMap<>();
    private final List<Patient> allPatients = new ArrayList<>();
    private final Set<String> fairnessBoostLoggedTokens = new HashSet<>();

    private Settings settings = new Settings();
    private PriorityStrategy strategy = new SmartOpdStrategy();
    private AppClock clock = new SimulatedClock();
    private TokenGenerator tokenGenerator = new TokenGenerator();
    private final EventLog eventLog = new EventLog();

    public QueueManager() {
    }

    public QueueManager(AppClock clock, Settings settings, PriorityStrategy strategy) {
        if (clock != null) this.clock = clock;
        if (settings != null) this.settings = settings;
        if (strategy != null) this.strategy = strategy;
    }

    // ------------------------------------------------------------------------
    // Doctor Management
    // ------------------------------------------------------------------------

    public synchronized void registerDoctor(Doctor doctor) {
        if (doctor == null) throw new ValidationException("Doctor cannot be null.");
        doctors.put(doctor.getId(), doctor);
        doctorQueues.put(doctor.getId(), new DoctorQueue(doctor, strategy, settings));
    }

    public synchronized void deleteDoctor(String doctorId) {
        if (doctorId == null || !doctors.containsKey(doctorId)) {
            throw new ValidationException("Doctor not found with ID \"" + doctorId + "\".");
        }
        Doctor doctor = doctors.get(doctorId);
        DoctorQueue queue = doctorQueues.get(doctorId);

        if (queue != null && (!queue.getWaitingPatients().isEmpty() || queue.getActivePatient() != null)) {
            int count = queue.getWaitingPatients().size() + (queue.getActivePatient() != null ? 1 : 0);
            throw new ValidationException(
                    "Cannot delete doctor " + doctor.getName() + " while " + count + " patient(s) are currently in queue or consultation."
            );
        }

        doctors.remove(doctorId);
        doctorQueues.remove(doctorId);
    }

    public Doctor getDoctor(String doctorId) {
        return doctors.get(doctorId);
    }

    public List<Doctor> getDoctors() {
        return Collections.unmodifiableList(new ArrayList<>(doctors.values()));
    }

    public DoctorQueue getDoctorQueue(String doctorId) {
        return doctorQueues.get(doctorId);
    }

    // ------------------------------------------------------------------------
    // Patient Registration
    // ------------------------------------------------------------------------

    public synchronized RegistrationResult registerPatient(PatientRegistrationRequest request) {
        if (request == null) throw new ValidationException("Registration request cannot be null.");
        Doctor doctor = doctors.get(request.getDoctorId());
        if (doctor == null) {
            throw new ValidationException("Please select an active OPD doctor.");
        }

        Instant now = clock.now();
        Map<String, Integer> prePositions = captureQueuePositions(now);

        Token token = tokenGenerator.nextToken(doctor, request.getCategory() != null ? request.getCategory() : Category.WALK_IN);
        Patient patient = PatientFactory.createPatient(request, doctor, token, now);

        DoctorQueue queue = doctorQueues.get(doctor.getId());
        queue.addPatient(patient);
        allPatients.add(patient);

        // Audit Event
        EventType eventType = patient.getCategory() == Category.EMERGENCY
                ? EventType.EMERGENCY_OVERRIDE
                : EventType.REGISTERED;

        String reasonText = patient.getCategory() == Category.EMERGENCY
                ? "Emergency override: " + patient.getName() + " (Sev " + patient.getSeverity() + ") registered into " + doctor.getName() + "'s queue."
                : "Token " + token.getValue() + " registered for " + doctor.getName() + " (" + patient.getCategory().getDisplayName() + ", Sev " + patient.getSeverity() + ").";

        eventLog.log(new QueueEvent(eventType, token.getValue(), doctor.getId(), reasonText, now));

        // Detect position changes for other patients
        Map<String, Integer> postPositions = captureQueuePositions(now);
        detectPositionChanges(prePositions, postPositions, now);

        List<Patient> ordered = queue.getOrdered(now);
        WaitEstimate estimate = WaitTimeEstimator.estimate(patient, doctor, queue.getActivePatient(), ordered, now);
        String explanation = strategy.explain(patient, now, settings);

        return new RegistrationResult(patient, estimate.getPosition(), estimate.getWaitMinutes(), estimate.getEstimatedTimeStr(), explanation);
    }

    // ------------------------------------------------------------------------
    // Doctor Consultation Actions
    // ------------------------------------------------------------------------

    public synchronized Patient callNext(String doctorId) {
        DoctorQueue queue = doctorQueues.get(doctorId);
        if (queue == null) throw new ValidationException("Doctor queue not found for ID: " + doctorId);

        Instant now = clock.now();
        Map<String, Integer> prePositions = captureQueuePositions(now);

        Patient called = queue.callNext(now, eventLog);

        Map<String, Integer> postPositions = captureQueuePositions(now);
        detectPositionChanges(prePositions, postPositions, now);

        return called;
    }

    public synchronized Patient completeConsultation(String doctorId) {
        DoctorQueue queue = doctorQueues.get(doctorId);
        if (queue == null) throw new ValidationException("Doctor queue not found for ID: " + doctorId);

        Instant now = clock.now();
        Map<String, Integer> prePositions = captureQueuePositions(now);

        Patient completed = queue.completeConsultation(now, eventLog);

        Map<String, Integer> postPositions = captureQueuePositions(now);
        detectPositionChanges(prePositions, postPositions, now);

        return completed;
    }

    public synchronized Patient markNoShow(String tokenNumber) {
        if (tokenNumber == null) throw new ValidationException("Token number cannot be null.");
        Instant now = clock.now();
        Map<String, Integer> prePositions = captureQueuePositions(now);

        for (DoctorQueue queue : doctorQueues.values()) {
            for (Patient p : queue.getWaitingPatients()) {
                if (p.getToken() != null && p.getToken().getValue().equalsIgnoreCase(tokenNumber.trim())) {
                    Patient noShow = queue.markNoShow(tokenNumber, now, eventLog);
                    Map<String, Integer> postPositions = captureQueuePositions(now);
                    detectPositionChanges(prePositions, postPositions, now);
                    return noShow;
                }
            }
        }
        throw new ValidationException("Token " + tokenNumber + " not found.");
    }

    public synchronized Patient skipPatient(String tokenNumber, String reason) {
        if (tokenNumber == null) throw new ValidationException("Token number cannot be null.");
        Instant now = clock.now();
        Map<String, Integer> prePositions = captureQueuePositions(now);

        for (DoctorQueue queue : doctorQueues.values()) {
            for (Patient p : queue.getWaitingPatients()) {
                if (p.getToken() != null && p.getToken().getValue().equalsIgnoreCase(tokenNumber.trim())) {
                    Patient skipped = queue.skipPatient(tokenNumber, reason, now, eventLog);
                    Map<String, Integer> postPositions = captureQueuePositions(now);
                    detectPositionChanges(prePositions, postPositions, now);
                    return skipped;
                }
            }
        }
        throw new ValidationException("Token " + tokenNumber + " not found.");
    }

    // ------------------------------------------------------------------------
    // Simulated Demo Clock Operations
    // ------------------------------------------------------------------------

    public synchronized void advanceClock(long minutes) {
        Instant now = clock.now();
        Map<String, Integer> prePositions = captureQueuePositions(now);

        if (clock instanceof SimulatedClock) {
            ((SimulatedClock) clock).advance(minutes);
        }
        Instant newNow = clock.now();

        // Check for patients that newly crossed fairness threshold
        for (DoctorQueue queue : doctorQueues.values()) {
            for (Patient p : queue.getWaitingPatients()) {
                String tokenStr = p.getToken().getValue();
                if (strategy.isFairnessBoosted(p, newNow, settings) && !fairnessBoostLoggedTokens.contains(tokenStr)) {
                    fairnessBoostLoggedTokens.add(tokenStr);
                    if (strategy instanceof SmartOpdStrategy) {
                        long waited = ((SmartOpdStrategy) strategy).getWaitedMinutes(p, newNow);
                        eventLog.log(new QueueEvent(
                                EventType.FAIRNESS_BOOST,
                                tokenStr,
                                p.getDoctorId(),
                                "Fairness boost applied to " + tokenStr + " (+" + (int) settings.getFairnessBoost()
                                + " pts): waited " + waited + " mins (exceeded threshold).",
                                newNow
                        ));
                    }
                }
            }
        }

        Map<String, Integer> postPositions = captureQueuePositions(newNow);
        detectPositionChanges(prePositions, postPositions, newNow);
    }

    public synchronized void resetClock() {
        if (clock instanceof SimulatedClock) {
            ((SimulatedClock) clock).reset();
        }
    }

    public Instant getNow() {
        return clock.now();
    }

    // ------------------------------------------------------------------------
    // Settings Operations
    // ------------------------------------------------------------------------

    public Settings getSettings() {
        return settings.copy();
    }

    public synchronized void resetSettings() {
        this.settings.resetToDefaults();
        doctorQueues.values().forEach(q -> q.setSettings(this.settings));
        eventLog.log(new QueueEvent(
                EventType.CONFIG_CHANGE,
                "SYSTEM",
                "SYSTEM",
                "Reset scoring weights to system defaults.",
                clock.now()
        ));
    }

    public synchronized void updateSettings(Settings newSettings) {
        if (newSettings == null) throw new ValidationException("Settings cannot be null.");
        this.settings = newSettings.copy();
        doctorQueues.values().forEach(q -> q.setSettings(this.settings));
        eventLog.log(new QueueEvent(
                EventType.CONFIG_CHANGE,
                "SYSTEM",
                "SYSTEM",
                "System scoring settings updated: Severity Weight=" + settings.getSeverityWeight()
                + ", Wait Weight/Min=" + settings.getWaitWeightPerMin()
                + ", Fairness Boost=" + settings.getFairnessBoost(),
                clock.now()
        ));
    }

    // ------------------------------------------------------------------------
    // Audit & Query Helpers
    // ------------------------------------------------------------------------

    public List<QueueEvent> getEvents(String doctorId, String tokenNumber, int limit) {
        return eventLog.getEvents(doctorId, tokenNumber, limit);
    }

    public List<QueueEvent> getTokenTimeline(String tokenNumber) {
        if (tokenNumber == null) return Collections.emptyList();
        String q = tokenNumber.trim().toLowerCase();
        List<QueueEvent> matching = eventLog.getAllEvents().stream()
                .filter(e -> e.getTokenNumber().toLowerCase().contains(q))
                .collect(Collectors.toList());
        // Return chronologically (oldest first)
        Collections.reverse(matching);
        return matching;
    }

    public Patient getPatientByToken(String tokenNumber) {
        if (tokenNumber == null) return null;
        String clean = tokenNumber.trim().toLowerCase();
        for (Patient p : allPatients) {
            if (p.getToken() != null && p.getToken().getValue().toLowerCase().equals(clean)) {
                return p;
            }
        }
        return null;
    }

    public EventLog getEventLog() {
        return eventLog;
    }

    public PriorityStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(PriorityStrategy strategy) {
        this.strategy = strategy;
        doctorQueues.values().forEach(q -> q.setStrategy(strategy));
    }

    // ------------------------------------------------------------------------
    // State Rebuilding & Synchronization Helpers
    // ------------------------------------------------------------------------

    public synchronized void resetAll() {
        doctors.clear();
        doctorQueues.clear();
        allPatients.clear();
        fairnessBoostLoggedTokens.clear();
    }

    public synchronized void loadDoctorQueueState(Doctor doctor, List<Patient> waiting, Patient active) {
        doctors.put(doctor.getId(), doctor);
        DoctorQueue dq = new DoctorQueue(doctor, strategy, settings);
        for (Patient p : waiting) {
            dq.addPatient(p);
        }
        dq.setActivePatient(active);
        doctorQueues.put(doctor.getId(), dq);
    }

    public synchronized void addPatientRecord(Patient patient) {
        if (patient != null && !allPatients.contains(patient)) {
            allPatients.add(patient);
        }
    }

    public synchronized void removePatientRecord(String tokenNumber) {
        allPatients.removeIf(p -> p.getToken() != null && p.getToken().getValue().equalsIgnoreCase(tokenNumber));
    }

    public List<Patient> getAllPatients() {
        return Collections.unmodifiableList(new ArrayList<>(allPatients));
    }

    public AppClock getClock() {
        return clock;
    }

    public void setClock(AppClock clock) {
        this.clock = clock;
    }

    public TokenGenerator getTokenGenerator() {
        return tokenGenerator;
    }

    public void setTokenGenerator(TokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    // ------------------------------------------------------------------------
    // Position Snapshot & Change Detection
    // ------------------------------------------------------------------------

    public Map<String, Integer> captureQueuePositions(Instant now) {
        Map<String, Integer> positions = new HashMap<>();
        for (DoctorQueue queue : doctorQueues.values()) {
            List<Patient> ordered = queue.getOrdered(now);
            for (int i = 0; i < ordered.size(); i++) {
                positions.put(ordered.get(i).getToken().getValue(), i + 1);
            }
        }
        return positions;
    }

    public List<QueueEvent> detectPositionChanges(Map<String, Integer> pre, Map<String, Integer> post, Instant now) {
        List<QueueEvent> events = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : post.entrySet()) {
            String token = entry.getKey();
            Integer postPos = entry.getValue();
            Integer prePos = pre.get(token);

            if (prePos != null && postPos != null) {
                Patient p = getPatientByToken(token);
                String docId = p != null ? p.getDoctorId() : "SYSTEM";

                if (postPos < prePos) {
                    String reason = p != null ? strategy.explain(p, now, settings) : "Priority score adjustment";
                    QueueEvent event = new QueueEvent(
                            EventType.MOVED_UP,
                            token,
                            docId,
                            "Token " + token + " moved from #" + prePos + " to #" + postPos + " in queue (" + reason + ").",
                            now
                    );
                    eventLog.log(event);
                    events.add(event);
                } else if (postPos > prePos) {
                    QueueEvent event = new QueueEvent(
                            EventType.MOVED_DOWN,
                            token,
                            docId,
                            "Token " + token + " moved from #" + prePos + " to #" + postPos + " due to higher priority admission.",
                            now
                    );
                    eventLog.log(event);
                    events.add(event);
                }
            }
        }
        return events;
    }
}
