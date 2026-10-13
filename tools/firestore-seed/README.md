# firestore-seed

One-shot loaders that push seed data directly into the live MyCity Firestore via the `firebase-admin` SDK. Bypasses the in-app importer entirely — useful when the admin UI is broken, you want to script setup, or you need bidirectional `routeIds <-> stopIds` linking that the in-app importer doesn't do.

Currently provides:

- **seed-transport.js** — reads `../transit_scraper/output/transit_mogilev.json` and writes routes + stops into `transportRoutes` and `transportStops` collections, then back-fills `routeIds` on every stop with the routes that reference it.

## One-time setup

1. **Generate a service account key** in Firebase Console:
   - Open `https://console.firebase.google.com/project/mycity-40f5b/settings/serviceaccounts/adminsdk`
   - Click **Generate new private key** -> download the JSON
   - Save it as `tools/firestore-seed/service-account.json` (already in `.gitignore` — do not commit)

2. **Install dependencies:**
   ```powershell
   cd C:\mycity-master\tools\firestore-seed
   npm install
   ```

3. **Make sure the source JSON exists:**
   ```powershell
   cd C:\mycity-master\tools\transit_scraper
   npm install   # only first time
   npm run scrape
   ```
   Should produce `output/transit_mogilev.json` (~140 KB).

## Run

```powershell
cd C:\mycity-master\tools\firestore-seed

# preview — parses JSON, prints what would be written, no Firebase access
npm run seed:transport:dry

# actual run — writes to Firestore (transportRoutes + transportStops)
npm run seed:transport

# wipe both collections first, then write fresh
node seed-transport.js --purge
```

After completion check Firebase Console -> Firestore -> `transportStops` (334 docs) and `transportRoutes` (51 docs). The app's `TransportFeedFragment` will populate immediately via its listener.

## What gets written

**transportStops** docs (auto-generated IDs):
```
{ name: "...", lat: 0, lng: 0, routeIds: ["<routeDocId>", ...], gtfsStopId: null }
```
Coordinates default to `(0, 0)` because the source site doesn't expose them — set them manually in the admin UI map picker, or write a follow-up Overpass-API enrichment script.

**transportRoutes** docs (auto-generated IDs):
```
{
  routeNumber: "1",
  name: "Энергоремонт — Фатина Улица",
  type: "BUS" | "TROLLEYBUS" | "TRAM" | "MARSHRUTKA",
  color: "#RRGGBB",
  stopIds: ["<stopDocId>", ...],     // ordered, references transportStops
  scheduleTimes: ["07:00", ...],     // first-stop departures only
  polyline: [],                      // populate later (osmdroid wants this)
  active: true,
  gtfsRouteId: null
}
```

## Why not just use the in-app importer?

The in-app `AdminTransportImportFragment` works for one-off manual imports but:
- requires the app + admin login + paste 140 KB JSON into an EditText (slow, error-prone)
- doesn't backfill `routeIds` on stops (in-app route edits do it, but bulk import skips it)
- doesn't handle re-imports cleanly (creates duplicates)

This script does the whole thing in ~5 seconds via batch writes, with proper back-references and `--purge` for clean re-imports.

## Safety

- Service account JSON has **Editor** rights on the entire Firebase project. Treat it like a password. The `.gitignore` already excludes it.
- `--purge` deletes all docs from both collections before writing. Don't run this against a prod database where users have already started saving data.
- Default behavior is additive: writes new docs with new auto-IDs every time. Run with `--purge` to avoid duplicates on re-runs.
