# Voice credits

The spoken alerts in `src/main/resources/com/bradsmultitool/voices/` were generated with
[Piper](https://github.com/rhasspy/piper) using these voices from
[rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices). Regenerate them with
`scripts/generate_voice_clips.py`.

| Persona | Piper voice | Training data licence |
|---|---|---|
| Amy (US female) | en_US-amy-medium | See the [mimic3-voices](https://github.com/MycroftAI/mimic3-voices) model card |
| John (US male), and TzHaar's base voice | en_US-joe-medium | [CC0](https://creativecommons.org/publicdomain/zero/1.0/) |
| Cori (UK female) | en_GB-cori-high | Public domain (LibriVox) |
| Alan (UK male) | en_GB-alan-medium | See the [mimic3-voices](https://github.com/MycroftAI/mimic3-voices) model card |
| Arctic (Scottish male) | en_US-arctic-medium, speaker awb | [CMU ARCTIC](http://www.festvox.org/cmu_arctic/) (Carnegie Mellon University; free to use with its copyright notice) |

Character voices are the voices above with pitch, speed and effects applied by the generator. They are
inspired by characters (they are not anyone's real voice).

| Character | Based on | Effect |
|---|---|---|
| Wise Old Man | Alan | Slower, slightly lower, wavering, light echo |
| TzHaar | Joe | Pitched down, brisk, rough edge |
| Ghost | Amy | Slow, wavering, hollow double echo |
