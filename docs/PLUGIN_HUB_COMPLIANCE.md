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
Tracker, Tick Counter, Planner and an optional Alerts plugin; docs/DEVELOPMENT.md "Planned: separate plugins" lists what goes where). Each part should stay self-contained so it can be lifted into its own plugin:
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
| Don't create your own `Gson` / `OkHttpClient` (inject them) | OK. Plugin code uses the injected `Gson` (tests create their own, which isn't shipped); the Planner saves its lists as JSON with it, into ConfigManager (no `TypeToken`: plain holder classes) |
| No `Client.menuAction`, `hopToWorld` | OK |
| `Client.runScript` is a "sensitive API": allowed, with extra review when code around it changes | Only through RuneLite's own `Quest.getState` (a read-only quest-status script), on the client thread, after login, after a quest completes and every 5 minutes (`QuestProgress`), to leave out training methods whose quests you haven't done. Nothing that sends anything to the server |
| Don't modify other plugins' configs | OK. Reads the client's idle timeout, and reads (never changes) which plugins are enabled via `PluginManager.getPlugins()` / `isPluginEnabled()` so our resource timers can step aside for a dedicated timer plugin, and reads (never writes) settings of RuneLite's own Fishing and Motherlode Mine plugins (`fishing.showIcons`, `fishing.showTiles`, `fishing.onlyCurrent`, `fishing.showMinnowOverlay`, `motherlode.showVeins`), and whether its Mining plugin is on (it always times depleted rocks and Daeyalt), through `ConfigManager.getConfiguration` so our tile style leaves out an icon or outline those already draw. Matched by class name, no reflection/annotations. Each plugin saves the defaults of its own settings (its own config group only) where nothing is saved yet (`ConfigDefaults`), through generated plain calls rather than reflection |
| Third-party dependencies need hash verification | OK, none |
| runelite-plugin.properties has `build=standard` (the Plugin Hub rejects a plugin without a build type) | OK. Written by split_plugins.py |
| Repository licence: BSD 2-Clause | OK. `LICENSE` here and in every generated plugin repository |
| Plugins talking to each other | OK. Only through RuneLite's `PluginMessage` events (`PluginFamily`): hello/here/bye, the Alerts plugin's voice settings, the voice and volume for Same Voice For All (each plugin then changes only its own settings), and which NPC the Skilling Helper's card shows (so PvM's card steps aside). No shared code, no reflection, and no reading or changing another plugin's config. Each plugin works alone with all its features |

## Feature rules (Jagex guidelines + rejected features)

| Feature | Status / precedent |
|---|---|
| PvM HUD, monster hover (HP, weakness, aggression to you) | OK. Information-only; aggression follows the game's documented rule |
| Buff / health / prayer / spec / poison / antifire alerts | OK. Notifications only, like core plugins |
| Idle logout warning, AFK alert | OK. Notifications only (core Idle Notifier / Logout Timer do the same); nothing prevents logout |
| Loot value on the PvM HUD, materials value on goal cards | OK. RuneLite's `NpcLootReceived` and ItemManager GE prices, as the core Loot Tracker; figures only |
| Death alert and gravestone timer | OK. Same as the old core Death Indicator: reads your death (`ActorDeath`), location and item counts; an InfoBox of our own; nothing acts for the player |
| Clue drop alert | OK. Clue items by name from kill loot and your inventory; notifications only |
| Planner: TODAY dailies checklist | OK. Your own dailies, ticked by hand; one generic voice line at login/reset |
| Planner: WEEKLY (Tears of Guthix, Kingdom of Miscellania, farming contract) | OK. Tears of Guthix from the game's own collecting varbit going back to 0 (when your game ends) plus the wiki's "start of the seventh day after" rule; kingdom approval/coffer the way core Kingdom of Miscellania reads them; the contract the way core Time Tracking reads it. Information only |
| Planner: TIMERS (farming patches incl. compost bins and Hespori, bird houses, your own timers) | OK. Same information as core Time Tracking (patch varbits read with its guards: two ticks in one region, no modal interface open; the growth tick offset learnt the same way); own timers are a countdown the player starts, with a notification |
| Planner: SLAYER | OK. The varps/varbits and game tables core Slayer reads; information only |
| Planner: DROPS (drop tracker) | OK. Like core Loot Tracker: `NpcLootReceived`, and `LootReceived` for chests/caskets/raids (posted by core Loot Tracker; its NPC and player records are skipped so kills aren't counted twice); GE prices via ItemManager; saved with ConfigManager (RS profile), this session's totals in memory only. Per monster: items, times dropped (drop rate seen), kills since a valuable drop, best drop; figures only, like core Loot Tracker's boxes. Clash note while core Loot Tracker is on |
| Planner: Notes (to-do and notes) | OK. Text the player types, saved with ConfigManager (RS profile); no files |
| Planner: CALCULATOR (skill calculator) | OK. The same information as RuneLite's own Skill Calculator (its data converted into `calculator.json` by a script, not read from its classes, with each action's members-only flag as its `isMembers` says: the item's members flag from the wiki): XP from `Client.getSkillExperience`, items from the inventory container and the bank as last opened (`ItemContainerChanged`, read on the client thread), whether you're on a members world (`Client.getWorldType()`, as RuneLite's calculator hides members actions on free worlds), GE prices via ItemManager (by name, and by item id with `getItemPrice` on the client thread), spell icons via `SpriteManager.getSpriteAsync`. Set as goal only writes our own goal keys (ConfigManager, RS profile). Information only |
| Planner: skill plans and Export to tracker | OK. A plan (goal and milestones) the player builds, saved as plain text in our own shared group (ConfigManager, RS profile, `SkillPlans`); Export to tracker only writes our own goal and plan keys, offered only when `PluginFamily` says the Skilling Helper or Toolbelt runs. The tracker reads the plan from the shared group (no Planner classes): its milestone shows as a bar and suggestion, and its method is outlined like any suggestion (objects/NPCs, never clicked). Information only |
| Planner sidebar (tabs, cards, item pictures) | OK. Plain Swing with RuneLite's own `MaterialTabGroup`/`ColorScheme`/`FontManager`; item pictures from `ItemManager.getImage` (filled in on the Swing thread when loaded), skill icons from `SkillIconManager`; everything drawn on the Swing thread. Clicking only changes our own lists. The offline preview (`PlannerPreview`, test code, not shipped) reads ConfigManager's fields reflectively and fetches wiki pictures |
| Gemstone Crab HUD and radar | OK. Plugin Hub has gemstone-crab-timer |
| Slayer task card, missing item warning | OK |
| Task monster outlines (Konar area aware), Slayer info on monster hover/HUD | OK. Core Slayer plugin highlights task monsters too; information only |
| Slayer points and streak on the card, "next task is a milestone" alert | OK. The same varbits core Slayer shows in its infobox tooltip; information only. Nothing hides or reorders masters or NPC Contact options (Slayer Streak's menu hiding is on the never-add list) |
| Slayer supplies used (food, doses) per task | OK. Counted from the game's own eat/drink chat messages; saved with ConfigManager (RS profile), no files |
| Kill goals per monster ("Set kill goal" right-click, chat box number input) | OK. RUNELITE-type menu entry like Track for goal and RuneLite's chat box input (ChatboxPanelManager, as Bank Tags uses); only changes what we show; saved with ConfigManager (RS profile) |
| Finish-off marker on lizards, gargoyles, rockslugs, zygomites | OK. Core Slayer's "Show monster weakness" does the same (its thresholds); only draws the item, never uses it. Steps aside for core's own marks (reads whether core Slayer and that setting are on, never changes them) |
| Monster max hit and attack speed on the hover card | OK. Static wiki infobox facts (like Slayer Suite / npc-max-hit on the Hub). No countdown to a monster's next attack |
| Wrong protection prayer alert (off by default) | OK. Only for monsters whose every wiki attack style one prayer stops, compared with the prayer you have on; says nothing about when or how it attacks next (not boss attack prediction) |
| Unpotted reminder (off by default) | OK. Notification only: reads your boosted stats when you start a fight |
| Buff bar: overload, prayer regeneration, vengeance, staff of the dead, imbued heart cooldown, frozen, teleblocked | OK. The same varbits and game messages core Timers & Buffs reads; information only. No clash entry: core shows these as infoboxes by default, and the buff bar already overlaps it (antifire, stamina) as an alternative layout |
| Slayer master card (no task) and task directions (where to kill, how to get there) | OK. Wiki text only; nothing is pathed, clicked or walked for the player |
| Slayer card pictures (master chatheads, task icons, ~90 small PNGs from the wiki, ~0.3MB) | OK. Game imagery as hosted on the wiki, credited in the README; other Plugin Hub plugins bundle game images the same way. Fallback is an in-game item sprite |
| Skilling hover cards (trees, rocks, stalls, pickpocket, fishing, farming) | OK. Information from the wiki and visible game state |
| Resource timer pies (trees, MLM, calcified, Daeyalt, stars, fishing spots, shipwrecks) | OK. Each mirrors an existing Plugin Hub plugin (credited in README) |
| Depleted rock respawn timers | OK. Same as RuneLite's own Mining plugin: reads the arguments of the game's rock respawn script (5474) in `ScriptPreFired` (the rock's tile, the depleted rock's id and the ticks until it's back); only reads them, never runs a script |
| Farming patch hover | OK. Same information as the core Time Tracking plugin |
| Hunter: creature hover, trap pies, bird house hover + ready alert | OK. Core Hunter and Time Tracking plugins show trap timers and bird house times. A trap that sprang empty gets its own colour, as core Hunter colours empty traps |
| Hunters' Rumours card (creature, catches toward the pity drop, where to find it) and rumour complete alert | OK. Same as the Plugin Hub's Hunter Rumours plugin: reads the guild hunters' dialogue, the Quetzal whistle message, the rare part's chat message and your Hunter XP; wiki locations. Leaves out that plugin's fairy ring auto-scroll (it changes an interface for you) |
| Skills tab goal bars | OK. An overlay drawn over the Skills tab's widgets (bounds only), like the Plugin Hub's Skills Progress plugin; the interface isn't changed |
| Items gathered per hour, keeping the tracker session after a restart | OK. Counts the game's own chat messages, as core Woodcutting/Fishing/Cooking plugins do; the session is saved in our own RS profile config, as core XP Tracker keeps its session |
| Firemaking fire pies | OK. Plugin Hub has fire-timer |
| Runecraft altar hover, Agility course/shortcut hover, Cooking burn levels | OK. Wiki information; core Agility plugin knows the same shortcuts and courses |
| Brimhaven Arena obstacle colours, route to the tagged pillar, one-obstacle rep counter (arena and shortcuts) | OK. Information only: outlines obstacles from the loaded scene by your level (wiki data) and draws a route line, like core Agility's clickboxes and the Shortest Path plugin; reads the hint arrow, never clicks or walks for you. Steps aside for obstacles core Agility's Show Clickboxes already outlines |
| Best Choice By (XP or GE profit) | OK. Prices from RuneLite's ItemManager (no own HTTP client) |
| Cooking range/fire card (food levels, XP, burn levels), Smithing anvil/furnace cards | OK. Wiki recipe information and your own inventory; no menu or input changes |
| Skilling card instead of monster card when auto retaliate is off | OK. Only chooses which of our own overlays to draw; reads the auto retaliate setting, changes nothing |
| Coin pouch full, batch complete alerts | OK. Notifications only |
| Minigame cards and alerts (Wintertodt, Tempoross, Guardians of the Rift) and Sailing boat alerts (low boat health, trim sails) | OK. Read the game's own HUD boxes, chat, varbits and inventory; core Wintertodt and the Tempoross, GOTR Helper and Sailing hub plugins notify the same things. GOTR altars are shown on our card only: no menu options are hidden, swapped or reordered |
| Skill tracker goals and auto-pause | OK. Same as the core XP Tracker. Goals, Track for goal picks, Suggest choices, Prayer methods and the pinned skill (like bird houses and compost) are saved under one group, "bradsmultitool", for both the Toolbelt and the Skilling Helper (our own plugins, so either shows the same goals); the Skilling Helper moves goals it saved under its own group there once |
| Pinned skill overlay (one skill on screen, Unpin via the overlay's own menu) | OK. Like the core XP Tracker's on-screen skill boxes; overlay menu entry only |
| "Track for goal" right-click option on skilling NPCs/objects, and on inventory/bank items (with a submenu of Prayer methods) | OK. Adds a RUNELITE-type menu entry that only changes what we outline (like core NPC Indicators' Tag); never acts in game, never removes or reorders game options |
| Goal items: outline in inventory/bank the materials of the best thing to make for a goal, with a tooltip | OK. Same as core Inventory Tags / Bank Tags style highlighting; reads containers only, never moves or uses items |
| Training Focus (fastest / other / cheapest / money maker) | OK. Wiki guide data and GE prices via ItemManager; suggestions only |
| Suggested training method on goal cards, outline of that method's trees/rocks/stalls/NPCs | OK. Wiki guide text plus object/NPC outlines (like core Object Markers / NPC Indicators); never clicks or moves the player |
| Voice alerts (bundled clips) | OK. Character voices are named after RuneScape characters but are our own synthesized clips, not Jagex audio |
| Skill tracker panel, level-up alerts | OK |
| Tick counter (over head, tick boxes, smooth tick bar) and metronome, incl. Follow Attack Speed | OK. Core Metronome plugin does the tick sounds (and sets the sound effect volume for one sound and restores it, as we do); Plugin Hub has visual metronome / tick counter plugins. Counts ticks only: it never highlights what to click or when, and never acts for the player |
| Click timing (ms after the tick for each click, ding/dun sounds, on time / late / missed summary) | Not published: only in builds from this repository. `split_plugins.py` leaves out the code between `// <click-timing>` markers, so the Plugin Hub copies don't have it |
| Reset in the PvM HUD's and crab card's right-click menus | OK. RUNELITE_OVERLAY menu entries on our own overlays (like the pinned skill's Unpin); only clear our numbers |
| Buff bar (your buffs/debuffs with timers) and monster effects on the PvM HUD (poison/venom/burn, freezes, Defence after your special attacks) | OK. Same information as the core Timers and Buffs and Boosts plugins, read from varbits, hitsplats and graphics; information only |
| Slayer card hotkey | OK (a keybind that toggles our own overlay) |
| Tick Counter hotkeys (reset count, show/hide, Ticks Per Cycle up/down) | OK. Keybinds (KeyManager/HotkeyListener, unset by default) that only change our own overlays and our own setting |
| Attack timer (Follow Attack Speed: count down, late ticks, eating delays) | OK. Your own attacks only, from your animation, chat lines and equipment; information only, like the Plugin Hub's Attack Timer Metronome. Never predicts a monster's attacks |
| Run energy low / restored alerts | OK. Notifications only, like core Idle Notifier's run energy thresholds |
| Speak Only When Unfocused | OK. Reads `ClientUI.isFocused()`, the same check RuneLite's `Notifier` makes; no `KeyboardFocusManager` |
| Tick Pattern (numbered boxes lit in a timing pattern: two halves of a tick) | OK. Numbers only, like a visual metronome: no labels or item names, nothing says what to click; the player decides what each box means and where it goes. Labelled step-by-step presets ("Herb", "Tar"...) were considered and left out as a click guide |
| Half-Tick Boxes (a box for each half of the tick) | OK. Like RuneLite's own Prayer plugin's flick indicator: shows timing within a tick only; never says what to click and never acts |
| About settings: Open the Guide, clash notes | OK. Open the Guide opens the feature guide with `LinkBrowser.browse` and switches itself back off; clash notes only read which plugins (and a few of RuneLite's own settings: `slayer.highlightOutline/Hull/Tile`) are on, say so once a login in chat, and show a warning info box (our own InfoBox, removed when the plugin stops) |
| Resource timer tile style (tile outline, item picture, Timer Opacity) | OK. Same look as the core Fishing plugin's spot tiles and icons; steps aside where that plugin (or core Motherlode) already draws them |
| Fishing spots: window display (earliest/latest move), spot age, newest-spot marker, your spot's outline | OK. Plugin Hub's Fishing Spot Tracker shows the same (age, newest-spot badge, active-spot colour); the move window is the one Jagex published (Mod Ash: 250-530 ticks), nothing hidden is read |
| Track for goal: ways to fish a spot (colour-coded by your level) and recipes for an item, as submenus | OK. RUNELITE-type menu entries that only change our own goal pick; game options untouched |
| Offering the method you're actually training on (tracker card Track it / Dismiss, chat line) | OK. Reads your clicks (`MenuOptionClicked`, never consumed) and XP drops after the fact; only changes our goal pick when you press Track it |
| Settings tooltips wrapped with `<div width>` | OK. Plain HTML in descriptions, as RuneLite's settings panel already renders them as HTML |

Never add: anything that clicks, types or acts for the player; menu entry removal based on conditions;
boss attack/prayer prediction for high-end PvM; hidden-information reveals; "AFK agility" helpers; PvP
level/skull indicators; anything that modifies or hides core RuneLite UI or other plugins.

## Open questions before publishing

- **Click timing:** settled. Left out of the published plugins (kept in builds from this repository).

- **Voice clip licences:** settled. Ryan (CC BY-NC-SA) and L2 Arctic (CC BY-NC) were removed; the voices left are
  CC0, public domain, or free to use with credit (VOICE_CREDITS.md).

- Plugin size: the published plugins ship four voices (Amy, Cori, Alan, John; `PUBLISHED_VOICES` in
  scripts/split_plugins.py), 11 kHz mu-law, level-up numbers built from parts; the clips are already trimmed. Builds
  from this repository keep all eight. If a reviewer still objects, the next step is fewer voices again.
- `Executors.newSingleThreadExecutor()` for audio is not forbidden, but reviewers may prefer the
  injected `ScheduledExecutorService`.
- The skilling/farming data files are generated from the OSRS Wiki (CC BY-NC-SA) and RuneLite
  (BSD-2-Clause); both are credited in the README.
- `runelite-plugin.properties` and the build files need the Plugin Hub template layout at submission.

## Developer launcher (not part of any plugin)

`Launch-BradsMultiTool.bat` / `gradlew runBrad` only run RuneLite's developer client from source, which is how
RuneLite's own developer guide says to test plugins before the Plugin Hub. Logging in uses RuneLite's own
`--insecure-write-credentials` client option (RuneLite's documented way to use a Jagex Account with a dev
client); the launcher only checks the session file exists and shows the character name and date in it, never
the session itself; with your OK it adds that option to RuneLite's own launcher settings (`settings.json`, backed
up first) or opens RuneLite (configure), and nothing in the plugins reads or sends it. Updating is a plain `git pull` of this
repository.

