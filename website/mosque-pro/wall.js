// The wall of screens on the landing page: a dark band that holds the app's real screens in columns (three on
// phones, five on wider screens, seven on wide ones) that glide as the page scrolls, alternate columns in opposite
// directions, with an iPhone held still over the middle column. The middle column's screens are cut to the phone's
// screen and spaced so that, as they pass behind it, the phone shows the app one screen at a time. The band ends with
// one short line; the download buttons stay where they are on the page. It fills each [data-wall] on the page, in the
// page's language, and follows the page when its language changes.
//
// The motion: this file sets the progress values on each frame while the band is on screen, and wall.css turns them
// into transforms (only transforms). Not CSS scroll-driven animations: Safari 26 and Chrome on Windows both left the
// phone on its first screen with them. With Reduce Motion the columns and the phone stand still, and the phone
// changes from one screen to the next at the same points of the scroll, without sliding.
//
// The hand: off unless the band says which pictures to use, as data-hand="1" for assets/wall/hand-back-1.webp and
// assets/wall/hand-front-1.webp (README.md). Without it nothing is requested.
const BASE = new URL('assets/', import.meta.url).href;
const T = {
  en: { eyebrow: 'Screen by screen', title: ['Every screen, ', 'made for prayer', '.'] },
  ar: { eyebrow: 'شاشةً بعد شاشة', title: ['كل شاشةٍ ', 'صُمِّمت للصلاة', '.'] },
};
// Picture sizes by folder (assets/<lang>/<folder>/), for the width and height of each card.
const SIZE = { iphone: [640, 1391], android: [640, 1387], wall: [590, 1279] };
// The screens the phone shows, in turn. Five: the phone stops on each at every quarter of the pinned stretch
// (stepAt below), and .wall's --run in wall.css gives that stretch four steps' height and a little more.
const STEPS = ['iphone/01-prayer', 'iphone/02-qibla', 'iphone/03-mosques-map', 'iphone/04-ask', 'iphone/05-classics'];
// The middle column outside the phone runs on as if the reel went round: the last two screens above the first, the
// first two below the last.
const MID = [STEPS[3], STEPS[4], ...STEPS, STEPS[0], STEPS[1]];
// The other columns never show one of the phone's screens. Each layout deals the same thirteen screens between its
// columns, so no screen is on view twice at once, and each column repeats its own few as it runs (no more often than
// it has room for). From the reading side; the number is the column's ring, counted out from the middle column.
const W = (n) => `wall/${n}`, I = (n) => `iphone/${n}`, A = (n) => `android/${n}`;
// Only screens without the other app's name (the Android welcome steps "promise", "extras", "more", "adhan" and
// "done" say it, so they are left out).
const DEAL = {
  narrow: [
    [1, [I('06-widgets'), W('asr'), I('09-mosques-radar'), W('weather'), A('07-onboarding-look'), I('10-prayer-dark'), W('privacy')]],
    [1, [I('08-audio'), W('where'), W('friday'), I('07-island'), A('09-onboarding-day'), I('02-prayer-weather')]],
  ],
  mid: [
    [2, [I('06-widgets'), I('10-prayer-dark'), W('asr'), W('privacy')]],
    [1, [I('09-mosques-radar'), W('weather'), A('07-onboarding-look')]],
    [1, [I('08-audio'), W('where'), A('09-onboarding-day')]],
    [2, [I('07-island'), I('02-prayer-weather'), W('friday')]],
  ],
  wide: [
    [3, [I('07-island'), W('privacy')]],
    [2, [I('06-widgets'), W('asr')]],
    [1, [I('09-mosques-radar'), W('weather')]],
    [1, [I('08-audio'), W('where'), A('09-onboarding-day')]],
    [2, [I('02-prayer-weather'), W('friday')]],
    [3, [I('10-prayer-dark'), A('07-onboarding-look')]],
  ],
};
// How each ring moves: which way as the page scrolls (1 up, -1 down; the middle column goes up), how fast as a share
// of the scroll, and where it starts (in cards), for the column on the reading side and the one on the other side.
const RING = {
  1: { dir: -1, r: [0.55, 0.62], off: [0.2, -0.15] },
  2: { dir: 1, r: [0.72, 0.66], off: [-0.3, 0.35] },
  3: { dir: -1, r: [0.6, 0.64], off: [0.1, -0.25] },
};
const CARDS = 12; // in each of the other columns
// The layouts, as the breakpoints in wall.css.
const MIDDLE = matchMedia('(min-width: 700px)');
const WIDE = matchMedia('(min-width: 700px) and (min-aspect-ratio: 3/2), (min-width: 1280px)');
const layout = () => (WIDE.matches ? 'wide' : MIDDLE.matches ? 'mid' : 'narrow');

const spots = [...document.querySelectorAll('[data-wall]')];
const pageLang = () => (document.documentElement.lang === 'ar' ? 'ar' : 'en');
const reduce = matchMedia('(prefers-reduced-motion: reduce)');
const state = new Map(); // each spot: { key, near, live, geo }

const card = (lang, shot) => {
  const [w, h] = SIZE[shot.split('/')[0]];
  return `<img class="wall-card" data-src="${BASE}${lang}/${shot}.webp" alt="" width="${w}" height="${h}" decoding="async">`;
};
// The hand's pictures, by the number in data-hand: a new number for a new picture, past the week the pictures are cached.
const handOf = (el) => (/^[1-9]\d{0,3}$/.test(el.dataset.hand || '') ? el.dataset.hand : '');
const hand = (v, layer) => (v ? `<img class="wall-hand wall-hand-${layer}" data-src="${BASE}wall/hand-${layer}-${v}.webp" alt="" decoding="async">` : '');

function render(el, k) {
  const s = state.get(el);
  const lang = pageLang(), m = layout(), key = `${lang} ${m}`;
  if (s.key === key) return;
  s.key = key;
  const t = T[lang], cols = DEAL[m], half = cols.length / 2, v = handOf(el);
  const side = ([ring, set], j) => {
    const c = RING[ring], end = j < half ? 0 : 1;
    const shots = Array.from({ length: CARDS }, (_, i) => set[(i + j) % set.length]);
    return `<div class="wall-col wall-side" data-ring="${ring}" style="--dir:${c.dir};--r:${c.r[end]};--off:${c.off[end]};--n:${CARDS}"><div class="wall-strip">${shots.map((x) => card(lang, x)).join('')}</div></div>`;
  };
  const sides = cols.map(side);
  const reel = STEPS.map((x) => card(lang, x)).join('');
  el.setAttribute('aria-labelledby', `wall-title-${k}`);
  el.innerHTML = `<div class="wall-track">
      <div class="wall-stage" aria-hidden="true">
        <div class="wall-cols">
          ${sides.slice(0, half).join('')}
          <div class="wall-col wall-mid"><div class="wall-strip">${MID.map((x) => card(lang, x)).join('')}</div></div>
          ${sides.slice(half).join('')}
        </div>
        <div class="wall-shade"></div>
        <div class="wall-hold"><div class="wall-lean">
          ${hand(v, 'back')}
          <div class="wall-phone"><div class="wall-screen">
            <div class="wall-reel">${reel}</div>
            <div class="wall-bar"><div class="wall-reel">${reel}</div></div>
            <i class="wall-island"></i>
          </div></div>
          ${hand(v, 'front')}
        </div></div>
      </div>
    </div>
    <div class="wall-end">
      <p class="wall-eyebrow">${t.eyebrow}</p>
      <h2 class="wall-title" id="wall-title-${k}">${t.title[0]}<span>${t.title[1]}</span>${t.title[2]}</h2>
    </div>`;
  el.classList.toggle('has-hand', !!v);
  s.stage = el.querySelector('.wall-stage');
  s.track = el.querySelector('.wall-track');
  if (seen) {
    if (s.end) seen.unobserve(s.end);
    s.end = el.querySelector('.wall-end');
    if (!el.classList.contains('in')) seen.observe(s.end);
  } else el.classList.add('in');
  if (s.near) load(el);
  measure();
}

// Pictures load only as the band comes near, and only for the columns this screen shows (a column can be laid out
// but wholly past the edge of a narrower screen).
function load(el) {
  const vw = innerWidth;
  el.querySelectorAll('img[data-src]').forEach((img) => {
    const r = img.closest('.wall-col, .wall-hold').getBoundingClientRect();
    if (!r.width || r.right <= 0 || r.left >= vw) return;
    if (img.classList.contains('wall-hand')) img.addEventListener('error', () => img.remove(), { once: true });
    img.src = img.dataset.src;
    img.removeAttribute('data-src');
  });
}

// Where each band is on the page, read only when the page changes size (never while scrolling).
function measure() {
  spots.forEach((el) => {
    const s = state.get(el);
    if (!s || !s.track) return;
    const r = s.track.getBoundingClientRect();
    s.geo = { top: r.top + scrollY, h: r.height, stage: s.stage.offsetHeight };
  });
  frame();
}

// The progress values wall.css reads, set on each frame while a band is on screen.
const clamp = (x) => (x < 0 ? 0 : x > 1 ? 1 : x);
const stepAt = (q) => {
  const n = STEPS.length - 1, u = q * n, i = Math.min(n - 1, Math.floor(u));
  return i + clamp((u - i - 0.1) / 0.8); // still for a tenth of each step either side of a screen
};
let queued = 0;
function frame() {
  queued = 0;
  const y = scrollY, vh = innerHeight, still = reduce.matches;
  spots.forEach((el) => {
    const s = state.get(el), g = s && s.geo;
    if (!g || !s.live) return;
    const enter = g.top - vh, pinned = g.top, unpinned = g.top + g.h - g.stage, gone = g.top + g.h;
    const st = s.stage.style;
    const step = stepAt(clamp((y - pinned) / Math.max(1, unpinned - pinned)));
    // Reduce Motion: the columns stay centred and the phone upright; its screen changes halfway between two stops.
    st.setProperty('--p', still ? '0.5' : clamp((y - enter) / (gone - enter)).toFixed(4));
    st.setProperty('--s', still ? String(Math.round(step)) : step.toFixed(4));
    st.setProperty('--tin', still ? '0' : ((1 - clamp((y - enter) / Math.max(1, pinned - enter))) ** 2).toFixed(4)); // 1 - ease-out
    st.setProperty('--tout', still ? '0' : (clamp((y - unpinned) / Math.max(1, gone - unpinned)) ** 2).toFixed(4));
  });
}
const queue = () => { if (!queued) queued = requestAnimationFrame(frame); };

function mode() {
  spots.forEach((el) => el.classList.add('wall-js'));
  measure();
}

const near = 'IntersectionObserver' in window ? new IntersectionObserver((list) => list.forEach((e) => {
  if (!e.isIntersecting) return;
  state.get(e.target).near = true;
  load(e.target);
}), { rootMargin: '150% 0px 150% 0px' }) : null;
const live = 'IntersectionObserver' in window ? new IntersectionObserver((list) => list.forEach((e) => {
  state.get(e.target).live = e.isIntersecting;
  if (e.isIntersecting) queue();
})) : null;
// The page's top bar takes light words while the band is under it, as it does over the sky (.on-sky in style.css).
const under = 'IntersectionObserver' in window ? new IntersectionObserver((list) => list.forEach((e) => {
  state.get(e.target).under = e.isIntersecting;
  document.documentElement.classList.toggle('wall-under', spots.some((el) => state.get(el)?.under));
}), { rootMargin: '0px 0px -93% 0px' }) : null;
// The closing line rises into place when it comes into view.
const seen = 'IntersectionObserver' in window ? new IntersectionObserver((list) => list.forEach((e) => {
  if (e.isIntersecting) { e.target.closest('[data-wall]').classList.add('in'); seen.unobserve(e.target); }
}), { rootMargin: '0px 0px -12% 0px' }) : null;

spots.forEach((el, k) => {
  el.classList.add('wall');
  state.set(el, { key: null, near: !near, live: !live });
  render(el, k);
  if (near) near.observe(el); else load(el);
  if (live) live.observe(el);
  if (under) under.observe(el);
});
if (spots.length) {
  mode();
  const again = () => spots.forEach(render);
  addEventListener('scroll', queue, { passive: true });
  addEventListener('resize', () => { measure(); spots.forEach((el) => { if (state.get(el).near) load(el); }); });
  addEventListener('load', measure);
  reduce.addEventListener?.('change', mode);
  MIDDLE.addEventListener?.('change', again);
  WIDE.addEventListener?.('change', again);
  // The story above changes height when the language changes or its pictures arrive: measure again then.
  if ('ResizeObserver' in window) new ResizeObserver(() => measure()).observe(document.body);
  new MutationObserver(again).observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] });
}
