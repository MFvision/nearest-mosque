// The landing page's words and pictures, in English and Arabic. The page (app.js) reads only this; the stage
// follows each chapter's `stage` keys. Facts come from docs/10-features.md; keep them true when editing.
//
// stage: globe  { lon, lat } centre of the view ('you': the visitor's city, from the browser's time zone; haram: the
//               close-up of the Kaaba; fly: reach this chapter's view by flying out and back down) (spin: 1 turns the globe on its own; follow: 1 keeps the view 70° east of
//               the sun, on the evening side), zoom, lines (0..1, the five prayer lines), arcs (0..1, the Qibla
//               arcs), hours (simulated hours added across the chapter)
//        sky    t on the app's sky loop (0 Fajr … 1 Fajr again), mix (0 globe … 1 sky), weather ('rain': the sky
//               under rain, as with Sky follows the weather on; its streaks fall only without Reduce Motion)
// media: phones (iPhone screenshots, in the iPhone frame), tv (a TV, with a smaller one in front for inset), panel (a
// settings card drawn from the chapter's own `panel` words), watches, devices; paths are under assets/<lang>/.

export const links = {
  // Set the App Store link when the listing is live; until then the button reads "Coming soon". Mosque Pro is on
  // Apple devices and any TV's browser; it has no Android, Windows or Linux build.
  appStore: null,
  tv: 'tv/',
};

const chapters = [
  { id: 'world', stage: { lon: 34, lat: 14, zoom: 1, lines: 0, arcs: 0, mix: 0, hours: 0, follow: 0, spin: 1 } },
  { id: 'lines', stage: { lon: 30, lat: 12, zoom: 1, lines: 1, arcs: 0, mix: 0, hours: 4, follow: 0, spin: 1 } },
  { id: 'qibla', stage: { lon: 'you', lat: 'you', zoom: 1.5, lines: 0, arcs: 1, mix: 0, hours: 0, follow: 0, spin: 0, real: 1 } },
  // From the visitor's city to Makkah, as a flight: out, across, then down to the Kaaba.
  { id: 'kaaba', stage: { lon: 39.8262, lat: 21.4225, zoom: 27000, arcs: 1, haram: 0, fly: 1 } },
  { id: 'sky', stage: { mix: 1, t: 0.03, fly: 0 }, media: [{ phone: 'iphone/01-prayer', clip: 'clips/01-header' }] },
  // What Mosque Pro adds: the Quran, the TV screen, Clean network. Pictures of the TV screen are taken from website/tv/.
  { id: 'quran', stage: { mix: 1, t: 0.08 }, media: [{ tv: 'screen/03-quran' }] },
  { id: 'screen', stage: { mix: 1, t: 0.13 }, media: [{ tv: 'screen/01-board', inset: 'screen/02-iqama' }] },
  { id: 'clean', stage: { mix: 1, t: 0.19 }, media: [{ panel: 'clean' }] },
  { id: 'weather', stage: { mix: 1, t: 0.25, weather: 'rain' }, media: [{ phone: 'iphone/02-prayer-weather' }] },
  { id: 'mosque', stage: { mix: 1, t: 0.31 }, media: [{ phone: 'iphone/09-mosques-radar' }, { phone: 'iphone/03-mosques-map' }] },
  { id: 'ask', stage: { mix: 1, t: 0.37 }, media: [{ phone: 'iphone/04-ask', clip: 'clips/03-search' }] },
  { id: 'library', stage: { mix: 1, t: 0.43 }, media: [{ phone: 'iphone/05-classics' }] },
  { id: 'adhan', stage: { mix: 1, t: 0.5 }, media: [{ phone: 'iphone/08-audio' }] },
  { id: 'glance', stage: { mix: 1, t: 0.6 }, media: [{ phone: 'iphone/06-widgets' }, { phone: 'iphone/07-island' }] },
  { id: 'watch', stage: { mix: 1, t: 0.68 }, media: [{ watch: 'watch/01-watch-prayer' }, { watch: 'watch/03-watch-spirit' }, { watch: 'watch/04-watch-tasbih' }] },
  { id: 'devices', stage: { mix: 1, t: 0.76 }, media: [{ devices: ['iphone/10-prayer-dark', 'ipad/01-prayer', 'mac/01-prayer', 'screen/01-board', 'vision/01-prayer'] }] },
  { id: 'help', stage: { mix: 1, t: 0.83 }, media: [{ panel: 'help' }] },
  { id: 'privacy', stage: { mix: 1, t: 0.9 } },
  { id: 'get', stage: { lon: 40, lat: 18, zoom: 1, lines: 1, arcs: 0, mix: 0, hours: 3, follow: 0, spin: 1, haram: 0, real: 0 } },
];

const en = {
  dir: 'ltr',
  lang: 'en',
  switchTo: 'العربية',
  title: 'Mosque Pro',
  skip: 'Skip to everything inside',
  prayers: { fajr: 'Fajr', dhuhr: 'Dhuhr', asr: 'Asr', maghrib: 'Maghrib', isha: 'Isha' },
  makkah: 'Makkah',
  scroll: 'Scroll',
  sound: ['Hear the Adhan', 'Adhan on'],
  daynight: ['Day', 'Night'],
  hud: { legend: 'Each light is a city at its prayer time', clock: 'Now in Makkah', styles: ['Earth', 'Dots'], you: 'You', madinah: 'Madinah' },
  board: { world: 'Next prayer', lines: 'Five prayers', qibla: 'Qibla', kaaba: 'Makkah', sky: 'Your sky', quran: 'The Quran', screen: 'TV screen', clean: 'Clean network', weather: 'Weather', mosque: 'Nearest mosque', ask: 'Ask', library: 'Library', adhan: 'Adhan', glance: 'Widgets and Siri', watch: 'Apple Watch', devices: 'Every screen', help: 'Help', privacy: 'No ads', get: 'Download' },
  boardLabel: 'Story',
  soon: 'Coming soon',
  stores: { appStore: ['Download on the', 'App Store'], tv: ['Open on your TV', 'TV screen'] },
  storesMore: {},
  chapters: {
    world: {
      eyebrow: 'Mosque Pro',
      title: 'Your next prayer, wherever you are.',
      body: 'Prayer times, the Qibla and the nearest mosque, with the Quran to listen to, a prayer screen for your TV and a network without ads. No ads in the app, ever.',
      // The title becomes the visitor's next prayer and the time left (app.js); these are its words.
      nextIn: 'in', nextAt: 'at {time} in {city}',
      cta: 'Get the app',
    },
    lines: {
      eyebrow: 'Five times a day',
      title: 'Each prayer moves around the world [[with the sun]].',
      body: 'Each light is a city reaching its prayer time. Fajr travels with the dawn and Maghrib with the sunset, following the sun’s real position today.',
      points: ['13,090 cities built in, no internet needed', '11 calculation methods, suggested for your country'],
    },
    qibla: {
      eyebrow: 'Qibla',
      title: 'From anywhere, [[one direction]].',
      body: 'Turn your phone and the arrow leads you to the Kaaba. When you face it, the Qibla lights up and you feel a gentle vibration.',
      points: ['Bearing and distance to the Kaaba', 'Without a compass, it is drawn from north and says so'],
    },
    kaaba: {
      eyebrow: 'Makkah',
      title: 'Every line meets at the [[Kaaba]].',
      body: 'Wherever you pray, your Qibla is the same. Mosque Pro shows you its direction from where you stand.',
    },
    sky: {
      eyebrow: 'Prayer times',
      title: 'Your [[next prayer]], on the sky of the hour.',
      body: 'The header shows the next prayer and the time left. Its sky turns pink at Fajr, gold at Asr and violet at Maghrib.',
      points: ['Today’s six times, each with its own bell', 'Hijri and Gregorian dates side by side', 'A verse and a hadith of the day'],
    },
    quran: {
      eyebrow: 'The Quran · New',
      title: 'Press play, and [[the Quran]] begins.',
      body: '“Play for me” picks the surah for the moment: Al-Kahf on Friday, Al-Mulk before sleep, and on other days one of the surahs people love to hear. Or turn on the Quran radio, live day and night.',
      points: ['7 reciters, among them Mishary Alafasy, Maher Al-Muaiqly and Abdulrahman Al-Sudais', 'Keeps playing with the screen off, and stops by itself at the next Adhan', 'A daily reminder to read, even a few verses'],
    },
    screen: {
      eyebrow: 'TV screen · New',
      title: 'A prayer screen for [[your home or your mosque]].',
      body: 'Open the TV screen in any TV’s browser, or the Mosque Pro app on Apple TV. Today’s times, the countdown, and at each Adhan a full screen with the countdown to the iqama.',
      points: ['Set the iqama for each prayer, and Jumu’ah, the way your mosque does it', 'Your calculation method, Hanafi Asr and minute adjustments', 'The Quran, the weather, the Qibla, and Ramadan and Friday on screen', 'After a few minutes, the Kaaba fills the screen with the time'],
    },
    clean: {
      eyebrow: 'Clean network · New',
      title: 'Ads and trackers [[blocked]] in every app.',
      body: 'Turn on Clean network and your iPhone or iPad looks up websites through AdGuard’s free DNS, which refuses the addresses of ads and trackers. It works in every app and browser, not only in Mosque Pro.',
      points: ['One switch in Settings, then one step in iOS Settings', 'AdGuard sees the names of the sites you look up, not what is on them', 'Ads served from the same address as the video, as on YouTube, can’t be blocked this way'],
      panel: { title: 'Clean network', on: 'On: ads and trackers are blocked.', rows: [['Ads', 'Blocked'], ['Trackers', 'Blocked'], ['Your websites and apps', 'Open']] },
    },
    weather: {
      eyebrow: 'Sky follows the weather',
      title: 'A sky that [[follows your city’s weather]].',
      body: 'The sky shows the weather in your prayer city (clouds, rain, thunderstorms, fog or snow), with the temperature and the wind.',
      points: ['Off until you turn it on', 'While the app is open, about every half hour, the city’s location rounded to about 1 km goes to MET Norway'],
      // The credit MET Norway's licence asks for, linked to its licensing page (app.js renders [text, link]).
      credit: ['Weather: based on data from MET Norway', 'https://www.met.no/en/free-meteorological-data/Licensing-and-crediting'],
    },
    mosque: {
      eyebrow: 'Nearest mosque',
      title: 'See which way the [[mosque]] is, and how far.',
      body: 'The compass shows each mosque at its real direction. Switch to the map for walking time and directions.',
      points: ['Directions in Apple Maps or Google Maps', 'Built-in mosque data, plus Apple Maps when online search is on'],
    },
    ask: {
      eyebrow: 'Ask',
      title: 'Ask about Islam and see the [[source]] of every answer.',
      body: 'Answers quote the books on your phone and list each source. A scholar’s fatwa comes first when one matches.',
      points: ['25 common questions in 22 languages, labelled as awaiting scholar review', 'Written on your phone by Apple’s on-device model. Questions never go to a cloud AI.'],
      note: 'Answers quote books. They are not a fatwa.',
    },
    library: {
      eyebrow: 'Library',
      title: '[[99 classical books]] you can read without the internet.',
      body: 'Download a volume once and read it offline, next to fatwas, lectures and translations of the Qur’an.',
      points: ['Creed, Hadith, Fiqh, Tafsir, Sirah and more', 'Each volume is checked before it opens'],
    },
    adhan: {
      eyebrow: 'Adhan and reminders',
      title: 'The Adhan from [[Makkah or Madinah]], with a reminder before it.',
      body: 'Choose the Adhan of Masjid al-Haram or al-Masjid an-Nabawi for each prayer. Reminders come 10 or 5 minutes before, spoken in Arabic or English.',
      points: ['After each Adhan, the countdown to the iqama', 'A Fajr alarm, and reminders on Friday for Al-Kahf and Jumu’ah', 'Silent during prayer: a Shortcut you run with one tap from the Adhan notification'],
    },
    glance: {
      eyebrow: 'Widgets, Live Activities and Siri',
      title: 'Check the time left [[without opening the app]].',
      body: 'Ten kinds of widgets for the Home Screen and Lock Screen. The countdown waits in the Dynamic Island, and Siri tells you the next prayer.',
      points: ['“When is the next prayer in Mosque Pro?”', 'The Qibla compass in the Dynamic Island'],
    },
    watch: {
      eyebrow: 'Apple Watch',
      title: 'Your prayers [[on your wrist]].',
      body: 'The watch shows the next prayer, finds the Qibla with a tap on your wrist, and counts tasbih on the Digital Crown.',
      points: ['A companion that brightens as you mark your prayers', 'Works even when your iPhone stays behind'],
    },
    devices: {
      eyebrow: 'Every screen',
      title: 'On [[iPhone, iPad, Mac, Apple TV and Vision Pro]], and any TV.',
      body: 'One app for your Apple devices, and the TV screen in the browser of any smart TV.',
      points: ['36 interface languages, 6 of them right to left', 'The TV screen needs no app: open nearmosque.net/mosque-pro/tv in the TV’s browser'],
      labels: ['iPhone', 'iPad', 'Mac', 'TV', 'Vision Pro'],
    },
    help: {
      eyebrow: 'Help Center · New',
      title: 'Answers in the app, and [[a person]] when you need one.',
      body: 'Settings → Help Center answers the common questions in your language. Report a problem or suggest an idea from there, and we reply by email.',
      points: ['See what you sent us, in the app', 'Or email us at app@vision.bz'],
      actions: [['Email us', 'mailto:app@vision.bz']],
      panel: { title: 'Help Center', rows: [['Answers', '›'], ['Report a problem', '›'], ['Suggest an idea', '›'], ['What you sent', '›']] },
    },
    privacy: {
      eyebrow: 'No ads, no account',
      title: 'No account, no ads, [[no tracking]].',
      body: 'Mosque Pro shows no ads, anywhere. Prayer times, the Qibla and your answers are worked out on your device. There is no account to make and nothing about you is sent to us.',
      points: ['Prayer times and Qibla work offline', 'Online only for mosque search (with your location rounded to about 1 km), maps, the Quran you play, the downloads you choose, the weather sky and Clean network if you turn them on', 'Read the privacy policy for each service and what it receives'],
      actions: [['Privacy policy', 'privacy.html']],
    },
    get: {
      eyebrow: 'Mosque Pro',
      title: 'Every prayer [[on time]], wherever you are.',
      body: 'No account to create and no ads to sit through. The TV screen is ready now; the app is coming to the App Store.',
    },
  },
  index: {
    title: 'Everything inside',
    intro: 'What the app does today, device by device.',
    groups: [
      ['Prayer times', ['Next prayer, countdown and the sky of the hour', 'Today’s six times with a bell for each', '11 calculation methods, Hanafi or standard Asr, high-latitude rules', 'Minute adjustments for each time and a Hijri adjustment', 'Estimated times where the sun does not set or rise', 'A verse and a hadith of the day']],
      ['The Quran', ['Play for me: Al-Kahf on Friday, Al-Mulk before sleep, a loved surah on other days', 'Quran radio, live day and night', '7 reciters, with Alafasy as a fallback when a file won’t load', 'Keeps playing in the background and stops at the next Adhan', 'A daily reading reminder']],
      ['TV screen', ['In any TV’s browser, and the Mosque Pro app on Apple TV', 'Today’s times, the countdown and a full-screen Adhan', 'The countdown to the iqama, set for each prayer and Jumu’ah', 'Calculation method, Asr and minute adjustments', 'The Quran row, the weather, the Qibla, Ramadan and Friday', 'The Kaaba lock screen after a few minutes']],
      ['Clean network', ['Ads and trackers blocked in every app, with AdGuard’s free DNS', 'iPhone and iPad', 'Off until you turn it on']],
      ['Sky follows the weather', ['Clouds, rain, thunderstorms, fog and snow on the sky of the hour', 'The temperature and the wind, with an icon for the weather', 'Off until you turn it on; then the city’s location, rounded to about 1 km, goes to MET Norway']],
      ['Qibla', ['Live compass that lights up and taps when you face the Kaaba', 'Bearing and distance to the Kaaba', 'Drawn from north on devices without a compass']],
      ['Nearest mosque', ['Compass radar with each mosque at its real direction', 'Map with walking time, calls and websites', 'Directions in Apple Maps or Google Maps', 'Built-in mosque data, plus Apple Maps when online search is on']],
      ['Ask and the library', ['Answers quoting books on your phone, each with its source', 'A scholar’s fatwa first when one matches', '25 common questions in 22 languages, awaiting scholar review', '99 classical books in 450 volumes, read offline', 'IslamHouse, HadeethEnc, Qur’an translations and tafsir, Ibn Baz fatwas', 'Read answers aloud with your device’s voices']],
      ['Adhan and reminders', ['Adhan of Masjid al-Haram or al-Masjid an-Nabawi', 'Reminders 10 and 5 minutes before, spoken in Arabic or English', 'The countdown to the iqama after each Adhan', 'Fajr alarm, Surat Al-Kahf and Jumu’ah on Friday, Ramadan suhoor and iftar']],
      ['Widgets and Live Activities', ['10 widget kinds for the Home Screen, Lock Screen and Mac desktop', 'Next-prayer countdown on the Lock Screen and in the Dynamic Island', 'Qibla compass in the Dynamic Island', '5 styles that colour the app and every widget']],
      ['Siri and Shortcuts', ['“When is the next prayer in Mosque Pro?”', '“Where is the nearest mosque in Mosque Pro?”', 'Directions, Ask, prayer times and the Qibla by voice or the Action button']],
      ['Apple Watch', ['Next prayer on the living sky', 'Qibla compass with a tap on your wrist', 'Spiritual health: mark your prayers and see your week', 'Tasbih on the Digital Crown', '7 complications in 15 sizes']],
      ['Help Center', ['Answers to common questions in the app', 'Report a problem or suggest an idea', 'See what you sent', 'We reply by email']],
      ['Privacy', ['No ads, no accounts, no tracking, no cloud AI', 'Prayer times, Qibla and answers worked out on the device', 'Online only for mosque search, maps, the Quran, the downloads you choose, and the weather sky and Clean network if you turn them on']],
    ],
  },
  footer: {
    privacy: 'Privacy policy',
    terms: 'Terms of use',
    tv: 'TV screen',
    contact: 'Contact us',
  },
};

const ar = {
  dir: 'rtl',
  lang: 'ar',
  switchTo: 'English',
  title: 'مسجد وأكثر',
  skip: 'انتقل إلى كل ما في التطبيق',
  prayers: { fajr: 'الفجر', dhuhr: 'الظهر', asr: 'العصر', maghrib: 'المغرب', isha: 'العشاء' },
  makkah: 'مكة المكرمة',
  scroll: 'مرّر',
  sound: ['استمع إلى الأذان', 'الأذان يعمل'],
  daynight: ['نهار', 'ليل'],
  hud: { legend: 'كل ضوءٍ مدينةٌ حان وقت صلاتها', clock: 'الآن في مكة', styles: ['الأرض', 'النقاط'], you: 'أنت هنا', madinah: 'المدينة المنورة' },
  board: { world: 'صلاتك القادمة', lines: 'الصلوات الخمس', qibla: 'القبلة', kaaba: 'مكة المكرمة', sky: 'سماؤك', quran: 'القرآن الكريم', screen: 'شاشة التلفاز', clean: 'شبكة نظيفة', weather: 'الطقس', mosque: 'أقرب مسجد', ask: 'اسأل', library: 'المكتبة', adhan: 'الأذان والتذكير', glance: 'الأدوات وSiri', watch: 'Apple Watch', devices: 'كل الشاشات', help: 'المساعدة', privacy: 'بلا إعلانات', get: 'التحميل' },
  boardLabel: 'القصة',
  soon: 'قريبًا',
  stores: { appStore: ['حمّله من', 'App Store'], tv: ['افتحها على تلفازك', 'شاشة التلفاز'] },
  storesMore: {},
  chapters: {
    world: {
      eyebrow: 'مسجد وأكثر',
      title: 'صلاتك القادمة، أينما كنت.',
      body: 'مواقيت الصلاة والقبلة وأقرب مسجد، ومعها القرآن تستمع إليه، وشاشة صلاة لتلفازك، وشبكة بلا إعلانات. ولا إعلانات في التطبيق أبدًا.',
      nextIn: 'بعد', nextAt: 'الساعة {time} في {city}',
      cta: 'حمّل التطبيق',
    },
    lines: {
      eyebrow: 'خمس مرات في اليوم',
      title: 'كل صلاةٍ تسري حول العالم [[مع الشمس]].',
      body: 'كل ضوءٍ مدينةٌ حان وقت صلاتها. الفجر يسير مع الشروق الأول، والمغرب مع الغروب، تبعًا لموضع الشمس الحقيقي اليوم.',
      points: ['13,090 مدينة مدمجة في التطبيق، دون حاجة إلى الإنترنت', '11 طريقة حساب، يُقترح منها ما يناسب بلدك'],
    },
    qibla: {
      eyebrow: 'القبلة',
      title: 'من أي مكان، [[اتجاهٌ واحد]].',
      body: 'أدِر هاتفك يُرشدك السهم إلى الكعبة، فإذا استقبلتها أضاءت القبلة وشعرت باهتزازةٍ خفيفة.',
      points: ['الاتجاه والمسافة إلى الكعبة', 'وفي الأجهزة التي لا بوصلة فيها تُحدَّد القبلة انطلاقًا من الشمال، مع التنبيه إلى ذلك'],
    },
    kaaba: {
      eyebrow: 'مكة المكرمة',
      title: 'كل الخطوط تلتقي عند [[الكعبة]].',
      body: 'أينما صلّيت فقبلتك واحدة، و«مسجد وأكثر» يدلّك على اتجاهها من حيث تقف.',
    },
    sky: {
      eyebrow: 'مواقيت الصلاة',
      title: '[[صلاتك القادمة]] تحت سماء ساعتك.',
      body: 'يعرض لك أعلى الشاشة الصلاة القادمة والوقت المتبقي لها، تحت سماءٍ يتبدّل لونها مع يومك: وردية عند الفجر، وذهبية عند العصر، وبنفسجية عند المغرب.',
      points: ['مواقيت اليوم الستة، لكلٍّ منها تنبيه', 'التاريخان الهجري والميلادي جنبًا إلى جنب', 'آية اليوم وحديث اليوم'],
    },
    quran: {
      eyebrow: 'القرآن الكريم · جديد',
      title: 'اضغط تشغيل، ويبدأ [[القرآن]].',
      body: '«شغّل لي» يختار السورة المناسبة للوقت: الكهف يوم الجمعة، والملك قبل النوم، وفي سائر الأيام سورةً مما يحب الناس سماعه. أو شغّل إذاعة القرآن الكريم، بثًّا مباشرًا على مدار الساعة.',
      points: ['7 قرّاء، منهم مشاري العفاسي وماهر المعيقلي وعبدالرحمن السديس', 'يستمر والشاشة مطفأة، ويتوقف من تلقاء نفسه عند الأذان التالي', 'تذكير يومي بالقراءة، ولو آيات'],
    },
    screen: {
      eyebrow: 'شاشة التلفاز · جديد',
      title: 'شاشة صلاة [[لبيتك أو لمسجدك]].',
      body: 'افتح شاشة التلفاز في متصفح أي تلفاز، أو تطبيق «مسجد وأكثر» على Apple TV. مواقيت اليوم والعدّ التنازلي، وعند كل أذان شاشة كاملة مع العدّ التنازلي للإقامة.',
      points: ['اضبط الإقامة لكل صلاة وللجمعة كما يفعل مسجدك', 'طريقة الحساب، والعصر على المذهب الحنفي، وتعديل الدقائق', 'القرآن والطقس والقبلة، ورمضان والجمعة على الشاشة', 'بعد دقائق تملأ الكعبةُ الشاشة مع الساعة'],
    },
    clean: {
      eyebrow: 'شبكة نظيفة · جديد',
      title: 'الإعلانات والمتتبّعات [[محجوبة]] في كل تطبيق.',
      body: 'فعّل «شبكة نظيفة» فيبحث آيفونك أو آيبادك عن المواقع عبر DNS المجاني من AdGuard، الذي يرفض عناوين الإعلانات والمتتبّعات. يعمل في كل التطبيقات والمتصفحات، لا في «مسجد وأكثر» وحده.',
      points: ['مفتاح واحد في الإعدادات، ثم خطوة واحدة في إعدادات iOS', 'يرى AdGuard أسماء المواقع التي تبحث عنها، لا ما فيها', 'الإعلانات التي تأتي من عنوان الفيديو نفسه، كما في YouTube، لا تُحجب بهذه الطريقة'],
      panel: { title: 'شبكة نظيفة', on: 'مفعّلة: الإعلانات والمتتبّعات محجوبة.', rows: [['الإعلانات', 'محجوبة'], ['المتتبّعات', 'محجوبة'], ['مواقعك وتطبيقاتك', 'مفتوحة']] },
    },
    weather: {
      eyebrow: 'سماء حسب الطقس',
      title: 'سماءٌ [[تتبدّل مع طقس مدينتك]].',
      body: 'تُظهر السماء طقس مدينة الصلاة، من غيومٍ ومطرٍ وعواصف رعدية وضبابٍ وثلج، مع درجة الحرارة والرياح.',
      points: ['لا تعمل حتى تفعّلها', 'أثناء فتح التطبيق، مرة كل نصف ساعة تقريبًا، يُرسل موقع المدينة مقرّبًا إلى نحو كيلومتر إلى MET Norway'],
      credit: ['الطقس: استنادًا إلى بيانات MET Norway', 'https://www.met.no/en/free-meteorological-data/Licensing-and-crediting'],
    },
    mosque: {
      eyebrow: 'أقرب مسجد',
      title: 'اعرف اتجاه [[أقرب مسجد]] وكم يبعد عنك.',
      body: 'تعرض البوصلة كل مسجدٍ في اتجاهه الحقيقي، وتعرض الخريطة وقت المشي إليه وطريق الوصول.',
      points: ['الاتجاهات في خرائط Apple أو Google Maps', 'بيانات مساجد مدمجة، ومعها خرائط Apple عند تفعيل البحث على الإنترنت'],
    },
    ask: {
      eyebrow: 'اسأل',
      title: 'اسأل عن دينك، واعرف [[مصدر]] كل إجابة.',
      body: 'تُستمدّ الإجابات من الكتب المحفوظة في هاتفك، مع ذكر مصدر كل نص، وتتقدّمها فتوى أهل العلم إن وُجدت فتوى مطابقة.',
      points: ['25 سؤالًا شائعًا بـ22 لغة، عليها علامة «بانتظار مراجعة العلماء»', 'تُكتب على هاتفك بنموذج Apple المدمج في الجهاز، ولا تُرسل أسئلتك إلى ذكاءٍ اصطناعي سحابي'],
      note: 'الإجابات تقتبس من الكتب، وليست فتوى.',
    },
    library: {
      eyebrow: 'المكتبة',
      title: '[[99 كتابًا من أمهات الكتب]] تقرؤها دون إنترنت.',
      body: 'نزّل المجلد مرة واحدة واقرأه دون اتصال، إلى جانب الفتاوى والمحاضرات وترجمات معاني القرآن.',
      points: ['العقيدة والحديث والفقه والتفسير والسيرة وغيرها', 'يُتحقق من كل مجلد قبل فتحه'],
    },
    adhan: {
      eyebrow: 'الأذان والتذكير',
      title: 'الأذان من [[مكة أو المدينة]]، وتذكيرٌ قبله.',
      body: 'اختر أذان المسجد الحرام أو المسجد النبوي لكل صلاة. ويأتيك التذكير قبل الصلاة بعشر دقائق أو خمس، بصوتٍ عربي أو إنجليزي.',
      points: ['بعد كل أذان، العدّ التنازلي للإقامة', 'منبّه للفجر، وتذكير يوم الجمعة بسورة الكهف وصلاة الجمعة', '«الصمت أثناء الصلاة»: اختصارٌ تشغّله بلمسة من تنبيه الأذان'],
    },
    glance: {
      eyebrow: 'الأدوات والأنشطة المباشرة وSiri',
      title: 'اعرف الوقت المتبقي [[دون أن تفتح التطبيق]].',
      body: 'عشرة أنواع من الأدوات للشاشة الرئيسية وشاشة القفل. والعدّ التنازلي ينتظرك في الجزيرة الديناميكية، وSiri يخبرك بالصلاة القادمة.',
      points: ['«متى الصلاة القادمة في مسجد وأكثر؟»', 'بوصلة القبلة في الجزيرة الديناميكية'],
    },
    watch: {
      eyebrow: 'Apple Watch',
      title: 'صلواتك [[على معصمك]].',
      body: 'تعرض الساعة الصلاة القادمة، وتجد القبلة بنقرةٍ على معصمك، وتعدّ التسبيح بالتاج الرقمي.',
      points: ['رفيقٌ يزداد إشراقًا كلما سجّلت صلواتك', 'تعمل حتى إن بقي الآيفون بعيدًا'],
    },
    devices: {
      eyebrow: 'على كل شاشة',
      title: 'على [[آيفون وآيباد وماك وآبل تي في وفيجن برو]]، وأي تلفاز.',
      body: 'تطبيقٌ واحد لأجهزة Apple كلها، وشاشة التلفاز في متصفح أي تلفاز ذكي.',
      points: ['36 لغة للواجهة، منها 6 من اليمين إلى اليسار', 'شاشة التلفاز لا تحتاج تطبيقًا: افتح nearmosque.net/mosque-pro/tv في متصفح التلفاز'],
      labels: ['آيفون', 'آيباد', 'ماك', 'التلفاز', 'فيجن برو'],
    },
    help: {
      eyebrow: 'مركز المساعدة · جديد',
      title: 'إجابات في التطبيق، و[[شخصٌ يرد عليك]] حين تحتاج.',
      body: 'الإعدادات ← مركز المساعدة يجيب عن الأسئلة الشائعة بلغتك. ومن هناك أبلغ عن مشكلة أو اقترح فكرة، ونرد عليك بالبريد.',
      points: ['اطّلع على ما أرسلته إلينا، داخل التطبيق', 'أو راسلنا على app@vision.bz'],
      actions: [['راسلنا بالبريد', 'mailto:app@vision.bz']],
      panel: { title: 'مركز المساعدة', rows: [['الإجابات', '‹'], ['الإبلاغ عن مشكلة', '‹'], ['اقتراح فكرة', '‹'], ['ما أرسلته', '‹']] },
    },
    privacy: {
      eyebrow: 'بلا إعلانات ولا حساب',
      title: 'بلا حساب، بلا إعلانات، [[بلا تتبُّع]].',
      body: 'لا إعلانات في «مسجد وأكثر»، في أي مكان. تُحسب المواقيت والقبلة وإجاباتك على جهازك. لا حساب تنشئه، ولا يُرسل إلينا شيءٌ عنك.',
      points: ['المواقيت والقبلة تعمل دون إنترنت', 'الاتصال فقط للبحث عن المساجد (مع تقريب موقعك إلى نحو كيلومتر) والخرائط والقرآن الذي تشغّله وما تختار تنزيله، ولسماء الطقس و«شبكة نظيفة» إن فعّلتهما', 'اقرأ سياسة الخصوصية لتعرف كل خدمة وما يصلها'],
      actions: [['سياسة الخصوصية', 'privacy.html#ar']],
    },
    get: {
      eyebrow: 'مسجد وأكثر',
      title: 'كل صلاةٍ [[في وقتها]]، أينما كنت.',
      body: 'لا حساب تنشئه، ولا إعلانات تنتظرها. شاشة التلفاز جاهزة الآن، والتطبيق قادمٌ إلى App Store.',
    },
  },
  index: {
    title: 'كل ما في التطبيق',
    intro: 'ما يفعله التطبيق اليوم، جهازًا جهازًا.',
    groups: [
      ['مواقيت الصلاة', ['الصلاة القادمة والعدّ التنازلي وسماء الساعة', 'مواقيت اليوم الستة ولكلٍّ منها تنبيه', '11 طريقة حساب، والعصر على المذهب الحنفي أو الجمهور، وقواعد خطوط العرض العليا', 'تعديل كل وقت بالدقائق، وتعديل التاريخ الهجري', 'أوقات تقديرية حيث لا تغرب الشمس أو لا تشرق', 'آية اليوم وحديث اليوم']],
      ['القرآن الكريم', ['«شغّل لي»: الكهف يوم الجمعة، والملك قبل النوم، وسورة محبوبة في سائر الأيام', 'إذاعة القرآن الكريم، بثًّا مباشرًا على مدار الساعة', '7 قرّاء، والعفاسي بديلًا إن تعذّر تحميل الملف', 'يستمر في الخلفية ويتوقف عند الأذان التالي', 'تذكير يومي بالقراءة']],
      ['شاشة التلفاز', ['في متصفح أي تلفاز، وتطبيق «مسجد وأكثر» على Apple TV', 'مواقيت اليوم والعدّ التنازلي، والأذان بملء الشاشة', 'العدّ التنازلي للإقامة، مضبوطًا لكل صلاة وللجمعة', 'طريقة الحساب والعصر وتعديل الدقائق', 'صف القرآن والطقس والقبلة ورمضان والجمعة', 'شاشة الكعبة بعد دقائق']],
      ['شبكة نظيفة', ['حجب الإعلانات والمتتبّعات في كل التطبيقات، عبر DNS المجاني من AdGuard', 'آيفون وآيباد', 'متوقفة حتى تفعّلها']],
      ['سماء حسب الطقس', ['الغيوم والمطر والعواصف الرعدية والضباب والثلج على سماء الساعة', 'درجة الحرارة والرياح، مع رمزٍ لحالة الطقس', 'متوقفة حتى تفعّلها، وعندها يُرسل موقع المدينة مقرّبًا إلى نحو كيلومتر إلى MET Norway']],
      ['القبلة', ['بوصلة حيّة تضيء وتنقر حين تستقبل الكعبة', 'الاتجاه والمسافة إلى الكعبة', 'تُرسم من الشمال على الأجهزة التي لا بوصلة فيها']],
      ['أقرب مسجد', ['رادار يُظهر كل مسجد في اتجاهه الحقيقي', 'خريطة بوقت المشي والاتصال والمواقع', 'الاتجاهات في خرائط Apple أو Google Maps', 'بيانات مساجد مدمجة، ومعها خرائط Apple عند تفعيل البحث على الإنترنت']],
      ['اسأل والمكتبة', ['إجابات تقتبس من الكتب على هاتفك، مع مصدر كل منها', 'فتوى العالم أولًا إن وُجدت فتوى مطابقة', '25 سؤالًا شائعًا بـ22 لغة، بانتظار مراجعة العلماء', '99 كتابًا في 450 مجلدًا، تُقرأ دون اتصال', 'دار الإسلام وموسوعة الأحاديث وترجمات معاني القرآن والتفاسير وفتاوى ابن باز', 'قراءة الإجابات بصوتٍ مسموع بأصوات جهازك']],
      ['الأذان والتذكير', ['أذان المسجد الحرام أو المسجد النبوي', 'تذكير قبل الصلاة بعشر دقائق وخمس، بصوتٍ عربي أو إنجليزي', 'العدّ التنازلي للإقامة بعد كل أذان', 'منبّه الفجر، وسورة الكهف والجمعة يوم الجمعة، والسحور والإفطار في رمضان']],
      ['الأدوات والأنشطة المباشرة', ['10 أنواع من الأدوات للشاشة الرئيسية وشاشة القفل وسطح مكتب الماك', 'عدّ تنازلي للصلاة القادمة على شاشة القفل وفي الجزيرة الديناميكية', 'بوصلة القبلة في الجزيرة الديناميكية', '5 أنماط تلوّن التطبيق وكل أدواته']],
      ['Siri والاختصارات', ['«متى الصلاة القادمة في مسجد وأكثر؟»', '«أين أقرب مسجد في مسجد وأكثر؟»', 'الاتجاهات والسؤال والمواقيت والقبلة بالصوت أو بزر الإجراءات']],
      ['Apple Watch', ['الصلاة القادمة على السماء الحيّة', 'بوصلة القبلة مع نقرةٍ على المعصم', 'الصحة الروحية: سجّل صلواتك واطّلع على أسبوعك', 'التسبيح بالتاج الرقمي', '7 مضاعفات بـ15 حجمًا']],
      ['مركز المساعدة', ['إجابات الأسئلة الشائعة داخل التطبيق', 'الإبلاغ عن مشكلة أو اقتراح فكرة', 'اطّلع على ما أرسلته', 'نرد عليك بالبريد']],
      ['الخصوصية', ['بلا إعلانات ولا حسابات ولا تتبُّع ولا ذكاء اصطناعي سحابي', 'المواقيت والقبلة والإجابات تُحسب على الجهاز', 'الاتصال فقط للبحث عن المساجد والخرائط والقرآن وما تختار تنزيله، ولسماء الطقس و«شبكة نظيفة» إن فعّلتهما']],
    ],
  },
  footer: {
    privacy: 'سياسة الخصوصية',
    terms: 'شروط الاستخدام',
    tv: 'شاشة التلفاز',
    contact: 'تواصل معنا',
  },
};

export const story = { chapters, en, ar };
