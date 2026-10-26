package com.smartopd;

import com.smartopd.auth.PasswordHasher;
import com.smartopd.config.Settings;
import com.smartopd.dao.ConnectionProvider;
import com.smartopd.dao.DataAccessException;
import com.smartopd.dao.SchemaInitializer;
import com.smartopd.dao.jdbc.*;
import com.smartopd.events.QueueEvent;
import com.smartopd.model.*;
import com.smartopd.service.AuthResult;
import com.smartopd.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DaoRoundTripAndConstraintsTest - Tests 3, 4, 5, 6, and 10:
 * - DAO round trips & subclass hydration
 * - Relational foreign key and CHECK constraint enforcement
 * - Token uniqueness
 * - SQL injection immunity
 * - Password PBKDF2 authentication & salt verification
 */
public class DaoRoundTripAndConstraintsTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private JdbcDepartmentDao departmentDao;
    private JdbcDoctorDao doctorDao;
    private JdbcPatientDao patientDao;
    private JdbcSettingsDao settingsDao;
    private JdbcEventDao eventDao;
    private JdbcUserDao userDao;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test_dao.db").toString();
        connectionProvider = new ConnectionProvider(dbPath);
        new SchemaInitializer(connectionProvider).initialize();
        departmentDao = new JdbcDepartmentDao();
        doctorDao = new JdbcDoctorDao();
        patientDao = new JdbcPatientDao();
        settingsDao = new JdbcSettingsDao();
        eventDao = new JdbcEventDao();
        userDao = new JdbcUserDao();
    }

    @Test
    @DisplayName("Test 3: DAO round trips for doctor, patient (all three subclasses come back as the right class), settings and events")
    void testDaoRoundTripsAndPolymorphicSubclasses() throws Exception {
        try (Connection conn = connectionProvider.getConnection()) {
            departmentDao.insert(conn, new Department("CARD", "Cardiology"));

            Doctor doctor = new Doctor("DOC-201", "Dr. Anita Sen", 42, "Female", "9876500001",
                    "Cardiology", "CARD", "Room 201", "09:00 - 15:00", 12, 2);
            doctorDao.insert(conn, doctor);

            Optional<Doctor> docFetched = doctorDao.findById(conn, "DOC-201");
            assertTrue(docFetched.isPresent());
            assertEquals("Dr. Anita Sen", docFetched.get().getName());

            Instant now = Instant.now();

            // 1. EmergencyPatient subclass round-trip
            Patient pEmerg = PatientFactory.reconstructPatient(
                    "P-EMERG", "John Emergency", 50, "Male", "9876543210",
                    new Token("CARD", "E", 101), "DOC-201", "Dr. Anita Sen", "Room 201",
                    "Cardiology", 5, Category.EMERGENCY, now, null, "Chest pain",
                    PatientStatus.WAITING, false, null, null
            );
            patientDao.insert(conn, pEmerg);

            // 2. ScheduledPatient subclass round-trip
            Patient pSched = PatientFactory.reconstructPatient(
                    "P-SCHED", "Alice Scheduled", 35, "Female", "9876543211",
                    new Token("CARD", "S", 102), "DOC-201", "Dr. Anita Sen", "Room 201",
                    "Cardiology", 2, Category.SCHEDULED, now, "11:30", "Consultation",
                    PatientStatus.WAITING, false, null, null
            );
            patientDao.insert(conn, pSched);

            // 3. WalkInPatient subclass round-trip
            Patient pWalkin = PatientFactory.reconstructPatient(
                    "P-WALK", "Bob Walkin", 28, "Male", "9876543212",
                    new Token("CARD", "W", 103), "DOC-201", "Dr. Anita Sen", "Room 201",
                    "Cardiology", 1, Category.WALK_IN, now, null, "Flu check",
                    PatientStatus.WAITING, false, null, null
            );
            patientDao.insert(conn, pWalkin);

            // Verify subclass types from DB
            Patient fetchedEmerg = patientDao.findByToken(conn, "CARD-E-101").orElseThrow();
            assertInstanceOf(EmergencyPatient.class, fetchedEmerg, "Must reconstruct as EmergencyPatient");
            assertEquals(5, fetchedEmerg.getSeverity());

            Patient fetchedSched = patientDao.findByToken(conn, "CARD-S-102").orElseThrow();
            assertInstanceOf(ScheduledPatient.class, fetchedSched, "Must reconstruct as ScheduledPatient");
            assertEquals("11:30", fetchedSched.getAppointmentTime());

            Patient fetchedWalkin = patientDao.findByToken(conn, "CARD-W-103").orElseThrow();
            assertInstanceOf(WalkInPatient.class, fetchedWalkin, "Must reconstruct as WalkInPatient");

            // Settings round-trip
            Settings s = new Settings();
            s.setFairnessBoost(200.0);
            s.setWaitWeightPerMin(4.5);
            settingsDao.save(conn, s);
            Settings loadedSettings = settingsDao.load(conn);
            assertEquals(200.0, loadedSettings.getFairnessBoost());
            assertEquals(4.5, loadedSettings.getWaitWeightPerMin());

            // Event round-trip
            QueueEvent event = new QueueEvent(EventType.CALLED, "CARD-E-101", "DOC-201", "Patient called", now);
            eventDao.append(conn, event);
            var events = eventDao.findByToken(conn, "CARD-E-101", 10);
            assertEquals(1, events.size());
            assertEquals(EventType.CALLED, events.get(0).getType());
        }
    }

    @Test
    @DisplayName("Test 4: Foreign key is enforced (patient with unknown doctor_id fails) and severity CHECK rejects 0 and 6")
    void testDatabaseConstraints() throws Exception {
        try (Connection conn = connectionProvider.getConnection()) {
            departmentDao.insert(conn, new Department("GEN", "General Medicine"));
            doctorDao.insert(conn, new Doctor("DOC-1", "Dr. Valid", 45, "Male", "9876500000",
                    "General Medicine", "GEN", "Room 1", "09:00 - 15:00", 10, 2));

            // Foreign Key constraint failure: unknown doctor_id
            Patient invalidDocPatient = PatientFactory.reconstructPatient(
                    "P-BAD-DOC", "Invalid Doc Patient", 30, "Male", "9876543210",
                    new Token("GEN", "W", 999), "DOC-UNKNOWN-999", "Unknown", "Room",
                    "General", 2, Category.WALK_IN, Instant.now(), null, "Test",
                    PatientStatus.WAITING, false, null, null
            );
            assertThrows(DataAccessException.class, () -> patientDao.insert(conn, invalidDocPatient),
                    "Foreign key enforcement must reject non-existent doctor_id");

            // Database CHECK constraint: severity < 1 (0) rejected by DB
            String sqlCheck0 = "INSERT INTO patient (id, token, name, age, gender, phone, category, severity, "
                    + "doctor_id, registered_at, chief_complaint, status, skipped) "
                    + "VALUES ('P-SEV-0', 'GEN-W-800', 'Zero Sev', 30, 'M', '9876543210', 'Walk-in', 0, "
                    + "'DOC-1', datetime('now'), 'Complaint', 'WAITING', 0)";
            try (PreparedStatement ps0 = conn.prepareStatement(sqlCheck0)) {
                assertThrows(Exception.class, ps0::executeUpdate, "SQLite CHECK constraint must reject severity = 0");
            }

            // Database CHECK constraint: severity > 5 (6) rejected by DB
            String sqlCheck6 = "INSERT INTO patient (id, token, name, age, gender, phone, category, severity, "
                    + "doctor_id, registered_at, chief_complaint, status, skipped) "
                    + "VALUES ('P-SEV-6', 'GEN-W-801', 'Six Sev', 30, 'M', '9876543210', 'Walk-in', 6, "
                    + "'DOC-1', datetime('now'), 'Complaint', 'WAITING', 0)";
            try (PreparedStatement ps6 = conn.prepareStatement(sqlCheck6)) {
                assertThrows(Exception.class, ps6::executeUpdate, "SQLite CHECK constraint must reject severity = 6");
            }
        }
    }

    @Test
    @DisplayName("Test 5: Token uniqueness: inserting a duplicate token throws DataAccessException")
    void testTokenUniquenessConstraint() throws Exception {
        try (Connection conn = connectionProvider.getConnection()) {
            departmentDao.insert(conn, new Department("GEN", "General Medicine"));
            doctorDao.insert(conn, new Doctor("DOC-1", "Dr. A", 40, "Male", "9876500000",
                    "General Medicine", "GEN", "Room 1", "09:00 - 15:00", 10, 2));

            Patient p1 = PatientFactory.reconstructPatient(
                    "P-1", "Patient 1", 30, "Male", "9876543210",
                    new Token("GEN", "W", 50), "DOC-1", "Dr. A", "Room 1",
                    "General", 2, Category.WALK_IN, Instant.now(), null, "Test",
                    PatientStatus.WAITING, false, null, null
            );
            patientDao.insert(conn, p1);

            Patient p2 = PatientFactory.reconstructPatient(
                    "P-2", "Patient 2", 32, "Female", "9876543211",
                    new Token("GEN", "W", 50), "DOC-1", "Dr. A", "Room 1",
                    "General", 2, Category.WALK_IN, Instant.now(), null, "Test",
                    PatientStatus.WAITING, false, null, null
            );

            assertThrows(DataAccessException.class, () -> patientDao.insert(conn, p2),
                    "Duplicate token GEN-W-050 must trigger UNIQUE constraint violation");
        }
    }

    @Test
    @DisplayName("Test 6: SQL injection: registering a patient named \"Robert'); DROP TABLE patient;--\" stores it as plain text and the table still exists")
    void testSqlInjectionImmunity() throws Exception {
        QueueService service = new QueueService(connectionProvider);
        service.addDepartment("GEN", "General Medicine");
        service.addDoctor(new Doctor("DOC-101", "Dr. Rajesh Mehta", 48, "Male", "9876500101", "General Medicine", "GEN", "OPD Room 102", "09:00 - 16:00", 12, 2));

        String injectionPayload = "Robert'); DROP TABLE patient;--";
        PatientRegistrationRequest request = new PatientRegistrationRequest(
                injectionPayload, 30, "Male", "9876543210",
                Category.WALK_IN, 2, "DOC-101", null, "Routine check"
        );

        var regResult = service.registerPatient(request);
        assertEquals(injectionPayload, regResult.getPatient().getName(), "Stored name must equal payload verbatim");

        // Verify patient table still exists and patient is found
        try (Connection conn = connectionProvider.getConnection()) {
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "patient", null)) {
                assertTrue(rs.next(), "patient table must still exist intact");
            }
            Patient fetched = service.getPatientByToken(regResult.getPatient().getToken().getValue());
            assertNotNull(fetched);
            assertEquals(injectionPayload, fetched.getName());
        }
    }

    @Test
    @DisplayName("Test 10: authenticate succeeds for reception and doctor, fails for a wrong password, and stored hashes never equal the plaintext")
    void testAuthenticationAndPasswordHashing() throws Exception {
        QueueService service = new QueueService(connectionProvider);
        service.completeSetup("reception", "Reception Desk", "rec123password");
        service.addDepartment("CARD", "Cardiology");
        service.addDoctor(new Doctor("DOC-102", "Dr. Rohith Sharma", 52, "Male", "9876500102", "Cardiology", "CARD", "OPD Room 108", "09:00 - 15:00", 15, 3));
        service.createStaffAccount("doctor", "Dr. Rohith Sharma", "doc123password", "DOCTOR", "DOC-102");

        // Success reception
        Optional<AuthResult> recAuth = service.authenticate("reception", "rec123password");
        assertTrue(recAuth.isPresent());
        assertEquals("RECEPTIONIST", recAuth.get().role());
        assertEquals("Reception Desk", recAuth.get().displayName());

        // Success doctor
        Optional<AuthResult> docAuth = service.authenticate("doctor", "doc123password");
        assertTrue(docAuth.isPresent());
        assertEquals("DOCTOR", docAuth.get().role());
        assertEquals("DOC-102", docAuth.get().doctorId());

        // Failure wrong password
        Optional<AuthResult> badPassAuth = service.authenticate("reception", "wrongpassword");
        assertFalse(badPassAuth.isPresent(), "Authentication must fail on wrong password");

        // Failure non-existent user
        Optional<AuthResult> badUserAuth = service.authenticate("unknown_user", "rec123password");
        assertFalse(badUserAuth.isPresent(), "Authentication must fail on non-existent user");

        // Verify stored hash digests are never plaintext
        try (Connection conn = connectionProvider.getConnection()) {
            UserAccount recAccount = userDao.findByUsername(conn, "reception").orElseThrow();
            assertNotEquals("rec123password", recAccount.getPasswordHash(), "Stored hash must never equal plaintext");
            assertNotNull(recAccount.getSalt(), "Salt must be stored");
            assertTrue(PasswordHasher.verify("rec123password", recAccount.getSalt(), recAccount.getPasswordHash()));
        }
    }
}
