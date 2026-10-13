'use strict';
const path = require('path');
const admin = require('firebase-admin');
const cred = require(path.resolve(__dirname, 'service-account.json'));
admin.initializeApp({ credential: admin.credential.cert(cred) });
const db = admin.firestore();

const COLLECTIONS = ['issues', 'users', 'cityPosts', 'news', 'chats', 'notifications'];

function looksLikeBase64Image(s) {
  if (typeof s !== 'string') return false;
  if (s.length < 1000) return false;
  return /^data:image\//.test(s) || /^\/9j\//.test(s) || /^iVBORw/.test(s) || /^R0lGOD/.test(s);
}

function describe(value, depth = 0) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'string') {
    if (looksLikeBase64Image(value)) {
      return { type: 'BASE64_IMAGE', length: value.length, preview: value.slice(0, 60) + '...' };
    }
    if (value.length > 2000) {
      return { type: 'long_string', length: value.length, preview: value.slice(0, 80) + '...' };
    }
    if (/^https?:\/\//.test(value)) {
      return { type: 'url', value };
    }
    return null;
  }
  if (Array.isArray(value)) {
    const items = value.map((v) => describe(v, depth + 1)).filter(Boolean);
    return items.length > 0 ? items : null;
  }
  if (typeof value === 'object') {
    const result = {};
    let hasAny = false;
    for (const k of Object.keys(value)) {
      const r = describe(value[k], depth + 1);
      if (r) {
        result[k] = r;
        hasAny = true;
      }
    }
    return hasAny ? result : null;
  }
  return null;
}

(async () => {
  let totalDocs = 0;
  let totalUrls = 0;
  let totalLongStrings = 0;
  let totalBase64Images = 0;
  const byField = new Map();
  const longStringExamples = [];
  const base64Examples = [];

  for (const col of COLLECTIONS) {
    const snap = await db.collection(col).get();
    console.log(`\n=== ${col}: ${snap.docs.length} docs ===`);
    let colUrls = 0;
    let colLongs = 0;
    let colB64 = 0;
    for (const doc of snap.docs) {
      totalDocs++;
      const data = doc.data();
      for (const [field, value] of Object.entries(data)) {
        const stack = [{ key: field, val: value }];
        while (stack.length > 0) {
          const { key, val } = stack.pop();
          if (val === null || val === undefined) continue;
          if (Array.isArray(val)) {
            val.forEach((v) => stack.push({ key: key + '[]', val: v }));
            continue;
          }
          if (typeof val === 'object') {
            for (const [k, v] of Object.entries(val)) stack.push({ key: key + '.' + k, val: v });
            continue;
          }
          if (typeof val !== 'string') continue;
          if (looksLikeBase64Image(val)) {
            totalBase64Images++;
            colB64++;
            base64Examples.push({ col, docId: doc.id, field: key, length: val.length });
            byField.set(key, (byField.get(key) || 0) + 1);
          } else if (val.length > 2000) {
            totalLongStrings++;
            colLongs++;
            if (longStringExamples.length < 5) {
              longStringExamples.push({ col, docId: doc.id, field: key, length: val.length, preview: val.slice(0, 100) });
            }
          } else if (/^https?:\/\//.test(val)) {
            totalUrls++;
            colUrls++;
          }
        }
      }
    }
    console.log(`  URLs: ${colUrls}  long strings (>2KB): ${colLongs}  base64 images: ${colB64}`);
  }

  console.log('\n=== Summary ===');
  console.log(`Total docs scanned: ${totalDocs}`);
  console.log(`URL strings:        ${totalUrls}`);
  console.log(`Long strings:       ${totalLongStrings}`);
  console.log(`Base64 images:      ${totalBase64Images}`);

  if (base64Examples.length > 0) {
    console.log('\nBase64 image examples:');
    for (const e of base64Examples.slice(0, 10)) {
      console.log(`  ${e.col}/${e.docId} -> ${e.field} (${e.length} chars)`);
    }
  }

  if (longStringExamples.length > 0) {
    console.log('\nLong-string (suspicious) examples:');
    for (const e of longStringExamples) {
      console.log(`  ${e.col}/${e.docId} -> ${e.field} (${e.length} chars): "${e.preview}..."`);
    }
  }

  process.exit(0);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
