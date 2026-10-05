# Emblem

`reference.png` is the customer's emblem: the Kaaba as a cube (top face, hizam band, courses) with the
ribbed dome of the Prophet's Mosque inside it. `emblem.py` redraws it as vector; the rib curves are
cubics fitted to the reference's centre lines (within 2 to 4 trace units).

`options.py` combines the navigation arrow with the emblem, in light and dark, and writes
`option-*.svg` and `options.html` (each at 340, 120, 60 and 40 px; screenshots in `options.png` and,
full size, `options-large.png`):

- 2: the angled arrow behind the cube, low inside its walls; the cube's lines run over it.
- 3: the dome's crown is the arrow; its base is the roof's top edges.

    python3 emblem.py && python3 options.py
