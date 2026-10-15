// The globe. Two looks: the real Earth (NASA Blue Marble by day, Black Marble city lights by night, the line
// between them where the sun is now) or gold dots for the land. On both, every one of the app's 13,090 built-in
// cities lights up when one of its five prayers comes, so the Adhan moves round the world as waves of light;
// Makkah shines brightest, then Madinah. For the Qibla, arcs run from cities everywhere into Makkah, light
// travelling along them, and close up the Kaaba sits inside rings of gold: the rows of people praying towards it.
//
// Angles: Fajr 18° and Isha 17° below the horizon (Muslim World League), Maghrib at sunset (0.833° below),
// Dhuhr when the sun crosses the meridian, Asr when a shadow equals its object's length plus the noon shadow.
import * as THREE from './vendor/three.module.min.js';

const D = Math.PI / 180;
export const MAKKAH = [21.4225, 39.8262];
const MADINAH = [24.4672, 39.6111];
// Cities whose Qibla arcs are drawn (latitude, longitude).
const ARC_CITIES = [[51.51, -0.13], [48.86, 2.35], [41.01, 28.98], [55.76, 37.62], [30.04, 31.24], [6.52, 3.38], [33.57, -7.59], [-1.29, 36.82],
  [-26.2, 28.05], [35.69, 51.39], [24.86, 67.0], [28.61, 77.21], [23.81, 90.41], [41.3, 69.24], [40.41, 49.87], [3.14, 101.69], [-6.21, 106.85],
  [52.52, 13.4], [40.42, -3.7], [14.72, -17.47], [15.55, 32.53], [2.05, 45.32], [36.75, 3.06], [33.89, 35.5], [33.31, 44.37], [12.97, 77.59], [43.24, 76.89],
  [40.71, -74.0], [43.65, -79.38], [-23.55, -46.63], [-33.87, 151.21], [35.68, 139.65], [14.6, 120.98], [1.35, 103.82], [55.79, 49.12], [12.0, 8.59]];
export const PRAYER_COLORS = ['#F6C9A2', '#FFE9A8', '#FFCB57', '#F7A84A', '#E9C98F']; // Fajr rose gold, Dhuhr pale gold, Asr gold, Maghrib amber, Isha champagne

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

const earthVS = `
  varying vec3 vN; varying vec2 vUv; varying vec3 vV;
  void main() { vN = normalize(position); vUv = uv; vV = normalize(normalMatrix * normal); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`;
const earthFS = `
  uniform sampler2D uDay; uniform sampler2D uNight; uniform vec3 uSun; uniform vec3 uRim; uniform float uDots; uniform vec3 uOceanDay; uniform vec3 uOceanNight;
  varying vec3 vN; varying vec2 vUv; varying vec3 vV;
  void main() {
    // vN is in the sphere's own frame (turned a quarter round), so compare with the sun turned the same way.
    float s = dot(vec3(-vN.z, vN.y, vN.x), uSun);
    float day = smoothstep(-0.08, 0.18, s);
    vec3 d = texture2D(uDay, vUv).rgb;
    vec3 n = texture2D(uNight, vUv).rgb;
    // Real Earth: the day image lit by the sun, the night image's city lights where it is dark, a warm band at dusk.
    float sunlit = clamp(s * 1.4 + 0.25, 0.0, 1.0);
    // The texture is read as linear light, and this colour goes to the screen as it is, so the day side is put back
    // in the photograph's own tones; without it the sunlit oceans came out nearly black.
    vec3 p = pow(d, vec3(1.0 / 2.2));
    vec3 lit = p * (0.42 + 0.68 * sunlit);
    // The sunlit sea brighter and bluer, as the air makes it look from space; a light haze over the land.
    float sea = smoothstep(0.03, 0.12, p.b - max(p.r, p.g));
    lit += vec3(0.05, 0.15, 0.32) * sunlit * (0.25 + 0.75 * sea);
    vec3 dark = d * 0.035 + pow(n, vec3(1.7)) * vec3(1.25, 1.02, 0.7) * 1.5;
    vec3 col = mix(dark, lit, day);
    col += vec3(0.95, 0.55, 0.25) * exp(-pow(s / 0.07, 2.0)) * 0.22;
    // Dots look: a plain ocean under the land dots.
    col = mix(col, mix(uOceanNight, uOceanDay, day), uDots);
    float rim = pow(1.0 - max(0.0, vV.z), 3.0);
    col = mix(col, uRim, rim * 0.55);
    gl_FragColor = vec4(col, 1.0);
  }`;
const glowVS = `varying vec3 vV; void main() { vV = normalize(normalMatrix * normal); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`;
const glowFS = `uniform vec3 uRim; uniform float uA; varying vec3 vV; void main() { float rim = pow(max(0.0, 1.0 - abs(vV.z)), 2.6); gl_FragColor = vec4(uRim, rim * uA); }`;
const dotVS = `
  uniform vec3 uSun; uniform float uSize; uniform float uPR; uniform float uScale;
  varying float vDay;
  void main() { vDay = dot(normalize(position), uSun); vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_Position = projectionMatrix * mv; gl_PointSize = uSize * uPR * uScale / -mv.z; }`;
const dotFS = `
  uniform vec3 uDay; uniform vec3 uNight; uniform vec3 uDusk; uniform float uOn; varying float vDay;
  void main() {
    float d = length(gl_PointCoord - 0.5); if (d > 0.5 || uOn < 0.01) discard;
    vec3 col = mix(uNight, uDay, smoothstep(-0.1, 0.12, vDay));
    col = mix(col, uDusk, exp(-pow(vDay / 0.07, 2.0)) * 0.85);
    gl_FragColor = vec4(col, smoothstep(0.5, 0.3, d) * uOn);
  }`;
// The waves: each city's light from its sun, worked out on the graphics card.
const waveVS = `
  attribute vec3 aCity; // latitude, longitude (degrees), size 0..1
  uniform float uDecl; uniform float uSunLon; uniform float uPR; uniform float uScale; uniform vec3 uColors[5]; uniform float uOn;
  varying vec3 vCol; varying float vA;
  float band(float x, float w) { return x > 0.0 && x < w ? pow(1.0 - x / w, 0.8) : 0.0; }
  void main() {
    float f = radians(aCity.x), dcl = uDecl;
    float H = mod(aCity.y - uSunLon + 540.0, 360.0) - 180.0;   // hour angle: below 0 morning
    float alt = degrees(asin(sin(f) * sin(dcl) + cos(f) * cos(dcl) * cos(radians(H))));
    float asr = degrees(atan(1.0 / (1.0 + tan(abs(f - dcl)))));
    float W = 5.5;
    float i0 = H < 0.0 ? band(alt + 18.0, W) : 0.0;              // Fajr: the sun climbs past 18° below
    float i1 = band(H, W * 1.3);                                   // Dhuhr: just past noon
    float i2 = H > 0.0 ? band(asr - alt, W) : 0.0;                 // Asr
    float i3 = H > 0.0 ? band(-0.833 - alt, W) : 0.0;              // Maghrib
    float i4 = H > 0.0 ? band(-17.0 - alt, W) : 0.0;               // Isha
    float m = max(max(max(i0, i1), max(i2, i3)), i4);
    vCol = m == i0 ? uColors[0] : m == i1 ? uColors[1] : m == i2 ? uColors[2] : m == i3 ? uColors[3] : uColors[4];
    vA = min(1.0, m * 1.3) * uOn * (0.7 + 0.3 * aCity.z);
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = m > 0.0 ? (4.0 + 12.0 * m * (0.6 + 0.6 * aCity.z)) * uPR * uScale / -mv.z : 0.0;
  }`;
const waveFS = `
  varying vec3 vCol; varying float vA;
  void main() { float d = length(gl_PointCoord - 0.5) * 2.0; if (d > 1.0 || vA < 0.005) discard; float core = smoothstep(0.42, 0.0, d);
    gl_FragColor = vec4(mix(vCol * 0.92, vec3(1.0, 0.97, 0.88), core * 0.7), min(1.0, vA * (pow(1.0 - d, 1.2) * 1.3 + core))); }`;
const glowDotFS = `
  uniform vec3 uColor; varying float vA;
  void main() { float d = length(gl_PointCoord - 0.5) * 2.0; if (d > 1.0) discard; gl_FragColor = vec4(uColor, vA * pow(1.0 - d, 1.6)); }`;
// Arcs: light runs along each one towards Makkah; the first arc (from the visitor's city) is the brightest.
const arcVS = `
  attribute float aT; attribute float aK; uniform float uPR; uniform float uProg; uniform float uTime; uniform float uScale;
  varying float vA;
  void main() {
    float shown = aK < 0.5 ? step(aT, uProg * 2.2) : step(aT, (uProg - 0.25) * 1.5 - mod(aK, 7.0) * 0.05);
    float head = fract(uTime * 0.22 + aK * 0.137);
    float glow = exp(-pow((aT - head) * 14.0, 2.0));
    float mine = aK < 0.5 ? 1.0 : 0.0;
    vA = shown * (0.28 + 0.9 * glow + mine * 0.5);
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = (4.5 + 6.0 * glow + mine * 3.5) * uPR * uScale / -mv.z;
  }`;
const pulseVS = `
  attribute float aSize; uniform float uPR; uniform float uTime; uniform float uOn;
  varying float vA;
  void main() { vA = uOn; vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_Position = projectionMatrix * mv; gl_PointSize = aSize * uPR * (1.0 + 0.12 * sin(uTime * 2.2)); }`;
const pulseFS = `
  uniform vec3 uColor; varying float vA;
  void main() { float d = length(gl_PointCoord - 0.5) * 2.0; if (d > 1.0) discard; float core = smoothstep(0.22, 0.0, d);
    gl_FragColor = vec4(mix(uColor, vec3(1.0), core), vA * (pow(1.0 - d, 2.4) + core)); }`;
const ringVS = `
  attribute float aBorn; uniform float uNow; uniform float uPR; uniform float uScale;
  varying float vAge;
  void main() { vAge = (uNow - aBorn) / 2.6; vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_Position = projectionMatrix * mv;
    float e = 1.0 - pow(1.0 - clamp(vAge, 0.0, 1.0), 3.0); gl_PointSize = (4.0 + 50.0 * e) * uPR * uScale / -mv.z; }`;
const ringFS = `
  uniform vec3 uColor; varying float vAge;
  void main() { if (vAge < 0.0 || vAge > 1.0) discard; float d = length(gl_PointCoord - 0.5) * 2.0;
    float ring = smoothstep(0.66, 0.82, d) * smoothstep(1.0, 0.86, d); gl_FragColor = vec4(uColor, ring * (1.0 - vAge) * 0.9); }`;
// Close up at Makkah: rows of light round the Kaaba.
const rowVS = `
  attribute float aR; uniform float uPR; uniform float uTime; uniform float uOn;
  varying float vA;
  void main() { vA = uOn * (0.7 + 0.3 * sin(uTime * 1.6 - aR * 0.45)); vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv; gl_PointSize = 4.0 * uPR; }`;
// The ground round the Kaaba: white marble (the mataf) in the middle, then the dark courtyard under the rows.
const groundFS = `
  uniform float uOn; uniform vec3 uColor; varying vec2 vUv;
  void main() {
    float d = length(vUv - 0.5) * 2.0;
    vec3 col = mix(vec3(0.91, 0.88, 0.81), uColor, smoothstep(0.034, 0.05, d));
    col += vec3(1.0, 0.75, 0.35) * 0.14 * smoothstep(0.32, 0.0, d);
    gl_FragColor = vec4(col, uOn * smoothstep(1.0, 0.6, d));
  }`;

export async function createGlobe(canvas, base, places = []) {
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'high-performance', logarithmicDepthBuffer: true });
  } catch (e) {
    return null;
  }
  const pr = Math.min(2, window.devicePixelRatio || 1);
  renderer.setPixelRatio(pr);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(32, 1, 0.001, 200);
  camera.position.set(0, 0, 5.4);
  const world = new THREE.Group();
  world.rotation.order = 'XYZ';
  scene.add(world);

  const loader = new THREE.TextureLoader();
  const load = (p) => new Promise((res) => loader.load(base + p, (t) => { t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = renderer.capabilities.getMaxAnisotropy(); res(t); }, undefined, () => res(null)));
  const [landData, cityData, dayTex, nightTex] = await Promise.all([fetch(base + 'assets/land.json').then((r) => r.json()), fetch(base + 'assets/cities.json').then((r) => r.json()),
    load('assets/earth-day.jpg'), load('assets/earth-night.jpg')]);
  const uSun = { value: new THREE.Vector3(1, 0, 0) };
  const uPR = { value: pr };
  const uScale = { value: 3.2 };
  const uTime = { value: 0 };
  const colors = {};
  const col = (k) => (colors[k] = colors[k] || { value: new THREE.Color() });
  const uDots = { value: 0 };

  // The Earth. Three.js maps the image's left edge (180° W) to the sphere's -x; turned a quarter so 0° faces +z.
  const earthMat = new THREE.ShaderMaterial({ vertexShader: earthVS, fragmentShader: earthFS,
    uniforms: { uDay: { value: dayTex }, uNight: { value: nightTex }, uSun, uRim: col('rim'), uDots, uOceanDay: col('oceanDay'), uOceanNight: col('oceanNight') } });
  const earth = new THREE.Mesh(new THREE.SphereGeometry(1, 160, 110), earthMat);
  earth.rotation.y = -Math.PI / 2;
  world.add(earth);
  const glowA = { value: 0.35 };
  const glow = new THREE.Mesh(new THREE.SphereGeometry(1.04, 64, 48), new THREE.ShaderMaterial({ vertexShader: glowVS, fragmentShader: glowFS, uniforms: { uRim: col('rim'), uA: glowA }, transparent: true, side: THREE.BackSide, depthWrite: false }));
  scene.add(glow);

  // Land dots (the dots look).
  const n = landData.length / 2;
  const lp = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) { const v = vec(landData[i * 2] / 100, landData[i * 2 + 1] / 100, 1.001); lp.set([v.x, v.y, v.z], i * 3); }
  const landGeo = new THREE.BufferGeometry();
  landGeo.setAttribute('position', new THREE.BufferAttribute(lp, 3));
  const landOn = { value: 0 };
  world.add(new THREE.Points(landGeo, new THREE.ShaderMaterial({ vertexShader: dotVS, fragmentShader: dotFS, uniforms: { uSun, uPR, uScale, uSize: { value: 2.3 }, uDay: col('landDay'), uNight: col('landNight'), uDusk: col('dusk'), uOn: landOn }, transparent: true })));

  // Every city, for the waves.
  const cn = cityData.length / 3;
  const cpos = new Float32Array(cn * 3), cattr = new Float32Array(cn * 3);
  for (let i = 0; i < cn; i++) {
    const lat = cityData[i * 3] / 100, lon = cityData[i * 3 + 1] / 100, m = cityData[i * 3 + 2] / 255;
    const v = vec(lat, lon, 1.003);
    cpos.set([v.x, v.y, v.z], i * 3);
    cattr.set([lat, lon, Math.max(0, (m - 0.55) / 0.45)], i * 3);
  }
  const waveGeo = new THREE.BufferGeometry();
  waveGeo.setAttribute('position', new THREE.BufferAttribute(cpos, 3));
  waveGeo.setAttribute('aCity', new THREE.BufferAttribute(cattr, 3));
  const uDecl = { value: 0 }, uSunLon = { value: 0 }, wavesOn = { value: 1 };
  const uColors = { value: PRAYER_COLORS.map((c) => new THREE.Color(c)) };
  world.add(new THREE.Points(waveGeo, new THREE.ShaderMaterial({ vertexShader: waveVS, fragmentShader: waveFS, uniforms: { uDecl, uSunLon, uPR, uScale, uColors, uOn: wavesOn }, transparent: true, depthWrite: false })));

  // Makkah and Madinah, always shining.
  const mk = vec(...MAKKAH), md = vec(...MADINAH);
  const holyGeo = new THREE.BufferGeometry();
  holyGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array([...vec(...MAKKAH, 1.004).toArray(), ...vec(...MADINAH, 1.004).toArray()]), 3));
  holyGeo.setAttribute('aSize', new THREE.BufferAttribute(new Float32Array([96, 26]), 1));
  const holyOn = { value: 1 };
  world.add(new THREE.Points(holyGeo, new THREE.ShaderMaterial({ vertexShader: pulseVS, fragmentShader: pulseFS, uniforms: { uPR, uTime, uOn: holyOn, uColor: col('holy') }, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending })));

  // Qibla arcs.
  const ARC_N = 110;
  const arcsProg = { value: 0 };
  const arcPoints = new THREE.Points(new THREE.BufferGeometry(), new THREE.ShaderMaterial({ vertexShader: arcVS, fragmentShader: `uniform vec3 uColor; varying float vA;
    void main() { float d = length(gl_PointCoord - 0.5) * 2.0; if (d > 1.0) discard; float core = smoothstep(0.45, 0.0, d);
      gl_FragColor = vec4(mix(uColor, vec3(1.0, 0.96, 0.85), core * 0.6), min(1.0, vA * (pow(1.0 - d, 1.3) * 1.2 + core * 0.6))); }`, uniforms: { uPR, uProg: arcsProg, uTime, uScale, uColor: col('arc') }, transparent: true, depthWrite: false }));
  world.add(arcPoints);
  function buildArcs(mine) {
    const list = mine ? [mine, ...ARC_CITIES] : ARC_CITIES;
    const pos = new Float32Array(list.length * ARC_N * 3), at = new Float32Array(list.length * ARC_N), ak = new Float32Array(list.length * ARC_N);
    list.forEach(([la, lo], k) => {
      const a = vec(la, lo), ang = Math.max(1e-4, a.angleTo(mk));
      for (let i = 0; i < ARC_N; i++) {
        const t = i / (ARC_N - 1);
        const p = a.clone().multiplyScalar(Math.sin((1 - t) * ang)).add(mk.clone().multiplyScalar(Math.sin(t * ang))).divideScalar(Math.sin(ang));
        p.normalize().multiplyScalar(1.004 + Math.sin(Math.PI * t) * ang * 0.11);
        pos.set([p.x, p.y, p.z], (k * ARC_N + i) * 3);
        at[k * ARC_N + i] = t;
        ak[k * ARC_N + i] = mine && k === 0 ? 0 : k + 3;
      }
    });
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    g.setAttribute('aT', new THREE.BufferAttribute(at, 1));
    g.setAttribute('aK', new THREE.BufferAttribute(ak, 1));
    arcPoints.geometry.dispose();
    arcPoints.geometry = g;
  }
  buildArcs(null);

  // Rings and cards: the named cities (places.js), checked here frame by frame.
  const RING_N = 64;
  const ringPos = new Float32Array(RING_N * 3), ringBorn = new Float32Array(RING_N).fill(-99);
  const ringGeo = new THREE.BufferGeometry();
  ringGeo.setAttribute('position', new THREE.BufferAttribute(ringPos, 3));
  ringGeo.setAttribute('aBorn', new THREE.BufferAttribute(ringBorn, 1));
  const uNow = { value: 0 };
  world.add(new THREE.Points(ringGeo, new THREE.ShaderMaterial({ vertexShader: ringVS, fragmentShader: ringFS, uniforms: { uNow, uPR, uScale, uColor: col('ring') }, transparent: true, depthWrite: false })));
  let ringNext = 0;
  const spawn = (v, now) => {
    ringPos.set([v.x, v.y, v.z], ringNext * 3); ringBorn[ringNext] = now; ringNext = (ringNext + 1) % RING_N;
    ringGeo.attributes.position.needsUpdate = true; ringGeo.attributes.aBorn.needsUpdate = true;
  };
  const named = places.map(([, , lat, lon], k) => ({ lat, lon, v: vec(lat, lon, 1.003), prev: null, place: k }));
  const events = [], heard = [];

  // Close up at Makkah: a dark ground, the Kaaba, rows of light round it.
  const up = mk.clone().normalize();
  const east = new THREE.Vector3(0, 1, 0).cross(up).normalize();
  const north = up.clone().cross(east).normalize();
  const haram = new THREE.Group();
  haram.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(east, north, up));
  haram.position.copy(up.clone().multiplyScalar(1.00002));
  world.add(haram);
  const haramOn = { value: 0 };
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(0.0034, 0.0034), new THREE.ShaderMaterial({ vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }', fragmentShader: groundFS, uniforms: { uOn: haramOn, uColor: col('ground') }, transparent: true, depthWrite: false }));
  haram.add(ground);
  const rows = [], rr = [];
  for (let r = 0; r < 40; r++) {
    const rad = 0.00006 + r * 0.0000068;
    const count = Math.floor((2 * Math.PI * rad) / 0.0000036);
    for (let i = 0; i < count; i++) { const a = (i / count) * Math.PI * 2 + r * 0.37; rows.push(Math.cos(a) * rad, Math.sin(a) * rad, 0.000001); rr.push(r); }
  }
  const rowGeo = new THREE.BufferGeometry();
  rowGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(rows), 3));
  rowGeo.setAttribute('aR', new THREE.BufferAttribute(new Float32Array(rr), 1));
  haram.add(new THREE.Points(rowGeo, new THREE.ShaderMaterial({ vertexShader: rowVS, fragmentShader: glowDotFS, uniforms: { uPR, uTime, uOn: haramOn, uColor: col('ring') }, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending })));
  const kS = 0.000032;
  const kMat = new THREE.MeshBasicMaterial({ color: 0x0b0b0d, transparent: true, opacity: 0 });
  const bandMat = new THREE.MeshBasicMaterial({ color: 0xd7ae4b, transparent: true, opacity: 0 });
  const kaaba = new THREE.Group();
  const kBox = new THREE.Mesh(new THREE.BoxGeometry(kS, kS * 0.82, kS * 1.1), kMat);
  kBox.position.z = kS * 0.55;
  const band = new THREE.Mesh(new THREE.BoxGeometry(kS * 1.03, kS * 0.85, kS * 0.12), bandMat);
  band.position.z = kS * 0.85;
  kaaba.add(kBox, band);
  kaaba.rotation.z = 0.5;
  haram.add(kaaba);

  // Stars far away, only on a dark ground.
  const sp = new Float32Array(1800 * 3);
  for (let i = 0; i < 1800; i++) { const v = new THREE.Vector3().randomDirection().multiplyScalar(60); sp.set([v.x, v.y, v.z], i * 3); }
  const starGeo = new THREE.BufferGeometry();
  starGeo.setAttribute('position', new THREE.BufferAttribute(sp, 3));
  const stars = new THREE.Points(starGeo, new THREE.PointsMaterial({ size: 1.5 * pr, sizeAttenuation: false, color: 0xffffff, transparent: true, opacity: 0.5, depthWrite: false }));
  scene.add(stars);

  let day = 287;
  let last = null, lastHoly = 0;
  const state = { lon: 28, lat: 14, zoom: 1, lines: 0, arcs: 0, haram: 0, hours: 14.5, shift: 0, lift: 0, now: 0 };
  const tmp = new THREE.Vector3();
  const lineMid = Array.from({ length: 5 }, () => null);

  function setTheme(c) {
    for (const [k, v] of Object.entries(c)) if (typeof v === 'string') col(k).value.set(v);
    stars.material.opacity = c.stars ? 0.55 : 0;
    glowA.value = c.stars ? 0.45 : 0.3;
  }
  function setStyle(s) {
    uDots.value = s === 'dots' ? 1 : 0;
    landOn.value = s === 'dots' ? 1 : 0;
  }
  function setYou(lat, lon) { buildArcs([lat, lon]); }

  function resize() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }

  function project(v) {
    tmp.copy(v).applyMatrix4(world.matrixWorld);
    const facing = tmp.clone().sub(world.position).normalize().dot(camera.position.clone().sub(world.position).normalize());
    tmp.project(camera);
    return { x: (tmp.x + 1) / 2 * canvas.clientWidth, y: (1 - tmp.y) / 2 * canvas.clientHeight, facing };
  }

  function update(s) {
    Object.assign(state, s);
    if (state.day) day = state.day;
    const sun = sunAt(day, state.hours);
    uSun.value.copy(vec(sun.decl, sun.lon));
    uDecl.value = sun.decl * D;
    uSunLon.value = sun.lon;
    uNow.value = state.now;
    uTime.value = state.now;
    world.rotation.set(state.lat * D, -state.lon * D, 0, 'XYZ');
    world.position.set(state.shift, state.lift, 0);
    glow.position.copy(world.position);
    const dist = 1 + 4.4 / state.zoom;
    camera.position.set(0, 0, dist);
    camera.near = Math.max(0.000002, (dist - 1) * 0.2);
    camera.updateProjectionMatrix();
    // Point sizes follow the window's height; close to the ground they stop growing.
    // Points keep the same size on screen at any distance (so close up they do not swell into the view).
    uScale.value = 3.2 * (canvas.clientHeight / 900) * Math.min(1, (dist - 1) / 4.4);
    arcsProg.value = state.arcs;
    haramOn.value = state.haram;
    holyOn.value = 1 - Math.max(state.haram * 0.92, state.veil || 0);
    kMat.opacity = state.haram; bandMat.opacity = state.haram;
    // The real photographs of Makkah cover the globe up close (`veil`, 0..1).
    wavesOn.value = 1 - Math.max(state.haram * 0.85, state.veil || 0);

    // Where to put each prayer's name: on its line where it faces the viewer most.
    const sd = sun.decl * D;
    [{ alt: -18, side: -1 }, { side: 0 }, { asr: true, side: 1 }, { alt: -0.833, side: 1 }, { alt: -17, side: 1 }].forEach((k, j) => {
      let best = -2, bestV = null;
      for (let lat = -30; lat <= 40; lat += 2) {
        const f = lat * D;
        let lon = null;
        if (k.side === 0) lon = sun.lon;
        else {
          const alt = k.asr ? Math.atan(1 / (1 + Math.tan(Math.abs(f - sd)))) : k.alt * D;
          const c = (Math.sin(alt) - Math.sin(f) * Math.sin(sd)) / (Math.cos(f) * Math.cos(sd));
          if (c >= -1 && c <= 1) lon = sun.lon + k.side * Math.acos(c) / D;
        }
        if (lon === null) continue;
        const v = vec(lat, lon, 1.006);
        tmp.copy(v).applyEuler(world.rotation);
        if (tmp.z > best) { best = tmp.z; bestV = v; }
      }
      lineMid[j] = bestV;
    });

    // The named cities: crossing a prayer's angle since the last frame starts a ring and a card.
    if (last !== null && Math.abs(state.hours - last) < 1.5 && state.hours !== last) {
      for (const c of named) {
        const f = c.lat * D;
        const alt = Math.asin(c.v.clone().normalize().dot(uSun.value)) / D;
        const h = ((c.lon - sun.lon + 540) % 360) - 180;
        const asr = Math.atan(1 / (1 + Math.tan(Math.abs(f - sd)))) / D;
        if (c.prev) {
          const [pa, ph] = c.prev;
          const kind = pa < -18 && alt >= -18 && h < 0 ? 0 : ph < 0 && h >= 0 && ph > -90 ? 1 : pa > asr && alt <= asr && h > 0 ? 2
            : pa > -0.833 && alt <= -0.833 && h > 0 ? 3 : pa > -17 && alt <= -17 && h > 0 ? 4 : -1;
          if (kind >= 0) {
            tmp.copy(c.v).applyEuler(world.rotation);
            if (tmp.z > 0.05) { spawn(c.v, state.now); heard.push([c.v, state.now]); if (tmp.z > 0.3) events.push({ place: c.place, kind, hours: state.hours, v: c.v }); }
          }
        }
        c.prev = [alt, h];
      }
    } else {
      for (const c of named) c.prev = [Math.asin(c.v.clone().normalize().dot(uSun.value)) / D, ((c.lon - sun.lon + 540) % 360) - 180];
    }
    if (state.now - lastHoly > 1.3) { spawn(vec(...MAKKAH, 1.004), state.now); lastHoly = state.now; }
    last = state.hours;
    renderer.render(scene, camera);
  }

  function sound() {
    while (heard.length && state.now - heard[0][1] > 4) heard.shift();
    if (!heard.length) return { pan: 0, level: 0 };
    let x = 0;
    for (const [v] of heard) x += project(v).x;
    x /= heard.length;
    return { pan: Math.max(-1, Math.min(1, (x / canvas.clientWidth) * 2 - 1)), level: Math.min(1, heard.length / 4) };
  }
  function adhans() { return events.splice(0); }
  function labels() { return { lines: lineMid.map((v) => (v ? project(v) : null)), makkah: project(mk), madinah: project(md) }; }

  return { update, resize, setTheme, setStyle, setYou, labels, sound, adhans, project, dispose: () => renderer.dispose() };
}
