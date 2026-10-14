// The landing page: builds the chapters from story.js, then one scroll conductor drives the stage (the globe, then
// the app's sky from sky.js) and marks the current chapter. Native scrolling throughout; nothing hijacks the wheel.
import { story, links } from './story.js';
import { createGlobe } from './globe.js';
import { places } from './places.js';

const BASE = new URL('.', import.meta.url).href;
const WEIGHTS = { world: 1.5, lines: 1.8, qibla: 1.7, sky: 1.9, mosque: 1.4, ask: 1.4, library: 1.2, adhan: 1.2, glance: 1.4, watch: 1.3, devices: 1.5, privacy: 1.3, get: 1.7 };
const GLOBE_CH = new Set(['world', 'lines', 'get']);
const reduce = matchMedia('(prefers-reduced-motion: reduce)');
const store = { get: (k) => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch {} } };
const $ = (s, el = document) => el.querySelector(s);
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

let lang = (location.hash === '#ar' || location.hash === '#en') ? location.hash.slice(1) : store.get('nm-lang') || ((navigator.languages || [navigator.language]).some((l) => /^ar/i.test(l)) ? 'ar' : 'en');
document.documentElement.classList.add('js');

// The stage's states, each chapter's filled in from the one before.
const states = [];
let hoursAcc = 0;
story.chapters.forEach((c, i) => {
  const prev = i ? states[i - 1] : { lon: 28, lat: 14, zoom: 1, lines: 0, arcs: 0, mix: 0, t: 0, follow: 0, spin: 0 };
  const s = { ...prev, ...c.stage };
  s.hoursAt = hoursAcc;
  s.hours = c.stage.hours || 0;
  hoursAcc += s.hours;
  states.push(s);
});

const ICONS = {
  appStore: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm6.2 4.2-4.6 8h2.1l.8-1.4h4.9l-1-1.8H10.6l2.5-4.4-1.9-.4Zm2.6 3.2-1 1.7 2.2 3.9h2.1l-3.3-5.6Z"/></svg>',
  googlePlay: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 2.8v18.4c0 .6.6.9 1.1.6l15.4-8.9c.5-.3.5-1 0-1.3L5.1 2.2C4.6 1.9 4 2.2 4 2.8Z"/></svg>',
  microsoftStore: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3h8.5v8.5H3zM12.5 3H21v8.5h-8.5zM3 12.5h8.5V21H3zM12.5 12.5H21V21h-8.5z"/></svg>',
};

// A title's words, each in its own span (they come in one by one); [[…]] are highlighted in gold.
function words(str) {
  let k = 0;
  return str.split(/(\[\[.*?\]\])/).map((part) => {
    const hl = part.startsWith('[[');
    const inner = part.replace(/\[\[|\]\]/g, '').split(/(\s+)/).map((w) => (!w || /^\s+$/.test(w) ? w : `<span class="w" style="--d:${k++}">${esc(w)}</span>`)).join('');
    return hl ? `<span class="hl" style="--d:${k}">${inner}</span>` : inner;
  }).join('');
}
const plain = (str) => str.replace(/\[\[|\]\]/g, '');

function img(path, alt = '', cls = '') {
  return `<img src="${BASE}assets/${lang}/${path}.webp" alt="${esc(alt)}" loading="lazy" decoding="async"${cls ? ` class="${cls}"` : ''}>`;
}

function mediaHTML(c, t) {
  if (!c.media) return '';
  return c.media.map((m) => {
    if (m.phone) {
      const still = `${BASE}assets/${lang}/${m.phone}.webp`;
      const video = m.clip ? `<video muted playsinline loop preload="none" poster="${still}" src="${BASE}assets/${lang}/${m.clip}.mp4"></video>` : '';
      return `<div class="phone">${img(m.phone, plain(t.title))}${video}</div>`;
    }
    if (m.watch) return `<div class="watch">${img(m.watch, plain(t.title))}</div>`;
    if (m.devices) {
      const [iphone, ipad, mac, tv, vision] = m.devices;
      const L = t.labels;
      return `<div class="family" role="img" aria-label="${esc(L.join(', '))}">
        <figure class="tv"><div class="screen">${img(tv)}</div><div class="stand"></div><figcaption>${L[3]}</figcaption></figure>
        <figure class="mac"><div class="screen">${img(mac)}</div><div class="base"></div><figcaption>${L[2]}</figcaption></figure>
        <figure class="vision"><div class="screen">${img(vision)}</div><figcaption>${L[4]}</figcaption></figure>
        <figure class="ipad"><div class="screen">${img(ipad)}</div><figcaption>${L[1]}</figcaption></figure>
        <figure class="iphone"><div class="screen">${img(iphone)}</div><figcaption>${L[0]}</figcaption></figure>
      </div>`;
    }
    return '';
  }).join('');
}

function storesHTML(t) {
  return `<div class="stores">${Object.keys(ICONS).map((k) => {
    const href = links[k];
    const [small, big] = t.stores[k];
    return href
      ? `<a class="store" href="${esc(href)}" rel="noopener" target="_blank">${ICONS[k]}<small><span>${esc(small)}</span></small><b>${esc(big)}</b></a>`
      : `<span class="store" aria-disabled="true" data-soon="${esc(t.soon)}">${ICONS[k]}<small><span>${esc(small)}</span></small><b>${esc(big)}</b></span>`;
  }).join('')}</div>`;
}

function render() {
  const t = story[lang];
  document.documentElement.lang = t.lang;
  document.documentElement.dir = t.dir;
  document.title = t.title;
  $('#sound span').textContent = t.sound[audio.on ? 1 : 0];
  $('#sound').setAttribute('aria-label', t.sound[0]);
  $('#sound').setAttribute('aria-pressed', audio.on ? 'true' : 'false');
  $('.board').setAttribute('aria-label', t.boardLabel);
  $('.board').innerHTML = story.chapters.map((c) => `<a href="#${c.id}" data-id="${c.id}"><span>${esc(t.board[c.id])}</span></a>`).join('');
  $('#lang').textContent = t.switchTo;
  $('#lang').setAttribute('lang', lang === 'ar' ? 'en' : 'ar');
  $('.skip').textContent = t.skip;
  $('.brand span').textContent = t.title;
  $('#story').innerHTML = story.chapters.map((c, i) => {
    const x = t.chapters[c.id];
    const h = i === 0 ? 'h1' : 'h2';
    return `<section class="chapter${GLOBE_CH.has(c.id) ? ' globe-ch' : ''}" id="${c.id}" data-i="${i}" style="--weight:${WEIGHTS[c.id]}" aria-labelledby="h-${c.id}">
      <div class="beat">
        <div class="copy">
          <p class="eyebrow">${esc(x.eyebrow)}</p>
          <${h} id="h-${c.id}" aria-label="${esc(plain(x.title))}"><span aria-hidden="true">${words(x.title)}</span></${h}>
          <p>${esc(x.body)}</p>
          ${x.points ? `<ul>${x.points.map((p) => `<li>${esc(p)}</li>`).join('')}</ul>` : ''}
          ${x.note ? `<p class="note">${esc(x.note)}</p>` : ''}
          ${c.id === 'world' ? `<a class="cta-link" href="#get">${esc(x.cta)}</a><div class="scroll-cue">${esc(t.scroll)}</div>` : ''}
          ${c.id === 'get' ? storesHTML(t) : ''}
        </div>
        <div class="media">${mediaHTML(c, x)}</div>
      </div>
    </section>`;
  }).join('');
  const shots = ['01-prayer', '02-qibla', '03-mosques-map', '04-ask', '05-classics', '06-widgets', '07-island', '08-audio', '09-mosques-radar', '10-prayer-dark'];
  $('#inside').innerHTML = `<div>
      <h2 id="inside-title">${esc(t.index.title)}</h2>
      <p>${esc(t.index.intro)}</p>
      <div class="strip" tabindex="0" aria-label="${esc(t.index.title)}">${shots.map((s) => img(`iphone/${s}`, '')).join('')}${['01-watch-prayer', '02-watch-qibla', '05-watch-mosques', '06-watch-ask'].map((s) => img(`watch/${s}`, '')).join('')}</div>
      <div class="groups">${t.index.groups.map(([g, items]) => `<div class="group"><h3>${esc(g)}</h3><ul>${items.map((i) => `<li>${esc(i)}</li>`).join('')}</ul></div>`).join('')}</div>
    </div>`;
  $('footer').innerHTML = `<div><nav><a href="${BASE}privacy.html${lang === 'ar' ? '#ar' : ''}">${esc(t.footer.privacy)}</a><a href="${BASE}security.html${lang === 'ar' ? '#ar' : ''}">${esc(t.footer.security)}</a></nav>
    <p>${esc(t.footer.note)}</p><p>${esc(t.footer.sources)}</p></div>`;
  chapters = [...document.querySelectorAll('.chapter')];
  measure();
}

// Theme: the system's, or the viewer's choice.
const THEMES = ['auto', 'light', 'dark'];
const THEME_LABEL = { en: { auto: 'Auto', light: 'Light', dark: 'Dark' }, ar: { auto: 'تلقائي', light: 'فاتح', dark: 'داكن' } };
let theme = store.get('nm-theme') || 'auto';
function applyTheme() {
  if (theme === 'auto') delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = theme;
  $('#theme').textContent = THEME_LABEL[lang][theme];
  dark = theme === 'dark' || (theme === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
  globe?.setTheme(dark
    ? { oceanDay: '#173F5A', oceanNight: '#05121C', landDay: '#F4EEDF', landNight: '#2B4A6C', dusk: '#E2B649', lights: '#F6D88E', line: '#E2B649', arc: '#F6D88E', ring: '#F2CF6B', rim: '#5D8FC9', stars: true }
    : { oceanDay: '#D5DFE2', oceanNight: '#0E2F45', landDay: '#1A4D6E', landNight: '#3A6488', dusk: '#C9952A', lights: '#F2CF6B', line: '#B8862A', arc: '#F2C14E', ring: '#B8862A', rim: '#FFFFFF', stars: false });
  dirty = true;
}

// The scroll conductor.
let chapters = [], tops = [], heights = [], storyEnd = 0, dirty = true, globe = null, dark = false;
function measure() {
  tops = chapters.map((c) => c.offsetTop);
  heights = chapters.map((c) => c.offsetHeight);
  storyEnd = tops.length ? tops[tops.length - 1] + heights[heights.length - 1] : 0;
  dirty = true;
}
const smooth = (x) => { x = Math.min(1, Math.max(0, x)); return x * x * (3 - 2 * x); };
const lerp = (a, b, p) => a + (b - a) * p;
const lerpLon = (a, b, p) => { let d = ((b - a + 540) % 360) - 180; return a + d * p; };

function stageAt(y) {
  const vh = innerHeight, mid = y + vh * 0.5;
  let i = 0;
  while (i + 1 < tops.length && mid >= tops[i + 1]) i++;
  const p = Math.min(1, Math.max(0, (mid - tops[i]) / (heights[i] || 1)));
  const a = states[i], b = states[Math.min(i + 1, states.length - 1)];
  let e = reduce.matches ? (p > 0.8 ? 1 : 0) : smooth((p - 0.55) / 0.45);
  if (i + 1 >= states.length) e = 0;
  // Into the sky the globe zooms first and the sky follows; out of it, the sky clears first.
  const mixP = a.mix < b.mix ? smooth((e - 0.45) / 0.55) : a.mix > b.mix ? smooth(e / 0.5) : e;
  return {
    i, p,
    // A spinning chapter's view turns west to east on its own (the globe turns under the viewer).
    lon: lerpLon(a.lon - spinAcc * a.spin, b.lon - spinAcc * b.spin, e), lat: lerp(a.lat, b.lat, e), zoom: lerp(a.zoom, b.zoom, a.mix < b.mix ? smooth(e / 0.8) : e),
    follow: 70, followMix: lerp(a.follow, b.follow, e),
    lines: lerp(a.lines, b.lines, e), arcs: lerp(a.arcs, b.arcs, e), mix: lerp(a.mix, b.mix, mixP), t: lerp(a.t, b.t, e),
    hours: a.hoursAt + a.hours * (reduce.matches ? 0 : smooth(p)),
  };
}

const globeCanvas = $('#globe-canvas'), skyCanvas = $('#sky-canvas'), labelBox = $('.labels');
const skyCtx = skyCanvas.getContext('2d');
let lastSky = null, current = -1, ambient = 0, spinAcc = 0, lastFrame = performance.now();
function sizeSky() {
  const k = Math.min(1.5, devicePixelRatio || 1);
  skyCanvas.width = Math.round(innerWidth * k);
  skyCanvas.height = Math.round(innerHeight * k);
  lastSky = null;
}

function frame(now) {
  const dt = Math.min(0.1, (now - lastFrame) / 1000);
  lastFrame = now;
  const y = scrollY;
  const s = stageAt(y);
  const inStory = y < storyEnd;
  for (let k = Math.max(0, s.i - 1); k <= Math.min(chapters.length - 1, s.i + 1); k++) {
    const p = Math.min(1, Math.max(0, (y + innerHeight * 0.5 - tops[k]) / (heights[k] || 1)));
    chapters[k].style.setProperty('--p', p.toFixed(4));
  }
  $('.board').classList.toggle('off', !inStory);
  if (s.i !== current) {
    current = s.i;
    chapters.forEach((c, k) => c.classList.toggle('on', k === s.i));
    document.querySelectorAll('.board a').forEach((a, k) => { if (k === s.i) a.setAttribute('aria-current', 'step'); else a.removeAttribute('aria-current'); });
    chapters.forEach((c, k) => c.querySelectorAll('video').forEach((v) => { if (k === s.i && !reduce.matches) v.play().catch(() => {}); else v.pause(); }));
  }
  if (!reduce.matches) { ambient += dt * 0.2; spinAcc += dt * 7; } // twelve minutes of the sun, seven degrees of turn, every second
  if (inStory) {
    if (globe && s.mix < 0.999) {
      const wide = innerWidth > 860, rtl = lang === 'ar', aspect = innerWidth / innerHeight;
      const away = Math.max(0, 1 - (s.zoom - 1) / 0.8);
      const fit = wide ? 1 : Math.min(2.6, Math.max(1, (1.25 / (0.2867 * aspect) - 1) / 4.4));
      globe.update({ ...s, zoom: s.zoom / fit, hours: 14.5 + s.hours + ambient, now: now / 1000, shift: wide ? (rtl ? -0.82 : 0.82) * away : 0, lift: wide ? 0 : 0.5 * away * fit });
      globeCanvas.style.opacity = String(1 - s.mix);
      // The prayer names beside their lines, Makkah beside its light.
      const L = globe.labels(), t = story[lang];
      const names = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];
      labelBox.querySelectorAll(':scope > span').forEach((el, k) => {
        const pt = k < 5 ? L.lines[k] : L.makkah;
        const on = k < 5 ? s.lines > 0.3 && s.mix < 0.2 : s.arcs > 0.3 && s.mix < 0.2;
        if (!pt || !on || pt.facing < 0.25) { el.style.opacity = 0; return; }
        el.textContent = k < 5 ? t.prayers[names[k]] : t.makkah;
        el.style.left = `${pt.x}px`; el.style.top = `${pt.y}px`;
        el.style.opacity = String(k < 5 ? Math.min(1, (s.lines - 0.3) * 2) : Math.min(1, (s.arcs - 0.3) * 2));
      });
      tagFrame(s, now / 1000);
    } else {
      globeCanvas.style.opacity = '0';
      labelBox.querySelectorAll(':scope > span, .tag').forEach((el) => { el.style.opacity = 0; });
    }
    document.documentElement.classList.toggle('on-sky', s.mix > 0.5);
    if (s.mix > 0.001) {
      skyCanvas.style.opacity = String(s.mix);
      const key = s.t.toFixed(4) + ':' + skyCanvas.width;
      if (key !== lastSky && typeof drawSky === 'function') {
        drawSky(skyCtx, skyCanvas.width, skyCanvas.height, s.t, { arc: false, mosque: innerWidth > 860 ? 0.17 : 0.12, horizon: 0.8 });
        lastSky = key;
      }
    } else skyCanvas.style.opacity = '0';
  }
  soundFrame(s);
  requestAnimationFrame(frame);
}

// Cards that rise where a named city's Adhan comes: the city, the prayer, and its local time.
const TAG_LIFE = 3.4, fmt = {};
let lastTag = 0;
function localTime(hours, tz) {
  const key = lang + tz;
  fmt[key] = fmt[key] || new Intl.DateTimeFormat(lang === 'ar' ? 'ar-u-nu-latn' : 'en-US', { hour: 'numeric', minute: '2-digit', timeZone: tz });
  return fmt[key].format(new Date(Date.UTC(2026, 9, 14) + hours * 3600e3));
}
function tagFrame(s, now) {
  const tags = [...labelBox.querySelectorAll('.tag')];
  const show = s.mix < 0.2 && s.arcs < 0.3 && s.zoom < 1.3;
  const t = story[lang], names = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];
  for (const ev of globe.adhans()) {
    if (!show || now - lastTag < 0.6) continue;
    const free = tags.find((el) => !el.dataset.born || now - +el.dataset.born > TAG_LIFE);
    if (!free) continue;
    // Keep the cards apart: none within 130 px of one already showing.
    const at = globe.project(ev.v);
    if (tags.some((el) => el !== free && el.dataset.born && now - +el.dataset.born < TAG_LIFE && el._v && Math.hypot(globe.project(el._v).x - at.x, globe.project(el._v).y - at.y) < 130)) continue;
    const [en, ar, , , tz] = places[ev.place];
    free.innerHTML = `<b>${esc(lang === 'ar' ? ar : en)}</b><span>${esc(t.prayers[names[ev.kind]])} · ${esc(localTime(ev.hours, tz))}</span>`;
    free.dataset.born = now;
    free._v = ev.v;
    lastTag = now;
  }
  tags.forEach((el) => {
    const age = el.dataset.born ? now - +el.dataset.born : 99;
    if (!show || age > TAG_LIFE || !el._v) { el.style.opacity = 0; return; }
    const pt = globe.project(el._v);
    if (pt.facing < 0.15) { el.style.opacity = 0; return; }
    el.style.left = `${pt.x}px`; el.style.top = `${pt.y}px`;
    el.style.opacity = String(Math.min(1, age / 0.35, (TAG_LIFE - age) / 0.6));
    el.style.transform = `translate(-50%, calc(-100% - 14px - ${Math.min(1, age / 0.6) * 8}px))`;
  });
}

// The Adhan: starts only from the button; it comes from where the rings rise on the globe, softer over the sky.
const audio = { on: false, ctx: null, gain: null, pan: null };
async function toggleSound() {
  audio.on = !audio.on;
  const t = story[lang];
  $('#sound span').textContent = t.sound[audio.on ? 1 : 0];
  $('#sound').setAttribute('aria-pressed', audio.on ? 'true' : 'false');
  if (audio.on && !audio.ctx) {
    try {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      audio.ctx = new Ctx();
      audio.gain = audio.ctx.createGain();
      audio.gain.gain.value = 0;
      audio.pan = audio.ctx.createStereoPanner ? audio.ctx.createStereoPanner() : null;
      const buf = await fetch(BASE + 'assets/adhan.mp3').then((r) => r.arrayBuffer()).then((b) => audio.ctx.decodeAudioData(b));
      const src = audio.ctx.createBufferSource();
      src.buffer = buf; src.loop = true;
      (audio.pan ? src.connect(audio.pan).connect(audio.gain) : src.connect(audio.gain)).connect(audio.ctx.destination);
      src.start();
    } catch (e) { audio.on = false; $('#sound span').textContent = t.sound[0]; $('#sound').setAttribute('aria-pressed', 'false'); }
  }
  if (audio.ctx && audio.on) audio.ctx.resume();
}
function soundFrame(s) {
  if (!audio.ctx) return;
  const heard = globe && s.mix < 0.999 ? globe.sound() : { pan: 0, level: 0 };
  const level = audio.on ? (0.35 + 0.45 * heard.level) * (1 - s.mix) + 0.12 * s.mix : 0;
  const now = audio.ctx.currentTime;
  audio.gain.gain.setTargetAtTime(level, now, 0.4);
  if (audio.pan) audio.pan.pan.setTargetAtTime(heard.pan * 0.8 * (1 - s.mix), now, 0.8);
}
$('#sound').addEventListener('click', toggleSound);

$('#lang').addEventListener('click', () => {
  const at = current, frac = chapters[at] ? (scrollY - tops[at]) / (heights[at] || 1) : 0;
  lang = lang === 'en' ? 'ar' : 'en';
  store.set('nm-lang', lang);
  render();
  applyTheme();
  if (chapters[at]) scrollTo(0, tops[at] + frac * heights[at]);
});
$('#theme').addEventListener('click', () => {
  theme = THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length];
  store.set('nm-theme', theme);
  applyTheme();
});
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);
addEventListener('resize', () => { measure(); sizeSky(); globe?.resize(); });

render();
sizeSky();
applyTheme();
labelBox.innerHTML = '<span></span><span></span><span></span><span></span><span></span><span class="makkah"></span>' + '<div class="tag"></div>'.repeat(4);
createGlobe(globeCanvas, BASE, places).then((g) => {
  globe = g;
  if (!g) { document.documentElement.classList.add('no-webgl'); return; }
  g.resize();
  applyTheme();
}).catch(() => document.documentElement.classList.add('no-webgl'));
requestAnimationFrame(frame);
addEventListener('load', measure);
