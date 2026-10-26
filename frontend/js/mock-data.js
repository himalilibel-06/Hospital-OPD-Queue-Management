/**
 * SmartOPD – Mock Healthcare Dataset
 * File: js/mock-data.js
 * 
 * Preloaded mock data for doctors, departments, patient demographics, and active OPD queue tokens.
 * Used as default initial state for js/queue-mock.js.
 */

window.SmartOPDMock = {
  // 5 OPD Clinical Departments
  departments: [
    { id: 'GEN', name: 'General Medicine', code: 'GEN', floor: '1st Floor, Wing A' },
    { id: 'CARD', name: 'Cardiology', code: 'CARD', floor: '2nd Floor, Wing B' },
    { id: 'ORTH', name: 'Orthopedics', code: 'ORTH', floor: '1st Floor, Wing C' },
    { id: 'PED', name: 'Pediatrics', code: 'PED', floor: 'Ground Floor, Wing D' },
    { id: 'ENT', name: 'ENT', code: 'ENT', floor: '2nd Floor, Wing A' }
  ],

  // 6 Hospital Doctors across departments
  doctors: [
    {
      id: 'DOC-101',
      name: 'Dr. Rajesh Mehta',
      department: 'General Medicine',
      deptCode: 'GEN',
      room: 'OPD Room 102',
      workingHours: { start: '09:00', end: '16:00' },
      avgConsultMinutes: 12,
      status: 'Available',
      emergencyBufferSlots: 2
    },
    {
      id: 'DOC-102',
      name: 'Dr. Rohith Sharma',
      department: 'Cardiology',
      deptCode: 'CARD',
      room: 'OPD Room 108',
      workingHours: { start: '09:00', end: '15:00' },
      avgConsultMinutes: 15,
      status: 'Available',
      emergencyBufferSlots: 3
    },
    {
      id: 'DOC-103',
      name: 'Dr. Priya Nair',
      department: 'Pediatrics',
      deptCode: 'PED',
      room: 'OPD Room 104',
      workingHours: { start: '09:30', end: '16:30' },
      avgConsultMinutes: 10,
      status: 'Available',
      emergencyBufferSlots: 2
    },
    {
      id: 'DOC-104',
      name: 'Dr. Vikram Patel',
      department: 'Orthopedics',
      deptCode: 'ORTH',
      room: 'OPD Room 112',
      workingHours: { start: '10:00', end: '17:00' },
      avgConsultMinutes: 14,
      status: 'On Break',
      emergencyBufferSlots: 2
    },
    {
      id: 'DOC-105',
      name: 'Dr. Suresh Verma',
      department: 'ENT',
      deptCode: 'ENT',
      room: 'OPD Room 106',
      workingHours: { start: '09:00', end: '14:00' },
      avgConsultMinutes: 8,
      status: 'Available',
      emergencyBufferSlots: 1
    },
    {
      id: 'DOC-106',
      name: 'Dr. Kavita Rao',
      department: 'General Medicine',
      deptCode: 'GEN',
      room: 'OPD Room 103',
      workingHours: { start: '13:00', end: '19:00' },
      avgConsultMinutes: 11,
      status: 'Off Duty',
      emergencyBufferSlots: 2
    }
  ],

  // 12 Realistic Indian Patients currently distributed in OPD Queues
  patients: [
    {
      id: 'PAT-101',
      tokenNumber: 'GEN-S-001',
      name: 'Ramesh Kumar',
      age: 52,
      gender: 'Male',
      phone: '9876543210',
      category: 'Scheduled',
      severity: 2,
      department: 'General Medicine',
      doctorId: 'DOC-101',
      doctorName: 'Dr. Rajesh Mehta',
      room: 'OPD Room 102',
      appointmentTime: '09:30',
      registrationTime: new Date(Date.now() - 45 * 60000).toISOString(),
      chiefComplaint: 'Follow-up for hypertension and medication refill',
      status: 'In Consultation',
      consultationStartTime: new Date(Date.now() - 5 * 60000).toISOString()
    },
    {
      id: 'PAT-102',
      tokenNumber: 'CARD-E-002',
      name: 'Sunita Devi',
      age: 68,
      gender: 'Female',
      phone: '9811122334',
      category: 'Emergency',
      severity: 5,
      department: 'Cardiology',
      doctorId: 'DOC-102',
      doctorName: 'Dr. Rohith Sharma',
      room: 'OPD Room 108',
      appointmentTime: null,
      registrationTime: new Date(Date.now() - 35 * 60000).toISOString(),
      chiefComplaint: 'Severe radiating chest pain and sudden diaphoresis',
      status: 'In Consultation',
      consultationStartTime: new Date(Date.now() - 8 * 60000).toISOString()
    },
    {
      id: 'PAT-103',
      tokenNumber: 'PED-S-003',
      name: 'Aarav Gupta',
      age: 7,
      gender: 'Male',
      phone: '9922233445',
      category: 'Scheduled',
      severity: 3,
      department: 'Pediatrics',
      doctorId: 'DOC-103',
      doctorName: 'Dr. Priya Nair',
      room: 'OPD Room 104',
      appointmentTime: '10:00',
      registrationTime: new Date(Date.now() - 25 * 60000).toISOString(),
      chiefComplaint: 'High grade pediatric fever (102°F) and persistent cough',
      status: 'In Consultation',
      consultationStartTime: new Date(Date.now() - 4 * 60000).toISOString()
    },
    {
      id: 'PAT-104',
      tokenNumber: 'GEN-W-004',
      name: 'Meera Iyer',
      age: 38,
      gender: 'Female',
      phone: '9733344556',
      category: 'Walk-in',
      severity: 1,
      department: 'General Medicine',
      doctorId: 'DOC-101',
      doctorName: 'Dr. Rajesh Mehta',
      room: 'OPD Room 102',
      appointmentTime: null,
      registrationTime: new Date(Date.now() - 52 * 60000).toISOString(), // 52 mins waiting -> Fairness boost triggered!
      chiefComplaint: 'Mild seasonal allergies and throat irritation',
      status: 'Waiting'
    },
    {
      id: 'PAT-105',
      tokenNumber: 'CARD-W-005',
      name: 'Priya Sharma',
      age: 46,
      gender: 'Female',
      phone: '9844455667',
      category: 'Walk-in',
      severity: 4,
      department: 'Cardiology',
      doctorId: 'DOC-102',
      doctorName: 'Dr. Rohith Sharma',
      room: 'OPD Room 108',
      appointmentTime: null,
      registrationTime: new Date(Date.now() - 20 * 60000).toISOString(),
      chiefComplaint: 'Sudden tachycardia and palpitations (HR 135 bpm)',
      status: 'Waiting'
    },
    {
      id: 'PAT-106',
      tokenNumber: 'ORTH-S-006',
      name: 'Vikram Singh',
      age: 29,
      gender: 'Male',
      phone: '9755566778',
      category: 'Scheduled',
      severity: 3,
      department: 'Orthopedics',
      doctorId: 'DOC-104',
      doctorName: 'Dr. Vikram Patel',
      room: 'OPD Room 112',
      appointmentTime: '10:30',
      registrationTime: new Date(Date.now() - 32 * 60000).toISOString(),
      chiefComplaint: 'Acute knee sprain sustained during cricket practice',
      status: 'Waiting'
    },
    {
      id: 'PAT-107',
      tokenNumber: 'ENT-W-007',
      name: 'Anjali Deshmukh',
      age: 33,
      gender: 'Female',
      phone: '9866677889',
      category: 'Walk-in',
      severity: 2,
      department: 'ENT',
      doctorId: 'DOC-105',
      doctorName: 'Dr. Suresh Verma',
      room: 'OPD Room 106',
      appointmentTime: null,
      registrationTime: new Date(Date.now() - 18 * 60000).toISOString(),
      chiefComplaint: 'Inner ear blockage and ringing tinnitus for 3 days',
      status: 'Waiting'
    },
    {
      id: 'PAT-108',
      tokenNumber: 'GEN-S-008',
      name: 'Rohan Patil',
      age: 41,
      gender: 'Male',
      phone: '9977788990',
      category: 'Scheduled',
      severity: 2,
      department: 'General Medicine',
      doctorId: 'DOC-101',
      doctorName: 'Dr. Rajesh Mehta',
      room: 'OPD Room 102',
      appointmentTime: '10:15',
      registrationTime: new Date(Date.now() - 15 * 60000).toISOString(),
      chiefComplaint: 'Routine blood sugar evaluation and HbA1c review',
      status: 'Waiting'
    },
    {
      id: 'PAT-109',
      tokenNumber: 'PED-W-009',
      name: 'Amit Joshi',
      age: 5,
      gender: 'Male',
      phone: '9888899001',
      category: 'Walk-in',
      severity: 1,
      department: 'Pediatrics',
      doctorId: 'DOC-103',
      doctorName: 'Dr. Priya Nair',
      room: 'OPD Room 104',
      appointmentTime: null,
      registrationTime: new Date(Date.now() - 12 * 60000).toISOString(),
      chiefComplaint: 'General pediatric wellness and booster vaccination',
      status: 'Waiting'
    },
    {
      id: 'PAT-110',
      tokenNumber: 'CARD-S-010',
      name: 'Pooja Reddy',
      age: 58,
      gender: 'Female',
      phone: '9799900112',
      category: 'Scheduled',
      severity: 3,
      department: 'Cardiology',
      doctorId: 'DOC-102',
      doctorName: 'Dr. Rohith Sharma',
      room: 'OPD Room 108',
      appointmentTime: '10:45',
      registrationTime: new Date(Date.now() - 10 * 60000).toISOString(),
      chiefComplaint: 'Post-angioplasty 6-month routine stress test follow-up',
      status: 'Waiting'
    },
    {
      id: 'PAT-111',
      tokenNumber: 'ORTH-W-011',
      name: 'Deepak Verma',
      age: 64,
      gender: 'Male',
      phone: '9822211334',
      category: 'Walk-in',
      severity: 2,
      department: 'Orthopedics',
      doctorId: 'DOC-104',
      doctorName: 'Dr. Vikram Patel',
      room: 'OPD Room 112',
      appointmentTime: null,
      registrationTime: new Date(Date.now() - 8 * 60000).toISOString(),
      chiefComplaint: 'Chronic bilateral osteoarthritis knee pain',
      status: 'Waiting'
    },
    {
      id: 'PAT-112',
      tokenNumber: 'ENT-S-012',
      name: 'Rajeshwari Pillai',
      age: 49,
      gender: 'Female',
      phone: '9933322445',
      category: 'Scheduled',
      severity: 1,
      department: 'ENT',
      doctorId: 'DOC-105',
      doctorName: 'Dr. Suresh Verma',
      room: 'OPD Room 106',
      appointmentTime: '10:20',
      registrationTime: new Date(Date.now() - 5 * 60000).toISOString(),
      chiefComplaint: 'Chronic sinusitis consultation',
      status: 'Waiting'
    }
  ],

  // Recent queue operations activity log
  recentActivity: [
    {
      id: 'ACT-001',
      timestamp: new Date(Date.now() - 35 * 60000).toISOString(),
      type: 'EMERGENCY_ADMISSION',
      tokenNumber: 'CARD-E-002',
      patientName: 'Sunita Devi',
      doctorName: 'Dr. Rohith Sharma',
      reason: 'Emergency Level 5 triage override: admitted to Room 108 immediately.'
    },
    {
      id: 'ACT-002',
      timestamp: new Date(Date.now() - 25 * 60000).toISOString(),
      type: 'CONSULTATION_STARTED',
      tokenNumber: 'GEN-S-001',
      patientName: 'Ramesh Kumar',
      doctorName: 'Dr. Rajesh Mehta',
      reason: 'Scheduled appointment consultation started on time.'
    },
    {
      id: 'ACT-003',
      timestamp: new Date(Date.now() - 7 * 60000).toISOString(),
      type: 'FAIRNESS_BOOST',
      tokenNumber: 'GEN-W-004',
      patientName: 'Meera Iyer',
      doctorName: 'Dr. Rajesh Mehta',
      reason: 'Fairness boost applied (+150 pts): waiting time reached 45 mins.'
    },
    {
      id: 'ACT-004',
      timestamp: new Date(Date.now() - 5 * 60000).toISOString(),
      type: 'TOKEN_ISSUED',
      tokenNumber: 'ENT-S-012',
      patientName: 'Rajeshwari Pillai',
      doctorName: 'Dr. Suresh Verma',
      reason: 'Scheduled token issued for 10:20 AM slot.'
    }
  ]
};
