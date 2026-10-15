// Version 2 of the landing page: the visitor's next prayer first. The city comes from the browser's time zone
// (no location is asked for); the times are worked out here for the centre of that city with the method the app
// suggests for its country, and the sky behind them is the app's sky at this moment of the day.
import { story, links } from '../story.js';
import { places } from '../places.js';
import { resolveCity as findCity, schedule as plan2, nextPrayer, skyT, qibla as qiblaFrom, clock, storesHTML as stores, esc } from '../prayer.js';

const D = Math.PI / 180;
const store = { get: (k) => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch {} } };
const $ = (s) => document.querySelector(s);

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

let city = null;
const cityName = () => (lang === 'ar' ? city.ar : city.en);
const clockAt = (date) => clock(date, city.tz, lang);
const prayerName = (k) => (k === 'sunrise' ? T[lang].sunrise : story[lang].prayers[k]);

function render() {
  const t = T[lang], s = story[lang];
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  document.title = s.title;
  document.querySelectorAll('[data-t]').forEach((el) => { const k = el.dataset.t; el.textContent = t[k] ?? s[k] ?? el.textContent; });
  $('#lang').textContent = s.switchTo;
  $('#lang').setAttribute('lang', lang === 'ar' ? 'en' : 'ar');
  $('#hero-shot').src = `../assets/${lang}/iphone/01-prayer.webp`;
  $('#stores-top').innerHTML = $('#stores-end').innerHTML = stores(story[lang], links);
  $('#cards').innerHTML = t.cards.map(([img, h, p]) => `<article class="card"><div class="shot"><div class="${img.startsWith('watch') ? 'watch' : 'phone'}"><img src="../assets/${lang}/${img}.webp" alt="" loading="lazy" decoding="async"></div></div><h3>${esc(h)}</h3><p>${esc(p)}</p></article>`).join('');
  const f = s.footer;
  $('#foot').innerHTML = [['../privacy.html', f.privacy], ['../terms.html', f.terms], ['../support.html', f.support], ['../security.html', f.security], ['../credits.html', f.credits]].map(([h, l]) => `<a href="${h}${lang === 'ar' ? '#ar' : ''}">${esc(l)}</a>`).join('');
  // The city list: the named places, in this language's order; the visitor's own city first if it is not among them.
  const list = places.filter((p) => !/^(Madinah)$/.test(p[0])).map((p) => [p[0], lang === 'ar' ? p[1] : p[0]]).sort((a, b) => a[1].localeCompare(b[1], lang));
  if (!places.some((p) => p[0] === city.en)) list.unshift([city.en, cityName()]);
  $('#city-pick').innerHTML = list.map(([v, l]) => `<option value="${esc(v)}"${v === city.en ? ' selected' : ''}>${esc(l)}</option>`).join('');
  $('#city-pick').setAttribute('aria-label', t.change);
  $('#city-btn').setAttribute('aria-label', `${cityName()}, ${t.change}`);
  plan = plan2(city);
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
  if (now > plan.tomorrowFajr) plan = plan2(city);
  const next = nextPrayer(plan, now);
  const left = Math.max(0, Math.round((next[1] - now) / 1000));
  const hms = `${Math.floor(left / 3600)}:${String(Math.floor(left / 60) % 60).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
  $('#count').textContent = hms;
  if (force || $('#next-name').textContent !== prayerName(next[0])) {
    $('#next-name').textContent = prayerName(next[0]);
    $('#next-in').textContent = t.in;
    $('#at').textContent = t.at(clockAt(next[1]), cityName());
    $('#city').textContent = cityName();
    $('#times').innerHTML = plan.today.map(([k, v]) => `<li${k === next[0] ? ' class="is-next"' : ''}><span>${esc(prayerName(k))}</span><b>${esc(clockAt(v))}</b></li>`).join('');
    const q = qiblaFrom(city);
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

city = await findCity(places, '../assets/zones.json', store.get('nm-city2'));
sizeSky();
render();
setInterval(() => tick(false), 1000);
