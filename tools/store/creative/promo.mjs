// Renders the promo's pictures (docs/11-promo-script.md) for a timeline from promo.py, frame by frame in Chromium,
// streamed to ffmpeg. Apple's style: an off-white page, white cards with large radii and soft shadows, large calm
// type, one idea per scene, slow movement; the app's living sky opens and closes it; the app's real screens and
// recordings sit in simple device frames.
//
//   node promo.mjs <timeline.json> <out.mp4> <clicks.json>
//
// Assets (the timeline's "assets" folder): icon.png; iphone/, ipad/, mac/, tv/, vision/, watch/ with the Arabic
// store screenshots; clips/<header|mosques|search>/0001.jpg... (30 fps frames of the app preview recordings; a
// scene falls back to a screenshot when its clip is missing).
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
const url = (p) => pathToFileURL(path.join(A, p)).href;
const clipFrames = (name) => (has(`clips/${name}`) ? fs.readdirSync(path.join(A, 'clips', name)).filter((f) => f.endsWith('.jpg')).length : 0);
const CLIPS = Object.fromEntries(['header', 'mosques', 'search'].map((n) => [n, clipFrames(n)]));

// One scene per script line: what it shows. `media` is a screenshot, or a clip with the second to start at and the
// playback rate.
const SCENES = {
  1: { kind: 'sky', sky: [0.0, 0.11], title: 'كلُّ صلاةٍ في وقتها، أينما كنت' },
  2: { kind: 'brand', title: 'أقرب مسجد', chips: ['مواقيت الصلاة', 'القبلة', 'أقرب مسجد'] },
  3: { kind: 'phone', title: 'الصلاة القادمة والوقت المتبقي', sub: 'وسماءٌ تتبدّل مع ساعات يومك', media: { clip: 'header', from: 0.4, rate: 1, still: 'iphone/01-prayer.png' } },
  4: { kind: 'phone', title: 'وجّه هاتفك نحو القبلة', sub: 'فإذا استقبلتها أضاءت لك', media: { clip: 'header', from: 10.2, rate: 1, still: 'iphone/02-qibla.png' } },
  5: { kind: 'phone', title: 'أقرب المساجد إليك', sub: 'على الخريطة، بالمسافة والاتجاه', media: { clip: 'mosques', from: 4.6, rate: 1.1, still: 'iphone/03-mosques-map.png' } },
  6: { kind: 'phone', title: 'اسأل عن دينك', sub: 'إجاباتٌ من كتبٍ موثوقة على هاتفك، مع مصادرها', media: { clip: 'search', from: 2.8, rate: 1.55, still: 'iphone/04-ask.png' } },
  7: { kind: 'phone', title: 'مكتبةٌ على هاتفك', sub: 'كتبٌ وفتاوى ومحاضرات، تقرؤها وتسمعها دون اتصال', media: { still: 'iphone/05-classics.png' } },
  8: { kind: 'phone', title: 'أذانٌ بأصواتٍ تختارها', sub: 'وتذكيرٌ قبل كل صلاة', media: { still: 'iphone/08-audio.png' } },
  9: { kind: 'two', title: 'مواقيتك أمامك', sub: 'على الشاشة الرئيسية وفي الجزيرة الديناميكية', media: ['iphone/06-widgets.png', 'iphone/07-island.png'] },
  10: { kind: 'watch', title: 'وعلى ساعتك', sub: 'رفيقٌ يفرح بصلاتك، وسجلٌّ لصلواتك، وتسبيحٌ بالتاج الرقمي', media: ['watch/03-watch-spirit.png', 'watch/01-watch-prayer.png', 'watch/04-watch-tasbih.png'] },
  11: { kind: 'devices', title: 'على كل أجهزتك' },
  12: { kind: 'languages', title: 'لغة' },
  13: { kind: 'privacy', title: 'هادئٌ وآمن', cards: [['بلا إعلانات', 'لا شيء يقطع صلاتك'], ['بلا تتبُّع', 'لا يغادر شيءٌ جهازك'], ['دون إنترنت', 'مواقيتك وقبلتك تعمل في كل مكان']] },
  14: { kind: 'end', sky: [0.6, 0.66], title: 'أقرب مسجد' },
  15: { kind: 'end', sky: [0.66, 0.7], title: 'أقرب مسجد', sub: 'حمّله الآن من متجر التطبيقات' },
};

const wide = spec.cut === 'wide';
const W = spec.width, H = spec.height;
const u = (wide ? H : W) / 1080; // one design unit

// Scenes start a little before their line and end where the next one starts.
const lines = spec.lines;
const scenes = lines.map((l, i) => ({ ...SCENES[l.line], line: l.line, start: i ? l.start - 0.35 : 0, end: i + 1 < lines.length ? lines[i + 1].start - 0.35 : spec.duration }));

const css = `
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+Arabic:wght@400;500;600;700&family=Inter:wght@500;600&display=block');
  * { box-sizing: border-box; margin: 0; }
  html, body { width: ${W}px; height: ${H}px; overflow: hidden; background: #F5F5F7; }
  body { font-family: 'IBM Plex Sans Arabic', sans-serif; color: #1D1D1F; direction: rtl; }
  .scene { position: absolute; inset: 0; opacity: 0; }
  .bg { position: absolute; inset: 0; background: radial-gradient(120% 90% at 70% 10%, #FFFFFF 0%, #F5F5F7 55%, #EFEDE8 100%); }
  .el { position: absolute; will-change: transform, opacity; }
  h1 { font-weight: 700; font-size: ${64 * u * (wide ? 1 : 1.22)}px; line-height: 1.25; letter-spacing: 0; }
  h2 { font-weight: 500; font-size: ${32 * u * (wide ? 1 : 1.25)}px; line-height: 1.5; color: #6E6E73; }
  .gold { color: #B8922F; }
  .card { background: #FFFFFF; border-radius: ${44 * u}px; box-shadow: 0 ${30 * u}px ${80 * u}px rgba(20, 30, 50, 0.10), 0 ${2 * u}px ${6 * u}px rgba(20, 30, 50, 0.05); }
  .phone { border-radius: ${62 * u}px; background: #111317; padding: ${11 * u}px; box-shadow: 0 ${40 * u}px ${90 * u}px rgba(20, 30, 50, 0.22), inset 0 0 0 ${2 * u}px #6B7079; }
  .phone img { display: block; width: 100%; height: 100%; border-radius: ${52 * u}px; object-fit: cover; object-position: top; }
  .chip { display: inline-block; padding: ${16 * u}px ${34 * u}px; border-radius: ${999}px; background: #FFFFFF; font-size: ${30 * u}px; font-weight: 600; margin: ${8 * u}px; box-shadow: 0 ${10 * u}px ${30 * u}px rgba(20, 30, 50, 0.08); }
  .sky h1, .end h1, .end h2 { color: #FFFDF6; text-shadow: 0 ${4 * u}px ${30 * u}px rgba(8, 20, 40, 0.5); }
  .end h2 { color: #F4E7C5; }
  .label { font-size: ${26 * u}px; font-weight: 600; color: #6E6E73; text-align: center; }
  .num { font-family: 'Inter', sans-serif; font-weight: 600; }
`;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: W, height: H } });
const sky = fs.readFileSync(path.join(here, 'sky.js'), 'utf8');
// Loaded from a file next to the assets, so the page may show them (a page set from a string may not).
const htmlPath = path.join(A, '.promo.html');
fs.writeFileSync(htmlPath, `<html><head><meta charset="utf-8"><style>${css}</style></head><body><div id=root></div><script>${sky}</script></body></html>`);
await page.goto(pathToFileURL(htmlPath).href, { waitUntil: 'networkidle' });
await page.evaluate(() => document.fonts.ready);

// Builds the scenes in the page and returns, per scene, its elements' appearance times (relative to the scene).
const appear = await page.evaluate(async ({ scenes, wide, W, H, u, CLIPS, A }) => {
  const root = document.getElementById('root');
  const fileUrl = (p) => 'file://' + A + '/' + p;
  const loads = [];
  const mk = (parent, tag, cls, style = {}, html = '') => { const e = document.createElement(tag); if (cls) e.className = cls; Object.assign(e.style, style); e.innerHTML = html; parent.appendChild(e); return e; };
  const px = (v) => `${v}px`;
  const out = [];
  scenes.forEach((s, i) => {
    const el = mk(root, 'div', 'scene ' + s.kind);
    el.id = 'scene' + i;
    const items = []; // [element, delay, click]
    const text = (x, y, wBox, align = 'right') => {
      const box = mk(el, 'div', 'el', { left: px(x), top: px(y), width: px(wBox), textAlign: align });
      const t = mk(box, 'h1', '', {}, s.title);
      items.push([box, 0.15, false]);
      if (s.sub) { const sb = mk(el, 'div', 'el', { left: px(x), top: px(y), width: px(wBox), textAlign: align }); mk(sb, 'h2', '', { visibility: 'hidden' }, s.title); mk(sb, 'h2', '', {}, s.sub); sb.firstChild.style.fontSize = getComputedStyle(t).fontSize; sb.firstChild.style.lineHeight = '1.25'; sb.firstChild.style.marginBottom = px(18 * u); items.push([sb, 0.45, false]); }
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
    if (s.kind === 'sky' || s.kind === 'end') {
      const c = mk(el, 'canvas', '', { position: 'absolute', inset: 0 });
      c.width = W; c.height = H; c.dataset.sky = JSON.stringify(s.sky);
      const box = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (s.kind === 'end' ? (wide ? 0.16 : 0.24) : wide ? 0.3 : 0.32)), textAlign: 'center', padding: `0 ${8 * u}%` });
      if (s.kind === 'end') {
        const icon = mk(box, 'img', '', { width: px(170 * u), height: px(170 * u), borderRadius: px(38 * u), boxShadow: `0 ${20 * u}px ${60 * u}px rgba(0,0,0,0.35)`, marginBottom: px(30 * u) });
        icon.src = fileUrl('icon.png');
      }
      mk(box, 'h1', '', {}, s.title);
      if (s.sub) mk(box, 'h2', '', { marginTop: px(16 * u) }, s.sub);
      items.push([box, s.kind === 'sky' ? 0.6 : 0.2, false]);
    } else {
      mk(el, 'div', 'bg');
      if (s.kind === 'brand') {
        const box = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.2 : 0.3)), textAlign: 'center' });
        const icon = mk(box, 'img', '', { width: px(220 * u), height: px(220 * u), borderRadius: px(50 * u), boxShadow: `0 ${30 * u}px ${70 * u}px rgba(20,30,50,0.18)` });
        icon.src = fileUrl('icon.png');
        items.push([box, 0.1, true]);
        const name = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.2 : 0.3) + 270 * u), textAlign: 'center' });
        mk(name, 'h1', '', { fontSize: px(84 * u) }, s.title);
        items.push([name, 0.5, false]);
        s.chips.forEach((c, k) => {
          const row = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.2 : 0.3) + 400 * u + (wide ? 0 : k * 100 * u)), textAlign: 'center' });
          if (wide) { row.style.left = px(W / 2 - 520 * u + (2 - k) * 360 * u); row.style.right = 'auto'; row.style.width = px(320 * u); }
          mk(row, 'span', 'chip', {}, c);
          items.push([row, 1.6 + k * 1.3, true]);
        });
      } else if (s.kind === 'phone') {
        if (wide) { text(W * 0.5, H * 0.34, W * 0.4); phone(W * 0.3, H * 0.08, H * 0.84, s.media); }
        else { text(W * 0.08, H * 0.07, W * 0.84, 'center'); phone(W * 0.5, H * 0.28, H * 0.66, s.media); }
      } else if (s.kind === 'two') {
        if (wide) { text(W * 0.55, H * 0.36, W * 0.38); phone(W * 0.15, H * 0.1, H * 0.8, s.media[0]); phone(W * 0.385, H * 0.1, H * 0.8, s.media[1], 0.9); }
        else { text(W * 0.08, H * 0.07, W * 0.84, 'center'); phone(W * 0.27, H * 0.31, H * 0.56, s.media[0]); phone(W * 0.73, H * 0.31, H * 0.56, s.media[1], 0.9); }
      } else if (s.kind === 'watch') {
        const size = wide ? H * 0.42 : W * 0.4;
        const spots = wide ? [[0.12, 0.3], [0.27, 0.22], [0.42, 0.3]] : [[0.28, 0.36], [0.72, 0.36], [0.5, 0.6]];
        if (wide) text(W * 0.55, H * 0.36, W * 0.38); else text(W * 0.08, H * 0.07, W * 0.84, 'center');
        s.media.forEach((m, k) => {
          const [fx, fy] = spots[k];
          const wch = mk(el, 'div', 'el', { left: px(W * fx - size * 0.41), top: px(H * fy), width: px(size * 0.82), height: px(size), borderRadius: px(size * 0.24), background: '#111317', padding: px(size * 0.05), boxShadow: `0 ${30 * u}px ${70 * u}px rgba(20,30,50,0.22), inset 0 0 0 ${2 * u}px #6B7079` });
          const img = mk(wch, 'img', '', { width: '100%', height: '100%', borderRadius: px(size * 0.19), objectFit: 'cover', display: 'block' });
          img.src = fileUrl(m);
          mk(wch, 'div', '', { position: 'absolute', right: px(-size * 0.035), top: px(size * 0.28), width: px(size * 0.04), height: px(size * 0.14), borderRadius: px(size * 0.02), background: '#3A3D44' });
          items.push([wch, 0.3 + k * 0.9, true]);
        });
      } else if (s.kind === 'devices') {
        const t = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.09 : 0.08)), textAlign: 'center' });
        mk(t, 'h1', '', {}, s.title);
        items.push([t, 0.1, false]);
        const list = [['iphone/01-prayer.png', 'آيفون', 0.25, 0.462], ['ipad/01-prayer.png', 'آيباد', 0.55, 1.33], ['mac/01-prayer.png', 'ماك', 0.62, 1.6], ['tv/01-tv-board.png', 'آبل تي في', 0.62, 1.78], ['vision/01-prayer.png', 'فيجن برو', 0.62, 1.78]];
        const slots = wide ? list.map((_, k) => [W * (0.9 - k * 0.2), H * 0.56]) : [[W * 0.24, H * 0.36], [W * 0.66, H * 0.34], [W * 0.5, H * 0.55], [W * 0.27, H * 0.76], [W * 0.73, H * 0.76]];
        list.forEach(([img, label, scale, ratio], k) => {
          const hgt = (wide ? H * 0.34 : H * 0.15) * (k === 0 ? 1.15 : 1) * (k === 0 ? 1 : 0.62 + 0.38 * (ratio < 1.5 ? 1 : 0.7));
          const wdt = k === 0 ? hgt * ratio : Math.min(hgt * ratio, wide ? W * 0.18 : W * 0.4);
          const [cx, cy] = slots[k];
          const box = mk(el, 'div', 'el', { left: px(cx - wdt / 2), top: px(cy - hgt / 2), width: px(wdt), textAlign: 'center' });
          const frame = mk(box, 'div', '', { width: px(wdt), height: px(wdt / ratio), borderRadius: px(k === 0 ? 30 * u : 16 * u), background: '#111317', padding: px(k === 0 ? 6 * u : 5 * u), boxShadow: `0 ${20 * u}px ${50 * u}px rgba(20,30,50,0.18)` });
          const im = mk(frame, 'img', '', { width: '100%', height: '100%', objectFit: 'cover', borderRadius: px(k === 0 ? 24 * u : 11 * u), display: 'block' });
          im.src = fileUrl(img);
          if (k === 2) mk(box, 'div', '', { width: px(wdt), height: px(10 * u), borderRadius: `0 0 ${12 * u}px ${12 * u}px`, background: '#C9CBD0' });
          mk(box, 'div', 'label', { marginTop: px(18 * u) }, label);
          items.push([box, 0.35 + k * 0.95, true]);
        });
      } else if (s.kind === 'languages') {
        const words = ['مرحبًا', 'Hello', 'Bonjour', 'Hola', 'Merhaba', 'Selamat', 'خوش آمدید', 'Привет', 'Welkom', '你好', 'नमस्ते', 'স্বাগতম', 'Olá', 'Szia', 'สวัสดี', 'Xin chào', 'ਜੀ ਆਇਆਂ', 'സ്വാഗതം'];
        words.forEach((w, k) => {
          const a = (k / words.length) * Math.PI * 2, r = (wide ? H * 0.36 : W * 0.4) * (0.85 + 0.15 * ((k * 7) % 3) / 2);
          const e = mk(el, 'div', 'el', { left: px(W / 2 + Math.cos(a) * r * (wide ? 1.6 : 1) - 150 * u), top: px(H / 2 + Math.sin(a) * r - 30 * u), width: px(300 * u), textAlign: 'center', fontSize: px(30 * u), fontWeight: 500, color: '#8E8E93' }, w);
          items.push([e, 0.05 + k * 0.06, false]);
        });
        const big = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H / 2 - 150 * u), textAlign: 'center' });
        mk(big, 'div', 'num gold', { fontSize: px(200 * u), lineHeight: 1 }, '36');
        mk(big, 'h1', '', { fontSize: px(70 * u) }, s.title);
        items.push([big, 0.15, true]);
      } else if (s.kind === 'privacy') {
        const t = mk(el, 'div', 'el', { left: 0, right: 0, top: px(H * (wide ? 0.13 : 0.1)), textAlign: 'center' });
        mk(t, 'h1', '', {}, s.title);
        items.push([t, 0.1, false]);
        const icons = ['⊘', '◎', '☾'];
        s.cards.forEach(([a, b], k) => {
          const cw = wide ? W * 0.25 : W * 0.8, ch = wide ? H * 0.42 : H * 0.2;
          const x = wide ? W / 2 + (1 - k) * (cw + 40 * u) - cw / 2 : W * 0.1;
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
  await Promise.all([...document.images].map((im) => im.decode().catch(() => {})));
  return out;
}, { scenes, wide, W, H, u, CLIPS, A });

// The moments elements appear with a click.
const clicks = [];
scenes.forEach((s, i) => appear[i].forEach(([d, c]) => { if (c) clicks.push(s.start + d + 0.15); }));
fs.writeFileSync(clicksOut, JSON.stringify(clicks));

await page.evaluate((A) => { window.__A = A; }, A);
// STILLS="5,15.2" saves those moments as <out>-<t>.png instead of the video (to check the layout).
const stills = process.env.STILLS ? process.env.STILLS.split(',').map(Number) : null;
const frames = stills ? stills.length : Math.round(spec.duration * 30);
const ff = stills ? null : spawn('ffmpeg', ['-nostdin', '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', '30', '-i', '-',
  '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', '-profile:v', 'high', out], { stdio: ['pipe', 'inherit', 'inherit'] });
for (let f = 0; f < frames; f++) {
  const t = stills ? stills[f] : f / 30;
  await page.evaluate(async ({ t, scenes }) => {
    const ease = (x) => 1 - Math.pow(1 - Math.min(1, Math.max(0, x)), 3);
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
        const p = ease((lt - +e.dataset.delay) / 0.8);
        // A slow drift for the whole scene, as in Apple's films.
        const drift = 1 + 0.025 * Math.min(1, lt / dur);
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
    });
    await Promise.all(waits);
  }, { t, scenes: scenes.map(({ start, end }) => ({ start, end })) });
  if (stills) { await page.screenshot({ path: `${out}-${t}.png` }); continue; }
  const png = await page.screenshot({ type: 'png' });
  if (!ff.stdin.write(png)) await new Promise((r) => ff.stdin.once('drain', r));
  if (f % 300 === 0) process.stdout.write(`${f}/${frames}\n`);
}
if (ff) { ff.stdin.end(); await new Promise((r) => ff.on('close', r)); }
await browser.close();
