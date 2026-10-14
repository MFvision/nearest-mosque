// Shows the page in one language: #ar or #en in the address, else the one chosen on the landing page, else the
// browser's. The button switches between them.
(() => {
  const root = document.documentElement;
  root.classList.add('js');
  let saved = null;
  try { saved = localStorage.getItem('nm-lang'); } catch {}
  let lang = location.hash === '#ar' || location.hash === '#en' ? location.hash.slice(1)
    : saved || ((navigator.languages || [navigator.language]).some((l) => /^ar/i.test(l)) ? 'ar' : 'en');
  const button = document.getElementById('lang');
  function show() {
    const ar = lang === 'ar';
    root.lang = ar ? 'ar' : 'en';
    root.dir = ar ? 'rtl' : 'ltr';
    document.querySelectorAll('article').forEach((a) => a.classList.toggle('shown', a.lang === lang));
    const a = document.querySelector(`article[lang="${lang}"]`);
    if (a && a.dataset.title) document.title = a.dataset.title;
    button.textContent = ar ? 'English' : 'العربية';
    button.lang = ar ? 'en' : 'ar';
    document.querySelectorAll('[data-href]').forEach((l) => { l.href = l.dataset.href + (ar ? '#ar' : ''); });
    document.querySelectorAll('[data-en]').forEach((l) => { l.textContent = ar ? l.dataset.ar : l.dataset.en; });
  }
  button.addEventListener('click', () => {
    lang = lang === 'ar' ? 'en' : 'ar';
    try { localStorage.setItem('nm-lang', lang); } catch {}
    history.replaceState(null, '', '#' + lang);
    show();
  });
  show();
})();
