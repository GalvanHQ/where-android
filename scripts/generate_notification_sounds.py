"""
Premium notification sound pack for Where.

Each sound is a small sound-design composition rather than a raw beep.
Techniques used (all stock DSP — no external samples needed):

  • FM bell synthesis (Chowning's algorithm) for tuned-percussion timbres
    that read as expensive notification chimes (Pixel / Samsung One UI feel).
  • Layered sine / triangle partials with detuning for chord stacks.
  • Exponential ADSR envelopes — attack-fade is exponential rather than
    linear, so transients feel "snappy" instead of "clicky".
  • Light Schroeder reverb tail (4 short comb filters + 2 allpasses) so
    every tone has air around it.
  • Soft-knee compression + tanh limiter so layered hits never clip even
    when played through small phone speakers.
  • Stereo: tones are rendered mono, then a tiny pre-delay between L and R
    creates a subtle width without messing with phone speakers (most
    Android speakers are mono — the L channel will play unchanged).
  • Output: 16-bit PCM at 48 kHz, encoded to Ogg Vorbis when ffmpeg is
    available (3-7 KB / file), WAV otherwise (~80 KB / file).

Sound design intent (kept tight — every recipe ≤ 1.2 s so the system
doesn't queue or duck other audio):

  • where_chat       — two-note bell + pad shimmer. Friendly DM ping.
  • where_mention    — chat motif + a higher answering bell + reverb
                       tail. More urgent than chat, still warm.
  • where_friend     — Cmaj7 arpeggio on an FM bell, soft attack, long
                       release. Warm, social, "pleasant news".
  • where_meetup     — ascending five-note motif (Pentatonic G) with
                       sparkle on top — designed to grab attention for
                       arrivals without alarm.
  • where_location   — single soft pulse + breath-pad swell. Ambient,
                       low-distraction.
  • where_general    — minor-third bell pair (E5 → C5). Classic phone
                       chime updated for 2025.
"""

from __future__ import annotations

import math
import os
import shutil
import struct
import subprocess
import sys
import wave
from dataclasses import dataclass, field
from typing import Callable, Iterable

import numpy as np

# ─── Config ────────────────────────────────────────────────────────────────
SAMPLE_RATE = 48_000
BIT_DEPTH = 16
PEAK_INT = (2 ** (BIT_DEPTH - 1)) - 1
STEREO = True

# Equal-temperament frequencies (Hz).
NOTE_FREQS: dict[str, float] = {
    "G3": 196.00, "A3": 220.00, "B3": 246.94,
    "C4": 261.63, "D4": 293.66, "E4": 329.63, "F4": 349.23, "G4": 392.00,
    "A4": 440.00, "B4": 493.88, "C5": 523.25, "D5": 587.33, "E5": 659.25,
    "F5": 698.46, "G5": 783.99, "A5": 880.00, "B5": 987.77, "C6": 1046.50,
    "D6": 1174.66, "E6": 1318.51, "G6": 1567.98, "A6": 1760.00,
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
    if s_len < 0:
        s_len = 0
    r = max(0, n_samples - a - d - s_len)

    env = np.zeros(n_samples, dtype=np.float64)

    # Attack — short exponential ramp (1 - e^-x) for a snappy onset.
    if a > 0:
        x = np.linspace(0.0, 4.0, a)
        env[:a] = 1.0 - np.exp(-x)
        env[:a] /= env[a - 1] if env[a - 1] > 0 else 1.0

    # Decay — exponential to sustain level.
    if d > 0:
        x = np.linspace(0.0, 3.0, d)
        env[a:a + d] = sustain + (1.0 - sustain) * np.exp(-x)

    # Sustain — flat.
    if s_len > 0:
        env[a + d:a + d + s_len] = sustain

    # Release — exponential decay to zero, with a configurable curve.
    if r > 0:
        x = np.linspace(0.0, curve, r)
        env[-r:] = sustain * np.exp(-x)

    return env


# ─── Oscillators ───────────────────────────────────────────────────────────
def sine(freq: float, n_samples: int, phase: float = 0.0) -> np.ndarray:
    t = np.arange(n_samples, dtype=np.float64) / SAMPLE_RATE
    return np.sin(2 * np.pi * freq * t + phase)


def fm_bell(
    carrier_freq: float,
    n_samples: int,
    mod_ratio: float = 1.4,
    mod_index_start: float = 6.0,
    mod_index_end: float = 0.5,
    mod_decay_ms: float = 220.0,
) -> np.ndarray:
    """
    Chowning-style FM bell. Modulation index sweeps from `start` to `end`
    over `mod_decay_ms`, producing the iconic "ping then settle to a
    near-sine" timbre of a tuned bell. mod_ratio = 1.4 → inharmonic but
    tuned-feeling partials (3.5 / 2.5 / 1.4 above carrier).
    """
    t = np.arange(n_samples, dtype=np.float64) / SAMPLE_RATE
    mod_freq = carrier_freq * mod_ratio
    decay_samples = max(1, int(mod_decay_ms * SAMPLE_RATE / 1000))
    decay_samples = min(decay_samples, n_samples)
    decay = np.ones(n_samples, dtype=np.float64) * mod_index_end
    x = np.linspace(0.0, 5.0, decay_samples)
    decay[:decay_samples] = mod_index_end + (mod_index_start - mod_index_end) * np.exp(-x)
    modulator = decay * np.sin(2 * np.pi * mod_freq * t)
    return np.sin(2 * np.pi * carrier_freq * t + modulator)


def soft_pad(freq: float, n_samples: int, detune_cents: float = 7.0) -> np.ndarray:
    """3 detuned sines = poor man's lush analog pad. Detune in cents."""
    cents = detune_cents / 1200.0
    f1 = freq * (2 ** -cents)
    f2 = freq
    f3 = freq * (2 ** cents)
    return (sine(f1, n_samples) + sine(f2, n_samples) + sine(f3, n_samples)) / 3.0


def shimmer(freq: float, n_samples: int) -> np.ndarray:
    """A single high partial with random-walk amplitude — adds 'air'."""
    rng = np.random.default_rng(0xCAFE)
    base = sine(freq * 2.0, n_samples) * 0.6 + sine(freq * 3.0, n_samples) * 0.3
    noise_env = np.cumsum(rng.normal(0, 0.05, n_samples))
    noise_env = (noise_env - noise_env.min())
    if noise_env.max() > 0:
        noise_env = noise_env / noise_env.max()
    return base * noise_env


# ─── Voicing ───────────────────────────────────────────────────────────────
@dataclass
class Voice:
    freq: float
    start_ms: float
    duration_ms: float
    velocity: float = 1.0
    voice_type: str = "bell"          # bell | sine | pad | shimmer
    attack_ms: float = 6.0
    decay_ms: float = 80.0
    sustain: float = 0.45
    release_ms: float = 380.0
    fm_ratio: float = 1.4
    fm_index_start: float = 6.0
    fm_index_end: float = 0.5
    fm_mod_decay_ms: float = 220.0


def render_voice(v: Voice, total_samples: int) -> np.ndarray:
    start_sample = int(v.start_ms * SAMPLE_RATE / 1000)
    dur_samples = int(v.duration_ms * SAMPLE_RATE / 1000)
    if start_sample >= total_samples:
        return np.zeros(total_samples, dtype=np.float64)
    available = total_samples - start_sample
    n = min(dur_samples, available)
    if n <= 0:
        return np.zeros(total_samples, dtype=np.float64)

    if v.voice_type == "bell":
        wave_signal = fm_bell(
            v.freq, n,
            mod_ratio=v.fm_ratio,
            mod_index_start=v.fm_index_start,
            mod_index_end=v.fm_index_end,
            mod_decay_ms=v.fm_mod_decay_ms,
        )
    elif v.voice_type == "pad":
        wave_signal = soft_pad(v.freq, n)
    elif v.voice_type == "shimmer":
        wave_signal = shimmer(v.freq, n)
    else:
        wave_signal = sine(v.freq, n)

    env = adsr(
        n,
        attack_ms=v.attack_ms,
        decay_ms=v.decay_ms,
        sustain=v.sustain,
        release_ms=v.release_ms,
    )
    rendered = wave_signal * env * v.velocity
    out = np.zeros(total_samples, dtype=np.float64)
    out[start_sample:start_sample + n] = rendered
    return out


def render_score(voices: Iterable[Voice], total_ms: int) -> np.ndarray:
    total_samples = int(total_ms * SAMPLE_RATE / 1000)
    buf = np.zeros(total_samples, dtype=np.float64)
    for v in voices:
        buf += render_voice(v, total_samples)
    return buf


# ─── Effects ───────────────────────────────────────────────────────────────
def schroeder_reverb(x: np.ndarray, mix: float = 0.18, decay: float = 0.6) -> np.ndarray:
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


def soft_compress(x: np.ndarray, threshold: float = 0.6, ratio: float = 3.0) -> np.ndarray:
    """Simple soft-knee compressor — keeps layered hits punchy without clipping."""
    over = np.abs(x) - threshold
    over = np.maximum(over, 0.0)
    gain = 1.0 - (over * (1.0 - 1.0 / ratio)) / np.maximum(np.abs(x), 1e-9)
    return x * gain


def tanh_limiter(x: np.ndarray, drive: float = 1.0) -> np.ndarray:
    return np.tanh(x * drive)


def normalize(x: np.ndarray, target_peak: float = 0.92) -> np.ndarray:
    peak = np.max(np.abs(x)) or 1.0
    return x * (target_peak / peak)


def fade_out(x: np.ndarray, ms: float = 30.0) -> np.ndarray:
    n = min(len(x), int(ms * SAMPLE_RATE / 1000))
    if n <= 0:
        return x
    fade = np.linspace(1.0, 0.0, n)
    out = x.copy()
    out[-n:] *= fade
    return out


def stereo_widen(mono: np.ndarray, pre_delay_ms: float = 6.0) -> np.ndarray:
    """Tiny ITD widening — phones with mono speakers will play L unchanged."""
    d = max(1, int(pre_delay_ms * SAMPLE_RATE / 1000))
    right = np.zeros_like(mono)
    right[d:] = mono[:-d]
    return np.stack([mono, right], axis=1)


# ─── Recipes ───────────────────────────────────────────────────────────────
def chat_recipe() -> tuple[list[Voice], int]:
    """
    Bright two-note bell ping (E5 → A5) with a soft pad shimmer behind it.
    Total duration ~700 ms; effective "perceived" length ~340 ms thanks
    to fast release.
    """
    voices = [
        Voice(NOTE_FREQS["E5"], start_ms=0,   duration_ms=480,
              voice_type="bell", attack_ms=4, decay_ms=80, sustain=0.30, release_ms=380,
              fm_ratio=1.41, fm_index_start=5.5, fm_index_end=0.4, fm_mod_decay_ms=160),
        Voice(NOTE_FREQS["A5"], start_ms=80,  duration_ms=560, velocity=0.9,
              voice_type="bell", attack_ms=4, decay_ms=90, sustain=0.30, release_ms=460,
              fm_ratio=1.41, fm_index_start=5.0, fm_index_end=0.4, fm_mod_decay_ms=170),
        # Soft pad behind the bells — almost subliminal.
        Voice(NOTE_FREQS["A4"], start_ms=20, duration_ms=520, velocity=0.18,
              voice_type="pad", attack_ms=80, decay_ms=120, sustain=0.5, release_ms=300),
    ]
    return voices, 900


def mention_recipe() -> tuple[list[Voice], int]:
    """
    Chat motif + a higher answering bell. Slightly more urgent than
    chat_recipe — total ~1.0 s.
    """
    voices = [
        Voice(NOTE_FREQS["E5"], start_ms=0,   duration_ms=420,
              voice_type="bell", attack_ms=3, decay_ms=70, sustain=0.32, release_ms=320,
              fm_ratio=1.41, fm_index_start=6.0, fm_index_end=0.4, fm_mod_decay_ms=160),
        Voice(NOTE_FREQS["A5"], start_ms=80,  duration_ms=460, velocity=0.95,
              voice_type="bell", attack_ms=3, decay_ms=80, sustain=0.32, release_ms=380,
              fm_ratio=1.41, fm_index_start=5.5, fm_index_end=0.4, fm_mod_decay_ms=170),
        # The answering bell — an octave above, lower velocity.
        Voice(NOTE_FREQS["E6"], start_ms=260, duration_ms=520, velocity=0.7,
              voice_type="bell", attack_ms=4, decay_ms=80, sustain=0.28, release_ms=420,
              fm_ratio=1.41, fm_index_start=4.5, fm_index_end=0.3, fm_mod_decay_ms=180),
        Voice(NOTE_FREQS["A6"], start_ms=320, duration_ms=440, velocity=0.5,
              voice_type="shimmer", attack_ms=12, decay_ms=120, sustain=0.5, release_ms=300),
        # Pad behind everything for cohesion.
        Voice(NOTE_FREQS["A4"], start_ms=20, duration_ms=560, velocity=0.16,
              voice_type="pad", attack_ms=80, decay_ms=140, sustain=0.55, release_ms=320),
    ]
    return voices, 1100


def friend_recipe() -> tuple[list[Voice], int]:
    """
    Cmaj7 arpeggio (C5 → E5 → G5 → B5) on a soft FM bell. Warm, social,
    lands in the "pleasant news" zone.
    """
    voices = []
    arp = ["C5", "E5", "G5", "B5"]
    for i, note in enumerate(arp):
        voices.append(
            Voice(NOTE_FREQS[note], start_ms=i * 90, duration_ms=560,
                  voice_type="bell", attack_ms=8, decay_ms=110, sustain=0.30,
                  release_ms=460, velocity=0.9 - i * 0.07,
                  fm_ratio=1.0, fm_index_start=3.0, fm_index_end=0.3, fm_mod_decay_ms=200)
        )
    # Pad chord underneath.
    for note in ("C4", "E4", "G4"):
        voices.append(
            Voice(NOTE_FREQS[note], start_ms=0, duration_ms=900, velocity=0.10,
                  voice_type="pad", attack_ms=120, decay_ms=200, sustain=0.6, release_ms=500)
        )
    return voices, 1300


def meetup_recipe() -> tuple[list[Voice], int]:
    """
    Pentatonic-G ascending arpeggio (G4 C5 E5 G5 D6) — the "you've arrived"
    motif. Last note lingers with a bright shimmer so it feels celebratory.
    """
    pattern = ["G4", "C5", "E5", "G5", "D6"]
    voices = []
    for i, note in enumerate(pattern):
        voices.append(
            Voice(NOTE_FREQS[note], start_ms=i * 110, duration_ms=420,
                  voice_type="bell", attack_ms=4, decay_ms=80, sustain=0.32,
                  release_ms=300, velocity=0.95 - i * 0.05,
                  fm_ratio=1.41, fm_index_start=5.5, fm_index_end=0.4, fm_mod_decay_ms=140)
        )
    # Final shimmer — held longer.
    voices.append(
        Voice(NOTE_FREQS["G6"], start_ms=520, duration_ms=560, velocity=0.55,
              voice_type="shimmer", attack_ms=15, decay_ms=140, sustain=0.5, release_ms=360)
    )
    # Pad anchor at the root.
    voices.append(
        Voice(NOTE_FREQS["G3"], start_ms=0, duration_ms=900, velocity=0.12,
              voice_type="pad", attack_ms=120, decay_ms=180, sustain=0.55, release_ms=420)
    )
    return voices, 1300


def location_recipe() -> tuple[list[Voice], int]:
    """
    Single soft pulse + breath-pad swell. Quiet, ambient, reads as
    "background heads-up" — won't pull users out of whatever they're doing.
    """
    voices = [
        Voice(NOTE_FREQS["A4"], start_ms=0, duration_ms=380, velocity=0.85,
              voice_type="bell", attack_ms=10, decay_ms=120, sustain=0.30, release_ms=300,
              fm_ratio=2.0, fm_index_start=2.5, fm_index_end=0.2, fm_mod_decay_ms=180),
        Voice(NOTE_FREQS["E5"], start_ms=80, duration_ms=380, velocity=0.55,
              voice_type="bell", attack_ms=10, decay_ms=120, sustain=0.28, release_ms=300,
              fm_ratio=2.0, fm_index_start=2.0, fm_index_end=0.2, fm_mod_decay_ms=180),
        Voice(NOTE_FREQS["A4"], start_ms=0, duration_ms=620, velocity=0.18,
              voice_type="pad", attack_ms=120, decay_ms=160, sustain=0.6, release_ms=320),
    ]
    return voices, 900


def general_recipe() -> tuple[list[Voice], int]:
    """
    Classic two-note minor-third ping (E5 → C5) with a touch of pad.
    Catch-all used by GENERAL.
    """
    voices = [
        Voice(NOTE_FREQS["E5"], start_ms=0, duration_ms=420,
              voice_type="bell", attack_ms=4, decay_ms=70, sustain=0.30, release_ms=320,
              fm_ratio=1.41, fm_index_start=4.5, fm_index_end=0.3, fm_mod_decay_ms=160),
        Voice(NOTE_FREQS["C5"], start_ms=140, duration_ms=520, velocity=0.85,
              voice_type="bell", attack_ms=4, decay_ms=80, sustain=0.30, release_ms=420,
              fm_ratio=1.41, fm_index_start=4.0, fm_index_end=0.3, fm_mod_decay_ms=180),
        Voice(NOTE_FREQS["C4"], start_ms=10, duration_ms=560, velocity=0.13,
              voice_type="pad", attack_ms=100, decay_ms=160, sustain=0.55, release_ms=300),
    ]
    return voices, 1000


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
    """`samples` is float32 in [-1, 1]; shape (N,) for mono or (N, 2) stereo."""
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
    mono = schroeder_reverb(mono, mix=0.16, decay=0.55)
    mono = soft_compress(mono, threshold=0.55, ratio=3.0)
    mono = tanh_limiter(mono, drive=1.05)
    mono = fade_out(mono, ms=40.0)
    mono = normalize(mono, target_peak=0.94)

    final = stereo_widen(mono, pre_delay_ms=6.0) if STEREO else mono

    raw_dir = os.path.join("app", "src", "main", "res", "raw")
    os.makedirs(raw_dir, exist_ok=True)
    base = os.path.join(raw_dir, name)

    # Wipe previous variants so Android resource resolution is clean.
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
