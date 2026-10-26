-- SmartOPD Relational Database Schema
-- Technology: SQLite 3.4x with ACID transactions, WAL mode, and foreign keys enabled

-- 1. OPD Clinical Departments
CREATE TABLE IF NOT EXISTS department (
    code TEXT PRIMARY KEY,
    name TEXT UNIQUE NOT NULL
);

-- 2. Hospital Doctors
CREATE TABLE IF NOT EXISTS doctor (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    department_code TEXT NOT NULL,
    room TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    avg_consult_minutes INTEGER NOT NULL,
    emergency_buffer_slots INTEGER NOT NULL,
    status TEXT NOT NULL,
    age INTEGER NOT NULL,
    gender TEXT NOT NULL,
    phone TEXT NOT NULL,
    FOREIGN KEY (department_code) REFERENCES department(code) ON DELETE RESTRICT
);

-- 3. OPD Patients with Clinical Triage Severity and Token
CREATE TABLE IF NOT EXISTS patient (
    id TEXT PRIMARY KEY,
    token TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    age INTEGER NOT NULL,
    gender TEXT NOT NULL,
    phone TEXT NOT NULL,
    category TEXT NOT NULL,
    severity INTEGER NOT NULL CHECK (severity >= 1 AND severity <= 5),
    doctor_id TEXT NOT NULL,
    registered_at TEXT NOT NULL,
    appointment_time TEXT,
    chief_complaint TEXT,
    status TEXT NOT NULL,
    skipped INTEGER NOT NULL DEFAULT 0 CHECK (skipped IN (0, 1)),
    consult_start TEXT,
    consult_end TEXT,
    FOREIGN KEY (doctor_id) REFERENCES doctor(id) ON DELETE RESTRICT
);

-- 4. Atomic Token Sequences per Department and Category
CREATE TABLE IF NOT EXISTS token_sequence (
    department_code TEXT NOT NULL,
    category_code TEXT NOT NULL,
    last_value INTEGER NOT NULL,
    PRIMARY KEY (department_code, category_code)
);

-- 5. Audit Log of Queue State Changes and Clinical Transitions
CREATE TABLE IF NOT EXISTS queue_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    token TEXT NOT NULL,
    doctor_id TEXT,
    type TEXT NOT NULL,
    reason TEXT
);

-- 6. Centralized Hospital Triage Scoring Configurations
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 7. Application Simulation State (Simulated clock offsets & emergency counters)
CREATE TABLE IF NOT EXISTS app_state (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 8. User Accounts (Only Front Desk Receptionists and Doctors authenticate)
CREATE TABLE IF NOT EXISTS user_account (
    username TEXT PRIMARY KEY,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('RECEPTIONIST', 'DOCTOR')),
    display_name TEXT NOT NULL,
    doctor_id TEXT,
    FOREIGN KEY (doctor_id) REFERENCES doctor(id) ON DELETE SET NULL
);

-- Performance Optimization Indexes
CREATE INDEX IF NOT EXISTS idx_patient_doc_status ON patient(doctor_id, status);
CREATE INDEX IF NOT EXISTS idx_event_token ON queue_event(token);
CREATE INDEX IF NOT EXISTS idx_event_doc ON queue_event(doctor_id);
