# Near Mosque website

The landing page: one scroll story in English and Arabic. The globe (Three.js, the real Earth or gold dots) turns on its own
while all 13,090 built-in cities light up in gold as each prayer's time reaches them, so the Adhan moves round the
world in waves; Makkah shines brightest. Then the view flies from the visitor's city (from the browser's time zone)
to Makkah along the Qibla arcs, down to the Kaaba inside rings of light; then the page dives into the app's living sky (`sky.js`, the same sky as the
promo and the watch) for each feature, with the store screenshots.

* `story.js`: every word and picture, both languages, and each chapter's stage. Store links are at the top:
  set them when the listings are live (until then the buttons read "Coming soon").
* `globe.js`, `app.js`, `style.css`, `index.html`: the page. Static files: host the folder as it is.
* `assets/`: built by `tools/site/build_assets.py` (land and cities for the globe, WebP screenshots, short clips,
  the opening of the Makkah Adhan for the sound button).
* `privacy.html`, `security.html`, `credits.html` (sources and licences; with `legal.css`, `legal.js`): the privacy policy and the security page in both
  languages, for the store listings. They describe what the apps do; when an app starts sending or keeping
  something new, update them and their date before the release.
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
