// The iPhone Duo panel shared by every version of the landing page: the app on iPhone Duo in its three poses
// (standing on the table, open, half folded), in the page's language. It fills each [data-duo] on the page and
// follows the page when its language changes.
const BASE = new URL('assets/', import.meta.url).href;
const T = {
  en: { eyebrow: 'iPhone Duo', title: 'Prayer times, the Qibla and the Adhan on iPhone Duo.', sub: 'Standing on the table, open, or half folded.', poses: ['On the table', 'Open', 'Half folded'] },
  ar: { eyebrow: 'iPhone Duo', title: 'مواقيت الصلاة والقبلة والأذان على iPhone Duo.', sub: 'على الطاولة، ومفتوحًا، ونصف مطوي.', poses: ['على الطاولة', 'مفتوحًا', 'نصف مطوي'] },
};
const POSES = [['1-table', 530, 294], ['2-open', 562, 451], ['3-half', 496, 382]];
const spots = [...document.querySelectorAll('[data-duo]')];
const pageLang = () => (document.documentElement.lang === 'ar' ? 'ar' : 'en');
let shown = null;

function render() {
  const lang = pageLang();
  if (lang === shown) return;
  shown = lang;
  const t = T[lang];
  spots.forEach((el, k) => {
    el.setAttribute('aria-labelledby', `duo-title-${k}`);
    el.innerHTML = `<div class="duo-card">
      <p class="duo-eyebrow">${t.eyebrow}</p>
      <h2 class="duo-title" id="duo-title-${k}">${t.title}</h2>
      <p class="duo-sub">${t.sub}</p>
      <div class="duo-poses">${POSES.map(([name, w, h], i) => `<figure style="--w:${w};--i:${i}"><img src="${BASE}${lang}/duo/${name}.webp" alt="${t.poses[i]}" width="${w}" height="${h}" loading="lazy" decoding="async"><figcaption>${t.poses[i]}</figcaption></figure>`).join('')}</div>
    </div>`;
  });
}

// The three poses rise into place when the panel comes into view.
const io = 'IntersectionObserver' in window ? new IntersectionObserver((list) => list.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: 0.25 }) : null;
spots.forEach((el) => { el.classList.add('duo'); if (io) io.observe(el); else el.classList.add('in'); });
new MutationObserver(render).observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] });
render();
