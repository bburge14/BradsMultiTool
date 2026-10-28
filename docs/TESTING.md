# Testing checklist

Work through these a section at a time in game. Anything that doesn't behave as described: note what you
did and what you saw (a screenshot helps), or use the panel's **Report an Issue** button.

## Login and alerts
- [ ] Nothing speaks or posts in chat before you click "Click here to play".
- [ ] Logging in with low prayer or health doesn't announce it.
- [ ] Turning a Voice: switch on plays a preview; turning it off silences only that alert.
- [ ] Chat messages match the voice line, with details in brackets.

## PvM
- [ ] PvM HUD appears on attacking a monster: HP bar, aggro line, weakness, style, DPS, last/max hit.
- [ ] Elemental weakness shows the Strike spell icon for that element.
- [ ] Monster hover: aggression reads "No (lvl X+)" for low-level monsters when you're over twice their level.
- [ ] God Wars: aggression changes when you equip a god item.
- [ ] Buff timers count down correctly; running out / expired alerts fire once (plus repeats if on).

## Gemstone Crab
- [ ] Radar and outlines only near the crab's mines (not in Draynor or other caves).
- [ ] Outlines draw under the minimap and inventory, not over them.
- [ ] Shell green when you can mine it, red when you can't.

## Slayer
- [ ] Task card, Ctrl+Shift+S hides/shows it (chat says hidden/shown), missing-item warning.
- [ ] Kill rate survives walking between areas.

## Skilling cards (hover)
- [ ] Trees, rocks, stalls, pickpocket NPCs, fishing spots (Bait at a sea spot shows sardine/herring, not pike).
- [ ] Farming patches: empty patch shows what you can plant; planted patch shows crop, stage, grown-in time, compost after you use it, watered.
- [ ] Hunter creature card; bird house card on Fossil Island.
- [ ] Runecraft altar card (runes per essence at your level).
- [ ] Agility course obstacle card (laps to level) and a shortcut card.
- [ ] Cooking: hover a range or fire with raw food: each food shows XP (or the level it needs), burning, and XP for all.
- [ ] Smithing: hover an anvil with bars (best item, how many, next unlock) and a furnace with ores (bars, how many with your coal).
- [ ] Auto retaliate off: hovering a pickpocketable NPC whose left-click is Attack shows the Thieving card.
- [ ] Sailing: charting spot (task text on its own lines, riddle answer for diving tasks), shipwreck (best hook) and shoal cards.
- [ ] XP rates on cards only while you're training that skill.
- [ ] An NPC walking past a tree doesn't change the card.

## Timer pies
- [ ] Trees: pie on trees being chopped (also others' trees), centred on the canopy; turns into a full pulsing red pie when the tree can fall on the next log; no pie on a tree already being chopped when you arrived.
- [ ] Mining: Motherlode veins, calcified rocks, Daeyalt essence, shooting stars.
- [ ] Fishing spots: pies on spots that moved while you were there.
- [ ] Hunter: pie on traps you lay, green when caught.
- [ ] Firemaking: pie on fires you light.
- [ ] Sailing: shipwreck pies.
- [ ] Pies don't draw over the bank or other interfaces.

## Skilling alerts
- [ ] Tree fallen / rock depleted / fishing spot moved only for the resource you're working.
- [ ] Inventory full while working a resource.
- [ ] Coin pouches full (at 28, or your Ardougne diary cap).
- [ ] Bird houses ready 50 minutes after seeding.
- [ ] Batch complete after a run of cooking / smithing / crafting / fletching / herblore.

## Skill tracker
- [ ] Cards, fields switch on/off without gaps, progress bar colours.
- [ ] Actions left hidden for combat skills.
- [ ] Set Goal button: pick a skill, enter a level (e.g. 80) or XP (e.g. 2000000); the skill appears under GOALS.
- [ ] A goal skill stays pinned under GOALS after logging out and back in, even before you train it.
- [ ] Actions Until Goal on: a goal skill's card shows "Goal Actions" counting to the goal; off (default): "Actions" to the next level.
- [ ] Clear goal moves the card back under THIS SESSION (or removes it if untrained).
- [ ] Auto-pause after the set minutes without XP; resumes on XP.
- [ ] Level-up voice says skill and level.

## Idle
- [ ] AFK alert after training then stopping with no input; never while banking or in shops/GE.
- [ ] Idle logout warning before the logout.
