#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const DRY_RUN = process.argv.includes('--dry-run');
const PURGE = process.argv.includes('--purge');

const SERVICE_ACCOUNT_PATH = path.resolve(__dirname, 'service-account.json');
const INPUT_JSON = path.resolve(
  __dirname,
  '..',
  'transit_scraper',
  'output',
  'transit_mogilev.json'
);

const COLLECTION_ROUTES = 'transportRoutes';
const COLLECTION_STOPS = 'transportStops';
const BATCH_LIMIT = 450;

const ROUTE_TYPE_MAP = {
  BUS: 'BUS',
  TRAM: 'TRAM',
  TROLLEY: 'TROLLEYBUS',
  TROLLEYBUS: 'TROLLEYBUS',
  MARSHRUTKA: 'MARSHRUTKA',
  MINIBUS: 'MARSHRUTKA',
};

function fail(msg) {
  console.error('ERROR: ' + msg);
  process.exit(1);
}

function loadPayload() {
  if (!fs.existsSync(INPUT_JSON)) {
    fail(
      'Seed file not found: ' +
        INPUT_JSON +
        '\nRun the scraper first: cd ../transit_scraper && npm install && npm run scrape'
    );
  }
  const raw = fs.readFileSync(INPUT_JSON, 'utf8');
  const data = JSON.parse(raw);
  if (!Array.isArray(data.stops) || !Array.isArray(data.routes)) {
    fail('Malformed JSON — expected { stops: [...], routes: [...] }');
  }
  return data;
}

function initAdmin() {
  if (DRY_RUN) {
    console.log('[dry-run] skipping firebase-admin init');
    return null;
  }
  if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
    fail(
      'Service account key not found at ' +
        SERVICE_ACCOUNT_PATH +
        '\nDownload from Firebase Console -> Project Settings -> Service Accounts -> Generate new private key'
    );
  }
  const credential = require(SERVICE_ACCOUNT_PATH);
  admin.initializeApp({ credential: admin.credential.cert(credential) });
  return admin.firestore();
}

async function purgeCollection(db, name) {
  if (!db) return;
  console.log('[purge] clearing collection ' + name + '...');
  const snap = await db.collection(name).get();
  let removed = 0;
  let batch = db.batch();
  for (const doc of snap.docs) {
    batch.delete(doc.ref);
    removed++;
    if (removed % BATCH_LIMIT === 0) {
      await batch.commit();
      batch = db.batch();
    }
  }
  if (removed % BATCH_LIMIT !== 0) await batch.commit();
  console.log('[purge] removed ' + removed + ' docs from ' + name);
}

async function commitInBatches(db, ops, label) {
  if (!db) {
    console.log('[dry-run] would write ' + ops.length + ' docs to ' + label);
    return;
  }
  let written = 0;
  let batch = db.batch();
  let inBatch = 0;
  for (const op of ops) {
    batch.set(op.ref, op.data);
    inBatch++;
    written++;
    if (inBatch >= BATCH_LIMIT) {
      await batch.commit();
      batch = db.batch();
      inBatch = 0;
    }
  }
  if (inBatch > 0) await batch.commit();
  console.log('[write] ' + written + ' docs to ' + label);
}

function buildStopDocs(db, stopsSrc) {
  const ops = [];
  const indexToId = new Array(stopsSrc.length).fill(null);
  for (let i = 0; i < stopsSrc.length; i++) {
    const src = stopsSrc[i];
    if (!src || !src.name) continue;
    const ref = db ? db.collection(COLLECTION_STOPS).doc() : { id: 'dry-stop-' + i };
    indexToId[i] = ref.id;
    ops.push({
      ref,
      data: {
        name: src.name,
        lat: typeof src.lat === 'number' ? src.lat : 0,
        lng: typeof src.lng === 'number' ? src.lng : 0,
        routeIds: [],
        gtfsStopId: null,
      },
    });
  }
  return { ops, indexToId };
}

function buildRouteDocs(db, routesSrc, indexToId) {
  const ops = [];
  const stopIdToRouteIds = new Map();
  let skipped = 0;
  for (const src of routesSrc) {
    if (!src || !src.number || !src.name || !Array.isArray(src.stops)) {
      skipped++;
      continue;
    }
    const ref = db ? db.collection(COLLECTION_ROUTES).doc() : { id: 'dry-route-' + src.number };
    const mappedStops = [];
    for (const idx of src.stops) {
      if (Number.isInteger(idx) && idx >= 0 && idx < indexToId.length && indexToId[idx]) {
        mappedStops.push(indexToId[idx]);
      }
    }
    for (const stopId of mappedStops) {
      if (!stopIdToRouteIds.has(stopId)) stopIdToRouteIds.set(stopId, []);
      stopIdToRouteIds.get(stopId).push(ref.id);
    }
    ops.push({
      ref,
      data: {
        routeNumber: String(src.number),
        name: src.name,
        type: ROUTE_TYPE_MAP[String(src.type || '').toUpperCase()] || 'BUS',
        color: src.color || '#3478F6',
        stopIds: mappedStops,
        scheduleTimes: Array.isArray(src.scheduleTimes) ? src.scheduleTimes : [],
        polyline: [],
        active: true,
        gtfsRouteId: null,
      },
    });
  }
  if (skipped > 0) console.log('[routes] skipped ' + skipped + ' invalid entries');
  return { ops, stopIdToRouteIds };
}

async function updateStopBackrefs(db, stopIdToRouteIds) {
  if (!db) {
    console.log('[dry-run] would update routeIds on ' + stopIdToRouteIds.size + ' stops');
    return;
  }
  let updated = 0;
  let batch = db.batch();
  let inBatch = 0;
  for (const [stopId, routeIds] of stopIdToRouteIds.entries()) {
    const ref = db.collection(COLLECTION_STOPS).doc(stopId);
    batch.update(ref, { routeIds });
    inBatch++;
    updated++;
    if (inBatch >= BATCH_LIMIT) {
      await batch.commit();
      batch = db.batch();
      inBatch = 0;
    }
  }
  if (inBatch > 0) await batch.commit();
  console.log('[backref] linked ' + updated + ' stops to their routes');
}

(async () => {
  const payload = loadPayload();
  console.log(
    'Loaded ' + payload.stops.length + ' stops and ' + payload.routes.length + ' routes from JSON'
  );

  const db = initAdmin();

  if (PURGE && db) {
    await purgeCollection(db, COLLECTION_STOPS);
    await purgeCollection(db, COLLECTION_ROUTES);
  } else if (PURGE && !db) {
    console.log('[dry-run] would purge collections ' + COLLECTION_STOPS + ' and ' + COLLECTION_ROUTES);
  }

  const { ops: stopOps, indexToId } = buildStopDocs(db, payload.stops);
  await commitInBatches(db, stopOps, COLLECTION_STOPS);

  const { ops: routeOps, stopIdToRouteIds } = buildRouteDocs(db, payload.routes, indexToId);
  await commitInBatches(db, routeOps, COLLECTION_ROUTES);

  await updateStopBackrefs(db, stopIdToRouteIds);

  console.log('Done.');
  process.exit(0);
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
