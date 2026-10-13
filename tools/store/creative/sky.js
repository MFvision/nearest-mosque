// The living sky of the app (ios/App/Support/Sky.swift, Art.swift) drawn on a canvas, for the App Store
// creative assets and the promo videos. drawSky(ctx, w, h, t, opts) draws one frame; t runs 0..1 over one loop
// (a whole day, Fajr to Fajr) and the frame at t = 1 equals the frame at t = 0, so the video loops seamlessly.
// Reference data: colours are the app's dark skies ("sky" in shared/design/tokens.json).

const GOLD = [212, 168, 67];

// The periods of the day and where they fall in the loop.
const KEYS = [
  [0.00, 'fajr', 0x141E3C, 0x3A3566, 0xC98A8A, 0x1E2242],
  [0.14, 'sunrise', 0x1C3F7A, 0x345A99, 0xF3C98B, 0x26365E],
  [0.30, 'day', 0x1C3F7A, 0x2F5590, 0xF7E0A8, 0x22406E],
  [0.47, 'asr', 0x173F5A, 0x23606F, 0xF2D27A, 0x1D3E4E],
  [0.62, 'maghrib', 0x1B1F45, 0x4A2F5E, 0xF08A5D, 0x2A1E3E],
  [0.75, 'isha', 0x0A1430, 0x16264D, 0x3A4E86, 0x0B1530],
  [0.88, 'night', 0x081B29, 0x12304A, 0x2C4C7A, 0x0B1A2C],
  [1.00, 'fajr', 0x141E3C, 0x3A3566, 0xC98A8A, 0x1E2242],
];

const rgb = (h) => [(h >> 16) & 255, (h >> 8) & 255, h & 255];
const mix = (a, b, f) => a.map((v, i) => v + (b[i] - v) * f);
const css = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;
const smooth = (x) => x * x * (3 - 2 * x);
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const TAU = Math.PI * 2;

function palette(t) {
  for (let i = 0; i < KEYS.length - 1; i++) {
    const [a, , ...ca] = KEYS[i];
    const [b, , ...cb] = KEYS[i + 1];
    if (t >= a && t <= b) {
      const f = smooth((t - a) / (b - a));
      return { top: mix(rgb(ca[0]), rgb(cb[0]), f), mid: mix(rgb(ca[1]), rgb(cb[1]), f), glow: mix(rgb(ca[2]), rgb(cb[2]), f), low: mix(rgb(ca[3]), rgb(cb[3]), f) };
    }
  }
}

// How dark it is (1 = full night): stars and the moon follow it.
function nightness(t) {
  const d = (x, a, b) => clamp((x - a) / (b - a));
  if (t < 0.14) return 1 - smooth(d(t, 0.0, 0.14)) * 0.95;
  if (t < 0.6) return 0.05 * (1 - d(t, 0.14, 0.2));
  return smooth(d(t, 0.62, 0.8));
}

// A deterministic random sequence (the same stars and clouds in every frame).
function rand(seed) {
  let s = seed;
  return () => ((s = (s * 16807) % 2147483647) - 1) / 2147483646;
}

// The sun's path: from the left horizon (sunrise) to the right one (Maghrib), and the prayer marks on it.
// The path rises steeply at the edges and runs high across the middle, so the sun and the moon pass above the
// phrase in the centre (never behind it).
function arcPoint(w, h, s, horizon) {
  const x = w * (0.06 + 0.88 * s);
  const y = horizon - Math.sqrt(Math.sin(Math.PI * clamp(s))) * (horizon - h * 0.1);
  return [x, y];
}

const MARKS = [
  // [where on the path, when it lights in the loop]
  [0.02, 0.03], // Fajr
  [0.5, 0.32], // Dhuhr
  [0.7, 0.47], // Asr
  [0.98, 0.62], // Maghrib
];

function skyline(ctx, x, y, w, h, fill) {
  // MosqueSkyline (Art.swift) in a w x h box whose bottom sits on y.
  const p = (u, v) => [x + u * w, y - h + v * h];
  ctx.fillStyle = fill;
  ctx.beginPath();
  const rect = (u, v, uw, vh) => { const [a, b] = p(u, v); ctx.rect(a, b, uw * w, vh * h); };
  rect(0.16, 0.66, 0.68, 0.34);
  const dome = (cx, hw, base, top) => {
    ctx.moveTo(...p(cx - hw, base));
    ctx.bezierCurveTo(...p(cx - hw * 1.05, base - (base - top) * 0.55), ...p(cx - hw * 0.45, top + (base - top) * 0.2), ...p(cx, top));
    ctx.bezierCurveTo(...p(cx + hw * 0.45, top + (base - top) * 0.2), ...p(cx + hw * 1.05, base - (base - top) * 0.55), ...p(cx + hw, base));
    ctx.closePath();
    rect(cx - 0.004, top - 0.08, 0.008, 0.09);
  };
  dome(0.5, 0.17, 0.68, 0.2);
  dome(0.27, 0.08, 0.7, 0.46);
  dome(0.73, 0.08, 0.7, 0.46);
  for (const u of [0.06, 0.9]) {
    rect(u, 0.12, 0.04, 0.88);
    rect(u - 0.008, 0.34, 0.056, 0.03);
    ctx.moveTo(...p(u, 0.12));
    ctx.lineTo(...p(u + 0.02, 0.0));
    ctx.lineTo(...p(u + 0.04, 0.12));
    ctx.closePath();
  }
  ctx.fill();
  // Crescents on the dome finials.
}

function ridge(ctx, w, h, base, amp, seed, fill) {
  const r = rand(seed);
  const waves = [0, 1, 2].map(() => [1 + Math.floor(r() * 3), r() * TAU, 0.4 + r() * 0.6]);
  ctx.fillStyle = fill;
  ctx.beginPath();
  ctx.moveTo(0, h);
  for (let i = 0; i <= 200; i++) {
    const u = i / 200;
    let v = 0;
    for (const [k, ph, a] of waves) v += a * Math.sin(TAU * k * u + ph);
    ctx.lineTo(u * w, base - amp * (0.6 + 0.4 * v / 2));
  }
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fill();
}

function crescent(ctx, x, y, r, glow) {
  // A shining crescent: a soft halo, then the lit sliver (a disc minus an offset disc).
  const halo = ctx.createRadialGradient(x, y, r * 0.6, x, y, r * 5);
  halo.addColorStop(0, `rgba(220,230,255,${0.45 * glow})`);
  halo.addColorStop(0.35, `rgba(200,215,255,${0.14 * glow})`);
  halo.addColorStop(1, 'rgba(200,215,255,0)');
  ctx.fillStyle = halo;
  ctx.beginPath(); ctx.arc(x, y, r * 5, 0, TAU); ctx.fill();
  const off = document.createElement('canvas');
  off.width = off.height = Math.ceil(r * 2.4);
  const o = off.getContext('2d');
  const c = off.width / 2;
  const g = o.createLinearGradient(c - r, c - r, c + r, c + r);
  g.addColorStop(0, '#FFF6DA'); g.addColorStop(1, '#F3E3B0');
  o.fillStyle = g;
  o.beginPath(); o.arc(c, c, r, 0, TAU); o.fill();
  o.globalCompositeOperation = 'destination-out';
  o.beginPath(); o.arc(c + r * 0.42, c - r * 0.22, r * 0.86, 0, TAU); o.fill();
  ctx.globalAlpha = glow;
  ctx.drawImage(off, x - c, y - c);
  ctx.globalAlpha = 1;
}

const sprites = {};
function cloudSprite(sc, seed) {
  // A white cloud (a few overlapping ellipses), blurred once and cached.
  const key = sc + ':' + seed;
  if (sprites[key]) return sprites[key];
  const pad = sc;
  const c = document.createElement('canvas');
  c.width = Math.ceil(sc * 6 + pad * 2); c.height = Math.ceil(sc * 1.2 + pad * 2);
  const o = c.getContext('2d');
  o.filter = `blur(${sc * 0.35}px)`;
  o.fillStyle = '#fff';
  for (let j = 0; j < 5; j++) {
    o.beginPath();
    o.ellipse(c.width / 2 + (j - 2) * sc * 0.9, c.height / 2 + Math.sin(j * 1.7 + seed) * sc * 0.12, sc * (1.3 - Math.abs(j - 2) * 0.2), sc * 0.22, 0, 0, TAU);
    o.fill();
  }
  return (sprites[key] = c);
}

function drawSky(ctx, w, h, t, opts = {}) {
  t = ((t % 1) + 1) % 1;
  const horizon = h * (opts.horizon ?? 0.72);
  const pal = palette(t);
  const night = nightness(t);
  const unit = Math.min(w, h * 2.2);

  // Sky gradient, the horizon glow.
  const g = ctx.createLinearGradient(0, 0, 0, horizon);
  g.addColorStop(0, css(pal.top));
  g.addColorStop(0.62, css(pal.mid));
  g.addColorStop(1, css(mix(pal.mid, pal.glow, 0.55)));
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, w, horizon + 2);

  // The sun's position (and so where the glow is strongest).
  const sunS = clamp((t - 0.12) / (0.64 - 0.12));
  const sunUp = t > 0.11 && t < 0.65;
  const [sx, sy] = arcPoint(w, h, sunS, horizon);
  const glowX = sunUp ? sx : w * 0.5;
  const hg = ctx.createRadialGradient(glowX, horizon, 0, glowX, horizon, w * 0.6);
  hg.addColorStop(0, css(pal.glow, 0.55));
  hg.addColorStop(0.4, css(pal.glow, 0.18));
  hg.addColorStop(1, css(pal.glow, 0));
  ctx.fillStyle = hg;
  ctx.fillRect(0, 0, w, horizon + 2);

  // Stars, twinkling (whole numbers of twinkles per loop).
  if (night > 0.01) {
    const r = rand(7);
    for (let i = 0; i < 260; i++) {
      const x = r() * w, y = Math.pow(r(), 1.4) * horizon * 0.9, s = 0.4 + r() * 1.3, k = 1 + Math.floor(r() * 4), ph = r() * TAU;
      const a = night * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(TAU * k * t + ph)));
      ctx.fillStyle = `rgba(255,255,255,${a * 0.85})`;
      ctx.beginPath(); ctx.arc(x, y, s * unit / 1400, 0, TAU); ctx.fill();
    }
  }

  // Clouds drifting (one or two crossings per loop), from soft sprites blurred once.
  {
    const r = rand(3);
    for (let i = 0; i < 7; i++) {
      const y = horizon * (0.15 + r() * 0.45), sc = unit * (0.05 + r() * 0.06), sp = 1 + Math.floor(r() * 2), x0 = r();
      const x = (((x0 + sp * t) % 1) * 1.4 - 0.2) * w;
      const sprite = cloudSprite(Math.round(sc), i);
      ctx.globalAlpha = 0.10 + 0.08 * (1 - night);
      ctx.drawImage(sprite, x - sprite.width / 2, y - sprite.height / 2);
      ctx.globalAlpha = 1;
    }
  }

  // The gold path of the sun with the prayer marks; each mark lights when its time comes.
  if (opts.arc !== false) {
    ctx.save();
    ctx.strokeStyle = css(GOLD, 0.30);
    ctx.lineWidth = Math.max(1.5, unit / 900);
    ctx.setLineDash([unit / 160, unit / 140]);
    ctx.beginPath();
    for (let i = 0; i <= 120; i++) { const [x, y] = arcPoint(w, h, i / 120, horizon); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); }
    ctx.stroke();
    ctx.restore();
    for (const [s, at] of MARKS) {
      const [x, y] = arcPoint(w, h, s, horizon);
      const since = ((t - at) % 1 + 1) % 1;
      const pulse = since < 0.08 ? Math.sin(Math.PI * since / 0.08) : 0;
      const lit = since < 0.9 ? 1 - since / 0.9 * 0.55 : 0.45;
      const r0 = unit / 260;
      const halo = ctx.createRadialGradient(x, y, 0, x, y, r0 * (4 + 6 * pulse));
      halo.addColorStop(0, css(GOLD, 0.55 * lit + 0.4 * pulse));
      halo.addColorStop(1, css(GOLD, 0));
      ctx.fillStyle = halo;
      ctx.beginPath(); ctx.arc(x, y, r0 * (4 + 6 * pulse), 0, TAU); ctx.fill();
      ctx.fillStyle = css(mix(GOLD, [255, 244, 210], 0.4 * pulse), 0.65 + 0.35 * lit);
      ctx.beginPath(); ctx.arc(x, y, r0 * (1 + 0.5 * pulse), 0, TAU); ctx.fill();
    }
  }

  // The sun.
  if (sunUp) {
    const rise = clamp(Math.min(t - 0.11, 0.65 - t) / 0.03);
    const R = unit / 55;
    // Low on the horizon the sun is deeper orange; high, a pale gold.
    const high = Math.sin(Math.PI * sunS);
    const core = mix([255, 170, 90], [255, 236, 186], high), rim = mix([240, 120, 60], [247, 200, 110], high);
    const sg = ctx.createRadialGradient(sx, sy, R * 0.8, sx, sy, R * 9);
    sg.addColorStop(0, css(core, 0.55 * rise));
    sg.addColorStop(0.18, css(rim, 0.22 * rise));
    sg.addColorStop(1, css(rim, 0));
    ctx.fillStyle = sg;
    ctx.beginPath(); ctx.arc(sx, sy, R * 9, 0, TAU); ctx.fill();
    const disc = ctx.createRadialGradient(sx - R * 0.25, sy - R * 0.25, 0, sx, sy, R);
    disc.addColorStop(0, css([255, 250, 232], rise));
    disc.addColorStop(0.7, css(core, rise));
    disc.addColorStop(1, css(rim, rise));
    ctx.fillStyle = disc;
    ctx.beginPath(); ctx.arc(sx, sy, R, 0, TAU); ctx.fill();
  }

  // The moon: rises after Maghrib, sets before sunrise (crossing the loop's end).
  const mt = ((t - 0.64) % 1 + 1) % 1; // 0 at Maghrib
  let moon = null;
  if (mt < 0.46) {
    const s = mt / 0.46;
    const [mx, my] = arcPoint(w, h, 1 - s, horizon);
    const vis = clamp(Math.min(mt, 0.46 - mt) / 0.05) * clamp(night * 1.3);
    if (vis > 0) { crescent(ctx, mx, my, unit / 48, vis); moon = [mx, my, vis]; }
  }

  // Mountain ridges behind the mosque, darker towards the front.
  ridge(ctx, w, h, horizon + 2, h * 0.06, 11, css(mix(pal.low, pal.mid, 0.35), 0.9));
  ridge(ctx, w, h, horizon + 2, h * 0.035, 23, css(mix(pal.low, [0, 0, 0], 0.15), 0.95));

  // The mosque on the horizon.
  const mh = h * (opts.mosque ?? 0.2);
  // The mosque's centre (fraction of the width).
  const mx0 = w * (opts.mosqueX ?? 0.5);
  skyline(ctx, mx0 - mh * 1.1, horizon + 2, mh * 2.2, mh, css(mix(pal.low, [0, 0, 0], 0.35)));
  // Warm lit windows at night.
  if (night > 0.2) {
    ctx.fillStyle = `rgba(255,205,120,${0.55 * night})`;
    for (let i = 0; i < 5; i++) ctx.fillRect(mx0 - mh * 0.55 + i * mh * 0.25, horizon - mh * 0.16, mh * 0.06, mh * 0.1);
  }

  // The calm lake: the sky reflected, with a shimmering streak under the sun or the moon.
  const lake = ctx.createLinearGradient(0, horizon, 0, h);
  lake.addColorStop(0, css(mix(pal.low, pal.glow, 0.25)));
  lake.addColorStop(1, css(mix(pal.low, [0, 0, 0], 0.3)));
  ctx.fillStyle = lake;
  ctx.fillRect(0, horizon + 2, w, h - horizon);
  const light = sunUp ? [sx, 1, [255, 226, 170]] : moon ? [moon[0], moon[2] * 0.7, [210, 222, 255]] : null;
  if (light) {
    const [lx, la, lc] = light;
    for (let i = 0; i < 40; i++) {
      const f = i / 40;
      const y = horizon + 4 + (h - horizon) * f;
      const wob = Math.sin(TAU * (4 * t + i * 0.53)) * unit / 260 * (0.4 + f);
      const half = unit / 90 * (1 + f * 2.2) * (0.75 + 0.25 * Math.sin(TAU * (2 * t + i * 0.31)));
      const cx = lx + wob;
      ctx.save();
      ctx.translate(cx, y);
      ctx.scale(1, (h - horizon) / 60 / half);
      const gx = ctx.createRadialGradient(0, 0, 0, 0, 0, half);
      gx.addColorStop(0, css(lc, la * 0.3 * (1 - f * 0.8)));
      gx.addColorStop(1, css(lc, 0));
      ctx.fillStyle = gx;
      ctx.beginPath(); ctx.arc(0, 0, half, 0, TAU); ctx.fill();
      ctx.restore();
    }
  }
  // The mosque reflected in the water, soft and faint.
  ctx.save();
  ctx.translate(0, 2 * (horizon + 2));
  ctx.scale(1, -1);
  ctx.globalAlpha = 0.3;
  skyline(ctx, mx0 - mh * 1.1, horizon + 2, mh * 2.2, mh, css(mix(pal.low, [0, 0, 0], 0.45)));
  if (night > 0.2) {
    ctx.fillStyle = `rgba(255,205,120,${0.5 * night})`;
    for (let i = 0; i < 5; i++) ctx.fillRect(mx0 - mh * 0.55 + i * mh * 0.25, horizon - mh * 0.16, mh * 0.06, mh * 0.1);
  }
  ctx.restore();
  // Ripples.
  ctx.strokeStyle = 'rgba(255,255,255,0.05)';
  ctx.lineWidth = 1;
  for (let i = 1; i < 9; i++) {
    const y = horizon + (h - horizon) * Math.pow(i / 9, 1.3);
    ctx.beginPath();
    for (let u = 0; u <= 60; u++) { const x = u / 60 * w; const yy = y + Math.sin(TAU * (u / 12 + 2 * t + i * 0.2)) * 1.5; u ? ctx.lineTo(x, yy) : ctx.moveTo(x, yy); }
    ctx.stroke();
  }
  const v = ctx.createRadialGradient(w / 2, h * 0.45, h * 0.3, w / 2, h * 0.45, w * 0.75);
  v.addColorStop(0, 'rgba(0,0,0,0)');
  v.addColorStop(1, 'rgba(0,0,0,0.28)');
  ctx.fillStyle = v;
  ctx.fillRect(0, 0, w, h);
  return { night, period: KEYS.reduce((p, k) => (t >= k[0] ? k[1] : p), 'fajr') };
}

if (typeof module !== 'undefined') module.exports = { drawSky, MARKS };
