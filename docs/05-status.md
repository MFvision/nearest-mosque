# 5. Status, test results and limitations

Snapshot: 2026-10-03, second increment (Liquid Glass redesign, onboarding, live map and live results, phone time zone). Not store-ready.

## 5.1 Feature status

Legend: **Done** = implemented and covered by automated tests or rendered screenshots;
**Built** = implemented and compiled, not exercised on a device; **Partial**; **Not started**.

| Area | Android | iOS | Evidence / notes |
|---|---|---|---|
| Exactly three tabs + gear for everything else | Done | Done | Android screenshots; iOS 26 simulator screenshots (system Liquid Glass tab bar) |
| Quiet Glass design: sky per prayer period, glass surfaces, floating glass tab bar | Done | Done | iOS 26 simulator screenshots (real Liquid Glass); Android Robolectric screenshots (drawn glass) |
| Animated onboarding (6 pages, language + location setup, replay from Settings) | Done | Done | Screenshots of each page on both platforms; Reduce Motion shows still frames |
| Live street map | Built | Done | iOS: Apple Maps map rendered in the simulator with mosque pins. Android: MapLibre + OpenFreeMap; compiles and is wired, not rendered in tests (native GL) |
| Live mosque results merged with downloaded data | Done | Done | Parser/merge tested in both cores (shared Overpass fixture); iOS simulator showed Apple Maps results and a walking route; Android Overpass client not exercised against the live service here (blocked by this environment's proxy) |
| Mosque compass (bearing + distance per mosque, turns with heading) | Done | Done | Screenshots on both platforms (north-up; live heading needs a device) |
| Seven languages, device default + persistent override, RTL (ar, ur) | Done | Built | 206 keys × 7 from one source; Android ar/ur/tr screenshots; iOS override via String Catalog bundles. Non-en/ar translations are drafts needing native review |
| City picker with IANA zone, offline | Done | Built | 13,090 cities; search in Latin and Arabic script tested (both) |
| Device location (foreground, at point of use), no default city | Done | Built | Android `LocationManager`; iOS Core Location. Not tested on hardware |
| Time zone for device fix: phone's zone when consistent (like the website), city zone + confirmation only for a clear mismatch near a city | Done | Done | Tests in both cores (alias, at sea, traveller, no phone zone) |
| Prayer times (11 methods, Asr, high-latitude rule, offsets) | Done | Done | 23 golden cases vs independent NOAA reference: worst deviation 84 s (Adhan rounds to the minute), tolerance 120 s, both platforms run the same file |
| Date line, DST, midnight Isha, zero coordinates | Done | Done | Apia/Kiritimati, London/NY/Auckland DST, Oslo middle-of-night, (0,0) |
| Polar day/night: unavailable unless the user picks nearest-latitude (48.5°) | Done | Done | Tromsø fixtures; UI card + estimated badge |
| Umm al-Qura Ramadan Isha +30 min, Hijri (Umm al-Qura) with adjustment | Done | Done | Tests in both cores |
| Countdown from instants (DST-correct), next prayer skips sunrise, "Fajr ends at sunrise" | Done | Done | Tests in both cores |
| Header: home / expanded / compact sticky, arc = time progress, disc glow = alignment | Done | Built | Android screenshots (home, expanded, first launch); iOS compiles in CI |
| Qibla bearing + distance (great circle, true north) | Done | Done | 14 fixtures incl. near-Kaaba, antipode; cross-checked with Adhan |
| Live compass (true heading), shortest-path rotation, smoothing, hysteresis alignment + haptic, bearing-only fallback | Built | Built | Math unit-tested; sensor paths need physical devices (see 5.3) |
| Reminders (opt-in per prayer, bounded horizon, reboot/time-change rebuild) | Built | Built | Android 7 days of exact/inexact alarms; iOS ≤ 60 pending. Not tested on devices |
| Mosque packs: offline nearest/list, straight-line distance, dedupe, distinct empty states | Done | Done | Robolectric (Room) and GRDB tests: found / not-downloaded / no-records; ranking deterministic |
| Mosque details, call/website/directions hand-off, favorites surviving pack updates | Done | Built | Favorites survival tested on both data layers |
| Opening hours never shown as open | Done | Done | Status always unknown; raw tagged hours shown with source date |
| Search races (generation tokens) | Done | Done | Unit-tested; UIs drop stale results |
| Packs: manifest, SHA-256 verify, atomic install/rollback, remove/restore, import | Done | Done | Corrupt update keeps previous version (tested both). Android imports `.nmpack` zip; iOS imports a pack folder. No download server (resume designed, not built) |
| Ask AI: cited search over bundled Quran + Pickthall, common questions in 7 languages, follow-ups | Done | Done | 20 retrieval cases identical across Python reference, Room FTS4 and GRDB FTS5; rendered answers in en/ar |
| Ask AI library: IslamHouse catalogue (24,787 records in 7 languages) as a separate collection; answers list matching books, articles, fatwas, videos and audio in the interface language (then English, then Arabic) | Done | Done (CI) | "What is Islam?" finds IslamHouse items in en, ar and (via fallback) ur; an off-topic question finds none. Quran fixtures unchanged. Redistribution permission not yet confirmed (release gate) |
| In-app reader: PDF books (downloaded on tap, kept offline), video/audio player, article page | Done (PdfRenderer, VideoView, WebView) | Done (PDFKit opening at the first page that mentions the question, AVKit, Safari view) | Host allow-list (HTTPS IslamHouse only) unit-tested on Android; reading a real book on a device is still to be checked on TestFlight |
| Library search: light Arabic stemming, prefix matching, multilingual lexicon (126 term groups in 7 languages) | Done | Done | Python reference `tools/library_search.py` and Kotlin/Swift ports agree on `shared/fixtures/library-retrieval.json`; Quran fixtures unchanged |
| More sources: Ibn Baz fatwas (24,138), HadeethEnc hadiths (7 languages), QuranEnc translations (ur, tr, id, fr, es) | Done | Done (CI) | Reader shows each part (question/answer, hadith/explanation/lessons, verse/translation/notes) with grade, printed source and the required attribution and version |
| Library packs install for the reader's languages (interface language, Arabic, and English for non-Arabic readers), in the background after the Quran and mosques | Done | Done | Installs stream line by line (no whole pack in memory); other languages install when the interface language changes |
| New app icon (customer's layered emblem with the brand's location arrow) | Done | Done | `shared/brand/near-mosque-icon.svg`; adaptive icon with monochrome layer on Android |
| "Search these sites too" under each answer: IslamQA, Dorar, Ibn Uthaymeen, alifta (link out, nothing copied) | Done | Done | Link building unit-tested on both platforms |
| Directions: choice of maps app | System chooser of installed maps apps | Apple Maps, Google Maps, Waze | Google Maps/Waze open their app when installed, else their website |
| Source cards: reference, verbatim original, labelled translation, read-in-context offline, original link | Done | Built | Android screenshots |
| "Not found" instead of guessing; prompt-injection question returns nothing | Done | Done | Fixtures |
| Common answers labelled "awaiting scholar review" | Done | Done | All 20 are `unreviewed`; none may be relabelled without a named reviewer |
| 20 common questions in 7 languages (added: what is Islam, five pillars, becoming Muslim, the Prophet ﷺ, the Quran, how to pray, Hajj, Friday prayer, halal food, dua, parents, the Kaaba), each citing verses and, where one fits, a HadeethEnc hadith shown in the reader's language | Done | Done | Matching checked on 73 phrasings in 7 languages; 14 shared retrieval cases; non-English summaries are draft translations |
| Meaning-based (semantic) library search, hybrid with word search | Done | Done | Same results on all platforms (shared fixture). On 30 test questions in 7 languages, a correct fatwa or hadith in the top 6 for 23 (word search alone: 20); off-topic questions gain at most one item. Vectors build in the background after install (about 40 s for ar+en+ur in the Android test runner; unmeasured on phones) |
| iOS: on-device rephrasing of questions into Arabic and English search queries (Apple Intelligence) | — | Built | Unverified on a device; skipped when the model or language is unavailable |
| On-device generation | Not started | Built | iOS 26 Foundation Models provider, gated on availability + language, output rejected unless every paragraph cites a supplied passage. Android: no model shipped (cited search only) |
| Cloud AI | — | — | Not implemented; off by design |
| OCR, PDF/EPUB import | Not started | Not started | Next increments; licensing first (see 04) |
| Guided learning flows | Not started | Not started | |
| Light and dark appearance following the phone (Settings → Appearance overrides) | Done | Done | Daytime pastel skies in light mode; `tools/check_tokens.py` checks 4.5:1 for text and gold on every light sky stop |
| Prayer header in three levels: compact bar → sky card (next prayer, sun or moon on its path with the prayers marked, Qibla row) → full compass with a direction beam | Done | Done | `DayArc` unit-tested on both platforms; screenshots: Android `sky_card_*`, iOS CI 06c/06d/11b/15 |
| Navigation: two tabs and a floating Ask AI pill (iOS 26: tab bar bottom accessory) opening the chat full screen | Done | Done | Screenshots both platforms |
| Ask AI: thinking card with the real steps, chats saved on the phone (questions only, excluded from backup), history, new chat, suggested questions, Library card | Done | Done | Android `ChatHistoryTest`; iOS unverified on a device |
| Reduced motion / transparency, contrast, large text | Done (150% text screenshots) | Built (accessibility-medium screenshots in CI) | Contrast checked by `tools/check_tokens.py`. Screen-reader labels audited in code (map, rows, radar, tap targets fixed); not yet tried with VoiceOver/TalkBack on a device. iOS Increase Contrast not specially handled |
| Home-screen widgets: next prayer with a live countdown, today's times; cream, green or night look | Done (RemoteViews screenshots) | Built (CI simulator build) | Android: small and medium, style picked on add and reconfigurable, refreshed after each prayer. iOS: small, medium, large and Lock Screen (circular, rectangular, inline) plus a Control Centre Qibla control; state shared through the app's keychain group. Not tried on a device |
| Reminders and alarms: minutes before, Friday (45 min before Dhuhr), Ramadan suhoor and iftar, Fajr alarm | Built | Built | Planner unit-tested on both cores. Android: alarm-clock Fajr alarm with Stop. iOS 26: AlarmKit Fajr alarm (API not exercised on a device). No adhan audio: needs a licensed recording |
| Siri and shortcuts: Open Qibla, Next prayer; nearmosque:// links | Built | Built | iOS App Intents (English phrases only); Android static shortcuts. Not tried with Siri or a launcher on a device |
| Read aloud: the answer, then up to two cited verses (Arabic and translation) | Built | Built | System text-to-speech; voices depend on what the phone has installed |
| Full-screen mosque map | Built | Built | Expand button on the map card |
| Scholars' answer first: a matching fatwa leads the answer card | Done | Built | Android data tests; iOS CI build |
| Tafsir (Al-Mukhtasar) for cited verses, in ar, tr, id, fr, es (Arabic for other readers) | Done | Built | Android data tests resolve the 2:255 tafsir; a Tafsir button on each Quran citation opens it. Asbab al-nuzul not included (no licensed source) |
| 36 interface languages (29 added: ru, bn, fa, prs, zh, hi, pt, ha, sw, tl, vi, th, km, ug, ckb, bs, sr, mk, hu, nl, ka, si, te, kn, ml, mr, gu, pa, as) from one table, `shared/i18n/languages.json` | Done (screenshots: fa right to left, hi, ru, zh) | Built | All 374 strings drafted for each new language and checked for placeholders and plural forms; every new language is a draft that needs a native speaker's review before release, Hausa, Assamese and Khmer first. The app name stays "Near Mosque" except in Arabic-script languages, as in the existing drafts. Content for the new languages is downloaded on request (next row) |
| Content for the new languages downloaded on request: a card after choosing the language (Settings and the last onboarding page) offers that language's hadith (HadeethEnc), Quran translation and Mukhtasar tafsir (QuranEnc) and IslamHouse library, with the size; nothing is fetched until the reader taps Download | Done (download, check and install tested against a fake host) | Built | Packs are built and published by the Content packs workflow (`tools/build_remote_packs.py`) as GitHub release assets; the app carries the catalog (`shared/content/remote-packs.json`) with each manifest's SHA-256 and installs only content that matches it, so the host can be changed (one setting) without trusting it. Downloading contacts github.com, which sees the phone's IP address. Chinese, Thai, Khmer, Lao and Myanmar text is indexed as overlapping two-letter pieces (no spaces between words), the same on both platforms and in the reference tools |

## 5.2 Test results (this environment and CI)

| Suite | Where | Result |
|---|---|---|
| Python references (`gen_fixtures.py`, `reference_search.py --gen`) | Linux | 23 prayer, 14 Qibla, 12 normalization, 20 retrieval cases; 0 failures |
| Android `:core:test` (adds online parser/merge and time-zone cases) | Linux (local) + CI | 28 tests, 0 failures |
| Android `:app:testDebugUnitTest` (Robolectric data layer + 16 screenshot tests incl. 6 onboarding) | Linux (local) + CI | 23 tests, 0 failures |
| Android `:app:assembleDebug` | Linux (local) | `app-debug.apk` 76 MB (MapLibre native libraries for all ABIs; a Play bundle splits per device) |
| `NearestMosqueKit` `swift test` (adds online parser/merge and time-zone cases) | Linux Swift 6.1 (local + CI) and macOS (CI) | 21 tests, 0 failures |
| iOS app `xcodebuild` and launch in the iOS 26 Simulator (iPhone, Xcode 26.3) | GitHub macOS runner | Builds, installs, launches; screenshots of onboarding, Prayer (en/ar), compass, Mosques (compass, live map), Ask (en/ar) |
| Strings / fixtures / tokens drift | CI | pass |

Screenshots (Robolectric, rendered from the real database and calculations at 2026-10-03 05:30 SAST,
Cape Town, MWL) are produced by `./gradlew :app:recordRoborazziDebug` and uploaded by CI as an artifact.

## 5.3 Not verified here

* **No physical device or emulator testing** on either platform (this environment has no KVM and no
  Mac). Location fixes, compass accuracy, magnetic interference, haptics, notifications/alarms
  across reboot and Doze, Focus modes, performance, battery and memory are unmeasured.
* iOS now runs in the iOS 26 Simulator in CI (screenshots reviewed), but not on a physical iPhone:
  real compass heading, haptics, notifications and Foundation Models answers are unobserved.
* Android's live map (MapLibre) and the Overpass request were not exercised here: Robolectric cannot
  render native GL, and this environment's proxy blocks the Overpass host.
* VoiceOver/TalkBack, Dynamic Type at accessibility sizes, Reduce Motion/Transparency: implemented,
  not audited with assistive technology.
* Translations other than English and Arabic are machine-assisted drafts.

## 5.4 CI evidence

Workflow `Nearest Mosque apps`, run 37148659597 (commit `6ae16bb`, when the project still lived on a
branch of MFvision/ZOOD-PDF): **all four jobs green**
(https://github.com/MFvision/ZOOD-PDF/actions/runs/37148659597). The same workflow now runs here as
`.github/workflows/ci.yml`.

| Job | Result |
|---|---|
| Shared strings, design tokens (42 contrast checks), fixture regeneration | pass (no drift) |
| Android: `:core:test`, `:app:testDebugUnitTest`, screenshots, `assembleDebug` | pass (ubuntu-24.04, JDK 21) |
| NearestMosqueKit on Linux (Swift 6.1) | 21/21 |
| iOS: package tests on macOS; `xcodebuild` with **Xcode 26.3**; install and launch in the iOS 26 Simulator; 12 screenshots (artifact `ios-simulator-screenshots`) | pass; screenshots reviewed: real Liquid Glass tab bar and surfaces, Apple Maps live map with pins, Apple Maps live results ("Live from Apple Maps") and a walking route ("480 m · 7 min"), Arabic RTL |

Issues found only by looking at the simulator screenshots and fixed: the disc's glow layer enlarged the
disc so it covered the prayer name; GeoNames romanized mixtures ("kېp ټawn") shown as the Arabic city
name; the Ask header scrolling under the status bar.

### TestFlight

Run 37221509202 of `testflight.yml` (2026-10-04): archived (Release), signed with automatic signing via
the App Store Connect API key, and uploaded **version 0.2.0 build 102** of `sa.zood.nearmosque`
("Uploaded NearMosque", "EXPORT SUCCEEDED"). Device testing on TestFlight is the next step.

## 5.5 Known limitations and next increments

1. Offline street-map packs (self-built vector tiles) and regional pack downloads with resume;
   Android walking routes (needs a routing service); optional Google Places with a restricted key.
2. More mosque regions; measured coverage reports per region; congregation timetables as separate,
   dated data.
3. Book ingestion pipeline (PDF/EPUB/HTML, OCR review), licensed hadith and translations, embeddings,
   IslamHouse inventory once permissions exist.
4. On-device model for Android (LiteRT-LM or llama.cpp) after benchmarking memory, latency and
   Arabic grounding on real devices. Gemini Nano (ML Kit GenAI) is ruled out: its terms prohibit apps
   likely to be used by people under 18, and a prayer app is used by families. Until a model ships,
   Android answers with the quoted, cited passages.
8. "Search these sites too" links (IslamQA, Dorar, Ibn Uthaymeen, alifta) stay while the permission
   requests in docs/08-permission-requests.md are pending; their content is added once permission is given.
5. City names are stored in the language active when chosen; re-localize by city id.
6. Scholar review of the eight common answers and native review of translations.
7. Pickthall translation source for any commercial release (licensing gate).
