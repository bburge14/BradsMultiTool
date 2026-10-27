#!/usr/bin/env python3
"""Regenerate the bundled voice alert clips with Piper TTS (https://github.com/rhasspy/piper).

Usage:
    pip install piper-tts numpy
    python3 scripts/generate_voice_clips.py           # only clips that don't exist yet
    python3 scripts/generate_voice_clips.py --force   # re-record everything
    python3 scripts/generate_voice_clips.py --compact-existing   # convert old 16-bit clips

Voice models are downloaded from Hugging Face (rhasspy/piper-voices) into scripts/.piper-models/.
Personas and lines must match VoiceAlertService.VoicePersona / VoiceLine.
"""
import math
import os
import sys
import urllib.request
import wave

import numpy as np
from piper import PiperVoice
from piper.config import SynthesisConfig

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "com", "bradsmultitool", "voices")
MODEL_DIR = os.path.join(os.path.dirname(__file__), ".piper-models")
HF = "https://huggingface.co/rhasspy/piper-voices/resolve/main/"

# persona folder -> Piper voice (language/name/quality); licences are in VOICE_CREDITS.md
PERSONAS = {
    # US voices must use espeak "en-us" in their .onnx.json; several en_US models use British "en"
    "amy": "en/en_US/amy/medium",
    "joe": "en/en_US/joe/medium",
    "mike": "en/en_US/mike/medium",
    "cori": "en/en_GB/cori/high",
    "alba": "en/en_GB/alba/medium",
    "alan": "en/en_GB/alan/medium",
    "northern": "en/en_GB/northern_english_male/medium",
}

# Character voices: a base voice spoken at a different speed (length_scale), then pitch-shifted by
# resampling (which also changes speed) and given an effect. Net speed = pitch / length_scale.
CHARACTERS = {
    "gnome": {"voice": "en/en_GB/cori/high", "length_scale": 1.25, "pitch": 1.5},
    "ogre": {"voice": "en/en_US/joe/medium", "length_scale": 0.9, "pitch": 0.68},
    "wizard": {"voice": "en/en_GB/alan/medium", "length_scale": 1.3, "pitch": 0.93, "vibrato": True, "echo": True},
    "robot": {"voice": "en/en_US/mike/medium", "length_scale": 1.05, "pitch": 0.95, "ring": 55},
}

LINES = {
    "buff_expired": "Buff has run out.",
    "prayer_low": "Prayer is low.",
    "out_of_prayer": "Out of prayer points.",
    "crab_moved": "The crab has moved!",
    "idle_logout": "You are about to be logged out.",
    "crab_low_health": "The crab is about to burrow.",
    "character_idle": "You are idle.",
    "buff_low": "Buff is running out.",
    "health_low": "Health is low.",
    "melee_buff_low": "Melee boost is running out.",
    "melee_buff_expired": "Melee boost has run out.",
    "ranged_buff_low": "Ranged boost is running out.",
    "ranged_buff_expired": "Ranged boost has run out.",
    "magic_buff_low": "Magic boost is running out.",
    "magic_buff_expired": "Magic boost has run out.",
    "defence_buff_low": "Defence boost is running out.",
    "defence_buff_expired": "Defence boost has run out.",
    "spec_ready": "Special attack is ready.",
    "antifire_low": "Antifire is running out.",
    "antipoison_low": "Poison protection is running out.",
    "poisoned": "You have been poisoned.",
    "venomed": "You have been venomed.",
    "stamina_expired": "Stamina has worn off.",
    "ammo_low": "Ammo is running low.",
    "out_of_runes": "You are out of runes.",
    "cannon_low": "Your cannon is running low.",
    "superior_spawned": "A superior has spawned!",
    "task_complete": "Slayer task complete.",
    "slayer_item_missing": "You are missing your slayer item.",
    "inventory_full": "Inventory is full.",
    "tree_gone": "The tree is gone.",
    "rock_depleted": "The rock is depleted.",
    "fishing_spot_moved": "The fishing spot has moved.",
}


def model_path(voice):
    os.makedirs(MODEL_DIR, exist_ok=True)
    base = voice.split("/")
    name = "%s-%s-%s" % (base[1], base[2], base[3])
    path = os.path.join(MODEL_DIR, name + ".onnx")
    for suffix in (".onnx", ".onnx.json"):
        target = path[: -len(".onnx")] + suffix
        if not os.path.exists(target):
            print("Downloading", name + suffix)
            urllib.request.urlretrieve(HF + voice + "/" + name + suffix, target)
    return path


OUT_RATE = 16000


def write_compact_wav(path, samples, rate):
    """Write 16 kHz 8-bit mu-law WAV (G.711, what phone audio uses): about a third the size of 22 kHz
    16-bit PCM and still clear for short spoken alerts. Java Sound decodes it natively."""
    x = samples.astype(np.float64) / 32768.0
    if rate != OUT_RATE:
        # Band-limited resample via FFT so there's no aliasing
        n_out = int(round(len(x) * OUT_RATE / rate))
        spectrum = np.fft.rfft(x)
        keep = n_out // 2 + 1
        x = np.fft.irfft(spectrum[:keep], n_out) * (n_out / len(x))
    x = np.clip(x, -1.0, 1.0)
    data = linear_to_ulaw(np.round(x * 32767).astype(np.int16))
    header = b"RIFF" + (36 + 2 + len(data) + (len(data) & 1)).to_bytes(4, "little") + b"WAVE"
    fmt = (7).to_bytes(2, "little") + (1).to_bytes(2, "little") + OUT_RATE.to_bytes(4, "little") \
        + OUT_RATE.to_bytes(4, "little") + (1).to_bytes(2, "little") + (8).to_bytes(2, "little") + (0).to_bytes(2, "little")
    with open(path, "wb") as f:
        f.write(header + b"fmt " + len(fmt).to_bytes(4, "little") + fmt)
        f.write(b"data" + len(data).to_bytes(4, "little") + data.tobytes())
        if len(data) & 1:
            f.write(b"\x00")


def linear_to_ulaw(pcm):
    """Standard G.711 mu-law encoder for 16-bit samples."""
    bias, clip = 0x84, 32635
    x = pcm.astype(np.int32)
    sign = np.where(x < 0, 0x80, 0)
    x = np.minimum(np.abs(x), clip) + bias
    exponent = np.floor(np.log2(x)).astype(np.int32) - 7
    exponent = np.clip(exponent, 0, 7)
    mantissa = (x >> (exponent + 3)) & 0x0F
    return (~(sign | (exponent << 4) | mantissa) & 0xFF).astype(np.uint8)


def read_pcm(path):
    with wave.open(path) as w:
        return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16), w.getframerate()


def apply_character(samples, rate, spec):
    x = samples.astype(np.float64) / 32768.0
    n_out = int(len(x) / spec["pitch"])
    t = np.arange(n_out) / rate
    pos = np.arange(n_out) * spec["pitch"]
    if spec.get("vibrato"):
        # Wavering old voice: read position wobbles ~5 times a second
        pos = pos + 0.0015 * rate * np.sin(2 * math.pi * 5.0 * t)
    y = np.interp(np.clip(pos, 0, len(x) - 1), np.arange(len(x)), x)
    if spec.get("ring"):
        # Ring modulation: the classic metallic robot sound
        y = 0.35 * y + 0.65 * y * np.sin(2 * math.pi * spec["ring"] * t)
    if spec.get("echo"):
        delay = int(0.09 * rate)
        echoed = np.copy(y)
        echoed[delay:] += 0.35 * y[:-delay]
        y = echoed
    y = y / max(1e-9, np.max(np.abs(y))) * 0.89
    return (y * 32767).astype(np.int16)


def synth_character(piper_voice, text, spec):
    chunks = list(piper_voice.synthesize(text, SynthesisConfig(length_scale=spec["length_scale"])))
    rate = chunks[0].sample_rate
    samples = np.concatenate([c.audio_int16_array for c in chunks])
    return rate, apply_character(samples, rate, spec)


def compact_existing():
    """Convert any 16-bit PCM clips already in OUT_DIR to the compact format (without re-recording)."""
    for root, _, files in os.walk(OUT_DIR):
        for name in files:
            path = os.path.join(root, name)
            try:
                samples, rate = read_pcm(path)
            except wave.Error:
                continue  # already mu-law
            write_compact_wav(path, samples, rate)


def main():
    if "--compact-existing" in sys.argv:
        compact_existing()
        return
    # Piper output varies slightly run to run, so keep existing clips unless asked
    force = "--force" in sys.argv
    for persona, voice in PERSONAS.items():
        folder = os.path.join(OUT_DIR, persona)
        os.makedirs(folder, exist_ok=True)
        missing = {k: t for k, t in LINES.items() if force or not os.path.exists(os.path.join(folder, k + ".wav"))}
        if not missing:
            continue
        piper_voice = PiperVoice.load(model_path(voice))
        for key, text in missing.items():
            chunks = list(piper_voice.synthesize(text))
            samples = np.concatenate([c.audio_int16_array for c in chunks])
            write_compact_wav(os.path.join(folder, key + ".wav"), samples, chunks[0].sample_rate)
        print("Generated", persona, ", ".join(missing))

    for character, spec in CHARACTERS.items():
        folder = os.path.join(OUT_DIR, character)
        os.makedirs(folder, exist_ok=True)
        missing = {k: t for k, t in LINES.items() if force or not os.path.exists(os.path.join(folder, k + ".wav"))}
        if not missing:
            continue
        piper_voice = PiperVoice.load(model_path(spec["voice"]))
        for key, text in missing.items():
            rate, audio = synth_character(piper_voice, text, spec)
            write_compact_wav(os.path.join(folder, key + ".wav"), audio, rate)
        print("Generated", character, ", ".join(missing))


if __name__ == "__main__":
    main()
