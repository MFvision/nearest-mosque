// The landing page's words and pictures, in English and Arabic. The page (app.js) reads only this; the stage
// follows each chapter's `stage` keys. Facts come from docs/10-features.md; keep them true when editing.
//
// stage: globe  { lon, lat } centre of the view ('you': the visitor's city, from the browser's time zone; haram: the
//               close-up of the Kaaba; fly: reach this chapter's view by flying out and back down) (spin: 1 turns the globe on its own; follow: 1 keeps the view 70° east of
//               the sun, on the evening side), zoom, lines (0..1, the five prayer lines), arcs (0..1, the Qibla
//               arcs), hours (simulated hours added across the chapter)
//        sky    t on the app's sky loop (0 Fajr … 1 Fajr again), mix (0 globe … 1 sky)
// media: phones, watches, devices; paths are under assets/<lang>/.

export const links = {
  // Set a link when the listing is live; until then the button reads "Coming soon".
  appStore: null,
  googlePlay: null,
  microsoftStore: null,
};

const chapters = [
  { id: 'world', stage: { lon: 34, lat: 14, zoom: 1, lines: 0, arcs: 0, mix: 0, hours: 0, follow: 0, spin: 1 } },
  { id: 'lines', stage: { lon: 30, lat: 12, zoom: 1, lines: 1, arcs: 0, mix: 0, hours: 4, follow: 0, spin: 1 } },
  { id: 'qibla', stage: { lon: 'you', lat: 'you', zoom: 1.5, lines: 0, arcs: 1, mix: 0, hours: 0, follow: 0, spin: 0, real: 1 } },
  // From the visitor's city to Makkah, as a flight: out, across, then down to the Kaaba.
  { id: 'kaaba', stage: { lon: 39.8262, lat: 21.4225, zoom: 27000, arcs: 1, haram: 0, fly: 1 } },
  { id: 'sky', stage: { mix: 1, t: 0.03, fly: 0 }, media: [{ phone: 'iphone/01-prayer', clip: 'clips/01-header' }] },
  { id: 'mosque', stage: { mix: 1, t: 0.17 }, media: [{ phone: 'iphone/09-mosques-radar' }, { phone: 'iphone/03-mosques-map' }] },
  { id: 'ask', stage: { mix: 1, t: 0.3 }, media: [{ phone: 'iphone/04-ask', clip: 'clips/03-search' }] },
  { id: 'library', stage: { mix: 1, t: 0.38 }, media: [{ phone: 'iphone/05-classics' }] },
  { id: 'adhan', stage: { mix: 1, t: 0.48 }, media: [{ phone: 'iphone/08-audio' }] },
  { id: 'glance', stage: { mix: 1, t: 0.6 }, media: [{ phone: 'iphone/06-widgets' }, { phone: 'iphone/07-island' }] },
  { id: 'watch', stage: { mix: 1, t: 0.7 }, media: [{ watch: 'watch/01-watch-prayer' }, { watch: 'watch/03-watch-spirit' }, { watch: 'watch/04-watch-tasbih' }] },
  { id: 'devices', stage: { mix: 1, t: 0.8 }, media: [{ devices: ['iphone/10-prayer-dark', 'ipad/01-prayer', 'mac/01-prayer', 'tv/01-tv-board', 'vision/01-prayer'] }] },
  { id: 'privacy', stage: { mix: 1, t: 0.9 } },
  { id: 'get', stage: { lon: 40, lat: 18, zoom: 1, lines: 1, arcs: 0, mix: 0, hours: 3, follow: 0, spin: 1, haram: 0, real: 0 } },
];

const en = {
  dir: 'ltr',
  lang: 'en',
  switchTo: 'العربية',
  title: 'Near Mosque',
  skip: 'Skip to everything inside',
  prayers: { fajr: 'Fajr', dhuhr: 'Dhuhr', asr: 'Asr', maghrib: 'Maghrib', isha: 'Isha' },
  makkah: 'Makkah',
  scroll: 'Scroll',
  sound: ['Hear the Adhan', 'Adhan on'],
  daynight: ['Day', 'Night'],
  hud: { legend: 'Each light is a city at its prayer time', clock: 'Now in Makkah', styles: ['Earth', 'Dots'], you: 'You', madinah: 'Madinah' },
  board: { world: 'The Adhan', lines: 'Five prayers', qibla: 'Qibla', kaaba: 'Makkah', sky: 'Your sky', mosque: 'Nearest mosque', ask: 'Ask', library: 'Library', adhan: 'Adhan', glance: 'Widgets and Siri', watch: 'Apple Watch', devices: 'Every screen', privacy: 'Privacy', get: 'Download' },
  boardLabel: 'Story',
  soon: 'Coming soon',
  stores: { appStore: ['Download on the', 'App Store'], googlePlay: ['Get it on', 'Google Play'], microsoftStore: ['Get it from', 'Microsoft Store'] },
  chapters: {
    world: {
      eyebrow: 'Near Mosque',
      title: 'Somewhere on Earth, the [[Adhan]] is being called right now.',
      body: 'Prayer times, the Qibla and the nearest mosque for wherever you are, in one calm app.',
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
    sky: {
      eyebrow: 'Prayer times',
      title: 'Your [[next prayer]], on the sky of the hour.',
      body: 'The header shows the next prayer and the time left. Its sky turns pink at Fajr, gold at Asr and violet at Maghrib.',
      points: ['Today’s six times, each with its own bell', 'Hijri and Gregorian dates side by side'],
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
      points: ['A Fajr alarm, at Fajr or up to 45 minutes before', 'Friday reminders for Surat Al-Kahf and Jumu’ah'],
    },
    glance: {
      eyebrow: 'Widgets, Live Activities and Siri',
      title: 'Check the time left [[without opening the app]].',
      body: 'Ten kinds of widgets for the Home Screen and Lock Screen. The countdown waits in the Dynamic Island, and Siri tells you the next prayer.',
      points: ['“When is the next prayer in Near Mosque?”', 'The Qibla compass in the Dynamic Island'],
    },
    watch: {
      eyebrow: 'Apple Watch',
      title: 'Your prayers [[on your wrist]].',
      body: 'The watch shows the next prayer, finds the Qibla with a tap on your wrist, and counts tasbih on the Digital Crown.',
      points: ['A companion that brightens as you mark your prayers', 'Works even when your iPhone stays behind'],
    },
    devices: {
      eyebrow: 'Every screen',
      title: 'On [[iPhone, iPad, Mac, Apple TV and Vision Pro]].',
      body: 'One listing for your Apple devices. Android and Windows versions keep the same prayer times and Qibla.',
      points: ['36 interface languages, 6 of them right to left'],
      labels: ['iPhone', 'iPad', 'Mac', 'Apple TV', 'Vision Pro'],
    },
    privacy: {
      eyebrow: 'Privacy',
      title: 'No account, no ads, [[no tracking]].',
      body: 'Prayer times, the Qibla and your answers are worked out on your device. There is no account to make and nothing about you is sent to us.',
      points: ['Prayer times and Qibla work offline', 'Online only for mosque search (with your location rounded to about 1 km), maps and the downloads you choose'],
    },
    kaaba: {
      eyebrow: 'Makkah',
      title: 'Every line meets at the [[Kaaba]].',
      body: 'Wherever you pray, your Qibla is the same. Near Mosque shows you its direction from where you stand.',
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
      ['Qibla', ['Live compass that lights up and taps when you face the Kaaba', 'Bearing and distance to the Kaaba', 'Drawn from north on devices without a compass']],
      ['Nearest mosque', ['Compass radar with each mosque at its real direction', 'Map with walking time, calls and websites', 'Directions in Apple Maps or Google Maps', 'Built-in mosque data, plus Apple Maps when online search is on']],
      ['Ask and the library', ['Answers quoting books on your phone, each with its source', 'A scholar’s fatwa first when one matches', '25 common questions in 22 languages, awaiting scholar review', '99 classical books in 450 volumes, read offline', 'IslamHouse, HadeethEnc, Qur’an translations and tafsir, Ibn Baz fatwas', 'Read answers aloud with your device’s voices']],
      ['Adhan and reminders', ['Adhan of Masjid al-Haram or al-Masjid an-Nabawi', 'Reminders 10 and 5 minutes before, spoken in Arabic or English', 'Fajr alarm, Surat Al-Kahf and Jumu’ah on Friday, Ramadan suhoor and iftar']],
      ['Widgets and Live Activities', ['10 widget kinds for the Home Screen, Lock Screen and Mac desktop', 'Next-prayer countdown on the Lock Screen and in the Dynamic Island', 'Qibla compass in the Dynamic Island', '5 styles that colour the app and every widget']],
      ['Siri and Shortcuts', ['“When is the next prayer in Near Mosque?”', '“Where is the nearest mosque in Near Mosque?”', 'Directions, Ask, prayer times and the Qibla by voice or the Action button']],
      ['Apple Watch', ['Next prayer on the living sky', 'Qibla compass with a tap on your wrist', 'Spiritual health: mark your prayers and see your week', 'Tasbih on the Digital Crown', '7 complications in 15 sizes']],
      ['Every device', ['iPhone, iPad, Mac, Apple TV and Apple Vision Pro', 'Apple TV prayer board, full-screen alert and Top Shelf', 'Android with 8 widget kinds, and Windows in the notification area']],
      ['Privacy', ['No accounts, no ads, no tracking, no cloud AI', 'Prayer times, Qibla and answers worked out on the device', 'Online only for mosque search, maps and the downloads you choose']],
    ],
  },
  footer: {
    privacy: 'Privacy policy',
    security: 'Security',
    terms: 'Terms of use',
    support: 'Support',
    credits: 'Sources and licences',
  },
};

const ar = {
  dir: 'rtl',
  lang: 'ar',
  switchTo: 'English',
  title: 'أقرب مسجد',
  skip: 'انتقل إلى كل ما في التطبيق',
  prayers: { fajr: 'الفجر', dhuhr: 'الظهر', asr: 'العصر', maghrib: 'المغرب', isha: 'العشاء' },
  makkah: 'مكة المكرمة',
  scroll: 'مرّر',
  sound: ['استمع إلى الأذان', 'الأذان يعمل'],
  daynight: ['نهار', 'ليل'],
  hud: { legend: 'كل ضوءٍ مدينةٌ حان وقت صلاتها', clock: 'الآن في مكة', styles: ['الأرض', 'النقاط'], you: 'أنت هنا', madinah: 'المدينة المنورة' },
  board: { world: 'الأذان', lines: 'الصلوات الخمس', qibla: 'القبلة', kaaba: 'مكة المكرمة', sky: 'سماؤك', mosque: 'أقرب مسجد', ask: 'اسأل', library: 'المكتبة', adhan: 'الأذان والتذكير', glance: 'الأدوات وSiri', watch: 'Apple Watch', devices: 'كل الشاشات', privacy: 'الخصوصية', get: 'التحميل' },
  boardLabel: 'القصة',
  soon: 'قريبًا',
  stores: { appStore: ['حمّله من', 'App Store'], googlePlay: ['احصل عليه من', 'Google Play'], microsoftStore: ['احصل عليه من', 'Microsoft Store'] },
  chapters: {
    world: {
      eyebrow: 'أقرب مسجد',
      title: 'في مكانٍ ما على الأرض، يُرفع [[الأذان]] الآن.',
      body: 'مواقيت الصلاة والقبلة وأقرب مسجد أينما كنت، في تطبيقٍ واحدٍ هادئ.',
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
    sky: {
      eyebrow: 'مواقيت الصلاة',
      title: '[[صلاتك القادمة]] تحت سماء ساعتك.',
      body: 'يعرض لك أعلى الشاشة الصلاة القادمة والوقت المتبقي لها، تحت سماءٍ يتبدّل لونها مع يومك: وردية عند الفجر، وذهبية عند العصر، وبنفسجية عند المغرب.',
      points: ['مواقيت اليوم الستة، لكلٍّ منها تنبيه', 'التاريخان الهجري والميلادي جنبًا إلى جنب'],
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
      points: ['منبّه للفجر، عند الأذان أو قبله بما يصل إلى 45 دقيقة', 'تذكير يوم الجمعة بسورة الكهف وصلاة الجمعة'],
    },
    glance: {
      eyebrow: 'الأدوات والأنشطة المباشرة وSiri',
      title: 'اعرف الوقت المتبقي [[دون أن تفتح التطبيق]].',
      body: 'عشرة أنواع من الأدوات للشاشة الرئيسية وشاشة القفل. والعدّ التنازلي ينتظرك في الجزيرة الديناميكية، وSiri يخبرك بالصلاة القادمة.',
      points: ['«متى الصلاة القادمة في أقرب مسجد؟»', 'بوصلة القبلة في الجزيرة الديناميكية'],
    },
    watch: {
      eyebrow: 'Apple Watch',
      title: 'صلواتك [[على معصمك]].',
      body: 'تعرض الساعة الصلاة القادمة، وتجد القبلة بنقرةٍ على معصمك، وتعدّ التسبيح بالتاج الرقمي.',
      points: ['رفيقٌ يزداد إشراقًا كلما سجّلت صلواتك', 'تعمل حتى إن بقي الآيفون بعيدًا'],
    },
    devices: {
      eyebrow: 'على كل شاشة',
      title: 'على [[آيفون وآيباد وماك وآبل تي في وفيجن برو]].',
      body: 'تطبيقٌ واحد لأجهزة Apple كلها، ونسختان لأندرويد وويندوز بالمواقيت والقبلة نفسها.',
      points: ['36 لغة للواجهة، منها 6 من اليمين إلى اليسار'],
      labels: ['آيفون', 'آيباد', 'ماك', 'آبل تي في', 'فيجن برو'],
    },
    privacy: {
      eyebrow: 'الخصوصية',
      title: 'بلا حساب، بلا إعلانات، [[بلا تتبُّع]].',
      body: 'تُحسب المواقيت والقبلة وإجاباتك على جهازك. لا حساب تنشئه، ولا يُرسل إلينا شيءٌ عنك.',
      points: ['المواقيت والقبلة تعمل دون إنترنت', 'الاتصال فقط للبحث عن المساجد (مع تقريب موقعك إلى نحو كيلومتر) والخرائط وما تختار تنزيله'],
    },
    kaaba: {
      eyebrow: 'مكة المكرمة',
      title: 'كل الخطوط تلتقي عند [[الكعبة]].',
      body: 'أينما صلّيت فقبلتك واحدة، و«أقرب مسجد» يدلّك على اتجاهها من حيث تقف.',
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
      ['القبلة', ['بوصلة حيّة تضيء وتنقر حين تستقبل الكعبة', 'الاتجاه والمسافة إلى الكعبة', 'تُرسم من الشمال على الأجهزة التي لا بوصلة فيها']],
      ['أقرب مسجد', ['رادار يُظهر كل مسجد في اتجاهه الحقيقي', 'خريطة بوقت المشي والاتصال والمواقع', 'الاتجاهات في خرائط Apple أو Google Maps', 'بيانات مساجد مدمجة، ومعها خرائط Apple عند تفعيل البحث على الإنترنت']],
      ['اسأل والمكتبة', ['إجابات تقتبس من الكتب على هاتفك، مع مصدر كل منها', 'فتوى العالم أولًا إن وُجدت فتوى مطابقة', '25 سؤالًا شائعًا بـ22 لغة، بانتظار مراجعة العلماء', '99 كتابًا في 450 مجلدًا، تُقرأ دون اتصال', 'دار الإسلام وموسوعة الأحاديث وترجمات معاني القرآن والتفاسير وفتاوى ابن باز', 'قراءة الإجابات بصوتٍ مسموع بأصوات جهازك']],
      ['الأذان والتذكير', ['أذان المسجد الحرام أو المسجد النبوي', 'تذكير قبل الصلاة بعشر دقائق وخمس، بصوتٍ عربي أو إنجليزي', 'منبّه الفجر، وسورة الكهف والجمعة يوم الجمعة، والسحور والإفطار في رمضان']],
      ['الأدوات والأنشطة المباشرة', ['10 أنواع من الأدوات للشاشة الرئيسية وشاشة القفل وسطح مكتب الماك', 'عدّ تنازلي للصلاة القادمة على شاشة القفل وفي الجزيرة الديناميكية', 'بوصلة القبلة في الجزيرة الديناميكية', '5 أنماط تلوّن التطبيق وكل أدواته']],
      ['Siri والاختصارات', ['«متى الصلاة القادمة في أقرب مسجد؟»', '«أين أقرب مسجد في أقرب مسجد؟»', 'الاتجاهات والسؤال والمواقيت والقبلة بالصوت أو بزر الإجراءات']],
      ['Apple Watch', ['الصلاة القادمة على السماء الحيّة', 'بوصلة القبلة مع نقرةٍ على المعصم', 'الصحة الروحية: سجّل صلواتك واطّلع على أسبوعك', 'التسبيح بالتاج الرقمي', '7 مضاعفات بـ15 حجمًا']],
      ['على كل جهاز', ['آيفون وآيباد وماك وآبل تي في وApple Vision Pro', 'لوحة المواقيت على آبل تي في، والتنبيه بملء الشاشة، والرف العلوي', 'أندرويد بثمانية أنواع من الأدوات، وويندوز في منطقة الإشعارات']],
      ['الخصوصية', ['بلا حسابات ولا إعلانات ولا تتبُّع ولا ذكاء اصطناعي سحابي', 'المواقيت والقبلة والإجابات تُحسب على الجهاز', 'الاتصال فقط للبحث عن المساجد والخرائط وما تختار تنزيله']],
    ],
  },
  footer: {
    privacy: 'سياسة الخصوصية',
    security: 'الأمان',
    terms: 'شروط الاستخدام',
    support: 'الدعم',
    credits: 'المصادر والتراخيص',
  },
};

export const story = { chapters, en, ar };
