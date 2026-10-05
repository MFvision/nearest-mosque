# 4. Data, sources and licenses

Nothing below grants redistribution beyond what each license states. Items marked **release gate**
must be resolved before a store release.

## 4.1 Bundled packs

| Pack | Content | Source and retrieval | License | Status |
|---|---|---|---|---|
| `cities.world` | 13,090 places (capitals, admin seats, population ≥ 50,000) with IANA zone, Arabic-script names | GeoNames `cities15000.zip`, retrieved 2026-10-03 | CC BY 4.0, attribution "City data © GeoNames" | OK (attribution shown in Settings → Sources and licenses) |
| `mosques.za-cape-town` | 69 records | OpenStreetMap via BBBike extract (snapshot 2026-09-25) | ODbL 1.0, "© OpenStreetMap contributors" | OK; derivative database must stay ODbL and be offered on request |
| `mosques.eg-cairo` | 519 records | same | ODbL 1.0 | OK |
| `mosques.gb-london` | 255 records (254 mosques, 1 prayer space) | same | ODbL 1.0 | OK |
| `sources.quran-tanzil-pickthall` | 6,236 verses, one chunk per verse, anchor `surah:ayah` | Tanzil Quran Text (Simple) v1.1 + `en.pickthall`, retrieved 2026-10-03; SHA-256 of both inputs recorded in `documents.json` | Arabic: Tanzil terms (CC BY 3.0, verbatim only, notice + link required). Translation: public-domain work (M. M. Pickthall, 1930; d. 1936) **digitized by Tanzil, whose translation terms say non-commercial use only** | Arabic OK. **Release gate:** for any commercial distribution, re-source Pickthall from a public-domain digitization or get Tanzil's permission. |
| `sources.islamhouse-{en,ar,ur,tr,id,fr,es}` | Library catalogue: en 3,337 · ar 11,931 · ur 2,870 · tr 1,194 · id 2,015 · fr 1,993 · es 1,447 records (books, articles, fatwas, videos, audio): title, description, authors, the start of the text where the API provides it, and links | IslamHouse public API v3 (`api3.islamhouse.com`), retrieved 2026-10-05 with `tools/build_islamhouse_pack.py` | IslamHouse publishes its material for free distribution; **permission to redistribute it inside an app is not confirmed in writing** | **Release gate:** written permission from IslamHouse.com (or remove these packs). Full books, videos and audio are not bundled: they download from IslamHouse hosts only when the user opens them, and are kept on the device |
| `sources.binbaz-ar` | 24,138 fatwas of Shaykh Ibn Baz (four collections: مجموع الفتاوى 4,734 · نور على الدرب 12,271 · فتاوى الدروس 5,669 · فتاوى الجامع الكبير 1,464): title, question, answer (answers over 1,500 characters are shortened, 2,903 of them, with the full page one tap away), printed source, topics, link | Official site binbaz.org.sa, retrieved 2026-10-05 with `tools/build_binbaz_pack.py` (robots.txt allows the pages; rate limited; inventory in the pack: 0 failed) | The site states «جميع الحقوق محفوظة والنقل متاح لكل مسلم بشرط ذكر المصدر» (copying permitted with the source cited) | OK with attribution: every card and the reader name the source and link the page |
| `sources.hadeethenc-{ar,en,ur,tr,id,fr,es}` | Translated hadiths with explanation, lessons, grade and takhrij: ar 3,582 · en 2,328 · ur 2,220 · tr 2,150 · id 2,260 · fr 1,790 · es 1,955 | HadeethEnc.com official Excel downloads (version header kept in `NOTICE-hadeethenc.txt`), `tools/build_enc_packs.py` | HadeethEnc terms: re-publishing permitted with no modification/addition/deletion, publisher and source credited, version stated and kept, notes sent to the source, new versions followed, no inappropriate ads | OK under those terms: texts stored in full; version shown with each item; rebuild to follow new versions |
| `sources.quranenc-{ur,tr,id,fr,es}` | Translations of the meanings of the Quran, 6,236 verses each, with the translators' notes: Urdu (Junagarhi), Turkish (Rowwad), Indonesian (Ministry of Religious Affairs), French (Rachid Maach), Spanish (Isa Garcia); the Arabic verse is shown from the Tanzil pack | QuranEnc.com official SQLite downloads, `tools/build_enc_packs.py` | QuranEnc terms (same seven conditions); only translations QuranEnc offers for download are used | OK under those terms |

Coverage is measured, not claimed: each mosque pack records its bbox and count. OSM coverage of
mosques varies (Cape Town's 69 records are certainly fewer than the mosques that exist); the app says
"nearest **known** mosque" and "the data may be incomplete".

Tanzil's copyright block is kept verbatim in `LICENSE-tanzil-quran-text.txt` inside the pack and the
displayed Arabic is never altered; the normalized search field is derived at install time and never
shown.

## 4.2 Candidate sources not bundled

| Source | Why not yet | What is needed |
|---|---|---|
| Ibn Uthaymeen (binothaimeen.net), IslamQA, Dorar, alifta.gov.sa | All rights reserved; Ibn Uthaymeen's foundation asks to be contacted for publishing permission; IslamQA's terms bar redistribution; Dorar's API is for live website search; alifta states no reuse terms | Written permission from each publisher. The app may link to their pages meanwhile |
| Balagh (بلاغ) app content | An App Store app's private storage cannot be copied (App Store binaries do not run in the Simulator, and extracting another app's data is not permitted); its own curation and Q&A are not published through an API we can use | The publisher's permission and an export or API. The IslamHouse catalogue above is the public source used instead |
| Hadith collections (Arabic and translations) | Digital editions carry their own terms | A licensed edition with stable numbering |
| Other Quran translations (ur, tr, id, fr, es) on Tanzil | Tanzil marks them non-commercial; translators/publishers hold rights | Permission from each translator/publisher |
| Offline street-map tiles | Live maps now ship (below); offline map packs need a licensed or self-built extract | A self-hosted vector tile extract (Protomaps/OpenMapTiles build) with style, glyphs and sprites for MapLibre |
| Google Places (the website's mosque source) | Needs a billed API key; shipping an unrestricted key in an app is unsafe, and Places content must be shown on a Google map with caching limits | A key restricted to the app's package/bundle ID and signing certificate, a server proxy if usage must be controlled, Google Maps SDK for display |
| Time-zone boundaries | Not needed for city selection; nearest-city + confirmation covers arbitrary points | `timezone-boundary-builder` (ODbL) pack if exact boundaries are wanted |

## 4.2b Online services used (live map and live results)

| Service | Platform | Terms that matter | What is sent |
|---|---|---|---|
| Apple Maps (MapKit map, MKLocalSearch, MKDirections walking routes) | iOS | Free with the Apple Developer Program; results shown on Apple's map; Apple attribution is drawn by MapKit | Map area being viewed; search region centred on a 0.01° rounded point; route endpoints when a walking route is requested |
| OpenFreeMap (`tiles.openfreemap.org`, "dark" style) | Android | Free, no key, no usage limits stated; attribution "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" shown in the app | Tile requests for the area being viewed |
| OpenStreetMap Overpass API (`overpass-api.de`) | Android | Public instance for light use; heavy production traffic should use a self-hosted or commercial instance; data ODbL | One query per search: a 0.01° rounded centre and a radius |

None of these receive an account, device ID, prayer settings or questions. Online search can be
switched off in Settings; the map images are only loaded when the Map view is open.

## 4.3 Software dependencies

| Dependency | Version | License | Platform |
|---|---|---|---|
| adhan2 (Adhan Kotlin) | 0.0.7 | MIT | Android |
| adhan-swift | 1.5.0 | MIT | iOS |
| GRDB.swift | 7.11.1 | MIT | iOS |
| Jetpack Compose BOM | 2025.12.01 | Apache-2.0 | Android |
| AndroidX Room | 2.8.4 | Apache-2.0 | Android |
| AndroidX DataStore, AppCompat, Activity, Lifecycle, Core | see `android/gradle/libs.versions.toml` | Apache-2.0 | Android |
| kotlinx-serialization / coroutines | 1.9.0 / 1.10.2 | Apache-2.0 | Android |
| Robolectric, Roborazzi (tests only) | 4.16.1 / 1.52.0 | MIT / Apache-2.0 | Android tests |
| pyosmium (tools only) | 4.3.1 | BSD-2-Clause | pack building |
| MapLibre Native Android | 13.0.2 | BSD-2-Clause | Android (live map) |
| MapKit | system | Apple SDK | iOS (live map, search, routes) |

No analytics, crash reporting or ads are included. The only network code is the live map and live
mosque search described in 4.2b.

## 4.4 Brand assets

`shared/brand/near-mosque-logo-source.png` is the logo from the website ZIP; the app icon and header
mark are crops of it (`shared/brand/`). No remote images are used.

## 4.5 Rebuilding packs

```bash
pip install osmium
python3 tools/build_cities.py cities15000.txt --out packs/cities
python3 tools/build_mosque_pack.py CapeTown.osm.pbf --id za-cape-town --name "Cape Town" \
    --source-url https://download.bbbike.org/osm/bbbike/CapeTown/ --out packs/mosques
python3 tools/build_quran_pack.py --arabic quran-simple.txt --translation en.pickthall.txt \
    --metadata quran-data.xml --questions shared/content/common-questions.json \
    --out packs/sources/quran-tanzil-pickthall
python3 tools/gen_fixtures.py && python3 tools/reference_search.py --gen   # fixtures
python3 tools/build_islamhouse_pack.py --out packs/sources                   # IslamHouse library (cached in .cache/)
python3 tools/build_binbaz_pack.py --out packs/sources                       # Ibn Baz fatwas (rate limited, cached)
python3 tools/build_enc_packs.py --out packs/sources                         # HadeethEnc + QuranEnc (needs openpyxl)
python3 tools/library_search.py --gen                                        # library search fixture
python3 tools/gen_strings.py                                                # iOS + Android strings
```

Inputs: `https://download.geonames.org/export/dump/cities15000.zip`,
`https://download.bbbike.org/osm/bbbike/<City>/<City>.osm.pbf`,
`https://tanzil.net/pub/download/index.php?quranType=simple&outType=txt-2&agree=true&marks=true&sajdah=true&tatweel=true`,
`https://tanzil.net/trans/en.pickthall`, `https://tanzil.net/res/text/metadata/quran-data.xml`.
A pack for import is the pack folder (manifest + files); Android also accepts it zipped as `.nmpack`.
