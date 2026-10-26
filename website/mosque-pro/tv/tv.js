// Mosque Pro on a TV: a home screen (in a TV's own browser, or the LG app tools/webos/build.py packs from this folder).
// On top the prayer board; below it, rows the remote walks through: the TV's apps, the Quran (a surah picked for the
// moment, the radio, a few surahs, the reciter), today (the weather, the six times, the Qibla, the network), and the
// settings. In Ramadan the board counts to the iftar and the end of suhoor; on Friday it greets the day and offers
// Al-Kahf. At the Adhan the Adhan screen counts to the iqama; late at night a dim
// clock moves about the screen. Any key brings the home screen back for a few minutes. The times come from the same
// formulas as the app (../prayer.js); the choices stay on the TV. The weather is Open-Meteo's (no key, no account).
//
// Written for the older Chromium inside TVs: no top-level await, no ?. or ??; the LG build turns the modules into one
// plain script, so they must stay import/export only. launcher.js (a plain script) gives window.TVApps.
import { places } from '../places.js';
import { resolveCity, schedule, nextPrayer, qibla, clock, METHODS } from '../prayer.js';
import { mosqueSettings, iqamaMinutes, adhanWindow, IQAMA_CHOICES, JUMUAH_CHOICES, OFFSET_CHOICES, MOSQUE_KEY } from './mosque.js';

const $ = (s) => document.querySelector(s);
const store = {
  get: (k) => { try { return localStorage.getItem(k); } catch (e) { return null; } },
  set: (k, v) => { try { if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) { /* private window */ } },
};
const apps = window.TVApps;

const T = {
  ar: {
    prayers: { fajr: 'الفجر', sunrise: 'الشروق', dhuhr: 'الظهر', jumuah: 'الجمعة', asr: 'العصر', maghrib: 'المغرب', isha: 'العشاء' },
    next: 'الصلاة القادمة', remain: 'متبقية', adhan: 'حان الآن موعد أذان', iqama: 'الإقامة بعد', ayah: 'آية اليوم', hadith: 'حديث اليوم',
    iqamaOf: (n) => 'إقامة ' + n, net: 'الشبكة', netClean: 'نظيفة · بلا إعلانات', netAds: 'الإعلانات غير محجوبة',
    netHow: 'لحجبها: إعدادات التلفاز ← الشبكة ← DNS ‏94.140.14.14', netOk: 'تُحجب الإعلانات والمتتبعات على هذه الشبكة',
    nightNext: (name, time, left) => `${name} ${time} · بعد ${left}`, hours: (h, m) => (h ? h + ' س و ' : '') + m + ' د',
    adhanHint: 'الأذان يعمل · اضغط OK لإيقافه',
    apps: 'تطبيقاتك', appsHere: 'افتح التطبيقات من زر الرئيسية في التلفاز', today: 'اليوم', settings: 'الإعدادات', tvSettings: 'إعدادات التلفاز', open: 'فتح',
    weather: 'الطقس', hiLo: (h, l) => `العظمى ${h}° · الصغرى ${l}°`, times: 'مواقيت اليوم',
    qibla: 'القبلة', fromNorth: 'من الشمال', toMakkah: (km) => km.toLocaleString('ar-SA-u-nu-latn') + ' كم إلى مكة المكرمة',
    city: 'المدينة', lang: 'اللغة', langName: 'العربية', sound: 'صوت الأذان', night: 'شاشة الليل',
    on: 'يعمل', off: 'متوقف', auto: 'تلقائي', autoCity: 'تلقائي (حسب التلفاز)', saudi: 'المملكة العربية السعودية', world: 'مدن أخرى',
    pickCity: 'اختر المدينة', pickKeys: '▲ ▼ للتنقّل · OK للاختيار · رجوع للإلغاء',
    greet: (h) => (h >= 4 && h < 12 ? 'صباح الخير' : 'مساء الخير'), lockHint: 'اضغط أي زر للعودة',
    lockNext: 'القادمة', lock: 'شاشة القفل', lockOn: 'بعد ٥ دقائق',
    hintApps: '◀ ▶ للتنقّل · OK للفتح · ▼ للمزيد', hintRows: '▲ ▼ بين الأقسام · ◀ ▶ داخلها · رجوع للأعلى', hintPrefs: 'OK للتغيير · رجوع للأعلى',
    wx: { clear: 'صافٍ', partly: 'غائم جزئيًا', cloudy: 'غائم', fog: 'ضباب', drizzle: 'رذاذ', rain: 'مطر', snow: 'ثلج', storm: 'عاصفة رعدية' },
    day: (d) => new Intl.DateTimeFormat('ar-SA-u-nu-latn', { weekday: 'short', timeZone: 'UTC' }).format(d), sourceSep: ' · ',
    quran: 'القرآن الكريم', lucky: 'شغّل لي', luckyFri: 'سنّة يوم الجمعة', luckyNight: 'قبل النوم', luckyAny: 'اختيار لك',
    nowPlaying: 'يُتلى الآن', stopHint: 'OK للإيقاف', paused: 'متوقف · OK للمتابعة', radio: 'إذاعة القرآن الكريم', radioSub: 'بث مباشر على مدار الساعة',
    reciter: 'القارئ', pickReciter: 'اختر القارئ', surah: (s) => 'سورة ' + s, playErr: 'تعذّر التشغيل · تأكّد من الإنترنت',
    hintQuran: '◀ ▶ للتنقّل · OK للاستماع · ▼ للمزيد',
    ramadan: 'رمضان كريم', iftar: 'موعد الإفطار', iftarChip: 'الإفطار', suhoor: 'السحور حتى الفجر', suhoorChip: 'السحور حتى',
    ramadanDay: (d) => 'اليوم ' + d + ' من رمضان', taraweeh: 'التراويح بعد العشاء',
    jumuah: 'جمعة مباركة', early: 'بكِّر إلى صلاة الجمعة', answerHour: 'ساعة إجابة · أكثِر من الدعاء', kahfDay: 'يوم الجمعة', kahf: 'سورة الكهف',
    iqamaSet: 'الإقامة', calc: 'طريقة الحساب', methodL: 'الطريقة', asrL: 'العصر', adjust: 'دقائق تُضاف أو تُطرح',
    asrStd: 'الجمهور (ظل المثل)', asrHanafi: 'الحنفي (ظل المثلين)', autoMethod: (m) => 'تلقائي · ' + m, jumuahL: 'الجمعة (من الأذان إلى الصلاة)',
    usual: 'المعتادة', custom: 'حسب مسجدك',
    short: { mwl: 'الرابطة', uaq: 'أم القرى', egypt: 'المصرية', karachi: 'كراتشي', isna: 'أمريكا الشمالية', dubai: 'دبي', kuwait: 'الكويت', qatar: 'قطر', sea: 'سنغافورة', turkey: 'تركيا' },
    mins: (n) => n + ' د', minsSigned: (n) => (n > 0 ? '+' : n < 0 ? '−' : '') + Math.abs(n) + ' د', noWait: 'بلا انتظار',
    methods: { mwl: 'رابطة العالم الإسلامي', uaq: 'أم القرى (مكة المكرمة)', egypt: 'الهيئة المصرية العامة للمساحة', karachi: 'جامعة العلوم الإسلامية بكراتشي',
      isna: 'الجمعية الإسلامية لأمريكا الشمالية', dubai: 'دبي', kuwait: 'الكويت', qatar: 'قطر', sea: 'سنغافورة وجنوب شرق آسيا', turkey: 'الشؤون الدينية التركية' },
    help: 'المساعدة', helpValue: 'تواصل معنا', helpMail: 'البريد',
    helpReply: 'نقرأ كل رسالة ونردّ بالبريد.',
    helpQA: [
      ['المواقيت لا تطابق مسجدي', 'تُحسب بالطريقة المعتمدة في بلد المدينة (أم القرى في السعودية). تأكّد أن المدينة في الإعدادات صحيحة، ثم اضبط الطريقة والعصر والدقائق من الإعدادات ← طريقة الحساب، والإقامة من الإعدادات ← الإقامة.'],
      ['لا أسمع الأذان', 'صوت الأذان متوقف في البداية. شغّله من الإعدادات ← صوت الأذان، ثم اضغط أي زر مرة ليسمح التلفاز بالصوت.'],
      ['القرآن لا يعمل', 'التلاوات تُبث عبر الإنترنت. تأكّد من اتصال التلفاز، أو جرّب قارئًا آخر.'],
    ],
  },
  en: {
    prayers: { fajr: 'Fajr', sunrise: 'Sunrise', dhuhr: 'Dhuhr', jumuah: "Jumu'ah", asr: 'Asr', maghrib: 'Maghrib', isha: 'Isha' },
    next: 'Next prayer', remain: 'to go', adhan: 'Time for the Adhan of', iqama: 'Iqama in', ayah: 'Verse of the day', hadith: 'Hadith of the day',
    iqamaOf: (n) => n + ' iqama', net: 'Network', netClean: 'Clean · no ads', netAds: 'Ads not blocked',
    netHow: 'To block them: TV settings → Network → DNS 94.140.14.14', netOk: 'Ads and trackers are blocked on this network',
    nightNext: (name, time, left) => `${name} ${time} · in ${left}`, hours: (h, m) => (h ? h + 'h ' : '') + m + 'm',
    adhanHint: 'Adhan playing · press OK to stop',
    apps: 'Your apps', appsHere: "Open apps from the TV's Home button", today: 'Today', settings: 'Settings', tvSettings: 'TV settings', open: 'Open',
    weather: 'Weather', hiLo: (h, l) => `High ${h}° · Low ${l}°`, times: "Today's times",
    qibla: 'Qibla', fromNorth: 'from north', toMakkah: (km) => km.toLocaleString('en-US') + ' km to Makkah',
    city: 'City', lang: 'Language', langName: 'English', sound: 'Adhan sound', night: 'Night screen',
    on: 'On', off: 'Off', auto: 'Automatic', autoCity: 'Automatic (from the TV)', saudi: 'Saudi Arabia', world: 'Other cities',
    pickCity: 'Choose the city', pickKeys: '▲ ▼ to move · OK to choose · Back to cancel',
    greet: (h) => (h >= 4 && h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'), lockHint: 'Press any key to go back',
    lockNext: 'Next', lock: 'Lock screen', lockOn: 'After 5 minutes',
    hintApps: '◀ ▶ to move · OK to open · ▼ for more', hintRows: '▲ ▼ between rows · ◀ ▶ within · Back to the top', hintPrefs: 'OK to change · Back to the top',
    wx: { clear: 'Clear', partly: 'Partly cloudy', cloudy: 'Cloudy', fog: 'Fog', drizzle: 'Drizzle', rain: 'Rain', snow: 'Snow', storm: 'Thunderstorm' },
    day: (d) => new Intl.DateTimeFormat('en-GB', { weekday: 'short', timeZone: 'UTC' }).format(d), sourceSep: ' · ',
    quran: 'The Quran', lucky: 'Play for me', luckyFri: 'Sunnah on Friday', luckyNight: 'Before sleep', luckyAny: 'Picked for you',
    nowPlaying: 'Now playing', stopHint: 'OK to stop', paused: 'Paused · OK to go on', radio: 'Quran radio', radioSub: 'Live, day and night',
    reciter: 'Reciter', pickReciter: 'Choose the reciter', surah: (s) => 'Surah ' + s, playErr: "Couldn't play · check the internet",
    hintQuran: '◀ ▶ to move · OK to listen · ▼ for more',
    ramadan: 'Ramadan Kareem', iftar: 'Iftar', iftarChip: 'Iftar', suhoor: 'Suhoor until Fajr', suhoorChip: 'Suhoor until',
    ramadanDay: (d) => 'Day ' + d + ' of Ramadan', taraweeh: 'Taraweeh after Isha',
    jumuah: "Jumu'ah Mubarak", early: "Go early to Jumu'ah", answerHour: "An hour of answered du'a", kahfDay: 'Friday', kahf: 'Surah Al-Kahf',
    iqamaSet: 'Iqama', calc: 'Calculation', methodL: 'Method', asrL: 'Asr', adjust: 'Minutes added or taken away',
    asrStd: "Standard (Shafi'i, Maliki, Hanbali)", asrHanafi: 'Hanafi', autoMethod: (m) => 'Automatic · ' + m, jumuahL: "Jumu'ah (Adhan to prayer)",
    usual: 'Usual', custom: "Your mosque's",
    short: { mwl: 'MWL', uaq: 'Umm al-Qura', egypt: 'Egypt', karachi: 'Karachi', isna: 'ISNA', dubai: 'Dubai', kuwait: 'Kuwait', qatar: 'Qatar', sea: 'Singapore', turkey: 'Diyanet' },
    mins: (n) => n + ' min', minsSigned: (n) => (n > 0 ? '+' : n < 0 ? '−' : '') + Math.abs(n) + ' min', noWait: 'No wait',
    methods: { mwl: 'Muslim World League', uaq: 'Umm al-Qura (Makkah)', egypt: 'Egyptian General Authority', karachi: 'University of Islamic Sciences, Karachi',
      isna: 'ISNA (North America)', dubai: 'Dubai', kuwait: 'Kuwait', qatar: 'Qatar', sea: 'Singapore and Southeast Asia', turkey: 'Diyanet (Turkey)' },
    help: 'Help', helpValue: 'Contact', helpMail: 'Email',
    helpReply: 'We read every message and reply by email.',
    helpQA: [
      ["The times don't match my mosque", "They use the method of the city's country (Umm al-Qura in Saudi Arabia). Check the city in Settings, then set the method, Asr and minutes in Settings, Calculation, and the iqama in Settings, Iqama."],
      ["I don't hear the Adhan", 'The Adhan sound starts off. Turn it on in Settings, Adhan sound, then press any key once so the TV allows sound.'],
      ["The Quran won't play", "Recitations are streamed. Check the TV's internet connection, or try another reciter."],
    ],
  },
};
// Where help requests go (the same as shared/help/help.json).
const CONTACT = { email: 'app@vision.bz' };
const MIN = 60e3;
const WAKE = 3 * MIN; // a key press on the Adhan or night screen shows the home screen this long
const LOCK_AFTER = 5 * MIN; // the lock screen comes up after this long without a key

// More Saudi cities than the site's globe names, so the TV's list has the Kingdom's cities first.
const SAUDI = [
  ['Riyadh', 'الرياض', 24.7136, 46.6753], ['Makkah', 'مكة المكرمة', 21.4225, 39.8262], ['Madinah', 'المدينة المنورة', 24.4686, 39.6142],
  ['Jeddah', 'جدة', 21.5433, 39.1728], ['Dammam', 'الدمام', 26.4207, 50.0888], ['Khobar', 'الخبر', 26.2172, 50.1971],
  ['Dhahran', 'الظهران', 26.2361, 50.0393], ['Taif', 'الطائف', 21.2703, 40.4158], ['Tabuk', 'تبوك', 28.3835, 36.5662],
  ['Buraidah', 'بريدة', 26.326, 43.975], ['Unaizah', 'عنيزة', 26.084, 43.994], ['Abha', 'أبها', 18.2164, 42.5053],
  ['Khamis Mushait', 'خميس مشيط', 18.3, 42.73], ['Hail', 'حائل', 27.5114, 41.7208], ['Jazan', 'جازان', 16.8892, 42.5511],
  ['Najran', 'نجران', 17.5656, 44.2289], ['Al-Ahsa', 'الأحساء', 25.38, 49.588], ['Qatif', 'القطيف', 26.556, 50.008],
  ['Jubail', 'الجبيل', 27.0174, 49.6225], ['Yanbu', 'ينبع', 24.089, 38.0637], ['Al Kharj', 'الخرج', 24.1556, 47.312],
  ['Al Bahah', 'الباحة', 20.0129, 41.4677], ['Sakaka', 'سكاكا', 29.9697, 40.2064], ['Arar', 'عرعر', 30.9753, 41.0381],
  ['Hafar Al-Batin', 'حفر الباطن', 28.4328, 45.9708], ['Al Majmaah', 'المجمعة', 25.9039, 45.3456], ['Rabigh', 'رابغ', 22.7986, 39.0349],
  ['Al Qurayyat', 'القريات', 31.3318, 37.3428],
].map((p) => p.concat('Asia/Riyadh'));
const WORLD = places.filter((p) => p[4] !== 'Asia/Riyadh');
const ALL = SAUDI.concat(WORLD);

// The Quran: whole surahs from mp3quran.net (free, no account), by reciter; Alafasy on QuranicAudio if a file fails.
const RECITERS = [
  ['afs', 'https://server8.mp3quran.net/afs/', 'مشاري العفاسي', 'Mishary Alafasy'],
  ['maher', 'https://server12.mp3quran.net/maher/', 'ماهر المعيقلي', 'Maher Al-Muaiqly'],
  ['sds', 'https://server11.mp3quran.net/sds/', 'عبدالرحمن السديس', 'Abdulrahman Al-Sudais'],
  ['yasser', 'https://server11.mp3quran.net/yasser/', 'ياسر الدوسري', 'Yasser Al-Dosari'],
  ['s_gmd', 'https://server7.mp3quran.net/s_gmd/', 'سعد الغامدي', 'Saad Al-Ghamdi'],
  ['husr', 'https://server13.mp3quran.net/husr/', 'محمود خليل الحصري', 'Mahmoud Al-Husary'],
  ['basit', 'https://server7.mp3quran.net/basit/', 'عبدالباسط عبدالصمد', 'Abdul Basit'],
];
const FALLBACK = 'https://download.quranicaudio.com/quran/mishaari_raashid_al_3afaasee/';
const RADIO = 'https://backup.qurango.net/radio/mix';
const SURAH = {
  1: ['الفاتحة', 'Al-Fatihah'], 2: ['البقرة', 'Al-Baqarah'], 12: ['يوسف', 'Yusuf'], 18: ['الكهف', 'Al-Kahf'], 19: ['مريم', 'Maryam'],
  32: ['السجدة', 'As-Sajdah'], 36: ['يس', 'Ya-Sin'], 55: ['الرحمن', 'Ar-Rahman'], 56: ['الواقعة', "Al-Waqi'ah"], 67: ['الملك', 'Al-Mulk'],
  76: ['الإنسان', 'Al-Insan'], 78: ['النبأ', "An-Naba'"], 87: ['الأعلى', "Al-A'la"], 89: ['الفجر', 'Al-Fajr'], 93: ['الضحى', 'Ad-Duha'],
};
const SHOWN = [36, 55, 56, 67, 2, 12, 19, 1]; // the surahs on the row (Al-Kahf leads on Friday)
const LUCKY = [12, 18, 19, 32, 36, 55, 56, 67, 76, 78, 87, 89, 93];

let lang = store.get('mp-tv-lang') || ((navigator.languages || [navigator.language]).some((l) => /^ar/i.test(l)) ? 'ar' : 'en');
let sound = store.get('mp-tv-sound') === '1';
let nightOn = store.get('mp-tv-night') !== '0';
let lockOn = store.get('mp-tv-lock') !== '0';
// The mosque's iqama, Jumu'ah, method, Asr and minutes (website/tv/mosque.js).
let mosque = mosqueSettings(store.get(MOSQUE_KEY));
let reciter = RECITERS.filter((r) => r[0] === store.get('mp-tv-reciter'))[0] || RECITERS[0];
let playing = null, lucky = 0, quranKey = '', hushed = '';
let lastKey = Date.now();
let city = null, plan = null, planDay = '', daily = null, played = '', unlocked = false, mode = '';
let appList = [], weather = null, netClean = null, wakeUntil = 0;
let row = 0; const col = [0, 0, 0, 0];

const t = () => T[lang];
const rtl = () => lang === 'ar';
const cityName = () => (rtl() ? city.ar : city.en);
const at = (d) => clock(d, city.tz, lang);
const ymd = (d) => new Intl.DateTimeFormat('en-CA', { timeZone: city.tz, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);
const isFriday = (d) => new Intl.DateTimeFormat('en-US', { timeZone: city.tz, weekday: 'short' }).format(d) === 'Fri';
const name = (k, d) => t().prayers[k === 'dhuhr' && isFriday(d) ? 'jumuah' : k];
const side = () => (rtl() ? 'right' : 'left');
const surahName = (n) => (rtl() ? SURAH[n][0] : SURAH[n][1]);
const reciterName = (r) => (rtl() ? r[2] : r[3]);
const html = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

function pad(n) { return n < 10 ? '0' + n : '' + n; }
function countdown(ms) {
  const s = Math.max(0, Math.floor(ms / 1000)), h = Math.floor(s / 3600);
  return (h ? h + ':' : '') + pad(Math.floor(s / 60) % 60) + ':' + pad(s % 60);
}
// By XHR: it works over the web and from an installed TV app's own files (file://) alike.
function getJSON(url) {
  return new Promise((ok, fail) => {
    const x = new XMLHttpRequest();
    x.open('GET', url);
    x.timeout = 15000;
    x.onload = () => { try { ok(JSON.parse(x.responseText)); } catch (e) { fail(e); } };
    x.onerror = x.ontimeout = fail;
    x.send();
  });
}

// Icons: thin gold strokes, as in the app.
const ICON = {
  pin: 'M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  lang: 'M4 5h10M9 3v2M6 5c1 4 4 7 7 8M12 5c-1 4-4 7-8 8M13 21l4-10 4 10M14.5 17h5',
  sound: 'M11 5L6 9H3v6h3l5 4V5zM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13',
  moon: 'M15.5 3.5a8.5 8.5 0 1 0 5 13.6A7 7 0 0 1 15.5 3.5z',
  help: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6M12 17h.01',
  gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  compass: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM15.5 8.5l-2 5-5 2 2-5z',
  shield: 'M12 3l7 3v5c0 4.5-3 8.3-7 10-4-1.7-7-5.5-7-10V6zM9 12l2 2 4-4',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  play: 'M8 5v14l11-7z',
  stop: 'M7 7h10v10H7z',
  radio: 'M4 10h16v10H4zM8 10l9-5.5M8.5 17.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM14 14h3M14 17h3',
  mic: 'M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21',
  crescent: 'M14 3.5a8.5 8.5 0 1 0 6.5 13A7 7 0 0 1 14 3.5zM18.5 4l.5 1.3 1.3.5-1.3.5-.5 1.3-.5-1.3-1.3-.5 1.3-.5z',
  clear: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM12 1.5v2M12 20.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M1.5 12h2M20.5 12h2M4.6 19.4L6 18M18 6l1.4-1.4',
  partly: 'M8 4.5v1.5M3.8 6.3l1 1M2 10.5h1.5M12.2 7.3l1-1M6 13a4 4 0 0 1 7.2-2.4M9 20h9a3.5 3.5 0 0 0 0-7h-.3A5.5 5.5 0 0 0 7 14.5 3 3 0 0 0 9 20z',
  cloudy: 'M7 19h11a4 4 0 0 0 0-8h-.5A6 6 0 0 0 6 12.5 3.5 3.5 0 0 0 7 19z',
  fog: 'M4 10h16M3 14h18M6 18h12M7 6h10',
  rain: 'M7 15h11a4 4 0 0 0 0-8h-.5A6 6 0 0 0 6 8.5 3.5 3.5 0 0 0 7 15zM8 18l-1 2.5M12 18l-1 2.5M16 18l-1 2.5',
  snow: 'M7 14h11a4 4 0 0 0 0-8h-.5A6 6 0 0 0 6 7.5 3.5 3.5 0 0 0 7 14zM8 18h.01M12 18h.01M16 18h.01M10 21h.01M14 21h.01',
  storm: 'M7 14h11a4 4 0 0 0 0-8h-.5A6 6 0 0 0 6 7.5 3.5 3.5 0 0 0 7 14zM12.5 14l-2 4h3l-2 4',
};
const svg = (k) => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${ICON[k]}"/></svg>`;
function wxKind(code) {
  if (code === 0) return 'clear';
  if (code <= 2) return 'partly';
  if (code === 3) return 'cloudy';
  if (code === 45 || code === 48) return 'fog';
  if (code >= 51 && code <= 57) return 'drizzle';
  if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return 'rain';
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snow';
  if (code >= 95) return 'storm';
  return 'cloudy';
}
const wxIcon = (k, day) => (k === 'clear' && !day ? 'moon' : k === 'drizzle' ? 'rain' : k);

// The verse and hadith of the day: the same pick as the app (days since the epoch, round the list), for the city's date.
function dailyPick(now) {
  if (!daily) return null;
  const [y, m, d] = ymd(now).split('-').map(Number), [ey, em, ed] = daily.epoch.split('-').map(Number);
  const days = Math.round((Date.UTC(y, m - 1, d) - Date.UTC(ey, em - 1, ed)) / 864e5);
  const mod = (n, c) => ((n % c) + c) % c;
  return { ayah: daily.ayat[mod(days, daily.ayat.length)], hadith: daily.hadith[mod(days, daily.hadith.length)] };
}

// MARK: - Ramadan and Friday

// The Umm al-Qura month and day at a moment; null where the TV's browser lacks that calendar (it would quietly give
// the Gregorian one, so that is checked).
function hijri(d) {
  try {
    const f = new Intl.DateTimeFormat('en-US-u-ca-islamic-umalqura-nu-latn', { timeZone: city.tz, day: 'numeric', month: 'numeric' });
    if (!/^islamic/.test(f.resolvedOptions().calendar) || !f.formatToParts) return null;
    const o = {};
    f.formatToParts(d).forEach((x) => { o[x.type] = +x.value; });
    return o.month ? { m: o.month, d: o.day } : null;
  } catch (e) { return null; }
}
const inRamadan = (d) => { const h = hijri(d); return !!h && h.m === 9; };
const weekday = (d) => new Intl.DateTimeFormat('en-US', { timeZone: city.tz, weekday: 'short' }).format(d);
// Al-Kahf is read from Thursday's Maghrib to Friday's.
function kahfTime(now) {
  const maghrib = plan.today[4][1], day = weekday(now);
  return (day === 'Fri' && now < maghrib) || (day === 'Thu' && now >= maghrib);
}
function greeting(now) {
  const tx = t();
  if (isFriday(now)) return tx.jumuah;
  if (inRamadan(now)) return tx.ramadan;
  return tx.greet(hourIn(now));
}
// Over the next prayer in the arch: the iftar and the end of suhoor in Ramadan; on Friday, go early to the Jumu'ah,
// and the last hour before Maghrib, an hour of answered du'a.
function nextLabel(now, next) {
  const tx = t(), k = next[0];
  if (k === 'maghrib' && inRamadan(next[1])) return tx.iftar;
  if (k === 'fajr' && inRamadan(next[1])) return tx.suhoor;
  if (k === 'dhuhr' && isFriday(next[1]) && next[1] - now < 180 * MIN) return tx.early;
  if (k === 'maghrib' && isFriday(now) && next[1] - now < 60 * MIN) return tx.answerHour;
  return tx.next;
}
// The chips under the verse: what is playing, the iftar or suhoor (when the arch isn't already showing it), Al-Kahf.
function renderChips(now, next) {
  const tx = t(), maghrib = plan.today[4][1];
  $('#chip-play').hidden = !playing;
  if (playing) { $('#play-l').textContent = tx.nowPlaying; $('#play-now').textContent = playTitle(true); }
  let fast = null;
  if (now < maghrib && next[0] !== 'maghrib' && inRamadan(now)) fast = [tx.iftarChip, maghrib];
  else if (now >= maghrib && next[0] !== 'fajr' && inRamadan(plan.tomorrowFajr)) fast = [tx.suhoorChip, plan.tomorrowFajr];
  $('#chip-fast').hidden = !fast;
  if (fast) { $('#fast-l').textContent = fast[0]; $('#fast-at').textContent = at(fast[1]); }
  $('#chip-kahf').hidden = !kahfTime(now) || !!(playing && playing.n === 18);
  $('#kahf-l').textContent = tx.kahfDay;
  $('#kahf').textContent = tx.kahf;
  // Four at most; the later ones give way.
  let shown = 0;
  Array.prototype.forEach.call(document.querySelectorAll('.chips .chip'), (c) => c.classList.toggle('cap', !c.hidden && ++shown > 4));
}

// MARK: - The Quran

function suggestion(now) {
  const tx = t();
  if (kahfTime(now)) return { n: 18, why: tx.luckyFri };
  if (now >= plan.today[5][1] || now < plan.today[0][1]) return { n: 67, why: tx.luckyNight };
  if (!lucky) lucky = LUCKY[Math.floor(Math.random() * LUCKY.length)];
  return { n: lucky, why: tx.luckyAny };
}
function playTitle(short) {
  const tx = t();
  if (!playing) return '';
  if (playing.kind === 'radio') return tx.radio;
  return (short ? surahName(playing.n) : tx.surah(surahName(playing.n))) + ' · ' + reciterName(playing.r);
}

function renderQuran() {
  const tx = t(), s = suggestion(new Date());
  quranKey = lang + '|' + s.n;
  const eq = '<i class="eq"><i></i><i></i><i></i></i>';
  const on = (n) => !!playing && playing.kind === 'surah' && playing.n === n;
  const first = playing
    ? `<div class="card qcard lucky playing" data-act="lucky"><small>${eq}${html(tx.nowPlaying)}</small>
        <b>${html(playing.kind === 'radio' ? tx.radio : tx.surah(surahName(playing.n)))}</b>
        <p class="sub">${html(playing.kind === 'radio' ? tx.radioSub : reciterName(playing.r))}</p>
        <p class="go"><span class="ic">${svg('stop')}</span>${html(tx.stopHint)}</p></div>`
    : `<div class="card qcard lucky" data-act="lucky"><small>${html(s.why)}</small>
        <b>${html(tx.surah(surahName(s.n)))}</b><p class="sub">${html(reciterName(reciter))}</p>
        <p class="go"><span class="ic">${svg('play')}</span>${html(tx.lucky)}</p></div>`;
  const radioOn = !!playing && playing.kind === 'radio';
  const radio = `<div class="card qcard${radioOn ? ' playing' : ''}" data-act="radio"><span class="ic">${svg('radio')}</span>
    <b>${html(tx.radio)}</b><p class="sub">${radioOn ? eq : ''}${html(tx.radioSub)}</p></div>`;
  const who = `<div class="card qcard" data-act="reciter"><span class="ic">${svg('mic')}</span><small>${html(tx.reciter)}</small>
    <b>${html(reciterName(reciter))}</b></div>`;
  const list = (kahfTime(new Date()) ? [18] : []).concat(SHOWN);
  const surahs = list.map((n) => `<div class="card qcard surah${on(n) ? ' playing' : ''}" data-surah="${n}">
    <span class="num">${n}</span><b class="ar" lang="ar">${html(SURAH[n][0])}</b><small>${on(n) ? eq : ''}${html(rtl() ? 'سورة' : SURAH[n][1])}</small></div>`);
  $('#quran').innerHTML = [first, radio, who].concat(surahs).join('');
  place();
}

function playQuran(p) {
  const a = $('#quran-audio');
  stopAdhan();
  playing = p;
  a.src = p.kind === 'radio' ? RADIO : (p.fallback ? FALLBACK : p.r[1]) + ('00' + p.n).slice(-3) + '.mp3';
  const pr = a.play();
  if (pr && pr.catch) pr.catch(() => {});
  renderQuran();
}
function stopQuran() {
  if (!playing) return false;
  playing = null;
  const a = $('#quran-audio');
  a.pause();
  a.removeAttribute('src');
  try { a.load(); } catch (e) { /* nothing loaded */ }
  renderQuran();
  return true;
}
function toast(msg) {
  const el = $('#hint');
  el.textContent = msg;
  el.classList.remove('gone');
  clearTimeout(hintTimer);
  hintTimer = setTimeout(() => el.classList.add('gone'), 6000);
}
(function () {
  const a = $('#quran-audio');
  a.addEventListener('ended', () => { if (playing && playing.kind === 'surah') stopQuran(); });
  a.addEventListener('error', () => {
    if (!playing || !a.getAttribute('src')) return;
    // A reciter's file that won't load: the same surah by Alafasy from the second source, else say so.
    if (playing.kind === 'surah' && !playing.fallback) { playQuran({ kind: 'surah', n: playing.n, r: RECITERS[0], fallback: true }); return; }
    stopQuran();
    toast(t().playErr);
  });
})();

// Which screen: the Adhan from its time until the iqama, the night screen late at night, else the home screen.
function adhanNow(now) { return adhanWindow(plan, now, mosque, isFriday); }
function nightSpan(now) {
  const isha = plan.today.find(([k]) => k === 'isha')[1], fajr = plan.today.find(([k]) => k === 'fajr')[1];
  if (now >= +isha + 90 * MIN && now < +plan.tomorrowFajr - 30 * MIN) return [isha, plan.tomorrowFajr];
  if (now >= +plan.yesterdayIsha + 90 * MIN && now < +fajr - 30 * MIN) return [plan.yesterdayIsha, fajr];
  return null;
}

function show(next) {
  if (mode === next) return;
  mode = next;
  ['board', 'adhan', 'night', 'lock'].forEach((id) => { $('#' + id).hidden = id !== next; });
  // The lock screen's film plays only while it shows.
  const v = $('#lock-video');
  if (next === 'lock') {
    if (!v.getAttribute('src')) v.setAttribute('src', '../assets/kaaba-tawaf.mp4');
    const p = v.play();
    if (p && p.catch) p.catch(() => {});
    const h = $('#lock-hint');
    h.classList.remove('gone');
    setTimeout(() => h.classList.add('gone'), 6000);
  } else if (!v.paused) v.pause();
}

const hourIn = (d) => +new Intl.DateTimeFormat('en-GB', { timeZone: city.tz, hour: 'numeric', hourCycle: 'h23' }).format(d);

// The lock screen: the time large over the Kaaba, the dates, the next prayer counting down, the verse of the day.
// After Maghrib the film is dimmer and warmer, as the room is.
function renderLock(now, next) {
  const tx = t(), parts = at(now).split(' ');
  $('#lock-greet').textContent = greeting(now);
  $('#lock-city').textContent = cityName();
  $('#lock-clock').textContent = parts[0];
  $('#lock-ampm').textContent = parts.slice(1).join(' ');
  $('#lock-date').innerHTML = html($('#date').textContent) + ' · <b>' + html($('#hijri').textContent) + '</b>';
  $('#lock-next-l').textContent = next[0] === 'maghrib' && inRamadan(next[1]) ? tx.iftarChip : tx.lockNext;
  $('#lock-play').hidden = !playing;
  $('#lock-play').textContent = playing ? '♪  ' + playTitle(false) : '';
  $('#lock-next').textContent = name(next[0], next[1]);
  $('#lock-in').textContent = countdown(next[1] - now);
  $('#lock-hint').textContent = tx.lockHint;
  const pick = dailyPick(now);
  if (pick && $('#lock-ayah-ar').textContent !== pick.ayah.ar) {
    $('#lock-ayah-ar').textContent = pick.ayah.ar;
    $('#lock-ayah-ref').textContent = (rtl() ? 'سورة ' + pick.ayah.surah.ar : pick.ayah.surah.translit) + ' · ' + pick.ayah.ref;
  }
  const maghrib = plan.today[4][1], fajr = plan.today[0][1];
  const evening = now >= maghrib || now < fajr;
  $('#lock-video').style.filter = evening ? 'brightness(0.62) sepia(0.25) saturate(0.9)' : 'brightness(0.85)';
}

function playAdhan(key) {
  if (!sound || played === key) return;
  played = key;
  const a = $('#adhan-audio');
  a.currentTime = 0;
  const p = a.play();
  if (p && p.catch) p.catch(() => {});
}
function stopAdhan() { const a = $('#adhan-audio'); if (!a.paused) { a.pause(); return true; } return false; }

let lastShift = 0;
function tick() {
  const now = new Date();
  if (ymd(now) !== planDay) { plan = schedule(city, mosque); planDay = ymd(now); renderToday(); }
  const tx = t(), adhan = adhanNow(now), awake = now < wakeUntil;
  // At the Adhan the recitation stops, once; it can be started again after.
  if (adhan && hushed !== planDay + adhan.k) { hushed = planDay + adhan.k; stopQuran(); }
  if (adhan) playAdhan(planDay + adhan.k);
  if (adhan && !awake) {
    show('adhan');
    $('#adhan-label').textContent = tx.adhan;
    $('#adhan-name').textContent = name(adhan.k, now);
    $('#ring').hidden = !adhan.iqama;
    if (adhan.iqama) {
      const total = adhan.wait * MIN, left = adhan.iqama - now;
      $('#iqama-label').textContent = tx.iqama;
      $('#iqama-in').textContent = countdown(left);
      $('#ring-fg').style.strokeDashoffset = String(440 * (1 - left / total));
    }
    $('#adhan-foot').textContent = cityName() + ' · ' + at(now);
    $('#adhan-hint').textContent = sound && !$('#adhan-audio').paused ? tx.adhanHint : '';
    return;
  }
  const next = nextPrayer(plan, now);
  const night = nightOn && !awake && $('#picker').hidden && nightSpan(now);
  if (night) {
    show('night');
    const left = Math.max(0, Math.round((next[1] - now) / MIN));
    const nc = at(now).split(' ');
    $('#night-clock').textContent = nc[0];
    $('#night-ampm').textContent = nc.slice(1).join(' ');
    $('#night-next').textContent = tx.nightNext(name(next[0], next[1]), at(next[1]), tx.hours(Math.floor(left / 60), left % 60));
    $('#night-fill').style.width = Math.min(100, 100 * (now - night[0]) / (night[1] - night[0])).toFixed(1) + '%';
    // Move a little every minute so nothing stays lit in one place all night.
    if (now - lastShift > MIN) {
      lastShift = +now;
      const box = $('#night-box');
      box.style.top = (12 + Math.random() * 40) + 'vh';
      box.style.left = (6 + Math.random() * 40) + 'vw';
    }
    return;
  }
  if (lockOn && now - lastKey > LOCK_AFTER && $('#picker').hidden) {
    show('lock');
    renderLock(now, next);
    return;
  }
  if (mode !== 'board') { show('board'); place(); }
  $('#clock').textContent = at(now);
  $('#greet').textContent = greeting(now);
  $('#next-label').textContent = nextLabel(now, next);
  $('#next-name').textContent = name(next[0], next[1]);
  $('#next-at').textContent = at(next[1]);
  $('#countdown').textContent = countdown(next[1] - now);
  const wait = iqamaMinutes(mosque, next[0], isFriday(next[1]));
  if (wait) {
    $('#chip-iqama').hidden = false;
    $('#iqama-l').textContent = tx.iqamaOf(name(next[0], next[1]));
    $('#iqama-at').textContent = at(new Date(+next[1] + wait * MIN));
  } else $('#chip-iqama').hidden = true;
  renderLine(now, next);
  renderDaily(now);
  renderChips(now, next);
  if (now.getSeconds() === 0 && quranKey !== lang + '|' + suggestion(now).n) renderQuran();
  if (now.getSeconds() === 0) renderTimesCard(now);
  // A few pixels' drift every three minutes, against burn-in on screens left on all day.
  if (now - lastShift > 3 * MIN) {
    lastShift = +now;
    $('#board').style.transform = `translate(${(Math.random() - 0.5) * 0.6}vw, ${(Math.random() - 0.5) * 0.6}vh)`;
  }
}

// The day as a line from Fajr to Isha: the passed times in gold, the next one ringed, the sun where now is.
let lineKey = '';
function renderLine(now, next) {
  const fajr = +plan.today[0][1], isha = +plan.today[5][1], span = isha - fajr;
  const pct = (v) => Math.max(0, Math.min(100, 100 * (v - fajr) / span));
  const key = lang + planDay + next[0] + Math.floor(now / MIN);
  if (key !== lineKey) {
    lineKey = key;
    $('#marks').innerHTML = plan.today.map(([k, v]) => {
      const cls = 'mark' + (k === 'sunrise' ? ' sunrise' : '') + (k === next[0] && +v === +next[1] ? ' next' : v <= now ? ' past' : '');
      return `<div class="${cls}" style="${side()}:${pct(+v)}%"><span>${html(name(k, now))}</span><i></i><b>${html(at(v))}</b></div>`;
    }).join('');
  }
  const inDay = now >= fajr && now <= isha;
  const p = inDay ? pct(+now) : now > isha ? 100 : 0;
  $('#rail-done').style.width = p + '%';
  const sun = $('#sun');
  sun.hidden = !inDay;
  sun.style.left = sun.style.right = '';
  sun.style[side()] = p + '%';
}

// The verse and the hadith take turns, a minute each; a long one gets a smaller size so it still fits.
function renderDaily(now) {
  const pick = dailyPick(now), tx = t();
  $('#daily').hidden = !pick;
  if (!pick) return;
  const ayahTurn = Math.floor(now / MIN) % 2 === 0;
  let ar, tr, ref;
  if (ayahTurn) {
    ar = pick.ayah.ar; tr = pick.ayah.en;
    ref = (rtl() ? 'سورة ' + pick.ayah.surah.ar : pick.ayah.surah.translit) + ' · ' + pick.ayah.ref;
  } else {
    ar = '«' + pick.hadith.ar.text + '»'; tr = pick.hadith.en;
    ref = pick.hadith.ar.grade + tx.sourceSep + pick.hadith.ar.source + tx.sourceSep + 'HadeethEnc.com';
  }
  $('#daily-label').textContent = ayahTurn ? tx.ayah : tx.hadith;
  const el = $('#daily-ar');
  if (el.textContent !== ar) {
    el.textContent = ar;
    el.className = 'daily-ar' + (ar.length > 230 ? ' longer' : ar.length > 120 ? ' long' : '');
  }
  $('#daily-tr').textContent = rtl() ? '' : tr;
  $('#daily-ref').textContent = ref;
}

// MARK: - The rows

function renderShelf() {
  const tx = t();
  const tiles = appList.map((a) => {
    const initial = html((a.title || '?').trim().charAt(0).toUpperCase());
    const ic = a.icon ? `<img src="${html(a.icon)}" alt="">` : initial;
    const bg = a.icon ? 'transparent' : html(a.color || '#24415E');
    return `<div class="app" data-app="${html(a.id)}"><div class="ic" style="background:${bg}">${ic}</div><span>${html(a.title)}</span></div>`;
  });
  tiles.push(`<div class="app" data-act="tv-settings"><div class="ic">${svg('gear')}</div><span>${html(tx.tvSettings)}</span></div>`);
  $('#shelf').innerHTML = tiles.join('');
  // An icon that doesn't load becomes the app's initial on its colour.
  Array.prototype.forEach.call($('#shelf').querySelectorAll('img'), (img) => {
    img.onerror = () => {
      const box = img.parentNode, tile = box.parentNode;
      box.style.background = '#24415E';
      box.textContent = (tile.textContent || '?').trim().charAt(0).toUpperCase();
    };
  });
  place();
}

function renderTimesCard(now) {
  const el = $('#times-card');
  if (!el || !plan) return;
  const next = nextPrayer(plan, now || new Date());
  el.innerHTML = plan.today.map(([k, v]) => {
    const cls = k === next[0] && +v === +next[1] ? ' class="is-next"' : '';
    return `<span${cls}>${html(name(k, v))}</span><b${cls}>${html(at(v))}</b>`;
  }).join('');
}

function renderToday() {
  if (!city) return;
  const tx = t(), q = qibla(city);
  let wx = `<div class="card" data-act="none"><small>${html(tx.weather)} · ${html(cityName())}</small><p class="sub">…</p></div>`;
  if (weather && weather.current) {
    const c = weather.current, d = weather.daily, kind = wxKind(c.weather_code);
    const days = [];
    for (let i = 1; i < Math.min(4, d.time.length); i++) {
      const date = new Date(d.time[i] + 'T12:00:00Z');
      days.push(`<div>${html(tx.day(date))}<span class="wx">${svg(wxIcon(wxKind(d.weather_code[i]), true))}</span><b>${Math.round(d.temperature_2m_max[i])}°</b></div>`);
    }
    wx = `<div class="card" data-act="none"><small>${html(tx.weather)} · ${html(cityName())}</small>
      <div style="display:flex;align-items:center;gap:1.2vw"><span class="wx">${svg(wxIcon(kind, c.is_day))}</span><p class="big">${Math.round(c.temperature_2m)}°</p></div>
      <b>${html(tx.wx[kind])}</b><p class="sub">${html(tx.hiLo(Math.round(d.temperature_2m_max[0]), Math.round(d.temperature_2m_min[0])))}</p>
      <div class="days">${days.join('')}</div></div>`;
  }
  const times = `<div class="card" data-act="none"><small>${html(tx.times)}</small><div id="times-card" class="times-list"></div></div>`;
  const qib = `<div class="card" data-act="none"><small>${html(tx.qibla)}</small><span class="ic">${svg('compass')}</span>
    <p class="big">${q.deg}°</p><p class="sub">${html(tx.fromNorth)}<br>${html(tx.toMakkah(q.km))}</p></div>`;
  const net = netClean === null ? '' : `<div class="card" data-act="none"><small>${html(tx.net)}</small><span class="ic">${svg('shield')}</span>
    <b class="${netClean ? 'mint' : ''}">${html(netClean ? tx.netClean : tx.netAds)}</b><p class="sub">${html(netClean ? tx.netOk : tx.netHow)}</p></div>`;
  // In Ramadan the day's fast leads the row: the day of the month, the iftar, the end of suhoor.
  const now = new Date(), h = hijri(now);
  let ram = '';
  if (h && h.m === 9) {
    const fajr = now < plan.today[0][1] ? plan.today[0][1] : plan.tomorrowFajr;
    ram = `<div class="card ramadan" data-act="none"><small>${html(tx.ramadan)}</small><span class="ic">${svg('crescent')}</span>
      <b>${html(tx.ramadanDay(h.d))}</b><div class="times-list"><span>${html(tx.iftarChip)}</span><b>${html(at(plan.today[4][1]))}</b>
      <span>${html(tx.suhoorChip)}</span><b>${html(at(fajr))}</b></div><p class="sub">${html(tx.taraweeh)}</p></div>`;
  }
  $('#today').innerHTML = ram + wx + times + qib + net;
  renderTimesCard();
  place();
}

function renderPrefs() {
  const tx = t();
  const pref = (act, icon, label, value) => `<div class="card pref" data-act="${act}"><span class="ic">${svg(icon)}</span><span><small>${html(label)}</small><b>${html(value)}</b></span></div>`;
  $('#prefs').innerHTML = [
    pref('city', 'pin', tx.city, cityName()),
    pref('lang', 'lang', tx.lang, tx.langName),
    pref('sound', 'sound', tx.sound, sound ? tx.on : tx.off),
    pref('night', 'moon', tx.night, nightOn ? tx.auto : tx.off),
    pref('lock', 'clock', tx.lock, lockOn ? tx.lockOn : tx.off),
    pref('iqama', 'clock', tx.iqamaSet, JSON.stringify(mosque.iqama) === JSON.stringify(mosqueSettings(null).iqama) && !mosque.jumuah ? tx.usual : tx.custom),
    pref('calc', 'compass', tx.calc, tx.short[plan ? plan.method : 'uaq']),
    pref('help', 'help', tx.help, tx.helpValue),
    pref('tv-settings', 'gear', tx.tvSettings, tx.open),
  ].join('');
  place();
}

// MARK: - Focus, by remote: the rows go up and down, the items in a row left and right.

const ROWS = ['apps', 'quran', 'today', 'settings'];
const ITEMS = ['#shelf .app', '#quran .card', '#today .card', '#prefs .card'];
const items = (r) => Array.prototype.slice.call(document.querySelectorAll(ITEMS[r]));
const shift = [0, 0];

function place() {
  Array.prototype.forEach.call(document.querySelectorAll('.focus'), (el) => el.classList.remove('focus'));
  const list = items(row);
  if (!list.length) return;
  col[row] = Math.max(0, Math.min(col[row], list.length - 1));
  list[col[row]].classList.add('focus');
  // The page slides to the row; the first row keeps the prayer board in view.
  const sec = document.querySelector(`.row[data-row="${ROWS[row]}"]`);
  const most = $('#page').offsetHeight - window.innerHeight;
  const y = row === 0 ? 0 : Math.min(sec.offsetTop - window.innerHeight * 0.06, Math.max(0, most));
  $('#page').style.transform = `translateY(${-y}px)`;
  // The apps and the Quran slide sideways so the focused one stays on screen.
  if (row < 2) {
    const shelf = row === 0 ? $('#shelf') : $('#quran'), el = list[col[row]], w = shelf.clientWidth;
    const start = rtl() ? w - el.offsetLeft - el.offsetWidth : el.offsetLeft, end = start + el.offsetWidth;
    if (col[row] === 0) shift[row] = 0;
    else if (end - shift[row] > w) shift[row] = end - w;
    else if (start < shift[row]) shift[row] = start;
    shelf.style.transform = `translateX(${rtl() ? shift[row] : -shift[row]}px)`;
  }
}

let hintTimer = 0;
function hint() {
  const tx = t(), el = $('#hint');
  el.textContent = [tx.hintApps, tx.hintQuran, tx.hintRows, tx.hintPrefs][row];
  el.classList.remove('gone');
  clearTimeout(hintTimer);
  hintTimer = setTimeout(() => el.classList.add('gone'), 6000);
}

function activate(el) {
  if (!el) return;
  const id = el.getAttribute('data-app'), act = el.getAttribute('data-act'), n = +el.getAttribute('data-surah');
  // In a TV's web browser the page can't open apps, and only the LG app opens the TV's settings: say where they are.
  if ((id && !apps.opens) || (act === 'tv-settings' && !apps.webos)) { toast(t().appsHere); return; }
  if (id) { stopQuran(); apps.launch(id); return; }
  // OK plays; OK on what is playing stops it.
  if (n) { const was = !!playing && playing.n === n; stopQuran(); if (!was) playQuran({ kind: 'surah', n, r: reciter }); return; }
  if (act === 'lucky') { if (!stopQuran()) { const sg = suggestion(new Date()); lucky = 0; playQuran({ kind: 'surah', n: sg.n, r: reciter }); } }
  else if (act === 'radio') { const was = !!playing && playing.kind === 'radio'; stopQuran(); if (!was) playQuran({ kind: 'radio' }); }
  else if (act === 'reciter') openReciters();
  else if (act === 'help') openHelp();
  else if (act === 'iqama') openIqama(0);
  else if (act === 'calc') openCalc(0);
  else if (act === 'tv-settings') apps.settings();
  else if (act === 'city') openPicker();
  else if (act === 'lang') { lang = rtl() ? 'en' : 'ar'; store.set('mp-tv-lang', lang); render(); }
  else if (act === 'sound') { sound = !sound; store.set('mp-tv-sound', sound ? '1' : '0'); unlockAudio(); renderPrefs(); }
  else if (act === 'night') { nightOn = !nightOn; store.set('mp-tv-night', nightOn ? '1' : '0'); renderPrefs(); }
  else if (act === 'lock') { lockOn = !lockOn; store.set('mp-tv-lock', lockOn ? '1' : '0'); renderPrefs(); }
}

// MARK: - The city list: the Kingdom's cities first, then the world's; Up and Down, OK, Back.

let pick = 0, pickRows = [], pickDone = null;
function openList(title, rows, current, done) {
  pickRows = rows; pickDone = done; pick = Math.max(0, current);
  $('#picker-h').textContent = title;
  $('#picker-keys').textContent = t().pickKeys;
  $('#picker-list').innerHTML = rows.map((r) => (r.group ? `<li class="group">${html(r.group)}</li>`
    : r.qa ? `<li class="qa"><b>${html(r.qa[0])}</b><small>${html(r.qa[1])}</small></li>` : `<li>${html(r.label)}</li>`)).join('');
  $('#picker').hidden = false;
  movePicker(0);
}
function openPicker() {
  const tx = t(), saved = store.get('mp-tv-city');
  const rows = [{ auto: true, label: tx.autoCity }, { group: tx.saudi }]
    .concat(SAUDI.map((p) => ({ p, label: rtl() ? p[1] : p[0] })))
    .concat([{ group: tx.world }])
    .concat(WORLD.map((p) => ({ p, label: rtl() ? p[1] : p[0] })));
  openList(tx.pickCity, rows, saved ? rows.findIndex((r) => r.p && r.p[0] === saved) : 0, (r) => {
    if (r.auto) store.set('mp-tv-city', null); else store.set('mp-tv-city', r.p[0]);
    resolveCity(ALL, '../assets/zones.json', store.get('mp-tv-city')).then((c) => { city = c; weather = null; render(); loadWeather(); });
  });
}
// Help: how to reach us (and that we reply), then the answers to what TV viewers ask most. OK or Back closes it.
function openHelp() {
  const tx = t(), ltr = (s) => '\u2066' + s + '\u2069';
  const rows = [{ label: tx.helpMail + ' · ' + ltr(CONTACT.email) }, { group: tx.helpReply }]
    .concat(tx.helpQA.map((qa) => ({ qa })));
  openList(tx.help, rows, 0, () => {});
}
// The mosque's timetable: the iqama after each Adhan and the Jumu'ah; the method, Asr and minutes. Each row opens
// its choices, and OK on one goes back to the list with the new value.
function saveMosque() {
  store.set(MOSQUE_KEY, JSON.stringify(mosque));
  plan = schedule(city, mosque); planDay = ymd(new Date());
  renderToday(); renderPrefs(); tick();
}
function methodName() {
  const tx = t();
  return mosque.method === 'auto' ? tx.autoMethod(tx.methods[plan ? plan.method : 'uaq']) : tx.methods[mosque.method];
}
function openIqama(at) {
  const tx = t(), keys = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];
  const rows = keys.map((k) => ({ k, label: tx.prayers[k] + ' · ' + (mosque.iqama[k] ? tx.mins(mosque.iqama[k]) : tx.noWait) }))
    .concat([{ k: 'jumuah', label: tx.jumuahL + ' · ' + (mosque.jumuah ? tx.mins(mosque.jumuah) : tx.noWait) }]);
  openList(tx.iqamaSet, rows, at, (r) => {
    const i = rows.indexOf(r), jum = r.k === 'jumuah', choices = jum ? JUMUAH_CHOICES : IQAMA_CHOICES;
    const cur = jum ? mosque.jumuah : mosque.iqama[r.k];
    openList(r.label.split(' · ')[0], choices.map((m) => ({ m, label: m ? tx.mins(m) : tx.noWait })), choices.indexOf(cur), (c) => {
      if (jum) mosque.jumuah = c.m; else mosque.iqama[r.k] = c.m;
      saveMosque(); openIqama(i);
    });
  });
}
function openCalc(at) {
  const tx = t(), keys = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'];
  const rows = [{ k: 'method', label: tx.methodL + ' · ' + methodName() }, { k: 'asr', label: tx.asrL + ' · ' + (mosque.asr === 'hanafi' ? tx.asrHanafi : tx.asrStd) },
    { group: tx.adjust }].concat(keys.map((k) => ({ k, off: true, label: tx.prayers[k] + ' · ' + tx.minsSigned(mosque.offsets[k]) })));
  openList(tx.calc, rows, at, (r) => {
    const i = rows.indexOf(r), back = () => { saveMosque(); openCalc(i); };
    if (r.k === 'method') {
      const opts = [{ v: 'auto', label: tx.autoMethod(tx.methods[schedule(city).method]) }].concat(Object.keys(METHODS).map((v) => ({ v, label: tx.methods[v] })));
      openList(tx.methodL, opts, opts.findIndex((o) => o.v === mosque.method), (o) => { mosque.method = o.v; back(); });
    } else if (r.k === 'asr') {
      const opts = [{ v: 'standard', label: tx.asrStd }, { v: 'hanafi', label: tx.asrHanafi }];
      openList(tx.asrL, opts, mosque.asr === 'hanafi' ? 1 : 0, (o) => { mosque.asr = o.v; back(); });
    } else if (r.off) {
      openList(tx.prayers[r.k], OFFSET_CHOICES.map((v) => ({ v, label: tx.minsSigned(v) })), OFFSET_CHOICES.indexOf(mosque.offsets[r.k]),
        (o) => { mosque.offsets[r.k] = o.v; back(); });
    }
  });
}
function openReciters() {
  openList(t().pickReciter, RECITERS.map((r) => ({ r, label: reciterName(r) })), RECITERS.indexOf(reciter), (x) => {
    reciter = x.r;
    store.set('mp-tv-reciter', reciter[0]);
    // A surah playing goes on in the new voice, from the start.
    if (playing && playing.kind === 'surah') playQuran({ kind: 'surah', n: playing.n, r: reciter }); else renderQuran();
  });
}
function movePicker(step) {
  let i = pick;
  do { i = Math.max(0, Math.min(pickRows.length - 1, i + step)); } while (pickRows[i].group && i > 0 && i < pickRows.length - 1 && step);
  if (pickRows[i].group) i = pick;
  pick = i;
  const lis = $('#picker-list').children;
  Array.prototype.forEach.call(lis, (li, k) => li.classList.toggle('on', k === pick));
  const list = $('#picker-list'), li = lis[pick];
  list.scrollTop = li.offsetTop - list.clientHeight / 2 + li.offsetHeight / 2;
}
function choosePicker() {
  $('#picker').hidden = true;
  pickDone(pickRows[pick]);
}

// MARK: - The rest

function render() {
  const tx = t(), now = new Date();
  document.documentElement.lang = lang;
  document.documentElement.dir = rtl() ? 'rtl' : 'ltr';
  $('#city').textContent = cityName();
  $('#date').textContent = new Intl.DateTimeFormat(rtl() ? 'ar-SA-u-ca-gregory-nu-latn' : 'en-GB', { timeZone: city.tz, weekday: 'long', day: 'numeric', month: 'long' }).format(now);
  try {
    $('#hijri').textContent = new Intl.DateTimeFormat((rtl() ? 'ar-SA' : 'en-GB') + '-u-ca-islamic-umalqura-nu-latn', { timeZone: city.tz, day: 'numeric', month: 'long', year: 'numeric' }).format(now);
  } catch (e) { $('#hijri').textContent = ''; }
  $('#next-label').textContent = tx.next;
  $('#remain-l').textContent = tx.remain;
  $('#net-l').textContent = tx.net;
  $('#net').textContent = tx.netClean;
  $('#apps-h').textContent = tx.apps;
  $('#quran-h').textContent = tx.quran;
  $('#today-h').textContent = tx.today;
  $('#settings-h').textContent = tx.settings;
  plan = schedule(city, mosque); planDay = ymd(now);
  lineKey = ''; mode = '';
  renderShelf();
  renderQuran();
  renderToday();
  renderPrefs();
  renderWeatherChip();
  tick();
}

function loadWeather() {
  const c = city;
  const url = 'https://api.open-meteo.com/v1/forecast?latitude=' + c.lat + '&longitude=' + c.lon +
    '&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4';
  getJSON(url).then((w) => { if (c === city) { weather = w; renderToday(); renderWeatherChip(); } }).catch(() => {});
}
function renderWeatherChip() {
  const ok = weather && weather.current;
  $('#chip-wx').hidden = !ok;
  if (!ok) return;
  const kind = wxKind(weather.current.weather_code);
  $('#wx-chip-l').textContent = t().wx[kind];
  $('#wx-chip').textContent = Math.round(weather.current.temperature_2m) + '°';
}

// "Clean · no ads" only when it's true: an ad server doesn't answer while an ordinary site does, which is what a
// blocking DNS (AdGuard, the router's or the TV's) does.
function checkNetwork() {
  if (typeof fetch !== 'function') return;
  const reach = (u) => fetch(u, { mode: 'no-cors', cache: 'no-store' }).then(() => true, () => false);
  Promise.all([reach('https://www.google.com/generate_204'), reach('https://googleads.g.doubleclick.net/pagead/id')])
    .then(([web, ads]) => {
      if (!web) return;
      netClean = !ads;
      $('#chip-net').hidden = !netClean;
      renderToday();
    });
}

// A TV plays sound only after a key press on the page: the first key primes the Adhan, silently.
function unlockAudio() {
  if (unlocked || !sound) return;
  const a = $('#adhan-audio');
  a.muted = true;
  const p = a.play();
  const done = () => { a.pause(); a.muted = false; a.currentTime = 0; unlocked = true; };
  if (p && p.then) p.then(done, () => { a.muted = false; }); else done();
}

// In a TV's web browser the first press also hides the browser bar (the webOS app is full screen already).
function fullScreen() {
  const d = document.documentElement, f = d.requestFullscreen || d.webkitRequestFullscreen;
  if (!f || document.fullscreenElement || document.webkitFullscreenElement) return;
  try { const p = f.call(d); if (p && p.catch) p.catch(() => {}); } catch (err) { /* not allowed here */ }
}

const BACK = { Escape: 1, Backspace: 1, GoBack: 1, BrowserBack: 1 };
const KEYS = { 37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok' };
addEventListener('keydown', (e) => {
  lastKey = Date.now();
  if (mode === 'lock') { e.preventDefault(); tick(); return; } // any key: back from the lock screen
  const back = BACK[e.key] || e.keyCode === 10009 || e.keyCode === 461;
  const k = back ? 'back' : KEYS[e.keyCode];
  if (!k) return;
  e.preventDefault();
  unlockAudio();
  fullScreen();
  lastKey = Date.now();
  // On the Adhan or night screen a key first wakes the home screen (OK first stops a playing Adhan).
  if (mode !== 'board') {
    if (k === 'ok' && stopAdhan()) { tick(); return; }
    wakeUntil = Date.now() + WAKE;
    tick(); hint();
    return;
  }
  wakeUntil = Math.max(wakeUntil, Date.now() + WAKE);
  if (!$('#picker').hidden) {
    if (k === 'up') movePicker(-1);
    else if (k === 'down') movePicker(1);
    else if (k === 'ok') choosePicker();
    else if (k === 'back') $('#picker').hidden = true;
    return;
  }
  if (k === 'back') {
    if (row > 0) { row = 0; place(); hint(); } else apps.back();
    return;
  }
  if (k === 'ok') { activate(items(row)[col[row]]); return; }
  if (k === 'up' || k === 'down') {
    row = Math.max(0, Math.min(ROWS.length - 1, row + (k === 'down' ? 1 : -1)));
  } else {
    const forward = (k === 'left') === rtl();
    col[row] += forward ? 1 : -1;
  }
  place(); hint();
});
// LG's Magic Remote points and clicks.
addEventListener('click', (e) => {
  unlockAudio();
  lastKey = Date.now();
  if (mode !== 'board') { wakeUntil = Date.now() + WAKE; tick(); return; }
  const li = e.target.closest ? e.target.closest('#picker-list li') : null;
  if (li && !li.classList.contains('group')) { pick = Array.prototype.indexOf.call(li.parentNode.children, li); choosePicker(); return; }
  const el = e.target.closest ? e.target.closest('.app, .card') : null;
  if (!el) return;
  for (let r = 0; r < ROWS.length; r++) {
    const i = items(r).indexOf(el);
    if (i >= 0) { row = r; col[r] = i; place(); activate(el); return; }
  }
});
addEventListener('resize', () => { lineKey = ''; place(); tick(); });

// Keep the screen on: the browser's wake lock where there is one; on an LG TV, the app answers the TV's screensaver
// request with "not now", as LG's video apps do.
function keepAwake() { try { if (navigator.wakeLock) navigator.wakeLock.request('screen').catch(() => {}); } catch (e) { /* not offered */ } }
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible') { stopQuran(); return; }
  keepAwake();
  // Back from an app: the apps may have changed, and the home screen starts at the top.
  row = 0; apps.list((l) => { appList = l; renderShelf(); });
});
function holdScreensaver() {
  if (!apps.webos) return;
  const svc = 'luna://com.webos.service.tvpower/power/', id = 'sa.zood.mosquepro.tv';
  const reg = new window.PalmServiceBridge();
  reg.onservicecallback = (msg) => {
    let r = {};
    try { r = JSON.parse(msg); } catch (e) { return; }
    if (r.state === 'Active' && r.timestamp) {
      const ans = new window.PalmServiceBridge();
      ans.call(svc + 'responseScreenSaverRequest', JSON.stringify({ clientName: id, ack: false, timestamp: r.timestamp }));
      window.__mpAns = ans;
    }
  };
  reg.call(svc + 'registerScreenSaverRequest', JSON.stringify({ subscribe: true, clientName: id }));
  window.__mpSaver = reg; // keeps the subscription alive
}

resolveCity(ALL, '../assets/zones.json', store.get('mp-tv-city')).then((c) => {
  city = c;
  render();
  hint();
  keepAwake();
  holdScreensaver();
  checkNetwork();
  loadWeather();
  apps.list((l) => { appList = l; renderShelf(); });
  setInterval(tick, 1000);
  setInterval(checkNetwork, 30 * MIN);
  setInterval(loadWeather, 30 * MIN);
  getJSON('daily.json').then((d) => { daily = d; tick(); }).catch(() => {});
});
