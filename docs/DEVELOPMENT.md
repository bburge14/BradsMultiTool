# Development notes

For people working on the plugin. Players only need the [README](../README.md).

- [Plugin Hub compliance](PLUGIN_HUB_COMPLIANCE.md): what the review allows; check it before adding
  features and keep it up to date.
- [Testing checklist](TESTING.md): what to try in game after a change.
- [Feature guide](FEATURES.md): every feature, how to use it and its settings.

## Running a development client

`.\gradlew.bat runBrad` (Windows) or `./gradlew runBrad` launches RuneLite in developer mode with the plugin
loaded; see the README for the first-time setup and logging in with a Jagex Account. `-Pload=` picks the
separate plugins instead (`skilling`, `pvm`, `gemcrab`, `tick`, `alerts`, or `separate` for all of them;
`BradsMultiToolPluginTest`). They run from `src/` like the Toolbelt, which is the same code the `plugins/`
folders are made from; `split_plugins.py --verify` checks each of those builds on its own. `./gradlew test` runs
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
| `icons/skilltracker.png` (the tracker's sidebar tab: the Skilling logo, in the all-in-one too), `assets/hub/` and `icon.png` (Plugin Hub icons) | `update_icons.py` (needs Pillow) | The logos in `assets/` (MultiTool, Skilling, PvM, GemCrab, Alerts) |

## Planned: separate plugins

Besides the all-in-one **Snuffy's Toolbelt**, each feature is published as its own plugin. This
repository holds them all: the code is written once in `src/` (which is also the Toolbelt), and
`scripts/split_plugins.py` makes each plugin its own folder under `plugins/` (see "Building the separate plugins"
below).

| Plugin | What goes in it | Voice lines (`voices/<voice>/<folder>/`) |
|---|---|---|
| **Snuffy's Skilling Helper** | The sidebar tracker (cards, auto-pause, level-up alerts), the pinned skill overlay, goals, suggested training methods, goal outlines and Track for goal, goal items in inventory/bank, Issues? button; and the skilling helpers: hover cards, timer pies, skilling alerts. Data: `training.json`, `recipes.json`, `money.json`, `skilling.json`, `skill_helpers.json`, `farming*.json` | `skilling` (level-ups, inventory full, tree gone, rock depleted, fishing spot moved, coin pouches, bird houses, batch complete) + `shared` |
| **Snuffy's PvM/Slayer** | PvM HUD, monster hover card, buff timers, health/prayer/combat alerts; Slayer card, task monster outlines, Slayer info on monsters. Data: `monsters.json`, `slayer.json` | `pvm` (buffs, potions, health, prayer, spec, antifire, poison, stamina, ammo, runes, cannon, Slayer task/superior/item) + `shared` |
| **Snuffy's GemCrab Tracker** | Crab HUD, outlines, radar and alerts | `gemcrab` (crab moved, about to burrow) + `shared` |
| **Snuffy's Tick Counter** | Tick count over head, tick boxes, tick bar, metronome, click timing (its own plugin so neither the skilling nor the PvM side carries a second copy) | none (game sounds only) |
| **Snuffy's Alerts** (optional) | One voice and volume for all of Snuffy's plugins | none of its own beyond `shared` |

PvM and Slayer stay one plugin: Slayer uses the same monster data, hover card and buff alerts.

### How the code is split

| Plugin | Plugin class | Feature (what it starts) | Settings (group) | Its state |
|---|---|---|---|---|
| Toolbelt (all-in-one) | `BradsMultiToolPlugin` | all five below, joined up | `BradsMultiToolConfig` (`bradsmultitool`, extends the four below) | |
| Skilling Helper | `SkillTrackerPlugin` | `SkillTrackerFeature` | `SkillTrackerConfig` (`snuffysskillinghelper`) | `SkillTracker` |
| PvM/Slayer | `PvmPlugin` | `PvmFeature` | `PvmConfig` (`snuffyspvm`) | `PvmTracker`, `SlayerTracker` |
| GemCrab Tracker | `GemCrabPlugin` | `GemCrabFeature` | `GemCrabConfig` (`snuffysgemcrab`) | `GemCrabTracker` |
| Tick Counter | `TickCounterPlugin` | `TickCounterFeature` | `TickConfig` (`snuffystickcounter`) | `TickCounter` |
| Voices | `VoicesPlugin` | `VoicesFeature` | `VoicesConfig` (`snuffysalerts`) | |

Every plugin but the Tick Counter also starts `CommonFeature` (`Alerts`, `IdleWarnings`, `PluginFamily`) and
uses `CommonConfig` (voice, chat, idle); the Tick Counter has no voice or idle warnings, and only shares `InGame`
(past the welcome screen) and `CombatStyle` (for Follow Attack Speed). `Lifecycle` subscribes, shows and loads each shared piece only once and undoes it all on
stop. `PluginIdentity` says which build is running (settings group, chat tag, family id). Shared pieces used
by more than one plugin: `CombatStyle`, `PotionBuffs` (boosts and potion timers, for PvM and the crab card), `MonsterData`, `SlayerData`, `GemstoneCrabs`, `HudColors`, the
overlay components.

Rules that keep the split working:
- A separate plugin has every feature its part has in the Toolbelt: nothing it shows may depend on another
  plugin running. When two parts show the same thing (potion buffs on the PvM HUD and the crab card), each
  reads it itself through a shared class (`PotionBuffs`). Joins in the Toolbelt are only for working together
  (Reset all clearing the others' numbers, PvM's monster card stepping aside for a skilling card).
- A feature's classes take their own settings interface (`SkillTrackerConfig`, ...), never
  `BradsMultiToolConfig`, and never another feature's tracker. Where two features work together only in
  the all-in-one (Reset all, the PvM card stepping aside for skilling cards), the
  all-in-one plugin joins them with a hook.
- RuneLite only shows settings sections an interface declares itself, so the common sections are repeated in
  each plugin's settings and every section in `BradsMultiToolConfig` (`MultiToolConfigTest` checks).
- RuneLite also only saves the defaults of settings an interface declares itself, and the settings panel can't
  open with a choice (enum) setting that has nothing saved. So each feature saves its settings' defaults at
  start through `ConfigDefaults` and the generated `*ConfigDefaults` classes: after adding or renaming a setting,
  run `python3 scripts/generate_config_defaults.py` (`SettingsPanelTest` opens every plugin's real settings panel
  on a fresh install and fails on any setting left unsaved). A setting's type must be public: an enum inside a
  settings interface is (`MultiToolConfigTest` checks).
- `PluginWiringTest` checks every plugin can be built (a missing settings binding fails there, not in the
  client), and `split_plugins.py --verify` that each compiles with only its own classes.

### Voice lines

Every plugin ships its own clips (only its folder under `voices/<voice>/`, plus `shared` for the idle and
logout warnings) and keeps the switches for its lines in its own settings sections, so it works alone.
`VoiceAlertService.VoicePlugin` (and `plugin_of()` in `generate_voice_clips.py`) says which folder a clip
belongs to; add new lines there too.

The plugins find each other with RuneLite's `PluginMessage` events (`PluginFamily`, namespace `snuffys`):
each says "hello" on start and "bye" on stop, and the others answer "here". The Alerts plugin also sends
"voice-settings" (persona, volume, on/off); while it runs, every plugin speaks with that voice and volume
instead of its own (each plugin's own per-line switches still apply).

## Building the separate plugins

Each plugin has a folder under `plugins/`, generated from `src/` and committed, so merging into `main` brings
them along:

| Plugin | Folder | Jar |
|---|---|---|
| Snuffy's Toolbelt | `plugins/toolbelt/` | `snuffys-toolbelt-<version>.jar` |
| Snuffy's Skilling Helper | `plugins/skilling-helper/` | `snuffys-skilling-helper-<version>.jar` |
| Snuffy's PvM/Slayer | `plugins/pvm-slayer/` | `snuffys-pvm-slayer-<version>.jar` |
| Snuffy's GemCrab Tracker | `plugins/gemcrab-tracker/` | `snuffys-gemcrab-tracker-<version>.jar` |
| Snuffy's Tick Counter | `plugins/tick-counter/` | `snuffys-tick-counter-<version>.jar` |
| Snuffy's Alerts | `plugins/alerts/` | `snuffys-alerts-<version>.jar` |

Each folder has only what its plugin uses: starting from its plugin class, every class it refers to (and so
on), the data files those classes load, and its voice folders; the package renamed (`com.snuffys.<plugin>`);
and its own build files in the Plugin Hub's layout, `runelite-plugin.properties` (from the plugin's
`@PluginDescriptor`), icon (`icon.png`, from `assets/hub/`), the BSD-2 licence and a README. So each builds on
its own (`gradlew build` in the folder).

- `python3 scripts/split_plugins.py` rewrites the folders after a change in `src/` (never edit them by hand).
- `--verify` also compiles each on its own; `--check` only says whether any folder is out of date.
- `--build` also builds each jar into `releases/` (not committed), named with the version in `build.gradle`.
  Bump that version for a new release.

### For the Plugin Hub later

The Plugin Hub builds a plugin from the root of a repository, at a commit named in a file in
`runelite/plugin-hub`'s `plugins/` folder (`repository=...` and `commit=...`). A `plugins/<folder>` here is
already laid out that way, so publishing one means making that folder the root of a repository (its own, or a
copy of it) and submitting that file. Settle the open questions in docs/PLUGIN_HUB_COMPLIANCE.md first.
