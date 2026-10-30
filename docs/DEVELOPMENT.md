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
| `money.json` | `update_money_data.py` | Wiki: money making guides (skill, requirements, profit) |
| `training.json` | `update_training_data.py` | Wiki: training guides' methods by level |
| `slayer.json` | `update_slayer_data.py` | Wiki: Slayer masters, task locations, travel to each place |
| `skill_helpers.json` | `update_skill_helpers.py` | Wiki + RuneLite: Hunter, Runecraft, burn levels, cooking and smithing recipes, Agility, Sailing, fishing spots (every spot type by NPC id) |
| `voices/` | `generate_voice_clips.py` | Piper TTS (see [VOICE_CREDITS.md](../VOICE_CREDITS.md)) |

## Planned: separate plugins

Besides the all-in-one **Snuffy's Multi-Tool**, each feature is published as its own plugin. This
repository is where everything is worked on; `scripts/split_plugins.py` builds each plugin's own repository
from it (see "Publishing the separate plugins" below).

| Plugin | What goes in it | Voice lines (`voices/<voice>/<folder>/`) |
|---|---|---|
| **Snuffy's Skill Tracker** | The sidebar tracker (cards, auto-pause, level-up alerts), the pinned skill overlay, goals, suggested training methods, goal outlines and Track for goal, goal items in inventory/bank, Issues? button; and the skilling helpers: hover cards, timer pies, skilling alerts. Data: `training.json`, `recipes.json`, `money.json`, `skilling.json`, `skill_helpers.json`, `farming*.json` | `skilling` (level-ups, inventory full, tree gone, rock depleted, fishing spot moved, coin pouches, bird houses, batch complete) + `shared` |
| **Snuffy's PvM & Slayer** | PvM HUD, monster hover card, buff timers, health/prayer/combat alerts; Slayer card, task monster outlines, Slayer info on monsters. Data: `monsters.json`, `slayer.json` | `pvm` (buffs, potions, health, prayer, spec, antifire, poison, stamina, ammo, runes, cannon, Slayer task/superior/item) + `shared` |
| **Snuffy's Gemstone Crab** | Crab HUD, outlines, radar and alerts | `gemcrab` (crab moved, about to burrow) + `shared` |
| **Snuffy's Voices** (optional) | One voice and volume for all of Snuffy's plugins | none of its own beyond `shared` |

PvM and Slayer stay one plugin: Slayer uses the same monster data, hover card and buff alerts.

### How the code is split

| Plugin | Plugin class | Feature (what it starts) | Settings (group) | Its state |
|---|---|---|---|---|
| Multi-Tool (all-in-one) | `BradsMultiToolPlugin` | all four below, joined up | `BradsMultiToolConfig` (`bradsmultitool`, extends the three below) | |
| Skill Tracker | `SkillTrackerPlugin` | `SkillTrackerFeature` | `SkillTrackerConfig` (`snuffysskilltracker`) | `SkillTracker` |
| PvM & Slayer | `PvmPlugin` | `PvmFeature` | `PvmConfig` (`snuffyspvm`) | `PvmTracker`, `SlayerTracker` |
| Gemstone Crab | `GemCrabPlugin` | `GemCrabFeature` | `GemCrabConfig` (`snuffysgemcrab`) | `GemCrabTracker` |
| Voices | `VoicesPlugin` | `VoicesFeature` | `VoicesConfig` (`snuffysvoices`) | |

Every plugin also starts `CommonFeature` (`Alerts`, `IdleWarnings`, `PluginFamily`) and uses `CommonConfig`
(voice, chat, idle). `Lifecycle` subscribes, shows and loads each shared piece only once and undoes it all on
stop. `PluginIdentity` says which build is running (settings group, chat tag, family id). Shared pieces used
by more than one plugin: `CombatStyle`, `MonsterData`, `SlayerData`, `GemstoneCrabs`, `HudColors`, the
overlay components.

Rules that keep the split working:
- A feature's classes take their own settings interface (`SkillTrackerConfig`, ...), never
  `BradsMultiToolConfig`, and never another feature's tracker. Where two features work together only in
  the all-in-one (Reset all, the crab card's buff rows, the PvM card stepping aside for skilling cards), the
  all-in-one plugin joins them with a hook.
- RuneLite only shows settings sections an interface declares itself, so the common sections are repeated in
  each plugin's settings and every section in `BradsMultiToolConfig` (`MultiToolConfigTest` checks).
- `PluginWiringTest` checks every plugin can be built (a missing settings binding fails there, not in the
  client), and `split_plugins.py --verify` that each compiles with only its own classes.

### Voice lines

Every plugin ships its own clips (only its folder under `voices/<voice>/`, plus `shared` for the idle and
logout warnings) and keeps the switches for its lines in its own settings sections, so it works alone.
`VoiceAlertService.VoicePlugin` (and `plugin_of()` in `generate_voice_clips.py`) says which folder a clip
belongs to; add new lines there too.

The plugins find each other with RuneLite's `PluginMessage` events (`PluginFamily`, namespace `snuffys`):
each says "hello" on start and "bye" on stop, and the others answer "here". The Voices plugin also sends
"voice-settings" (persona, volume, on/off); while it runs, every plugin speaks with that voice and volume
instead of its own (each plugin's own per-line switches still apply).

## Publishing the separate plugins

`python3 scripts/split_plugins.py --verify` writes each plugin to `build/split/<plugin>/` and compiles it on
its own. For each plugin it takes, starting from its plugin class, every class it refers to (and so on), the
data files those classes load, and its voice folders; renames the package (`com.snuffys.<plugin>`); and adds
the Plugin Hub build files, `runelite-plugin.properties` (from the plugin's `@PluginDescriptor`), the BSD-2
licence and a README.

The **Split plugins** GitHub Action (`.github/workflows/split-plugins.yml`) does that on every push to `main`
and pushes each plugin to its repository:

| Plugin | Repository |
|---|---|
| Snuffy's Multi-Tool | `bburge14/snuffys-multi-tool` |
| Snuffy's Skill Tracker | `bburge14/snuffys-skill-tracker` |
| Snuffy's PvM & Slayer | `bburge14/snuffys-pvm-slayer` |
| Snuffy's Gemstone Crab | `bburge14/snuffys-gemstone-crab` |
| Snuffy's Voices | `bburge14/snuffys-voices` |

It needs a repository secret **SNUFFYS_PUSH_TOKEN**: a fine-grained personal access token with
*Contents: Read and write* on those five repositories (GitHub > Settings > Developer settings > Fine-grained
tokens; then this repository > Settings > Secrets and variables > Actions). Without it the Action still
builds and checks every plugin but pushes nothing. Don't edit the plugin repositories directly; changes there
are overwritten.

Submitting to the Plugin Hub (not done yet): one file per plugin in `runelite/plugin-hub`'s `plugins/`
folder, naming the repository and the commit to build; each release means updating that commit there.
