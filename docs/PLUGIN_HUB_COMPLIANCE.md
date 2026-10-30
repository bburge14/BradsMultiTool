# Plugin Hub & Jagex compliance

Snuffy's Toolbelt is meant to be published on the RuneLite Plugin Hub, so every feature and every line of
code has to pass the Plugin Hub review. This file tracks the rules and where the plugin stands. Check it
before adding a feature, and update it when something changes.

Sources (re-check these before publishing, they change):
- Plugin Hub rules: https://github.com/runelite/plugin-hub (README, "Reviewing")
- Rejected / rolled-back features and forbidden code: https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- Jagex's third-party client guidelines: https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1

Last checked: 2026-10-28.

## Planned split

The Toolbelt will also be published as separate plugins (Snuffy's Skilling Helper, PvM/Slayer, GemCrab
Tracker and an optional Alerts plugin; docs/DEVELOPMENT.md "Planned: separate plugins" lists what goes where). Each part should stay self-contained so it can be lifted into its own plugin:
its own data files, overlay(s) and config section, talking to the rest only through small shared helpers
(voice alerts, chat messages, cursor tooltip).

## Code rules (from the Rejected-or-Rolled-Back-Features page)

| Rule | Status |
|---|---|
| Java only, package not `net.runelite` | OK (`com.bradsmultitool`) |
| No reflection | OK. Farming data is generated from RuneLite's source into `farming_patches.json` instead of reading Time Tracking's internal classes; axe/pickaxe animation lists are written out instead of scanned |
| No JNI/JNA, no running external programs, no downloading code | OK |
| No `Thread.sleep` / `Thread.interrupt` | OK. Voice clips chain on the clip's STOP event |
| No `Desktop.browse` / `LinkBrowser.open` (`LinkBrowser.browse` is allowed) | OK. The Issues? button uses `LinkBrowser.browse` |
| No `java.io` file APIs (resources via `getResourceAsStream` are fine) | OK |
| No `Runtime`, `KeyboardFocusManager`, non-OkHttp network clients | OK (no network use at all) |
| Don't create your own `Gson` / `OkHttpClient` (inject them) | OK. Plugin code uses the injected `Gson` (tests create their own, which isn't shipped) |
| No `Client.menuAction`, `hopToWorld` | OK |
| `Client.runScript` is a "sensitive API": allowed, with extra review when code around it changes | Only through RuneLite's own `Quest.getState` (a read-only quest-status script), on the client thread, after login, after a quest completes and every 5 minutes (`QuestProgress`), to leave out training methods whose quests you haven't done. Nothing that sends anything to the server |
| Don't modify other plugins' configs | OK. Reads the client's idle timeout, and reads (never changes) which plugins are enabled via `PluginManager.getPlugins()` / `isPluginEnabled()` so our timer pies can step aside for a dedicated timer plugin. Matched by class name, no reflection/annotations. Each plugin saves the defaults of its own settings (its own config group only) where nothing is saved yet (`ConfigDefaults`), through generated plain calls rather than reflection |
| Third-party dependencies need hash verification | OK, none |
| Repository licence: BSD 2-Clause | OK. `LICENSE` here and in every generated plugin repository |
| Plugins talking to each other | OK. Only through RuneLite's `PluginMessage` events (`PluginFamily`): no shared code, no reflection, and no reading or changing another plugin's config. Each plugin works alone |

## Feature rules (Jagex guidelines + rejected features)

| Feature | Status / precedent |
|---|---|
| PvM HUD, monster hover (HP, weakness, aggression to you) | OK. Information-only; aggression follows the game's documented rule |
| Buff / health / prayer / spec / poison / antifire alerts | OK. Notifications only, like core plugins |
| Idle logout warning, AFK alert | OK. Notifications only (core Idle Notifier / Logout Timer do the same); nothing prevents logout |
| Gemstone Crab HUD and radar | OK. Plugin Hub has gemstone-crab-timer |
| Slayer task card, missing item warning | OK |
| Task monster outlines (Konar area aware), Slayer info on monster hover/HUD | OK. Core Slayer plugin highlights task monsters too; information only |
| Slayer master card (no task) and task directions (where to kill, how to get there) | OK. Wiki text only; nothing is pathed, clicked or walked for the player |
| Slayer card pictures (master chatheads, task icons, ~90 small PNGs from the wiki, ~0.3MB) | OK. Game imagery as hosted on the wiki, credited in the README; other Plugin Hub plugins bundle game images the same way. Fallback is an in-game item sprite |
| Skilling hover cards (trees, rocks, stalls, pickpocket, fishing, farming) | OK. Information from the wiki and visible game state |
| Resource timer pies (trees, MLM, calcified, Daeyalt, stars, fishing spots, shipwrecks) | OK. Each mirrors an existing Plugin Hub plugin (credited in README) |
| Farming patch hover | OK. Same information as the core Time Tracking plugin |
| Hunter: creature hover, trap pies, bird house hover + ready alert | OK. Core Hunter and Time Tracking plugins show trap timers and bird house times |
| Firemaking fire pies | OK. Plugin Hub has fire-timer |
| Runecraft altar hover, Agility course/shortcut hover, Cooking burn levels | OK. Wiki information; core Agility plugin knows the same shortcuts and courses |
| Best Choice By (XP or GE profit) | OK. Prices from RuneLite's ItemManager (no own HTTP client) |
| Cooking range/fire card (food levels, XP, burn levels), Smithing anvil/furnace cards | OK. Wiki recipe information and your own inventory; no menu or input changes |
| Skilling card instead of monster card when auto retaliate is off | OK. Only chooses which of our own overlays to draw; reads the auto retaliate setting, changes nothing |
| Coin pouch full, batch complete alerts | OK. Notifications only |
| Skill tracker goals and auto-pause | OK. Same as the core XP Tracker |
| Pinned skill overlay (one skill on screen, Unpin via the overlay's own menu) | OK. Like the core XP Tracker's on-screen skill boxes; overlay menu entry only |
| "Track for goal" right-click option on skilling NPCs/objects, and on inventory/bank items (with a submenu of Prayer methods) | OK. Adds a RUNELITE-type menu entry that only changes what we outline (like core NPC Indicators' Tag); never acts in game, never removes or reorders game options |
| Goal items: outline in inventory/bank the materials of the best thing to make for a goal, with a tooltip | OK. Same as core Inventory Tags / Bank Tags style highlighting; reads containers only, never moves or uses items |
| Training Focus (fastest / other / cheapest / money maker) | OK. Wiki guide data and GE prices via ItemManager; suggestions only |
| Suggested training method on goal cards, outline of that method's trees/rocks/stalls/NPCs | OK. Wiki guide text plus object/NPC outlines (like core Object Markers / NPC Indicators); never clicks or moves the player |
| Voice alerts (bundled clips) | OK. Character voices are named after RuneScape characters but are our own synthesized clips, not Jagex audio |
| Skill tracker panel, level-up alerts | OK |
| Tick counter (over head or a movable box) and metronome, incl. Follow Attack Speed | OK. Core Metronome plugin does the tick sounds (and sets the sound effect volume for one sound and restores it, as we do); Plugin Hub has visual metronome / tick counter plugins. Counts ticks only: it never highlights what to click or when, and never acts for the player |
| Slayer card hotkey | OK (a keybind that toggles our own overlay) |

Never add: anything that clicks, types or acts for the player; menu entry removal based on conditions;
boss attack/prayer prediction for high-end PvM; hidden-information reveals; "AFK agility" helpers; PvP
level/skull indicators; anything that modifies or hides core RuneLite UI or other plugins.

## Open questions before publishing

- **Voice clip licences.** Ryan's clips are CC BY-NC-SA 4.0 and L2 Arctic's CC BY-NC 4.0 (their recordings'
  licences; VOICE_CREDITS.md). The plugin is free, so non-commercial use holds, but the Plugin Hub asks for
  BSD-2 and says nothing about bundled sounds under other licences. Ask the Plugin Hub reviewers, or drop
  those two voices from the published build.

- Plugin size: the jar is about 17.5 MB, nearly all voice clips (9 voices: 4 standard, 5 RuneScape-inspired characters; 11 kHz mu-law, level-up
  numbers built from parts; was 35 MB with 11 voices at 16 kHz). Still large for the Plugin Hub: if a
  reviewer objects, ship fewer voices (e.g. the four standard ones) or move the character voices to their
  own plugin. Check before submitting.
- `Executors.newSingleThreadExecutor()` for audio is not forbidden, but reviewers may prefer the
  injected `ScheduledExecutorService`.
- The skilling/farming data files are generated from the OSRS Wiki (CC BY-NC-SA) and RuneLite
  (BSD-2-Clause); both are credited in the README.
- `runelite-plugin.properties` and the build files need the Plugin Hub template layout at submission.
