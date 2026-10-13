#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const DRY_RUN = process.argv.includes('--dry-run');

const SERVICE_ACCOUNT_PATH = path.resolve(__dirname, 'service-account.json');
const JSON_PATH = path.resolve(__dirname, '..', 'transit_scraper', 'output', 'transit_mogilev.json');
const COLLECTION_ROUTES = 'transportRoutes';
const BATCH_LIMIT = 450;

function fail(msg) {
  console.error('ERROR: ' + msg);
  process.exit(1);
}

const MAX_REASONABLE_NAME = 200;

function deriveCleanName(badName, stopNames) {
  if (!badName) return badName;
  if (badName.length <= MAX_REASONABLE_NAME) {
    return badName.replace(/\s+/g, ' ').trim();
  }
  let cleanest = badName;
  for (const stopName of stopNames) {
    if (!stopName) continue;
    const idx = cleanest.indexOf(stopName);
    if (idx > 0) {
      cleanest = cleanest.slice(0, idx).trim();
    }
  }
  return cleanest.replace(/\s+/g, ' ').trim();
}

(async () => {
  if (!fs.existsSync(JSON_PATH)) fail('Source JSON not found: ' + JSON_PATH);
  if (!DRY_RUN && !fs.existsSync(SERVICE_ACCOUNT_PATH)) {
    fail('Service account key not found at ' + SERVICE_ACCOUNT_PATH);
  }

  const json = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
  const stops = json.stops || [];
  const routes = json.routes || [];

  console.log('Source: ' + routes.length + ' routes, ' + stops.length + ' stops');

  let db = null;
  if (!DRY_RUN) {
    const credential = require(SERVICE_ACCOUNT_PATH);
    admin.initializeApp({ credential: admin.credential.cert(credential) });
    db = admin.firestore();
  }

  let firestoreRoutes;
  if (DRY_RUN) {
    firestoreRoutes = routes.map((r) => ({ id: 'dry-' + r.number, data: () => ({ routeNumber: r.number, name: r.name }), ref: null }));
  } else {
    const snap = await db.collection(COLLECTION_ROUTES).get();
    firestoreRoutes = snap.docs;
    console.log('Firestore: ' + firestoreRoutes.length + ' route docs');
  }

  const byNumber = new Map();
  for (const r of routes) {
    if (!byNumber.has(String(r.number))) byNumber.set(String(r.number), []);
    byNumber.get(String(r.number)).push(r);
  }

  const updates = [];
  const skipped = [];
  let suspicious = 0;

  for (const doc of firestoreRoutes) {
    const data = doc.data();
    const number = String(data.routeNumber || '');
    const candidates = byNumber.get(number) || [];

    let source = candidates.find((c) => c.name === data.name);
    if (!source && candidates.length === 1) source = candidates[0];
    if (!source && candidates.length > 0) {
      source = candidates.find((c) => c.type === data.type) || candidates[0];
    }

    if (!source) {
      skipped.push({ number, reason: 'no JSON source for route #' + number });
      continue;
    }

    const stopNames = (source.stops || [])
      .map((idx) => (Number.isInteger(idx) && stops[idx] ? stops[idx].name : null))
      .filter(Boolean);

    const cleanName = deriveCleanName(source.name, stopNames);

    if (!cleanName || cleanName.length === 0) {
      skipped.push({ number, reason: 'empty after cleanup', oldLen: data.name ? data.name.length : 0 });
      continue;
    }

    if (cleanName === data.name) {
      continue;
    }

    if (cleanName.length > 200) {
      suspicious++;
      skipped.push({ number, reason: 'cleaned still >200 chars (' + cleanName.length + ')', preview: cleanName.slice(0, 80) });
      continue;
    }

    updates.push({ ref: doc.ref, number, oldLen: data.name ? data.name.length : 0, newName: cleanName });
  }

  console.log('\n=== Plan ===');
  console.log('To update: ' + updates.length);
  console.log('Skipped:   ' + skipped.length + (suspicious ? ' (' + suspicious + ' still too long)' : ''));

  console.log('\nFirst 10 updates:');
  for (const u of updates.slice(0, 10)) {
    console.log('  #' + u.number + ': ' + u.oldLen + ' -> ' + u.newName.length + ' chars: "' + u.newName + '"');
  }

  if (skipped.length > 0) {
    console.log('\nSkipped:');
    for (const s of skipped.slice(0, 10)) {
      console.log('  #' + s.number + ' -- ' + s.reason + (s.preview ? ' :: ' + s.preview : ''));
    }
  }

  if (DRY_RUN) {
    console.log('\n[dry-run] not writing to Firestore');
    process.exit(0);
  }

  if (updates.length === 0) {
    console.log('\nNothing to update.');
    process.exit(0);
  }

  console.log('\n[write] updating ' + updates.length + ' routes...');
  let batch = db.batch();
  let inBatch = 0;
  for (const u of updates) {
    batch.update(u.ref, { name: u.newName });
    inBatch++;
    if (inBatch >= BATCH_LIMIT) {
      await batch.commit();
      batch = db.batch();
      inBatch = 0;
    }
  }
  if (inBatch > 0) await batch.commit();

  console.log('[write] ' + updates.length + ' route names cleaned');
  console.log('Done.');
  process.exit(0);
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
