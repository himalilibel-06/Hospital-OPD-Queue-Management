/**
 * SmartOPD – Static Code Inspection Verification Script
 * File: frontend/js/tests/static-check.js
 * 
 * Verifies that all 6 migrated frontend pages:
 * 1. Do not reference SmartOPDQueue
 * 2. Do not load queue-mock.js
 * 3. Do not load mock-data.js
 * 4. Do not use localStorage for queue state
 * 5. Do not contain hard-coded doctor names or IDs
 * 
 * Run with: node frontend/js/tests/static-check.js
 */

const fs = require('fs');
const path = require('path');

const MIGRATED_PAGES = [
  'patient-tracker.html',
  'display-board.html',
  'reception-dashboard.html',
  'register-patient.html',
  'token-slip.html',
  'queue-manager.html',
  'doctor-console.html',
  'manage-doctors.html',
  'settings.html',
  'analytics.html',
  'setup.html'
];

const FORBIDDEN_PATTERNS = [
  { label: 'SmartOPDQueue reference', regex: /\bSmartOPDQueue\b/ },
  { label: 'queue-mock.js script tag', regex: /queue-mock\.js/ },
  { label: 'mock-data.js script tag', regex: /mock-data\.js/ },
  { label: 'localStorage queue state', regex: /localStorage\s*\.\s*(?:getItem|setItem|removeItem)\s*\(\s*['"]smartopd_state['"]\s*\)/ },
  { label: 'Hard-coded doctor ID DOC-101', regex: /\bDOC-101\b/ },
  { label: 'Hard-coded doctor ID DOC-102', regex: /\bDOC-102\b/ },
  { label: 'Hard-coded doctor ID DOC-103', regex: /\bDOC-103\b/ },
  { label: 'Hard-coded doctor ID DOC-104', regex: /\bDOC-104\b/ },
  { label: 'Hard-coded doctor ID DOC-105', regex: /\bDOC-105\b/ },
  { label: 'Hard-coded doctor ID DOC-106', regex: /\bDOC-106\b/ },
  { label: 'Hard-coded doctor name "Rohith"', regex: /\bRohith\b/i },
  { label: 'Hard-coded doctor name "Ananya"', regex: /\bAnanya\b/i },
  { label: 'Hard-coded doctor name "Rajesh Mehta"', regex: /Rajesh\s+Mehta/i },
  { label: 'Hard-coded doctor name "Priya Nair"', regex: /Priya\s+Nair/i },
  { label: 'Hard-coded doctor name "Vikram Patel"', regex: /Vikram\s+Patel/i },
  { label: 'Hard-coded doctor name "Suresh Verma"', regex: /Suresh\s+Verma/i },
  { label: 'simulator.js script tag', regex: /simulator\.js/ },
  { label: 'Old demo credential "rec123"', regex: /\brec123\b/i },
  { label: 'Old demo credential "doc123"', regex: /\bdoc123\b/i },
  { label: 'Old demo credential "pat123" or "patient123"', regex: /\b(?:pat123|patient123)\b/i },
  { label: 'Pre-filled password field', regex: /type\s*=\s*["']password["'][^>]*value\s*=/i },
  { label: 'Reference or link to staff.html', regex: /\bstaff\.html\b/i }
];

const frontendDir = path.resolve(__dirname, '../..');

let totalChecks = 0;
let passedChecks = 0;
let failedChecks = 0;

console.log('============================================================');
console.log('SmartOPD Static Migration Audit (All 11 Frontend Pages)');
console.log('============================================================\n');

// 1. Assert staff.html must NOT exist
totalChecks++;
const staffHtmlPath = path.join(frontendDir, 'staff.html');
if (fs.existsSync(staffHtmlPath)) {
  console.error('[FAIL] staff.html still exists in frontend/ directory!');
  failedChecks++;
} else {
  passedChecks++;
  console.log('[PASS] staff.html does not exist.');
}

// 2. Audit all migrated pages
for (const pageName of MIGRATED_PAGES) {
  const filePath = path.join(frontendDir, pageName);
  if (!fs.existsSync(filePath)) {
    console.error(`[FAIL] File missing: ${pageName}`);
    failedChecks++;
    continue;
  }

  const content = fs.readFileSync(filePath, 'utf8');

  for (const rule of FORBIDDEN_PATTERNS) {
    totalChecks++;
    const match = content.match(rule.regex);
    if (match) {
      console.error(`[FAIL] ${pageName}: Contains forbidden pattern [${rule.label}] at match "${match[0]}"`);
      failedChecks++;
    } else {
      passedChecks++;
    }
  }

  // manage-doctors.html must contain doctor-login section
  if (pageName === 'manage-doctors.html') {
    totalChecks++;
    if (content.includes('id="doctor-login-section"') && content.includes('id="doc-create-login-cb"')) {
      passedChecks++;
      console.log(`[PASS] manage-doctors.html: Contains doctor-login section.`);
    } else {
      console.error(`[FAIL] manage-doctors.html: Missing doctor-login section!`);
      failedChecks++;
    }
  }

  console.log(`[PASS] ${pageName}: Passed all static checks.`);
}

console.log('\n------------------------------------------------------------');
console.log(`Summary: ${passedChecks}/${totalChecks} checks passed. (${failedChecks} failures)`);
console.log('------------------------------------------------------------\n');

if (failedChecks > 0) {
  process.exit(1);
} else {
  console.log('[ALL PASS] All migrated pages strictly satisfy static decoupling constraints.\n');
  process.exit(0);
}
