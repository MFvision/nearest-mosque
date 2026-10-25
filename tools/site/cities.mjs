// Writes a prayer-time page for each city in website/places.js (the cities named on the globe, each with a checked
// Arabic name): website/prayer-times/<city>/ in English and website/ar/prayer-times/<city>/ in Arabic, plus a page
// listing them in each language, and the sitemap. The city, its Qibla and the calculation method are written into
// the HTML; today's times are worked out in the visitor's browser (prayer-times/city.js), so nothing is sent.
// Run by tools/site/prerender.mjs, or alone: node tools/site/cities.mjs
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const site = join(here, '../../website');
const { story, links } = await import(join(site, 'story.js'));
const { places } = await import(join(site, 'places.js'));
const { qibla, methodFor, storesHTML, esc } = await import(join(site, 'prayer.js'));

const slug = (en) => en.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
const cities = places.map(([en, ar, lat, lon, tz]) => ({ en, ar, lat, lon, tz, slug: slug(en) }));
const SITE = 'https://nearmosque.net/';
const num = (n, lang) => n.toLocaleString(lang === 'ar' ? 'ar-u-nu-latn' : 'en-US');

const W = {
  en: {
    title: (c) => `Prayer times in ${c} today · Near Mosque`,
    description: (c, deg) => `Today's Fajr, Dhuhr, Asr, Maghrib and Isha times in ${c}, the next prayer with a countdown, and the Qibla at ${deg}° from north.`,
    h1: (c) => `Prayer times in ${c}`, today: 'Today', in: 'in',
    qTitle: (c) => `Qibla from ${c}`, qSub: (deg, km) => `${deg}° from north · ${km} km to the Kaaba`,
    note: (c, m) => `Times for the centre of ${c}, ${m} method. The app works them out for exactly where you are, and you can choose the method your mosque follows.`,
    noscript: 'Today’s times are worked out in your browser. Turn on JavaScript to see them, or get the app.',
    more: 'More from the app', others: 'Prayer times in other cities', all: 'All cities', allTitle: 'Prayer times by city · Near Mosque',
    allH1: 'Prayer times by city', allLead: 'Today’s times and the Qibla for each city, worked out in your browser. The app does the same for exactly where you are.',
    allDesc: 'Today’s prayer times and the Qibla for cities around the world, from Makkah to Toronto.',
    privacyEyebrow: 'Near Mosque', privacy: 'Every prayer on time, wherever you are.', privacyLine: 'No account, no ads, no tracking. Prayer times and the Qibla work without the internet.',
    home: 'Home',
    methods: { mwl: 'Muslim World League', uaq: 'Umm al-Qura', egypt: 'Egyptian General Authority', karachi: 'Karachi', isna: 'ISNA', dubai: 'Dubai', kuwait: 'Kuwait', qatar: 'Qatar', sea: 'Singapore and Southeast Asia', turkey: 'Diyanet' },
    cards: [
      ['iphone/09-mosques-radar', 'See which way the mosque is, and how far.', 'A compass with each mosque at its real direction, and a map for the walk there.'],
      ['iphone/08-audio', 'The Adhan from Makkah or Madinah.', 'With a reminder before each prayer, a Fajr alarm and Friday reminders.'],
      ['iphone/04-ask', 'Ask about Islam and see the source.', 'Answers quote the books on your phone, each with its source. They are not a fatwa.'],
    ],
  },
  ar: {
    title: (c) => `مواقيت الصلاة في ${c} اليوم · أقرب مسجد`,
    description: (c, deg) => `مواقيت الفجر والظهر والعصر والمغرب والعشاء اليوم في ${c}، والصلاة القادمة بعدٍّ تنازلي، والقبلة على ${deg}° من الشمال.`,
    h1: (c) => `مواقيت الصلاة في ${c}`, today: 'اليوم', in: 'بعد',
    qTitle: (c) => `القبلة من ${c}`, qSub: (deg, km) => `${deg}° من الشمال · ${km} كم إلى الكعبة`,
    note: (c, m) => `المواقيت لوسط ${c} بطريقة ${m}. أما التطبيق فيحسبها لمكانك بالضبط، وتختار فيه الطريقة التي يتبعها مسجدك.`,
    noscript: 'تُحسب مواقيت اليوم في متصفحك. فعّل JavaScript لتراها، أو حمّل التطبيق.',
    more: 'وفي التطبيق أيضًا', others: 'مواقيت الصلاة في مدن أخرى', all: 'كل المدن', allTitle: 'مواقيت الصلاة في المدن · أقرب مسجد',
    allH1: 'مواقيت الصلاة في المدن', allLead: 'مواقيت اليوم والقبلة لكل مدينة، تُحسب في متصفحك. ويحسبها التطبيق لمكانك بالضبط.',
    allDesc: 'مواقيت الصلاة اليوم والقبلة في مدن العالم، من مكة المكرمة إلى تورنتو.',
    privacyEyebrow: 'أقرب مسجد', privacy: 'كل صلاةٍ في وقتها، أينما كنت.', privacyLine: 'بلا حساب، بلا إعلانات، بلا تتبُّع. والمواقيت والقبلة تعمل دون إنترنت.',
    home: 'الرئيسية',
    methods: { mwl: 'رابطة العالم الإسلامي', uaq: 'أم القرى', egypt: 'الهيئة المصرية العامة للمساحة', karachi: 'كراتشي', isna: 'أمريكا الشمالية', dubai: 'دبي', kuwait: 'الكويت', qatar: 'قطر', sea: 'سنغافورة وجنوب شرق آسيا', turkey: 'ديانت' },
    cards: [
      ['iphone/09-mosques-radar', 'اعرف اتجاه أقرب مسجد وكم يبعد عنك.', 'بوصلة تعرض كل مسجد في اتجاهه الحقيقي، وخريطة لطريق المشي إليه.'],
      ['iphone/08-audio', 'الأذان من مكة أو المدينة.', 'مع تذكير قبل كل صلاة، ومنبّه للفجر، وتذكيرات يوم الجمعة.'],
      ['iphone/04-ask', 'اسأل عن دينك، واعرف المصدر.', 'تقتبس الإجابات من الكتب المحفوظة في هاتفك مع مصدر كل نص، وليست فتوى.'],
    ],
  },
};

const pathOf = (lang, c) => `${lang === 'ar' ? 'ar/' : ''}prayer-times/${c ? c.slug + '/' : ''}`;
const D = Math.PI / 180;
const ticks = Array.from({ length: 36 }, (_, i) => { const a = i * 10 * D, r1 = i % 9 ? 42 : 39; return `<line x1="${(50 + Math.sin(a) * r1).toFixed(2)}" y1="${(50 - Math.cos(a) * r1).toFixed(2)}" x2="${(50 + Math.sin(a) * 44).toFixed(2)}" y2="${(50 - Math.cos(a) * 44).toFixed(2)}"/>`; }).join('');

function head(lang, root, title, description, path, alt) {
  return `<!doctype html>
<html lang="${lang}" dir="${lang === 'ar' ? 'rtl' : 'ltr'}">
<head>
<!-- Written by tools/site/cities.mjs. Edit that script, not this file. -->
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(title)}</title>
<meta name="description" content="${esc(description)}">
<link rel="canonical" href="${SITE}${path}">
<link rel="alternate" hreflang="${lang}" href="${SITE}${path}">
<link rel="alternate" hreflang="${lang === 'ar' ? 'en' : 'ar'}" href="${SITE}${alt}">
<meta name="theme-color" content="#081B29">
<meta property="og:type" content="website">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:url" content="${SITE}${path}">
<meta property="og:image" content="${SITE}assets/share-card.jpg">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="${root}assets/icon-180.png">
<link rel="apple-touch-icon" href="${root}assets/icon-180.png">
<link rel="preload" href="${root}fonts/readex-pro-${lang === 'ar' ? 'arabic' : 'latin'}-600-normal.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="${root}fonts.css">
<link rel="stylesheet" href="${root}devices.css">
<link rel="stylesheet" href="${root}prayer-times/city.css">
</head>`;
}
function bar(lang, root, alt) {
  const s = story[lang];
  return `<header class="bar">
  <a class="brand" href="${root}${lang === 'ar' ? 'ar/' : ''}"><img src="${root}assets/icon.webp" alt="" width="34" height="34"><span>${esc(s.title)}</span></a>
  <nav class="tools"><a class="pill" href="${root}${lang === 'ar' ? 'ar/' : ''}">${esc(W[lang].home)}</a><a class="pill" href="${root}${alt}" lang="${lang === 'ar' ? 'en' : 'ar'}" hreflang="${lang === 'ar' ? 'en' : 'ar'}">${esc(s.switchTo)}</a></nav>
</header>`;
}
function foot(lang, root) {
  const f = story[lang].footer, h = (p) => `${root}${p}.html${lang === 'ar' ? '#ar' : ''}`;
  return `<footer><nav>${[[`${root}${pathOf(lang)}`, W[lang].all], [h('privacy'), f.privacy], [h('terms'), f.terms], [h('support'), f.support], [h('credits'), f.credits]].map(([u, l]) => `<a href="${u}">${esc(l)}</a>`).join('')}</nav></footer>`;
}
const cityList = (lang, root, list) => `<ul>${list.map((c) => `<li><a href="${root}${pathOf(lang, c)}">${esc(lang === 'ar' ? c.ar : c.en)}<small>${esc(lang === 'ar' ? c.en : c.ar)}</small></a></li>`).join('')}</ul>`;

function cityPage(lang, c) {
  const w = W[lang], s = story[lang], root = lang === 'ar' ? '../../../' : '../../';
  const name = lang === 'ar' ? c.ar : c.en, q = qibla(c), method = methodFor(c.tz);
  // Other cities: those in the same time zone first, then the nearest others, eleven in all.
  const dist = (o) => Math.hypot(o.lat - c.lat, (o.lon - c.lon) * Math.cos(c.lat * D));
  const others = cities.filter((o) => o !== c).sort((a, b) => (b.tz === c.tz) - (a.tz === c.tz) || dist(a) - dist(b)).slice(0, 11);
  const ld = { '@context': 'https://schema.org', '@type': 'WebPage', name: w.title(name), inLanguage: lang, about: { '@type': 'City', name, geo: { '@type': 'GeoCoordinates', latitude: c.lat, longitude: c.lon } } };
  return `${head(lang, root, w.title(name), w.description(name, q.deg), pathOf(lang, c), pathOf(lang === 'ar' ? 'en' : 'ar', c))}
<body data-en="${esc(c.en)}" data-ar="${esc(c.ar)}" data-lat="${c.lat}" data-lon="${c.lon}" data-tz="${esc(c.tz)}">
<script type="application/ld+json">${JSON.stringify(ld)}</script>
${bar(lang, root, pathOf(lang === 'ar' ? 'en' : 'ar', c))}
<section class="hero" aria-labelledby="title">
  <canvas class="sky" aria-hidden="true"></canvas>
  <div class="hero-in">
    <div class="now">
      <p class="eyebrow" id="date">${esc(w.today)}</p>
      <h1 id="title">${esc(w.h1(name))}</h1>
      <p class="next" aria-live="off"><span id="next-name"></span> <span id="next-in" class="soft"></span> <span id="count" class="count"></span></p>
      <p class="at" id="at"></p>
      <ol class="times" id="times" aria-label="${esc(w.today)}"></ol>
      <noscript><p class="at">${esc(w.noscript)}</p></noscript>
      <div class="qibla">
        <svg class="dial" viewBox="0 0 100 100" aria-hidden="true">
          <circle cx="50" cy="50" r="46" class="ring"/>
          <g class="ticks">${ticks}</g>
          <text x="50" y="16" class="n">N</text>
          <g id="needle" style="transform: rotate(${q.deg}deg)"><path d="M50 14 L55 50 L50 56 L45 50 Z" class="arrow"/><rect x="45.5" y="8" width="9" height="9" rx="1.6" class="kaaba"/></g>
          <circle cx="50" cy="50" r="3" class="hub"/>
        </svg>
        <div><p class="q-title">${esc(w.qTitle(name))}</p><p class="q-sub">${esc(w.qSub(q.deg, num(q.km, lang)))}</p></div>
      </div>
      <div class="stores">${storesHTML(s, links)}</div>
      <p class="note">${esc(w.note(name, w.methods[method]))}</p>
    </div>
    <figure class="hero-phone"><div class="phone"><img src="${root}assets/${lang}/iphone/01-prayer.webp" alt="" width="440" height="956" fetchpriority="high"></div></figure>
  </div>
</section>
<main>
  <section class="cities" aria-labelledby="others">
    <h2 id="others">${esc(w.others)}</h2>
    ${cityList(lang, root, others)}
    <a class="all" href="${root}${pathOf(lang)}">${esc(w.all)}</a>
  </section>
  <section class="more" aria-labelledby="more-title">
    <h2 id="more-title">${esc(w.more)}</h2>
    <div class="cards">${w.cards.map(([img, h, p]) => `<article class="card"><div class="shot"><div class="phone"><img src="${root}assets/${lang}/${img}.webp" alt="" loading="lazy" decoding="async"></div></div><h3>${esc(h)}</h3><p>${esc(p)}</p></article>`).join('')}</div>
  </section>
  <section class="end" aria-labelledby="end-title">
    <p class="eyebrow">${esc(w.privacyEyebrow)}</p>
    <h2 id="end-title">${esc(w.privacy)}</h2>
    <p class="lead">${esc(w.privacyLine)}</p>
    <div class="stores">${storesHTML(s, links)}</div>
  </section>
</main>
${foot(lang, root)}
<script src="${root}sky.js"></script>
<script type="module" src="${root}prayer-times/city.js"></script>
</body>
</html>
`;
}

function indexPage(lang) {
  const w = W[lang], root = lang === 'ar' ? '../../' : '../';
  const sorted = [...cities].sort((a, b) => (lang === 'ar' ? a.ar : a.en).localeCompare(lang === 'ar' ? b.ar : b.en, lang));
  return `${head(lang, root, w.allTitle, w.allDesc, pathOf(lang), pathOf(lang === 'ar' ? 'en' : 'ar'))}
<body class="list-page">
${bar(lang, root, pathOf(lang === 'ar' ? 'en' : 'ar'))}
<main>
  <section class="cities" aria-labelledby="title">
    <h1 id="title">${esc(w.allH1)}</h1>
    <p class="lead">${esc(w.allLead)}</p>
    ${cityList(lang, root, sorted)}
  </section>
</main>
${foot(lang, root)}
</body>
</html>
`;
}

const write = (rel, html) => { const out = join(site, rel); mkdirSync(dirname(out), { recursive: true }); writeFileSync(out, html); };
for (const lang of ['en', 'ar']) {
  for (const c of cities) write(pathOf(lang, c) + 'index.html', cityPage(lang, c));
  write(pathOf(lang) + 'index.html', indexPage(lang));
}
const urls = ['', 'ar/', 'prayer-times/', 'ar/prayer-times/', ...cities.flatMap((c) => [pathOf('en', c), pathOf('ar', c)]), 'support.html', 'privacy.html', 'terms.html', 'security.html', 'credits.html'];
writeFileSync(join(site, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.map((u) => `  <url><loc>${SITE}${u}</loc></url>`).join('\n')}\n</urlset>\n`);
console.log(`wrote ${cities.length} cities in English and Arabic, two city lists and sitemap.xml`);
