// The app's short film, in Arabic or English, on every version of the landing page: a play button in the top bar,
// a small card in a corner of wide screens (until the page is scrolled or the card is closed), and a player that
// opens over the page. The wide cut plays on wide screens and the tall cut on phones held upright; the film's
// language follows the page and can be switched in the player. Nothing plays until the visitor asks for it.
const BASE = new URL('assets/film/', import.meta.url).href;
const T = {
  en: { button: 'Watch the video', short: 'Video', card: 'Watch the video', sub: 'A short film about the app', close: 'Close', ar: 'العربية', en: 'English', title: 'Near Mosque, the film' },
  ar: { button: 'شاهد الفيديو', short: 'فيديو', card: 'شاهد الفيديو', sub: 'فيلم قصير عن التطبيق', close: 'إغلاق', ar: 'العربية', en: 'English', title: 'فيلم «أقرب مسجد»' },
};
const PLAY = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M8 5.5v13a1 1 0 0 0 1.5.86l10.4-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5Z" fill="currentColor"/></svg>';
const store = { get: (k) => { try { return sessionStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { sessionStorage.setItem(k, v); } catch {} } };
const pageLang = () => (document.documentElement.lang === 'ar' ? 'ar' : 'en');
const cut = () => (innerHeight > innerWidth * 1.15 ? 'tall' : 'wide');
const src = (lang, c) => `${BASE}promo-${lang}-${c}.mp4`;
const poster = (lang, c) => `${BASE}poster-${lang}-${c}.jpg`;

// The button in the top bar, before the others.
const tools = document.querySelector('.bar .tools');
const button = document.createElement('button');
button.type = 'button';
button.className = 'film-btn' + (tools && tools.querySelector('.pill') ? ' pill' : '');
button.innerHTML = `${PLAY}<span class="long"></span><span class="short"></span>`;
if (tools) tools.prepend(button);

// The card in the corner.
const card = document.createElement('div');
card.className = 'film-card';
card.innerHTML = `<button type="button" class="film-open"><span class="film-thumb"><img alt="" width="640" height="360" decoding="async"><i>${PLAY}</i></span><span class="film-words"><b></b><small></small></span></button><button type="button" class="film-x"><svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg></button>`;
if (store.get('nm-film-card') !== 'closed') document.body.append(card);

// The player.
const dlg = document.createElement('dialog');
dlg.className = 'promo-player';
dlg.innerHTML = `<div class="film-frame"><video controls playsinline preload="none"></video></div><div class="film-row"><div class="film-langs" role="group"><button type="button" data-l="ar" lang="ar">العربية</button><button type="button" data-l="en" lang="en">English</button></div><button type="button" class="film-close"></button></div>`;
document.body.append(dlg);
const video = dlg.querySelector('video');
let lang = pageLang();

function words() {
  const t = T[pageLang()];
  button.querySelector('.long').textContent = t.button;
  button.querySelector('.short').textContent = t.short;
  button.setAttribute('aria-label', t.button);
  card.querySelector('img').src = poster(pageLang(), 'wide');
  card.querySelector('b').textContent = t.card;
  card.querySelector('small').textContent = t.sub;
  card.querySelector('.film-x').setAttribute('aria-label', t.close);
  dlg.setAttribute('aria-label', t.title);
  dlg.querySelector('.film-close').textContent = t.close;
  dlg.querySelector('.film-langs').setAttribute('aria-label', t.title);
  dlg.dir = pageLang() === 'ar' ? 'rtl' : 'ltr';
}
function load(l, play) {
  lang = l;
  const c = cut();
  dlg.dataset.cut = c;
  video.poster = poster(l, c);
  video.src = src(l, c);
  video.lang = l;
  dlg.querySelectorAll('.film-langs button').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.l === l)));
  if (play) video.play().catch(() => {});
}
function open() {
  // The landing page's Adhan sound stops, so the two do not play together.
  const sound = document.getElementById('sound');
  if (sound && sound.getAttribute('aria-pressed') === 'true') sound.click();
  words();
  if (typeof dlg.showModal === 'function') dlg.showModal(); else dlg.setAttribute('open', '');
  document.documentElement.classList.add('film-on');
  load(pageLang(), true);
}
function close() { video.pause(); if (dlg.open) dlg.close(); }
dlg.addEventListener('close', () => { video.pause(); video.removeAttribute('src'); video.load(); document.documentElement.classList.remove('film-on'); button.focus({ preventScroll: true }); });
dlg.addEventListener('click', (e) => { if (e.target === dlg) close(); });
dlg.querySelector('.film-close').addEventListener('click', close);
dlg.querySelectorAll('.film-langs button').forEach((b) => b.addEventListener('click', () => { if (b.dataset.l !== lang) load(b.dataset.l, true); }));
button.addEventListener('click', open);
card.querySelector('.film-open').addEventListener('click', open);
card.querySelector('.film-x').addEventListener('click', () => { card.remove(); store.set('nm-film-card', 'closed'); });

// The card steps away once the visitor scrolls on; the button in the bar stays.
let away = false;
addEventListener('scroll', () => { const a = scrollY > innerHeight * 0.5; if (a !== away) { away = a; card.classList.toggle('away', a); } }, { passive: true });
new MutationObserver(words).observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] });
words();
requestAnimationFrame(() => card.classList.add('in'));
