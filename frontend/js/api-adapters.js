/**
 * SmartOPD – REST API Adapters
 * File: js/api-adapters.js
 * 
 * Bridges response shapes between Spring Boot REST API and frontend pages:
 * - Pure data transformations, zero DOM manipulation
 * - Usable in both browser (window.SmartOPDAdapters) and Node.js (module.exports)
 * - Details documented in backend/API.md contract notes
 */

const SmartOPDAdapters = {
  /**
   * Adapts GET /api/public/board
   * Maps backend PublicBoardDoctorDto list to display-board expectations.
   * Shape difference: Backend uses estimatedStart and isEmergency boolean without severity.
   */
  adaptPublicBoard(apiBoard) {
    if (!Array.isArray(apiBoard)) return [];
    return apiBoard.map(doc => ({
      doctorId: doc.doctorId || '',
      doctorName: doc.doctorName || '',
      department: doc.department || '',
      room: doc.room || '',
      status: doc.status || 'Available',
      nowServingToken: doc.nowServingToken || null,
      nextTokens: Array.isArray(doc.nextTokens)
        ? doc.nextTokens.map(t => ({
            token: t.token || '',
            category: t.category || 'Walk-in',
            isEmergency: Boolean(t.isEmergency),
            estimatedStart: t.estimatedStart || ''
          }))
        : []
    }));
  },

  /**
   * Adapts GET /api/public/tracker/{token}
   * Maps backend PublicTrackerResponse to patient-tracker.html expectations.
   * Shape difference: Backend embeds timeline directly, returns privacy-safe firstName only,
   * and omits severity level.
   */
  adaptPublicTracker(apiTracker) {
    if (!apiTracker) return null;
    return {
      token: apiTracker.token || '',
      firstName: apiTracker.firstName || 'Patient',
      doctor: apiTracker.doctor || '',
      department: apiTracker.department || '',
      room: apiTracker.room || '',
      category: apiTracker.category || 'Walk-in',
      status: apiTracker.status || 'Waiting',
      position: typeof apiTracker.position === 'number' ? apiTracker.position : 1,
      patientsAhead: typeof apiTracker.patientsAhead === 'number' ? apiTracker.patientsAhead : 0,
      estimatedStart: apiTracker.estimatedStart || 'Calculating...',
      estimatedWaitMinutes: typeof apiTracker.estimatedWaitMinutes === 'number' ? apiTracker.estimatedWaitMinutes : 0,
      explanation: apiTracker.explanation || '',
      fairnessBoost: Boolean(apiTracker.fairnessBoost),
      isNext: Boolean(apiTracker.isNext),
      isEmergencyDelayed: Boolean(apiTracker.isEmergencyDelayed),
      timeline: Array.isArray(apiTracker.timeline)
        ? apiTracker.timeline.map(ev => ({
            timestamp: ev.timestamp || '',
            type: ev.type || 'EVENT',
            reason: ev.reason || ''
          }))
        : []
    };
  },

  /**
   * Adapts a single QueuePatientDto item
   */
  adaptQueuePatient(p) {
    if (!p) return null;
    return {
      id: p.id || '',
      tokenNumber: p.tokenNumber || p.token || '',
      token: p.token || p.tokenNumber || '',
      name: p.name || '',
      age: typeof p.age === 'number' ? p.age : 0,
      gender: p.gender || 'Other',
      phone: p.phone || '',
      category: p.category || 'Walk-in',
      severity: typeof p.severity === 'number' ? p.severity : 1,
      position: typeof p.position === 'number' ? p.position : 1,
      score: typeof p.score === 'number' ? p.score : 0,
      estimatedStartTime: p.estimatedStartTime || p.estimatedStart || '',
      estimatedTimeStr: p.estimatedStartTime || p.estimatedStart || '',
      estimatedWaitMinutes: typeof p.estimatedWaitMinutes === 'number' ? p.estimatedWaitMinutes : (typeof p.waitMinutes === 'number' ? p.waitMinutes : 0),
      explanation: p.explanation || '',
      fairnessBoost: Boolean(p.fairnessBoost || p.fairnessBoosted),
      appointmentTime: p.appointmentTime || null,
      chiefComplaint: p.chiefComplaint || '',
      status: p.status || 'Waiting',
      skipped: Boolean(p.skipped)
    };
  },

  /**
   * Adapts GET /api/queues (List<DoctorQueueDto>)
   * Maps backend DoctorQueueDto to the structure expected by reception-dashboard
   * and queue-manager (formerly SmartOPDQueue.getDoctorsWithQueueSummary()).
   * Shape differences:
   * - Backend uses waitingList instead of waitingPatients
   * - Backend uses nowServing instead of activePatient
   * - Provides activeToken and activePatientName helpers
   */
  adaptQueues(apiQueues) {
    if (!Array.isArray(apiQueues)) return [];
    return apiQueues.map(doc => {
      const waiting = Array.isArray(doc.waitingList)
        ? doc.waitingList.map(p => this.adaptQueuePatient(p))
        : [];
      const active = doc.nowServing ? {
        tokenNumber: doc.nowServing.tokenNumber || doc.nowServing.token || '',
        name: doc.nowServing.name || '',
        age: doc.nowServing.age || 0,
        gender: doc.nowServing.gender || 'Other',
        phone: doc.nowServing.phone || '',
        category: doc.nowServing.category || 'Walk-in',
        severity: doc.nowServing.severity || 1,
        doctorId: doc.nowServing.doctorId || doc.doctorId,
        doctorName: doc.nowServing.doctorName || doc.doctorName,
        department: doc.nowServing.department || doc.department,
        room: doc.nowServing.room || doc.room,
        status: doc.nowServing.status || 'In Consultation',
        elapsedMinutes: typeof doc.nowServing.elapsedMinutes === 'number' ? doc.nowServing.elapsedMinutes : 0,
        chiefComplaint: doc.nowServing.chiefComplaint || ''
      } : null;

      let nextFree = 'Available Now';
      if (doc.status === 'Off Duty') {
        nextFree = 'Off Duty';
      } else if (waiting.length > 0 && waiting[0].estimatedStartTime) {
        nextFree = waiting[0].estimatedStartTime;
      }

      return {
        id: doc.doctorId || '',
        name: doc.doctorName || '',
        department: doc.department || '',
        room: doc.room || '',
        status: doc.status || 'Available',
        avgConsultMinutes: typeof doc.avgConsultMinutes === 'number' ? doc.avgConsultMinutes : 15,
        waitingCount: typeof doc.waitingCount === 'number' ? doc.waitingCount : waiting.length,
        emergencyCount: typeof doc.emergencyCount === 'number' ? doc.emergencyCount : 0,
        activeToken: active ? active.tokenNumber : null,
        activePatientName: active ? active.name : null,
        activePatient: active,
        nextFreeTimeStr: nextFree,
        queue: waiting,
        fullQueue: waiting
      };
    });
  },

  /**
   * Adapts GET /api/doctors/{id}/queue
   */
  adaptDoctorQueue(apiDocQueue) {
    if (!apiDocQueue) return null;
    const adaptedList = this.adaptQueues([apiDocQueue]);
    return adaptedList.length > 0 ? adaptedList[0] : null;
  },

  /**
   * Adapts GET /api/analytics
   * Ensures all metrics used by reception-dashboard charts and indicators are present.
   */
  adaptAnalytics(apiAnalytics) {
    if (!apiAnalytics) return {};
    return {
      totalPatients: apiAnalytics.totalPatients || 0,
      waitingCount: apiAnalytics.waitingCount || 0,
      inConsultationCount: apiAnalytics.inConsultationCount || 0,
      completedCount: apiAnalytics.completedCount || 0,
      noShowCount: apiAnalytics.noShowCount || 0,
      averageWaitOverall: apiAnalytics.averageWaitOverall || 0,
      totalsByCategory: apiAnalytics.totalsByCategory || apiAnalytics.categoryBreakdown || {
        Emergency: 0,
        Scheduled: 0,
        'Walk-in': 0
      },
      severityDistribution: apiAnalytics.severityDistribution || {
        1: 0, 2: 0, 3: 0, 4: 0, 5: 0
      },
      patientsPerHour: Array.isArray(apiAnalytics.patientsPerHour) ? apiAnalytics.patientsPerHour : [],
      averageWaitPerDoctor: Array.isArray(apiAnalytics.averageWaitPerDoctor) ? apiAnalytics.averageWaitPerDoctor : [],
      doctorUtilisation: Array.isArray(apiAnalytics.doctorUtilisation) ? apiAnalytics.doctorUtilisation : [],
      overallDoctorUtilisation: apiAnalytics.overallDoctorUtilisation || 0,
      longestWait: apiAnalytics.longestWait || { minutes: 0, tokenNumber: 'None' },
      fairnessBoostCount: apiAnalytics.fairnessBoostCount || apiAnalytics.fairnessBoostedCount || 0,
      emergencyOverrideCount: apiAnalytics.emergencyOverrideCount || 0
    };
  },

  /**
   * Adapts GET /api/settings
   */
  adaptSettings(apiSettings) {
    if (!apiSettings) return null;
    return {
      severityWeight: typeof apiSettings.severityWeight === 'number' ? apiSettings.severityWeight : 100,
      waitWeightPerMin: typeof apiSettings.waitWeightPerMin === 'number' ? apiSettings.waitWeightPerMin : 3,
      fairnessBoost: typeof apiSettings.fairnessBoost === 'number' ? apiSettings.fairnessBoost : 150,
      fairnessThresholdScheduled: typeof apiSettings.fairnessThresholdScheduled === 'number' ? apiSettings.fairnessThresholdScheduled : 45,
      fairnessThresholdWalkin: typeof apiSettings.fairnessThresholdWalkin === 'number' ? apiSettings.fairnessThresholdWalkin : 45,
      maxConsecutiveEmergencies: typeof apiSettings.maxConsecutiveEmergencies === 'number' ? apiSettings.maxConsecutiveEmergencies : 2,
      emergencyBufferSlotsDefault: typeof apiSettings.emergencyBufferSlotsDefault === 'number' ? apiSettings.emergencyBufferSlotsDefault : 2
    };
  },

  /**
   * Adapts GET /api/events
   * Maps QueueEventDto list for reception activity feed and queue manager explanation log.
   */
  adaptEvents(apiEvents) {
    if (!Array.isArray(apiEvents)) return [];
    return apiEvents.map(ev => ({
      id: ev.id,
      timestamp: ev.timestamp || '',
      tokenNumber: ev.tokenNumber || '',
      doctorId: ev.doctorId || '',
      type: ev.type || 'INFO',
      reason: ev.reason || '',
      patientName: ev.tokenNumber || ''
    }));
  },

  /**
   * Adapts GET /api/patients/{token} or POST /api/patients response
   * Maps PatientSlipDto to token-slip.html and register-patient.html expectations.
   * Shape differences:
   * - registeredAt mapped to registrationTime
   * - estimatedStart / estimatedStartTime standardized
   */
  adaptPatientSlip(apiPatient) {
    if (!apiPatient) return null;
    return {
      tokenNumber: apiPatient.tokenNumber || apiPatient.token || '',
      name: apiPatient.name || '',
      age: typeof apiPatient.age === 'number' ? apiPatient.age : 0,
      gender: apiPatient.gender || 'Other',
      phone: apiPatient.phone || '',
      category: apiPatient.category || 'Walk-in',
      severity: typeof apiPatient.severity === 'number' ? apiPatient.severity : 1,
      doctorId: apiPatient.doctorId || '',
      doctorName: apiPatient.doctorName || '',
      department: apiPatient.department || '',
      room: apiPatient.room || '',
      appointmentTime: apiPatient.appointmentTime || null,
      chiefComplaint: apiPatient.chiefComplaint || '',
      status: apiPatient.status || 'Waiting',
      position: typeof apiPatient.position === 'number' ? apiPatient.position : 1,
      estimatedStartTime: apiPatient.estimatedStartTime || apiPatient.estimatedStart || '',
      estimatedWaitMinutes: typeof apiPatient.estimatedWaitMinutes === 'number' ? apiPatient.estimatedWaitMinutes : 0,
      explanation: apiPatient.explanation || '',
      registrationTime: apiPatient.registeredAt || apiPatient.registrationTime || new Date().toISOString(),
      registeredAt: apiPatient.registeredAt || null
    };
  },

  /**
   * Adapts List<PatientSlipDto> from GET /api/patients
   */
  adaptAllPatients(apiPatients) {
    if (!Array.isArray(apiPatients)) return [];
    return apiPatients.map(p => this.adaptPatientSlip(p));
  },

  /**
   * Adapts POST /api/patients/preview response
   * Maps preview response for register-patient.html live preview box.
   */
  adaptPatientPreview(apiPreview) {
    if (!apiPreview) return null;
    return {
      tokenNumber: apiPreview.predictedToken || apiPreview.tokenNumber || apiPreview.token || 'PREVIEW-###',
      position: typeof apiPreview.position === 'number' ? apiPreview.position : 1,
      estimatedWaitMinutes: typeof apiPreview.estimatedWaitMinutes === 'number' ? apiPreview.estimatedWaitMinutes : 0,
      estimatedStartTime: apiPreview.estimatedStart || apiPreview.estimatedStartTime || '',
      explanation: apiPreview.explanation || '',
      doctorId: apiPreview.doctorId || '',
      doctorName: apiPreview.doctorName || '',
      score: typeof apiPreview.score === 'number' ? apiPreview.score : 0,
      isEmergencyOverride: Boolean(apiPreview.isEmergencyOverride)
    };
  },

  /**
   * Adapts GET /api/clock and POST /api/clock/advance response
   */
  adaptClock(apiClock) {
    if (!apiClock) return { currentTime: new Date(), offsetMinutes: 0, timeString: '' };
    const dateObj = apiClock.currentTime ? new Date(apiClock.currentTime) : new Date();
    return {
      currentTime: dateObj,
      offsetMinutes: typeof apiClock.offsetMinutes === 'number' ? apiClock.offsetMinutes : 0,
      timeString: dateObj.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true })
    };
  },

  /**
   * Adapts GET /api/simulation/presets response
   */
  adaptSimulationPresets(apiPresets) {
    if (!Array.isArray(apiPresets)) return [];
    return apiPresets.map(p => ({
      id: p.id || '',
      name: p.name || '',
      description: p.description || '',
      numPatients: p.numPatients || 108,
      numDoctors: p.numDoctors || 4,
      avgConsultMinutes: p.avgConsultMinutes || 10,
      emergencyPercent: p.emergencyPercent || 12,
      scheduledPercent: p.scheduledPercent || 48,
      arrivalSpreadMinutes: p.arrivalSpreadMinutes || 300,
      randomSeed: p.randomSeed || 202,
      offeredLoadPercent: p.offeredLoadPercent || 90
    }));
  },

  /**
   * Adapts POST /api/simulation/run response
   * Bridges Spring Boot SimulationResult to frontend analytics charts and views.
   */
  adaptSimulationResult(apiResult) {
    if (!apiResult) return null;
    const policies = apiResult.policies || apiResult.results || {};

    const adaptPolicy = (pol, defaultName) => {
      if (!pol) return null;
      const metrics = pol.metrics || {
        averageWaitOverall: pol.averageWaitOverall || 0,
        avgWaitOverall: pol.avgWaitOverall || 0,
        averageWaitEmergency: pol.averageWaitEmergency || 0,
        avgWaitEmergency: pol.avgWaitEmergency || 0,
        averageWaitHighSeverity: pol.averageWaitHighSeverity || 0,
        avgWaitHighSeverity: pol.avgWaitHighSeverity || 0,
        averageWaitScheduled: pol.averageWaitScheduled || 0,
        avgWaitScheduled: pol.avgWaitScheduled || 0,
        averageWaitWalkin: pol.averageWaitWalkin || 0,
        avgWaitWalkin: pol.avgWaitWalkin || 0,
        p90Wait: pol.p90Wait || 0,
        maxScheduledWait: pol.maxScheduledWait || 0,
        maxWaitScheduled: pol.maxWaitScheduled || 0,
        starvationCount: pol.starvationCount || 0,
        doctorIdlePercent: pol.doctorIdlePercent || 0
      };
      const waits = pol.patientWaits || pol.records || [];
      return {
        policyName: pol.policyName || defaultName,
        totalServed: pol.totalServed || waits.length,
        totalSimTime: pol.totalSimTime || 0,
        metrics,
        ...metrics,
        patientWaits: waits,
        records: waits
      };
    };

    const fcfs = adaptPolicy(policies.fcfs || policies.FCFS, 'FCFS');
    const strictPriority = adaptPolicy(policies.strictPriority || policies.StrictPriority, 'StrictPriority');
    const smartopd = adaptPolicy(policies.smartopd || policies.SmartOPD, 'SmartOPD');

    const adaptedPolicies = {
      fcfs,
      strictPriority,
      smartopd,
      FCFS: fcfs,
      StrictPriority: strictPriority,
      SmartOPD: smartopd
    };

    return {
      config: apiResult.config || {},
      offeredLoadPercent: typeof apiResult.offeredLoadPercent === 'number' ? apiResult.offeredLoadPercent : 90,
      patientsCount: typeof apiResult.patientsCount === 'number' ? apiResult.patientsCount : 108,
      patientList: apiResult.patientList || [],
      policies: adaptedPolicies,
      results: adaptedPolicies,
      insights: Array.isArray(apiResult.insights) ? apiResult.insights : []
    };
  }
};

// Expose globally in browser and Node.js
if (typeof window !== 'undefined') {
  window.SmartOPDAdapters = SmartOPDAdapters;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = SmartOPDAdapters;
}
