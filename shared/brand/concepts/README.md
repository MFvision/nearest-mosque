# App icon concepts

The customer's layered emblem (block with nested domes) combined with the brand's navy location arrow.

* `options-first-round.png`: A (arrow above the block), B (block in a location pin), C (block in the Qibla ring).
* `options-final.png`: D (block with Qibla arc and arrow) and **E (arrow fused to the block's corner), chosen**.
* `draw.py` draws D and E; `python3 draw.py` rewrites `icon-D.svg` / `icon-E.svg`.

The chosen icon is `../near-mosque-icon.svg` (full icon) and `../near-mosque-mark.svg` (mark alone, used in
the app's logo disc). Installed as `ios/App/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`
and the Android adaptive icon (`res/drawable-nodpi/ic_launcher_foreground.png`, `ic_launcher_monochrome.png`,
`res/drawable/ic_launcher_background.xml`); see `android-adaptive-preview.png`.
