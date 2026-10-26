// A city's prayer-time page: the build (tools/site/cities.mjs) writes the city, its Qibla and the method into the
// HTML; here the browser adds today's times, the next prayer with its countdown, the date and the app's sky at this
// moment, all worked out on the visitor's device. Nothing is sent anywhere.
import { story } from '../story.js';
import { schedule, nextPrayer, skyT, clock, esc } from '../prayer.js';

const $ = (s) => document.querySelector(s);
const b = document.body.dataset;
const lang = document.documentElement.lang === 'ar' ? 'ar' : 'en';
const city = { en: b.en, ar: b.ar, lat: +b.lat, lon: +b.lon, tz: b.tz };
const name = lang === 'ar' ? city.ar : city.en;
const W = {
  en: { in: 'in', at: (time) => `at ${time} in ${name}`, sunrise: 'Sunrise' },
  ar: { in: 'بعد', at: (time) => `الساعة ${time} في ${name}`, sunrise: 'الشروق' },
}[lang];
const prayerName = (k) => (k === 'sunrise' ? W.sunrise : story[lang].prayers[k]);
const at = (d) => clock(d, city.tz, lang);

// The date in the city, in both calendars.
function dateLine(now) {
  const loc = lang === 'ar' ? 'ar-u-nu-latn' : 'en-GB';
  const g = new Intl.DateTimeFormat(loc, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: city.tz }).format(now);
  let h = '';
  try { h = new Intl.DateTimeFormat(`${loc.split('-u-')[0]}-u-ca-islamic-umalqura${lang === 'ar' ? '-nu-latn' : ''}`, { day: 'numeric', month: 'long', year: 'numeric', timeZone: city.tz }).format(now); } catch {}
  return h ? `${g} · ${h}` : g;
}

let plan = schedule(city), shown = '', lastSky = -1;
const sky = $('.sky'), ctx = sky.getContext('2d');
function sizeSky() {
  const k = Math.min(1.5, devicePixelRatio || 1);
  sky.width = Math.round(sky.clientWidth * k); sky.height = Math.round(sky.clientHeight * k);
  lastSky = -1;
}
function tick() {
  const now = new Date();
  if (now > plan.tomorrowFajr) plan = schedule(city);
  const next = nextPrayer(plan, now);
  const left = Math.max(0, Math.round((next[1] - now) / 1000));
  $('#count').textContent = `${Math.floor(left / 3600)}:${String(Math.floor(left / 60) % 60).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
  if (shown !== next[0] + +next[1]) {
    shown = next[0] + +next[1];
    $('#date').textContent = dateLine(now);
    $('#next-name').textContent = prayerName(next[0]);
    $('#next-in').textContent = W.in;
    $('#at').textContent = W.at(at(next[1]));
    $('#times').innerHTML = plan.today.map(([k, v]) => `<li${k === next[0] ? ' class="is-next"' : ''}><span>${esc(prayerName(k))}</span><b>${esc(at(v))}</b></li>`).join('');
  }
  const st = skyT(plan, now);
  if (Math.abs(st - lastSky) > 0.0015 && typeof drawSky === 'function' && sky.width) {
    drawSky(ctx, sky.width, sky.height, st, { horizon: 0.86, mosque: 0.13, mosqueX: lang === 'ar' ? 0.22 : 0.78 });
    lastSky = st;
  }
}
addEventListener('resize', () => { sizeSky(); tick(); });
sizeSky();
tick();
setInterval(tick, 1000);
