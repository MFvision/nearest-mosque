// Renders the promo's pictures (docs/11-promo-script.md) for a timeline from promo.py, frame by frame in Chromium,
// streamed to ffmpeg. Apple's style: an off-white page, white cards with large radii and soft shadows, large calm
// type, one idea per scene, slow movement; the app's living sky opens and closes it; the app's real screens and
// recordings sit in simple device frames.
//
//   node promo.mjs <timeline.json> <out.mp4> <clicks.json>
//
// The timeline's "lang" is "ar" (default) or "en"; the English cut sets its titles larger, word by word, with the key
// words highlighted ("textfx"; the timeline can turn it on or off for either language).
//
// Assets (the timeline's "assets" folder): icon.png; iphone/, ipad/, mac/, tv/, vision/, watch/ with the language's
// store screenshots; clips/<header|mosques|search>/0001.jpg... (30 fps frames of the app preview recordings; a
// scene falls back to a screenshot when its clip is missing).
//
// The widgets scene (line 9) is live: a Home Screen with the app's widgets and its Dynamic Island going through the
// next-prayer countdown and the Qibla compass, and a Lock Screen with the Live Activity and Siri answering "when is
// the next prayer". The island and the cards are drawn after the app's own views (ios/Widgets/PrayerLiveActivity
// .swift, QiblaLiveActivity.swift, ios/Shared/IslandViews.swift) and its strings, with the times of the widget
// screenshot; the widgets are cut from iphone/06-widgets.png.
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const { chromium } = await import(process.env.PLAYWRIGHT || 'playwright');
const [, , specPath, out, clicksOut] = process.argv;
const spec = JSON.parse(fs.readFileSync(specPath, 'utf8'));
const A = spec.assets;
const has = (p) => fs.existsSync(path.join(A, p));
const clipFrames = (name) => (has(`clips/${name}`) ? fs.readdirSync(path.join(A, 'clips', name)).filter((f) => f.endsWith('.jpg')).length : 0);
const CLIPS = Object.fromEntries(['header', 'mosques', 'search'].map((n) => [n, clipFrames(n)]));
const LANG = spec.lang || 'ar';
const rtl = LANG === 'ar';
const fx = spec.textfx ?? LANG === 'en';

// One scene per script line: what it shows. `media` is a screenshot, or a clip with the second to start at and the
// playback rate. [[…]] marks the words the text effect highlights.
const SCENES = {
  ar: {
    1: { kind: 'sky', sky: [0.0, 0.11], title: 'كلُّ صلاةٍ [[في وقتها]]، أينما كنت' },
    2: { kind: 'brand', title: 'أقرب مسجد', chips: ['مواقيت الصلاة', 'القبلة', 'أقرب مسجد'] },
    3: { kind: 'phone', title: '[[الصلاة القادمة]] والوقت المتبقي', sub: 'وسماءٌ تتبدّل مع ساعات يومك', media: { clip: 'header', from: 0.4, rate: 1, still: 'iphone/01-prayer.png' } },
    4: { kind: 'phone', title: 'وجّه هاتفك نحو [[القبلة]]', sub: 'فإذا استقبلتها أضاءت لك', media: { clip: 'header', from: 10.3, rate: 1, still: 'iphone/02-qibla.png' } },
    5: { kind: 'phone', title: '[[أقرب المساجد]] إليك', sub: 'على الخريطة، بالمسافة والاتجاه', media: { still: 'iphone/03-mosques-map.png' } },
    6: { kind: 'phone', title: 'اسأل عن [[دينك]]', sub: 'إجاباتٌ من كتبٍ موثوقة على هاتفك، مع مصادرها', media: { clip: 'search', from: 2.8, rate: 1.55, still: 'iphone/04-ask.png' } },
    7: { kind: 'phone', title: '[[مكتبةٌ]] على هاتفك', sub: 'كتبٌ وفتاوى ومحاضرات، تقرؤها وتسمعها دون اتصال', media: { still: 'iphone/05-classics.png' } },
    8: { kind: 'phone', title: '[[أذانٌ]] بأصواتٍ تختارها', sub: 'وتذكيرٌ قبل كل صلاة', media: { still: 'iphone/08-audio.png' } },
    9: { kind: 'live', title: 'مواقيتك [[أمامك]]', sub: 'على الشاشة الرئيسية وفي الجزيرة الديناميكية' },
    10: { kind: 'watch', title: 'وعلى [[ساعتك]]', sub: 'رفيقٌ يفرح بصلاتك، وسجلٌّ لصلواتك، وتسبيحٌ بالتاج الرقمي', media: ['watch/03-watch-spirit.png', 'watch/01-watch-prayer.png', 'watch/04-watch-tasbih.png'] },
    11: { kind: 'devices', title: 'على [[كل أجهزتك]]', labels: ['آيفون', 'آيباد', 'ماك', 'آبل تي في', 'فيجن برو'] },
    12: { kind: 'languages', title: 'لغة' },
    13: { kind: 'privacy', title: 'هادئٌ و[[آمن]]', cards: [['بلا إعلانات', 'لا شيء يقطع صلاتك'], ['بلا تتبُّع', 'لا يغادر شيءٌ جهازك'], ['دون إنترنت', 'مواقيتك وقبلتك تعمل في كل مكان']] },
    14: { kind: 'end', sky: [0.6, 0.66], title: 'أقرب مسجد' },
    15: { kind: 'end', sky: [0.66, 0.7], title: 'أقرب مسجد', sub: 'حمّله الآن من متجر التطبيقات' },
  },
  en: {
    1: { kind: 'sky', sky: [0.0, 0.11], title: 'Every prayer [[on time]], wherever you are' },
    2: { kind: 'brand', title: 'Near Mosque', chips: ['Prayer times', 'Qibla', 'Nearest mosque'] },
    3: { kind: 'phone', title: 'Your [[next prayer]], and the time left', sub: 'A sky that changes with the hours of your day', media: { clip: 'header', from: 0.4, rate: 1, still: 'iphone/01-prayer.png' } },
    4: { kind: 'phone', title: 'Point your phone to the [[Qibla]]', sub: 'It lights up when you face it', media: { clip: 'header', from: 10.3, rate: 1, still: 'iphone/02-qibla.png' } },
    5: { kind: 'phone', title: 'The mosques [[nearest]] to you', sub: 'On the map, with distance and direction', media: { still: 'iphone/03-mosques-map.png' } },
    6: { kind: 'phone', title: 'Ask about [[your faith]]', sub: 'Answers from trusted books on your phone, with their sources', media: { clip: 'search', from: 2.8, rate: 1.55, still: 'iphone/04-ask.png' } },
    7: { kind: 'phone', title: 'A [[library]] on your phone', sub: 'Books, fatwas and lectures to read and listen to, offline', media: { still: 'iphone/05-classics.png' } },
    8: { kind: 'phone', title: 'The [[Adhan]] in the voices you choose', sub: 'And a reminder before every prayer', media: { still: 'iphone/08-audio.png' } },
    9: { kind: 'live', title: 'Your prayer times, [[at a glance]]', sub: 'On your Home Screen and in the Dynamic Island' },
    10: { kind: 'watch', title: 'And on your [[watch]]', sub: 'A companion that rejoices in your prayers, a record of every prayer, and tasbih on the Digital Crown', media: ['watch/03-watch-spirit.png', 'watch/01-watch-prayer.png', 'watch/04-watch-tasbih.png'] },
    11: { kind: 'devices', title: 'On [[all your devices]]', labels: ['iPhone', 'iPad', 'Mac', 'Apple TV', 'Vision Pro'] },
    12: { kind: 'languages', title: 'languages' },
    13: { kind: 'privacy', title: 'Calm and [[private]]', cards: [['No ads', 'Nothing interrupts your prayer'], ['No tracking', 'Nothing about you is followed'], ['Works offline', 'Prayer times and Qibla, anywhere']] },
    14: { kind: 'end', sky: [0.6, 0.66], title: 'Near Mosque' },
    15: { kind: 'end', sky: [0.66, 0.7], title: 'Near Mosque', sub: 'Download it now on the App Store' },
  },
}[LANG];

// The live scene's words: the app's strings (shared/i18n/strings.json, AppShortcuts.xcstrings) and the moment of
// the language's widget screenshot (Riyadh; English: Maghrib at 5:29 PM, 2:27:52 left; Arabic: Asr at 3:00 PM,
// 3:55 left), so the island, the Lock Screen and the widgets agree.
const LIVE = {
  ar: { prayer: 'العصر', at: '3:00 م', next: 'الصلاة القادمة', city: 'الرياض', qibla: 'القبلة', mosque: 'أقرب مسجد', left: 'انعطف يسارًا', facing: 'استقبلت القبلة',
    clock: '2:56', date: 'الاثنين 12 أكتوبر', ask: 'متى الصلاة القادمة في أقرب مسجد', answer: 'العصر الساعة 3:00 م، بعد 3 دقائق.', app: 'أقرب مسجد', seconds: 3 * 60 + 55, index: 2, icon: 'sun',
    slots: [['الفجر', '4:32'], ['الظهر', '11:40'], ['العصر', '3:00'], ['المغرب', '5:29'], ['العشاء', '6:59']] },
  en: { prayer: 'Maghrib', at: '5:29 PM', next: 'Next prayer', city: 'Riyadh', qibla: 'Qibla', mosque: 'Nearest Mosque', left: 'Turn left', facing: 'Facing the Qibla',
    clock: '3:01', date: 'Monday, October 12', ask: 'When is the next prayer in Near Mosque', answer: 'Maghrib is at 5:29 PM, in 2 hours, 27 minutes.', app: 'Near Mosque', seconds: 2 * 3600 + 27 * 60 + 52, index: 3, icon: 'sunset',
    slots: [['Fajr', '4:32'], ['Dhuhr', '11:40'], ['Asr', '3:00'], ['Maghrib', '5:29'], ['Isha', '6:59']] },
}[LANG];
const LEFT = LIVE.seconds; // seconds left at the scene's start

const wide = spec.cut === 'wide';
const W = spec.width, H = spec.height;
const u = (wide ? H : W) / 1080; // one design unit

// Scenes start a little before their line and end where the next one starts.
const lines = spec.lines;
const scenes = lines.map((l, i) => ({ ...SCENES[l.line], line: l.line, voice: l.end - l.start, start: i ? l.start - 0.35 : 0, end: i + 1 < lines.length ? lines[i + 1].start - 0.35 : spec.duration }));

const font = rtl ? "'IBM Plex Sans Arabic', sans-serif" : "'Inter', sans-serif";
const h1 = (rtl ? 64 : fx ? 82 : 66) * u * (wide ? 1 : rtl ? 1.22 : 1.12);
const h2 = (rtl ? 32 : fx ? 36 : 32) * u * (wide ? 1 : 1.25);
const css = `
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+Arabic:wght@400;500;600;700&family=Inter:wght@300;400;500;600;700;800&display=block');
  * { box-sizing: border-box; margin: 0; }
  html, body { width: ${W}px; height: ${H}px; overflow: hidden; background: #F5F5F7; }
  body { font-family: ${font}; color: #1D1D1F; direction: ${rtl ? 'rtl' : 'ltr'}; }
  .scene { position: absolute; inset: 0; opacity: 0; }
  .bg { position: absolute; inset: 0; background: radial-gradient(120% 90% at ${rtl ? 70 : 30}% 10%, #FFFFFF 0%, #F5F5F7 55%, #EFEDE8 100%); }
  .el { position: absolute; will-change: transform, opacity; }
  h1 { font-weight: ${rtl ? 700 : 700}; font-size: ${h1}px; line-height: ${rtl ? 1.25 : 1.1}; letter-spacing: ${rtl ? 0 : '-0.025em'}; }
  h2 { font-weight: ${rtl ? 500 : 500}; font-size: ${h2}px; line-height: ${rtl ? 1.5 : 1.35}; color: #6E6E73; letter-spacing: ${rtl ? 0 : '-0.01em'}; }
  .w { display: inline-block; will-change: transform, opacity; }
  .hl { background-image: linear-gradient(transparent 62%, rgba(226, 186, 84, 0.5) 62%, rgba(226, 186, 84, 0.5) 92%, transparent 92%); background-repeat: no-repeat; background-position: ${rtl ? 'right' : 'left'} 0; background-size: 0% 100%; }
  .hl .w { color: #A57A12; }
  .sky .hl, .end .hl { background-image: linear-gradient(transparent 64%, rgba(246, 216, 142, 0.35) 64%, rgba(246, 216, 142, 0.35) 92%, transparent 92%); }
  .sky .hl .w, .end .hl .w { color: #F6D88E; }
  .gold { color: #B8922F; }
  .card { background: #FFFFFF; border-radius: ${44 * u}px; box-shadow: 0 ${30 * u}px ${80 * u}px rgba(20, 30, 50, 0.10), 0 ${2 * u}px ${6 * u}px rgba(20, 30, 50, 0.05); }
  .phone { border-radius: ${62 * u}px; background: #111317; padding: ${11 * u}px; box-shadow: 0 ${40 * u}px ${90 * u}px rgba(20, 30, 50, 0.22), inset 0 0 0 ${2 * u}px #6B7079; }
  .phone > img { display: block; width: 100%; height: 100%; border-radius: ${52 * u}px; object-fit: cover; object-position: top; }
  .chip { display: inline-block; padding: ${16 * u}px ${34 * u}px; border-radius: ${999}px; background: #FFFFFF; font-size: ${30 * u}px; font-weight: 600; margin: ${8 * u}px; box-shadow: 0 ${10 * u}px ${30 * u}px rgba(20, 30, 50, 0.08); }
  .sky h1, .end h1, .end h2 { color: #FFFDF6; text-shadow: 0 ${4 * u}px ${30 * u}px rgba(8, 20, 40, 0.5); }
  .end h2 { color: #F4E7C5; }
  .label { font-size: ${26 * u}px; font-weight: 600; color: #6E6E73; text-align: center; }
  .num { font-family: 'Inter', sans-serif; font-weight: 600; }
  /* The live scene: a screen of 402 x 874 points, scaled into its phone. */
  .screen { position: absolute; left: 0; top: 0; width: 402px; transform-origin: 0 0; overflow: hidden; border-radius: 55px; font-family: ${rtl ? "'IBM Plex Sans Arabic'" : "'Inter'"}, sans-serif; }
  .screen > canvas { position: absolute; inset: 0; width: 100%; height: 100%; }
  .status { position: absolute; top: 17px; left: 0; right: 0; height: 22px; direction: ltr; color: #fff; font: 600 17px 'Inter', sans-serif; }
  .island { position: absolute; top: 11px; left: 50%; background: #000; overflow: hidden; color: #fff; direction: ${rtl ? 'rtl' : 'ltr'}; }
  .island .part { position: absolute; inset: 0; }
  .mono { font-family: 'Inter', sans-serif; font-variant-numeric: tabular-nums; }
`;

// File pages may read their own images (the widgets are cut out of a screenshot in a canvas).
const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
const page = await browser.newPage({ viewport: { width: W, height: H } });
const sky = fs.readFileSync(path.join(here, 'sky.js'), 'utf8');
// Loaded from a file next to the assets, so the page may show them (a page set from a string may not).
const htmlPath = path.join(A, '.promo.html');
fs.writeFileSync(htmlPath, `<html><head><meta charset="utf-8"><style>${css}</style></head><body><div id=root></div><script>${sky}</script></body></html>`);
await page.goto(pathToFileURL(htmlPath).href, { waitUntil: 'networkidle' });
await page.evaluate(() => document.fonts.ready);

// Builds the scenes in the page and returns, per scene, its elements' appearance times (relative to the scene).
const appear = await page.evaluate(async ({ scenes, wide, W, H, u, CLIPS, A, rtl, fx, LIVE }) => {
  const root = document.getElementById('root');
  const fileUrl = (p) => 'file://' + A + '/' + p;
  const mk = (parent, tag, cls, style = {}, html = '') => { const e = document.createElement(tag); if (cls) e.className = cls; Object.assign(e.style, style); e.innerHTML = html; parent.appendChild(e); return e; };
  const px = (v) => `${v}px`;
  // Wide layouts are drawn for Arabic (text on the right); left to right they are mirrored.
  const mx = (x, w = 0) => (rtl ? x : W - x - w);
  const mc = (x) => (rtl ? x : W - x);
  // Text split into words (the effect shows them one by one); [[…]] are highlighted. Plain text without the effect.
  const words = (str, d0, step) => {
    if (!fx) return { html: str.replace(/\[\[|\]\]/g, ''), end: d0 };
    let k = 0, html = '';
    str.split(/(\[\[.*?\]\])/).forEach((part) => {
      const hl = part.startsWith('[[');
      const ws = part.replace(/\[\[|\]\]/g, '').split(/(\s+)/);
      const inner = ws.map((w) => (/^\s+$/.test(w) || !w ? w : `<span class="w" data-d="${(d0 + step * k++).toFixed(2)}">${w}</span>`)).join('');
      html += hl ? `<span class="hl" data-d="${(d0 + step * k).toFixed(2)}">${inner}</span>` : inner;
    });
    return { html, end: d0 + step * k };
  };
  const out = [];
  const lives = [];
  scenes.forEach((s, i) => {
    const el = mk(root, 'div', 'scene ' + s.kind);
    el.id = 'scene' + i;
    const items = []; // [element, delay, click]
    // The text effect sets its own pace: the element stays, its words come in.
    const textItem = (box, delay) => { if (fx) box.dataset.fx = 1; items.push([box, delay, false]); };
    const text = (x, y, wBox, align = rtl ? 'right' : 'left') => {
      const box = mk(el, 'div', 'el', { left: px(x), top: px(y), width: px(wBox), textAlign: align });
      const t1 = words(s.title, 0, 0.11);
      const t = mk(box, 'h1', '', {}, t1.html);
      textItem(box, 0.15);
      if (s.sub) {
        const sb = mk(el, 'div', 'el', { left: px(x), top: px(y), width: px(wBox), textAlign: align });
        mk(sb, 'h2', '', { visibility: 'hidden' }, t1.html);
        mk(sb, 'h2', '', {}, words(s.sub, t1.end + 0.2, 0.05).html);
        sb.firstChild.style.fontSize = getComputedStyle(t).fontSize; sb.firstChild.style.lineHeight = getComputedStyle(t).lineHeight;
        sb.firstChild.style.letterSpacing = getComputedStyle(t).letterSpacing; sb.firstChild.style.fontWeight = 700; sb.firstChild.style.marginBottom = px(18 * u);
        items.push([sb, fx ? 0.15 : 0.45, false]);
        if (fx) sb.dataset.fx = 1;
      }
      return box;
    };
    const phone = (x, y, h, media, delay = 0.25) => {
      const w = h * 0.4615;
      const p = mk(el, 'div', 'el phone', { left: px(x - w / 2), top: px(y), width: px(w), height: px(h) });
      const img = mk(p, 'img', '');
      img.src = fileUrl(typeof media === 'string' ? media : media.still);
      if (media.clip && CLIPS[media.clip]) { img.dataset.clip = media.clip; img.dataset.from = media.from; img.dataset.rate = media.rate; }
      items.push([p, delay, true]);
      return p;
    };
    // A phone whose screen is drawn here (402 x 874 points).
    const livePhone = (x, y, h, delay) => {
      const w = h * 0.4615;
      const p = mk(el, 'div', 'el phone', { left: px(x - w / 2), top: px(y), width: px(w), height: px(h) });
      const inner = w - 22 * u;
      const scr = mk(p, 'div', 'screen', { transform: `translate(${11 * u}px, ${11 * u}px) scale(${inner / 402})`, borderRadius: px(52 * u * 402 / inner), height: px((h - 22 * u) * 402 / inner) });
      items.push([p, delay, true]);
      return scr;
    };
    if (s.kind === 'sky' || s.kind === 'end') {
      const c = mk(el, 'canvas', '', { position: 'absolute', inset: 0 });
      c.width = W; c.height = H; c.dataset.sky = JSON.stringify(s.sky);
      const box = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (s.kind === 'end' ? (wide ? 0.16 : 0.24) : wide ? 0.3 : 0.32)), textAlign: 'center', padding: `0 ${8 * u}%` });
      let d = s.kind === 'sky' ? 0.6 : 0.2;
      if (s.kind === 'end') {
        const icon = mk(box, 'img', '', { width: px(170 * u), height: px(170 * u), borderRadius: px(38 * u), boxShadow: `0 ${20 * u}px ${60 * u}px rgba(0,0,0,0.35)`, marginBottom: px(30 * u) });
        icon.src = fileUrl('icon.png');
      }
      const t1 = words(s.title, 0, 0.13);
      mk(box, 'h1', '', {}, t1.html);
      if (s.sub) mk(box, 'h2', '', { marginTop: px(16 * u) }, words(s.sub, t1.end + 0.3, 0.07).html);
      // With the effect the icon fades in as an element of its own, the words after it.
      if (fx && s.kind === 'end') { box.dataset.fx = 1; box.firstChild.classList.add('w'); box.firstChild.dataset.d = 0; }
      textItem(box, d);
    } else {
      mk(el, 'div', 'bg');
      if (s.kind === 'brand') {
        const box = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.2 : 0.3)), textAlign: 'center' });
        const icon = mk(box, 'img', '', { width: px(220 * u), height: px(220 * u), borderRadius: px(50 * u), boxShadow: `0 ${30 * u}px ${70 * u}px rgba(20,30,50,0.18)` });
        icon.src = fileUrl('icon.png');
        items.push([box, 0.1, true]);
        const name = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.2 : 0.3) + 270 * u), textAlign: 'center' });
        mk(name, 'h1', '', { fontSize: px((rtl ? 84 : 96) * u) }, words(s.title, 0, 0.14).html);
        textItem(name, 0.5);
        s.chips.forEach((c, k) => {
          const row = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.2 : 0.3) + 400 * u + (wide ? 0 : k * 100 * u)), textAlign: 'center' });
          if (wide) { row.style.left = px(mx(W / 2 - 520 * u + (2 - k) * 360 * u, 320 * u)); row.style.right = 'auto'; row.style.width = px(320 * u); }
          mk(row, 'span', 'chip', {}, c);
          items.push([row, 1.6 + k * 1.3, true]);
        });
      } else if (s.kind === 'phone') {
        if (wide) { text(mx(W * 0.5, W * 0.4), H * 0.34, W * 0.4); phone(mc(W * 0.3), H * 0.08, H * 0.84, s.media); }
        else { text(W * 0.08, H * 0.07, W * 0.84, 'center'); phone(W * 0.5, H * 0.28, H * 0.66, s.media); }
      } else if (s.kind === 'live') {
        let a, b;
        if (wide) { text(mx(W * 0.565, W * 0.37), H * 0.36, W * 0.37); a = livePhone(mc(W * 0.4), H * 0.1, H * 0.8, 0.25); b = livePhone(mc(W * 0.17), H * 0.1, H * 0.8, 0.7); }
        else { text(W * 0.08, H * 0.07, W * 0.84, 'center'); a = livePhone(mc(W * 0.73), H * 0.33, H * 0.5, 0.25); b = livePhone(mc(W * 0.27), H * 0.33, H * 0.5, 0.7); }
        lives.push({ scene: i, home: a, lock: b });
      } else if (s.kind === 'watch') {
        const size = wide ? H * 0.42 : W * 0.4;
        const spots = wide ? [[0.12, 0.3], [0.27, 0.22], [0.42, 0.3]] : [[0.28, 0.36], [0.72, 0.36], [0.5, 0.6]];
        if (wide) text(mx(W * 0.55, W * 0.38), H * 0.36, W * 0.38); else text(W * 0.08, H * 0.07, W * 0.84, 'center');
        s.media.forEach((m, k) => {
          const [fx0, fy] = spots[k];
          const cx = wide ? mc(W * fx0) : W * fx0;
          const wch = mk(el, 'div', 'el', { left: px(cx - size * 0.41), top: px(H * fy), width: px(size * 0.82), height: px(size), borderRadius: px(size * 0.24), background: '#111317', padding: px(size * 0.05), boxShadow: `0 ${30 * u}px ${70 * u}px rgba(20,30,50,0.22), inset 0 0 0 ${2 * u}px #6B7079` });
          const img = mk(wch, 'img', '', { width: '100%', height: '100%', borderRadius: px(size * 0.19), objectFit: 'cover', display: 'block' });
          img.src = fileUrl(m);
          mk(wch, 'div', '', { position: 'absolute', right: px(-size * 0.035), top: px(size * 0.28), width: px(size * 0.04), height: px(size * 0.14), borderRadius: px(size * 0.02), background: '#3A3D44' });
          items.push([wch, 0.3 + k * 0.9, true]);
        });
      } else if (s.kind === 'devices') {
        // The devices come in one by one as the voice names them: each rises and tilts into place, a light passes
        // over its screen, it floats, and its screen changes to a second view of the app.
        const t = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.09 : 0.08)), textAlign: 'center' });
        mk(t, 'h1', '', {}, words(s.title, 0, 0.11).html);
        textItem(t, 0.1);
        // [screens, centre x and y (fractions, left to right), screen width and height (units), kind, depth]: a family
        // shot, the TV and the Mac behind, the iPad and the iPhone in front, the Vision Pro window floating.
        const list = [[['iphone/01-prayer.png', 'iphone/02-qibla.png'], 0.615, 0.74, 172, 372, 'phone', 5], [['ipad/01-prayer.png', 'ipad/03-mosques-map.png'], 0.31, 0.73, 420, 315, 'tablet', 4],
          [['mac/01-prayer.png', 'mac/03-ask.png'], 0.55, 0.47, 640, 400, 'mac', 2], [['tv/01-tv-board.png', 'tv/02-tv-qibla.png'], 0.225, 0.45, 600, 338, 'tv', 1],
          [['vision/01-prayer.png', 'vision/02-mosques-map.png'], 0.835, 0.4, 440, 248, 'vision', 3]];
        const z = wide ? u : u * 0.55;
        const beat = (s.voice || 5) * 0.9 / list.length;
        // The name of the device coming in, under the title.
        const names = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * 0.09 + parseFloat(getComputedStyle(t.firstChild).fontSize) * 1.3), textAlign: 'center', height: px(60 * u) });
        names.dataset.names = 1;
        s.labels.forEach((l, k) => { const n = mk(names, 'div', '', { position: 'absolute', left: 0, right: 0, fontSize: px(40 * u), fontWeight: 600, color: '#A57A12', opacity: 0 }, l); n.dataset.at = 0.3 + k * beat; });
        items.push([names, 0, false]);
        names.dataset.fx = 1;
        list.forEach(([shots, fxp, fyp, sw, sh, kind, depth], k) => {
          const cx = rtl ? W * (1 - fxp) : W * fxp, cy = H * fyp;
          const box = mk(el, 'div', 'el', { left: px(cx - sw * z / 2), top: px(cy - sh * z / 2), width: px(sw * z), height: px(sh * z), transformOrigin: '50% 100%', zIndex: depth });
          box.dataset.dev = k;
          if (kind === 'vision') box.dataset.tilt = rtl ? 10 : -10;
          const r = { phone: 30, tablet: 18, mac: 12, tv: 6, vision: 22 }[kind] * z;
          const bez = { phone: 6, tablet: 8, mac: 7, tv: 5, vision: 0 }[kind] * z;
          const frame = mk(box, 'div', '', { position: 'absolute', inset: 0, borderRadius: px(r), background: kind === 'vision' ? 'transparent' : '#111317', padding: px(bez),
            boxShadow: kind === 'vision' ? `0 ${30 * z}px ${80 * z}px rgba(20,30,50,0.30), 0 0 0 ${1.5 * z}px rgba(255,255,255,0.7)` : `0 ${30 * z}px ${70 * z}px rgba(20,30,50,0.25), inset 0 0 0 ${1.5 * z}px #6B7079` });
          const scr = mk(frame, 'div', '', { position: 'relative', width: '100%', height: '100%', borderRadius: px(Math.max(2, r - bez)), overflow: 'hidden', background: '#000' });
          shots.forEach((src, j) => { const im = mk(scr, 'img', '', { position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top', opacity: j ? 0 : 1 }); im.src = fileUrl(src); if (j) im.dataset.swap = 1; });
          const shine = mk(scr, 'div', '', { position: 'absolute', top: '-20%', bottom: '-20%', width: '45%', left: '-60%', background: 'linear-gradient(100deg, transparent 0%, rgba(255,255,255,0) 30%, rgba(255,255,255,0.45) 50%, rgba(255,255,255,0) 70%, transparent 100%)' });
          shine.dataset.shine = 1;
          if (kind === 'phone') mk(scr, 'div', '', { position: 'absolute', top: px(8 * z), left: '50%', width: px(52 * z), height: px(15 * z), marginLeft: px(-26 * z), borderRadius: px(8 * z), background: '#000' });
          if (kind === 'mac') {
            mk(box, 'div', '', { position: 'absolute', left: px(-56 * z), right: px(-56 * z), top: '100%', height: px(16 * z), borderRadius: `${2 * z}px ${2 * z}px ${16 * z}px ${16 * z}px`, background: 'linear-gradient(#E6E7EB,#B5B7BD)', boxShadow: `0 ${14 * z}px ${30 * z}px rgba(20,30,50,0.2)` });
            mk(box, 'div', '', { position: 'absolute', left: '50%', width: px(110 * z), marginLeft: px(-55 * z), top: '100%', height: px(6 * z), borderRadius: `0 0 ${6 * z}px ${6 * z}px`, background: '#A4A6AD' });
          }
          if (kind === 'tv') {
            mk(box, 'div', '', { position: 'absolute', left: '50%', width: px(22 * z), marginLeft: px(-11 * z), top: '100%', height: px(46 * z), background: 'linear-gradient(90deg,#9EA1A8,#CDCFD4,#9EA1A8)' });
            mk(box, 'div', '', { position: 'absolute', left: '50%', width: px(200 * z), marginLeft: px(-100 * z), top: `calc(100% + ${46 * z}px)`, height: px(9 * z), borderRadius: px(5 * z), background: '#B9BBC1', boxShadow: `0 ${10 * z}px ${22 * z}px rgba(20,30,50,0.18)` });
          }
          items.push([box, 0.3 + k * beat, true]);
        });
      } else if (s.kind === 'languages') {
        const ws = ['مرحبًا', 'Hello', 'Bonjour', 'Hola', 'Merhaba', 'Selamat', 'خوش آمدید', 'Привет', 'Welkom', '你好', 'नमस्ते', 'স্বাগতম', 'Olá', 'Szia', 'สวัสดี', 'Xin chào', 'ਜੀ ਆਇਆਂ', 'സ്വാഗതം'];
        ws.forEach((w, k) => {
          const a = (k / ws.length) * Math.PI * 2, r = (wide ? H * 0.36 : W * 0.4) * (0.85 + 0.15 * ((k * 7) % 3) / 2);
          const e = mk(el, 'div', 'el', { left: px(W / 2 + Math.cos(a) * r * (wide ? 1.6 : 1) - 150 * u), top: px(H / 2 + Math.sin(a) * r - 30 * u), width: px(300 * u), textAlign: 'center', fontSize: px(30 * u), fontWeight: 500, color: '#8E8E93' }, w);
          items.push([e, 0.05 + k * 0.06, false]);
        });
        const big = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H / 2 - 150 * u), textAlign: 'center' });
        mk(big, 'div', 'num gold', { fontSize: px(200 * u), lineHeight: 1 }, '36');
        mk(big, 'h1', '', { fontSize: px(70 * u) }, s.title);
        items.push([big, 0.15, true]);
      } else if (s.kind === 'privacy') {
        const t = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.13 : 0.1)), textAlign: 'center' });
        mk(t, 'h1', '', {}, words(s.title, 0, 0.11).html);
        textItem(t, 0.1);
        const icons = ['⊘', '◎', '☾'];
        s.cards.forEach(([a, b], k) => {
          const cw = wide ? W * 0.25 : W * 0.8, ch = wide ? H * 0.42 : H * 0.2;
          const x = wide ? mx(W / 2 + (1 - k) * (cw + 40 * u) - cw / 2, cw) : W * 0.1;
          const y = wide ? H * 0.36 : H * 0.25 + k * (ch + 40 * u);
          const c = mk(el, 'div', 'el card', { left: px(x), top: px(y), width: px(cw), height: px(ch), padding: px(44 * u), textAlign: 'center', display: 'flex', flexDirection: 'column', justifyContent: 'center' });
          mk(c, 'div', 'gold', { fontSize: px(64 * u), lineHeight: 1.1, marginBottom: px(18 * u) }, icons[k]);
          mk(c, 'h1', '', { fontSize: px(48 * u) }, a);
          mk(c, 'h2', '', { fontSize: px(28 * u), marginTop: px(8 * u) }, b);
          items.push([c, 0.5 + k * 1.4, true]);
        });
      }
    }
    items.forEach(([e, d, c], k) => { e.dataset.delay = d; e.dataset.click = c ? 1 : 0; e.dataset.k = k; });
    out.push(items.map(([, d, c]) => [d, c]));
  });

  // The live scene's screens. The widgets come out of the widget screenshot: the boxes that are not its grey.
  const shot = new Image();
  shot.src = fileUrl('iphone/06-widgets.png');
  await shot.decode().catch(() => {});
  const cuts = [];
  if (shot.naturalWidth) {
    const cv = document.createElement('canvas'); cv.width = shot.naturalWidth; cv.height = shot.naturalHeight;
    const cx = cv.getContext('2d'); cx.drawImage(shot, 0, 0);
    const d = cx.getImageData(0, 0, cv.width, cv.height).data;
    const b0 = ((cv.height / 2 | 0) * cv.width + 4) * 4;
    const bg = [d[b0], d[b0 + 1], d[b0 + 2]];
    const off = (x, y) => { const k = (y * cv.width + x) * 4; return Math.abs(d[k] - bg[0]) + Math.abs(d[k + 1] - bg[1]) + Math.abs(d[k + 2] - bg[2]) > 40; };
    const midX = cv.width / 2 | 0;
    let y = Math.round(cv.height * 0.08), top = -1;
    for (; y < cv.height; y++) {
      // A row is in a widget when it differs from the grey at any of three columns (text can be grey-ish).
      const on = off(midX, y) || off(cv.width * 0.12 | 0, y) || off(cv.width * 0.3 | 0, y);
      if (on && top < 0) top = y;
      if (!on && top >= 0) {
        if (y - top > 100) {
          let l = 0, r = cv.width - 1; const ym = (top + y) / 2 | 0;
          while (l < midX && !off(l, ym)) l++;
          while (r > midX && !off(r, ym)) r--;
          cuts.push({ x: l, y: top, w: r - l + 1, h: y - top, scale: 402 / cv.width });
        }
        top = -1;
      }
    }
  }
  const kaabaG = `<path d="M4 8 L13 4 L22 8 L22 19 L13 23 L4 19 Z" fill="#1b1b1d" stroke="#3a3a3e" stroke-width="0.6"/><path d="M4 8 L13 12 L22 8 L13 4 Z" fill="#2c2c30"/><path d="M4 10.5 L13 14.5 L22 10.5 L22 12.2 L13 16.2 L4 12.2 Z" fill="#D7AE4B"/>`;
  const arrowG = (gold) => `<path d="M0 -11 L8 9 L0 4 Z" fill="${gold}"/><path d="M0 -11 L-8 9 L0 4 Z" fill="#FFFFFF"/>`;
  const kaaba = (s) => `<svg width="${s}" height="${s}" viewBox="0 0 26 26"><path d="M4 8 L13 4 L22 8 L22 19 L13 23 L4 19 Z" fill="#1b1b1d" stroke="#3a3a3e" stroke-width="0.6"/><path d="M4 8 L13 12 L22 8 L13 4 Z" fill="#2c2c30"/><path d="M4 10.5 L13 14.5 L22 10.5 L22 12.2 L13 16.2 L4 12.2 Z" fill="#D7AE4B"/><path d="M13 12 L13 23" stroke="#000" stroke-width="0.5" opacity="0.6"/></svg>`;
  // The app's arrow: a split arrowhead, white on one side, gold on the other.
  const arrow = (s, gold) => `<svg width="${s}" height="${s}" viewBox="-12 -12 24 24" style="display:block;overflow:visible"><path d="M0 -11 L8 9 L0 4 Z" fill="${gold}"/><path d="M0 -11 L-8 9 L0 4 Z" fill="#FFFFFF"/></svg>`;
  const sunset = (s, c) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="${c}" stroke-width="2" stroke-linecap="round"><path d="M3 18h18M7 18a5 5 0 0 1 10 0M12 5v4M5 10l2 2M19 10l-2 2M9 3l3 2 3-2"/></svg>`;
  const sunG = (s, c) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="${c}" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>`;
  const symbol = (s, c) => (LIVE.icon === 'sun' ? sunG(s, c) : sunset(s, c));
  const pin = (s, c) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="${c}" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="7" r="3"/><path d="M12 10v8M6 16c-2 .6-3 1.4-3 2.2C3 19.8 7 21 12 21s9-1.2 9-2.8c0-.8-1-1.6-3-2.2"/></svg>`;
  const north = (s, c) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M12 3 L18 19 L12 15 L6 19 Z" fill="${c}"/></svg>`;
  const columns = (s, c) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="${c}" stroke-width="2" stroke-linecap="round"><path d="M3 9 L12 4 L21 9 Z M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18"/></svg>`;
  const gold = '#E2B649';
  lives.forEach(({ home, lock }) => {
    // Home Screen: the sky as wallpaper, the status bar, the two widgets, the island.
    [home, lock].forEach((scr) => { const c = mk(scr, 'canvas', ''); c.width = 804; c.height = 1748; c.dataset.wall = 1; });
    const status = (scr) => mk(scr, 'div', 'status', {}, `<span style="position:absolute;left:38px">${LIVE.clock}</span><span style="position:absolute;right:34px;top:2px;display:flex;gap:6px;align-items:center"><svg width="19" height="12" viewBox="0 0 19 12"><rect x="0" y="8" width="3" height="4" rx="1" fill="#fff"/><rect x="5" y="5.5" width="3" height="6.5" rx="1" fill="#fff"/><rect x="10" y="3" width="3" height="9" rx="1" fill="#fff"/><rect x="15" y="0" width="3" height="12" rx="1" fill="#fff"/></svg><svg width="17" height="12" viewBox="0 0 17 12"><path d="M8.5 11.5 L5.6 8.4 A4.2 4.2 0 0 1 11.4 8.4 Z M2.8 5.6 A8.2 8.2 0 0 1 14.2 5.6 L12.7 7.1 A6 6 0 0 0 4.3 7.1 Z M0 2.8 A12 12 0 0 1 17 2.8 L15.5 4.3 A10 10 0 0 0 1.5 4.3 Z" fill="#fff"/></svg><svg width="27" height="13" viewBox="0 0 27 13"><rect x="0.5" y="0.5" width="23" height="12" rx="3.5" fill="none" stroke="#fff" stroke-opacity="0.45"/><rect x="2" y="2" width="20" height="9" rx="2" fill="#fff"/><rect x="24.5" y="4.5" width="1.6" height="4" rx="0.8" fill="#fff" fill-opacity="0.45"/></svg></span>`);
    status(home);
    let wy = 92;
    cuts.slice(0, 2).forEach((c) => {
      const w = c.w * c.scale, h = c.h * c.scale;
      const box = mk(home, 'div', '', { position: 'absolute', left: px((402 - w) / 2), top: px(wy), width: px(w), height: px(h), borderRadius: px(26), overflow: 'hidden', boxShadow: '0 10px 30px rgba(0,0,0,0.18)' });
      const cv = mk(box, 'canvas', '', { width: px(w), height: px(h), display: 'block' });
      cv.width = c.w; cv.height = c.h; cv.getContext('2d').drawImage(shot, c.x, c.y, c.w, c.h, 0, 0, c.w, c.h);
      wy += h + 22;
    });
    // The island: one black shape that grows and shrinks; each state's content in a part of its own.
    const isl = mk(home, 'div', 'island', { width: '126px', height: '37px', marginLeft: '-63px', borderRadius: '19px' });
    isl.dataset.island = 1;
    const part = (name, html) => { const p = mk(isl, 'div', 'part', { opacity: 0 }, html); p.dataset.part = name; return p; };
    const lead = rtl ? 'right' : 'left', trail = rtl ? 'left' : 'right';
    part('pc', `<div style="position:absolute;${lead}:12px;top:9px;display:flex;gap:4px;align-items:center;font-size:12.5px;font-weight:600">${symbol(16, gold)}<span>${LIVE.prayer}</span></div>
      <div class="mono" data-count="short" style="position:absolute;${trail}:12px;top:10px;font-size:12.5px;font-weight:600;color:${gold}"></div>`);
    part('pe', `<div style="position:absolute;${lead}:22px;top:20px;display:flex;gap:8px;align-items:center;font-size:17px;font-weight:600">${symbol(20, gold)}<span>${LIVE.prayer}</span></div>
      <div class="mono" style="position:absolute;${trail}:22px;top:21px;font-size:17px;font-weight:600;color:rgba(255,255,255,0.8)">${LIVE.at}</div>
      <div style="position:absolute;left:0;right:0;top:50px;text-align:center;font-size:12px;color:rgba(255,255,255,0.7)">${LIVE.next} · ${LIVE.city}</div>
      <div class="mono" data-count="long" style="position:absolute;left:0;right:0;top:72px;text-align:center;font-size:36px;font-weight:600;color:${gold};letter-spacing:-0.01em"></div>
      <div style="position:absolute;left:22px;right:22px;top:124px;height:5px;border-radius:3px;background:rgba(255,255,255,0.18);direction:ltr"><div data-progress=1 style="height:100%;border-radius:3px;background:${gold};width:44%"></div></div>
      <div style="position:absolute;left:18px;right:18px;top:144px;display:flex;gap:8px">
        <div style="flex:1;height:38px;border-radius:19px;background:${gold};color:#081C29;display:flex;gap:6px;align-items:center;justify-content:center;font-size:14px;font-weight:600">${north(14, '#081C29')}${LIVE.qibla}</div>
        <div style="flex:1;height:38px;border-radius:19px;background:rgba(255,255,255,0.15);display:flex;gap:6px;align-items:center;justify-content:center;font-size:14px;font-weight:600">${columns(15, '#fff')}${LIVE.mosque}</div></div>`);
    part('qc', `<div data-arrow="22" style="position:absolute;${lead}:14px;top:7px;width:22px;height:22px">${arrow(22, gold)}</div>
      <div data-turnlabel=1 class="mono" style="position:absolute;${trail}:14px;top:10px;font-size:13px;font-weight:700;color:${gold};direction:ltr"></div>`);
    // The arc dial (QiblaArcDial): an arc of ticks, the Kaaba at its top, a gold dot where the Qibla is, the glow
    // between them, the arrow in the middle.
    const ticks = Array.from({ length: 13 }, (_, k) => { const a = (-70 + k * (140 / 12)) * Math.PI / 180; const r1 = 70, r2 = k % 3 ? 76 : 80; return `<line x1="${Math.sin(a) * r1}" y1="${-Math.cos(a) * r1}" x2="${Math.sin(a) * r2}" y2="${-Math.cos(a) * r2}" stroke="rgba(255,255,255,0.45)" stroke-width="1.4"/>`; }).join('');
    part('qe', `<div style="position:absolute;${lead}:22px;top:18px;display:flex;gap:8px;align-items:center;font-size:17px;font-weight:600"><span data-kaaba=1>${kaaba(26)}</span><span>${LIVE.qibla}</span></div>
      <div style="position:absolute;${trail}:22px;top:14px;text-align:${trail}"><div class="mono" style="font-size:22px;font-weight:700;color:${gold}">243°</div><div style="font-size:11px;color:rgba(255,255,255,0.6)">${LIVE.city}</div></div>
      <svg data-dial=1 width="200" height="120" viewBox="-100 -96 200 120" style="position:absolute;left:50%;margin-left:-100px;top:52px;overflow:visible">
        <path d="M ${-Math.sin(70 * Math.PI / 180) * 70} ${-Math.cos(70 * Math.PI / 180) * 70} A 70 70 0 0 1 ${Math.sin(70 * Math.PI / 180) * 70} ${-Math.cos(70 * Math.PI / 180) * 70}" stroke="rgba(255,255,255,0.35)" stroke-width="1.6" fill="none"/>${ticks}
        <line x1="-74" y1="${-Math.cos(70 * Math.PI / 180) * 70}" x2="-62" y2="${-Math.cos(70 * Math.PI / 180) * 70}" stroke="rgba(255,255,255,0.5)" stroke-width="1.6"/>
        <line x1="62" y1="${-Math.cos(70 * Math.PI / 180) * 70}" x2="74" y2="${-Math.cos(70 * Math.PI / 180) * 70}" stroke="rgba(255,255,255,0.5)" stroke-width="1.6"/>
        <path data-glow=1 d="" stroke="${gold}" stroke-width="5" stroke-linecap="round" fill="none" opacity="0.85" style="filter:drop-shadow(0 0 4px ${gold})"/>
        <circle data-dot=1 r="7" fill="${gold}" stroke="#fff" stroke-width="1.6"/>
        <circle cx="0" cy="-70" r="13" fill="#fff" data-ring="1"/>
        <g transform="translate(-9.1 -79.1) scale(0.7)">${kaabaG}</g>
        <g data-needle=1><g transform="translate(0 -6) scale(1.9)">${arrowG(gold)}</g></g>
      </svg>
      <div data-hint=1 style="position:absolute;left:0;right:0;top:174px;text-align:center;font-size:15px;font-weight:600"></div>`);

    // Lock Screen: the date and the clock, the Live Activity card at the bottom, then Siri.
    status(lock);
    mk(lock, 'div', '', { position: 'absolute', left: 0, right: 0, top: '84px', textAlign: 'center', color: 'rgba(255,255,255,0.92)', fontSize: '20px', fontWeight: 600 }, LIVE.date).dataset.siri = 'clock';
    mk(lock, 'div', 'mono', { position: 'absolute', left: 0, right: 0, top: '104px', textAlign: 'center', color: 'rgba(255,255,255,0.95)', fontSize: '104px', fontWeight: 600, letterSpacing: '-0.03em', textShadow: '0 4px 30px rgba(0,20,50,0.25)', direction: 'ltr' }, LIVE.clock).dataset.siri = 'clock';
    const card = mk(lock, 'div', '', { position: 'absolute', left: '14px', right: '14px', top: '560px', height: '178px', borderRadius: '26px', padding: '16px', color: '#fff', background: 'linear-gradient(180deg,#1F3B78 0%,#2E5A9E 100%)', boxShadow: '0 12px 40px rgba(0,0,0,0.25)', direction: rtl ? 'rtl' : 'ltr' });
    card.dataset.card = 1;
    card.innerHTML = `<div style="display:flex;justify-content:space-between;font-size:13px;font-weight:600;color:rgba(255,255,255,0.8)"><span style="display:flex;gap:6px;align-items:center">${pin(14, 'rgba(255,255,255,0.8)')}${LIVE.city}</span><span class="mono">${LIVE.clock}</span></div>
      <div style="display:flex;justify-content:space-between;align-items:baseline;margin-top:6px"><span style="display:flex;gap:8px;align-items:center;font-size:24px;font-weight:700">${symbol(22, '#F2CF6B')}${LIVE.prayer}</span><span class="mono" data-count="long" style="font-size:31px;font-weight:600;color:#F2CF6B;direction:ltr"></span></div>
      <div style="margin-top:8px;height:5px;border-radius:3px;background:rgba(255,255,255,0.2);direction:ltr"><div data-progress=1 style="height:100%;border-radius:3px;background:#F2CF6B;width:44%"></div></div>
      <div style="display:flex;gap:4px;margin-top:10px">${LIVE.slots.map(([n, t], k) => `<div style="flex:1;text-align:center;padding:4px 0;border-radius:8px;font-size:11px;line-height:1.35;${k === LIVE.index ? 'background:#F2CF6B;color:#14284F;font-weight:700' : 'color:rgba(255,255,255,0.85)'}"><div>${n}</div><div class="mono">${t}</div></div>`).join('')}</div>`;
    // Siri: the glowing edge, what was asked, and the answer from the app (NextPrayerIntent's dialog).
    const glow = mk(lock, 'div', '', { position: 'absolute', inset: 0, borderRadius: '55px', padding: '9px', overflow: 'hidden', opacity: 0, pointerEvents: 'none',
      webkitMask: 'linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)', webkitMaskComposite: 'xor', maskComposite: 'exclude' });
    glow.dataset.siri = 'glow';
    glow.innerHTML = `<div style="position:absolute;left:50%;top:50%;width:1100px;height:1100px;margin:-550px 0 0 -550px;filter:blur(6px);background:conic-gradient(from 0deg,#FF6A3D,#FF3DBB,#8A5CFF,#3DB2FF,#5DF2C6,#FFC93D,#FF6A3D)"></div>`;
    const ringMask = mk(lock, 'div', '', { position: 'absolute', inset: '9px', borderRadius: '47px', boxShadow: '0 0 18px 6px rgba(255,255,255,0.25) inset', opacity: 0, pointerEvents: 'none' });
    ringMask.dataset.siri = 'ring';
    const ask = mk(lock, 'div', '', { position: 'absolute', left: '24px', right: '24px', top: '770px', textAlign: 'center', color: '#fff', fontSize: '17px', fontWeight: 600, textShadow: '0 2px 10px rgba(0,0,0,0.35)', opacity: 0 }, `“${LIVE.ask}”`);
    ask.dataset.siri = 'ask';
    const ans = mk(lock, 'div', '', { position: 'absolute', left: '14px', right: '14px', top: '58px', borderRadius: '26px', padding: '14px 16px', background: 'rgba(255,255,255,0.86)', backdropFilter: 'blur(20px)', boxShadow: '0 12px 40px rgba(0,0,0,0.2)', color: '#1D1D1F', opacity: 0, direction: rtl ? 'rtl' : 'ltr' });
    ans.dataset.siri = 'answer';
    ans.innerHTML = `<div style="display:flex;gap:8px;align-items:center;font-size:12px;font-weight:600;color:#6E6E73"><img src="${fileUrl('icon.png')}" style="width:22px;height:22px;border-radius:6px">${LIVE.app}</div><div style="margin-top:6px;font-size:17px;font-weight:600;line-height:1.3">${LIVE.answer}</div>`;
  });
  await Promise.all([...document.images].map((im) => im.decode().catch(() => {})));
  return out;
}, { scenes, wide, W, H, u, CLIPS, A, rtl, fx, LIVE });

// The moments elements appear with a click; the live scene adds one per change of the island and for Siri.
const clicks = [];
scenes.forEach((s, i) => appear[i].forEach(([d, c]) => { if (c) clicks.push(s.start + d + 0.15); }));
const liveT = (dur) => { const f = Math.min(1, dur / 7.2); return { compact: 0.5 * f, expand: 1.5 * f, qibla: 3.5 * f, qexpand: 4.1 * f, sweep: [4.4 * f, 6.4 * f], siri: 2.6 * f, answer: 3.9 * f }; };
scenes.forEach((s) => { if (s.kind === 'live') { const T = liveT(s.end - s.start); [T.compact, T.expand, T.qibla, T.qexpand, T.siri, T.answer].forEach((d) => clicks.push(s.start + d)); } });
clicks.sort((a, b) => a - b);
fs.writeFileSync(clicksOut, JSON.stringify(clicks));

await page.evaluate((A) => { window.__A = A; }, A);
// STILLS="5,15.2" saves those moments as <out>-<t>.png instead of the video (to check the layout).
const stills = process.env.STILLS ? process.env.STILLS.split(',').map(Number) : null;
const frames = stills ? stills.length : Math.round(spec.duration * 30);
const ff = stills ? null : spawn('ffmpeg', ['-nostdin', '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', '30', '-i', '-',
  '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', '-profile:v', 'high', out], { stdio: ['pipe', 'inherit', 'inherit'] });
const sceneTimes = scenes.map(({ start, end, kind }) => ({ start, end, kind, T: kind === 'live' ? liveT(end - start) : null }));
for (let f = 0; f < frames; f++) {
  const t = stills ? stills[f] : f / 30;
  await page.evaluate(async ({ t, scenes, LEFT, LIVE, rtl, u }) => {
    const ease = (x) => 1 - Math.pow(1 - Math.min(1, Math.max(0, x)), 3);
    // A soft spring for the island: a little past the target, then back.
    const spring = (x) => { x = Math.min(1, Math.max(0, x)); return 1 - Math.exp(-6 * x) * Math.cos(7 * x) * (1 - x) - (x === 1 ? 0 : 0); };
    const lerp = (a, b, p) => a + (b - a) * p;
    const waits = [];
    scenes.forEach((s, i) => {
      const el = document.getElementById('scene' + i);
      const lt = t - s.start, dur = s.end - s.start;
      const vis = lt >= -0.01 && lt <= dur + 0.5;
      el.style.display = vis ? 'block' : 'none';
      if (!vis) return;
      // In over half a second, out over half a second into the next scene.
      el.style.opacity = Math.min(ease(lt / 0.5), 1 - ease((lt - dur) / 0.5));
      el.querySelectorAll('.el').forEach((e) => {
        const d = +e.dataset.delay;
        // A slow drift for the whole scene, as in Apple's films.
        const drift = 1 + 0.025 * Math.min(1, lt / dur);
        if (e.dataset.dev !== undefined) {
          const k = +e.dataset.dev, x = (lt - d) / 0.9;
          const p = 1 - Math.exp(-6 * Math.max(0, x)) * Math.cos(7 * Math.max(0, x)) * Math.max(0, 1 - Math.max(0, x));
          const pp = x <= 0 ? 0 : Math.min(1.08, p);
          const float = Math.sin(lt * 1.1 + k * 1.3) * 5 * u * ease((lt - d - 0.8) / 1.2);
          e.style.opacity = ease((lt - d) / 0.45);
          const tilt = +(e.dataset.tilt || 0);
          e.style.transform = `perspective(${1400 * u}px) translateY(${(1 - pp) * 90 * u + float}px) rotateX(${(1 - pp) * 24}deg) rotateY(${tilt * Math.min(1, pp)}deg) scale(${0.86 + 0.14 * pp})`;
          const sh = e.querySelector('[data-shine]');
          if (sh) sh.style.left = `${-60 + 220 * ease((lt - d - 0.45) / 1.1)}%`;
          e.querySelectorAll('[data-swap]').forEach((im) => { im.style.opacity = ease((lt - d - 2.1) / 0.7); });
          return;
        }
        if (e.dataset.names) {
          e.style.opacity = 1;
          const ns = [...e.children];
          ns.forEach((n, k) => {
            const a = +n.dataset.at, b = k + 1 < ns.length ? +ns[k + 1].dataset.at : 1e9;
            const p = ease((lt - a) / 0.35) * (1 - ease((lt - b + 0.1) / 0.3));
            n.style.opacity = p;
            n.style.transform = `translateY(${(1 - ease((lt - a) / 0.35)) * 14 * u - ease((lt - b + 0.1) / 0.3) * 14 * u}px)`;
          });
          return;
        }
        if (e.dataset.fx) {
          // Word by word: each rises and sharpens in; the highlight sweeps under its words once they are in.
          e.style.opacity = 1;
          e.querySelectorAll('.w').forEach((w) => {
            const p = ease((lt - d - +w.dataset.d) / 0.55);
            w.style.opacity = p;
            w.style.transform = `translateY(${(1 - p) * 26 * u}px)`;
            w.style.filter = p < 1 ? `blur(${(1 - p) * 8 * u}px)` : 'none';
          });
          e.querySelectorAll('.hl').forEach((h) => { h.style.backgroundSize = `${ease((lt - d - +h.dataset.d - 0.2) / 0.7) * 100}% 100%`; });
          return;
        }
        const p = ease((lt - d) / 0.8);
        e.style.opacity = p;
        e.style.transform = `translateY(${(1 - p) * 40}px) scale(${(0.96 + 0.04 * p) * (e.classList.contains('phone') ? drift : 1)})`;
      });
      el.querySelectorAll('img[data-clip]').forEach((img) => {
        const n = Math.max(1, Math.floor((+img.dataset.from + Math.max(0, lt) * +img.dataset.rate) * 30));
        const src = 'file://' + window.__A + '/clips/' + img.dataset.clip + '/' + String(n).padStart(4, '0') + '.jpg';
        if (img.src !== src) { img.src = src; waits.push(img.decode().catch(() => {})); }
      });
      el.querySelectorAll('canvas[data-sky]').forEach((c) => {
        const [a, b] = JSON.parse(c.dataset.sky);
        drawSky(c.getContext('2d'), c.width, c.height, a + (b - a) * Math.min(1, Math.max(0, lt / dur)), { arc: false, mosque: 0.17 });
      });
      if (s.kind !== 'live') return;
      const T = s.T, now = Math.max(0, lt);
      // The wallpapers: the afternoon sky, the clouds drifting.
      el.querySelectorAll('canvas[data-wall]').forEach((c) => drawSky(c.getContext('2d'), c.width, c.height, 0.43 + now * 0.0015, { arc: false, mosque: 0.12, horizon: 0.8 }));
      // The countdown, as the app writes it (H:MM:SS; M:SS under an hour).
      const left = Math.max(0, LEFT - Math.floor(now));
      const hms = left >= 3600 ? `${Math.floor(left / 3600)}:${String(Math.floor(left / 60) % 60).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}` : `${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`;
      el.querySelectorAll('[data-count]').forEach((c) => { c.textContent = hms; });
      el.querySelectorAll('[data-progress]').forEach((p) => { p.style.width = `${44 + now * 0.02}%`; });
      // The island: idle -> prayer (compact) -> prayer (expanded) -> Qibla (compact) -> Qibla (expanded).
      const isl = el.querySelector('[data-island]');
      const S = { idle: [126, 37, 18.5], pc: [232, 37, 18.5], pe: [374, 206, 46], qc: [210, 37, 18.5], qe: [374, 212, 46] };
      const steps = [[0, 'idle'], [T.compact, 'pc'], [T.expand, 'pe'], [T.qibla, 'qc'], [T.qexpand, 'qe']];
      let k = 0; while (k + 1 < steps.length && now >= steps[k + 1][0]) k++;
      const from = S[steps[Math.max(0, k - 1)][1]], to = S[steps[k][1]];
      const p = k === 0 ? 1 : spring((now - steps[k][0]) / 0.55);
      const [w, h, r] = [0, 1, 2].map((j) => lerp(from[j], to[j], p));
      isl.style.width = `${w}px`; isl.style.height = `${h}px`; isl.style.marginLeft = `${-w / 2}px`; isl.style.borderRadius = `${r}px`;
      const fade = ease((now - steps[k][0] - 0.18) / 0.3);
      isl.querySelectorAll('[data-part]').forEach((pt) => { pt.style.opacity = pt.dataset.part === steps[k][1] ? fade : 0; });
      // The Qibla: the phone turns from 38° off towards the Kaaba until it faces it (within 5°), then everything glows.
      const x = Math.min(1, Math.max(0, (now - T.sweep[0]) / (T.sweep[1] - T.sweep[0])));
      const sp = x * x * (3 - 2 * x);
      const turn = -38 * (1 - sp);
      const facing = Math.abs(turn) <= 5;
      const sign = 1; // the dial reads the same way in every language (as in the app)
      isl.querySelectorAll('[data-arrow]').forEach((a) => { a.style.transform = `rotate(${-turn * sign}deg)`; });
      const tl = isl.querySelector('[data-turnlabel]');
      if (tl) tl.textContent = facing ? '' : `↰ ${Math.round(Math.abs(turn))}°`;
      const R = 70, ang = turn * sign * Math.PI / 180;
      const dot = isl.querySelector('[data-dot]');
      if (dot) { dot.setAttribute('cx', Math.sin(ang) * R); dot.setAttribute('cy', -Math.cos(ang) * R); dot.style.opacity = facing ? 0 : 1; }
      const glow = isl.querySelector('[data-glow]');
      if (glow) glow.setAttribute('d', Math.abs(ang) < 0.01 ? '' : `M ${Math.sin(ang) * R} ${-Math.cos(ang) * R} A ${R} ${R} 0 0 ${ang < 0 ? 1 : 0} 0 ${-R}`);
      const needle = isl.querySelector('[data-needle]');
      if (needle) { needle.setAttribute('transform', `rotate(${-turn * sign})`); needle.style.filter = facing ? 'drop-shadow(0 0 8px #E2B649)' : 'none'; }
      const ring = isl.querySelector('[data-ring]');
      if (ring) { ring.setAttribute('stroke', facing ? '#E2B649' : 'none'); ring.setAttribute('stroke-width', 3); ring.style.filter = facing ? 'drop-shadow(0 0 8px #E2B649)' : 'none'; }
      const hint = isl.querySelector('[data-hint]');
      if (hint) { hint.textContent = facing ? LIVE.facing : LIVE.left; hint.style.color = facing ? '#E2B649' : '#fff'; }
      const kb = isl.querySelector('[data-kaaba]');
      if (kb) kb.style.filter = facing ? 'drop-shadow(0 0 6px #E2B649)' : 'none';
      // Siri on the Lock Screen: the edge lights up, the question shows, the answer card comes down.
      const sOn = ease((now - T.siri) / 0.5);
      el.querySelectorAll('[data-siri]').forEach((e) => {
        const kind = e.dataset.siri;
        if (kind === 'glow') {
          e.style.opacity = sOn * 0.95;
          e.firstChild.style.transform = `rotate(${now * 60}deg)`;
        } else if (kind === 'ring') e.style.opacity = sOn;
        else if (kind === 'clock') e.style.opacity = 1 - 0.75 * ease((now - T.answer) / 0.5);
        else if (kind === 'ask') { const q = ease((now - T.siri - 0.3) / 0.5); e.style.opacity = q * (1 - ease((now - T.answer - 1.2) / 0.5)); e.style.transform = `translateY(${(1 - q) * 12}px)`; }
        else if (kind === 'answer') { const q = spring((now - T.answer) / 0.6); e.style.opacity = Math.min(1, q); e.style.transform = `translateY(${(1 - q) * -30}px) scale(${0.94 + 0.06 * q})`; }
      });
    });
    await Promise.all(waits);
  }, { t, scenes: sceneTimes, LEFT, LIVE, rtl, u });
  if (stills) { await page.screenshot({ path: `${out}-${t}.png` }); continue; }
  const png = await page.screenshot({ type: 'png' });
  if (!ff.stdin.write(png)) await new Promise((r) => ff.stdin.once('drain', r));
  if (f % 300 === 0) process.stdout.write(`${f}/${frames}\n`);
}
if (ff) { ff.stdin.end(); await new Promise((r) => ff.on('close', r)); }
await browser.close();
