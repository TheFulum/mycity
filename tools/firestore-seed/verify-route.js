'use strict';
const path = require('path');
const admin = require('firebase-admin');
const cred = require(path.resolve(__dirname, 'service-account.json'));
admin.initializeApp({ credential: admin.credential.cert(cred) });
const db = admin.firestore();
(async () => {
  const target = process.argv[2] || '10';
  const snap = await db.collection('transportRoutes').where('routeNumber', '==', target).get();
  if (snap.empty) { console.log('No route #' + target); process.exit(0); }
  snap.forEach((doc) => {
    const d = doc.data();
    console.log('Route #' + d.routeNumber + ' [' + (d.name || '').length + ' chars]:');
    console.log('  name: "' + d.name + '"');
    console.log('  type: ' + d.type);
    console.log('  stopIds: ' + (d.stopIds ? d.stopIds.length : 0) + ' stops');
    console.log('  scheduleTimes: ' + (d.scheduleTimes ? d.scheduleTimes.length : 0));
  });
  process.exit(0);
})();
