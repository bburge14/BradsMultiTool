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

## Buff bar and monster effects
- [ ] Buff bar: a potion shows its icon, +boost and time left (divine exact, regular "~" until synced); antifire,
      super antifire, stamina and antipoison/antivenom show with timers; anglerfish shows Hitpoints +N.
- [ ] Debuffs in red: poisoned, venomed, a drained stat (e.g. Defence drained by a monster); Show Debuffs off hides them.
- [ ] Hover an icon: its name. Alt + drag moves it; Layout and Icon Size work; nothing active: no bar.
- [ ] With the bar on, the PvM HUD has no potion rows; running out / expired alerts still play.
- [ ] HUD Status: Poisoned/Venomed after poison or venom hits the monster; Frozen 0:xx after Ice barrage (etc.);
      Burning from burn hitsplats.
- [ ] HUD Defence: Dragon warhammer hit lowers it 30% ("Defence: 140 (-60)"), Elder maul 35%, BGS by the damage.

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
- [ ] Skilling > Best Choice By = Most profit: anvil Best changes to the most profitable item and a Profit line appears (anvil and furnace).
- [x] Auto retaliate off: hovering a pickpocketable NPC whose left-click is Attack shows the Thieving card.
- [ ] Sailing: charting spot (task text on its own lines, riddle answer for diving tasks), shipwreck (best hook) and shoal cards.
- [x] XP rates on cards only while you're training that skill.
- [x] An NPC walking past a tree doesn't change the card.

## Timer pies
- [x] Trees: pie on trees being chopped (also others' trees), centred on the canopy; turns into a full pulsing red pie when the tree can fall on the next log; no pie on a tree already being chopped when you arrived.
- [ ] Mining: Motherlode veins, calcified rocks, Daeyalt essence, shooting stars.
- [ ] Fishing spots: pies on spots that moved while you were there.
- [ ] Hunter: pie on traps you lay, green when caught.
- [ ] Fires: a pie only on a fire you cook on (Use food on it), none on fires you just light or walk past; it
      runs out at about 60 s, then pulses red until the fire goes; it sits still, not bouncing with the flames.
- [ ] "The tree has fallen." (new wording) on oaks and up; no tree/rock alert on regular trees, copper or tin.
- [ ] Sailing: shipwreck pies.
- [x] Pies don't draw over the bank or other interfaces.

## Skilling alerts
- [x] Tree fallen / rock depleted / fishing spot moved only for the resource you're working.
- [x] Inventory full while working a resource.
- [ ] Coin pouches full (at 28, or your Ardougne diary cap).
- [ ] Bird houses ready 50 minutes after seeding.
- [ ] Batch complete after a run of cooking / smithing / crafting / fletching / herblore.

## Skill tracker
- [x] Cards, fields switch on/off without gaps, progress bar colours.
- [ ] Training combat (e.g. Gemstone Crab): cards stay still, no field wraps onto two lines; GOALS / THIS SESSION headings centred.
- [x] Actions left hidden for combat skills.
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
- [ ] Follow Attack Speed: cycle matches the weapon (whip 4, shortbow on Rapid 3); 1 lands on each attack.
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
