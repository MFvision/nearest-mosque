# Emblem

`reference.png` is the customer's emblem: the Kaaba as a cube (top face, hizam band, courses) with the
ribbed dome of the Prophet's Mosque inside it. `emblem.py` redraws it as vector; the rib curves are
cubics fitted to the reference's centre lines (within 2 to 4 trace units).

`options.py` combines the navigation arrow with the emblem, in light and dark, and writes
`option-*.svg` and `options.html` (each at 340, 120, 60 and 40 px; screenshots in `options.png` and,
full size, `option2-large.png` and `option3-large.png`):

- 2: the angled arrow behind the cube, low: only its tip is inside, under the cube's lines.
- 3: the dome's crown is the arrow; its base is the roof's top edges. Colours a to d: navy and gold,
  the cube's own colour, navy, and green like the dome.

    python3 emblem.py && python3 options.py

**Installed: option 2.** `python3 install.py` writes `../near-mosque-icon.svg`, `../near-mosque-icon-dark.svg`
and `../near-mosque-mark.svg`, and renders the iOS app icon (light, dark and tinted, in
`AppIcon.appiconset`), the logo mark (iOS `LogoMark`, Android `logo_mark`) and the Android adaptive icon
foreground and monochrome layers (inside the 66 dp safe circle). `python3 preview.py` writes
`installed-preview.png` from the installed files.
