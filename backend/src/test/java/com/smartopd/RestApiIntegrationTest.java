package com.smartopd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartopd.dao.jdbc.JdbcPatientDao;
import com.smartopd.model.*;
import com.smartopd.service.QueueService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RestApiIntegrationTest - Comprehensive integration tests verifying all Spring Boot REST API endpoints:
 * 1. Setup Endpoints (/api/setup/status, /api/setup)
 * 2. Staff Endpoints (/api/staff, /api/staff/{username}/password)
 * 3. Public Endpoints (/api/public/board, /api/public/tracker/{token}, /api/public/sample-tokens)
 * 4. Auth Endpoints (/api/auth/login, /api/auth/logout, /api/auth/me)
 * 5. Reception Endpoints (/api/departments, /api/doctors, /api/patients, /api/clock, /api/analytics, etc.)
 * 6. Doctor Endpoints (/api/doctors/{id}/queue, call-next, complete, status)
 * 7. Security & RBAC: Interceptor Bearer authentication, role enforcement, and cross-doctor isolation.
 */
@SpringBootTest(properties = {
        "smartopd.db.path=target/test-api-smartopd.db"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueueService queueService;

    private static String receptionToken;
    private static String doctorToken;

    @BeforeAll
    static void cleanTestDb() {
        File dbFile = new File("target/test-api-smartopd.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    private void seedClinicalDataset() {
        queueService.addDepartment("GEN", "General Medicine");
        queueService.addDepartment("CARD", "Cardiology");
        queueService.addDepartment("PED", "Pediatrics");
        queueService.addDepartment("ORTH", "Orthopedics");
        queueService.addDepartment("ENT", "ENT");

        Doctor d101 = new Doctor("DOC-101", "Dr. Rajesh Mehta", 48, "Male", "9876500101",
                "General Medicine", "GEN", "OPD Room 102", "09:00 - 16:00", 12, 2);
        Doctor d102 = new Doctor("DOC-102", "Dr. Rohith Sharma", 52, "Male", "9876500102",
                "Cardiology", "CARD", "OPD Room 108", "09:00 - 15:00", 15, 3);
        Doctor d103 = new Doctor("DOC-103", "Dr. Priya Nair", 41, "Female", "9876500103",
                "Pediatrics", "PED", "OPD Room 104", "08:30 - 14:30", 10, 2);
        Doctor d104 = new Doctor("DOC-104", "Dr. Vikram Patel", 46, "Male", "9876500104",
                "Orthopedics", "ORTH", "OPD Room 112", "09:30 - 16:30", 14, 2);
        d104.setStatus(DoctorStatus.ON_BREAK);
        Doctor d105 = new Doctor("DOC-105", "Dr. Suresh Verma", 55, "Male", "9876500105",
                "ENT", "ENT", "OPD Room 106", "10:00 - 16:00", 10, 1);
        Doctor d106 = new Doctor("DOC-106", "Dr. Kavita Rao", 39, "Female", "9876500106",
                "General Medicine", "GEN", "OPD Room 104", "08:00 - 14:00", 12, 2);
        d106.setStatus(DoctorStatus.OFF_DUTY);

        queueService.addDoctor(d101);
        queueService.addDoctor(d102);
        queueService.addDoctor(d103);
        queueService.addDoctor(d104);
        queueService.addDoctor(d105);
        queueService.addDoctor(d106);

        Instant now = Instant.now();
        Patient p101 = new ScheduledPatient("PAT-101", "Ramesh Kumar", 52, "Male", "9876543210",
                new Token("GEN", "S", 1), "DOC-101", "Dr. Rajesh Mehta", "OPD Room 102",
                "General Medicine", 2, now.minusSeconds(45 * 60), "09:30", "Follow-up for hypertension and medication refill");
        p101.setStatus(PatientStatus.IN_CONSULTATION);
        p101.setConsultationStartTime(now.minusSeconds(5 * 60));

        Patient p102 = new EmergencyPatient("PAT-102", "Sunita Devi", 68, "Female", "9811122334",
                new Token("CARD", "E", 2), "DOC-102", "Dr. Rohith Sharma", "OPD Room 108",
                "Cardiology", 5, now.minusSeconds(35 * 60), "Severe radiating chest pain and sudden diaphoresis");
        p102.setStatus(PatientStatus.IN_CONSULTATION);
        p102.setConsultationStartTime(now.minusSeconds(8 * 60));

        Patient p103 = new ScheduledPatient("PAT-103", "Aarav Gupta", 7, "Male", "9922233445",
                new Token("PED", "S", 3), "DOC-103", "Dr. Priya Nair", "OPD Room 104",
                "Pediatrics", 3, now.minusSeconds(25 * 60), "10:00", "High grade pediatric fever (102°F) and persistent cough");
        p103.setStatus(PatientStatus.IN_CONSULTATION);
        p103.setConsultationStartTime(now.minusSeconds(4 * 60));

        Patient p104 = new WalkInPatient("PAT-104", "Meera Iyer", 38, "Female", "9733344556",
                new Token("GEN", "W", 4), "DOC-101", "Dr. Rajesh Mehta", "OPD Room 102",
                "General Medicine", 1, now.minusSeconds(52 * 60), "Mild seasonal allergies and throat irritation");

        Patient p105 = new WalkInPatient("PAT-105", "Priya Sharma", 46, "Female", "9844455667",
                new Token("CARD", "W", 5), "DOC-102", "Dr. Rohith Sharma", "OPD Room 108",
                "Cardiology", 4, now.minusSeconds(20 * 60), "Sudden tachycardia and palpitations (HR 135 bpm)");

        Patient p106 = new ScheduledPatient("PAT-106", "Vikram Singh", 29, "Male", "9755566778",
                new Token("ORTH", "S", 6), "DOC-104", "Dr. Vikram Patel", "OPD Room 112",
                "Orthopedics", 3, now.minusSeconds(32 * 60), "10:30", "Acute knee sprain sustained during cricket practice");

        Patient p107 = new WalkInPatient("PAT-107", "Anjali Deshmukh", 33, "Female", "9866677889",
                new Token("ENT", "W", 7), "DOC-105", "Dr. Suresh Verma", "OPD Room 106",
                "ENT", 2, now.minusSeconds(18 * 60), "Inner ear blockage and ringing tinnitus for 3 days");

        Patient p108 = new ScheduledPatient("PAT-108", "Rohan Patil", 41, "Male", "9977788990",
                new Token("GEN", "S", 8), "DOC-101", "Dr. Rajesh Mehta", "OPD Room 102",
                "General Medicine", 2, now.minusSeconds(15 * 60), "10:15", "Routine blood sugar evaluation and HbA1c review");

        Patient p109 = new WalkInPatient("PAT-109", "Amit Joshi", 5, "Male", "9888899001",
                new Token("PED", "W", 9), "DOC-103", "Dr. Priya Nair", "OPD Room 104",
                "Pediatrics", 1, now.minusSeconds(12 * 60), "General pediatric wellness and booster vaccination");

        Patient p110 = new ScheduledPatient("PAT-110", "Pooja Reddy", 58, "Female", "9799900112",
                new Token("CARD", "S", 10), "DOC-102", "Dr. Rohith Sharma", "OPD Room 108",
                "Cardiology", 3, now.minusSeconds(10 * 60), "10:45", "Post-angioplasty 6-month routine stress test follow-up");

        Patient p111 = new WalkInPatient("PAT-111", "Deepak Verma", 64, "Male", "9822211334",
                new Token("ORTH", "W", 11), "DOC-104", "Dr. Vikram Patel", "OPD Room 112",
                "Orthopedics", 2, now.minusSeconds(8 * 60), "Chronic bilateral osteoarthritis knee pain");

        Patient p112 = new ScheduledPatient("PAT-112", "Rajeshwari Pillai", 49, "Female", "9933322445",
                new Token("ENT", "S", 12), "DOC-105", "Dr. Suresh Verma", "OPD Room 106",
                "ENT", 1, now.minusSeconds(5 * 60), "11:00", "Chronic sinusitis consultation");

        Patient[] allPatients = {p101, p102, p103, p104, p105, p106, p107, p108, p109, p110, p111, p112};
        JdbcPatientDao patientDao = new JdbcPatientDao();
        com.smartopd.dao.jdbc.JdbcTokenSequenceDao seqDao = new com.smartopd.dao.jdbc.JdbcTokenSequenceDao();
        try (Connection conn = queueService.getConnectionProvider().getConnection()) {
            for (Patient p : allPatients) {
                patientDao.insert(conn, p);
            }
            seqDao.setLastValue(conn, "GEN", "S", 8);
            seqDao.setLastValue(conn, "GEN", "W", 4);
            seqDao.setLastValue(conn, "CARD", "E", 2);
            seqDao.setLastValue(conn, "CARD", "W", 5);
            seqDao.setLastValue(conn, "CARD", "S", 10);
            seqDao.setLastValue(conn, "PED", "S", 3);
            seqDao.setLastValue(conn, "PED", "W", 9);
            seqDao.setLastValue(conn, "ORTH", "S", 6);
            seqDao.setLastValue(conn, "ORTH", "W", 11);
            seqDao.setLastValue(conn, "ENT", "W", 7);
            seqDao.setLastValue(conn, "ENT", "S", 12);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        queueService.reloadAllFromDatabase();
    }

    // ------------------------------------------------------------------------
    // 0. INITIAL SETUP & STAFF ENDPOINTS (Tasks 2 & 3)
    // ------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("Setup 1: GET /api/setup/status returns needsSetup: true when database has 0 users")
    void testSetupStatusInitiallyTrue() throws Exception {
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.needsSetup", equalTo(true)));
    }

    @Test
    @Order(2)
    @DisplayName("Setup 2: POST /api/setup validates username, password complexity, and displayName")
    void testSetupValidationFailures() throws Exception {
        // Bad username (too short)
        mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ab\", \"displayName\": \"Admin\", \"password\": \"ValidPass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Username must be")));

        // Bad password (too short)
        mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"reception\", \"displayName\": \"Admin\", \"password\": \"short1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Password must be at least 8 characters")));

        // Bad password (no digits)
        mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"reception\", \"displayName\": \"Admin\", \"password\": \"alllettersonly\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Password must be at least 8 characters")));

        // Blank display name
        mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"reception\", \"displayName\": \"   \", \"password\": \"ValidPass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Display name cannot be blank")));
    }

    @Test
    @Order(3)
    @DisplayName("Setup 3: POST /api/setup creates first RECEPTIONIST account without returning token")
    void testSetupCompleteFirstReceptionist() throws Exception {
        String setupJson = """
                {
                    "username": "reception",
                    "displayName": "Reception Desk",
                    "password": "rec123password"
                }
                """;

        mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", containsString("completed successfully")))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @Order(4)
    @DisplayName("Setup 4: POST /api/setup afterwards returns 409 Conflict 'Setup already completed'")
    void testSetupAlreadyCompletedReturns409() throws Exception {
        String setupJson = """
                {
                    "username": "another_admin",
                    "displayName": "Another Receptionist",
                    "password": "anotherPass123"
                }
                """;

        mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.message", containsString("Setup already completed")));
    }

    @Test
    @Order(5)
    @DisplayName("Setup 5: GET /api/setup/status returns needsSetup: false after first account is created")
    void testSetupStatusFalseAfterSetup() throws Exception {
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsSetup", equalTo(false)));
    }

    @Test
    @Order(6)
    @DisplayName("Auth 1: POST /api/auth/login succeeds for created receptionist and returns Bearer token")
    void testReceptionLogin() throws Exception {
        String loginJson = """
                {
                    "username": "reception",
                    "password": "rec123password"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role", equalTo("RECEPTIONIST")))
                .andExpect(jsonPath("$.displayName", equalTo("Reception Desk")))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        receptionToken = root.get("token").asText();
        assertNotNull(receptionToken);
        assertFalse(receptionToken.isBlank());
    }

    @Test
    @Order(7)
    @DisplayName("Test Setup: Initialise clinical dataset (departments, doctors, patients)")
    void testSeedClinicalRoster() throws Exception {
        seedClinicalDataset();
        assertEquals(5, queueService.getDepartments().size());
        assertEquals(6, queueService.getDoctors().size());
    }

    @Test
    @Order(8)
    @DisplayName("Staff 1: GET /api/staff requires RECEPTIONIST role and never leaks password hash or salt")
    void testStaffSecurityAndListing() throws Exception {
        // Without auth -> 401
        mockMvc.perform(get("/api/staff"))
                .andExpect(status().isUnauthorized());

        // With receptionist token -> 200 OK
        MvcResult res = mockMvc.perform(get("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].username", equalTo("reception")))
                .andExpect(jsonPath("$[0].role", equalTo("RECEPTIONIST")))
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"), "Staff list must never contain passwordHash");
        assertFalse(body.contains("salt"), "Staff list must never contain salt");
    }

    @Test
    @Order(9)
    @DisplayName("Staff 2: POST /api/staff validates role constraints (doctor requires doctorId, receptionist forbids doctorId)")
    void testStaffCreationValidation() throws Exception {
        // DOCTOR without doctorId
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"doc_user\", \"displayName\": \"Dr\", \"password\": \"pass1234\", \"role\": \"DOCTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Doctor ID is required when role is DOCTOR")));

        // DOCTOR with non-existent doctorId
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"doc_user\", \"displayName\": \"Dr\", \"password\": \"pass1234\", \"role\": \"DOCTOR\", \"doctorId\": \"DOC-999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Assigned doctor ID does not exist: DOC-999")));

        // RECEPTIONIST with doctorId
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"rec_user2\", \"displayName\": \"Rec\", \"password\": \"pass1234\", \"role\": \"RECEPTIONIST\", \"doctorId\": \"DOC-102\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Doctor ID must not be provided when role is RECEPTIONIST")));

        // Short password
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"rec_user2\", \"displayName\": \"Rec\", \"password\": \"short1\", \"role\": \"RECEPTIONIST\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    @DisplayName("Staff 3: POST /api/staff creates doctor account and enforces 409 on duplicate username or doctorId")
    void testStaffCreateDoctorAccount() throws Exception {
        String staffJson = """
                {
                    "username": "doctor",
                    "displayName": "Dr. Rohith Sharma",
                    "password": "doc123password",
                    "role": "DOCTOR",
                    "doctorId": "DOC-102"
                }
                """;

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", equalTo("doctor")))
                .andExpect(jsonPath("$.role", equalTo("DOCTOR")))
                .andExpect(jsonPath("$.doctorId", equalTo("DOC-102")));

        // Duplicate username -> 409 Conflict
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));

        // Duplicate doctorId login -> 409 Conflict
        String dupDocJson = """
                {
                    "username": "doctor2",
                    "displayName": "Dr. Rohith Sharma Again",
                    "password": "doc123password",
                    "role": "DOCTOR",
                    "doctorId": "DOC-102"
                }
                """;
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupDocJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already has an assigned login account")));
    }

    @Test
    @Order(11)
    @DisplayName("Staff 4: PUT /api/staff/{username}/password resets password and verifies login with new password")
    void testStaffUpdatePassword() throws Exception {
        // Update password to newPassword1
        mockMvc.perform(put("/api/staff/doctor/password")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"newDocPassword1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Password updated successfully")));

        // Old password must fail
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"doctor\", \"password\": \"doc123password\"}"))
                .andExpect(status().isUnauthorized());

        // New password must succeed
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"doctor\", \"password\": \"newDocPassword1\"}"))
                .andExpect(status().isOk());

        // Reset password back to doc123password for subsequent tests
        mockMvc.perform(put("/api/staff/doctor/password")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"doc123password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(11)
    @DisplayName("Staff 5: DELETE /api/staff/{username} enforces constraints and deletes account")
    void testStaffDeleteAccount() throws Exception {
        // Attempting to delete own account returns 409 Conflict
        mockMvc.perform(delete("/api/staff/reception")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isConflict());

        // Create temporary staff account
        String createJson = """
                {
                    "username": "tempfrontdesk",
                    "displayName": "Temporary Receptionist",
                    "password": "Password123",
                    "role": "RECEPTIONIST"
                }
                """;
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated());

        // Delete temporary account succeeds
        mockMvc.perform(delete("/api/staff/tempfrontdesk")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("deleted successfully")));

        // Attempting to delete non-existent account returns 404
        mockMvc.perform(delete("/api/staff/nonexistentuser")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(12)
    @DisplayName("Auth 2: POST /api/auth/login succeeds for doctor credentials and binds doctorId")
    void testDoctorLogin() throws Exception {
        String loginJson = """
                {
                    "username": "doctor",
                    "password": "doc123password"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role", equalTo("DOCTOR")))
                .andExpect(jsonPath("$.doctorId", equalTo("DOC-102")))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        doctorToken = root.get("token").asText();
        assertNotNull(doctorToken);

        // DOCTOR role must receive 403 on ALL Staff management endpoints
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"forbidden_user\",\"displayName\":\"Forbidden\",\"password\":\"Pass1234\",\"role\":\"RECEPTIONIST\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/staff/reception/password")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Pass1234\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/staff/reception")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    @DisplayName("Auth 3: POST /api/auth/login fails for incorrect password with 401 Unauthorized")
    void testLoginInvalidPassword() throws Exception {
        String loginJson = """
                {
                    "username": "reception",
                    "password": "wrongpassword"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", equalTo("Unauthorized")));
    }

    @Test
    @Order(14)
    @DisplayName("Auth 4: GET /api/auth/me returns current session user identity")
    void testAuthMe() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", equalTo("reception")))
                .andExpect(jsonPath("$.role", equalTo("RECEPTIONIST")));
    }

    // ------------------------------------------------------------------------
    // 1. PUBLIC ENDPOINTS (No Authentication Required)
    // ------------------------------------------------------------------------

    @Test
    @Order(15)
    @DisplayName("Public 1: GET /api/public/board returns 200 OK and privacy-safe doctor queue data (Zero PII)")
    void testPublicBoardZeroPii() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/board"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$[0].doctorId").exists())
                .andExpect(jsonPath("$[0].doctorName").exists())
                .andExpect(jsonPath("$[0].department").exists())
                .andExpect(jsonPath("$[0].room").exists())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        // Zero PII rule: full patient names, phones, ages, and medical complaints must never appear
        assertFalse(json.contains("Rajeshwari Pillai"), "Public board must never leak patient full name");
        assertFalse(json.contains("9876543210"), "Public board must never leak patient phone numbers");
        assertFalse(json.contains("chiefComplaint"), "Public board must not expose medical chief complaints");
    }

    @Test
    @Order(16)
    @DisplayName("Public 2: GET /api/public/sample-tokens returns sample waiting tokens for quick demo")
    void testPublicSampleTokens() throws Exception {
        mockMvc.perform(get("/api/public/sample-tokens"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }

    @Test
    @Order(17)
    @DisplayName("Public 3: GET /api/public/tracker/{token} returns privacy-safe timeline and wait estimate")
    void testPublicTracker() throws Exception {
        // CARD-E-002 is Sunita Devi (Level 5 Emergency for Dr. Rohith Sharma DOC-102)
        mockMvc.perform(get("/api/public/tracker/CARD-E-002"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token", equalTo("CARD-E-002")))
                .andExpect(jsonPath("$.firstName", equalTo("Sunita"))) // First name only
                .andExpect(jsonPath("$.doctor", containsString("Rohith Sharma")))
                .andExpect(jsonPath("$.room", equalTo("OPD Room 108")))
                .andExpect(jsonPath("$.timeline").isArray());
    }

    @Test
    @Order(18)
    @DisplayName("Public 4: GET /api/public/tracker/UNKNOWN returns 404 Not Found")
    void testPublicTrackerNotFound() throws Exception {
        mockMvc.perform(get("/api/public/tracker/UNKNOWN-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", equalTo("Not Found")));
    }

    // ------------------------------------------------------------------------
    // 3. ROLE-BASED ACCESS CONTROL (RBAC) & SECURITY INTERCEPTOR
    // ------------------------------------------------------------------------

    @Test
    @Order(20)
    @DisplayName("Security 1: Protected endpoint rejects request without Authorization header (401 Unauthorized)")
    void testMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", equalTo("Unauthorized")));
    }

    @Test
    @Order(21)
    @DisplayName("Security 2: Protected endpoint rejects invalid Bearer token (401 Unauthorized)")
    void testInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer invalid-fake-token-12345"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", equalTo("Unauthorized")));
    }

    @Test
    @Order(22)
    @DisplayName("Security 3: DOCTOR role is forbidden from accessing Receptionist-only endpoint (403 Forbidden)")
    void testDoctorForbiddenFromReceptionEndpoint() throws Exception {
        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", equalTo("Forbidden")));
    }

    @Test
    @Order(23)
    @DisplayName("Security 4: DOCTOR is forbidden from accessing another doctor's queue (Cross-Doctor Isolation)")
    void testDoctorCrossAccessForbidden() throws Exception {
        // doctorToken belongs to DOC-102 (Dr. Rohith Sharma). Attempting DOC-101 must fail!
        mockMvc.perform(get("/api/doctors/DOC-101/queue")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Assigned ID: DOC-102")));
    }

    // ------------------------------------------------------------------------
    // 4. RECEPTION CONTROLLER ENDPOINTS
    // ------------------------------------------------------------------------

    @Test
    @Order(30)
    @DisplayName("Reception 1: GET /api/departments returns clinical departments")
    void testGetDepartments() throws Exception {
        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[?(@.code == 'CARD')].name").value("Cardiology"));
    }

    @Test
    @Order(31)
    @DisplayName("Reception 2: GET /api/doctors returns all doctors with departments and consult averages")
    void testGetDoctors() throws Exception {
        mockMvc.perform(get("/api/doctors")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$[?(@.id == 'DOC-102')].room").value("OPD Room 108"));
    }

    @Test
    @Order(32)
    @DisplayName("Reception 3: POST /api/patients/preview predicts queue position without database insertion")
    void testPreviewPatient() throws Exception {
        String previewJson = """
                {
                    "name": "Test Transient Patient",
                    "age": 40,
                    "gender": "Male",
                    "phone": "9876500000",
                    "category": "Scheduled",
                    "severity": 3,
                    "doctorId": "DOC-102",
                    "appointmentTime": "11:00",
                    "chiefComplaint": "Routine checkup"
                }
                """;

        mockMvc.perform(post("/api/patients/preview")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position", greaterThan(0)))
                .andExpect(jsonPath("$.estimatedStart").isString())
                .andExpect(jsonPath("$.explanation").isString());
    }

    @Test
    @Order(33)
    @DisplayName("Reception 4: POST /api/patients registers Level 5 Emergency with Override to Position #1")
    void testRegisterEmergencyPatient() throws Exception {
        String registerJson = """
                {
                    "name": "Arun Kumar Emergency",
                    "age": 55,
                    "gender": "Male",
                    "phone": "9876511111",
                    "category": "Emergency",
                    "severity": 5,
                    "doctorId": "DOC-102",
                    "chiefComplaint": "Acute chest pain"
                }
                """;

        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position", equalTo(1))) // Position #1 guaranteed for Sev 5
                .andExpect(jsonPath("$.token", startsWith("CARD-E-")))
                .andExpect(jsonPath("$.explanation", containsString("emergency override")));
    }

    @Test
    @Order(34)
    @DisplayName("Reception 5: GET /api/clock and POST /api/clock/advance fast-forward simulated demo time")
    void testSimulatedClockApi() throws Exception {
        mockMvc.perform(get("/api/clock")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTime").isString());

        mockMvc.perform(post("/api/clock/advance")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\": 50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetMinutes", greaterThanOrEqualTo(50)));
    }

    @Test
    @Order(35)
    @DisplayName("Reception 6: GET /api/analytics returns OPD simulation metrics and hourly distributions")
    void testAnalyticsApi() throws Exception {
        mockMvc.perform(get("/api/analytics")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPatients", greaterThan(0)))
                .andExpect(jsonPath("$.waitingCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.averageWaitOverall", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.patientsPerHour", hasSize(11)));
    }

    // ------------------------------------------------------------------------
    // 5. DOCTOR CONSOLE ENDPOINTS
    // ------------------------------------------------------------------------

    @Test
    @Order(40)
    @DisplayName("Doctor 1: GET /api/doctors/DOC-102/queue returns doctor's active patient and waiting queue")
    void testDoctorQueue() throws Exception {
        mockMvc.perform(get("/api/doctors/DOC-102/queue")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId", equalTo("DOC-102")))
                .andExpect(jsonPath("$.nowServing").isNotEmpty())
                .andExpect(jsonPath("$.waitingList", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.waitingList[0].position", equalTo(1)));
    }

    @Test
    @Order(41)
    @DisplayName("Doctor 2: POST /api/doctors/DOC-102/complete completes active ongoing consultation and frees room")
    void testDoctorComplete() throws Exception {
        mockMvc.perform(post("/api/doctors/DOC-102/complete")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("Completed")));
    }

    @Test
    @Order(42)
    @DisplayName("Doctor 3: POST /api/doctors/DOC-102/call-next calls the highest priority patient into consultation")
    void testDoctorCallNext() throws Exception {
        mockMvc.perform(post("/api/doctors/DOC-102/call-next")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("In Consultation")))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @Order(43)
    @DisplayName("Doctor 4: PUT /api/doctors/DOC-102/status toggles availability (On Break)")
    void testDoctorStatusUpdate() throws Exception {
        String statusJson = "{\"status\": \"On Break\"}";

        mockMvc.perform(put("/api/doctors/DOC-102/status")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("On Break")));

        // Revert back to Available
        mockMvc.perform(put("/api/doctors/DOC-102/status")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"Available\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("Available")));
    }

    // ------------------------------------------------------------------------
    // 6. CLOSEOUT AUDIT TESTS (a through j)
    // ------------------------------------------------------------------------

    @Test
    @Order(44)
    @DisplayName("Audit (a): 401 without token on every protected endpoint and public endpoints open")
    void testAuditA_401WithoutTokenOnEveryProtectedEndpointAndPublicEndpointsOpen() throws Exception {
        String[] protectedGet = {
                "/api/departments",
                "/api/doctors",
                "/api/patients",
                "/api/queues",
                "/api/clock",
                "/api/analytics",
                "/api/events",
                "/api/settings",
                "/api/auth/me",
                "/api/doctors/DOC-102/queue"
        };
        for (String url : protectedGet) {
            mockMvc.perform(get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error", equalTo("Unauthorized")));
        }

        String[] protectedPost = {
                "/api/departments",
                "/api/doctors",
                "/api/patients",
                "/api/patients/preview",
                "/api/clock/advance",
                "/api/clock/reset",
                "/api/settings/reset",
                "/api/auth/logout",
                "/api/doctors/DOC-102/call-next",
                "/api/doctors/DOC-102/complete"
        };
        for (String url : protectedPost) {
            mockMvc.perform(post(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error", equalTo("Unauthorized")));
        }

        // Public endpoints must be open without token
        mockMvc.perform(get("/api/public/board"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/sample-tokens"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/tracker/CARD-E-002"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(45)
    @DisplayName("Audit (b): 6th failed login for one username returns 429 Too Many Requests")
    void testAuditB_6thFailedLoginForOneUsernameReturns429() throws Exception {
        String bruteUser = "brute_lockout_user";
        String badPassPayload = "{\"username\": \"" + bruteUser + "\", \"password\": \"wrongpass\"}";

        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badPassPayload))
                    .andExpect(status().isUnauthorized());
        }

        // 5th attempt triggers lockout
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPassPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", equalTo(429)));

        // 6th attempt must return 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPassPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", equalTo(429)))
                .andExpect(jsonPath("$.error", equalTo("Too Many Requests")))
                .andExpect(jsonPath("$.message", containsString("Account is locked")));
    }

    @Test
    @Order(46)
    @DisplayName("Audit (c): DOCTOR token gets 403 on POST /api/patients and on another doctor's call-next")
    void testAuditC_DoctorTokenGets403OnPatientRegistrationAndOtherDoctorCallNext() throws Exception {
        String patientJson = """
                {
                    "name": "Doctor Unauthorized Patient",
                    "age": 35,
                    "gender": "Male",
                    "phone": "9876543210",
                    "category": "Walk-in",
                    "severity": 2,
                    "doctorId": "DOC-102",
                    "chiefComplaint": "Fever"
                }
                """;

        // DOCTOR token on POST /api/patients must get 403 Forbidden
        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)))
                .andExpect(jsonPath("$.error", equalTo("Forbidden")));

        // DOCTOR token on another doctor's call-next must get 403 Forbidden
        mockMvc.perform(post("/api/doctors/DOC-101/call-next")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)))
                .andExpect(jsonPath("$.error", equalTo("Forbidden")));
    }

    @Test
    @Order(47)
    @DisplayName("Audit (d): POST /api/patients/preview changes nothing (queue and next token number identical before and after)")
    void testAuditD_PatientPreviewChangesNothingQueueAndNextTokenIdentical() throws Exception {
        // Snapshot queue overview before
        MvcResult beforeResult = mockMvc.perform(get("/api/queues")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andReturn();
        String beforeJson = beforeResult.getResponse().getContentAsString();

        // Perform preview
        String previewJson = """
                {
                    "name": "Preview Transient Check",
                    "age": 45,
                    "gender": "Female",
                    "phone": "9812345678",
                    "category": "Walk-in",
                    "severity": 3,
                    "doctorId": "DOC-101",
                    "chiefComplaint": "Temporary shoulder pain"
                }
                """;
        mockMvc.perform(post("/api/patients/preview")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position", greaterThan(0)));

        // Snapshot queue overview after
        MvcResult afterResult = mockMvc.perform(get("/api/queues")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andReturn();
        String afterJson = afterResult.getResponse().getContentAsString();

        assertEquals(beforeJson, afterJson, "Queue state before and after POST /api/patients/preview must be identical");
    }

    @Test
    @Order(48)
    @DisplayName("Audit (e): registration validation: bad phone, severity 6, emergency with severity 2, off-duty doctor return correct 400 or 409 JSON")
    void testAuditE_RegistrationValidation_BadPhone_Severity6_EmergencySev2_OffDutyDoctor() throws Exception {
        // Bad phone
        String badPhoneJson = """
                {"name": "Bad Phone", "age": 30, "gender": "M", "phone": "12345", "category": "Walk-in", "severity": 2, "doctorId": "DOC-101"}
                """;
        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPhoneJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.message", containsString("Phone must be exactly 10 digits")));

        // Severity 6
        String sev6Json = """
                {"name": "Sev 6", "age": 30, "gender": "M", "phone": "9876543210", "category": "Walk-in", "severity": 6, "doctorId": "DOC-101"}
                """;
        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sev6Json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.message", containsString("Severity must be between 1 and 5")));

        // Emergency with severity 2
        String emergSev2Json = """
                {"name": "Emerg Sev 2", "age": 30, "gender": "M", "phone": "9876543210", "category": "Emergency", "severity": 2, "doctorId": "DOC-101"}
                """;
        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emergSev2Json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.message", containsString("Emergency category requires Severity Level 4 or 5")));

        // Off-duty doctor (DOC-106 Dr. Kavita Rao is seeded Off Duty)
        String offDutyJson = """
                {"name": "Off Duty Doc", "age": 30, "gender": "M", "phone": "9876543210", "category": "Walk-in", "severity": 2, "doctorId": "DOC-106"}
                """;
        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offDutyJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.message", containsString("Off Duty")));
    }

    @Test
    @Order(49)
    @DisplayName("Audit (f): privacy: scan raw JSON of /api/public/board, /api/public/tracker/{token} and /api/public/sample-tokens against ALL seeded patient full names, phones, ages and complaints and assert none appear")
    void testAuditF_PrivacyScanPublicEndpointsAgainstAllSeededPatients() throws Exception {
        MvcResult boardResult = mockMvc.perform(get("/api/public/board")).andExpect(status().isOk()).andReturn();
        String boardJson = boardResult.getResponse().getContentAsString();

        MvcResult sampleResult = mockMvc.perform(get("/api/public/sample-tokens")).andExpect(status().isOk()).andReturn();
        String sampleJson = sampleResult.getResponse().getContentAsString();

        MvcResult trackerResult = mockMvc.perform(get("/api/public/tracker/CARD-E-002")).andExpect(status().isOk()).andReturn();
        String trackerJson = trackerResult.getResponse().getContentAsString();

        String combined = boardJson + " " + sampleJson + " " + trackerJson;

        record SeedPatientInfo(String name, String phone, int age, String complaint) {}
        SeedPatientInfo[] seeded = {
                new SeedPatientInfo("Ramesh Kumar", "9876543210", 52, "Follow-up for hypertension and medication refill"),
                new SeedPatientInfo("Sunita Devi", "9811122334", 68, "Severe radiating chest pain and sudden diaphoresis"),
                new SeedPatientInfo("Aarav Gupta", "9922233445", 7, "High grade pediatric fever (102°F) and persistent cough"),
                new SeedPatientInfo("Meera Iyer", "9733344556", 38, "Mild seasonal allergies and throat irritation"),
                new SeedPatientInfo("Priya Sharma", "9844455667", 46, "Sudden tachycardia and palpitations (HR 135 bpm)"),
                new SeedPatientInfo("Vikram Singh", "9755566778", 29, "Acute knee sprain sustained during cricket practice"),
                new SeedPatientInfo("Anjali Deshmukh", "9866677889", 33, "Inner ear blockage and ringing tinnitus for 3 days"),
                new SeedPatientInfo("Rohan Patil", "9977788990", 41, "Routine blood sugar evaluation and HbA1c review"),
                new SeedPatientInfo("Amit Joshi", "9888899001", 5, "General pediatric wellness and booster vaccination"),
                new SeedPatientInfo("Pooja Reddy", "9799900112", 58, "Post-angioplasty 6-month routine stress test follow-up"),
                new SeedPatientInfo("Deepak Verma", "9822211334", 64, "Chronic bilateral osteoarthritis knee pain"),
                new SeedPatientInfo("Rajeshwari Pillai", "9933322445", 49, "Chronic sinusitis consultation")
        };

        for (SeedPatientInfo p : seeded) {
            assertFalse(combined.contains(p.name()), "Public endpoints must not leak full name: " + p.name());
            assertFalse(combined.contains(p.phone()), "Public endpoints must not leak phone: " + p.phone());
            assertFalse(combined.contains(p.complaint()), "Public endpoints must not leak complaint: " + p.complaint());
        }

        assertFalse(boardJson.contains("\"age\""), "Board must not contain age");
        assertFalse(sampleJson.contains("\"age\""), "Sample tokens must not contain age");
        assertFalse(trackerJson.contains("\"age\""), "Tracker must not contain age");

        // Privacy fix: no "severity" key in public JSON
        assertFalse(boardJson.contains("\"severity\""), "Board JSON must not contain \"severity\" key");
        assertFalse(sampleJson.contains("\"severity\""), "Sample tokens JSON must not contain \"severity\" key");
        assertFalse(trackerJson.contains("\"severity\""), "Tracker JSON must not contain \"severity\" key");

        // Privacy fix: no "Severity" followed by a digit in public JSON
        java.util.regex.Pattern severityDigitPattern = java.util.regex.Pattern.compile("(?i)severity\\s*\\d");
        assertFalse(severityDigitPattern.matcher(combined).find(), "Public JSON must not contain Severity followed by a digit: " + combined);

        // Verify across all sample tokens in the live database
        List<String> sampleTokenList = objectMapper.readValue(sampleJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        for (String sToken : sampleTokenList) {
            String sTrJson = mockMvc.perform(get("/api/public/tracker/" + sToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(sTrJson.contains("\"severity\""), "Tracker for " + sToken + " must not contain \"severity\" key");
            assertFalse(severityDigitPattern.matcher(sTrJson).find(), "Tracker for " + sToken + " must not contain Severity followed by a digit: " + sTrJson);
        }
    }

    @Test
    @Order(51)
    @DisplayName("Audit (g): settings change reorders the queue and a bad value returns 400")
    void testAuditG_SettingsChangeReordersQueueAndBadValueReturns400() throws Exception {
        // Bad value: severityWeight = -50 -> 400 Bad Request
        String badSettings = """
                {
                    "severityWeight": -50.0,
                    "waitWeightPerMin": 3.0,
                    "fairnessBoost": 150.0,
                    "fairnessThresholdScheduled": 45,
                    "fairnessThresholdWalkin": 45,
                    "maxConsecutiveEmergencies": 2,
                    "emergencyBufferSlotsDefault": 2
                }
                """;
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badSettings))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.message", containsString("Severity Weight must be a valid number between 10 and 500")));

        // Valid update that shifts scoring weights
        String newSettings = """
                {
                    "severityWeight": 400.0,
                    "waitWeightPerMin": 0.5,
                    "fairnessBoost": 0.0,
                    "fairnessThresholdScheduled": 45,
                    "fairnessThresholdWalkin": 45,
                    "maxConsecutiveEmergencies": 2,
                    "emergencyBufferSlotsDefault": 2
                }
                """;
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSettings))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severityWeight", equalTo(400.0)));

        // Reset settings
        mockMvc.perform(post("/api/settings/reset")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severityWeight", equalTo(100.0)));
    }

    @Test
    @Order(52)
    @DisplayName("Audit (h): delete doctor with waiting patients returns 409 with a readable message")
    void testAuditH_DeleteDoctorWithWaitingPatientsReturns409WithReadableMessage() throws Exception {
        // DOC-101 has waiting patients
        mockMvc.perform(delete("/api/doctors/DOC-101")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.error", equalTo("Conflict")))
                .andExpect(jsonPath("$.message", containsString("Cannot delete doctor while")));
    }

    @Test
    @Order(53)
    @DisplayName("Audit (i): error responses never contain a stack trace, 'SQLException' or SQL text")
    void testAuditI_ErrorResponsesNeverContainStackTraceSQLExceptionOrSqlText() throws Exception {
        MvcResult res400 = mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult res401 = mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult res403 = mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andReturn();

        MvcResult res404 = mockMvc.perform(get("/api/public/tracker/NONEXISTENT-999"))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult res409 = mockMvc.perform(delete("/api/doctors/DOC-101")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isConflict())
                .andReturn();

        List<String> bodies = List.of(
                res400.getResponse().getContentAsString(),
                res401.getResponse().getContentAsString(),
                res403.getResponse().getContentAsString(),
                res404.getResponse().getContentAsString(),
                res409.getResponse().getContentAsString()
        );

        for (String body : bodies) {
            assertFalse(body.contains("SQLException"), "Error body must not contain SQLException: " + body);
            assertFalse(body.contains("java.sql."), "Error body must not contain java.sql: " + body);
            assertFalse(body.contains("org.sqlite."), "Error body must not contain org.sqlite: " + body);
            assertFalse(body.contains("at com.smartopd."), "Error body must not contain stack trace 'at com.smartopd': " + body);
            assertFalse(body.contains("at org.springframework."), "Error body must not contain stack trace 'at org.springframework': " + body);
            assertFalse(body.contains("SELECT "), "Error body must not contain SQL text: " + body);
            assertFalse(body.contains("INSERT INTO "), "Error body must not contain SQL text: " + body);

            assertTrue(body.contains("\"status\""));
            assertTrue(body.contains("\"error\""));
            assertTrue(body.contains("\"message\""));
            assertTrue(body.contains("\"path\""));
            assertTrue(body.contains("\"timestamp\""));
        }
    }

    @Test
    @Order(54)
    @DisplayName("Audit (j): clock advance +50 minutes produces a FAIRNESS_BOOST event visible in /api/events and the tracker")
    void testAuditJ_ClockAdvance50MinutesProducesFairnessBoostEventInEventsAndTracker() throws Exception {
        // Reset clock
        mockMvc.perform(post("/api/clock/reset")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk());

        // Advance clock by 50 minutes
        mockMvc.perform(post("/api/clock/advance")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\": 50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetMinutes", greaterThanOrEqualTo(50)));

        // Check /api/events has FAIRNESS_BOOST event
        MvcResult eventsRes = mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andReturn();
        String eventsJson = eventsRes.getResponse().getContentAsString();
        assertTrue(eventsJson.contains("FAIRNESS_BOOST"), "Events list must contain FAIRNESS_BOOST after +50m advance");

        // Check tracker has FAIRNESS_BOOST or isBoosted
        MvcResult trackerRes = mockMvc.perform(get("/api/public/tracker/GEN-W-004"))
                .andExpect(status().isOk())
                .andReturn();
        String trackerJson = trackerRes.getResponse().getContentAsString();
        assertTrue(trackerJson.contains("FAIRNESS_BOOST") || trackerJson.contains("\"isBoosted\":true"),
                "Tracker must reflect fairness boost");
    }

    // ------------------------------------------------------------------------
    // 7. LOGOUT & INVALIDATION
    // ------------------------------------------------------------------------

    @Test
    @Order(60)
    @DisplayName("Auth 5: POST /api/auth/logout invalidates token, preventing subsequent API calls")
    void testLogoutInvalidation() throws Exception {
        // Logout doctor session
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Logged out")));

        // Subsequent call with the same doctorToken must now return 401 Unauthorized
        mockMvc.perform(get("/api/doctors/DOC-102/queue")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @Order(65)
    @DisplayName("Public Rate Limit: 30 requests per minute per IP on /api/public/tracker/**, 31st returns 429")
    void testPublicTrackerRateLimitingPerIp() throws Exception {
        String testIp = "192.168.10.99";

        // First 30 requests within a minute succeed
        for (int i = 1; i <= 30; i++) {
            mockMvc.perform(get("/api/public/tracker/CARD-E-002")
                            .header("X-Forwarded-For", testIp))
                    .andExpect(status().isOk());
        }

        // 31st request from same IP must return 429 Too Many Requests
        mockMvc.perform(get("/api/public/tracker/CARD-E-002")
                        .header("X-Forwarded-For", testIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", equalTo(429)))
                .andExpect(jsonPath("$.error", equalTo("Too Many Requests")))
                .andExpect(jsonPath("$.message", containsString("Rate limit exceeded")));

        // A different IP is unaffected
        mockMvc.perform(get("/api/public/tracker/CARD-E-002")
                        .header("X-Forwarded-For", "192.168.10.100"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------
    // 8. SIMULATION REST ENDPOINTS (Front Desk Role Only)
    // ------------------------------------------------------------------------

    @Test
    @Order(70)
    @DisplayName("Simulation 1: Security guards - 401 without token, 403 for DOCTOR token")
    void testSimulationSecurityGuards() throws Exception {
        // 401 Unauthorized without token
        mockMvc.perform(get("/api/simulation/presets"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/simulation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"busy-day\"}"))
                .andExpect(status().isUnauthorized());

        // Login as doctor to test 403 Forbidden
        String docLoginJson = "{\"username\": \"doctor\", \"password\": \"doc123password\"}";
        MvcResult docLoginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(docLoginJson))
                .andExpect(status().isOk())
                .andReturn();
        String tempDocToken = objectMapper.readTree(docLoginRes.getResponse().getContentAsString()).get("token").asText();

        // 403 Forbidden for DOCTOR role
        mockMvc.perform(get("/api/simulation/presets")
                        .header("Authorization", "Bearer " + tempDocToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", equalTo("Forbidden")));

        mockMvc.perform(post("/api/simulation/run")
                        .header("Authorization", "Bearer " + tempDocToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"busy-day\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", equalTo("Forbidden")));
    }

    @Test
    @Order(71)
    @DisplayName("Simulation 2: Validation - 400 Bad Request for numPatients 5000 and invalid presets")
    void testSimulationValidationRejections() throws Exception {
        // numPatients exceeds limit of 500
        mockMvc.perform(post("/api/simulation/run")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numPatients\": 5000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("numPatients")));

        // numPatients below limit of 50
        mockMvc.perform(post("/api/simulation/run")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numPatients\": 20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Bad Request")));

        // Unknown preset
        mockMvc.perform(post("/api/simulation/run")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\": \"invalid-preset\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Bad Request")));
    }

    @Test
    @Order(72)
    @DisplayName("Simulation 3: GET /api/simulation/presets returns all 3 presets with offered load")
    void testSimulationPresets() throws Exception {
        mockMvc.perform(get("/api/simulation/presets")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id", equalTo("quiet-day")))
                .andExpect(jsonPath("$[0].offeredLoadPercent", equalTo(60)))
                .andExpect(jsonPath("$[1].id", equalTo("busy-day")))
                .andExpect(jsonPath("$[1].offeredLoadPercent", equalTo(90)))
                .andExpect(jsonPath("$[2].id", equalTo("emergency-surge")))
                .andExpect(jsonPath("$[2].offeredLoadPercent", equalTo(105)));
    }

    @Test
    @Order(73)
    @DisplayName("Simulation 4: POST /api/simulation/run succeeds for each preset and is deterministic over HTTP")
    void testSimulationRunAndDeterminism() throws Exception {
        for (String preset : List.of("quiet-day", "busy-day", "emergency-surge")) {
            mockMvc.perform(post("/api/simulation/run")
                            .header("Authorization", "Bearer " + receptionToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"preset\": \"" + preset + "\", \"randomSeed\": 42}"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.offeredLoadPercent").isNumber())
                    .andExpect(jsonPath("$.patientsCount").isNumber())
                    .andExpect(jsonPath("$.policies.fcfs").exists())
                    .andExpect(jsonPath("$.policies.strictPriority").exists())
                    .andExpect(jsonPath("$.policies.smartopd").exists())
                    .andExpect(jsonPath("$.insights").isArray());
        }

        // Test determinism over HTTP: 2 runs with seed 42 must yield identical JSON
        MvcResult resA = mockMvc.perform(post("/api/simulation/run")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\": \"busy-day\", \"randomSeed\": 42}"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult resB = mockMvc.perform(post("/api/simulation/run")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\": \"busy-day\", \"randomSeed\": 42}"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(resA.getResponse().getContentAsString(), resB.getResponse().getContentAsString(),
                "Two HTTP simulation runs with identical seed must return identical JSON");
    }

    @Test
    @Order(74)
    @DisplayName("Auth 6: POST /api/auth/change-password validates credentials, updates password, ends other sessions, and never leaks passwords")
    void testChangePasswordFlow() throws Exception {
        // 1. Without token returns 401 Unauthorized
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPass123\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isUnauthorized());

        // 2. Create dedicated staff account for password change test
        String createJson = """
                {
                    "username": "pwtestuser",
                    "displayName": "Password Test User",
                    "password": "InitialPassword1",
                    "role": "RECEPTIONIST"
                }
                """;
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated());

        // 3. Open session A and session B
        MvcResult loginARes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pwtestuser\",\"password\":\"InitialPassword1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String tokenA = objectMapper.readTree(loginARes.getResponse().getContentAsString()).get("token").asText();

        MvcResult loginBRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pwtestuser\",\"password\":\"InitialPassword1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String tokenB = objectMapper.readTree(loginBRes.getResponse().getContentAsString()).get("token").asText();
        assertNotEquals(tokenA, tokenB, "Each login creates a unique session token");

        // 4. Test wrong current password (returns 400 Bad Request)
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongInitial1\",\"newPassword\":\"NewSecurePassword1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Current password is incorrect")));

        // 5. Test weak new password (< 8 chars, or missing digit) returns 400 Bad Request
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"InitialPassword1\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at least 8 characters")));

        // 6. Test successful password change using session B
        MvcResult successRes = mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"InitialPassword1\",\"newPassword\":\"BrandNewPassword2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Password changed successfully")))
                .andReturn();

        // Security check: response body must NEVER contain the password
        String responseContent = successRes.getResponse().getContentAsString();
        assertFalse(responseContent.contains("InitialPassword1"), "Response must never leak old password");
        assertFalse(responseContent.contains("BrandNewPassword2"), "Response must never leak new password");

        // 7. Verify session A (other session) is invalidated (401 Unauthorized)
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());

        // 8. Verify session B (current session) remains valid (200 OK)
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", equalTo("pwtestuser")));

        // 9. Verify old password no longer works for new logins (401 Unauthorized)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pwtestuser\",\"password\":\"InitialPassword1\"}"))
                .andExpect(status().isUnauthorized());

        // 10. Verify new password works for login (200 OK)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pwtestuser\",\"password\":\"BrandNewPassword2\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(75)
    @DisplayName("Doctor 5: Deleting a doctor cascades to remove their login account in the same transaction and ends their sessions")
    void testDoctorDeletionCascadesToLoginAndInvalidatesSession() throws Exception {
        // 1. Create a dedicated doctor
        String doctorJson = """
                {
                    "name": "Dr. Cascade Tester",
                    "age": 48,
                    "gender": "Female",
                    "phone": "9811223344",
                    "department": "Cardiology",
                    "deptCode": "CARD",
                    "room": "OPD Room 204",
                    "workingHours": "09:00 - 17:00",
                    "avgConsultMinutes": 10,
                    "emergencyBufferSlots": 2,
                    "status": "Available"
                }
                """;
        MvcResult docRes = mockMvc.perform(post("/api/doctors")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson))
                .andExpect(status().isCreated())
                .andReturn();
        String cascadeDocId = objectMapper.readTree(docRes.getResponse().getContentAsString()).get("id").asText();
        assertNotNull(cascadeDocId);

        // 2. Create doctor login account via /api/staff
        String staffJson = """
                {
                    "username": "drcascade",
                    "displayName": "Dr. Cascade Tester",
                    "password": "DoctorPass123",
                    "role": "DOCTOR",
                    "doctorId": "%s"
                }
                """.formatted(cascadeDocId);
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson))
                .andExpect(status().isCreated());

        // 3. Log in as this doctor
        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"drcascade\",\"password\":\"DoctorPass123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String docSessionToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();

        // Verify doctor session works
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + docSessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId", equalTo(cascadeDocId)));

        // 4. Delete the doctor via DELETE /api/doctors/{id}
        mockMvc.perform(delete("/api/doctors/" + cascadeDocId)
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk());

        // 5. Verify doctor login is no longer valid (401 Unauthorized)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"drcascade\",\"password\":\"DoctorPass123\"}"))
                .andExpect(status().isUnauthorized());

        // 6. Verify existing doctor session token has been invalidated (401 Unauthorized)
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + docSessionToken))
                .andExpect(status().isUnauthorized());
    }
}
