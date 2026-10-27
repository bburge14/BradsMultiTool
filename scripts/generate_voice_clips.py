#!/usr/bin/env python3
"""Regenerate the bundled voice alert clips with Piper TTS (https://github.com/rhasspy/piper).

Usage:
    pip install piper-tts
    python3 scripts/generate_voice_clips.py           # only clips that don't exist yet
    python3 scripts/generate_voice_clips.py --force   # re-record everything

Voice models are downloaded from Hugging Face (rhasspy/piper-voices) into scripts/.piper-models/.
Personas and lines must match VoiceAlertService.VoicePersona / VoiceLine.
"""
import os
import sys
import urllib.request
import wave

from piper import PiperVoice

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "com", "bradsmultitool", "voices")
MODEL_DIR = os.path.join(os.path.dirname(__file__), ".piper-models")
HF = "https://huggingface.co/rhasspy/piper-voices/resolve/main/"

# persona folder -> Piper voice (language/name/quality); licences are in VOICE_CREDITS.md
PERSONAS = {
    "amy": "en/en_US/amy/medium",
    "kristin": "en/en_US/kristin/medium",
    "cori": "en/en_GB/cori/high",
    "alba": "en/en_GB/alba/medium",
    "norman": "en/en_US/norman/medium",
    "alan": "en/en_GB/alan/medium",
    "northern": "en/en_GB/northern_english_male/medium",
}

LINES = {
    "buff_expired": "Buff has run out.",
    "prayer_low": "Prayer is low.",
    "out_of_prayer": "Out of prayer points.",
    "crab_moved": "The crab has moved!",
    "idle_logout": "You are about to be logged out.",
    "crab_low_health": "The crab is about to burrow.",
    "character_idle": "You are idle.",
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


if __name__ == "__main__":
    main()
