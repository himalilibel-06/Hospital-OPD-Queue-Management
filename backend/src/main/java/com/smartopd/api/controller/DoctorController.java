package com.smartopd.api.controller;

import com.smartopd.api.dto.DoctorDto;
import com.smartopd.api.dto.DoctorQueueOverviewDto;
import com.smartopd.api.dto.DoctorStatusRequest;
import com.smartopd.api.dto.QueuePatientDto;
import com.smartopd.api.security.RequiresRole;
import com.smartopd.model.Doctor;
import com.smartopd.model.DoctorStatus;
import com.smartopd.model.Patient;
import com.smartopd.queue.DoctorQueue;
import com.smartopd.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * DoctorController - Endpoints for doctor consultation workflow.
 * 
 * ============================================================================
 * OOP & Security Concepts:
 * - Role-Based Access Control: Accessible by DOCTOR and RECEPTIONIST.
 * - Resource Ownership Enforcement: A DOCTOR can only access and modify their
 *   own doctor ID, verified centrally by AuthInterceptor.
 * - Consultation State Machine: Manages transitions from WAITING -> IN_CONSULTATION -> COMPLETED.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/doctors")
@RequiresRole({"DOCTOR", "RECEPTIONIST"})
public class DoctorController {

    private final QueueService queueService;

    public DoctorController(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * Retrieves the doctor's active patient and full ordered waiting queue.
     */
    @GetMapping("/{id}/queue")
    public ResponseEntity<DoctorQueueOverviewDto> getDoctorQueue(@PathVariable("id") String id) {
        return ResponseEntity.ok(queueService.getDoctorQueueOverview(id));
    }

    /**
     * Calls the next patient in priority order into the consultation room.
     */
    @PostMapping("/{id}/call-next")
    public ResponseEntity<QueuePatientDto> callNext(@PathVariable("id") String id) {
        Patient called = queueService.callNext(id);
        DoctorQueue dq = queueService.getQueueManager().getDoctorQueue(id);
        QueuePatientDto dto = queueService.toQueuePatientDto(called, 0, queueService.getNow(), dq);
        return ResponseEntity.ok(dto);
    }

    /**
     * Completes the ongoing consultation and recalculates smoothed average consult duration.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<QueuePatientDto> completeConsultation(@PathVariable("id") String id) {
        Patient completed = queueService.completeConsultation(id);
        DoctorQueue dq = queueService.getQueueManager().getDoctorQueue(id);
        QueuePatientDto dto = queueService.toQueuePatientDto(completed, 0, queueService.getNow(), dq);
        return ResponseEntity.ok(dto);
    }

    /**
     * Updates doctor availability status (e.g. Available, On Break, Off Duty).
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<DoctorDto> updateDoctorStatus(
            @PathVariable("id") String id,
            @Valid @RequestBody DoctorStatusRequest req) {
        DoctorStatus status = DoctorStatus.fromString(req.status());
        queueService.setDoctorStatus(id, status);
        Doctor doctor = queueService.getDoctor(id);
        return ResponseEntity.ok(new DoctorDto(
                doctor.getId(),
                doctor.getName(),
                doctor.getAge(),
                doctor.getGender(),
                doctor.getPhone(),
                doctor.getDepartment(),
                doctor.getDeptCode(),
                doctor.getRoom(),
                doctor.getWorkingHours(),
                doctor.getAvgConsultMinutes(),
                doctor.getEmergencyBufferSlots(),
                doctor.getStatus().getDisplayName()
        ));
    }
}
