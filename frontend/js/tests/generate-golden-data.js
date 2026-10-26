const Sim = require('../simulator.js');
const presets = ['quiet-day', 'busy-day', 'emergency-surge'];
const seeds = [1, 7, 42];

console.log('| Preset | Seed | Policy | Avg Wait | Emerg Wait | High Sev | Sched Wait | Walk-in | Max Sched | Starv | p90 | Idle% |');
console.log('|---|---|---|---|---|---|---|---|---|---|---|---|');

const fullData = [];

for (const p of presets) {
  for (const s of seeds) {
    const cfg = { ...Sim.PRESETS[p], randomSeed: s };
    const res = Sim.runSimulation(cfg);
    fullData.push({
      preset: p,
      seed: s,
      offeredLoadPercent: res.offeredLoadPercent,
      fcfs: res.policies.fcfs.metrics,
      strict: res.policies.strictPriority.metrics,
      smart: res.policies.smartopd.metrics,
      insights: res.insights
    });
    for (const [pol, key] of [['FCFS', 'fcfs'], ['Strict', 'strictPriority'], ['SmartOPD', 'smartopd']]) {
      const m = res.policies[key].metrics;
      console.log(`| ${p} | ${s} | ${pol} | ${m.avgWaitOverall} | ${m.avgWaitEmergency} | ${m.avgWaitHighSeverity} | ${m.avgWaitScheduled} | ${m.avgWaitWalkin} | ${m.maxScheduledWait} | ${m.starvationCount} | ${m.p90Wait} | ${m.doctorIdlePercent}% |`);
    }
  }
}

const fs = require('fs');
fs.writeFileSync(__dirname + '/golden-simulation-data.json', JSON.stringify(fullData, null, 2));
console.log('\nWrote golden-simulation-data.json successfully.');
