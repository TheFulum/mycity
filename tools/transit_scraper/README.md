# transit_scraper

Standalone Node.js scraper that produces seed JSON for the MyCity «Транспорт» admin import.

**Source:** `https://mogilev.btrans.by/` (HTML, no public API).
**Output:** `output/transit_mogilev.json` — same shape that `AdminTransportImportFragment` reads.

## What it does

1. Fetches the list pages for buses (`/avtobus`), trolleybuses (`/trollejbus`) and marshrutki (`/marshrutka`).
2. Extracts every individual route URL.
3. Visits each route page, parses the ordered list of stops for the forward direction (stops are dedup'd globally across routes by exact name).
4. Emits a JSON in the format:
   ```json
   {
     "stops": [{ "name": "Энергоремонт", "lat": null, "lng": null }, ...],
     "routes": [{
       "number": "1",
       "type": "BUS",
       "name": "Энергоремонт — Фатина Улица",
       "color": "#3478F6",
       "stops": [0, 1, 2],
       "scheduleTimes": []
     }, ...]
   }
   ```

## Last run (2026-05-12)

- **334 unique stops, 51 routes** written to `output/transit_mogilev.json` (~140 KB).
- **51/51 routes have schedule data** (departure times at the first stop in the forward direction). Departure counts range from 9 (rare school-hour buses like BUS 43) to 205 (frequent trunk lines like TROLLEYBUS 2).
- **43 bus routes** (numbers 1–46 minus 16, 42, 44 — those are gaps in btrans.by itself).
- **8 trolleybus routes** (2, 3, 4, 5, 6, 7, 16, 16д). Route 17 returned HTTP 500 from the server and was skipped — re-run later or add manually.
- **0 marshrutka routes** — btrans.by simply doesn't host marshrutka data for Mogilev (the `/marshrutka` page is a placeholder). Nothing to scrape here. If you need marshrutki later, source them elsewhere or have an admin enter them manually via the in-app CRUD.

## Known limitations

- **No coordinates.** btrans.by doesn't expose `lat/lng`; all stops emit with `null`. Admin sets them later via the in-app map picker (`AdminTransportStopEditFragment`), or a future overpass-enrichment script can backfill them by querying OSM for `public_transport=stop_position` within Mogilev's bbox + name matching.
- **Schedule = first-stop departures only.** btrans.by exposes schedules per-stop (`/avtobus/<N>/<stop-slug>` page has a `table.schedule--list` of `[hour, days, minutes-as-concatenated-2-digits]`). We fetch ONLY the first stop of the forward direction and treat those times as the route's departure schedule. Day-of-week info is flattened (all weekday + weekend times merged into one set). For per-stop arrival times, scrape every stop URL individually — 5–6× more requests.
- **No marshrutka data on the source site.** btrans.by's `/marshrutka` page is a placeholder — there is no scrapable content. The scraper still hits the URL (zero-cost — list parses as empty), so if btrans.by ever adds marshrutka data, the next run picks it up automatically.
- **Forward direction only.** Each route page concatenates forward + reverse stops in one `<ul>`. The scraper deduplicates by first occurrence — this drops the reverse half but loses any stop that exists ONLY on the way back (rare). Acceptable for seed data.
- **HTML parsing is brittle.** If btrans.by redesigns its DOM, the scraper breaks. Re-run only on demand.

## How to import the result into the app

1. Open `output/transit_mogilev.json` and copy the entire content.
2. In the app (as admin): tap the FAB → red transport button → tab «Импорт».
3. Paste the JSON into the text field, tap «Импортировать».
4. After completion, the «Маршруты» tab will be populated. Edit each stop to add coordinates via the map picker. Schedules can be added per-route via «Маршруты» → edit → «Расписание» textarea.

## Usage

Requires Node.js 18+ already installed (verified at `C:\Program Files\nodejs\node.exe`).

```powershell
cd C:\mycity-master\tools\transit_scraper
npm install
npm run scrape
```

Output appears at `output/transit_mogilev.json`. Open the file, paste the full content into the MyCity app: Admin FAB → Транспорт → tab «Импорт» → button «Импортировать».

## Politeness

The scraper sets a `User-Agent: MyCity-Transit-Seed-Scraper/1.0` header and rate-limits to ~200 ms between requests. Total run time: ~25–30 seconds for ~50 routes × 2 fetches each (route page + first-stop schedule).
