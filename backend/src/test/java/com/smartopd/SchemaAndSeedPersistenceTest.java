package com.smartopd;

import com.smartopd.config.Settings;
import com.smartopd.dao.ConnectionProvider;
import com.smartopd.dao.DefaultsInitializer;
import com.smartopd.dao.SchemaInitializer;
import com.smartopd.dao.jdbc.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SchemaAndSeedPersistenceTest - Tests schema creation idempotency, empty database state,
 * and DefaultsInitializer behavior.
 */
public class SchemaAndSeedPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Test 1: Schema is created and re-running SchemaInitializer does nothing harmful")
    void testSchemaCreationAndIdempotency() throws Exception {
        String dbPath = tempDir.resolve("test_schema.db").toString();
        ConnectionProvider cp = new ConnectionProvider(dbPath);
        SchemaInitializer initializer = new SchemaInitializer(cp);

        // First run creates tables
        assertDoesNotThrow(initializer::initialize);

        // Second run must be completely idempotent and safe
        assertDoesNotThrow(initializer::initialize);

        try (Connection conn = cp.getConnection()) {
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "patient", null)) {
                assertTrue(rs.next(), "patient table must exist");
            }
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "queue_event", null)) {
                assertTrue(rs.next(), "queue_event table must exist");
            }
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "user_account", null)) {
                assertTrue(rs.next(), "user_account table must exist");
            }
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "settings", null)) {
                assertTrue(rs.next(), "settings table must exist");
            }
        }
    }

    @Test
    @DisplayName("Test 2: Fresh database starts with 0 departments, 0 doctors, 0 patients, 0 users, and default Settings")
    void testDefaultsInitializerEmptyStart() throws Exception {
        String dbPath = tempDir.resolve("test_empty_seed.db").toString();
        ConnectionProvider cp = new ConnectionProvider(dbPath);
        new SchemaInitializer(cp).initialize();
        DefaultsInitializer initializer = new DefaultsInitializer(cp);

        // First initialization
        boolean firstRun = initializer.initializeIfEmpty();
        assertTrue(firstRun, "First initializer run must execute");

        // Second initialization
        boolean secondRun = initializer.initializeIfEmpty();
        assertFalse(secondRun, "Second run must detect non-empty settings and skip");

        try (Connection conn = cp.getConnection()) {
            assertEquals(0, new JdbcDepartmentDao().findAll(conn).size(), "Fresh DB must have 0 departments");
            assertEquals(0, new JdbcDoctorDao().findAll(conn).size(), "Fresh DB must have 0 doctors");
            assertEquals(0, new JdbcPatientDao().findAll(conn).size(), "Fresh DB must have 0 patients");
            assertEquals(0, new JdbcUserDao().findAll(conn).size(), "Fresh DB must have 0 users");

            // Verify default settings
            Settings settings = new JdbcSettingsDao().load(conn);
            assertNotNull(settings, "Default Settings must be initialized");
            assertEquals(100.0, settings.getSeverityWeight(), 0.001);
            assertEquals(3.0, settings.getWaitWeightPerMin(), 0.001);
            assertEquals(150.0, settings.getFairnessBoost(), 0.001);
            assertEquals(45, settings.getFairnessThresholdScheduled());
            assertEquals(45, settings.getFairnessThresholdWalkin());
            assertEquals(2, settings.getMaxConsecutiveEmergencies());
        }
    }

    @Test
    @DisplayName("Test 3: Token sequences are generated on demand inside caller's transaction")
    void testTokenSequenceGeneratedOnDemand() throws Exception {
        String dbPath = tempDir.resolve("test_seq.db").toString();
        ConnectionProvider cp = new ConnectionProvider(dbPath);
        new SchemaInitializer(cp).initialize();
        new DefaultsInitializer(cp).initializeIfEmpty();

        try (Connection conn = cp.getConnection()) {
            JdbcTokenSequenceDao seqDao = new JdbcTokenSequenceDao();

            // First request for CARD-E generates 1
            int firstVal = seqDao.nextValue(conn, "CARD", "E");
            assertEquals(1, firstVal, "First sequence value for brand-new department/category must be 1");

            // Second request increments to 2
            int secondVal = seqDao.nextValue(conn, "CARD", "E");
            assertEquals(2, secondVal, "Second sequence value must increment to 2");

            // Different department and category starts at 1
            int genWalkin = seqDao.nextValue(conn, "GEN", "W");
            assertEquals(1, genWalkin, "Independent sequence must start at 1");
        }
    }
}
