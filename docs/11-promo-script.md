# Arabic promo: voiceover script

The Arabic promo video comes in two cuts from one script: **vertical 9:16, about 60 seconds** (Reels, TikTok,
Stories) and **wide 16:9, about 90 seconds** (YouTube, the website). No music: soft wind and birds, and a gentle
click as each feature appears (`tools/store/creative/ambience.py`). Every claim below matches the current build
(`docs/10-features.md`); keep it that way when editing.

## Recording

* One take per line, in a quiet room, phone held 20 cm from the mouth, no echo (a room with curtains or a closet).
* Calm, warm, unhurried; a short pause after each line. Save as one file per line (`01.m4a`, `02.m4a`, …) or one
  file with two seconds of silence between lines.
* Lines marked **(wide only)** are for the 90-second cut; the 60-second cut skips them.

## Script

| # | Seconds | On screen | Voice (فصحى) |
|---|---------|-----------|--------------|
| 01 | 0–5 | The sky at Fajr, the mosque on the horizon, the app icon | كلُّ صلاةٍ في وقتها، أينما كنت. |
| 02 | 5–10 | The name «أقرب مسجد» on a white card | «أقرب مسجد»: مواقيتُ الصلاة والقبلةُ وأقربُ مسجدٍ، في تطبيقٍ واحدٍ هادئ. |
| 03 | 10–17 | The prayer header: the next prayer, the time left, the sky of the hour | يعرفُ الصلاةَ القادمة، ويعدُّ لك الوقتَ المتبقّي، وسماؤه تتبدّل مع ساعات يومك. |
| 04 | 17–23 | The Qibla view turning until it lights up | ووجّه هاتفك، فإذا استقبلتَ القبلةَ أضاءت لك. |
| 05 | 23–29 | The map and the mosque compass | وأقربُ المساجد إليك على الخريطة، بالمسافة والاتجاه. |
| 06 | 29–36 | Ask: a question, then the answer with its sources | واسأل عن دينك، فتأتيك الإجابةُ من كتبٍ موثوقةٍ على هاتفك، مع مصادرها. |
| 07 | 36–41 | The library: classical books (wide only) | مكتبةٌ من الكتب والفتاوى والمحاضرات، تقرؤها وتسمعها دون اتصال. **(wide only)** |
| 08 | 41–47 | Prayer & Audio: Adhan voices and reminders | أذانٌ بأصواتٍ تختارها، وتذكيرٌ قبل كل صلاة. |
| 09 | 47–53 | Widgets on the Home Screen, the Dynamic Island | ومواقيتُك على الشاشة الرئيسية وفي الجزيرة الديناميكية، دون أن تفتح التطبيق. |
| 10 | 53–59 | The watch: the companion, prayers kept, the tasbih | وعلى ساعتك: رفيقٌ يفرحُ بصلاتك، وسجلٌّ لصلواتك، وتسبيحٌ بالتاج الرقمي. |
| 11 | 59–65 | iPhone, iPad, Mac, Apple TV, Vision Pro side by side (wide only) | على آيفون وآيباد وماك وآبل تي في وفيجن برو. **(wide only)** |
| 12 | 65–71 | «36 لغة» growing out of the word «مرحبًا» in many scripts | بستٍّ وثلاثين لغة. |
| 13 | 71–78 | A lock and a crossed-out ad (wide only) | بلا إعلانات، ولا تتبُّع، وتعمل مواقيتُك وقبلتُك دون إنترنت. **(wide only)** |
| 14 | 78–85 | The sky at Maghrib, the moon rising, the app icon | «أقرب مسجد». |
| 15 | 85–90 | App Store badge | حمّله الآن من متجر التطبيقات. |

The 60-second cut runs 01–06, 08–10, 12, 14, 15 with the same pictures, each a little shorter.

## Facts behind each line (keep true)

* 03: next prayer and countdown on the prayer header; sky follows the prayer period (`SkyPeriod`).
* 04: the Qibla view lights up when aligned (`AlignmentDetector`); on devices without a compass it is drawn from north.
* 05: nearest mosques with distance and direction (map and mosque compass).
* 06: answers come from the app's offline library with cited sources; common answers are labelled
  "awaiting scholar review" in the app, so the voice says «من كتبٍ موثوقة» and never «فتوى معتمدة».
* 07: 99 classical books (OpenITI) plus lectures and fatwas, downloadable once and read offline.
* 08: Adhan voices per prayer and reminders before the prayer (Prayer & Audio).
* 09: Home Screen widgets and the next-prayer Live Activity in the Dynamic Island.
* 10: Apple Watch spiritual health page (companion, prayers marked), tasbih on the Digital Crown.
* 11: the apps for iPhone, iPad, Mac, Apple Watch, Apple TV and Apple Vision Pro.
* 12: 36 interface languages (English and Arabic final; the rest drafts).
* 13: no ads and no tracking (privacy manifests: tracking false); prayer times and Qibla work offline. Map search
  and downloads need the internet.

## English promo

The same 15 lines and scenes in English (`Near-Mosque-Voiceover-EN`, three voices). `promo.py --voice-map
alghammas-en|ali-en|raed-en` picks the take; the assets folder then holds the English store screenshots and the
English preview recordings. The English cut sets the titles larger, word by word, with the key words highlighted in
gold (`textfx` in the timeline).

| # | Voice (English) |
|---|-----------------|
| 01 | Every prayer, on time… wherever you are. |
| 02 | Near Mosque: prayer times, the Qibla, and the nearest mosque… in one calm app. |
| 03 | It knows your next prayer, counts down the time left… and its sky changes with the hours of your day. |
| 04 | Point your phone… and when you face the Qibla, it lights up. |
| 05 | The mosques nearest to you, on the map… with distance and direction. |
| 06 | Ask about your faith… and the answer comes from trusted books on your phone, with its sources. |
| 07 | A library of books, fatwas and lectures… to read and listen to, offline. **(wide only)** |
| 08 | The Adhan in the voices you choose… and a reminder before every prayer. |
| 09 | Your prayer times on your Home Screen and in the Dynamic Island… without opening the app. |
| 10 | And on your watch: a companion that rejoices in your prayers, a record of every prayer, and tasbih on the Digital Crown. |
| 11 | On iPhone… iPad… Mac… Apple TV… and Vision Pro. **(wide only)** |
| 12 | In thirty-six languages. |
| 13 | No ads. No tracking… and your prayer times and Qibla work without the internet. **(wide only)** |
| 14 | Near Mosque. |
| 15 | Download it now on the App Store. |

## The live widgets scene (line 09, both languages)

Line 09 is in motion rather than two screenshots: a Home Screen with the app's widgets (cut from the widget
screenshot) whose Dynamic Island goes from the next-prayer countdown (compact, then expanded) to the Qibla compass
(the arrow turns until it faces the Qibla and lights up), and a Lock Screen with the next-prayer Live Activity
counting down while Siri answers "When is the next prayer in Near Mosque". The island, the cards and Siri's answer
follow the app's own views and strings (`PrayerLiveActivity.swift`, `QiblaLiveActivity.swift`, `IslandViews.swift`,
`siri_next_prayer`), at the moment of the widget screenshot (Riyadh, Maghrib at 5:29 PM, 2:27:52 left). They are
drawn for the film, not recorded from a device.
