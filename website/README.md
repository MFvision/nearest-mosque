# Near Mosque website

The landing page: one scroll story in English and Arabic. The globe (Three.js, the real Earth or gold dots) turns on its own
while all 13,090 built-in cities light up in gold as each prayer's time reaches them, so the Adhan moves round the
world in waves; Makkah shines brightest. Then the view flies from the visitor's city (from the browser's time zone)
to Makkah along the Qibla arcs, down to the Kaaba inside rings of light; then the page dives into the app's living sky (`sky.js`, the same sky as the
promo and the watch) for each feature, with the store screenshots. The chapters, in order: next prayer, five prayers, Qibla, Makkah, your sky,
weather (Sky follows the weather: the sky turns grey and overcast and rain falls, as in the app; with Reduce Motion it
stays one still, veiled frame), setup (the welcome tour, on Android screenshots), nearest mosque, Ask, library, Adhan
(with Silent during prayer), widgets and Siri, Apple Watch, every screen, privacy, download; then "Everything inside",
the features by group.

* `story.js`: every word and picture, both languages, and each chapter's stage. The download links are at the top
  (`links`), and every version's buttons come from them:
  * App Store and Google Play: set each link when its listing is live (until then the button reads "Coming soon"). Google Play is live (com.AVA.nearmosque); the App Store link waits for Apple's review.
  * Windows and Linux are not in a store: the buttons download the file straight from GitHub, from the release
    `desktop-latest` that `.github/workflows/desktop-release.yml` keeps on the newest version under fixed names
    (`near-mosque-windows.msi`, `near-mosque-linux-x86_64.AppImage`). A line of small links under the buttons
    offers the .deb for Ubuntu and Debian (`near-mosque-linux-amd64.deb`) and the release page with every file
    and `SHA256SUMS.txt`. Those links work only once the workflow has published `desktop-latest` (run it with
    **release** and **publish** ticked).
* `globe.js`, `app.js`, `style.css`, `index.html`: the page. Static files: host the folder as it is.
* `assets/`: built by `tools/site/build_assets.py` (land and cities for the globe, WebP screenshots, short clips,
  the opening of the Makkah Adhan for the sound button). `assets/<lang>/android/` holds Android phone screenshots
  (the welcome tour, from `android/app/build/screenshots/`), shown only in their own plain frame, never the iPhone's.
* `2/`: version 2, for comparing against the story: the visitor's next prayer with a live countdown on the app's
  sky, today's times, the Qibla from their city, then the rest of the app as cards. No 3D and no film. Times are
  worked out in `2/now.js` for the centre of the city from the browser's time zone, with the method the app
  suggests for that country.
* `3/`: version 3, a day of prayer: the app's sky fills the window and moves from Fajr to Isha as the page
  scrolls, each prayer time (with the visitor's own time for it) showing one part of the app.
* `4/`: version 4, Ask first: four of the app's common questions typed in and answered as the app answers them,
  with their Qur'an and HadeethEnc sources and the review label. `4/questions.json` comes from
  `shared/content/common-questions.json` through `tools/site/build_ask_sample.py`.
* `versions/`: one page that opens all four versions, for asking people which they prefer.
* `prayer.js`: prayer times, the Qibla and the sky's moment for versions 2 and 3.
* Shared by all four versions: `devices.css` with `assets/frames/` (the iPhone and Apple Watch frames drawn round
  the screenshots, and a plain phone drawn in CSS for the Android screenshots); `film.js`, `film.css` with `assets/film/` (the button and player for the app's short film, in
  Arabic and English, a wide cut and a tall cut, cut down for the web from the promo masters); `duo.js`, `duo.css`
  with `assets/<lang>/duo/` (the app on iPhone Duo in its three poses).
* `support.html` (the store's support URL) and `terms.html` (terms of use).
* `wall.js`, `wall.css` (the story page only, between the story and iPhone Duo): the wall of screens. A dark band, in
  both themes, of the app's real screens in columns (three on phones, five from 700 px wide, seven on wide screens,
  the outer two running off the edges) that glide as the page scrolls, at a little over half its speed, alternate
  columns in opposite directions, with an iPhone held still over the middle column. The middle column's screens are
  cut to the phone's screen and move at about the speed of the scroll, so as they pass behind it the phone shows the
  app one screen at a time (the five in `STEPS`), with a short stop on each; the phone's Dynamic Island stays still
  and each screen's status bar slides into place only at the top of the phone. Then one line, "Every screen, made
  for prayer." The other columns never show the phone's five screens, and no screen is on view twice at once
  (`DEAL` in `wall.js` deals eighteen screens between the columns of each layout). The motion comes from `wall.js`
  in every browser (CSS scroll-driven animations left the phone on its first screen in Safari 26 and in Chrome on
  Windows); with Reduce Motion the columns and the phone stand still and the phone changes screens without sliding. Screens: `assets/<lang>/iphone/`, `assets/<lang>/android/` and `assets/<lang>/wall/` (the welcome
  tour as plain cards: 590 px WebP made from `android/app/build/screenshots/onboarding_<step>_<lang>.png`).
  The hand is optional and off: the phone shows alone and no picture is requested. To show the phone held in a hand
  (only a picture we have the rights to), cut it into two transparent WebPs of the same size from one picture, with
  no phone in either: `assets/wall/hand-back-1.webp`, the palm and fingers, drawn behind the phone, and
  `assets/wall/hand-front-1.webp`, only the thumb (and anything else that crosses in front of the phone), drawn over
  it. Then write `<section data-wall data-hand="1">` in `index.html`. Both are laid over the phone at 1.6 times its
  width, their left edge 42% of the phone's width before the phone's, their top at 46% of its height; `--hand-w`,
  `--hand-x` and `--hand-y` in `wall.css` move them. For a new picture, use the next number in both file names and
  in `data-hand`: pictures are cached for a week (`Caddyfile`), so a file replaced under the same name can stay
  unseen by returning visitors.
* `privacy.html`, `security.html`, `credits.html` (sources and licences; with `legal.css`, `legal.js`): the privacy policy and the security page in both
  languages, for the store listings. They describe what the apps do; when an app starts sending or keeping
  something new, update them and their date before the release.
* `CNAME`: the domain, nearmosque.net, for hosts that read it (GitHub Pages). Upload the folder as it is to the
  root of the domain; the store fields are https://nearmosque.net/support.html and https://nearmosque.net/privacy.html.
* `Dockerfile`, `Caddyfile`, `railway.json`: the site as a small Caddy server, for Railway. In Railway: deploy
  this repository, set the service's root directory to `/website`, then add the custom domain nearmosque.net
  under Networking and put the DNS record Railway shows at the domain's registrar.
* `vendor/three.module.min.js`: Three.js r160 (MIT, `vendor/three.LICENSE`).

Photographs of Makkah: the city from the International Space Station (ISS Expedition 44, NASA, public domain),
Masjid al-Haram at night (Wurzelgnohm, CC0) and the tawaf, cropped from "Time lapse of Masjid al-Haram" by Masajida
Allah (CC BY 3.0; a wide cut and a tall cut for portrait screens), all via Wikimedia Commons (`.github/workflows/fetch-media.yml` downloads such files).

Data: NASA Blue Marble Next Generation and Black Marble 2016 (the Earth by day and night, public domain), Natural
Earth land (public domain, via world-atlas, for the dots look), GeoNames cities (CC BY 4.0). The Adhan recording's
rights notes are in `shared/audio/CREDITS.md`.

Checked at phone, tablet and desktop sizes from 360×740 to 2560×1440, phones held both ways, in both languages and
both themes: each chapter fits its screen, the words stay clear of the legend and the globe, and the city names never
overlap.
