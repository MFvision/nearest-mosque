// Prayer times, the Qibla and the app's sky, worked out in the browser for the landing pages' other versions
// (2/, 3/). The same formulas and methods as the app: the sun's position for the day, the method's Fajr and Isha
// angles (or minutes), Asr when a shadow equals its object plus its noon shadow, and the angle-based rule far
// from the equator.
const D = Math.PI / 180;
export const MAKKAH = [21.4225, 39.8262];

// Calculation methods: Fajr angle, Isha angle or minutes after Maghrib. Chosen by the visitor's time zone, as the
// app suggests one by country.
export const METHODS = {
  mwl: [18, 17], uaq: [18.5, '90'], egypt: [19.5, 17.5], karachi: [18, 18], isna: [15, 15], dubai: [18.2, 18.2],
  kuwait: [18, 17.5], qatar: [18, '90'], sea: [20, 18], turkey: [18, 17],
};
export function methodFor(tz) {
  if (tz === 'Asia/Riyadh' || tz === 'Asia/Aden') return 'uaq';
  if (tz === 'Africa/Cairo') return 'egypt';
  if (/^Asia\/(Karachi|Kolkata|Calcutta|Dhaka|Kathmandu|Colombo|Kabul)$/.test(tz)) return 'karachi';
  if (/^America\/|^Canada\//.test(tz)) return 'isna';
  if (tz === 'Asia/Dubai' || tz === 'Asia/Muscat') return 'dubai';
  if (tz === 'Asia/Kuwait') return 'kuwait';
  if (tz === 'Asia/Qatar' || tz === 'Asia/Bahrain') return 'qatar';
  if (/^Asia\/(Singapore|Kuala_Lumpur|Kuching|Jakarta|Makassar|Jayapura|Pontianak|Brunei)$/.test(tz)) return 'sea';
  if (tz === 'Europe/Istanbul') return 'turkey';
  return 'mwl';
}

// The sun on a given Julian day: declination and the equation of time (hours).
export function sun(jd) {
  const d = jd - 2451545.0;
  const g = (357.529 + 0.98560028 * d) * D;
  const q = 280.459 + 0.98564736 * d;
  const L = (q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g)) * D;
  const e = (23.439 - 0.00000036 * d) * D;
  const ra = Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L)) / D / 15;
  const fix = (h) => ((h % 24) + 24) % 24;
  let eqt = fix(q / 15) - fix(ra);
  if (eqt > 12) eqt -= 24; if (eqt < -12) eqt += 24;
  return { decl: Math.asin(Math.sin(e) * Math.sin(L)), eqt };
}
const julian = (y, m, d) => { if (m <= 2) { y -= 1; m += 12; } const A = Math.floor(y / 100); return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + 2 - A + Math.floor(A / 4) - 1524.5; };

const hijriMonth = (y, m, d) => { try { return +new Intl.DateTimeFormat('en-u-ca-islamic-umalqura', { month: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(y, m - 1, d, 12))); } catch { return 0; } };
// One day's times for a place, as hours from 0:00 UTC of that (local) date. shadow is 1 for the standard Asr, 2 for
// the Hanafi one (a shadow twice its object plus its noon shadow).
export function dayTimes(y, m, d, lat, lon, method, shadow = 1) {
  const jd = julian(y, m, d), [fajrA, ishaA] = METHODS[method], la = lat * D;
  const noon = (h) => 12 - lon / 15 - sun(jd + h / 24).eqt;
  const atAlt = (alt, h, side) => {
    const s = sun(jd + h / 24);
    const c = (Math.sin(alt * D) - Math.sin(s.decl) * Math.sin(la)) / (Math.cos(s.decl) * Math.cos(la));
    return noon(h) + side * Math.acos(Math.max(-2, Math.min(2, c))) / D / 15;
  };
  const asrAlt = (h) => { const s = sun(jd + h / 24); return Math.atan(1 / (shadow + Math.tan(Math.abs(la - s.decl)))) / D; };
  const refine = (f, h) => f(f(h));
  const t = {
    dhuhr: refine((h) => noon(h), 12 - lon / 15),
    sunrise: refine((h) => atAlt(-0.833, h, -1), 6 - lon / 15),
    maghrib: refine((h) => atAlt(-0.833, h, 1), 18 - lon / 15),
    fajr: refine((h) => atAlt(-fajrA, h, -1), 5 - lon / 15),
    asr: refine((h) => atAlt(asrAlt(h), h, 1), 15 - lon / 15),
  };
  t.isha = typeof ishaA === 'string' ? t.maghrib + +ishaA / 60 : refine((h) => atAlt(-ishaA, h, 1), 20 - lon / 15);
  // Umm al-Qura: in Ramadan, Isha is 30 minutes later.
  if (method === 'uaq' && hijriMonth(y, m, d) === 9) t.isha += 0.5;
  // Far from the equator, where the twilight never ends: the angle-based rule (a share of the night).
  const night = 24 - (t.maghrib - t.sunrise);
  const fajrMax = (fajrA / 60) * night, ishaMax = ((typeof ishaA === 'string' ? 18 : ishaA) / 60) * night;
  if (!isFinite(t.fajr) || t.sunrise - t.fajr > fajrMax) t.fajr = t.sunrise - fajrMax;
  if (typeof ishaA !== 'string' && (!isFinite(t.isha) || t.isha - t.maghrib > ishaMax)) t.isha = t.maghrib + ishaMax;
  return t;
}

// Where the visitor is: a saved choice, a named place in the browser's time zone, else that zone's most populous
// built-in city, else Makkah. No location is asked for.
export async function resolveCity(places, zonesUrl, saved) {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Riyadh';
  const from = (p) => ({ en: p[0], ar: p[1], lat: p[2], lon: p[3], tz: p[4] });
  if (saved) { const p = places.find((x) => x[0] === saved); if (p) return from(p); }
  const p = places.find((x) => x[4] === tz && !/^(Makkah|Madinah)$/.test(x[0])) || places.find((x) => x[4] === tz);
  if (p) return from(p);
  try {
    const z = (await fetch(zonesUrl).then((r) => r.json()))[tz];
    if (z) return { en: z[2], ar: z[2], lat: z[0], lon: z[1], tz };
  } catch {}
  return { en: 'Makkah', ar: 'مكة المكرمة', lat: MAKKAH[0], lon: MAKKAH[1], tz: 'Asia/Riyadh' };
}

// Today's six times, tomorrow's Fajr and yesterday's Isha as real moments, in the city's own calendar. opts (the TV's
// settings): method (a METHODS key; otherwise the city's), asr ('hanafi' or the standard), offsets (minutes per time).
export function schedule(city, opts = {}, now = new Date()) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone: city.tz, year: 'numeric', month: 'numeric', day: 'numeric' }).formatToParts(now).map((p) => [p.type, p.value]));
  const y = +parts.year, m = +parts.month, d = +parts.day;
  const offsets = opts.offsets || {};
  const at = (dd, hours, k) => new Date(Date.UTC(y, m - 1, dd) + hours * 3600e3 + (offsets[k] || 0) * 60e3);
  const method = METHODS[opts.method] ? opts.method : methodFor(city.tz);
  const shadow = opts.asr === 'hanafi' ? 2 : 1;
  const on = (dd) => { const x = new Date(Date.UTC(y, m - 1, dd)); return dayTimes(x.getUTCFullYear(), x.getUTCMonth() + 1, x.getUTCDate(), city.lat, city.lon, method, shadow); };
  const today = on(d), tomorrow = on(d + 1), yesterday = on(d - 1);
  const keys = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'];
  return { method, today: keys.map((k) => [k, at(d, today[k], k)]), tomorrowFajr: at(d + 1, tomorrow.fajr, 'fajr'), yesterdayIsha: at(d - 1, yesterday.isha, 'isha') };
}

// The next of the five prayers after now (tomorrow's Fajr after Isha).
export function nextPrayer(plan, now = new Date()) {
  return plan.today.filter(([k]) => k !== 'sunrise').find(([, v]) => v > now) || ['fajr', plan.tomorrowFajr];
}

// The app's sky loop runs Fajr 0, sunrise 0.14, Dhuhr 0.30, Asr 0.47, Maghrib 0.62, Isha 0.75, the middle of the
// night 0.88 and Fajr again at 1; this is where a moment falls on it.
export function skyT(plan, now = new Date()) {
  const tt = Object.fromEntries(plan.today.map(([k, v]) => [k, +v]));
  const midY = +plan.yesterdayIsha + (tt.fajr - plan.yesterdayIsha) / 2, mid = tt.isha + (plan.tomorrowFajr - tt.isha) / 2;
  const pts = [[+plan.yesterdayIsha, -0.25], [midY, -0.12], [tt.fajr, 0], [tt.sunrise, 0.14], [tt.dhuhr, 0.3], [tt.asr, 0.47], [tt.maghrib, 0.62], [tt.isha, 0.75], [mid, 0.88], [+plan.tomorrowFajr, 1]];
  const n = +now;
  for (let i = 0; i + 1 < pts.length; i++) {
    if (n >= pts[i][0] && n <= pts[i + 1][0]) return (((pts[i][1] + (pts[i + 1][1] - pts[i][1]) * (n - pts[i][0]) / (pts[i + 1][0] - pts[i][0])) % 1) + 1) % 1;
  }
  return 0.88;
}

// The Qibla from a place: the great-circle bearing from north and the distance to the Kaaba.
export function qibla(city) {
  const [p1, l1, p2, l2] = [city.lat * D, city.lon * D, MAKKAH[0] * D, MAKKAH[1] * D];
  const deg = (Math.atan2(Math.sin(l2 - l1), Math.cos(p1) * Math.tan(p2) - Math.sin(p1) * Math.cos(l2 - l1)) / D + 360) % 360;
  const a = Math.sin((p2 - p1) / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin((l2 - l1) / 2) ** 2;
  return { deg: Math.round(deg), km: Math.round(2 * 6371 * Math.asin(Math.sqrt(a))) };
}

// A clock for a time zone, in the page's language (Western digits in Arabic, as the app shows them).
const fmts = {};
export function clock(date, tz, lang) {
  const key = lang + tz;
  fmts[key] = fmts[key] || new Intl.DateTimeFormat(lang === 'ar' ? 'ar-u-nu-latn' : 'en-US', { hour: 'numeric', minute: '2-digit', timeZone: tz });
  return fmts[key].format(date);
}

// The download buttons: the two stores (until a listing is live, its button reads "Coming soon"), then Windows and
// Linux straight from GitHub, with the .deb and the page of every download in a line of small links under them.
const ICONS = {
  appStore: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm6.2 4.2-4.6 8h2.1l.8-1.4h4.9l-1-1.8H10.6l2.5-4.4-1.9-.4Zm2.6 3.2-1 1.7 2.2 3.9h2.1l-3.3-5.6Z"/></svg>',
  googlePlay: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 2.8v18.4c0 .6.6.9 1.1.6l15.4-8.9c.5-.3.5-1 0-1.3L5.1 2.2C4.6 1.9 4 2.2 4 2.8Z"/></svg>',
  // Windows and Linux: plain shapes (a window, a terminal), not the systems' logos.
  windows: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3h8.5v8.5H3zM12.5 3H21v8.5h-8.5zM3 12.5h8.5V21H3zM12.5 12.5H21V21h-8.5z"/></svg>',
  linux: '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill-rule="evenodd" d="M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm2.3 4.4L9.9 12l-3.6 3.6L7.7 17l5-5-5-5ZM13 15.2V17h5v-1.8Z"/></svg>',
};
export const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
const FILES = new Set(['windows', 'linux', 'linuxDeb']); // files: they download in place; pages open in a new tab
const target = (k) => (FILES.has(k) ? '' : ' target="_blank"');
export function storesHTML(t, links) {
  const more = Object.keys(t.storesMore).filter((k) => links[k])
    .map((k) => `<a href="${esc(links[k])}" rel="noopener"${target(k)}>${esc(t.storesMore[k])}</a>`);
  return Object.keys(ICONS).map((k) => {
    const [small, big] = t.stores[k];
    const inner = `${ICONS[k]}<small>${esc(links[k] ? small : t.soon)}</small><b>${esc(big)}</b>`;
    return links[k] ? `<a class="store" href="${esc(links[k])}" rel="noopener"${target(k)}>${inner}</a>` : `<span class="store soon" aria-disabled="true">${inner}</span>`;
  }).join('') + (more.length ? `<p class="stores-more">${more.join('<span aria-hidden="true">·</span>')}</p>` : '');
}
