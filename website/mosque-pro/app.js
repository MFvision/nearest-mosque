// The landing page: builds the chapters from story.js, then one scroll conductor drives the stage (the globe, then
// the app's sky from sky.js) and marks the current chapter. Native scrolling throughout; nothing hijacks the wheel.
import { story, links } from './story.js';
import { createGlobe, PRAYER_COLORS, sunAt } from './globe.js';
import { places } from './places.js';
import { resolveCity, schedule, nextPrayer, clock } from './prayer.js';

const BASE = new URL('.', import.meta.url).href;
const WEIGHTS = { world: 1.6, lines: 1.9, qibla: 1.5, kaaba: 2.1, sky: 1.9, quran: 1.6, screen: 1.7, clean: 1.5, weather: 1.4, mosque: 1.4, ask: 1.4, library: 1.2, adhan: 1.2, glance: 1.4, watch: 1.3, devices: 1.5, help: 1.3, privacy: 1.3, get: 1.7 };
const GLOBE_CH = new Set(['world', 'lines', 'get']);
// The chapters that show the legend at the bottom of the screen (on phones their words stay above it).
const HUD_CH = new Set(['world', 'lines', 'qibla', 'get']);
const reduce = matchMedia('(prefers-reduced-motion: reduce)');
const store = { get: (k) => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch {} } };
const $ = (s, el = document) => el.querySelector(s);
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

let lang = (location.hash === '#ar' || location.hash === '#en') ? location.hash.slice(1) : store.get('mp-lang') || ((navigator.languages || [navigator.language]).some((l) => /^ar/i.test(l)) ? 'ar' : 'en');
document.documentElement.classList.add('js');

// Where the visitor is: the most populous built-in city in the browser's time zone (no location is asked for).
// Riyadh until the table has loaded, or when the zone is not in it.
const you = { lat: 24.714, lon: 46.675, en: 'Riyadh', ar: 'الرياض' };
// The stage's states, each chapter's filled in from the one before.
const states = [];
function buildStates() {
  states.length = 0;
  let hoursAcc = 0;
  story.chapters.forEach((c, i) => {
    const prev = i ? states[i - 1] : { lon: 28, lat: 14, zoom: 1, lines: 0, arcs: 0, haram: 0, mix: 0, t: 0, follow: 0, spin: 0, fly: 0, real: 0, rain: 0 };
    const s = { ...prev, ...c.stage };
    if (s.lon === 'you') s.lon = you.lon;
    if (s.lat === 'you') s.lat = you.lat;
    s.fly = c.stage.fly || 0;
    // The weather belongs to its own chapter only: the sky clears again after it.
    s.rain = c.stage.weather === 'rain' ? 1 : 0;
    s.hoursAt = hoursAcc;
    s.hours = c.stage.hours || 0;
    hoursAcc += s.hours;
    states.push(s);
  });
}
buildStates();

const ICONS = {
  appStore: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm6.2 4.2-4.6 8h2.1l.8-1.4h4.9l-1-1.8H10.6l2.5-4.4-1.9-.4Zm2.6 3.2-1 1.7 2.2 3.9h2.1l-3.3-5.6Z"/></svg>',
  // The TV screen: a plain screen on a stand.
  tv: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-6v1.5h3V21H7v-1.5h3V18H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm0 2v10h16V6Z"/></svg>',
};

// A title's words, each in its own span (they come in one by one); [[…]] are highlighted in gold.
function words(str) {
  let k = 0;
  const spans = (part) => part.split(/(\s+)/).map((w) => (!w || /^\s+$/.test(w) ? w : `<span class="w" style="--d:${k++}">${esc(w)}</span>`)).join('');
  return str.split(/(\[\[.*?\]\][.,،:;!?؟]*)/).map((part) => {
    const m = part.match(/^\[\[(.*?)\]\]([.,،:;!?؟]*)$/);
    if (!m) return spans(part);
    const [, inner, punct] = m, d = k;
    if (!punct) return `<span class="hl" style="--d:${d}">${spans(inner)}</span>`;
    // The mark after a highlight stays on the line of its last word.
    const cut = inner.lastIndexOf(' ') + 1, head = inner.slice(0, cut), last = inner.slice(cut);
    return `${head ? `<span class="hl" style="--d:${d}">${spans(head)}</span>` : ''}<span class="nw"><span class="hl" style="--d:${d}">${spans(last)}</span>${esc(punct)}</span>`;
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
      const clip = `${BASE}assets/${lang}/${m.clip}`;
      const video = m.clip ? `<video muted playsinline loop preload="none" poster="${still}"><source src="${clip}.mp4" type="video/mp4"><source src="${clip}.webm" type="video/webm"></video>` : '';
      return `<div class="phone">${img(m.phone, plain(t.title))}${video}</div>`;
    }
    // The TV screen (pictures of website/tv/), with a second, smaller TV in front for `inset`.
    if (m.tv) return `<div class="tvset${m.inset ? ' two' : ''}"><figure><div class="screen">${img(m.tv, plain(t.title))}</div><div class="stand"></div></figure>${m.inset ? `<figure class="inset"><div class="screen">${img(m.inset, '')}</div></figure>` : ''}</div>`;
    // A settings card drawn from the chapter's words (Clean network, the Help Center): no screenshot of it yet.
    if (m.panel) {
      const P = t.panel;
      return `<div class="panel" role="img" aria-label="${esc(P.title)}"><div class="panel-head"><b>${esc(P.title)}</b>${P.on ? '<span class="switch" aria-hidden="true"></span>' : ''}</div>${P.on ? `<p class="panel-on">${esc(P.on)}</p>` : ''}<ul>${P.rows.map(([k, v]) => `<li><span>${esc(k)}</span><em>${esc(v)}</em></li>`).join('')}</ul></div>`;
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
        <figure class="iphone"><div class="phone">${img(iphone)}</div><figcaption>${L[0]}</figcaption></figure>
      </div>`;
    }
    return '';
  }).join('');
}

// The TV screen is a page of this site and opens in place; the stores open in a new tab.
const HERE = new Set(['tv']);
const target = (k) => (HERE.has(k) ? '' : ' target="_blank"');
function storesHTML(t) {
  const more = Object.keys(t.storesMore).filter((k) => links[k])
    .map((k) => `<a href="${esc(links[k])}" rel="noopener"${target(k)}>${esc(t.storesMore[k])}</a>`);
  return `<div class="stores">${Object.keys(ICONS).map((k) => {
    const href = links[k];
    const [small, big] = t.stores[k];
    return href
      ? `<a class="store" href="${esc(href)}" rel="noopener"${target(k)}>${ICONS[k]}<small>${esc(small)}</small><b>${esc(big)}</b></a>`
      : `<span class="store" aria-disabled="true">${ICONS[k]}<small>${esc(t.soon)}</small><b>${esc(big)}</b></span>`;
  }).join('')}</div>${more.length ? `<p class="stores-more">${more.join('<span aria-hidden="true">·</span>')}</p>` : ''}`;
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
    return `<section class="chapter${GLOBE_CH.has(c.id) ? ' globe-ch' : ''}${HUD_CH.has(c.id) ? ' hud-ch' : ''}" id="${c.id}" data-i="${i}" style="--weight:${WEIGHTS[c.id]}" aria-labelledby="h-${c.id}">
      <div class="beat">
        <div class="copy">
          <p class="eyebrow">${esc(x.eyebrow)}</p>
          <${h} id="h-${c.id}" aria-label="${esc(plain(x.title))}"><span aria-hidden="true">${words(x.title)}</span></${h}>
          ${c.id === 'world' ? '<p class="hero-at"></p>' : ''}
          <p>${esc(x.body)}</p>
          ${x.points ? `<ul>${x.points.map((p) => `<li>${esc(p)}</li>`).join('')}</ul>` : ''}
          ${x.note ? `<p class="note">${esc(x.note)}</p>` : ''}
          ${x.actions ? `<p class="actions">${x.actions.map(([label, href]) => `<a href="${esc(/^[a-z]+:/.test(href) ? href : BASE + href)}"${/^https:/.test(href) ? ' rel="noopener" target="_blank"' : ''}>${esc(label)}</a>`).join('')}</p>` : ''}
          ${x.credit ? `<p class="credit"><a href="${esc(x.credit[1])}" rel="noopener" target="_blank">${esc(x.credit[0])}</a></p>` : ''}
          ${c.id === 'world' ? `<a class="cta-link" href="#get">${esc(x.cta)}</a><div class="scroll-cue">${esc(t.scroll)}</div>` : ''}
          ${c.id === 'get' ? storesHTML(t) : ''}
          ${c.id === 'kaaba' ? `<div class="daynight" role="group">${t.daynight.map((n, k) => `<button type="button" data-night="${k}" aria-pressed="${(k === 1) === makkahNight()}">${esc(n)}</button>`).join('')}</div>` : ''}
        </div>
        <div class="media">${mediaHTML(c, x)}</div>
      </div>
    </section>`;
  }).join('');
  const shots = ['01-prayer', '02-prayer-weather', '02-qibla', '03-mosques-map', '04-ask', '05-classics', '06-widgets', '07-island', '08-audio', '09-mosques-radar', '10-prayer-dark'];
  $('#inside').innerHTML = `<div>
      <h2 id="inside-title">${esc(t.index.title)}</h2>
      <p>${esc(t.index.intro)}</p>
      <div class="strip" tabindex="0" aria-label="${esc(t.index.title)}">${shots.map((s) => img(`iphone/${s}`, '')).join('')}${['01-watch-prayer', '02-watch-qibla', '05-watch-mosques', '06-watch-ask'].map((s) => img(`watch/${s}`, '')).join('')}</div>
      <div class="groups">${t.index.groups.map(([g, items]) => `<div class="group"><h3>${esc(g)}</h3><ul>${items.map((i) => `<li>${esc(i)}</li>`).join('')}</ul></div>`).join('')}</div>
    </div>`;
  const hash = lang === 'ar' ? '#ar' : '';
  $('footer').innerHTML = `<div><nav>${[[`${BASE}privacy.html${hash}`, t.footer.privacy], [`${BASE}terms.html${hash}`, t.footer.terms], [`${BASE}tv/`, t.footer.tv], ['mailto:app@vision.bz', t.footer.contact]].map(([href, label]) => `<a href="${href}">${esc(label)}</a>`).join('')}</nav></div>`;
  chapters = [...document.querySelectorAll('.chapter')];
  document.querySelectorAll('.daynight button').forEach((b) => b.addEventListener('click', () => {
    nightPick = b.dataset.night === '1';
    document.querySelectorAll('.daynight button').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
  }));
  renderHud();
  measure();
  heroTitle();
  // The chapters and the storyboard are new: the next frame marks the one in view again (its title, its copy, its
  // video), even when it is the same chapter as before (the language button keeps the visitor where they were).
  current = -1;
}

// The first title is the visitor's next prayer and the time left, for the centre of their city (from the browser's
// time zone, with the method the app suggests there), counting down each second.
let heroCity = null, heroPlan = null;
function heroTitle() {
  const el = $('#h-world');
  if (!el || !heroCity) return;
  const t = story[lang], x = t.chapters.world, now = new Date();
  if (!heroPlan || now > heroPlan.tomorrowFajr) heroPlan = schedule(heroCity);
  const [k, at] = nextPrayer(heroPlan, now);
  const name = t.prayers[k], city = lang === 'ar' ? heroCity.ar : heroCity.en;
  if (el.dataset.next !== k + lang + +at) {
    el.dataset.next = k + lang + +at;
    const when = x.nextAt.replace('{time}', clock(at, heroCity.tz, lang)).replace('{city}', city);
    el.setAttribute('aria-label', `${name} ${when}`);
    el.innerHTML = `<span aria-hidden="true"><span class="w" style="--d:0">${esc(name)}</span> <span class="w" style="--d:1">${esc(x.nextIn)}</span> <span class="hl" style="--d:2"><span class="w count" style="--d:2"></span></span></span>`;
    $('#world .hero-at').textContent = when;
  }
  const left = Math.max(0, Math.round((at - now) / 1000));
  el.querySelector('.count').textContent = `${Math.floor(left / 3600)}:${String(Math.floor(left / 60) % 60).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
}
resolveCity(places, BASE + 'assets/zones.json', null).then((c) => { heroCity = c; heroTitle(); setInterval(heroTitle, 1000); }).catch(() => {});

// Theme: the system's, or the viewer's choice.
const THEMES = ['auto', 'light', 'dark'];
const THEME_LABEL = { en: { auto: 'Auto', light: 'Light', dark: 'Dark' }, ar: { auto: 'تلقائي', light: 'فاتح', dark: 'داكن' } };
let theme = store.get('mp-theme') || 'auto';
function applyTheme() {
  if (theme === 'auto') delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = theme;
  $('#theme').textContent = THEME_LABEL[lang][theme];
  dark = theme === 'dark' || (theme === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
  globe?.setTheme(dark
    ? { oceanDay: '#173F5A', oceanNight: '#05121C', landDay: '#F4EEDF', landNight: '#2B4A6C', dusk: '#FFC85A', arc: '#FFC94D', ring: '#FFD36B', holy: '#FFE7A8', ground: '#0B1620', rim: '#6FA3E0', stars: true }
    : { oceanDay: '#D5DFE2', oceanNight: '#0E2F45', landDay: '#1A4D6E', landNight: '#3A6488', dusk: '#E8A93A', arc: '#FFC94D', ring: '#F5B93F', holy: '#FFE7A8', ground: '#0B1620', rim: '#BFD7F2', stars: false });
  dirty = true;
}

// The scroll conductor.
let chapters = [], tops = [], heights = [], storyEnd = 0, dirty = true, globe = null, dark = false;
// Where the reading column ends (its inner edge on the screen) and the gap after it, for placing the globe.
let colEdge = 0, colGap = 0;
function measure() {
  const beat = $('.chapter .beat');
  if (beat) {
    const cs = getComputedStyle(beat), r = beat.getBoundingClientRect(), w1 = parseFloat(cs.gridTemplateColumns) || 0;
    colGap = parseFloat(cs.columnGap) || 0;
    colEdge = lang === 'ar' ? r.right - parseFloat(cs.paddingRight) - w1 : r.left + parseFloat(cs.paddingLeft) + w1;
  }
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
  let e = smooth((p - 0.4) / 0.6);
  if (i + 1 >= states.length) e = 0;
  // Into the sky the globe zooms first and the sky follows; out of it, the sky clears first.
  const mixP = a.mix < b.mix ? smooth((e - 0.45) / 0.55) : a.mix > b.mix ? smooth(e / 0.5) : e;
  // In a flight the view crosses first and the zoom follows; elsewhere both move together.
  const fe = b.fly ? smooth(e / 0.75) : e;
  const ze = b.fly ? Math.pow(e, 1.6) : a.mix < b.mix ? smooth(e / 0.8) : e;
  return {
    i, p,
    // A spinning chapter's view turns west to east on its own (the globe turns under the viewer).
    lon: lerpLon(a.spin ? dayLon() : a.lon, b.spin ? dayLon() : b.lon, fe), lat: lerp(a.lat, b.lat, fe),
    // Zoom in steps of scale; a flight pulls back first, crosses, then comes down (as Google Earth does).
    zoom: Math.exp(lerp(Math.log(a.zoom), Math.log(b.zoom), ze) - (b.fly ? 1.2 * Math.sin(Math.PI * Math.min(1, e * 1.1)) : 0)),
    haram: lerp(a.haram, b.haram, a.haram < b.haram ? smooth((e - 0.7) / 0.3) : smooth(e / 0.3)),
    follow: 70, followMix: lerp(a.follow, b.follow, e),
    lines: lerp(a.lines, b.lines, e), arcs: lerp(a.arcs, b.arcs, e), mix: lerp(a.mix, b.mix, mixP), t: lerp(a.t, b.t, e), rain: lerp(a.rain, b.rain, e),
    hours: a.hoursAt + a.hours * smooth(p),
    // 1 where the globe shows the real time (the Qibla, Makkah); 0 where the day runs fast round the world.
    real: lerp(a.real || 0, b.real || 0, e),
  };
}

const globeCanvas = $('#globe-canvas'), skyCanvas = $('#sky-canvas'), labelBox = $('.labels');
const skyCtx = skyCanvas.getContext('2d');
let lastSky = null, current = -1, arcsShown = 0, lastFrame = performance.now();
// Under rain the sky is drawn once into skyBase and the streaks fall over a copy of it each frame.
const skyBase = document.createElement('canvas'), skyBaseCtx = skyBase.getContext('2d');
let lastBase = null, skyScale = 1;
// Today, in hours since midnight UTC, and its day of the year.
const today = new Date();
const dayStart = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
const utcNow = () => (Date.now() - dayStart) / 3600e3;
const dayOfYear = () => Math.floor((dayStart - Date.UTC(today.getUTCFullYear(), 0, 1)) / 86400e3) + 1;
// The turning globe: the view stays on the sunlit side, 50° east of the point under the sun (late afternoon in the
// middle, the sunset and its Maghrib and Isha lights in view to the east), and the Earth turns beneath it as the
// day runs fast from the real time now. Makkah is in view from mid-morning to two hours after its sunset.
const DAY_EAST = 50;
let ambient = 0, simHours = utcNow();
const dayLon = () => sunAt(dayOfYear(), simHours).lon + DAY_EAST;
function sizeSky() {
  const k = Math.min(1.5, devicePixelRatio || 1);
  skyScale = k;
  skyCanvas.width = skyBase.width = Math.round(innerWidth * k);
  skyCanvas.height = skyBase.height = Math.round(innerHeight * k);
  lastSky = lastBase = null;
}

function frame(now) {
  try { step(now); } catch (e) { console.error(e); }
  requestAnimationFrame(frame);
}
function step(now) {
  const dt = Math.min(0.1, (now - lastFrame) / 1000);
  lastFrame = now;
  const y = scrollY;
  const s = stageAt(y);
  const inStory = y < storyEnd;
  for (let k = Math.max(0, s.i - 1); k <= Math.min(chapters.length - 1, s.i + 1); k++) {
    const p = Math.min(1, Math.max(0, (y + innerHeight * 0.5 - tops[k]) / (heights[k] || 1)));
    chapters[k].style.setProperty('--p', p.toFixed(4));
  }
  // The storyboard and the legend leave as the page after the story comes up over the stage.
  const storyShown = y + innerHeight * 0.8 < storyEnd;
  $('.board').classList.toggle('off', !storyShown);
  if (s.i !== current) {
    current = s.i;
    chapters.forEach((c, k) => c.classList.toggle('on', k === s.i));
    document.querySelectorAll('.board a').forEach((a, k) => { if (k === s.i) a.setAttribute('aria-current', 'step'); else a.removeAttribute('aria-current'); });
    chapters.forEach((c, k) => c.querySelectorAll('video').forEach((v) => { if (k === s.i && !reduce.matches) v.play().catch(() => {}); else v.pause(); }));
  }
  // Eighteen minutes of the day every second (the Earth turns 4.5° a second); with Reduce Motion, a slow, steady
  // turn. Where the globe shows the real time (the Qibla, Makkah), the fast day stops.
  ambient += dt * (reduce.matches ? 0.04 : 0.3) * (1 - s.real);
  if (inStory) {
    if (globe && s.mix < 0.999) {
      const wide = innerWidth > 860 || (innerWidth > innerHeight && innerHeight <= 520), rtl = lang === 'ar', aspect = innerWidth / innerHeight;
      // The globe sits beside the words, then moves to the middle as it comes closer.
      const away = Math.max(0, Math.min(1, 1 - Math.log(s.zoom) / Math.log(4)));
      // A tall screen sees the whole globe from a little further back; close to the ground both are the same.
      // Beside the words, the whole globe (with its glow) fits the room left by the reading column and the storyboard.
      const room = rtl ? colEdge - colGap - 48 : innerWidth - 48 - colEdge - colGap;
      const rBase = innerHeight / (2 * 0.2867 * Math.sqrt(5.4 * 5.4 - 1));
      const distW = Math.sqrt(1 + Math.pow(innerHeight * 1.07 / (2 * 0.2867 * Math.max(60, Math.min(rBase * 1.07, room / 2))), 2));
      const xc = rtl ? colEdge - colGap - room / 2 : colEdge + colGap + room / 2;
      const shiftWide = (xc - innerWidth / 2) * 0.2867 * distW / (innerHeight / 2);
      const fitFar = wide ? Math.max(1, (distW - 1) / 4.4) : Math.min(2.6, Math.max(1, (1.25 / (0.2867 * aspect) - 1) / 4.4));
      const fit = Math.pow(fitFar, Math.max(0, 1 - Math.log(s.zoom) / Math.log(40)));
      // The globe's time: now, plus the fast day; towards the Qibla it runs on to the same time of day as now.
      const off = s.hours + ambient, hours = utcNow() + lerp(off, Math.ceil(off / 24) * 24, smooth(s.real));
      simHours = hours;
      // The arcs draw over a few seconds once they are called for, the visitor's own first.
      arcsShown += (s.arcs - arcsShown) * Math.min(1, dt * (s.arcs > arcsShown ? 0.7 : 3));
      const veil = descentFrame(s, s.zoom / fit, wide);
      globe.update({ ...s, veil, arcs: arcsShown, day: dayOfYear(), zoom: s.zoom / fit, hours, now: now / 1000, shift: wide ? shiftWide * away : 0, lift: wide ? 0 : 0.5 * away * fit });
      globeCanvas.style.opacity = String(1 - s.mix);
      // The prayer names on their waves; Makkah and Madinah beside their light; the visitor's city.
      const L = globe.labels(), t = story[lang];
      const names = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];
      const farOut = s.zoom < 2.2 && s.mix < 0.2;
      // In order of importance (Makkah, the visitor, Madinah, the prayers); a name that would touch one already
      // placed is left out until it stands clear.
      const spans = labelBox.querySelectorAll(':scope > span');
      placed.length = 0;
      for (const k of [5, 7, 6, 0, 1, 2, 3, 4]) {
        const el = spans[k];
        let pt, on, text, op = 1;
        if (k < 5) { pt = L.lines[k]; on = s.lines > 0.3 && farOut; text = t.prayers[names[k]]; op = Math.min(1, (s.lines - 0.3) * 2); el.style.color = PRAYER_COLORS[k]; }
        else if (k === 5) { pt = L.makkah; on = s.mix < 0.2 && descentShown < 0.3; text = t.makkah; }
        else if (k === 6) { pt = L.madinah; text = t.hud.madinah; op = 0.9; on = farOut && s.zoom < 1.3 && Math.hypot(pt.x - L.makkah.x, pt.y - L.makkah.y) > 70; }
        else {
          pt = globe.project(youVec()); text = `${t.hud.you}${lang === 'ar' ? (you.ar ? ' · ' + you.ar : '') : ' · ' + you.en}`;
          on = s.arcs > 0.2 && s.zoom < 2.2 && s.mix < 0.2 && Math.hypot(pt.x - L.makkah.x, pt.y - L.makkah.y) > 50;
        }
        if (!pt || !on || pt.facing < 0.25) { el.style.opacity = 0; continue; }
        if (el._text !== text) { el.textContent = text; el._text = text; el._w = el.offsetWidth; el._h = el.offsetHeight; }
        const box = labelRect(k, pt, el._w, el._h);
        if (placed.some((r) => overlaps(r, box, 6))) { el.style.opacity = 0; continue; }
        placed.push(box);
        el.style.left = `${pt.x}px`; el.style.top = `${pt.y}px`;
        el.style.opacity = String(op);
      }
      // The legend, the style switch and Makkah's time, while the whole globe is in view.
      hud.classList.toggle('off', !(storyShown && farOut && s.haram < 0.1 && HUD_CH.has(story.chapters[s.i].id)));
      // Makkah's clock is the real time now (the globe's day may be running fast).
      hudClock.textContent = `${t.hud.clock} · ${localTime(utcNow(), 'Asia/Riyadh')}`;
      tagFrame(s, now / 1000);
    } else {
      descent.style.opacity = '0';
      globeCanvas.style.opacity = '0';
      hud.classList.add('off');
      labelBox.querySelectorAll(':scope > span, .tag').forEach((el) => { el.style.opacity = 0; });
    }
    document.documentElement.classList.toggle('on-sky', s.mix > 0.5);
    if (s.mix > 0.001) {
      skyCanvas.style.opacity = String(s.mix);
      const rain = s.rain > 0.001 ? s.rain : 0;
      const key = s.t.toFixed(4) + ':' + rain.toFixed(3) + ':' + skyCanvas.width;
      const opts = { arc: false, mosque: innerWidth > 860 ? 0.17 : 0.12, horizon: 0.8, rain };
      // The weather chapter's rain falls every frame; with Reduce Motion the sky stays one still, veiled frame.
      if (rain && !reduce.matches && typeof drawRain === 'function') {
        if (key !== lastBase) { drawSky(skyBaseCtx, skyBase.width, skyBase.height, s.t, opts); lastBase = key; }
        skyCtx.drawImage(skyBase, 0, 0);
        drawRain(skyCtx, skyCanvas.width, skyCanvas.height, rain, now / 1000, { horizon: opts.horizon, scale: skyScale });
        lastSky = null;
      } else if (key !== lastSky && typeof drawSky === 'function') {
        drawSky(skyCtx, skyCanvas.width, skyCanvas.height, s.t, opts);
        lastSky = key;
      }
    } else skyCanvas.style.opacity = '0';
  }
  else hud.classList.add('off');
  soundFrame(s);
}

// Where a floating name sits on the screen, from its point and the offset its style gives it (see .labels).
const placed = [];
const LABEL_DY = { 5: 0.55, 6: -1.7, 7: -1.9 };
const labelRect = (k, pt, w, h) => { const top = pt.y + (LABEL_DY[k] ?? -1.4) * h; return { l: pt.x - w / 2, r: pt.x + w / 2, t: top, b: top + h }; };
const overlaps = (a, b, pad = 0) => a.l < b.r + pad && b.l < a.r + pad && a.t < b.b + pad && b.t < a.b + pad;

const youVec = () => { const p = you.lat * Math.PI / 180, l = you.lon * Math.PI / 180; return { x: Math.cos(p) * Math.sin(l) * 1.004, y: Math.sin(p) * 1.004, z: Math.cos(p) * Math.cos(l) * 1.004 }; };
// The legend of the five prayers' colours, Makkah's time, and the switch between the two looks of the globe.
const hud = document.createElement('div');
hud.className = 'hud off';
document.body.appendChild(hud);
let hudClock = null;
let globeStyle = store.get('mp-globe') === 'dots' ? 'dots' : 'earth';
function renderHud() {
  const t = story[lang], names = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];
  hud.innerHTML = `<p class="hud-legend">${esc(t.hud.legend)}</p><ul>${names.map((n, k) => `<li><i style="background:${PRAYER_COLORS[k]}"></i>${esc(t.prayers[n])}</li>`).join('')}</ul>
    <div class="hud-row"><span class="hud-clock"></span><span class="hud-style" role="group">${['earth', 'dots'].map((k, j) => `<button type="button" data-style="${k}" aria-pressed="${globeStyle === k}">${esc(t.hud.styles[j])}</button>`).join('')}</span></div>`;
  hudClock = hud.querySelector('.hud-clock');
  measureHud();
  hud.querySelectorAll('[data-style]').forEach((b) => b.addEventListener('click', () => {
    globeStyle = b.dataset.style;
    store.set('mp-globe', globeStyle);
    globe?.setStyle(globeStyle);
    hud.querySelectorAll('[data-style]').forEach((x) => x.setAttribute('aria-pressed', String(x.dataset.style === globeStyle)));
  }));
}

// The descent to Makkah, in real photographs that grow with the camera as one shot: by day the city from the
// International Space Station (its centre on Makkah, at its real scale: about 3.9 km across), then the tawaf
// round the Kaaba; by night Masjid al-Haram lit, the view closing in on the Kaaba. Day or night follows the real
// time in Makkah unless the visitor picks one.
// The legend's height, so the words on phones can stay clear of it.
function measureHud() { document.documentElement.style.setProperty('--hud-h', `${hud.offsetHeight}px`); }
document.fonts?.ready.then(() => { measureHud(); labelBox.querySelectorAll(':scope > span').forEach((el) => { el._text = null; }); measure(); });
const descent = $('.descent'), satBox = $('.descent .sat'), nightImg = $('.descent .night'), film = $('.descent .film');
let descentShown = 0, nightPick = null, filmTall = null;
// Portrait screens get a tall cut of the film at the source's full detail; wide ones the wide cut.
function pickFilm() {
  const tall = innerWidth / innerHeight < 0.9;
  if (tall === filmTall) return;
  filmTall = tall;
  const name = tall ? 'kaaba-tawaf-tall' : 'kaaba-tawaf';
  film.innerHTML = `<source src="${BASE}assets/${name}.webm" type="video/webm"><source src="${BASE}assets/${name}.mp4" type="video/mp4">`;
  film.load();
}
pickFilm();
const makkahNight = () => {
  if (nightPick !== null) return nightPick;
  const sun = sunAt(dayOfYear(), utcNow()), D = Math.PI / 180;
  const f = 21.4225 * D, d = sun.decl * D, h = (39.8262 - sun.lon) * D;
  return Math.asin(Math.sin(f) * Math.sin(d) + Math.cos(f) * Math.cos(d) * Math.cos(h)) / D < -4;
};
function descentFrame(s, zoom, wide) {
  const dist = 1 + 4.4 / zoom;
  const viewKm = 2 * 0.2867 * (dist - 1) * 6371;       // the height of the view on the ground
  const pxPerKm = innerHeight / viewKm;
  const L = globe.labels();
  const size = 3.9 * pxPerKm;                           // the photograph's width on the screen
  const dpr = Math.min(3, devicePixelRatio || 1);
  const viewMinKm = viewKm * Math.min(1, innerWidth / innerHeight); // across the narrower side of the screen
  const night = makkahNight();
  const fade = (1 - s.mix) * (L.makkah.facing > 0.2 ? 1 : 0);
  const sm = (x) => smooth(x);
  // Day: the photograph from space, on Makkah, then the tawaf as the view comes down to the mosque.
  // Sizes against the screen's narrower side, so a phone sees the photograph come in at the same moment.
  const minDim = Math.min(innerWidth, innerHeight);
  const satOp = night ? 0 : sm((Math.log(size) - Math.log(minDim * 0.12)) / (Math.log(minDim * 0.6) - Math.log(minDim * 0.12)));
  // The film takes over before the photograph is enlarged past its own detail (2600 pixels across).
  const filmOp = night ? 0 : Math.max(sm((size * dpr - 2900) / 3200), sm((1.3 - viewMinKm) / 0.7));
  satBox.style.cssText = `opacity:${(satOp * (1 - filmOp)).toFixed(3)};width:${size}px;height:${size}px;left:${L.makkah.x - size / 2}px;top:${L.makkah.y - size / 2}px`;
  film.style.opacity = (filmOp * fade).toFixed(3);
  film.style.transform = `scale(${(1 + Math.max(0, viewKm - 0.4) * 0.3).toFixed(4)})`;
  if (filmOp > 0.01 && fade > 0.01) { if (film.paused) film.play().catch(() => {}); } else if (!film.paused) film.pause();
  // Night: the mosque lit, the view closing in on the Kaaba.
  const nightOp = night ? sm((Math.log(size) - Math.log(minDim * 0.4)) / (Math.log(minDim * 1.5) - Math.log(minDim * 0.4))) : 0;
  nightImg.style.opacity = nightOp.toFixed(3);
  // Closing in on the Kaaba, never past one and a half screen pixels per pixel of the photograph.
  const cover = Math.max(innerWidth / 2213, innerHeight / 2503) * dpr;
  const maxScale = Math.max(1, 1.5 / cover);
  nightImg.style.transform = `scale(${(1 + (maxScale - 1) * Math.max(0, Math.min(1, Math.log(Math.max(1, size / 1300)) / Math.log(6)))).toFixed(4)})`;
  descent.style.opacity = fade.toFixed(3);
  descentShown = Math.max(satOp, filmOp, nightOp) * fade;
  // The globe's waves and lights step back under the photographs.
  return Math.max(satOp, nightOp);
}

// Cards that rise where a named city's Adhan comes: the city, the prayer, and its local time.
const TAG_LIFE = 3.4, fmt = {};
let lastTag = 0;
function localTime(hours, tz) {
  const key = lang + tz;
  fmt[key] = fmt[key] || new Intl.DateTimeFormat(lang === 'ar' ? 'ar-u-nu-latn' : 'en-US', { hour: 'numeric', minute: '2-digit', timeZone: tz });
  return fmt[key].format(new Date(dayStart + hours * 3600e3));
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
    free._w = free._h = 0;
    free._v = ev.v;
    lastTag = now;
  }
  // The legend's box, when it shows: the cards keep off it too.
  let hudBox = null;
  if (!hud.classList.contains('off')) { const r = hud.getBoundingClientRect(); hudBox = { l: r.left, r: r.right, t: r.top, b: r.bottom }; }
  tags.forEach((el) => {
    const age = el.dataset.born ? now - +el.dataset.born : 99;
    if (!show || age > TAG_LIFE || !el._v) { el.style.opacity = 0; return; }
    const pt = globe.project(el._v);
    if (pt.facing < 0.15) { el.style.opacity = 0; return; }
    // A card that would cover a city's name steps back while it does.
    const w = el._w || (el._w = el.offsetWidth), h = el._h || (el._h = el.offsetHeight);
    const box = { l: pt.x - w / 2, r: pt.x + w / 2, t: pt.y - h - 22, b: pt.y - 14 };
    const clear = !placed.some((r) => overlaps(r, box, 4)) && !(hudBox && overlaps(hudBox, box, 6));
    el.style.left = `${pt.x}px`; el.style.top = `${pt.y}px`;
    el.style.opacity = clear ? String(Math.min(1, age / 0.35, (TAG_LIFE - age) / 0.6)) : '0';
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
  store.set('mp-lang', lang);
  render();
  applyTheme();
  if (chapters[at]) scrollTo(0, tops[at] + frac * heights[at]);
});
$('#theme').addEventListener('click', () => {
  theme = THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length];
  store.set('mp-theme', theme);
  applyTheme();
});
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);
addEventListener('resize', () => { pickFilm(); measureHud(); measure(); sizeSky(); globe?.resize(); });

render();
sizeSky();
applyTheme();
labelBox.innerHTML = '<span></span><span></span><span></span><span></span><span></span><span class="makkah"></span><span class="madinah"></span><span class="you"></span>' + '<div class="tag"></div>'.repeat(4);
fetch(BASE + 'assets/zones.json').then((r) => r.json()).then((zones) => {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  // A built-in place in the same zone (other than Makkah and Madinah) gives the Arabic name too.
  const place = places.find((p) => p[4] === tz && !/^(Makkah|Madinah)$/.test(p[0]));
  const z = zones[tz];
  if (place) Object.assign(you, { lat: place[2], lon: place[3], en: place[0], ar: place[1] });
  else if (z) Object.assign(you, { lat: z[0], lon: z[1], en: z[2], ar: '' });
  else return;
  buildStates();
  globe?.setYou(you.lat, you.lon);
}).catch(() => {});
createGlobe(globeCanvas, BASE, places).then((g) => {
  if (g) { g.setStyle(globeStyle); g.setYou(you.lat, you.lon); }
  globe = g;
  if (!g) { document.documentElement.classList.add('no-webgl'); return; }
  g.resize();
  applyTheme();
}).catch(() => document.documentElement.classList.add('no-webgl'));
requestAnimationFrame(frame);
addEventListener('load', measure);
