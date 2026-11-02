# Plugin audit: what other plugins do that ours could

Compared on 2026-11-01: RuneLite's own plugins and popular Plugin Hub plugins, setting by setting, against
Snuffy's plugins. Each part below has a table per plugin (their feature, ours, verdict, suggested action) and its
own ranked list. Verdicts: Same, Ours better, Theirs better, Missing, Not allowed (breaks our rules, see
PLUGIN_HUB_COMPLIANCE.md), Not relevant.

Notes:
- Popularity labels on Plugin Hub plugins are estimates: install counts couldn't be fetched. Check
  runelite.net/plugin-hub before relying on them.
- Licences: RuneLite and almost every Hub plugin here are BSD 2-Clause (Superior Slayer Tracker and Advanced
  Notifications are MIT). Ideas are free to use; copied code keeps its credit. "Missing" items found by searching
  our code for a name were not all checked line by line.
- Anything that acts for the player, hides or reorders menu options, talks to an outside server or writes files
  stays out, even where another plugin does it.

## Pick list (best first across all parts)

Tick what you want built.

- [ ] **Alerts through RuneLite's notifications**: an "also notify" switch so any alert can pop up, flash the
  taskbar or focus the window (today only the idle logout warning does). All plugins.
- [ ] **6-hour logout warning** with a voice line. Alerts.
- [ ] **Banked XP on the goal card**: "Banked: 1.2M XP", how much of the goal your bank covers. Skilling.
- [ ] **Forestry events and bird nests**: alerts when an event starts or a nest drops, outlines for what the
  event wants, bark and leaves per hour. Skilling.
- [ ] **Motherlode sack warning**: when the pay-dirt you carry won't fit in the sack. Skilling.
- [ ] **Farming patch list with "ready" alerts** for every patch you've visited. Skilling.
- [ ] **Items per hour on the cards**: logs, fish, ore, cooked and burnt. Skilling.
- [ ] **Gem crab stats**: crabs killed, gems by type, mining success/fail, XP, fight length, kept between sessions. GemCrab.
- [ ] **Slayer points and streak**, with a warning before a milestone task; task history; kill rate kept per task with
  a "done at" time. PvM/Slayer.
- [ ] **"Finish it off" marker** for lizards, gargoyles, rockslugs and zygomites. PvM/Slayer.
- [ ] **More on the buff bar**: vengeance, imbued heart, overload, frozen/teleblocked, staff of the dead, prayer
  regeneration. PvM/Slayer.
- [ ] **Monster max hit and attack speed** on the hover card. PvM/Slayer.
- [x] **Attack timer that counts down** to your next attack (shows when you're late; handles eating and weapon
  speed changes). Tick Counter.
- [x] **Tick Counter hotkeys**: reset, show/hide, change ticks per cycle. Tick Counter.
- [ ] **Hunter rumour card**: catches left to the guaranteed drop, where to go, alerts. Skilling.
- [ ] **Progress and goal bars on the in-game Skills tab**. Skilling.
- [ ] **Agility: outline marks of grace and course traps**. Skilling.
- [ ] **XP tracker keeps your session after a restart**, more than one pinned skill, actions per hour. Skilling.
- [ ] **Hunter trap states**: a failed trap coloured too, not only caught ones. Skilling.
- [ ] **Unpotted reminder**: warn when attacking without a combat boost. PvM/Slayer.
- [ ] **Wrong attack prayer alert**. PvM/Slayer.
- [x] **Run energy low/restored everywhere**, not only in Agility. Alerts.
- [ ] **Minigame cards**: Wintertodt, Tempoross, Tithe Farm timers. Skilling.

---

# Part: Skilling Helper vs RuneLite's own plugins

Compared on 2026-11-01. RuneLite source: runelite/runelite master (each plugin's *Config.java for its settings, plus
its plugin and overlay classes). Ours: docs/FEATURES.md, SkillTrackerConfig.java and the matching Java in
src/main/java/com/bradsmultitool/. Everything in a core plugin is already allowed by RuneLite, so nothing here comes
out as "Not allowed"; the only limit is that we stay information-only, which all of these are.

Verdicts: Same, Ours better, Theirs better, Missing (we don't have it), Not allowed, Not relevant.

### Best additions

1. **Motherlode sack warning** (Missing): turn the pay-dirt count red when the pay-dirt you carry won't fit in the sack, and add a voice alert. We time the veins but know nothing about the sack.
2. **Woodcutting bird nest, clue nest and Forestry event alerts** (Missing): voice lines for a nest dropping and for each Forestry event (Rising roots, Struggling sapling, Leprechaun, Pheasants, Bee hive, Ritual, Friendly ent, Poachers, Flowering tree), and outline what each event wants clicked on. This fits our voice alerts very well.
3. **Farming patch list and "patch ready" alerts** (Theirs better): Time Tracking remembers every patch you've visited and notifies when it's grown. Ours only shows a patch while you hover it in front of you.
4. **Item counts on skill cards** (Missing): logs cut, fish caught, ore mined, food cooked and burnt, each with a per-hour figure. Core shows these for Woodcutting, Fishing, Mining and Cooking; we only count XP and actions.
5. **Agility: marks of grace and course traps** (Missing): outline marks of grace you can pick up and the traps on courses, like core does. We count the marks but don't show where they are.
6. **XP tracker: keep the session across restarts, more than one skill on screen, actions per hour and actions done** (Theirs better): core can save the session, have any number of on-screen skill boxes with labels you choose, and show actions/hr. We pin one skill and lose session numbers on restart.
7. **Wintertodt card and alerts** (Missing): round starting soon, inventory full of roots, out of roots, brazier out, hit by cold or snowfall. Fits our voice alerts well and it's a very popular skilling spot.
8. **Hunter trap states** (Theirs better): core colours a trap open, caught, failed (empty) or changing. Ours goes green once something's caught but doesn't show a failed trap.
9. **Tithe Farm plant timers** (Missing): a pie per plant (watered or not) until it dies or needs water.
10. **Idle: six-hour logout warning and "run energy restored" alert** (Missing): easy additions to our Idle section.

---

### Agility

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Show clickboxes (course obstacles) | We step aside for it; we colour Brimhaven arena obstacles only | Theirs better | Leave it to core; our clash warning already says so. |
| Show lap count | Course Card: laps this session | Same | None. |
| Hide lap count (minutes) | Course Card stays 5 minutes after your last lap (fixed) | Theirs better | Make the 5 minutes a setting. |
| Show laps until goal | Laps to next level and to your goal | Same | None. |
| Show laps per hour | Laps and XP per hour, average lap time | Ours better | None. |
| Overlay color | No colour setting for the course card outlines | Not relevant | None. |
| Highlight marks of grace (and colour) | We count marks picked up; nothing outlines them | Missing | Outline marks of grace on the ground while on a course. |
| Highlight portals (Prifddinas) | Not found | Missing | Outline the Prifddinas course portal when it appears (low priority). |
| Highlight agility shortcuts | Obstacle Hover shows a shortcut's level when you hover it; no outline | Theirs better | Optionally outline shortcuts green/red by your level. |
| Show trap overlay (course traps) | Not found | Missing | Outline course traps (e.g. Ardougne, Pollnivneach). |
| Agility Arena notifier (ticket moved) | Pillar About to Move voice/chat warning before it moves | Ours better | None. |
| Agility Arena timer | Brimhaven Arena Card with countdown bar, tickets and tickets/hr | Ours better | None. |
| Highlight stick (Werewolf course) | Not found | Missing | Outline the stick on the Werewolf course (low priority). |
| Highlight projectiles (Sepulchre) | Not found | Missing | Consider Hallowed Sepulchre support later; it's a big job. |
| Highlight obstacles (Sepulchre) | Not found | Missing | Same as above. |
| Highlight skill challenges (Sepulchre) | Not found | Missing | Same as above. |
| (no setting) | Arena route to the tagged pillar, one-obstacle rep counter, run energy alert, Agility method list | Ours better | None. |

### Fishing

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Display only currently fished fish | Spots for your goal's method are outlined; tile style shows every spot | Theirs better | Add an "Only my kind of spot" option to the tile style. |
| Display spot tiles | Tile and icon timer style outlines spots | Same | None. |
| Display spot icons | Fish pictures on the tile, picked from your rod and bait | Ours better | None. |
| Display spot names | Hover card names every way to fish and each catch | Ours better | None. |
| Overlay color | Zone Colour and Your Spot Colour | Same | None. |
| Minnows overlay (colour) / Show minnow movement overlay | We time minnow spots, but hide our timer when core's Minnow overlay is on | Same | None. |
| Aerial overlay (1-tick colour) | We time aerial spots; no 1-tick colour | Theirs better | Low priority; a special colour for a spot you can 1-tick catch. |
| Harpoonfish overlay (bubbling spots, Tempoross) | Not found | Missing | Outline bubbling harpoonfish spots at Tempoross. |
| Reset stats (minutes) | Auto-Pause After for the rate clock | Same | None. |
| Show fishing session stats (caught fish, fish/hr) | XP/hr and actions left, no fish count | Missing | Add "Caught" and "Fish/hr" to the Fishing card. |
| Flying fish notification | Not found | Missing | Voice alert when a flying fish appears on your minnow spot. |
| Trawler timer in m:ss / Trawler contribution | Not found | Not relevant | Fishing Trawler is a minigame few train in; skip. |
| Minimap spot dots (no setting) | Not found | Missing | Optional minimap dot for the spot your goal wants. |
| (no setting) | Spot move window timers, spot moved alert, newest spot marker, hover cards | Ours better | None. |

### Mining

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Reset stats (minutes) | Auto-Pause After | Same | None. |
| Show session stats (total mined, mined/hr, "Mining"/"NOT mining") | XP/hr and actions only | Missing | Add "Mined" and "Ore/hr" to the Mining card. |
| Rock respawn timers (no setting) | Same game script, same pie; we step aside when core Mining is on | Same | None. |
| (no setting) | Motherlode, calcified rock, Daeyalt and shooting star timers, rock depleted alert, rock hover | Ours better | None. |

### Woodcutting

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Reset stats | Auto-Pause After | Same | None. |
| Bird nest notification | Not found | Missing | Voice alert "A bird nest fell" (and outline it). |
| Clue nest notification (from tier) | Not found | Missing | Voice alert for a clue nest, with a minimum tier setting. |
| Show session stats (logs cut, logs/hr, bark, bark/hr) | XP/hr and actions only | Missing | Add "Logs" and "Logs/hr" (and bark) to the Woodcutting card. |
| Show Redwood trees | Not found | Missing | Mark which redwood side is choppable (low priority). |
| Show respawn timers (fallen tree grows back) | We time how long a tree lasts (Forestry), not how long until it grows back | Missing | Add a respawn pie on the stump. |
| Leprechaun / Rising roots / Struggling sapling / Flowering tree / Poachers / Pheasant control / Bee hive / Enchantment ritual / Friendly ent notifications | Not found | Missing | One voice alert per Forestry event, each with a switch. |
| Highlight glowing roots / mulch ingredients / flowers / fox trap / pheasant nest / bee hive / ritual circle / leprechaun rainbow | Not found | Missing | Outline what each Forestry event needs, information only like core. |
| Show leprechaun's luck | Not found | Missing | Low priority. |
| (no setting) | Forestry tree despawn pie, tree fallen alert, tree hover with best axe | Ours better | None. |

### Hunter

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Open / Full / Empty / Transitioning trap colours | Trap pie until it collapses, green once caught | Theirs better | Show a failed (empty) trap in its own colour. |
| Maniacal monkey notification | Not found | Missing | Voice alert on a caught or missed maniacal monkey. |
| (no setting) | Creature hover, trap spot hover, Hunter method list and outlines | Ours better | None. |

### Motherlode Mine

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Show pay-dirt mining spots (vein icons) | Vein timers; we leave the icon to core when it's on | Same | None. |
| Show rocks obstacles (fallen rocks) | Not found | Missing | Outline fallen rocks blocking the way. |
| Track gems found / Track ores found (loot tracker) | Not found | Missing | Count ores and gems from the sack on a Motherlode card. |
| Show broken struts | Not found | Missing | Outline broken water wheel struts with a hammer icon. |
| Sack colour warning (no setting) | Not found | Missing | Warn (colour and voice) when your pay-dirt won't fit in the sack. |
| (no setting) | Vein depletion timers (upper and lower floor) | Ours better | None. |

### Cooking

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Reset stats | Auto-Pause After | Same | None. |
| Session stats (cooked, burnt, burn %) | Batch complete alert; no cooked/burnt count | Missing | Add "Cooked", "Burnt" and burn rate to the Cooking card. |
| Show wine ferment timer | Not found | Missing | Show a timer after making wine until it ferments. |
| (no setting) | Range/fire card with burn levels, XP for what you carry, suggested food | Ours better | None. |

### XP Tracker

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Save between sessions | Goals saved; session numbers aren't | Theirs better | Save session XP and time per profile so a restart doesn't wipe it. |
| Hide maxed skills | Ignored Skills (comma list) | Theirs better | Add a "Hide 99 skills" switch. |
| Pause on logout | Rate auto-pauses after 10 s without XP, so it pauses anyway | Same | None. |
| Show intermediate level markers | Not found | Missing | Draw level ticks on the goal bar when the goal is several levels away. |
| Auto pause after | Auto-Pause Idle Skills and After | Same | None. |
| Auto reset after | Drop Idle Skills After hides the card but keeps its numbers | Theirs better | Option to reset the rate instead of keeping it. |
| Add skill tab canvas menu option | Pin from the card only | Theirs better | Add "Pin to screen" to the skill tab's right-click menu. |
| On-screen tracker (top/bottom) and XP info box labels (4 corners) | One pinned skill with fixed fields | Theirs better | Allow more than one pinned skill and let players pick its fields. |
| Progress bar label / Tooltip label (time to goal or %) | Bar shows progress; time to level is a field | Same | None. |
| Move recently trained skills to top | Goals first, then this session | Same | None. |
| Wise Old Man option | Not found | Missing | Optional "Open on Wise Old Man" via LinkBrowser.browse (low priority). |
| Actions/hr, Actions done (label options) | Actions left only | Missing | Add Actions/hr and Actions done fields. |
| Reads the game's own XP goals (varps) | Our own goals only | Theirs better | Offer to import goals set in the game's skill guide. |
| Reset others (menu) | Reset, Reset all | Theirs better | Add "Reset others" to the card menu. |
| (no setting) | Goals, suggested methods, Where, needed tools, Make lines, kill counts, Track for goal | Ours better | None. |

### XP Globes

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| XP globes on XP gain | Not found (one pinned skill overlay) | Missing | Optional small globe per skill trained, using our card's numbers. |
| Enable tooltips / XP left / actions left / XP/hr / time til goal | Shown on the card and pinned overlay instead | Same | None. |
| Hide maxed skills / Show virtual level | Not found | Missing | Show virtual levels past 99 on cards. |
| Arc colour, outline, background, width, size, duration, vertical | Not found | Not relevant | Only if globes are added. |

### Skill Calculator

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Panel: current and target level/XP, actions needed for every item | Method list with XP/hr; actions count for one method | Theirs better | Show actions to goal for each entry in the Method list. |
| XP bonuses (outfits, boosts like Lumberjack, Zealot's robes) | Not found (angler only noted in method detection) | Missing | Factor skilling outfits into actions left. |
| Combine several actions | Not found | Not relevant | Skip; our goal cards cover the common case. |

### Herbiboar

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Show start objects / trail objects / trail / end tunnels (and colours, clickboxes) | Not found | Missing | Low priority; core already does it well. If added, outline the trail and the end tunnel. |

### Blast Furnace

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Show conveyor belt clickbox / bar dispenser clickbox | Not found | Missing | Low priority; core covers it. |
| Show coffer time remaining | Not found | Missing | Show coffer time left and warn before it runs out. |
| Bars and ores in the furnace (no setting) | Not found | Missing | Show bars ready in the dispenser on a Smithing card. |

### Tithe Farm

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Unwatered / Watered plant timers | Not found | Missing | Pie per plant until it needs water or dies. |

### Time Tracking (farming and bird houses)

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Farming tab: every patch with time to grown | Farming Patch Hover, only for the patch in front of you | Theirs better | Add a farming list to the panel with each patch you've seen and when it's done. |
| Patch ready notification (per patch) | Not found | Missing | Voice alert "Your herbs are ready" per patch type. |
| Compost and payment tracking | Shown on hover | Same | None. |
| Bird house tab and notification | Bird house hover and Bird Houses Ready alert | Same | None. |
| Show farming contract infobox | Not found | Missing | Show your farming contract and its patch's state. |
| Prefer soonest completion | Not found | Not relevant | Only with a patch list. |
| Time format / Sort order / Warning threshold / Default time / Timer notification (timers, stopwatches) | Not found | Not relevant | General timers aren't skilling. |

### Wintertodt

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Show overlay (what you're doing, roots and kindling) | Not found | Missing | Add a Wintertodt card: activity, roots, kindling. |
| Round notification (seconds) | Not found | Missing | Voice alert before the round starts. |
| Ambient / Snowfall / Brazier damage notification | Not found | Missing | Voice alert when you're hit and interrupted. |
| Full inventory / Empty inventory notification | Inventory Full alert (any skilling) | Theirs better | Add "Out of roots" for Wintertodt. |
| Brazier extinguish notification | Not found | Missing | Voice alert when your brazier goes out. |
| Damage notification colour | Not found | Not relevant | None. |

### Idle Notifier (skilling parts)

| Their feature (setting name) | Ours | Verdict | Suggested action |
|---|---|---|---|
| Idle animation / interaction notifications | AFK Alert: you were training, stopped and haven't clicked since | Same | None. |
| Idle movement notifications | Not found | Not relevant | None. |
| Idle notification delay | AFK Alert After | Same | None. |
| Idle logout notifications | Warn Before Logout, with repeats | Ours better | None. |
| Six hour logout notification | Not found | Missing | Add a warning before the six-hour logout. |
| Low energy notification (threshold) | Run Energy Alert Below, only while training Agility | Theirs better | Allow it outside Agility too. |
| High energy notification | Not found | Missing | Alert when run energy is back to a set level. |
| Oxygen notification | Not found | Missing | Low priority (underwater Fossil Island). |
| Hitpoints / Prayer / Spec notifications | In PvM/Slayer, not skilling | Not relevant | None for the Skilling Helper. |
| Desktop notifications (all of them) | Our alerts are voice and chat; only the logout warning also uses RuneLite's notifier | Theirs better | Add an option to send skilling alerts through RuneLite's notifier too. |

---

# Part: Skilling Helper vs Plugin Hub plugins

Audit date: 2026-11-01. Compared against docs/FEATURES.md, SkillTrackerConfig.java and a grep of
src/main/java/com/bradsmultitool/. Each hub plugin was read at the exact commit the hub pins
(runelite/plugin-hub `plugins/<name>`): README plus every `@ConfigItem` in its *Config.java.

**Popularity caveat:** install counts could not be fetched (api.runelite.net and runelite.net are blocked
from this session). "Popularity" below is a judgement from how well known the plugin is, not a number.
Core RuneLite already covers Wintertodt, Motherlode Mine, Tithe Farm, Pyramid Plunder, Herbiboar, Time
Tracking (bird houses, farming), XP tracker/globes and the skill calculator, so hub plugins for those were
skipped unless they add something clear.

**Licences:** every plugin below is BSD 2-Clause (same as ours), so ideas and, with credit kept, code can be
reused.

**Verdict key:** Same / Ours better / Theirs better / Missing / Not allowed (breaks our rules: acts for the
player, removes or reorders menu options, talks to a third-party server, auto-scrolls an interface) / Not
relevant.

### Best additions (ranked)

1. **Banked XP on the goal card** (from Banked Experience). We already read the bank for the Make: line;
   add "Banked: 1.2M XP (to level 84)" per skill and "Bank covers 62% of your goal". Small, high value.
2. **Hunter rumour card** (from Hunter Rumours). Current rumour creature, catches until the pity drop,
   "Where:" locations (we have that machinery), end-of-rumour stats, rumour-complete voice alert.
3. **Forestry events** (from Forestry Tracker). Voice/chat alert when an event starts near you, event name
   and age on the Woodcutting card, anima bark and leaves per hour. We time trees but know nothing of events.
4. **Progress and goal bars on the in-game Skills tab** (from Skills Tab Progress Bars). We already have
   the goals; drawing them over the stats widget gives them a home outside the sidebar.
5. **Tempoross card and alerts** (from Tempoross). Fish/cooked fish and damage you carry, phase, wave
   incoming / storm cloud / double spot / vulnerable alerts as voice lines. Fits Fishing; info only.
6. **Guardians of the Rift info** (from GOTR Helper, the most popular here). Active guardians outlined by
   cell tier and greyed when your level is too low, portal-spawn and game-start alerts, points balance. Big
   job and that plugin is excellent, so only do the alerts + "which altar can I use" part.
7. **Sailing alerts and highlights** (from Sailing). Low boat HP alert, trimmable-sail alert, speed-boost
   timer, rapids highlight. Leave out its menu deprioritising.
8. **Session history graph** (from XP Grapher). A small XP-over-time line on a tracker card's hover.
9. **Daily/weekly XP targets** (from XP Goalz). A goal type "50K Fishing XP today" that resets.
10. **Essence pouch contents** (from Essence Pouch Tracking). Stored count on each pouch; fits Runecraft.

Not worth chasing: shooting-star crowdsourcing (third-party server), MLM QoL and Sepulchre menu removal
(not allowed), Giants' Foundry and Mahogany Homes (each a whole minigame helper; already done well).

---

### Guardians of the Rift Helper
Repo: https://github.com/DatBear/Guardians-of-the-Rift-Helper · Licence: BSD 2-Clause · Popularity: very high
(one of the most installed skilling plugins).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Outline active guardians (elemental/catalytic or by cell tier), rune icons above | None (no GOTR code) | Missing | Worth a slim version: outline the altars you can use. |
| Recolour guardians your Runecraft level can't use | Altar hover gives level, but not in GOTR | Missing | Grey out GOTR altars above your level; fits our level-coloured style. |
| Notify on portal spawn, before game start, before first altar | None | Missing | Add as voice/chat alerts, they suit our voice lines. |
| Notify at N guardian fragments | None | Missing | Add with the alerts above. |
| Per-guardian portal-open notifications | None | Missing | Optional extra if the alerts are added. |
| Points overlay, potential points, balance helper | None | Missing | Lower priority; it is their core and done well. |
| Outline cell table, deposit pool, Great Guardian, cell tiles when relevant | None | Missing | Lower priority. |
| Inactive portal / start timer overlay | None | Missing | Include a start timer if alerts are added. |
| Hide Power-Up / Place-cell / Use-on-runes left-click options | None | Not allowed | Skip: removing menu options goes beyond our info-only rule. |
| Quick-Pass cooldown (with Menu Entry Swapper) | None | Not allowed | Skip. |
| Mute apprentice help messages | None | Not relevant | Skip. |

### Tempoross
Repo: https://github.com/nmlynch94/runelite-external-plugins (tempoross branch) · Licence: BSD 2-Clause · Popularity: high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Fish count (raw/cooked) and damage it does | None | Missing | Add a Tempoross card under Fishing. |
| Phase shown | None | Missing | Put on the same card. |
| Wave timer on totem (tethered/broken colours), wave-incoming notify | None | Missing | Add a "Wave incoming" voice alert first; timer later. |
| Fire highlights with spread timer | None | Missing | Optional. |
| Double fishing spot highlight + depletion timer + notify | Our fishing timers skip Tempoross spots (they never move) | Missing | Highlight the double spot and alert on it. |
| Storm cloud / vulnerable / storm intensity notify | None | Missing | Voice alerts, cheap to add with the card. |
| Reward permit count in Unkah | None | Missing | Small extra. |

### Easy Giants' Foundry
Repo: https://github.com/Toofifty/easy-giantsfoundry · Licence: BSD 2-Clause · Popularity: high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Heat/stage/actions-left overlays, notify before overheating or stage end | None | Missing | Skip unless asked: a full minigame helper, and theirs is the standard. |
| Highlight current tool, lava/waterfall, mould, crucible, Kovac | None | Missing | Skip for the same reason. |
| Best mould text, crucible content and quality | None | Missing | Skip. |
| Metals count in bank/inventory (ores, bars, equipment) | Smithing anvil/furnace hover counts bars you carry | Theirs better | Could reuse for a "Foundry metal" line, low priority. |
| Bonus notify and sound | None | Missing | Skip. |

### Mahogany Homes
Repo: https://github.com/TheStonedTurtle/Mahogany-Homes · Licence: BSD 2-Clause · Popularity: high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Contract text overlay (who, where) | Construction gets only the generic tracker | Missing | Skip unless Construction gets attention; would fit "Where:". |
| World map icon, hint arrows, Shortest Path route | Our "Where:" names places but no map icon | Theirs better | A world-map marker for "Where:" places would help many methods. |
| Required materials and supply check (plank sack aware) | Make:/Needs: lines check tools for other skills | Missing | Skip. |
| Highlight hotspots, stairs, teleport items | None | Missing | Skip. |
| Session stats (contracts, points) | None | Missing | Skip. |

### Volcanic Mine
Repo: https://github.com/hippipi/volcanic-mine-plugin · Licence: BSD 2-Clause · Popularity: medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Vent shift warning (seconds before) | None | Missing | Possible voice alert; niche. |
| Eruption warning | None | Missing | Same; niche. |
| Platform under you about to vanish | None | Missing | Same. |
| Boulder stage complete | None | Missing | Same. |

### Hallowed Sepulchre Helper
Repo: https://github.com/zipon458/hallowed-sepulchre-helper · Licence: BSD 2-Clause · Popularity: medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Info panel, floor timer, tick-perfect times, overall timer | Agility course card covers rooftops only | Missing | Could extend the Course Card to Sepulchre floors (times, XP/hr). |
| Highlight bolts, swords, crossbowmen, portals, lightning, danger tiles | None | Missing | Skip; specialist hazard overlay. |
| Navigation highlights, current-route only | Arena Route exists for Brimhaven | Missing | Skip. |
| Coffin looting highlights per floor, obelisk highlight | None | Missing | Skip. |
| Swap obelisk Activate/Quick-exit | None | Not allowed | Skip: menu swap. |
| Immunity outline after blue portal | None | Missing | Skip. |

### Shooting Stars (andmcadams)
Repo: https://github.com/andmcadams/plugin-repo (shooting-stars branch) · Licence: BSD 2-Clause · Popularity: medium (hub shows a third-party-server warning).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Crowdsourced star list via external server, shared key | None | Not allowed | Skip: we use no network and it needs a third-party server. |
| Side panel with landing times by region, filters | None | Not allowed | Skip (same data source). |
| Double-click world to hop | None | Not allowed | Skip: hopping for the player. |
| Star layer health timer while mining | Mining Timers: star time from layer health | Same | None (that's Star AFK Timer's feature, which we mirror). |

### Tree Despawn Timer
Repo: https://github.com/CreativeTechGuy/tree-despawn-timer · Licence: BSD 2-Clause · Popularity: medium-high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Forestry tree timer (pie) counting every chopper, blank if already chopped | Woodcutting > Tree Timers, same rule, plus red pulsing "due" pie | Same | None. |
| Display as pie, ticks or seconds | Pie with Timer Text (time) | Theirs better | Add a ticks option to Timer Text. |
| Popular tree indicator (10+ choppers, bigger timer) | We count choppers internally, don't show it | Missing | Show the chopper count, or flag 10+ (the Forestry boost cap). |
| Four timer colours, sizes | One opacity setting, fixed colours | Theirs better | Low priority; colour settings if players ask. |

### Fishing Spot Tracker
Repo: https://github.com/CharlesLillo/fishing-spot-tracker · Licence: BSD 2-Clause · Popularity: low-medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Pie timer circle with fresh/expiring/active colours | Tile outline coloured by the 2.5-5 min window, Your Spot Colour | Ours better | None (ours shows the real move window). |
| Timer text, fish name, fish icon | Timer Text, fish pictures by your method | Same | None. |
| Newest-spot badge | Mark Newest Spot | Same | None. |
| Show static spots (karambwan, Tempoross) | Untimed spots keep tile and fish picture | Same | None. |
| Ignored fish list (hide spots) | None | Missing | Small: an "Ignored Fish" text setting. |
| Idle notification when you stop fishing | Idle > AFK Alert (any skill) | Same | None. |
| Minimap dots for spots | None | Missing | Small: minimap dot per spot in its timer colour. |

### Fishing Spot Timer
Repo: https://github.com/ArnautS/FishingSpotTimer · Licence: BSD 2-Clause · Popularity: low-medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Timer since spot moved, yellow at min, red at max (user-set seconds) | Per-spot-type windows from Jagex/wiki data (eels, anglerfish...) | Ours better | None. |

### Barbarian Fishing Timers
Repo: https://github.com/staticvoid07/barbarian-fishing-timers · Licence: BSD 2-Clause · Popularity: low.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Tick timer per spot, colour thresholds | Fishing Spot Timers | Same | None. |
| Highlight lowest-timer spot | Mark Newest Spot | Same | None. |
| Notify when the spot beside you moves | Skilling Alerts > Resource Gone (fishing spot moved) | Same | None. |
| Keep timers through a teleport / off-screen moves | Spots that leave view are remembered (rememberedSpots) | Same | None. |

### Forestry Tracker
Repo: https://github.com/tucker-green/forestry-tracker · Licence: BSD 2-Clause · Popularity: low-medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Last/current Forestry event and time since | None (no event code found) | Missing | Add event name to the Woodcutting card and a voice alert on start. |
| Anima bark per hour, per event, session, lifetime | None | Missing | Add bark/hr to the Woodcutting card. |
| Logs cut and logs/hr | Actions left and XP/hr, no log count | Theirs better | Add logs/hr (cheap from XP drops). |
| Leaves gathered by type | None | Missing | Optional with the above. |
| Events seen count, session timeout | Auto-pause and drop idle skills | Same | None. |

### Hunter Rumours
Repo: https://github.com/geel9/runelite-hunter-rumours · Licence: BSD 2-Clause · Popularity: high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Rumour infobox: current target, catches until pity | None | Missing | Add a rumour line to the Hunter card with catches left. |
| World map locations for the rumour creature | Hunter "Where:" in chat/card only, no map | Missing | Reuse "Where:"; add map markers if done (see Mahogany Homes). |
| Preferred locations panel, Shortest Path route | None | Missing | Skip the panel; Shortest Path hand-off is possible later. |
| Guild overlay listing all hunters' rumours, back-to-back state | None | Missing | Lower priority. |
| Highlight current/known/unknown hunter NPCs | None | Missing | Small: outline the hunter who gave the task. |
| Chat on rumour change and end-of-rumour stats (luck colours) | None | Missing | Add as chat + voice alert ("Rumour complete"). |
| Notify on the rumour drop | None | Missing | Include with the alert. |
| Auto-scroll fairy ring interface to the right code | None | Not allowed | Skip: changes an interface for the player; outside our info-only rule. |

### Skills Tab Progress Bars
Repo: https://github.com/m0bilebtw/skills-tab-progress-bars · Licence: BSD 2-Clause · Popularity: high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Progress bar to next level on each skill stone | Sidebar cards and pinned overlay only | Missing | Add: bar drawn over the Skills tab widget. |
| Goal progress bar (game's XP-menu goals) | Our own goals in the sidebar | Missing | Draw our goals there, gold like the goal bar. |
| Virtual levels past 99, 200m | Not checked | Missing | Include virtual levels if added. |
| Only on hover, darken skills by level/XP | None | Missing | Optional. |
| Colours | Red/orange/green bar | Same | None. |

### Customizable XP Drops
Repo: https://github.com/l2-/template-plugin · Licence: BSD 2-Clause · Popularity: very high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Custom XP drops (grouping, speed, direction, fade, colours, fonts, icons) | None | Not relevant | Skip: cosmetic, and it's that plugin's whole purpose. |
| Predicted hit from XP drop | PvM HUD (different plugin) | Not relevant | Skip for Skilling Helper. |
| On-screen XP tracker for one skill with progress bar | Pin to screen overlay (bar, XP/hr, time to level, goal) | Ours better | None. |
| Hide vanilla XP tracker | None | Not relevant | Skip. |
| Filter skills | Ignored Skills | Same | None. |

### Goal Tracker
Repo: https://github.com/Darkforge317/rl-goal-tracker · Licence: BSD 2-Clause · Popularity: medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Skill goals with progress bars, pinned on top | GOALS section, gold bars, pinned | Same | None. |
| Quest, item and custom goals with task lists | Skill goals only | Theirs better | Consider "item goal" (e.g. Graceful) only if it serves skilling. |
| Auto-add quest prerequisites | We filter methods by quests, no quest goals | Missing | Skip (Quest Helper territory). |
| Presets (Barrows, Void, Ironman) | None | Missing | Skip. |
| Import/export goals as JSON | Goals sync via RuneLite profile | Missing | Small: copy/paste goals text; profile sync already covers most uses. |
| Completion chat message | "goal reached" chat line | Same | None. |
| Suggested method, tools, time to goal | Not in theirs | Ours better | None. |

### XP Goalz
Repo: https://github.com/hybridbrizz/Xp-Goals · Licence: BSD 2-Clause · Popularity: low-medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Per-skill XP targets that reset hourly/daily/weekly/monthly | Level/XP goals only | Missing | Add a "daily XP" goal type (reset at a chosen hour). |
| Custom schedule patterns | None | Not relevant | Skip; too niche. |
| Stacked goal bars overlay, overfill to 200% | Pinned overlay shows one skill | Theirs better | Optional multi-goal overlay. |
| Hover for recent results | None | Missing | Comes with the daily type. |

### Banked Experience
Repo: https://github.com/TheStonedTurtle/banked-experience · Licence: BSD 2-Clause · Popularity: high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Total XP banked per skill from logs, ores, herbs, bones... | We read the bank for Make:/Needs:, but show no banked XP total | Missing | Add "Banked XP" and the level it reaches to each card. |
| Choose activity per item (e.g. bones: altar type) | Track for goal item picks (Prayer methods, recipes) | Same | Reuse these picks for the banked total. |
| Show secondaries needed | Make: line knows recipes | Theirs better | Show missing secondaries on the Make: line. |
| Respect level requirements | We only suggest what your level allows | Same | None. |
| Include seed vault, looting bag, fossil chest, potion storage | Bank and inventory only | Theirs better | Add seed vault and potion storage later. |
| Include output items (chained steps) | None | Missing | Include if banked XP is added. |

### XP Grapher
Repo: https://github.com/cosmicquahog/XpGrapher · Licence: BSD 2-Clause · Popularity: medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Real-time XP-over-time graph, several skills, colours | Numbers only | Missing | Add a small session graph on card hover. |
| XP/hr on the graph | XP/hr per card | Same | None. |
| Movable/resizable overlay | Pinned overlay is movable | Same | None. |

### Essence Pouch Tracking
Repo: https://github.com/Infinitay/essence-pouch-tracking · Licence: BSD 2-Clause · Popularity: high.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Essence stored shown on each pouch | None (only coin pouches) | Missing | Add under Runecraft; makes the actions-left count honest too. |
| Essence until the pouch degrades | None | Missing | Include, with a voice alert near decay. |
| Colossal pouch only | None | Not relevant | Follows if added. |

### Sailing (LlemonDuck)
Repo: https://github.com/LlemonDuck/sailing · Licence: BSD 2-Clause · Popularity: high (main Sailing hub plugin).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Highlight sea charting spots (charted/uncharted/unavailable), map icons | Sailing hover: task, level, XP, riddle answer | Theirs better | Colour charting spots by charted/your level. |
| Weather station / current duck / mermaid solvers | Hover: mermaid riddle answer, duck drifting/despawn | Theirs better | Fine as is; maybe a duck trail line later. |
| Highlight rapids (safe/dangerous) | None | Missing | Add highlight; info only. |
| Lightning strike highlight | None | Missing | Add with rapids. |
| Low boat HP notify | None | Missing | Easy voice alert. |
| Trimmable sails highlight | None | Missing | Add highlight + optional alert. |
| Speed boost infobox, sail heading/speed, true tile | None | Missing | Speed boost timer is the useful one. |
| Crystal extractor harvestable highlight/notify | None | Missing | Easy alert. |
| Barracuda Trials crates, course targets, splits | None | Missing | Lower priority. |
| Courier destination on cargo crates, pickup table | None | Missing | Small, useful. |
| Shipwreck timers / hover | Shipwreck timers and salvage hover | Ours better | None. |
| Sea monster info | Sea monster hover (HP, max hit, keel) | Ours better | None. |
| Sails at helm only, prioritise cargo hold, deprio stop-navigating | None | Not allowed | Skip: menu reordering. |
| Mute crew text, hide portal transitions | None | Not relevant | Skip. |
| Splits to file | None | Not allowed | Skip: java.io file writing. |

### MLM QoL
Repo: https://github.com/H-Mill/Runelite-Plugins (mlm-qol) · Licence: BSD 2-Clause · Popularity: low (new).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Remove Mine option when sack is full | Inventory Full alert only | Not allowed | Skip the block; add a "sack full" voice alert instead. |
| Chat warning at sack full / room again | None | Missing | Add as a Mining alert (sack count incl. pay-dirt on the belt). |
| Remove hopper Deposit when blocked | None | Not allowed | Skip. |
| Highlight broken struts, "ore in transit" label | None | Missing | Small: strut-broken alert. |
| Wake-up sound when you stop mining | Idle > AFK Alert | Same | None. |
| Vein timers | Mining Timers for veins | Ours better | None. |
| Baby mole name | None | Not relevant | Skip. |

### Efficient Skilling
Repo: https://github.com/DapperMickie/EfficientSkillingPlugin · Licence: BSD 2-Clause · Popularity: low (new).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Route from current level to target, by level band | Goal card "Train: X to 62", hover for each leg | Same | None. |
| Fastest / balanced / cheapest / AFK priorities | Training Focus: Fastest, AFK, Cheapest, Money Maker | Same | None. |
| Account types (Ironman, HCIM, UIM, GIM) | Not found | Missing | Add an Ironman filter (no GE-bought supplies). |
| Time estimate, cost, risk, source link per band | XP/hr in Method list, wiki text on hover | Theirs better | Add time-to-goal per leg in the hover. |
| Path to Max: whole-account view, worklist, timed tasks | Per-skill goals only | Theirs better | Optional "all goals" summary; skip the rest. |
| Reads Banked Experience choices (read-only) | None | Not relevant | Skip: only useful if we had no banked XP of our own (reading, not changing, a config is allowed). |
| Live in-game method detection and outlines | MethodDetector, world outlines, Track for goal | Ours better | None. |

### Wintertodt Notifications
Repo: https://github.com/erwinvc/runelite-plugins (wintertodt-notifications) · Licence: BSD 2-Clause · Popularity: low-medium.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Flash overlay when your action stops or is interrupted, with the reason | Idle > AFK Alert (generic) | Theirs better | Add the interruption reason (brazier broke, cold, snowfall) to our idle alert at Wintertodt. |
| Stop notifying at N points | None | Missing | Include with the above. |
| Native notification | Voice/chat alerts | Same | None. |

---

# Part: PvM/Slayer and GemCrab Tracker

Audited 2026-11-01 against RuneLite master (core plugins, sparse clone) and the Plugin Hub `plugins/` folder
(3,176 entries). Our side read from docs/FEATURES.md, PvmConfig/GemCrabConfig/BuffConfig and the trackers.

**Popularity caveat:** runelite.net and the GitHub API were blocked from this session, so install counts and
stars could not be read. Popularity below is my estimate from familiarity with the Hub ("very high" = one of
the Hub's most installed plugins, roughly 100k+; "high" = tens of thousands; "medium" / "low" = smaller;
"new" = first published in 2026, so probably small). Check these numbers on runelite.net/plugin-hub before
you rely on them. Licences were read from each repo's LICENSE at the commit the Hub pins.

Facts about our code that come up a lot below:
- Our alerts are voice plus chat only. Only the idle/logout warning uses RuneLite's `Notifier`, so none of
  the PvM, Slayer or crab alerts can raise a desktop notification or flash the window.
- The Slayer kill rate is for this session only. We save no per-task rates, no task history, no points
  and no streak.
- The buff bar covers potion boosts (divines to the second), antifire, stamina, antipoison/venom, stat
  boosts and drains. It does not cover vengeance, imbued heart, overload, teleblock, freezes on you, staff
  of the dead, prayer regen and similar.
- The aggression tolerance timer starts when an aggressive target is first fought. It is not tied to an
  area the way core's calibrated unaggressive-area timer is.

---

### Best additions (ranked)

1. **Desktop notification / window flash for alerts** (Gemstone Crab, Gemstone Crab Alert, Superior/Slayer
   plugins, core Notifier use everywhere). Add an "also notify" option per alert through the injected
   `Notifier`. This matters most for crab AFKers who have the client minimised, and it's cheap to add.
2. **Gemstone Crab session/lifetime stats**: crabs killed, gems mined by type, mining success/fail, XP
   gained, fight duration (Gemstone Crab by ShaneeexD, the most popular crab plugin). Save the counts
   with ConfigManager (RS profile).
3. **"Finish it off now" marker for lizards, gargoyles, rockslugs and zygomites** (core Slayer "Show monster
   weakness"). We already know the task item, so we only need an HP-threshold marker on the monster.
4. **Slayer points and task streak on the card, with a "next task is a milestone (x10/x50...)" heads-up**
   (core Slayer tooltip, Slayer Streak). These are read from varbits and only shown, never acted on.
5. **More timers on the buff bar**: vengeance, imbued heart / saturated heart, overload / smelling salts,
   freeze/teleblock on you, staff of the dead, prayer regeneration, liquid adrenaline, menaphite remedy
   (core Timers & Buffs).
6. **Remembered per-task kill rate, time on task, "done at" clock time, and a short task history**
   (Slayer Task ETA, Slayer Task Time Tracker, Slayer History). Save it with ConfigManager, never to
   files (we can't use java.io).
7. **Monster max hit and attack speed on the hover card / HUD** (Slayer Suite hover card, npc-max-hit). These
   are static wiki facts, so they fit our wiki data scripts. Don't add live "next attack" countdowns.
8. **Slayer bracelet charges (slaughter / expeditious) and ring of recoil** on the Slayer card, with a
   low/out alert (core Item Charges, Item Charges Improved, SlayerPlus). Keep it to the Slayer-relevant
   items, because Item Charges Improved already does the rest well.
9. **Kill-count goals per monster with a progress bar** (Monster Monitor). This matches the Skilling Helper's
   goal style, and "kill 200 X" also works outside Slayer.
10. **Supplies used and their cost per task/session** (Supplies Tracker; Slayer Suite's packing list is a
    bigger version of this). Count food, potion doses, cannonballs and runes used while on a task.

Not worth chasing: loot tracking (core Loot Tracker is excellent, so don't duplicate it), DPS
*calculators* (the wiki's calc and the DPS Calculator plugin are large projects), boss-specific helpers.

---

### Core: Slayer (`slayer`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Task infobox (count, tooltip with points and streak) | Slayer card: kills left, rate, ETA, prayer, item, where to go, routes | Ours better | Keep. |
| Points and streak (wilderness/Mortimer aware) in tooltip | None | Missing | Show points and streak on the card from the same varbits. |
| Count remaining on slayer items (gem, helm, ring) | None | Missing | Optional small number on the enchanted gem/helm; low priority. |
| Superior foe notification | Superior Spawned voice/chat alert | Ours better | Add an optional desktop notification. |
| Highlight task monsters (hull/tile/outline, colour) | Outline only, Konar-area aware | Ours better (Konar) / Theirs better (styles) | Consider a tile/hull style choice. |
| "Show monster weakness": overlay when a lizard/gargoyle/rockslug/zygomite is low enough to finish | Required item shown, no finish marker | Missing | Add a finish-off marker at the game's HP threshold. |
| Infobox expiry | Card toggled with hotkey | Same | None. |
| `!task` chat command | None | Not relevant | Skip; chat commands need RuneLite's chat command service and few use it. |

### Core: Boosts (`boosts`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Boosted/drained stats panel and infoboxes, relative/compact | Buff bar shows boosts (green) and drains (red) with icons | Same | None. |
| Next buff/debuff change timer (stat restore tick) | Potion time left, not the next 1-level decay tick | Theirs better | Optionally show "next -1 in 23s" on hover. |
| Boost threshold notification | Potion running out / expired alerts | Ours better | None. |

### Core: Timers & Buffs (`timersandbuffs`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Antifire, stamina, antipoison/venom, divine potions | Buff bar and alerts | Same | None. |
| Vengeance, imbued heart, overload, liquid adrenaline, menaphite, surge, goading, prayer regen, moonlight potion | None | Missing | Add the common combat ones to the buff bar (varbits are in RuneLite's API). |
| Freeze / teleblock on *you*, staff of the dead, DFS special cooldown | We show freezes on the monster only | Missing | Add freeze/teleblock on you and SOTD. |
| Cannon timer, God Wars altar cooldown, teleport cooldowns, Arceuus spell timers | Cannon Low alert only | Theirs better | Add the cannon decay timer to the card if cheap; skip the rest. |
| Burn damage accumulated/next hit | We show "Burning" on the monster | Theirs better | Low priority. |
| Boss-specific (Abyssal Sire stun, Tormented demon, Scurrius food, Colosseum doom, Fight Caves/Inferno) | None | Not relevant | Skip; core covers these. |

### Core: Opponent Information (`opponentinfo`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Opponent HP bar (value/percent/both) when no boss bar | PvM HUD HP bar plus weakness, aggro, DPS, hits | Ours better | None. |
| Mark opponents in the right-click menu | None | Not relevant | Skip (menu recolour adds little). |
| Player combat comparison on interaction | None | Not relevant | Skip; PvP-ish. |

### Core: NPC Aggression Timer (`npcunaggroarea`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| 10-minute tolerance timer calibrated to the unaggressive area, area lines, notify on expiry | Timer on the HUD from the first aggressive target; aggressive-to-you logic (level, GWD items, greegree) | Theirs better (timer), Ours better (who is aggressive) | Base the timer on the 21x21 area like core, or defer to core when it's on (we already detect other plugins). |
| "Show on slayer task" | We show aggro for any target | Same | None. |

### Core: Status Bars (`statusbars`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| HP/prayer/run/spec bars beside the inventory with restore previews | Orb flash, low alerts, "Out of prayer" over head | Not relevant | Skip; core does it well. |

### Core: Special Attack Counter (`specialcounter`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Defence drain tracking (DWH, Elder maul, BGS, Arclight, Emberlight, Darklight) | "Defence: 112 (-48)" on the HUD, estimated from wiki Defence | Same | None. |
| Spec hit counter infobox, spec drops over player, threshold notifications, party sync | Spec Ready alert | Theirs better | Optional "specs landed: 3" line; party sync not relevant. |

### Core: Item Charges (`itemcharges`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Bracelet of slaughter / expeditious charges and notifications | None | Missing | Add to the Slayer card (see Best additions 8). |
| Ring of recoil, dodgy necklace, binding necklace, forging, clay, chemistry... | None | Not relevant | Leave non-combat items to core / Item Charges Improved. |
| Potion doses on items, teleport charges | None | Not relevant | Skip. |

### Core: Prayer (`prayer`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Prayer flick indicator | Tick Counter's half-tick boxes (other plugin) | Same | None. |
| Prayer time left at current drain, dose indicator, prayer bar over head | Low/out-of-prayer alerts, overhead "Out of Prayer" | Theirs better (time left) | Show "Prayer: 3:12 left" on the HUD, from prayer drain and bonus. |
| Overhead / boost indicator infoboxes | None | Not relevant | Skip. |

### Core: Attack Styles (`attackstyles`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Current style overlay | HUD shows combat style | Same | None. |
| Warn when training an unwanted skill (e.g. Defence) | None | Missing | A cheap "warn for Defence XP" alert (useful for pures). |
| Remove warned styles from the combat tab | None | Not allowed | Skip: hides game UI conditionally. |

### Core: DPS Counter (`dpscounter`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Damage and DPS, auto pause/reset, boss-only | DPS per monster/type/session, last/max hit, Reset | Ours better (solo) | None. |
| Party DPS | None | Not relevant | Skip. |

### Core: Loot Tracker (`loottracker`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Loot per source with GE value, kill counts, saved | None | Missing | Don't duplicate it. At most show "loot this task: 120k" from `LootReceived` on the Slayer card. |

### Core: NPC Indicators (`npchighlight`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Highlight by name list, many styles, names over NPCs | Task monster outline only | Not relevant | Leave general highlighting to core. |
| Respawn timer for tagged NPCs | None | Missing | Optional respawn timer for task monsters (useful for slow spawns). |

### Core: Cannon (`cannon`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Cannonball count infobox, low warning | Cannon Low alert | Same | None. |
| Double-hit spots, common cannon spots | Where To Go marks places "Cannon" | Theirs better | Skip; core shows spots in-world. |

### Core: Barrows (`barrows`), Corp (`corp`), Zalcano (`zalcano`), Boss Timers (`bosstimer`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Barrows: brothers killed, chest value, prayer drain timer, puzzle answer | None | Not relevant | Skip; core covers it. |
| Corp: damage dealt, dark core marker | None | Not relevant | Skip. |
| Zalcano: rock/shield overlays, damage panel | None | Not relevant | Skip. |
| Boss respawn timers (infobox) | None | Not relevant | Skip; core does it. |

### Core: Poison (`poison`) and Regeneration Meter (`regenmeter`)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Next poison/venom damage, HP orb icon change | Poison/venom alert, buff bar debuff | Theirs better (next hit) | Low priority. |
| HP and spec regen meters, HP regen notification | Spec Ready alert | Theirs better | Skip; core does it. |

---

### Hub: Gemstone Crab (`gemstone-crab-timer`)
Repo: https://github.com/ShaneeexD/GemstoneCrabTimer · Licence: BSD 2-Clause · Popularity: very high (the main crab plugin; my estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| HP-threshold desktop notification (default 2%) | Low Health Alert (%), voice/chat | Theirs better (desktop) | Add a Notifier option. |
| Tunnel highlight after death, screen pulse | Tunnel outline, crab-moved alert, radar | Ours better | Optional screen pulse. |
| DPS, total damage, XP gained, fight duration, time left | DPS, damage, last/max hit, burrow countdown, XP type | Same (theirs adds XP gained and duration) | Add XP gained and fight duration lines. |
| Gem tracker: gems mined by type (persisted) | Shell mineable or not; no gem counts | Missing | Count gems by type from inventory changes, saved per profile. |
| Lifetime kills, mining attempts/success/fail, cumulative XP, kill chat message | None | Missing | Add saved stats and a reset. |
| Overlay colour customisation | Outline colours only | Theirs better | Low priority. |
| — | Gear Check (damage bonus, off-style slots), potion buffs, prayer/HP low rows | Ours better | Keep; it's our edge. |

### Hub: Gemstone Crab Alert (`gemstone-crab-alert`)
Repo: https://github.com/1Andrewcoleman/Gemstone-Crab-Alert · Licence: BSD 2-Clause · Popularity: medium (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Full-screen flash at HP threshold (colour, duration, speed) | Voice/chat alert | Theirs better | Add an optional screen-flash for the crab alerts. |

### Hub: AFK Crab Helper (`afk-crab-helper`)
Repo: https://github.com/1504681/afk-crab-helper · Licence: BSD 2-Clause · Popularity: low/medium (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Dims the game screen while you fight the crab, with a countdown | None | Missing | Optional "AFK dim" overlay with the burrow countdown large; low effort. |

### Hub: Crab Scouter (`crab-scouter`)
Repo: https://github.com/jmccarey/crab-scouter · Licence: BSD 2-Clause · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Crowdsourced crab HP / players per world from a third-party server | None | Not allowed (for us) | Skip: we use no network and it sends IPs to a third-party server; a Hub reviewer flags this kind of thing. |

### Hub: Item Charges Improved (`tictac7x-charges`)
Repo: https://github.com/TicTac7x/runelite-plugins · Licence: BSD 2-Clause · Popularity: very high (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Charges on 100+ items (bracelets, serp helm, blowpipe-style weapons, glory...) | None | Missing (Slayer subset) | Only add slayer bracelets on the Slayer card; leave the rest to this plugin. |

### Hub: Supplies Tracker (`supplies-tracker`)
Repo: https://github.com/pwatts6060/SuppliesTrackerExternal · Licence: BSD 2-Clause · Popularity: very high (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Food, potions, ammo, runes, cannonballs and weapon charges used, with GE cost | Low ammo / out of runes / cannon low alerts | Missing | A per-task "supplies used: 340k" line on the Slayer card (ItemManager prices). |

### Hub: Monster HP Percentage (`monster-hp-percentage`)
Repo: https://github.com/lejeffe/MonsterHP · Licence: BSD 2-Clause · Popularity: high (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| HP % or number over listed NPCs, remembers HP when the bar is hidden, low-HP colour per NPC | HUD HP bar for your target; hover card HP | Theirs better (over many NPCs) | Optional HP text over task monsters; this is also how the finish-off marker would draw. |

### Hub: Better NPC Highlight (`better-npc-highlight`)
Repo: https://github.com/riktenx/better-npc-highlight · Licence: BSD 2-Clause · Popularity: very high (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| 9 highlight styles by name/ID, respawn timers | Task-monster outline | Theirs better | Add a style choice (outline/tile/hull) for task monsters; leave the rest. |

### Hub: Turael Skipping (`turael-skipping`)
Repo: https://github.com/BrastaSauce/turael-skipping · Licence: BSD 2-Clause · Popularity: high (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Best location for Turael/Aya tasks, teleports, items | Where To Go + How To Get There for every task | Ours better | None. |
| World map icon for the task location | None | Missing | Add a world map point for the first place (WorldMapPointManager). |
| Hides the info box once you're in the area | Card stays | Theirs better | Shrink "Where To Go" once you're there. |
| Area outlines | None | Missing | Low priority. |

### Hub: Slayer Assistant (`slayer-assistant`)
Repo: https://github.com/LeeOkeefe/slayer-assistant-plugin · Licence: BSD 2-Clause · Popularity: medium/high (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Panel to look up any task: locations, items, attack styles, masters, wiki links | Card for the current task only | Theirs better (lookup) | Optional: a "look up a task" search in a side panel. |

### Hub: Slayer Task ETA (`slayer-task-eta`)
Repo: https://github.com/ryandurden/slayer-task-eta · Licence: BSD 2-Clause · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Kills left, kills/hr, ETA | Same on the card | Same | None. |
| "Done at" clock time, time on task, rate that leaves out breaks, superiors excluded | Rate over the last 30 kills | Theirs better | Add "done at", time on task and a pause-aware rate. |
| Rates saved per task, so the ETA shows at once next time | Session only | Missing | Save the rate per task with ConfigManager. |

### Hub: Slayer Task Time Tracker (`slayer-task-time-tracker`) and Slayer History (`slayer-history`)
Repos: https://github.com/Avexiis/slayer-task-time-tracker (BSD 2-Clause, new) · https://github.com/samszotkowski/runelite-plugins (BSD 2-Clause, low/medium)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Past tasks with total and "efficient" time, skips marked | None | Missing | A short history (last 20 tasks) saved in config; no log files. |

### Hub: Slayer Streak (`slayer-streak`)
Repo: https://github.com/nw51/slayer-streak-plugin · Licence: BSD 2-Clause · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Streak, points, tasks to the next milestone, warning when the next task is a milestone | None | Missing | Add to the card plus a "next task is x50: go to Konar/Duradel" alert. |
| Hides non-Konar masters / NPC Contact options at milestones | None | Not allowed | Skip: conditional menu/NPC removal is on our "never add" list. |

### Hub: Superior Slayer Tracker (`superior-slayer-tracker`)
Repo: https://github.com/JJDeakins1/superior-slayer-tracker · Licence: **MIT** (not BSD-2; fine to read, but don't copy code without keeping the MIT notice) · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Superior kills per type, imbued heart odds and dry %, Mortimer boosts | Superior Spawned alert | Missing | Optional "superiors this task / lifetime" count; the odds are niche. |

### Hub: Better Slayer (`better-slayer`)
Repo: https://github.com/vividflash/better-slayer · Licence: BSD 2-Clause · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Mortimer task choice odds and pick, task sorting, master rules | Best master for your levels | Theirs better (Mortimer) | Skip unless Mortimer tasks matter to your players. |

### Hub: Slayer Suite (`slayer-suite`) and SlayerPlus (`slayerplus`)
Repos: https://github.com/pricecheckgg/slayer-suite (BSD 2-Clause, new) · https://github.com/rickernet/SlayerPlus (BSD 2-Clause, new)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Ranked spots for your account, routes with chevrons, fairy-ring dial helper | Where To Go + routes (text) | Theirs better | Skip the pathing (big job; Shortest Path exists). A world map marker is enough. |
| Bank readiness / packing list (food, restores, antifire per kills left) | Required item + missing item warning | Theirs better | A simple "bring: rock hammer, antifire, 10 prayer pots" line is possible later. |
| Drop odds / dry streak on the task's rare drops | None | Missing | Niche; skip. |
| Hover card with max hit, style, weakness, xp | Hover card with HP, weakness, attack style, aggro, Slayer info | Same (theirs has max hit) | Add max hit. |
| Valuable drops marked with despawn timer | None | Not relevant | Core Ground Items does this. |
| "Attack guard": non-task Attack options pushed off left-click or hidden | None | Not allowed | Skip: conditional menu changes are on our "never add" list. |
| Highlights the recommended menu text / item (SlayerPlus) | None | Not allowed (risky) | Skip: it tells the player what to click step by step. |
| Task complete card, weekly stats, points/skip costs | Task Complete alert | Theirs better | Optional "task done in 42 min, 512 kills/hr" chat summary. |

### Hub: Monster Monitor (`monster-monitor`)
Repo: https://github.com/ChunkyAtlas/monster-monitor · Licence: BSD 2-Clause · Popularity: medium (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Lifetime kill counts per NPC with a panel | None | Missing | Optional; loot tracker kill counts overlap. |
| Kill limits / goals with a progress bar and alert | None (only Slayer task counts) | Missing | Add "kill goal" on the PvM HUD, reusing the goal UI ideas. |
| Saves logs to a JSON file in ~/.runelite | — | Not allowed (for us) | Save via ConfigManager instead. |

### Hub: DPS Calculator (`dps-calculator`)
Repo: https://github.com/LlemonDuck/dps-calculator · Licence: BSD 2-Clause · Popularity: high (estimate)

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Theoretical DPS / max hit / accuracy from gear, levels, prayers vs an NPC | Measured DPS and your max hit seen; crab Gear Check | Theirs better | Skip a full calculator; maybe add "your max hit (calc)" later. |

### Hub: Elemental Weakness Checker (`elemental-weakness-checker`)
Repo: https://github.com/sweetbabynicco/elementalweaknessplugin · Licence: BSD 2-Clause · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Rune + weakness % over the NPC, autocast match colour | Weakness with element % on hover and HUD, green/red by your style, no lookup | Ours better | None (theirs fetches the wiki live; we ship the data). |

### Hub: HP & Prayer Regen Timers (`hp-prayer-regen-timers`)
Repo: https://github.com/FarlingLeaks/hp-prayer-regen-timers · Licence: BSD 2-Clause · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Ticks to next HP regen (Rapid Heal), overheal decay, prayer regen potion cycle | None | Not relevant | Skip; niche (core Regen Meter covers HP). |

### Hub: Wrong Prayer Warning (`wrong-prayer-warning`)
Repo: https://github.com/bockscarrasor/wrong-prayer-warning · Licence: BSD 2-Clause · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Warns when your offensive prayer (Piety/Rigour/Augury) doesn't match your weapon | None | Missing | Cheap, allowed alert: it checks your own setup, not the boss's attacks. Add it to Combat Alerts. |

### Hub: NPC Attack Tick Timer (`npc-attack-tick-timer`)
Repo: https://github.com/DillonMartin00/npc-attack-tick-timer · Licence: BSD 2-Clause · Popularity: new

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Countdown to a non-boss NPC's next attack | None | Not allowed (for bosses) / risky | Skip: Jagex rules ban next-attack prediction for bosses, demi-bosses and wave minigames, and the plugin itself needs a wiki safety filter. Too close to our "never add" line. |
| Attack speed in the right-click menu | None | Missing (static info) | Show static attack speed on the hover card instead. |

---

### Not allowed or risky (summary)
- Conditional menu changes: hiding masters (Slayer Streak), hiding non-task Attack (Slayer Suite), removing
  combat styles (core Attack Styles' option).
- Telling the player what to click next (SlayerPlus menu highlight).
- Next-attack countdowns on NPCs (bosses banned; ordinary NPCs borderline).
- Third-party network services (Crab Scouter), and file logs in ~/.runelite (Monster Monitor, Slayer
  History). Our compliance doc forbids java.io and we use no network.

---

# Part: Tick Counter and Alerts

Date: 2026-11-01. Ours was read from `src/main/java/com/bradsmultitool/` (TickConfig, TickCounter, CommonConfig,
VoicesConfig, IdleWarnings, Alerts, VoiceAlertService, AlertType, PotionAlerts/BuffTimer, PvmConfig) and
docs/FEATURES.md. Core plugins were read from runelite/runelite master. Hub plugins were shallow-cloned from the
repository named in their runelite/plugin-hub `plugins/<name>` file (hub list as of 2026-11-01, 3176 plugins).

**Popularity:** api.runelite.net (install counts) is blocked from this session (proxy 403) and the GitHub API was
also unavailable, so the popularity labels below (High / Medium / Low) are my estimates from memory of the hub's
install counts and how well-known each plugin is. They are not measured. Check runelite.net/plugin-hub before
deciding.

**Where our features live:** the Tick Counter has the tick count, boxes, bar, metronome and click timing. Snuffy's
Alerts (VoicesConfig) has the voice persona/volume, Same Voice For All, Chat Messages, the AFK alert and the idle
logout warning with its countdown overlay. The potion alerts are in PvM/Slayer and GemCrab (PotionAlerts), and
health, prayer, spec and run energy alerts are in PvM and Agility. Only the idle logout warning also goes through
RuneLite's `Notifier`. Every other alert is a voice line and/or a chat message.

---

### Best additions (ranked)

1. **"Also send a RuneLite notification" for alerts** (Alerts, all plugins). Core Idle Notifier and nearly every hub
   notifier go through `Notifier`, so players get the tray pop-up, taskbar flash, request focus and screen flash they
   picked in RuneLite's settings. Ours only does that for the logout warning. A switch (global, or one per alert next to
   Voice/Chat) that calls `notifier.notify(...)` covers players who play muted or in another window. Allowed: core does it.
2. **6-hour logout warning** (Alerts). Core Idle Notifier has "Six hour logout notification" (it fires 340 minutes after
   login), and two hub plugins exist only for this (6 Hour Logout Timer, Six Hour Reminder). We have nothing. Add a warning
   N minutes before, with a repeat, and put the countdown on our logout overlay. It needs a new voice line in every voice,
   made with the generation scripts.
3. **Attack countdown and lost ticks for Follow Attack Speed** (Tick Counter). Done: Count Down To Attack, Late Ticks, eating delays, non-attack animations. Attack Timer Metronome counts down to your
   next attack (3-2-1, or 2-1-0) and shows -1, -2 when you're late to attack. It also accounts for eating delays and for
   weapons that change speed. Ours only counts up from the attack tick and treats any animation while you're interacting
   as an attack. Add a "Count Down" option and a "Late by N ticks" display. Their repo is BSD-2, so their speed/delay
   cases can be studied and credited.
4. **Hotkeys** (Tick Counter). Done (Hotkeys section). Visual Metronome and Visual Ticks have hotkeys to reset the cycle to 1, show/hide, and
   raise or lower Ticks Per Cycle. Ours has none, so the only way to line the count up is turning a setting on and off.
   Allowed: like our Slayer card hotkey, it only changes our own overlay.
5. **Repeat the AFK alert / a stronger AFK cue** (Alerts). Idle Alarm loops a sound until you act, and Better
   Notifications fades a screen-edge glow in, getting stronger the longer you're idle. Ours speaks "You are idle" once.
   Add Repeat AFK Alert (Repeat Every / Max Repeats, the same as the logout warning) and an optional screen-edge glow or
   flash while you're idle.
6. **Unpotted reminder** (PvM, next to the potion alerts). Unpotted Reminder warns when you attack while your combat
   stats aren't boosted, and has a boost threshold, NPC allow/deny lists and an "only in instances" option. Our
   PotionAlerts already knows the UNPOTTED state but never says anything about it. Add "Not boosted" when you start
   attacking (once per fight).
7. **Run energy alerts everywhere** (Alerts). Done: Idle > Run Energy Low Below / Restored At. Core Idle Notifier has low *and* restored ("You have restored run energy")
   thresholds that work anywhere. Ours only warns about low energy while training Agility. Add both thresholds outside
   Agility.
8. **Tick count at the cursor** (Tick Counter). Visual Metronome's "Tick Counter Follows Mouse" and the hub's cursor-tick
   plugins (tickcursor, cursor-tick-ring, visual-tick-cursor) show the count where your eyes already are. Add a cursor
   position for the count, next to Count Over Head.
9. **Server tick health next to Click Timing** (Tick Counter). Tick Tracker measures how far real ticks drift from 600ms
   and warns about lag. Late clicks are often caused by server or connection jitter, so a small "ticks: 98% on time" line
   in the click summary would explain them. Information only.
10. **Speak only when RuneLite isn't focused** (Alerts). Done: General > Speak Only When Unfocused. This mirrors RuneLite's "Send notifications when focused" switch
    (which defaults to off). Players watching the screen may not want voice. Add a switch, checked against the client's
    focus state.

Not recommended, even though popular: free-form custom alerts (Watchdog / Advanced Notifications: leave that to
Watchdog; it also needs a no-go list of boss areas, see its section), text-to-speech (needs a network service or an
external program, both against our rules), and Discord webhooks (our plugins use no network at all).

---

### Core: Metronome

Repo: runelite/runelite `plugins/metronome`, BSD-2. Popularity: core (ships with RuneLite).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Sound every N ticks (Tick count) | Metronome on every tick, with Ticks Per Cycle 1-10 | Same | None. |
| Separate tick and tock volume | One Volume for all our sounds; a different sound on the first tick | Same | None; a second volume isn't worth a setting. |
| Tick/tock sound choice (fixed GE plops) | 7 tones plus First Tick Only and Play Ahead | Ours better | None. |
| Sets the game's sound effect volume for the sound and restores it | Same technique | Same | None. |

### Core: Idle Notifier

Repo: runelite/runelite `plugins/idlenotifier`, BSD-2. Popularity: core.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Idle animation (skilling animation stopped) | AFK Alert: you were gaining XP, stopped, and haven't clicked or typed; banking and dialogues ignored | Ours better | None; ours covers every skill, not only a list of animations. |
| Idle interaction (combat or fishing target gone) | Covered by the XP rule (combat gives XP) | Same | None. |
| Idle movement (stopped walking/running) | Not covered (walking gives no XP) | Missing | Low priority: an optional "Stopped moving" alert for long walks. |
| Idle logout warning | Warn Before Logout, Repeat, countdown overlay, voice and chat, plus `Notifier` | Ours better | None. |
| Six hour logout warning | None | Missing | Add (Best additions #2). |
| Idle notification delay (5s) | AFK Alert After (10s default) | Same | None. |
| Hitpoints threshold (absolute points) | PvM Low Health Alert as %, orb flash, repeats | Ours better | None. |
| Prayer threshold | PvM Low Prayer Alert as %, orb flash, OUT OF PRAYER overhead, repeats | Ours better | None. |
| Low run energy threshold | Only while training Agility (Run Energy Alert Below) | Theirs better | Make it work everywhere (Best additions #7). |
| High run energy (restored) | None | Missing | Add with #7. |
| Low oxygen (Fossil Island underwater) | None | Missing | Low priority: one threshold alert, but it needs a voice line. |
| Spec energy reached threshold | PvM Spec Alert At (only after you've used spec) | Same | None. |
| Each notification is a `Notification` (per-alert tray/flash/sound overrides) | Voice/Chat switches; `Notifier` only for logout | Theirs better | Add a RuneLite-notification switch (Best additions #1). |

### Core: Logout Timer

Repo: runelite/runelite `plugins/logouttimer`, BSD-2. Popularity: core.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Sets the client's idle timeout (up to 25 min) | We read it (`client.getIdleTimeout()`) and warn before it | Not relevant | None; changing the timeout is its job, and we must not change other plugins' settings. |

### Core: RuneLite notification settings (RuneLiteConfig)

Repo: runelite/runelite `client/config/RuneLiteConfig.java`, BSD-2. Popularity: core.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Tray notifications, tray icon | Logout warning only | Theirs better | Route alerts through `Notifier` as an option (#1). |
| Request focus (taskbar / force) | Logout warning only | Theirs better | Same as above. |
| Notification sound and volume | Our own voice clips, voice volume | Ours better | None. |
| Game message notifications (chatbox line) | Chat Messages per alert | Same | None. |
| Flash (game frame flash, colour) | Orb flash, out-of-prayer flash and logout overlay only | Theirs better | Comes free with #1; a screen-edge glow for AFK is #5. |
| Send notifications when focused (off by default) | Voice always plays | Missing | Add "only when not focused" (#10). |

### Core: Timers & Buffs (potion parts only)

Repo: runelite/runelite `plugins/timersandbuffs`, BSD-2. Popularity: core.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Timers for antipoison/venom, antifire, stamina, divine potions | Buff bar and HUD, plus voice/chat alerts before and at expiry | Ours better | None; core has no alerts at all. |
| Overload, liquid adrenaline, menaphite remedy, prayer enhance, prayer regeneration, goading, surge, moonlight potion timers | Not timed or alerted | Missing | Medium for PvM: add overload and prayer enhance/regeneration "running out" alerts (raids/NMZ players ask for overload most). |
| Imbued heart, vengeance, charge, magic imbue timers | Not covered | Missing | Low: "Imbued heart ready" and "Vengeance ready" alerts would fit Combat Alerts. |

### Core: Boosts (boost alert parts only)

Repo: runelite/runelite `plugins/boosts`, BSD-2. Popularity: core.

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Notify when boosted stats drain to the boost threshold (any skill) | Time-based "running out / run out" for combat boosts only | Same (different trigger) | Low: let Potion Buffs also warn at "+N levels left", and cover skilling boosts if players ask. |
| Next buff/debuff change timer | BuffTimer uses the same decay mechanic for exact time left | Same | None. |

---

### Hub: Visual Metronome

Repo: https://github.com/vincent0955/Visual-metronome (BSD-2). Popularity: High (estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Coloured metronome box, tick number in it | Tick Boxes (row/column, separate boxes, shapes, opacity), Box Numbers | Ours better | None. |
| Tick number over the player | Count Over Head (stays clear of the model) | Same | None. |
| Up to 10 colours, one per tick of the cycle | Tick Colour and First Tick Colour | Theirs better | Low: optional per-tick colours, numbers and colours only. |
| True tile overlay in metronome colours, fade-out | None (core Tile Indicators does true tile) | Not relevant | None; leave it to Tile Indicators. |
| Sync ticks with a party member | None | Missing | Low: possible through RuneLite's PartyService; only worth it if group 1-tick training is asked for. |
| Hotkeys: reset cycle (to a chosen tick), show/hide, ticks +/- | None | Missing | Add (Best additions #4). |
| Tick counter follows the mouse | None | Missing | Add (Best additions #8). |
| Cycle 2 and Cycle 3 counters running at the same time | One cycle | Theirs better | Owner's call; it's close to the multi-tick pattern idea already turned down, so I'd skip it. |
| Font type / size options | Over Head Text Size | Same | None. |

### Hub: Attack Timer Metronome

Repo: https://github.com/ngraves95/attacktimer (BSD-2). Popularity: High (estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Ticks until your next attack, counting down | Follow Attack Speed counts up, 1 = attack tick | Theirs better | Add a Count Down option (#3). |
| Zero-based count (2-1-0) | None | Missing | Include with Count Down. |
| Lost ticks (-1, -2 when you're late attacking) | None | Missing | Add (#3); it is the combat version of our Click Timing. |
| Last-tick colour | First Tick Colour (the attack tick) | Same | None. |
| Attack bar over the player (fills/drains, HD style) | Tick Bar (screen overlay, per tick) | Theirs better | Low: optionally let the Tick Bar fill over the whole attack cycle when following attack speed. |
| Eating delays, speed-changing weapons and bosses (Scurrius bones, ToA P4, Yama, Eye of Ayak...), blocklist of non-attack animations | Weapon speed with Rapid; any animation while interacting counts as an attack | Theirs better | Study their cases (BSD-2, credit) and add at least eating delays and the animation blocklist. |

### Hub: Tick Counter

Repo: https://github.com/winterdaze/tick-counter (BSD-2). Popularity: High in raids/PvM (estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Counts attack ticks per player (you and others) in raids/instances, totals, reset per instance | None (same name, different feature: theirs measures attack uptime) | Not relevant | None; it's a PvM stats tool. If wanted, a "your missed attack ticks this fight" stat fits PvM, not the Tick Counter. |

### Hub: Tick Tracker

Repo: https://github.com/Tatters654/tick-tracker (BSD-2). Popularity: Medium (estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Measures real tick lengths, % within low/medium/high variance, coloured overlay | None | Missing | Add a one-line tick health readout to the Click Timing summary (#9). |
| Chat warning when a tick is too long/short | None | Missing | Low: optional, with the readout. |

### Hub: Visual Ticks, Metronome Infobox, Game Tick Information (smaller tick plugins)

Repos: https://github.com/mcscoobe/visual-ticks (BSD-2), https://github.com/kentor/metronome-infobox (BSD-2),
https://github.com/AcolyteOfFire/Game_Tick_Info (BSD-2). Popularity: Low to Medium (estimates).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Visual Ticks: 3 independent sets of tick shapes, rows, spacing, "only show on a chosen tab" | One set of boxes, movable separately | Same | Low: "only show while the inventory tab is open" is a nice small option. |
| Visual Ticks: hotkeys to reset and add/remove ticks | None | Missing | Covered by #4. |
| Metronome Infobox: count as an infobox | None | Missing | Low: an infobox is a cheap extra place for the count. |
| Game Tick Info: ticks spent on the current tile | None | Missing | Low; useful for some PvM movement, not core to our plugin. |
| Game Tick Info: lap timer between marked tiles | None | Not relevant | None. |

### Hub: Watchdog

Repo: https://github.com/adamk33n3r/runelite-watchdog (BSD-2). Popularity: High (estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| User-built alerts on game messages, chat, overhead text, stat changes, XP drops, sounds, spawns, inventory, location, other plugins' notifications | Fixed set of ~30 alerts with voice/chat switches | Theirs better (different aim) | Don't copy; a free-form builder is a whole plugin. Ours wins on zero setup. |
| Actions: chat line, overhead text, tray, screen flash, overlay banner, popup, screen/object marker, request focus | Chat line, voice; overlays for low prayer and logout | Theirs better | Pick the cheap ones: `Notifier` (#1) and an edge glow (#5). |
| Text to speech (Eleven Labs or legacy TTS) | Pre-recorded voice lines (10 voices) | Not allowed | Keep clips; TTS needs a network service. |
| Custom .wav/.mp3 from disk | Bundled clips | Not allowed | None (java.io file APIs). |
| Plugin toggle action, Shortest Path / Dink hooks | None | Not allowed | None (we must not change other plugins). |
| Turns itself off in high-end boss areas (Hydra, DT2 bosses, Inferno, raids...) | n/a | Not relevant | Note: any future "alert on sound/spawn/overhead text" from us would need the same no-go list (boss attack prediction is a rejected feature). |
| Alert Hub (shared alerts downloaded) | None | Not relevant | None (network). |

### Hub: Dink

Repo: https://github.com/pajlads/DinkPlugin (BSD-2). Popularity: High (estimate, among the most installed).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Discord webhooks for deaths, loot, levels, pets, clues, KC, diaries, etc. | None | Not relevant | None; our plugins make no network calls, and Dink does this well. |

### Hub: Natural Speech and TTS

Repos: https://github.com/phyce/rl-natural-speech (BSD-2), https://github.com/techgaud/TTS (BSD-2). Popularity:
Natural Speech Medium-High, TTS Medium (estimates).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Speaks any chat, dialogue or notification text with many voices | Fixed pre-recorded lines in 10 voices | Not allowed | None; Natural Speech runs an external program (Piper) or a web API, and TTS calls a web service (readaloud.app). Both break our rules. |
| TTS: speaks RuneLite notifications | Our alerts speak themselves | Same | None. |
| Queueing, spam filter, word deny list | Clips chain on the clip's STOP event | Same | None. |
| Distance-based volume, per-speaker voices | n/a | Not relevant | None. |

### Hub: Better Notifications

Repo: https://github.com/david3200/better-notifications (BSD-2). Popularity: Low (newer, estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Screen-edge glow for low HP and prayer, warning and critical levels, pulse | Orb flash, OUT OF PRAYER overhead | Same | Low: an edge glow is an alternative look, not more information. |
| Gold edge glow that grows while you're idle, critical after N seconds | AFK voice/chat once | Theirs better | Add as part of #5. |
| Preview of each warning in settings | Turning a Voice switch on plays a preview | Same | None. |

### Hub: Idle Alarm, AFK Timer, Abex's Idle Notifier

Repos: https://github.com/observnad/Idle-Alarm (BSD-2), https://github.com/jkufro/AfkTimer (BSD-2),
https://github.com/abextm/abexs-idle-notifier (BSD-2). Popularity: Low to Medium (estimates).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Idle Alarm: looping alarm sound until you act, max duration | Single AFK line | Theirs better | Add Repeat AFK Alert (#5). |
| Idle Alarm: choose whether mouse movement resets the timer | Clicks/keys reset AFK (mouse movement doesn't) | Same | None. |
| Idle Alarm: screen flicker while idle | None | Missing | Part of #5 (gentle; avoid strobing). |
| AFK Timer: notify after N seconds of no input, whatever you were doing | AFK only after you were training | Ours better | None; ours doesn't nag while you're banking. |
| Abex: instant "not busy" when the rock you clicked despawns | Skilling Helper's Rock Depleted / Tree Fallen alerts | Same | None. |

### Hub: 6 Hour Logout Timer and Six Hour Reminder

Repos: https://github.com/LeoPalmowski/6Hour-Logout-Timer (BSD-2), https://github.com/Richardant/Six-Hour-Reminder
(BSD-2). Popularity: Low to Medium (estimates).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Warning N minutes before the 6-hour logout, repeat every minute | None | Missing | Add (#2). |
| Countdown overlay/infobox | Idle logout overlay only | Missing | Reuse our logout overlay for the 6-hour countdown in its last minutes. |
| Session length counted from login (the server time isn't readable) | n/a | Not relevant | Same limitation for us; say so in the setting's tooltip. |

### Hub: Unpotted Reminder

Repo: https://github.com/AnkouOSRS/unpotted-reminder (BSD-2). Popularity: Medium (estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Warn when attacking unboosted (melee/ranged/magic, per-style threshold) | PotionAlerts tracks UNPOTTED but never warns | Missing | Add "Not boosted" on attack (#6). |
| NPC allow/deny lists, only in instances, ignore small XP drops | n/a | Missing | Include "only in instances" and the XP-drop floor; skip the lists at first. |
| Flashing overlay or infobox, `Notifier` with cooldown | Voice/chat | Same | None (plus #1). |

### Hub: Low HP Alarm

Repo: https://github.com/brevinleethomas-commits/runelite-low-hp-alarm (BSD-2). Popularity: Low (new, estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Looping alarm at or below HP % | Low Health Alert % with Repeat Voice Alert, Repeat Every, Max Repeats | Same | None. |
| Custom WAV path from disk | Bundled clips | Not allowed | None (java.io). |
| Chat warning | Chat: Low Health | Same | None. |

### Hub: Potion Boosts

Repo: https://github.com/javlanoob/potion-boosts (BSD-2). Popularity: Low-Medium (estimate).

| Their feature | Ours | Verdict | Suggested action |
|---|---|---|---|
| Labels on potions in the inventory with how much they'd boost or restore now | None (we show active boosts, not what a dose would add) | Missing | Low, and it belongs to PvM rather than Alerts. Skip unless asked. |
| Triple-eat marking (food, karambwan, brew) | None | Not relevant | None; it edges towards telling you what to click. |

---

### Licences

All hub repos above are BSD 2-Clause, except Advanced Notifications (https://github.com/Zakru/advanced-notifications,
MIT), which wasn't given its own section (it's a smaller Watchdog: inventory count / empty space conditions, groups).
Core RuneLite is BSD-2. If any code or data is borrowed (most likely Attack Timer Metronome's speed cases), keep
their copyright notice and credit them in the README.
