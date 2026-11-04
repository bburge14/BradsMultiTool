# Snuffy's Toolbelt: feature guide

Every feature, what it does, how to use it and where its settings are. Settings are in RuneLite's
configuration panel (the wrench icon) under **Snuffy's Toolbelt**; each section below names the settings
section to open. In the Toolbelt, sections are named after their plugin and
grouped in order: About, General, Idle, then **PvM:** (HUD, Buff Bar, Potion Buffs, Health, Prayer, Combat Alerts,
Supply Alerts), **Slayer:** (Card, Monsters, Alerts), **GemCrab:** (Crab, Colours), **Skilling:** (Tracker, Goals,
Hover Cards, Resource Timers, Alerts, Woodcutting, Mining, Fishing, Hunter, Other Skills for the rest, and Minigames), and
**Tick:** (Counter, Boxes, Bar, Metronome, Click Timing), and **Planner:** (Today, Weekly, Timers, Slayer, Drops,
Notes & To-Do, Calculator). Each separate plugin has the same sections without the
plugin's name, and the Skilling Helper gives every skill its own. All start collapsed except About and General.

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
- [Hunters' Rumours card](#hunters-rumours-card)
- [Skill tracker](#skill-tracker)
- [Goals and suggested training](#goals-and-suggested-training)
- [Tick counter and metronome](#tick-counter-and-metronome)
- [Planner](#planner)
- [Idle alerts](#idle-alerts)
- [Clue drops](#clue-drops)
- [Agility](#agility)
- [Minigames: Wintertodt, Tempoross, Guardians of the Rift](#minigames-wintertodt-tempoross-guardians-of-the-rift)
- [Issues? button](#issues-button)

---

## Alerts (all of them)

**What it does:** Every alert can speak (a pre-recorded voice line) and/or post a chat message. The chat
message uses the same words as the voice line, with details in brackets.

**How to use:**
- **General > Voice Alerts / Chat Messages** turn all voice or all chat on or off.
- **General > Voice Persona** picks a voice, or **Random** (a different one each alert); changing it plays a sample.
  The plugins on the Plugin Hub have four: Amy, Cori, Alan and John. Builds from this repository (the launcher) also
  have Arctic, Wise Old Man, TzHaar and Ghost.
- Each alert also has its own **Voice: ...** and **Chat: ...** switch in its section. Turning a Voice
  switch on plays that line as a preview.
- **General > Same Voice For All** (on by default): with several of Snuffy's plugins installed, changing the
  voice or volume in one changes it in the others too, and one you install later picks it up. Turn it off in a
  plugin to give that plugin its own voice.
- **General > Speak Only When Unfocused** (off by default): alerts are only spoken while the RuneLite window
  isn't the one you're using (you're in a browser, say); chat messages still post. Like RuneLite's own
  notifications, which by default don't show while the client is focused. The preview a Voice switch plays still
  speaks.

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
- **Loot value:** "Loot: 1.25M (850K/h)": the Grand Exchange value of what the monsters you killed dropped this
  session (RuneLite's kill loot, priced as its Loot Tracker does), and per hour from your first hit (shown after a
  minute). Kill drops only: chests, raids and clue caskets aren't counted.

**How to use:** Attack anything. The HUD can be moved like any RuneLite overlay (hold Alt and drag).
Right-click the HUD > **Reset** clears this session's damage, DPS and loot.

**Settings (PvM HUD):** Show PvM HUD; Show Monster Effects; DPS Mode (Per Monster, Per Type, or the whole
session); Linger After Combat; Show Weakness / DPS / Last Hit / Max Hit / Aggro Status; Show Loot Value.

---

## Monster hover card

**What it does:** Hover an attackable monster to see its HP, weakness (green if your current style hits it,
red if not), what it attacks with, and whether it will be aggressive to you. Aggression follows the
game's rules: monsters ignore players over twice their level (except in the Wilderness), God Wars
followers go by your god items, vyre noble clothing and greegrees protect you. It also shows the wiki's
**max hit** for each of its attacks (up to three, e.g. "60 (melee), 35 (ranged)") and its **attack speed**
("4 ticks (2.4s)"): how often it attacks, never a countdown to its next attack. With a kill goal set on that
monster, a **Kill Goal** line shows your progress ("37 / 200").

**How to use:** Hover a monster. The card follows the game's left-click option, so it only shows when
the top-left tooltip names the monster.

**Settings:** PvM HUD > Monster Hover Card, Show Max Hit and Speed. With **Skilling Hover Cards > Skill Cards With Auto Retaliate Off** on
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
overload (one icon for all its boosts; smelling salts in the Tombs of Amascut), prayer regeneration potion,
staff of the dead's special (a minute, gone when you take the staff off), vengeance (ready to rebound, and its
30-second cooldown), the imbued / saturated heart's cooldown, and any other stat boosted above your level
(Hitpoints from an anglerfish) in green; poisoned, venomed, **frozen** (the spell's full length, "~", gone as soon
as you move) and **teleblocked** with time left, and stats drained below your level in red. Timers read the same
game values as RuneLite's Timers and Buffs plugin (overload is to about 15 seconds, so it has a "~"). Hover an icon for its name. With it on, the PvM HUD leaves the potion rows to
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
venomed (and poison protection ending), stamina worn off, low ammo, out of runes, cannon low. Two more, off by
default:
- **Wrong Protection Prayer:** a monster attacking you only attacks one way (every attack style the wiki lists
  is stopped by the same prayer, e.g. a gargoyle: melee) and the protection prayer you have on is a different
  one. Said once per monster (again if you switch to another wrong one). With no protection prayer on it stays
  quiet, and monsters with more than one style (or dragonfire, typeless hits) never count: it never guesses what
  a monster does next.
- **Unpotted Reminder:** you start a fight (after a minute without one) with no combat boost. **Boosts That
  Count:** Any stat (Attack, Strength, Defence, Ranged or Magic above your level) or Your style (Attack
  or Strength for melee, Ranged, Magic). At most once every two minutes.
- **Death alert:** when you die, a voice line and a chat message saying where (coordinates; "in the Wilderness" or
  "in an instance" when so). **Gravestone Timer:** an info box (bones icon) counting down the 15 minutes your
  gravestone keeps your items; like the game, it only counts while you're logged in. Only after a death where you
  lost items, and not in instances (Death's Office or the boss's reclaim) or the Wilderness (no gravestone); it goes
  when the time's up or you're back at the spot. With a Death Indicator plugin on, the clash note says so.

**Settings (Combat Alerts):** A switch for each, plus Spec Alert At, Low Ammo At and Cannon Low At, Boosts That
Count; Death Alert, Gravestone Timer; Voice/Chat switches.

---

## Slayer card

**What it does:**
- **On a task:** kills left, kill rate, time to finish, the **protection prayer** to use (from how the task's
  monsters attack, per the wiki: e.g. Fire giants, Protect from Melee; shown when most of them attack the same
  way), the item the task needs (red if you don't have it) and **Where To Go**: the wiki's places to kill it,
  each marked Multi / Cannon / Safespot (Wilderness places last), and the fastest ways to get to the first
  one. Right-click the card > **Show all / fewer places** to list every place instead of Places Shown.
- **Points and streak** (on a task and without one): your Slayer points and task streak, read like RuneLite's
  Slayer plugin (Krystilia and Mortimer keep their own streak). When the next task is a 10th, 50th, 100th, 250th or
  1000th it says so in gold with its bonus ("15x points, pick your master"), and the **Next Task Is a Milestone**
  alert says it the moment you finish the task before, so you can get the milestone from the master that gives the
  most points.
- **Supplies Used:** food eaten and potion doses drunk since the task started (from the game's "You eat" / "You
  drink some of your" messages), kept if you log out and back in on the same task.
- **Kill Goals:** right-click any monster > **Set kill goal** and type how many to kill (in the chat box). Each
  kill (a monster you hit that dies) counts; the card lists your goals with their progress (unfinished first),
  the monster's hover card shows its own, and **Kill Goal Reached** says when you get there. **Clear kill goal**
  on the monster's menu removes one; the card's right-click > **Clear finished kill goals** removes the done ones.
  Saved with your RuneScape profile. Works on and off task.
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
- **Finish-off marker:** a lizard, small or desert lizard (ice cooler, at 4 HP or less), gargoyle (rock hammer,
  9), rockslug (bag of salt, 4) or mutated / ancient zygomite (fungicide spray, 7) gets the item's picture over its
  head once it's low enough to finish, worked out from its health bar and the wiki's hitpoints (so only once the
  bar shows). RuneLite's Slayer plugin marks its task monsters the same way (Show Monster Weakness, on by
  default; not lizards): while it does, ours leaves those to it and still marks lizards and off-task ones.

**How to use:** It appears in the top-left. **Ctrl+Shift+S** hides or shows it (chat says which); it stays that
way after a restart.

**Settings (Slayer):** Show Slayer Card; Show/Hide Hotkey; Pictures; Kill Rate and Finish Time; Protection
Prayer; Required Item; Where To Go and Places Shown; How To Get There and Routes Shown; Master Card Without a Task; Next
Master; Points and Streak; Supplies Used; Kill Goals; Slayer Info on Monsters; Highlight Task Monsters and its
colour; Finish-Off Marker; Missing Item Warning; Superior Spawned, Task Complete, Task Halfway, Task Nearly Done (at
10 left by default, 0 off) and Next Task Is a Milestone alerts, and Kill Goal Reached, with Voice/Chat switches.

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

**Settings (Gemstone Crab):** Crab HUD and Highlights; Low Health Alert (%); Gear Check; Low Prayer/HP (%); Show Last/Max Hit; the four
outline colours; Voice/Chat switches.

---

## Skilling hover cards

Hover a thing to see a card next to the cursor. Cards follow the game's left-click option, so an NPC
walking past doesn't change them. Requirement lines read "Requires: Level N" with the skill's icon (green
when you have it, red when you don't). With **Skilling Hover Cards > XP Rates on Hover** on, the wiki's XP/hr and your
own rate show while you're training that skill.

| Hover | You see | Setting |
|---|---|---|
| Tree | Level, XP, your axe (in its metal colour) and the best axe for you | Skilling Hover Cards > Show Hover Cards |
| Rock | Level, XP, pickaxe | Skilling Hover Cards > Show Hover Cards |
| Libation bowl (the Teomat) | Your blessed bone shards, the XP they give (5 each, 6 with sunfire wine), jugs of wine needed (one per 400) and carried, prayer points needed, the level they take you to | Skilling Hover Cards > Show Hover Cards |
| Fishing spot (every kind: net/bait, lure/bait, cage/harpoon, big net, karambwan, karambwanji, anglerfish, dark crab, sacred/lava/infernal eels, minnows, aerial, squid, boat spots...) | Each way to fish it with the tool it needs (green if you have it) and each catch with its level and XP | Skilling Hover Cards > Show Hover Cards |
| Stall | Level, XP, the kind of loot | Skilling Hover Cards > Show Hover Cards |
| Pickpocket NPC | Level, XP, never-fail level with the Ardougne cloak, stun damage | Skilling Hover Cards > Show Hover Cards |
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

**What it does:** Shows how long a resource has left. Trees and rocks (Woodcutting and Mining) get a plain pie on
the tree or rock, like RuneLite's own. With the default tile style a fishing spot's tile outline is its timer
(green, then yellow, then red), with the fish pictures; traps, fires and campfires keep a small pie over their
picture.
When a tree can fall on the next log it turns into a full red pie that pulses.

| Pie on | Shows | Setting |
|---|---|---|
| Trees being chopped (anyone's) | The Forestry timer: it drops while anyone chops and recovers when nobody does | Woodcutting > Tree Timers |
| Depleted rocks (any rock you mine out while you're there: copper to runite, lead, nickel, gem rocks, amethyst...) | Time until the ore is back, exactly: the game announces each rock's respawn time to the client, and that's what it counts down (as RuneLite's Mining plugin does): a pie on the rock that empties as the ore comes back (red, yellow, then green), like RuneLite's Mining plugin | Mining > Mining Timers |
| Motherlode veins, calcified rocks, Daeyalt essence, shooting stars | Time left (stars exactly, from the layer's health) | Mining > Mining Timers |
| Fishing spots that moved while you were there (spots that were already there when you arrived, or came into view as you walked up, get one once they move) | Spots move at a random time 2.5-5 minutes after they appear, so this shows the window: green until the earliest it can move, then a red ring filling up to the latest; hover for the times. Above it, how long ago it appeared ("1:42") and the Fishing icon, ringed in gold on the newest spot (the least likely to move soon) with **Mark Newest Spot** on (off by default); and the spot you fish is outlined in **Your Spot Colour**. A spot that outlives 5 minutes without being seen to move starts over | Fishing > Fishing Spot Timers, Mark Newest Spot, Your Spot Colour |
| Your traps | Time until it collapses (green once something's caught; a full pie in **Failed Trap Colour**, purple, saying "Empty" once it springs with nothing in it, so you reset it; as RuneLite's Hunter plugin colours an empty trap) | Hunter > Trap Timers, Failed Trap Colour |
| Fires you cook on (lit while you were nearby) | Fires go out at a random time 60-119 s after lighting: green until the earliest, then a red ring filling up to the latest; hover for the times. Off by default | Firemaking > Fire Timers |
| Forester's Campfires | Time left (up to 3 minutes): set by the log that made it, topped up by each log you add, corrected when you Check it | Firemaking > Campfire Timers |
| Shipwrecks you saw surface | Time until it sinks | Sailing > Shipwreck Timers |

**Good to know:** Fishing spots and fires go at a random time the game picks (Jagex: fishing spots 250-530
ticks), so no plugin can count down to the moment; their timers show the window (fire timers are off by default). Each kind's setting (Tree Timers, Mining Timers, Fishing Spot Timers, Trap Timers, Fire
Timers, Campfire Timers, Shipwreck Timers) picks when it shows: **Always** (as soon as you're near, before you
start), **Training** (the default: from the moment you start fishing a spot, chopping or mining, or once you've had an XP drop in its skill in the last two minutes:
trees while you chop, rocks while you mine, fires and campfires while you cook or burn logs) or **Off**. With
the tile style, fishing spots that have no timer (there before you arrived, or ones that never move like
karambwan) still get their tile and fish picture, without a timer. Above every timer sit its time (time left; a star's layer health; a trap's "Caught"; for fishing spots and fires,
how long ago they appeared) and the skill's icon (**Resource Timers > Timer Text**, **Timer Skill Icon**).
**Resource Timers > Timer Opacity** sets how solid they are (60% by default). **Resource Timers > Timer Style** picks the look:
*Tile and icon* (the default) outlines the tile with a picture of what it gives (logs, ore;
for fishing spots the fish of the way you're fishing, picked when you click from the option and the rod and bait you carry (your catches correct it), stacked when it gives
several like trout and salmon, else of the best way for your level), the outline coloured by the timer (untimed
fishing spots in the **Zone Colour**; hover a fishing spot or fire for when it can go); *Floating timer* puts a
pie over each instead. Where RuneLite's Fishing plugin already draws its spot icons or tiles, or its
Motherlode Mine plugin its vein icons, ours leave the icon or outline out and only add the timer. Pies draw under the bank and other interfaces. A tree already being chopped when you
arrived gets no pie (its time can't be known). Other players' logs can't be seen, so a tree someone else
is chopping can sit at "due" a little while before it falls. If you run a dedicated timer plugin (Tree
Despawn Timer, Fishing Spot Tracker, RuneLite's Hunter plugin, RuneLite's Mining plugin for depleted rocks and
Daeyalt, RuneLite's Fishing plugin's Minnow overlay for minnow spots...), our pie for that timer hides
(**Resource Timers > Use Other Timer Plugins**).

---

## Skilling alerts

All only for the resource you're working, and each with Voice/Chat switches in its skill's section. (The farming
patches and bird houses ready alerts are in the [Planner](#planner) now.)

| Alert | When | Setting |
|---|---|---|
| Tree fallen / rock depleted / fishing spot moved | Your resource is gone ("The tree has fallen."); not for level 1 trees and rocks, which go after one log or ore | Skilling Alerts > Resource Gone |
| Inventory full | Your inventory fills while skilling | Skilling Alerts > Inventory Full |
| Forestry event | An event starts near you while you're chopping (Rising Roots, Struggling Sapling, Flowering Tree, Leprechaun, Poachers, Pheasant Control, Bee Hive, Enchantment Ritual, Friendly Ent; chat names it). RuneLite's Woodcutting plugin outlines what each event needs | Woodcutting > Voice/Chat: Forestry Event |
| Motherlode sack full | The pay-dirt you carry fills the sack (what's in it plus yours reaches 108, or 189 upgraded): empty it before mining more. RuneLite's Motherlode plugin turns the sack numbers red at the same point | Mining > Voice/Chat: Motherlode Sack Full |
| Boat health low | On your own boat, its hitpoints drop below **Boat Health Low At** (30% of its most); once until it's repaired above that | Sailing > Voice/Chat: Boat Health Low |
| Trim sails | On your own boat, a gust opens the window to trim the sails for a burst of speed (the game's buff bar times the same window) | Sailing > Voice/Chat: Trim Sails |
| Bird nest | A bird's nest falls out of the tree (chat says when it's a clue nest) | Woodcutting > Voice/Chat: Bird Nest |
| Coin pouches full | At 28 (more with the Ardougne Diary) | Thieving > Coin Pouch Alert |
| Rumour complete | Your Hunters' Rumour creature's rare part drops ("take the part to the Hunter Guild"; chat says how many you caught) | Hunter > Voice/Chat: Rumour Complete |
| Batch complete | A run of Cooking, Smithing, Crafting, Fletching or Herblore finishes (3+ items in a row; one-off XP like quest rewards and lamps doesn't count) | Skilling Alerts > Batch Complete |

---

## Hunters' Rumours card

**What it does:** A **HUNTER RUMOUR** card in the sidebar (and on the pinned skill when Hunter is pinned) while you
have a rumour: the creature and its method ("Pyre fox (Deadfall), for Gilman"), **Caught:** how many you've caught
since and the catch the rare part is guaranteed by ("12 (rare part by 30)"; lower with each guild hunter outfit
piece you wear), and **Where:** the first place to find it (hover the card for all of them). Once the rare part
drops it says so, with a voice/chat alert; handing the rumour in clears the card.

**How to use:** Take a rumour from a guild hunter, or use the Quetzal whistle's **Rumour** option, and the card
appears. Click the heading to fold it. Settings: **Hunter > Rumour Card**, Voice/Chat: Rumour Complete.

**Good to know:** The game keeps no variable for the rumour, so it's read the way the Hunter Rumours plugin reads
it: from the hunters' dialogue and the whistle message (kept with your profile). Catches are counted from Hunter XP
drops matching the creature's XP, so two catches in the same tick count as one, and catches made before the card
knew your rumour (or before this feature) aren't in the count. Locations, methods and pity thresholds are the wiki's
Hunters' Rumours page. Only information: nothing is clicked, scrolled or changed. With the Hunter Rumours plugin on
too, you'll see both (clash note).

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
- A skill's rate clock **auto-pauses** after a while without XP (**Auto-Pause After**: 10 seconds to an hour, 10 seconds by default) and
  resumes on your next XP.
- Level-ups are announced with the skill and level ("Woodcutting level... 45!").
- **Items gathered**: every skill's card (and the pinned skill), goal or not, counts what you did this session and
  how many an hour: Woodcutting, Fishing, Mining and Cooking count items from the game's chat messages (as
  RuneLite's Woodcutting, Fishing and Cooking plugins do; one per message, so a double catch counts once): "Logs:
  342 (1,050/hr)", "Fish:", "Ore:", "Gems:", "Cooked: 120 (1,400/hr), 4 burnt (3%)". Other skills count each action
  (each XP drop): "Steals:", "Made:", "Crafts:", "Casts:", "Catches:", "Burned:", "Offered:"... Combat skills count
  kills instead. Per hour is worked out at the skill's XP rate, so it matches the card's XP/hr. Reset rate or Reset
  clears them (**Show Items Gathered**).
- **Keep Session For** (30 minutes by default; 0 turns it off): close the client and log back in to the
  same account within that time of logging out (or of your last XP, if the client closed without logging out) and
  the tracker carries on with that session's XP, rates, actions and items, quietly. XP gained elsewhere in
  between (mobile) isn't added. Reset all skills starts afresh. Like RuneLite's XP Tracker keeping its session.

**Settings (Skill Tracker):** a switch for each field; **Compact Overlays** (General) trims the cards (see below); Actions Until Goal; Show Goal; Suggested Method,
Training Focus, Highlight Suggested Method and its colour; Auto-Pause Idle Skills and After
(minutes); Ignored Skills (comma-separated); Voice/Chat: Level Up; Show Items Gathered; Keep Session For.

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
  Train line for all of them. Where the guide names none, and for a plan's milestone, the places of its trees,
  rocks, stalls or creatures from their wiki pages, best first: the training guides' picks ("Isle of Souls (3)" for
  teaks), then the most of it ("Where: Isle of Souls (3) (+16 more)").
- When a level-up moves a goal onto a new method (or Slayer master), chat says so.
- **Skill plans** (with **Follow Skill Plans** on, the default): a plan exported from Snuffy's Planner's Calculator
  (Plan view > Export to tracker) sets the goal and its milestones. The goal card then shows the milestone you're on
  as the near goal, "Milestone: level 50 (Willow logs)", with its own blue bar under the gold goal bar (from the
  milestone's start to its end, with the time to it; hover for the whole plan), and the pinned skill the same bar and
  "Plan: Willow logs (to 50)". Actions (or kills) count to the milestone's end. Its method is the suggestion and its
  trees, rocks, NPCs or fishing spots are outlined (matched to the training guide's method by name; otherwise just
  the text), ahead of the wiki's suggestion and the Training Focus, behind a Track for goal pick or a Method you pick
  (exporting clears an old Method pick). Pass a milestone and it moves to the next (chat says "Woodcutting plan: next
  milestone, Teak logs to level 60"); levels the plan doesn't cover get the wiki's suggestion. Without an exported
  plan nothing changes; clearing the goal removes it.
- Where that method is a tree, rock, stall, pickpocket NPC, Hunter creature or fishing (every spot that catches its fish), those are outlined in the
  world.
- **Pick another method:** right-click the skill's card > **Method** lists every guide method your level and
  quests allow (also the guide's later ones whose trees, rocks or creatures you can already use: Lead rocks from
  Mining 25, though the guide lists it from 60), each with the XP/hr the guide gives for it where it gives one
  ("~46K xp/hr", an estimate). With the skill's Suggest set to **Money Maker**, it lists the wiki's money-making
  methods for the skill you meet the requirements for instead, with their GP/hr, most profitable first (the fastest marked; hover one for what the guide says about it). Pick one and the card, the
  world outlines, the tools and the actions estimate follow it ("Your method: Barbarian Fishing"); once your level
  is past what it covers, it goes back to **Suggested (automatic)**, which you can also pick any time.
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
  level from what's in your inventory and bank (most XP, or most profit with **Skilling Hover Cards > Best Choice By**). Its
  materials are outlined in your inventory and bank; hover one for "Suggested for your Cooking goal:
  Lobster". The bank counts once you've opened it this session.
- For gathering skills (Fishing, Mining, Woodcutting, Hunter) nothing gets used up but bait, so the **tools and
  bait** the method needs are outlined instead (the rod and feathers for fly fishing, an axe or pickaxe of any
  kind, the creature's trap; hover for "Needed for your Fishing goal: Feather"), while the spots, rocks and
  trees themselves are outlined in the world. A fish in your inventory is only outlined for a Cooking goal.
- **Hide Outlines While Working** (on by default): the fishing spot, tree or rock you're working drops its goal
  outline (and a fishing spot its plain tile outline) while you work it, since you've found it; the others keep
  theirs, and it comes back a few seconds after you stop.

**How to use:**
1. Click **Set Goal** at the top of the tracker (or right-click a card > Set goal...; a skill with a goal has Modify goal... instead, which starts from
   its goal and keeps the goal bar's progress).
2. Pick the skill (the list shows your level in each; below it, your level, XP and any current goal) and
   type a target: a number up to 99 is a level, anything bigger is an XP amount.
3. Clear it from the card's right-click menu (Clear goal only shows while the skill has a goal). A goal you reach
   clears itself, with a chat line ("Fishing goal reached (level 53): cleared"); turn off **Clear Goals When
   Reached** to keep reached goals (they show Done!).

**Skills tab bars:** on the game's Skills tab, two separate bars, drawn over the tab (the tab itself isn't changed):
- **Level bars** (**Goals > Skills Tab Level Bars**): along the bottom of every skill, how far you are to the next
  level, orange turning green as it fills (none at 99). While the Plugin Hub's Skills Tab Progress Bars is on, ours
  step aside for its bars (the clash icon says so).
- **Goal bars** (**Goals > Skills Tab Goal Bars**): up the left edge of each skill with a goal, a gold bar filling
  from the bottom, as far as the card's goal bar. On the edge, so it never sits on a level bar, ours or theirs.

Goals are saved with your RuneScape profile, so they stay after a relaunch. The Toolbelt and the Skilling Helper
share them (and your Track for goal picks, Suggest choices and pinned skill), so switching between them keeps
your goals. To have them on another PC,
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
Once you're getting XP, Fishing's count uses the average of your last 30 XP drops (shown with a "~"; hover for
the XP each), since one spot catches different fish (trout and salmon). Every other skill uses your usual XP drop
(the most common of your last 30), so a one-off, like tagging an arena pillar between floor spikes, doesn't throw
it off.
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

- **Banked XP** (Goals > Show Banked XP): on a production skill's goal (Cooking, Herblore, Prayer, Fletching...), a
  "Banked: 1.2M XP (to level 84)" line: the level that gets you to, and the XP in what's in your bank and inventory at your level, the most XP things first, each
  material counted once. Just a figure; it doesn't change what's suggested. **Note:** both use your bank as you last saw it: open your bank once on each account and they show from then on, even straight after login (the bank's remembered with your account and updated whenever you open it). Until then neither line shows.
- **Materials value** (Goals > Show Materials Value): under it, "Materials worth: 1.2M gp", the Grand Exchange value of
  the materials counted in Banked XP. Just a figure.

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
- **Move Boxes Separately:** each tick box becomes its own see-through box you Alt + drag anywhere, for example
  one over each item you click in a tick method (box 1 on the item for tick 1, and so on); each stays where you
  put it. They start stacked in the top-left corner; a box shows while its tick is in the cycle.
- **Half-Tick Boxes:** a half box after each tick box ("1.5"): the tick's box lights for the first half of the tick
  and its half box for the second, for actions between ticks. For 1-tick karambwans set Ticks Per Cycle to 1:
  click the fire when box 1 lights and the karambwan when 1.5 does; with Move Boxes Separately put box 1 over the
  fire and 1.5 over the karambwan.
- **Tick Pattern:** *1-tick: two halves*: box 1 lights for the first half of every tick and box 2 for the
  second, for clicking twice a tick (1-tick karambwans: box 1 on the fire, box 2 on the karambwan). *Custom* is
  one box per tick from Ticks Per Cycle.
- **Play Ahead:** the metronome sounds this long before each tick (200 ms by default), so clicking when you hear it
  lands on the tick: reacting to a sound, plus the sound's own delay, takes about that long. If Click Timing says
  your clicks on the sound are late, raise it; if early, lower it. 0 plays it on the tick.
- **Tick Bar:** fills smoothly from left to right over each tick and snaps back on the next, so you can see the
  tick coming instead of reacting to a flash. Move it with Alt + drag.
- The first tick of each cycle is in its own colour (green by default).
- **Metronome:** a game sound on every tick, and a different one on the first tick of the cycle. The **Tone**
  is Plop, Bell, Anvil, Boop, Coins, Pickaxe and axe, or Tinderbox. It can play on the first tick only.
- **Follow Attack Speed:** an attack timer for your own attacks (never a monster's). The cycle becomes your
  attack speed (the weapon's, one tick faster on Rapid, 5 for spells: 6 on the Twinflame staff, 4 on the
  harmonised nightmare staff), and the count starts again at 1 each time you attack, so 1 is your attack tick.
  - An attack only counts once your last one's delay has run out, and eating, blocking a hit, alching or
    pickpocketing never count as one (nor does fishing or talking to an NPC: only something you can fight).
  - The cycle is the speed of the attack you made: swapping weapon afterwards changes the next one, not this one
    (a swap on the attack tick itself counts for that attack).
  - **Eating** delays your next attack, as in the game: 3 ticks, 2 for karambwans, halibut and gnome foods, 4
    for the Stymphike tartare; wine and lumpy barbarian potions count as food. While the extra ticks pass the
    count stays on 1 (and Count Down shows the real number of ticks left).
  - **Count Down To Attack:** the count over your head counts down the ticks to your next possible attack (4,
    3, 2, 1, then you can attack), and the box numbers run 4, 3, 2, 1 too (half boxes 3.5, 2.5...). When
    you're not fighting it loops like the normal count.
  - **Late Ticks** (5 by default, 0 off): after an attack, once you could attack again but haven't, the count
    over your head shows how many ticks late you are in red (-1, -2...), up to that many; then it goes back to
    counting. Works with counting up or down.
  - The cases come from the Plugin Hub's Attack Timer Metronome (BSD-2, credited in the README). It also knows
    boss-specific speed changes (Scurrius, ToA, Yama...), spell-sound casts without an animation and special
    weapons; ours doesn't.
- **Hotkeys** (Hotkeys section; none set at first):
  - **Reset Count:** press on the tick you want as 1: the cycle starts again from 1 there (with Follow Attack
    Speed it also forgets your last attack, so the late count stops).
  - **Show/Hide:** hides or shows the tick count, boxes and bar (says which in chat); the metronome and click
    sounds keep playing. Turning the plugin off and on shows them again.
  - **Ticks Per Cycle Up / Down:** one more or one less (1-10), saved like changing the setting, with a chat
    line saying the new number (and when Follow Attack Speed or a Tick Pattern sets the cycle instead).
- **Click Timing** (builds from this repository only: the Plugin Hub version leaves it out): each click shows how long after the tick it landed (e.g. "310ms") over your head: green
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

**Settings (Tick Counter, Tick Boxes, Tick Bar, Metronome, Click Timing, Hotkeys):** Count Over Head; Tick Boxes, Box Layout (Horizontal or Vertical), Move Boxes Separately, Box Shape (Square, Rounded or Circle) and Box Opacity; Tick Bar;
Ticks Per Cycle (1-10); Follow Attack Speed, Count Down To Attack, Late Ticks (0-20); Tick Colour and First Tick Colour; Over Head Text Size; Box Size;
Bar Width; Metronome, Tone, First Tick Only, Volume (also the click sounds; 0 uses the game's sound effect
volume); Click Timing; Click Sounds; Late Sound; Late After; Ideal From and Ideal To; Click Every Tick; Reset Count,
Show/Hide, Ticks Per Cycle Up and Ticks Per Cycle Down hotkeys.

---

## Planner

**Snuffy's Planner** (in the Toolbelt too): its own sidebar tab (the calendar icon) for things on a clock and your
account's to-do list, laid out like RuneLite's Time Tracking: six tabs across the top (**Today**, **Timers**,
**Slayer**, **Drops**, **Notes**, **Calculator**; the one you pick stays open next time), each with a header line (its title, a
summary like "3 left · resets in 10h 37m", and a **+** to add) over cards with the item's picture. Right-click a card
for its menu (edit, hide, delete...). Each list has a **Show ...** setting to hide it. What you add is saved per
account (RuneScape profile), and the Toolbelt and the separate Planner show the same. Skill goals live in the
Skilling Helper; the Calculator's **Set as goal** sets the same goal (both plugins read it).

**Today: Dailies and Weeklies** (each header counts down to its reset: daily 00:00 UTC, weekly Wednesday 00:00 UTC):
- Only your own: nothing is read from the game. **+** adds one: **Start from** a preset task (Battlestaves, Buckets of
  sand, Pure essence, Random runes, Bowstrings, Bonemeal and slime, Dynamite, Ogre arrows, Herb boxes, Herb run, Bird
  house run, Clue scroll; weekly: Tears of Guthix, Kingdom of Miscellania, Penguin hunting, Farming contract), which
  fills in its name, where to go and its picture, or **Your own**; then its name, daily or weekly, an optional note
  and a **Picture** (any preset task's item, or none). Click one to tick it; it unticks at its reset. Right-click to
  **Edit...** (name, note, picture) or **Delete**.
- While a list is empty it shows four of the preset tasks as examples (greyed, not tracked); clicking one opens +
  with it filled in.
- At login and at the reset, if anything's left: one voice line ("You have dailies to do." / "...weeklies..."), and
  one line in the **split private chat** (where friends' logins show; it fades) saying how many are left (**Chat:
  Dailies**, **Chat: Weeklies**). Never a line per item.

**Timers** (the header says how many are ready and running):
- **Farming patches** (moved here from the Skilling Helper's sidebar; nothing saved is lost): every patch, compost bin
  and Hespori patch you've been to with something in it, what's in it (with the crop's picture), a bar
  filling as it grows and when it's grown ("ready" in green, "in 1h
  20m", "diseased", "dead"), soonest first. The game only tells us a patch's state while you're near it, so each is as
  you last saw it; right-click one to forget it. Like RuneLite's Time Tracking, a patch is only read after two ticks in
  the same area and not while a full-screen interface is open, and the account's growth tick offset is learnt the
  first time a patch is seen growing a stage while you stand there; until then times show with a **~** (they may be out
  by one growth cycle) and the ready alert waits a cycle longer.
- **Bird houses:** when your Fossil Island bird houses are full (50 minutes after seeding), with a bar.
- **Your own timers:** **+** with a name and a length (HH:MM:SS, e.g. 01:30:00; MM:SS or plain minutes work too); the play, stop and again buttons on each,
  the time left counting down with a bar; right-click to edit, reset or delete. They keep running through a logout; one that ends while
  you're away is said when you're back.
- **Ready Sound:** how the Timers tab tells you something's done (your timers, farming patches, bird houses): its
  voice line, or a game sound (Bell, Chime, Anvil, Boop, Door) played **Times** times (1-5, 3 at first); picking one
  plays it. Game sounds follow the game's Sound Effects volume. The chat line is posted either way.
- Alerts: Patches Ready (names them; once per crop), Bird Houses Ready, Timer Done ("Your timer is done.", names it),
  each with Voice/Chat switches. Their lines go to the game chat like other alerts (only the dailies and weeklies
  reminders use the split private chat).
- **Your timers' alerts**: adding or editing a timer (+, or right-click > Edit...) has **When it's done**: Default (the
  Ready Sound setting), Voice line, Bell, Chime, Anvil, Boop or Door (picking one plays it), and **Times** (1-5) for a
  sound. Each timer keeps its own; hover a timer to see it.

**Slayer:** your task and kills left (of how many, with a bar), Konar's area, and Slayer points and streak tiles, read
straight from the game (as RuneLite's Slayer plugin does), so it works without the PvM/Slayer plugin; kept for when you're logged out.

**Drops** (the header totals the value; the bin clears the lot): each monster is tracked on its own (and each chest,
clue casket, raid... with **Count Other Loot**). The front page lists them, most valuable first, one card each: its
picture (a clue casket's or key chest's item; a tile with a monster's initials), name, kills or opens, value per kill
and total Grand Exchange value; under them your latest drops (**Recent Drops Shown**), the valuable ones (**Valuable
Drop From**, 100K at first) in **Valuable Drop Colour**. Click a card, or one of its recent drops, for that monster's
own page (the arrow in its header goes back):
- Tiles: kills, total value, value per kill, and kills since its last valuable drop ("No valuable yet" until one).
- **Best drop**: its single most valuable drop, and when.
- **Session vs all time**: kills, value and value per kill since the client started (kept in memory) beside all
  time (saved).
- **Items**: every item it has dropped, most valuable first, with its total amount, how many kills it came in, the drop
  rate you've seen (**1 in 45** = kills / times dropped; "every kill") and its value; ones worth Valuable Drop From a
  drop are in Valuable Drop Colour with a bar at the side. **Show this session** lists this session's items instead.
- The bin resets that monster (after asking); right-click a card on the front page for the same.

Drops saved before drop rates were kept still load: those items show **?** for the rate until they drop again, and
rates count from then (hover says so). Monster drops are RuneLite's own kill loot (as the PvM HUD's loot per hour counts it, which stays in
PvM); other loot comes from RuneLite's Loot Tracker, so it's only counted while that plugin is on (its monster kills
aren't counted twice). With the Loot Tracker on, the clash note says the two lists overlap.

**Notes:** a to-do list (type in the box at the top and press Enter or **+**; click a line to tick it, right-click to
edit, move up or down, delete; **Clear ticked**) and a notes box below, saved as you type, for each account.

### Calculator

A skill calculator in the Planner's last tab (the calculator icon), made from RuneLite's own Skill Calculator: the
same actions, levels, XP and bonuses, and the same sums (XP to a tenth, rounded down).
- **Skill**: click one in the grid (the skills the calculator has, in the Skills tab's order); the one you pick is
  remembered.
- **Goals** (only while the Skilling Helper runs: goals are its; without it the Planner doesn't read or set them,
  and Set as goal, the goal line and the goal card don't show; likewise the Skilling Helper follows an exported plan
  only while the Planner runs): skills with a goal ahead of you have a gold line in
  the grid, and a **Goals** list under it shows each one ("Fletching → 99", with the percentage from where the goal
  started; hover for the XP to go and how many plans it has); click one to open that skill.
- **Current and target**: your level and XP fill in from the game (logged out, the last you had); type over any of the
  four boxes to plan ahead (a level fills in its XP and the other way round). The target starts at the skill's goal if
  it has one ahead of you, else the next level. A target you type is kept for that skill (switching skills, logging out)
  until **Reset** (back to the goal or next level) or **Set as goal**. The line under them says the goal, and **Set as goal** makes the target
  the skill's goal exactly as the Skilling Helper's Set Goal does (a goal still underway keeps where its bar started);
  the bar shows how far you are toward it.
- **XP bonuses** (where the skill has them: skilling outfits, achievement diaries, the Felling axe, altars and the
  Ectofuntus, the bonecrusher, shark lures, Daeyalt essence, Goldsmith gauntlets...): tick the ones you use. They're
  remembered per skill; ticking one that can't go with another unticks it (one altar at a time); each counts only for
  the actions it applies to (the gilded altar for bones, not ashes).
- **Where** (a milestone's tooltip): the best places for it, the training guides' picks first with why ("Isle of
  Souls (3): no requirements; birds make 2-tick possible", "Hardwood Grove (9): 100 trading sticks an entry"), then the
  places with the most of it, with what you need for them from the wiki ("partially complete Legends' Quest").
- **Actions**: each with its picture, name and level (greyed out above your level), XP each with your bonuses and how
  many to your target. Underneath where it applies: the time at the XP rate the wiki's training guide gives for a
  matching method (Teak trees for Teak logs), else at an estimate marked "est." (the OSRS Wiki's count an hour for it, from
  its training and money making guides or its tick timings with banking, times its XP each; faster ways such as 1-tick
  karambwans, 2-tick teaks, 3-tick granite, clicking darts, the Smiths' uniform or the Blast Furnace are in the
  tooltip and a milestone's **Pace**), how many you have (bank as last opened plus inventory: of the item, or
  how many you can make from your materials), and with **Show GE Prices** one line of money at Grand Exchange prices:
  what the materials to your target are worth ("Mats 1.24M", for what you make, burn or bury) and what the products
  sell for ("Sells 2.10M": what you make, or the logs, ore, fish or catch you gather, priced by the item's id); neither
  is profit (products count as sold on the GE after tax, or as high alched less a nature rune when that pays more:
  **Sell By**, or per milestone its right-click **Sell by**, or **drop them** to powerlevel (worth
  nothing: the net is what the materials cost), with the better way marked and an orange line on
  the milestone when the other way pays more; a milestone's **Record sales...** says how many you really sold and
  alched and what you got each, for its cash and net so far).
  The net beside them is: a green plus badge and "+860K" for a gain, a red minus and "-28K" for a loss
  (what it sells for less what the materials are worth), with the net gp per XP above it. Hover for it in words. The training guide's method at your level is marked in
  green. **Sort** (beside the search, remembered) orders them: **Level** low to high (at first) or high to low, **What you can do first** (then what you can't yet, each lowest first), **Most XP each**, **Fastest** (the guide's XP an hour), **Cheapest per XP**, **Most profit** or **Name**; all but the level and name orders put what you can do first. **Search** to filter by name.
  Hover a card for all of it in words; right-click it to **Add to plan**.
- **Members-only actions** (marked in each action's data, from RuneLite's Skill Calculator and the item's members
  flag) are left out on a free world, as RuneLite's calculator does, with a note "Members-only actions hidden on
  this free world"; logged out, everything shows (setting: Hide Members On Free Worlds).
- **Actions · Banked · Plan** (the tabs above the list; the one you pick is remembered).
- Each milestone card is its own levels only (not added up). Under them, **Whole plan** adds them up: the XP to the
  goal, the time to it at the wiki's rates (TTG), the average XP an hour over it, the net gp per XP, and the materials,
  what it sells for and the net (badge). Hover it for all of it in words.
- **Materials needed** (under Whole plan): everything the milestones still need, in full (each milestone's actions to
  its end, in goes of its recipe, times its materials; where there are several recipes, the one you have the most
  goes of), as "have / need": green when your bank and inventory have all of it, red when you're short (hover for how
  many); tools say "have it" or "missing".
- Darts, arrows and bolts are made 10 or 15 at a time: each action is one item, with its share of the materials.
- A plan goes by your real XP (the game's): typing a level into Current to try it out changes Actions and Banked,
  but doesn't mark the plan's milestones done.
- **Plan overlay** (only when the Skilling Helper isn't installed: its pinned skill card shows the plan then, so
  never in the Toolbelt): a card on the game screen (Alt + drag to move it) for the plan of the skill you last gained
  XP in, the plan open in the Calculator for it, or the plan you pinned (the pin on a plan's card in the Plan view,
  or right-click it > Pin to screen: it opens that plan and keeps it on screen whatever you train; click the solid pin,
  right-click > Unpin from screen, or right-click the overlay > Unpin to go back to following your training): the title ("Fletching: Darts to 99"), a bar for the milestone
  you're on ("Lvl 59 → 67", XP left), its method, actions left, the next milestone, and the materials this milestone
  still needs against what you carry and bank (green when you have it all, red when not; tools "have it"/"missing").
  Before a milestone starts (a gap) it says what's next and at what level; past them all, "Plan done". **Plan
  Overlay** (Calculator settings, on) turns it off; **Compact Overlays** (General) keeps the title, bar and actions left.
 (under the list, in Actions and Banked): the skill's goal and the plan's milestones (levels,
  method, XP left; the one you're on marked green) stay in view while you scroll the actions, to compare. Click its
  title to fold it away or open it again (remembered); **Edit** opens the Plan view. A long plan scrolls inside it.
- **Banked**: what you own instead: the XP in all of it for the skill at your level (the most
  XP each first, each item used once, as the Skilling Helper's banked XP), the level that gets you to (with your target
  beside it: "Level · aim 67"), what the materials it uses up are worth (**Materials**) and the **Net** of making it all (red for a loss), and each action you have the
  makings of, with how many, the XP and the levels it takes you through stacked on the ones above it ("lvl 59 → 61",
  then "lvl 61 → 63"... so the last ends at the total; green once it reaches the target), what its materials are
  worth and what it sells for, and its net with the plus or minus badge. Two actions sharing materials split them as the total does (most XP first): "x 0 of 620 · used
  above". One above your level says what it needs ("needs lvl 80") and isn't counted. The bank counts
  once you've opened it (with the Planner or the Skilling Helper on; it's remembered).
- **The goal** (when the skill has one, set here or in the Skilling Helper): a gold card at the top of the Plan view
  with its levels, what it trains (Track for goal pick, the method picked on the tracker card, the exported plan, or
  the wiki's suggestion), your level and how far along it is. Click it to unfold **Change goal** (a level or XP),
  **Make a plan** (a new plan named "Goal to 70", with one milestone of what it trains when that's a
  Calculator action) and **Remove goal** (asks first: it's the Skilling Helper's goal too); right-click for the same.
- **Plans**: the Plan view starts with the skill's saved plans: as many as you like, each with a name ("Darts to
  99", "AFK teaks"), its levels and how many milestones, **In tracker** on the one exported. **+** makes a new one
  (it asks for a name). Each plan's card shows its levels, your level and how far along it you are (a bar and a
  percentage, by XP). Click one to open it: it unfolds under its card (a green edge: it's the one **Add to plan** adds to
  and the plan bar shows); click it again to fold it away (remembered); right-click one to **Rename...**, **Duplicate** (to try a variation) or
  **Delete...**. Under them, links to other skills' plans ("Other skills: Fletching (2)"). Plans made before there
  could be several become the first, unnamed ("Plan 1").
- **Plan**: the open plan: a final goal (level 99, or your target) and milestones on the way, each with its
  own method ("1-20 Logs, 20-50 Willow logs, 50-99 Teak logs"). **Suggest a plan...** works out a plan from your level to
  the goal six ways and shows them side by side (time, gp after sales, milestones): **Fastest** (the most XP an hour
  at each level), **Cheapest** (the fewest gp lost per XP), **Money maker** (the most profit), **Balanced** (the least
  gp and time together, an hour counted at **Hour Worth (gp)**), **AFK** and **Wiki guide** (the wiki's training
  guides, as before); an ironman gets Fastest, AFK and Wiki guide. Pick one and it fills the milestones (supply
  milestones stay), each an ordinary milestone to change. When the plan has milestones already, **Keep my milestones,
  suggest only the levels they leave open** (ticked) adds the suggestions before, between and after yours ("adds 3
  milestones"); untick it to replace them. A milestone's right-click **Pace** times it at the wiki guide's rate, the
  normal pace or a faster way (1-tick, 3-tick, clicking fast), with each one's XP an hour. Or build it
  by hand: right-click an action > **Add to plan** (it starts where the last milestone ends, or at its own level, and
  the last one stretches or shortens to meet it; inside a milestone, it takes over from its own level to where that one ended: Atlatl
  darts (74) in Adamant darts 67-81 make Adamant 67-74 and Atlatl 74-81; when there's no room before the goal, it
  asks to raise the goal so the new one goes on after it). **+** with levels you type puts it in at those levels:
  the milestones it overlaps are cut short, split around it, or removed if it covers them. A new plan's goal is the target you typed or the skill's goal, else 99, or **+** to pick a method and levels. Right-click a milestone to
  **Edit...** its method and levels, **Change method (pick an action)** from the Actions list, **Move up**/**Move down**
  (swaps the methods; the levels stay in order) or **Delete** it. Each milestone shows what's left of it: XP, how many
  actions, the time at the guide's XP rate, the net gp per XP and its cost and earnings; the one you're on is marked,
  finished ones greyed, levels with no milestone noted. Above them the totals: XP to go, time, cost and earnings, and
  your level to the goal (**Goal: level 99** to change it). Saved per account (`plan.<SKILL>` in the shared group).
- **Export to tracker** (only when Snuffy's Skilling Helper or the Toolbelt is installed; otherwise a note says
  installing it lets you): sets the plan's final goal as the skill's goal (as Set as goal does) and gives the Skilling
  Helper the milestones, which its goal card then follows (see Goals). Edits to the plan you sent are a draft: the
  tracker keeps the version you sent until **Send changes** (or **Undo** puts the plan back as the tracker has it), so
  trying things never moves your goal; the plan's card says "Unsent changes" and the goal card "Changes to ... not
  sent" (right-click it to send or undo). When the tracker has a different plan, **Send** makes it follow the open one and
  **Take** replaces the open one with the tracker's (its name stays). **Follow** (the tracker's goal card, and the
  Planner's goal card): **Your plan**, **Your pick** (a Track for goal or a picked method, for a one-off) or **The
  wiki's suggestion**, or the usual order (a pick, then the plan, then the wiki); the others stay saved to switch back
  to. Sending a plan sets it to the plan; making a pick sets it to the pick. The milestones go by your XP whatever you
  train with. A supply milestone (logs kept for Firemaking) goes by what the other plan still needs: its levels
  reached another way, it says "Levels done, supplies to get" and how many; and it says how many are for the other
  plan and how many just for its levels, or that the other plan needs none now; **Remove** takes it out of the tracker (the goal
  stays). An older copy that none of your plans match any more shows on the goal card ("Tracker has an older copy"):
  right-click it to send your open plan, bring the copy in as a plan, or take it out of the tracker.
- Information only: nothing is done in the game.

**Settings (Planner: Today, Weekly, Timers, Slayer, Drops, Notes & To-Do, Calculator):** a Show switch for each list; Dailies To Do
Alert, Weeklies To Do Alert; Farming Patch List, Patches Ready Alert, Bird
Houses Ready Alert; Show GE Prices, Hide Members On Free Worlds (Calculator); Count Other Loot, Valuable Drop From, Valuable Drop Colour, Recent Drops Shown; Voice/Chat switches.

---

## Idle alerts

**What it does:**
- **AFK alert:** you were training (gaining XP), your character stopped, and you haven't clicked or typed
  since. Banking, shops, the GE and menus never set it off.
- **Idle logout warning:** before the game logs you out for inactivity (follows RuneLite's Logout Timer
  setting; in combat the game's 20-minute rule).
- **Six-hour logout warning:** the game logs you out after six hours online; this warns twenty minutes before
  (counted from when you logged in or hopped worlds), with a RuneLite notification too. RuneLite's Idle Notifier has
  the same notification.
- **Run energy low** (off at first): "Your run energy is low" anywhere when your run energy drops below **Run
  Energy Low Below**; it can go again once energy is 10 above that. While it's on, the Agility section's own
  run energy alert (only while training Agility) stays quiet, so you never hear both.
- **Run energy restored** (off at first): "Your run energy is restored" when your energy is back up to **Run
  Energy Restored At** (100 for full), after it had dropped at least 10 below it, so a step or two of running
  doesn't set it off. Not at login.
- With several of Snuffy's plugins installed these are said once, like the idle warnings.

**Settings (Idle):** AFK Alert and Idle Alert After (seconds); Warn Before Logout (seconds); Repeat
Logout Warning, Repeat Every, Max Repeats; Six-Hour Logout Warning; Run Energy Low Below and Run Energy Restored
At (%, 0 off); Voice/Chat switches.

---

## Clue drops

Every plugin but the Tick Counter has this; with several installed only one says it.

**What it does:** a clue scroll in the drop of a monster you killed, or a clue bottle, clue geode, clue nest or
scroll box from skilling arriving in your inventory: "Clue: a hard clue scroll from Abyssal demon!" (the tier from
the item's name). Taking one out of the bank, the GE or a deposit box doesn't count, and picking up a dropped one
isn't said twice. (The daily reminders that used to be here are the Planner's TODAY list now, without the chat line.)

**Settings (Reminders):** Clue Drop Alert; Voice/Chat: Clue Drop.

---

## Agility

**What it does:**
- **Course Card:** while you run a course, its card shows your laps this session (from the game's lap count message),
  laps and XP per hour (XP per lap is worked out from your own laps), average lap time, laps to your next level and
  to your goal, and marks of grace picked up. It stays five minutes after your last lap.
- **Brimhaven Arena Card:** in the Brimhaven Agility Arena, a bar counting down to the ticket pillar moving (every
  minute; it starts once the pillar's arrow first moves), yellow then red near the end, plus your tickets this
  session and per hour. **Voice/Chat: Pillar About to Move** warns you **Pillar Warning At** seconds before (10).
- **Arena Obstacle Colours:** in the arena, each obstacle is outlined by your Agility level: **green** you can do it
  and won't fail, **yellow** you can do it but may fail (floor spikes until 50, spinning blades and darts until 99,
  the wrong plank), **red** your level is too low. Planks: of each gap's three, the two broken ones are red and the
  smooth one green, told apart by their broken pieces (until it can tell, all three are yellow). Hover one (Obstacle Hover; it works on the ones you can't click
  too, like floor spikes and pressure pads) for its level, XP, time, XP per tick,
  your chance to get across, and whether it's the best XP per tick you can do safely.
- **Arena Route:** the quickest way to the tagged pillar, drawn as a line from you across the pillars with its obstacles
  outlined thicker and the pillar's tile marked. It only uses obstacles your level allows (and leaves out any you'd fail more often than not) and counts each one's time,
  the walk across each pillar and the time a fall would cost you. The arena card shows how many obstacles and about
  how long. It's worked out from the obstacles loaded around you, so it can only route through what the game has
  loaded. The arena's pillars stand on a 5 by 5 grid (not every pillar has a ticket dispenser, but they all count),
  and the route is worked out again every game tick from the pillar nearest you, so it follows you as you go.
  **Arena Route on Minimap** draws it on the minimap too, with a dot on the tagged pillar.
  **Arena Route Readout** adds what it found to the card (obstacles, links, the pillar nearest you, each step), for
  checking.
- **One Obstacle Training:** cross the same obstacle over and over instead of running a course (the arena's floor
  spikes back and forth, or any Agility shortcut anywhere), and after three reps in a row a card shows it: reps, reps
  and XP per hour, reps to your next level and goal. In the arena it's on the arena card, that obstacle is outlined
  thicker, and the route and the pillar warning step aside until you stop (a minute without a rep), tag a pillar or move
  on to another obstacle. Laps on a course count on the Course Card instead.
- **Single obstacles as your method:** the arena obstacles worth repeating (rope swing, pressure pad, floor spikes,
  spinning blades, darts) are each in the Agility card's **Method** list from their level to 99, with their best XP
  per hour; pick one and it's your suggestion and gets outlined. Right-click one in the arena and **Track for goal**
  picks it too (while Agility has a goal).
- **Run Alert Below:** while you're training Agility, an alert ("Your run energy is low") when your run
  energy drops below this (15%; 0 turns it off).
- **Obstacle Hover:** hovering an obstacle or shortcut shows its level, the course XP per lap and laps to level.

RuneLite's own Agility plugin has an arena timer and a lap counter too, and its Show Clickboxes outlines most arena
obstacles (Snuffy's colours leave those to it); with any of them on, Snuffy's clash warning says so.
**Settings (Agility):** Obstacle Hover, Course Card, Brimhaven Arena Card, Pillar Warning At, Voice/Chat: Pillar
About to Move (both on), Arena Obstacle Colours, Arena Route, Arena Route on Minimap, Arena Route Readout, One Obstacle Training, Run Alert Below,
Voice/Chat: Run Energy Low.

## Minigames: Wintertodt, Tempoross, Guardians of the Rift

**What it does:** a small card (top left) and voice/chat alerts in each of these minigames, only while you're in it.
Everything comes from the game's own boxes, chat and your inventory: information only, nothing is clicked, outlined,
hidden or reordered for you.

- **Wintertodt** (in its room): the card shows your **warmth** (red below Warmth Low At), the Wintertodt's
  **energy**, your **points** this round (green from 500, a reward crate), the **roots / kindling** you carry, and
  between rounds when the next one starts. Alerts: **Round Starting** (5 seconds before), **Brazier Out / Broken**
  (your brazier goes out, or breaks and hits you), **Out of Roots** (you run out while fletching or feeding),
  **Cold Stopped You** (the cold or snowfall hit you and stopped fletching, feeding, lighting or fixing; chopping
  carries on, so it says nothing then), **Warmth Low** (below **Warmth Low At**, 30%; once until it's back 10 above).
- **Tempoross** (while its box shows): the card shows its **energy** and **essence**, the **storm** (orange from
  70%, red from 90%: the game is lost when it fills), your **points** (green from 2,000, a reward permit), the raw
  and cooked fish you carry and the **damage** they'd do in the cannon (raw 10, cooked 15). Alerts: **Wave
  Incoming** (tether to a mast or totem), **Fire Near You** (lightning about to strike within 6 tiles of you; at
  most once every 20 seconds), **Double Fishing Spot**, **Vulnerable** (attack it; the next phase starts).
- **Guardians of the Rift** (while its box shows): the card shows the Great Guardian's **power**, your elemental /
  catalytic **energy**, guardians built, the **two altars open now** with the Runecraft level each needs (green you
  can use it, red your level is too low; boosts count), and where the **portal** is while it's open. Alerts: **Game
  Starting** (10 seconds before the rift opens), **Portal Opened** (chat says where, like "south west"; not while
  you're in the huge guardian remains yourself).

**Good to know:** RuneLite's own Wintertodt plugin can notify you of the same things, and the Tempoross and Guardians
of the Rift Helper Plugin Hub plugins have their own notifications; with any of them on, Snuffy's clash warning says
so. Those plugins also outline things (fires, totems, guardians) that these cards leave to them.
**Settings (Minigames):** Wintertodt Card, Voice/Chat: Round Starting, Brazier Out / Broken, Out of Roots, Cold
Stopped You, Warmth Low, Warmth Low At; Tempoross Card, Voice/Chat: Wave Incoming, Fire Near You, Double Fishing
Spot, Vulnerable; Guardians of the Rift Card, Voice/Chat: Game Starting, Portal Opened (all on).

## Issues? button

The **Issues?** button in the top-right corner of the skill tracker panel opens a new GitHub issue in your browser. Say
what you did and what you saw; a screenshot helps.

---

## Compact Overlays

One switch in **General** for less on a plugin's cards and HUDs (one in the Toolbelt for all of them; one in each
separate plugin that has overlays: Skilling Helper, PvM/Slayer, GemCrab Tracker). The other settings still decide
what shows when it's off.
- **Skill cards** (sidebar and pinned): the level bar, the goal and step bars, XP/hr and time to level.
- **Skilling hover cards**: what it is, what it needs and gives; no tools, XP rates or money lines.
- **Agility**: course laps, laps to your next level and marks; arena pillar and tickets; one-obstacle reps and reps to
  your next level.
- **Wintertodt / Tempoross / Guardians of the Rift**: the round, warmth, energy and points / energy, essence, storm
  and points / the power, open altars and portal.
- **PvM HUD**: the target, its health, weakness and the LOW warnings. **Monster hover card**: name, health, weakness,
  aggression and kill goal. **Slayer card**: the task, kills left, time to finish, the prayer and the item it needs
  (no task: the master and where).
- **GemCrab HUD**: the crab's health, when it burrows, whether you're attacking and the LOW warnings.
