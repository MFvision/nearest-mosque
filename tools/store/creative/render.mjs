// Renders the creative videos' moving parts in Chromium (Playwright) and streams the frames to ffmpeg.
//
//   node render.mjs sky <out.mp4> <width> <height> <seconds> [fps]    the living sky loop, no text
//   node render.mjs still <out.png> <width> <height> <t>              one frame of the sky (a background)
//   node render.mjs text <out.png> <width> <height> <lang> <phrase> [size] [y] [left,right]   the phrase on a transparent layer
//
// The sky is drawn by sky.js (one frame per call, t = 0..1 over the loop). Text uses web fonts per script so every
// store language renders, and is composited onto the sky by make.sh.
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const { chromium } = await import(process.env.PLAYWRIGHT || 'playwright');
const [, , kind, out, W, H, ...rest] = process.argv;
const w = +W, h = +H;

// One family per script (Google Fonts); Latin, Cyrillic and Vietnamese share Inter.
const FONTS = {
  ar: ['IBM Plex Sans Arabic', 600], ur: ['Noto Nastaliq Urdu', 600], zh: ['Noto Sans SC', 600],
  hi: ['Noto Sans Devanagari', 600], mr: ['Noto Sans Devanagari', 600], bn: ['Noto Sans Bengali', 600],
  gu: ['Noto Sans Gujarati', 600], kn: ['Noto Sans Kannada', 600], ml: ['Noto Sans Malayalam', 600],
  pa: ['Noto Sans Gurmukhi', 600], te: ['Noto Sans Telugu', 600], th: ['Noto Sans Thai', 600],
};
const RTL = new Set(['ar', 'ur']);

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: w, height: h } });

if (kind === 'sky') {
  const [seconds, fps = '30'] = rest;
  const frames = Math.round(+seconds * +fps);
  // SKY='{"mosque":0.12,"mosqueX":0.27,"horizon":0.74}' changes the scene for other shapes (sky.js options).
  const opts = JSON.parse(process.env.SKY || '{}');
  await page.setContent(`<html><body style="margin:0;background:#000"><canvas id=c width=${w} height=${h}></canvas>
    <script>${fs.readFileSync(path.join(here, 'sky.js'), 'utf8')}</script></body></html>`);
  const ff = spawn('ffmpeg', ['-nostdin', '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', fps, '-i', '-',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '14', '-pix_fmt', 'yuv420p', '-tune', 'film', out], { stdio: ['pipe', 'inherit', 'inherit'] });
  // FRAMES=a:b renders only frames a..b-1 (to split the work over processes; make.sh joins the parts).
  const [from, to] = (process.env.FRAMES || `0:${frames}`).split(':').map(Number);
  for (let i = from; i < to; i++) {
    // The last frame is one step before t = 1, which is the first frame again: the loop has no seam.
    await page.evaluate(([t, opts]) => { const c = document.getElementById('c'); drawSky(c.getContext('2d'), c.width, c.height, t, opts); }, [i / frames, opts]);
    const png = await page.screenshot({ type: 'png' });
    if (!ff.stdin.write(png)) await new Promise((r) => ff.stdin.once('drain', r));
    if (i % 60 === 0) process.stdout.write(`${i}/${to}\n`);
  }
  ff.stdin.end();
  await new Promise((r) => ff.on('close', r));
} else if (kind === 'still') {
  // One frame of the sky at t (0..1 over the day), as a PNG (backgrounds); SKY options as for the loop.
  const [t] = rest;
  await page.setContent(`<html><body style="margin:0;background:#000"><canvas id=c width=${w} height=${h}></canvas>
    <script>${fs.readFileSync(path.join(here, 'sky.js'), 'utf8')}</script></body></html>`);
  await page.evaluate(([t, opts]) => { const c = document.getElementById('c'); drawSky(c.getContext('2d'), c.width, c.height, t, opts); },
    [+t, JSON.parse(process.env.SKY || '{}')]);
  await page.screenshot({ path: out });
} else if (kind === 'text') {
  // size: font size as a fraction of the height; y: the text's centre; box: its left and right edges (fractions).
  const [lang, phrase, size = '0.07', y = '0.37', box = '0.22,0.78'] = rest;
  const [bl, br] = box.split(',').map(Number);
  const [family, weight] = FONTS[lang] || ['Inter', 600];
  const css = `https://fonts.googleapis.com/css2?family=${family.replace(/ /g, '+')}:wght@${weight}&display=block`;
  const px = Math.round(h * +size * (lang === 'ur' ? 0.9 : 1));
  await page.setContent(`<html><head><link rel="stylesheet" href="${css}"></head>
    <body style="margin:0;background:transparent">
    <div dir="${RTL.has(lang) ? 'rtl' : 'ltr'}" style="position:absolute;left:0;right:0;top:${+y * 100}%;transform:translateY(-50%);
      text-align:center;font-family:'${family}',sans-serif;font-weight:${weight};font-size:${px}px;line-height:${lang === 'ur' ? 1.9 : 1.25};
      color:#FFFDF6;letter-spacing:${RTL.has(lang) || lang === 'zh' || lang === 'th' ? 0 : -0.01}em;
      text-shadow:0 ${px * 0.04}px ${px * 0.35}px rgba(8,20,40,0.55),0 0 ${px * 0.9}px rgba(8,20,40,0.35);
      padding:0 ${w * (1 - br)}px 0 ${w * bl}px">${phrase}</div></body></html>`, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: out, omitBackground: true });
}
await browser.close();
