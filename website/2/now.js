// Version 2 of the landing page: the visitor's next prayer first. The city comes from the browser's time zone
// (no location is asked for); the times are worked out here for the centre of that city with the method the app
// suggests for its country, and the sky behind them is the app's sky at this moment of the day.
import { story, links } from '../story.js';
import { places } from '../places.js';

const D = Math.PI / 180;
const MAKKAH = [21.4225, 39.8262];
const store = { get: (k) => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch {} } };
const $ = (s) => document.querySelector(s);
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

let lang = (location.hash === '#ar' || location.hash === '#en') ? location.hash.slice(1) : store.get('nm-lang') || ((navigator.languages || [navigator.language]).some((l) => /^ar/i.test(l)) ? 'ar' : 'en');

// The page's own words; the store names, prayer names and footer come from the story.
const T = {
  en: {
    story: 'See the story', change: 'Change city', in: 'in', at: (time, city) => `at ${time} in ${city}`,
    qTitle: (city) => `Qibla from ${city}`, qSub: (deg, km) => `${deg}° from north · ${km} km to Makkah`,
    note: (city, method) => `Times for the centre of ${city}, ${method} method. The app works them out for where you are and suggests the method for your country.`,
    sunrise: 'Sunrise', more: 'What else is in the app', privacyEyebrow: 'Privacy', privacy: 'No account, no ads, no tracking.',
    privacyLine: 'Prayer times and the Qibla are worked out on your device and work without the internet.',
    methods: { mwl: 'Muslim World League', uaq: 'Umm al-Qura', egypt: 'Egyptian', karachi: 'Karachi', isna: 'ISNA', dubai: 'Dubai', kuwait: 'Kuwait', qatar: 'Qatar', sea: 'Singapore and Southeast Asia', turkey: 'Diyanet' },
    cards: [
      ['iphone/09-mosques-radar', 'See which way the mosque is, and how far.', 'A compass with each mosque at its real direction, and a map for the walk there.'],
      ['iphone/04-ask', 'Ask about Islam and see the source.', 'Answers quote the books on your phone, each with its source. They are not a fatwa.'],
      ['iphone/05-classics', '99 classical books, offline.', 'Download a volume once and read it without the internet.'],
      ['iphone/08-audio', 'The Adhan from Makkah or Madinah.', 'With a reminder before each prayer, and a Fajr alarm.'],
      ['iphone/06-widgets', 'The time left, without opening the app.', 'Widgets, Live Activities, the Dynamic Island and Siri.'],
      ['watch/01-watch-prayer', 'On your wrist, and on every screen.', 'Apple Watch, iPad, Mac, Apple TV and Vision Pro, with Android and Windows too.'],
    ],
  },
  ar: {
    story: 'شاهد القصة', change: 'غيّر المدينة', in: 'بعد', at: (time, city) => `الساعة ${time} في ${city}`,
    qTitle: (city) => `القبلة من ${city}`, qSub: (deg, km) => `${deg}° من الشمال · ${km} كم إلى مكة`,
    note: (city, method) => `المواقيت لوسط ${city} بطريقة ${method}. أما التطبيق فيحسبها لمكانك ويقترح طريقة بلدك.`,
    sunrise: 'الشروق', more: 'وفي التطبيق أيضًا', privacyEyebrow: 'الخصوصية', privacy: 'بلا حساب، بلا إعلانات، بلا تتبّع.',
    privacyLine: 'تُحسب المواقيت والقبلة على جهازك، وتعمل دون إنترنت.',
    methods: { mwl: 'رابطة العالم الإسلامي', uaq: 'أم القرى', egypt: 'الهيئة المصرية', karachi: 'كراتشي', isna: 'أمريكا الشمالية', dubai: 'دبي', kuwait: 'الكويت', qatar: 'قطر', sea: 'سنغافورة وجنوب شرق آسيا', turkey: 'ديانت' },
    cards: [
      ['iphone/09-mosques-radar', 'اعرف اتجاه أقرب مسجد وكم يبعد عنك.', 'بوصلة تعرض كل مسجد في اتجاهه الحقيقي، وخريطة لطريق المشي إليه.'],
      ['iphone/04-ask', 'اسأل عن دينك، واعرف المصدر.', 'تقتبس الإجابات من الكتب المحفوظة في هاتفك مع مصدر كل نص، وليست فتوى.'],
      ['iphone/05-classics', '99 كتابًا من أمهات الكتب دون إنترنت.', 'نزّل المجلد مرة واحدة واقرأه دون اتصال.'],
      ['iphone/08-audio', 'الأذان من مكة أو المدينة.', 'مع تذكير قبل كل صلاة، ومنبّه للفجر.'],
      ['iphone/06-widgets', 'الوقت المتبقي دون فتح التطبيق.', 'أدوات الشاشة والأنشطة المباشرة والجزيرة الديناميكية وSiri.'],
      ['watch/01-watch-prayer', 'على معصمك، وعلى كل شاشة.', 'Apple Watch وآيباد وماك وApple TV وVision Pro، ومعها أندرويد وويندوز.'],
    ],
  },
};

// Calculation methods: Fajr angle, Isha angle or minutes after Maghrib. Chosen by the visitor's time zone, as the
// app suggests one by country.
const METHODS = {
  mwl: [18, 17], uaq: [18.5, '90'], egypt: [19.5, 17.5], karachi: [18, 18], isna: [15, 15], dubai: [18.2, 18.2],
  kuwait: [18, 17.5], qatar: [18, '90'], sea: [20, 18], turkey: [18, 17],
};
function methodFor(tz) {
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
function sun(jd) {
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
// One day's times for a place, as hours from 0:00 UTC of that (local) date.
function dayTimes(y, m, d, lat, lon, method) {
  const jd = julian(y, m, d), [fajrA, ishaA] = METHODS[method], la = lat * D;
  const noon = (h) => 12 - lon / 15 - sun(jd + h / 24).eqt;
  const atAlt = (alt, h, side) => {
    const s = sun(jd + h / 24);
    const c = (Math.sin(alt * D) - Math.sin(s.decl) * Math.sin(la)) / (Math.cos(s.decl) * Math.cos(la));
    return noon(h) + side * Math.acos(Math.max(-2, Math.min(2, c))) / D / 15;
  };
  const asrAlt = (h) => { const s = sun(jd + h / 24); return Math.atan(1 / (1 + Math.tan(Math.abs(la - s.decl)))) / D; };
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

// Where the visitor is: a named place in the time zone, else the zone's most populous built-in city, else Makkah.
const tzNow = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Riyadh';
let city = null;
async function resolveCity() {
  const saved = store.get('nm-city2');
  if (saved) { const p = places.find((x) => x[0] === saved); if (p) return { en: p[0], ar: p[1], lat: p[2], lon: p[3], tz: p[4] }; }
  const p = places.find((x) => x[4] === tzNow && !/^(Makkah|Madinah)$/.test(x[0])) || places.find((x) => x[4] === tzNow);
  if (p) return { en: p[0], ar: p[1], lat: p[2], lon: p[3], tz: p[4] };
  try {
    const z = (await fetch('../assets/zones.json').then((r) => r.json()))[tzNow];
    if (z) return { en: z[2], ar: z[2], lat: z[0], lon: z[1], tz: tzNow };
  } catch {}
  return { en: 'Makkah', ar: 'مكة المكرمة', lat: MAKKAH[0], lon: MAKKAH[1], tz: 'Asia/Riyadh' };
}
const cityName = () => (lang === 'ar' ? city.ar : city.en);

// Today's and tomorrow's times as real moments, in the city's own calendar.
function schedule() {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone: city.tz, year: 'numeric', month: 'numeric', day: 'numeric' }).formatToParts(new Date()).map((p) => [p.type, p.value]));
  const y = +parts.year, m = +parts.month, d = +parts.day;
  const at = (dd, hours) => new Date(Date.UTC(y, m - 1, dd) + hours * 3600e3);
  const method = methodFor(city.tz);
  const today = dayTimes(y, m, d, city.lat, city.lon, method);
  const next = new Date(Date.UTC(y, m - 1, d + 1));
  const tomorrow = dayTimes(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate(), city.lat, city.lon, method);
  const yest = new Date(Date.UTC(y, m - 1, d - 1));
  const yesterday = dayTimes(yest.getUTCFullYear(), yest.getUTCMonth() + 1, yest.getUTCDate(), city.lat, city.lon, method);
  const keys = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'];
  return {
    method,
    today: keys.map((k) => [k, at(d, today[k])]),
    tomorrowFajr: at(d + 1, tomorrow.fajr),
    yesterdayIsha: at(d - 1, yesterday.isha),
  };
}

let fmtCache = {};
const clock = (date) => {
  const key = lang + city.tz;
  fmtCache[key] = fmtCache[key] || new Intl.DateTimeFormat(lang === 'ar' ? 'ar-u-nu-latn' : 'en-US', { hour: 'numeric', minute: '2-digit', timeZone: city.tz });
  return fmtCache[key].format(date);
};
const prayerName = (k) => (k === 'sunrise' ? T[lang].sunrise : story[lang].prayers[k]);

// The sky's moment: the app's loop runs Fajr 0, sunrise 0.14, Dhuhr 0.30, Asr 0.47, Maghrib 0.62, Isha 0.75,
// the middle of the night 0.88 and Fajr again at 1.
function skyT(s, now) {
  const tt = Object.fromEntries(s.today.map(([k, v]) => [k, +v]));
  const midY = +s.yesterdayIsha + (tt.fajr - s.yesterdayIsha) / 2, mid = tt.isha + (s.tomorrowFajr - tt.isha) / 2;
  const pts = [[+s.yesterdayIsha, -0.25], [midY, -0.12], [tt.fajr, 0], [tt.sunrise, 0.14], [tt.dhuhr, 0.3], [tt.asr, 0.47], [tt.maghrib, 0.62], [tt.isha, 0.75], [mid, 0.88], [+s.tomorrowFajr, 1]];
  const n = +now;
  for (let i = 0; i + 1 < pts.length; i++) {
    if (n >= pts[i][0] && n <= pts[i + 1][0]) return (((pts[i][1] + (pts[i + 1][1] - pts[i][1]) * (n - pts[i][0]) / (pts[i + 1][0] - pts[i][0])) % 1) + 1) % 1;
  }
  return 0.88;
}

// The Qibla from the city: the great-circle bearing and the distance to the Kaaba.
function qibla() {
  const [p1, l1, p2, l2] = [city.lat * D, city.lon * D, MAKKAH[0] * D, MAKKAH[1] * D];
  const deg = (Math.atan2(Math.sin(l2 - l1), Math.cos(p1) * Math.tan(p2) - Math.sin(p1) * Math.cos(l2 - l1)) / D + 360) % 360;
  const a = Math.sin((p2 - p1) / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin((l2 - l1) / 2) ** 2;
  return { deg: Math.round(deg), km: Math.round(2 * 6371 * Math.asin(Math.sqrt(a))) };
}

function storesHTML() {
  const t = story[lang];
  const ICONS = {
    appStore: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm6.2 4.2-4.6 8h2.1l.8-1.4h4.9l-1-1.8H10.6l2.5-4.4-1.9-.4Zm2.6 3.2-1 1.7 2.2 3.9h2.1l-3.3-5.6Z"/></svg>',
    googlePlay: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 2.8v18.4c0 .6.6.9 1.1.6l15.4-8.9c.5-.3.5-1 0-1.3L5.1 2.2C4.6 1.9 4 2.2 4 2.8Z"/></svg>',
    microsoftStore: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3h8.5v8.5H3zM12.5 3H21v8.5h-8.5zM3 12.5h8.5V21H3zM12.5 12.5H21V21h-8.5z"/></svg>',
  };
  return Object.keys(ICONS).map((k) => {
    const [small, big] = t.stores[k];
    const inner = `${ICONS[k]}<small>${esc(links[k] ? small : t.soon)}</small><b>${esc(big)}</b>`;
    return links[k] ? `<a class="store" href="${esc(links[k])}" rel="noopener" target="_blank">${inner}</a>` : `<span class="store soon" aria-disabled="true">${inner}</span>`;
  }).join('');
}

function render() {
  const t = T[lang], s = story[lang];
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  document.title = s.title;
  document.querySelectorAll('[data-t]').forEach((el) => { const k = el.dataset.t; el.textContent = t[k] ?? s[k] ?? el.textContent; });
  $('#lang').textContent = s.switchTo;
  $('#lang').setAttribute('lang', lang === 'ar' ? 'en' : 'ar');
  $('#hero-shot').src = `../assets/${lang}/iphone/01-prayer.webp`;
  $('#stores-top').innerHTML = $('#stores-end').innerHTML = storesHTML();
  $('#cards').innerHTML = t.cards.map(([img, h, p]) => `<article class="card"><div class="shot${img.startsWith('watch') ? ' watch' : ''}"><img src="../assets/${lang}/${img}.webp" alt="" loading="lazy" decoding="async"></div><h3>${esc(h)}</h3><p>${esc(p)}</p></article>`).join('');
  const f = s.footer;
  $('#foot').innerHTML = [['../privacy.html', f.privacy], ['../terms.html', f.terms], ['../support.html', f.support], ['../security.html', f.security], ['../credits.html', f.credits]].map(([h, l]) => `<a href="${h}${lang === 'ar' ? '#ar' : ''}">${esc(l)}</a>`).join('');
  // The city list: the named places, in this language's order; the visitor's own city first if it is not among them.
  const list = places.filter((p) => !/^(Madinah)$/.test(p[0])).map((p) => [p[0], lang === 'ar' ? p[1] : p[0]]).sort((a, b) => a[1].localeCompare(b[1], lang));
  if (!places.some((p) => p[0] === city.en)) list.unshift([city.en, cityName()]);
  $('#city-pick').innerHTML = list.map(([v, l]) => `<option value="${esc(v)}"${v === city.en ? ' selected' : ''}>${esc(l)}</option>`).join('');
  $('#city-pick').setAttribute('aria-label', t.change);
  $('#city-btn').setAttribute('aria-label', `${cityName()}, ${t.change}`);
  fmtCache = {};
  plan = schedule();
  tick(true);
}

let plan = null, lastSky = -1;
const sky = $('.sky'), ctx = sky.getContext('2d');
function sizeSky() {
  const k = Math.min(1.5, devicePixelRatio || 1);
  sky.width = Math.round(sky.clientWidth * k); sky.height = Math.round(sky.clientHeight * k);
  lastSky = -1;
}
function tick(force) {
  if (!plan) return;
  const now = new Date(), t = T[lang];
  // Past today's Isha the next is tomorrow's Fajr; a new day re-plans.
  if (now > plan.tomorrowFajr) plan = schedule();
  const five = plan.today.filter(([k]) => k !== 'sunrise');
  let next = five.find(([, v]) => v > now) || ['fajr', plan.tomorrowFajr];
  const left = Math.max(0, Math.round((next[1] - now) / 1000));
  const hms = `${Math.floor(left / 3600)}:${String(Math.floor(left / 60) % 60).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
  $('#count').textContent = hms;
  if (force || $('#next-name').textContent !== prayerName(next[0])) {
    $('#next-name').textContent = prayerName(next[0]);
    $('#next-in').textContent = t.in;
    $('#at').textContent = t.at(clock(next[1]), cityName());
    $('#city').textContent = cityName();
    $('#times').innerHTML = plan.today.map(([k, v]) => `<li${k === next[0] ? ' class="is-next"' : ''}><span>${esc(prayerName(k))}</span><b>${esc(clock(v))}</b></li>`).join('');
    const q = qibla();
    $('#q-title').textContent = t.qTitle(cityName());
    $('#q-sub').textContent = t.qSub(q.deg, q.km.toLocaleString(lang === 'ar' ? 'ar-u-nu-latn' : 'en-US'));
    $('#needle').style.transform = `rotate(${q.deg}deg)`;
    $('#note').textContent = t.note(cityName(), t.methods[plan.method]);
    document.title = `${prayerName(next[0])} · ${story[lang].title}`;
  }
  const st = skyT(plan, now);
  if (force || Math.abs(st - lastSky) > 0.0015) {
    if (typeof drawSky === 'function' && sky.width) drawSky(ctx, sky.width, sky.height, st, { horizon: 0.86, mosque: 0.13, mosqueX: lang === 'ar' ? 0.22 : 0.78 });
    lastSky = st;
  }
}

// The dial's ticks, drawn once.
$('.ticks').innerHTML = Array.from({ length: 36 }, (_, i) => { const a = i * 10 * D, r1 = i % 9 ? 42 : 39; return `<line x1="${50 + Math.sin(a) * r1}" y1="${50 - Math.cos(a) * r1}" x2="${50 + Math.sin(a) * 44}" y2="${50 - Math.cos(a) * 44}"/>`; }).join('');

$('#lang').addEventListener('click', () => { lang = lang === 'ar' ? 'en' : 'ar'; store.set('nm-lang', lang); history.replaceState(null, '', '#' + lang); render(); });
$('#city-btn').addEventListener('click', () => { const p = $('.picker'); p.hidden = !p.hidden; if (!p.hidden) $('#city-pick').focus(); });
$('#city-pick').addEventListener('change', (e) => {
  const p = places.find((x) => x[0] === e.target.value);
  if (p) { city = { en: p[0], ar: p[1], lat: p[2], lon: p[3], tz: p[4] }; store.set('nm-city2', p[0]); }
  $('.picker').hidden = true;
  render();
});
addEventListener('resize', () => { sizeSky(); tick(true); });

city = await resolveCity();
sizeSky();
render();
setInterval(() => tick(false), 1000);
