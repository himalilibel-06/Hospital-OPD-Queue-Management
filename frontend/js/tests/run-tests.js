/**
 * SmartOPD – Automated Queue Engine Test Suite
 * File: js/tests/run-tests.js
 * 
 * Verifies:
 * 1. Emergency override ordering (Severity 5 -> Position #1)
 * 2. Fairness boost after advanceClock(+35) for a Scheduled patient
 * 3. Emergency cap rule (MAX_CONSECUTIVE_EMERGENCIES = 2)
 * 4. No-show frees the slot and shifts queue
 * 5. Deterministic tie-breaking by registration time (FIFO)
 */

const fs = require('fs');
const path = require('path');

// Read frontend scripts
const mockDataCode = fs.readFileSync(path.join(__dirname, '../mock-data.js'), 'utf-8');
const scoringCode = fs.readFileSync(path.join(__dirname, '../scoring.js'), 'utf-8');
const queueMockCode = fs.readFileSync(path.join(__dirname, '../queue-mock.js'), 'utf-8');
const simulatorCode = fs.readFileSync(path.join(__dirname, '../simulator.js'), 'utf-8');

// Mock browser globals for Node.js execution
global.window = {};
global.localStorage = {
  store: {},
  getItem(k) { return this.store[k] || null; },
  setItem(k, v) { this.store[k] = String(v); },
  removeItem(k) { delete this.store[k]; },
  clear() { this.store = {}; }
};

// Evaluate code in context
eval(mockDataCode);
eval(scoringCode);
eval(queueMockCode);
eval(simulatorCode);

const q = window.SmartOPDQueue;
const Scoring = window.SmartOPDScoring || require('../scoring.js');
const Sim = window.SmartOPDSimulator || require('../simulator.js');
let passedCount = 0;
let totalCount = 0;

function assert(description, condition, details = '') {
  totalCount++;
  if (condition) {
    passedCount++;
    console.log(`[PASS] ${description}`);
  } else {
    console.error(`[FAIL] ${description} -> ${details}`);
  }
}

console.log('========================================================');
console.log('   SmartOPD Queue Engine – Automated Verification Suite  ');
console.log('========================================================\n');

// Reset to clean mock state before starting tests
q.resetState();
q.resetClock();

// --------------------------------------------------------------------------
// TEST 1: Emergency Override Ordering
// --------------------------------------------------------------------------
console.log('--- Test 1: Emergency Override Ordering ---');
// Doctor: Dr. Suresh Verma (DOC-105, ENT)
// First, register a routine Walk-in patient (Sev 2)
const walkin = q.registerPatient({
  name: 'Alok Nath',
  age: 35,
  gender: 'Male',
  phone: '9876500001',
  category: 'Walk-in',
  severity: 2,
  doctorId: 'DOC-105'
});

// Second, register a Level 5 Emergency patient
const emerg = q.registerPatient({
  name: 'Sunita Emergency',
  age: 50,
  gender: 'Female',
  phone: '9876500002',
  category: 'Emergency',
  severity: 5,
  doctorId: 'DOC-105',
  chiefComplaint: 'Acute respiratory distress'
});

const entQueue = q.getDoctorQueue('DOC-105');
assert(
  'Level 5 Emergency is placed at Position #1 in doctor queue over existing walk-in',
  entQueue[0].id === emerg.id && entQueue[0].score >= 10000,
  `Expected ${emerg.id} at pos 0 with score >= 10000, got ${entQueue[0].id} with score ${entQueue[0].score}`
);

assert(
  'Emergency patient explanation states "Severity 5 emergency override"',
  emerg.explanation.includes('Severity 5 emergency override'),
  `Got explanation: "${emerg.explanation}"`
);

// --------------------------------------------------------------------------
// TEST 2: Fairness Boost after advanceClock(+50) for a Scheduled Patient
// --------------------------------------------------------------------------
console.log('\n--- Test 2: Fairness Boost after advanceClock(+50) ---');
// Doctor: Dr. Rajesh Mehta (DOC-101)
const scheduledPatient = q.registerPatient({
  name: 'Tarun Verma',
  age: 40,
  gender: 'Male',
  phone: '9876500003',
  category: 'Scheduled',
  severity: 1,
  doctorId: 'DOC-101',
  appointmentTime: '11:00'
});

// Check initially: should not be fairness-boosted
const initiallyBoosted = q.isFairnessBoosted(scheduledPatient, q.getNow());
assert('Scheduled patient not fairness boosted at 0 min wait', !initiallyBoosted);

// Advance clock by 50 minutes (exceeds 45 min scheduled threshold)
q.advanceClock(50);

const nowAfter50 = q.getNow();
const boostedAfter50 = q.isFairnessBoosted(scheduledPatient, nowAfter50);
const scoreAfter50 = q.calculatePriorityScore(scheduledPatient, nowAfter50);
const events = q.getEvents({ tokenNumber: scheduledPatient.tokenNumber });
const fairnessEventLogged = events.some(e => e.type === 'FAIRNESS_BOOST');

assert(
  'Scheduled patient achieves Fairness Boost after 50 minutes wait (threshold: 45m)',
  boostedAfter50,
  `isFairnessBoosted returned false after 50m wait`
);

assert(
  'Fairness boost (+150 pts) reflects in priority score calculation',
  scoreAfter50 >= (1 * 100) + (50 * 3) + 150,
  `Expected score >= 400, got ${scoreAfter50}`
);

assert(
  'FAIRNESS_BOOST event logged into audit eventLog',
  fairnessEventLogged,
  `No FAIRNESS_BOOST event found in logs for ${scheduledPatient.tokenNumber}`
);

// --------------------------------------------------------------------------
// TEST 3: Emergency Cap Rule (MAX_CONSECUTIVE_EMERGENCIES = 2)
// --------------------------------------------------------------------------
console.log('\n--- Test 3: Emergency Cap Rule (MAX_CONSECUTIVE_EMERGENCIES = 2) ---');
// Use Dr. Priya Nair (DOC-103)
// Clear active consultation if any
q.resetState();
q.resetClock();

// Ensure active consultation is completed
try { q.completeConsultation('DOC-103'); } catch (e) {}

const curState = q.getState();
const doc103 = curState.doctors.find(d => d.id === 'DOC-103');
doc103.consecutiveEmergencies = 2;

// Add a fairness-boosted Routine Walk-in patient (registered 50 mins ago)
const starvingPatient = {
  id: 'PAT-STARVE-1',
  tokenNumber: 'PED-W-998',
  name: 'Patient Starving',
  age: 8,
  gender: 'Female',
  phone: '9876599998',
  category: 'Walk-in',
  severity: 1,
  department: 'Pediatrics',
  doctorId: 'DOC-103',
  doctorName: 'Dr. Priya Nair',
  room: 'OPD Room 104',
  appointmentTime: null,
  registrationTime: new Date(Date.now() - 46 * 60000).toISOString(), // 46m wait -> exceeded 45m walk-in threshold, score 388 < 400 (Sev 4)
  status: 'Waiting'
};
curState.patients.push(starvingPatient);

// Add an urgent Level 4 Emergency
const urgentEmerg = {
  id: 'PAT-EMERG-4',
  tokenNumber: 'PED-E-999',
  name: 'Urgent Sev 4',
  age: 6,
  gender: 'Male',
  phone: '9876599999',
  category: 'Emergency',
  severity: 4,
  department: 'Pediatrics',
  doctorId: 'DOC-103',
  doctorName: 'Dr. Priya Nair',
  room: 'OPD Room 104',
  appointmentTime: null,
  registrationTime: new Date().toISOString(),
  status: 'Waiting'
};
curState.patients.push(urgentEmerg);
q.saveState(curState);

// Now call next! Because consecutiveEmergencies >= 2, and urgentEmerg is Sev 4 (not Sev 5),
// the fairness cap rule must intervene and call the starving patient!
const calledPatient = q.callNext('DOC-103');

assert(
  'Fairness cap serves starving patient after 2 consecutive emergencies when top patient is Sev 4',
  calledPatient.id === starvingPatient.id,
  `Expected ${starvingPatient.id} called, got ${calledPatient.id} (${calledPatient.name})`
);

const recentCallLogs = q.getEvents({ doctorId: 'DOC-103', limit: 5 });
const fairnessCapLog = recentCallLogs.find(e => (e.reason || '').includes('Fairness cap'));
assert(
  'Fairness cap action is logged with plain-English explanation',
  !!fairnessCapLog,
  `Expected log with "Fairness cap", found: ${JSON.stringify(recentCallLogs.map(e => e.reason))}`
);

// --------------------------------------------------------------------------
// TEST 4: No-Show Frees the Slot & Shifts Remaining Queue
// --------------------------------------------------------------------------
console.log('\n--- Test 4: No-Show Frees the Slot & Shifts Remaining Queue ---');
// Use Dr. Suresh Verma (DOC-105)
const preQueue = q.getDoctorQueue('DOC-105');
if (preQueue.length >= 2) {
  const topToken = preQueue[0].tokenNumber;
  const secondToken = preQueue[1].tokenNumber;

  q.markNoShow(topToken);

  const postQueue = q.getDoctorQueue('DOC-105');
  assert(
    'Top patient marked as No-Show is removed from waiting queue',
    !postQueue.some(p => p.tokenNumber === topToken),
    `Found ${topToken} still in waiting queue`
  );

  assert(
    'Second patient shifts up to Position #1 after No-Show',
    postQueue[0] && postQueue[0].tokenNumber === secondToken,
    `Expected ${secondToken} at pos 0, got ${postQueue[0] ? postQueue[0].tokenNumber : 'empty'}`
  );

  const noShowEvents = q.getEvents({ tokenNumber: topToken });
  assert(
    'NO_SHOW event recorded in audit trail',
    noShowEvents.some(e => e.type === 'NO_SHOW'),
    `No NO_SHOW event found for ${topToken}`
  );
} else {
  assert('Test 4 setup', false, 'Not enough patients in queue for DOC-105');
}

// --------------------------------------------------------------------------
// TEST 5: Tie-Break Deterministically by Registration Time (FIFO)
// --------------------------------------------------------------------------
console.log('\n--- Test 5: Tie-Break Deterministically by Registration Time (FIFO) ---');
// Reset state
q.resetState();
q.resetClock();

// Register two identical walk-in patients (both Sev 2) with 2 mins difference
const earlierRegTime = new Date(Date.now() - 10 * 60000).toISOString();
const laterRegTime = new Date(Date.now() - 5 * 60000).toISOString();

const stateForTie = q.getState();
const doc104QueueBefore = stateForTie.patients.filter(p => p.doctorId === 'DOC-104' && p.status === 'Waiting');
// Clean out waiting for DOC-104 to isolate test
stateForTie.patients = stateForTie.patients.filter(p => !(p.doctorId === 'DOC-104' && p.status === 'Waiting'));

const patientA = {
  id: 'PAT-TIE-A',
  tokenNumber: 'ORTH-W-801',
  name: 'Earlier Patient A',
  age: 30,
  gender: 'Male',
  phone: '9876511111',
  category: 'Walk-in',
  severity: 2,
  department: 'Orthopedics',
  doctorId: 'DOC-104',
  doctorName: 'Dr. Vikram Patel',
  room: 'OPD Room 112',
  appointmentTime: null,
  registrationTime: earlierRegTime,
  status: 'Waiting'
};

const patientB = {
  id: 'PAT-TIE-B',
  tokenNumber: 'ORTH-W-802',
  name: 'Later Patient B',
  age: 32,
  gender: 'Female',
  phone: '9876522222',
  category: 'Walk-in',
  severity: 2,
  department: 'Orthopedics',
  doctorId: 'DOC-104',
  doctorName: 'Dr. Vikram Patel',
  room: 'OPD Room 112',
  appointmentTime: null,
  registrationTime: laterRegTime,
  status: 'Waiting'
};

stateForTie.patients.push(patientB); // Add in reverse order
stateForTie.patients.push(patientA);
q.saveState(stateForTie);

const tieQueue = q.getDoctorQueue('DOC-104');
assert(
  'Patients with identical scores are ordered strictly by earlier registration time (FIFO)',
  tieQueue[0].id === patientA.id && tieQueue[1].id === patientB.id,
  `Expected [${patientA.id}, ${patientB.id}], got [${tieQueue[0]?.id}, ${tieQueue[1]?.id}]`
);

// --------------------------------------------------------------------------
// TEST 6: getPatientByToken (Case-Insensitive & Space Trimming)
// --------------------------------------------------------------------------
console.log('\n--- Test 6: getPatientByToken Helper ---');
q.resetState();
q.resetClock();

const foundWithSpaces = q.getPatientByToken('   card-w-005   ');
assert(
  'getPatientByToken finds token with lower-case and whitespace trimming',
  foundWithSpaces !== null && foundWithSpaces.tokenNumber === 'CARD-W-005',
  `Expected CARD-W-005, got ${foundWithSpaces?.tokenNumber}`
);

const nonExistent = q.getPatientByToken('UNKNOWN-999');
assert(
  'getPatientByToken returns null for non-existent token',
  nonExistent === null,
  `Expected null, got ${JSON.stringify(nonExistent)}`
);

// --------------------------------------------------------------------------
// TEST 7: getPublicBoardData Zero Patient PII Leakage
// --------------------------------------------------------------------------
console.log('\n--- Test 7: getPublicBoardData Privacy Verification ---');
const boardData = q.getPublicBoardData();

assert(
  'getPublicBoardData returns board list for all registered doctors',
  Array.isArray(boardData) && boardData.length >= 5,
  `Expected array of >= 5 doctors, got ${boardData?.length}`
);

let piiFound = false;
let piiDetails = '';

boardData.forEach(panel => {
  // Check panel root properties
  if (panel.patientName || panel.phone || panel.age || panel.chiefComplaint || panel.diagnosis) {
    piiFound = true;
    piiDetails = `Doctor panel leaked patient field in ${JSON.stringify(panel)}`;
  }
  // Check waiting tokens
  panel.nextTokens.forEach(t => {
    if (t.name || t.patientName || t.phone || t.age || t.chiefComplaint || t.diagnosis) {
      piiFound = true;
      piiDetails = `Waiting token leaked patient PII: ${JSON.stringify(t)}`;
    }
    // Must only have privacy-safe keys
    const allowedKeys = ['token', 'category', 'severity', 'isEmergency', 'estimatedStart'];
    const extraKeys = Object.keys(t).filter(k => !allowedKeys.includes(k));
    if (extraKeys.length > 0) {
      piiFound = true;
      piiDetails = `Waiting token contains unexpected fields: ${extraKeys.join(', ')}`;
    }
  });
});

assert(
  'getPublicBoardData contains ZERO patient names, phone numbers, ages or clinical complaints',
  !piiFound,
  piiDetails
);

// --------------------------------------------------------------------------
// TEST 8: getPublicQueueSummary Safe Fields Only
// --------------------------------------------------------------------------
console.log('\n--- Test 8: getPublicQueueSummary Privacy Verification ---');
const summary = q.getPublicQueueSummary('CARD-W-005');

assert(
  'getPublicQueueSummary exposes only privacy-safe fields',
  summary !== null &&
  summary.token === 'CARD-W-005' &&
  typeof summary.firstName === 'string' &&
  summary.doctor !== undefined &&
  summary.department !== undefined &&
  summary.room !== undefined &&
  summary.category !== undefined &&
  summary.status !== undefined &&
  typeof summary.position === 'number' &&
  typeof summary.patientsAhead === 'number' &&
  typeof summary.estimatedWaitMinutes === 'number' &&
  typeof summary.explanation === 'string',
  `Summary fields: ${JSON.stringify(summary)}`
);

assert(
  'getPublicQueueSummary does NOT expose full name, phone number, age or chief complaint',
  summary &&
  summary.name === undefined &&
  summary.phone === undefined &&
  summary.age === undefined &&
  summary.chiefComplaint === undefined,
  `Detected unexpected PII fields in summary: ${JSON.stringify(summary)}`
);

// Verify firstName is first name only (e.g. "Priya" not "Priya Sharma")
const fullPatient = q.getPatientByToken('CARD-W-005');
const expectedFirstName = fullPatient ? fullPatient.name.split(' ')[0] : 'Priya';
assert(
  'getPublicQueueSummary greeting uses first name only',
  summary && summary.firstName === expectedFirstName && !summary.firstName.includes(' '),
  `Expected "${expectedFirstName}", got "${summary?.firstName}"`
);

// --------------------------------------------------------------------------
// TEST 9: getTokenTimeline Chronological Order (Oldest First)
// --------------------------------------------------------------------------
console.log('\n--- Test 9: getTokenTimeline Order Verification ---');
const testDocId = 'DOC-103'; // Dr. Priya Nair (Pediatrics)
q.completeConsultation(testDocId); // Complete active consultation first
q.advanceClock(40); // Advance clock to trigger waiting aging and fairness boosts
const testPatient = q.callNext(testDocId); // Call next waiting patient

// Check timeline for the called patient
const timeline = q.getTokenTimeline(testPatient.tokenNumber);

assert(
  'getTokenTimeline returns array of events for the patient',
  Array.isArray(timeline) && timeline.length >= 1,
  `Expected timeline events, got ${timeline?.length}`
);

let isChronological = true;
let orderDetails = '';
for (let i = 0; i < timeline.length - 1; i++) {
  const t1 = new Date(timeline[i].timestamp).getTime();
  const t2 = new Date(timeline[i + 1].timestamp).getTime();
  if (t1 > t2) {
    isChronological = false;
    orderDetails = `Event ${i} (${timeline[i].timestamp}) is after event ${i+1} (${timeline[i+1].timestamp})`;
    break;
  }
}

assert(
  'getTokenTimeline orders events chronologically (oldest first)',
  isChronological,
  orderDetails
);

// --------------------------------------------------------------------------
// TEST 10: Settings Validation Rejects Bad Values
// --------------------------------------------------------------------------
console.log('\n--- Test 10: Settings Validation Rejects Bad Values ---');
let caughtErrors = 0;

try {
  q.updateSettings({ SEVERITY_WEIGHT: -10 });
} catch (e) {
  caughtErrors++;
}

try {
  q.updateSettings({ WAIT_WEIGHT_PER_MIN: 15 });
} catch (e) {
  caughtErrors++;
}

try {
  q.updateSettings({ MAX_CONSECUTIVE_EMERGENCIES: 0 });
} catch (e) {
  caughtErrors++;
}

try {
  q.updateSettings({ EMERGENCY_BUFFER_SLOTS_DEFAULT: 25 });
} catch (e) {
  caughtErrors++;
}

assert(
  'updateSettings rejects out-of-range values with readable errors (4/4 rejected)',
  caughtErrors === 4,
  `Expected 4 caught errors, got ${caughtErrors}`
);

// --------------------------------------------------------------------------
// TEST 11: updateSettings Changes Scoring Order Dynamically
// --------------------------------------------------------------------------
console.log('\n--- Test 11: updateSettings Changes Scoring Order ---');
q.resetState();
q.resetClock();

// Clean queue for DOC-104
const s11 = q.getState();
s11.patients = s11.patients.filter(p => !(p.doctorId === 'DOC-104' && p.status === 'Waiting'));
q.saveState(s11);

// Register Patient A (Sev 2, arrived 0m ago)
const pHighSev = q.registerPatient({
  name: 'Medium Severity Low Wait',
  age: 45,
  gender: 'Female',
  phone: '9876540001',
  category: 'Walk-in',
  severity: 2,
  doctorId: 'DOC-104'
});

// Register Patient B (Sev 1, arrived 80m ago -> boosted under default threshold)
const s11B = q.getState();
const pLongWait = {
  id: 'P_LONG_WAIT',
  tokenNumber: 'ORTH-W-888',
  name: 'Low Severity Long Wait',
  age: 50,
  gender: 'Male',
  phone: '9876540002',
  category: 'Walk-in',
  severity: 1,
  department: 'Orthopedics',
  doctorId: 'DOC-104',
  doctorName: 'Dr. Vikram Patel',
  room: 'OPD Room 112',
  registrationTime: new Date(Date.now() - 80 * 60000).toISOString(),
  status: 'Waiting'
};
s11B.patients.push(pLongWait);
q.saveState(s11B);

// Under default settings (SEVERITY_WEIGHT=100, WAIT_WEIGHT_PER_MIN=3, FAIRNESS_BOOST=150):
// pHighSev score = 2*100 + 0*3 = 200
// pLongWait score = 1*100 + 80*3 + 150 = 490
let queueOrder = q.getDoctorQueue('DOC-104');
const pLongWaitAheadInitially = queueOrder[0].id === pLongWait.id;

// Now update settings: massive severity weight (300), low wait weight (0.2), low fairness boost (10)
q.updateSettings({
  SEVERITY_WEIGHT: 300,
  WAIT_WEIGHT_PER_MIN: 0.2,
  FAIRNESS_BOOST: 10
});

// Under new settings:
// pHighSev score = 2*300 + 0*0.2 = 600
// pLongWait score = 1*300 + 80*0.2 + 10 = 326
queueOrder = q.getDoctorQueue('DOC-104');
const pHighSevAheadAfterUpdate = queueOrder[0].id === pHighSev.id;

assert(
  'updateSettings dynamically inverts queue ranking based on updated triage policy',
  pLongWaitAheadInitially && pHighSevAheadAfterUpdate,
  `Initially pos 0: ${pLongWaitAheadInitially}, After update pos 0: ${pHighSevAheadAfterUpdate}`
);

// Reset settings
q.resetSettings();

// --------------------------------------------------------------------------
// TEST 12: deleteDoctor Blocked with Waiting Patients
// --------------------------------------------------------------------------
console.log('\n--- Test 12: deleteDoctor Blocked with Waiting Patients ---');
q.resetState();
q.resetClock();

let deleteBlocked = false;
let blockError = '';
try {
  // DOC-101 has waiting patients in default state
  q.deleteDoctor('DOC-101');
} catch (e) {
  deleteBlocked = true;
  blockError = e.message;
}

assert(
  'deleteDoctor is strictly blocked when doctor has waiting patients in queue',
  deleteBlocked && blockError.includes('waiting patient'),
  `Got: "${blockError}"`
);

// Verify deletion succeeds for doctor without waiting patients
q.addDoctor({
  id: 'DOC-NEW',
  name: 'Dr. New Recruit',
  department: 'General Medicine',
  room: 'Room 999',
  workingHours: { start: '09:00', end: '17:00' },
  avgConsultMinutes: 10,
  emergencyBufferSlots: 2,
  status: 'Available'
});
q.deleteDoctor('DOC-NEW');
const docStillExists = q.getDoctors().some(d => d.id === 'DOC-NEW');
assert(
  'deleteDoctor succeeds when doctor has zero waiting patients',
  !docStillExists
);

// --------------------------------------------------------------------------
// TEST 13: Admin Role Completely Removed Across System
// --------------------------------------------------------------------------
console.log('\n--- Test 13: Admin Role Removal Verification ---');

// 1. admin-dashboard.html must be deleted
const adminDashboardExists = fs.existsSync(path.join(__dirname, '../../admin-dashboard.html'));
assert(
  'admin-dashboard.html has been deleted from repository',
  !adminDashboardExists,
  'admin-dashboard.html still exists on disk'
);

// 2. auth.js has no admin credentials or Admin role
const authSource = fs.readFileSync(path.join(__dirname, '../auth.js'), 'utf-8');
const authHasAdmin = authSource.includes('admin123') || 
                     authSource.includes("'Admin'") || 
                     authSource.includes('"Admin"') ||
                     authSource.includes('role: \'Admin\'');
assert(
  'auth.js has zero Admin role or admin/admin123 credentials',
  !authHasAdmin,
  'auth.js still contains Admin role references'
);

// 3. login.html has no Admin role card
const loginSource = fs.readFileSync(path.join(__dirname, '../../login.html'), 'utf-8');
const loginHasAdmin = loginSource.includes('data-role="Admin"') || 
                      loginSource.includes('admin / admin123') ||
                      loginSource.includes('>Admin<');
assert(
  'login.html contains no Admin role cards or admin hints',
  !loginHasAdmin,
  'login.html still contains Admin cards/hints'
);

// 4. app.js has no Admin role routes or nav
const appSource = fs.readFileSync(path.join(__dirname, '../app.js'), 'utf-8');
const appHasAdmin = appSource.includes("'Admin': 'admin-dashboard.html'") || 
                    appSource.includes("'Admin': [") ||
                    appSource.includes("['Admin']");
assert(
  'app.js has zero Admin routing or navigation menus',
  !appHasAdmin,
  'app.js still contains Admin routes/menus'
);

// 5. queue-mock.js uses CONFIG_CHANGE and no ADMIN_CHANGE
const queueMockSource = fs.readFileSync(path.join(__dirname, '../queue-mock.js'), 'utf-8');
const queueHasAdminChange = queueMockSource.includes('ADMIN_CHANGE');
const queueHasConfigChange = queueMockSource.includes('CONFIG_CHANGE');
assert(
  'queue-mock.js logs CONFIG_CHANGE events (zero ADMIN_CHANGE)',
  !queueHasAdminChange && queueHasConfigChange,
  'queue-mock.js still contains ADMIN_CHANGE or is missing CONFIG_CHANGE'
);

// 6. Pages require Front Desk only
const pageList = [
  'reception-dashboard.html',
  'register-patient.html',
  'queue-manager.html',
  'token-slip.html',
  'manage-doctors.html',
  'settings.html'
];
let pagesClean = true;
let dirtyPage = '';
for (const page of pageList) {
  const content = fs.readFileSync(path.join(__dirname, `../../${page}`), 'utf-8');
  if (content.includes("requireAuth(['Admin'])") || content.includes("'Admin'")) {
    pagesClean = false;
    dirtyPage = page;
    break;
  }
}
assert(
  'Core application pages allow Front Desk (Receptionist) only with zero Admin guards',
  pagesClean,
  `Page ${dirtyPage} still references Admin`
);

// 7. README.md contains no Admin demo account
const readmeSource = fs.readFileSync(path.join(__dirname, '../../../README.md'), 'utf-8');
const readmeHasAdmin = readmeSource.includes('| **Admin** |') || readmeSource.includes('admin-dashboard.html');
assert(
  'README.md documentation contains zero Admin demo accounts or files',
  !readmeHasAdmin,
  'README.md still documents Admin role'
);

// --------------------------------------------------------------------------
// TEST 14: Discrete-Event Simulator Engine Verification
// --------------------------------------------------------------------------
console.log('\n--- Test 14: Discrete-Event Simulator Engine Verification ---');

// Check 1: Simulator is deterministic for the same seed
const simRunA = Sim.runSimulation({ ...Sim.PRESETS['busy-day'], randomSeed: 12345 });
const simRunB = Sim.runSimulation({ ...Sim.PRESETS['busy-day'], randomSeed: 12345 });
const isDeterministic = JSON.stringify(simRunA.policies.smartopd.records) === JSON.stringify(simRunB.policies.smartopd.records) &&
                        simRunA.policies.smartopd.metrics.averageWaitOverall === simRunB.policies.smartopd.metrics.averageWaitOverall;
assert(
  'simulator is deterministic for the same seed',
  isDeterministic,
  'Two simulation runs with seed 12345 produced differing results'
);

// Check 2: All three policies serve every patient exactly once
const sim75 = Sim.runSimulation({ numPatients: 75, numDoctors: 3, randomSeed: 888 });
const fcfsServed = sim75.policies.fcfs.records;
const strictServed = sim75.policies.strictPriority.records;
const smartServed = sim75.policies.smartopd.records;

const fcfsSet = new Set(fcfsServed.map(p => p.id));
const strictSet = new Set(strictServed.map(p => p.id));
const smartSet = new Set(smartServed.map(p => p.id));

const allServedOnce = fcfsServed.length === 75 && fcfsSet.size === 75 &&
                      strictServed.length === 75 && strictSet.size === 75 &&
                      smartServed.length === 75 && smartSet.size === 75;
assert(
  'all three policies serve every patient exactly once',
  allServedOnce,
  `Expected 75 unique patients served in all 3 policies, got FCFS=${fcfsSet.size}, Strict=${strictSet.size}, SmartOPD=${smartSet.size}`
);

// Check 3: In Busy Day, SmartOPD average emergency wait is less than or equal to FCFS
const busyRes = Sim.runSimulation(Sim.PRESETS['busy-day']);
const smartEmergBusy = busyRes.policies.smartopd.metrics.averageWaitEmergency;
const fcfsEmergBusy = busyRes.policies.fcfs.metrics.averageWaitEmergency;
assert(
  'in Busy Day, SmartOPD average emergency wait is less than or equal to FCFS',
  smartEmergBusy <= fcfsEmergBusy,
  `SmartOPD emergency wait (${smartEmergBusy}m) exceeded FCFS (${fcfsEmergBusy}m)`
);

// Check 4: In Emergency Surge, SmartOPD starvation count is less than or equal to Strict Priority
const surgeRes = Sim.runSimulation(Sim.PRESETS['emergency-surge']);
const smartStarvSurge = surgeRes.policies.smartopd.metrics.starvationCount;
const strictStarvSurge = surgeRes.policies.strictPriority.metrics.starvationCount;
assert(
  'in Emergency Surge, SmartOPD starvation count is less than or equal to Strict Priority',
  smartStarvSurge <= strictStarvSurge,
  `SmartOPD starvation count (${smartStarvSurge}) was greater than Strict Priority (${strictStarvSurge})`
);

// Check 5: Insights text contains numbers that match the metrics
const insights = busyRes.insights;
const fcfsM = busyRes.policies.fcfs.metrics;
const strictM = busyRes.policies.strictPriority.metrics;
const smartM = busyRes.policies.smartopd.metrics;

const hasEmergNum = insights.some(txt => txt.includes(String(smartM.avgWaitEmergency)) && txt.includes(String(fcfsM.avgWaitEmergency)));
const hasStarvNum = insights.some(txt => txt.includes(String(strictM.starvationCount)) && txt.includes(String(smartM.starvationCount)));
const hasMaxSchedNum = insights.some(txt => txt.includes(String(smartM.maxScheduledWait)));
const hasP90Num = insights.some(txt => txt.includes(String(smartM.p90Wait)));

const insightsNumbersMatch = hasEmergNum && hasStarvNum && hasMaxSchedNum && hasP90Num;
assert(
  'insights text contains numbers that match the metrics',
  insightsNumbersMatch,
  `Insights text did not match metrics: ${JSON.stringify(insights)}`
);

// Check 6: A 500 patient run finishes under 2 seconds
const tStart = Date.now();
const sim500 = Sim.runSimulation({
  numPatients: 500,
  numDoctors: 4,
  avgConsultMinutes: 10,
  emergencyPercent: 15,
  scheduledPercent: 45,
  arrivalSpreadMinutes: 300,
  randomSeed: 42
});
const tElapsed = Date.now() - tStart;
assert(
  'a 500 patient run finishes under 2 seconds',
  tElapsed < 2000 && sim500.patientsCount === 500,
  `500 patient simulation took ${tElapsed}ms (limit: 2000ms)`
);

// --------------------------------------------------------------------------
// TEST 15: Simulator Offered Load, Rebalanced Presets & Shared Scoring Verification
// --------------------------------------------------------------------------
console.log('\n--- Test 15: Offered Load, Presets & Shared Scoring Verification ---');

// Check 1: offeredLoadPercent is correct for a known config
const knownConfig = {
  numPatients: 100,
  numDoctors: 4,
  avgConsultMinutes: 12,
  arrivalSpreadMinutes: 300,
  randomSeed: 42
};
// Load = (100 * 12) / (4 * 300) = 1200 / 1200 = 100%
const knownRun = Sim.runSimulation(knownConfig);
assert(
  'offeredLoadPercent is correct for a known config',
  knownRun.offeredLoadPercent === 100,
  `Expected offeredLoadPercent = 100, got ${knownRun.offeredLoadPercent}`
);

// Check 2: Preset offered load ranges computed from actual preset objects
const quietLoad = Sim.runSimulation(Sim.PRESETS['quiet-day']).offeredLoadPercent;
assert(
  'Quiet Day preset load is between 50 and 70',
  quietLoad >= 50 && quietLoad <= 70,
  `Expected Quiet Day load between 50% and 70%, got ${quietLoad}%`
);

const busyPresetRun = Sim.runSimulation(Sim.PRESETS['busy-day']);
const busyLoad = busyPresetRun.offeredLoadPercent;
assert(
  'Busy Day preset load is between 80 and 100',
  busyLoad >= 80 && busyLoad <= 100,
  `Expected Busy Day load between 80% and 100%, got ${busyLoad}%`
);

const surgeLoad = Sim.runSimulation(Sim.PRESETS['emergency-surge']).offeredLoadPercent;
assert(
  'Emergency Surge preset load is between 100 and 115',
  surgeLoad >= 100 && surgeLoad <= 115,
  `Expected Emergency Surge load between 100% and 115%, got ${surgeLoad}%`
);

// Check 3: Averaged over seeds 1 to 30, SmartOPD scheduled starvation count is less than or equal to Strict Priority
let sumSmartStarv = 0;
let sumStrictStarv = 0;
let sumSmartMaxSched = 0;
let sumStrictMaxSched = 0;

for (let s = 1; s <= 30; s++) {
  const run = Sim.runSimulation({ ...Sim.PRESETS['busy-day'], randomSeed: s });
  sumSmartStarv += run.policies.smartopd.metrics.starvationCount;
  sumStrictStarv += run.policies.strictPriority.metrics.starvationCount;
  sumSmartMaxSched += run.policies.smartopd.metrics.maxScheduledWait;
  sumStrictMaxSched += run.policies.strictPriority.metrics.maxScheduledWait;
}

const avgSmartStarv = sumSmartStarv / 30;
const avgStrictStarv = sumStrictStarv / 30;
assert(
  'averaged over seeds 1 to 30, SmartOPD scheduled starvation count is less than or equal to Strict Priority',
  avgSmartStarv <= avgStrictStarv,
  `SmartOPD avg starvation (${avgSmartStarv.toFixed(2)}) exceeded Strict Priority (${avgStrictStarv.toFixed(2)})`
);

// Check 4: Averaged over seeds 1 to 30, SmartOPD max scheduled wait is less than or equal to Strict Priority
const avgSmartMaxSched = sumSmartMaxSched / 30;
const avgStrictMaxSched = sumStrictMaxSched / 30;
assert(
  'averaged over seeds 1 to 30, SmartOPD max scheduled wait is less than or equal to Strict Priority',
  avgSmartMaxSched <= avgStrictMaxSched,
  `SmartOPD avg max scheduled wait (${avgSmartMaxSched.toFixed(2)}m) exceeded Strict Priority (${avgStrictMaxSched.toFixed(2)}m)`
);

// Check 5: Shared scoring function equality test
// Proving that SmartOPDScoring and queue-mock give the same score for the same patient
const testPatientScheduled = {
  id: 'TEST-P-001',
  tokenNumber: 'CARD-S-001',
  category: 'Scheduled',
  severity: 3,
  registrationTime: new Date(Date.now() - 25 * 60000).toISOString(),
  appointmentTime: '12:00',
  arrivalMinute: 5,
  appointmentMinute: 30
};
const nowTime = new Date();
const scoreFromQueueMock = q.calculatePriorityScore(testPatientScheduled, nowTime);
const scoreFromScoringModule = Scoring.calculatePriorityScore(testPatientScheduled, { now: nowTime, settings: q.getSettings() });

const testPatientEmergency = {
  id: 'TEST-P-002',
  tokenNumber: 'EMERG-E-002',
  category: 'Emergency',
  severity: 5,
  registrationTime: new Date(Date.now() - 10 * 60000).toISOString(),
  arrivalMinute: 10
};
const emergScoreQM = q.calculatePriorityScore(testPatientEmergency, nowTime);
const emergScoreSM = Scoring.calculatePriorityScore(testPatientEmergency, { now: nowTime, settings: q.getSettings() });

const scoringEqual = (scoreFromQueueMock === scoreFromScoringModule) && (emergScoreQM === emergScoreSM);
assert(
  'shared scoring function equality test',
  scoringEqual,
  `QueueMock scores (${scoreFromQueueMock}, ${emergScoreQM}) differed from Scoring module (${scoreFromScoringModule}, ${emergScoreSM})`
);

// Check 6: defaults equal 150 / 3 / 45 after resetSettings()
q.resetSettings();
const defs = q.getSettings();
const isTunedDefaults = defs.FAIRNESS_BOOST === 150 &&
                        defs.WAIT_WEIGHT_PER_MIN === 3 &&
                        defs.FAIRNESS_THRESHOLD_SCHEDULED === 45;
assert(
  'defaults equal 150 / 3 / 45 after resetSettings()',
  isTunedDefaults,
  `Expected 150/3/45, got BOOST=${defs.FAIRNESS_BOOST}, WAIT=${defs.WAIT_WEIGHT_PER_MIN}, THRESH=${defs.FAIRNESS_THRESHOLD_SCHEDULED}`
);

// Check 7: insight text has correct singular/plural for a run where the count is 1
// In emergency-surge seed 43, strict starvation count is 1
const resSeed43 = Sim.runSimulation({ ...Sim.PRESETS['emergency-surge'], randomSeed: 43 });
const hasSingular = resSeed43.insights.some(txt => txt.includes('1 scheduled patient') && !txt.includes('1 scheduled patients'));
const noBadPlural = !resSeed43.insights.some(txt => txt.includes('1 scheduled patients') || txt.includes('1 patients') || txt.includes('1 pts'));
assert(
  'insight text has correct singular/plural for a run where the count is 1',
  hasSingular && noBadPlural,
  `Insights text had plural issues: ${JSON.stringify(resSeed43.insights)}`
);

// Check 8: the trade-off sentence appears only when walk-in wait under SmartOPD exceeds FCFS
// Case A: busy-day seed 42 where SmartOPD walk-in wait (16m) > FCFS walk-in wait (10m)
const bWalkinRes = Sim.runSimulation({ ...Sim.PRESETS['busy-day'], randomSeed: 42 });
const diffW = bWalkinRes.policies.smartopd.metrics.avgWaitWalkin - bWalkinRes.policies.fcfs.metrics.avgWaitWalkin;
const tradeOffPrefix = 'Trade-off: walk-in patients waited';
const hasTradeoffA = bWalkinRes.insights.some(txt => txt.startsWith(tradeOffPrefix));

// Case B: cohort where walk-in wait under SmartOPD <= FCFS
const mockNoWalkinLag = Sim.runSimulation({ numPatients: 40, numDoctors: 4, emergencyPercent: 0, scheduledPercent: 0, randomSeed: 10 });
const diffMock = mockNoWalkinLag.policies.smartopd.metrics.avgWaitWalkin - mockNoWalkinLag.policies.fcfs.metrics.avgWaitWalkin;
const hasTradeoffB = mockNoWalkinLag.insights.some(txt => txt.startsWith(tradeOffPrefix));

const tradeoffConditionCorrect = (diffW > 0 ? hasTradeoffA : !hasTradeoffA) && (diffMock <= 0 ? !hasTradeoffB : hasTradeoffB);
assert(
  'the trade-off sentence appears only when walk-in wait under SmartOPD exceeds FCFS',
  tradeoffConditionCorrect,
  `diffW=${diffW}, hasTradeoffA=${hasTradeoffA}; diffMock=${diffMock}, hasTradeoffB=${hasTradeoffB}`
);

// --------------------------------------------------------------------------
// SUMMARY RESULTS
// --------------------------------------------------------------------------
console.log('\n========================================================');
console.log(`TEST SUITE RESULTS: ${passedCount} / ${totalCount} CHECKS PASSED`);
if (passedCount === totalCount) {
  console.log('STATUS: ALL TESTS PASSED SUCCESSFULLY! (100%)');
  console.log('========================================================\n');
  process.exit(0);
} else {
  console.error(`STATUS: ${totalCount - passedCount} TESTS FAILED!`);
  console.log('========================================================\n');
  process.exit(1);
}
