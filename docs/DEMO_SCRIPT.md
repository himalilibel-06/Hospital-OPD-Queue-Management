# SmartOPD – 10-Minute College Demo & Presentation Script

This script provides an exact, step-by-step walkthrough for demonstrating the SmartOPD system during a 10-minute college project evaluation or viva defense.

---

## 🖥️ Screen Layout & Preparation (Before Starting)

Set up your screen with two browser windows side-by-side:

- **Left Window (Staff Portals):** `http://localhost:8080/login.html`
- **Right Window (Public Displays):** `http://localhost:8080/display-board.html`

Ensure your host machine has `smartopd.jar` compiled and running (via `run.bat`).

---

## ⏱️ Timeline & Presentation Steps

### 00:00 – 01:30 | Project Introduction & Architecture
- **Action:** Open the Left Window to `http://localhost:8080/login.html`.
- **Say:**  
  > *"Good morning evaluators. Today I am presenting SmartOPD, an intelligent, explainable, and fairness-aware Hospital OPD Queue Management System built with pure Java 17, SQLite ACID persistence, and a modern responsive frontend. Standard hospital queues suffer from a painful dilemma: pure First-Come-First-Served ignores critical medical emergencies, while unconstrained strict priority starves scheduled appointments. SmartOPD solves this dilemma through mathematical anti-starvation aging, an emergency cap rule, and 100% transparent queue position explainability."*
- **Click:** Click the **Front Desk (Reception)** role card; credentials auto-populate (`reception` / `rec123`). Click **Sign In**.
- **Audience Sees:** The Reception Dashboard appears, showing live telemetry cards (Total Patients, Waiting, In Consultation, Avg Wait) and doctor consultation queues.

---

### 01:30 – 03:00 | Emergency Registration & Instant Override
- **Action:** In the left sidebar, click **"Register Patient"** (`register-patient.html`).
- **Say:**  
  > *"Let's register an incoming patient. Notice that as I select Department, Doctor, Category, and Severity, SmartOPD provides an instant, 300-millisecond debounced live preview of their predicted queue position and wait time before any record is written to the database."*
- **Click & Fill:**
  1. Department: **Cardiology**
  2. Doctor: **Dr. Rohith Sharma (OPD Room 108)**
  3. Patient Name: `Vikramaditya Rao`
  4. Phone: `9876543210` | Age: `58` | Gender: `Male`
  5. Category: **Emergency**
  6. Severity: **Level 5 (Critical / Resuscitation)**
- **Say:**  
  > *"Notice that for a Level 5 emergency, the algorithm assigns an immediate override score of 10,000 points. Let's submit the registration."*
- **Click:** Click **"Register Patient"**.
- **Audience Sees:** The page redirects immediately to `token-slip.html` with generated token `CARD-E-004` (or next sequence). The slip displays Position #1, an estimated start time of "Next Call (Immediate)", a plain-English explanation, and a thermal-printable ticket with a dynamic QR code.

---

### 03:00 – 04:30 | Public Waiting Board & Mobile Phone Tracker
- **Action:** Direct the audience's attention to the **Right Window** (`display-board.html`).
- **Say:**  
  > *"Simultaneously, the public 16:9 waiting room display board updates automatically via visibility-aware polling. Notice the strict Privacy by Design rule: zero patient names, mobile numbers, or clinical severity numbers appear on this screen. Only anonymous tokens and room destinations are shown."*
- **Action:** Open a new tab in the right window to `http://localhost:8080/patient-tracker.html?guest=true` (or open on your physical phone connected to the same Wi-Fi using the LAN URL printed by `run.bat`).
- **Click:** Enter the newly generated emergency token (e.g. `CARD-E-004`) and click **Track Token**.
- **Audience Sees:** The mobile tracker greets the patient with their first name only (`"Hello, Vikramaditya"`), shows Position #1, estimated wait, and a chronological event timeline.

---

### 04:30 – 06:00 | Doctor Consultation Console & Rolling Average Smoothing
- **Action:** In the Left Window, log out and sign in as **OPD Doctor** (`doctor` / `doc123`).
- **Audience Sees:** `doctor-console.html` opens, showing Dr. Rohith Sharma's consultation room (OPD Room 108), current active consultation, and the next 5 patients in line.
- **Say:**  
  > *"In the Doctor Console, the doctor sees their active consultation and queue order. When the doctor clicks 'Call Next', the server acquires our fair single-writer lock, updates the patient status to IN_CONSULTATION, and persists a CALLED audit event."*
- **Click:** Click **"Call Next Patient"** (or press keyboard shortcut `N`).
- **Audience Sees:** The emergency token moves to the "Now In Room" consultation card, the active timer starts counting upward from `00:00`, and the public display board in the right window rings an audio chime.
- **Say:**  
  > *"When consultation completes, SmartOPD measures the actual consultation duration and updates the doctor's running average duration using exponential moving average smoothing: 70% previous average plus 30% actual duration, clamped between 3 and 60 minutes."*
- **Click:** Click **"Complete Consultation"** (or press keyboard shortcut `C`).

---

### 06:00 – 07:30 | Demo Clock +50m & Anti-Starvation Fairness Boost
- **Action:** Log back in as `reception` and navigate to **"Live Queue Manager"** (`queue-manager.html`).
- **Say:**  
  > *"Now let's observe how SmartOPD prevents routine scheduled patients from starving. Notice scheduled patient CARD-S-010 waiting behind routine cases with a wait time of 0 minutes. Under our algorithm, patients waiting longer than 45 minutes receive an automatic anti-starvation Fairness Boost of +150 bonus points."*
- **Click:** In the Demo Clock controls bar, click **"+50 Mins"**.
- **Say:**  
  > *"Notice what just happened in real time! Advancing the simulated clock by 50 minutes pushed CARD-S-010 past their 45-minute threshold. The system immediately awarded +150 bonus points, re-sorted the queue, logged a FAIRNESS_BOOST event, and advanced CARD-S-010 up the queue."*
- **Audience Sees:** The queue column updates instantly. The Activity Audit Feed at the bottom displays:  
  `"Fairness boost applied to CARD-S-010 (+150 pts): waited 50 mins (exceeded threshold)."`

---

### 07:30 – 08:30 | Discrete-Event Simulation & Policy Comparison
- **Action:** In the left sidebar, click **"OPD Analytics & Simulation"** (`analytics.html`).
- **Say:**  
  > *"To validate that our algorithm works under severe clinical pressure, SmartOPD embeds a pure Java discrete-event simulation engine capable of running 500 patient encounters in under 50 milliseconds. Let's run the 'Emergency Surge' stress test comparing pure FCFS, Strict Priority, and SmartOPD."*
- **Click:** Select the **Emergency Surge** preset (126 patients, 4 doctors, 10m consult, 105% offered load). Click **"Run Simulation"**.
- **Audience Sees:** The grouped SVG bar chart and comparative telemetry table render instantly:
  - FCFS Emergency Wait: ~7 minutes.
  - SmartOPD Emergency Wait: ~1.2 minutes (83% reduction).
  - Strict Priority Starvation Count: 1–2 scheduled patients starved (>90m wait).
  - SmartOPD Starvation Count: **0 starved patients** (starvation completely eliminated).
- **Say:**  
  > *"Notice the automated insight generated by the engine: SmartOPD matches the emergency response of Strict Priority while eliminating scheduled patient starvation entirely."*

---

### 08:30 – 09:15 | Server Restart Persistence Demonstration
- **Say:**  
  > *"Unlike toy college projects that store queue state in volatile browser memory, SmartOPD uses full write-through SQLite ACID persistence. Let's prove it by killing the server."*
- **Action:** Switch to the server terminal, press `Ctrl+C` to terminate the running Java process. Refresh `http://localhost:8080/` to show the browser is disconnected.
- **Action:** Restart the server with `run.bat` (or `java -jar backend/target/smartopd.jar`).
- **Action:** Refresh the Left Window (`reception-dashboard.html`).
- **Audience Sees:** All registered patients, advanced clock offsets, doctor consultation states, and token numbers remain 100% intact from `backend/data/smartopd.db`.

---

### 09:15 – 10:00 | 60-Second Code Tour (5 Key Files)
- **Action:** Open your IDE or editor to showcase these 5 files:
  1. **`SmartOpdStrategy.java`:** Show the dynamic scoring formula, the $+150.0$ boost, and polymorphic dispatch (`patient.urgencyBonus(now)`).
  2. **`EmergencyCapSelector.java`:** Show the static emergency cap logic shared between `DoctorQueue` and `Simulator`.
  3. **`QueueService.java`:** Show the fair `ReentrantLock writeLock`, transactional write-through caching, and the fail-safe `rebuildDoctorQueueFromDb()` recovery hook.
  4. **`PatientFactory.java`:** Show the Creational Factory pattern enforcing triage rules before instantiating concrete subclasses.
  5. **`Simulator.java`:** Show the discrete-event queue loop and the Mulberry32 PRNG achieving bit-exact parity with JavaScript golden suites.

---

## 🆘 "If Something Goes Wrong" Troubleshooting Guide

| Issue | Root Cause | Immediate Fix |
| :--- | :--- | :--- |
| **Port 8080 Already in Use** | A previous instance of the server was not shut down cleanly. | Run in PowerShell: `Get-NetTCPConnection -LocalPort 8080 -State Listen \| Select-Object -ExpandProperty OwningProcess \| ForEach-Object { Stop-Process -Id $_ -Force }` |
| **Server Won't Start (`smartopd.jar` missing)** | The build step was skipped or failed. | Execute in root folder: `run.bat -rebuild` |
| **Browser Displays Red Connection Banner** | The backend server process is stopped or still initializing. | Check terminal for `"Server is up and ready"`. Ensure `http://localhost:8080/api/public/board` returns HTTP 200. |
| **Queue State Corrupted or Inconsistent During Demo** | Manual testing left queues in an unrepresentative state. | Sign in as `reception`, open the Dashboard, and click the **"Reset Demo State"** button. This cleanly restores the original 5 departments, 6 doctors, and 12 baseline patients. |
| **Mobile Phone Cannot Connect to LAN URL** | Windows Firewall is blocking inbound connections on port 8080. | In PowerShell as Administrator run: `New-NetFirewallRule -DisplayName "SmartOPD 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow` |
