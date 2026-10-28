# Brad's Multi-Tool

A RuneLite plugin that bundles PvM, Gemstone Crab, Slayer and skilling helpers, alerts with voice lines,
and a skill tracker into one place. Everything can be switched on or off in the plugin's settings.

- [Features](#features) (full guide with how-tos: [docs/FEATURES.md](docs/FEATURES.md))
- [Setup (Windows)](#first-time-setup-windows-powershell)
- [Logging in with a Jagex Account](#logging-in-with-a-jagex-account)
- [Settings guide](#settings-guide)
- [Credits](#credits)

Working on the plugin itself? See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

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

### Gemstone Crab
- HUD with the crab's health bar, burrow countdown, whether you're attacking, the XP type you're getting,
  damage, DPS and last/max hit. When the crab moves, its stats stay up for 12 seconds, then the radar
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
- Superior spawned and task complete alerts.

### Skilling
Hover cards (they follow the left-click option the game shows, so nothing else gets in the way), timer
pies, and alerts that only fire for the resource you're working:

| Skill | What you get |
|---|---|
| Woodcutting | Tree card (level, XP, your axe in its metal colour, best axe for you); a pie on every tree being chopped (Forestry timer, counting every player chopping, pulsing once it can fall on the next log); tree fallen alert |
| Mining | Rock card; pies on Motherlode Mine veins, calcified rocks, Daeyalt essence and shooting stars (exact, from the star's layer health); rock depleted alert |
| Fishing | Spot card (what each option catches at that kind of spot, tool needed); pies on spots showing how soon they may move (per spot type); fishing spot moved alert |
| Thieving | Stall and pickpocket cards (level, XP, never-fail level with the Ardougne cloak, stun damage); coin pouches full alert |
| Farming | Patch card: crop, stage, roughly when it's grown, diseased/dead, compost, watered, the farmer's protection payment, and what you can plant |
| Hunter | Creature card (level, XP, trap); a pie on each trap you lay (green when something's caught); bird house card and a "bird houses ready" alert |
| Firemaking | Pies on fires lit nearby showing how long they may burn |
| Runecraft | Altar card: level, XP, runes per essence at your level, next multiple |
| Agility | Course card (level, XP per lap, laps to your next level) and shortcut card (level) |
| Cooking | Range/fire card: each raw food you carry, what it cooks into, its level and XP, when it stops burning (with gauntlets if worn) and the XP for the lot |
| Smithing | Anvil card: for each bar you carry, the best item you can make (most XP or most profit: Skilling > Best Choice By), how many, the XP (and profit at GE prices), and the next unlock. Furnace card: the bars your ores make, how many (coal included) and the XP |
| Sailing | Charting spot card (task, what to do, level, XP, the riddle answer for mermaid dives), shipwreck card (level, XP, salvage, best hook for you), shoal card (level, depth, bait, fish); pies on shipwrecks showing how long until they sink |
| Production | "Batch complete" alert when a run of Cooking, Smithing, Crafting, Fletching or Herblore finishes |

If you also run a dedicated timer plugin (Tree Despawn Timer, MLM Despawn Timer, Fishing Spot Tracker,
RuneLite's Hunter plugin, ...), our pie for that timer hides so the two don't stack (Skilling > Use Other
Timer Plugins). XP rates on the cards only show while you're training that skill. Inventory full is announced while you
work a resource.

### Brad's Skill Tracker (sidebar)
- A card per skill you train: XP gained, XP to next level, actions left (from your last XP drop; not for
  combat skills), XP/hr, XP/min, time to level, and a red/orange/green progress bar. Each field has its
  own switch.
- **Goals**: the **Set Goal** button at the top (or right-click a card > Set goal...) takes a skill and a
  level or XP amount. Skills with a goal are pinned under **GOALS** at the top, even if you haven't trained
  them this session, with XP left and time to reach it; everything else is under **THIS SESSION**.
  Switch on **Actions Until Goal** to count actions left to the goal instead of to the next level.
- **Suggested method** (every skill): a goal card shows the wiki training guide's method for your level
  ("Train: Oak trees") and what to switch to before the goal ("At 35: Teak trees"); Slayer suggests the
  best Slayer master. **Training Focus** chooses what it
  aims for: **Fastest XP**, the guide's **Other methods** (often AFK), **Cheapest** (fewest GP per XP when
  making things; gathering is free) or **Money maker** (the most profitable wiki money making guide you
  can do). Where the method is a tree,
  rock, stall or pickpocket NPC, those are outlined in the world (**Highlight Suggested Method**).
- **Track for goal**: right-click a creature, NPC, tree, rock or stall for a goal skill to outline that
  instead of the suggestion ("Tracking: ... (your pick)"); **Use suggested method** on the card goes back.
- **Goal items**: for production skills the card says what to **Make** from what's in your inventory and
  bank, and outlines its materials there (hover one: "Suggested for your Cooking goal: Lobster";
  **Highlight Goal Items**).
- **Pin to screen**: right-click a card > **Pin to screen** to show that skill on the game screen (level
  bar, XP/hr, gained, time to level, goal and suggested method) without the sidebar open. One skill at a
  time; right-click the overlay > **Unpin** (or the card > Unpin from screen) to remove it.
- **Auto-pause**: a skill's rate clock stops after a few minutes without XP and resumes on your next XP.
- Right-click to pause, reset the rate, reset or hide a skill.
- Level-up alerts say the skill and level ("Woodcutting level... 45!").
- **Issues?** button (top-right of the tracker) opens a new GitHub issue.

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
- 11 voices (pre-recorded, so they work on any OS): Amy, Joe, Mike, Cori, Alba, Alan, Northern, and four
  characters: Gnome, Ogre, Old Wizard and Robot. Changing the voice plays a sample.
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
$s = (New-Object -ComObject WScript.Shell).CreateShortcut("$env:USERPROFILE\Desktop\Brad's Multi-Tool.lnk")
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
