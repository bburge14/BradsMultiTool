# Testing checklist

Work through these a section at a time in game. Anything that doesn't behave as described: note what you
did and what you saw (a screenshot helps), or use the **Issues?** button at the top of the tracker panel.

## Login and alerts
- [x] Nothing speaks or posts in chat before you click "Click here to play".
- [x] Logging in with low prayer or health doesn't announce it.
- [x] Turning a Voice: switch on plays a preview; turning it off silences only that alert.
- [x] Chat messages match the voice line, with details in brackets.
- [x] The Toolbelt's settings open, with older settings kept; no voice previews when the client starts.

## PvM
- [x] PvM HUD appears on attacking a monster: HP bar, aggro line, weakness, style, DPS, last/max hit.
- [x] Elemental weakness shows the Strike spell icon for that element.
- [ ] PvM HUD Weakness line is green when your style hits it, red when it doesn't (like the hover card).
- [x] Monster hover: aggression reads "No (lvl X+)" for low-level monsters when you're over twice their level.
- [ ] God Wars: aggression changes when you equip a god item.
- [ ] Buff timers count down correctly; running out / expired alerts fire once (plus repeats if on).
- [ ] Running out fires again after another divine dose at the same boost, and after re-dosing a regular potion.
- [ ] A buff's line on the HUD is red while within the running-out window and green whenever it's above it (e.g. after re-dosing).
- [ ] Running out still fires right after logging in or hopping (says "under 1:00 left" when the timer isn't synced yet).
- [ ] Hover a gargoyle: Max Hit: 11, Attack Speed: 4 ticks (2.4s); General Graardor lists 60 (melee), 35 (ranged);
      Show Max Hit and Speed off hides both.
- [ ] Wrong Protection Prayer on: Protect from Magic against a gargoyle says "Wrong protection prayer" (chat: attacks
      with melee) once; switching to Protect from Melee stays quiet; no prayer on stays quiet; a mixed-style
      monster (Graardor) never sets it off.
- [ ] Unpotted Reminder on: attacking with no boost says "No combat boost active", not again within 2 minutes;
      after a super combat it stays quiet. Your Style's: a Magic boost doesn't count while meleeing.
- [ ] Loot line: after a kill with drops, "Loot:" shows their GE value; "/h" appears after a minute; HUD Reset clears it.
- [ ] Death (e.g. somewhere safe-ish with cheap items): chat/voice says where; the gravestone info box counts down from
  15:00, pauses while logged out, and goes when you're back at the spot. No box after a death in an instance, the
  Wilderness, or a safe minigame (nothing lost).

## Buff bar and monster effects
- [ ] Buff bar: a potion shows its icon, +boost and time left (divine exact, regular "~" until synced); antifire,
      super antifire, stamina and antipoison/antivenom show with timers; anglerfish shows Hitpoints +N.
- [ ] Debuffs in red: poisoned, venomed, a drained stat (e.g. Defence drained by a monster); Show Debuffs off hides them.
- [ ] Hover an icon: its name. Alt + drag moves it; Layout and Icon Size work; nothing active: no bar.
- [ ] With the bar on, the PvM HUD has no potion rows; running out / expired alerts still play.
- [ ] HUD Status: Poisoned/Venomed after poison or venom hits the monster; Frozen 0:xx after Ice barrage (etc.);
      Burning from burn hitsplats.
- [ ] HUD Defence: Dragon warhammer hit lowers it 30% ("Defence: 140 (-60)"), Elder maul 35%, BGS by the damage.
- [ ] Buff bar: Overload (Nightmare Zone or a raid) shows one Overload icon with ~time instead of potion icons and
      boosted stats; Prayer regeneration potion; Staff of the dead special (1:00, gone when you unequip it);
      Vengeance (ready, then the 0:30 cooldown); Imbued heart cooldown after using it.
- [ ] Buff bar debuffs: Frozen after an ice spell or bind hits you (gone once you can move); Teleblocked with its
      time left (Wilderness).

## Gemstone Crab
- [ ] When the crab moves, its stats stay up 12s marked (moved) with a "Radar in" countdown, then the radar.
- [ ] No AFK alert while attacking the crab.
- [ ] Crab card, on its own (`runBrad "-Pload=gemcrab"`) and in the Toolbelt alike: potion buffs with time left
      (divine exact, regular "~" until the first stat drop), red while running out (Warn Before Ending);
      Potion Buffs > Show Buffs on HUD off hides them; Prayer: LOW / Health: LOW at or below Low Prayer/HP.
- [ ] GemCrab on its own: potion running out / run out alerts (voice and chat), with its Potion Buffs settings.
- [ ] Gear Check on the crab card: Str +N while meleeing, Ranged str +N while ranging, Magic dmg +N% while
      casting (follows a style switch); a Necklace of anguish or berserker ring worn for the other style shows
      under Off-style; red Str +0 with no strength gear.
- [ ] `-Pload=separate` (PvM and GemCrab both): each potion alert is said once, not twice.
- [ ] Right-click the crab card > Reset clears its damage/DPS; right-click the PvM HUD > Reset clears PvM's;
      the tracker's Reset all no longer touches either.
- [ ] `-Pload=separate`: hover a pickpocket NPC (Attack not left-click): only the Thieving card, no PvM card.
- [ ] PvM buff timers and buff alerts still work as before (PvM on its own and in the Toolbelt).
- [x] Radar and outlines only near the crab's mines (not in Draynor or other caves).
- [x] Outlines draw under the minimap and inventory, not over them.
- [x] Shell green when you can mine it, red when you can't.

## Slayer
- [ ] Task card, Ctrl+Shift+S hides/shows it (chat says hidden/shown), missing-item warning.
- [ ] Kill rate survives walking between areas.
- [ ] Hide the Slayer card with Ctrl+Shift+S, restart the client: it stays hidden (and shown stays shown).
- [ ] Finishing a task says "Slayer task complete" (chat adds the task name).
- [ ] Half the task done says "Slayer task halfway done" (chat: N left), once; 10 left says "Slayer task nearly
      done" (Task Nearly Done At; 0 turns it off). Neither on login part-way through a task.
- [ ] No task: card shows the best master for your levels, where they are, routes and the next master.
- [ ] Pictures: master chathead with no task, monster icon on a task (Slayer gem if none); nothing overlaps.
- [ ] Each Slayer switch hides its part; Places Shown / Routes Shown change the counts.
- [ ] Konar task: subtitle says the area and that place is listed first.
- [ ] Hover a banshee: Slayer section with Level 15, Wear / Use: Earmuffs (red without, green with), Task: Banshees, Slayer XP.
- [ ] Below the Slayer level: "(can't damage)" in red. PvM HUD shows the same while fighting.
- [ ] Task monsters outlined; non-task monsters not. Konar task: same monster elsewhere isn't outlined and its hover says Wrong place.
- [ ] On task: Where To Go lists places (Multi/Cannon/Safespot) and how to reach the first one.
- [ ] A place whose tags don't fit beside it (Fire giants: Charred Dungeon) has them on the next line,
      right-aligned and grey.
- [ ] Right-click the card > Show all / fewer places lists every place, and back.
- [ ] Points and streak on the card (same as RuneLite's Slayer infobox tooltip), also on the no-task card; at a
      streak of 9, 49, 99... the gold "Next task is your 10th/50th..." note; finishing that task before says
      "Your next slayer task is a milestone" (chat: task N, Nx points). Not on login.
- [ ] Supplies Used counts food eaten and potion doses on the task; logging out and in keeps them; a new task
      starts at 0.
- [ ] Right-click a monster > Set kill goal, type 5: the hover card shows Kill Goal 0 / 5 and the card lists it;
      kills count up; the 5th says "Kill goal reached"; Clear kill goal and Clear finished kill goals remove them;
      goals stay after a relog.
- [ ] Finish-off marker: rock hammer over a gargoyle at 9 HP or less, ice cooler over a lizard at 4 or less, salt
      over a rockslug, fungicide over a zygomite. With RuneLite's Slayer plugin on (Show Monster Weakness), task
      gargoyles/rockslugs/zygomites only get its marker (not two); lizards still get ours.
- [ ] Pray: line (Fire giants: Protect from Melee; Cave kraken: Magic; Smoke devils: Missiles); none for
      mixed tasks (Spiritual creatures); Protection Prayer off hides it.

## Skilling cards (hover)
- [ ] Trees, rocks, stalls, pickpocket NPCs, fishing spots (Bait at a sea spot shows sardine/herring, not pike).
- [ ] Fishing spot card: each catch has its fish's picture, "Trout (50 xp)" and "Lvl 20" with room between them (no "Level ..." cut off).
- [ ] Farming patches: empty patch shows what you can plant; planted patch shows crop, stage, grown-in time, compost after you use it, watered.
- [ ] Hunter creature card; bird house card on Fossil Island.
- [ ] Hover a young tree (Set-trap): Net trap card listing the lizards/salamanders nearby with levels.
- [ ] Monster hover / PvM HUD show HP and weakness for monster variants (e.g. Sulphur Nagua, new bosses' forms).
- [ ] Runecraft altar card (runes per essence at your level).
- [ ] Agility course obstacle card (laps to level) and a shortcut card.
- [ ] Cooking: hover a range or fire with raw food: each food shows XP (or the level it needs), burning, and XP for all.
- [ ] Smithing: hover an anvil with bars (best item, how many, next unlock) and a furnace with ores (bars, how many with your coal).
- [ ] Skilling Hover Cards > Best Choice By = Most profit: anvil Best changes to the most profitable item and a Profit line appears (anvil and furnace).
- [x] Auto retaliate off: hovering a pickpocketable NPC whose left-click is Attack shows the Thieving card.
- [ ] Sailing: charting spot (task text on its own lines, riddle answer for diving tasks), shipwreck (best hook) and shoal cards.
- [x] XP rates on cards only while you're training that skill.
- [x] An NPC walking past a tree doesn't change the card.

## Resource timers
- [x] Trees: pie on trees being chopped (also others' trees), centred on the canopy; turns into a full pulsing red pie when the tree can fall on the next log; no pie on a tree already being chopped when you arrived.
- [ ] Mining: Motherlode veins, calcified rocks, Daeyalt essence, shooting stars.
- [ ] Every resource timer: its time and skill icon above it (Timer Text / Timer Skill Icon). Fishing spots: age
  ("1:42"), with Mark Newest Spot on (off by default), a gold ring around the Fishing icon on the newest timed spot, your spot outlined in Your Spot Colour.
- [ ] Fire timers are off by default (fishing spot timers on); turned on: green until the earliest time, then a red ring
  filling to the latest; hovering says "Won't move for 1:20 (gone by 4:00 at the latest)" / "Can move any time (...)".
- [ ] Campfire Timers (on by default) still show with Fire Timers off.
- [ ] Fishing spots: a timer only on spots that moved while you were there; none on a spot that was already there
  or came into view as you walked up, until it moves; a full timer again after each move (minnow spots included).
- [ ] Fishing spot moved: the voice line plays when the spot you're fishing moves, even after fishing it 3+ minutes.
- [ ] Timer Style: Tile and icon (no pie: the outline goes green/yellow/red, pulsing red when due; traps, fires and campfires keep a pie; hover a fishing spot for its times) and Floating timer (pies).
- [ ] Hunter: pie on traps you lay, green when caught; a trap that springs empty turns a full pie in Failed Trap Colour (purple) saying "Empty", and back to a fresh timer once you reset it.
- [ ] Fires: a pie only on a fire you cook on (Use food on it), none on fires you just light or walk past; it
      runs out at about 60 s, then pulses red until the fire goes; it sits still, not bouncing with the flames.
- [ ] "The tree has fallen." (new wording) on oaks and up; no tree/rock alert on regular trees, copper or tin.
- [ ] Sailing: shipwreck pies.
- [x] Pies don't draw over the bank or other interfaces.

## Skilling alerts
- [x] Tree fallen / rock depleted / fishing spot moved only for the resource you're working.
- [x] Inventory full while working a resource.
- [ ] Coin pouches full (at 28, or your Ardougne diary cap).
- [ ] Batch complete after a run of cooking / smithing / crafting / fletching / herblore.
- [ ] Sailing: Boat Health Low below 30% on your own boat (once until repaired); Trim Sails when a gust opens the
      trim window; neither on someone else's boat or ashore.

## Minigames
- [ ] Wintertodt: card in the room (warmth, energy, points, roots/kindling, next round); Round Starting at 5 s;
      Brazier Out / Broken; Out of Roots; Cold Stopped You only when hit while fletching/feeding (not chopping);
      Warmth Low once below 30%.
- [ ] Tempoross: card (energy, essence, storm, points, fish, damage) only during the game; Wave Incoming; Fire Near
      You only for strikes close to you; Double Fishing Spot; Vulnerable.
- [ ] Guardians of the Rift: card (power, energy, guardians built, open altars red above your level, portal);
      Game Starting at the 10-second message; Portal Opened with its direction, not while in the remains.
- [ ] With RuneLite's Wintertodt / Tempoross / GOTR Helper notifications / Sailing's low HP notification on, the
      clash warning names them.
- [ ] Hunters' Rumours: take a rumour from a guild hunter (or use the Quetzal whistle's Rumour): HUNTER RUMOUR in the
      tracker names the creature, method and hunter, Where: (hover for every place); each catch adds to Caught (the
      pity number drops with guild hunter outfit pieces on); the rare part plays "Rumour complete" and the card says
      to take it back; handing it in clears the card.

## Skill tracker
- [x] Cards, fields switch on/off without gaps, progress bar colours.
- [ ] Items: chop, fish, mine, cook: the card (and pinned skill) shows "Logs: 342 (1,050/hr)", "Fish:", "Ore:"/"Gems:",
      "Cooked: ..., 4 burnt (3%)"; Reset rate / Reset clears them; Show Items Gathered off hides them.
- [ ] Keep Session After Restart: train, close the client, reopen and log in within 30 minutes: the cards carry on
      (chat says so), XP/hr about the same; after longer (or with it at 0), a fresh session. Reset all skills, restart: fresh.
- [ ] Skills Tab Goal Bars: a thin gold bar along the bottom of each goal skill on the Skills tab, matching the card's
      goal bar; none with the setting off or on other tabs.
- [ ] Training combat (e.g. Gemstone Crab): cards stay still, no field wraps onto two lines; GOALS / THIS SESSION headings centred.
- [x] Actions left hidden for combat skills.
- [ ] Production goal (e.g. Cooking) after opening the bank: "Materials: ... gp" under Banked on the card and the pinned skill; Show Materials Value off hides it.
- [x] Set Goal dialog: the skill list shows your levels; picking one shows your level, XP and current goal.
- [x] Monsters with no Slayer level (cows, goblins) have no Slayer section.
- [x] Set Goal button: pick a skill, enter a level (e.g. 80) or XP (e.g. 2000000); the skill appears under GOALS.
- [x] A goal skill stays pinned under GOALS after logging out and back in, even before you train it.
- [x] Actions Until Goal on: a goal skill's card counts actions to the goal (hover: "until your goal"); off (default): to the next level.
- [ ] Hover Actions on a goal skill's card: "Next level: N actions" and "Your goal: N actions", the shown one marked (shown);
      with Actions Until Goal on and a method change before the goal, a "Shown: until level X..." line.
- [ ] Goal card shows "Train: ..." and "At N: ..."; Training Focus changes it.
- [ ] Train: names the specific thing: Herblore 19 "Serum 207 (Tarromin potion (unf) + Ashes)", Fishing 25 "Fly
      fishing: Trout, Salmon", Fishing 60 "Barbarian Fishing", Firemaking 45 "Burning logs: Maple logs"; hovering
      it shows the guide's description.
- [ ] Nothing quest-locked: without Shades of Mort'ton started, Herblore 15-21 says Strength potion (not Serum
      207); without Barbarian Training started, Fishing 58+ isn't Barbarian Fishing; Lunar spells only after Lunar
      Diplomacy; Bloodwood only after The Blood Moon Rises.
- [ ] Where: line under Train (Fishing 30: "Prifddinas (+4 more)"; Ranged 50 with MM2: "Kruk's Dungeon"), on the
      pinned overlay too; hover lists all places; none shown for methods the guide gives no place for.
- [x] With a Woodcutting/Mining/Thieving goal, the suggested trees/rocks/stalls/NPCs are outlined.
- [ ] Cooking goal with raw food in inventory/bank: card says Make: ..., the raw food is outlined in inventory and bank, hover shows the goal tooltip.
- [ ] Highlight Goal Items off removes the outlines.
- [ ] Levelling into a new method's range (e.g. 35 Woodcutting with a goal) updates Train: and posts "you can now switch to ..." in chat.
- [ ] Goals in combat skills, Prayer, Slayer, Sailing, Herblore each show a Train: line (Slayer: Tasks from <master>).
- [ ] Prayer goal with bones in inventory/bank: Use: Bury <best bones>, bones outlined.
- [ ] Hunter goal: the suggested creatures are outlined.
- [ ] Hunter goal: right-click a different creature > Track for goal; it's outlined instead and the card says Tracking: ... (your pick). Untrack / Use suggested method restores the suggestion.
- [x] No Track for goal option on things whose skill has no goal.
- [x] Prayer goal: right-click bones in the inventory > Track for goal > Gilded altar; the card says Tracking: Dragon bones (Gilded altar), Offer: ..., and the actions left drop to about a third. Green marks the current pick; Untrack for goal is at the bottom (check the list's order).
- [x] Prayer goal: right-click blessed bone shards > Track for goal > Sunfire wine; tools list the sunfire wine and XP each is 6.
- [x] Cooking goal: right-click raw fish in the bank > Track for goal; the card says Cook: that fish.
- [x] Card > Pin to screen shows the skill overlay; pinning another skill replaces it; overlay right-click > Unpin removes it; stays pinned after a restart.
- [ ] Training Focus: Money maker shows Money: <guide> (~X/hr); Cheapest shows Cheapest: <recipe> (gp/xp) for making skills and the fastest method for gathering; Other methods changes the guide method.
- [x] Clear goal moves the card back under THIS SESSION (or removes it if untrained).
- [x] Auto-pause after the set minutes without XP; resumes on XP.
- [x] Level-up voice says skill and level.

## Tick counter
- [x] Count Over Head: the count shows above your character, 1 to Ticks Per Cycle, first tick in its colour.
- [x] Tick Boxes: one box per tick of the cycle, the current one lit; Horizontal and Vertical; drawn once in the
      Toolbelt (not two stacked boxes); moves with Alt + drag and keeps its place.
- [x] Tick Bar: fills smoothly over each tick and snaps back on the tick; Bar Width.
- [x] Metronome on/off; each Tone plays, with the second sound on the first tick; First Tick Only; Volume.
- [ ] Follow Attack Speed: cycle matches the weapon (whip 4, shortbow on Rapid 3, autocasting 5); 1 lands on each attack.
- [ ] Follow Attack Speed: being hit while not attacking (block animation), eating, alching or pickpocketing
      doesn't restart the count; nor does an animation before your attack delay has run out.
- [ ] Eat a shark mid-cycle: the count holds on 1 for 3 extra ticks and 1 still lands on your next attack; a
      karambwan adds 2.
- [ ] Count Down To Attack: over head 4, 3, 2, 1 on a 4-tick weapon, the boxes numbered 4 3 2 1 (half boxes
      3.5...); eating shows the extra ticks (e.g. 5).
- [ ] Late Ticks: stop attacking: -1, -2... in red up to Late Ticks, then back to the plain count; 0 never shows it.
- [ ] Weapon swap: spec with a different-speed weapon then swap back: the cycle after each attack is that
      attack's weapon's speed.
- [ ] Hotkeys (bind each in Hotkeys): Reset Count makes this tick 1; Show/Hide hides and shows the count,
      boxes and bar (chat says which; the metronome keeps playing); Ticks Per Cycle Up/Down change the setting
      (1-10, chat says the number). Unbound: nothing happens. Also in the Toolbelt (Tick: Hotkeys).
- [x] Click Timing: "NNNms" rises over your head on each click (green, red past Late After); the bar marks the
      click; the summary counts on time / late / missed. Right-click "Track for goal" isn't counted.
- [x] Ideal zone: green on the tick bar from Ideal From to Ideal To; clicks in it show green, others white
      (red late); the summary counts "ideal".
- [ ] Snuffy's Tick Counter on its own (`runBrad "-Pload=tick"`): its settings open (Tick Counter section open), everything works.
- [x] Click Sounds: ding on time, Late Sound (each of the four) when late.
- [ ] Click Every Tick: Late Sound on a tick with no click while 1-ticking.
- [ ] Actually tick skilling with it (e.g. 3-tick or 1-tick): the bar, ideal zone and sounds help, and the
      ms and summary match how it felt.
- [x] Loading and the welcome screen after login: no count, boxes, bar or sounds.
- [x] Count Over Head sits just above the head.
- [x] All off: nothing shows and no sound.

## Same Voice For All (`runBrad "-Pload=separate"`)
- [ ] Change the Voice Persona in one plugin: the others' settings show the same voice, and only one sample plays.
- [ ] Voice Volume follows the same way.
- [ ] Turn Same Voice For All off in one plugin: changing its voice leaves the others alone, and theirs leave it alone.
- [ ] Each plugin keeps its own other General/Idle settings (e.g. AFK Alert off in one, on in another).

## Idle
- [x] AFK alert after training then stopping with no input; never while banking or in shops/GE.
- [x] No AFK alert after logging in without doing anything (login's XP report isn't training).
- [x] Idle logout warning before the logout.
- [ ] Run Energy Low Below 30: run below 30%: "Your run energy is low" once, again only after it's back above 40.
- [ ] Training Agility with Run Energy Low Below on: only one run energy alert (the Agility one stays quiet);
      with it 0, Agility's own alert works as before.
- [ ] Run Energy Restored At 100: drop below 90, rest: "Your run energy is restored" at 100; a few steps of
      running at 100 don't set it off; nothing at login.
- [ ] With two of Snuffy's plugins running separately, each run energy alert is said once.
- [ ] Speak Only When Unfocused: with RuneLite focused alerts post in chat but aren't spoken; click another
      window and they're spoken; Voice switch previews still play.

## Reminders
- [ ] A monster's clue scroll drop: "Clue: a <tier> clue scroll from <monster>!" once; picking it up says nothing more.
- [ ] A clue geode / bottle / nest / scroll box from skilling: alert with its tier. Withdrawing one from the bank: no alert.
- [ ] Logging in with clues already in the inventory: no alert.
- [ ] With two of Snuffy's plugins installed, clue alerts are said once; no plugin posts a daily reminder in chat.

## Planner (`runBrad "-Pload=planner"`, and in the Toolbelt)
- [ ] The Planner tab (calendar icon) shows TODAY, WEEKLY, TIMERS, SLAYER, DROPS, NOTES & TO-DO; clicking a heading
      folds it and it stays folded after a restart; each Show switch hides its list.
- [ ] Logged out: "Log in to fill your Planner."; logging into another account shows that account's lists.
- [ ] TODAY on a member: the dailies you've unlocked (e.g. Zaff's battlestaves after the Varrock easy diary) and
      none you haven't; collecting one ticks it within a few seconds; herb boxes show "n of 15 bought". The heading
      counts down to 00:00 UTC.
- [ ] Add... a daily with a note (hover shows it), tick it, edit its name, delete it. Hide a preset; Show hidden
      brings it back. A ticked custom daily unticks after the daily reset (and a weekly one after Wednesday 00:00 UTC).
- [ ] At login with dailies left: one voice line "You have dailies to do." and nothing in chat (Chat: Dailies off);
      turning Chat: Dailies on adds one line with how many. Same for weeklies. Nothing when everything is ticked.
- [ ] Staying logged in over 00:00 UTC: the collected dailies are due again and the voice line plays once.
- [ ] Tears of Guthix: after a game it says "back in ..." (start of the seventh day after); ticking it by hand does
      the same; before any game it says "not seen yet" and isn't counted in the weeklies line.
- [ ] Kingdom of Miscellania (after Throne of Miscellania): visiting the kingdom shows approval % and coffer, kept
      after logout; under 50% it's orange.
- [ ] Farming contract: shows the crop and how the Farming Guild patch is doing; "done: claim from Guinevere" once grown.
- [ ] TIMERS: patches you've visited (with compost bins and Hespori) and their grown-by times, as the Skilling
      Helper's FARMING list showed them before (old patches still there). Times show "~" until a patch is seen growing
      a stage while you stand by it, then no "~". Teleporting between two herb patches doesn't copy one's crop onto
      the other or delete it. Right-click > Forget this patch.
- [ ] Patches ready alert (voice + chat naming them, once per crop); bird houses ready 50 minutes after seeding.
- [ ] Add timer... "Test" 1 minute: Start counts down, Stop resets, it says "Your timer is done." with the name in
      chat when it ends; still there (and running) after relogging; Edit... and Delete work.
- [ ] SLAYER: task, kills left (of how many), Konar's area, points and streak, without the PvM/Slayer plugin; kept
      while logged out.
- [ ] DROPS: kill a few monsters: each gets kills and a GE value, click to see items; Recent lists drops, those over
      Valuable Drop From in Valuable Drop Colour. With RuneLite's Loot Tracker on, a clue casket or chest is counted
      once (and a monster kill isn't counted twice); the clash note mentions the Loot Tracker. Reset one; Reset all.
- [ ] NOTES & TO-DO: add (Enter), tick, edit, move up/down, delete, Clear ticked; notes kept after a restart and per
      account; typing in notes isn't interrupted by the list redrawing.
- [ ] The Skilling Helper's sidebar no longer has the FARMING list; its farming patch and bird house hover cards
      still work.
- [ ] With the Toolbelt and the separate Planner both on, the Toolbelt's clash note names Snuffy's Planner.

## Track for goal: specific methods
- [ ] Right-click a fishing spot with a Fishing goal: Track for goal opens the ways to fish it with catches,
  levels and bait; picking one shows it on the card, outlines only spots with that option, and greys out ways you can't do yet.
- [ ] Right-click logs (Fletching goal) or a bar (Smithing goal): a list with Best I can make and each recipe at your level.
- [ ] Fly fishing trout and salmon: the actions count stays steady (average of recent drops, hover says the XP each).
- [ ] Track for goal on a fishing spot: ways colour-coded (white all, yellow some, red none at your level, green your pick), no numbers.
- [ ] Train a goal skill on something other than the suggestion (e.g. fly fish when bait is suggested): after 5 XP
  drops the card offers it with Track it / Dismiss and a chat line; Track it switches the goal to it; Dismiss hides it for the session.
- [ ] Every tracker card has a pin: solid when pinned, faint otherwise; clicking a faint pin moves the pin there (tooltip names the skill it replaces); clicking the solid one unpins.
- [ ] Card right-click: Set goal... only with no goal; Modify goal... (prefilled, keeps the goal bar's progress) and Clear goal only with one.
- [ ] Launcher: Relaunch closes RuneLite and starts it again (forced after 20 s if it hangs); a new version found while open says "Update ready: Relaunch to use it". Launcher window: Toolbelt icon on the window and desktop shortcut (Make a desktop shortcut; an existing shortcut gets the icon). Setup rows (RuneLite found wherever it is installed, Find it... otherwise, session saving with Turn it on, session with Save one / Refresh it ticking itself once saved, Java, git); update status (Up to date / Updated with the list / Couldn't update), session line with your character and date (or the set-up link), Toolbelt vs Separate plugins checkboxes (greyed while Toolbelt is picked), Launch runs hidden with the progress bar (building, starting, "RuneLite is running"), stays open, says when RuneLite closes, Show log opens the output; remembers the pick.
- [ ] Fishing/Mining/Woodcutting goal: tools and bait outlined in the inventory and bank (fly fishing rod + feathers, any axe/pickaxe), not the fish/ore/logs; the spots/rocks/trees outlined in the world. Fish only outlined with a Cooking goal.

- [ ] Set a goal in the Toolbelt, launch the Skilling Helper alone: the same goal (and Track for goal pick, Suggest choice, pinned skill) is there, and the other way round.
- [ ] Fishing spot tiles: fly fishing shows trout and salmon stacked (only those your level catches); right as you click Lure with feathers (no catch needed); with stripy feathers it shows rainbow fish; Bait shows pike.
- [ ] Fishing goal with a fishing suggestion (e.g. Fly fishing: Trout, Salmon): every spot that catches those fish is outlined in the world, before you start fishing.
- [ ] Each timer kind's setting is Always / While training / Off (old on/off choices carried over: off stays Off, on becomes While training, or Always if Timers When Not Training was on). Fishing Spot Timers on Always: spots show their tile and fish before you start fishing (untimed spots without a timer).

- [ ] Hide Outlines While Working: only the spot/tree/rock you're working loses its goal outline (and plain spot outline); other spots, trees and rocks and the tools stay outlined; back a few seconds after stopping.
- [ ] Card right-click > Method: lists the guide methods for your level (fastest marked, hover for the guide text); picking one changes the card to "Your method: ..." with its outlines/tools/estimate; Suggested (automatic) goes back; shared with the Skilling Helper.
- [ ] Mining: mine out any rock (iron, lead, coal...): the depleted rock's tile counts down to the ore coming back (red, yellow, green), with the ore picture and time; lead rocks can be tracked for a Mining goal and the guide's Lead rocks method outlines them.
- [ ] Lead rocks: mine one out: its depleted tile gets the respawn timer; grey lead rocks are never outlined for a goal.
- [ ] Card > Method: methods show ~XP/hr where the guide gives one; with Suggest on Money Maker it lists money makers with GP/hr, and picking one shows "Your money maker: ...".
- [ ] Tracker: a skill without a goal leaves after Drop Idle Skills After (30 min) without XP and returns on new XP; goal skills and the pinned skill stay; header shows TRAINED with a tooltip.
- [ ] Tracker: GOALS and THIS SESSION each list the skill with the latest XP first; Attack/Hitpoints from one hit don't swap back and forth.
- [ ] Tracker: click the GOALS / THIS SESSION heading to fold the list ("+ GOALS (3)") and again to open it; remembered after a relaunch.
- [ ] Settings > About: heading tooltip lists the plugin and its clashes; Open the Guide opens the guide and switches off. clashes show only as the warning icon (hover for the list), never in chat.
- [ ] Tick Counter > Move Boxes Separately: the boxes become separate see-through boxes, each Alt + draggable and kept where placed after a relaunch; only boxes up to the cycle length show.
- [ ] Tick boxes: Box Shape (square/rounded/circle), Box Opacity and Box Size apply to both the row and the separate boxes.
- [ ] Reaching a goal clears it with a chat line; a goal already reached clears at the next login; Clear Goals When Reached off keeps it (Done!).
- [ ] Tick Counter > Half-Tick Boxes: a 1.5 box after each tick box; the tick box lights the first half, the half box the second; with Move Boxes Separately each half box moves on its own.
- [ ] Metronome Play Ahead (200 ms): clicking on the sound lands green in Click Timing; raising/lowering it moves the clicks later/earlier; 0 plays on the tick.
- [ ] Tick Boxes > Tick Pattern: 1-tick two halves (box 1 first half, box 2 second), each movable with Move Boxes Separately; Custom = Ticks Per Cycle.
- [ ] Any of Snuffy's plugins with a clashing plugin on (e.g. RuneLite's Mining or Metronome): a yellow warning info box top-left, tooltip lists the clashes; it goes within a few seconds of turning the clash off, and when the plugin stops.
- [ ] About > Clash Warnings off: no warning icon, no chat line (within a few seconds); on again brings them back.
- [ ] Agility: Course Card on a rooftop (laps, laps/hr, XP/hr after 2 laps, lap time, laps to level/goal, marks); Brimhaven Arena Card (countdown from the first pillar move, red near the end, Pillar About to Move voice at 10 s, tickets/hr); Run Energy alert below 15% while training Agility.
- [ ] Brimhaven Arena: obstacles green/yellow/red by level (floor spikes yellow below 50; ones RuneLite's Agility Show Clickboxes outlines are left to it, clash warning says so); hover one for XP/time/chance; cyan route line to the hint-arrow pillar; Arena Route Readout shows pillars/obstacles/links; Chat: Pillar About to Move now on. Route starts at you, follows you every tick, and goes through the middle pillars (no dispenser) instead of around the edge. Jump floor spikes back and forth: after 3 reps the card shows Training/Reps/Reps/hr/XP/hr/reps to level, route and pillar warning hidden. Same card for an Agility shortcut crossed 3 times in a row outside the arena.
- [ ] Agility Method list: Rope swing, Pressure pad, Floor spikes, Spinning blades, Darts (Brimhaven Agility Arena) from their level to 99 with XP/hr, outlined when picked.
