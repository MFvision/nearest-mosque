# 9. App Store screenshots and where each one goes

This is the guide for whoever uploads the App Store listing (a person or an agent). The screenshots
are real captures of the app's Debug builds, taken in Apple's simulators (iPhone, iPad, Apple TV, Apple
Vision Pro) and from the Mac app on a Mac runner. They are not mock-ups. A file named for a slot is
already at a size App Store Connect accepts for that slot.

## Getting the files

1. GitHub → Actions → **App Store screenshots** → **Run workflow**. Leave the inputs empty for every
   device in all 22 store languages, or name some (e.g. `iphone ipad`, `ar en`).
2. When it finishes, take the files from either place:
   * the repository's **Releases** page, release `store-screenshots-<commit>` (made by a full run: all
     devices, all languages; it does not expire): one zip per device type plus
     `manifest-sheets-icons.zip`. Unzip all of them into one folder. The workflow can make the release
     only when Settings → Actions → General → Workflow permissions is "Read and write permissions".
   * or the run's artifact **app-store-screenshots** (every run; kept 30 days).

   To redo only the sorting (after a change to `tools/store/screens.json`), run it with **from_run** set to
   the ID of the run that captured the pictures; nothing is captured again.
3. The layout is:

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

`missing` lists any capture that failed. Do not upload a set for a locale that has missing files
without telling the owner.

## The slots

App Store Connect needs one size per device family; it scales it down for smaller devices of the
same family (the 6.9-inch iPhone set covers 6.5-inch and smaller, the 13-inch iPad set covers smaller
iPads). At most 10 screenshots per set.

| Device | `displayType` | App Store version | Sizes the files have (pixels) |
|---|---|---|---|
| iPhone 6.9" | `APP_IPHONE_67` | iOS | 1320 × 2868 (portrait) |
| iPad 13" | `APP_IPAD_PRO_3GEN_129` | iOS | 2064 × 2752 (portrait) |
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

The Ask answer exists in the 7 languages that have the common questions (ar, en, es, fr, id, tr, ur).
In the other languages the Ask screenshot shows the Ask screen with its question list, and its `shows`
field says so.

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

By hand: App Store Connect → the app → the platform's version → **Previews and Screenshots** → pick
the language at the top right → drag a locale folder's files onto the matching device box in order.

## What has no screenshot slot

* **App icons** come from the build, not from the listing. `icons/` holds them for reference (website,
  press): iPhone/iPad/Mac 1024 px (light, dark, tinted), the Apple TV App Store icon (1280 × 768, its
  two layers merged) and Top Shelf images, and the Apple Vision Pro icon with its three layers.
* **Widgets, Live Activities, Siri shortcuts** have no fields of their own in App Store Connect; they
  appear in screenshots and in the description. See `10-features.md` for the full list.
* **App previews** (videos) are not made by this workflow.

## Rules for the listing text

* Describe only what the build does. Do not claim offline completeness, store readiness or answer
  accuracy without evidence: the Ask answers come from the app's library with their sources, and
  translations of the interface are drafts awaiting native review (`05-status.md`).
* The five Quran questions are marked "awaiting scholar review" in the app; the listing should not
  present them as reviewed.
* Limits: name 30 characters, subtitle 30, promotional text 170, keywords 100 (comma-separated),
  description 4,000.
