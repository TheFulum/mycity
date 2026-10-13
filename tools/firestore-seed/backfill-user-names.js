'use strict';
const path = require('path');
const admin = require('firebase-admin');

const DRY_RUN = process.argv.includes('--dry-run');
const cred = require(path.resolve(__dirname, 'service-account.json'));
admin.initializeApp({ credential: admin.credential.cert(cred) });
const db = admin.firestore();
const auth = admin.auth();

function fallbackFromEmail(email) {
  if (!email) return null;
  const local = email.split('@')[0];
  if (!local) return null;
  const tokens = local.split(/[._-]+/).filter((t) => t.length > 0);
  const providerStop = new Set(['yandex', 'gmail', 'mail', 'rambler', 'outlook', 'hotmail', 'icloud', 'yahoo']);
  const tldStop = new Set(['ru', 'by', 'com', 'net', 'org', 'ua', 'kz']);
  while (tokens.length > 1) {
    const last = tokens[tokens.length - 1].toLowerCase();
    if (tldStop.has(last) || providerStop.has(last)) tokens.pop();
    else break;
  }
  if (tokens.length === 0) return null;
  return tokens.map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ');
}

async function fetchAllAuthUsers() {
  const users = new Map();
  let token = undefined;
  do {
    const page = await auth.listUsers(1000, token);
    for (const u of page.users) users.set(u.uid, u);
    token = page.pageToken;
  } while (token);
  return users;
}

(async () => {
  const authUsers = await fetchAllAuthUsers();
  console.log('[auth] ' + authUsers.size + ' users');

  const snap = await db.collection('users').get();
  console.log('[firestore] ' + snap.docs.length + ' users\n');

  const userUpdates = [];
  for (const doc of snap.docs) {
    const data = doc.data();
    const uid = doc.id;
    const hasName = data.displayName && data.displayName.trim().length > 0;
    if (hasName) {
      console.log('  [skip] ' + uid + ' already has "' + data.displayName + '"');
      continue;
    }

    const authUser = authUsers.get(uid);
    let resolved = null;
    let source = null;

    if (authUser) {
      if (authUser.displayName && authUser.displayName.trim()) {
        resolved = authUser.displayName.trim();
        source = 'auth.displayName';
      } else if (authUser.providerData && authUser.providerData.length > 0) {
        for (const p of authUser.providerData) {
          if (p.displayName && p.displayName.trim()) {
            resolved = p.displayName.trim();
            source = 'auth.provider.' + p.providerId;
            break;
          }
        }
      }
    }

    if (!resolved) {
      const email = (authUser && authUser.email) || data.email;
      const phone = (authUser && authUser.phoneNumber) || data.phone;
      const fromEmail = fallbackFromEmail(email);
      if (fromEmail) {
        resolved = fromEmail;
        source = 'email-prefix';
      } else if (phone) {
        resolved = phone;
        source = 'phone';
      }
    }

    if (!resolved) {
      console.log('  [unresolved] ' + uid + ' — no auth info to derive name');
      continue;
    }

    userUpdates.push({ ref: doc.ref, uid, newName: resolved, source });
    console.log('  [update] ' + uid + ' -> "' + resolved + '" (from ' + source + ')');
  }

  if (DRY_RUN) {
    console.log('\n[dry-run] would update ' + userUpdates.length + ' users, then refresh chats');
    process.exit(0);
  }

  console.log('\n[write] updating ' + userUpdates.length + ' users...');
  let batch = db.batch();
  for (const u of userUpdates) batch.update(u.ref, { displayName: u.newName });
  if (userUpdates.length > 0) await batch.commit();
  console.log('[write] users updated');

  console.log('\n[refresh] regenerating participantNames in chats...');
  const usersAfter = await db.collection('users').get();
  const nameByUid = new Map();
  for (const d of usersAfter.docs) {
    const x = d.data();
    if (x.displayName && x.displayName.trim()) nameByUid.set(d.id, x.displayName.trim());
  }

  const chats = await db.collection('chats').get();
  let chatsUpdated = 0;
  let chatBatch = db.batch();
  for (const doc of chats.docs) {
    const data = doc.data();
    const pids = data.participantIds || [];
    const oldNames = data.participantNames || {};
    const newNames = {};
    let changed = false;
    for (const pid of pids) {
      const fromUsers = nameByUid.get(pid);
      const existing = oldNames[pid];
      const next = fromUsers || existing;
      if (next) newNames[pid] = next;
      if (next !== existing) changed = true;
    }
    if (changed) {
      chatBatch.update(doc.ref, { participantNames: newNames });
      chatsUpdated++;
      console.log('  [chat] ' + doc.id + ' -> ' + JSON.stringify(newNames));
    }
  }
  if (chatsUpdated > 0) await chatBatch.commit();
  console.log('[refresh] ' + chatsUpdated + ' chats updated');
  console.log('\nDone.');
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
