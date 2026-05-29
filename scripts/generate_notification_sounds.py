"""
Premium notification sound pack for Where  (v2 — "warm signature" redesign).

The previous pack leaned on Chowning FM bells, which read as metallic and —
more importantly — every sound was its own island. Brand sound systems
(Slack, Messenger, iMessage) work because every cue shares DNA: one timbre
family and one recognisable motif. This pack gives Where that identity.

────────────────────────────────────────────────────────────────────────────
SONIC IDENTITY
────────────────────────────────────────────────────────────────────────────
  • Timbre family — warm MARIMBA / MALLET voices (tuned sine + 4:1 and
    ~10:1 partials with a fast strike transient and no sustain). Rounded
    and "wooden-premium" rather than glassy/metallic. A gentle glass voice
    and an airy sparkle are used only as accents.

  • Brand motif — a rising PERFECT FOURTH, E5 → A5, anchored in A major.
    Every cue is a variation on that two-note "Where" signature:
        chat      = the signature, plain and friendly
        general   = the signature's tail (single A, octave grace)
        mention   = signature + a brighter answering note (C#6)
        friend    = the signature opened into an A-major arpeggio
        meetup    = the signature pushed up to a celebratory run
        location  = a single warm water-drop on the root (A) — ambient
    Because they share a key and a motif, the pack reads as ONE voice.

  • Production — one-pole low-pass to tame fizz, a short Schroeder reverb
    for air, soft-knee compression + tanh limiting so layered hits never
    clip on tiny phone speakers, and 6 dB more headroom than v1 so the
    sounds feel composed, not shouty.

  • Output — 16-bit / 48 kHz PCM, encoded to Ogg Vorbis when ffmpeg is
    available (~5-9 KB/file), WAV otherwise (~40-80 KB/file). File names
    are unchanged so res/raw + NotificationSound.kt keep resolving.

Every recipe stays ≤ ~1.1 s so the system never queues or ducks other audio.
"""

from __future__ import annotations

import math
import os
import shutil
import subprocess
import sys
import wave
from dataclasses import dataclass
from typing import Callable, Iterable

import numpy as np

# ─── Config ────────────────────────────────────────────────────────────────
SAMPLE_RATE = 48_000
BIT_DEPTH = 16
PEAK_INT = (2 ** (BIT_DEPTH - 1)) - 1
STEREO = True

# Equal-temperament frequencies (Hz). A-major palette + a few neighbours.
NOTE_FREQS: dict[str, float] = {
    "A3": 220.00, "B3": 246.94, "CS4": 277.18, "D4": 293.66, "E4": 329.63,
    "FS4": 369.99, "G4": 392.00, "A4": 440.00, "B4": 493.88, "CS5": 554.37,
    "D5": 587.33, "E5": 659.25, "FS5": 739.99, "G5": 783.99, "A5": 880.00,
    "B5": 987.77, "CS6": 1108.73, "D6": 1174.66, "E6": 1318.51, "FS6": 1479.98,
    "A6": 1760.00, "CS7": 2217.46, "E7": 2637.02,
}


# ─── Envelope ──────────────────────────────────────────────────────────────
def adsr(
    n_samples: int,
    attack_ms: float = 6.0,
    decay_ms: float = 80.0,
    sustain: float = 0.45,
    release_ms: float = 380.0,
    curve: float = 4.0,
) -> np.ndarray:
    """Exponential ADSR. `curve` controls release shape (>1 = quicker fade)."""
    a = max(1, int(attack_ms * SAMPLE_RATE / 1000))
    d = max(1, int(decay_ms * SAMPLE_RATE / 1000))
    r = max(1, int(release_ms * SAMPLE_RATE / 1000))
    a = min(a, n_samples)
    d = min(d, max(0, n_samples - a))
    s_len = max(0, n_samples - a - d - r)
    r = max(0, n_samples - a - d - s_len)

    env = np.zeros(n_samples, dtype=np.float64)

    if a > 0:
        x = np.linspace(0.0, 4.0, a)
        env[:a] = 1.0 - np.exp(-x)
        env[:a] /= env[a - 1] if env[a - 1] > 0 else 1.0
    if d > 0:
        x = np.linspace(0.0, 3.0, d)
        env[a:a + d] = sustain + (1.0 - sustain) * np.exp(-x)
    if s_len > 0:
        env[a + d:a + d + s_len] = sustain
    if r > 0:
        x = np.linspace(0.0, curve, r)
        env[-r:] = sustain * np.exp(-x)
    return env


def _t(n_samples: int) -> np.ndarray:
    return np.arange(n_samples, dtype=np.float64) / SAMPLE_RATE


# ─── Oscillators / voices ────────────────────────────────────────────────────
def sine(freq: float, n_samples: int, phase: float = 0.0) -> np.ndarray:
    return np.sin(2 * np.pi * freq * _t(n_samples) + phase)


def mallet(
    freq: float,
    n_samples: int,
    pitch_drop: float = 0.06,
    drop_tau_ms: float = 7.0,
) -> np.ndarray:
    """
    Marimba / mallet voice. A tuned fundamental plus the marimba's
    characteristic 4:1 and ~10:1 inharmonic partials, each decaying faster
    than the last so the strike is bright but the tail is a pure-ish tone.
    A tiny downward pitch blip at the onset gives the "thock" of a struck
    bar. Reads as warm, wooden, and expensive — the opposite of a beep.
    """
    t = _t(n_samples)
    # Onset pitch blip — small downward exp glide applied to all partials,
    # so they stay harmonically locked while the strike "settles".
    inst_mult = 1.0 + pitch_drop * np.exp(-t / (drop_tau_ms / 1000.0))
    phase = 2 * np.pi * freq * np.cumsum(inst_mult) / SAMPLE_RATE

    # (ratio, amplitude, decay-rate multiplier). Higher partials die first.
    partials = (
        (1.0, 1.00, 3.2),
        (3.93, 0.42, 6.5),
        (9.55, 0.14, 11.0),
    )
    sig = np.zeros(n_samples, dtype=np.float64)
    for ratio, amp, decay_mult in partials:
        env = np.exp(-t * decay_mult)
        sig += amp * env * np.sin(ratio * phase)
    return sig / 1.3


def water_drop(
    freq: float,
    n_samples: int,
    glide_oct: float = 0.9,
    glide_tau_ms: float = 55.0,
) -> np.ndarray:
    """
    Friendly "pop / water-drop" voice. A sine whose pitch glides downward
    by ~an octave over the note — the classic warm, rounded social-app
    pop. Low-distraction; used for the ambient location cue.
    """
    t = _t(n_samples)
    # Start ~glide_oct octaves above `freq` and settle exponentially to it,
    # giving the rounded "ploop" of a drop landing.
    f = freq * np.power(2.0, glide_oct * np.exp(-t / (glide_tau_ms / 1000.0)))
    phase = 2 * np.pi * np.cumsum(f) / SAMPLE_RATE
    return np.sin(phase)


def fm_glass(
    carrier_freq: float,
    n_samples: int,
    mod_ratio: float = 2.0,
    mod_index_start: float = 2.6,
    mod_index_end: float = 0.18,
    mod_decay_ms: float = 200.0,
) -> np.ndarray:
    """
    Gentle FM 'glass' voice. Much lower modulation index than a classic FM
    bell, so it shimmers without the metallic clang. Used sparingly as an
    accent over the mallet voices.
    """
    t = _t(n_samples)
    mod_freq = carrier_freq * mod_ratio
    decay_samples = min(max(1, int(mod_decay_ms * SAMPLE_RATE / 1000)), n_samples)
    decay = np.ones(n_samples, dtype=np.float64) * mod_index_end
    x = np.linspace(0.0, 5.0, decay_samples)
    decay[:decay_samples] = mod_index_end + (mod_index_start - mod_index_end) * np.exp(-x)
    modulator = decay * np.sin(2 * np.pi * mod_freq * t)
    return np.sin(2 * np.pi * carrier_freq * t + modulator)


def soft_pad(freq: float, n_samples: int, detune_cents: float = 6.0) -> np.ndarray:
    """3 detuned sines = warm analog-ish pad. Detune in cents."""
    cents = detune_cents / 1200.0
    return (
        sine(freq * (2 ** -cents), n_samples)
        + sine(freq, n_samples)
        + sine(freq * (2 ** cents), n_samples)
    ) / 3.0


def air(freq: float, n_samples: int) -> np.ndarray:
    """High partials with a slow random-walk amplitude — adds 'sparkle/air'."""
    rng = np.random.default_rng(0xCAFE)
    base = sine(freq * 2.0, n_samples) * 0.6 + sine(freq * 3.0, n_samples) * 0.3
    noise_env = np.cumsum(rng.normal(0, 0.05, n_samples))
    noise_env -= noise_env.min()
    if noise_env.max() > 0:
        noise_env /= noise_env.max()
    return base * noise_env


# ─── Voicing ───────────────────────────────────────────────────────────────
@dataclass
class Voice:
    freq: float
    start_ms: float
    duration_ms: float
    velocity: float = 1.0
    voice_type: str = "mallet"        # mallet | drop | glass | pad | air | sine
    attack_ms: float = 3.0
    decay_ms: float = 90.0
    sustain: float = 0.0
    release_ms: float = 320.0
    # mallet
    pitch_drop: float = 0.06
    # drop
    glide_oct: float = 0.9
    glide_tau_ms: float = 55.0
    # glass
    fm_ratio: float = 2.0
    fm_index_start: float = 2.6
    fm_index_end: float = 0.18
    fm_mod_decay_ms: float = 200.0


def render_voice(v: Voice, total_samples: int) -> np.ndarray:
    start_sample = int(v.start_ms * SAMPLE_RATE / 1000)
    dur_samples = int(v.duration_ms * SAMPLE_RATE / 1000)
    if start_sample >= total_samples:
        return np.zeros(total_samples, dtype=np.float64)
    n = min(dur_samples, total_samples - start_sample)
    if n <= 0:
        return np.zeros(total_samples, dtype=np.float64)

    if v.voice_type == "mallet":
        wave_signal = mallet(v.freq, n, pitch_drop=v.pitch_drop)
    elif v.voice_type == "drop":
        wave_signal = water_drop(v.freq, n, glide_oct=v.glide_oct, glide_tau_ms=v.glide_tau_ms)
    elif v.voice_type == "glass":
        wave_signal = fm_glass(
            v.freq, n,
            mod_ratio=v.fm_ratio,
            mod_index_start=v.fm_index_start,
            mod_index_end=v.fm_index_end,
            mod_decay_ms=v.fm_mod_decay_ms,
        )
    elif v.voice_type == "pad":
        wave_signal = soft_pad(v.freq, n)
    elif v.voice_type == "air":
        wave_signal = air(v.freq, n)
    else:
        wave_signal = sine(v.freq, n)

    env = adsr(
        n,
        attack_ms=v.attack_ms,
        decay_ms=v.decay_ms,
        sustain=v.sustain,
        release_ms=v.release_ms,
    )
    out = np.zeros(total_samples, dtype=np.float64)
    out[start_sample:start_sample + n] = wave_signal * env * v.velocity
    return out


def render_score(voices: Iterable[Voice], total_ms: int) -> np.ndarray:
    total_samples = int(total_ms * SAMPLE_RATE / 1000)
    buf = np.zeros(total_samples, dtype=np.float64)
    for v in voices:
        buf += render_voice(v, total_samples)
    return buf


# ─── Effects ───────────────────────────────────────────────────────────────
def lowpass(x: np.ndarray, cutoff_hz: float) -> np.ndarray:
    """One-pole low-pass — rounds off fizz so mallets stay warm on small speakers."""
    rc = 1.0 / (2 * np.pi * cutoff_hz)
    dt = 1.0 / SAMPLE_RATE
    alpha = dt / (rc + dt)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += alpha * (x[i] - acc)
        y[i] = acc
    return y


def schroeder_reverb(x: np.ndarray, mix: float = 0.14, decay: float = 0.5) -> np.ndarray:
    """Tiny Schroeder reverb: 4 parallel comb filters + 2 series allpasses."""
    comb_delays_ms = (29.7, 37.1, 41.1, 43.7)
    allpass_delays_ms = (5.0, 1.7)

    def comb(sig: np.ndarray, delay_ms: float, fb: float) -> np.ndarray:
        d = max(1, int(delay_ms * SAMPLE_RATE / 1000))
        out = np.copy(sig)
        for i in range(d, len(out)):
            out[i] += fb * out[i - d]
        return out

    def allpass(sig: np.ndarray, delay_ms: float, g: float = 0.5) -> np.ndarray:
        d = max(1, int(delay_ms * SAMPLE_RATE / 1000))
        out = np.copy(sig)
        for i in range(d, len(out)):
            out[i] = -g * out[i] + sig[i - d] + g * out[i - d]
        return out

    wet = np.zeros_like(x)
    for delay in comb_delays_ms:
        wet += comb(x, delay, decay)
    wet /= len(comb_delays_ms)
    for delay in allpass_delays_ms:
        wet = allpass(wet, delay, 0.5)
    return (1.0 - mix) * x + mix * wet


def soft_compress(x: np.ndarray, threshold: float = 0.5, ratio: float = 3.0) -> np.ndarray:
    """Soft-knee compressor — keeps layered hits punchy without clipping."""
    over = np.maximum(np.abs(x) - threshold, 0.0)
    gain = 1.0 - (over * (1.0 - 1.0 / ratio)) / np.maximum(np.abs(x), 1e-9)
    return x * gain


def tanh_limiter(x: np.ndarray, drive: float = 1.0) -> np.ndarray:
    return np.tanh(x * drive)


def normalize(x: np.ndarray, target_peak: float = 0.90) -> np.ndarray:
    peak = np.max(np.abs(x)) or 1.0
    return x * (target_peak / peak)


def fade_in(x: np.ndarray, ms: float = 3.0) -> np.ndarray:
    n = min(len(x), int(ms * SAMPLE_RATE / 1000))
    if n <= 0:
        return x
    out = x.copy()
    out[:n] *= np.linspace(0.0, 1.0, n)
    return out


def fade_out(x: np.ndarray, ms: float = 45.0) -> np.ndarray:
    n = min(len(x), int(ms * SAMPLE_RATE / 1000))
    if n <= 0:
        return x
    out = x.copy()
    out[-n:] *= np.linspace(1.0, 0.0, n)
    return out


def stereo_widen(mono: np.ndarray, pre_delay_ms: float = 6.0) -> np.ndarray:
    """Tiny ITD widening — phones with mono speakers play L unchanged."""
    d = max(1, int(pre_delay_ms * SAMPLE_RATE / 1000))
    right = np.zeros_like(mono)
    right[d:] = mono[:-d]
    return np.stack([mono, right], axis=1)


# ─── Recipes ───────────────────────────────────────────────────────────────
# Shared brand motif: a warm mallet rising perfect fourth, E5 → A5 (A major).

def _signature(start_ms: float = 0.0, vel: float = 1.0) -> list[Voice]:
    """The two-note 'Where' signature: E5 → A5 on warm mallets."""
    return [
        Voice(NOTE_FREQS["E5"], start_ms=start_ms, duration_ms=460, velocity=0.95 * vel,
              voice_type="mallet", attack_ms=2, decay_ms=70, release_ms=360, pitch_drop=0.05),
        Voice(NOTE_FREQS["A5"], start_ms=start_ms + 130, duration_ms=560, velocity=1.0 * vel,
              voice_type="mallet", attack_ms=2, decay_ms=80, release_ms=460, pitch_drop=0.05),
    ]


def chat_recipe() -> tuple[list[Voice], int]:
    """The signature, plain and friendly — the everyday DM ping."""
    voices = _signature()
    # Almost-subliminal pad on the root for warmth.
    voices.append(
        Voice(NOTE_FREQS["A4"], start_ms=20, duration_ms=620, velocity=0.12,
              voice_type="pad", attack_ms=70, decay_ms=140, sustain=0.5, release_ms=320)
    )
    return voices, 900


def general_recipe() -> tuple[list[Voice], int]:
    """The signature's tail — a single warm A with a low-octave grace note."""
    voices = [
        Voice(NOTE_FREQS["A4"], start_ms=0, duration_ms=320, velocity=0.5,
              voice_type="mallet", attack_ms=2, decay_ms=60, release_ms=240, pitch_drop=0.05),
        Voice(NOTE_FREQS["A5"], start_ms=70, duration_ms=560, velocity=1.0,
              voice_type="mallet", attack_ms=2, decay_ms=80, release_ms=440, pitch_drop=0.05),
        Voice(NOTE_FREQS["A4"], start_ms=10, duration_ms=520, velocity=0.10,
              voice_type="pad", attack_ms=80, decay_ms=140, sustain=0.5, release_ms=300),
    ]
    return voices, 850


def mention_recipe() -> tuple[list[Voice], int]:
    """Signature + a brighter answering note (C#6) — 'hey, you'. More present."""
    voices = _signature(vel=1.0)
    voices += [
        # Answering accent — the major third above A, on gentle glass.
        Voice(NOTE_FREQS["CS6"], start_ms=320, duration_ms=520, velocity=0.7,
              voice_type="glass", attack_ms=3, decay_ms=90, release_ms=420,
              fm_ratio=2.0, fm_index_start=2.4, fm_index_end=0.15, fm_mod_decay_ms=200),
        Voice(NOTE_FREQS["E6"], start_ms=380, duration_ms=420, velocity=0.4,
              voice_type="air", attack_ms=12, decay_ms=120, sustain=0.4, release_ms=300),
        Voice(NOTE_FREQS["A4"], start_ms=20, duration_ms=640, velocity=0.13,
              voice_type="pad", attack_ms=70, decay_ms=140, sustain=0.55, release_ms=320),
    ]
    return voices, 1050


def friend_recipe() -> tuple[list[Voice], int]:
    """The signature opened into an A-major arpeggio — warm, social good news."""
    arp = ["A4", "CS5", "E5", "A5"]
    voices = [
        Voice(NOTE_FREQS[n], start_ms=i * 95, duration_ms=600,
              voice_type="mallet", attack_ms=3, decay_ms=100, release_ms=480,
              velocity=0.92 - i * 0.05, pitch_drop=0.045)
        for i, n in enumerate(arp)
    ]
    for n in ("A3", "E4", "A4"):
        voices.append(
            Voice(NOTE_FREQS[n], start_ms=0, duration_ms=950, velocity=0.09,
                  voice_type="pad", attack_ms=120, decay_ms=200, sustain=0.6, release_ms=500)
        )
    return voices, 1150


def meetup_recipe() -> tuple[list[Voice], int]:
    """Signature pushed up to a celebratory run — the 'you've arrived' motif."""
    pattern = ["A4", "E5", "A5", "CS6"]
    voices = [
        Voice(NOTE_FREQS[n], start_ms=i * 105, duration_ms=460,
              voice_type="mallet", attack_ms=2, decay_ms=80, release_ms=340,
              velocity=0.95 - i * 0.04, pitch_drop=0.05)
        for i, n in enumerate(pattern)
    ]
    # Final sparkle, held — celebratory but not alarming.
    voices.append(
        Voice(NOTE_FREQS["E6"], start_ms=470, duration_ms=560, velocity=0.5,
              voice_type="air", attack_ms=14, decay_ms=140, sustain=0.45, release_ms=380)
    )
    voices.append(
        Voice(NOTE_FREQS["A3"], start_ms=0, duration_ms=950, velocity=0.11,
              voice_type="pad", attack_ms=120, decay_ms=180, sustain=0.55, release_ms=420)
    )
    return voices, 1150


def location_recipe() -> tuple[list[Voice], int]:
    """A single warm water-drop on the root (A) + breath pad. Ambient, minimal."""
    voices = [
        Voice(NOTE_FREQS["A5"], start_ms=0, duration_ms=420, velocity=0.8,
              voice_type="drop", attack_ms=4, decay_ms=140, release_ms=300,
              glide_oct=0.85, glide_tau_ms=55),
        Voice(NOTE_FREQS["A4"], start_ms=0, duration_ms=640, velocity=0.16,
              voice_type="pad", attack_ms=120, decay_ms=160, sustain=0.6, release_ms=340),
    ]
    return voices, 850


SOUNDS: dict[str, Callable[[], tuple[list[Voice], int]]] = {
    "where_chat":     chat_recipe,
    "where_mention":  mention_recipe,
    "where_friend":   friend_recipe,
    "where_meetup":   meetup_recipe,
    "where_location": location_recipe,
    "where_general":  general_recipe,
}


# ─── I/O ───────────────────────────────────────────────────────────────────
def write_wav(samples: np.ndarray, path: str) -> None:
    samples = np.clip(samples, -1.0, 1.0)
    if samples.ndim == 1:
        channels = 1
        interleaved = (samples * PEAK_INT).astype(np.int16)
    else:
        channels = samples.shape[1]
        interleaved = (samples * PEAK_INT).astype(np.int16).flatten()
    with wave.open(path, "wb") as f:
        f.setnchannels(channels)
        f.setsampwidth(BIT_DEPTH // 8)
        f.setframerate(SAMPLE_RATE)
        f.writeframes(interleaved.tobytes())


def encode_to_ogg(wav_path: str, ogg_path: str) -> bool:
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        return False
    cmd = [
        ffmpeg, "-y", "-loglevel", "error",
        "-i", wav_path,
        "-c:a", "libvorbis",
        "-q:a", "5",
        ogg_path,
    ]
    try:
        subprocess.check_call(cmd)
        return True
    except Exception as e:
        print(f"  ffmpeg encode failed ({e}); keeping WAV", file=sys.stderr)
        return False


def render_one(name: str, recipe: Callable[[], tuple[list[Voice], int]]) -> tuple[str, float]:
    voices, total_ms = recipe()
    mono = render_score(voices, total_ms)
    mono = lowpass(mono, 9000.0)            # warm the highs / kill fizz
    mono = schroeder_reverb(mono, mix=0.14, decay=0.5)
    mono = soft_compress(mono, threshold=0.5, ratio=3.0)
    mono = tanh_limiter(mono, drive=1.0)
    mono = fade_in(mono, ms=3.0)
    mono = fade_out(mono, ms=45.0)
    mono = normalize(mono, target_peak=0.90)  # 6 dB more headroom than v1

    final = stereo_widen(mono, pre_delay_ms=6.0) if STEREO else mono

    raw_dir = os.path.join("app", "src", "main", "res", "raw")
    os.makedirs(raw_dir, exist_ok=True)
    base = os.path.join(raw_dir, name)

    # Wipe previous variants so Android resource resolution stays unambiguous.
    for ext in (".wav", ".ogg", ".mp3"):
        p = base + ext
        if os.path.exists(p):
            os.remove(p)

    wav_path = base + ".wav"
    write_wav(final.astype(np.float32), wav_path)

    ogg_path = base + ".ogg"
    if encode_to_ogg(wav_path, ogg_path):
        os.remove(wav_path)
        out = ogg_path
    else:
        out = wav_path
    return out, os.path.getsize(out) / 1024.0


def main() -> int:
    for name, recipe in SOUNDS.items():
        out, size_kb = render_one(name, recipe)
        print(f"  wrote {out}  ({size_kb:.1f} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
