# Rollout: from this repository to the Plugin Hub

Development and testing stay here (the launcher, the Toolbelt, `src/`). The Plugin Hub builds each plugin from its
own public GitHub repository at a commit named in `runelite/plugin-hub`. One command copies each plugin there and
prepares the Plugin Hub update: the launcher's **Dev** tab (or `Publish-Plugins.bat push` then `Publish-Plugins.bat hub`).

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
- The launcher's **Dev** tab runs `scripts/publish_plugins.py` (or `Publish-Plugins.bat push` / `hub` yourself):
  - **Check (dry run)**: what would change in each repository and the new versions, without pushing.
  - **Push to repos** (`push`): checks this repository is clean, pushed and `plugins/` is up to date; copies each
    ticked plugin's folder, exactly as committed, into its repository. A plugin that changed gets its next version
    (patch by default; pick minor or major in the tab), written into its `build.gradle`, a commit named
    "<plugin> <version>" listing what changed since its last push, a `v<version>` tag, and a new entry at the top
    of the repository's `CHANGELOG.md` ("## 1.0.3 (date)" and the same list: the titles of this repository's
    commits that changed that plugin's folder). Unchanged plugins are
    left alone. You can push as often as you like: players don't get anything until you publish.
  - **Publish to RuneLite** (`hub`): writes each plugin's Plugin Hub entry (its repository and the commit it's at
    now) and puts them on a branch of your `plugin-hub` fork; the tab then shows the link to open the pull request.

## Versions

Each plugin has its own version, kept in its own repository (its `build.gradle` and tags), so a fix to the Tick
Counter doesn't change the PvM/Slayer's version. The first push is 1.0.0. After that, only plugins with changes
get a new number: **patch** for fixes (1.0.0 to 1.0.1), **minor** for new features (1.1.0), **major** for big
changes (2.0.0). The version in this repository's `build.gradle` is only the starting one for a new plugin.

## One-time setup

1. On GitHub, make seven empty public repositories: `snuffys-toolbelt`, `snuffys-skilling-helper`, `snuffys-pvm-slayer`,
   `snuffys-gemcrab-tracker`, `snuffys-tick-counter`, `snuffys-planner`, `snuffys-alerts` (names as in `publish.json`;
   change them there if you pick others). Already made the first six? Just add `snuffys-planner`.
2. Fork `runelite/plugin-hub` to your account (`bburge14/plugin-hub`, as in `publish.json`).
3. On your PC: Python 3 (`py` on Windows) and git logged in to GitHub (the same login the launcher's updates use,
   with permission to push).
4. Launcher, **Dev** tab: **Check (dry run)** once to see it work, then **Push to repos**.

## Before the first submission (checklist)

- [ ] **Test round in game** of every plugin on its own (launcher: pick the separate plugins) and together
      (clashes between ours, shared goals, one voice for all). docs/TESTING.md is the checklist.
- [ ] **Open questions** in docs/PLUGIN_HUB_COMPLIANCE.md settled:
  - Click timing (Tick Counter): done, left out of the published plugins (kept in launcher builds).
  - Voice licences: done (Ryan and L2 Arctic removed).
  - Size: done as far as it goes: the published plugins ship four voices (see the sizes `split_plugins.py` prints).
- [ ] **READMEs**: each `plugins/<folder>/README.md` is what players see on the Plugin Hub page: check the text
      and add a screenshot or two.
- [ ] **Icons**: `assets/hub/` (48x72 at most; ours are 48x55). The Planner's logo (`assets/Planner.png`) is a
      stand-in copy of the Tick Counter's until its own art is in; run `python3 scripts/update_icons.py` after.
- [ ] Run `./gradlew test` and `python3 scripts/split_plugins.py --verify`; commit and push.

## Submitting

1. Dev tab: **Push to repos** (all seven), then **Publish to RuneLite**.
2. Open the pull request from the link it shows. Title like "Add Snuffy's plugins"; in the description, one line
   per plugin saying what it does, and mention they share settings/voices when installed together.
3. The reviewers comment on the pull request; fix things here, publish again (the same command updates the
   repositories and the pull request branch), and reply.
4. Once merged, the plugins appear in RuneLite's Plugin Hub within a few hours.

## Updating a live plugin

1. Develop and test here as usual (regenerate `plugins/`, commit and push).
2. Dev tab: **Push to repos** (pick patch, minor or major), then **Publish to RuneLite**.
3. Open the pull request it links. Updates to existing plugins are usually merged quickly, as long as the diff
   follows the rules.

## What to publish

- **The six separate plugins** (Skilling Helper, PvM/Slayer, GemCrab Tracker, Tick Counter, Planner, Alerts). Each
  stands alone, and they work together when installed side by side (shared goals, one voice, no double alerts).
- **The Toolbelt** too: its own plugin with everything in one, for players who'd rather install one thing. Say
  in the pull request that it's the all-in-one of the six (same code), so the reviewers don't take it for a
  copy. It's the biggest jar (about 19 MB); if size is a problem it can ship with fewer voices. Players should
  install either the Toolbelt or the separate ones, not both (its clash warning says so if they do).
- Start with one or two (e.g. Tick Counter and Alerts, the smallest) to learn the review process, then the rest.
