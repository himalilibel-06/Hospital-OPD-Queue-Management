package com.smartopd;

import com.smartopd.exception.DoctorUnavailableException;
import com.smartopd.exception.InvalidSeverityException;
import com.smartopd.exception.ValidationException;
import com.smartopd.model.Category;
import com.smartopd.model.Doctor;
import com.smartopd.model.DoctorStatus;
import com.smartopd.model.EmergencyPatient;
import com.smartopd.model.Patient;
import com.smartopd.model.PatientFactory;
import com.smartopd.model.PatientRegistrationRequest;
import com.smartopd.model.ScheduledPatient;
import com.smartopd.model.Token;
import com.smartopd.model.TokenGenerator;
import com.smartopd.model.WalkInPatient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenAndFactoryTest - Verifies Token formatting and PatientFactory business validation.
 * 
 * Demonstrates:
 * - Factory Pattern
 * - Value Object immutability
 * - Subclass polymorphic creation
 */
public class TokenAndFactoryTest {

    @Test
    @DisplayName("Test 7: Token format and sequence generation (DEPT-CAT-001)")
    void testTokenFormatAndSequenceGeneration() {
        TokenGenerator generator = new TokenGenerator();
        Doctor cardDoc = new Doctor("DOC-102", "Dr. Rohith Sharma", "Cardiology", "CARD", "Room 108");

        Token t1 = generator.nextToken(cardDoc, Category.EMERGENCY);
        Token t2 = generator.nextToken(cardDoc, Category.SCHEDULED);
        Token t3 = generator.nextToken(cardDoc, Category.WALK_IN);

        assertEquals("CARD-E-001", t1.getValue());
        assertEquals("CARD-S-002", t2.getValue());
        assertEquals("CARD-W-003", t3.getValue());

        // Token parsing & equality
        Token parsed = Token.parse("CARD-E-001");
        assertEquals(t1, parsed);
        assertEquals(t1.hashCode(), parsed.hashCode());
        assertEquals("CARD-E-001", parsed.toString());

        assertThrows(IllegalArgumentException.class, () -> Token.parse("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Test 8: PatientFactory validates emergency severity, phone, age and doctor availability")
    void testPatientFactoryValidation() {
        Doctor doctor = new Doctor("DOC-104", "Dr. Meenakshi Rao", "Orthopedics", "ORTH", "Room 105");
        Token token = new Token("ORTH", "E", 1);
        Instant now = Instant.now();

        // 1. Emergency category requires severity 4 or 5
        PatientRegistrationRequest emergLowSev = new PatientRegistrationRequest(
                "Ramesh", 45, "Male", "9876543210", Category.EMERGENCY, 2, "DOC-104", null, "Chest discomfort"
        );
        InvalidSeverityException ex1 = assertThrows(
                InvalidSeverityException.class,
                () -> PatientFactory.createPatient(emergLowSev, doctor, token, now)
        );
        assertTrue(ex1.getMessage().contains("Emergency category requires Severity Level 4 or 5."));

        // 2. Severity outside 1..5 rejected
        PatientRegistrationRequest invalidSev = new PatientRegistrationRequest(
                "Suresh", 40, "Male", "9876543210", Category.WALK_IN, 6, "DOC-104", null, "Checkup"
        );
        assertThrows(InvalidSeverityException.class, () -> PatientFactory.createPatient(invalidSev, doctor, token, now));

        // 3. Invalid phone number rejected
        PatientRegistrationRequest badPhone = new PatientRegistrationRequest(
                "Mahesh", 35, "Male", "12345", Category.WALK_IN, 2, "DOC-104", null, "Checkup"
        );
        ValidationException exPhone = assertThrows(
                ValidationException.class,
                () -> PatientFactory.createPatient(badPhone, doctor, token, now)
        );
        assertTrue(exPhone.getMessage().contains("Please enter a valid 10-digit mobile number."));

        // 4. Invalid age rejected
        PatientRegistrationRequest badAge = new PatientRegistrationRequest(
                "Geeta", -5, "Female", "9876543210", Category.WALK_IN, 2, "DOC-104", null, "Checkup"
        );
        assertThrows(ValidationException.class, () -> PatientFactory.createPatient(badAge, doctor, token, now));

        // 5. Blank name rejected
        PatientRegistrationRequest blankName = new PatientRegistrationRequest(
                "  ", 25, "Female", "9876543210", Category.WALK_IN, 2, "DOC-104", null, "Checkup"
        );
        assertThrows(ValidationException.class, () -> PatientFactory.createPatient(blankName, doctor, token, now));

        // 6. Scheduled appointment requires appointmentTime slot
        PatientRegistrationRequest schedNoTime = new PatientRegistrationRequest(
                "Pooja", 29, "Female", "9876543210", Category.SCHEDULED, 1, "DOC-104", null, "Slot missing"
        );
        assertThrows(ValidationException.class, () -> PatientFactory.createPatient(schedNoTime, doctor, token, now));

        // 7. Doctor Off Duty rejects new registration
        doctor.setStatus(DoctorStatus.OFF_DUTY);
        PatientRegistrationRequest validWalkin = new PatientRegistrationRequest(
                "Kavita", 28, "Female", "9876543210", Category.WALK_IN, 2, "DOC-104", null, "Checkup"
        );
        assertThrows(DoctorUnavailableException.class, () -> PatientFactory.createPatient(validWalkin, doctor, token, now));

        // 8. Successful polymorphic creation
        doctor.setStatus(DoctorStatus.AVAILABLE);
        Patient pEmerg = PatientFactory.createPatient(
                new PatientRegistrationRequest("E1", 30, "M", "9876543210", Category.EMERGENCY, 5, "DOC-104", null, "Trauma"),
                doctor, new Token("ORTH", "E", 1), now
        );
        assertTrue(pEmerg instanceof EmergencyPatient);
        assertEquals(0.0, pEmerg.urgencyBonus(now), "EmergencyPatient urgencyBonus must be 0.0");
        assertEquals(45, pEmerg.fairnessThresholdMinutes(new com.smartopd.config.Settings()), "EmergencyPatient fairness threshold must be 45m");

        Patient pSched = PatientFactory.createPatient(
                new PatientRegistrationRequest("S1", 30, "M", "9876543210", Category.SCHEDULED, 1, "DOC-104", "10:30", "Follow-up"),
                doctor, new Token("ORTH", "S", 2), now
        );
        assertTrue(pSched instanceof ScheduledPatient);
        assertTrue(pSched.urgencyBonus(now) >= 0.0, "ScheduledPatient urgencyBonus must return non-negative value");
        assertEquals(45, pSched.fairnessThresholdMinutes(new com.smartopd.config.Settings()), "ScheduledPatient fairness threshold must be 45m");

        Patient pWalkin = PatientFactory.createPatient(
                new PatientRegistrationRequest("W1", 30, "M", "9876543210", Category.WALK_IN, 2, "DOC-104", null, "Routine"),
                doctor, new Token("ORTH", "W", 3), now
        );
        assertTrue(pWalkin instanceof WalkInPatient);
        assertEquals(0.0, pWalkin.urgencyBonus(now), "WalkInPatient urgencyBonus must be 0.0");
        assertEquals(45, pWalkin.fairnessThresholdMinutes(new com.smartopd.config.Settings()), "WalkInPatient fairness threshold must be 45m");
    }
}
