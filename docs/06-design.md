# 6. Design: Quiet Glass

The design follows the user's reference videos and concept images (see `01-audit.md`): an atmospheric
sky for the current prayer period, Liquid Glass surfaces, and the Qibla folded into the prayer header.

## Sky

Seven skies (night, Fajr, sunrise, day, Asr, Maghrib, Isha) in `shared/design/tokens.json`: top, mid,
horizon glow and ground/lake colours. The landscape is drawn, not photographed: gradient, stars at
night, slow clouds, a horizon glow behind two mountain ridges, a lake with the glow's reflection, and a
mosque skyline on the Ask tab. The horizon height differs per tab (0.64 Prayer, 0.42 Mosques, 0.30 Ask)
and glides when switching tabs on Android. `tools/check_tokens.py` checks white text on every dark sky stop
and the three inks (text, gold, secondary) on every light sky stop, including the glow blended in at its maximum
where text can sit, for today's sky and for each weather veil below (also at the lighter showers mix): 1,827
checks with the card checks (7 cards, 7 dark skies × 13 looks × 5, 7 light skies × 13 looks × 15).

**Sky follows the weather** (opt-in, off by default) puts the prayer city's current weather from MET Norway
on this sky without replacing it: clear, partly cloudy, cloudy, rain, thunderstorm, fog, snow (showers are a
lighter rain or snow). The numbers are in `shared/design/tokens.json` → `weather`, and NMCore and
`android/core` `WeatherLook` carry the same ones (their tests check). Every stop is mixed toward the
condition's grey (one for dark skies, one for light), the glow, stars and cloud band are scaled, and a few
quiet layers are added: an overcast deck (kept translucent enough for 4.5:1), thin rain streaks (60 at full
intensity) or small flakes (55), faint under the header text (rain 35% at the top, full nearer the horizon;
flakes 10% at the top, 30% at 0.6 of the way down, full only at the horizon, and pale blue-grey rather than the
white of the text on dark skies, so none reads as a star or a letter's dot), a low
fog band and, in a thunderstorm, a rare soft glow. With Reduce Motion / animations off it is one still frame
with no lightning. iOS also gives the still frame in Low Power Mode and drops the lightning with Dim Flashing
Lights; Android drops it in Battery Saver.
One small line under the location pill gives the temperature and the wind only ("24°C · Wind 14 km/h";
°F and mph where the region uses them). Windows and Linux show the same weather in a strip behind the
next-prayer card, under a 60% card-colour scrim where the text sits (`SkyStripTest` checks 4.5:1 for the
three inks on every period and condition). It is decoration: hidden from VoiceOver and TalkBack, never
mirrored, never takes touches, and with no recent data (offline, or over 3 hours old) the normal sky returns
without a message (`ios/App/Support/WeatherSky.swift`, `android/.../ui/glass/WeatherSky.kt`,
`windows/.../desktop/SkyStrip.kt`).

## Glass

| | iOS | Android |
|---|---|---|
| Surfaces | Apple Liquid Glass (`glassEffect`, `GlassEffectContainer`) on iOS 26, tinted with the sky's ground colour at 35%; `ultraThinMaterial` with a specular rim on iOS 18–25; solid surface with Reduce Transparency | Drawn glass: translucent tint, top-to-bottom sheen, specular rim brighter at the top-left, soft shadow (the backdrop is a smooth procedural sky, so a blur pass adds nothing visible and keeps every API level identical) |
| Tab bar | System Liquid Glass tab bar (minimizes on scroll, iOS 26) | Floating glass capsule with a gold bubble on the selected tab |
| Buttons | `.glass` and gold `.glassProminent` (dark label: white on gold is under 4.5:1) | Same shapes and colours |
| Appearance | Always dark scheme (the app always sits on a sky) | Same |

## Interaction states

1. **Home** — the arc: brand disc at the top, gold dot at the Qibla's direction relative to the top of
   the phone, a white trail from the disc to the dot; next prayer, time (60 pt light), remaining time
   ("7 hr, 5 min, 32 sec left") and guidance ("Turn the phone toward the Qibla").
2. **Aligned** — dot under the disc (enter ≤ 5°, leave > 8°, accuracy ≤ 20°), disc glows and pulses,
   gold arcs either side of the disc, "You're facing the Qibla", one success haptic.
3. **No live heading** — the arc is north-up ("N" under the disc), the dot sits at the true bearing,
   and the text says so; never a fake live arrow.
4. **Compact** — scrolling past the arc pins a glass capsule with prayer, time, remaining time, a
   small Qibla arrow and a button back to the top.
5. **Full compass** — on the sky: glass dial turning with the heading, Kaaba at the Qibla bearing with
   a gold line from the centre, guidance, bearing and distance chips, accuracy.

## Onboarding

Six pages on the sky with glass controls (Skip, Back, page dots, Next / Get started): welcome (logo
disc with expanding rings), prayer times (sun travelling the arc, the active prayer highlighting),
Qibla (dot travelling to the disc, then the glow), nearest mosque (pins dropping, a dashed route to the
nearest), Ask (question bubble, typing dots, a source card rising), and setup (language chips, use my
location / search a city, online-search disclosure). With Reduce Motion / animations off each
illustration shows its final still frame and no animation runs.

## Accessibility

* Visible labels on all three tabs; 44 pt (iOS) / 48 dp (Android) minimum targets.
* Dynamic Type / font scale: no fixed-height text containers; the big time scales down before truncating.
* VoiceOver/TalkBack: the next prayer is one header element ("Dhuhr at 12:36 PM, in 7:05:32");
  guidance is a polite live region; decorative art (sky, disc, illustrations) is hidden.
* Reduce Motion: springs become instant, loops stop. Reduce Transparency (iOS): glass becomes solid.
* RTL: Arabic and Urdu mirror layout and navigation; compass, arc, radar and map geometry do not
  mirror; data strings (Latin names/addresses) keep their own direction.
* Colour: white text checked on every sky; gold `#D4A843` used for accents and large text only.
