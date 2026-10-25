# 10. Everything in the app, one item at a time

A reference for the App Store listing, support and review. It describes what the code does on each device,
with the file each item lives in. Screenshots and where they go are in `09-app-store.md`. When the code
changes, this page is checked again; it is not a promise beyond the current build.

Near Mosque runs on iPhone, iPad, Mac (Mac Catalyst), Apple TV and Apple Vision Pro, all under the one
bundle ID `sa.zood.nearmosque`, plus Android, Windows and Linux. The Apple Watch app (`sa.zood.nearmosque.watchkitapp`)
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
| Onboarding steps | 13 (12 on Windows and Linux, which have no style step) |
| Android widgets | 8 kinds in 16 sizes, 3 styles |

## iPhone and iPad

### Layout

* Two tabs: **Prayer & Qibla** and **Nearest Mosque** (`ios/App/NearMosqueApp.swift`).
* **Ask AI** is a button ("Ask anything about Islam") that opens Ask full screen. On iOS 26 it sits in the
  tab bar's accessory slot and the tab bar shrinks on scroll; on earlier iOS it floats as a glass capsule.
* Settings opens as a sheet from the gear on each tab.
* **Classic menu** (Settings → Menu and start → Menu style): instead of the tab bar, one page (Prayer & Qibla)
  with a glass row at the bottom: Qibla, Nearest Mosque (opens over it, ✕ closes) and Ask.
* iPhone shows one column; iPad and Mac show two (list beside map, cards beside the header).
* Links into the app: `nearmosque://prayer`, `qibla`, `mosques`, `mosque?id=`, `settings`, `ask?q=`. A
  question in a link is only placed in the box, not asked, unless it comes from inside the app or from
  this phone's own widget (max 300 characters).
* Keyboard (iPad with keyboard, Mac): ⌘, Settings · ⌘1 Prayer · ⌘2 Mosques · ⌘3 Qibla · ⌘K Ask.

### Prayer & Qibla tab (`ios/App/Prayer/`)

1. **Header**: location pill, the Qibla arc with a gold dot at the Qibla's direction relative to the top
   of the phone, the next prayer with its time and countdown, distance to the Kaaba, the nearest-mosque
   chip. When the phone faces the Qibla the logo glows and the phone gives one haptic tap. Without a
   compass the arc is drawn from north and says so. With **Sky follows the weather** on (off by default),
   the sky also shows the prayer city's current weather (clear, partly cloudy, cloudy, rain, thunderstorm,
   fog, snow), and a small line under the location pill shows the temperature and the wind (°C/km/h, or
   °F/mph where the region uses them). Static under Reduce Motion, with no lightning flashes then; the normal
   sky returns silently when offline or when the data is over 3 hours old (`ios/App/Support/WeatherSky.swift`,
   `ios/App/Platform/WeatherService.swift`, rules in NMCore `Weather.swift`).
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

**Menu and start** (`StartSettingsSection.swift`, rules in `NMCore/StartScreen.swift`): Menu style (Tabs or
Classic); Open with (Where I left off, Prayer & Qibla, Qibla, Nearest Mosque, Ask), applied once at launch after
the tour, a widget or Siri link wins; App icon (Gold, Night, Green, Teal, Sky, Black; iPhone and iPad only, drawn
by `shared/brand/icons/make_icons.py`). Kept on the phone only.

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
    Al-Masjid an-Nabawi (Madinah), with preview. **Adhan length**: Full, or Short (the first "Allahu akbar,
    Allahu akbar" of the same recording, about 12 seconds).
  * **Iqama**: after each Adhan the countdown is to the iqama, then the prayer shows ("Dhuhr prayer has
    begun") before the countdown to the next prayer. Waits: Fajr 25, Dhuhr 20, Asr 20, Maghrib 10, Isha 20
    minutes; prayer time after the iqama 15 minutes; a notification at the iqama; each can be changed or the
    whole thing switched off. These are the usual waits, not a mosque's own timetable, and the app says so.
  * **Silent during prayer** (off by default): from the iqama (or the Adhan) to the end of the prayer, for the
    prayers chosen; on Friday from the Dhuhr Adhan for 45 to 90 minutes (60 by default) for the khutbah.
    Android turns Do Not Disturb on and off itself through its own Do Not Disturb rule (with the user's
    permission; Android 8 and 9 switch to priority only and back), with a quiet notification and "Sound on
    now"; turning sound on by hand is left alone. iPhone and iPad let no app change Focus, so the app gives a
    Shortcuts action, "End of prayer", for a Shortcut the user makes once (Set Focus: Do Not Disturb, On,
    Until that time); the Adhan or iqama notification has a button that runs it, as do Siri and the Action
    button. Not on Windows (no public way for an app to turn on Do Not Disturb). Not yet tried on devices.
  * Before the prayer: 10 minutes, 5 minutes, and a pre-prayer reminder at 15, 20 or 30 minutes; spoken
    in Arabic or English.
  * Each prayer on its own: notification, Adhan, the reminders. Sunrise (end of Fajr time).
  * Qur'an and dhikr: salawat on Fridays, daily Qur'an reading reminder, a verse before sleep; reciter
    Mishary Alafasy, or Al-Husary and Abdul Basit.
  * More: Surat Al-Kahf on Friday, Jumu'ah 45 minutes before Dhuhr, Ramadan suhoor and iftar, and the
    **Fajr alarm** (off, at Fajr, or 10/20/30/45 minutes before).
* Countdown on the Lock Screen (iPhone and iPad).

**Personalize**: Companion (Lantern, Heart, None); **App and widget style**: one choice of eight that
colours the app's buttons and accents, every widget, the Lock Screen and the Dynamic Island. Five keep the sky;
three are solid themes with their own background (`shared/design/themes.json`, `SolidTheme` in
`Shared/StyleAccent.swift`, contrast checked by `tools/check_tokens.py`): **Night garden** (forest black, mint for
the current prayer, red only for the iqama countdown and buttons; always dark), **Desert lantern** (mocha and champagne,
amber buttons; a cream day version follows the phone's light mode) and **Charcoal crimson** (true black for OLED;
always dark); **Sky follows
the weather** (off by default), with its note (what is sent, to whom, how often) and the "Weather: based on
data from MET Norway" link.

**Nearest Mosque**: Search online for mosques (on by default).

**Downloads**: installed packs with remove, Import from file (checked), Restore built-in data.

**About**: Privacy, Sources and licenses (with the MET Norway weather credit), On-device AI status, Show
welcome tour, version; publisher AVA ai (avaai.sa), website nearmosque.net, support app@vision.bz, phone +966 57 937 2403, terms of use, copyright.

**Moving between the two pages**: swipe sideways on Prayer & Qibla to go to Nearest Mosque, and from the
screen's edge on Nearest Mosque to come back (from the edge only, so dragging the map never changes page).

### Onboarding (`ios/App/Onboarding/`)

"First Light", thirteen steps on the living sky under a "Your day" header whose chips fill as each answer is
given: the promise (with the language menu) · where you pray (your location, asked for at that moment, or a
city) · when Asr begins (both schools with today's real times, and the calculation method) · how to be called
(Makkah, Madinah, the short Adhan or a banner only, with previews; Silent during prayer on iPhone and iPad) ·
Friday and Al-Kahf reminders · Sky follows the weather (a preview with sample values and no network; off until
turned on, with the MET Norway credit) · the app's style and appearance · building the day · the day itself (an
arc to drag the sun or moon along, the countdown, the six times, the Qibla, the Hijri date) · widgets, Siri,
Apple Watch and Ask · the rest of the app · exactly what leaves the device, with the notification ask · done.
Every answer is saved through its setting at once; Settings → "Show welcome tour" replays it, and someone who
finished it is not sent through it again. Two panes on iPad, Mac and Apple Vision Pro. Under Reduce Motion every
transition is a short crossfade.

## Widgets (iPhone, iPad, Mac) (`ios/Widgets/`)

Each widget has one setting, **Widget style**: Same as app (default), Gold and cream, Green, Night sky,
Teal, Time of day (colours follow the prayer period), Night garden, Desert lantern, Charcoal crimson.

| # | Widget | What it shows | Home Screen sizes | Lock Screen sizes | Tap opens |
|---|---|---|---|---|---|
| 1 | Next prayer | The next prayer, its countdown and today's times (after the Adhan: the countdown to the iqama, then "… prayer has begun") | Small, Medium, Large | Circular, Rectangular, Inline | Prayer |
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
   * Starts when the app opens with "Countdown on the Lock Screen" on (default on) and a city set. At the Adhan
     it turns by itself to "Time for …" and the countdown to the iqama (with the iqama off: "Time for …" for 15
     minutes); iOS removes it after at most 8 hours. No push updates.
2. **Qibla compass** (`ios/Widgets/QiblaLiveActivity.swift`), started from the Qibla widget's dial: the Kaaba at
   the top of an arc, the gold dot where the Qibla is, the arrow turning with the phone (updated about four
   times a second and gliding between updates), bearing, turn hint, glow when facing; uses the compass for 2
   minutes and then ends.

Both follow the app's style. Neither exists on Mac or Vision Pro.

## Siri and Shortcuts (`ios/App/AppShortcuts.swift`)

| Action | Example phrase (English; Arabic too) | Result |
|---|---|---|
| Next prayer | "When is the next prayer in Near Mosque" | Spoken answer without opening the app |
| Nearest mosque | "Where is the nearest mosque in Near Mosque" | Spoken answer and card (phone unlocked); a fresh location even with the app in the background, the downloaded mosques, then Apple Maps when online search is on |
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

**Apple Health** (Settings on the watch, off by default): each prayer you mark on the watch adds mindful
minutes to Apple Health (5 to 30 minutes, 10 unless changed), ending when you mark it. Marks made on the
iPhone count for the companion but are not written to Health (their time is not known). Only mindful sessions are written, nothing is read, and each prayer is written
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

## Android, Windows and Linux (brief)

* **Android**: the same tabs and Ask; 8 widget kinds (Next prayer, Countdown, Ring, Today, Mosque, Ask,
  Qibla, Actions) in 16 size variants, 3 styles (cream, green, night); app shortcuts Qibla, Mosques, Ask.
  **Sky follows the weather** as on the iPhone (off by default; offered on its own step of the first-run tour,
  a switch in Settings → Appearance with its note and the MET Norway link): the header's sky shows the prayer
  city's weather and a line gives the temperature and the wind. Refreshed only while the app is in the
  foreground, never by a background job (`android/app/src/main/kotlin/sa/zood/nearmosque/data/Weather.kt`,
  `ui/glass/WeatherSky.kt`; shared rules in `android/core` `Weather.kt`).
* **Windows and Linux** (one app, `windows/`): Prayer, Qibla (from north), Nearest Mosque, Ask, Classical books
  (opened in the PC's PDF reader) and Settings; stays in the notification area and plays the Adhan and reminders
  while running. After each Adhan the countdown is to the iqama, then "… prayer has begun", with a notification at
  the iqama. Settings: language, city, method, Asr, high-latitude rule, polar rule (show as unavailable, or the
  nearest latitude, 48.5°, marked "Estimated" on the prayer screen; a polar day or night without times offers it
  in one click), Ramadan Isha (Umm al-Qura), Hijri adjustment (−2…+2), minute adjustments for each of the six
  times, sunrise included (−30…+30), Adhan on/off, Makkah or Madinah for the Adhan and the Fajr Adhan, Full or
  Short Adhan; before the prayer: 10 and 5 minutes before and the pre-prayer reminder (15, 20 or 30 minutes), all
  spoken, in Arabic or English; each prayer (its Adhan and its 10, 5 and pre-prayer reminders), sunrise; the iqama
  waits; Qur'an and dhikr: salawat on Friday, the Qur'an reading reminder, a verse before sleep (each at a time of
  day, recited by Alafasy or Al-Husary/Abdul Basit), Surat Al-Kahf on Friday; more reminders: Jumu'ah, Ramadan
  suhoor and iftar (Hijri with the adjustment), and the Fajr alarm (at Fajr or 10–45 minutes before; the short
  Adhan repeated until Stop in the window or the tray menu, at most 10 minutes, with the window brought up);
  online mosque search, start at sign-in, and About (publisher, website, support,
  phone, terms, privacy, version). Opening the app a second time brings the running window forward. The reminders
  and the alarm come only while the PC is awake and the app runs (no wake timers); one missed during a sleep is not
  given late (more than two minutes after its time).
  Not on the PC: Silent during prayer, widgets.
  * First-run tour ("First Light", `windows/src/main/kotlin/sa/zood/nearmosque/desktop/Onboarding.kt`): the
    phones' twelve steps without the style step (the desktop has one look), two panes in a wide window: the city
    (the time zone's largest offered, or a search), the Asr school with both real times, the Adhan, Friday and
    Al-Kahf, the weather sky, the day built from the answers, the notification area and Ask, the rest of the app,
    and what leaves the PC. Every answer is saved through its setting at once. Settings → "Show welcome tour"
    shows it again; someone who used the app before it is not sent through it.
  * Sky follows the weather (off by default; asked in the first-run tour, or, for someone who used the app before
    the tour, offered once in a card on the prayer screen; a switch in Settings →
    Appearance with the MET Norway link): a line under the city name (the condition's icon, the temperature and the
    wind) and a sky strip behind the next-prayer card,
    refreshed only while the window is open (not while it is minimized or closed to the notification area)
    (`windows/src/main/kotlin/sa/zood/nearmosque/desktop/Weather.kt`, `SkyStrip.kt`).
  * Nearest Mosque: around the chosen city (a PC has no GPS), from the mosque data that comes with the app
    and, with "Search online for mosques" on, OpenStreetMap (the city centre is sent, rounded to about 1 km);
    walking directions open in the browser.
  * Ask: the common questions and the Qur'an (Arabic with Pickthall's English) and HadeethEnc hadith (Arabic
    and English) that come with the app, searched on the PC, every passage with its source.
  * Start with Windows: the installed app (MSI) opens in the notification area at sign-in (on by default, a
    switch in Settings). The MSI is what the website offers (downloaded from GitHub, not a store); the optional
    MSIX package (`windows/msix/`, only if it is ever sent to the Microsoft Store) has a startup task the person
    turns on in Windows Settings → Apps → Startup.
  * Linux: a .deb (`sudo apt install ./near-mosque_*.deb`) and an AppImage. Start at sign-in uses
    `~/.config/autostart`; notifications go through `notify-send`. On a desktop without a notification area
    (GNOME by default) closing the window quits the app and a start at sign-in opens it minimized. Details and
    what has been tested: `05-status.md` §5.6.

## Privacy (what stays and what goes)

* **On the device**: prayer times, Qibla, Ask and on-device answers, chat history, companion log, tasbih
  count, bookmarks, and the last weather report with Sky follows the weather on (in the cache, out of backups).
* **Online, only for**: the mosque search with "Search online" on (location rounded to about 1 km; walking
  times rounded to about 100 m), map images, downloads the person taps (language packs, classical books,
  library files), links the person opens, and the weather for the prayer city with Sky follows the weather on
  (its location rounded to about 1 km, to MET Norway, at most about every 30 minutes while the app is open;
  the last report is cached on the device and deleted when the switch is turned off).
* No accounts, no ads, no tracking, no cloud AI. Privacy manifest: `ios/App/PrivacyInfo.xcprivacy`.

## Content sources

GeoNames (cities), OpenStreetMap (mosques, ODbL), IslamHouse, HadeethEnc, QuranEnc, Tanzil, binbaz.org.sa
(fatwas), OpenITI (classical books), MET Norway (weather, CC BY 4.0 and NLOD 2.0), and the recordings in
`shared/audio/CREDITS.md`. Licences:
`04-data-and-licenses.md`.
