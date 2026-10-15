// Version 4 of the landing page: Ask first. Four of the app's common questions (questions.json, copied from
// shared/content/common-questions.json by tools/site/build_ask_sample.py) are typed into the question field one
// after another, each answered as the app answers it: the summary, its Qur'an and HadeethEnc sources, and the
// review label while a scholar has not reviewed it.
import { story, links } from '../story.js';
import { storesHTML, esc } from '../prayer.js';

const store = { get: (k) => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch {} } };
const $ = (s) => document.querySelector(s);
const reduce = matchMedia('(prefers-reduced-motion: reduce)');
let lang = (location.hash === '#ar' || location.hash === '#en') ? location.hash.slice(1) : store.get('nm-lang') || ((navigator.languages || [navigator.language]).some((l) => /^ar/i.test(l)) ? 'ar' : 'en');

const T = {
  en: {
    story: 'See the story', eyebrow: 'Near Mosque · Ask', h1: 'Ask about Islam, and see where the answer comes from.',
    lead: 'Answers quote the Qur’an, hadith and books stored on your phone, and show each source.',
    badge: 'Awaiting scholar review', sources: 'Sources', fine: 'Answers quote their sources. They are not a fatwa.', try: 'Try another question',
    note: 'These are 4 of the 25 common questions that come with the app, shown as the app shows them.',
    quran: (r) => `Qur’an ${r}`, hadith: (n) => `Hadith, HadeethEnc ${n}`,
    more: 'Also in the app', privacyEyebrow: 'Privacy', privacy: 'No account, no ads, no tracking.',
    privacyLine: 'Your questions are answered on your device. They are not sent to any AI service.',
    cards: [
      ['iphone/01-prayer', 'Your next prayer, on the sky of the hour.', 'Today’s six times, each with its own reminder, and the Hijri date.'],
      ['iphone/02-qibla', 'The Qibla from anywhere.', 'Turn your phone and the arrow leads you to the Kaaba, with the bearing and distance.'],
      ['iphone/09-mosques-radar', 'The nearest mosque.', 'Each mosque at its real direction and distance, and the walk there on the map.'],
      ['iphone/05-classics', '99 classical books, offline.', 'Download a volume once and read it next to fatwas, lectures and translations of the Qur’an.'],
    ],
  },
  ar: {
    story: 'شاهد القصة', eyebrow: 'أقرب مسجد · اسأل', h1: 'اسأل عن دينك، واعرف من أين جاءت الإجابة.',
    lead: 'تقتبس الإجابات من القرآن والحديث والكتب المحفوظة في هاتفك، وتعرض مصدر كل نص.',
    badge: 'بانتظار مراجعة العلماء', sources: 'المصادر', fine: 'الإجابات تقتبس من مصادرها، وليست فتوى.', try: 'جرّب سؤالًا آخر',
    note: 'هذه 4 من 25 سؤالًا شائعًا مدمجة في التطبيق، معروضة كما يعرضها.',
    quran: (r) => `القرآن ${r}`, hadith: (n) => `حديث، موسوعة الأحاديث ${n}`,
    more: 'وفي التطبيق أيضًا', privacyEyebrow: 'الخصوصية', privacy: 'بلا حساب، بلا إعلانات، بلا تتبّع.',
    privacyLine: 'تُجاب أسئلتك على جهازك، ولا تُرسل إلى أي خدمة ذكاء اصطناعي.',
    cards: [
      ['iphone/01-prayer', 'صلاتك القادمة تحت سماء ساعتك.', 'مواقيت اليوم الستة، لكلٍّ منها تنبيه، مع التاريخ الهجري.'],
      ['iphone/02-qibla', 'القبلة من أي مكان.', 'أدِر هاتفك يرشدك السهم إلى الكعبة، مع الاتجاه بالدرجات والمسافة.'],
      ['iphone/09-mosques-radar', 'أقرب مسجد.', 'كل مسجد في اتجاهه الحقيقي وعلى بعده، وطريق المشي إليه على الخريطة.'],
      ['iphone/05-classics', '99 كتابًا من أمهات الكتب دون إنترنت.', 'نزّل المجلد مرة واحدة واقرأه، إلى جانب الفتاوى والمحاضرات وترجمات معاني القرآن.'],
    ],
  },
};

let questions = [], current = 0, timer = null, typing = null;

function show(i, animate) {
  current = i;
  const q = questions[i], t = T[lang];
  clearInterval(typing); clearTimeout(timer);
  document.querySelectorAll('#chips button').forEach((b, k) => b.setAttribute('aria-pressed', String(k === i)));
  const text = q.question[lang];
  const answer = $('#answer');
  answer.classList.remove('in');
  $('.badge').hidden = q.reviewed;
  $('#summary').textContent = q.summary[lang];
  $('#sources').innerHTML = [
    ...q.quran.map((r) => `<li><a href="https://quran.com/${r.replace(':', '/')}" rel="noopener" target="_blank">${esc(t.quran(r))}</a></li>`),
    ...q.hadith.map((n) => `<li><a href="https://hadeethenc.com/${lang}/browse/hadith/${n}" rel="noopener" target="_blank">${esc(t.hadith(n))}</a></li>`),
  ].join('');
  const done = () => { $('#q').textContent = text; $('.ask').classList.remove('typing'); answer.classList.add('in'); if (!reduce.matches) timer = setTimeout(() => show((current + 1) % questions.length, true), 9000); };
  if (!animate || reduce.matches) { done(); return; }
  // The question is typed in, then the answer comes up.
  $('.ask').classList.add('typing');
  let n = 0;
  $('#q').textContent = '';
  typing = setInterval(() => {
    n += 1;
    $('#q').textContent = [...text].slice(0, n).join('');
    if (n >= [...text].length) { clearInterval(typing); timer = setTimeout(done, 450); }
  }, 32);
}

function render() {
  const t = T[lang], s = story[lang];
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  document.title = s.title;
  document.querySelectorAll('[data-t]').forEach((el) => { el.textContent = t[el.dataset.t] ?? s[el.dataset.t] ?? el.textContent; });
  $('#lang').textContent = s.switchTo;
  $('#lang').setAttribute('lang', lang === 'ar' ? 'en' : 'ar');
  $('#hero-shot').src = `../assets/${lang}/iphone/04-ask.webp`;
  $('#stores-top').innerHTML = $('#stores-end').innerHTML = storesHTML(s, links);
  $('#chips').innerHTML = questions.map((q, i) => `<button type="button" data-i="${i}">${esc(q.question[lang])}</button>`).join('');
  document.querySelectorAll('#chips button').forEach((b) => b.addEventListener('click', () => show(+b.dataset.i, true)));
  $('#cards').innerHTML = t.cards.map(([img, h, p]) => `<article class="card"><div class="shot"><div class="phone"><img src="../assets/${lang}/${img}.webp" alt="" loading="lazy" decoding="async"></div></div><div><h3>${esc(h)}</h3><p>${esc(p)}</p></div></article>`).join('');
  const f = s.footer;
  $('#foot').innerHTML = [['../privacy.html', f.privacy], ['../terms.html', f.terms], ['../support.html', f.support], ['../security.html', f.security], ['../credits.html', f.credits]].map(([h, l]) => `<a href="${h}${lang === 'ar' ? '#ar' : ''}">${esc(l)}</a>`).join('');
  show(current, true);
}

$('#lang').addEventListener('click', () => { lang = lang === 'ar' ? 'en' : 'ar'; store.set('nm-lang', lang); history.replaceState(null, '', '#' + lang); render(); });
questions = await fetch('questions.json').then((r) => r.json());
render();
