#!/usr/bin/env python3
"""Synthesizes the soundscape of the creative videos: no music, only soft wind, birds by day, crickets at night
and a gentle chime when a prayer mark lights. Every sound is generated here (no recordings, so no rights to clear),
and the result loops seamlessly (its end flows into its start).

    ambience.py <out.wav> <seconds> [chime times as fractions of the loop, e.g. 0.03,0.32,0.47,0.62]
                [--day 0.15:0.6] [--night 0.78:1.0] [--clicks 1.2,3.4] [--level -20]

Times after --clicks are seconds: a soft "tick" for a feature appearing in the promo.
"""
import sys
import wave

import numpy as np

RATE = 48000


def fast_lowpass(x, cutoff):
    # The same filter in the frequency domain (the loop is circular, which also keeps it seamless).
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    return np.fft.irfft(np.fft.rfft(x) / (1 + (f / cutoff) ** 4), len(x))


def bandpass(x, lo, hi):
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    gain = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) / (1 + (f / hi) ** 4)
    return np.fft.irfft(np.fft.rfft(x) * gain, len(x))


def window(n, u, a, b, fade):
    # 1 inside [a, b] of the loop (fractions), easing in and out over `fade`.
    w = np.clip((u - a) / fade, 0, 1) * np.clip((b - u) / fade, 0, 1)
    return w * w * (3 - 2 * w)


def main():
    out, seconds = sys.argv[1], float(sys.argv[2])
    args = sys.argv[3:]
    chimes = [float(v) for v in args[0].split(",")] if args and not args[0].startswith("--") else []
    opt = {args[i]: args[i + 1] for i in range(len(args)) if args[i].startswith("--") and i + 1 < len(args)}
    day = [float(v) for v in opt.get("--day", "0.15:0.6").split(":")]
    night = [float(v) for v in opt.get("--night", "0.78:1.0").split(":")]
    clicks = [float(v) for v in opt["--clicks"].split(",")] if opt.get("--clicks") else []
    level = float(opt.get("--level", "-20"))
    rng = np.random.default_rng(5)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    u = t / seconds
    mix = np.zeros((2, n))

    # Wind: filtered noise breathing slowly (whole cycles per loop), a little different in each ear.
    for ch in range(2):
        noise = fast_lowpass(rng.standard_normal(n), 380 + 60 * ch)
        gust = 0.55 + 0.25 * np.sin(2 * np.pi * (2 * u + 0.3 * ch)) + 0.2 * np.sin(2 * np.pi * (5 * u + 1.1))
        mix[ch] += noise / np.abs(noise).max() * gust * 0.32

    # Birds by day: short chirps (rising and falling whistles) at random moments, panned left or right.
    dayw = window(n, u, day[0], day[1], 0.05)
    for _ in range(int(seconds * 1.6)):
        at = rng.uniform(0, seconds)
        if window(1, np.array([at / seconds]), day[0], day[1], 0.05)[0] < 0.3:
            continue
        pan = rng.uniform(0.15, 0.85)
        f0 = rng.uniform(2600, 4200)
        for k in range(rng.integers(2, 5)):
            start = at + k * rng.uniform(0.07, 0.13)
            dur = rng.uniform(0.05, 0.11)
            i0, i1 = int(start * RATE), int((start + dur) * RATE)
            if i1 >= n:
                continue
            tt = np.arange(i1 - i0) / RATE
            sweep = f0 * (1 + 0.35 * np.sin(np.pi * tt / dur) * (1 if k % 2 else -1))
            phase = 2 * np.pi * np.cumsum(sweep) / RATE
            env = np.sin(np.pi * tt / dur) ** 2
            s = np.sin(phase) * env * 0.09
            mix[0, i0:i1] += s * (1 - pan)
            mix[1, i0:i1] += s * pan

    # Crickets at night: a soft pulsing trill.
    nightw = window(n, u, night[0], night[1] + 0.2, 0.05) + (window(n, u, -0.2, 0.08, 0.05) if night[0] <= 1 else 0)
    trill = np.sin(2 * np.pi * 4300 * t) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 30 * t))) * (np.sin(2 * np.pi * 3 * t) > 0.2)
    for ch in range(2):
        mix[ch] += bandpass(trill, 3000, 6000) * np.clip(nightw, 0, 1) * (0.018 if ch else 0.012)

    # A gentle chime (soft bell partials, slow decay) when a prayer mark lights.
    def bell(at, gain):
        i0 = int(at * RATE) % n
        length = int(2.6 * RATE)
        tt = np.arange(length) / RATE
        s = sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt * d) for f, a, d in ((659, 1, 1.6), (988, 0.5, 2.2), (1318, 0.25, 3.0), (1976, 0.1, 4.5)))
        s *= np.clip(tt / 0.008, 0, 1) * gain
        idx = (i0 + np.arange(length)) % n  # wraps round the loop
        mix[0, idx] += s
        mix[1, idx] += s * 0.92

    for c in chimes:
        bell(c * seconds, 0.07)

    # A soft "tick" for a feature appearing (promo): a short filtered click with a little body.
    for c in clicks:
        i0 = int(c * RATE)
        length = int(0.06 * RATE)
        if i0 + length >= n:
            continue
        tt = np.arange(length) / RATE
        s = (np.sin(2 * np.pi * 1700 * tt) * 0.6 + rng.standard_normal(length) * 0.25) * np.exp(-tt * 90) * 0.18
        mix[0, i0:i0 + length] += s
        mix[1, i0:i0 + length] += s

    # Level: peak-normalised, then set to `level` dBFS RMS; the edges already meet (wind and chimes are circular).
    mix /= np.abs(mix).max()
    rms = np.sqrt((mix ** 2).mean())
    mix *= 10 ** (level / 20) / rms
    mix = np.clip(mix, -0.98, 0.98)
    with wave.open(out, "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes((mix.T * 32767).astype("<i2").tobytes())


if __name__ == "__main__":
    main()
