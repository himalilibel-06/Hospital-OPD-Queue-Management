/**
 * SmartOPD – Algorithmic Parameter Tuning & Policy Sweep Engine
 * File: js/tests/tune.js
 * 
 * ============================================================================
 * Task 4: Parameter Sweep across 48 algorithmic weight combinations
 * Seeds: 1 to 30 (30 seeds)
 * Presets: Quiet Day, Busy Day, Emergency Surge (3 operational regimes)
 * Total runs: 48 combos x 30 seeds x 3 presets = 4,320 simulations
 * 
 * Explored Parameter Grid:
 * - FAIRNESS_BOOST: [25, 50, 100, 150]
 * - WAIT_WEIGHT_PER_MIN: [1, 2, 3, 5]
 * - FAIRNESS_THRESHOLD_SCHEDULED: [20, 30, 45]
 * 
 * Output:
 * Averages over seeds 1-30 across all three presets for FCFS vs Strict Priority vs SmartOPD.
 * Top 5 combinations keeping avg emergency wait < 10m while minimising
 * max scheduled wait and starvation count.
 * ============================================================================
 */

const path = require('path');
const Sim = require(path.join(__dirname, '../simulator.js'));

const FAIRNESS_BOOSTS = [25, 50, 100, 150];
const WAIT_WEIGHTS = [1, 2, 3, 5];
const FAIRNESS_THRESHOLDS = [20, 30, 45];
const SEEDS = Array.from({ length: 30 }, (_, i) => i + 1);
const PRESET_KEYS = ['quiet-day', 'busy-day', 'emergency-surge'];

console.log('=========================================================================================');
console.log('             SmartOPD Algorithmic Parameter Sweep & Optimization Suite                   ');
console.log('=========================================================================================');
console.log(`Running sweep over ${FAIRNESS_BOOSTS.length * WAIT_WEIGHTS.length * FAIRNESS_THRESHOLDS.length} parameter combinations...`);
console.log(`Seeds: 1..30 (${SEEDS.length} seeds) x Presets: [${PRESET_KEYS.join(', ')}]`);
console.log(`Total simulations per combination: ${SEEDS.length * PRESET_KEYS.length} runs`);
console.log('-----------------------------------------------------------------------------------------\n');

const t0 = Date.now();

// Pre-compute FCFS, Strict Priority, and SmartOPD (Tuned Defaults: 150/3/45) baselines across (preset, seed) runs
let totalFcfsEmerg = 0, totalFcfsWalkin = 0, totalFcfsMaxSched = 0, totalFcfsStarv = 0;
let totalStrictEmerg = 0, totalStrictWalkin = 0, totalStrictMaxSched = 0, totalStrictStarv = 0;
let totalSmartTunedEmerg = 0, totalSmartTunedWalkin = 0, totalSmartTunedMaxSched = 0, totalSmartTunedStarv = 0;
const totalBaselineRuns = SEEDS.length * PRESET_KEYS.length;

// Store synthetic patient cohorts for each (preset, seed) to guarantee identical inputs
const cachedRuns = {};
PRESET_KEYS.forEach(presetKey => {
  cachedRuns[presetKey] = {};
  SEEDS.forEach(seed => {
    const baseConfig = { ...Sim.PRESETS[presetKey], randomSeed: seed };
    const res = Sim.runSimulation(baseConfig);
    cachedRuns[presetKey][seed] = {
      patients: res.patientList,
      numDoctors: res.config.numDoctors,
      fcfs: res.policies.fcfs.metrics,
      strict: res.policies.strictPriority.metrics,
      smart: res.policies.smartopd.metrics
    };
    totalFcfsEmerg += res.policies.fcfs.metrics.averageWaitEmergency;
    totalFcfsWalkin += res.policies.fcfs.metrics.averageWaitWalkin;
    totalFcfsMaxSched += res.policies.fcfs.metrics.maxScheduledWait;
    totalFcfsStarv += res.policies.fcfs.metrics.starvationCount;

    totalStrictEmerg += res.policies.strictPriority.metrics.averageWaitEmergency;
    totalStrictWalkin += res.policies.strictPriority.metrics.averageWaitWalkin;
    totalStrictMaxSched += res.policies.strictPriority.metrics.maxScheduledWait;
    totalStrictStarv += res.policies.strictPriority.metrics.starvationCount;

    totalSmartTunedEmerg += res.policies.smartopd.metrics.averageWaitEmergency;
    totalSmartTunedWalkin += res.policies.smartopd.metrics.averageWaitWalkin;
    totalSmartTunedMaxSched += res.policies.smartopd.metrics.maxScheduledWait;
    totalSmartTunedStarv += res.policies.smartopd.metrics.starvationCount;
  });
});

const baselineFCFS = {
  avgEmergencyWait: (totalFcfsEmerg / totalBaselineRuns).toFixed(2),
  avgWalkinWait: (totalFcfsWalkin / totalBaselineRuns).toFixed(2),
  maxScheduledWait: (totalFcfsMaxSched / totalBaselineRuns).toFixed(2),
  starvationCount: (totalFcfsStarv / totalBaselineRuns).toFixed(2)
};

const baselineStrict = {
  avgEmergencyWait: (totalStrictEmerg / totalBaselineRuns).toFixed(2),
  avgWalkinWait: (totalStrictWalkin / totalBaselineRuns).toFixed(2),
  maxScheduledWait: (totalStrictMaxSched / totalBaselineRuns).toFixed(2),
  starvationCount: (totalStrictStarv / totalBaselineRuns).toFixed(2)
};

const tunedSmartOPD = {
  avgEmergencyWait: (totalSmartTunedEmerg / totalBaselineRuns).toFixed(2),
  avgWalkinWait: (totalSmartTunedWalkin / totalBaselineRuns).toFixed(2),
  maxScheduledWait: (totalSmartTunedMaxSched / totalBaselineRuns).toFixed(2),
  starvationCount: (totalSmartTunedStarv / totalBaselineRuns).toFixed(2)
};

// Sweep combinations
const results = [];

FAIRNESS_BOOSTS.forEach(boost => {
  WAIT_WEIGHTS.forEach(waitWeight => {
    FAIRNESS_THRESHOLDS.forEach(schedThresh => {
      let smartEmergSum = 0;
      let smartWalkinSum = 0;
      let smartMaxSchedSum = 0;
      let smartStarvSum = 0;
      let totalRuns = 0;

      const policySettings = {
        FAIRNESS_BOOST: boost,
        WAIT_WEIGHT_PER_MIN: waitWeight,
        FAIRNESS_THRESHOLD_SCHEDULED: schedThresh,
        FAIRNESS_THRESHOLD_WALKIN: 45,
        SEVERITY_WEIGHT: 100,
        MAX_CONSECUTIVE_EMERGENCIES: 2,
        APPOINTMENT_URGENCY_MAX: 60
      };

      PRESET_KEYS.forEach(presetKey => {
        SEEDS.forEach(seed => {
          const runConfig = {
            ...Sim.PRESETS[presetKey],
            randomSeed: seed,
            policySettings
          };
          const simRes = Sim.runSimulation(runConfig);
          const sm = simRes.policies.smartopd.metrics;

          smartEmergSum += sm.averageWaitEmergency;
          smartWalkinSum += sm.averageWaitWalkin;
          smartMaxSchedSum += sm.maxScheduledWait;
          smartStarvSum += sm.starvationCount;
          totalRuns++;
        });
      });

      const avgEmerg = smartEmergSum / totalRuns;
      const avgWalkin = smartWalkinSum / totalRuns;
      const avgMaxSched = smartMaxSchedSum / totalRuns;
      const avgStarv = smartStarvSum / totalRuns;

      results.push({
        boost,
        waitWeight,
        schedThresh,
        smart: {
          avgEmergencyWait: avgEmerg,
          avgWalkinWait: avgWalkin,
          maxScheduledWait: avgMaxSched,
          starvationCount: avgStarv
        },
        fcfs: baselineFCFS,
        strict: baselineStrict
      });
    });
  });
});

const elapsed = ((Date.now() - t0) / 1000).toFixed(2);
console.log(`Sweep completed in ${elapsed}s.\n`);

// Filter: keep average emergency wait under 10 minutes
const qualified = results.filter(r => r.smart.avgEmergencyWait < 10.0);

// Sort: Minimising starvation count, then max scheduled wait, then avg emergency wait
qualified.sort((a, b) => {
  if (Math.abs(a.smart.starvationCount - b.smart.starvationCount) > 0.001) {
    return a.smart.starvationCount - b.smart.starvationCount;
  }
  if (Math.abs(a.smart.maxScheduledWait - b.smart.maxScheduledWait) > 0.001) {
    return a.smart.maxScheduledWait - b.smart.maxScheduledWait;
  }
  return a.smart.avgEmergencyWait - b.smart.avgEmergencyWait;
});

const top5 = qualified.slice(0, 5);

// Print Averaged Policy Comparison across Seeds 1-30 and 3 Presets (TASK 4 requirement)
console.log('=======================================================================================================================================');
console.log('                 SEEDS 1–30 AVERAGED OVER THREE PRESETS (90 RUNS): FCFS vs STRICT PRIORITY vs SmartOPD                                 ');
console.log('=======================================================================================================================================');
console.log(
  'Policy'.padEnd(30) +
  'Emergency Wait'.padEnd(20) +
  'Walk-in Wait'.padEnd(20) +
  'Max Sched Wait'.padEnd(20) +
  'Starvation Count'
);
console.log('-'.repeat(108));
console.log(
  'FCFS (Baseline)'.padEnd(30) +
  `${baselineFCFS.avgEmergencyWait} min`.padEnd(20) +
  `${baselineFCFS.avgWalkinWait} min`.padEnd(20) +
  `${baselineFCFS.maxScheduledWait} min`.padEnd(20) +
  `${baselineFCFS.starvationCount} patients`
);
console.log(
  'Strict Priority'.padEnd(30) +
  `${baselineStrict.avgEmergencyWait} min`.padEnd(20) +
  `${baselineStrict.avgWalkinWait} min`.padEnd(20) +
  `${baselineStrict.maxScheduledWait} min`.padEnd(20) +
  `${baselineStrict.starvationCount} patients`
);
console.log(
  'SmartOPD (Tuned: 150/3/45)'.padEnd(30) +
  `${tunedSmartOPD.avgEmergencyWait} min`.padEnd(20) +
  `${tunedSmartOPD.avgWalkinWait} min`.padEnd(20) +
  `${tunedSmartOPD.maxScheduledWait} min`.padEnd(20) +
  `${tunedSmartOPD.starvationCount} patients`
);
console.log('=======================================================================================================================================\n');

// Print Top 5 Table
console.log('=======================================================================================================================================');
console.log('                                             TOP 5 PARAMETER COMBINATIONS                                                              ');
console.log('                      (Criterion: Avg Emergency Wait < 10m, Minimising Max Scheduled Wait & Starvation)                               ');
console.log('=======================================================================================================================================');
console.log(
  'Rank'.padEnd(6) +
  'Boost'.padEnd(8) +
  'Weight'.padEnd(8) +
  'Thresh'.padEnd(8) +
  'SmartOPD Emerg'.padEnd(16) +
  'SmartOPD Max'.padEnd(14) +
  'SmartOPD Starv'.padEnd(18) +
  'Strict (Em/Max/St)'.padEnd(22) +
  'FCFS (Em/Max/St)'.padEnd(20)
);
console.log('-'.repeat(135));

top5.forEach((item, index) => {
  const rank = `#${index + 1}`.padEnd(6);
  const boost = String(item.boost).padEnd(8);
  const weight = `${item.waitWeight} pts/m`.padEnd(8);
  const thresh = `${item.schedThresh}m`.padEnd(8);
  const smEmerg = `${item.smart.avgEmergencyWait.toFixed(2)} min`.padEnd(16);
  const smMax = `${item.smart.maxScheduledWait.toFixed(1)} min`.padEnd(14);
  const smStarv = `${item.smart.starvationCount.toFixed(2)} patients`.padEnd(18);
  const strictStr = `${baselineStrict.avgEmergencyWait}m / ${baselineStrict.maxScheduledWait}m / ${baselineStrict.starvationCount}`.padEnd(22);
  const fcfsStr = `${baselineFCFS.avgEmergencyWait}m / ${baselineFCFS.maxScheduledWait}m / ${baselineFCFS.starvationCount}`.padEnd(20);

  console.log(`${rank}${boost}${weight}${thresh}${smEmerg}${smMax}${smStarv}${strictStr}${fcfsStr}`);
});
console.log('=======================================================================================================================================\n');

console.log('Chosen from a 48-combination sweep over seeds 1-30; weight 3 keeps severity dominant (about 33 minutes of waiting to climb one severity level).\n');

// Print Seed 42 Benchmark Tables for Busy Day and Emergency Surge
console.log('=======================================================================================================================================');
console.log('                                         BENCHMARK RUN: SEED 42 (CURRENT DEFAULTS)                                                     ');
console.log('=======================================================================================================================================\n');

function printSeed42Table(presetKey) {
  const preset = Sim.PRESETS[presetKey];
  const res = Sim.runSimulation({ ...preset, randomSeed: 42 });
  console.log(`PRESET: ${res.config.name || preset.name} (Seed 42) | Offered Load: ${res.offeredLoadPercent}% | Patients: ${res.patientsCount}`);
  console.log('-'.repeat(105));
  
  const mF = res.policies.fcfs.metrics;
  const mS = res.policies.strictPriority.metrics;
  const mO = res.policies.smartopd.metrics;

  const rows = [
    { metric: 'Average Wait (Overall)', unit: 'min', fcfs: mF.averageWaitOverall, strict: mS.averageWaitOverall, smart: mO.averageWaitOverall },
    { metric: 'Emergency Average Wait', unit: 'min', fcfs: mF.averageWaitEmergency, strict: mS.averageWaitEmergency, smart: mO.averageWaitEmergency },
    { metric: 'High Severity (4-5) Avg Wait', unit: 'min', fcfs: mF.averageWaitHighSeverity, strict: mS.averageWaitHighSeverity, smart: mO.averageWaitHighSeverity },
    { metric: 'Scheduled Patients Avg Wait', unit: 'min', fcfs: mF.averageWaitScheduled, strict: mS.averageWaitScheduled, smart: mO.averageWaitScheduled },
    { metric: 'Walk-in Patients Avg Wait', unit: 'min', fcfs: mF.averageWaitWalkin, strict: mS.averageWaitWalkin, smart: mO.averageWaitWalkin },
    { metric: '90th Percentile Wait (P90)', unit: 'min', fcfs: mF.p90Wait, strict: mS.p90Wait, smart: mO.p90Wait },
    { metric: 'Maximum Scheduled Wait', unit: 'min', fcfs: mF.maxScheduledWait, strict: mS.maxScheduledWait, smart: mO.maxScheduledWait },
    { metric: 'Scheduled Starvation Count', unit: 'patients', fcfs: mF.starvationCount, strict: mS.starvationCount, smart: mO.starvationCount },
    { metric: 'Doctor Idle Capacity', unit: '%', fcfs: mF.doctorIdlePercent, strict: mS.doctorIdlePercent, smart: mO.doctorIdlePercent }
  ];

  console.log('Metric'.padEnd(36) + 'FCFS (Baseline)'.padEnd(22) + 'Strict Priority'.padEnd(22) + 'SmartOPD (Fairness)');
  console.log('-'.repeat(105));
  rows.forEach(r => {
    const fStr = (r.unit === 'patients' ? `${r.fcfs} ${r.fcfs === 1 ? 'patient' : 'patients'}` : `${r.fcfs} ${r.unit}`).padEnd(22);
    const sStr = (r.unit === 'patients' ? `${r.strict} ${r.strict === 1 ? 'patient' : 'patients'}` : `${r.strict} ${r.unit}`).padEnd(22);
    const oStr = (r.unit === 'patients' ? `${r.smart} ${r.smart === 1 ? 'patient' : 'patients'}` : `${r.smart} ${r.unit}`);
    console.log(r.metric.padEnd(36) + fStr + sStr + oStr);
  });
  console.log('-'.repeat(105));
  console.log('Key Insights:');
  res.insights.forEach(ins => console.log(' • ' + ins));
  console.log('\n');
}

printSeed42Table('busy-day');
printSeed42Table('emergency-surge');
