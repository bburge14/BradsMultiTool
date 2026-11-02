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
| **Snuffy's Skilling Helper** | The sidebar tracker with goals and suggested training, the pinned skill, skilling hover cards, resource timers and skilling alerts |
| **Snuffy's PvM/Slayer** | The PvM HUD and monster card, buff timers, health and prayer warnings, combat alerts, the Slayer card and task outlines |
| **Snuffy's GemCrab Tracker** | The crab HUD, burrow countdown, radar between coves, outlines and alerts |
| **Snuffy's Tick Counter** | Tick count over your head, tick boxes and a smooth tick bar, metronome, click timing for tick skilling |
| **Snuffy's Alerts** | Optional: one voice and volume for all of the separate plugins |

**From the Plugin Hub** (once they're published there): in RuneLite open the **Plugin Hub** (the plug icon
at the bottom of the configuration panel), search **Snuffy's**, and install either **Snuffy's Toolbelt**
or the separate plugins you want. Don't install the Toolbelt together with the separate ones: you'd get
everything twice. Every plugin has the idle and logout warnings; with several installed they're said once.

**Before they're on the Plugin Hub**, run them from this repository with **Snuffy's Launcher**: see
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
  as written. It also shows the wiki's max hit for each attack and how often it attacks. With auto retaliate
  off, NPCs you can pickpocket show the Thieving card instead.
- **Buff timers**: each combat potion boost with its icon and time left, divine potions to the second;
  "running out" and "expired" alerts with optional repeats; a buff's line is red while it's running out. Each potion has its own voice line.
- **Health and prayer**: low health / low prayer / out of prayer alerts (by percent), flashing orbs, and an
  "OUT OF PRAYER" overhead warning.
- **Combat alerts**: special attack ready (only after you've used it), antifire and poison protection
  running out, poisoned or venomed, stamina worn off, low ammo, out of runes, cannon low; and (off by default)
  a wrong protection prayer for a monster with one attack style, and starting a fight with no combat boost.

### Buff bar
- Your buffs and debuffs as icons with timers, like RS3's: potion boosts, overload, antifire, stamina, poison
  protection, prayer regeneration, vengeance, staff of the dead's special, the imbued heart's cooldown, boosted
  stats; poison, venom, frozen, teleblocked, drained stats. Movable; hover for names. The PvM HUD shows the **monster's**
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
- Your Slayer points and streak on the card, with a heads-up when your next task is a 10th/50th/100th/250th/1000th
  (bonus points) so you can pick your master; food and potion doses used on the task.
- **Kill goals**: right-click a monster > Set kill goal ("kill 200 gargoyles"); progress on the card and the
  monster's hover card, and an alert when you reach it.
- **Finish-off marker**: the ice cooler, rock hammer, bag of salt or fungicide over a lizard, gargoyle, rockslug
  or zygomite once it's low enough to finish.

### Skilling
Hover cards (they follow the left-click option the game shows, so nothing else gets in the way), resource
timers (each kind set to show Always, While training or Off; by default each timed tree, rock or
fishing spot gets its tile outlined with a picture of what it gives, the outline changing colour as its timer runs out; traps and fires keep a small pie), and alerts that only fire for the resource you're working:

| Skill | What you get |
|---|---|
| Woodcutting | Tree card (level, XP, your axe in its metal colour, best axe for you); a pie on every tree being chopped (Forestry timer, counting every player chopping, pulsing once it can fall on the next log); tree fallen alert |
| Mining | Rock card; pies on Motherlode Mine veins, calcified rocks, Daeyalt essence and shooting stars (exact, from the star's layer health); rock depleted alert |
| Prayer | Libation bowl card: your blessed bone shards, the XP they give, wine and prayer points needed, the level they reach; shards count as a Prayer goal item |
| Fishing | Spot card for every kind of spot (net, bait, lure, cage, harpoon, karambwan, anglerfish, dark crab, eels, minnows, aerial, boat spots...): what each way of fishing it catches, level, XP, tool needed; timers on spots showing the window they move in (per spot type), how long ago each appeared, (optionally) a gold ring on the newest spot (the best one to fish) and your spot highlighted; fishing spot moved alert |
| Thieving | Stall and pickpocket cards (level, XP, never-fail level with the Ardougne cloak, stun damage); coin pouches full alert |
| Farming | Patch card: crop, stage, roughly when it's grown, diseased/dead, compost, watered, the farmer's protection payment, and what you can plant |
| Hunter | Creature card (level, XP, trap); trap spot card (young tree, boulder, pit: what you can catch there, with levels); a pie on each trap you lay (green when something's caught, purple when it springs empty); bird house card and a "bird houses ready" alert; a Hunters' Rumour card (creature, catches toward the guaranteed rare part, where to find it) with a "rumour complete" alert |
| Firemaking | Timers on Forester's Campfires showing how long they have left, and (off by default) on fires you cook on showing the window they go out in |
| Runecraft | Altar card: level, XP, runes per essence at your level, next multiple |
| Agility | Course card (level, XP per lap, laps to your next level) and shortcut card (level); Brimhaven Arena obstacles coloured by your level with the quickest route to the tagged pillar |
| Cooking | Range/fire card: each raw food you carry, what it cooks into, its level and XP, when it stops burning (with gauntlets if worn) and the XP for the lot |
| Smithing | Anvil card: for each bar you carry, the best item you can make (most XP or most profit: Skilling Hover Cards > Best Choice By), how many, the XP (and profit at GE prices), and the next unlock. Furnace card: the bars your ores make, how many (coal included) and the XP |
| Sailing | Charting spot card (task, what to do, level, XP, the riddle answer for mermaid dives), shipwreck card (level, XP, salvage, best hook for you), shoal card (level, depth, bait, fish), sea monster card (HP, safe keel, bounty parts), current duck card (despawn timer); pies on shipwrecks showing how long until they sink; boat health low and trim sails alerts |
| Minigames | Wintertodt card (warmth, energy, points, roots/kindling) and alerts (round starting, brazier out, out of roots, the cold stopped you, warmth low); Tempoross card (energy, essence, storm, points, fish and their damage) and alerts (wave incoming, fire near you, double spot, vulnerable); Guardians of the Rift card (guardian's power, energy, the open altars coloured by your Runecraft level, portal) and alerts (game starting, portal opened with where) |
| Production | "Batch complete" alert when a run of Cooking, Smithing, Crafting, Fletching or Herblore finishes |

If you also run a dedicated timer plugin (Tree Despawn Timer, MLM Despawn Timer, Fishing Spot Tracker,
RuneLite's Hunter plugin, ...), our pie for that timer hides so the two don't stack (Resource Timers > Use Other
Timer Plugins). XP rates on the cards only show while you're training that skill. Inventory full is announced while you
work a resource.

### Snuffy's Skilling Helper (sidebar)
- A card per skill you train: XP gained, XP to next level, actions left (from your last XP drop; not for
  combat skills), XP/hr, XP/min, time to level, and a red/orange/green progress bar. Each field has its
  own switch.
- **Goals**: the **Set Goal** button at the top (or right-click a card > Set goal..., or Modify goal... once it
  has one) takes a skill and a level or XP amount. Skills with a goal are pinned under **GOALS** at the top, even if you haven't trained
  them this session, with XP left and time to reach it; everything else is under **THIS SESSION**.
  Switch on **Actions Until Goal** to count actions left to the goal instead of to the next level (or to
  the end of the current step when the suggested method changes before the goal). Hover the count to see both:
  to the next level and to your goal, with the one shown marked.
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
  **Highlight Goal Items** (Goals)).
- **Pin to screen**: click the pin in a card's top-right corner (or right-click > **Pin to screen**) to show that skill on the game screen (level
  bar, XP/hr, gained, time to level, actions left, goal and suggested method) without the sidebar open. One skill at a
  time: the pinned card's pin is solid, the others faint, and clicking a faint one moves the pin there. Click the
  solid pin to unpin, or right-click the overlay > **Unpin**.
- **Auto-pause**: a skill's rate clock stops after a while without XP (10 seconds to an hour; 10 seconds by default) and resumes on your
  next XP.
- **Idle skills drop off**: a skill without a goal leaves the tracker after 30 minutes without XP (**Drop Idle Skills
  After**, 0 for never) and comes back, with its session numbers, when you gain XP in it again. Skills with a goal
  and the pinned skill always stay; there's no other limit on how many cards it shows. **TRAINED** at the top counts
  the skills you've gained XP in this session. Both lists put the skill you last gained XP in at the top; click a list's heading to fold it away ("+ GOALS (3)")
  or open it again, which it remembers.
- Right-click to pause, reset the rate, reset or hide a skill.
- Level-up alerts say the skill and level ("Woodcutting level... 45!").
- **Items gathered**: logs, fish, ore and gems, food cooked and burnt this session, with how many an hour, on the card and the pinned skill.
- **Keeps your session** after a restart: log back in within 30 minutes (**Keep Session After Restart**) and the tracker carries on.
- **Skills tab goal bars**: a thin gold bar on the game's Skills tab under each skill with a goal.
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
- **Follow Attack Speed**: an attack timer for your own attacks. The cycle follows your weapon (and spells,
  Rapid, eating delays) and starts again each time you attack; it can **count down** to your next attack and
  show how many ticks **late** you are (-1, -2).
- **Hotkeys** (none set at first): reset the count to 1, show/hide the tick overlays, Ticks Per Cycle up/down.
- **Snuffy's Tick Counter** (also in the Toolbelt); settings under **Tick Counter**.

### Idle
- **AFK alert**: when you were training (gaining XP) and your character stopped, with no clicks or key
  presses since. Banking, shops, the GE and menus never set it off.
- **Idle logout warning**: before the game logs you out for inactivity (follows RuneLite's Logout Timer
  setting; uses the game's 20-minute rule in combat), with optional repeats.
- **Run energy low / restored** anywhere (both off at first): below a level you pick, and back up to one (say
  100%).

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
- **Speak Only When Unfocused** (General, off at first): voice only while you're in another window; chat
  messages still post.

## First-time setup (Windows, PowerShell)

**1. Install Java and Git** (skip what you already have; check with `java -version` and `git --version`):

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id Git.Git -e
```

Close and reopen PowerShell afterwards so `java` and `git` are on your PATH.

**2. Download the project:**

```powershell
New-Item -ItemType Directory -Force C:\dev | Out-Null
Set-Location C:\dev
git clone https://github.com/bburge14/bradsmultitool.git BradsMultiTool
Set-Location C:\dev\BradsMultiTool
git checkout claude/nifty-feynman-izouon   # skip once this branch is merged into main
```

If the repo is private, Git opens a browser window to sign in to GitHub.

**3. Put the launcher on your desktop** (run once; or open `Launch-BradsMultiTool.bat` and click **Make a
desktop shortcut** at the bottom of the window):

```powershell
$s = (New-Object -ComObject WScript.Shell).CreateShortcut("$env:USERPROFILE\Desktop\Snuffy's Launcher.lnk")
$s.TargetPath = "C:\dev\BradsMultiTool\Launch-BradsMultiTool.bat"
$s.WorkingDirectory = "C:\dev\BradsMultiTool"
$s.IconLocation = "C:\dev\BradsMultiTool\assets\launcher.ico,0"
$s.Save()
```

**4. Open Snuffy's Launcher** (the shortcut, or double-click `Launch-BradsMultiTool.bat`). It's a small window that:

- **updates** the plugins each time you open it, and while it's open checks every minute in the background and
  pulls any update straight away, listing what's new (**Check again** to retry; when you're offline or have changed
  files here, it says so and you launch the version you have);
- **warns you when the open RuneLite is out of date**: it remembers which version each launch was built from (even
  if you close and reopen the launcher), and when there's a newer one it turns red ("RuneLite is out of date: press
  Relaunch", with what's new listed below), makes **Relaunch** bold and pops up a Windows notification;
- checks your **setup**, each row with a button to fix it:
  - **RuneLite** installed (it looks in RuneLite's usual folders, its uninstall entry, Start menu shortcuts and
    the Jagex Launcher's folder; **Find it...** lets you point to `RuneLite.exe` if it's somewhere else);
  - **session saving** on in RuneLite (configure): **Turn it on** adds `--insecure-write-credentials` for you
    (keeping a backup of RuneLite's settings) or opens the configure window to add it yourself;
  - a **Jagex session** saved, for which character and when: **Save one** opens the Jagex Launcher, tells you
    what to do (press Play, log in once) and ticks the row as soon as the session is saved. It only reads the
    character's name and the date, never the session itself;
  - **Java** (needed to build) and **git** (for updates);
- lets you pick the **plugins to load**: **Snuffy's Toolbelt** on its own (it already includes every separate
  plugin), or **Separate plugins** with a checkbox each. Your pick is ticked again next time;
- a **Dev** tab (for the plugins' owner): **Push to repos** copies the plugins to their own GitHub repositories with
  new versions for the ones that changed (**Check (dry run)** first shows what would change), and **Publish to
  RuneLite** gets the Plugin Hub pull request ready (see docs/ROLLOUT.md);
- **Relaunch** closes RuneLite (as its X button would, answering OK to RuneLite's "Are you sure you want to exit?"
  for you; forced if it hangs), updates, and starts it again with your pick;
- **Launch RuneLite** builds and starts it silently (no console window): a progress bar shows it building,
  starting RuneLite and then "RuneLite is running", and the launcher stays open (it says when RuneLite closes,
  and **Launch** works again). If something goes wrong it says so in red; **Show log** opens the full output.
  The first launch downloads Gradle and RuneLite, so it takes a few minutes.

The first time, work down the Setup rows until they're all ticked, then press **Launch RuneLite**. After that
it's just open and Launch: updates come in on their own.

Why not the Jagex Launcher with the plugin dropped in? RuneLite only loads plugin files of your own in
developer mode, and it turns developer mode off when started from its launcher, so the developer client
(logged in with the saved session) is the only way to run Snuffy's before it's on the Plugin Hub.

## Logging in with a Jagex Account

The launcher's Setup rows do this for you (see above). The saved session, `credentials.properties` in
`%USERPROFILE%\.runelite`, is a live login: never share or commit it. To revoke it, delete the file (and
remove `--insecure-write-credentials` from RuneLite (configure) if you no longer need it); if it may have
leaked, end your sessions from your Jagex account settings. If RuneLite asks for an email and password instead
of showing Play, the session has expired: press **Refresh it** in the launcher.

<details>
<summary>Advanced: running it by hand, without the launcher</summary>

**Saving a session by hand:**

1. Open RuneLite's configure tool: Windows Start menu > **RuneLite (configure)** (or `RuneLite.exe --configure`).
2. In **Client arguments**, add `--insecure-write-credentials` and click **Save**.
3. Launch RuneLite **once through the Jagex Launcher** and log in. This writes
   `%USERPROFILE%\.runelite\credentials.properties`.
4. Close it and run `.\gradlew.bat runBrad`. The dev client picks up the credentials file automatically.

**Updating and launching from PowerShell:**

```powershell
Set-Location C:\dev\BradsMultiTool
git pull
.\gradlew.bat runBrad
```

`runBrad` launches a developer-mode RuneLite client with the plugin loaded (needs JDK 11 or newer; macOS /
Linux use `./gradlew runBrad`). If something seems stale, `.\gradlew.bat clean runBrad --info` rebuilds
from scratch.

To load the separate plugins instead of the Toolbelt, name them with `-Pload=` (in quotes in PowerShell):

```powershell
.\gradlew.bat runBrad "-Pload=skilling"          # just Snuffy's Skilling Helper
.\gradlew.bat runBrad "-Pload=pvm,tick"          # PvM/Slayer and the Tick Counter together
.\gradlew.bat runBrad "-Pload=separate"          # all the separate plugins at once
```

The names are `toolbelt` (the default), `skilling`, `pvm`, `gemcrab`, `tick` and `alerts`. The Toolbelt always
loads on its own (it already includes the others). Each separate plugin has its own settings, so settings you
change in the Toolbelt don't carry over to them (and the other way round).

</details>

## Settings guide

Each plugin's settings start with **About**: hover its heading for what the plugin does and which other plugins
clash with it, and switch on **Open the Guide** to open the full feature guide. While a clashing plugin is on (or the
setting of it that clashes), every one of Snuffy's plugins shows a small yellow warning icon among RuneLite's info
boxes (top left of the game; hover it for the clashes and what they mean), and each plugin says its clashes in chat
once a login. It's live: turn the clashing plugin or setting off and the icon goes within a few seconds. Don't want
any of it? Turn off **About > Clash Warnings** in that plugin's settings. Clashes include the dedicated timer plugins, RuneLite's Hunter, Mining, Fishing (spot icons, tiles,
minnows), Motherlode Mine (veins), Wintertodt, Slayer (task highlight), Metronome and Idle Notifier, and the Tempoross,
Guardians of the Rift Helper and Sailing plugins' notifications.


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

- The resource timers follow the logic worked out by these Plugin Hub plugins (BSD-2 licensed):
  [Tree Despawn Timer](https://github.com/CreativeTechGuy/tree-despawn-timer) by CreativeTechGuy,
  [MLM Despawn Timer](https://github.com/xetrics/mlm-despawn-timer),
  [Calcified Rock Despawn Timer](https://github.com/NotTeeZee/calcified-rocks-despawn-timer),
  [Daeyalt Mining Timer](https://github.com/Pvff13/daeyalt-mining-timer),
  [Fishing Spot Timer](https://github.com/ArnautS/FishingSpotTimer),
  [Fishing Spot Tracker](https://github.com/CharlesLillo/fishing-spot-tracker),
  [Star AFK Timer](https://github.com/silly-build/star-afk-timer),
  [Salvaging AFK Timer](https://github.com/silly-build/salvaging-afk-timer) and
  [Fire Timer](https://github.com/autumn-smellegy/fire-timer).
- The Tick Counter's attack timer (Follow Attack Speed: attacks only once the delay has run out, eating delays,
  the animations that aren't attacks, late ticks) follows [Attack Timer Metronome](https://github.com/ngraves95/attacktimer)
  by Nick Graves and contributors (BSD-2 licensed).
- Farming patch data, Hunter trap IDs, Agility courses and shortcuts, and the Wintertodt messages are converted from
  RuneLite's own plugins (BSD-2-Clause), and the items gathered counts use the chat messages its Woodcutting, Fishing
  and Cooking plugins read. The Slayer points and streak, the finish-off thresholds and the buff bar's timers read
  the game the same way as RuneLite's Slayer and Timers and Buffs plugins (BSD-2-Clause).
- The minigame and Sailing alerts use what these Plugin Hub plugins (BSD-2-Clause) worked out:
  [Tempoross](https://github.com/nmlynch94/runelite-external-plugins) (messages, ids, fish damage),
  [Guardians of the Rift Helper](https://github.com/DatBear/Guardians-of-the-Rift-Helper) by DatBear (messages,
  altar icons and levels) and [Sailing](https://github.com/LlemonDuck/sailing) by LlemonDuck (low boat HP).
- The Hunters' Rumours card reads your rumour the way [Hunter Rumours](https://github.com/geel9/runelite-hunter-rumours)
  (BSD-2) does: from the guild hunters' dialogue, the Quetzal whistle and the rare part's message.
- Game information, Slayer master chatheads and task icons are from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki).
- Voices are generated with [Piper](https://github.com/rhasspy/piper) (see [VOICE_CREDITS.md](VOICE_CREDITS.md)).
