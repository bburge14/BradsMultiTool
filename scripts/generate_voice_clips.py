#!/usr/bin/env python3
"""Regenerate the bundled voice alert clips with Piper TTS (https://github.com/rhasspy/piper).

Usage:
    pip install piper-tts numpy
    python3 scripts/generate_voice_clips.py           # only clips that don't exist yet
    python3 scripts/generate_voice_clips.py --force   # re-record everything
    python3 scripts/generate_voice_clips.py --compact-existing   # convert old 16-bit clips
    python3 scripts/generate_voice_clips.py --trim-existing      # cut silence off existing clips

Voice models are downloaded from Hugging Face (rhasspy/piper-voices) into scripts/.piper-models/.
Personas and lines must match VoiceAlertService.VoicePersona / VoiceLine.
"""
import json
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
# Standard voices (their own accents, no effects); John and the rest are characters below
PERSONAS = {
    # US voices must use espeak "en-us" in their .onnx.json; several en_US models use British "en"
    "amy": "en/en_US/amy/medium",
    "ryan": "en/en_US/ryan/high",
    # Piper's Joe voice as recorded (it already sounds like a certain armoured Spartan)
    "john": "en/en_US/joe/medium",
    # Multi-speaker models: (model, speaker); the demo page's first speaker of each
    "arctic": ("en/en_US/arctic/medium", "awb"),      # Scottish man
    "l2arctic": ("en/en_US/l2arctic/medium", "TXHC"),  # Mandarin Chinese-accented man
    "cori": "en/en_GB/cori/high",
    "alan": "en/en_GB/alan/medium",
}

# Character voices inspired by RuneScape characters (not their real voices): a base voice spoken at a
# different speed (length_scale), then pitch-shifted by resampling (which also changes speed) and given an
# effect. Net speed = pitch / length_scale.
CHARACTERS = {
    "wise_old_man": {"voice": "en/en_GB/alan/medium", "length_scale": 1.3, "pitch": 0.9, "vibrato": True, "echo": True},
    "tzhaar": {"voice": "en/en_US/joe/medium", "length_scale": 0.85, "pitch": 0.72, "growl": 1.6},
    "ghost": {"voice": "en/en_US/amy/medium", "length_scale": 1.35, "pitch": 1.05, "vibrato": True, "echo": True,
              "long_echo": True},
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
    "task_halfway": "Slayer task halfway done.",
    "task_nearly_done": "Slayer task nearly done.",
    "slayer_item_missing": "You are missing your slayer item.",
    "inventory_full": "Inventory is full.",
    "tree_gone": "The tree has fallen.",
    "rock_depleted": "The rock is depleted.",
    "fishing_spot_moved": "The fishing spot has moved.",
    "potion_super_combat_low": "Super combat is running out.",
    "potion_super_combat_expired": "Super combat has run out.",
    "potion_combat_low": "Combat potion is running out.",
    "potion_combat_expired": "Combat potion has run out.",
    "potion_attack_low": "Attack potion is running out.",
    "potion_attack_expired": "Attack potion has run out.",
    "potion_strength_low": "Strength potion is running out.",
    "potion_strength_expired": "Strength potion has run out.",
    "potion_defence_low": "Defence potion is running out.",
    "potion_defence_expired": "Defence potion has run out.",
    "potion_ranging_low": "Ranging potion is running out.",
    "potion_ranging_expired": "Ranging potion has run out.",
    "potion_magic_low": "Magic potion is running out.",
    "potion_magic_expired": "Magic potion has run out.",
    "potion_bastion_low": "Bastion potion is running out.",
    "potion_bastion_expired": "Bastion potion has run out.",
    "potion_battlemage_low": "Battlemage potion is running out.",
    "potion_battlemage_expired": "Battlemage potion has run out.",
    "potion_divine_super_combat_low": "Divine super combat is running out.",
    "potion_divine_super_combat_expired": "Divine super combat has run out.",
    "potion_divine_bastion_low": "Divine bastion is running out.",
    "potion_divine_bastion_expired": "Divine bastion has run out.",
    "potion_divine_battlemage_low": "Divine battlemage is running out.",
    "potion_divine_battlemage_expired": "Divine battlemage has run out.",
    "potion_divine_attack_low": "Divine attack is running out.",
    "potion_divine_attack_expired": "Divine attack has run out.",
    "potion_divine_strength_low": "Divine strength is running out.",
    "potion_divine_strength_expired": "Divine strength has run out.",
    "potion_divine_defence_low": "Divine defence is running out.",
    "potion_divine_defence_expired": "Divine defence has run out.",
    "potion_divine_ranging_low": "Divine ranging is running out.",
    "potion_divine_ranging_expired": "Divine ranging has run out.",
    "potion_divine_magic_low": "Divine magic is running out.",
    "potion_divine_magic_expired": "Divine magic has run out.",
    "coin_pouch_full": "Your coin pouches are full.",
    "birdhouses_ready": "Your bird houses are ready.",
    "batch_complete": "Batch complete.",
}

# Level-up alerts: "<Skill> level" then the number, built from parts to keep the plugin small: 2-19 and
# round tens ("Forty!") are single clips, the rest a tens prefix then a unit ("forty" + "five!")
SKILLS = ["Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
          "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility",
          "Thieving", "Slayer", "Farming", "Runecraft", "Hunter", "Construction", "Sailing"]
for _skill in SKILLS:
    LINES["skill_" + _skill.lower()] = _skill + " level"
_UNITS = ["", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
          "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"]
_TENS = {20: "Twenty", 30: "Thirty", 40: "Forty", 50: "Fifty", 60: "Sixty", 70: "Seventy", 80: "Eighty", 90: "Ninety"}
for _n in range(1, 20):
    LINES["num_%d" % _n] = _UNITS[_n] + "!"
for _n, _word in _TENS.items():
    LINES["num_%d" % _n] = _word + "!"
    LINES["tens_%d" % _n] = _word


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


OUT_RATE = 11025


def write_compact_wav(path, samples, rate):
    """Write 11 kHz 8-bit mu-law WAV (G.711, what phone audio uses): about a quarter the size of 22 kHz
    16-bit PCM and still clear for short spoken alerts. Java Sound decodes it natively."""
    x = trim_silence(samples.astype(np.float64) / 32768.0, rate)
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


def trim_silence(x, rate, threshold=0.01, pad=0.04):
    """Cut leading/trailing silence (keeping a short pad), so clips played back to back flow together."""
    loud = np.nonzero(np.abs(x) > threshold)[0]
    if len(loud) == 0:
        return x
    margin = int(pad * rate)
    return x[max(0, loud[0] - margin):min(len(x), loud[-1] + margin)]


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
    if spec.get("growl"):
        # Soft clipping: a gritty, distorted edge
        y = np.tanh(spec["growl"] * y)
    if spec.get("radio"):
        # Helmet radio: only the 300-3000 Hz band, a little static
        spectrum = np.fft.rfft(y)
        freqs = np.fft.rfftfreq(len(y), 1.0 / rate)
        spectrum[(freqs < 300) | (freqs > 3000)] = 0
        y = np.fft.irfft(spectrum, len(y))
        y = y / max(1e-9, np.max(np.abs(y)))
        y = np.tanh(1.8 * y) + np.random.default_rng(117).normal(0, 0.012, len(y))
    if spec.get("tremolo"):
        # A quavering, creaky old voice: volume wobbling ~7 times a second
        y = y * (1 - spec["tremolo"] * (0.5 + 0.5 * np.sin(2 * math.pi * 7.0 * t)))
    if spec.get("arena"):
        # A big hall: several fading echoes
        hall = np.copy(y)
        for seconds, gain in ((0.11, 0.42), (0.23, 0.28), (0.37, 0.17), (0.55, 0.09)):
            delay = int(seconds * rate)
            hall[delay:] += gain * y[:-delay]
        y = hall
    if spec.get("echo"):
        delay = int(0.09 * rate)
        echoed = np.copy(y)
        echoed[delay:] += 0.35 * y[:-delay]
        y = echoed
    if spec.get("long_echo"):
        # A second, later and softer echo: hollow and ghostly
        delay = int(0.22 * rate)
        echoed = np.copy(y)
        echoed[delay:] += 0.3 * y[:-delay]
        y = echoed
    y = y / max(1e-9, np.max(np.abs(y))) * 0.89
    return (y * 32767).astype(np.int16)


def synth_character(piper_voice, text, spec):
    chunks = list(piper_voice.synthesize(text, SynthesisConfig(length_scale=spec["length_scale"])))
    rate = chunks[0].sample_rate
    samples = np.concatenate([c.audio_int16_array for c in chunks])
    return rate, apply_character(samples, rate, spec)


def ulaw_to_linear(codes):
    """Standard G.711 mu-law decoder (inverse of linear_to_ulaw)."""
    u = ~codes.astype(np.int32) & 0xFF
    sign = u & 0x80
    exponent = (u >> 4) & 0x07
    mantissa = u & 0x0F
    x = (((mantissa << 3) + 0x84) << exponent) - 0x84
    return np.where(sign != 0, -x, x).astype(np.int16)


def trim_existing():
    """Trim leading/trailing silence from every clip already in OUT_DIR."""
    for root, _, files in os.walk(OUT_DIR):
        for name in files:
            path = os.path.join(root, name)
            with open(path, "rb") as f:
                raw = f.read()
            start = raw.find(b"data") + 8
            length = int.from_bytes(raw[start - 4:start], "little")
            samples = ulaw_to_linear(np.frombuffer(raw[start:start + length], dtype=np.uint8))
            write_compact_wav(path, samples, OUT_RATE)


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


# Which separate plugin each clip ships with: voices/<persona>/<plugin>/<clip>.wav, so each plugin's build
# takes just its folder. Keep in step with VoiceAlertService.VoicePlugin.of()
SKILLING_LINES = {"inventory_full", "tree_gone", "rock_depleted", "fishing_spot_moved", "coin_pouch_full",
                  "birdhouses_ready", "batch_complete"}


def plugin_of(key):
    if key.startswith(("skill_", "num_", "tens_")) or key in SKILLING_LINES:
        return "skilling"
    if key.startswith(("potion_", "buff_")) or key.endswith(("_buff_low", "_buff_expired")):
        return "potions"
    if key in ("crab_moved", "crab_low_health"):
        return "gemcrab"
    if key in ("character_idle", "idle_logout"):
        return "shared"
    return "pvm"


def clip_path(folder, key):
    return os.path.join(folder, plugin_of(key), key + ".wav")


def main():
    if "--compact-existing" in sys.argv:
        compact_existing()
        return
    if "--trim-existing" in sys.argv:
        trim_existing()
        return
    # Piper output varies slightly run to run, so keep existing clips unless asked
    force = "--force" in sys.argv
    for persona, voice in PERSONAS.items():
        folder = os.path.join(OUT_DIR, persona)
        os.makedirs(folder, exist_ok=True)
        missing = {k: t for k, t in LINES.items() if force or not os.path.exists(clip_path(folder, k))}
        if not missing:
            continue
        model, speaker = voice if isinstance(voice, tuple) else (voice, None)
        piper_voice = PiperVoice.load(model_path(model))
        config = SynthesisConfig()
        if speaker:
            with open(model_path(model) + ".json") as f:
                config = SynthesisConfig(speaker_id=json.load(f)["speaker_id_map"][speaker])
        for key, text in missing.items():
            chunks = list(piper_voice.synthesize(text, config))
            samples = np.concatenate([c.audio_int16_array for c in chunks])
            os.makedirs(os.path.dirname(clip_path(folder, key)), exist_ok=True)
            write_compact_wav(clip_path(folder, key), samples, chunks[0].sample_rate)
        print("Generated", persona, ", ".join(missing))

    for character, spec in CHARACTERS.items():
        folder = os.path.join(OUT_DIR, character)
        os.makedirs(folder, exist_ok=True)
        missing = {k: t for k, t in LINES.items() if force or not os.path.exists(clip_path(folder, k))}
        if not missing:
            continue
        piper_voice = PiperVoice.load(model_path(spec["voice"]))
        for key, text in missing.items():
            rate, audio = synth_character(piper_voice, text, spec)
            os.makedirs(os.path.dirname(clip_path(folder, key)), exist_ok=True)
            write_compact_wav(clip_path(folder, key), audio, rate)
        print("Generated", character, ", ".join(missing))


if __name__ == "__main__":
    main()
