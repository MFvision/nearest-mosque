// The landing page's words and pictures, in English and Arabic. The page (app.js) reads only this; the stage
// follows each chapter's `stage` keys. Facts come from docs/10-features.md; keep them true when editing.
//
// stage: globe  { lon, lat } centre of the view ('you': the visitor's city, from the browser's time zone; haram: the
//               close-up of the Kaaba; fly: reach this chapter's view by flying out and back down) (spin: 1 turns the globe on its own; follow: 1 keeps the view 70° east of
//               the sun, on the evening side), zoom, lines (0..1, the five prayer lines), arcs (0..1, the Qibla
//               arcs), hours (simulated hours added across the chapter)
//        sky    t on the app's sky loop (0 Fajr … 1 Fajr again), mix (0 globe … 1 sky), weather ('rain': the sky
//               under rain, as with Sky follows the weather on; its streaks fall only without Reduce Motion)
// media: phones (iPhone screenshots, in the iPhone frame), android (Android screenshots, in a plain phone frame),
// watches, devices; paths are under assets/<lang>/.

export const links = {
  // Set a store link when the listing is live; until then that store has no button anywhere on the page.
  appStore: null,
  googlePlay: 'https://play.google.com/store/apps/details?id=com.AVA.nearmosque',
  // Windows and Linux are downloaded straight from GitHub: the release "desktop-latest", which
  // .github/workflows/desktop-release.yml keeps on the newest version under these fixed names.
  windows: 'https://github.com/MFvision/nearest-mosque/releases/download/desktop-latest/near-mosque-windows.msi',
  linux: 'https://github.com/MFvision/nearest-mosque/releases/download/desktop-latest/near-mosque-linux-x86_64.AppImage',
  // The small links under the buttons: the .deb, and the release page with every file and SHA256SUMS.txt.
  linuxDeb: 'https://github.com/MFvision/nearest-mosque/releases/download/desktop-latest/near-mosque-linux-amd64.deb',
  desktopAll: 'https://github.com/MFvision/nearest-mosque/releases/tag/desktop-latest',
};

const chapters = [
  // The hero: the visitor's next prayer, with the five prayer lines already moving round the world.
  { id: 'world', stage: { lon: 34, lat: 14, zoom: 1, lines: 1, arcs: 0, mix: 0, hours: 4, follow: 0, spin: 1 } },
  // The Qibla, as a flight: out from the hero's view, across along the Qibla arcs, then down to the Kaaba.
  { id: 'kaaba', stage: { lon: 39.8262, lat: 21.4225, zoom: 27000, lines: 0, arcs: 1, haram: 0, fly: 1, real: 1 } },
  { id: 'sky', stage: { mix: 1, t: 0.03, fly: 0 }, media: [{ phone: 'iphone/01-prayer', clip: 'clips/01-header' }, { phone: 'iphone/02-prayer-weather' }] },
  { id: 'mosque', stage: { mix: 1, t: 0.17 }, media: [{ phone: 'iphone/09-mosques-radar' }, { phone: 'iphone/03-mosques-map' }] },
  { id: 'ask', stage: { mix: 1, t: 0.3 }, media: [{ phone: 'iphone/04-ask', clip: 'clips/03-search' }, { phone: 'iphone/05-classics' }] },
  { id: 'adhan', stage: { mix: 1, t: 0.48 }, media: [{ phone: 'iphone/08-audio' }, { phone: 'iphone/07-island' }] },
  { id: 'devices', stage: { mix: 1, t: 0.8 }, media: [{ devices: ['iphone/10-prayer-dark', 'ipad/01-prayer', 'mac/01-prayer', 'tv/01-tv-board', 'vision/01-prayer'] }] },
  { id: 'privacy', stage: { mix: 1, t: 0.9 } },
  { id: 'get', stage: { lon: 40, lat: 18, zoom: 1, lines: 1, arcs: 0, mix: 0, hours: 3, follow: 0, spin: 1, haram: 0, real: 0 } },
];

const en = {
  dir: 'ltr',
  lang: 'en',
  switchTo: 'العربية',
  title: 'Near Mosque',
  pageTitle: 'Near Mosque · Prayer times, the Qibla and the nearest mosque',
  description: 'Prayer times, the Qibla and the nearest mosque for wherever you are, in one calm app. No account, no ads, no tracking.',
  skip: 'Skip to everything inside',
  prayers: { fajr: 'Fajr', dhuhr: 'Dhuhr', asr: 'Asr', maghrib: 'Maghrib', isha: 'Isha' },
  makkah: 'Makkah',
  scroll: 'Scroll',
  sound: ['Hear the Adhan', 'Adhan on'],
  daynight: ['Day', 'Night'],
  hud: { legend: 'Each light is a city at its prayer time', clock: 'Now in Makkah', styles: ['Earth', 'Dots'], you: 'You', madinah: 'Madinah' },
  board: { world: 'Next prayer', kaaba: 'Qibla', sky: 'Your sky', mosque: 'Nearest mosque', ask: 'Ask and library', adhan: 'Adhan and widgets', devices: 'Every screen', privacy: 'Privacy', get: 'Download' },
  boardLabel: 'Story',
  stores: { appStore: ['Download on the', 'App Store'], googlePlay: ['Get it on', 'Google Play'], windows: ['Download for', 'Windows'], linux: ['Download for', 'Linux'] },
  storesMore: { linuxDeb: '.deb for Ubuntu/Debian', desktopAll: 'All downloads and checksums' },
  chapters: {
    world: {
      eyebrow: 'Your next prayer',
      title: 'Your next prayer, wherever you are.',
      body: 'Somewhere on Earth, the Adhan is being called right now. Prayer times, the Qibla and the nearest mosque for wherever you are, in one calm app.',
      // The title becomes the visitor's next prayer and the time left (app.js); these are its words.
      nextIn: 'in', nextAt: 'at {time} in {city}',
      cta: 'Get the app',
    },
    kaaba: {
      eyebrow: 'Qibla',
      title: 'From anywhere, [[one direction]].',
      body: 'Turn your phone and the arrow leads you to the Kaaba. When you face it, the Qibla lights up and you feel a gentle vibration.',
      points: ['Bearing and distance to the Kaaba', 'Without a compass, it is drawn from north and says so'],
    },
    sky: {
      eyebrow: 'Prayer times',
      title: 'Your [[next prayer]], on the sky of the hour.',
      body: 'The header shows the next prayer and the time left. Its sky turns pink at Fajr, gold at Asr and violet at Maghrib, and if you like, it shows your city’s weather too.',
      points: ['13,090 cities built in, and 11 calculation methods suggested for your country', 'Today’s six times, each with its own bell, and the Hijri date', 'Sky follows the weather: off until you turn it on; then the city’s location, rounded to about 1 km, goes to MET Norway'],
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
      eyebrow: 'Ask and the library',
      title: 'Ask about Islam and see the [[source]] of every answer.',
      body: 'Answers quote the books on your phone and list each source. A scholar’s fatwa comes first when one matches. Behind them are 99 classical books you can read offline.',
      points: ['Written on your phone by Apple’s on-device model. Questions never go to a cloud AI.', '25 common questions in 22 languages, labelled as awaiting scholar review', 'Creed, Hadith, Fiqh, Tafsir and Sirah, each volume checked before it opens'],
      note: 'Answers quote books. They are not a fatwa.',
    },
    adhan: {
      eyebrow: 'Adhan, reminders and widgets',
      title: 'The Adhan from [[Makkah or Madinah]], and the time left at a glance.',
      body: 'Choose the Adhan of Masjid al-Haram or al-Masjid an-Nabawi for each prayer, with a reminder 10 or 5 minutes before. The countdown waits on your Lock Screen and in the Dynamic Island.',
      points: ['A Fajr alarm, and Friday reminders for Al-Kahf and Jumu’ah', 'Silent during prayer: Do Not Disturb on Android; on iPhone, a Shortcut you run with one tap from the Adhan notification', 'Ten kinds of widgets, and Siri tells you the next prayer'],
    },
    devices: {
      eyebrow: 'Every screen',
      title: 'On [[every screen]] you pray with.',
      body: 'One listing for iPhone, iPad, Mac, Apple TV, Vision Pro and Apple Watch, and the same prayer times and Qibla on Android, Windows and Linux.',
      points: ['Apple Watch: the next prayer, the Qibla with a tap on your wrist, and tasbih on the Digital Crown', '36 interface languages, 6 of them right to left', 'Android on Google Play; Windows and Linux downloaded from this page'],
      labels: ['iPhone', 'iPad', 'Mac', 'Apple TV', 'Vision Pro'],
    },
    privacy: {
      eyebrow: 'Privacy',
      title: 'No account, no ads, [[no tracking]].',
      body: 'Prayer times, the Qibla and your answers are worked out on your device. There is no account to make and nothing about you is sent to us.',
      points: ['Prayer times and Qibla work offline', 'Online only for mosque search (with your location rounded to about 1 km), maps, the downloads you choose and the weather sky if you turn it on'],
    },
    get: {
      eyebrow: 'Near Mosque',
      title: 'Every prayer [[on time]], wherever you are.',
      body: 'No account to create and no ads to sit through.',
    },
  },
  index: {
    title: 'Everything inside',
    intro: 'What the app does today, device by device.',
    groups: [
      ['Prayer times', ['Next prayer, countdown and the sky of the hour', 'Today’s six times with a bell for each', '11 calculation methods, Hanafi or standard Asr, high-latitude rules', 'Minute adjustments for each time and a Hijri adjustment', 'Estimated times where the sun does not set or rise']],
      ['Sky follows the weather', ['Clouds, rain, thunderstorms, fog and snow on the sky of the hour', 'The temperature and the wind, with an icon for the weather', 'Off until you turn it on; then the city’s location, rounded to about 1 km, goes to MET\u00a0Norway', 'iPhone, iPad, Mac, Vision Pro, Android, Windows and Linux']],
      ['Qibla', ['Live compass that lights up and taps when you face the Kaaba', 'Bearing and distance to the Kaaba', 'Drawn from north on devices without a compass']],
      ['Nearest mosque', ['Compass radar with each mosque at its real direction', 'Map with walking time, calls and websites', 'Directions in Apple Maps or Google Maps', 'Built-in mosque data, plus Apple Maps when online search is on']],
      ['Ask and the library', ['Answers quoting books on your phone, each with its source', 'A scholar’s fatwa first when one matches', '25 common questions in 22 languages, awaiting scholar review', '99 classical books in 450 volumes, read offline', 'IslamHouse, HadeethEnc, Qur’an translations and tafsir, Ibn Baz fatwas', 'Read answers aloud with your device’s voices']],
      ['Adhan and reminders', ['Adhan of Masjid al-Haram or al-Masjid an-Nabawi', 'Reminders 10 and 5 minutes before, spoken in Arabic or English', 'Fajr alarm, Surat Al-Kahf and Jumu’ah on Friday, Ramadan suhoor and iftar']],
      ['Silent during prayer', ['From the iqama or the Adhan until the prayer ends, for the prayers you choose', 'On Friday, from the Adhan for 45 to 90 minutes', 'Android turns Do Not Disturb on and off, with your permission', 'iPhone and iPad: one tap on the Adhan or iqama notification runs a Shortcut you make once, which turns on Focus until the prayer ends']],
      ['Widgets and Live Activities', ['10 widget kinds for the Home Screen, Lock Screen and Mac desktop', 'Next-prayer countdown on the Lock Screen and in the Dynamic Island', 'Qibla compass in the Dynamic Island', '5 styles that colour the app and every widget']],
      ['Siri and Shortcuts', ['“When is the next prayer in Near Mosque?”', '“Where is the nearest mosque in Near Mosque?”', 'Directions, Ask, prayer times and the Qibla by voice or the Action button']],
      ['Apple Watch', ['Next prayer on the living sky', 'Qibla compass with a tap on your wrist', 'Spiritual health: mark your prayers and see your week', 'Tasbih on the Digital Crown', '7 complications in 15 sizes']],
      ['Welcome tour', ['13 short steps, 12 on Windows and Linux', 'Where you pray, Asr, the Adhan with previews, Friday and the weather sky', 'Your day built from your answers, with a sun you can drag', '5 colour styles (Gold and cream, Green, Teal, Night sky, Time of day), light or dark', 'Exactly what leaves your device, then the notification request', 'Show it again from Settings']],
      ['Every device', ['iPhone, iPad, Mac, Apple TV and Apple Vision Pro', 'Apple TV prayer board, full-screen alert and Top Shelf', 'Android phones, foldables and tablets on Google Play, with 8 widget kinds', 'Windows and Linux, downloaded from this page', 'On Windows, the Adhan comes from the notification area']],
      ['Privacy', ['No accounts, no ads, no tracking, no cloud AI', 'Prayer times, Qibla and answers worked out on the device', 'Online only for mosque search, maps, the downloads you choose and the weather sky if you turn it on']],
    ],
  },
  footer: {
    privacy: 'Privacy policy',
    security: 'Security',
    terms: 'Terms of use',
    support: 'Support',
    credits: 'Sources and licences',
    line: 'Prayer times, the Qibla and the nearest mosque, wherever you are.',
    get: 'Get the app',
    help: 'Help',
    cities: 'Prayer times by city',
    about: 'About',
    note: 'No account, no ads, no tracking. This website sets no cookies and loads nothing from other companies.',
  },
};

const ar = {
  dir: 'rtl',
  lang: 'ar',
  switchTo: 'English',
  title: 'أقرب مسجد',
  pageTitle: 'أقرب مسجد · مواقيت الصلاة والقبلة وأقرب مسجد',
  description: 'مواقيت الصلاة والقبلة وأقرب مسجد أينما كنت، في تطبيقٍ واحدٍ هادئ. بلا حساب، بلا إعلانات، بلا تتبُّع.',
  skip: 'انتقل إلى كل ما في التطبيق',
  prayers: { fajr: 'الفجر', dhuhr: 'الظهر', asr: 'العصر', maghrib: 'المغرب', isha: 'العشاء' },
  makkah: 'مكة المكرمة',
  scroll: 'مرّر',
  sound: ['استمع إلى الأذان', 'الأذان يعمل'],
  daynight: ['نهار', 'ليل'],
  hud: { legend: 'كل ضوءٍ مدينةٌ حان وقت صلاتها', clock: 'الآن في مكة', styles: ['الأرض', 'النقاط'], you: 'أنت هنا', madinah: 'المدينة المنورة' },
  board: { world: 'صلاتك القادمة', kaaba: 'القبلة', sky: 'سماؤك', mosque: 'أقرب مسجد', ask: 'اسأل والمكتبة', adhan: 'الأذان والأدوات', devices: 'كل الشاشات', privacy: 'الخصوصية', get: 'التحميل' },
  boardLabel: 'القصة',
  stores: { appStore: ['حمّله من', 'App Store'], googlePlay: ['احصل عليه من', 'Google Play'], windows: ['حمّل لـ', 'Windows'], linux: ['حمّل لـ', 'Linux'] },
  storesMore: { linuxDeb: '\u2066.deb\u2069 لأوبونتو وديبيان', desktopAll: 'كل التنزيلات وبصماتها' }, // \u2066…\u2069 keeps ".deb" left to right, its dot in front
  chapters: {
    world: {
      eyebrow: 'صلاتك القادمة',
      title: 'صلاتك القادمة، أينما كنت.',
      body: 'في مكانٍ ما على الأرض يُرفع الأذان الآن. مواقيت الصلاة والقبلة وأقرب مسجد أينما كنت، في تطبيقٍ واحدٍ هادئ.',
      nextIn: 'بعد', nextAt: 'الساعة {time} في {city}',
      cta: 'حمّل التطبيق',
    },
    kaaba: {
      eyebrow: 'القبلة',
      title: 'من أي مكان، [[اتجاهٌ واحد]].',
      body: 'أدِر هاتفك يُرشدك السهم إلى الكعبة، فإذا استقبلتها أضاءت القبلة وشعرت باهتزازةٍ خفيفة.',
      points: ['الاتجاه والمسافة إلى الكعبة', 'وفي الأجهزة التي لا بوصلة فيها تُحدَّد القبلة انطلاقًا من الشمال، مع التنبيه إلى ذلك'],
    },
    sky: {
      eyebrow: 'مواقيت الصلاة',
      title: '[[صلاتك القادمة]] تحت سماء ساعتك.',
      body: 'يعرض لك أعلى الشاشة الصلاة القادمة والوقت المتبقي لها، تحت سماءٍ يتبدّل لونها مع يومك: وردية عند الفجر، وذهبية عند العصر، وبنفسجية عند المغرب، وتُظهر طقس مدينتك إن شئت.',
      points: ['13,090 مدينة مدمجة في التطبيق، و11 طريقة حساب يُقترح منها ما يناسب بلدك', 'مواقيت اليوم الستة، لكلٍّ منها تنبيه، مع التاريخ الهجري', 'سماء حسب الطقس: لا تعمل حتى تفعّلها، وعندها يُرسل موقع المدينة مقرّبًا إلى نحو كيلومتر إلى MET Norway'],
      credit: ['الطقس: استنادًا إلى بيانات MET Norway', 'https://www.met.no/en/free-meteorological-data/Licensing-and-crediting'],
    },
    mosque: {
      eyebrow: 'أقرب مسجد',
      title: 'اعرف اتجاه [[أقرب مسجد]] وكم يبعد عنك.',
      body: 'تعرض البوصلة كل مسجدٍ في اتجاهه الحقيقي، وتعرض الخريطة وقت المشي إليه وطريق الوصول.',
      points: ['الاتجاهات في خرائط Apple أو Google Maps', 'بيانات مساجد مدمجة، ومعها خرائط Apple عند تفعيل البحث على الإنترنت'],
    },
    ask: {
      eyebrow: 'اسأل والمكتبة',
      title: 'اسأل عن دينك، واعرف [[مصدر]] كل إجابة.',
      body: 'تُستمدّ الإجابات من الكتب المحفوظة في هاتفك، مع ذكر مصدر كل نص، وتتقدّمها فتوى أهل العلم إن وُجدت فتوى مطابقة. ووراءها 99 كتابًا من أمهات الكتب تقرؤها دون إنترنت.',
      points: ['تُكتب على هاتفك بنموذج Apple المدمج في الجهاز، ولا تُرسل أسئلتك إلى ذكاءٍ اصطناعي سحابي', '25 سؤالًا شائعًا بـ22 لغة، عليها علامة «بانتظار مراجعة العلماء»', 'العقيدة والحديث والفقه والتفسير والسيرة، ويُتحقق من كل مجلد قبل فتحه'],
      note: 'الإجابات تقتبس من الكتب، وليست فتوى.',
    },
    adhan: {
      eyebrow: 'الأذان والتذكير والأدوات',
      title: 'الأذان من [[مكة أو المدينة]]، والوقت المتبقي في لمحة.',
      body: 'اختر أذان المسجد الحرام أو المسجد النبوي لكل صلاة، مع تذكيرٍ قبلها بعشر دقائق أو خمس. والعدّ التنازلي ينتظرك على شاشة القفل وفي الجزيرة الديناميكية.',
      points: ['منبّه للفجر، وتذكير يوم الجمعة بسورة الكهف وصلاة الجمعة', '«الصمت أثناء الصلاة»: «عدم الإزعاج» في أندرويد، وفي آيفون اختصارٌ تشغّله بلمسة من تنبيه الأذان', 'عشرة أنواع من الأدوات، وSiri يخبرك بالصلاة القادمة'],
    },
    devices: {
      eyebrow: 'على كل شاشة',
      title: 'على [[كل شاشة]] تصلّي معها.',
      body: 'تطبيقٌ واحد لآيفون وآيباد وماك وآبل تي في وفيجن برو وApple Watch، والمواقيت والقبلة نفسها على أندرويد وويندوز ولينكس.',
      points: ['Apple Watch: الصلاة القادمة، والقبلة بنقرةٍ على معصمك، والتسبيح بالتاج الرقمي', '36 لغة للواجهة، منها 6 من اليمين إلى اليسار', 'أندرويد من Google Play، وويندوز ولينكس بالتنزيل من هذه الصفحة'],
      labels: ['آيفون', 'آيباد', 'ماك', 'آبل تي في', 'فيجن برو'],
    },
    privacy: {
      eyebrow: 'الخصوصية',
      title: 'بلا حساب، بلا إعلانات، [[بلا تتبُّع]].',
      body: 'تُحسب المواقيت والقبلة وإجاباتك على جهازك. لا حساب تنشئه، ولا يُرسل إلينا شيءٌ عنك.',
      points: ['المواقيت والقبلة تعمل دون إنترنت', 'الاتصال فقط للبحث عن المساجد (مع تقريب موقعك إلى نحو كيلومتر) والخرائط وما تختار تنزيله، ولسماء الطقس إن فعّلتها'],
    },
    get: {
      eyebrow: 'أقرب مسجد',
      title: 'كل صلاةٍ [[في وقتها]]، أينما كنت.',
      body: 'لا حساب تنشئه، ولا إعلانات تنتظرها.',
    },
  },
  index: {
    title: 'كل ما في التطبيق',
    intro: 'ما يفعله التطبيق اليوم، جهازًا جهازًا.',
    groups: [
      ['مواقيت الصلاة', ['الصلاة القادمة والعدّ التنازلي وسماء الساعة', 'مواقيت اليوم الستة ولكلٍّ منها تنبيه', '11 طريقة حساب، والعصر على المذهب الحنفي أو الجمهور، وقواعد خطوط العرض العليا', 'تعديل كل وقت بالدقائق، وتعديل التاريخ الهجري', 'أوقات تقديرية حيث لا تغرب الشمس أو لا تشرق']],
      ['سماء حسب الطقس', ['الغيوم والمطر والعواصف الرعدية والضباب والثلج على سماء الساعة', 'درجة الحرارة والرياح، مع رمزٍ لحالة الطقس', 'متوقفة حتى تفعّلها، وعندها يُرسل موقع المدينة مقرّبًا إلى نحو كيلومتر إلى MET\u00a0Norway', 'على آيفون وآيباد وماك وفيجن برو وأندرويد وويندوز ولينكس']],
      ['القبلة', ['بوصلة حيّة تضيء وتنقر حين تستقبل الكعبة', 'الاتجاه والمسافة إلى الكعبة', 'تُرسم من الشمال على الأجهزة التي لا بوصلة فيها']],
      ['أقرب مسجد', ['رادار يُظهر كل مسجد في اتجاهه الحقيقي', 'خريطة بوقت المشي والاتصال والمواقع', 'الاتجاهات في خرائط Apple أو Google Maps', 'بيانات مساجد مدمجة، ومعها خرائط Apple عند تفعيل البحث على الإنترنت']],
      ['اسأل والمكتبة', ['إجابات تقتبس من الكتب على هاتفك، مع مصدر كل منها', 'فتوى العالم أولًا إن وُجدت فتوى مطابقة', '25 سؤالًا شائعًا بـ22 لغة، بانتظار مراجعة العلماء', '99 كتابًا في 450 مجلدًا، تُقرأ دون اتصال', 'دار الإسلام وموسوعة الأحاديث وترجمات معاني القرآن والتفاسير وفتاوى ابن باز', 'قراءة الإجابات بصوتٍ مسموع بأصوات جهازك']],
      ['الأذان والتذكير', ['أذان المسجد الحرام أو المسجد النبوي', 'تذكير قبل الصلاة بعشر دقائق وخمس، بصوتٍ عربي أو إنجليزي', 'منبّه الفجر، وسورة الكهف والجمعة يوم الجمعة، والسحور والإفطار في رمضان']],
      ['الصمت أثناء الصلاة', ['من الإقامة أو الأذان حتى نهاية الصلاة، للصلوات التي تختارها', 'يوم الجمعة من الأذان لمدة 45 إلى 90 دقيقة', 'في أندرويد يشغّل التطبيق «عدم الإزعاج» ويطفئه، بإذنك', 'في آيفون وآيباد: لمسةٌ على تنبيه الأذان أو الإقامة تشغّل اختصارًا تصنعه مرة واحدة، فيبقى «التركيز» مفعّلًا حتى نهاية الصلاة']],
      ['الأدوات والأنشطة المباشرة', ['10 أنواع من الأدوات للشاشة الرئيسية وشاشة القفل وسطح مكتب الماك', 'عدّ تنازلي للصلاة القادمة على شاشة القفل وفي الجزيرة الديناميكية', 'بوصلة القبلة في الجزيرة الديناميكية', '5 أنماط تلوّن التطبيق وكل أدواته']],
      ['Siri والاختصارات', ['«متى الصلاة القادمة في أقرب مسجد؟»', '«أين أقرب مسجد في أقرب مسجد؟»', 'الاتجاهات والسؤال والمواقيت والقبلة بالصوت أو بزر الإجراءات']],
      ['Apple Watch', ['الصلاة القادمة على السماء الحيّة', 'بوصلة القبلة مع نقرةٍ على المعصم', 'الصحة الروحية: سجّل صلواتك واطّلع على أسبوعك', 'التسبيح بالتاج الرقمي', '7 مضاعفات بـ15 حجمًا']],
      ['الجولة الترحيبية', ['13 خطوة قصيرة، و12 على ويندوز ولينكس', 'مكان صلاتك، والعصر، والأذان مع معاينته، والجمعة، وسماء الطقس', 'يومك مبنيٌّ على إجاباتك، مع شمسٍ تسحبها', '5 أنماط لونية (ذهبي وكريمي، وأخضر، وأخضر مزرق، وسماء الليل، وحسب الوقت)، بمظهر فاتح أو داكن', 'ما الذي يغادر جهازك بالضبط، ثم طلب إذن الإشعارات', 'عرضها مجددًا من الإعدادات']],
      ['على كل جهاز', ['آيفون وآيباد وماك وآبل تي في وApple Vision Pro', 'لوحة المواقيت على آبل تي في، والتنبيه بملء الشاشة، والرف العلوي', 'هواتف أندرويد وأجهزته القابلة للطي واللوحية من Google Play، بثمانية أنواع من الأدوات', 'ويندوز ولينكس، بالتنزيل من هذه الصفحة', 'في ويندوز يصلك الأذان من منطقة الإشعارات']],
      ['الخصوصية', ['بلا حسابات ولا إعلانات ولا تتبُّع ولا ذكاء اصطناعي سحابي', 'المواقيت والقبلة والإجابات تُحسب على الجهاز', 'الاتصال فقط للبحث عن المساجد والخرائط وما تختار تنزيله، ولسماء الطقس إن فعّلتها']],
    ],
  },
  footer: {
    privacy: 'سياسة الخصوصية',
    security: 'الأمان',
    terms: 'شروط الاستخدام',
    support: 'الدعم',
    credits: 'المصادر والتراخيص',
    line: 'مواقيت الصلاة والقبلة وأقرب مسجد، أينما كنت.',
    get: 'حمّل التطبيق',
    help: 'المساعدة',
    cities: 'مواقيت الصلاة في المدن',
    about: 'عن التطبيق',
    note: 'بلا حساب، بلا إعلانات، بلا تتبُّع. وهذا الموقع لا يضع ملفات تعريف ارتباط، ولا يحمّل شيئًا من شركات أخرى.',
  },
};

export const story = { chapters, en, ar };
