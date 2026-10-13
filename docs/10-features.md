# 10. Everything in the app, one item at a time

A reference for the App Store listing, support and review. It describes what the code does on each device,
with the file each item lives in. Screenshots and where they go are in `09-app-store.md`. When the code
changes, this page is checked again; it is not a promise beyond the current build.

Near Mosque runs on iPhone, iPad, Mac (Mac Catalyst), Apple TV and Apple Vision Pro, all under the one
bundle ID `sa.zood.nearmosque`, plus Android and Windows. The Apple Watch app (`sa.zood.nearmosque.watchkitapp`)
ships inside the iPhone app, in the same listing.

## Counts at a glance

| Item | Count |
|---|---|
| Interface languages | 36 (6 right-to-left: ar, ur, fa, prs, ug, ckb); English and Arabic reviewed, the others drafts awaiting native review |
| App Store languages with screenshots | 22 (`09-app-store.md`) |
| iPhone and iPad widgets | 10 kinds |
| Lock Screen widget sizes | 9 (in 5 of the widgets) |
| Control Center controls | 1 on iPhone (Qibla), 2 on Apple Watch (Qibla, tasbih) |
| Apple Watch complications | 7 kinds in 15 sizes, plus 1 Smart Stack widget |
| Live Activities | 2 (next-prayer countdown, Qibla compass) |
| Siri and Shortcuts actions | 6 |
| App and widget styles | 5 (cream, green, night, teal, sky), plus "Same as app" in widgets |
| Calculation methods | 11 |
| Recordings | 38 (4 Adhans, 2 salawat, 2 evening verses, 30 spoken reminders) |
| Common questions | 25, in the 22 store languages (all awaiting scholar review; translations other than English and Arabic are drafts) |
| Classical books in the download catalogue | 99 books in 450 volumes (Arabic, from OpenITI) |
| Built-in cities | 13,090 (GeoNames) |
| Built-in mosque areas | Cairo (519), London (255), Cape Town (69) (OpenStreetMap) |
| Onboarding pages | 6 |
| Android widgets | 8 kinds in 16 sizes, 3 styles |

## iPhone and iPad

### Layout

* Two tabs: **Prayer & Qibla** and **Nearest Mosque** (`ios/App/NearMosqueApp.swift`).
* **Ask AI** is a button ("Ask anything about Islam") that opens Ask full screen. On iOS 26 it sits in the
  tab bar's accessory slot and the tab bar shrinks on scroll; on earlier iOS it floats as a glass capsule.
* Settings opens as a sheet from the gear on each tab.
* iPhone shows one column; iPad and Mac show two (list beside map, cards beside the header).
* Links into the app: `nearmosque://prayer`, `qibla`, `mosques`, `mosque?id=`, `settings`, `ask?q=`. A
  question in a link is only placed in the box, not asked, unless it comes from inside the app or from
  this phone's own widget (max 300 characters).
* Keyboard (iPad with keyboard, Mac): ⌘, Settings · ⌘1 Prayer · ⌘2 Mosques · ⌘3 Qibla · ⌘K Ask.

### Prayer & Qibla tab (`ios/App/Prayer/`)

1. **Header**: location pill, the Qibla arc with a gold dot at the Qibla's direction relative to the top
   of the phone, the next prayer with its time and countdown, distance to the Kaaba, the nearest-mosque
   chip. When the phone faces the Qibla the logo glows and the phone gives one haptic tap. Without a
   compass the arc is drawn from north and says so.
2. **Compact bar**: after scrolling, a glass bar with the next prayer, time left and a small Qibla arrow;
   tap to go back up.
3. **Qibla compass** (tap the header): dial turning with the phone, the Kaaba at the Qibla bearing,
   bearing and distance, and the note that phone compasses are approximate.
4. **Cards**: estimated-times badge where the nearest-latitude rule applies, time-zone confirmation,
   polar-day notice, the sun and moon path of the day, the **Companion** (a lantern or a heart that
   brightens as prayers are marked "I prayed", and moves when the phone tilts), **Today's prayer times**
   (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha, each with a bell), dates (Gregorian, Hijri) with the
   calculation button.

### Nearest Mosque tab (`ios/App/Mosques/`)

* **Compass / Map** switch.
* **Compass (radar)**: the person in the middle, mosques at their real direction and distance; it turns
  with the phone and says "Mosque ahead" or which way to turn.
* **Map**: Apple Maps with pins, "Search this area", and a full-screen map with mosque cards along the
  bottom (Directions, Call, Website, walking time).
* **List**: nearest known mosque, straight-line distance, walking route, Get directions (Apple Maps, and
  Google Maps or Waze when installed; Apple Maps only on Mac).
* **Mosque details**: category, opening hours when known, Directions, Call, Website, source, favourite.
* Search within 25 km from the mosque data on the device; with "Search online for mosques" on, Apple Maps
  results within 10 km are added.

### Ask (`ios/App/Ask/`)

* Top bar: close, New conversation, Chats (history), Library.
* Start screen: "Ask anything about Islam", **Common questions** (25, in 22 languages), "You could also ask".
* **Answer**: a scholar's fatwa first when one matches; otherwise the reviewed answer to a common
  question, an answer written on the device from the cited passages, or the passages themselves; or
  "not enough in the library". Every answer lists its sources, related passages and library items, can be
  read aloud with the device's voices, and ends with "Answers quote books; they are not a fatwa".
* The five Quran questions (miracle, science, preservation, prophecies, author) are marked "awaiting
  scholar review".
* **Search these sites too**: IslamQA, Dorar, Ibn Uthaymeen, the Permanent Committee (links out).
* On-device writing uses Apple's on-device model only (iOS 26); questions never go to a cloud model.
* Chat history keeps questions only, at most 50 chats, on the device and out of backups.

### Library (from Ask)

* Shelves: **Books**, **Browse**, **Saved**, **Downloads**. Always the dark reading-room look.
* Browse starts with **Classical books**, then the installed collections (IslamHouse, HadeethEnc, Quran
  translations and tafsir, Ibn Baz fatwas), each with filters (All, Books, Audio, Video, Article, Fatwa,
  Hadith, Tafsir, Qur'an translation), search and "Load more".
* **Classical books**: 99 Sunni books in Arabic grouped as Creed (33), Hadith (23), Fiqh (14), Softening
  the heart (13), Tafsir (6), History and biographies (6), Sirah (4). Each volume downloads only when tapped,
  is checked against its SHA-256, and is read offline. Source OpenITI (CC BY-NC-SA 4.0).
* Reader: PDF (opening at the page matching the question), audio, video, articles, and stored records.
* Saved: bookmarks. Downloads: downloaded files with Delete and Delete all.

### Settings (`ios/App/Settings/`)

**General**: Language (device language or one of 36); Appearance (Same as phone, Light, Dark).

**Prayer times**
* Location: city search or "Use my location".
* Calculation: method (Muslim World League; Umm al-Qura, Makkah; Egyptian General Authority of Survey;
  University of Islamic Sciences, Karachi; Islamic Society of North America; Dubai; Kuwait; Qatar;
  Singapore and Southeast Asia; Diyanet, Turkey; Moonsighting Committee), suggested by country · Asr
  (Standard: Shafi'i, Maliki, Hanbali; or Hanafi) · high-latitude rule (Automatic, Middle of the night,
  One-seventh of the night, Twilight angle) · when the sun does not rise or set (unavailable, or nearest
  latitude 48.5°) · Ramadan Isha 30 minutes later (Umm al-Qura only) · Hijri adjustment −2…+2 days ·
  minutes adjustment −30…+30 for each of the six times.
* **Prayer & Audio**:
  * Adhan for Dhuhr, Asr, Maghrib and Isha, and the Fajr Adhan, each from Masjid al-Haram (Makkah) or
    Al-Masjid an-Nabawi (Madinah), with preview.
  * Before the prayer: 10 minutes, 5 minutes, and a pre-prayer reminder at 15, 20 or 30 minutes; spoken
    in Arabic or English.
  * Each prayer on its own: notification, Adhan, the reminders. Sunrise (end of Fajr time).
  * Qur'an and dhikr: salawat on Fridays, daily Qur'an reading reminder, a verse before sleep; reciter
    Mishary Alafasy, or Al-Husary and Abdul Basit.
  * More: Surat Al-Kahf on Friday, Jumu'ah 45 minutes before Dhuhr, Ramadan suhoor and iftar, and the
    **Fajr alarm** (off, at Fajr, or 10/20/30/45 minutes before).
* Countdown on the Lock Screen (iPhone and iPad).

**Personalize**: Companion (Lantern, Heart, None); **App and widget style**: one choice of five that
colours the app's buttons and accents, every widget, the Lock Screen and the Dynamic Island.

**Nearest Mosque**: Search online for mosques (on by default).

**Downloads**: installed packs with remove, Import from file (checked), Restore built-in data.

**About**: Privacy, Sources and licenses, On-device AI status, Show welcome tour, version.

### Onboarding (`ios/App/Onboarding/`)

Six pages, each with a language menu and Skip: 1 Assalamu alaikum · 2 Prayer times that follow the sky ·
3 Face the Qibla · 4 The nearest mosque · 5 Ask anything about Islam · 6 Let's set up (language,
location, the online-search notice). With Reduce Motion each page shows a still picture.

## Widgets (iPhone, iPad, Mac) (`ios/Widgets/`)

Each widget has one setting, **Widget style**: Same as app (default), Gold and cream, Green, Night sky,
Teal, Time of day (colours follow the prayer period).

| # | Widget | What it shows | Home Screen sizes | Lock Screen sizes | Tap opens |
|---|---|---|---|---|---|
| 1 | Next prayer | The next prayer, its countdown and today's times | Small, Medium, Large | Circular, Rectangular, Inline | Prayer |
| 2 | Prayer countdown | A bar that fills up until the next prayer | Small, Medium | Rectangular | Prayer |
| 3 | Prayer ring | Time left on a ring, with all of today's times | Small, Medium, Large, Extra Large | none | Prayer |
| 4 | Today's prayers | All five prayers, the date and the next prayer | Medium, Large, Extra Large | none | Prayer |
| 5 | Nearest mosque | The nearest mosques, how far and which way | Small, Medium | none | That mosque, or Mosques |
| 6 | Ask about Islam | Ask, or tap one of today's suggestions | Small, Medium | none | Ask |
| 7 | Qibla | The Qibla direction from north; tap the dial for the Dynamic Island compass | Small, Medium | Circular | Qibla |
| 8 | Quick actions | Ask, Nearest mosque, Prayer & Qibla, one tap each | Medium | none | Each button its screen |
| 9 | Tasbih counter | Tap the circle to count; the ring fills every 33 | Small, Medium | Circular | Counts in place (with reset) |
| 10 | Companion | The lantern or heart, brighter as prayers are marked | Small, Medium | Circular | "I prayed" in place |

Extra Large is iPad only. On the Mac the widgets have Home Screen (desktop) sizes only. Widgets read the
app's data through the device's keychain; nothing is sent anywhere.

**Control Center**: one control, **Qibla**, opening the Qibla compass (also usable on the Action button).
Not on Mac.

## Live Activities and the Dynamic Island (iPhone)

1. **Next-prayer countdown** (`ios/Widgets/PrayerLiveActivity.swift`)
   * Lock Screen: the time-of-day sky, the next prayer with a live countdown and bar, the day's five
     prayers with the next one lit.
   * Dynamic Island: compact (icon and name; countdown), minimal (progress ring), expanded (name, time,
     city, large countdown, **Qibla** and **Nearest Mosque** buttons; "Time for …" when it arrives).
   * Starts when the app opens with "Countdown on the Lock Screen" on (default on) and a city set; marked
     stale 15 minutes after the prayer; iOS removes it after at most 8 hours. No push updates.
2. **Qibla compass** (`ios/Widgets/QiblaLiveActivity.swift`), started from the Qibla widget's dial: arrow,
   bearing, turn hint, Kaaba when facing; uses the compass for 2 minutes and then ends.

Both follow the app's style. Neither exists on Mac or Vision Pro.

## Siri and Shortcuts (`ios/App/AppShortcuts.swift`)

| Action | Example phrase (English; Arabic too) | Result |
|---|---|---|
| Next prayer | "When is the next prayer in Near Mosque" | Spoken answer without opening the app |
| Nearest mosque | "Where is the nearest mosque in Near Mosque" | Spoken answer and card (phone unlocked) |
| Directions to the nearest mosque | "Take me to the nearest mosque with Near Mosque" | Apple Maps walking directions |
| Ask about Islam | "Ask Near Mosque" | Opens Ask with the question |
| Prayer times | "Show prayer times in Near Mosque" | Opens Prayer |
| Qibla | "Where is the Qibla in Near Mosque" | Opens the Qibla compass |

## Notifications and alarm (`ios/App/Platform/Reminders.swift`)

* Prayer alerts with buttons: Prayed, Remind me in 5 min, Qibla, Nearest Mosque. Reminders before a
  prayer: Qibla, Nearest Mosque.
* Up to 60 alerts, up to 14 days ahead, refreshed in the background.
* Opening an Adhan notification plays the full Adhan in the app with a now-playing bar.
* **Fajr alarm** with AlarmKit on iOS 26 (up to 7 alarms, Stop button); an ordinary notification on
  earlier iOS, on the Mac, or if alarm permission is refused.

## iPhone Duo

The app is built with the iOS 26 SDK, so on iPhone Duo it runs centred on the open inner display (Apple's
behaviour for older SDKs). The layout code follows Apple's preparation list: no `UIScreen.main` sizing, no
layout from device orientation (only the compass reads it, to correct its heading), no device checks by
idiom (Settings → Apple Watch shows where WatchConnectivity is supported), standard `NavigationStack` and
`TabView`, and every toolbar button with an icon and a title for vertical bars. A CI job builds with the
iOS 27 SDK on GitHub's preview runner. Building with the iOS 27.1 SDK (edge to edge, vertical bars) waits
for Xcode 27.1 and a check in the iPhone Duo simulator.

## Mac (Mac Catalyst)

The iPad app on the Mac with: desktop widgets (no Lock Screen, Control or Live Activities), the Qibla
drawn from north (no compass), the Fajr alarm as a notification, Apple Maps only for directions, the Ask
button floating, onboarding turned with buttons, and the menu-bar commands above.

## Apple TV (`ios/TV/`)

* **Prayer times** board: city, both dates, the next prayer with a large countdown, today's six times.
* **Qibla**: a north-up diagram with bearing and distance (labelled as not a live compass).
* **Nearest Mosque**: list beside a map, the chosen mosque's card and a QR code for directions on the phone.
* **Settings**: city, calculation (as on the phone), language, keep the board on screen, full-screen
  alert at prayer time, online search, About.
* **Prayer alert**: full screen "Time for …", silent, while the app is open; closes by button, Back or
  after 5 minutes.
* **Top Shelf**: city, weekday and date, Hijri date and the six times with the next one in gold.
* Not on TV: Ask, library, widgets, Adhan audio.

## Apple Vision Pro (`NearMosqueVision`)

The same app built natively for visionOS 26, in a 980 × 720 window. The Qibla is drawn from north (no
compass). Left out: Live Activities, widgets, the Fajr alarm, the Lock Screen countdown, the companion's
motion (it sways on its own) and Liquid Glass (a material look instead).

## Apple Watch (`ios/Watch/`, `ios/WatchWidgets/`)

watchOS 26 (Series 6, SE 2nd generation, Ultra and later). Installed from the iPhone's Watch app or the
App Store on the watch; it also works without the iPhone.

**Where the times come from** (Settings on the watch, the gear at the top):
* **From iPhone** (default): the city, calculation method, language, style, reminder bells, the nearest
  mosques and today's suggested questions arrive from the iPhone app through WatchConnectivity
  (`ios/App/Platform/WatchBridge.swift` → `ios/Watch/WatchModel.swift`). Nothing goes to a server.
* **This watch's location**: the watch locates itself (kept to about 100 m), names the place after the
  nearest built-in city and suggests the method for the country. Method and Asr time can be changed there.

**Design**: every page sits on the iPhone's landscape (`ios/App/Support/Sky.swift`): the sky of the prayer
period (dawn pinks at Fajr, gold at sunrise, blue by day, amber at Asr, orange and violet at Maghrib, deep
blue at Isha and night), stars that twinkle at night, drifting clouds, the horizon glow, ridges, a lake and a
mosque on the horizon, with the sun or a shining crescent moon where it is in the sky now
(`ios/Watch/WatchSky.swift`). Motion stops under Reduce Motion; when the screen dims (always on) the
background is black.

**Pages** (turn the Digital Crown to move between them):
1. **Prayer**: the next prayer with a ring for the period and a live countdown, on the sky of the hour;
   today's six times with the reminder bells; city and Hijri date.
2. **Qibla**: a live compass (Series 5 and later have one): the ring turns, the Kaaba sits on the rim at
   the Qibla, the hint says which way to turn, and the watch taps the wrist when you face it (±5°).
   Without a compass reading it shows the Qibla from north and says so.
3. **Spiritual health** (`ios/Watch/SpiritPage.swift`): the companion (the lantern or the heart chosen on
   the iPhone), alive, bright and smiling when you keep your prayers and dim and sad when you miss them
   (the same level as on the iPhone); **I prayed …** for the prayer in progress; today's five prayers lit
   when marked; the last seven days as five dots a day with the count, and the days in a row with all five.
   Only what you mark counts. Marks made on the watch reach the iPhone's companion and the iPhone's marks
   reach the watch (WatchConnectivity); a tasbih round of 33 counts as on the iPhone.
4. **Tasbih**: 33 beads round the count light up one by one, with the dhikr of the round above the number
   (subḥān Allāh, al-ḥamdu lillāh, Allāhu akbar, in Arabic in every language). Count with the Digital Crown,
   a tap, or a double tap with two fingers (Series 9, Ultra 2 and later); a light tap on the wrist per count
   and a stronger one every 33; reset beside the hint.
5. **Nearest mosques**: from the watch's position (else the prayer city), built-in data plus Apple Maps
   when online search is on (only a rounded centre is sent). Each row's arrow points to the mosque as the
   watch is held. A mosque opens a small map, its distance and **Directions** (walking, in Maps). With
   nothing found, the mosques the iPhone last found are shown.
6. **Ask**: speak (dictation), Scribble or type. The watch answers from the common answers it carries,
   with their Qur'an references, hadith numbers and the "awaiting scholar review" label; other questions
   get a note to ask on the iPhone, which searches the books. Today's suggested questions are under the box.

**Reminders**: reminders from the iPhone already show on the watch. **Reminders on this watch** (off by
default) makes the watch set its own for the prayers you pick, ten days ahead, refreshed in the
background, for when the iPhone stays behind.

**Apple Health** (Settings on the watch, off by default): each prayer you mark adds mindful minutes to
Apple Health (5 to 30 minutes, 10 unless changed), ending when you mark it, or from the prayer's time for
a mark made on the iPhone. Only mindful sessions are written, nothing is read, and each prayer is written
once (a sync identifier per prayer). Apple Health has no spiritual category, so mindful minutes is where
it goes. The watch app asks for Health permission only when this is turned on.

**Complications** (any face with complications; long-press the face → Edit):

| Complication | Sizes | Opens |
|---|---|---|
| Next prayer | circular (ring and time), corner, rectangular (name, time, countdown, city), inline | Prayer |
| Prayer countdown | circular, corner, rectangular (with bar) | Prayer |
| Today's prayers | rectangular (five times, the next lit) | Prayer |
| Qibla | circular (arrow from north), corner, inline | Qibla |
| Tasbih | circular (count and ring of 33) | Tasbih |
| Nearest mosque | circular (distance), rectangular (name, arrow, distance) | Mosques |
| Companion | circular (the lantern or the heart, as full as on the iPhone) | Prayer |

The complications rank higher in the Smart Stack in the half hour before a prayer. **Prayer soon** is a
Smart Stack widget only: it comes up from 30 minutes before each prayer until 10 minutes after
(`ios/WatchWidgets/PrayerSoon.swift`).

**Control Center** on the watch: **Qibla** and **Tasbih** buttons (also for the Action button on Ultra).

**Live Activities on the watch**: the iPhone's next-prayer countdown and Qibla compass also show in the
watch's Smart Stack, in a layout made for the watch (prayer, time, countdown and bar; the arrow and the hint).

**Ready-made faces**: none ship. Apple only lets an app offer a face that was exported from a real watch
(`.watchface` files); Settings → Apple Watch on the iPhone adds any that are put in `ios/App/Resources/`,
and otherwise explains how to add the complications to a face. Sharing faces is reported broken with
WidgetKit complications on watchOS 26, so this needs a test on a real watch first.

Not on the watch: the library and books, Adhan recordings, the Fajr alarm (AlarmKit is iPhone and iPad
only), widgets' styles other than the accent colour.

## Android and Windows (brief)

* **Android**: the same tabs and Ask; 8 widget kinds (Next prayer, Countdown, Ring, Today, Mosque, Ask,
  Qibla, Actions) in 16 size variants, 3 styles (cream, green, night); app shortcuts Qibla, Mosques, Ask.
* **Windows**: Prayer, Qibla (from north), Nearest Mosque, Ask, Classical books (opened in the PC's PDF
  reader) and Settings; stays in the notification area and plays the Adhan and reminders while running.
  * Nearest Mosque: around the chosen city (a PC has no GPS), from the mosque data that comes with the app
    and, with "Search online for mosques" on, OpenStreetMap (the city centre is sent, rounded to about 1 km);
    walking directions open in the browser.
  * Ask: the common questions and the Qur'an (Arabic with Pickthall's English) and HadeethEnc hadith (Arabic
    and English) that come with the app, searched on the PC, every passage with its source.
  * Start with Windows: the installed app (MSI) opens in the notification area at sign-in (on by default, a
    switch in Settings); the Microsoft Store version has a startup task the person turns on in Windows
    Settings → Apps → Startup.

## Privacy (what stays and what goes)

* **On the device**: prayer times, Qibla, Ask and on-device answers, chat history, companion log, tasbih
  count, bookmarks.
* **Online, only for**: the mosque search with "Search online" on (location rounded to about 1 km; walking
  times rounded to about 100 m), map images, downloads the person taps (language packs, classical books,
  library files), and links the person opens.
* No accounts, no ads, no tracking, no cloud AI. Privacy manifest: `ios/App/PrivacyInfo.xcprivacy`.

## Content sources

GeoNames (cities), OpenStreetMap (mosques, ODbL), IslamHouse, HadeethEnc, QuranEnc, Tanzil, binbaz.org.sa
(fatwas), OpenITI (classical books), and the recordings in `shared/audio/CREDITS.md`. Licences:
`04-data-and-licenses.md`.
