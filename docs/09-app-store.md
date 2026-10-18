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

To redo a few captures, run it with **only** (e.g. `ar/01-prayer en/02-prayer-weather`, numbered in the current order
of `tools/store/screens.json`) and **from_run** (the IDs of the runs that have the rest, older first). To redo
only the sorting after a change to `tools/store/screens.json`, run it with **from_run** and **capture** off;
nothing is captured again.

From each **from_run** the workflow reads the finished set (the `app-store-screenshots` artifact, kept 30 days)
and the raw captures (kept 3 days), then this run's own captures (`tools/store/collect.py`). Captures are
matched by screen name, not by number: a later capture of a screen replaces an earlier one whatever its number,
and a run made in an older order sorts into the new one (`finish.py` numbers each file by its position now).
A run that sorted only (capture off) holds whatever it sorted, which may be a few files: check its manifest's
`missing` before using it as a source. `python3 tools/store/collect.py --check` (CI) checks `screens.json` and
this merge.

A capture that fails is never filled in from an older run in silence. `tools/store/shoot.py` tries each simulator
screenshot up to 3 times (60 seconds each, a short pause before a retry, the app launched afresh and waited for
before the last one; the iPad and Apple Vision Pro simulators sometimes hang on animated screens such as the
onboarding promise and the weather sky). Each shard first marks every screen it is to capture as
`<NN>-<screen>.failed` in its raw captures (before Xcode, the runtime download and the build), and each capture,
Mac ones included, replaces its marker; a capture is written under a partial name and renamed only once complete.
So a screen with no capture (every attempt failed, the launch or the build failed, or the shard stopped part way)
keeps its marker, with the reason. A planned shard that uploaded nothing at all (a cancelled job) is marked by the
sorting job, with an error in the log. The merge then drops any earlier capture of that screen for that device and
language, so the screen is listed under `missing` (and under `failed`, with the reason) instead of mixing an old
design into the new set; a later capture of it replaces the marker. The finished set keeps `failed`, so a later run
that reads it after the raw captures have expired still does not use the older capture. An Apple Vision Pro capture
left out as still loading is listed under `failed` as well as `skipped`, for the same reason. To redo such screens, run with **only**
and **from_run**. To use an older capture after all, give the run that has it after the failed one in **from_run**
(with **capture** off, as this run's own captures always come last).

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
set is one shorter and the positions close up. `missing` lists every screen with no capture (`platform/language/NN-screen.png`);
`failed` lists every screen with no usable capture and why (`platform`, `appLanguage`, `screen`, `position`, `reason`):
a capture that failed or was never reached, in this run or an earlier one it merged, an unreadable file, or an Apple
Vision Pro capture still loading. Do not upload a set for a locale that has missing or skipped files without telling
the owner.

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
| 2 | `02-prayer-weather.png` | Sky follows the weather | The prayer screen with Sky follows the weather turned on: rain on the sky with the temperature and the wind (a fixed demo forecast; in the app the weather comes from MET Norway). |
| 3 | `03-qibla.png` | Qibla compass | The Qibla compass: the Kaaba at the Qibla bearing with the bearing and the distance to Makkah. On a phone with a compass the dial turns with the phone; without one (simulator, Mac) it is drawn from north and says so. |
| 4 | `04-mosques-map.png` | Nearest mosques on the map | Mosques around the person on the map, with the list of the nearest ones and their distance. |
| 5 | `05-ask.png` | Ask with cited sources | Ask (opened from the Ask AI button): the answer to the common question on the Quran as a miracle, from the app's offline library, with its sources. |
| 6 | `06-classics.png` | Classical books | The library's classical books (Arabic, from OpenITI): grouped by subject, each downloadable once and read offline. |
| 7 | `07-widgets.png` | Home Screen widgets | The Today widget (all prayers of the day) in its sizes, in the app's style. |
| 8 | `08-island.png` | Dynamic Island and Lock Screen | The next-prayer Live Activity in the Dynamic Island and on the Lock Screen. |
| 9 | `09-audio.png` | Adhan and reminders | Prayer & Audio settings: the Adhan voice for each prayer, reminders before the prayer and at sunrise. |
| 10 | `10-mosques-radar.png` | Mosque compass | The mosque compass: the nearest mosques placed around the person by direction and distance. |

### iPad 13" display: `APP_IPAD_PRO_3GEN_129`

| # | File | Title | What it shows |
|---|---|---|---|
| 1 | `01-prayer.png` | Prayer times | Home: the next prayer, its time and the time left, the Qibla arc and today's five prayers on the sky for the current part of the day. |
| 2 | `02-prayer-weather.png` | Sky follows the weather | The prayer screen with Sky follows the weather turned on: rain on the sky with the temperature and the wind (a fixed demo forecast; in the app the weather comes from MET Norway). |
| 3 | `03-qibla.png` | Qibla compass | The Qibla compass: the Kaaba at the Qibla bearing with the bearing and the distance to Makkah. On a phone with a compass the dial turns with the phone; without one (simulator, Mac) it is drawn from north and says so. |
| 4 | `04-mosques-map.png` | Nearest mosques on the map | Mosques around the person on the map, with the list of the nearest ones and their distance. |
| 5 | `05-ask.png` | Ask with cited sources | Ask (opened from the Ask AI button): the answer to the common question on the Quran as a miracle, from the app's offline library, with its sources. |
| 6 | `06-classics.png` | Classical books | The library's classical books (Arabic, from OpenITI): grouped by subject, each downloadable once and read offline. |
| 7 | `07-widgets.png` | Home Screen widgets | The Today widget (all prayers of the day) in its sizes, in the app's style. |
| 8 | `08-audio.png` | Adhan and reminders | Prayer & Audio settings: the Adhan voice for each prayer, reminders before the prayer and at sunrise. |
| 9 | `09-widgets-qibla.png` | Qibla and ring widgets | The Qibla widget and the prayer ring widget in the teal style. |
| 10 | `10-onboarding.png` | Welcome | The first page of the welcome tour. |

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
| 2 | `02-prayer-weather.png` | Sky follows the weather | The prayer screen with Sky follows the weather turned on: rain on the sky with the temperature and the wind (a fixed demo forecast; in the app the weather comes from MET Norway). |
| 3 | `03-mosques-map.png` | Nearest mosques on the map | Mosques around the person on the map, with the list of the nearest ones and their distance. |
| 4 | `04-ask.png` | Ask with cited sources | Ask (opened from the Ask AI button): the answer to the common question on the Quran as a miracle, from the app's offline library, with its sources. |
| 5 | `05-classics.png` | Classical books | The library's classical books (Arabic, from OpenITI): grouped by subject, each downloadable once and read offline. |
| 6 | `06-widgets.png` | Home Screen widgets | The Today widget (all prayers of the day) in its sizes, in the app's style. |
| 7 | `07-audio.png` | Adhan and reminders | Prayer & Audio settings: the Adhan voice for each prayer, reminders before the prayer and at sunrise. |
| 8 | `08-qibla.png` | Qibla compass | The Qibla compass: the Kaaba at the Qibla bearing with the bearing and the distance to Makkah. On a phone with a compass the dial turns with the phone; without one (simulator, Mac) it is drawn from north and says so. |

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
| 2 | `02-prayer-weather.png` | Sky follows the weather | The prayer screen with Sky follows the weather turned on: rain on the sky with the temperature and the wind (a fixed demo forecast; in the app the weather comes from MET Norway). |
| 3 | `03-mosques-map.png` | Nearest mosques on the map | Mosques around the person on the map, with the list of the nearest ones and their distance. |
| 4 | `04-ask.png` | Ask with cited sources | Ask (opened from the Ask AI button): the answer to the common question on the Quran as a miracle, from the app's offline library, with its sources. |
| 5 | `05-classics.png` | Classical books | The library's classical books (Arabic, from OpenITI): grouped by subject, each downloadable once and read offline. |
| 6 | `06-audio.png` | Adhan and reminders | Prayer & Audio settings: the Adhan voice for each prayer, reminders before the prayer and at sunrise. |
| 7 | `07-onboarding.png` | Welcome | The first page of the welcome tour. |

**Sky follows the weather** (`prayer-weather`, iPhone, iPad, Mac and Apple Vision Pro; the feature is not on
the Apple TV or the Apple Watch): the prayer screen with `-demoWeather rain`, which draws a fixed rain forecast
(18 °C, wind 4 m/s) in the Debug build and sends nothing to MET Norway, and `-appearance dark` (the app's own
Settings → Appearance key), so the Mac capture is dark too; the simulators are also set to dark. In the app the
switch is off by default; when it is on, the prayer city's coordinates rounded to about 1 km go to MET Norway
while the app is open. The main app has no demo clock, so the sky is the one of the moment of capture in
Riyadh. Start the run that captures it at about 12:00 UTC, so the captures fall in Riyadh's Asr (from late
October to mid November about 14:50 to 17:10 Riyadh time, 11:50 to 14:10 UTC): the rain and the grey cloud deck
then lie on the teal Asr sky with a little gold left on the horizon, at the same time of day as the Google Play
weather screenshot (16:20 Riyadh). Outside that window the shot shows the sky of its own hour, dimmed under the
rain the same way.

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
  prayer-weather, qibla, mosques-map, ask, classics, widgets, audio, mosques-radar. Folder `IPHONE_DUO_OUTER`.
  The captures made so far (October 2026, 13 languages, before the weather screen) sort into this order; the
  weather screen needs a new Duo run.
* `duo-inner`: the iPad screens in the iPad order (prayer, prayer-weather, qibla, mosques-map, ask, classics,
  widgets, audio, widgets-qibla, onboarding), not captured. Unfolding in the simulator (the outer display
  powered off with `simctl io … screenConfig --display=1 power off`) froze the runner both times it was tried
  (October 2026).
  Try again with a later Xcode 27.1 or 27.2 image.
* Upload by hand: App Store Connect's API has no iPhone Duo screenshot type yet, so the upload workflow
  cannot fill the slot. Drag a locale's `IPHONE_DUO_OUTER` files onto the iPhone Duo box. Once one set
  exists, its `screenshotDisplayType` can be read from the API and the upload workflow pointed at it.

Do not fill the slot with resized iPhone screenshots: the proportions and the side bars differ.

* `shared/store/duo-poses/<locale>/00-duo-poses.png` (2034 × 1398): a marketing image, the app's screens set
  into the three poses of an iPhone Duo picture (on the table: the Apple TV prayer board; open: the outer
  display's prayer and Qibla captures; half folded: the TV Qibla screen and a drawn Adhan strip). It is not a
  capture. Number 00 puts it first; the "App Store asset library upload" workflow adds it with mode `fill`,
  folder `IPHONE_DUO_POSES`, `repo_folder` `shared/store/duo-poses` and group `IPHONE_DUO_PROFILE`, where a
  locale's set has room (at most 10).

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
`app-store-screenshots` artifact of a screenshots run (`run_id`). Start every change with `dry_run` on (the
default) to see what it would do, then again with it off. What it does depends on the inputs:

| Inputs | What it does |
|---|---|
| `display_types` only (the default) | Fills empty sets. A set that already holds screenshots is reported and left as it is, so it never replaces or deletes anything. |
| `screens` (e.g. `prayer-weather`) | Adds the files of those screens to sets that already hold screenshots, where that screen is not there yet, then orders the set as the manifest does. Screenshots the manifest does not know (a screen that left the order, or a file name it does not recognise) keep their order after the others. A second run adds nothing. A set that would go over 10 is skipped and reported; a missing or empty set is left to the default mode. Only a screenshot App Store Connect finished processing counts as there: a new one that fails processing or cannot be uploaded is deleted again at once (it is the run's own upload), and one of those screens already in a set in any other state stops that set with an error saying to remove it with `remove_screens` and `confirm_delete` first. An upload error stops the run cleanly. |
| `remove_screens` (e.g. `prayer-dark`) and `confirm_delete` | Deletes the screenshots of those screens in the sets being processed. With `confirm_delete` off (the default) it only lists what it would delete and deletes nothing. Turn it on only after the owner has confirmed that list. On its own it checks no manifest: it deletes in every set processed, in every locale. Together with `screens` or `replace_screens`, it deletes in a set only when the manifest has every one of those screens for that `displayType` and locale and the insert is not skipped (a set that would go over 10, or that holds one of those screens unfinished, is left whole). A set the manifest lacks one of them for (for example a locale whose weather capture is missing) is reported and left exactly as it is: nothing deleted, added, replaced or reordered. The manifest check does not guarantee the new screenshot reaches App Store Connect, so the order matters: a set with room takes the new screenshots first and deletes only once every one of them is ready; if one fails, nothing is deleted there. A full set (10) has to delete first to make room, so if the new screenshot then fails processing or cannot be uploaded, the deleted one is not brought back: the run reports it (an upload error also stops the run), and the set holds one fewer until a later run uploads the new screen. The dry run and the report with `confirm_delete` off say which sets delete first, also when the new screen is a `replace_screens` addition. |
| `replace_screens` (e.g. `onboarding`) and `confirm_delete` | For a screen whose picture changed. With `confirm_delete` off (the default) it changes nothing and lists what it would replace or add. In each set, for each named screen the manifest has a file for (that `displayType` and locale; a set it has none for is left as it is for that screen, with a warning when the manifest lists that screen under `failed`, `missing` or `skipped` for that device and language, and a plain note when it is not a screen of that display type, such as `onboarding` on the iPhone): where the set's screenshot of that screen has a `sourceFileChecksum` other than the manifest's `md5`, the new file is uploaded and the old screenshot is deleted only once the new one is ready (`COMPLETE`); if the new one fails processing, cannot be uploaded or is not ready yet, the old one is kept (a failed new one, the run's own upload, is deleted again), the set is not reordered and nothing else changes in it. That needs room for one more screenshot, so a set that already holds 10 is reported and left with nothing deleted, unless `delete_first_in_full_sets` is on: then, in such a set only, the old screenshot is deleted first and the new file uploaded, and if the new one fails processing or cannot be uploaded the old one is not brought back (the run reports it as failed and a later run adds the new one). Where the checksum is the same and the screenshot is ready, nothing happens (an older file of that screen next to it, left by a replace whose new file was still processing, is deleted). Where the checksum is the same but App Store Connect failed it (a replace whose new file was still processing when its run ended), that unusable screenshot is deleted first and does not count toward the room; the replace then goes on. Where the set has no screenshot of that screen, it is added as `screens` adds it. Replacements go first, one screen at a time, then `screens` and `remove_screens`, then the set is ordered as the manifest does. It can be combined with `screens` and `remove_screens`, but each screen goes in only one of the three. |
| `replace_sets` and `confirm_delete` | Replaces whole sets, for sets uploaded by hand under names no screen name matches (`ipad_01.jpg`, `mac_01.jpg`, `vision_01.jpg`). With `confirm_delete` off (the default) it changes nothing and lists, per set, what it would delete and upload. For each set in `display_types`, in each locale of the platform's editable version that the manifest has at least one file for (a locale with `sameFileAs` has its own entries, with the files of its source locale): a set that already holds exactly the manifest's files in `position` order (as many, each `sourceFileChecksum` equal to the `md5` in any case, all processed) is reported as "already up to date" and left; the same files in another order are only ordered; a set that would end shorter, because the manifest lacks a screen of the display type for that locale (as `missing`, `failed` or `skipped` say) or has fewer files for it than the set holds now, is left as it is (nothing deleted or uploaded) and reported as an error naming the missing screens, and the run fails at the end, unless `allow_short_sets` is on (then a warning, and it is replaced); otherwise every screenshot in the set is deleted, recognised or not, and the manifest's files are uploaded in `position` order (each committed with its `md5`), then, once all are processed, the set is ordered. If a delete or an upload fails, a file fails processing (it is deleted again: the run's own upload) or a file is still processing when the wait ends, that set is reported: it may now be short, and a re-run with `replace_sets` replaces it again, because it no longer holds what the manifest says (files that were only still processing count as up to date once they are ready). The other sets go on and the run fails at the end, but two such sets with no fully replaced set between them stop the run, and a set that ends with none of the manifest's files in it (every upload failed, for example files App Store Connect does not process for that display type) stops it at once, so a broken upload never empties every set. A dry run cannot catch a processing failure. A manifest set of more than 10 files is reported and left. A locale or a set missing in App Store Connect is skipped and reported (it does not create them; the default mode fills a missing set). It cannot be combined with `screens`, `remove_screens` or `replace_screens`. A deleted screenshot cannot be brought back. |
| `text` and `platforms` | Updates `description`, `keywords` and `promotionalText` of every locale of each named version (`IOS`, `MAC_OS`, `VISION_OS`, `TV_OS`) from `shared/store/listing.json`, and `whatsNew` unless the version is the app's first on that platform (if App Store Connect refuses it, it is skipped and reported). It never changes the name or the subtitle (the subtitle is on `appInfoLocalizations`: change it by hand), never blanks a field the file does not have, sends only the fields that differ, and skips and reports locales App Store Connect does not have (it does not create them). With `TV_OS` in `platforms` it also updates the **Privacy Policy Text** of the app information (App Information → Privacy Policy Text, shown on Apple TV; API `appInfoLocalizations`, `privacyPolicyText`) from each locale's `APP_INFO`, only where it differs and only on an editable app information (`GET /v1/apps/{id}/appInfos`, state Prepare for Submission or rejected, the same states as the versions). If none is editable it reports "app information is in review or live: privacy text not changed" and goes on. It never touches the name, the subtitle or the privacy policy URL, and a dry run shows the locales and lengths, not the text. For text only, leave `display_types` empty; `run_id` is then not needed. |

A screenshot already in App Store Connect is recognised by its file name, `<anything>-<screen>.png` (the
workflow uploads files named `NN-<screen>.png`; the number does not matter). The longest screen name wins, so
`09-widgets-qibla.png` is `widgets-qibla`, never `qibla`, and a file it does not recognise is never deleted, except
by `replace_sets`, which replaces the whole set. In these modes a platform with no editable version is reported ("the version is in review or live: nothing
changed") and the other platforms go on; the default mode stops there, as before. A dry run lists the file
names each set holds; the logs show only locales, counts and those file names. `python3
tools/store/tests/test_asc_upload.py` tests the modes offline against a fake App Store Connect.

**Adding the weather screen (`prayer-weather`).** After a screenshots run that has it:

1. Dry run with `display_types` `APP_IPHONE_67 APP_IPAD_PRO_3GEN_129 APP_DESKTOP APP_APPLE_VISION_PRO`,
   `screens` `prayer-weather`, `remove_screens` `prayer-dark` and `confirm_delete` off. It shows what each set
   holds and what it would do there.
2. Sets with room take the weather screen as number 2: run again with `dry_run` off and `remove_screens`
   empty. The previous order had 9 iPad, 7 Mac and 6 Apple Vision Pro screens; the dry run shows what each set
   really holds.
3. A set that holds 10 is skipped. The previous iPhone order had 10, the last one `prayer-dark`, which left the
   iPhone order to make room for the weather screen. Deleting it needs the owner's yes; with it, run with
   `remove_screens` `prayer-dark` and `confirm_delete` on (dry run first). These sets are full, so `prayer-dark`
   is deleted before the weather screen is uploaded: if that upload fails, `prayer-dark` is not brought back and
   the set holds 9 until a run uploads the weather screen.
4. The text, once `shared/store/listing.json` has it: `text` on, `display_types` empty, `platforms`
   `IOS MAC_OS VISION_OS` (the weather is not on the Apple TV or the Apple Watch), dry run first.
5. The Apple TV Privacy Policy Text, which still said the app goes online only for mosque search, maps,
   downloads and links: `text` on, `platforms` `TV_OS`, dry run first. It updates the Privacy Policy Text
   (it now names the optional weather on the other Apple devices and says the Apple TV app does not use it) and
   the Apple TV version's text from `listing.json`; the Apple TV description keeps its privacy paragraph without
   the weather, because the TV app has none. The app information must be editable (a new version in Prepare for
   Submission on any platform); otherwise the run reports it and changes nothing there.

**Replacing the onboarding screen (`onboarding`).** The iPad and Apple Vision Pro sets hold the old welcome tour
(`09-onboarding.png` on iPad, `06-onboarding.png` on Apple Vision Pro). After a screenshots run with the new
onboarding in every language (check that its manifest's `missing` and `failed` have no `onboarding`; a locale that
lacks it is left as it is):

1. Dry run with `display_types` `APP_IPAD_PRO_3GEN_129 APP_APPLE_VISION_PRO`, `replace_screens` `onboarding` and
   `confirm_delete` off. It lists each set and which onboarding screenshot it would replace, add or leave.
2. With the owner's yes, run with `confirm_delete` on (dry run first, then `dry_run` off). In each set the new
   file is uploaded, the old one deleted once the new one is ready, and the set ordered. A second run changes
   nothing.
3. A set that holds 10 (the iPad sets, once the weather screen is in) has no room to take the new file before the
   old one goes, so it is reported and left. Replacing it there needs the owner's yes for a gap: the same run with
   `delete_first_in_full_sets` on as well (dry run first; with `confirm_delete` off the report says which sets
   delete first). In each full set where the manifest has the new onboarding, the old one is deleted, then the
   new file uploaded; if that upload fails, the old screenshot is not brought back and the set holds 9 until a run
   adds it (the same inputs again). A full set whose locale lacks the new onboarding is reported and left with its
   old one. Do not use `remove_screens` `onboarding` on its own for this: it deletes in every locale of the sets
   processed, whatever the manifest holds, so a locale whose new capture failed would end with no onboarding. On an
   iPad set of 9 that still lacks the weather screen, one run with `screens` `prayer-weather` and
   `replace_screens` `onboarding` does both, the replace first.

**Replacing sets uploaded by hand (`replace_sets`).** Sets uploaded in the browser keep the file names they had
(`ipad_01.jpg` … `ipad_09.jpg`, `mac_01.jpg` …, `vision_01.jpg` …), so `screens` and `replace_screens` cannot tell
which screen each one is. To replace them whole with the manifest's complete sets, once the owner has agreed to it:

1. Check the screenshots run's manifest: `missing`, `failed` and `skipped` should be empty for those devices. A set
   that would end shorter (a locale that lacks a screen, or a set that holds more than the manifest has for it) is
   left as it is and reported as an error, so redo such captures first. Only if the owner agrees to shorter sets,
   turn on `allow_short_sets`: those sets are then replaced with what the manifest has.
2. Dry run with `display_types` of one device (e.g. `APP_IPAD_PRO_3GEN_129`), `replace_sets` on and `confirm_delete`
   off. It lists, per locale, the file names it would delete and the files it would upload, and which sets are
   already up to date, missing in App Store Connect, without manifest files or left because they would end shorter
   (an error, so that dry run fails).
3. With the owner's yes, the same with `confirm_delete` on (dry run first, then `dry_run` off). Each set is emptied
   and then filled, so it is short for the minutes the uploads take; if an upload fails, the set stays short until a
   re-run with the same inputs replaces it again. Two sets that end short (an upload error or a file that fails or is
   still processing) with no fully replaced set between them stop the run, and so does one set left with none of its
   files. Run one device per run: each set takes a few minutes and the job stops after 120 minutes, and a run
   stopped in the middle of a set leaves that set short until a re-run.
4. Run the same inputs again: every set reports "already up to date" and nothing changes. Then the next device
   (`APP_DESKTOP`, `APP_APPLE_VISION_PRO`).

The iPhone Duo slot has no screenshot type in the API yet (see above), so its files are still added by hand.

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
widgets. Next to the platforms, each locale has `APP_INFO` with the app-level `privacyPolicyText` (App
Information → Privacy Policy Text, shown on Apple TV), from `privacy_policy_text` in the language file; it links
the full policy (`https://nearmosque.net/privacy.html`). It is built by `tools/store/listing.py` from one file
per language in `shared/store/listing/`; edit those, never the output. CI fails when a field is over its limit
(the Privacy Policy Text is held to 4,000 characters) or the privacy text does not link the policy.

In App Store Connect (API: `appStoreVersionLocalizations`, fields `description`, `keywords`,
`promotionalText`, `whatsNew`; the subtitle is on `appInfoLocalizations`), each locale's text goes into the
same locale as its screenshots. The app's name is not in the file: keep the name the listing already has.
English and Arabic are final; the other languages are drafts until a native speaker has read them.

## App Privacy (the privacy label)

Before "Sky follows the weather" the label was **Data Not Collected**: `ios/App/PrivacyInfo.xcprivacy` declared
no collected data types, and Apple Maps is Apple's own service. With the switch on (it is off by default), the
app sends the prayer city's coordinates rounded to about 1 km to MET Norway (with "Use my location", the nearest
bundled city within 30 km), a third party whose server logs may keep them longer than the request itself, and it
does so on its own about every half hour while the app is open. The exemption for optional data the user enters
each time does not fit. The conservative answer, recommended here, is an owner or legal decision:

* App Store Connect → App Privacy: **Location → Coarse Location**, not linked to the user, not used for
  tracking, purpose **App Functionality**. The label then reads "Data Not Linked to You: Location".
* The matching entry in `PrivacyInfo.xcprivacy` → `NSPrivacyCollectedDataTypes`:
  `NSPrivacyCollectedDataTypeCoarseLocation`, linked `false`, tracking `false`, purpose
  `NSPrivacyCollectedDataTypePurposeAppFunctionality`. `NSPrivacyTracking` stays `false` and
  `NSPrivacyTrackingDomains` empty. No new required-reason API: the weather cache is plain file I/O, and file
  timestamps are already declared (C617.1).
* The manifest entry is in place (so no build ships saying nothing is collected). Before the build that brings
  the feature is submitted, change the App Store Connect answer to match, and the Google Play Data safety form
  (`12-google-play.md`); `website/privacy.html` is already updated. If the owner decides against declaring it,
  remove the manifest entry in the same change.

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
