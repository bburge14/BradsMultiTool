# Rollout: from this repository to the Plugin Hub

Development and testing stay here (the launcher, the Toolbelt, `src/`). The Plugin Hub builds each plugin from its
own public GitHub repository at a commit named in `runelite/plugin-hub`. One command copies each plugin there and
prepares the Plugin Hub update: `Publish-Plugins.bat` (or `python3 scripts/publish_plugins.py`).

## How it fits together

```
this repo (develop + test)            one repo per plugin (built by the Plugin Hub)      runelite/plugin-hub
src/  --split_plugins.py-->  plugins/skilling-helper/  --publish-->  bburge14/snuffys-skilling-helper  <--commit=--  plugins/snuffys-skilling-helper
                             plugins/pvm-slayer/       --publish-->  bburge14/snuffys-pvm-slayer        <--commit=--  plugins/snuffys-pvm-slayer
                             ...
```

- `plugins/<folder>` is generated from `src/` and committed (run `python3 scripts/split_plugins.py --verify`
  after changes, as now). Each folder is already a complete plugin repository: build files, `runelite-plugin.properties`,
  icon, licence, README.
- `publish.json` says which repository each folder goes to (`repo`), its Plugin Hub file name (`hubName`) and
  whether it's published (`publish`).
- `scripts/publish_plugins.py` (Windows: `Publish-Plugins.bat`):
  1. checks the working tree is clean, pushed, and `plugins/` matches `src/`;
  2. for each plugin: copies its folder exactly as committed into its repository, commits
     ("Update from BradsMultiTool <commit>: <message>") and pushes (nothing is pushed if nothing changed);
  3. writes the Plugin Hub entries to `releases/plugin-hub/<name>` (`repository=` and `commit=`);
  4. puts those entries on a branch of your `plugin-hub` fork and prints the link to open the pull request.
  `--dry-run` does it all without pushing; name folders to publish only some (`Publish-Plugins.bat pvm-slayer`).

## One-time setup

1. On GitHub, make six empty public repositories: `snuffys-toolbelt`, `snuffys-skilling-helper`, `snuffys-pvm-slayer`,
   `snuffys-gemcrab-tracker`, `snuffys-tick-counter`, `snuffys-alerts` (names as in `publish.json`; change them
   there if you pick others).
2. Fork `runelite/plugin-hub` to your account (`bburge14/plugin-hub`, as in `publish.json`).
3. On your PC: Python 3 (`py` on Windows) and git logged in to GitHub (the same login the launcher's updates use,
   with permission to push).
4. Run `Publish-Plugins.bat --dry-run` once to see it work, then `Publish-Plugins.bat`.

## Before the first submission (checklist)

- [ ] **Test round in game** of every plugin on its own (launcher: pick the separate plugins) and together
      (clashes between ours, shared goals, one voice for all). docs/TESTING.md is the checklist.
- [ ] **Open questions** in docs/PLUGIN_HUB_COMPLIANCE.md settled:
  - Click timing (Tick Counter): ask the reviewers, or leave it out.
  - Voice licences: Ryan (CC BY-NC-SA) and L2 Arctic (CC BY-NC) — ask, or drop those two voices from the
    published plugins.
  - Size: PvM/Slayer is about 12 MB and the Skilling Helper about 7 MB, almost all voice clips. The reviewers may
    ask for smaller; the fix is fewer voices per plugin (e.g. the four standard ones).
- [ ] **Version**: set `version` in `build.gradle` (1.0.0 for the first release) and regenerate.
- [ ] **READMEs**: each `plugins/<folder>/README.md` is what players see on the Plugin Hub page: check the text
      and add a screenshot or two.
- [ ] **Icons**: `assets/hub/` (48x72 at most; ours are 48x55).
- [ ] Run `./gradlew test` and `python3 scripts/split_plugins.py --verify`; commit and push.

## Submitting

1. `Publish-Plugins.bat` (all six).
2. Open the pull request from the link it prints. Title like "Add Snuffy's plugins"; in the description, one line
   per plugin saying what it does, and mention they share settings/voices when installed together.
3. The reviewers comment on the pull request; fix things here, publish again (the same command updates the
   repositories and the pull request branch), and reply.
4. Once merged, the plugins appear in RuneLite's Plugin Hub within a few hours.

## Updating a live plugin

1. Develop and test here as usual; bump `version` in `build.gradle`; regenerate `plugins/`; commit and push.
2. `Publish-Plugins.bat` (or just the plugins that changed).
3. Open the pull request it links. Updates to existing plugins are usually merged quickly, as long as the diff
   follows the rules.

## What to publish

- **The five separate plugins** (Skilling Helper, PvM/Slayer, GemCrab Tracker, Tick Counter, Alerts). Each
  stands alone, and they work together when installed side by side (shared goals, one voice, no double alerts).
- **The Toolbelt** too: its own plugin with everything in one, for players who'd rather install one thing. Say
  in the pull request that it's the all-in-one of the five (same code), so the reviewers don't take it for a
  copy. It's the biggest jar (about 19 MB); if size is a problem it can ship with fewer voices. Players should
  install either the Toolbelt or the separate ones, not both (its clash warning says so if they do).
- Start with one or two (e.g. Tick Counter and Alerts, the smallest) to learn the review process, then the rest.
