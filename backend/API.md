# SmartOPD – REST API Reference & Integration Contract

Comprehensive API reference specification for the SmartOPD Spring Boot application. All endpoints, request bodies, and sample responses below are **real JSON payloads captured from the live running server** on `http://localhost:8080`.

---

## 🔐 Authentication & Bearer Token Flow

SmartOPD uses stateless, in-memory **256-bit cryptographically secure bearer tokens** (`SecureRandom` + Hex encoding) with an **8-hour session validity**.

### 1. Login Request
- **Endpoint:** `POST /api/auth/login`
- **Access:** Public (No token required)
- **Request Body:**
```json
{
  "username": "reception",
  "password": "rec123"
}
```
- **Live Response (`200 OK`):**
```json
{
  "token": "469389ea12b5e52c8a14f484803716a13f7c461fe9e0c51d66fe48a97750c182",
  "username": "reception",
  "role": "RECEPTIONIST",
  "displayName": "Reception Desk",
  "doctorId": null,
  "expiresInSeconds": 28800
}
```

### 2. Authorization Header Format
For all protected endpoints, include the token in the HTTP `Authorization` request header:
```http
Authorization: Bearer 469389ea12b5e52c8a14f484803716a13f7c461fe9e0c51d66fe48a97750c182
```

### 3. Check Current User (`GET /api/auth/me`)
- **Required Role:** Any authenticated user (`RECEPTIONIST` or `DOCTOR`)
- **Live Response (`200 OK`):**
```json
{
  "username": "doctor",
  "role": "DOCTOR",
  "displayName": "Dr. Rohith Sharma",
  "doctorId": "DOC-102"
}
```

### 4. Logout Session (`POST /api/auth/logout`)
- **Required Role:** Any authenticated user
- **Live Response (`200 OK`):**
```json
{
  "status": 200,
  "message": "Logged out successfully."
}
```

### 5. Change Own Password (`POST /api/auth/change-password`)
- **Required Role:** Any authenticated user (`RECEPTIONIST` or `DOCTOR`)
- **Header:** `Authorization: Bearer <token>`
- **Request Body:**
```json
{
  "currentPassword": "OldPassword123",
  "newPassword": "NewPassword456"
}
```
- **Validation Rules:**
  - `currentPassword`: Verified against stored PBKDF2 hash. Wrong password returns `400 Bad Request` and counts towards brute-force lockout rules.
  - `newPassword`: Must be at least 8 characters long and contain both a letter and a digit.
- **Session Management:** Invalidates the user's OTHER active sessions across all devices/tabs, while keeping the current session active. Passwords are never logged or returned in responses.
- **Live Response (`200 OK`):**
```json
{
  "status": 200,
  "message": "Password changed successfully."
}
```

### 6. Brute-Force Defense & Account Lockout
- If 5 consecutive failed login or password change attempts occur for any username within a 5-minute sliding window, the account is temporarily locked for 60 seconds.
- Any subsequent attempt (such as the 6th attempt) immediately returns **`429 Too Many Requests`**.

---

## 🛠️ First-Run Setup & Staff Management

### 1. Check Setup Status (`GET /api/setup/status`)
- **Access:** Public (No token required)
- **Response (`200 OK`):**
```json
{
  "needsSetup": true
}
```

### 2. Complete Initial Setup (`POST /api/setup`)
- **Access:** Public (Only available when database has 0 user accounts; returns 409 Conflict once setup is complete)
- **Rate Limit:** 10 requests per minute per IP
- **Request Body:**
```json
{
  "username": "reception",
  "displayName": "Front Desk Receptionist",
  "password": "Password123"
}
```
- **Response (`201 Created`):**
```json
{
  "message": "Initial setup completed successfully. Please sign in with your receptionist credentials."
}
```

### 3. List Staff Accounts (`GET /api/staff`)
- **Required Role:** `RECEPTIONIST`
- **Response (`200 OK`):**
```json
[
  {
    "username": "reception",
    "displayName": "Front Desk Receptionist",
    "role": "RECEPTIONIST",
    "doctorId": null
  }
]
```

### 4. Create Staff Account (`POST /api/staff`)
- **Required Role:** `RECEPTIONIST`
- **Request Body:**
```json
{
  "username": "dr.sharma",
  "displayName": "Dr. Rohith Sharma",
  "password": "DoctorPassword123",
  "role": "DOCTOR",
  "doctorId": "DOC-102"
}
```
- **Response (`201 Created`):**
```json
{
  "username": "dr.sharma",
  "displayName": "Dr. Rohith Sharma",
  "role": "DOCTOR",
  "doctorId": "DOC-102"
}
```

### 5. Update Staff Password (`PUT /api/staff/{username}/password`)
- **Required Role:** `RECEPTIONIST`
- **Request Body:**
```json
{
  "newPassword": "NewSecurePassword123"
}
```
- **Response (`200 OK`):**
```json
{
  "message": "Password updated successfully for dr.sharma."
}
```

### 6. Delete Staff Account (`DELETE /api/staff/{username}`)
- **Required Role:** `RECEPTIONIST`
- **Response (`200 OK`):**
```json
{
  "message": "Staff account 'dr.sharma' deleted successfully."
}
```
- **Errors:**
  - `409 Conflict`: Cannot delete own account or last Front Desk account.
  - `404 Not Found`: Staff username does not exist.

---

## ⚠️ Standard Error Response Format

All error responses from the API adhere to RFC 7807 problem details semantics and are masked to **never leak SQL queries, database table names, or internal stack traces**:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Phone must be exactly 10 digits",
  "path": "/api/patients",
  "timestamp": "2026-10-23T14:10:02.123456Z"
}
```

### Status Code Mapping:
- **`400 Bad Request`**: Validation failures (invalid phone format, severity > 5, emergency severity < 4).
- **`401 Unauthorized`**: Missing or expired `Authorization: Bearer <token>` header.
- **`403 Forbidden`**: Role violation (e.g. Doctor attempting front-desk registration, or cross-doctor queue access).
- **`404 Not Found`**: Resource does not exist (unknown doctor ID, non-existent token).
- **`409 Conflict`**: State conflict (doctor off-duty, doctor currently in another consultation, duplicate token).
- **`429 Too Many Requests`**: Account temporarily locked due to brute-force login attempts.
- **`500 Internal Server Error`**: Generic masked error without JDBC / SQL exception details.

---

## 🌐 1. Public Endpoints (Zero Authentication & Zero PII)

Public endpoints require **no Authorization header** and strictly exclude patient full names, mobile phone numbers, ages, and medical chief complaints.

### 1.1 OPD Live Display Board
- **Method / Path:** `GET /api/public/board`
- **Role:** Public
- **Request Body:** None
- **Live Response (`200 OK`):**
```json
[
  {
    "doctorId": "DOC-103",
    "doctorName": "Dr. Priya Nair",
    "department": "Pediatrics",
    "room": "OPD Room 104",
    "status": "Available",
    "nowServingToken": "PED-S-003",
    "nextTokens": [
      {
        "token": "PED-W-009",
        "category": "Walk-in",
        "isEmergency": false,
        "estimatedStart": "08:02 PM (~2m)"
      }
    ]
  },
  {
    "doctorId": "DOC-102",
    "doctorName": "Dr. Ananya Sharma",
    "department": "Cardiology",
    "room": "OPD Room 108",
    "status": "Available",
    "nowServingToken": null,
    "nextTokens": [
      {
        "token": "CARD-W-005",
        "category": "Walk-in",
        "isEmergency": false,
        "estimatedStart": "08:28 PM (~28m)"
      },
      {
        "token": "CARD-S-010",
        "category": "Scheduled",
        "isEmergency": false,
        "estimatedStart": "08:56 PM (~56m)"
      }
    ]
  }
]
```

### 1.2 Patient Self-Service Tracker
- **Method / Path:** `GET /api/public/tracker/{token}`
- **Role:** Public
- **Rate Limit:** 30 requests per minute per IP address (returns `429 Too Many Requests` on 31st request).
- **Privacy Rule:** Strictly zero PII. First name greeting only. Severity level numbers are completely omitted from public view.
- **Request Body:** None
- **Live Response (`200 OK`):**
```json
{
  "token": "CARD-W-006",
  "firstName": "Anil",
  "doctor": "Dr. Ananya Sharma",
  "department": "Cardiology",
  "room": "OPD Room 108",
  "category": "Walk-in",
  "status": "Waiting",
  "position": 3,
  "patientsAhead": 2,
  "estimatedStart": "07:59 PM (~18m)",
  "estimatedWaitMinutes": 18,
  "explanation": "Fairness boost applied (+150 pts): waiting time (50 min) exceeded 45-min threshold.",
  "fairnessBoost": true,
  "isNext": false,
  "isEmergencyDelayed": false,
  "timeline": [
    {
      "timestamp": "2026-10-23T13:21:01.984579300Z",
      "type": "REGISTERED",
      "reason": "Token CARD-W-006 registered for Dr. Ananya Sharma (Walk-in, standard priority)."
    },
    {
      "timestamp": "2026-10-23T13:21:01.984579300Z",
      "type": "MOVED_DOWN",
      "reason": "Token CARD-W-006 moved from #3 to #4 due to higher priority admission."
    },
    {
      "timestamp": "2026-10-23T13:21:01.984579300Z",
      "type": "MOVED_UP",
      "reason": "Token CARD-W-006 moved from #4 to #3 in queue (Standard queue priority: medical priority with 0 mins in queue.)."
    },
    {
      "timestamp": "2026-10-23T14:11:01.984579300Z",
      "type": "FAIRNESS_BOOST",
      "reason": "Fairness boost applied to CARD-W-006 (+150 pts): waited 50 mins (exceeded threshold)."
    }
  ]
}
```

### 1.3 Sample Tokens for Quick Demo
- **Method / Path:** `GET /api/public/sample-tokens`
- **Role:** Public
- **Request Body:** None
- **Live Response (`200 OK`):**
```json
[
  "PED-W-009",
  "CARD-W-005",
  "CARD-S-010",
  "GEN-W-004",
  "GEN-S-008"
]
```

---

## 🏥 2. Front Desk Receptionist Endpoints

All endpoints below require an `Authorization: Bearer <reception-token>` header with role `RECEPTIONIST`.

### 2.1 Get Departments
- **Method / Path:** `GET /api/departments`
- **Live Response (`200 OK`):**
```json
[
  { "code": "GEN", "name": "General Medicine" },
  { "code": "CARD", "name": "Cardiology" },
  { "code": "ORTH", "name": "Orthopedics" },
  { "code": "PED", "name": "Pediatrics" },
  { "code": "ENT", "name": "ENT" }
]
```

### 2.2 Register Patient
- **Method / Path:** `POST /api/patients`
- **Request Body:**
```json
{
  "name": "Kamla Bai",
  "age": 71,
  "gender": "Female",
  "phone": "9822334455",
  "category": "Emergency",
  "severity": 5,
  "doctorId": "DOC-102",
  "chiefComplaint": "Severe acute chest pain radiating to left arm (Triage Level 5)"
}
```
- **Live Response (`201 Created`):**
```json
{
  "token": "CARD-E-003",
  "position": 1,
  "estimatedWaitMinutes": 0,
  "estimatedStart": "Next Call (Immediate)",
  "explanation": "Severity 5 emergency override: critical case escalated to top of queue ahead of routine visits.",
  "doctorId": "DOC-102",
  "doctorName": "Dr. Rohith Sharma",
  "department": "Cardiology",
  "room": "OPD Room 108"
}
```

### 2.3 Live Patient Preview (Transient / Non-Mutating)
- **Method / Path:** `POST /api/patients/preview`
- **Request Body:** Same as Register Patient
- **Live Response (`200 OK`):**
```json
{
  "token": "CARD-W-PREV",
  "position": 3,
  "estimatedWaitMinutes": 28,
  "estimatedStart": "08:28 PM (~28m)",
  "explanation": "Standard queue priority: Severity Level 2 with 0 mins in queue.",
  "doctorId": "DOC-102",
  "doctorName": "Dr. Rohith Sharma"
}
```

### 2.4 Get All Active Doctor Queues
- **Method / Path:** `GET /api/queues`
- **Live Response (`200 OK`):**
```json
[
  {
    "doctorId": "DOC-102",
    "doctorName": "Dr. Rohith Sharma",
    "department": "Cardiology",
    "deptCode": "CARD",
    "room": "OPD Room 108",
    "status": "Available",
    "avgConsultMinutes": 15,
    "emergencyBufferSlots": 3,
    "nowServing": null,
    "waitingList": [
      {
        "token": "CARD-E-003",
        "name": "Kamla Bai",
        "category": "Emergency",
        "severity": 5,
        "position": 1,
        "estimatedStart": "Next Call (Immediate)",
        "registeredAt": "2026-10-23T13:21:01.984579300Z",
        "chiefComplaint": "Severe acute chest pain radiating to left arm (Triage Level 5)"
      },
      {
        "token": "CARD-W-005",
        "name": "Priya Sharma",
        "category": "Walk-in",
        "severity": 4,
        "position": 2,
        "estimatedStart": "07:41 PM (~0m)",
        "registeredAt": "2026-10-23T13:01:01.984579300Z",
        "chiefComplaint": "Sudden tachycardia and palpitations (HR 135 bpm)"
      }
    ]
  }
]
```

### 2.5 Advance Simulated Demo Clock
- **Method / Path:** `POST /api/clock/advance`
- **Request Body:**
```json
{
  "minutes": 50
}
```
- **Live Response (`200 OK`):**
```json
{
  "currentTime": "2026-10-23T14:11:01.984579300Z",
  "offsetMinutes": 50
}
```

### 2.6 Reset Simulated Demo Clock
- **Method / Path:** `POST /api/clock/reset`
- **Request Body:** `{}`
- **Live Response (`200 OK`):**
```json
{
  "currentTime": "2026-10-23T13:21:01.984579300Z",
  "offsetMinutes": 0
}
```

### 2.7 Get Events Audit Log
- **Method / Path:** `GET /api/events?limit=10`
- **Live Response (`200 OK`):**
```json
[
  {
    "id": "EVT-20",
    "timestamp": "2026-10-23T14:11:01.984579300Z",
    "tokenNumber": "CARD-W-006",
    "doctorId": "DOC-102",
    "type": "FAIRNESS_BOOST",
    "reason": "Fairness boost applied to CARD-W-006 (+150 pts): waited 50 mins (exceeded threshold)."
  },
  {
    "id": "EVT-11",
    "timestamp": "2026-10-23T13:21:01.984579300Z",
    "tokenNumber": "CARD-E-003",
    "doctorId": "DOC-102",
    "type": "COMPLETED",
    "reason": "Consultation completed for CARD-E-003 in 1m. Running avg updated to 9m."
  }
]
```

### 2.8 Get OPD Analytics
- **Method / Path:** `GET /api/analytics`
- **Live Response (`200 OK`):**
```json
{
  "totalPatients": 14,
  "waitingCount": 10,
  "inConsultationCount": 2,
  "completedCount": 2,
  "noShowCount": 0,
  "averageWaitOverall": 24,
  "emergencyWaitAvg": 4,
  "scheduledWaitAvg": 21,
  "walkInWaitAvg": 31,
  "throughputPerHour": 4.5,
  "activeDoctorCount": 5,
  "totalDoctors": 6,
  "fairnessBoostedCount": 7,
  "emergencyOverrideCount": 2,
  "categoryBreakdown": {
    "Emergency": 2,
    "Scheduled": 6,
    "Walk-in": 6
  },
  "patientsPerHour": [
    { "hour": "09:00", "count": 3 },
    { "hour": "10:00", "count": 7 },
    { "hour": "11:00", "count": 4 }
  ],
  "waitTimesByCategory": {
    "Emergency": 4,
    "Scheduled": 21,
    "Walk-in": 31
  }
}
```

### 2.9 Get and Update Algorithm Settings
- **Method / Path:** `GET /api/settings`
- **Live Response (`200 OK`):**
```json
{
  "severityWeight": 100.0,
  "waitWeightPerMin": 3.0,
  "fairnessBoost": 150.0,
  "fairnessThresholdScheduled": 45,
  "fairnessThresholdWalkin": 45,
  "maxConsecutiveEmergencies": 2,
  "emergencyBufferSlotsDefault": 2
}
```
- **Update Settings (`PUT /api/settings`):**
  Accepts identical JSON object. Returns updated `SettingsDto`. Bad values (e.g. `severityWeight < 10`) return `400 Bad Request`.
- **Reset Settings (`POST /api/settings/reset`):**
  Resets all algorithm tuning weights back to default values.

---

## 🩺 3. Doctor Console Endpoints

All endpoints below require an `Authorization: Bearer <doctor-token>` header with role `DOCTOR`. Cross-doctor isolation strictly prevents doctor tokens from calling endpoints on other doctor IDs.

### 3.1 Get Doctor Queue
- **Method / Path:** `GET /api/doctors/{id}/queue`
- **Role:** `DOCTOR` (Matching doctor ID) or `RECEPTIONIST`
- **Live Response (`200 OK`):**
```json
{
  "doctorId": "DOC-102",
  "doctorName": "Dr. Rohith Sharma",
  "department": "Cardiology",
  "deptCode": "CARD",
  "room": "OPD Room 108",
  "status": "Available",
  "avgConsultMinutes": 15,
  "emergencyBufferSlots": 3,
  "nowServing": {
    "token": "CARD-E-002",
    "name": "Sunita Devi",
    "category": "Emergency",
    "severity": 5,
    "position": 0,
    "estimatedStart": "Now Serving",
    "registeredAt": "2026-10-23T12:46:01.984579300Z",
    "chiefComplaint": "Severe radiating chest pain and sudden diaphoresis"
  },
  "waitingList": [
    {
      "token": "CARD-W-005",
      "name": "Priya Sharma",
      "category": "Walk-in",
      "severity": 4,
      "position": 1,
      "estimatedStart": "07:41 PM (~0m)",
      "registeredAt": "2026-10-23T13:01:01.984579300Z",
      "chiefComplaint": "Sudden tachycardia and palpitations (HR 135 bpm)"
    }
  ]
}
```

### 3.2 Call Next Patient
- **Method / Path:** `POST /api/doctors/{id}/call-next`
- **Role:** `DOCTOR`
- **Live Response (`200 OK`):**
```json
{
  "token": "CARD-E-003",
  "name": "Kamla Bai",
  "age": 71,
  "gender": "Female",
  "phone": "9822334455",
  "category": "Emergency",
  "severity": 5,
  "doctorId": "DOC-102",
  "doctorName": "Dr. Rohith Sharma",
  "department": "Cardiology",
  "room": "OPD Room 108",
  "appointmentTime": null,
  "chiefComplaint": "Severe acute chest pain radiating to left arm (Triage Level 5)",
  "status": "In Consultation",
  "registeredAt": "2026-10-23T13:21:01.984579300Z",
  "consultationStartedAt": "2026-10-23T13:21:01.984579300Z"
}
```

### 3.3 Complete Active Consultation
- **Method / Path:** `POST /api/doctors/{id}/complete`
- **Role:** `DOCTOR`
- **Live Response (`200 OK`):**
```json
{
  "token": "CARD-E-003",
  "name": "Kamla Bai",
  "age": 71,
  "gender": "Female",
  "phone": "9822334455",
  "category": "Emergency",
  "severity": 5,
  "doctorId": "DOC-102",
  "doctorName": "Dr. Rohith Sharma",
  "department": "Cardiology",
  "room": "OPD Room 108",
  "appointmentTime": null,
  "chiefComplaint": "Severe acute chest pain radiating to left arm (Triage Level 5)",
  "status": "Completed",
  "registeredAt": "2026-10-23T13:21:01.984579300Z",
  "consultationStartedAt": "2026-10-23T13:21:01.984579300Z"
}
```

### 3.4 Toggle Doctor Availability Status
- **Method / Path:** `PUT /api/doctors/{id}/status`
- **Role:** `DOCTOR`
- **Request Body:**
```json
{
  "status": "On Break"
}
```
- **Live Response (`200 OK`):**
```json
{
  "id": "DOC-102",
  "name": "Dr. Rohith Sharma",
  "age": 52,
  "gender": "Male",
  "phone": "9876500102",
  "department": "Cardiology",
  "deptCode": "CARD",
  "room": "OPD Room 108",
  "workingHours": "09:00 - 15:00",
  "avgConsultMinutes": 15,
  "emergencyBufferSlots": 3,
  "status": "On Break"
}
```

---

## 🧪 3.10 Discrete-Event Queue Simulation (Front Desk Role Only)

Endpoints supporting synthetic multi-policy OPD queue load simulations and parameter sensitivity analysis.

### 3.10.1 Get Simulation Presets
- **Method / Path:** `GET /api/simulation/presets`
- **Role:** `RECEPTIONIST`
- **Request Body:** None
- **Live Response (`200 OK`):**
```json
[
  {
    "id": "quiet-day",
    "name": "Quiet Day",
    "description": "Low patient volume, high scheduled appointments ratio, minimal emergencies. (~60% load)",
    "numPatients": 90,
    "numDoctors": 4,
    "avgConsultMinutes": 8,
    "emergencyPercent": 5,
    "scheduledPercent": 65,
    "arrivalSpreadMinutes": 300,
    "randomSeed": 101,
    "offeredLoadPercent": 60
  },
  {
    "id": "busy-day",
    "name": "Busy Day",
    "description": "Typical heavy hospital weekday with regular emergencies and steady walk-ins. (~90% load)",
    "numPatients": 108,
    "numDoctors": 4,
    "avgConsultMinutes": 10,
    "emergencyPercent": 12,
    "scheduledPercent": 48,
    "arrivalSpreadMinutes": 300,
    "randomSeed": 202,
    "offeredLoadPercent": 90
  },
  {
    "id": "emergency-surge",
    "name": "Emergency Surge",
    "description": "Mass casualty / monsoon surge with high emergency intake stressing routine OPD. (~105% load)",
    "numPatients": 126,
    "numDoctors": 4,
    "avgConsultMinutes": 10,
    "emergencyPercent": 28,
    "scheduledPercent": 35,
    "arrivalSpreadMinutes": 300,
    "randomSeed": 303,
    "offeredLoadPercent": 105
  }
]
```

### 3.10.2 Run Discrete-Event Simulation
- **Method / Path:** `POST /api/simulation/run`
- **Role:** `RECEPTIONIST`
- **Constraints:** `numPatients` (50..500), `numDoctors` (1..6), 10-second server execution timeout. Uses live algorithm settings from SQLite.
- **Sample Request Body:**
```json
{
  "preset": "busy-day",
  "randomSeed": 42
}
```
- **Live Response (`200 OK`):**
```json
{
  "config": {
    "numPatients": 108,
    "numDoctors": 4,
    "avgConsultMinutes": 10,
    "emergencyPercent": 12,
    "scheduledPercent": 48,
    "arrivalSpreadMinutes": 300,
    "randomSeed": 42
  },
  "offeredLoadPercent": 90,
  "patientsCount": 108,
  "policies": {
    "fcfs": {
      "policyName": "FCFS",
      "totalServed": 108,
      "totalSimTime": 314,
      "metrics": {
        "averageWaitOverall": 10,
        "avgWaitOverall": 10,
        "averageWaitEmergency": 8,
        "avgWaitEmergency": 8,
        "averageWaitHighSeverity": 9,
        "avgWaitHighSeverity": 9,
        "averageWaitScheduled": 11,
        "avgWaitScheduled": 11,
        "averageWaitWalkin": 10,
        "avgWaitWalkin": 10,
        "p90Wait": 24,
        "maxScheduledWait": 29,
        "maxWaitScheduled": 29,
        "starvationCount": 0,
        "doctorIdlePercent": 14
      }
    },
    "strictPriority": {
      "policyName": "StrictPriority",
      "totalServed": 108,
      "totalSimTime": 314,
      "metrics": {
        "averageWaitOverall": 10,
        "avgWaitOverall": 10,
        "averageWaitEmergency": 1,
        "avgWaitEmergency": 1,
        "averageWaitHighSeverity": 1,
        "avgWaitHighSeverity": 1,
        "averageWaitScheduled": 10,
        "avgWaitScheduled": 10,
        "averageWaitWalkin": 12,
        "avgWaitWalkin": 12,
        "p90Wait": 42,
        "maxScheduledWait": 62,
        "maxWaitScheduled": 62,
        "starvationCount": 0,
        "doctorIdlePercent": 14
      }
    },
    "smartopd": {
      "policyName": "SmartOPD",
      "totalServed": 108,
      "totalSimTime": 314,
      "metrics": {
        "averageWaitOverall": 10,
        "avgWaitOverall": 10,
        "averageWaitEmergency": 1,
        "avgWaitEmergency": 1,
        "averageWaitHighSeverity": 1,
        "avgWaitHighSeverity": 1,
        "averageWaitScheduled": 8,
        "avgWaitScheduled": 8,
        "averageWaitWalkin": 15,
        "avgWaitWalkin": 15,
        "p90Wait": 39,
        "maxScheduledWait": 41,
        "maxWaitScheduled": 41,
        "starvationCount": 0,
        "doctorIdlePercent": 14
      }
    }
  },
  "insights": [
    "Compared with strict priority, SmartOPD cut the longest scheduled wait by 21 minutes without increasing emergency wait.",
    "Average wait is similar across policies; SmartOPD changes who waits, not how much total waiting there is.",
    "Trade-off: walk-in patients waited 5 minutes longer on average than under first come first served, because emergencies and scheduled patients are protected first.",
    "SmartOPD cut average emergency wait by 88 percent versus FCFS (1m vs 8m).",
    "Strict priority left 0 scheduled patients waiting over 90 minutes; SmartOPD held this to 0.",
    "Worst-case wait for scheduled appointments was 62m under Strict Priority vs 41m with SmartOPD fairness safeguards.",
    "SmartOPD maintains clinical balance with a 90th-percentile wait of 39 minutes across all patient categories."
  ]
}
```

---

## 📋 4. Frontend Contract Notes

This table details the integration bridge for all 24 methods called on `SmartOPDQueue` by the frontend pages (`app.js`, `display-board.html`, `patient-tracker.html`, `doctor-console.html`, `admin-settings.html`). It flags every structural and naming difference between the legacy in-memory mock (`queue-mock.js`) and the production Spring Boot REST API:

| # | Frontend Method on `SmartOPDQueue` | Replaced by REST API Endpoint | Role Required | Shape Difference vs `queue-mock.js` |
|---|---|---|---|---|
| 1 | `registerPatient(data)` | `POST /api/patients` | `RECEPTIONIST` | **DIFF:** Backend returns HTTP 201 Created directly with flat fields (`token`, `position`, `estimatedWaitMinutes`, `estimatedStart`, `explanation`, `doctorId`, `doctorName`, `department`, `room`). Mock wrapped in `{ success: true, patient: {...} }`. Backend uses `estimatedStart` instead of `estimatedTimeStr`. |
| 2 | `getDoctorQueue(doctorId)` | `GET /api/doctors/{id}/queue` | `DOCTOR` or `RECEPTION` | **DIFF:** Backend uses `waitingList` (not `waitingPatients`) and `nowServing` (not `activePatient`). Flattens doctor attributes at root instead of nesting under `doctor: {...}`. Stats are retrieved separately via `GET /api/analytics`. |
| 3 | `callNext(doctorId)` | `POST /api/doctors/{id}/call-next` | `DOCTOR` | **DIFF:** Backend returns flat `PatientSlipDto` of called patient. Mock returned `{ success: true, activePatient, waitingPatients, doctor }`. Frontend should refresh queue after calling next. |
| 4 | `completeConsultation(doctorId)` | `POST /api/doctors/{id}/complete` | `DOCTOR` | **DIFF:** Backend returns flat `PatientSlipDto` of completed patient with `status: "Completed"`. Mock returned `{ success: true, completedPatient, doctor }`. |
| 5 | `markNoShow(tokenNumber)` | `POST /api/patients/{token}/no-show` | `RECEPTIONIST` | **DIFF:** Backend returns flat `PatientSlipDto` with `status: "No-Show"` instead of `{ success: true, patient }`. |
| 6 | `skipPatient(tokenNumber, reason)` | `POST /api/patients/{token}/skip` | `RECEPTIONIST` | **DIFF:** Backend returns `PatientSlipDto` with `skipped: true`. Mock returned `{ success: true, patient, oldPosition, newPosition }`. |
| 7 | `setDoctorStatus(doctorId, status)` | `PUT /api/doctors/{id}/status` | `DOCTOR` or `RECEPTION` | **DIFF:** Backend returns flat `DoctorDto` with updated `status`. Mock wrapped in `{ success: true, doctor }`. |
| 8 | `advanceClock(minutes)` | `POST /api/clock/advance` | `RECEPTIONIST` | **DIFF:** Backend returns `{ currentTime, offsetMinutes }`. Mock returned `{ success: true, newTime, offsetMinutes, events }`. In backend, events are queried separately via `GET /api/events`. |
| 9 | `resetClock()` | `POST /api/clock/reset` | `RECEPTIONIST` | **DIFF:** Backend returns `{ currentTime, offsetMinutes: 0 }` instead of `{ success: true, newTime, offsetMinutes }`. |
| 10 | `getEvents(docId, token, limit)` | `GET /api/events?doctorId=...` | `RECEPTIONIST` | **DIFF:** Backend returns `List<QueueEventDto>` with fields `id`, `timestamp`, `tokenNumber`, `doctorId`, `type`, `reason`. Mock returned `doctorName` and raw `patientName`. Zero PII is enforced. |
| 11 | `getPublicBoardData()` | `GET /api/public/board` | `Public` | **DIFF:** Backend uses `estimatedStart` instead of `estimatedTimeStr`, and includes `isEmergency: boolean`. Does not output internal `waitMinutes` on public board. |
| 12 | `getPublicQueueSummary(token)` | `GET /api/public/tracker/{token}` | `Public` | **DIFF:** Backend uses `estimatedStart` instead of `estimatedTimeStr`. Timeline event objects are `{ timestamp, type, reason }`. Patient full name is masked to `firstName` only for zero PII. |
| 13 | `getTokenTimeline(token)` | Embedded in tracker / `GET /api/events` | `Public` / `RECEPTION` | **DIFF:** Timeline is directly embedded inside `PublicTrackerResponse.timeline`. For reception desk audit, `GET /api/events?tokenNumber={token}` provides full staff event history. |
| 14 | `getAnalytics()` | `GET /api/analytics` | `RECEPTIONIST` | **MATCH:** All 17 analytics metrics match 1:1 (`totalPatients`, `waitingCount`, `inConsultationCount`, `completedCount`, `noShowCount`, `averageWaitOverall`, `patientsPerHour`, etc.). |
| 15 | `getSettings()` | `GET /api/settings` | `RECEPTIONIST` | **MATCH:** All 7 scoring weights and thresholds match 1:1 (`severityWeight`, `waitWeightPerMin`, `fairnessBoost`, `fairnessThresholdScheduled`, etc.). |
| 16 | `updateSettings(newSettings)` | `PUT /api/settings` | `RECEPTIONIST` | **DIFF:** Backend returns updated `SettingsDto` directly. Mock wrapped in `{ success: true, settings }`. Out of range values return `400 Bad Request`. |
| 17 | `resetSettings()` | `POST /api/settings/reset` | `RECEPTIONIST` | **DIFF:** Backend returns default `SettingsDto` directly instead of `{ success: true, settings }`. |
| 18 | `addDoctor(doctorData)` | `POST /api/doctors` | `RECEPTIONIST` | **DIFF:** Backend returns HTTP 201 Created with flat `DoctorDto` instead of `{ success: true, doctor }`. |
| 19 | `updateDoctor(id, updates)` | `PUT /api/doctors/{id}` | `RECEPTIONIST` | **DIFF:** Backend returns updated flat `DoctorDto` instead of `{ success: true, doctor }`. |
| 20 | `deleteDoctor(id)` | `DELETE /api/doctors/{id}` | `RECEPTIONIST` | **DIFF:** Backend returns `{ status: 200, message: "..." }`. If doctor has waiting patients, backend returns HTTP `409 Conflict` with human-readable error message. |
| 21 | `addDepartment(code, name)` | `POST /api/departments` | `RECEPTIONIST` | **DIFF:** Backend returns HTTP 201 Created with flat `DepartmentDto` (`{"code": "...", "name": "..."}`). |
| 22 | `resetState()` | Delete `data/smartopd.db` & restart | Staff CLI / Admin | **DIFF:** In persistent architecture, demo state is stored in SQLite file. Complete system reset is performed by deleting `data/smartopd.db` (auto-reseeded on boot) or resetting clock + settings. |
| 23 | `getAllTokens()` | `GET /api/patients` | `RECEPTIONIST` | **DIFF:** Backend returns full `List<PatientSlipDto>` containing all active and completed patient slips. |
| 24 | `getDashboardStats()` | `GET /api/analytics` | `RECEPTIONIST` | **DIFF:** Replaced by `GET /api/analytics`, which returns real-time OPD throughput, waiting counts, and hourly metrics. |
