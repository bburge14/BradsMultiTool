# Snuffy's Toolbelt: feature guide

Every feature, what it does, how to use it and where its settings are. Settings are in RuneLite's
configuration panel (the wrench icon) under **Snuffy's Toolbelt**; each section below names the settings
section to open. All sections except General start collapsed.

- [Alerts (all of them)](#alerts-all-of-them)
- [PvM HUD](#pvm-hud)
- [Monster hover card](#monster-hover-card)
- [Potion buffs](#potion-buffs)
- [Buff bar](#buff-bar)
- [Health and prayer](#health-and-prayer)
- [Combat alerts](#combat-alerts)
- [Slayer card](#slayer-card)
- [Gemstone Crab](#gemstone-crab)
- [Skilling hover cards](#skilling-hover-cards)
- [Resource timers](#resource-timers)
- [Skilling alerts](#skilling-alerts)
- [Skill tracker](#skill-tracker)
- [Goals and suggested training](#goals-and-suggested-training)
- [Tick counter and metronome](#tick-counter-and-metronome)
- [Idle alerts](#idle-alerts)
- [Issues? button](#issues-button)

---

## Alerts (all of them)

**What it does:** Every alert can speak (a pre-recorded voice line) and/or post a chat message. The chat
message uses the same words as the voice line, with details in brackets.

**How to use:**
- **General > Voice Alerts / Chat Messages** turn all voice or all chat on or off.
- **General > Voice Persona** picks one of 10 voices (Amy, Cori, Alan, Ryan, Arctic, L2 Arctic, John, Wise
  Old Man, TzHaar, Ghost) or **Random** (a different one each alert). Changing it plays a sample.
- Each alert also has its own **Voice: ...** and **Chat: ...** switch in its section. Turning a Voice
  switch on plays that line as a preview.
- **General > Same Voice For All** (on by default): with several of Snuffy's plugins installed, changing the
  voice or volume in one changes it in the others too, and one you install later picks it up. Turn it off in a
  plugin to give that plugin its own voice.

**Good to know:** No alert fires before you click "Click here to play", and anything already true when
you log in (low prayer, poisoned...) isn't announced.

---

## PvM HUD

**What it does:** When you attack a monster, a HUD shows its HP bar, whether it's aggressive *to you* (with
a tolerance timer), its weakness (style icon, or the element's Strike spell icon for magic), your combat
style, DPS, and your last and max hit. It stays up for a few seconds after the fight.
- **Monster effects:** what's on the monster: Poisoned, Venomed, Burning (from their hitsplats) and Frozen with
  time left (a freeze spell's impact), and its Defence after your special attacks lower it (Dragon warhammer
  30%, Elder maul 35%, Bandos godsword by the damage, Arclight/Emberlight), e.g. "Defence: 112 (-48)",
  estimated from the wiki's Defence level.

**How to use:** Attack anything. The HUD can be moved like any RuneLite overlay (hold Alt and drag).
Right-click the HUD > **Reset** clears this session's damage and DPS.

**Settings (PvM HUD):** Enable PvM HUD; Show Monster Effects; DPS Mode (Per Monster, Per Type, or the whole
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

**What it does:** Each combat potion boost shows on the PvM HUD and the Gemstone Crab card with its icon and
time left (divine potions to the second). Alerts fire shortly before a boost runs out and when it has. In both
PvM/Slayer and the GemCrab Tracker (the same settings section in each); with both installed separately, only
one of them says the alerts.

**Settings (Potion Buffs):** Show Buffs on HUD; Flash When Expired; Warn Before Ending (seconds);
Repeat Expired Alert, Repeat Every, Max Repeats; Voice/Chat switches.

---

## Buff bar

**What it does:** Your buffs and debuffs as a row of icons, like RS3's: each potion boost (super combat,
divine, ranging...) with its boost and time left, antifire / super antifire, stamina, antipoison / antivenom,
and any other stat boosted above your level (Hitpoints from an anglerfish) in green; poisoned, venomed and stats
drained below your level in red. Hover an icon for its name. With it on, the PvM HUD leaves the potion rows to
it (the running-out and expired alerts carry on).

**How to use:** It sits above the chat box on the right; move it with Alt + drag.

**Settings (Buff Bar):** Show Buff Bar; Show Debuffs; Layout (Horizontal or Vertical); Icon Size.

---

## Health and prayer

**What it does:** Alerts when health or prayer drops to your threshold (as a % of your level), flashes the
orb by the minimap, and shows an "OUT OF PRAYER" warning above your character (its size: **Prayer > Out
of Prayer Text Size**).

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
- **On a task:** kills left, kill rate, time to finish, the **protection prayer** to use (from how the task's
  monsters attack, per the wiki: e.g. Fire giants, Protect from Melee; shown when most of them attack the same
  way), the item the task needs (red if you don't have it) and **Where To Go**: the wiki's places to kill it,
  each marked Multi / Cannon / Safespot (Wilderness places last), and the fastest ways to get to the first
  one. Right-click the card > **Show all / fewer places** to list every place instead of Places Shown.
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

**Settings (Slayer):** Show Task Tracker; Show/Hide Hotkey; Pictures; Kill Rate and Finish Time; Protection
Prayer; Required Item; Where To Go and Places Shown; How To Get There and Routes Shown; Master Card Without a Task; Next
Master; Slayer Info on Monsters; Highlight Task Monsters and its colour; Missing Item Warning; Superior Spawned,
Task Complete, Task Halfway and Task Nearly Done (at 10 left by default, 0 off) alerts with Voice/Chat switches.

---

## Gemstone Crab

**What it does:** Near the crab's three mines in the Tlati Rainforest only: a HUD with the crab's health,
burrow countdown, whether you're attacking, the XP type you're getting, damage, DPS and last/max hit, and your
potion buffs with their time left (red in the last minute) and Prayer/Health LOW rows, the same in the Toolbelt
and on its own; outlines on the crab, its shell (green when you can still mine it, red when you can't) and the cave
tunnels; a radar to find it. Alerts when it's about to burrow and when it has moved, and the potion alerts
(see Potion buffs). Right-click the crab card > **Reset** clears its damage and DPS.

**Gear Check:** the crab can't block and only hits for 1, so only *damage* matters there (XP is 3.5 per
damage, whatever skill you train). The card shows your gear's damage bonus for the style you're fighting with
(Str +72, Ranged str +45 or Magic dmg +15%; red at none), and **Off-style:** any worn item whose damage bonus is
for another style (a Necklace of anguish while meleeing), since that slot could be adding damage. Armour's
defence doesn't matter, so lower-tier gear with more damage beats better armour without it.

**Settings (Gemstone Crab):** Enable Crab Features; Low Health Alert (%); Gear Check; Low Prayer/HP (%); Show Last/Max Hit; the four
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
| Libation bowl (the Teomat) | Your blessed bone shards, the XP they give (5 each, 6 with sunfire wine), jugs of wine needed (one per 400) and carried, prayer points needed, the level they take you to | Skilling > Skilling Hover Info |
| Fishing spot (every kind: net/bait, lure/bait, cage/harpoon, big net, karambwan, karambwanji, anglerfish, dark crab, sacred/lava/infernal eels, minnows, aerial, squid, boat spots...) | Each way to fish it with the tool it needs (green if you have it) and each catch with its level and XP | Skilling > Skilling Hover Info |
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
| Sea monster (sharks, rays, krakens, sea birds...) | Combat level, HP, aggressive, max hit with no keel, the keel it can't hurt you through, bounty task parts and the Sailing level for each; "Harvest" once defeated | Sailing > Sailing Hover |
| Current duck | Drifting or stopped, and once stopped how long until it despawns | Sailing > Sailing Hover |

**Best Choice By (Skilling):** how cards pick the "best" thing to make. **Most XP** (default) or **Most
profit** at Grand Exchange prices; with profit on, the anvil and furnace cards add a Profit line.

---

## Resource timers

**What it does:** A small pie over a resource that empties as it runs out: green, then yellow, then red.
When a tree can fall on the next log it turns into a full red pie that pulses.

| Pie on | Shows | Setting |
|---|---|---|
| Trees being chopped (anyone's) | The Forestry timer: it drops while anyone chops and recovers when nobody does | Woodcutting > Tree Timers |
| Motherlode veins, calcified rocks, Daeyalt essence, shooting stars | Time left (stars exactly, from the layer's health) | Mining > Mining Timers |
| Fishing spots that moved while you were there (spots that were already there when you arrived, or came into view as you walked up, get one once they move) | Spots move at a random time 2.5-5 minutes after they appear, so this shows the window: green until the earliest it can move, then a red ring filling up to the latest; hover for the times. Above it, how long ago it appeared ("1:42") and the Fishing icon, ringed in gold on the newest spot (the least likely to move soon) with **Mark Newest Spot** on (off by default); the spot you fish is outlined in **Your Spot Colour**; and each gets a minimap dot (green, then orange to red). A spot that outlives 5 minutes without being seen to move starts over | Fishing > Fishing Spot Timers, Mark Newest Spot, Minimap Dots, Your Spot Colour |
| Your traps | Time until it collapses (green once something's caught) | Hunter > Trap Timers |
| Fires you cook on (lit while you were nearby) | Fires go out at a random time 60-119 s after lighting: green until the earliest, then a red ring filling up to the latest; hover for the times. Off by default | Firemaking > Fire Timers |
| Forester's Campfires | Time left (up to 3 minutes): set by the log that made it, topped up by each log you add, corrected when you Check it | Firemaking > Campfire Timers |
| Shipwrecks you saw surface | Time until it sinks | Sailing > Shipwreck Timers |

**Good to know:** Fishing spots and fires go at a random time the game picks (Jagex: fishing spots 250-530
ticks), so no plugin can count down to the moment; their timers show the window (fire timers are off by default). Each timer only shows while you're training its skill (an XP drop in it in the last two
minutes): trees while you chop, rocks while you mine, fires and campfires while you cook or burn logs.
Turn on **Skilling > Timers When Not Training** to see them all the time. Above every timer sit its time (time left; a star's layer health; a trap's "Caught"; for fishing spots and fires,
how long ago they appeared) and the skill's icon (**Skilling > Timer Text**, **Timer Skill Icon**).
**Skilling > Timer Opacity** sets how solid they are (60% by default). **Skilling > Timer Style** picks the look:
*Tile, icon and timer* (the default) outlines the tile in the **Zone Colour** with a picture of what it gives (logs, ore,
the best fish your level can catch there) and the pie over the picture; *Timer only* is the floating timer. Where RuneLite's Fishing plugin already draws its spot icons or tiles, or its
Motherlode Mine plugin its vein icons, ours leave the icon or outline out and only add the timer. Pies draw under the bank and other interfaces. A tree already being chopped when you
arrived gets no pie (its time can't be known). Other players' logs can't be seen, so a tree someone else
is chopping can sit at "due" a little while before it falls. If you run a dedicated timer plugin (Tree
Despawn Timer, Fishing Spot Tracker, RuneLite's Hunter plugin...), our pie for that timer hides
(**Skilling > Use Other Timer Plugins**).

---

## Skilling alerts

All only for the resource you're working, and each with Voice/Chat switches in its skill's section.

| Alert | When | Setting |
|---|---|---|
| Tree fallen / rock depleted / fishing spot moved | Your resource is gone ("The tree has fallen."); not for level 1 trees and rocks, which go after one log or ore | Skilling > Resource Gone |
| Inventory full | Your inventory fills while skilling | Skilling > Inventory Full |
| Coin pouches full | At 28 (more with the Ardougne Diary) | Thieving > Coin Pouch Alert |
| Bird houses ready | 50 minutes after you seeded them | Hunter > Bird Houses Ready Alert |
| Batch complete | A run of Cooking, Smithing, Crafting, Fletching or Herblore finishes (3+ items in a row; one-off XP like quest rewards and lamps doesn't count) | Skilling > Batch Complete Alert |

---

## Skill tracker

*Snuffy's Skilling Helper: the sidebar panel (its own plugin once the Toolbelt is split).*

**What it does:** A sidebar panel (the plugin's icon on RuneLite's sidebar) with a card per skill you
train, laid out like RuneLite's XP tracker: XP gained, XP left, actions left (from your last XP drop; not
for combat skills), XP/hr, XP/min, time to level, and a red/orange/green progress bar.

**How to use:**
- Right-click a card to pause/resume it, reset its rate, reset or hide it, set or clear a goal, or reset
  every skill (just the tracker's skills: PvM's HUD and the crab card have their own Reset).
- **Pin to screen**: click the pin in a card's top-right corner (or right-click > Pin to screen) puts that skill on the game screen as a movable
  overlay (level bar, XP/hr, gained, time to level, actions left, goal bar, suggested method and what to
  make or burn from your items). Only one at a time:
  every card has a pin, solid on the pinned skill and faint on the others; clicking a faint one moves the pin
  there (its tooltip says which skill it takes over from). Click the solid pin,
  right-click the overlay > Unpin, or the card > Unpin from screen to remove it. It stays pinned after a
  restart, and its card stays in the sidebar the whole time, even before you've trained it this session or
  after a Reset.
- A skill's rate clock **auto-pauses** after a while without XP (**Auto-Pause After**: 10 seconds to an hour, 5 minutes by default) and
  resumes on your next XP.
- Level-ups are announced with the skill and level ("Woodcutting level... 45!").

**Settings (Skill Tracker):** a switch for each field; Actions Until Goal; Show Goal; Suggested Method,
Training Focus, Highlight Suggested Method and its colour; Auto-Pause Idle Skills and After
(minutes); Ignored Skills (comma-separated); Voice/Chat: Level Up.

---

## Goals and suggested training

**What it does:**
- Skills with a goal are pinned under **GOALS** at the top of the tracker (even before you train them this
  session), with a gold goal bar, XP left and time to reach it. Everything else is under **THIS SESSION**.
- **Needs:** the tools the suggestion takes (an axe or pickaxe, the trap, a tinderbox, or the recipe's
  knife, chisel, hammer, needle, mould...), green if you carry, wear or bank one (the bank as last opened)
  and red if not.
- Suggestions skip methods you can't do yet: the guide's required quests (checked against your quest
  log) and other skills' levels (Sulphur Naguas need 48 Slayer). At low levels the card also lists
  **Quest XP:** quests the guide recommends for that skill's XP that you haven't done (hover for all).
- Each skill can aim for something different: right-click it in the tracker > **Suggest** (or pick
  **Suggest** when setting its goal) and choose Fastest, AFK / Other, Cheapest or Money Maker, or
  Default to follow the setting. Saved with your goals, per account.
- A goal card shows the wiki training guide's method for your level and, when it changes before the
  goal, the level it lasts to (**Train: Teak trees to 62**). Hover it for every step on the way:
  50-62: Teak trees, 62-90: Blisterwood tree, 90-99: Sulliuscep. The pinned
  overlay shows the current leg ("Train: Teak trees (to 62)"). Every skill is covered: combat skills and Hitpoints
  use the melee or ranged guide, Prayer, Herblore and Sailing list their guide's methods, and **Slayer**
  suggests the best Slayer master for your levels (and the next one: "At Combat 100, Slayer 50:
  Duradel"). Questing is only suggested when the guide has nothing else for your level.
- It names the actual thing to do: where the guide has a table by level it picks the row for your level
  (**Train: Strength potion (Tarromin potion (unf) + Limpwurt root)**, "Cutting gems: Ruby", "Burning logs:
  Maple logs", "Anvil smithing: Iron warhammer"), and otherwise what the method trains on ("Fly fishing: Trout,
  Salmon"). Hover the line for what the guide says about the method. Where the guide's methods overlap, the one
  that takes over at your level wins, and a method that trains two skills at once (Drift net fishing: Fishing
  and Hunter) only when nothing else fits.
- Only what you can do: a method (or a row of its table, like Serum 207) that needs a quest is left out until
  you've done it, or just started it where the guide says partial completion is enough (Barbarian Fishing,
  chinchompas, Serum 207); quests are read from the game's quest list.
- **Where:** under the Train line, where the guide says to do it ("Where: Prifddinas (+4 more)" for fly fishing,
  "Kruk's Dungeon" for chinning, "Piscatoris Fishing Colony" for monkfish), also on the pinned overlay; hover the
  Train line for all of them. Only places the guide names (and only real wiki location pages), so some methods
  have none.
- When a level-up moves a goal onto a new method (or Slayer master), chat says so.
- Where that method is a tree, rock, stall, pickpocket NPC or Hunter creature, those are outlined in the
  world.
- **Train something else:** right-click a Hunter creature, pickpocket NPC, fishing spot, tree, rock or
  stall for a skill you have a goal in, or any monster while a combat skill has a goal, and choose
  **Track for goal** (offered whenever that skill has a goal; a monster, the Gemstone Crab included, counts for
  every combat skill with a goal, and its kills are counted, except the crab's, which burrows rather than dies). That's outlined instead of the
  suggestion and the card says "Tracking: Ruby harvest (your pick)". **Untrack for goal** on it, or
  **Use suggested method** on the card's right-click menu, goes back to the suggestion. Only skills with
  a goal are ever outlined.
  A **fishing spot** opens a list of the ways to fish it, each with what it catches, the levels and what you need
  ("Fly fishing: Trout, Salmon (Fly fishing rod + Feather)": white when your level catches all of it, yellow when only some, red when none yet; your pick green). The card
  then says "Tracking: Fly fishing: Trout, Salmon (Fly fishing rod + Feather)", only spots that offer that way of
  fishing are outlined, and the XP each is the average of the fish you can catch that way.
- **It notices what you're doing:** train a goal skill on something that isn't the suggestion or your pick (a
  different fishing spot or way of fishing it, tree, rock, stall, pickpocket NPC, Hunter creature, or monster for
  a combat goal) and after 5 XP drops from it (15 hits in combat) the skill's tracker card asks "You're training
  on: Fly fishing: Trout, Salmon (Fly fishing rod + Feather). Track it for your goal?" with **Track it** and
  **Dismiss**, and a chat message says so. Dismissed ones aren't asked again until you log out. The fishing way is
  worked out from the XP each catch gives at the spot you clicked.
- **Track an item:** right-click an item in your inventory or bank that a goal skill uses (logs for
  Firemaking or Fletching, raw fish for Cooking, bones for Prayer) and choose **Track for goal**: the card
  then works out what to make, the XP and the actions left from that item only, even once you've run out
  ("(you have none)"). An item used for several things (logs: arrow shafts and each bow; a bar: everything you
  can smith from it) opens a list instead: **Best I can make** (works it out from your items), or one recipe
  at your level ("Longbow (u)"), and the card says "Tracking: Longbow (u) (from Logs)". For Prayer it opens a list of how you train it, and your pick is kept:
  - bones: **Bury** (100%), **Gilded altar** (350%, both burners lit), **Chaos altar** (the Wilderness
    one: 350%, and half your bones are kept, so it's worth 7x per bone) or **Ectofuntus** (400%; you'll
    need a pot and a bucket of slime per bone, listed as tools);
  - blessed bone shards: **Blessed wine** (5 XP each) or **Sunfire wine** (6). Until you pick, it goes by
    the wine you carry.
  The card shows "Tracking: Dragon bones (Gilded altar)" and "Offer: Dragon bones (Gilded altar)". The
  current pick is green in the list, and **Untrack for goal** at its bottom goes back to the suggestion
  (the bones' method stays for next time).
- For production skills (Cooking, Smithing, Fletching, Crafting, Herblore, Firemaking, Construction,
  Magic, Runecraft) and Prayer the card also says what to **Make** (or, by the item you use, **Cook**:
  Raw karambwan, **Burn**: logs, **Bury**/**Offer**: bones, **Scatter**: ashes) and how many you have: the best thing you can make at your
  level from what's in your inventory and bank (most XP, or most profit with **Skilling > Best Choice By**). Its
  materials are outlined in your inventory and bank; hover one for "Suggested for your Cooking goal:
  Lobster". The bank counts once you've opened it this session.

**How to use:**
1. Click **Set Goal** at the top of the tracker (or right-click a card > Set goal...; a skill with a goal has Modify goal... instead, which starts from
   its goal and keeps the goal bar's progress).
2. Pick the skill (the list shows your level in each; below it, your level, XP and any current goal) and
   type a target: a number up to 99 is a level, anything bigger is an XP amount.
3. Clear it from the card's right-click menu (Clear goal only shows while the skill has a goal).

Goals are saved with your RuneScape profile, so they stay after a relaunch. To have them on another PC,
log in to a RuneLite account on both and turn on **Sync** for your profile (Profiles panel).

**Settings (Skill Tracker):** Suggested Method; **Training Focus** (for skills without their
own): Fastest (the guide's fastest
method), AFK / Other (the guide's other methods, often more AFK), Cheapest (for making things: the recipe with
the fewest GP per XP at GE prices; gathering skills are free so they get the fastest), or Money Maker (the
most profitable wiki money making guide you meet the requirements for, e.g. "Money: Cutting yew logs
(~335K/hr)"). The Make: line follows it too; Highlight Suggested Method; Highlight Goal
Items; Highlight Colour;
Actions Until Goal (the actions field counts to the goal instead of the next level; when the suggested
method changes before the goal it counts to the end of the step you're on). Hover the count: it says what it
counts to, and with a goal shows both, "Next level: 120 actions" and "Your goal: 4,300 actions", marking the
one shown.
Once you're getting XP, the count uses the average of your last 30 XP drops in the skill (shown with a "~";
hover for the XP each), so a method that gives different XP each time (trout and salmon, 17 and 18 XP for a
17.5 XP action) doesn't make it jump around.
Before your first XP drop in the skill, the count uses the XP per action of what you're suggested (or
picked with Track for goal, or can make from your items), shown with a "~" (a method catching several fish, like fly fishing's trout and salmon, counts the average
XP of those you can catch). Attack, Strength, Defence,
Ranged and Hitpoints goals count **kills** instead ("Kills: ~240"): XP per kill from the hitpoints of
your Track for goal pick (else the monster you last fought, else the suggested one), at the XP per
damage of your combat style: 4 in the skill it trains, 4/3 each to Attack, Strength and Defence in
Controlled, 2 each to Ranged and Defence in Longrange, 1 to Defence by defensive casting, 4/3 to Hitpoints
(a style that doesn't train the skill counts as 4, as if you switched to one); hover the count to see which
monster. A Gemstone Crab has no kill count.
Combat goal cards (and the pinned overlay) also show your **Kill rate** on your Track for goal pick (else what
you killed last), e.g. "Kill rate: Sand Crab: 52/hr (1:09 each)", over your kills in the last hour once you've
killed it twice. Potions and better gear show up here as faster kills; they don't change the kills a goal needs
(combat XP comes from damage, and a kill can only take its hitpoints).

---

## Tick counter and metronome

In **Snuffy's Tick Counter** and the Toolbelt.

**What it does:** counts game ticks in a cycle (1, 2, 3, 1, 2, 3...) for tick-based skilling and for timing
your attacks, and can time your clicks against the tick. It only shows and sounds the ticks and reads your
clicks; it never clicks, changes a menu or does anything for you.
- **Count Over Head:** the count just above the top of your character as the game draws it, so it stays clear of
  your character from any camera angle.
- **Tick Boxes:** a box for each tick of the cycle (3 boxes for a 3-tick cycle), in a row or a column, the
  current one lit. Move them with Alt + drag.
- **Tick Bar:** fills smoothly from left to right over each tick and snaps back on the next, so you can see the
  tick coming instead of reacting to a flash. Move it with Alt + drag.
- The first tick of each cycle is in its own colour (green by default).
- **Metronome:** a game sound on every tick, and a different one on the first tick of the cycle. The **Tone**
  is Plop, Bell, Anvil, Boop, Coins, Pickaxe and axe, or Tinderbox. It can play on the first tick only.
- **Follow Attack Speed:** the cycle becomes your weapon's attack speed (one tick faster on Rapid), and the
  count starts again at 1 each time you attack, so 1 is your attack tick.
- **Click Timing:** each click shows how long after the tick it landed (e.g. "310ms") over your head: green
  in the ideal zone, white elsewhere before Late After, red when late. The tick bar shades the **ideal zone**
  green (**Ideal From** to **Ideal To**, 100-350ms by default) and the late part orange, and marks your last
  click. A summary of your last 50 clicks shows with the bar: "Last 50: 44 on time (30 ideal), 4 late, 2
  missed, avg 310ms".
- **Click Sounds:** a ding for each click on time and the **Late Sound** (Vwoop, Boing, Thud or Door) for a
  late one. **Late After** (450ms by default) sets when a click counts as late: with your ping it may land on
  the next tick.
- **Click Every Tick** (for 1-ticking, where you click once every tick): once you've started clicking, a tick
  that passes without a click counts as missed and plays the Late Sound. Leave it off for methods that don't
  click every tick.
- Nothing shows or plays until you're in the game (past loading and the welcome screen).

**How to use it:** open **Tick Counter**, turn on what you want to see, and set **Ticks Per Cycle** (e.g. 3 for
3-tick skilling), or turn on **Follow Attack Speed** for combat. Turn on **Metronome** to hear it. For 1-ticking, set Ticks Per Cycle to 1 and
turn on the Tick Bar, Click Timing, Click Sounds and Click Every Tick.

**Settings (Tick Counter):** Count Over Head; Tick Boxes and Box Layout (Horizontal or Vertical); Tick Bar;
Ticks Per Cycle (1-10); Follow Attack Speed; Tick Colour and First Tick Colour; Over Head Text Size; Box Size;
Bar Width; Metronome, Tone, First Tick Only, Volume (also the click sounds; 0 uses the game's sound effect
volume); Click Timing; Click Sounds; Late Sound; Late After; Ideal From and Ideal To; Click Every Tick.

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
