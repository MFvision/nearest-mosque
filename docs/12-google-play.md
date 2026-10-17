# 12. Google Play

The workflow `.github/workflows/android-release.yml` builds the minified Android release (App Bundle for Google
Play, APK for checks), signs it with the upload key when its secrets exist, keeps the bundle, the APK and the
R8 mapping as artifacts, and, when asked, puts the bundle on a Google Play track as a **draft** release. No key,
password or service account is stored in the repository; they live only in GitHub repository secrets.

There is no Google Play Console account yet. Until there is, the workflow still runs: it builds an unsigned
release and says, in a notice, which steps it skipped. Nothing here shows the app is ready for review; the
items under "Before the first review" are still open.

## One-time setup (account holder)

1. **Developer account (organization).** An organization account is recommended: it shows the
   organization as the developer and is the type Google expects for a company app. You need:
   * a **D-U-N-S number** for the organization (free from Dun & Bradstreet; it can take up to 30 days; the
     legal name and address must match the Google payments profile exactly),
   * the organization's website, phone and a contact email and phone (verified by one-time code),
   * the one-time registration fee (US$25 when this was written; Play Console shows the current amount),
   * identity and organization verification, which Google may take a few days to finish.

   Sign up at play.google.com/console → **Organization**. Developer name, email and phone you enter are shown
   on the Play store page.
2. **Create the app**: Play Console → **Create app** → app name, default language, **App**, **Free**, accept
   the declarations. The package name is fixed by the first upload: **`com.AVA.nearmosque`** (the code namespace stays `sa.zood.nearmosque`).
3. **Upload key** (on your own computer, once; keep the file and passwords in a password manager and a backup;
   never commit them):

   ```sh
   keytool -genkeypair -v -keystore upload-keystore.jks -storetype PKCS12 \
     -alias nearmosque-upload -keyalg RSA -keysize 4096 -validity 10000
   ```

   keytool asks for a password and a name for the certificate (any name; it is not shown to users). With
   PKCS12 the key password is the same as the store password.
4. **Play App Signing** is required for new apps: Google keeps the app signing key and you sign uploads
   with the upload key above. On the first upload, keep the default "Google-generated key". If the upload
   key is ever lost, the account owner can ask for an upload key reset in Play Console → Test and release →
   App integrity.
5. **GitHub secrets** (github.com/MFvision/nearest-mosque → Settings → Secrets and variables → Actions → New
   repository secret):

   | Secret | Value |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | `upload-keystore.jks`, base64-encoded (see below) |
   | `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
   | `ANDROID_KEY_ALIAS` | `nearmosque-upload` (the `-alias` above; GitHub hides every secret value in logs, so a common word like "upload" would be starred out everywhere) |
   | `ANDROID_KEY_PASSWORD` | The key password (the same as the keystore password for PKCS12) |
   | `PLAY_SERVICE_ACCOUNT_JSON` | The whole service account JSON key file (step 6) |

   To set the keystore without copying it through the clipboard, with the GitHub CLI signed in:

   ```sh
   base64 -i upload-keystore.jks | gh secret set ANDROID_KEYSTORE_BASE64 --repo MFvision/nearest-mosque   # macOS
   base64 -w0 upload-keystore.jks | gh secret set ANDROID_KEYSTORE_BASE64 --repo MFvision/nearest-mosque  # Linux
   gh secret set PLAY_SERVICE_ACCOUNT_JSON --repo MFvision/nearest-mosque < service-account.json
   ```

   `gh secret set NAME --repo MFvision/nearest-mosque` with no input asks for the passwords without echoing
   them. The four signing secrets go together: with only some of them set, the workflow fails and names the
   missing ones.
6. **Service account for the API** (only needed for uploads from the workflow):
   1. Google Cloud console → create a project (or use one) → APIs & Services → enable **Google Play Android
      Developer API**.
   2. IAM & Admin → Service accounts → Create (no Google Cloud roles needed) → Keys → Add key → JSON. If key
      creation is blocked, the Cloud organization has the policy `iam.disableServiceAccountKeyCreation`; an
      organization admin can allow it for this project.
   3. Play Console → **Users and permissions** → Invite new users → the service account's email → **App
      permissions** → add only this app, with **Release apps to testing tracks** (enough for internal and
      closed tracks). Add **Release to production, exclude devices, and use Play App Signing** only if the
      workflow should put drafts on the production track. No account permissions and no admin rights.
      (Older guides also link the Cloud project under Setup → API access; if Play Console still shows that
      page, link the project there too.) New permissions can take a day or more to start working.

## The first upload is by hand

For a new app the API cannot make the first release: Google's tools and the API documentation say the app
(its package name) must exist in Play Console with a build uploaded there first. So:

1. Run the workflow with **upload** off after the signing secrets are set.
2. Download the artifact `android-release-<versionCode>`, take `app-release.aab`.
3. Play Console → Test and release → Testing → **Internal testing** → Create new release → upload the
   bundle (this also sets up Play App Signing) → release notes → save and roll out to internal testers.

After that, runs with **upload** on put each new bundle on the chosen track as a draft: open Play Console,
check the draft and press Review release / Start rollout. While the app has never been published, Google
allows only draft releases through the API, which is what the workflow sends.

## Making a build

GitHub → Actions → **Android release (Google Play)** → Run workflow:

| Input | Meaning |
|---|---|
| `track` | `internal` (default), `closed` (Play's default closed track, `alpha`) or `production` |
| `upload` | Off: build and keep artifacts only. On: also upload the bundle as a draft (needs `PLAY_SERVICE_ACCOUNT_JSON` and the signing secrets; without them the step is skipped with a notice) |

The run validates the Gradle wrapper, runs the unit and Robolectric tests, builds with
`-PversionCode=1000 + run number` (each run is higher than the last; Google Play refuses a version code it
has seen), verifies the signatures quietly (the certificate's subject is never printed) and uploads the
artifacts (30 days). `versionName` is in `android/app/build.gradle.kts` (0.5.0). Locally,
`./gradlew :app:bundleRelease` builds an unsigned bundle with versionCode 1; the same environment
variables as CI (`ANDROID_KEYSTORE_FILE` = path to the .jks, plus the three passwords/alias) sign it.

The R8 mapping is inside the bundle, so Play Console shows readable crash stacks without a separate upload.
`mapping.txt` is also kept as an artifact for side-loaded APKs.

## Testing tracks

* **Internal testing**: up to 100 testers by email list; a release usually reaches them within minutes.
  Start here.
* **Closed testing**: testers by email list or Google Group; reviewed by Google.
* **Production access**: Google's help page says that **personal** accounts created after 13 November 2023
  must run a closed test with at least **12 testers opted in for 14 continuous days** before they can apply
  for production. The page describes this for personal accounts; it does not list organization accounts.
  Play Console's Dashboard shows the steps that apply to this account, so check there before planning a
  launch date.

## App content (Policy → App content)

### Privacy policy

`https://nearmosque.net/privacy.html` (`website/privacy.html`). It already covers Android.

### Ads, app access, government, financial, health, news

No ads. All functionality is available without an account or special access (no login). Not a government,
financial, health or news app.

### Data safety

Answers derived from the Android code and the privacy policy (re-check them after any change to networking):

| Question | Answer and why |
|---|---|
| Does the app collect or share any of the required user data types? | **Yes**, one: approximate location (below). Everything else stays on the device. |
| Is all user data encrypted in transit? | **Yes**: every request is HTTPS (Overpass, OpenFreeMap tiles, content downloads, MET Norway weather). |
| Can users request that their data is deleted? | No account and nothing kept by the developer. Users delete Ask history in the app or uninstall; turning Sky follows the weather off deletes the cached weather report; describe this in the answer. |
| **Location → Approximate location** | **Collected**. Mosque search sends the position rounded to about 1 km (`OnlineMosques.privacyRound`, two decimal places) and a radius to OpenStreetMap's Overpass API (`overpass-api.de`). On by default, can be turned off in Settings → so **optional** ("users can choose"). Purpose: **App functionality**. Not used for tracking, ads or analytics. Not shared: the request goes from the phone to the service only when the user searches, a user-initiated transfer; if in doubt, mark it shared with the same purpose, which is the more conservative answer. And, with **Sky follows the weather** on (off by default; setup page and Settings → Appearance), the prayer city's coordinates (the chosen city, or with "Use my location" the nearest bundled city within 30 km, and nothing without one) rounded to 0.01° (`Weather` in `android/core`, two decimal places) to MET Norway (`api.met.no`) about every 30 minutes while the app is open, never in the background. Still **optional**, purpose **App functionality**, not used for ads, analytics or tracking. **Shared: yes**, with MET Norway, a third party that receives it automatically while the switch is on (not each time at the user's request); this is the conservative answer recommended above, and with it the mosque search can be marked shared too. Encrypted in transit (HTTPS). Nothing is kept by the developer; the phone keeps the last report in its cache and deletes it when the switch is turned off. |
| Location → Precise location | **Not collected**. The precise fix is used only on the device for prayer times, the Qibla and distances; it never leaves the phone. The weather request sends only a city's coordinates rounded to 0.01° (about 1 km), which is approximate location (above). |
| Personal info, financial info, health, messages, photos, audio, files, calendar, contacts | **Not collected**. |
| App activity, web browsing, app info and performance, device or other IDs | **Not collected**: no analytics, crash reporting SDK or advertising ID. The Ask questions stay on the device (they leave only when the user taps a "search these sites too" link, which opens the browser). |

Map tiles (OpenFreeMap) receive the area on screen, as for any map; content downloads (GitHub, IslamHouse,
binbaz.org.sa, HadeethEnc, QuranEnc) are ordinary file requests with no user data. Ask runs entirely on the
device (offline library and on-device search); no AI service is called on Android.

### Content rating (IARC questionnaire)

Category: Reference/Education or Utility. No violence, sexual content, profanity, gambling or purchases; no
user-to-user communication; the app does not share the user's location with other users. The library holds
religious texts (Quran, hadith, fatwas) that discuss topics such as alcohol or punishments as rulings; answer
the questionnaire honestly if it asks about references, and expect an all-ages or low rating.

### Target audience

Chosen by the owner: **13 and over** (13–15, 16–17, 18+), not designed for children, with the content rating
Everyone (so the app is still suitable for, and available to, all ages). Choosing any age under 13
puts the app under the Families policy (extra review, limits on sending location from children's devices).

### Permission declarations for this manifest

| Permission | What Play needs | This app |
|---|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | No declaration for foreground use. Only background location (`ACCESS_BACKGROUND_LOCATION`) needs one. | Foreground only: location is read from the Prayer and Mosques screens when the user taps "Use my location". There is no background location permission. |
| `SCHEDULE_EXACT_ALARM` | No declaration (it is the user-granted permission; `USE_EXACT_ALARM`, which this app does not use, is restricted to alarm and calendar apps). On Android 14+ it is off by default for new installs. | Reminders, the Adhan, iqama alerts, the Fajr alarm and silence during prayer use exact alarms when allowed and inexact ones otherwise. When any of them is on and exact alarms are off, Prayer & Audio starts with a row ("To ring on time, allow “Alarms & reminders” for this app.") that opens the system page for this app; when the user comes back, or Android reports the change, everything is rescheduled. |
| `USE_FULL_SCREEN_INTENT` | Not requested, so no declaration. | Removed: the Fajr alarm is an `AlarmManager` alarm clock on the alarm channel with an insistent sound, shown as a heads-up notification. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | **Declaration** (App content → Foreground service permissions): describe the feature, the impact if it is deferred, and give a **video link** showing how a user turns it on and what happens. | `PrayerAudioService` plays the Adhan or a spoken reminder (opt-in) at the prayer time with a Stop button on its notification, then stops. For the video: Settings → Prayer & Audio → choose an Adhan for a prayer → wait for (or set) the time → the notification with Stop while it plays. |
| `ACCESS_NOTIFICATION_POLICY` | No Play declaration; the user grants Do Not Disturb access in system settings. | "Silent during prayer" (opt-in) turns the app's own Do Not Disturb rule on and off. |
| `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` | No declaration. | Reminders, rescheduling after reboot, maps and downloads. |

**When Android refuses the Adhan player.** On Android 12+ a foreground service may not start from an inexact
alarm (exact alarms off). `PrayerAudioService.start` catches the refusal and the reminder still shows: for an
Adhan, on a notification channel whose sound is that recording (one channel per recording, e.g. "Adhan ·
Masjid al-Haram, Makkah"), so it still sounds (ringer and Do Not Disturb permitting) and stops when the
notification is swiped away; a spoken reminder falls back to the reminder sound. Tests:
`app/src/test/.../platform/ReminderFallbackTest.kt`.

## Store listing

**Text.** Start from the App Store listing: `shared/store/listing/<lang>.json` (source) and
`shared/store/listing.json` (built by `tools/store/listing.py`), described in `09-app-store.md`. Use the
`IOS` description and remove what Android does not have (Apple Watch, Dynamic Island and Live Activities,
Apple Maps, Apple Intelligence); the Android widgets differ from the iOS ones (`10-features.md`). Play has
no keywords field and no promotional text. The same rules apply: describe only what the build does, and
the non-English texts are drafts until a native speaker has read them.

| Play field | Limit | Source |
|---|---|---|
| App name | 30 characters | The name the App Store listing uses |
| Short description | 80 characters | New; can start from `subtitle` + `promo` |
| Full description | 4,000 characters | `description` (`IOS`), edited as above |

**Graphics.**

| Asset | Play's requirement | What exists |
|---|---|---|
| App icon | 512 × 512 PNG (32-bit, up to 1 MB) | `icon.png`, scaled from `shared/brand/app-icon-preview.png` by `tools/store/play_graphics.py`; `mark-512.png` is the mark alone, not the icon |
| Feature graphic | 1024 × 500 JPEG or 24-bit PNG, no transparency | `<lang>/feature.png` per app language, drawn by `tools/store/play_graphics.py` |
| Phone screenshots | 2–8, JPEG or 24-bit PNG (no transparency), each side 320–3,840 px, the long side at most twice the short side | 8 per language at 1080 × 1920 (below) |
| Tablet screenshots | Optional (7" and 10") | 8 per language: 7-inch at 1811 × 2176 (an unfolded Galaxy Z Fold), 10-inch at 2560 × 1440 (a tablet held sideways) |
| Chromebook and Android XR screenshots | 4–8; the Google Play Developer API has no image type for them, so they go up by hand in Play Console | 7 per language (no widgets: neither has home-screen widgets): a desktop window at 2880 × 1620, the app's panel in a space at 3840 × 2160 |

The screenshots are the real screens rendered under Robolectric with demo data
(`android/app/src/test/.../StoreScreenshotTest.kt`, the same cities as the App Store screenshots: Riyadh for the
prayer times and the Qibla, Cairo for the mosques), then flattened and checked against Play's limits by
`tools/store/play_graphics.py`. GitHub → Actions → **Google Play graphics** (`store-play-assets.yml`) builds all
of them and keeps them in the artifact `play-store-assets`; it uploads nothing. Store order, the same in every
slot and language:

| # | File | Shows |
|---|---|---|
| 1 | `01-prayer.png` | The prayer screen: the next prayer and the time left, the Qibla arc and the day's prayers on the late-afternoon sky |
| 2 | `02-weather.png` | The same prayer screen with **Sky follows the weather** turned on (off by default): rain on the sky, with the temperature and the wind under the city ("18°C · Wind 14 km/h"; the units follow the phone's region, and the test sets the phone's locale to the app language, with no region or one that uses them, so °C and km/h in every language). A fixed demo forecast (rain, 18 °C, wind 4 m/s, as the iOS `-demoWeather` screenshots) from a fake source in the test, so nothing is fetched; in the app the weather comes from MET Norway |
| 3 | `03-qibla.png` | The Qibla compass, facing the Qibla |
| 4 | `04-mosques.png` | The mosques around Cairo and the nearest ones (the MapLibre map does not draw under Robolectric) |
| 5 | `05-ask.png` | Ask: a common question and the start of its answer |
| 6 | `06-library.png` | The library's classical books |
| 7 | `07-widgets.png` | Home-screen widgets (phone and tablets only) |
| 8 | `08-silence.png` | Silent during prayer, on, in Prayer & Audio |

Locally (from `android/`):
`./gradlew :app:testDebugUnitTest -PstoreShots=true -PstoreLangs="en ar" -PstoreDevices="phone" -Proborazzi.test.record=true --tests 'sa.zood.nearmosque.StoreScreenshotTest'`
writes `android/app/build/store-play/<lang>/<device>/`; then `python3 tools/store/play_graphics.py --languages "en ar" --slots phone`
writes `build/store-play/`. Other than English and Arabic, the text on the screenshots is the app's draft
translation, awaiting native review like the rest of the listing.

## Before the first review

* Run `android-release.yml` with the signing secrets and install the signed APK on a real Android 14+ phone:
  first launch, location, each tab, a reminder, an Adhan and the Fajr alarm. The minified build has not
  yet been run on a device.
* Record the foreground-service video.
* Fill App content (Data safety, content rating, target audience, declarations) and the store listing.
* Size: the bundle is about 99 MB (mostly the offline library and recordings); Play Console shows the
  per-device download size after upload.
