// Version 3 of the landing page: one day, from the night before Fajr to the night after Isha. The sky behind the
// page is the app's sky; scrolling moves it through the day, and each prayer time opens one part of the app,
// with the visitor's own time for it (worked out for their city, as on version 2).
import { story, links } from '../story.js';
import { places } from '../places.js';
import { resolveCity, schedule, qibla, clock, storesHTML, esc } from '../prayer.js';

const store = { get: (k) => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch {} } };
const $ = (s) => document.querySelector(s);
let lang = (location.hash === '#ar' || location.hash === '#en') ? location.hash.slice(1) : store.get('nm-lang') || ((navigator.languages || [navigator.language]).some((l) => /^ar/i.test(l)) ? 'ar' : 'en');

// Each part of the day: where it sits on the app's sky loop (0 Fajr … 1 Fajr again; the night before is just
// under 0), the prayer whose time it shows, and its picture.
const PARTS = [
  { id: 'start', t: -0.06, shot: null },
  { id: 'fajr', t: 0.03, prayer: 'fajr', shot: 'iphone/08-audio' },
  { id: 'sunrise', t: 0.16, prayer: 'sunrise', shot: 'iphone/02-qibla' },
  { id: 'dhuhr', t: 0.30, prayer: 'dhuhr', shot: 'iphone/09-mosques-radar' },
  { id: 'asr', t: 0.47, prayer: 'asr', shot: 'iphone/04-ask' },
  { id: 'maghrib', t: 0.62, prayer: 'maghrib', shot: 'iphone/06-widgets' },
  { id: 'isha', t: 0.75, prayer: 'isha', shot: 'iphone/05-classics' },
  { id: 'night', t: 0.88, shot: null },
];

const T = {
  en: {
    story: 'See the story', sunriseName: 'Sunrise', in: (time, city) => `${time} in ${city}`, rail: 'The day', scroll: 'Scroll through the day',
    start: ['Near Mosque', 'Your day of prayer, from Fajr to Isha.', 'Prayer times, the Qibla and the nearest mosque for wherever you are, in one calm app.'],
    fajr: ['The day starts before the sun.', 'A Fajr alarm at the Adhan or up to 45 minutes before, and the Adhan from Makkah or Madinah.'],
    sunrise: ['Know which way to face, wherever you wake.', (q, city) => `The Qibla from ${city} is ${q.deg}° from north, ${q.km.toLocaleString('en-US')} km from the Kaaba. Turn your phone and the arrow leads you.`],
    dhuhr: ['A mosque near you at Dhuhr.', 'Each mosque at its real direction and distance, and the walk there on the map.'],
    asr: ['A question, between Asr and Maghrib.', 'Answers quote the books stored on your phone, each with its source. They are not a fatwa.'],
    maghrib: ['Maghrib, at a glance.', 'The time left on your Home Screen and Lock Screen, in the Dynamic Island and on Apple Watch.'],
    isha: ['After Isha, a page of the classics.', '99 classical books, downloaded once and read without the internet.'],
    night: ['Privacy', 'No account, no ads, no tracking.', 'Prayer times and the Qibla are worked out on your device and work offline.'],
  },
  ar: {
    story: 'شاهد القصة', sunriseName: 'الشروق', in: (time, city) => `${time} في ${city}`, rail: 'اليوم', scroll: 'مرّر عبر اليوم',
    start: ['أقرب مسجد', 'يومك مع الصلاة، من الفجر إلى العشاء.', 'مواقيت الصلاة والقبلة وأقرب مسجد أينما كنت، في تطبيق واحد هادئ.'],
    fajr: ['يبدأ اليوم قبل الشمس.', 'منبّه للفجر عند الأذان أو قبله بما يصل إلى 45 دقيقة، والأذان من مكة أو المدينة.'],
    sunrise: ['اعرف اتجاه القبلة أينما استيقظت.', (q, city) => `القبلة من ${city} على ${q.deg}° من الشمال، وتبعد الكعبة ${q.km.toLocaleString('ar-u-nu-latn')} كم. أدِر هاتفك يرشدك السهم.`],
    dhuhr: ['مسجد قريب منك عند الظهر.', 'كل مسجد في اتجاهه الحقيقي وعلى بعده، وطريق المشي إليه على الخريطة.'],
    asr: ['سؤال بين العصر والمغرب.', 'تقتبس الإجابات من الكتب المحفوظة في هاتفك مع مصدر كل نص، وليست فتوى.'],
    maghrib: ['المغرب في لمحة.', 'الوقت المتبقي على الشاشة الرئيسية وشاشة القفل، وفي الجزيرة الديناميكية وعلى Apple Watch.'],
    isha: ['بعد العشاء، صفحة من أمهات الكتب.', '99 كتابًا تراثيًا، تنزّلها مرة واحدة وتقرؤها دون إنترنت.'],
    night: ['الخصوصية', 'بلا حساب، بلا إعلانات، بلا تتبّع.', 'تُحسب المواقيت والقبلة على جهازك، وتعمل دون إنترنت.'],
  },
};

let city = null, plan = null;
const cityName = () => (lang === 'ar' ? city.ar : city.en);
const prayerName = (k) => (k === 'sunrise' ? T[lang].sunriseName : story[lang].prayers[k]);

function render() {
  const t = T[lang], s = story[lang];
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  document.title = s.title;
  document.querySelectorAll('[data-t]').forEach((el) => { el.textContent = t[el.dataset.t] ?? s[el.dataset.t] ?? el.textContent; });
  $('#lang').textContent = s.switchTo;
  $('#lang').setAttribute('lang', lang === 'ar' ? 'en' : 'ar');
  plan = schedule(city);
  const times = Object.fromEntries(plan.today.map(([k, v]) => [k, v]));
  const q = qibla(city);
  $('#day').innerHTML = PARTS.map((p, i) => {
    const shot = p.shot ? `<figure class="phone"><img src="../assets/${lang}/${p.shot}.webp" alt="" loading="${i < 2 ? 'eager' : 'lazy'}" decoding="async"></figure>` : '';
    if (p.id === 'start') {
      const [eyebrow, h, body] = t.start;
      return `<section class="part start" id="start" data-i="${i}"><div class="words"><p class="eyebrow">${esc(eyebrow)}</p><h1>${esc(h)}</h1><p class="body">${esc(body)}</p><div class="stores">${storesHTML(s, links)}</div><p class="cue">${esc(t.scroll)}</p></div></section>`;
    }
    if (p.id === 'night') {
      const [eyebrow, h, body] = t.night;
      return `<section class="part night" id="night" data-i="${i}"><div class="words"><p class="eyebrow">${esc(eyebrow)}</p><h2>${esc(h)}</h2><p class="body">${esc(body)}</p><div class="stores">${storesHTML(s, links)}</div></div></section>`;
    }
    const [h, body] = t[p.id];
    const text = typeof body === 'function' ? body(q, cityName()) : body;
    return `<section class="part" id="${p.id}" data-i="${i}"><div class="words"><p class="eyebrow"><span class="prayer">${esc(prayerName(p.prayer))}</span><span class="when">${esc(t.in(clock(times[p.prayer], city.tz, lang), cityName()))}</span></p><h2>${esc(h)}</h2><p class="body">${esc(text)}</p></div>${shot}</section>`;
  }).join('');
  $('.rail').setAttribute('aria-label', t.rail);
  $('.rail').innerHTML = PARTS.filter((p) => p.prayer).map((p) => `<a href="#${p.id}" data-id="${p.id}"><span>${esc(prayerName(p.prayer))}</span></a>`).join('');
  const f = s.footer;
  $('#foot').innerHTML = [['../privacy.html', f.privacy], ['../terms.html', f.terms], ['../support.html', f.support], ['../security.html', f.security], ['../credits.html', f.credits]].map(([h, l]) => `<a href="${h}${lang === 'ar' ? '#ar' : ''}">${esc(l)}</a>`).join('');
  measure();
  frame(true);
}

// Scrolling moves the sky: between two parts, the sky runs from one's moment to the next's.
let centres = [], lastT = null;
function measure() { centres = [...document.querySelectorAll('.part')].map((el) => el.offsetTop + el.offsetHeight / 2); }
const sky = $('.sky'), ctx = sky.getContext('2d');
function sizeSky() { const k = Math.min(1.5, devicePixelRatio || 1); sky.width = Math.round(innerWidth * k); sky.height = Math.round(innerHeight * k); lastT = null; }
function frame(force) {
  if (!centres.length) return;
  const y = scrollY + innerHeight / 2;
  let i = 0;
  while (i + 1 < centres.length && y > centres[i + 1]) i++;
  const a = PARTS[i], b = PARTS[Math.min(i + 1, PARTS.length - 1)];
  const span = (centres[i + 1] ?? centres[i] + 1) - centres[i];
  const f = Math.max(0, Math.min(1, (y - centres[i]) / span));
  const e = f * f * (3 - 2 * f);
  const t = ((a.t + (b.t - a.t) * e) % 1 + 1) % 1;
  if (force || lastT === null || Math.abs(t - lastT) > 0.0015) {
    if (typeof drawSky === 'function') drawSky(ctx, sky.width, sky.height, t, { horizon: 0.84, mosque: 0.14, mosqueX: lang === 'ar' ? 0.2 : 0.8 });
    lastT = t;
  }
  // The rail marks the part in view; the words of the part in view come in.
  const near = Math.max(0, Math.min(PARTS.length - 1, f > 0.5 ? i + 1 : i));
  document.querySelectorAll('.part').forEach((el, k) => el.classList.toggle('on', k === near));
  document.querySelectorAll('.rail a').forEach((el) => { if (el.dataset.id === PARTS[near].id) el.setAttribute('aria-current', 'step'); else el.removeAttribute('aria-current'); });
  $('.rail').classList.toggle('off', !PARTS[near].prayer);
}
let ticking = false;
addEventListener('scroll', () => { if (!ticking) { ticking = true; requestAnimationFrame(() => { ticking = false; frame(false); }); } }, { passive: true });
addEventListener('resize', () => { sizeSky(); measure(); frame(true); });
addEventListener('load', () => { measure(); frame(true); });
$('#lang').addEventListener('click', () => { lang = lang === 'ar' ? 'en' : 'ar'; store.set('nm-lang', lang); history.replaceState(null, '', '#' + lang); render(); });

city = await resolveCity(places, '../assets/zones.json', store.get('nm-city2'));
sizeSky();
render();
