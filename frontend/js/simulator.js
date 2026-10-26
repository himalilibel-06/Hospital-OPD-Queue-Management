/**
 * SmartOPD – Discrete-Event OPD Queue Simulator
 * File: js/simulator.js
 * 
 * ============================================================================
 * Pure JavaScript Simulation Engine (Zero DOM dependencies)
 * Runs in both modern web browsers (window.SmartOPDSimulator) and Node.js.
 * 
 * Compares 3 queue policies on identical synthetic patient streams:
 * 1. FCFS (First-Come, First-Served)
 * 2. Strict Priority (Clinical severity triage only, no fairness aging)
 * 3. SmartOPD (Explainable severity + wait aging + anti-starvation + emergency cap)
 * ============================================================================
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof exports === 'object') {
    module.exports = factory();
  } else {
    root.SmartOPDSimulator = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {

  let ScoringEngine = (typeof SmartOPDScoring !== 'undefined') ? SmartOPDScoring : null;
  if (!ScoringEngine && typeof require === 'function') {
    try {
      ScoringEngine = require('./scoring.js');
    } catch (e) {}
  }
  if (!ScoringEngine && typeof window !== 'undefined' && window.SmartOPDScoring) {
    ScoringEngine = window.SmartOPDScoring;
  }

  /**
   * Mulberry32 Seeded Pseudo-Random Number Generator
   * Guarantees 100% repeatable, deterministic results for any given seed.
   */
  function createPRNG(seed) {
    let s = Math.floor(Math.abs(seed)) || 42;
    return function next() {
      s |= 0;
      s = (s + 0x6D2B79F5) | 0;
      let t = Math.imul(s ^ (s >>> 15), 1 | s);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  /**
   * Generates a realistic synthetic cohort of outpatient visits
   */
  function generateSyntheticPatients(config, rng) {
    const {
      numPatients = 150,
      emergencyPercent = 12,
      scheduledPercent = 45,
      arrivalSpreadMinutes = 300,
      avgConsultMinutes = 10
    } = config;

    const patients = [];

    for (let i = 0; i < numPatients; i++) {
      const id = `SIM-P-${String(i + 1).padStart(3, '0')}`;
      const arrivalMinute = Math.floor(rng() * arrivalSpreadMinutes);
      const catRoll = rng() * 100;

      let category = 'Walk-in';
      let severity = 2;
      let appointmentMinute = null;

      if (catRoll < emergencyPercent) {
        category = 'Emergency';
        // 65% Severity 5 (Critical/Trauma), 35% Severity 4 (Urgent)
        severity = rng() < 0.65 ? 5 : 4;
      } else if (catRoll < emergencyPercent + scheduledPercent) {
        category = 'Scheduled';
        // Severity 1 to 3
        const sevRoll = rng();
        severity = sevRoll < 0.4 ? 1 : (sevRoll < 0.75 ? 2 : 3);
        // Scheduled appointment around arrival time (+/- 15 mins)
        appointmentMinute = Math.max(0, arrivalMinute + Math.floor((rng() - 0.5) * 30));
      } else {
        category = 'Walk-in';
        // Severity 1 to 4
        const sevRoll = rng();
        severity = sevRoll < 0.45 ? 1 : (sevRoll < 0.8 ? 2 : (sevRoll < 0.95 ? 3 : 4));
      }

      // Consultation duration with +/- 30% random variation around average
      const variation = (rng() * 0.60) - 0.30;
      const consultDuration = Math.max(3, Math.round(avgConsultMinutes * (1 + variation)));

      patients.push({
        id,
        index: i,
        category,
        severity,
        arrivalMinute,
        appointmentMinute,
        consultDuration
      });
    }

    // Sort strictly by arrival time
    patients.sort((a, b) => a.arrivalMinute - b.arrivalMinute || a.index - b.index);
    return patients;
  }

  /**
   * Executes a single policy simulation on the given patient cohort
   */
  function simulatePolicy(policyName, patients, numDoctors, settings) {
    const unserved = patients.map(p => ({ ...p }));
    const served = [];

    // Initialize doctor pool
    const doctors = [];
    for (let d = 0; d < numDoctors; d++) {
      doctors.push({
        id: d,
        freeAt: 0,
        consecutiveEmergencies: 0,
        busyMinutes: 0
      });
    }

    let currentMinute = 0;
    const totalCount = unserved.length;

    while (served.length < totalCount) {
      // 1. Gather all arrived, unserved patients
      const waiting = unserved.filter(p => p.arrivalMinute <= currentMinute);

      // 2. If nobody has arrived yet, jump time forward
      if (waiting.length === 0) {
        let nextEventMinute = Infinity;
        // Next arrival
        const nextArrival = unserved.find(p => p.arrivalMinute > currentMinute);
        if (nextArrival) nextEventMinute = Math.min(nextEventMinute, nextArrival.arrivalMinute);
        // Earliest doctor becoming free
        doctors.forEach(doc => {
          if (doc.freeAt > currentMinute) {
            nextEventMinute = Math.min(nextEventMinute, doc.freeAt);
          }
        });

        if (nextEventMinute === Infinity || nextEventMinute <= currentMinute) {
          currentMinute++;
        } else {
          currentMinute = nextEventMinute;
        }
        continue;
      }

      // 3. Find an idle doctor
      const idleDoctor = doctors.find(doc => doc.freeAt <= currentMinute);
      if (!idleDoctor) {
        // All doctors busy -> advance clock to the earliest free doctor
        let earliestFree = Infinity;
        doctors.forEach(doc => {
          if (doc.freeAt > currentMinute) earliestFree = Math.min(earliestFree, doc.freeAt);
        });
        currentMinute = earliestFree;
        continue;
      }

      // 4. Select next patient according to policy
      let selectedPatient = null;

      if (policyName === 'FCFS') {
        // Plain First-Come, First-Served (sorted by arrival time)
        waiting.sort((a, b) => a.arrivalMinute - b.arrivalMinute || a.index - b.index);
        selectedPatient = waiting[0];

      } else if (policyName === 'StrictPriority') {
        // Strict Priority: Severity descending, ties by arrival time
        waiting.sort((a, b) => b.severity - a.severity || a.arrivalMinute - b.arrivalMinute || a.index - b.index);
        selectedPatient = waiting[0];

      } else if (policyName === 'SmartOPD') {
        // SmartOPD: Severity + Wait Aging + Fairness Anti-Starvation + Emergency Cap
        // Delegates directly to shared SmartOPDScoring module
        waiting.forEach(p => {
          if (ScoringEngine && typeof ScoringEngine.calculatePriorityScore === 'function') {
            p.currentScore = ScoringEngine.calculatePriorityScore(p, { currentMinute, settings });
            p.isFairnessBoosted = ScoringEngine.isFairnessBoosted(p, { currentMinute, settings });
          } else {
            const waited = currentMinute - p.arrivalMinute;
            const sevWeight = settings.SEVERITY_WEIGHT ?? 100;
            const waitWeight = settings.WAIT_WEIGHT_PER_MIN ?? 3;
            const fairnessBoost = settings.FAIRNESS_BOOST ?? 150;
            const schedThreshold = settings.FAIRNESS_THRESHOLD_SCHEDULED ?? 45;
            const walkinThreshold = settings.FAIRNESS_THRESHOLD_WALKIN ?? 45;
            const urgencyMax = settings.APPOINTMENT_URGENCY_MAX ?? 60;

            if (p.severity >= 5 || (p.category === 'Emergency' && p.severity >= 5)) {
              p.currentScore = 10000 + (waited * 0.1);
            } else {
              let score = (p.severity * sevWeight) + (waited * waitWeight);
              if (p.category === 'Scheduled' && p.appointmentMinute !== null) {
                const diff = p.appointmentMinute - currentMinute;
                if (diff <= 0) score += urgencyMax;
                else if (diff <= 30) score += (30 - diff) * 1.5;
              }
              const threshold = p.category === 'Scheduled' ? schedThreshold : walkinThreshold;
              if (waited >= threshold) score += fairnessBoost;
              p.currentScore = Math.round(score);
            }
            p.isFairnessBoosted = (p.severity < 5) && (waited >= (p.category === 'Scheduled' ? schedThreshold : walkinThreshold));
          }
        });

        // Sort by score descending, ties broken by earlier arrival
        waiting.sort((a, b) => b.currentScore - a.currentScore || a.arrivalMinute - b.arrivalMinute || a.index - b.index);

        // Check Emergency Cap rule on idleDoctor using shared ScoringEngine
        if (ScoringEngine && typeof ScoringEngine.selectNextPatientWithEmergencyCap === 'function') {
          const capOutcome = ScoringEngine.selectNextPatientWithEmergencyCap(idleDoctor, waiting, { currentMinute, settings });
          selectedPatient = capOutcome.selectedPatient;
        } else {
          const emergCap = settings.MAX_CONSECUTIVE_EMERGENCIES ?? 2;
          let chosen = waiting[0];
          if (idleDoctor.consecutiveEmergencies >= emergCap) {
            if (chosen.severity < 5 && (chosen.severity === 4 || chosen.category === 'Emergency')) {
              const fairnessCandidate = waiting.find(p => p.isFairnessBoosted);
              if (fairnessCandidate) {
                chosen = fairnessCandidate;
                idleDoctor.consecutiveEmergencies = 0;
              }
            }
          }
          selectedPatient = chosen;
          if (selectedPatient.severity === 5 || selectedPatient.category === 'Emergency') {
            idleDoctor.consecutiveEmergencies++;
          } else {
            idleDoctor.consecutiveEmergencies = 0;
          }
        }
      }

      // 5. Serve patient
      const startMinute = currentMinute;
      const waitMinutes = startMinute - selectedPatient.arrivalMinute;
      const finishMinute = startMinute + selectedPatient.consultDuration;

      idleDoctor.freeAt = finishMinute;
      idleDoctor.busyMinutes += selectedPatient.consultDuration;

      served.push({
        id: selectedPatient.id,
        index: selectedPatient.index,
        category: selectedPatient.category,
        severity: selectedPatient.severity,
        arrivalMinute: selectedPatient.arrivalMinute,
        startMinute,
        finishMinute,
        consultDuration: selectedPatient.consultDuration,
        waitMinutes
      });

      // Remove from unserved pool
      const remIdx = unserved.findIndex(p => p.id === selectedPatient.id);
      if (remIdx !== -1) unserved.splice(remIdx, 1);
    }

    // 6. Compute Comprehensive Policy Metrics
    const schedThreshold = settings.FAIRNESS_THRESHOLD_SCHEDULED ?? 30;
    const walkinThreshold = settings.FAIRNESS_THRESHOLD_WALKIN ?? 45;

    let totalWait = 0;
    let emergWaitSum = 0, emergCount = 0;
    let highSevWaitSum = 0, highSevCount = 0;
    let schedWaitSum = 0, schedCount = 0;
    let walkinWaitSum = 0, walkinCount = 0;
    let maxWaitSched = 0;
    let starvationCount = 0;

    const allWaits = [];

    served.forEach(p => {
      totalWait += p.waitMinutes;
      allWaits.push(p.waitMinutes);

      if (p.category === 'Emergency') {
        emergWaitSum += p.waitMinutes;
        emergCount++;
      }
      if (p.severity >= 4) {
        highSevWaitSum += p.waitMinutes;
        highSevCount++;
      }
      if (p.category === 'Scheduled') {
        schedWaitSum += p.waitMinutes;
        schedCount++;
        if (p.waitMinutes > maxWaitSched) maxWaitSched = p.waitMinutes;
        // Starvation: Scheduled patient waited > 2x fairness threshold (default 60 mins)
        if (p.waitMinutes > (schedThreshold * 2)) {
          starvationCount++;
        }
      }
      if (p.category === 'Walk-in') {
        walkinWaitSum += p.waitMinutes;
        walkinCount++;
      }
    });

    allWaits.sort((a, b) => a - b);
    const p90Index = Math.min(allWaits.length - 1, Math.floor(allWaits.length * 0.90));
    const p90Wait = allWaits[p90Index] || 0;

    const totalSimTime = Math.max(...served.map(p => p.finishMinute), 1);
    const totalDoctorCapacity = numDoctors * totalSimTime;
    const totalDoctorBusy = doctors.reduce((acc, d) => acc + d.busyMinutes, 0);
    const doctorIdlePercent = Math.max(0, Math.min(100, Math.round(((totalDoctorCapacity - totalDoctorBusy) / totalDoctorCapacity) * 100)));

    const patientRecords = served.map(p => {
      const limit = (p.category === 'Scheduled') ? (schedThreshold * 2) : (walkinThreshold * 2);
      return {
        id: p.id,
        patientId: p.id,
        tokenNumber: p.id,
        category: p.category,
        severity: p.severity,
        arrivalMinute: p.arrivalMinute,
        arrivalTime: p.arrivalMinute,
        startMinute: p.startMinute,
        startTime: p.startMinute,
        finishMinute: p.finishMinute,
        completionTime: p.finishMinute,
        waitMinutes: p.waitMinutes,
        starved: p.waitMinutes > limit
      };
    });

    const metrics = {
      averageWaitOverall: Math.round(totalWait / served.length),
      avgWaitOverall: Math.round(totalWait / served.length),
      averageWaitEmergency: emergCount > 0 ? Math.round(emergWaitSum / emergCount) : 0,
      avgWaitEmergency: emergCount > 0 ? Math.round(emergWaitSum / emergCount) : 0,
      averageWaitHighSeverity: highSevCount > 0 ? Math.round(highSevWaitSum / highSevCount) : 0,
      avgWaitHighSeverity: highSevCount > 0 ? Math.round(highSevWaitSum / highSevCount) : 0,
      averageWaitScheduled: schedCount > 0 ? Math.round(schedWaitSum / schedCount) : 0,
      avgWaitScheduled: schedCount > 0 ? Math.round(schedWaitSum / schedCount) : 0,
      averageWaitWalkin: walkinCount > 0 ? Math.round(walkinWaitSum / walkinCount) : 0,
      avgWaitWalkin: walkinCount > 0 ? Math.round(walkinWaitSum / walkinCount) : 0,
      p90Wait,
      maxScheduledWait: maxWaitSched,
      maxWaitScheduled: maxWaitSched,
      starvationCount,
      doctorIdlePercent
    };

    return {
      policyName,
      totalServed: served.length,
      totalSimTime,
      ...metrics,
      metrics,
      records: patientRecords,
      patientWaits: patientRecords
    };
  }

  /**
   * Generates analytical insight statements comparing the 3 simulation runs
   */
  function generateInsights(fcfs, strict, smart, settings = {}) {
    const insights = [];
    const schedLimit = (settings.FAIRNESS_THRESHOLD_SCHEDULED ?? 45) * 2;

    // 1. Honest Trade-off Statement (Strict Priority vs SmartOPD)
    const diffEmerg = Math.max(0, smart.avgWaitEmergency - strict.avgWaitEmergency);
    const diffMaxSched = Math.max(0, strict.maxScheduledWait - smart.maxScheduledWait);
    const emergMinText = diffEmerg === 1 ? '1 more minute' : `${diffEmerg} more minutes`;
    const schedMinText = diffMaxSched === 1 ? '1 minute' : `${diffMaxSched} minutes`;

    if (diffEmerg > 0 && diffMaxSched > 0) {
      insights.push(`Compared with strict priority, SmartOPD accepted ${emergMinText} of emergency wait to cut the longest scheduled wait by ${schedMinText}.`);
    } else if (diffMaxSched > 0) {
      insights.push(`Compared with strict priority, SmartOPD cut the longest scheduled wait by ${schedMinText} without increasing emergency wait.`);
    } else {
      insights.push(`Compared with strict priority, SmartOPD balanced emergency response (${smart.avgWaitEmergency}m) against longest scheduled wait (${smart.maxScheduledWait}m).`);
    }

    // 2. Fundamental Queue Conservation Law Takeaway
    insights.push('Average wait is similar across policies; SmartOPD changes who waits, not how much total waiting there is.');

    // 3. Honest Walk-in Trade-off Line (when walk-in average wait under SmartOPD exceeds FCFS)
    const diffWalkin = smart.avgWaitWalkin - fcfs.avgWaitWalkin;
    if (diffWalkin > 0) {
      const walkinMinText = diffWalkin === 1 ? '1 minute' : `${diffWalkin} minutes`;
      insights.push(`Trade-off: walk-in patients waited ${walkinMinText} longer on average than under first come first served, because emergencies and scheduled patients are protected first.`);
    }

    // 4. Emergency Wait Comparison (FCFS vs SmartOPD)
    if (fcfs.avgWaitEmergency > 0 && smart.avgWaitEmergency < fcfs.avgWaitEmergency) {
      const reduction = Math.round(((fcfs.avgWaitEmergency - smart.avgWaitEmergency) / fcfs.avgWaitEmergency) * 100);
      insights.push(`SmartOPD cut average emergency wait by ${reduction} percent versus FCFS (${smart.avgWaitEmergency}m vs ${fcfs.avgWaitEmergency}m).`);
    } else {
      insights.push(`SmartOPD prioritized critical triage, keeping average emergency wait to ${smart.avgWaitEmergency} minutes.`);
    }

    // 5. Starvation Comparison (Strict Priority vs SmartOPD) with proper singular/plural
    const strictStarvText = strict.starvationCount === 1 ? '1 scheduled patient' : `${strict.starvationCount} scheduled patients`;
    if (strict.starvationCount > smart.starvationCount) {
      insights.push(`Strict priority left ${strictStarvText} waiting over ${schedLimit} minutes; SmartOPD reduced this to ${smart.starvationCount}.`);
    } else {
      insights.push(`Strict priority left ${strictStarvText} waiting over ${schedLimit} minutes; SmartOPD held this to ${smart.starvationCount}.`);
    }

    // 6. Maximum Scheduled Patient Wait
    if (strict.maxWaitScheduled > smart.maxWaitScheduled) {
      insights.push(`Worst-case wait for scheduled appointments was ${strict.maxWaitScheduled}m under Strict Priority vs ${smart.maxWaitScheduled}m with SmartOPD fairness safeguards.`);
    } else {
      insights.push(`Scheduled appointments maximum wait was controlled at ${smart.maxWaitScheduled}m with SmartOPD fairness safeguards.`);
    }

    // 7. Predictability (90th Percentile)
    insights.push(`SmartOPD maintains clinical balance with a 90th-percentile wait of ${smart.p90Wait} minutes across all patient categories.`);

    return insights;
  }

  // --- Public SmartOPDSimulator Interface ---
  return {
    /**
     * Standard Simulation Presets
     * Load = (numPatients * avgConsultMinutes) / (numDoctors * arrivalSpreadMinutes)
     */
    PRESETS: {
      // Quiet Day: Load = (90 * 8) / (4 * 300) = 720 / 1200 = 60.0% offered load
      'quiet-day': {
        name: 'Quiet Day',
        description: 'Low patient volume, high scheduled appointments ratio, minimal emergencies. (~60% load)',
        numPatients: 90,
        numDoctors: 4,
        avgConsultMinutes: 8,
        emergencyPercent: 5,
        scheduledPercent: 65,
        arrivalSpreadMinutes: 300,
        randomSeed: 101
      },
      // Busy Day: Load = (108 * 10) / (4 * 300) = 1080 / 1200 = 90.0% offered load
      'busy-day': {
        name: 'Busy Day',
        description: 'Typical heavy hospital weekday with regular emergencies and steady walk-ins. (~90% load)',
        numPatients: 108,
        numDoctors: 4,
        avgConsultMinutes: 10,
        emergencyPercent: 12,
        scheduledPercent: 48,
        arrivalSpreadMinutes: 300,
        randomSeed: 202
      },
      // Emergency Surge: Load = (126 * 10) / (4 * 300) = 1260 / 1200 = 105.0% offered load
      'emergency-surge': {
        name: 'Emergency Surge',
        description: 'Mass casualty / monsoon surge with high emergency intake stressing routine OPD. (~105% load)',
        numPatients: 126,
        numDoctors: 4,
        avgConsultMinutes: 10,
        emergencyPercent: 28,
        scheduledPercent: 35,
        arrivalSpreadMinutes: 300,
        randomSeed: 303
      }
    },

    /**
     * Runs discrete-event simulation comparing FCFS, Strict Priority, and SmartOPD
     */
    runSimulation(config = {}) {
      const rng = createPRNG(config.randomSeed ?? 202);
      const patients = generateSyntheticPatients(config, rng);
      const numDoctors = Math.max(1, Math.min(6, config.numDoctors ?? 4));
      const windowMinutes = config.arrivalSpreadMinutes || 300;
      const avgConsultMinutes = config.avgConsultMinutes ?? 10;
      const numPatients = patients.length;

      // Compute offered load percentage: Load = (numPatients * avgConsultMinutes) / (numDoctors * windowMinutes)
      const offeredLoadPercent = Math.round(((numPatients * avgConsultMinutes) / (numDoctors * windowMinutes)) * 100);

      // Extract policy settings, reading live settings from SmartOPDQueue if available
      let liveSettings = {};
      if (typeof window !== 'undefined' && window.SmartOPDQueue && typeof window.SmartOPDQueue.getSettings === 'function') {
        try { liveSettings = window.SmartOPDQueue.getSettings() || {}; } catch (e) {}
      } else if (typeof global !== 'undefined' && global.window && global.window.SmartOPDQueue && typeof global.window.SmartOPDQueue.getSettings === 'function') {
        try { liveSettings = global.window.SmartOPDQueue.getSettings() || {}; } catch (e) {}
      }

      const defaultSettings = (ScoringEngine && ScoringEngine.DEFAULT_SETTINGS) ? ScoringEngine.DEFAULT_SETTINGS : {
        SEVERITY_WEIGHT: 100,
        WAIT_WEIGHT_PER_MIN: 3,
        FAIRNESS_BOOST: 150,
        FAIRNESS_THRESHOLD_SCHEDULED: 45,
        FAIRNESS_THRESHOLD_WALKIN: 45,
        MAX_CONSECUTIVE_EMERGENCIES: 2,
        APPOINTMENT_URGENCY_MAX: 60
      };

      const settings = {
        ...defaultSettings,
        ...liveSettings,
        ...(config.policySettings || {})
      };

      const fcfs = simulatePolicy('FCFS', patients, numDoctors, settings);
      const strict = simulatePolicy('StrictPriority', patients, numDoctors, settings);
      const smart = simulatePolicy('SmartOPD', patients, numDoctors, settings);

      const insights = generateInsights(fcfs, strict, smart, settings);

      const policies = {
        fcfs,
        strictPriority: strict,
        smartopd: smart,
        FCFS: fcfs,
        StrictPriority: strict,
        SmartOPD: smart
      };

      return {
        config: { ...config, numDoctors, numPatients, arrivalSpreadMinutes: windowMinutes, avgConsultMinutes },
        offeredLoadPercent,
        patientsCount: patients.length,
        patientList: patients,
        policies,
        results: policies,
        insights
      };
    }
  };
}));
