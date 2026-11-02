# Development notes

For people working on the plugin. Players only need the [README](../README.md).

- [Plugin Hub compliance](PLUGIN_HUB_COMPLIANCE.md): what the review allows; check it before adding
  features and keep it up to date.
- [Testing checklist](TESTING.md): what to try in game after a change.
- [Feature guide](FEATURES.md): every feature, how to use it and its settings.

## Running a development client

`.\gradlew.bat runBrad` (Windows) or `./gradlew runBrad` launches RuneLite in developer mode with the plugin
loaded; see the README for the first-time setup and logging in with a Jagex Account. `-Pload=` picks the
separate plugins instead (`skilling`, `pvm`, `gemcrab`, `tick`, `planner`, `alerts`, or `separate` for all of them;
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
| `monsters.json` | `update_monster_data.py` | Wiki: HP, defences, weakness, aggression and its conditions, Slayer level, XP and task, max hits and attack speed (`fields` names each column); monsters the wiki has no NPC id for yet are kept by name ("unlisted") |
| `skilling.json` | `update_skilling_data.py` | Wiki: trees, rocks, stalls, pickpocket targets |
| `farming.json` | `update_farming_data.py` | Wiki: crop levels, protection payments, growth times |
| `farming_patches.json` | `update_farming_patches.py` | RuneLite Time Tracking: every patch (compost bins and Hespori too) and its states, and each crop's farming contract value |
| `recipes.json` | `update_recipe_data.py` | Wiki: recipes for the production skills, Firemaking logs, Prayer bones and ashes |
| `money.json` | `update_money_data.py` | Wiki: money making guides (skill, requirements, profit) |
| `training.json` | `update_training_data.py` | Wiki: training guides' methods by level, each with what it trains on (its section's Main links), the guide's first sentences about it, and its table by level (level, item, ingredients, XP) where it has one; where the guide says to do it (links to the wiki's location pages in its list of spots or in "located in"/"at the" sentences, not ones about buying, rewards or requirements); the quests each method and row needs (finished, or "started" where the guide says partial completion; a row's from the item's infobox quest) |
| `slayer.json` | `update_slayer_data.py` | Wiki: Slayer masters, task locations, travel to each place |
| `skill_helpers.json` | `update_skill_helpers.py` | Wiki + RuneLite: Hunter (and Hunters' Rumours: locations, method, pity threshold; `--only hunter rumours` refreshes just those), Runecraft, burn levels, cooking and smithing recipes, Agility (and the Brimhaven Arena's obstacles), Sailing, fishing spots (every spot type by NPC id) |
| `voices/` | `generate_voice_clips.py` | Piper TTS (see [VOICE_CREDITS.md](../VOICE_CREDITS.md)) |
| `icons/skilltracker.png` and `icons/planner.png` (the tracker's and the Planner's sidebar tabs: their logos, in the all-in-one too), `assets/hub/` and `icon.png` (Plugin Hub icons) | `update_icons.py` (needs Pillow) | The logos in `assets/` (MultiTool, Skilling, PvM, GemCrab, TickCounter, Planner, Alerts) |

## Planned: separate plugins

Besides the all-in-one **Snuffy's Toolbelt**, each feature is published as its own plugin. This
repository holds them all: the code is written once in `src/` (which is also the Toolbelt), and
`scripts/split_plugins.py` makes each plugin its own folder under `plugins/` (see "Building the separate plugins"
below).

| Plugin | What goes in it | Voice lines (`voices/<voice>/<folder>/`) |
|---|---|---|
| **Snuffy's Skilling Helper** | The sidebar tracker (cards, auto-pause, level-up alerts), the pinned skill overlay, goals, suggested training methods, goal outlines and Track for goal, goal items in inventory/bank, Issues? button; and the skilling helpers: hover cards (farming patches and bird houses too), timer pies, skilling alerts. Data: `training.json`, `recipes.json`, `money.json`, `skilling.json`, `skill_helpers.json`, `farming*.json` | `skilling` (level-ups, inventory full, tree gone, rock depleted, fishing spot moved, coin pouches, rumour complete, batch complete) + `shared` |
| **Snuffy's PvM/Slayer** | PvM HUD, monster hover card, buff timers, health/prayer/combat alerts; Slayer card, task monster outlines, Slayer info on monsters. Data: `monsters.json`, `slayer.json` | `pvm` (health, prayer, spec, antifire, poison, stamina, ammo, runes, cannon, wrong prayer, unpotted, Slayer task/superior/item/milestone, kill goal) + `potions` (potion buffs running out / run out, shared with the GemCrab Tracker) + `shared` |
| **Snuffy's GemCrab Tracker** | Crab HUD, outlines, radar and alerts, potion buffs and their alerts | `gemcrab` (crab moved, about to burrow) + `potions` + `shared` |
| **Snuffy's Tick Counter** | Tick count over head, tick boxes, tick bar, metronome, click timing (its own plugin so neither the skilling nor the PvM side carries a second copy) | none (game sounds only) |
| **Snuffy's Planner** | Its own sidebar tab: TODAY (dailies, from DailyReminders, which it replaced), WEEKLY (Tears of Guthix, Kingdom of Miscellania, farming contract), TIMERS (the farming patch list and its ready alert, moved from the Skilling Helper; bird houses ready; your own timers), SLAYER (read from the game), DROPS (drop tracker), NOTES & TO-DO. No skill goals (those stay in the Skilling Helper). Data: `farming*.json` | `planner` (dailies, weeklies, timer done, farming patches ready, bird houses ready) + `shared` |
| **Snuffy's Alerts** (optional) | One voice and volume for all of Snuffy's plugins | none of its own beyond `shared` |

PvM and Slayer stay one plugin: Slayer uses the same monster data, hover card and buff alerts.

### How the code is split

| Plugin | Plugin class | Feature (what it starts) | Settings (group) | Its state |
|---|---|---|---|---|
| Toolbelt (all-in-one) | `BradsMultiToolPlugin` | all six below, joined up | `BradsMultiToolConfig` (`bradsmultitool`, extends the five below) | |
| Skilling Helper | `SkillTrackerPlugin` | `SkillTrackerFeature` | `SkillTrackerConfig` (`snuffysskillinghelper`) | `SkillTracker` |
| PvM/Slayer | `PvmPlugin` | `PvmFeature` | `PvmConfig` (`snuffyspvm`) | `PvmTracker`, `SlayerTracker` |
| GemCrab Tracker | `GemCrabPlugin` | `GemCrabFeature` | `GemCrabConfig` (`snuffysgemcrab`) | `GemCrabTracker` |
| Tick Counter | `TickCounterPlugin` | `TickCounterFeature` | `TickConfig` (`snuffystickcounter`) | `TickCounter` |
| Planner | `PlannerPlugin` | `PlannerFeature` | `PlannerConfig` (`snuffysplanner`) | `PlannerChores`, `PlannerTimers`, `FarmingTracker`, `PlannerSlayer`, `DropTracker`, `PlannerNotes`; panel `PlannerPanel` (redrawn through `PlannerView`) |
| Voices | `VoicesPlugin` | `VoicesFeature` | `VoicesConfig` (`snuffysalerts`) | |

Every plugin but the Tick Counter also starts `CommonFeature` (`Alerts`, `IdleWarnings`, `PluginFamily`) and
uses `CommonConfig` (voice, chat, idle); the Tick Counter has no voice or idle warnings, and only shares `InGame`
(past the welcome screen) and `CombatStyle` (for Follow Attack Speed). `Lifecycle` subscribes, shows and loads each shared piece only once and undoes it all on
stop. `PluginIdentity` says which build is running (settings group, chat tag, family id). Shared pieces used
by more than one plugin: `CombatStyle`, `PotionBuffs` (boosts and potion timers, for PvM and the crab card), `MonsterData`, `SlayerData`, `GemstoneCrabs`, `HudColors`, the
overlay components, `SlayerVars` (the Slayer task, points and streak from the game, for PvM's card and the Planner), and
`FarmingData` and `BirdhouseTracker` (the Skilling Helper's hover cards and the Planner's TIMERS; both record what they
see in the shared group, so either plugin alone has it all).

Rules that keep the split working:
- A separate plugin has every feature its part has in the Toolbelt: nothing it shows may depend on another
  plugin running. When two parts have the same thing (potion buffs and their alerts on the PvM HUD and the crab
  card), each has it itself through shared classes (`PotionBuffs`, `PotionAlerts`, `BuffConfig`); with both
  installed separately only one speaks (`PluginFamily.isFirstOf`). Each part's numbers have their own reset.
- Working together is only for things that need both parts: PvM's monster card steps aside when the Skilling
  Helper's card shows the NPC. The Toolbelt joins them directly; separate plugins do it with a `PluginFamily`
  message ("skilling-card", sent before each frame).
- A feature's classes take their own settings interface (`SkillTrackerConfig`, ...), never
  `BradsMultiToolConfig`, and never another feature's tracker; the Toolbelt joins features with a hook only
  where they work together (above).
- RuneLite only shows settings sections an interface declares itself, so the common sections are repeated in
  each plugin's settings and every section in `BradsMultiToolConfig` (`MultiToolConfigTest` checks).
- RuneLite caches settings by their Java method, not by plugin (`ConfigInvocationHandler`), so separate plugins
  inheriting a setting from a shared interface (`CommonConfig`, `BuffConfig`) would all read whichever plugin's
  value was read first. Each plugin's settings interface re-declares the shared settings in a generated block
  (`scripts/generate_config_defaults.py`, its `SHARED` list); `VoiceSyncTest` checks each plugin reads its own.
- Same Voice For All (`VoiceSync`): changing the voice or volume in one plugin sends a "voice-sync" message, and
  the others change their own settings to match; one that starts later takes it on "hello".
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
logout warnings and the run energy alerts, which Agility's own run energy alert also uses) and keeps the switches for its lines in its own settings sections, so it works alone.
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
| Snuffy's Planner | `plugins/planner/` | `snuffys-planner-<version>.jar` |
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

`Publish-Plugins.bat` (`scripts/publish_plugins.py`, repositories in `publish.json`) does that: it copies each
folder to its own repository, pushes, and prepares the plugin-hub pull request. The full plan, setup and
checklist are in docs/ROLLOUT.md.

## Plugin audit

`docs/PLUGIN_AUDIT.md` compares RuneLite's own and popular Plugin Hub plugins with ours, feature by feature, with a
pick list of what to add. Check it (and the compliance doc) before borrowing a feature from another plugin.

## Saved data shared between plugins

Settings live in each plugin's own config group, but skilling data the player builds up is kept in the
Toolbelt's old group `"bradsmultitool"` (`SkillTracker.SHARED_GROUP`) by both the Toolbelt and the Skilling
Helper: goals, goal starts, Track for goal picks, Suggest choices, Prayer methods and the pinned skill (RuneScape
profile keys, plus `pinnedSkill`), as bird houses and compost already were. The Planner keeps its data there too, by
the Toolbelt and the separate Planner alike: farming patches (`farmPatch.*`, the same keys as when the list was in the
Skilling Helper) and the account's growth tick offset (`farmTickOffset*`), and under `planner.*` its dailies and
weeklies (presets' changes and your own, as JSON), what the game last said about each daily, Tears of Guthix, the
kingdom and the contract, your timers, the Slayer task, drops, to-do list and notes (all RuneScape profile keys), and
which lists are folded (`plannerCollapsed.*`). `SkillTracker.moveToSharedGroup`
moves anything the Skilling Helper saved under its own group before this, once, without overwriting.

`training.json` methods carry `xpHour` where the guide's text gives an XP rate ("around 45,000 experience per
hour"; the top of a range), read by `scripts/update_training_data.py` (`xp_rate`).

## The Toolbelt's settings page

`BradsMultiToolConfig` redeclares every section (RuneLite only shows a config's own sections) and names them after
their plugin ("PvM: HUD", "Skilling: Goals"). It also overrides the smaller skills' settings (Thieving, Farming,
Firemaking, Runecraft, Agility, Cooking, Sailing, Smithing) into one "Skilling: Other Skills" section; the keys are
the same, so values carry over between the Toolbelt and the Skilling Helper's own sections. SettingsPanelTest checks
each setting shows once and keeps its plugin's section except those.

