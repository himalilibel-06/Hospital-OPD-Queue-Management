# SmartOPD – Explainable, Fairness-Aware Hospital OPD Queue Management System

SmartOPD is an intelligent, explainable, and fairness-aware Outpatient Department (OPD) queue management system developed as a college Object-Oriented Programming (OOP) capstone project. Built using pure Java 17, SQLite ACID persistence, and a modern responsive frontend, SmartOPD balances urgent clinical triage protocols (Emergency Overrides) with mathematical anti-starvation aging (Fairness Boosts and an Emergency Cap rule) so that critical emergency patients are prioritized immediately while routine scheduled appointments are guaranteed predictable consultation times.

---

## 📚 Documentation

Comprehensive architectural, structural, and evaluation documentation is maintained in the [`docs/`](docs/) directory:

- **[`docs/uml-class-diagrams.md`](docs/uml-class-diagrams.md)** – Six modular Mermaid class diagrams covering Domain Model, Scoring Strategies, Queue Engine, Persistence/DAOs, Service/API, and Simulation Engine.
- **[`docs/sequence-diagrams.md`](docs/sequence-diagrams.md)** – End-to-end Mermaid sequence diagrams tracing Authentication, Patient Registration, Doctor Consultation, Public Tracker, Fairness Boost Detection, and Transaction Rollback.
- **[`docs/er-diagram.md`](docs/er-diagram.md)** – Entity-Relationship diagram for all 8 SQLite database tables with exact column types, primary/foreign key constraints, and 3NF normalization analysis.
- **[`docs/flowcharts.md`](docs/flowcharts.md)** – Algorithmic flowcharts detailing the end-to-end patient journey, the emergency cap selection rule (`EmergencyCapSelector`), and the fairness boost aging mechanism.
- **[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)** – Complete system architecture including multi-tier layer breakdown, component diagram, concurrency/consistency model, security framework, and a comprehensive OOP concept mapping table.
- **[`docs/VIVA.md`](docs/VIVA.md)** – 30 defense viva examination questions and answers across 5 categories with source code references and the raw 30-seed aggregate simulation benchmark table.
- **[`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md)** – Timed 10-minute presentation script with window layouts, exact clicks, spoken commentary, expected visual output, a 60-second code tour, and troubleshooting guides.

---

## ⚡ Quick Start

### 1. Windows (One-Click)
Double-click `run.bat` in the repository root (or execute in PowerShell / Command Prompt):
```cmd
run.bat
```
*`run.bat` checks that Java 17 and Maven are installed, automatically compiles `smartopd.jar` (skipping tests for speed) if missing or modified, starts the server from `backend/` (persisting data in `backend/data/smartopd.db`), polls for health, opens your default browser to `http://localhost:8080/login.html`, and prints the LAN URL for mobile phone access.*

To force a complete rebuild of the packaged JAR:
```cmd
run.bat -rebuild
```

### 2. Any OS (macOS / Linux)
Make `run.sh` executable and run it:
```bash
chmod +x run.sh
./run.sh
```

### 3. Running from Standalone Executable JAR
```bash
java -jar backend/target/smartopd.jar
```
*(The JAR is completely self-contained with embedded Tomcat and bundled static frontend resources; it can be copied and run from any directory).*

### 4. Developer Mode (Live Frontend Edits)
```bash
cd backend
mvn spring-boot:run
```
*(Live frontend edits in `frontend/` reflect immediately in your browser on page refresh).*

---

## 🛑 Stopping the Server & Port 8080 Troubleshooting

- **To Stop the Server:** Press `Ctrl+C` in the terminal or close the "SmartOPD Server" console window.
- **Port 8080 Already in Use:** If another process is holding port 8080, run the following PowerShell command to locate and terminate it:
  ```powershell
  # Find PID listening on port 8080
  Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force }
  ```

---

## 🔑 Demo Login Accounts & Portals

| Role | Authentication / Access | Target Dashboard | Purpose |
| :--- | :--- | :--- | :--- |
| **First-Run Setup** | Created on first launch via `/login.html` | `/login.html` | Initializes primary Front Desk staff account on clean databases |
| **Front Desk** | Staff username & password | `reception-dashboard.html` | Patient intake, triage registration, queue monitoring, doctor management |
| **OPD Doctor** | Doctor account (created by Front Desk) | `doctor-console.html` | Live consultation room queue & patient calling controls |
| **Patient** | *(No login required – Guest access)* | `patient-tracker.html` | Self-service queue tracking & live estimated wait times |

### Core Application URLs:
- **Authentication Portal:** `http://localhost:8080/login.html`
- **Waiting Room Display Board (16:9 TV):** `http://localhost:8080/display-board.html`
- **Patient Mobile Tracker (Self-Service):** `http://localhost:8080/patient-tracker.html?guest=true` (or with token: `?token=CARD-E-002`)

### 📱 Opening the Tracker on a Phone (Same Wi-Fi)
1. Ensure your host computer and phone are connected to the same local Wi-Fi network.
2. `run.bat` prints your host's local IPv4 address at startup (e.g. `192.168.1.45`).
3. On your phone's mobile browser, open:
   ```
   http://<YOUR_HOST_IP>:8080/patient-tracker.html?guest=true
   ```
   Patients can enter any active token (e.g. `CARD-E-002`, `GEN-W-004`) to monitor their queue position, wait times, and timeline updates live.

---

## 🧪 Comprehensive Automated Test Suites (All 4 Green)

SmartOPD maintains four automated test suites verifying correctness from low-level algorithms up to HTTP contracts:

| Test Suite | Execution Command | Checks | Purpose & Coverage |
| :--- | :--- | :---: | :--- |
| **1. Backend JUnit 5** | `cd backend && mvn test` | **84 Tests** | Verifies pure Java OOP core engine, SQLite ACID persistence, multi-threaded SQLite concurrency, PBKDF2 password hashing, RBAC security filters, 9 simulation golden runs, and 30-seed aggregate parity. |
| **2. Algorithm Unit Tests** | `node frontend/js/tests/run-tests.js` | **48 Checks** | Verifies Mulberry32 PRNG determinism, emergency override ordering, fairness aging boost (+150 pts), emergency cap rule (2 consecutive), and preset load boundaries (Quiet, Busy, Surge). |
| **3. Static Architecture Check** | `node frontend/js/tests/static-check.js` | **170 Checks** | Audits all 10 HTML pages ensuring zero legacy mock leakage (`queue-mock.js`, `mock-data.js`, `simulator.js`), script order, valid charset, and CSS design tokens. |
| **4. REST API Contract Test** | `node frontend/js/tests/api-contract-test.js` | **141 Checks** | Runs 15 end-to-end integration steps against running backend: authentication, RBAC authorization, public privacy (zero PII), patient preview/creation, doctor console, clock manipulation, and simulation endpoints. |

---

## ⚠️ Known Limits

1. **SQLite Single-Writer Concurrency:** SQLite allows concurrent readers via WAL mode (`PRAGMA journal_mode=WAL`), but serializes write locks. `QueueService` employs a fair `ReentrantLock` mutex to serialize all mutating operations into ACID transactions, guaranteeing zero lock contention.
2. **In-Memory Session Store:** User authentication sessions are held in server memory; restarting the application logs all active users out.
3. **No HTTPS / Plain HTTP:** Designed for local hospital LAN deployments and educational demonstration; TLS certificates should be terminated at a reverse proxy (e.g., NGINX) in production.
4. **Fictional Seed Data:** Pre-seeded departments, doctors, patient names, and phone numbers are synthetic and for demonstration only.
5. **Synthetic Simulation Cohort:** The discrete-event policy simulator generates synthetic cohorts via Mulberry32 pseudo-random sampling across configured triage mixes.
6. **Starvation Definition in Simulator:** In the policy simulator, "starvation" is defined as a scheduled appointment patient whose total waiting time exceeds twice the scheduled fairness threshold ($2 \times 45 = 90$ minutes).

---

## REST API Integration Status (100% Full-Stack Migration)

The entire frontend (all 10 application pages) has been completely migrated from the local in-memory simulation to the production Spring Boot + SQLite REST API. All pages make real HTTP network requests with Bearer token authentication, visibility-aware polling, loading/error states, double-submit prevention, and zero hardcoded doctor identifiers.

| Page | Type | REST API Endpoints Used | Migration Notes |
| :--- | :--- | :--- | :--- |
| **patient-tracker.html** | Public (No login) | `GET /api/public/tracker/{token}`<br>`GET /api/public/sample-tokens` | 5s visibility-aware polling; rate-limited (30 req/min per IP); zero PII and severity numbers removed from public view; remembers last tracked token in localStorage. |
| **display-board.html** | Public (No login) | `GET /api/public/board` | 3s polling; WebAudio chime on token call change; full-screen 16:9 layout; zero PII. |
| **reception-dashboard.html** | Protected (Receptionist) | `GET /api/queues`<br>`GET /api/analytics`<br>`POST /api/patients` | 4 KPI summary cards from live analytics; Doctor overview grid; Emergency Quick Add modal; Top 5 queue modal; Real-time queue insights charts. |
| **register-patient.html** | Protected (Receptionist) | `GET /api/departments`<br>`GET /api/doctors`<br>`POST /api/patients/preview`<br>`POST /api/patients` | Dynamic department and doctor dropdowns; 300 ms debounced live preview with out-of-order response discard; creates token and redirects to `token-slip.html?token=`. |
| **token-slip.html** | Receptionist / Guest | `GET /api/patients/{token}`<br>`GET /api/patients?search=...` | Single slip mode with dynamic QR code using `location.origin`; Directory list mode with debounced search and category filtering. |
| **queue-manager.html** | Protected (Receptionist) | `GET /api/queues`<br>`GET /api/clock`<br>`POST /api/clock/advance`<br>`POST /api/clock/reset`<br>`GET /api/events`<br>`POST /api/doctors/{id}/call-next`<br>`POST /api/doctors/{id}/complete`<br>`POST /api/patients/{token}/skip`<br>`POST /api/patients/{token}/no-show` | 5s polling with reconnecting banner; simulated demo clock controls (+10m, +30m, +60m, Reset); real-time doctor queue columns; instant refresh on actions. |
| **doctor-console.html** | Protected (Doctor) | `GET /api/doctors/{id}/queue`<br>`POST /api/doctors/{id}/call-next`<br>`POST /api/doctors/{id}/complete`<br>`POST /api/patients/{token}/skip`<br>`POST /api/patients/{token}/no-show`<br>`PUT /api/doctors/{id}/status`<br>`GET /api/events?doctorId=...`<br>`GET /api/analytics` | Dynamic doctor identity from session; real-time consult elapsed timer; live next 5 patients in line; keyboard shortcuts (N / C); instant room audit log. |
| **manage-doctors.html** | Protected (Receptionist) | `GET /api/doctors`<br>`POST /api/doctors`<br>`PUT /api/doctors/{id}`<br>`DELETE /api/doctors/{id}`<br>`GET /api/departments`<br>`POST /api/departments`<br>`GET /api/queues` | Dynamic doctor roster & department tabs; live waiting count badge per doctor; safe deletion guard with HTTP 409 Conflict handling when patients wait. |
| **settings.html** | Protected (Receptionist) | `GET /api/settings`<br>`PUT /api/settings`<br>`POST /api/settings/reset` | Live slider controls for scoring weights, fairness thresholds, emergency cap, and buffer slots; interactive re-ranking preview; unsaved changes warning. |
| **analytics.html** | Protected (Receptionist) | `GET /api/analytics`<br>`GET /api/simulation/presets`<br>`POST /api/simulation/run` | Live hospital telemetry cards, patients-per-hour line chart, category intake donut, doctor wait bar chart, severity distribution, and doctor utilisation table; executes discrete-event queue simulation via pure Java backend comparing FCFS, Strict Priority, and SmartOPD. |

---

## 📁 Project Folder Structure

```
Hospital-OPD-Queue-Management/
├── .gitignore                      # Git ignore rules for IDEs, DBs, logs & build artifacts
├── README.md                       # Project documentation, architecture & demo script
├── backend/                        # Java 17 Spring Boot + SQLite backend with pure Java simulation engine
│   ├── README.md                   # Comprehensive viva documentation and OOP concept mapping
│   ├── API.md                      # REST API endpoint reference and JSON contracts
│   └── src/
│       ├── main/java/com/smartopd/
│       │   ├── simulation/         # Pure Java Discrete-Event Simulation Engine (bit-exact parity)
│       │   ├── queue/              # Live queue engine & reusable EmergencyCapSelector
│       │   ├── scoring/            # Strategy Pattern (FcfsStrategy, StrictPriorityStrategy, SmartOpdStrategy)
│       │   └── api/                # Spring Boot REST controllers, DTOs & interceptors
│       └── test/java/com/smartopd/ # 84 JUnit 5 integration, persistence & parity tests
└── frontend/                       # Plain HTML5, CSS3 & Vanilla JavaScript
    ├── index.html                  # Root landing page (redirects to login.html)
    ├── login.html                  # Split-screen role-based authentication portal
    ├── reception-dashboard.html    # Full Front Desk: 4 KPI cards, doctor grid & top 5 modal
    ├── register-patient.html       # 2-column registration form with real-time live preview & triage
    ├── queue-manager.html          # Live Queue Manager: Demo Clock, doctor queue columns & event audit log
    ├── manage-doctors.html         # Front Desk doctor roster & department management
    ├── settings.html               # Hospital scoring weights, starvation & buffer slot configuration
    ├── analytics.html              # OPD Queue simulation & policy comparative analytics (calls backend API)
    ├── doctor-console.html         # Doctor OPD Room Console: Active timer, Up Next, keyboard shortcuts
    ├── token-slip.html             # Printable thermal ticket slip (@media print) with real dynamic QR code
    ├── patient-tracker.html        # Mobile-First Live Patient Queue Tracker (guest & authenticated)
    ├── display-board.html          # 16:9 TV Waiting-Room Display Board (WebAudio chime, zero PII)
    ├── css/
    │   ├── variables.css           # Centralized design tokens (colors, typography, grid, radii)
    │   ├── base.css                # CSS reset, typography, buttons, inputs, cards, tables, badges
    │   └── layout.css              # Reusable app shell (dark navy sidebar + responsive top bar)
    ├── js/
    │   ├── app.js                  # Shared helpers: role guards, toast alerts, live clock, nav
    │   ├── api-client.js           # REST API client connecting frontend to Spring Boot backend
    │   ├── api-adapters.js         # Adapters mapping backend API response DTOs to UI view models
    │   ├── mock-data.js            # Initial dataset: 5 departments, 6 doctors, 12 Indian patients
    │   ├── queue-mock.js           # Reference standalone queue simulation
    │   ├── scoring.js              # Reference scoring rules (retained as executable specification)
    │   ├── simulator.js            # Reference JS discrete-event simulator (retained as executable specification)
    │   ├── charts.js               # Reusable inline SVG charting library (bar, grouped bar, line, donut, zero CDNs)
    │   ├── qr.js                   # Standalone pure JS QR code generator (Model 2, Byte Mode, Zero CDNs)
    │   ├── auth.js                 # Authentication, role card selector & sessionStorage session
    │   └── tests/
    │       ├── queue-tests.html    # Browser-based automated test runner with PASS/FAIL UI
    │       ├── run-tests.js        # Node.js automated test script for CI / terminal execution
    │       ├── static-check.js     # Static decoupling and architecture boundary verification (170 checks)
    │       └── api-contract-test.js # End-to-end REST API contract verification (141 checks)
    └── assets/                     # SVG icons and visual assets
```

---

## ⚙️ Mock Queue Engine (`js/queue-mock.js`)

> **Note for Students & Evaluators:**  
> `js/queue-mock.js` is a **temporary frontend simulation** that will be replaced by the Java Spring Boot + SQLite backend. It persists state in `localStorage` (`smartopd_state`) and implements the exact mathematical logic of the future Java backend:

- **Configurable Algorithmic Weights (`SETTINGS` object):**
  - **Base Severity Weight:** `100` points per triage level (Levels 1 to 5).
  - **Waiting Time Aging:** `+3` points per minute waited in lobby (weight 3 keeps clinical severity dominant: ~33 mins waiting to climb 1 severity level).
  - **Anti-Starvation Fairness Boost:** `+150` bonus points once waiting threshold is exceeded:
    - `45 mins` threshold for Scheduled appointments.
    - `45 mins` threshold for Walk-in patients.
  - **Emergency Override:** Level 5 triage bypasses all routine scoring with a score of `10,000` to guarantee position #1.
  - **Emergency Cap Rule (`MAX_CONSECUTIVE_EMERGENCIES = 2`):** Prevents routine emergency starvation by forcing `callNext()` to prioritize a waiting fairness-boosted patient once 2 emergencies are served consecutively (unless the incoming patient is Level 5 critical).
- **Position-Change Detection & Audit Trail:**
  - Automatically snapshots queue order before and after operations. Logs `MOVED_UP`, `MOVED_DOWN`, `CALLED`, `COMPLETED`, `NO_SHOW`, `SKIPPED`, `FAIRNESS_BOOST`, and `EMERGENCY_OVERRIDE` with plain-English reasons into `eventLog`.
- **Public & Patient Privacy Helpers:**
  - `getPatientByToken(tokenNumber)`: Case-insensitive, whitespace-trimmed token lookup.
  - `getPublicBoardData()`: Exposes only doctor names, departments, rooms, status, now-serving tokens, and next 5 tokens with estimated times (STRICT ZERO PII RULE: never exposes patient names, phone numbers, age, or medical notes).
  - `getPublicQueueSummary(tokenNumber)`: Returns only privacy-safe fields for mobile tracking (`token`, `firstName` greeting only, `doctor`, `room`, `position`, `patientsAhead`, `estimatedWaitMinutes`, `explanation`).
  - `getTokenTimeline(tokenNumber)`: Chronological oldest-first event timeline of the patient's OPD journey.
- **Simulated Demo Clock:**
  - `SmartOPDQueue.getNow()`: returns real time plus stored offset.
  - `SmartOPDQueue.advanceClock(minutes)`: advances time to demonstrate fairness aging live during college demos.
  - `SmartOPDQueue.resetClock()`: restores real time.

---

## 🎬 System Walkthrough & Demo Guide

On first start open the app and create the Front Desk account on the setup page, then add departments and doctors on the Doctors page, then create doctor logins on the Staff page.

Follow this sequence to demonstrate the entire SmartOPD ecosystem:

1. **Step 1: Patient Registration & Triage**
   - Open `frontend/register-patient.html` as Front Desk.
   - Register a patient for an assigned doctor with **Emergency Category & Severity 5**.
   - Notice the live preview immediately indicates **Position #1 (Emergency Override)** with score `10,000`.
   - On submission, view the ticket slip on `token-slip.html` and notice the **real dynamic QR code** generated via pure JS (`js/qr.js`).
2. **Step 2: Mobile Live Patient Tracker**
   - Click **"Track this token"** on `token-slip.html` (or open `frontend/patient-tracker.html?token=...` in a narrow phone-size window).
   - Observe the mobile-first status card: first name greeting, large token number, visual **queue lane track** showing patients ahead, and plain-English explainability notes.
3. **Step 3: TV Waiting-Room Display Board**
   - Open `frontend/display-board.html` in a new tab or second monitor (16:9 layout).
   - View the doctor grid with high-contrast navy styling, large "NOW SERVING" displays, the upcoming tokens row (with red emergency borders), and scrolling public announcement ticker.
   - Click the **"Sound: Muted"** button to unmute the WebAudio chime.
4. **Step 4: Doctor Console & Real-Time Sync**
   - Sign into `doctor-console.html` with the doctor account created on the Staff page.
   - Press **N** (or click **"Call Next Patient"**).
   - Watch both the **Patient Tracker** (phone window) and the **TV Display Board** update automatically within 3–5 seconds:
     - The TV board plays a crisp electronic two-tone chime and pulses the new token in cyan glow!
     - The patient tracker lane moves the patient to the front and displays the green banner: *"Please proceed to Room 108 immediately!"*
5. **Step 5: Fast-Forward Time for Fairness Demonstration**
   - Open `frontend/queue-manager.html`.
   - Advance time past 45 minutes on the Demo Clock toolbar.
   - Watch routine waiting patients automatically receive **Violet Fairness Boosts (+150 pts)**.
   - Inspect the live event log documenting every `FAIRNESS_BOOST` and `MOVED_UP` transition.

---

## 📊 OPD Queue Discrete-Event Simulator & Policy Analytics

SmartOPD features a pure JavaScript discrete-event simulation engine (`js/simulator.js`) and inline SVG charting library (`js/charts.js`) accessible to Front Desk staff at `analytics.html`.

### How the Simulation Engine Works:
- **Synthetic Patient Cohort Generation:** Dynamically generates between 50 and 500 patient visits with realistic outpatient distributions (emergency ratio, scheduled appointments, routine walk-ins) arriving across a configurable time window (60–480 min). Consult durations vary by +/- 30% around the hospital average using a seeded PRNG.
- **Deterministic Mulberry32 PRNG:** Uses a seeded pseudo-random number generator so identical seeds yield 100% reproducible results for academic evaluation.
- **Multi-Policy Comparative Benchmarking:** Evaluates the **exact same synthetic cohort** across 3 queue disciplines:
  1. **FCFS (First-Come, First-Served):** Baseline FIFO ordering without clinical triage.
  2. **Strict Priority:** Severe cases served first with zero anti-starvation protection.
  3. **SmartOPD (Fairness-Aware):** Blends clinical severity triage, appointment urgency, waiting aging (+3 pts/min), fairness boost (+150 pts), and an emergency cap (max 2 consecutive emergencies).
- **Core Performance Metrics:** Computes overall average wait, emergency wait, high-severity wait, scheduled wait, 90th percentile wait, max scheduled wait, scheduled starvation count (>60m wait), and doctor idle percentage.
- **Automated Explainable Insights:** Generates plain-English comparative conclusions calculated directly from empirical simulation numbers.
- **High-Throughput Performance:** Simulates 500 patients across multiple doctors in under 50 milliseconds.

---

## 🧪 Automated Test Suite

We provide both a terminal-based Node.js test runner and a browser-based visual test runner:

### Option 1: Terminal Execution
```bash
node frontend/js/tests/run-tests.js
```

### Option 2: Browser Test Runner
Open `frontend/js/tests/queue-tests.html` in your browser.

**Verified Test Cases (46 / 46 Checks Passed • 100%):**
1. ✅ **Emergency Override Ordering:** Level 5 critical case immediately takes Position #1 over routine queue.
2. ✅ **Emergency Explanation:** Plain-English reason confirms clinical bypass.
3. ✅ **Fairness Boost Activation:** Advancing clock +50 mins awards +150 pts and logs `FAIRNESS_BOOST` for scheduled patients.
4. ✅ **Fairness Cap Rule:** Enforces `MAX_CONSECUTIVE_EMERGENCIES = 2` and prioritizes starving routine patients.
5. ✅ **No-Show Handling:** `markNoShow()` frees the room slot and shifts subsequent patients up.
6. ✅ **FIFO Deterministic Ordering:** Identical score ties are deterministically broken by earlier registration time.
7. ✅ **Token Lookup:** `getPatientByToken()` case-insensitive and whitespace trimming support.
8. ✅ **Display Board Privacy:** `getPublicBoardData()` strictly contains ZERO patient names, phone numbers, ages, or medical complaints.
9. ✅ **Patient Tracker Privacy:** `getPublicQueueSummary()` exposes only privacy-safe fields and first name greeting.
10. ✅ **Journey Timeline:** `getTokenTimeline()` guarantees strict chronological ordering (oldest first).
11. ✅ **Settings Validation:** Rejects negative weights and out-of-range thresholds with human-readable errors.
12. ✅ **Dynamic Scoring Re-Ranking:** Inverts live queue ordering upon policy weight updates.
13. ✅ **Doctor Deletion Guard:** Blocks deletion when active waiting queue exists.
14. ✅ **Deterministic Simulation:** Mulberry32 PRNG produces bit-exact identical runs for the same seed.
15. ✅ **Patient Conservation:** All three policies serve 100% of generated patients exactly once.
16. ✅ **Triage Advantage:** Busy Day SmartOPD emergency wait is strictly less than or equal to FCFS.
17. ✅ **Starvation Safeguard:** Emergency Surge SmartOPD starvation count is less than or equal to Strict Priority.
18. ✅ **Insight Numerical Accuracy:** Generated plain-English sentences contain numbers directly derived from simulation metrics.
19. ✅ **Simulation Performance:** Full 500-patient discrete-event simulation completes in under 2 seconds.
