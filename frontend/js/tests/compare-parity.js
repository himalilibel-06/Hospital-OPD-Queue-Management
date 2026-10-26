const http = require('http');
const Sim = require('../simulator.js');

function postJson(urlPath, data, token) {
  return new Promise((resolve, reject) => {
    const postData = JSON.stringify(data);
    const req = http.request({
      hostname: 'localhost',
      port: 8080,
      path: urlPath,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData),
        'Authorization': 'Bearer ' + token
      }
    }, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(body) }));
    });
    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

function login() {
  return new Promise((resolve, reject) => {
    const postData = JSON.stringify({ username: 'reception', password: 'rec123' });
    const req = http.request({
      hostname: 'localhost',
      port: 8080,
      path: '/api/auth/login',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    }, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve(JSON.parse(body).token));
    });
    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

async function main() {
  const token = await login();
  const cases = [
    { name: 'Busy Day (Seed 42)', preset: 'busy-day', seed: 42 },
    { name: 'Emergency Surge (Seed 42)', preset: 'emergency-surge', seed: 42 }
  ];

  for (const c of cases) {
    console.log('\n============================================================');
    console.log('   ' + c.name);
    console.log('============================================================');
    const apiRes = await postJson('/api/simulation/run', { preset: c.preset, randomSeed: c.seed }, token);
    const jsRes = Sim.runSimulation({ ...Sim.PRESETS[c.preset], randomSeed: c.seed });

    console.log('\n--- Live Spring Boot REST API Output (POST /api/simulation/run) ---');
    console.log('| Policy | Avg Wait | Emerg Wait | Walk-in Wait | Sched Wait | Max Sched | Starvation |');
    console.log('|---|---|---|---|---|---|---|');
    for (const [pol, key] of [['FCFS', 'fcfs'], ['Strict Priority', 'strictPriority'], ['SmartOPD', 'smartopd']]) {
      const m = apiRes.body.policies[key].metrics;
      console.log(`| ${pol} | ${m.avgWaitOverall}m | ${m.avgWaitEmergency}m | ${m.avgWaitWalkin}m | ${m.avgWaitScheduled}m | ${m.maxScheduledWait}m | ${m.starvationCount} |`);
    }

    console.log('\n--- JavaScript Simulator Executable Spec Output ---');
    console.log('| Policy | Avg Wait | Emerg Wait | Walk-in Wait | Sched Wait | Max Sched | Starvation |');
    console.log('|---|---|---|---|---|---|---|');
    for (const [pol, key] of [['FCFS', 'fcfs'], ['Strict Priority', 'strictPriority'], ['SmartOPD', 'smartopd']]) {
      const m = jsRes.policies[key].metrics;
      console.log(`| ${pol} | ${m.avgWaitOverall}m | ${m.avgWaitEmergency}m | ${m.avgWaitWalkin}m | ${m.avgWaitScheduled}m | ${m.maxScheduledWait}m | ${m.starvationCount} |`);
    }

    // Verify parity
    let identical = true;
    for (const [pol, key] of [['FCFS', 'fcfs'], ['Strict Priority', 'strictPriority'], ['SmartOPD', 'smartopd']]) {
      const mApi = apiRes.body.policies[key].metrics;
      const mJs = jsRes.policies[key].metrics;
      if (mApi.avgWaitOverall !== mJs.avgWaitOverall ||
          mApi.avgWaitEmergency !== mJs.avgWaitEmergency ||
          mApi.avgWaitWalkin !== mJs.avgWaitWalkin ||
          mApi.avgWaitScheduled !== mJs.avgWaitScheduled ||
          mApi.maxScheduledWait !== mJs.maxScheduledWait ||
          mApi.starvationCount !== mJs.starvationCount) {
        identical = false;
      }
    }
    console.log(`\nParity Result for ${c.name}: ${identical ? '100% IDENTICAL MATCH' : 'MISMATCH'}`);
  }
}

main().catch(console.error);
