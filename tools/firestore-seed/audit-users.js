'use strict';
const path = require('path');
const admin = require('firebase-admin');
const cred = require(path.resolve(__dirname, 'service-account.json'));
admin.initializeApp({ credential: admin.credential.cert(cred) });
const db = admin.firestore();
(async () => {
  const users = await db.collection('users').get();
  console.log('=== users (' + users.docs.length + ') ===');
  for (const d of users.docs) {
    const x = d.data();
    console.log('  ' + d.id + ' :: displayName="' + (x.displayName || '') + '" email="' + (x.email || '') + '" role="' + (x.role || '') + '"');
  }
  console.log('');
  const chats = await db.collection('chats').get();
  console.log('=== chats (' + chats.docs.length + ') ===');
  for (const d of chats.docs) {
    const x = d.data();
    console.log('  ' + d.id);
    console.log('    type: ' + x.type);
    console.log('    participantIds: ' + JSON.stringify(x.participantIds));
    console.log('    participantNames: ' + JSON.stringify(x.participantNames));
    console.log('    lastMessageAt: ' + (x.lastMessageAt ? x.lastMessageAt.toDate() : 'null'));
  }
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
