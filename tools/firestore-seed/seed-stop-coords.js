#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const DRY_RUN = process.argv.includes('--dry-run');
const FORCE = process.argv.includes('--force');

const SERVICE_ACCOUNT_PATH = path.resolve(__dirname, 'service-account.json');
const COLLECTION_STOPS = 'transportStops';
const BATCH_LIMIT = 450;

const BBOX = { south: 53.83, west: 30.15, north: 54.02, east: 30.55 };
const OVERPASS_URL = 'https://overpass-api.de/api/interpreter';

function fail(msg) {
  console.error('ERROR: ' + msg);
  process.exit(1);
}

const STRIP_WORDS = new Set([
  'остановка', 'ост',
  'улица', 'ул',
  'проспект', 'пр', 'просп',
  'переулок', 'пер',
  'площадь', 'пл',
  'набережная', 'наб',
  'поселок', 'посёлок', 'пос',
  'деревня', 'дер',
  'микрорайон', 'мкр',
  'предприятие',
  'оао', 'зао', 'ип', 'чуп', 'чтуп', 'суп', 'упп', 'руп',
  'имени', 'им',
]);

function normalize(name) {
  if (!name) return '';
  let s = name
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/«|»|"|'|„|”/g, '')
    .replace(/\([^)]*\)/g, '')
    .replace(/\b\p{L}{1,5}\./gu, ' ')
    .replace(/№/g, '')
    .replace(/[—–-]/g, ' ')
    .replace(/[^\p{L}\p{N}\s]/gu, '')
    .replace(/\s+/g, ' ')
    .trim();

  const tokens = s.split(/\s+/).filter((t) => t && !STRIP_WORDS.has(t));
  return tokens.join(' ').trim();
}

async function fetchOverpass() {
  const query = `[out:json][timeout:60];
(
  node["highway"="bus_stop"](${BBOX.south},${BBOX.west},${BBOX.north},${BBOX.east});
  node["public_transport"="stop_position"](${BBOX.south},${BBOX.west},${BBOX.north},${BBOX.east});
  node["public_transport"="platform"](${BBOX.south},${BBOX.west},${BBOX.north},${BBOX.east});
);
out body;`;

  console.log('[overpass] querying ' + OVERPASS_URL + ' ...');
  const response = await fetch(OVERPASS_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'User-Agent': 'MyCity-Seed/1.0 (admin tool)',
    },
    body: 'data=' + encodeURIComponent(query),
  });
  if (!response.ok) {
    const text = await response.text().catch(() => '');
    fail('Overpass HTTP ' + response.status + ': ' + text.slice(0, 200));
  }
  const json = await response.json();
  return json.elements || [];
}

function buildOsmIndex(elements) {
  const idx = new Map();
  for (const el of elements) {
    if (!el.tags) continue;
    const candidates = [el.tags.name, el.tags['name:ru'], el.tags['name:be']];
    for (const raw of candidates) {
      if (!raw) continue;
      const norm = normalize(raw);
      if (!norm) continue;
      if (!idx.has(norm)) idx.set(norm, []);
      idx.get(norm).push({ lat: el.lat, lon: el.lon, raw });
    }
  }
  return idx;
}

function averageCoords(coords) {
  let lat = 0;
  let lon = 0;
  for (const c of coords) {
    lat += c.lat;
    lon += c.lon;
  }
  return { lat: lat / coords.length, lon: lon / coords.length };
}

function findMatch(stopName, osmIdx) {
  const norm = normalize(stopName);
  if (!norm) return null;

  if (osmIdx.has(norm)) {
    const coords = osmIdx.get(norm);
    return {
      lat: averageCoords(coords).lat,
      lon: averageCoords(coords).lon,
      matchType: 'exact',
      count: coords.length,
    };
  }

  let best = null;
  let bestScore = 0;
  for (const [osmNorm, coords] of osmIdx.entries()) {
    let score = 0;
    if (osmNorm === norm) score = 1.0;
    else if (osmNorm.includes(norm) && norm.length >= 4) {
      score = norm.length / osmNorm.length;
    } else if (norm.includes(osmNorm) && osmNorm.length >= 4) {
      score = osmNorm.length / norm.length;
    }
    if (score > bestScore) {
      bestScore = score;
      const avg = averageCoords(coords);
      best = {
        lat: avg.lat,
        lon: avg.lon,
        matchType: 'fuzzy',
        score: score,
        osmName: osmNorm,
        count: coords.length,
      };
    }
  }
  return bestScore >= 0.5 ? best : null;
}

async function commitUpdates(db, updates) {
  let batch = db.batch();
  let inBatch = 0;
  let written = 0;
  for (const u of updates) {
    batch.update(u.ref, { lat: u.lat, lng: u.lng });
    inBatch++;
    written++;
    if (inBatch >= BATCH_LIMIT) {
      await batch.commit();
      batch = db.batch();
      inBatch = 0;
    }
  }
  if (inBatch > 0) await batch.commit();
  return written;
}

(async () => {
  if (!DRY_RUN && !fs.existsSync(SERVICE_ACCOUNT_PATH)) {
    fail('Service account key not found at ' + SERVICE_ACCOUNT_PATH);
  }

  const elements = await fetchOverpass();
  console.log('[overpass] got ' + elements.length + ' transit nodes in Mogilev bbox');

  const osmIdx = buildOsmIndex(elements);
  console.log('[index] ' + osmIdx.size + ' unique normalized names');

  let db = null;
  if (!DRY_RUN) {
    const credential = require(SERVICE_ACCOUNT_PATH);
    admin.initializeApp({ credential: admin.credential.cert(credential) });
    db = admin.firestore();
  }

  let stops;
  if (DRY_RUN) {
    const jsonPath = path.resolve(__dirname, '..', 'transit_scraper', 'output', 'transit_mogilev.json');
    const data = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
    stops = data.stops.map((s, i) => ({ id: 'dry-' + i, data: () => s, ref: null }));
  } else {
    const snap = await db.collection(COLLECTION_STOPS).get();
    stops = snap.docs;
  }
  console.log('[firestore] ' + stops.length + ' stops to process');

  const updates = [];
  const unmatched = [];
  let skippedAlreadySet = 0;
  let exactCount = 0;
  let fuzzyCount = 0;

  for (const doc of stops) {
    const data = doc.data();
    const name = data.name;
    if (!name) continue;

    const hasCoords = (data.lat && data.lat !== 0) || (data.lng && data.lng !== 0);
    if (hasCoords && !FORCE) {
      skippedAlreadySet++;
      continue;
    }

    const match = findMatch(name, osmIdx);
    if (match) {
      if (match.matchType === 'exact') exactCount++;
      else fuzzyCount++;
      updates.push({ ref: doc.ref, lat: match.lat, lng: match.lon, name, info: match });
    } else {
      unmatched.push(name);
    }
  }

  console.log('\n=== Match report ===');
  console.log('Total stops:        ' + stops.length);
  console.log('Already had coords: ' + skippedAlreadySet + (FORCE ? ' (re-processing due to --force)' : ' (skipped, use --force to override)'));
  console.log('Matched exactly:    ' + exactCount);
  console.log('Matched fuzzy:      ' + fuzzyCount);
  console.log('Unmatched:          ' + unmatched.length);
  const total = exactCount + fuzzyCount + unmatched.length;
  if (total > 0) {
    console.log('Match rate:         ' + ((exactCount + fuzzyCount) / total * 100).toFixed(1) + '%');
  }

  if (unmatched.length > 0) {
    console.log('\nUnmatched stop names (first 30):');
    for (const n of unmatched.slice(0, 30)) console.log('  - ' + n);
    if (unmatched.length > 30) console.log('  ...and ' + (unmatched.length - 30) + ' more');
  }

  if (DRY_RUN) {
    console.log('\n[dry-run] not writing to Firestore');
    process.exit(0);
  }

  if (updates.length === 0) {
    console.log('\nNothing to update.');
    process.exit(0);
  }

  console.log('\n[write] updating ' + updates.length + ' stops...');
  const written = await commitUpdates(db, updates);
  console.log('[write] ' + written + ' stops updated');
  console.log('Done.');
  process.exit(0);
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
