package com.smartopd.api.controller;

import com.smartopd.api.dto.*;
import com.smartopd.api.security.RequiresRole;
import com.smartopd.config.Settings;
import com.smartopd.events.QueueEvent;
import com.smartopd.exception.DoctorUnavailableException;
import com.smartopd.exception.ResourceNotFoundException;
import com.smartopd.model.*;
import com.smartopd.queue.DoctorQueue;
import com.smartopd.queue.RegistrationResult;
import com.smartopd.queue.WaitEstimate;
import com.smartopd.queue.WaitTimeEstimator;
import com.smartopd.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * ReceptionController - Front desk reception endpoints.
 * 
 * ============================================================================
 * OOP & Security Concepts:
 * - Role-Based Access Control: All endpoints protected by @RequiresRole("RECEPTIONIST").
 * - Live Form Preview: POST /api/patients/preview computes projected queue slip
 *   estimates without mutating database rows or sequence counters.
 * ============================================================================
 */
@RestController
@RequestMapping("/api")
@RequiresRole("RECEPTIONIST")
public class ReceptionController {

    private final QueueService queueService;

    public ReceptionController(QueueService queueService) {
        this.queueService = queueService;
    }

    // ------------------------------------------------------------------------
    // Departments
    // ------------------------------------------------------------------------

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentDto>> getDepartments() {
        List<Department> depts = queueService.getDepartments();
        List<DepartmentDto> dtos = depts.stream()
                .map(d -> new DepartmentDto(d.getCode(), d.getName()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentDto> createDepartment(@Valid @RequestBody DepartmentDto dto) {
        queueService.addDepartment(dto.code(), dto.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // ------------------------------------------------------------------------
    // Doctors
    // ------------------------------------------------------------------------

    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorDto>> getDoctors() {
        List<Doctor> docs = queueService.getDoctors();
        List<DoctorDto> dtos = docs.stream().map(this::toDoctorDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/doctors")
    public ResponseEntity<DoctorDto> createDoctor(@Valid @RequestBody DoctorDto dto) {
        String id = (dto.id() != null && !dto.id().isBlank())
                ? dto.id()
                : "DOC-" + (100 + queueService.getDoctors().size() + 1);
        Doctor d = new Doctor(
                id, dto.name(), dto.age(), dto.gender(), dto.phone(),
                dto.department(), dto.deptCode(), dto.room(), dto.workingHours(),
                dto.avgConsultMinutes(), dto.emergencyBufferSlots()
        );
        if (dto.status() != null) {
            d.setStatus(DoctorStatus.fromString(dto.status()));
        }
        queueService.addDoctor(d);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDoctorDto(d));
    }

    @PutMapping("/doctors/{id}")
    public ResponseEntity<DoctorDto> updateDoctor(@PathVariable("id") String id, @Valid @RequestBody DoctorDto dto) {
        Doctor d = queueService.getDoctor(id);
        if (d == null) {
            throw new ResourceNotFoundException("Doctor not found: " + id);
        }
        Doctor updated = new Doctor(
                id, dto.name(), dto.age(), dto.gender(), dto.phone(),
                dto.department(), dto.deptCode(), dto.room(), dto.workingHours(),
                dto.avgConsultMinutes(), dto.emergencyBufferSlots()
        );
        if (dto.status() != null) {
            updated.setStatus(DoctorStatus.fromString(dto.status()));
        }
        queueService.updateDoctor(updated);
        return ResponseEntity.ok(toDoctorDto(updated));
    }

    @DeleteMapping("/doctors/{id}")
    public ResponseEntity<?> deleteDoctor(@PathVariable("id") String id) {
        DoctorQueue dq = queueService.getQueueManager().getDoctorQueue(id);
        if (dq != null && (!dq.getWaitingPatients().isEmpty() || dq.getActivePatient() != null)) {
            int count = dq.getWaitingPatients().size() + (dq.getActivePatient() != null ? 1 : 0);
            throw new DoctorUnavailableException("Cannot delete doctor while " + count + " patient(s) are currently in queue or consultation.");
        }
        queueService.deleteDoctor(id);
        return ResponseEntity.ok(Map.of("status", 200, "message", "Doctor " + id + " deleted successfully."));
    }

    // ------------------------------------------------------------------------
    // Patient Registration & Live Preview
    // ------------------------------------------------------------------------

    @PostMapping("/patients")
    public ResponseEntity<RegistrationResponse> registerPatient(@Valid @RequestBody PatientRegisterRequest req) {
        Category cat = Category.fromString(req.category());
        PatientRegistrationRequest coreReq = new PatientRegistrationRequest(
                req.name(), req.age(), req.gender(), req.phone(),
                cat, req.severity(), req.doctorId(), req.appointmentTime(), req.chiefComplaint()
        );
        RegistrationResult result = queueService.registerPatient(coreReq);
        Patient p = result.getPatient();
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistrationResponse(
                p.getToken().getValue(),
                result.getPosition(),
                result.getEstimatedWaitMinutes(),
                result.getEstimatedTimeStr(),
                result.getExplanation(),
                p.getDoctorId(),
                p.getDoctorName(),
                p.getDepartment(),
                p.getRoom()
        ));
    }

    @PostMapping("/patients/preview")
    public ResponseEntity<PatientPreviewResponse> previewPatient(@Valid @RequestBody PatientRegisterRequest req) {
        Category cat = Category.fromString(req.category());
        PatientRegistrationRequest coreReq = new PatientRegistrationRequest(
                req.name(), req.age(), req.gender(), req.phone(),
                cat, req.severity(), req.doctorId(), req.appointmentTime(), req.chiefComplaint()
        );
        RegistrationResult result = queueService.previewPatient(coreReq);
        Patient p = result.getPatient();
        return ResponseEntity.ok(new PatientPreviewResponse(
                p.getToken().getValue(),
                result.getPosition(),
                result.getEstimatedWaitMinutes(),
                result.getEstimatedTimeStr(),
                result.getExplanation(),
                p.getDoctorId(),
                p.getDoctorName()
        ));
    }

    @GetMapping("/patients/{token}")
    public ResponseEntity<PatientSlipDto> getPatientByToken(@PathVariable("token") String token) {
        Patient p = queueService.getPatientByToken(token);
        if (p == null) {
            throw new ResourceNotFoundException("Patient token '" + token + "' was not found.");
        }
        return ResponseEntity.ok(toPatientSlipDto(p));
    }

    @GetMapping("/patients")
    public ResponseEntity<List<PatientSlipDto>> searchPatients(@RequestParam(value = "search", required = false) String search) {
        List<Patient> patients = queueService.getPatients(search);
        List<PatientSlipDto> dtos = patients.stream().map(this::toPatientSlipDto).toList();
        return ResponseEntity.ok(dtos);
    }

    // ------------------------------------------------------------------------
    // Queues & Operations
    // ------------------------------------------------------------------------

    @GetMapping("/queues")
    public ResponseEntity<List<DoctorQueueOverviewDto>> getAllQueues() {
        return ResponseEntity.ok(queueService.getAllQueuesOverview());
    }

    @PostMapping("/patients/{token}/no-show")
    public ResponseEntity<PatientSlipDto> markNoShow(@PathVariable("token") String token) {
        Patient p = queueService.markNoShow(token);
        return ResponseEntity.ok(toPatientSlipDto(p));
    }

    @PostMapping("/patients/{token}/skip")
    public ResponseEntity<PatientSlipDto> skipPatient(
            @PathVariable("token") String token,
            @RequestBody(required = false) SkipPatientRequest req) {
        String reason = req != null && req.reason() != null ? req.reason() : "Temporarily unavailable in waiting room";
        Patient p = queueService.skipPatient(token, reason);
        return ResponseEntity.ok(toPatientSlipDto(p));
    }

    @RequiresRole({"RECEPTIONIST", "DOCTOR"})
    @GetMapping("/events")
    public ResponseEntity<List<QueueEventDto>> getEvents(
            @RequestParam(value = "doctorId", required = false) String doctorId,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<QueueEvent> events = queueService.getEvents(doctorId, token, limit);
        List<QueueEventDto> dtos = events.stream()
                .map(e -> new QueueEventDto(
                        e.getId(),
                        e.getTimestamp().toString(),
                        e.getTokenNumber(),
                        e.getDoctorId(),
                        e.getType().name(),
                        e.getReason()
                ))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ------------------------------------------------------------------------
    // Settings, Clock, Analytics & Demo
    // ------------------------------------------------------------------------

    @GetMapping("/settings")
    public ResponseEntity<SettingsDto> getSettings() {
        Settings s = queueService.getSettings();
        return ResponseEntity.ok(toSettingsDto(s));
    }

    @PutMapping("/settings")
    public ResponseEntity<SettingsDto> updateSettings(@RequestBody SettingsDto req) {
        Settings s = new Settings();
        s.setSeverityWeight(req.severityWeight());
        s.setWaitWeightPerMin(req.waitWeightPerMin());
        s.setFairnessBoost(req.fairnessBoost());
        s.setFairnessThresholdScheduled(req.fairnessThresholdScheduled());
        s.setFairnessThresholdWalkin(req.fairnessThresholdWalkin());
        s.setMaxConsecutiveEmergencies(req.maxConsecutiveEmergencies());
        s.setEmergencyBufferSlotsDefault(req.emergencyBufferSlotsDefault());
        queueService.updateSettings(s);
        return ResponseEntity.ok(toSettingsDto(queueService.getSettings()));
    }

    @PostMapping("/settings/reset")
    public ResponseEntity<SettingsDto> resetSettings() {
        queueService.resetSettings();
        return ResponseEntity.ok(toSettingsDto(queueService.getSettings()));
    }

    @PostMapping("/clock/advance")
    public ResponseEntity<ClockResponse> advanceClock(@RequestBody ClockAdvanceRequest req) {
        queueService.advanceClock(req.minutes());
        return ResponseEntity.ok(new ClockResponse(
                queueService.getNow().toString(),
                queueService.getClockOffsetMinutes()
        ));
    }

    @PostMapping("/clock/reset")
    public ResponseEntity<ClockResponse> resetClock() {
        queueService.resetClock();
        return ResponseEntity.ok(new ClockResponse(
                queueService.getNow().toString(),
                queueService.getClockOffsetMinutes()
        ));
    }

    @GetMapping("/clock")
    public ResponseEntity<ClockResponse> getClock() {
        return ResponseEntity.ok(new ClockResponse(
                queueService.getNow().toString(),
                queueService.getClockOffsetMinutes()
        ));
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(queueService.getAnalytics());
    }

    @PostMapping("/demo/reset")
    public ResponseEntity<?> resetDemo() {
        queueService.resetDemoData();
        return ResponseEntity.ok(Map.of("status", 200, "message", "Database restored to initial mock seed data."));
    }

    // ------------------------------------------------------------------------
    // Mapping Helpers
    // ------------------------------------------------------------------------

    private DoctorDto toDoctorDto(Doctor d) {
        return new DoctorDto(
                d.getId(), d.getName(), d.getAge(), d.getGender(), d.getPhone(),
                d.getDepartment(), d.getDeptCode(), d.getRoom(), d.getWorkingHours(),
                d.getAvgConsultMinutes(), d.getEmergencyBufferSlots(), d.getStatus().getDisplayName()
        );
    }

    private PatientSlipDto toPatientSlipDto(Patient p) {
        Instant now = queueService.getNow();
        Doctor d = queueService.getDoctor(p.getDoctorId());
        DoctorQueue dq = d != null ? queueService.getQueueManager().getDoctorQueue(d.getId()) : null;
        Patient active = dq != null ? dq.getActivePatient() : null;
        List<Patient> ordered = dq != null ? dq.getOrdered(now) : Collections.emptyList();
        WaitEstimate est = WaitTimeEstimator.estimate(p, d, active, ordered, now);
        String explanation = queueService.getQueueManager().getStrategy().explain(p, now, queueService.getSettings());

        int pos = p.getStatus() == PatientStatus.WAITING ? est.getPosition() : (p.getStatus() == PatientStatus.IN_CONSULTATION ? 0 : -1);

        return new PatientSlipDto(
                p.getId(),
                p.getToken() != null ? p.getToken().getValue() : null,
                p.getName(),
                p.getAge(),
                p.getGender(),
                p.getPhone(),
                p.getCategory() != null ? p.getCategory().getDisplayName() : null,
                p.getSeverity(),
                p.getDoctorId(),
                p.getDoctorName(),
                p.getDepartment(),
                p.getRoom(),
                p.getAppointmentTime(),
                p.getRegisteredAt() != null ? p.getRegisteredAt().toString() : null,
                p.getChiefComplaint(),
                p.getStatus() != null ? p.getStatus().getDisplayName() : null,
                p.isSkipped(),
                p.getConsultationStartTime() != null ? p.getConsultationStartTime().toString() : null,
                p.getConsultationEndTime() != null ? p.getConsultationEndTime().toString() : null,
                pos,
                est.getWaitMinutes(),
                est.getEstimatedTimeStr(),
                explanation
        );
    }

    private SettingsDto toSettingsDto(Settings s) {
        return new SettingsDto(
                s.getSeverityWeight(),
                s.getWaitWeightPerMin(),
                s.getFairnessBoost(),
                s.getFairnessThresholdScheduled(),
                s.getFairnessThresholdWalkin(),
                s.getMaxConsecutiveEmergencies(),
                s.getEmergencyBufferSlotsDefault()
        );
    }
}
