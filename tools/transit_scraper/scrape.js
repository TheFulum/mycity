const fs = require('fs');
const path = require('path');
const axios = require('axios');
const cheerio = require('cheerio');
const iconv = require('iconv-lite');

const BASE = 'https://mogilev.btrans.by';
const UA = 'MyCity-Transit-Seed-Scraper/1.0';
const REQUEST_DELAY_MS = 200;
const OUTPUT_PATH = path.join(__dirname, 'output', 'transit_mogilev.json');

const CATEGORIES = [
  { type: 'BUS',        listPath: '/avtobus',    routePathPrefix: '/avtobus/' },
  { type: 'TROLLEYBUS', listPath: '/trollejbus', routePathPrefix: '/trollejbus/' },
  { type: 'MARSHRUTKA', listPath: '/marshrutka', routePathPrefix: '/marshrutka/' }
];

const DEFAULT_COLOR_BY_TYPE = {
  BUS: '#3478F6',
  TROLLEYBUS: '#34A853',
  MARSHRUTKA: '#F4B400',
  TRAM: '#EA4335'
};

const STOP_HREF_RE = /\/(avtobus|trollejbus|marshrutka)\/[^/]+\/[^/?#]+/;

const sleep = (ms) => new Promise((res) => setTimeout(res, ms));

async function fetchHtml(url) {
  const res = await axios.get(url, {
    responseType: 'arraybuffer',
    headers: { 'User-Agent': UA, 'Accept': 'text/html,application/xhtml+xml' },
    timeout: 15000,
    validateStatus: (s) => s >= 200 && s < 400
  });
  const buf = Buffer.from(res.data);
  const ctype = (res.headers['content-type'] || '').toLowerCase();
  const isCp1251 = ctype.includes('windows-1251') || ctype.includes('cp1251');
  return isCp1251 ? iconv.decode(buf, 'win1251') : buf.toString('utf8');
}

function collectRouteUrls($, prefix) {
  const seen = new Set();
  const urls = [];
  $('a[href]').each((_, el) => {
    let href = $(el).attr('href');
    if (!href) return;
    try {
      const abs = new URL(href, BASE).toString();
      if (!abs.startsWith(BASE + prefix)) return;
      const rest = abs.slice((BASE + prefix).length);
      if (!rest || rest.includes('/')) return;
      if (seen.has(abs)) return;
      seen.add(abs);
      urls.push(abs);
    } catch (_) { /* skip bad hrefs */ }
  });
  return urls;
}

function extractRouteNumber(url, prefix) {
  const rest = url.slice((BASE + prefix).length);
  try { return decodeURIComponent(rest); } catch (_) { return rest; }
}

function parseDirections($, routePrefix) {
  const dirs = [];
  for (let i = 0; i < 5; i++) {
    const root = $('#napravlenie-' + i);
    if (!root.length) break;
    const next = $('#napravlenie-' + (i + 1));
    let pool;
    if (root.find('a[href]').length > 0) {
      pool = root.find('a[href]');
    } else if (next.length) {
      pool = root.nextUntil(next).find('a[href]');
    } else {
      pool = root.nextAll().find('a[href]');
    }
    const stops = [];
    const stopUrls = [];
    const seenInDir = new Set();
    pool.each((_, a) => {
      const href = $(a).attr('href');
      const txt = $(a).text().trim().replace(/\s+/g, ' ');
      if (!href || !txt) return;
      let abs;
      try { abs = new URL(href, BASE).toString(); } catch (_) { return; }
      if (!STOP_HREF_RE.test(abs)) return;
      if (!abs.includes(routePrefix)) return;
      if (seenInDir.has(txt)) return;
      seenInDir.add(txt);
      stops.push(txt);
      stopUrls.push(abs);
    });
    if (stops.length) dirs.push({ stops, stopUrls });
  }
  return dirs;
}

function pickRouteName($, fallback) {
  const MAX_NAME_LEN = 200;
  for (let i = 0; i < 5; i++) {
    const el = $('#napravlenie-' + i).first();
    if (!el.length) break;
    const text = el.text().trim().replace(/\s+/g, ' ');
    if (text && / [—–-] /.test(text) && text.length < MAX_NAME_LEN) return text;
  }
  const seoPattern = /^Расписание\s+(автобуса|троллейбуса|маршрутки|трамвая)/i;
  let result = null;
  $('h1, h2, h3').each((_, el) => {
    if (result) return;
    const text = $(el).text().trim().replace(/\s+/g, ' ');
    if (!text || seoPattern.test(text)) return;
    if (/ [—–-] /.test(text) && text.length < MAX_NAME_LEN) result = text;
  });
  return result || fallback;
}

function parseScheduleFromHtml(html) {
  const $ = cheerio.load(html);
  const times = new Set();
  $('table').each((_, tbl) => {
    const cls = ($(tbl).attr('class') || '').toLowerCase();
    if (!cls.includes('schedule')) return;
    let currentHour = null;
    $(tbl).find('tbody tr').each((_, tr) => {
      const cells = [];
      $(tr).find('td, th').each((_, c) => cells.push($(c).text().trim().replace(/\s+/g, ' ')));
      let hour, minutesStr;
      if (cells.length >= 3 && /^\d{1,2}$/.test(cells[0])) {
        hour = cells[0];
        minutesStr = cells[cells.length - 1];
        currentHour = hour;
      } else if (cells.length >= 2 && currentHour !== null) {
        hour = currentHour;
        minutesStr = cells[cells.length - 1];
      } else {
        return;
      }
      const minutes = minutesStr.match(/\d{2}/g) || [];
      const hh = String(hour).padStart(2, '0');
      for (const m of minutes) times.add(`${hh}:${m}`);
    });
  });
  return Array.from(times).sort();
}

async function fetchSchedule(stopUrl) {
  try {
    const html = await fetchHtml(stopUrl);
    return parseScheduleFromHtml(html);
  } catch (_) {
    return [];
  }
}

async function scrapeRoute(url, type, routePathPrefix) {
  const html = await fetchHtml(url);
  const $ = cheerio.load(html);
  const number = extractRouteNumber(url, routePathPrefix);
  const name = pickRouteName($, `Маршрут ${number}`);
  const dirs = parseDirections($, routePathPrefix);
  const forward = dirs[0] || { stops: [], stopUrls: [] };
  let scheduleTimes = [];
  if (forward.stopUrls.length > 0) {
    await sleep(REQUEST_DELAY_MS);
    scheduleTimes = await fetchSchedule(forward.stopUrls[0]);
  }
  return {
    number,
    type,
    name,
    color: DEFAULT_COLOR_BY_TYPE[type],
    stops: forward.stops,
    scheduleTimes
  };
}

function normalizeName(s) {
  return s.replace(/\s+/g, ' ').trim();
}

async function main() {
  const allRoutes = [];
  const stopIndex = new Map();
  const stopsOut = [];

  for (const cat of CATEGORIES) {
    const listUrl = BASE + cat.listPath;
    process.stdout.write(`Fetching list: ${listUrl}\n`);
    let listHtml;
    try {
      listHtml = await fetchHtml(listUrl);
    } catch (e) {
      process.stderr.write(`  WARN: list fetch failed (${e.message}); skipping ${cat.type}\n`);
      continue;
    }
    const $list = cheerio.load(listHtml);
    const routeUrls = collectRouteUrls($list, cat.routePathPrefix);
    process.stdout.write(`  Found ${routeUrls.length} ${cat.type} routes\n`);

    for (const url of routeUrls) {
      await sleep(REQUEST_DELAY_MS);
      try {
        const r = await scrapeRoute(url, cat.type, cat.routePathPrefix);
        if (!r.stops.length) {
          process.stderr.write(`  WARN: ${url} produced 0 stops; skipping route\n`);
          continue;
        }
        const stopIndices = [];
        for (const rawName of r.stops) {
          const name = normalizeName(rawName);
          if (!name) continue;
          let idx = stopIndex.get(name);
          if (idx === undefined) {
            idx = stopsOut.length;
            stopIndex.set(name, idx);
            stopsOut.push({ name, lat: null, lng: null });
          }
          stopIndices.push(idx);
        }
        allRoutes.push({
          number: r.number,
          type: r.type,
          name: r.name,
          color: r.color,
          stops: stopIndices,
          scheduleTimes: r.scheduleTimes
        });
        process.stdout.write(`  ✓ ${r.type} ${r.number}: ${stopIndices.length} stops, ${r.scheduleTimes.length} departures\n`);
      } catch (e) {
        process.stderr.write(`  WARN: route ${url} failed (${e.message})\n`);
      }
    }
  }

  const out = { stops: stopsOut, routes: allRoutes };
  fs.mkdirSync(path.dirname(OUTPUT_PATH), { recursive: true });
  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(out, null, 2), 'utf8');
  const withSchedule = allRoutes.filter((r) => r.scheduleTimes.length > 0).length;
  process.stdout.write(`\nDone. ${stopsOut.length} unique stops across ${allRoutes.length} routes (${withSchedule} with schedule).\nWrote: ${OUTPUT_PATH}\n`);
}

main().catch((e) => {
  process.stderr.write(`FATAL: ${e.stack || e.message}\n`);
  process.exit(1);
});
