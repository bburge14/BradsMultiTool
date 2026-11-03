# Snuffy's Toolbelt

A RuneLite plugin that bundles PvM, Gemstone Crab, Slayer and skilling helpers, alerts with voice lines,
a skill tracker and a planner for your dailies, timers and drops into one place. Everything can be switched on or off in the plugin's settings.

- [Installing](#installing): the all-in-one, or just the parts you want
- [Features](#features) (full guide with how-tos: [docs/FEATURES.md](docs/FEATURES.md))
- [Setup (Windows)](#first-time-setup-windows)
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
| **Snuffy's Planner** | A sidebar tab for dailies and weeklies, farming patches, bird houses and your own timers, your Slayer task, a drop tracker, a to-do list with notes, and a skill calculator |
| **Snuffy's Alerts** | Optional: one voice and volume for all of the separate plugins |

**From the Plugin Hub** (once they're published there): in RuneLite open the **Plugin Hub** (the plug icon
at the bottom of the configuration panel), search **Snuffy's**, and install either **Snuffy's Toolbelt**
or the separate plugins you want. Don't install the Toolbelt together with the separate ones: you'd get
everything twice. Every plugin has the idle and logout warnings; with several installed they're said once.

**Before they're on the Plugin Hub** (or to get updates the day they're made), use **Snuffy's Launcher**:
[github.com/bburge14/snuffys-launcher](https://github.com/bburge14/snuffys-launcher). It's only the launcher: it
downloads each plugin from its own repository, keeps them up to date and starts RuneLite with the ones you pick. See
[First-time setup](#first-time-setup-windows). (Working on the plugins themselves: see
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).)

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
- **Compact Overlays** (General): less on the PvM HUD (the target, its health, weakness and the LOW warnings), the
  monster hover card (name, health, weakness, aggression, kill goal) and the Slayer card (the task, kills left, time
  to finish, the prayer and the item it needs).
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
- **Compact Overlays** (General): the HUD shows just the crab's health, when it burrows, whether you're attacking
  and the LOW warnings.

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
  **Highlight Goal Items** (Goals)). Following a plan from the Planner, it's the item of the milestone you're on
  ("Make: Mithril dart (you have 24,620)", counted in items: 10 darts a go).
- **Banked** and **Materials worth** (goal cards): "Banked: 335K XP (lvl 68)" is the XP in everything for that
  skill in your bank and inventory at your level (each item used once, the most XP each first) and the level it
  would get you to; "Materials worth: 2.74M gp" is what those materials sell for on the Grand Exchange. Both are
  information only; each has its own switch (**Show Banked XP**, **Show Materials Worth**). The bank counts once
  you've opened it.
- **Plans on the card**: with a plan exported from the Planner, a blue **Step** bar ("Step: Lvl 67") under the gold
  goal bar shows the milestone you're on, and "Plan: Mithril dart (to 67)" says its method.
- **Compact Overlays** (General): one switch for less on the skill cards and the pinned skill: just the level bar,
  the goal and step bars, XP/hr and time to level. It also trims the Skilling Helper's other overlays (hover cards
  lose tools and rates, the Agility and minigame cards keep their key numbers).
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
- **Click timing** (only when run from this repository with the launcher; not in the Plugin Hub version) for 1-ticking: how many ms after the tick each click landed, an ideal click zone on the tick
  bar, a ding when on time and a different sound when late or when you miss a tick, and a summary of your last
  50 clicks.
- **Follow Attack Speed**: an attack timer for your own attacks. The cycle follows your weapon (and spells,
  Rapid, eating delays) and starts again each time you attack; it can **count down** to your next attack and
  show how many ticks **late** you are (-1, -2).
- **Hotkeys** (none set at first): reset the count to 1, show/hide the tick overlays, Ticks Per Cycle up/down.
- **Snuffy's Tick Counter** (also in the Toolbelt); settings under **Tick Counter**.

### Snuffy's Planner (sidebar)
Its own sidebar tab (the calendar icon), also in the Toolbelt. Six lists, each folds away under its heading and
each can be hidden in the settings; everything is saved per account.
- **Today**: your own dailies with the time to the daily reset: press **+** to add one (they untick at the reset),
  click to tick, right-click to edit or delete. While a list is empty it shows a few preset tasks as examples
  (greyed, not tracked; click one to add it). One voice line at login and at the reset if there are
  dailies to do; no chat spam.
- **Weekly**: your own weeklies, the same way, with the time to the weekly reset (Wednesday). Adding a daily or
  weekly can start from a preset task (Battlestaves, Herb run, Tears of Guthix, Kingdom of Miscellania...: name, where
  and picture filled in), and each can have a picture. Nothing is read from the game.
- **Timers**: every farming patch, compost bin and Hespori you've been to and when it's grown (moved here from the
  Skilling Helper), your bird houses, and timers of your own; each with a ready alert in the game chat. When you add
  or edit a timer you pick what it does when it's done: the voice line or a sound (Bell, Chime, Anvil, Boop, Door;
  picking one plays it) and how many times; Default follows the **Ready Sound** setting.
  **GE buy limits** there too: each item you've bought on the Grand Exchange in the last 4 hours, how much of its buy
  limit that's used ("7,000 / 7,000 bought") and when it resets, and for a buy offer that isn't filling whether it's
  the limit ("At the limit: more in 2h 48m") or your price ("Under the limit: it's your price", yours against the GE
  price). It goes by your own offers as they fill (the game keeps no times for trades): what filled while you were
  logged out counts from when it was seen, so it resets "by" that time. A chat message when a limit you'd used
  resets (**Chat: Buy Limit Reset**), and a plan's **Materials needed** says how long what's short takes to buy at
  the limit.
- Only the dailies and weeklies reminders go to the split private chat (they fade); every other alert uses the game
  chat.
- **Slayer**: your task, kills left, points and streak, straight from the game.
- **Drops**: each monster or chest on its own: kills or opens and the Grand Exchange value on the list, and a page for
  each with every item it has dropped, the drop rate you've seen ("1 in 45"), its best drop, kills since a valuable
  drop and this session beside all time; plus your latest drops
  (valuable ones highlighted); reset one or all.
- **Notes & to-do**: a to-do list and a notes box.
- **Calculator**: a skill calculator. Pick a skill from the grid; your level and XP fill in from the game (type over
  them to plan ahead). Skills with a goal have a gold line in the grid, and **Goals** under it lists them all with how
  far along you are (click one to open it). Goals are the Skilling Helper's: with it running (installed and on),
  the Planner shows and edits the same goals; without it, the Planner doesn't look at them at all. Likewise the
  Skilling Helper only follows a plan from the Planner while the Planner runs. The target starts at the skill's goal, or the next level; a target you type is kept for that
  skill until **Reset** (next to it) or **Set as goal** (sets the skill's goal just like the Skilling Helper's Set
  Goal). Tick the XP bonuses you use (skilling outfits, diaries, altars, the bonecrusher...; ones that can't go
  together untick each other). Three views:
  - **Actions**: every action with its XP each, how many to your target, the time where the wiki's training guides
    give an XP rate, and how many you have (bank as last opened plus inventory). With **Show GE Prices**, the money:
    "Mats 254K · Sells 226K" is what the materials and the products are worth on the Grand Exchange (neither is
    profit), and the net beside it, with a **green plus** for a gain or a **red minus** for a loss ("-28K"), plus the
    net gp per XP. Hover a card for all of it in words. Darts, arrows and bolts count per item (10 darts a go, each
    with its share of the materials). **Sort** (next to the search) orders them: by level (low to high, at first, or
    high to low), what you can do first, most XP each, fastest, cheapest per XP, most profit or name; the guide's
    method at your level is marked in green; search to filter; on a free world members-only actions are left out.
  - **Banked**: what you own instead. The tiles show the XP in it all, your level now and after ("Level · aim 67":
    your target beside it), what the materials are worth and the **Net** of making it all. Each card is one thing you
    can make, and the cards **stack**: each shows the levels it takes you through on top of the ones above it
    ("lvl 59 → 61", then "lvl 61 → 63"...), so the last one ends at the total. Things that share materials split them
    as the total does ("x 0 of 620 · used above"); something above your level says what it needs ("needs lvl 80")
    and isn't counted.
  - **Plan**: the skill's **goal** comes first, as a card like a plan's (gold): its levels, what it's set to train
    (your Track for goal pick, the plan the tracker follows, or the wiki's suggestion) and how far along you are.
    It's the same goal as the Skilling Helper's: click it (or right-click) to **Change goal**, **Remove goal** or
    **Make a plan** (a new plan to that level, starting with what the goal trains). Then
    as many **plans** per skill as you like, each with a name. **+** makes a new one; each plan's card
    shows its levels, your level and how far along it you are (a bar and a percentage); click one to open it (it
    unfolds under its card), click it again to fold it away; right-click to rename, duplicate or delete. Under the
    list, links to your other skills' plans. A plan is a goal and milestones on the way, each with its own method
    ("1-20 Logs, 20-50 Willow logs, 50-99 Teak logs"): start from the wiki's suggestions, or right-click an action >
    **Add to plan** (it goes in at its own level: Atlatl darts at 74 inside Adamant darts 67-81 makes Adamant 67-74
    and Atlatl 74-81; when the plan's full it asks to raise its goal), or **+** to add one at levels you type (the
    milestones it overlaps make room). To fit something in (Atlatl darts 74-81 inside Onyx bolts 74-99), right-click the milestone > **Insert a milestone in it...**, or Add to plan from the list (it asks which levels when the plan's already full): the one it lands in makes room. Making a milestone shorter (Edit...) leaves a gap you can click to fill. **Money plan** (under the materials): how to do the plan
    within a **budget** you set for it (like 50m; empty uses all your coins): for each milestone what to buy that you
    don't own, in how many rounds of buy, make and sell (what each sale brings in buys the next lot, so you only need a
    round's worth at once; the buy limits allow a round every 4 hours), the budget left after it, and where it runs out
    if it can't carry the plan; on top, whether a budget is set (else it uses your coins), what the plan costs after
    sales, the **budget it needs** to reach the goal, and what's left (or how short it is). Milestones, Materials needed
    and Money plan each fold away (click the header). Sales are after the Grand Exchange's 2% tax everywhere. Your **real trades** count too: an item you've bought or sold on the GE in the last week goes at what you really paid or got (**Use Your Own Prices**; the tooltips say "your price"), and with a budget set, **Since you set the budget** shows what you've really spent on the plan's materials and got for its products, and the budget that leaves. **Get materials yourself**: right-click a material in Materials needed > **Gather it yourself** (logs from Woodcutting, fish from Fishing, ore from Mining) or **Make it yourself** (bars in Smithing): it shows which skill, is left out of the money plan's buying, and its tooltip says the XP and time that takes against what buying costs ("saves about 310K an hour"). A milestone's right-click menu has the same choice for its materials. **Ironman** (**Plan As Ironman**: from your account, always or never): everything you can gather or make is got yourself by default, all the way down (dart tips from Smithing, their bars from Smithing too, the ore and coal from Mining), and the money plan is left out. That skill's Plan view lists it under **Supplies for other plans** (right-click a supply > **Add to the Woodcutting plan** makes it a milestone there) ("Raw shark for Cooking 96 / 6,060", the XP and time, what it saves; click to go to that plan). Click a milestone for its **breakdown**: for one of what it makes, each material (how many x its price) and what it sells for, and the net, then the same for the whole milestone. Right-click a milestone > **Find a better method...** to price every action over its levels, with the best picks on top (cheapest per XP, fastest, most profit, most XP each): click one to use it. Each milestone shows what's left of it, one thing a line (how many to go and the XP; the time and gp per XP; what the materials **cost** and what the products **sell for**; the **net**), counting down as you train, with a bar and how far through it you are ("147K xp left · 65%"); once you pass its end it's ticked off as Done, and a game chat message says so and names the next one (and when you reach the plan's goal; **Chat: Milestone Done**); **Whole plan** adds them
    up (XP to the goal, time to it, average XP an hour, net); **Materials needed** lists everything the milestones
    still need in full against what you have ("27,800 / 24,610"), green when you have it all, red when you're
    short, and the tools. A plan goes by your real XP: typing in Current doesn't mark milestones done. With the
    Skilling Helper installed, **Export to tracker** makes its goal card follow the plan (one plan per skill at a
    time).
  - **Plan overlay** (without the Skilling Helper installed; its pinned skill card shows the plan otherwise): a
    card on the game screen for the plan of the skill you're training (the one open in the Calculator), or the plan
    you **pin** (the pin on its card in the Plan view, or right-click > Pin to screen; it stays up whatever you
    train until you unpin it, there or by right-clicking the overlay): the
    milestone's bar with the XP left, its method, actions left, the next milestone, and your materials for it in
    green or red. **Plan Overlay** (Calculator settings) turns it off; **Compact Overlays** (General) keeps just the
    plan, the bar and the actions left.
  - **Your plan bar**: under the Actions and Banked lists, the open plan's milestones (and the skill's goal) stay in
    view while you scroll, to compare; click its title to fold it, **Edit** to open the Plan view.

  Made from RuneLite's own Skill Calculator; information only.

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
- Pre-recorded voices (so they work on any OS): Amy and Cori (US and UK women), Alan and John (UK and US men), or
  **Random** for a different one each alert. Running it from this repository (the launcher) also has Arctic (a
  Scottish man), Wise Old Man, TzHaar and Ghost. Changing the voice plays a sample.
- Chat messages use the same words as the voice line, with details in brackets.
- **Speak Only When Unfocused** (General, off at first): voice only while you're in another window; chat
  messages still post.

## First-time setup (Windows)

No commands and nothing to install: the launcher gets what it needs (the plugins, and a Java of its own if your PC has
none) by itself.

**1. Download Snuffy's Launcher:** [snuffys-launcher (zip)](https://github.com/bburge14/snuffys-launcher/archive/refs/heads/main.zip)
(just the launcher: it downloads the plugins when it first opens).

**2. Unzip it** where you'd like to keep it (right-click the zip > **Extract All...**).

**3. Double-click "Snuffy's Launcher"** in the folder (Windows may ask once: **More info > Run anyway**), then click
**Make a desktop shortcut** at the bottom of the window so it's on your desktop from then on. The first time, it
downloads every plugin (a minute or two), and Java if you have none (about 190 MB, once).

**4. Using Snuffy's Launcher.** It's a small dark window that:

- **updates** the plugins each time you open it, and while it's open checks every minute in the background and
  pulls any update straight away: two chips under its name say where your copy of the code is ("Code: up to date") and what the open RuneLite is running ("RuneLite: latest code", or in red "RuneLite: 3 updates behind, Relaunch"), and **What's new** lists the changes, each with what it touches (the plugins it changes, which need a Relaunch, or the launcher and docs only, which don't; the RuneLite chip only counts the ones that need a Relaunch) (**Check again** to retry; when you're offline or have changed
  files here, it says so and you launch the version you have);
- **warns you when the open RuneLite is out of date**: it remembers which version each launch was built from (even
  if you close and reopen the launcher), and when there's a newer one its chip turns red ("RuneLite: 3 updates
  behind, Relaunch", with what's not in it yet listed below), makes **Relaunch** bold and pops up a Windows notification;
- checks your **setup**, each row with a button to fix it (once everything's ticked the Setup card folds to
  "All set"; **Show** opens it again):
  - **RuneLite** installed (it looks in RuneLite's usual folders, its uninstall entry, Start menu shortcuts and
    the Jagex Launcher's folder; **Find it...** lets you point to `RuneLite.exe` if it's somewhere else);
  - **session saving** on in RuneLite (configure): **Turn it on** adds `--insecure-write-credentials` for you
    (keeping a backup of RuneLite's settings) or opens the configure window to add it yourself;
  - a **Jagex session** saved, for which character and when: **Save one** opens the Jagex Launcher, tells you
    what to do (press Play, log in once) and ticks the row as soon as the session is saved. It only reads the
    character's name and the date, never the session itself;
  - **Java** (needed to build: the launcher gets one of its own if your PC has none);
- lets you pick the **plugins to load**: **Snuffy's Toolbelt** on its own (it already includes every separate
  plugin), or **Separate plugins** with a switch each. Your pick is remembered for next time. Each one shows its version (from its GitHub repository);
- **Relaunch** closes RuneLite (as its X button would, answering OK to RuneLite's "Are you sure you want to exit?"
  for you; forced if it hangs), updates, and starts it again with your pick;
- **Launch RuneLite** builds and starts it silently (no console window): the button fills as it goes, building,
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

The names are `toolbelt` (the default), `skilling`, `pvm`, `gemcrab`, `tick`, `planner` and `alerts`. The Toolbelt always
loads on its own (it already includes the others). Each separate plugin has its own settings, so settings you
change in the Toolbelt don't carry over to them (and the other way round).

</details>

## Settings guide

Each plugin's settings start with **About**: hover its heading for what the plugin does and which other plugins
clash with it, and switch on **Open the Guide** to open the full feature guide. While a clashing plugin is on (or the
setting of it that clashes), every one of Snuffy's plugins shows a small yellow warning icon among RuneLite's info
boxes (top left of the game; hover it for the clashes and what they mean); nothing is posted in chat. It's live: turn the clashing plugin or setting off and the icon goes within a few seconds. Don't want
any of it? Turn off **About > Clash Warnings** in that plugin's settings. Clashes include the dedicated timer plugins, RuneLite's Hunter, Mining, Fishing (spot icons, tiles,
minnows), Motherlode Mine (veins), Wintertodt, Slayer (task highlight), Metronome, Idle Notifier and
Loot Tracker, and the Tempoross,
Guardians of the Rift Helper and Sailing plugins' notifications.


All sections except General start collapsed.

| Section | What's in it |
|---|---|
| General | Voice on/off, voice, volume, chat messages on/off; **Compact Overlays** (less on the plugin's cards and HUDs: one switch in the Toolbelt, one in each separate plugin that has overlays) |
| Potion Buffs / Health / Prayer | Buff timers and alerts, thresholds, repeats, orb flash, their Voice/Chat switches |
| Combat Alerts | Spec, antifire, poison, stamina, ammo, runes, cannon |
| Idle | AFK alert and idle logout warning |
| PvM HUD | HUD on/off, monster hover, DPS mode, which lines to show |
| Slayer | Task card and each of its parts, hotkey, Slayer info on monsters, task monster outlines, item warning, superior and task complete |
| Gemstone Crab | HUD, outlines and their colours, crab alerts |
| Skilling | Hover cards, XP rates on cards, inventory full, resource gone, batch complete |
| Woodcutting ... Sailing | One section per skill with its pies, cards and alerts |
| Skill Tracker | Which fields to show, goals, suggested methods and highlights, goal items, auto-pause, ignored skills, level-up alerts |
| Planner: Today ... Calculator | Each Planner list's Show switch (Calculator: Show Calculator, Show GE Prices, Hide Members On Free Worlds); the dailies/weeklies voice line; farming, bird house and timer alerts; drop colours and the valuable drop amount |

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
- The Planner's Calculator is made from RuneLite's own Skill Calculator (BSD-2-Clause, (c) the RuneLite
  contributors): its actions, levels, XP and bonuses are converted from that plugin's source by
  `scripts/update_calculator_data.py`, and its sums work the same way.
- The minigame and Sailing alerts use what these Plugin Hub plugins (BSD-2-Clause) worked out:
  [Tempoross](https://github.com/nmlynch94/runelite-external-plugins) (messages, ids, fish damage),
  [Guardians of the Rift Helper](https://github.com/DatBear/Guardians-of-the-Rift-Helper) by DatBear (messages,
  altar icons and levels) and [Sailing](https://github.com/LlemonDuck/sailing) by LlemonDuck (low boat HP).
- The Hunters' Rumours card reads your rumour the way [Hunter Rumours](https://github.com/geel9/runelite-hunter-rumours)
  (BSD-2) does: from the guild hunters' dialogue, the Quetzal whistle and the rare part's message.
- Game information, Slayer master chatheads and task icons are from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki).
- Voices are generated with [Piper](https://github.com/rhasspy/piper) (see [VOICE_CREDITS.md](VOICE_CREDITS.md)).
