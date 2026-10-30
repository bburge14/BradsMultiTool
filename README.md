# Snuffy's Toolbelt

A RuneLite plugin that bundles PvM, Gemstone Crab, Slayer and skilling helpers, alerts with voice lines,
and a skill tracker into one place. Everything can be switched on or off in the plugin's settings.

- [Installing](#installing): the all-in-one, or just the parts you want
- [Features](#features) (full guide with how-tos: [docs/FEATURES.md](docs/FEATURES.md))
- [Setup (Windows)](#first-time-setup-windows-powershell)
- [Logging in with a Jagex Account](#logging-in-with-a-jagex-account)
- [Settings guide](#settings-guide)
- [Credits](#credits)

Working on the plugin itself? See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Installing

Snuffy's plugins come as one all-in-one plugin, or as separate plugins for just the parts you want:

| Plugin | What it has |
|---|---|
| **Snuffy's Toolbelt** | Everything below, in one plugin |
| **Snuffy's Skilling Helper** | The sidebar tracker with goals and suggested training, the pinned skill, skilling hover cards, timer pies and skilling alerts |
| **Snuffy's PvM/Slayer** | The PvM HUD and monster card, buff timers, health and prayer warnings, combat alerts, the Slayer card and task outlines |
| **Snuffy's GemCrab Tracker** | The crab HUD, burrow countdown, radar between coves, outlines and alerts |
| **Snuffy's Tick Counter** | Tick count over your head, tick boxes and a smooth tick bar, metronome, click timing for tick skilling |
| **Snuffy's Alerts** | Optional: one voice and volume for all of the separate plugins |

**From the Plugin Hub** (once they're published there): in RuneLite open the **Plugin Hub** (the plug icon
at the bottom of the configuration panel), search **Snuffy's**, and install either **Snuffy's Toolbelt**
or the separate plugins you want. Don't install the Toolbelt together with the separate ones: you'd get
everything twice. Every plugin has the idle and logout warnings; with several installed they're said once.

**Before they're on the Plugin Hub**, run the Toolbelt from this repository with the steps under
[First-time setup](#first-time-setup-windows-powershell). Each plugin also has its own folder under
[`plugins/`](plugins) (`toolbelt`, `skilling-helper`, `pvm-slayer`, `gemcrab-tracker`, `alerts`) that builds on
its own. `python3 scripts/split_plugins.py --build` makes all five jars in `releases/`.

**Trying the jars with friends:** put the jar(s) in `.runelite/sideloaded-plugins` (create it next to
`.runelite/settings.properties`) and start RuneLite in developer mode: in **RuneLite (configure)** add
`--developer-mode` to the client arguments and `-ea` to the JVM options. RuneLite only loads sideloaded jars in
developer mode.

## Features

### PvM
- **PvM HUD**: your target's HP bar, whether it's aggressive *to you* and an aggro timer, its weakness
  (style icon, plus the element's Strike spell icon for magic), your current combat style, DPS (per
  monster, per monster type or for the session), and last/max hit. It stays up for a few seconds after
  combat.
- **Monster hover card**: hover any attackable monster for its HP, weakness (green when your style hits
  it, red when it doesn't), what it attacks with, and aggression. Aggression follows the game's rules:
  monsters ignore players over twice their level (except in the Wilderness), God Wars followers go by
  your god items, vyre noble clothing and greegrees protect you, and any other wiki condition is shown
  as written. With auto retaliate off, NPCs you can pickpocket show the Thieving card instead.
- **Buff timers**: each combat potion boost with its icon and time left, divine potions to the second;
  "running out" and "expired" alerts with optional repeats; a buff's line is red while it's running out. Each potion has its own voice line.
- **Health and prayer**: low health / low prayer / out of prayer alerts (by percent), flashing orbs, and an
  "OUT OF PRAYER" overhead warning.
- **Combat alerts**: special attack ready (only after you've used it), antifire and poison protection
  running out, poisoned or venomed, stamina worn off, low ammo, out of runes, cannon low.

### Buff bar
- Your buffs and debuffs as icons with timers, like RS3's: potion boosts, antifire, stamina, poison protection,
  boosted stats; poison, venom, drained stats. Movable; hover for names. The PvM HUD shows the **monster's**
  effects instead: poisoned, venomed, burning, frozen, and Defence lowered by your special attacks.

### Gemstone Crab
- HUD with the crab's health bar, burrow countdown, whether you're attacking, the XP type you're getting,
  damage, DPS, last/max hit, your potion buffs with their time left, and a gear check (your damage bonus for
  the style you're using, and worn items whose damage is for another style). When the crab moves, its stats stay up for 12 seconds, then the radar
  takes over.
- Outlines on the crab, its shell (green when you can still mine it, red when you can't) and the cave
  tunnels, plus a radar to find it. All of it only near the crab's three mines in the Tlati Rainforest.
- Alerts when the crab is about to burrow and when it has moved.

### Slayer
- Task card with kills left, kill rate and time to finish, plus **where to go**: the wiki's places to kill the
  task (multicombat, cannon, safespot) and how to get to the first one (Ctrl+Shift+S hides/shows it).
- Pictures: the monster on a task, the master's chathead with no task; Konar's assigned area is shown.
  Every part of the card has its own switch.
- With no task, the card names the best Slayer master for your combat and Slayer level, any quest they
  need, where they are and the fastest ways there, and the next master you'll unlock.
- Task monsters are outlined (on a Konar task, only in her assigned area). Slayer monsters' hover card and
  the PvM HUD show the Slayer level needed, the gear it needs (green if you have it), whether it's your task,
  Slayer XP, and "wrong place" when Konar wants them elsewhere.
- Warns when you fight a task monster without its required item (rock hammer, nose peg, mirror shield...).
- Superior spawned, task halfway, task nearly done (10 left by default) and task complete alerts.

### Skilling
Hover cards (they follow the left-click option the game shows, so nothing else gets in the way), timer
pies (only while you train that skill, unless **Pies When Not Training** is on), and alerts that only fire for the resource you're working:

| Skill | What you get |
|---|---|
| Woodcutting | Tree card (level, XP, your axe in its metal colour, best axe for you); a pie on every tree being chopped (Forestry timer, counting every player chopping, pulsing once it can fall on the next log); tree fallen alert |
| Mining | Rock card; pies on Motherlode Mine veins, calcified rocks, Daeyalt essence and shooting stars (exact, from the star's layer health); rock depleted alert |
| Prayer | Libation bowl card: your blessed bone shards, the XP they give, wine and prayer points needed, the level they reach; shards count as a Prayer goal item |
| Fishing | Spot card for every kind of spot (net, bait, lure, cage, harpoon, karambwan, anglerfish, dark crab, eels, minnows, aerial, boat spots...): what each way of fishing it catches, level, XP, tool needed; pies on spots showing how soon they may move (per spot type); fishing spot moved alert |
| Thieving | Stall and pickpocket cards (level, XP, never-fail level with the Ardougne cloak, stun damage); coin pouches full alert |
| Farming | Patch card: crop, stage, roughly when it's grown, diseased/dead, compost, watered, the farmer's protection payment, and what you can plant |
| Hunter | Creature card (level, XP, trap); trap spot card (young tree, boulder, pit: what you can catch there, with levels); a pie on each trap you lay (green when something's caught); bird house card and a "bird houses ready" alert |
| Firemaking | Pies on fires lit nearby and on Forester's Campfires showing how long they may burn |
| Runecraft | Altar card: level, XP, runes per essence at your level, next multiple |
| Agility | Course card (level, XP per lap, laps to your next level) and shortcut card (level) |
| Cooking | Range/fire card: each raw food you carry, what it cooks into, its level and XP, when it stops burning (with gauntlets if worn) and the XP for the lot |
| Smithing | Anvil card: for each bar you carry, the best item you can make (most XP or most profit: Skilling > Best Choice By), how many, the XP (and profit at GE prices), and the next unlock. Furnace card: the bars your ores make, how many (coal included) and the XP |
| Sailing | Charting spot card (task, what to do, level, XP, the riddle answer for mermaid dives), shipwreck card (level, XP, salvage, best hook for you), shoal card (level, depth, bait, fish), sea monster card (HP, safe keel, bounty parts), current duck card (despawn timer); pies on shipwrecks showing how long until they sink |
| Production | "Batch complete" alert when a run of Cooking, Smithing, Crafting, Fletching or Herblore finishes |

If you also run a dedicated timer plugin (Tree Despawn Timer, MLM Despawn Timer, Fishing Spot Tracker,
RuneLite's Hunter plugin, ...), our pie for that timer hides so the two don't stack (Skilling > Use Other
Timer Plugins). XP rates on the cards only show while you're training that skill. Inventory full is announced while you
work a resource.

### Snuffy's Skilling Helper (sidebar)
- A card per skill you train: XP gained, XP to next level, actions left (from your last XP drop; not for
  combat skills), XP/hr, XP/min, time to level, and a red/orange/green progress bar. Each field has its
  own switch.
- **Goals**: the **Set Goal** button at the top (or right-click a card > Set goal...) takes a skill and a
  level or XP amount. Skills with a goal are pinned under **GOALS** at the top, even if you haven't trained
  them this session, with XP left and time to reach it; everything else is under **THIS SESSION**.
  Switch on **Actions Until Goal** to count actions left to the goal instead of to the next level (or to
  the end of the current step when the suggested method changes before the goal).
  Before your first XP drop it estimates from the suggested method's XP per action ("~1,240"). Combat
  goals count kills of the monster you're fighting, from its hitpoints ("Kills: ~240"), and show your kill
  rate ("Kill rate: Sand Crab: 52/hr (1:09 each)"), where potions show up as faster kills.
- **Suggested method** (every skill): a goal card shows the wiki training guide's method for your level
  and the level it lasts to ("Train: Teak trees to 62"; hover it for every step to the goal); Slayer
  suggests the best Slayer master. Methods whose quests or other levels you don't have yet are skipped, and at
  low levels the card lists quests worth doing for that skill's XP. A **Needs:** line lists the tools it takes
  (green if you have them). Each skill's **Suggest** choice (right-click it, or when setting its
  goal; otherwise the **Training Focus** setting) chooses what it
  aims for: **Fastest**, the guide's other methods (**AFK / Other**), **Cheapest** (fewest GP per XP when
  making things; gathering is free) or **Money Maker** (the most profitable wiki money making guide you
  can do). Where the method is a tree,
  rock, stall or pickpocket NPC, those are outlined in the world (**Highlight Suggested Method**).
- **Track for goal**: right-click a creature, NPC, tree, rock or stall for a goal skill (or any monster, the Gemstone Crab too, for a
  combat goal, which also sets what kills are counted on) to outline that
  instead of the suggestion ("Tracking: ... (your pick)"); **Use suggested method** on the card goes back.
  Items work too: right-click logs, bones, raw fish... in your inventory or bank and the goal card works it
  out from that item. For Prayer, bones open a list of **Bury**, **Gilded altar**, **Chaos altar** or
  **Ectofuntus**, and blessed bone shards one of **Blessed wine** or **Sunfire wine**; the XP, actions left and
  tools follow your pick ("Offer: Dragon bones (Gilded altar)").
- **Goal items**: for production skills the card says what to **Make** from what's in your inventory and
  bank, and outlines its materials there (hover one: "Suggested for your Cooking goal: Lobster";
  **Highlight Goal Items**).
- **Pin to screen**: right-click a card > **Pin to screen** to show that skill on the game screen (level
  bar, XP/hr, gained, time to level, actions left, goal and suggested method) without the sidebar open. One skill at a
  time; its card shows a red pin (click it to unpin), or right-click the overlay > **Unpin**.
- **Auto-pause**: a skill's rate clock stops after a while without XP (10 seconds to an hour; 5 minutes by default) and resumes on your
  next XP.
- Right-click to pause, reset the rate, reset or hide a skill.
- Level-up alerts say the skill and level ("Woodcutting level... 45!").
- **Issues?** button (top-right of the tracker) opens a new GitHub issue.

### Tick counter and metronome
- Counts game ticks in a cycle you set (1-10): **over your head**, as a **row of boxes** (one per tick, the
  current one lit; or a column), and/or a **tick bar** that fills smoothly over each tick so you can see it
  coming. The first tick of each cycle is in its own colour.
- **Metronome** with a choice of game sounds (Plop, Bell, Anvil, Boop, Coins, Pickaxe and axe, Tinderbox): a
  different sound on the first tick of the cycle.
- **Click timing** for 1-ticking: how many ms after the tick each click landed, an ideal click zone on the tick
  bar, a ding when on time and a different sound when late or when you miss a tick, and a summary of your last
  50 clicks.
- **Follow Attack Speed**: the cycle follows your weapon and starts again each time you attack.
- **Snuffy's Tick Counter** (also in the Toolbelt); settings under **Tick Counter**.

### Idle
- **AFK alert**: when you were training (gaining XP) and your character stopped, with no clicks or key
  presses since. Banking, shops, the GE and menus never set it off.
- **Idle logout warning**: before the game logs you out for inactivity (follows RuneLite's Logout Timer
  setting; uses the game's 20-minute rule in combat), with optional repeats.

### Alerts in general
- No alert fires until you're past the welcome screen, and anything already true when you log in isn't
  announced.
- Every alert has its own **Voice:** and **Chat:** switch in its section; **General** has the master
  switches, the voice and the volume.
- With several of the separate plugins, **Same Voice For All** keeps them on one voice and volume: change it
  in any of them (turn it off in one to give it its own).
- 10 voices (pre-recorded, so they work on any OS): Amy and Cori (US and UK women), Alan,
  Ryan and John (UK and US men), Arctic (a Scottish man), L2 Arctic (a man with a Mandarin Chinese accent), Wise Old Man, TzHaar and Ghost, or **Random** for a different
  one each alert. Changing the voice plays a sample.
- Chat messages use the same words as the voice line, with details in brackets.

## First-time setup (Windows, PowerShell)

**1. Install Java and Git** (skip what you already have; check with `java -version` and `git --version`):

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id Git.Git -e
```

Close and reopen PowerShell afterwards so `java` and `git` are on your PATH.

**2. Download the project and launch it:**

```powershell
New-Item -ItemType Directory -Force C:\dev | Out-Null
Set-Location C:\dev
git clone https://github.com/bburge14/bradsmultitool.git BradsMultiTool
Set-Location C:\dev\BradsMultiTool
git checkout claude/nifty-feynman-izouon   # skip once this branch is merged into main
.\gradlew.bat runBrad
```

The first run downloads Gradle and RuneLite, so it takes a few minutes. If the repo is private, Git opens
a browser window to sign in to GitHub.

**3. (Optional) Desktop shortcut:** run this once to put a launch button on your desktop:

```powershell
$s = (New-Object -ComObject WScript.Shell).CreateShortcut("$env:USERPROFILE\Desktop\Snuffy's Toolbelt.lnk")
$s.TargetPath = "C:\dev\BradsMultiTool\Launch-BradsMultiTool.bat"
$s.WorkingDirectory = "C:\dev\BradsMultiTool"
$s.Save()
```

Double-clicking it (or `Launch-BradsMultiTool.bat`) checks your Jagex session file is present, then builds
and launches RuneLite with the plugin.

**4. Log in:** follow [Logging in with a Jagex Account](#logging-in-with-a-jagex-account) (once per PC).

**Getting updates later:**

```powershell
Set-Location C:\dev\BradsMultiTool
git pull
.\gradlew.bat runBrad
```

`runBrad` launches a developer-mode RuneLite client with the plugin loaded (needs JDK 11 or newer; macOS /
Linux use `./gradlew runBrad`). If something seems stale, `.\gradlew.bat clean runBrad --info` rebuilds
from scratch.

To try the separate plugins instead of the Toolbelt (as friends installing just some of them would have),
name them with `-Pload=` (in quotes in PowerShell):

```powershell
.\gradlew.bat runBrad "-Pload=skilling"          # just Snuffy's Skilling Helper
.\gradlew.bat runBrad "-Pload=pvm,tick"          # PvM/Slayer and the Tick Counter together
.\gradlew.bat runBrad "-Pload=separate"          # all the separate plugins at once
```

The names are `toolbelt` (the default), `skilling`, `pvm`, `gemcrab`, `tick` and `alerts`. Each separate
plugin has its own settings, so settings you change in the Toolbelt don't carry over to them (and the other
way round). Don't load the Toolbelt together with separate ones: everything would show twice.

## Logging in with a Jagex Account

The dev client can't log in through the Jagex Launcher directly, but it reuses a session the official
client saves for it:

1. Open RuneLite's configure tool: Windows Start menu > **RuneLite (configure)** (or `RuneLite.exe --configure`).
2. In **Client arguments**, add `--insecure-write-credentials` and click **Save**.
3. Launch RuneLite **once through the Jagex Launcher** and log in. This writes
   `%USERPROFILE%\.runelite\credentials.properties`.
4. Close it and run `.\gradlew.bat runBrad`. The dev client picks up the credentials file automatically.

`credentials.properties` is a live login session. Never share or commit it. To revoke it, delete the file
(and remove the client argument if you no longer need it); if it may have leaked, end your sessions from
your Jagex account settings.

## Settings guide

All sections except General start collapsed.

| Section | What's in it |
|---|---|
| General | Voice on/off, voice, volume, chat messages on/off |
| Potion Buffs / Health / Prayer | Buff timers and alerts, thresholds, repeats, orb flash, their Voice/Chat switches |
| Combat Alerts | Spec, antifire, poison, stamina, ammo, runes, cannon |
| Idle | AFK alert and idle logout warning |
| PvM HUD | HUD on/off, monster hover, DPS mode, which lines to show |
| Slayer | Task card and each of its parts, hotkey, Slayer info on monsters, task monster outlines, item warning, superior and task complete |
| Gemstone Crab | HUD, outlines and their colours, crab alerts |
| Skilling | Hover cards, XP rates on cards, inventory full, resource gone, batch complete |
| Woodcutting ... Sailing | One section per skill with its pies, cards and alerts |
| Skill Tracker | Which fields to show, goals, suggested methods and highlights, goal items, auto-pause, ignored skills, level-up alerts |

## Credits

- The resource timer pies follow the logic worked out by these Plugin Hub plugins (BSD-2 licensed):
  [Tree Despawn Timer](https://github.com/CreativeTechGuy/tree-despawn-timer) by CreativeTechGuy,
  [MLM Despawn Timer](https://github.com/xetrics/mlm-despawn-timer),
  [Calcified Rock Despawn Timer](https://github.com/NotTeeZee/calcified-rocks-despawn-timer),
  [Daeyalt Mining Timer](https://github.com/Pvff13/daeyalt-mining-timer),
  [Fishing Spot Timer](https://github.com/ArnautS/FishingSpotTimer),
  [Fishing Spot Tracker](https://github.com/CharlesLillo/fishing-spot-tracker),
  [Star AFK Timer](https://github.com/silly-build/star-afk-timer),
  [Salvaging AFK Timer](https://github.com/silly-build/salvaging-afk-timer) and
  [Fire Timer](https://github.com/autumn-smellegy/fire-timer).
- Farming patch data, Hunter trap IDs, Agility courses and shortcuts are converted from RuneLite's own
  plugins (BSD-2-Clause).
- Game information, Slayer master chatheads and task icons are from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki).
- Voices are generated with [Piper](https://github.com/rhasspy/piper) (see [VOICE_CREDITS.md](VOICE_CREDITS.md)).
