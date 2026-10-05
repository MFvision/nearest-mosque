# 7. TestFlight

The workflow `.github/workflows/testflight.yml` archives the iOS app (Release), signs it
with Xcode automatic signing through an App Store Connect API key, and uploads it to TestFlight. No
certificates or provisioning profiles are stored in the repository.

## One-time setup (Apple account holder)

1. **Apple Developer Program** membership (paid). Team ID **AWV883QWPU** is already set in
   `ios/project.yml`.
2. **Register the bundle ID** (you choose it; it is the app's permanent identifier, reverse-domain
   style; this app uses **`sa.zood.nearmosque`**): developer.apple.com → Account → Certificates,
   Identifiers & Profiles → **Identifiers** → "+" → **App IDs** → Continue → **App** → Continue →
   Description `Near Mosque`, Bundle ID **Explicit** `sa.zood.nearmosque` → leave capabilities
   unticked → Continue → Register.
3. **App record**: App Store Connect → Apps → "+" → **New App** → platform iOS, a name of your choice
   (e.g. "Near Mosque"; it must be unique on the App Store, so try "Near Mosque - أقرب مسجد" if
   taken), primary language, Bundle ID: pick `sa.zood.nearmosque` from the list, SKU anything (e.g.
   `nearmosque`), full access → Create.
4. **API key**: App Store Connect → Users and Access → Integrations → App Store Connect API →
   Team Keys → "+", role **Admin** (needed for cloud-managed signing). Download the `.p8` file (only
   possible once) and note the **Key ID** and the **Issuer ID** shown above the list.
5. **GitHub secrets** (github.com/MFvision/nearest-mosque → Settings → Secrets and variables → Actions →
   New repository secret). Paste values there, never in chat or in files:

   | Secret | Value |
   |---|---|
   | `ASC_KEY_ID` | Key ID |
   | `ASC_ISSUER_ID` | Issuer ID (UUID) |
   | `ASC_PRIVATE_KEY` | The whole contents of `AuthKey_XXXX.p8`, including the BEGIN/END lines |

6. **Testers**: App Store Connect → the app → TestFlight → Internal Testing → create a group and add
   yourself (internal testers need no Beta App Review). Install the TestFlight app on the iPhone.

## Making a build

GitHub → Actions → "Nearest Mosque TestFlight" → **Run workflow**, or push a tag starting with `ios-v`
(e.g. `ios-v1`). Each run gets a
new build number (100 + run number); the version is `MARKETING_VERSION` in `ios/project.yml`.
After upload, Apple processes the build (usually 5–30 minutes); TestFlight then offers it to the group.
Export compliance is pre-answered (`ITSAppUsesNonExemptEncryption = NO`: the app only uses HTTPS).

After the upload the workflow waits for Apple's processing and runs `tools/asc_testflight.py`: it
answers export compliance, and gives the internal group **Near Mosque Team** (created automatically
with the App Store Connect team as testers, "access to all builds") the new build, so it reaches
TestFlight without manual steps. **Actions → TestFlight status → Run workflow** reports the latest
builds at any time. Internal testers install without Beta App Review; external groups need review.

If the run fails, the job log and the `testflight-logs` artifact show the archive and export output.
Typical causes: a missing secret (the first step names it), no app record for the bundle ID, or an
API key without the Admin role.

## What to test (every feature)

| Area | Steps | Expected |
|---|---|---|
| Welcome tour | Fresh install; swipe or use Next; change language from the globe button on any page; Skip; replay from Settings → "Show welcome tour" | Six animated pages; language changes immediately; Skip goes to the setup page (language and location) |
| Prayer times | Use my location, then compare with your mosque's timetable; switch method in Calculation | Times match the chosen method within a minute; countdown runs; Hijri date shown |
| Time zone | With the phone on automatic time, use your location | Times in the phone's zone; no prompt. Set a manual wrong zone near a city to see the confirmation |
| Qibla | Hold the phone flat on the Prayer tab and turn slowly; open the full compass | Gold dot moves; at the Qibla the disc glows and you feel one haptic. Check against a known Qibla direction |
| Reminders | Tap a bell; wait for the prayer time (or set a near offset) | Notification at the prayer time |
| Nearest Mosque | Compass view (turn the phone), Map view (pan, "Search this area"), cards, Get directions | Live Apple Maps results with walking time; Directions offers Apple Maps, Google Maps and Waze |
| Offline | Airplane mode with GPS on; reopen each tab | Prayer, Qibla, compass and Ask work; Mosques shows downloaded areas (Cape Town, Cairo, London) and says you're offline elsewhere |
| Ask | Common questions; your own question; follow-up; Read in context | Quoted verses with references; "couldn't find" instead of guessing. On iOS 26 devices with Apple Intelligence, an on-device written answer above the quotes |
| Ask library | Ask "What is Islam?" (and in Arabic "ما هو الإسلام؟"); tap "Read in the app" on a book, "Watch" on a video | IslamHouse items in your language; the book downloads once and opens at the first page mentioning your question; it opens again in airplane mode |
| Languages | Settings → Language: Arabic, Urdu, Turkish, Indonesian, French, Spanish | Text and layout switch immediately; Arabic and Urdu right-to-left |
| Accessibility | VoiceOver, larger text, Reduce Motion, Reduce Transparency | Everything readable and operable; animations stop; glass turns solid |
