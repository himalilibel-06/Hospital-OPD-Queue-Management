package com.smartopd;

import com.smartopd.config.Settings;
import com.smartopd.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SettingsValidationTest - Verifies that hospital settings reject out-of-range
 * values with plain-English readable error messages matching the frontend.
 * 
 * Ported directly from frontend/js/tests/run-tests.js (Test 10).
 */
public class SettingsValidationTest {

    @Test
    @DisplayName("Test 6: Settings Validation Rejects Bad Values with Readable Messages")
    void testSettingsValidationRejectsBadValues() {
        Settings settings = new Settings();

        // 1. Severity weight out of range (< 10)
        ValidationException ex1 = assertThrows(ValidationException.class, () -> settings.setSeverityWeight(-10));
        assertTrue(ex1.getMessage().contains("Severity Weight must be a valid number between 10 and 500."));

        // 2. Wait weight per min out of range (> 10)
        ValidationException ex2 = assertThrows(ValidationException.class, () -> settings.setWaitWeightPerMin(15));
        assertTrue(ex2.getMessage().contains("Wait Weight Per Minute must be a valid number between 0.1 and 10."));

        // 3. Max consecutive emergencies cap out of range (< 1)
        ValidationException ex3 = assertThrows(ValidationException.class, () -> settings.setMaxConsecutiveEmergencies(0));
        assertTrue(ex3.getMessage().contains("Max Consecutive Emergencies cap must be a valid integer between 1 and 10."));

        // 4. Default emergency buffer slots out of range (> 10)
        ValidationException ex4 = assertThrows(ValidationException.class, () -> settings.setEmergencyBufferSlotsDefault(25));
        assertTrue(ex4.getMessage().contains("Default Emergency Buffer Slots must be a valid integer between 0 and 10."));

        // 5. Fairness boost points (< 0)
        ValidationException ex5 = assertThrows(ValidationException.class, () -> settings.setFairnessBoost(-5));
        assertTrue(ex5.getMessage().contains("Fairness Boost points must be a valid number between 0 and 500."));

        // 6. Scheduled threshold (< 5)
        ValidationException ex6 = assertThrows(ValidationException.class, () -> settings.setFairnessThresholdScheduled(2));
        assertTrue(ex6.getMessage().contains("Scheduled Fairness Threshold must be a valid number between 5 and 180 minutes."));

        // 7. Walk-in threshold (> 240)
        ValidationException ex7 = assertThrows(ValidationException.class, () -> settings.setFairnessThresholdWalkin(300));
        assertTrue(ex7.getMessage().contains("Walk-in Fairness Threshold must be a valid number between 5 and 240 minutes."));
    }

    @Test
    @DisplayName("Test 6b: resetToDefaults restores tuned defaults (150 / 3 / 45)")
    void testResetToDefaults() {
        Settings settings = new Settings();
        settings.setFairnessBoost(200);
        settings.setWaitWeightPerMin(5.0);
        settings.setFairnessThresholdScheduled(60);

        settings.resetToDefaults();

        assertEquals(150.0, settings.getFairnessBoost(), "Fairness boost must restore to 150");
        assertEquals(3.0, settings.getWaitWeightPerMin(), "Wait weight per min must restore to 3.0");
        assertEquals(45, settings.getFairnessThresholdScheduled(), "Scheduled threshold must restore to 45");
        assertEquals(45, settings.getFairnessThresholdWalkin(), "Walk-in threshold must restore to 45");
        assertEquals(2, settings.getMaxConsecutiveEmergencies(), "Emergency cap must restore to 2");
    }
}
