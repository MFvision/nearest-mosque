// The landing page's markup, from story.js, in one language. The browser (app.js) and the build
// (tools/site/prerender.mjs, which writes 5/index.html and 5/ar/index.html) both use it, so the words search engines
// read are the words visitors see. No DOM here: plain strings only.
import { story, links } from './story.js';

export const WEIGHTS = { world: 1.6, kaaba: 2.1, sky: 1.9, mosque: 1.4, ask: 1.5, adhan: 1.4, devices: 1.5, privacy: 1.3, get: 1.7 };
export const GLOBE_CH = new Set(['world', 'get']);
// The chapters that show the legend at the bottom of the screen (on phones their words stay above it).
export const HUD_CH = new Set(['world', 'get']);
export const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

export const ICONS = {
  appStore: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm6.2 4.2-4.6 8h2.1l.8-1.4h4.9l-1-1.8H10.6l2.5-4.4-1.9-.4Zm2.6 3.2-1 1.7 2.2 3.9h2.1l-3.3-5.6Z"/></svg>',
  googlePlay: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 2.8v18.4c0 .6.6.9 1.1.6l15.4-8.9c.5-.3.5-1 0-1.3L5.1 2.2C4.6 1.9 4 2.2 4 2.8Z"/></svg>',
  // Windows and Linux: plain shapes (a window, a terminal), not the systems' logos.
  windows: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3h8.5v8.5H3zM12.5 3H21v8.5h-8.5zM3 12.5h8.5V21H3zM12.5 12.5H21V21h-8.5z"/></svg>',
  linux: '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill-rule="evenodd" d="M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm2.3 4.4L9.9 12l-3.6 3.6L7.7 17l5-5-5-5ZM13 15.2V17h5v-1.8Z"/></svg>',
};

// A title's words, each in its own span (they come in one by one); [[…]] are highlighted in gold.
export function words(str) {
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
export const plain = (str) => str.replace(/\[\[|\]\]/g, '');

// The Windows and Linux buttons and the .deb are files on GitHub, so they download in place; pages open in a new tab.
const FILES = new Set(['windows', 'linux', 'linuxDeb']);
const target = (k) => (FILES.has(k) ? '' : ' target="_blank"');

// Everything that depends on the language: lang ('en' or 'ar'), base (the site's root, ending in '/'), and the
// page's own choices: getHref (where "Get the app" leads), night (Makkah at night).
export function markup(lang, base, { getHref = '#get', night = false } = {}) {
  const t = story[lang];
  const img = (path, alt = '', cls = '') => `<img src="${base}assets/${lang}/${path}.webp" alt="${esc(alt)}" loading="lazy" decoding="async"${cls ? ` class="${cls}"` : ''}>`;
  const getTarget = getHref === '#get' ? '' : ' target="_blank" rel="noopener"';

  function mediaHTML(c, x) {
    if (!c.media) return '';
    return c.media.map((m) => {
      if (m.phone) {
        const still = `${base}assets/${lang}/${m.phone}.webp`;
        const clip = `${base}assets/${lang}/${m.clip}`;
        const video = m.clip ? `<video muted playsinline loop preload="none" poster="${still}"><source src="${clip}.mp4" type="video/mp4"><source src="${clip}.webm" type="video/webm"></video>` : '';
        return `<div class="phone">${img(m.phone, plain(x.title))}${video}</div>`;
      }
      // Android screenshots get a plain phone frame of their own, never the iPhone's.
      if (m.android) return `<div class="droid">${img(m.android, plain(x.title))}</div>`;
      if (m.watch) return `<div class="watch">${img(m.watch, plain(x.title))}</div>`;
      if (m.devices) {
        const [iphone, ipad, mac, tv, vision] = m.devices;
        const L = x.labels;
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

  function storesHTML() {
    const more = Object.keys(t.storesMore).filter((k) => links[k])
      .map((k) => `<a href="${esc(links[k])}" rel="noopener"${target(k)}>${esc(t.storesMore[k])}</a>`);
    return `<div class="stores">${Object.keys(ICONS).map((k) => {
      const href = links[k];
      const [small, big] = t.stores[k];
      // A store that is not live yet gets no button at all: the button appears the day its link is set.
      return href ? `<a class="store" href="${esc(href)}" rel="noopener"${target(k)}>${ICONS[k]}<small>${esc(small)}</small><b>${esc(big)}</b></a>` : '';
    }).join('')}</div>${more.length ? `<p class="stores-more">${more.join('<span aria-hidden="true">·</span>')}</p>` : ''}`;
  }

  const board = story.chapters.map((c) => `<a href="#${c.id}" data-id="${c.id}"><span>${esc(t.board[c.id])}</span></a>`).join('');

  const chapters = story.chapters.map((c, i) => {
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
          ${x.credit ? `<p class="credit"><a href="${esc(x.credit[1])}" rel="noopener" target="_blank">${esc(x.credit[0])}</a></p>` : ''}
          ${c.id === 'world' ? `<a class="cta-link" href="${esc(getHref)}"${getTarget}>${esc(x.cta)}</a><div class="scroll-cue">${esc(t.scroll)}</div>` : ''}
          ${c.id === 'get' ? storesHTML() : ''}
          ${c.id === 'kaaba' ? `<div class="daynight" role="group">${t.daynight.map((n, k) => `<button type="button" data-night="${k}" aria-pressed="${(k === 1) === night}">${esc(n)}</button>`).join('')}</div>` : ''}
        </div>
        <div class="media">${mediaHTML(c, x)}</div>
      </div>
    </section>`;
  }).join('');

  const shots = ['01-prayer', '02-prayer-weather', '02-qibla', '03-mosques-map', '04-ask', '05-classics', '06-widgets', '07-island', '08-audio', '09-mosques-radar', '10-prayer-dark'];
  const inside = `<div>
      <h2 id="inside-title">${esc(t.index.title)}</h2>
      <p>${esc(t.index.intro)}</p>
      <div class="strip" tabindex="0" aria-label="${esc(t.index.title)}">${shots.map((s) => img(`iphone/${s}`, '')).join('')}${['01-watch-prayer', '02-watch-qibla', '05-watch-mosques', '06-watch-ask'].map((s) => img(`watch/${s}`, '')).join('')}</div>
      <div class="groups">${t.index.groups.map(([g, items]) => `<div class="group"><h3>${esc(g)}</h3><ul>${items.map((it) => `<li>${esc(it)}</li>`).join('')}</ul></div>`).join('')}</div>
    </div>`;

  const f = t.footer, page = (p) => `${base}${p}.html${lang === 'ar' ? '#ar' : ''}`;
  const getLinks = Object.keys(ICONS).filter((k) => links[k]).map((k) => `<a href="${esc(links[k])}" rel="noopener"${target(k)}>${esc(t.stores[k][1])}</a>`).join('');
  const footer = `<div>
      <div class="foot-brand"><img src="${base}assets/icon.webp" alt="" width="40" height="40" loading="lazy"><div><b>${esc(t.title)}</b><p>${esc(f.line)}</p></div></div>
      <div class="foot-cols">
        <div><h3>${esc(f.get)}</h3>${getLinks}</div>
        <div><h3>${esc(f.help)}</h3><a href="${page('support')}">${esc(f.support)}</a><a href="${base}${lang === 'ar' ? 'ar/' : ''}prayer-times/">${esc(f.cities)}</a><a href="mailto:app@vision.bz?subject=Near%20Mosque">app@vision.bz</a></div>
        <div><h3>${esc(f.about)}</h3><a href="${page('privacy')}">${esc(f.privacy)}</a><a href="${page('terms')}">${esc(f.terms)}</a><a href="${page('security')}">${esc(f.security)}</a><a href="${page('credits')}">${esc(f.credits)}</a></div>
      </div>
      <p class="foot-note">${esc(f.note)}</p>
    </div>`;

  return { t, board, chapters, inside, footer };
}
