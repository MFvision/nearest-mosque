# Near Mosque website

The landing page: one scroll story in English and Arabic. The globe (Three.js) shows the five prayer lines where
the sun stands at each prayer's angle today, gold rings and mosques rising where a prayer time arrives, and the
Qibla arcs meeting at Makkah; then the page dives into the app's living sky (`sky.js`, the same sky as the
promo and the watch) for each feature, with the store screenshots.

* `story.js`: every word and picture, both languages, and each chapter's stage. Store links are at the top:
  set them when the listings are live (until then the buttons read "Coming soon").
* `globe.js`, `app.js`, `style.css`, `index.html`: the page. Static files: host the folder as it is.
* `assets/`: built by `tools/site/build_assets.py` (land and cities for the globe, WebP screenshots, short clips,
  the opening of the Makkah Adhan for the sound button).
* `vendor/three.module.min.js`: Three.js r160 (MIT, `vendor/three.LICENSE`).

Data: Natural Earth land (public domain, via world-atlas), GeoNames cities (CC BY 4.0). The Adhan recording's
rights notes are in `shared/audio/CREDITS.md`.
