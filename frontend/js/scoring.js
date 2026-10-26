/**
 * SmartOPD – Shared Priority Scoring & Triage Policy Engine
 * File: js/scoring.js
 * 
 * ============================================================================
 * Centralized, Single Source of Truth for Algorithmic Scoring & Fairness
 * 
 * Shared across:
 * 1. Live OPD Queue Simulator / Mock State (js/queue-mock.js)
 * 2. Discrete-Event Simulation Engine (js/simulator.js)
 * 3. Parameter Sweep & Optimization Suite (js/tests/tune.js)
 * 
 * Runs in both Browser (window.SmartOPDScoring) and Node.js environments.
 * ============================================================================
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof exports === 'object') {
    module.exports = factory();
  } else {
    root.SmartOPDScoring = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {

  // Chosen from a 48-combination sweep over seeds 1-30; weight 3 keeps severity dominant (about 33 minutes of waiting to climb one severity level)
  const DEFAULT_SETTINGS = {
    SEVERITY_WEIGHT: 100,             // 100 points per clinical triage level (1-5)
    WAIT_WEIGHT_PER_MIN: 3,           // 3 points per minute spent waiting in lobby
    FAIRNESS_BOOST: 150,              // Bonus points added once waiting exceeds threshold
    FAIRNESS_THRESHOLD_SCHEDULED: 45, // 45 minutes threshold for scheduled appointments
    FAIRNESS_THRESHOLD_WALKIN: 45,    // 45 minutes threshold for walk-in patients
    MAX_CONSECUTIVE_EMERGENCIES: 2,   // Fairness cap: max consecutive emergencies before serving starving patient
    EMERGENCY_BUFFER_SLOTS_DEFAULT: 2,// Default reserved emergency buffer slots per doctor
    EMERGENCY_OVERRIDE_SEVERITY: 5,   // Level 5 triggers unconditional emergency bypass
    EMERGENCY_OVERRIDE_SCORE: 10000,  // Guaranteed top-of-queue priority score
    APPOINTMENT_URGENCY_MAX: 60       // Max bonus points for scheduled arrival proximity
  };

  /**
   * Resolves effective settings by merging defaults, live queue settings, and custom overrides
   */
  function resolveSettings(customSettings) {
    if (customSettings && typeof customSettings === 'object' && customSettings.SEVERITY_WEIGHT !== undefined) {
      return customSettings;
    }
    let liveSettings = {};
    if (typeof window !== 'undefined' && window.SmartOPDQueue && typeof window.SmartOPDQueue.getSettings === 'function') {
      try {
        liveSettings = window.SmartOPDQueue.getSettings() || {};
      } catch (e) {
        liveSettings = {};
      }
    } else if (typeof global !== 'undefined' && global.window && global.window.SmartOPDQueue && typeof global.window.SmartOPDQueue.getSettings === 'function') {
      try {
        liveSettings = global.window.SmartOPDQueue.getSettings() || {};
      } catch (e) {
        liveSettings = {};
      }
    }
    return {
      ...DEFAULT_SETTINGS,
      ...liveSettings,
      ...(customSettings || {})
    };
  }

  /**
   * Computes minutes waited by the patient, handling both discrete sim minutes and real-time Date objects
   */
  function getWaitedMinutes(patient, options = {}) {
    let now = null;
    let currentMinute = undefined;

    if (options instanceof Date) {
      now = options;
    } else if (typeof options === 'number') {
      currentMinute = options;
    } else if (options && typeof options === 'object') {
      now = options.now || null;
      currentMinute = options.currentMinute;
    }

    if (currentMinute !== undefined && patient.arrivalMinute !== undefined) {
      return Math.max(0, currentMinute - patient.arrivalMinute);
    }

    if (patient.registrationTime) {
      const regTime = new Date(patient.registrationTime);
      let nowDate = now;
      if (!nowDate) {
        if (typeof window !== 'undefined' && window.SmartOPDQueue && typeof window.SmartOPDQueue.getNow === 'function') {
          nowDate = window.SmartOPDQueue.getNow();
        } else if (typeof global !== 'undefined' && global.window && global.window.SmartOPDQueue && typeof global.window.SmartOPDQueue.getNow === 'function') {
          nowDate = global.window.SmartOPDQueue.getNow();
        } else {
          nowDate = new Date();
        }
      }
      return Math.max(0, Math.floor((nowDate.getTime() - regTime.getTime()) / 60000));
    }

    if (patient.arrivalMinute !== undefined) {
      return Math.max(0, (currentMinute || 0) - patient.arrivalMinute);
    }

    return 0;
  }

  /**
   * Calculates dynamic Priority Score for a patient using unified policy rules
   */
  function calculatePriorityScore(patient, options = {}) {
    let customSettings = null;
    let now = null;
    let currentMinute = undefined;

    if (options instanceof Date) {
      now = options;
    } else if (typeof options === 'number') {
      currentMinute = options;
    } else if (options && typeof options === 'object') {
      now = options.now || null;
      currentMinute = options.currentMinute;
      customSettings = options.settings || options.policySettings || null;
    }

    const settings = resolveSettings(customSettings);
    const waitedMinutes = getWaitedMinutes(patient, { now, currentMinute });

    // 1. Level 5 Emergency Override (10,000 pts)
    const overrideSev = settings.EMERGENCY_OVERRIDE_SEVERITY ?? 5;
    const overrideScore = settings.EMERGENCY_OVERRIDE_SCORE ?? 10000;
    if (patient.severity === overrideSev || (patient.category === 'Emergency' && patient.severity >= 5)) {
      return overrideScore + (waitedMinutes * 0.1);
    }

    // 2. Base Clinical Severity (Severity * Weight)
    let score = (patient.severity || 1) * (settings.SEVERITY_WEIGHT ?? 100);

    // 3. Waiting Time Aging Factor (Wait minutes * Weight)
    score += waitedMinutes * (settings.WAIT_WEIGHT_PER_MIN ?? 2);

    // 4. Appointment Urgency (Scheduled patients only)
    if (patient.category === 'Scheduled') {
      const urgencyMax = settings.APPOINTMENT_URGENCY_MAX ?? 60;

      if (patient.appointmentMinute !== undefined && patient.appointmentMinute !== null && currentMinute !== undefined) {
        const diffMinutes = patient.appointmentMinute - currentMinute;
        if (diffMinutes <= 0) {
          score += urgencyMax;
        } else if (diffMinutes <= 30) {
          score += Math.max(0, 30 - diffMinutes) * 1.5;
        }
      } else if (patient.appointmentTime) {
        let nowDate = now;
        if (!nowDate) {
          if (typeof window !== 'undefined' && window.SmartOPDQueue && typeof window.SmartOPDQueue.getNow === 'function') {
            nowDate = window.SmartOPDQueue.getNow();
          } else if (typeof global !== 'undefined' && global.window && global.window.SmartOPDQueue && typeof global.window.SmartOPDQueue.getNow === 'function') {
            nowDate = global.window.SmartOPDQueue.getNow();
          } else {
            nowDate = new Date();
          }
        }
        const parts = String(patient.appointmentTime).split(':').map(Number);
        if (parts.length >= 2 && !isNaN(parts[0]) && !isNaN(parts[1])) {
          const slotDate = new Date(nowDate);
          slotDate.setHours(parts[0], parts[1], 0, 0);
          const diffMinutes = Math.floor((slotDate.getTime() - nowDate.getTime()) / 60000);
          if (diffMinutes <= 0) {
            score += urgencyMax;
          } else if (diffMinutes <= 30) {
            score += Math.max(0, 30 - diffMinutes) * 1.5;
          }
        }
      }
    }

    // 5. Anti-Starvation Fairness Boost (+pts once threshold exceeded)
    const threshold = patient.category === 'Scheduled'
      ? (settings.FAIRNESS_THRESHOLD_SCHEDULED ?? 30)
      : (settings.FAIRNESS_THRESHOLD_WALKIN ?? 45);

    if (waitedMinutes >= threshold) {
      score += (settings.FAIRNESS_BOOST ?? 50);
    }

    // 6. Skip penalty (if temporarily skipped)
    if (patient.skipped) {
      score -= 300;
    }

    return Math.round(score);
  }

  /**
   * Checks if fairness boost is actively applied to a patient
   */
  function isFairnessBoosted(patient, options = {}) {
    if (patient.severity >= 5) return false;

    let customSettings = null;
    let now = null;
    let currentMinute = undefined;

    if (options instanceof Date) {
      now = options;
    } else if (typeof options === 'number') {
      currentMinute = options;
    } else if (options && typeof options === 'object') {
      now = options.now || null;
      currentMinute = options.currentMinute;
      customSettings = options.settings || options.policySettings || null;
    }

    const settings = resolveSettings(customSettings);
    const waitedMinutes = getWaitedMinutes(patient, { now, currentMinute });
    const threshold = patient.category === 'Scheduled'
      ? (settings.FAIRNESS_THRESHOLD_SCHEDULED ?? 30)
      : (settings.FAIRNESS_THRESHOLD_WALKIN ?? 45);

    return waitedMinutes >= threshold;
  }

  /**
   * Evaluates Emergency Cap rule for next patient selection
   * If doctor has reached the consecutive emergency cap, serves the longest-waiting fairness boosted patient
   * Returns { selectedPatient, fairnessCapTriggered }
   */
  function selectNextPatientWithEmergencyCap(doctor, waitingQueue, options = {}) {
    doctor.consecutiveEmergencies = doctor.consecutiveEmergencies || 0;
    if (!waitingQueue || waitingQueue.length === 0) {
      return { selectedPatient: null, fairnessCapTriggered: false };
    }

    let customSettings = null;
    if (options && typeof options === 'object') {
      customSettings = options.settings || options.policySettings || null;
    }
    const settings = resolveSettings(customSettings);
    const maxConsecutive = settings.MAX_CONSECUTIVE_EMERGENCIES ?? 2;

    let selectedPatient = waitingQueue[0];
    let fairnessCapTriggered = false;

    if (doctor.consecutiveEmergencies >= maxConsecutive) {
      // If top candidate is not Severity 5, prioritize longest-waiting fairness boosted patient
      if (selectedPatient.severity < 5 && (selectedPatient.severity === 4 || selectedPatient.category === 'Emergency')) {
        const fairnessPatient = waitingQueue.find(p => isFairnessBoosted(p, options));
        if (fairnessPatient) {
          selectedPatient = fairnessPatient;
          fairnessCapTriggered = true;
          doctor.consecutiveEmergencies = 0; // Cap enforced, reset counter
        }
      }
    }

    // Update consecutive emergency counter
    if (!fairnessCapTriggered) {
      if (selectedPatient.severity === 5 || selectedPatient.category === 'Emergency') {
        doctor.consecutiveEmergencies++;
      } else {
        doctor.consecutiveEmergencies = 0;
      }
    }

    return { selectedPatient, fairnessCapTriggered };
  }

  /**
   * Plain-English explainability rationale
   */
  function explain(patient, options = {}) {
    let customSettings = null;
    let now = null;
    let currentMinute = undefined;

    if (options instanceof Date) {
      now = options;
    } else if (typeof options === 'number') {
      currentMinute = options;
    } else if (options && typeof options === 'object') {
      now = options.now || null;
      currentMinute = options.currentMinute;
      customSettings = options.settings || options.policySettings || null;
    }

    const settings = resolveSettings(customSettings);
    const waitedMinutes = getWaitedMinutes(patient, { now, currentMinute });

    if (patient.severity === 5) {
      return 'Severity 5 emergency override: critical case escalated to top of queue ahead of routine visits.';
    }

    if (patient.severity === 4) {
      return `Urgent triage priority (Severity Level 4) with ${waitedMinutes} mins waiting time.`;
    }

    const threshold = patient.category === 'Scheduled'
      ? (settings.FAIRNESS_THRESHOLD_SCHEDULED ?? 30)
      : (settings.FAIRNESS_THRESHOLD_WALKIN ?? 45);

    if (waitedMinutes >= threshold) {
      return `Fairness boost applied (+${settings.FAIRNESS_BOOST} pts): waiting time (${waitedMinutes} min) exceeded ${threshold}-min threshold.`;
    }

    if (patient.category === 'Scheduled' && patient.appointmentTime) {
      return `Scheduled slot (${patient.appointmentTime}) with Level ${patient.severity} severity and ${waitedMinutes} mins waiting time.`;
    }

    return `Standard queue priority: Severity Level ${patient.severity} with ${waitedMinutes} mins in queue.`;
  }

  return {
    DEFAULT_SETTINGS,
    resolveSettings,
    getWaitedMinutes,
    calculatePriorityScore,
    isFairnessBoosted,
    selectNextPatientWithEmergencyCap,
    explain
  };
}));
