# App icon concepts

The customer's layered emblem (block with nested domes) combined with the brand's navy location arrow.

* `options-first-round.png`: A (arrow above the block), B (block in a location pin), C (block in the Qibla ring).
* `options-final.png`: D (block with Qibla arc and arrow) and E (arrow fused to the block's corner), the icon until
  the customer's emblem replaced it (see `../emblem/`).
* `draw.py` draws D and E; `python3 draw.py` rewrites `icon-D.svg` / `icon-E.svg`.

The installed icon is now option 2 from `../emblem/` (the arrow behind the cube); `../emblem/install.py` writes
`../near-mosque-icon.svg`, `../near-mosque-icon-dark.svg`, `../near-mosque-mark.svg` and every platform file.
