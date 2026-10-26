// Writes version 5 of the landing page with its words already in the HTML, so search engines and link previews read
// the same page visitors see: website/5/index.html (English) and website/5/ar/index.html (Arabic), from
// tools/site/home.html, 5/story.js and 5/markup.js; then the city pages. Run it after changing any of them:
// node tools/site/prerender.mjs
// While version 5 is being compared with the current page (versions/), its pages say noindex. When it wins, move it
// to the site's root and drop the noindex.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const site = join(here, '../../website');
const { markup, esc } = await import(join(site, '5/markup.js'));

const template = readFileSync(join(here, 'home.html'), 'utf8');
const NOTE = '<!-- Written by tools/site/prerender.mjs from tools/site/home.html and 5/story.js. Edit those, not this file. -->';
const THEME_AUTO = { en: 'Auto', ar: 'تلقائي' };

for (const lang of ['en', 'ar']) {
  const root = lang === 'ar' ? '../../' : '../';
  const m = markup(lang, root);
  const t = m.t;
  const fill = {
    lang: t.lang, dir: t.dir, root,
    pageTitle: esc(t.pageTitle), description: esc(t.description),
    canonical: `https://nearmosque.net/5/${lang === 'ar' ? 'ar/' : ''}`,
    ogLocale: lang === 'ar' ? 'ar_SA' : 'en_US', ogLocaleAlt: lang === 'ar' ? 'en_US' : 'ar_SA',
    skip: esc(t.skip), brand: esc(t.title), sound: esc(t.sound[0]),
    switchTo: esc(t.switchTo), switchLang: lang === 'ar' ? 'en' : 'ar', themeAuto: THEME_AUTO[lang],
    boardLabel: esc(t.boardLabel), board: m.board, chapters: m.chapters, inside: m.inside, footer: m.footer,
    cta: esc(t.chapters.world.cta),
  };
  const html = template.replace(/\{\{(\w+)\}\}/g, (_, k) => {
    if (!(k in fill)) throw new Error(`home.html asks for {{${k}}}, which the build does not fill`);
    return fill[k];
  }).replace('<head>', `<head>\n${NOTE}`);
  const out = join(site, lang === 'ar' ? '5/ar/index.html' : '5/index.html');
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, html);
  console.log('wrote', out.replace(site + '/', 'website/'));
}

// The city pages come from their own script.
await import(join(here, "cities.mjs"));
