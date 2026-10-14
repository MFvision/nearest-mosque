// The globe: land as dots, the day and night halves from the sun's real position, city lights on the night side,
// the five prayer lines (where the sun is at each prayer's angle right now), a ring rising from a city when its
// prayer time comes, and the Qibla arcs from cities to Makkah.
//
// Angles: Fajr 18° and Isha 17° below the horizon (Muslim World League), Maghrib at sunset (0.833° below),
// Dhuhr when the sun crosses the meridian, Asr when a shadow equals its object's length plus the noon shadow.
import * as THREE from './vendor/three.module.min.js';

const D = Math.PI / 180;
const MAKKAH = [21.4225, 39.8262];
// Cities whose Qibla arcs are drawn (latitude, longitude).
const ARC_CITIES = [[51.51, -0.13], [48.86, 2.35], [41.01, 28.98], [55.76, 37.62], [30.04, 31.24], [6.52, 3.38], [33.57, -7.59], [-1.29, 36.82],
  [-26.2, 28.05], [35.69, 51.39], [24.86, 67.0], [28.61, 77.21], [23.81, 90.41], [41.3, 69.24], [40.41, 49.87], [3.14, 101.69], [-6.21, 106.85],
  [52.52, 13.4], [40.42, -3.7], [14.72, -17.47], [15.55, 32.53], [2.05, 45.32], [36.75, 3.06], [33.89, 35.5], [33.31, 44.37], [12.97, 77.59], [43.24, 76.89]];

export function vec(lat, lon, r = 1) {
  const p = lat * D, l = lon * D;
  return new THREE.Vector3(Math.cos(p) * Math.sin(l) * r, Math.sin(p) * r, Math.cos(p) * Math.cos(l) * r);
}

// The sun for a day of the year and a UTC hour: declination and the longitude where it is overhead (degrees).
export function sunAt(day, utcHours) {
  const g = (2 * Math.PI / 365.24) * (day - 1 + (utcHours - 12) / 24);
  const decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
  const eot = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g)); // minutes
  let lon = (12 - utcHours - eot / 60) * 15;
  lon = ((lon + 180) % 360 + 360) % 360 - 180;
  return { decl: decl / D, lon };
}

const dotVS = `
  uniform vec3 uSun; uniform float uSize; uniform float uPR;
  varying float vDay;
  void main() {
    vDay = dot(normalize(position), uSun);
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = uSize * uPR * (3.2 / -mv.z);
  }`;
const dotFS = `
  uniform vec3 uDay; uniform vec3 uNight; uniform vec3 uDusk;
  varying float vDay;
  void main() {
    vec2 c = gl_PointCoord - 0.5; float d = length(c);
    if (d > 0.5) discard;
    float day = smoothstep(-0.1, 0.12, vDay);
    vec3 col = mix(uNight, uDay, day);
    float dusk = exp(-pow(vDay / 0.07, 2.0));
    col = mix(col, uDusk, dusk * 0.85);
    gl_FragColor = vec4(col, smoothstep(0.5, 0.32, d));
  }`;
const oceanVS = `
  varying vec3 vN; varying vec3 vV;
  void main() { vN = normalize(position); vV = normalize(normalMatrix * normal); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`;
const oceanFS = `
  uniform vec3 uSun; uniform vec3 uOceanDay; uniform vec3 uOceanNight; uniform vec3 uRim;
  varying vec3 vN; varying vec3 vV;
  void main() {
    float day = smoothstep(-0.18, 0.22, dot(vN, uSun));
    vec3 col = mix(uOceanNight, uOceanDay, day);
    float rim = pow(1.0 - max(0.0, vV.z), 2.6);
    col = mix(col, uRim, rim * 0.55);
    gl_FragColor = vec4(col, 1.0);
  }`;
const glowFS = `
  uniform vec3 uRim; varying vec3 vN; varying vec3 vV;
  void main() { float rim = pow(max(0.0, 1.0 - abs(vV.z)), 3.0); gl_FragColor = vec4(uRim, rim * 0.28); }`;
const lightVS = `
  attribute float aMag; uniform vec3 uSun; uniform float uPR; uniform float uOn;
  varying float vA;
  void main() {
    float night = smoothstep(0.04, -0.14, dot(normalize(position), uSun));
    vA = night * uOn * (0.35 + aMag * 0.65);
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = (2.0 + aMag * 5.0) * uPR * (3.2 / -mv.z);
  }`;
const glowDotFS = `
  uniform vec3 uColor; varying float vA;
  void main() { float d = length(gl_PointCoord - 0.5) * 2.0; if (d > 1.0) discard; gl_FragColor = vec4(uColor, vA * pow(1.0 - d, 1.6)); }`;
const lineVS = `
  attribute float aA; uniform float uPR; uniform float uOn; uniform float uSize;
  varying float vA;
  void main() { vA = aA * uOn; vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_Position = projectionMatrix * mv; gl_PointSize = uSize * uPR * (3.2 / -mv.z); }`;
const ringVS = `
  attribute float aBorn; uniform float uNow; uniform float uPR;
  varying float vAge;
  void main() {
    vAge = (uNow - aBorn) / 2.6;
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    float e = 1.0 - pow(1.0 - clamp(vAge, 0.0, 1.0), 3.0);
    gl_PointSize = (4.0 + 44.0 * e) * uPR * (3.2 / -mv.z);
  }`;
const ringFS = `
  uniform vec3 uColor; varying float vAge;
  void main() {
    if (vAge < 0.0 || vAge > 1.0) discard;
    float d = length(gl_PointCoord - 0.5) * 2.0;
    float ring = smoothstep(0.62, 0.8, d) * smoothstep(1.0, 0.84, d) + smoothstep(0.3, 0.0, d) * (1.0 - vAge) * 0.8;
    gl_FragColor = vec4(uColor, ring * (1.0 - vAge));
  }`;

export async function createGlobe(canvas, base) {
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'high-performance' });
  } catch (e) {
    return null;
  }
  const pr = Math.min(2, window.devicePixelRatio || 1);
  renderer.setPixelRatio(pr);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(32, 1, 0.01, 200);
  camera.position.set(0, 0, 3.4);
  const world = new THREE.Group();
  world.rotation.order = 'XYZ';
  scene.add(world);

  const [landData, cityData] = await Promise.all([fetch(base + 'assets/land.json').then((r) => r.json()), fetch(base + 'assets/cities.json').then((r) => r.json())]);
  const uSun = { value: new THREE.Vector3(1, 0, 0) };
  const uPR = { value: pr };
  const colors = {};
  const col = (k) => (colors[k] = colors[k] || { value: new THREE.Color() });

  // Ocean, then its glow.
  const oceanMat = new THREE.ShaderMaterial({ vertexShader: oceanVS, fragmentShader: oceanFS, uniforms: { uSun, uOceanDay: col('oceanDay'), uOceanNight: col('oceanNight'), uRim: col('rim') } });
  world.add(new THREE.Mesh(new THREE.SphereGeometry(0.995, 96, 64), oceanMat));
  const glow = new THREE.Mesh(new THREE.SphereGeometry(1.035, 64, 48), new THREE.ShaderMaterial({ vertexShader: oceanVS, fragmentShader: glowFS, uniforms: { uRim: col('rim') }, transparent: true, side: THREE.BackSide, depthWrite: false }));
  scene.add(glow);

  // Land dots.
  const n = landData.length / 2;
  const lp = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) {
    const v = vec(landData[i * 2] / 100, landData[i * 2 + 1] / 100, 1.001);
    lp.set([v.x, v.y, v.z], i * 3);
  }
  const landGeo = new THREE.BufferGeometry();
  landGeo.setAttribute('position', new THREE.BufferAttribute(lp, 3));
  const landMat = new THREE.ShaderMaterial({ vertexShader: dotVS, fragmentShader: dotFS, uniforms: { uSun, uPR, uSize: { value: 2.3 }, uDay: col('landDay'), uNight: col('landNight'), uDusk: col('dusk') }, transparent: true });
  world.add(new THREE.Points(landGeo, landMat));

  // Cities: lights at night, and the places whose prayer times make the rings.
  const cn = cityData.length / 3;
  const cities = [];
  const cpos = new Float32Array(cn * 3), cmag = new Float32Array(cn);
  for (let i = 0; i < cn; i++) {
    const lat = cityData[i * 3] / 100, lon = cityData[i * 3 + 1] / 100, m = cityData[i * 3 + 2] / 255;
    const v = vec(lat, lon, 1.002);
    cpos.set([v.x, v.y, v.z], i * 3);
    cmag[i] = Math.max(0, (m - 0.55) / 0.45);
    if (i < 900) cities.push({ lat, lon, v: vec(lat, lon, 1.003), prev: null });
  }
  const lightGeo = new THREE.BufferGeometry();
  lightGeo.setAttribute('position', new THREE.BufferAttribute(cpos, 3));
  lightGeo.setAttribute('aMag', new THREE.BufferAttribute(cmag, 1));
  const lightOn = { value: 1 };
  world.add(new THREE.Points(lightGeo, new THREE.ShaderMaterial({ vertexShader: lightVS, fragmentShader: glowDotFS, uniforms: { uSun, uPR, uOn: lightOn, uColor: col('lights') }, transparent: true, depthWrite: false, blending: THREE.NormalBlending })));

  // The five prayer lines, as rows of glowing dots recomputed every frame.
  const LINE_N = 260;
  const linePos = new Float32Array(5 * LINE_N * 3), lineA = new Float32Array(5 * LINE_N);
  const lineGeo = new THREE.BufferGeometry();
  lineGeo.setAttribute('position', new THREE.BufferAttribute(linePos, 3));
  lineGeo.setAttribute('aA', new THREE.BufferAttribute(lineA, 1));
  const linesOn = { value: 0 };
  world.add(new THREE.Points(lineGeo, new THREE.ShaderMaterial({ vertexShader: lineVS, fragmentShader: glowDotFS, uniforms: { uPR, uOn: linesOn, uSize: { value: 8.5 }, uColor: col('line') }, transparent: true, depthWrite: false })));
  const lineMid = Array.from({ length: 5 }, () => null);

  // Qibla arcs: from each city along the great circle to Makkah, lifted off the surface.
  const ARC_N = 90;
  const arcPos = new Float32Array(ARC_CITIES.length * ARC_N * 3), arcA = new Float32Array(ARC_CITIES.length * ARC_N), arcT = [];
  const mk = vec(...MAKKAH);
  ARC_CITIES.forEach(([la, lo], k) => {
    const a = vec(la, lo), ang = a.angleTo(mk);
    for (let i = 0; i < ARC_N; i++) {
      const t = i / (ARC_N - 1);
      const p = new THREE.Vector3().copy(a).multiplyScalar(Math.sin((1 - t) * ang)).add(mk.clone().multiplyScalar(Math.sin(t * ang))).divideScalar(Math.sin(ang) || 1);
      p.normalize().multiplyScalar(1.004 + Math.sin(Math.PI * t) * ang * 0.12);
      arcPos.set([p.x, p.y, p.z], (k * ARC_N + i) * 3);
      arcT.push(t);
    }
  });
  const arcGeo = new THREE.BufferGeometry();
  arcGeo.setAttribute('position', new THREE.BufferAttribute(arcPos, 3));
  arcGeo.setAttribute('aA', new THREE.BufferAttribute(arcA, 1));
  const arcsOn = { value: 1 };
  world.add(new THREE.Points(arcGeo, new THREE.ShaderMaterial({ vertexShader: lineVS, fragmentShader: glowDotFS, uniforms: { uPR, uOn: arcsOn, uSize: { value: 6 }, uColor: col('arc') }, transparent: true, depthWrite: false })));
  // Makkah, a steady light.
  const mkGeo = new THREE.BufferGeometry();
  mkGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array([mk.x * 1.006, mk.y * 1.006, mk.z * 1.006]), 3));
  mkGeo.setAttribute('aA', new THREE.BufferAttribute(new Float32Array([1]), 1));
  const mkOn = { value: 0 };
  world.add(new THREE.Points(mkGeo, new THREE.ShaderMaterial({ vertexShader: lineVS, fragmentShader: glowDotFS, uniforms: { uPR, uOn: mkOn, uSize: { value: 34 }, uColor: col('arc') }, transparent: true, depthWrite: false })));

  // Rings: one rises where a prayer time arrives.
  const RING_N = 260;
  const ringPos = new Float32Array(RING_N * 3), ringBorn = new Float32Array(RING_N).fill(-99);
  const ringGeo = new THREE.BufferGeometry();
  ringGeo.setAttribute('position', new THREE.BufferAttribute(ringPos, 3));
  ringGeo.setAttribute('aBorn', new THREE.BufferAttribute(ringBorn, 1));
  const uNow = { value: 0 };
  const ringMat = new THREE.ShaderMaterial({ vertexShader: ringVS, fragmentShader: ringFS, uniforms: { uNow, uPR, uColor: col('ring') }, transparent: true, depthWrite: false });
  world.add(new THREE.Points(ringGeo, ringMat));
  // A small gold mosque stands in each ring while it rises: the Adhan of that city.
  const mc = document.createElement('canvas');
  mc.width = mc.height = 128;
  const g = mc.getContext('2d');
  g.fillStyle = '#fff';
  g.beginPath(); g.ellipse(64, 74, 30, 32, 0, Math.PI, 0); g.fill();          // dome
  g.fillRect(30, 72, 68, 30);                                                  // hall
  g.fillRect(14, 40, 9, 62); g.fillRect(105, 40, 9, 62);                       // minarets
  g.beginPath(); g.moveTo(12, 42); g.lineTo(18.5, 26); g.lineTo(25, 42); g.fill();
  g.beginPath(); g.moveTo(103, 42); g.lineTo(109.5, 26); g.lineTo(116, 42); g.fill();
  g.fillRect(62, 30, 4, 14);                                                   // finial
  const mosqueTex = new THREE.CanvasTexture(mc);
  const mosqueMat = new THREE.ShaderMaterial({
    vertexShader: ringVS.replace('gl_PointSize = (4.0 + 44.0 * e) * uPR * (3.2 / -mv.z);', 'gl_PointSize = 15.0 * uPR * (3.2 / -mv.z) * (0.6 + 0.4 * e);'),
    fragmentShader: `uniform vec3 uColor; uniform sampler2D uTex; varying float vAge;
      void main() { if (vAge < 0.0 || vAge > 1.0) discard; vec4 t = texture2D(uTex, vec2(gl_PointCoord.x, 1.0 - gl_PointCoord.y));
        gl_FragColor = vec4(uColor, t.a * pow(1.0 - vAge, 0.7)); }`,
    uniforms: { uNow, uPR, uColor: col('ring'), uTex: { value: mosqueTex } }, transparent: true, depthWrite: false });
  world.add(new THREE.Points(ringGeo, mosqueMat));
  // Where the Adhan is being called on the screen (for the sound's direction): recent rings, by their x.
  const heard = [];
  let ringNext = 0;
  const spawn = (v, now) => {
    ringPos.set([v.x, v.y, v.z], ringNext * 3);
    ringBorn[ringNext] = now;
    ringNext = (ringNext + 1) % RING_N;
    ringGeo.attributes.position.needsUpdate = true;
    ringGeo.attributes.aBorn.needsUpdate = true;
  };

  // Stars far away, only on a dark ground.
  const sp = new Float32Array(1500 * 3);
  for (let i = 0; i < 1500; i++) { const v = new THREE.Vector3().randomDirection().multiplyScalar(60); sp.set([v.x, v.y, v.z], i * 3); }
  const starGeo = new THREE.BufferGeometry();
  starGeo.setAttribute('position', new THREE.BufferAttribute(sp, 3));
  const starOn = { value: 0 };
  const stars = new THREE.Points(starGeo, new THREE.PointsMaterial({ size: 1.6 * pr, sizeAttenuation: false, color: 0xffffff, transparent: true, opacity: 0.5, depthWrite: false }));
  scene.add(stars);

  const day = 287; // 14 October
  let last = null;
  const state = { lon: 28, lat: 14, zoom: 1, lines: 0, arcs: 0, hours: 14.5, shift: 0, lift: 0, now: 0, follow: null, followMix: 0 };
  const tmp = new THREE.Vector3();

  function setTheme(c) {
    for (const [k, v] of Object.entries(c)) col(k).value.set(v);
    starOn.value = c.stars ? 1 : 0;
    stars.material.opacity = c.stars ? 0.55 : 0;
  }

  function resize() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }

  // Where a point of the globe is on the screen, and whether it faces the viewer.
  function project(v) {
    tmp.copy(v).applyMatrix4(world.matrixWorld);
    const facing = tmp.clone().sub(world.position).normalize().dot(camera.position.clone().sub(world.position).normalize());
    tmp.project(camera);
    return { x: (tmp.x + 1) / 2 * canvas.clientWidth, y: (1 - tmp.y) / 2 * canvas.clientHeight, facing };
  }

  function update(s) {
    Object.assign(state, s);
    const sun = sunAt(day, state.hours);
    uSun.value.copy(vec(sun.decl, sun.lon));
    uNow.value = state.now;
    // `follow` keeps the view on the evening side of the sun (the dusk line stays in sight as time passes).
    const lon = state.follow == null ? state.lon : state.lon + (((sun.lon + state.follow - state.lon + 540) % 360) - 180) * state.followMix;
    world.rotation.set(state.lat * D, -lon * D, 0, 'XYZ');
    world.position.set(state.shift, state.lift, 0);
    glow.position.copy(world.position);
    camera.position.set(0, 0, 1 + 4.4 / state.zoom);
    linesOn.value = state.lines;
    arcsOn.value = state.arcs > 0 ? 1 : 0;
    mkOn.value = Math.min(1, state.arcs * 2);
    lightOn.value = 1;

    // The prayer lines: for each latitude, the longitude where the sun stands at the prayer's angle.
    const sd = sun.decl * D;
    const kinds = [{ alt: -18, side: -1 }, { alt: null, side: 0 }, { asr: true, side: 1 }, { alt: -0.833, side: 1 }, { alt: -17, side: 1 }];
    kinds.forEach((k, j) => {
      let best = -2, bestV = null;
      for (let i = 0; i < LINE_N; i++) {
        const lat = -58 + (128 * i) / (LINE_N - 1), f = lat * D;
        let lon = null;
        if (k.side === 0) lon = sun.lon;
        else {
          const alt = k.asr ? Math.atan(1 / (1 + Math.tan(Math.abs(f - sd)))) : k.alt * D;
          const c = (Math.sin(alt) - Math.sin(f) * Math.sin(sd)) / (Math.cos(f) * Math.cos(sd));
          if (c >= -1 && c <= 1) lon = sun.lon + k.side * Math.acos(c) / D;
        }
        const o = (j * LINE_N + i);
        if (lon === null) { lineA[o] = 0; continue; }
        const v = vec(lat, lon, 1.006);
        linePos.set([v.x, v.y, v.z], o * 3);
        lineA[o] = 0.9;
        // The label sits where the line faces the viewer most, away from the poles.
        if (lat > -35 && lat < 45) {
          tmp.copy(v).applyEuler(world.rotation);
          if (tmp.z > best) { best = tmp.z; bestV = v; }
        }
      }
      lineMid[j] = bestV;
    });
    lineGeo.attributes.position.needsUpdate = true;
    lineGeo.attributes.aA.needsUpdate = true;

    // The arcs draw from the cities towards Makkah as `arcs` goes from 0 to 1.
    for (let i = 0; i < arcA.length; i++) {
      const t = arcT[i], k = Math.floor(i / ARC_N);
      const p = state.arcs * 1.25 - (k % 6) * 0.04;
      arcA[i] = t <= p ? 0.25 + 0.75 * Math.max(0, 1 - (p - t) * 4) : 0;
    }
    arcGeo.attributes.aA.needsUpdate = true;

    // Rings: compare each city's sun with the last frame's; a crossing of a prayer's angle starts a ring.
    if (last !== null && Math.abs(state.hours - last) < 1.5 && state.hours !== last) {
      let spawned = 0;
      for (const c of cities) {
        const f = c.lat * D;
        const sinAlt = c.v.clone().normalize().dot(uSun.value);
        const alt = Math.asin(sinAlt) / D;
        let h = ((c.lon - sun.lon + 540) % 360) - 180; // hour angle: below 0 morning, above 0 afternoon
        const asr = Math.atan(1 / (1 + Math.tan(Math.abs(f - sd)))) / D;
        if (c.prev && spawned < 14) {
          const [pa, ph] = c.prev;
          const hit = (pa < -18 && alt >= -18 && h < 0) || (ph < 0 && h >= 0 && ph > -90) || (pa > asr && alt <= asr && h > 0) || (pa > -0.833 && alt <= -0.833 && h > 0) || (pa > -17 && alt <= -17 && h > 0);
          if (hit) {
            tmp.copy(c.v).applyEuler(world.rotation);
            if (tmp.z > 0.05) { spawn(c.v, state.now); spawned++; heard.push([c.v, state.now]); }
          }
        }
        c.prev = [alt, h];
      }
    } else if (last === null || Math.abs(state.hours - last) >= 1.5) {
      for (const c of cities) {
        const alt = Math.asin(c.v.clone().normalize().dot(uSun.value)) / D;
        c.prev = [alt, ((c.lon - sun.lon + 540) % 360) - 180];
      }
    }
    last = state.hours;
    renderer.render(scene, camera);
  }

  // The sound's position: -1 (left) to 1 (right), and how much is being called now (0 to 1).
  function sound() {
    while (heard.length && state.now - heard[0][1] > 3) heard.shift();
    if (!heard.length) return { pan: 0, level: 0 };
    let x = 0;
    for (const [v] of heard) x += project(v).x;
    x /= heard.length;
    return { pan: Math.max(-1, Math.min(1, (x / canvas.clientWidth) * 2 - 1)), level: Math.min(1, heard.length / 6) };
  }

  function labels() {
    const out = lineMid.map((v) => (v ? project(v) : null));
    return { lines: out, makkah: project(mk) };
  }

  return { update, resize, setTheme, labels, sound, dispose: () => renderer.dispose() };
}
