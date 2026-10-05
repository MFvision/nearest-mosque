# Emblem

`reference.png` is the customer's emblem: the Kaaba as a cube (top face, hizam band, courses) with the
ribbed dome of the Prophet's Mosque inside it. `emblem.py` redraws it as vector; the rib curves are
cubics fitted to the reference's centre lines (within 2 to 4 trace units).

`options.py` places the app's navigation arrow inside the emblem and writes `option-1.svg` to
`option-6.svg` and `options.html` (each option at 340, 120, 60 and 40 px; screenshot in `options.png`).

    python3 emblem.py && python3 options.py
