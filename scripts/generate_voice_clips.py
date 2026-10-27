#!/usr/bin/env python3
"""Regenerate the bundled voice alert clips with Piper TTS (https://github.com/rhasspy/piper).

Usage:
    pip install piper-tts numpy
    python3 scripts/generate_voice_clips.py           # only clips that don't exist yet
    python3 scripts/generate_voice_clips.py --force   # re-record everything

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


def main():
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
            with wave.open(os.path.join(folder, key + ".wav"), "wb") as wav_file:
                piper_voice.synthesize_wav(text, wav_file)
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
            with wave.open(os.path.join(folder, key + ".wav"), "wb") as wav_file:
                wav_file.setnchannels(1)
                wav_file.setsampwidth(2)
                wav_file.setframerate(rate)
                wav_file.writeframes(audio.tobytes())
        print("Generated", character, ", ".join(missing))


if __name__ == "__main__":
    main()
