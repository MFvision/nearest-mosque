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
