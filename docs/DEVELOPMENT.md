# Development notes

For people working on the plugin. Players only need the [README](../README.md).

- [Plugin Hub compliance](PLUGIN_HUB_COMPLIANCE.md): what the review allows; check it before adding
  features and keep it up to date.
- [Testing checklist](TESTING.md): what to try in game after a change.
- [Feature guide](FEATURES.md): every feature, how to use it and its settings.

## Running a development client

`.\gradlew.bat runBrad` (Windows) or `./gradlew runBrad` launches RuneLite in developer mode with the plugin
loaded; see the README for the first-time setup and logging in with a Jagex Account. `./gradlew test` runs
the unit tests.

Two Gradle tasks render parts of the UI offline (no client needed) to check layouts:

- `./gradlew slayerPreview -Dout=slayer.png`: the Slayer card, with and without a task.
- `./gradlew headerPreview -Dout=header.png`: the skill tracker's header box.

## Data files

Game data is generated from the OSRS Wiki and RuneLite's source by the scripts in `scripts/` (needs
Python 3). Re-run them after a game update rather than editing the JSON by hand.

| File | Script | Source |
|---|---|---|
| `monsters.json` | `update_monster_data.py` | Wiki: HP, defences, weakness, aggression and its conditions |
| `skilling.json` | `update_skilling_data.py` | Wiki: trees, rocks, stalls, pickpocket targets |
| `farming.json` | `update_farming_data.py` | Wiki: crop levels, protection payments, growth times |
| `farming_patches.json` | `update_farming_patches.py` | RuneLite Time Tracking: every patch and its states |
| `recipes.json` | `update_recipe_data.py` | Wiki: recipes for the production skills, Firemaking logs, Prayer bones and ashes |
| `training.json` | `update_training_data.py` | Wiki: training guides' methods by level |
| `slayer.json` | `update_slayer_data.py` | Wiki: Slayer masters, task locations, travel to each place |
| `skill_helpers.json` | `update_skill_helpers.py` | Wiki + RuneLite: Hunter, Runecraft, burn levels, cooking and smithing recipes, Agility, Sailing |
| `voices/` | `generate_voice_clips.py` | Piper TTS (see [VOICE_CREDITS.md](../VOICE_CREDITS.md)) |

## Planned: separate plugins

The multi-tool will also be split into stand-alone plugins alongside the all-in-one version. New features
are kept self-contained with that in mind.

| Plugin | What goes in it |
|---|---|
| **Brad's Skill Tracker** | The sidebar tracker (cards, auto-pause, level-up alerts), the pinned skill overlay, goals, suggested training methods, goal outlines and Track for goal, goal items in inventory/bank, Issues? button. Data: `training.json`, `recipes.json` |
| Skilling helpers | Hover cards, timer pies, skilling alerts (`skilling.json`, `skill_helpers.json`, `farming*.json`) |
| PvM | PvM HUD, monster hover card, buff timers, health/prayer/combat alerts (`monsters.json`) |
| Slayer | Slayer card, task monster outlines, Slayer info on monsters (`slayer.json`) |
| Gemstone Crab | Crab HUD, outlines, radar and alerts |
