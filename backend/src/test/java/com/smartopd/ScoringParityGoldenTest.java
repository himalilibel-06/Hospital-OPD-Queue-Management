package com.smartopd;

import com.smartopd.config.Settings;
import com.smartopd.model.EmergencyPatient;
import com.smartopd.model.Patient;
import com.smartopd.model.ScheduledPatient;
import com.smartopd.model.Token;
import com.smartopd.model.WalkInPatient;
import com.smartopd.scoring.SmartOpdStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScoringParityGoldenTest - Proves 100% mathematical parity between Java backend
 * scoring and the JavaScript frontend specification (frontend/js/scoring.js).
 * 
 * ============================================================================
 * Golden Parity Table:
 * 1. Emerg-Sev5-0m:           Score=10000.0, Boosted=false
 * 2. Emerg-Sev5-20m:          Score=10002.0, Boosted=false
 * 3. Emerg-Sev4-10m:          Score=430.0,   Boosted=false
 * 4. Sched-Sev1-0m-Due:       Score=160.0,   Boosted=false
 * 5. Sched-Sev2-50m-Boosted:  Score=560.0,   Boosted=true
 * 6. Walkin-Sev2-15m-Normal:  Score=245.0,   Boosted=false
 * 7. Walkin-Sev1-46m-Boosted: Score=388.0,   Boosted=true
 * 8. Walkin-Sev3-20m-Skipped: Score=60.0,    Boosted=false
 * ============================================================================
 */
public class ScoringParityGoldenTest {

    private SmartOpdStrategy strategy;
    private Settings settings;
    private Instant now;

    @BeforeEach
    void setUp() {
        strategy = new SmartOpdStrategy();
        settings = new Settings(); // Defaults: 100 sev, 3.0 wait, 150 boost, 45 threshold
        now = Instant.parse("2026-10-22T10:00:00Z");
    }

    @Test
    @DisplayName("Case 1: Emergency Level 5 with 0m wait -> 10,000 pts override")
    void testCase1_EmergSev5_0m() {
        Patient p = new EmergencyPatient("P1", "Patient 1", 50, "M", "9876500001",
                new Token("GEN", "E", 1), "DOC-1", "Doctor", "Room", "General", 5, now, "Cardiac");

        double score = strategy.calculateScore(p, now, settings);
        boolean boosted = strategy.isFairnessBoosted(p, now, settings);
        String explanation = strategy.explain(p, now, settings);

        assertEquals(10000.0, score, "Score must match frontend exactly (10000)");
        assertFalse(boosted, "Severity 5 cannot be fairness boosted");
        assertEquals("Severity 5 emergency override: critical case escalated to top of queue ahead of routine visits.", explanation);
    }

    @Test
    @DisplayName("Case 2: Emergency Level 5 with 20m wait -> 10,002 pts (10000 + 20*0.1)")
    void testCase2_EmergSev5_20m() {
        Patient p = new EmergencyPatient("P2", "Patient 2", 52, "F", "9876500002",
                new Token("GEN", "E", 2), "DOC-1", "Doctor", "Room", "General", 5, now.minus(Duration.ofMinutes(20)), "Stroke");

        double score = strategy.calculateScore(p, now, settings);
        boolean boosted = strategy.isFairnessBoosted(p, now, settings);
        String explanation = strategy.explain(p, now, settings);

        assertEquals(10002.0, score, "Score must match frontend exactly (10002)");
        assertFalse(boosted, "Severity 5 cannot be fairness boosted");
        assertEquals("Severity 5 emergency override: critical case escalated to top of queue ahead of routine visits.", explanation);
    }

    @Test
    @DisplayName("Case 3: Emergency Level 4 with 10m wait -> 430 pts (4*100 + 10*3)")
    void testCase3_EmergSev4_10m() {
        Patient p = new EmergencyPatient("P3", "Patient 3", 45, "M", "9876500003",
                new Token("GEN", "E", 3), "DOC-1", "Doctor", "Room", "General", 4, now.minus(Duration.ofMinutes(10)), "Trauma");

        double score = strategy.calculateScore(p, now, settings);
        boolean boosted = strategy.isFairnessBoosted(p, now, settings);
        String explanation = strategy.explain(p, now, settings);

        assertEquals(430.0, score, "Score must match frontend exactly (430)");
        assertFalse(boosted, "10m wait has not reached 45m threshold");
        assertEquals("Urgent triage priority (Severity Level 4) with 10 mins waiting time.", explanation);
    }

    @Test
    @DisplayName("Case 4: Scheduled Level 1 with 0m wait & Due slot -> 160 pts (1*100 + 0*3 + 60 urgency)")
    void testCase4_SchedSev1_0m_Due() {
        Patient p = new ScheduledPatient("P4", "Patient 4", 30, "F", "9876500004",
                new Token("GEN", "S", 4), "DOC-1", "Doctor", "Room", "General", 1, now, "10:00", "Checkup");

        double score = strategy.calculateScore(p, now, settings);
        boolean boosted = strategy.isFairnessBoosted(p, now, settings);
        String explanation = strategy.explain(p, now, settings);

        assertEquals(160.0, score, "Score must match frontend exactly (160)");
        assertFalse(boosted);
        assertEquals("Scheduled slot (10:00) with Level 1 severity and 0 mins waiting time.", explanation);
    }

    @Test
    @DisplayName("Case 5: Scheduled Level 2 with 50m wait & Boosted -> 560 pts (2*100 + 50*3 + 60 urgency + 150 boost)")
    void testCase5_SchedSev2_50m_Boosted() {
        Patient p = new ScheduledPatient("P5", "Patient 5", 38, "M", "9876500005",
                new Token("GEN", "S", 5), "DOC-1", "Doctor", "Room", "General", 2, now.minus(Duration.ofMinutes(50)), "09:10", "Diabetes");

        double score = strategy.calculateScore(p, now, settings);
        boolean boosted = strategy.isFairnessBoosted(p, now, settings);
        String explanation = strategy.explain(p, now, settings);

        assertEquals(560.0, score, "Score must match frontend exactly (560)");
        assertTrue(boosted, "50m wait >= 45m threshold must award fairness boost");
        assertEquals("Fairness boost applied (+150 pts): waiting time (50 min) exceeded 45-min threshold.", explanation);
    }

    @Test
    @DisplayName("Case 6: Walk-in Level 2 with 15m wait -> 245 pts (2*100 + 15*3)")
    void testCase6_WalkinSev2_15m_Normal() {
        Patient p = new WalkInPatient("P6", "Patient 6", 26, "F", "9876500006",
                new Token("GEN", "W", 6), "DOC-1", "Doctor", "Room", "General", 2, now.minus(Duration.ofMinutes(15)), "Fever");

        double score = strategy.calculateScore(p, now, settings);
        boolean boosted = strategy.isFairnessBoosted(p, now, settings);
        String explanation = strategy.explain(p, now, settings);

        assertEquals(245.0, score, "Score must match frontend exactly (245)");
        assertFalse(boosted, "15m < 45m threshold");
        assertEquals("Standard queue priority: Severity Level 2 with 15 mins in queue.", explanation);
    }

    @Test
    @DisplayName("Case 7: Walk-in Level 1 with 46m wait & Boosted -> 388 pts (1*100 + 46*3 + 150 boost)")
    void testCase7_WalkinSev1_46m_Boosted() {
        Patient p = new WalkInPatient("P7", "Patient 7", 22, "M", "9876500007",
                new Token("GEN", "W", 7), "DOC-1", "Doctor", "Room", "General", 1, now.minus(Duration.ofMinutes(46)), "Cold");

        double score = strategy.calculateScore(p, now, settings);
        boolean boosted = strategy.isFairnessBoosted(p, now, settings);
        String explanation = strategy.explain(p, now, settings);

        assertEquals(388.0, score, "Score must match frontend exactly (388)");
        assertTrue(boosted, "46m wait >= 45m threshold");
        assertEquals("Fairness boost applied (+150 pts): waiting time (46 min) exceeded 45-min threshold.", explanation);
    }

    @Test
    @DisplayName("Case 8: Walk-in Level 3 with 20m wait & Skipped -> 60 pts (3*100 + 20*3 - 300 penalty)")
    void testCase8_WalkinSev3_20m_Skipped() {
        Patient p = new WalkInPatient("P8", "Patient 8", 41, "F", "9876500008",
                new Token("GEN", "W", 8), "DOC-1", "Doctor", "Room", "General", 3, now.minus(Duration.ofMinutes(20)), "Sprain");
        p.setSkipped(true);

        double score = strategy.calculateScore(p, now, settings);
        boolean boosted = strategy.isFairnessBoosted(p, now, settings);
        String explanation = strategy.explain(p, now, settings);

        assertEquals(60.0, score, "Score must match frontend exactly (60)");
        assertFalse(boosted);
        assertEquals("Standard queue priority: Severity Level 3 with 20 mins in queue.", explanation);
    }
}
