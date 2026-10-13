# 9. App Store screenshots and where each one goes

This is the guide for whoever uploads the App Store listing (a person or an agent). The screenshots
are real captures of the app's Debug builds, taken in Apple's simulators (iPhone, iPad, Apple TV, Apple
Vision Pro) and from the Mac app on a Mac runner. They are not mock-ups. A file named for a slot is
already at a size App Store Connect accepts for that slot.

## Getting the files

1. GitHub → Actions → **App Store screenshots** → **Run workflow**. Leave the inputs empty for every
   device in all 22 store languages, or name some (e.g. `iphone ipad`, `ar en`).
2. When it finishes, download the run's artifact **app-store-screenshots** (kept 30 days) and unzip it.
3. A full run also makes a release, `store-screenshots-<commit>`, that does not expire (one zip per
   device type plus `manifest-sheets-icons.zip`; unzip them all into one folder). The workflow can make
   it only when Settings → Actions → General → Workflow permissions is "Read and write permissions".

To redo a few captures, run it with **only** (e.g. `ar/01-prayer en/05-audio`) and **from_run** (the ID of
the run that has the rest). To redo only the sorting after a change to `tools/store/screens.json`, run it
with **from_run** and **capture** off; nothing is captured again.

The layout of the files:

```
<display type>/<App Store locale>/<NN>-<screen>.png   for example APP_IPHONE_67/ar-SA/01-prayer.png
manifest.json                                          every file, its slot, locale, position, size, md5, sha256
contact-sheets/<device>.jpg                            all languages of one device at a glance
icons/                                                 the app icons as built into the apps (reference only)
```

`manifest.json` is the source of truth. An agent should read it and not guess from folder names. Each
entry in `screenshots` says:

| Field | Meaning |
|---|---|
| `ascPlatform` | The App Store version the screenshot belongs to: `IOS` (iPhone and iPad), `MAC_OS`, `TV_OS`, `VISION_OS` |
| `displayType` | The screenshot set (`screenshotDisplayType` in the App Store Connect API) |
| `locale` | The App Store localization, e.g. `ar-SA`, `en-GB` |
| `position` | Order on the store page, 1 = first |
| `file` | Path inside the artifact |
| `width`, `height`, `bytes`, `md5`, `sha256` | Checks; `md5` is the `sourceFileChecksum` App Store Connect asks for when an upload is committed |
| `sameFileAs` | Set for locale variants (e.g. `en-GB`): the file is the same as that locale's |
| `title`, `shows` | What the screen is, for captions or review notes |

`skipped` lists Apple Vision Pro captures left out because the window was still loading; that locale's
set is one shorter and the positions close up. `missing` lists any capture that failed. Do not upload a set for a locale that has missing files
without telling the owner.

## The slots

App Store Connect needs one size per device family; it scales it down for smaller devices of the
same family (the 6.9-inch iPhone set covers 6.5-inch and smaller, the 13-inch iPad set covers smaller
iPads). At most 10 screenshots per set.

| Device | `displayType` | App Store version | Sizes the files have (pixels) |
|---|---|---|---|
| iPhone 6.9" | `APP_IPHONE_67` | iOS | 1320 × 2868 (portrait) |
| iPad 13" | `APP_IPAD_PRO_3GEN_129` | iOS | 2064 × 2752 (portrait) |
| Apple Watch Ultra | `APP_WATCH_ULTRA` | iOS (the watch app ships inside the iPhone app) | 422 × 514 |
| Mac | `APP_DESKTOP` | macOS | 1280 × 800, 1440 × 900, 2560 × 1600 or 2880 × 1800 (the app window on a blurred copy of itself) |
| Apple TV | `APP_APPLE_TV` | tvOS | 1920 × 1080 or 3840 × 2160 |
| Apple Vision Pro | `APP_APPLE_VISION_PRO` | visionOS | 3840 × 2160 |

The exact size of every file is in the manifest.

## The screens, in store order

### iPhone 6.9" display: `APP_IPHONE_67`

| # | File | Title | What it shows |
|---|---|---|---|
| 1 | `01-prayer.png` | Prayer times | Home: the next prayer, its time and the time left, the Qibla arc and today's five prayers on the sky for the current part of the day. |
| 2 | `02-qibla.png` | Qibla compass | The Qibla compass: the Kaaba at the Qibla bearing with the bearing and the distance to Makkah. On a phone with a compass the dial turns with the phone; without one (simulator, Mac) it is drawn from north and says so. |
| 3 | `03-mosques-map.png` | Nearest mosques on the map | Mosques around the person on the map, with the list of the nearest ones and their distance. |
| 4 | `04-ask.png` | Ask with cited sources | Ask (opened from the Ask AI button): the answer to the common question on the Quran as a miracle, from the app's offline library, with its sources. |
| 5 | `05-classics.png` | Classical books | The library's classical books (Arabic, from OpenITI): grouped by subject, each downloadable once and read offline. |
| 6 | `06-widgets.png` | Home Screen widgets | The Today widget (all prayers of the day) in its sizes, in the app's style. |
| 7 | `07-island.png` | Dynamic Island and Lock Screen | The next-prayer Live Activity in the Dynamic Island and on the Lock Screen. |
| 8 | `08-audio.png` | Adhan and reminders | Prayer & Audio settings: the Adhan voice for each prayer, reminders before the prayer and at sunrise. |
| 9 | `09-mosques-radar.png` | Mosque compass | The mosque compass: the nearest mosques placed around the person by direction and distance. |
| 10 | `10-prayer-dark.png` | Prayer times in dark mode | The prayer screen in dark mode. |

### iPad 13" display: `APP_IPAD_PRO_3GEN_129`

| # | File | Title | What it shows |
|---|---|---|---|
| 1 | `01-prayer.png` | Prayer times | Home: the next prayer, its time and the time left, the Qibla arc and today's five prayers on the sky for the current part of the day. |
| 2 | `02-qibla.png` | Qibla compass | The Qibla compass: the Kaaba at the Qibla bearing with the bearing and the distance to Makkah. On a phone with a compass the dial turns with the phone; without one (simulator, Mac) it is drawn from north and says so. |
| 3 | `03-mosques-map.png` | Nearest mosques on the map | Mosques around the person on the map, with the list of the nearest ones and their distance. |
| 4 | `04-ask.png` | Ask with cited sources | Ask (opened from the Ask AI button): the answer to the common question on the Quran as a miracle, from the app's offline library, with its sources. |
| 5 | `05-classics.png` | Classical books | The library's classical books (Arabic, from OpenITI): grouped by subject, each downloadable once and read offline. |
| 6 | `06-widgets.png` | Home Screen widgets | The Today widget (all prayers of the day) in its sizes, in the app's style. |
| 7 | `07-audio.png` | Adhan and reminders | Prayer & Audio settings: the Adhan voice for each prayer, reminders before the prayer and at sunrise. |
| 8 | `08-widgets-qibla.png` | Qibla and ring widgets | The Qibla widget and the prayer ring widget in the teal style. |
| 9 | `09-onboarding.png` | Welcome | The first page of the welcome tour. |

### Apple Watch Ultra: `APP_WATCH_ULTRA`

In App Store Connect this set is under the iOS version, in the Apple Watch section of each locale.

| # | File | Title | What it shows |
|---|---|---|---|
| 1 | `01-watch-prayer.png` | Prayer times on the wrist | Apple Watch: the next prayer, its time and a live countdown on the sky of the hour, then today's times with their reminder bells. |
| 2 | `02-watch-qibla.png` | Qibla compass on the watch | Apple Watch: the live Qibla compass, facing the Qibla (the watch taps the wrist when you face it). |
| 3 | `03-watch-spirit.png` | Spiritual health | Apple Watch: the companion bright or tired with the prayers you mark, today's five prayers, the last seven days and the days in a row with all five. |
| 4 | `04-watch-tasbih.png` | Tasbih on the Digital Crown | Apple Watch: the tasbih counter; turn the Digital Crown, tap or double-tap to count, and the ring fills every 33. |
| 5 | `05-watch-mosques.png` | Nearest mosques | Apple Watch: the nearest mosques with their distance and an arrow pointing to each. |
| 6 | `06-watch-ask.png` | Ask on the watch | Apple Watch: a common answer (asked by voice) with its Qur'an references and the review label. |

The simulator has no compass, so the Qibla screenshot is made with a set heading (`-demoHeading`); on a
watch the dial turns with the wrist. The skies are set per screen (`-demoSky`: Maghrib behind the Qibla,
night behind the tasbih and Isha behind spiritual health) so the set shows the day's colours; on a watch
the sky follows the time of day. The spiritual health screen shows a sample week (`-demoMarks`).

### Mac: `APP_DESKTOP`

| # | File | Title | What it shows |
|---|---|---|---|
| 1 | `01-prayer.png` | Prayer times | Home: the next prayer, its time and the time left, the Qibla arc and today's five prayers on the sky for the current part of the day. |
| 2 | `02-mosques-map.png` | Nearest mosques on the map | Mosques around the person on the map, with the list of the nearest ones and their distance. |
| 3 | `03-ask.png` | Ask with cited sources | Ask (opened from the Ask AI button): the answer to the common question on the Quran as a miracle, from the app's offline library, with its sources. |
| 4 | `04-classics.png` | Classical books | The library's classical books (Arabic, from OpenITI): grouped by subject, each downloadable once and read offline. |
| 5 | `05-widgets.png` | Home Screen widgets | The Today widget (all prayers of the day) in its sizes, in the app's style. |
| 6 | `06-audio.png` | Adhan and reminders | Prayer & Audio settings: the Adhan voice for each prayer, reminders before the prayer and at sunrise. |
| 7 | `07-qibla.png` | Qibla compass | The Qibla compass: the Kaaba at the Qibla bearing with the bearing and the distance to Makkah. On a phone with a compass the dial turns with the phone; without one (simulator, Mac) it is drawn from north and says so. |

### Apple TV: `APP_APPLE_TV`

| # | File | Title | What it shows |
|---|---|---|---|
| 1 | `01-tv-board.png` | Prayer board | The prayer board for the living room or the mosque hall: today's times and the time left to the next prayer. |
| 2 | `02-tv-qibla.png` | Qibla | The Qibla direction and distance for the chosen city. |
| 3 | `03-tv-mosques.png` | Nearby mosques | Mosques near the chosen city. |
| 4 | `04-tv-alert.png` | Prayer alert | The full-screen alert when a prayer time comes. |
| 5 | `05-tv-top-shelf.png` | Top Shelf | The Top Shelf on the Apple TV Home Screen: the next prayer without opening the app. |
| 6 | `06-tv-settings.png` | Settings | City, calculation method and Adhan settings. |

### Apple Vision Pro: `APP_APPLE_VISION_PRO`

| # | File | Title | What it shows |
|---|---|---|---|
| 1 | `01-prayer.png` | Prayer times | Home: the next prayer, its time and the time left, the Qibla arc and today's five prayers on the sky for the current part of the day. |
| 2 | `02-mosques-map.png` | Nearest mosques on the map | Mosques around the person on the map, with the list of the nearest ones and their distance. |
| 3 | `03-ask.png` | Ask with cited sources | Ask (opened from the Ask AI button): the answer to the common question on the Quran as a miracle, from the app's offline library, with its sources. |
| 4 | `04-classics.png` | Classical books | The library's classical books (Arabic, from OpenITI): grouped by subject, each downloadable once and read offline. |
| 5 | `05-audio.png` | Adhan and reminders | Prayer & Audio settings: the Adhan voice for each prayer, reminders before the prayer and at sunrise. |
| 6 | `06-onboarding.png` | Welcome | The first page of the welcome tour. |

Cities: prayer, Qibla and settings screens use Riyadh. Mosque screens use Cairo, because the offline
mosque data that comes with the app covers Cairo, London and Cape Town. The status bar reads 9:41 with
full battery. The prayer times shown are those computed for the moment of capture.

The common questions, and so the Ask answer, exist in all 22 store languages. In the screenshot set made
before they were translated (October 2026), the Ask screenshot of the 15 languages other than ar, en, es, fr,
id, tr and ur shows the question list instead of an answer; its `shows` field says so. A new run fixes that.

### iPhone Duo: outer display (`duo-outer`), inner display pending

App Store Connect has an iPhone Duo slot: outer display 1398 × 2034, inner display 2007 × 2853 (and the
same turned to landscape). Apple requires these from April 2027 for apps built with the iOS 27.1 SDK or
later. The captures come from the iPhone Duo simulator in Xcode 27.1 on GitHub's `xcode-27` runner
(iOS 27.1 simulator runtime), built with that SDK: the app fills the display edge to edge and the tab bar
stands vertically along the side, as on the device.

* `duo-outer`: display 1 of the simulator (it starts folded), the iPhone screens in this order: prayer,
  qibla, mosques-map, ask, classics, widgets, audio, mosques-radar, prayer-dark. Folder `IPHONE_DUO_OUTER`.
* `duo-inner`: not captured. Unfolding in the simulator (the outer display powered off with
  `simctl io … screenConfig --display=1 power off`) froze the runner both times it was tried (October 2026).
  Try again with a later Xcode 27.1 or 27.2 image.
* Upload by hand: App Store Connect's API has no iPhone Duo screenshot type yet, so the upload workflow
  cannot fill the slot. Drag a locale's `IPHONE_DUO_OUTER` files onto the iPhone Duo box. Once one set
  exists, its `screenshotDisplayType` can be read from the API and the upload workflow pointed at it.

Do not fill the slot with resized iPhone screenshots: the proportions and the side bars differ.

## Languages

The app has 36 interface languages; 22 of them are App Store languages. Each app language fills its
App Store locale and that locale's variants with the same files:

| App language | App Store locale | Same files also for |
|---|---|---|
| `ar` | `ar-SA` | none |
| `en` | `en-US` | `en-GB`, `en-AU`, `en-CA` |
| `fr` | `fr-FR` | `fr-CA` |
| `es` | `es-ES` | `es-MX` |
| `pt` | `pt-BR` | `pt-PT` |
| `tr` | `tr` | none |
| `id` | `id` | none |
| `ur` | `ur-PK` | none |
| `ru` | `ru` | none |
| `nl` | `nl-NL` | none |
| `hu` | `hu` | none |
| `zh` | `zh-Hans` | none |
| `hi` | `hi` | none |
| `bn` | `bn-BD` | none |
| `gu` | `gu-IN` | none |
| `kn` | `kn-IN` | none |
| `ml` | `ml-IN` | none |
| `mr` | `mr-IN` | none |
| `pa` | `pa-IN` | none |
| `te` | `te-IN` | none |
| `th` | `th` | none |
| `vi` | `vi` | none |

App languages with no App Store localization (fa, prs, ha, sw, tl, km, ug, ckb, bs, sr, mk, ka, si, as)
get no screenshots; people in those regions see the primary language's listing.

## Uploading (App Store Connect API)

The steps for each `ascPlatform`, locale and `displayType`. `tools/asc_testflight.py` shows the
authentication (an API key in the repository secrets `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_PRIVATE_KEY`).

1. Find the app: `GET /v1/apps?filter[bundleId]=sa.zood.nearmosque`.
2. Find the editable version of the platform: `GET /v1/apps/{id}/appStoreVersions?filter[platform]=IOS`
   (or `MAC_OS`, `TV_OS`, `VISION_OS`) in state `PREPARE_FOR_SUBMISSION`.
3. Its localizations: `GET /v1/appStoreVersions/{id}/appStoreVersionLocalizations`. Create a missing
   locale with `POST /v1/appStoreVersionLocalizations` (it also needs the locale's description).
4. The set: `GET /v1/appStoreVersionLocalizations/{id}/appScreenshotSets`; create one with
   `POST /v1/appScreenshotSets` (`screenshotDisplayType` = the entry's `displayType`).
5. Each file, in `position` order: `POST /v1/appScreenshots` with `fileName` and `fileSize` (`bytes`),
   send the bytes as the returned `uploadOperations` say, then `PATCH /v1/appScreenshots/{id}` with
   `uploaded: true` and `sourceFileChecksum` = `md5`.
6. Wait until each screenshot's `assetDeliveryState` is `COMPLETE`, then set the order with
   `PATCH /v1/appScreenshotSets/{id}/relationships/appScreenshots`.

The listing already has screenshots (about 1,198 assets). Before deleting any existing screenshot,
list what a set holds and get the owner's confirmation. A deleted screenshot cannot be brought back.

The workflow **App Store screenshots upload** (`.github/workflows/store-upload.yml`, `tools/store/asc_upload.py`)
does these steps for the sets named in `display_types` (e.g. `APP_WATCH_ULTRA`), from the
`app-store-screenshots` artifact of a screenshots run (`run_id`). It only fills empty sets: a set that
already holds screenshots is reported and left as it is, so it never replaces or deletes anything. Start it
with `dry_run` on to see what it would do, then again with it off.

By hand: App Store Connect → the app → the platform's version → **Previews and Screenshots** → pick
the language at the top right → drag a locale folder's files onto the matching device box in order.

## Creative assets (iOS 27): product page header and search results

Apple's iOS 27 App Store adds **creative assets**: an image or a looping video at the top of the product page
(the header) and on the app's card in search results. They show the app's brand and mood rather than its
screens, are muted by default and loop. The **App Store creative assets** workflow
(`.github/workflows/store-creative.yml`, started by hand) builds both in the 22 store languages; the artifact
`app-store-creative` holds `header/<locale>.mp4`, `search/<locale>.mp4` and a README.

| Asset | Apple's spec | Ours |
|-------|--------------|------|
| Product page header | 21:9, 3840 × 1646, 5–30 s, 30 or 60 fps, .mov/.m4v/.mp4 | 20 s loop, 30 fps, H.264 |
| Search results | 3:2, 1920 × 1280 to 3840 × 2560, 5–30 s | 2880 × 1920, 20 s loop, 30 fps, H.264 |

**What they show.** One day of prayer in 20 seconds, Fajr to Fajr, so the loop has no seam: the app's living
sky (`tools/store/creative/sky.js`, the colours of `ios/App/Support/Sky.swift`), the sun on a gold path with
the prayer marks lighting as it passes, the moon and stars after Maghrib, the mosque and its reflection, and
one short phrase per language (`shared/store/creative.json`; English and Arabic final, the rest drafts). The
search video adds the phone with the app's prayer screen, which turns to dark mode at night; for Arabic and Urdu
it is mirrored so the sun travels in the reading direction. The sound (`ambience.py`) is synthesized: wind,
birds by day, crickets at night and a soft chime at each prayer mark, with no music.

**Where they go.** App Store Connect: the version, **Product Page Information → Header and Search Results**,
then **Header** or **Search Results**; or **Asset Library → Upload Creative Assets** to submit them on their own.
They are reviewed like other metadata.

## App previews (videos)

The **App Store previews** workflow (`.github/workflows/store-previews.yml`, started by hand) records two
videos of the iPhone app in each store language, over 4 machines. The Debug build plays a scripted tour
(`-demoPreview`, `ios/App/Support/DemoPreview.swift`) and `tools/store/record.py` records the simulator
and converts the cut to Apple's preview format: 886 × 1920, H.264 High at 30 fps (about 11 Mbps), a silent
stereo AAC track, 20 seconds (Apple accepts 15 to 30). The clips are listed under `previews` in
`tools/store/screens.json`.

| # | Clip | Shows |
|---|------|-------|
| 1 | `header` | The prayer header on the sky of the hour, scrolled into the slim bar and back, then the Qibla view, turning until it lights up on the Qibla. |
| 2 | `search` | Ask: a question typed and sent, the thinking steps, the answer (labelled awaiting scholar review) and its sources. |

The run's artifact `app-store-previews` holds `<app language>/<NN>-<clip>.mp4`. Upload them in App Store
Connect under each language's **App Previews** for the iPhone 6.9" display; Apple's preview specification
lists the same 886 × 1920 size for iPhone Duo, so the same files can go there. The poster frame defaults
to 5 seconds and can be changed in App Store Connect. The upload tool (`asc_upload.py`) does not upload
previews; they are added by hand.

## The listing text

`shared/store/listing.json` holds the text for every App Store locale and every App Store version
(`IOS`, `MAC_OS`, `VISION_OS`, `TV_OS`): `subtitle`, `promotionalText`, `keywords`, `description` and
`whatsNew`. Each device gets its own description, because they differ: the Mac and Apple Vision Pro have
no compass, Dynamic Island or Lock Screen, and the Apple TV app is a prayer board without Ask, library or
widgets. It is built by `tools/store/listing.py` from one file per language in `shared/store/listing/`;
edit those, never the output. CI fails when a field is over its limit.

In App Store Connect (API: `appStoreVersionLocalizations`, fields `description`, `keywords`,
`promotionalText`, `whatsNew`; the subtitle is on `appInfoLocalizations`), each locale's text goes into the
same locale as its screenshots. The app's name is not in the file: keep the name the listing already has.
English and Arabic are final; the other languages are drafts until a native speaker has read them.

## What has no screenshot slot

* **App icons** come from the build, not from the listing. `icons/` holds them for reference (website,
  press): iPhone/iPad/Mac 1024 px (light, dark, tinted), the Apple TV App Store icon (1280 × 768, its
  two layers merged) and Top Shelf images, and the Apple Vision Pro icon with its three layers.
* **Widgets, Live Activities, Siri shortcuts** have no fields of their own in App Store Connect; they
  appear in screenshots and in the description. See `10-features.md` for the full list.

## Rules for the listing text

* Describe only what the build does. Do not claim offline completeness, store readiness or answer
  accuracy without evidence: the Ask answers come from the app's library with their sources, and
  translations of the interface are drafts awaiting native review (`05-status.md`).
* The five Quran questions are marked "awaiting scholar review" in the app; the listing should not
  present them as reviewed.
* Limits: name 30 characters, subtitle 30, promotional text 170, keywords 100 (comma-separated),
  description 4,000.
