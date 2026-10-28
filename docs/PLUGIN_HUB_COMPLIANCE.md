# Plugin Hub & Jagex compliance

Brad's Multi-Tool is meant to be published on the RuneLite Plugin Hub, so every feature and every line of
code has to pass the Plugin Hub review. This file tracks the rules and where the plugin stands. Check it
before adding a feature, and update it when something changes.

Sources (re-check these before publishing, they change):
- Plugin Hub rules: https://github.com/runelite/plugin-hub (README, "Reviewing")
- Rejected / rolled-back features and forbidden code: https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- Jagex's third-party client guidelines: https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1

Last checked: 2026-10-28.

## Planned split

The multi-tool will also be published as separate plugins (Gemstone Crab, skilling helpers, skill tracker,
PvM HUD, Slayer, alerts...). Each part should stay self-contained so it can be lifted into its own plugin:
its own data files, overlay(s) and config section, talking to the rest only through small shared helpers
(voice alerts, chat messages, cursor tooltip).

## Code rules (from the Rejected-or-Rolled-Back-Features page)

| Rule | Status |
|---|---|
| Java only, package not `net.runelite` | OK (`com.bradsmultitool`) |
| No reflection | OK. Farming data is generated from RuneLite's source into `farming_patches.json` instead of reading Time Tracking's internal classes; axe/pickaxe animation lists are written out instead of scanned |
| No JNI/JNA, no running external programs, no downloading code | OK |
| No `Thread.sleep` / `Thread.interrupt` | OK. Voice clips chain on the clip's STOP event |
| No `Desktop.browse` / `LinkBrowser.open` (`LinkBrowser.browse` is allowed) | OK. Report an Issue uses `LinkBrowser.browse` |
| No `java.io` file APIs (resources via `getResourceAsStream` are fine) | OK |
| No `Runtime`, `KeyboardFocusManager`, non-OkHttp network clients | OK (no network use at all) |
| Don't create your own `Gson` / `OkHttpClient` (inject them) | OK. Plugin code uses the injected `Gson` (tests create their own, which isn't shipped) |
| No `Client.menuAction`, `runScript`, `hopToWorld` | OK |
| Don't modify other plugins' configs | OK (only reads the client's idle timeout) |
| Third-party dependencies need hash verification | OK, none |

## Feature rules (Jagex guidelines + rejected features)

| Feature | Status / precedent |
|---|---|
| PvM HUD, monster hover (HP, weakness, aggression to you) | OK. Information-only; aggression follows the game's documented rule |
| Buff / health / prayer / spec / poison / antifire alerts | OK. Notifications only, like core plugins |
| Idle logout warning, AFK alert | OK. Notifications only (core Idle Notifier / Logout Timer do the same); nothing prevents logout |
| Gemstone Crab HUD and radar | OK. Plugin Hub has gemstone-crab-timer |
| Slayer task card, missing item warning | OK |
| Skilling hover cards (trees, rocks, stalls, pickpocket, fishing, farming) | OK. Information from the wiki and visible game state |
| Resource timer pies (trees, MLM, calcified, Daeyalt, stars, fishing spots, shipwrecks) | OK. Each mirrors an existing Plugin Hub plugin (credited in README) |
| Farming patch hover | OK. Same information as the core Time Tracking plugin |
| Hunter: creature hover, trap pies, bird house hover + ready alert | OK. Core Hunter and Time Tracking plugins show trap timers and bird house times |
| Firemaking fire pies | OK. Plugin Hub has fire-timer |
| Runecraft altar hover, Agility course/shortcut hover, Cooking burn levels | OK. Wiki information; core Agility plugin knows the same shortcuts and courses |
| Coin pouch full, batch complete alerts | OK. Notifications only |
| Skill tracker goals and auto-pause | OK. Same as the core XP Tracker |
| Voice alerts (bundled clips) | OK |
| Skill tracker panel, level-up alerts | OK |
| Slayer card hotkey | OK (a keybind that toggles our own overlay) |

Never add: anything that clicks, types or acts for the player; menu entry removal based on conditions;
boss attack/prayer prediction for high-end PvM; hidden-information reveals; "AFK agility" helpers; PvP
level/skull indicators; anything that modifies or hides core RuneLite UI or other plugins.

## Open questions before publishing

- Plugin size: the bundled voice clips make the jar about 35 MB. The Plugin Hub may reject or question
  a jar this large. Options: fewer personas, shorter level-up clips, or lower bitrate. Check before
  submitting.
- `Executors.newSingleThreadExecutor()` for audio is not forbidden, but reviewers may prefer the
  injected `ScheduledExecutorService`.
- The skilling/farming data files are generated from the OSRS Wiki (CC BY-NC-SA) and RuneLite
  (BSD-2-Clause); both are credited in the README.
- `runelite-plugin.properties` and the build files need the Plugin Hub template layout at submission.
