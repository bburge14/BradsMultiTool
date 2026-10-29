# Brad's Multi-Tool: feature guide

Every feature, what it does, how to use it and where its settings are. Settings are in RuneLite's
configuration panel (the wrench icon) under **Brad's Multi-Tool**; each section below names the settings
section to open. All sections except General start collapsed.

- [Alerts (all of them)](#alerts-all-of-them)
- [PvM HUD](#pvm-hud)
- [Monster hover card](#monster-hover-card)
- [Potion buffs](#potion-buffs)
- [Health and prayer](#health-and-prayer)
- [Combat alerts](#combat-alerts)
- [Slayer card](#slayer-card)
- [Gemstone Crab](#gemstone-crab)
- [Skilling hover cards](#skilling-hover-cards)
- [Timer pies](#timer-pies)
- [Skilling alerts](#skilling-alerts)
- [Skill tracker](#skill-tracker)
- [Goals and suggested training](#goals-and-suggested-training)
- [Idle alerts](#idle-alerts)
- [Issues? button](#issues-button)

---

## Alerts (all of them)

**What it does:** Every alert can speak (a pre-recorded voice line) and/or post a chat message. The chat
message uses the same words as the voice line, with details in brackets.

**How to use:**
- **General > Voice Alerts / Chat Messages** turn all voice or all chat on or off.
- **General > Voice Persona** picks one of 9 voices (Amy, Joe, Cori, Alan in US/UK female/male, and
  the RuneScape-inspired characters Gnome Child, Wise Old Man, Zamorak, TzHaar, Ghost). Changing it plays a sample.
- Each alert also has its own **Voice: ...** and **Chat: ...** switch in its section. Turning a Voice
  switch on plays that line as a preview.

**Good to know:** No alert fires before you click "Click here to play", and anything already true when
you log in (low prayer, poisoned...) isn't announced.

---

## PvM HUD

**What it does:** When you attack a monster, a HUD shows its HP bar, whether it's aggressive *to you* (with
a tolerance timer), its weakness (style icon, or the element's Strike spell icon for magic), your combat
style, DPS, and your last and max hit. It stays up for a few seconds after the fight.

**How to use:** Attack anything. The HUD can be moved like any RuneLite overlay (hold Alt and drag).

**Settings (PvM HUD):** Enable PvM HUD; DPS Tracking Mode (per monster, per monster type, or the whole
session); Linger After Combat; Show Weakness / DPS / Last Hit / Max Hit / Aggro Status.

---

## Monster hover card

**What it does:** Hover an attackable monster to see its HP, weakness (green if your current style hits it,
red if not), what it attacks with, and whether it will be aggressive to you. Aggression follows the
game's rules: monsters ignore players over twice their level (except in the Wilderness), God Wars
followers go by your god items, vyre noble clothing and greegrees protect you.

**How to use:** Hover a monster. The card follows the game's left-click option, so it only shows when
the top-left tooltip names the monster.

**Settings:** PvM HUD > Monster Hover Info. With **Skilling > Skill Cards With Auto Retaliate Off** on
and auto retaliate off, NPCs you can pickpocket show the Thieving card instead.

---

## Potion buffs

**What it does:** Each combat potion boost shows on the HUD with its icon and time left (divine potions to
the second). Alerts fire shortly before a boost runs out and when it has.

**Settings (Potion Buffs):** Show Buffs on HUD; Flash When Expired; Warn Before Ending (seconds);
Repeat Expired Alert, Repeat Every, Max Repeats; Voice/Chat switches.

---

## Health and prayer

**What it does:** Alerts when health or prayer drops to your threshold (as a % of your level), flashes the
orb by the minimap, and shows an "OUT OF PRAYER" warning above your character.

**Settings (Health / Prayer):** Alert switch; Alert Below (%); Flash Health/Prayer Orb; Overhead 'Out of
Prayer' Flash; Repeat Voice Alert, Repeat Every, Max Repeats; Voice/Chat switches.

---

## Combat alerts

**What it does:** Special attack ready (only after you've used it), antifire running out, poisoned or
venomed (and poison protection ending), stamina worn off, low ammo, out of runes, cannon low.

**Settings (Combat Alerts):** A switch for each, plus Spec Alert At, Low Ammo At and Cannon Low At.

---

## Slayer card

**What it does:**
- **On a task:** kills left, kill rate, time to finish, the item the task needs (red if you don't have it)
  and **Where To Go**: the wiki's places to kill it, each marked Multi / Cannon / Safespot (Wilderness
  places last), and the fastest ways to get to the first one.
- **No task:** the best Slayer master for your combat and Slayer level, any quest they need, where they
  are and the fastest ways there, and the next master you'll unlock (and what it needs).

- The monster's picture heads the card on a task, and the master's chathead with no task. A Konar task
  shows the area she assigned, and that place is listed first.

- **Task monsters are outlined** in the world. On a Konar task, monsters outside her assigned area aren't
  outlined, since kills there don't count.
- **Monsters that need a Slayer level** get a Slayer section on the hover card and PvM HUD (others don't): the Slayer level needed (red with
  "can't damage" if you're below it), the gear its task needs (earmuffs, nose peg, rock hammer, mirror
  shield...; red "missing" if you don't have it), which task it counts for ("on task" in green), and its
  Slayer XP. Hover one in the wrong place on a Konar task and it says "Wrong place: Konar wants these in
  ..., kills here won't count"; hover a Slayer monster that isn't your task and it shows your task.

**How to use:** It appears in the top-left. **Ctrl+Shift+S** hides or shows it (chat says which); it stays that
way after a restart.

**Settings (Slayer):** Show Task Tracker; Show/Hide Hotkey; Pictures; Kill Rate and Finish Time; Required
Item; Where To Go and Places Shown; How To Get There and Routes Shown; Master Card Without a Task; Next
Master; Slayer Info on Monsters; Highlight Task Monsters and its colour; Missing Item Warning; Superior Spawned and Task Complete alerts with Voice/Chat switches.

---

## Gemstone Crab

**What it does:** Near the crab's three mines in the Tlati Rainforest only: a HUD with the crab's health,
burrow countdown, whether you're attacking, the XP type you're getting, damage, DPS and last/max hit;
outlines on the crab, its shell (green when you can still mine it, red when you can't) and the cave
tunnels; a radar to find it. Alerts when it's about to burrow and when it has moved.

**Settings (Gemstone Crab):** Enable Crab Features; Low Health Alert (%); Show Last/Max Hit; the four
outline colours; Voice/Chat switches.

---

## Skilling hover cards

Hover a thing to see a card next to the cursor. Cards follow the game's left-click option, so an NPC
walking past doesn't change them. Requirement lines read "Requires: Level N" with the skill's icon (green
when you have it, red when you don't). With **Skilling > XP Rates on Hover** on, the wiki's XP/hr and your
own rate show while you're training that skill.

| Hover | You see | Setting |
|---|---|---|
| Tree | Level, XP, your axe (in its metal colour) and the best axe for you | Skilling > Skilling Hover Info |
| Rock | Level, XP, pickaxe | Skilling > Skilling Hover Info |
| Fishing spot | Each method (Net, Bait, Lure...) with the tool it needs (green if you have it) and each fish with its level | Skilling > Skilling Hover Info |
| Stall | Level, XP, the kind of loot | Skilling > Skilling Hover Info |
| Pickpocket NPC | Level, XP, never-fail level with the Ardougne cloak, stun damage | Skilling > Skilling Hover Info |
| Farming patch | Crop, stage, roughly when it's grown, diseased/dead, compost, watered, the farmer's payment, what you can plant | Farming > Farming Patch Hover |
| Hunter creature | Level, XP, the trap | Hunter > Creature Hover |
| Hunter trap spot (young tree, boulder, pit) | The trap it takes and what you can catch with it: the creatures nearby, else all of them, each with its level (green if you can) and XP | Hunter > Creature Hover |
| Bird house (Fossil Island) | Type, and when it'll be full | Hunter > Bird House Hover |
| Runecraft altar | Level, XP, runes per essence at your level, the next multiple | Runecraft > Altar Hover |
| Agility obstacle / shortcut | Course level, XP per lap, laps to your next level; shortcut level | Agility > Obstacle Hover |
| Range or fire | Each raw food you carry: what it cooks into, XP (or the level it needs), when it stops burning (with cooking gauntlets if worn), XP for the lot | Cooking > Range / Fire Hover |
| Anvil | For each bar you carry: the best item you can make, how many, the XP (and profit), the next unlock | Smithing > Anvil / Furnace Hover |
| Furnace | Each bar your ores make, how many (coal included), XP (and profit) | Smithing > Anvil / Furnace Hover |
| Sea charting spot | The task, what to do, level, XP, the riddle answer for mermaid dives | Sailing > Sailing Hover |
| Shipwreck | Level, salvage XP, what it gives, the best hook for you | Sailing > Sailing Hover |
| Trawling shoal | Level, depth, bait, fish | Sailing > Sailing Hover |

**Best Choice By (Skilling):** how cards pick the "best" thing to make. **Most XP** (default) or **Most
profit** at Grand Exchange prices; with profit on, the anvil and furnace cards add a Profit line.

---

## Timer pies

**What it does:** A small pie over a resource that empties as it runs out: green, then yellow, then red.
When a tree can fall on the next log it turns into a full red pie that pulses.

| Pie on | Shows | Setting |
|---|---|---|
| Trees being chopped (anyone's) | The Forestry timer: it drops while anyone chops and recovers when nobody does | Woodcutting > Tree Timer Pies |
| Motherlode veins, calcified rocks, Daeyalt essence, shooting stars | Time left (stars exactly, from the layer's health) | Mining > Mining Timer Pies |
| Fishing spots that moved while you were there | How soon they may move again | Fishing > Fishing Spot Pies |
| Your traps | Time until it collapses (green once something's caught) | Hunter > Trap Timer Pies |
| Fires lit nearby | How long it may burn | Firemaking > Fire Timer Pies |
| Shipwrecks you saw surface | Time until it sinks | Sailing > Shipwreck Pies |

**Good to know:** Pies draw under the bank and other interfaces. A tree already being chopped when you
arrived gets no pie (its time can't be known). Other players' logs can't be seen, so a tree someone else
is chopping can sit at "due" a little while before it falls. If you run a dedicated timer plugin (Tree
Despawn Timer, Fishing Spot Tracker, RuneLite's Hunter plugin...), our pie for that timer hides
(**Skilling > Use Other Timer Plugins**).

---

## Skilling alerts

All only for the resource you're working, and each with Voice/Chat switches in its skill's section.

| Alert | When | Setting |
|---|---|---|
| Tree fallen / rock depleted / fishing spot moved | Your resource is gone | Skilling > Resource Gone |
| Inventory full | Your inventory fills while skilling | Skilling > Inventory Full |
| Coin pouches full | At 28 (more with the Ardougne Diary) | Thieving > Coin Pouch Alert |
| Bird houses ready | 50 minutes after you seeded them | Hunter > Bird Houses Ready Alert |
| Batch complete | A run of Cooking, Smithing, Crafting, Fletching or Herblore finishes | Skilling > Batch Complete Alert |

---

## Skill tracker

*Brad's Skill Tracker: the sidebar panel (its own plugin once the multi-tool is split).*

**What it does:** A sidebar panel (the plugin's icon on RuneLite's sidebar) with a card per skill you
train, laid out like RuneLite's XP tracker: XP gained, XP left, actions left (from your last XP drop; not
for combat skills), XP/hr, XP/min, time to level, and a red/orange/green progress bar.

**How to use:**
- Right-click a card to pause/resume it, reset its rate, reset or hide it, set or clear a goal, or reset
  every skill.
- **Pin to screen**: right-click a card > Pin to screen puts that skill on the game screen as a movable
  overlay (level bar, XP/hr, gained, time to level, goal bar and suggested method). Only one at a time:
  pinning another replaces it. Right-click the overlay > Unpin, or the card > Unpin from screen. It stays
  pinned after a restart.
- A skill's rate clock **auto-pauses** after a few minutes without XP and resumes on your next XP.
- Level-ups are announced with the skill and level ("Woodcutting level... 45!").

**Settings (Skill Tracker):** a switch for each field; Actions Until Goal; Show Goal; Suggested Method,
Training Focus, Highlight Suggested Method and its colour; Auto-Pause Idle Skills and After
(minutes); Ignored Skills (comma-separated); Voice/Chat: Level Up.

---

## Goals and suggested training

**What it does:**
- Skills with a goal are pinned under **GOALS** at the top of the tracker (even before you train them this
  session), with a gold goal bar, XP left and time to reach it. Everything else is under **THIS SESSION**.
- A goal card shows the wiki training guide's method for your level (**Train: Oak trees**) and what to
  switch to before the goal (**At 35: Teak trees**). Every skill is covered: combat skills and Hitpoints
  use the melee or ranged guide, Prayer, Herblore and Sailing list their guide's methods, and **Slayer**
  suggests the best Slayer master for your levels (and the next one: "At Combat 100, Slayer 50:
  Duradel"). Questing is only suggested when the guide has nothing else for your level.
- When a level-up moves a goal onto a new method (or Slayer master), chat says so.
- Where that method is a tree, rock, stall, pickpocket NPC or Hunter creature, those are outlined in the
  world.
- **Train something else:** right-click a Hunter creature, pickpocket NPC, fishing spot, tree, rock or
  stall for a skill you have a goal in and choose **Track for goal**. That's outlined instead of the
  suggestion and the card says "Tracking: Ruby harvest (your pick)". **Untrack for goal** on it, or
  **Use suggested method** on the card's right-click menu, goes back to the suggestion. Only skills with
  a goal are ever outlined.
- For production skills (Cooking, Smithing, Fletching, Crafting, Herblore, Firemaking, Construction,
  Magic, Runecraft) and Prayer the card also says what to **Make** (or **Use**: bones, ashes, logs): the best thing you can make at your level from
  what's in your inventory and bank (most XP, or most profit with **Skilling > Best Choice By**). Its
  materials are outlined in your inventory and bank; hover one for "Suggested for your Cooking goal:
  Lobster". The bank counts once you've opened it this session.

**How to use:**
1. Click **Set Goal** at the top of the tracker (or right-click a card > Set goal...).
2. Pick the skill (the list shows your level in each; below it, your level, XP and any current goal) and
   type a target: a number up to 99 is a level, anything bigger is an XP amount.
3. Clear it from the card's right-click menu.

Goals are saved with your RuneScape profile, so they stay after a relaunch. To have them on another PC,
log in to a RuneLite account on both and turn on **Sync** for your profile (Profiles panel).

**Settings (Skill Tracker):** Suggested Method; **Training Focus**: Fastest XP (the guide's fastest
method), Other methods (the guide's others, often more AFK), Cheapest (for making things: the recipe with
the fewest GP per XP at GE prices; gathering skills are free so they get the fastest), or Money maker (the
most profitable wiki money making guide you meet the requirements for, e.g. "Money: Cutting yew logs
(~335K/hr)"). The Make: line follows it too; Highlight Suggested Method; Highlight Goal
Items; Highlight Colour;
Actions Until Goal (the actions field counts to the goal instead of the next level).

---

## Idle alerts

**What it does:**
- **AFK alert:** you were training (gaining XP), your character stopped, and you haven't clicked or typed
  since. Banking, shops, the GE and menus never set it off.
- **Idle logout warning:** before the game logs you out for inactivity (follows RuneLite's Logout Timer
  setting; in combat the game's 20-minute rule).

**Settings (Idle):** AFK Alert and Idle Alert After (seconds); Warn Before Logout (seconds); Repeat
Logout Warning, Repeat Every, Max Repeats; Voice/Chat switches.

---

## Issues? button

The **Issues?** button in the top-right corner of the skill tracker panel opens a new GitHub issue in your browser. Say
what you did and what you saw; a screenshot helps.
