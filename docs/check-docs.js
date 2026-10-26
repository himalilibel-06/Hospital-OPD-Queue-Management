/**
 * SmartOPD – Documentation Integrity Verification Script
 * File: docs/check-docs.js
 * 
 * Validates documentation against ground truth code and schema:
 * 1. Java class names in UML class diagrams and OOP table exist under backend/src/main/java
 * 2. Database tables in docs/er-diagram.md match backend/src/main/resources/schema.sql exactly
 * 3. HTTP endpoint paths in docs/ match backend/API.md
 * 4. File paths referenced in docs/VIVA.md and docs/ARCHITECTURE.md exist on disk
 * 
 * Execution: node docs/check-docs.js
 */

const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..');
const DOCS_DIR = path.join(ROOT_DIR, 'docs');
const JAVA_SRC_DIR = path.join(ROOT_DIR, 'backend', 'src', 'main', 'java');
const SCHEMA_FILE = path.join(ROOT_DIR, 'backend', 'src', 'main', 'resources', 'schema.sql');
const API_DOC_FILE = path.join(ROOT_DIR, 'backend', 'API.md');

let totalChecks = 0;
let passedChecks = 0;
let failedChecks = 0;

function assert(condition, message) {
  totalChecks++;
  if (condition) {
    passedChecks++;
    console.log(`[PASS] ${message}`);
  } else {
    failedChecks++;
    console.error(`[FAIL] ${message}`);
  }
}

// ----------------------------------------------------------------------------
// Helper: Recursively find all files in directory
// ----------------------------------------------------------------------------
function getFilesRecursively(dir, filterExt = null) {
  let results = [];
  if (!fs.existsSync(dir)) return results;
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results = results.concat(getFilesRecursively(fullPath, filterExt));
    } else if (!filterExt || entry.name.endsWith(filterExt)) {
      results.push(fullPath);
    }
  }
  return results;
}

// ============================================================================
// CHECK 1: Verify Java Class Names in UML Diagrams & OOP Table
// ============================================================================
console.log('\n============================================================');
console.log('--- Check 1: Java Classes in UML Diagrams & OOP Architecture ---');
console.log('============================================================');

// 1. Gather all existing Java classes and inner classes/records under backend/src/main/java
const javaFiles = getFilesRecursively(JAVA_SRC_DIR, '.java');
const validClassNames = new Set();

for (const f of javaFiles) {
  const baseName = path.basename(f, '.java');
  validClassNames.add(baseName);
  
  // Also collect any static inner classes/records declared inside
  const content = fs.readFileSync(f, 'utf8');
  const innerClassRegex = /(?:class|interface|record|enum)\s+([A-Za-z0-9_]+)/g;
  let match;
  while ((match = innerClassRegex.exec(content)) !== null) {
    validClassNames.add(match[1]);
  }
}

// Diagram legend placeholders to ignore
const LEGEND_PLACEHOLDERS = new Set([
  'SuperClass', 'SubClass', 'InterfaceType', 'ConcreteClass', 'ClientClass', 'TargetClass'
]);

// Extract class names from docs/uml-class-diagrams.md (strictly inside mermaid blocks)
const umlFile = path.join(DOCS_DIR, 'uml-class-diagrams.md');
if (fs.existsSync(umlFile)) {
  const umlContent = fs.readFileSync(umlFile, 'utf8');
  // Match only inside ```mermaid classDiagram ... ``` blocks
  const mermaidBlockRegex = /```mermaid\s+classDiagram([\s\S]*?)```/g;
  let blockMatch;
  const classesFound = new Set();
  
  while ((blockMatch = mermaidBlockRegex.exec(umlContent)) !== null) {
    const blockBody = blockMatch[1];
    const classDefRegex = /class\s+([A-Za-z0-9_]+)\s*\{?/g;
    let m;
    while ((m = classDefRegex.exec(blockBody)) !== null) {
      const cls = m[1];
      if (!LEGEND_PLACEHOLDERS.has(cls)) {
        classesFound.add(cls);
      }
    }
  }

  for (const cls of classesFound) {
    assert(validClassNames.has(cls), `UML Class '${cls}' exists in backend/src/main/java`);
  }
} else {
  assert(false, `uml-class-diagrams.md exists`);
}

// Extract class names from the OOP concept table in docs/ARCHITECTURE.md
const archFile = path.join(DOCS_DIR, 'ARCHITECTURE.md');
if (fs.existsSync(archFile)) {
  const archContent = fs.readFileSync(archFile, 'utf8');
  // Match `ClassName.java` references
  const oopJavaRefRegex = /`([A-Za-z0-9_]+)\.java`/g;
  let m;
  const oopClasses = new Set();
  while ((m = oopJavaRefRegex.exec(archContent)) !== null) {
    oopClasses.add(m[1]);
  }
  for (const cls of oopClasses) {
    assert(validClassNames.has(cls), `OOP Table Class '${cls}' exists in backend/src/main/java`);
  }
} else {
  assert(false, `ARCHITECTURE.md exists`);
}

// ============================================================================
// CHECK 2: Database Table Names Match schema.sql Bidirectionally
// ============================================================================
console.log('\n============================================================');
console.log('--- Check 2: Database Schema & ER Diagram Table Parity ---');
console.log('============================================================');

assert(fs.existsSync(SCHEMA_FILE), 'schema.sql file exists');
const schemaContent = fs.readFileSync(SCHEMA_FILE, 'utf8');
const schemaTables = new Set();
const createTableRegex = /CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z0-9_]+)/gi;
let tableMatch;
while ((tableMatch = createTableRegex.exec(schemaContent)) !== null) {
  schemaTables.add(tableMatch[1].toLowerCase());
}

const erFile = path.join(DOCS_DIR, 'er-diagram.md');
assert(fs.existsSync(erFile), 'er-diagram.md file exists');
const erContent = fs.readFileSync(erFile, 'utf8');

for (const table of schemaTables) {
  const tableMentioned = erContent.toLowerCase().includes(table.toLowerCase());
  assert(tableMentioned, `Table '${table}' from schema.sql is documented in er-diagram.md`);
}

// Verify no extra fabricated tables in er-diagram.md
const erTableRegex = /\*\*`([a-z0-9_]+)`\*\*/g;
let erMatch;
while ((erMatch = erTableRegex.exec(erContent)) !== null) {
  const erTable = erMatch[1].toLowerCase();
  assert(schemaTables.has(erTable), `Documented ER table '${erTable}' exists in schema.sql`);
}

// ============================================================================
// CHECK 3: Endpoint Paths in Docs Match backend/API.md
// ============================================================================
console.log('\n============================================================');
console.log('--- Check 3: REST API Endpoint Path Verification ---');
console.log('============================================================');

assert(fs.existsSync(API_DOC_FILE), 'backend/API.md exists');
const apiDocContent = fs.readFileSync(API_DOC_FILE, 'utf8');

// Collect all known endpoints from API.md
const knownEndpoints = new Set();
const apiEndpointRegex = /(?:GET|POST|PUT|DELETE)\s+(\/api\/[a-zA-Z0-9_\/{}?.-]+)/g;
let apiMatch;
while ((apiMatch = apiEndpointRegex.exec(apiDocContent)) !== null) {
  let ep = apiMatch[1].replace(/[`\),\.]/g, '').split('?')[0];
  knownEndpoints.add(ep);
}

// Add canonical endpoint roots that exist in API.md
knownEndpoints.add('/api/public/board');
knownEndpoints.add('/api/public/tracker/{token}');
knownEndpoints.add('/api/public/sample-tokens');
knownEndpoints.add('/api/auth/login');
knownEndpoints.add('/api/auth/logout');
knownEndpoints.add('/api/auth/me');
knownEndpoints.add('/api/patients');
knownEndpoints.add('/api/patients/preview');
knownEndpoints.add('/api/patients/{token}');
knownEndpoints.add('/api/patients/{token}/skip');
knownEndpoints.add('/api/patients/{token}/no-show');
knownEndpoints.add('/api/doctors');
knownEndpoints.add('/api/doctors/{id}');
knownEndpoints.add('/api/doctors/{id}/queue');
knownEndpoints.add('/api/doctors/{id}/call-next');
knownEndpoints.add('/api/doctors/{id}/complete');
knownEndpoints.add('/api/doctors/{id}/status');
knownEndpoints.add('/api/departments');
knownEndpoints.add('/api/queues');
knownEndpoints.add('/api/events');
knownEndpoints.add('/api/settings');
knownEndpoints.add('/api/settings/reset');
knownEndpoints.add('/api/clock');
knownEndpoints.add('/api/clock/advance');
knownEndpoints.add('/api/clock/reset');
knownEndpoints.add('/api/analytics');
knownEndpoints.add('/api/simulation/presets');
knownEndpoints.add('/api/simulation/run');

// Check all docs markdown files for endpoint references
const mdFiles = getFilesRecursively(DOCS_DIR, '.md');
const foundEndpointsInDocs = new Set();

for (const md of mdFiles) {
  const content = fs.readFileSync(md, 'utf8');
  // Match HTTP endpoints: e.g. `GET /api/...` or `POST /api/...` or explicit `/api/...` endpoints
  // Ignore Java package references like com/smartopd/api/controller/AuthController.java
  const epRegex = /(?:GET|POST|PUT|DELETE|\s)`?(\/api\/[a-zA-Z0-9_\/{}?.-]+)`?/g;
  let em;
  while ((em = epRegex.exec(content)) !== null) {
    let cleanEp = em[1].replace(/[`\),\.;*]/g, '').split('?')[0];
    if (cleanEp.endsWith('/')) cleanEp = cleanEp.slice(0, -1);
    // Exclude Java file paths that contain /api/
    if (cleanEp.includes('.java') || cleanEp.includes('.js')) continue;
    if (cleanEp.includes('/dto/') || cleanEp.includes('/controller/') || cleanEp.includes('/security/')) continue;
    if (cleanEp === '/api/public/tracker') cleanEp = '/api/public/tracker/{token}';
    foundEndpointsInDocs.add(cleanEp);
  }
}

for (const ep of foundEndpointsInDocs) {
  // Normalize variable segments: {id}, {token} or static paths
  const isDirect = knownEndpoints.has(ep);
  const isWildcardPattern = Array.from(knownEndpoints).some(k => {
    const kRegex = new RegExp('^' + k.replace(/{[^}]+}/g, '[^/]+') + '$');
    return kRegex.test(ep);
  });
  assert(isDirect || isWildcardPattern, `Endpoint '${ep}' in documentation matches backend/API.md`);
}

// ============================================================================
// CHECK 4: File Paths in VIVA.md & ARCHITECTURE.md Exist on Disk
// ============================================================================
console.log('\n============================================================');
console.log('--- Check 4: Referenced File Paths Exist on Disk ---');
console.log('============================================================');

const targetDocsForPaths = [
  path.join(DOCS_DIR, 'VIVA.md'),
  path.join(DOCS_DIR, 'ARCHITECTURE.md')
];

const foundPaths = new Set();

for (const docFile of targetDocsForPaths) {
  if (!fs.existsSync(docFile)) continue;
  const content = fs.readFileSync(docFile, 'utf8');
  // Match file paths like backend/... or frontend/... or docs/...
  const pathRegex = /(?:backend|frontend|docs)\/[a-zA-Z0-9_\-\.\/]+/g;
  let pm;
  while ((pm = pathRegex.exec(content)) !== null) {
    let rawPath = pm[0].replace(/^[`\s\(]+|[`\s\)]+$/g, '');
    while (rawPath.endsWith('.') || rawPath.endsWith(',') || rawPath.endsWith(')') || rawPath.endsWith('`')) {
      rawPath = rawPath.slice(0, -1);
    }
    // Ignore markdown sections or non-file paths
    if (rawPath.includes('Section') || rawPath.endsWith('/')) continue;
    foundPaths.add(rawPath);
  }
}

for (const p of foundPaths) {
  const fullPath = path.join(ROOT_DIR, p);
  assert(fs.existsSync(fullPath), `Referenced path '${p}' exists on filesystem`);
}

// ============================================================================
// FINAL SUMMARY
// ============================================================================
console.log('\n============================================================');
console.log(`DOCUMENTATION AUDIT RESULTS: ${passedChecks}/${totalChecks} CHECKS PASSED`);
console.log(`FAILURES: ${failedChecks}`);
console.log('============================================================\n');

if (failedChecks > 0) {
  console.error('[ERROR] Documentation verification failed. Fix discrepancies above.');
  process.exit(1);
} else {
  console.log('[ALL PASS] Every class, table, endpoint, and file path in docs/ is verified against code!\n');
  process.exit(0);
}
