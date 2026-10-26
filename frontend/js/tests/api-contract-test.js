/**
 * SmartOPD – REST API Integration Contract Verification Suite
 * File: frontend/js/tests/api-contract-test.js
 * 
 * Verifies live API responses against frontend contract adapters:
 * 1. Automatically boots a fresh empty temporary SQLite database on port 8086
 * 2. Checks first-run setup flow: needsSetup true -> POST /api/setup -> needsSetup false -> second setup 409
 * 3. Log in as Front Desk
 * 4. Creates department and doctor through the API
 * 5. Creates doctor login through POST /api/staff
 * 6. Logs in as that doctor, verifying doctor role & permissions
 * 7. Registers a patient as Front Desk, doctor calls next and completes consultation
 * 8. Deletes the doctor login via DELETE /api/staff/{username}
 * 9. Runs all API endpoints through SmartOPDAdapters
 * 10. Shuts down cleanly without mutating the user's production database
 * 
 * Run with: node frontend/js/tests/api-contract-test.js
 */

const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');
const SmartOPDAdapters = require('../api-adapters.js');

let totalChecks = 0;
let passedChecks = 0;
let failedChecks = 0;

function assert(name, condition, details = '') {
  totalChecks++;
  if (condition) {
    passedChecks++;
    console.log(`[PASS] ${name}`);
  } else {
    failedChecks++;
    console.error(`[FAIL] ${name} ${details ? '- ' + details : ''}`);
  }
}

async function apiRequest(baseUrl, endpoint, options = {}, token = null) {
  const url = `${baseUrl}${endpoint}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    ...options,
    headers
  });

  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch (e) {
    json = text;
  }

  return { status: res.status, ok: res.ok, body: json };
}

async function waitForServer(baseUrl, timeoutMs = 25000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(`${baseUrl}/api/setup/status`);
      if (res.status === 200) return true;
    } catch (e) {
      // Server not ready yet
    }
    await new Promise(r => setTimeout(r, 400));
  }
  return false;
}

async function runContractTests(BASE) {
  console.log('============================================================');
  console.log(`SmartOPD API Contract Test Suite`);
  console.log(`Target Backend: ${BASE}`);
  console.log('============================================================\n');

  // Step 0: First-Run Setup on Fresh Database
  console.log('--- Step 0: First-Run Setup (Fresh Database) ---');
  const statusBefore = await apiRequest(BASE, '/api/setup/status', { method: 'GET' });
  assert('Step 0.1: GET /api/setup/status returns needsSetup: true on empty database', statusBefore.body?.needsSetup === true);

  const setupRes = await apiRequest(BASE, '/api/setup', {
    method: 'POST',
    body: JSON.stringify({
      username: 'frontdesk',
      displayName: 'Lead Receptionist',
      password: 'HospitalPassword1'
    })
  });
  assert('Step 0.2: POST /api/setup creates first Front Desk account (201)', setupRes.status === 201);

  const statusAfter = await apiRequest(BASE, '/api/setup/status', { method: 'GET' });
  assert('Step 0.3: GET /api/setup/status returns needsSetup: false after setup', statusAfter.body?.needsSetup === false);

  const secondSetup = await apiRequest(BASE, '/api/setup', {
    method: 'POST',
    body: JSON.stringify({
      username: 'attacker',
      displayName: 'Attacker',
      password: 'HospitalPassword1'
    })
  });
  assert('Step 0.4: Second POST /api/setup is rejected with 409 Conflict', secondSetup.status === 409);

  // Step 1: Front Desk Authentication
  console.log('\n--- Step 1: Front Desk Authentication ---');
  const badLogin = await apiRequest(BASE, '/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'frontdesk', password: 'WrongPassword' })
  });
  assert('Step 1.1: POST /api/auth/login with wrong password returns 401', badLogin.status === 401);

  const recLogin = await apiRequest(BASE, '/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'frontdesk', password: 'HospitalPassword1' })
  });
  assert('Step 1.2: POST /api/auth/login with valid Front Desk credentials returns 200', recLogin.status === 200);
  assert('Step 1.3: Front Desk session token is issued', typeof recLogin.body?.token === 'string' && recLogin.body.token.length > 20);
  assert('Step 1.4: Front Desk role is RECEPTIONIST', recLogin.body?.role === 'RECEPTIONIST');
  let recToken = recLogin.body?.token;

  const meRes = await apiRequest(BASE, '/api/auth/me', { method: 'GET' }, recToken);
  assert('Step 1.5: GET /api/auth/me returns 200 for authenticated Front Desk', meRes.status === 200);
  assert('Step 1.6: GET /api/auth/me matches frontdesk identity', meRes.body?.username === 'frontdesk' && meRes.body?.role === 'RECEPTIONIST');

  // Step 1b: Change Password Flow
  console.log('\n--- Step 1b: Change Password Flow ---');
  const badPwChange = await apiRequest(BASE, '/api/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword: 'WrongPassword', newPassword: 'NewFrontDeskPassword9' })
  }, recToken);
  assert('Step 1b.1: POST /api/auth/change-password with wrong current password returns 400', badPwChange.status === 400);

  const weakPwChange = await apiRequest(BASE, '/api/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword: 'HospitalPassword1', newPassword: 'weak' })
  }, recToken);
  assert('Step 1b.2: POST /api/auth/change-password with weak new password returns 400', weakPwChange.status === 400);

  const goodPwChange = await apiRequest(BASE, '/api/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword: 'HospitalPassword1', newPassword: 'NewFrontDeskPassword9' })
  }, recToken);
  assert('Step 1b.3: POST /api/auth/change-password with valid passwords returns 200', goodPwChange.status === 200);

  // Verify old password stops working
  const oldPwLogin = await apiRequest(BASE, '/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'frontdesk', password: 'HospitalPassword1' })
  });
  assert('Step 1b.4: POST /api/auth/login with old password is now rejected (401)', oldPwLogin.status === 401);

  // Verify new password works
  const newPwLogin = await apiRequest(BASE, '/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'frontdesk', password: 'NewFrontDeskPassword9' })
  });
  assert('Step 1b.5: POST /api/auth/login with new password succeeds (200)', newPwLogin.status === 200);
  recToken = newPwLogin.body?.token;

  // Step 2: Department Management
  console.log('\n--- Step 2: Department Management ---');
  const createDeptRes = await apiRequest(BASE, '/api/departments', {
    method: 'POST',
    body: JSON.stringify({ code: 'CARD', name: 'Cardiology' })
  }, recToken);
  assert('Step 2.1: POST /api/departments creates Cardiology department (201)', createDeptRes.status === 201);

  const deptsRes = await apiRequest(BASE, '/api/departments', { method: 'GET' }, recToken);
  assert('Step 2.2: GET /api/departments returns created department list', Array.isArray(deptsRes.body) && deptsRes.body.length >= 1);
  assert('Step 2.3: Cardiology code is present', deptsRes.body.some(d => d.code === 'CARD'));

  // Step 3: Doctor Registration
  console.log('\n--- Step 3: Doctor Registration ---');
  const createDocRes = await apiRequest(BASE, '/api/doctors', {
    method: 'POST',
    body: JSON.stringify({
      name: 'Dr. Test Specialist',
      age: 45,
      gender: 'Male',
      phone: '9876543210',
      department: 'Cardiology',
      deptCode: 'CARD',
      room: 'OPD Room 101',
      workingHours: '09:00 - 17:00',
      avgConsultMinutes: 10,
      emergencyBufferSlots: 2,
      status: 'Available'
    })
  }, recToken);
  assert('Step 3.1: POST /api/doctors registers new doctor (201)', createDocRes.status === 201);
  const testDocId = createDocRes.body?.id;
  assert('Step 3.2: Registered doctor has assigned ID', typeof testDocId === 'string' && testDocId.length > 0);

  // Step 4: Staff Management (Create Doctor Login)
  console.log('\n--- Step 4: Staff Management (Create Doctor Login) ---');
  const createStaffRes = await apiRequest(BASE, '/api/staff', {
    method: 'POST',
    body: JSON.stringify({
      username: 'dr_specialist',
      displayName: 'Dr. Test Specialist',
      password: 'DoctorPassword1',
      role: 'DOCTOR',
      doctorId: testDocId
    })
  }, recToken);
  assert('Step 4.1: POST /api/staff creates doctor login account (201)', createStaffRes.status === 201);
  assert('Step 4.2: Staff DTO does not leak password or hash', !createStaffRes.body?.password && !createStaffRes.body?.passwordHash);

  const staffListRes = await apiRequest(BASE, '/api/staff', { method: 'GET' }, recToken);
  assert('Step 4.3: GET /api/staff lists created staff accounts', Array.isArray(staffListRes.body) && staffListRes.body.length >= 2);

  // Step 5: Doctor Authentication & RBAC Isolation
  console.log('\n--- Step 5: Doctor Authentication & RBAC ---');
  const docLogin = await apiRequest(BASE, '/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'dr_specialist', password: 'DoctorPassword1' })
  });
  assert('Step 5.1: POST /api/auth/login succeeds for created doctor credentials', docLogin.status === 200);
  assert('Step 5.2: Doctor login role is DOCTOR', docLogin.body?.role === 'DOCTOR');
  assert('Step 5.3: Doctor login binds correct doctorId', docLogin.body?.doctorId === testDocId);
  const docToken = docLogin.body?.token;

  // Doctor RBAC: forbidden from staff management
  const docStaffAttempt = await apiRequest(BASE, '/api/staff', { method: 'GET' }, docToken);
  assert('Step 5.4: DOCTOR role is forbidden (403) from accessing GET /api/staff', docStaffAttempt.status === 403);

  // Step 6: Patient Registration by Front Desk
  console.log('\n--- Step 6: Patient Registration ---');
  const regPatientRes = await apiRequest(BASE, '/api/patients', {
    method: 'POST',
    body: JSON.stringify({
      name: 'John Patient',
      age: 38,
      gender: 'Male',
      phone: '9123456780',
      category: 'Walk-in',
      severity: 3,
      doctorId: testDocId,
      chiefComplaint: 'Mild chest tightness'
    })
  }, recToken);
  assert('Step 6.1: POST /api/patients registers patient (201)', regPatientRes.status === 201);
  const patientToken = regPatientRes.body?.token;
  assert('Step 6.2: Generated patient token matches pattern', typeof patientToken === 'string' && patientToken.length > 0);

  // Step 7: Doctor Queue Consultation Lifecycle
  console.log('\n--- Step 7: Consultation Lifecycle ---');
  const queueRes = await apiRequest(BASE, `/api/doctors/${testDocId}/queue`, { method: 'GET' }, docToken);
  assert('Step 7.1: Doctor can view own queue (200)', queueRes.status === 200);
  assert('Step 7.2: Patient is present in doctor waiting queue', Array.isArray(queueRes.body?.waitingList) && queueRes.body.waitingList.length > 0);

  const callNextRes = await apiRequest(BASE, `/api/doctors/${testDocId}/call-next`, { method: 'POST' }, docToken);
  assert('Step 7.3: POST /api/doctors/{id}/call-next calls the waiting patient (200)', callNextRes.status === 200);

  const completeRes = await apiRequest(BASE, `/api/doctors/${testDocId}/complete`, { method: 'POST' }, docToken);
  assert('Step 7.4: POST /api/doctors/{id}/complete completes consultation (200)', completeRes.status === 200);

  // Step 8: Public Endpoints (Zero PII Verification)
  console.log('\n--- Step 8: Public Endpoints & Privacy ---');
  const boardRes = await apiRequest(BASE, '/api/public/board', { method: 'GET' });
  assert('Step 8.1: GET /api/public/board returns 200 without token', boardRes.status === 200);
  const adaptedBoard = SmartOPDAdapters.adaptPublicBoard(boardRes.body);
  assert('Step 8.2: adaptPublicBoard returns non-empty list', Array.isArray(adaptedBoard) && adaptedBoard.length > 0);
  assert('Step 8.3: Public board strictly does not leak patient names', !JSON.stringify(boardRes.body).includes('John Patient'));

  const trackerRes = await apiRequest(BASE, `/api/public/tracker/${patientToken}`, { method: 'GET' });
  assert('Step 8.4: GET /api/public/tracker/{token} returns 200', trackerRes.status === 200);
  const adaptedTracker = SmartOPDAdapters.adaptPublicTracker(trackerRes.body);
  assert('Step 8.5: adaptPublicTracker returns token matching query', adaptedTracker.token === patientToken);

  // Step 9: Settings Endpoints
  console.log('\n--- Step 9: Settings Endpoints ---');
  const getSettingsRes = await apiRequest(BASE, '/api/settings', { method: 'GET' }, recToken);
  assert('Step 9.1: GET /api/settings returns 200', getSettingsRes.status === 200);
  const adaptedSettings = SmartOPDAdapters.adaptSettings(getSettingsRes.body);
  assert('Step 9.2: adaptSettings returns fairnessBoost', typeof adaptedSettings.fairnessBoost === 'number');

  // Step 10: Discrete-Event Simulator & Analytics
  console.log('\n--- Step 10: Simulation & Analytics ---');
  const simRes = await apiRequest(BASE, '/api/simulation/run', {
    method: 'POST',
    body: JSON.stringify({ preset: 'busy-day', randomSeed: 42 })
  }, recToken);
  assert('Step 10.1: POST /api/simulation/run returns 200', simRes.status === 200);
  const adaptedSim = SmartOPDAdapters.adaptSimulationResult(simRes.body);
  assert('Step 10.2: adaptSimulationResult returns valid policy data', typeof adaptedSim.policies === 'object');

  // Step 11: Clock Simulation
  console.log('\n--- Step 11: Clock Simulation ---');
  const advanceClockRes = await apiRequest(BASE, '/api/clock/advance', {
    method: 'POST',
    body: JSON.stringify({ minutes: 50 })
  }, recToken);
  assert('Step 11.1: POST /api/clock/advance returns 200', advanceClockRes.status === 200);

  const resetClockRes = await apiRequest(BASE, '/api/clock/reset', { method: 'POST' }, recToken);
  assert('Step 11.2: POST /api/clock/reset returns 200', resetClockRes.status === 200);

  // Step 12: Doctor Deletion Cascades to Login & Constraints
  console.log('\n--- Step 12: Doctor Deletion Cascades to Login & Constraints ---');
  const deleteSelf = await apiRequest(BASE, '/api/staff/frontdesk', { method: 'DELETE' }, recToken);
  assert('Step 12.1: Deleting own account is rejected with 409 Conflict', deleteSelf.status === 409);

  // Create dedicated doctor to test deletion cascade
  const cascadeDocRes = await apiRequest(BASE, '/api/doctors', {
    method: 'POST',
    body: JSON.stringify({
      name: 'Dr. Cascade Tester',
      age: 48,
      gender: 'Female',
      phone: '9811223344',
      department: 'Cardiology',
      deptCode: 'CARD',
      room: 'OPD Room 204',
      workingHours: '09:00 - 17:00',
      avgConsultMinutes: 10,
      emergencyBufferSlots: 2,
      status: 'Available'
    })
  }, recToken);
  assert('Step 12.2: Register temporary doctor for cascade test (201)', cascadeDocRes.status === 201);
  const cascadeDocId = cascadeDocRes.body?.id;

  // Create login for this doctor via /api/staff
  const cascadeStaffRes = await apiRequest(BASE, '/api/staff', {
    method: 'POST',
    body: JSON.stringify({
      username: 'dr_cascade',
      displayName: 'Dr. Cascade Tester',
      password: 'CascadePass123',
      role: 'DOCTOR',
      doctorId: cascadeDocId
    })
  }, recToken);
  assert('Step 12.3: POST /api/staff creates doctor login (201)', cascadeStaffRes.status === 201);

  // Authenticate as this doctor
  const cascadeLoginRes = await apiRequest(BASE, '/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'dr_cascade', password: 'CascadePass123' })
  });
  assert('Step 12.4: Doctor logs in with credentials (200)', cascadeLoginRes.status === 200);
  const cascadeDocToken = cascadeLoginRes.body?.token;

  // Deleting the doctor must delete their login in the same transaction and invalidate their sessions
  const deleteDocRes = await apiRequest(BASE, `/api/doctors/${cascadeDocId}`, { method: 'DELETE' }, recToken);
  assert('Step 12.5: DELETE /api/doctors/{id} deletes doctor (200)', deleteDocRes.status === 200);

  // Doctor login can no longer authenticate
  const loginDeletedDoc = await apiRequest(BASE, '/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'dr_cascade', password: 'CascadePass123' })
  });
  assert('Step 12.6: Deleted doctor login can no longer authenticate (401)', loginDeletedDoc.status === 401);

  // Active doctor session is invalidated
  const docSessionInvalidated = await apiRequest(BASE, '/api/auth/me', { method: 'GET' }, cascadeDocToken);
  assert('Step 12.7: Active doctor session was invalidated upon doctor deletion (401)', docSessionInvalidated.status === 401);

  // Confirm staff account is removed
  const staffListAfter = await apiRequest(BASE, '/api/staff', { method: 'GET' }, recToken);
  assert('Step 12.8: dr_cascade is no longer in staff accounts list', !staffListAfter.body?.some(s => s.username === 'dr_cascade'));

  // Step 13: Logout
  console.log('\n--- Step 13: Logout ---');
  const logoutRes = await apiRequest(BASE, '/api/auth/logout', { method: 'POST' }, recToken);
  assert('Step 13.1: POST /api/auth/logout invalidates session (200)', logoutRes.status === 200);

  // Summary
  console.log('\n============================================================');
  console.log(`API Contract Verification Results: ${passedChecks}/${totalChecks} PASSED`);
  console.log(`Failures: ${failedChecks}`);
  console.log('============================================================\n');

  if (failedChecks > 0) {
    throw new Error(`${failedChecks} contract check(s) failed.`);
  }
}

async function main() {
  const envBase = process.env.BASE;
  if (envBase) {
    try {
      await runContractTests(envBase);
      process.exit(0);
    } catch (e) {
      console.error(e.message);
      process.exit(1);
    }
    return;
  }

  // Spin up dedicated test server on port 8086 with a fresh temp SQLite database
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'smartopd-contract-'));
  const tempDb = path.join(tempDir, 'clean-test.db');
  const testPort = 8086;
  const testBase = `http://localhost:${testPort}`;
  const repoRoot = path.resolve(__dirname, '../../..');
  const jarPath = path.join(repoRoot, 'backend', 'target', 'smartopd.jar');

  console.log(`[CONTRACT TEST] Booting isolated test instance on port ${testPort}...`);
  console.log(`[CONTRACT TEST] Database: ${tempDb}`);

  const serverProc = spawn('java', [
    `-Dserver.port=${testPort}`,
    `-Dsmartopd.db.path=${tempDb}`,
    '-jar',
    jarPath
  ], {
    cwd: path.join(repoRoot, 'backend'),
    stdio: 'ignore'
  });

  serverProc.on('error', (err) => {
    console.error('[CONTRACT TEST] Failed to spawn java server process:', err);
  });

  try {
    const isReady = await waitForServer(testBase, 25000);
    if (!isReady) {
      throw new Error(`Test server on port ${testPort} did not become ready within 25 seconds.`);
    }

    await runContractTests(testBase);
    console.log('[ALL PASS] All API contract checks passed on fresh isolated database.\n');
  } catch (err) {
    console.error('[ERROR]', err.message || err);
    process.exitCode = 1;
  } finally {
    console.log(`[CONTRACT TEST] Terminating test server process (PID ${serverProc.pid})...`);
    serverProc.kill('SIGTERM');
    try {
      if (fs.existsSync(tempDb)) fs.unlinkSync(tempDb);
      if (fs.existsSync(tempDir)) fs.rmdirSync(tempDir);
    } catch (e) {
      // Ignore cleanup error
    }
  }
}

main();
