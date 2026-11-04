#!/usr/bin/env python3
"""Make each of Snuffy's plugins its own folder under plugins/, and build their release jars.

Usage:
    python3 scripts/split_plugins.py              # (re)write plugins/<folder>/ for every plugin
    python3 scripts/split_plugins.py --verify     # ...and check each compiles on its own
    python3 scripts/split_plugins.py --build      # ...and build each one's jar into releases/
    python3 scripts/split_plugins.py --check      # are the plugins/ folders up to date? (changes nothing)
    python3 scripts/split_plugins.py pvm          # just one (multitool, skilltracker, pvm, gemcrab, tickcounter, planner, voices)

The code is written once, in src/ (which is also Snuffy's Toolbelt, the all-in-one). Each plugin's folder gets
only what that plugin uses: starting from its plugin class, every class it refers to (and so on), the data
files those classes load, and its voice clip folders (docs/DEVELOPMENT.md "Planned: separate plugins"). Its
package is renamed (com.bradsmultitool -> com.snuffys.<plugin>) and it gets its own build files (the Plugin
Hub's layout), runelite-plugin.properties, icon, licence and README, so each folder builds on its own. The
folders are committed: regenerate them after changing src/, and don't edit them by hand.
"""
import json
import hashlib
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "src", "main", "java", "com", "bradsmultitool")
RES = os.path.join(ROOT, "src", "main", "resources", "com", "bradsmultitool")
OUT = os.path.join(ROOT, "plugins")
RELEASES = os.path.join(ROOT, "releases")
OLD_PACKAGE = "com.bradsmultitool"

# Features only in builds from src/ (the launcher), not on the Plugin Hub: their code sits between
# "// <click-timing>" and "// </click-timing>" lines, which are left out here, with these words in descriptions
DEV_ONLY_BLOCK = re.compile(r"^[ \t]*// <click-timing>\n.*?^[ \t]*// </click-timing>\n", re.S | re.M)
DEV_ONLY_TEXT = {
    ", an attack timer and click timing for tick skilling": " and an attack timer",
    ", the metronome and click timing.": " and the metronome.",
    "the metronome and click timing. */": "and the metronome. */",
    " Raise it if Click Timing says your clicks on the sound are late, lower it if they're early.": "",
    "plays the metronome, and times your\n * clicks against the tick (how long after it each landed, a ding or a dun). Only reads your clicks and shows or\n"
    " * sounds the ticks: it never clicks, changes a menu or does anything in the game.":
        "and plays the metronome. Only shows and\n * sounds the ticks: it never clicks, changes a menu or does anything in the game.",
    " snaps back when the next tick comes, with the ideal click zone (green), the late part (orange)\n"
    " * and your last click marked on it. Under it, with Click\n * Timing on, a summary of your last clicks.":
        " snaps back when the next tick comes.",
    "the tick count, and with Click Timing on, how long after the tick each click\n"
    " * landed, rising and fading like an XP drop (green in the ideal zone, red late).": "the tick count.",
    "the metronome, click timing and its hotkeys.": "the metronome and its hotkeys.",
    "Volume of the metronome and the click sounds (0-100)": "Volume of the metronome (0-100)",
    # (the published plugins ship four voices)
    " Arctic: a Scottish man. Wise Old Man, TzHaar, Ghost: characters.": "",
}


def dev_only_removed(text):
    """The text without its dev-only blocks and words, and the config methods those blocks declared."""
    removed = set()
    for block in DEV_ONLY_BLOCK.findall(text):
        removed.update(re.findall(r"default [\w.<>]+ (\w+)\(\)", block))
    text = DEV_ONLY_BLOCK.sub("", text)
    for old, new in DEV_ONLY_TEXT.items():
        text = text.replace(old, new)
    return text, removed


# The voices the published plugins ship (to keep them small for the Plugin Hub); builds from src/ keep every voice.
# The others' VoicePersona entries are left out of the generated code too, so the settings only offer these.
PUBLISHED_VOICES = ["amy", "cori", "alan", "john"]

# Each plugin: its plugin class, package, project name, folder under plugins/, and which voice clip folders it ships
PLUGINS = {
    "multitool": {
        "class": "BradsMultiToolPlugin", "package": "com.snuffys.multitool", "name": "snuffys-toolbelt", "folder": "toolbelt",
        "voices": ["skilling", "pvm", "potions", "gemcrab", "planner", "shared"],
    },
    "skilltracker": {
        "class": "SkillTrackerPlugin", "package": "com.snuffys.skilltracker", "name": "snuffys-skilling-helper", "folder": "skilling-helper",
        "voices": ["skilling", "shared"],
    },
    "pvm": {
        "class": "PvmPlugin", "package": "com.snuffys.pvm", "name": "snuffys-pvm-slayer", "folder": "pvm-slayer",
        "voices": ["pvm", "potions", "shared"],
    },
    "gemcrab": {
        "class": "GemCrabPlugin", "package": "com.snuffys.gemcrab", "name": "snuffys-gemcrab-tracker", "folder": "gemcrab-tracker",
        "voices": ["gemcrab", "potions", "shared"],
    },
    "tickcounter": {
        "class": "TickCounterPlugin", "package": "com.snuffys.tickcounter", "name": "snuffys-tick-counter", "folder": "tick-counter",
        "voices": [],
    },
    "planner": {
        "class": "PlannerPlugin", "package": "com.snuffys.planner", "name": "snuffys-planner", "folder": "planner",
        "voices": ["planner", "shared"],
    },
    "voices": {
        "class": "VoicesPlugin", "package": "com.snuffys.voices", "name": "snuffys-alerts", "folder": "alerts",
        "voices": ["shared"],
    },
}
AUTHOR = "SgtSnufffy"

BUILD_GRADLE = """plugins {
    id 'java'
}

repositories {
    mavenLocal()
    maven {
        url = 'https://repo.runelite.net'
    }
    mavenCentral()
}

def runeLiteVersion = 'latest.release'

dependencies {
    compileOnly group: 'net.runelite', name: 'client', version: runeLiteVersion
    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
}

group = '%(package)s'
version = '%(version)s'

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release.set(11)
}
"""


README = """# %(title)s

%(description)s.

%(features)s
%(screenshots)s## Installing

In RuneLite, open the **Plugin Hub** (the plug icon at the bottom of the configuration panel), search for
**%(title)s** and click **Install**. Its settings are under its name in the configuration panel, and **Open the
Guide** in them links to the full feature guide.

%(family)s
## Source and issues

Made by SgtSnufffy. Source: [bburge14/bradsmultitool](https://github.com/bburge14/bradsmultitool); ideas and
problems: [open an issue](https://github.com/bburge14/bradsmultitool/issues).
"""

# The features each plugin's README lists (the Plugin Hub page players see); the Toolbelt lists every plugin's
FEATURES = {
    "skilltracker": [
        "**XP tracker** in the sidebar: XP/hr, time to level, actions left, items gathered per hour, auto-pause, a pinned "
        "skill on screen, and it keeps your session through a quick restart",
        "**Goals** with the wiki's training method for your level, outlined in the world, banked XP and materials value, "
        "Track for goal, goal bars on the Skills tab, and skill plans exported from Snuffy's Planner (the milestone "
        "you're on, with its own bar and method)",
        "**Hover cards** for trees, rocks, fishing spots, stalls, pickpocketing, farming patches, Hunter, Runecraft, "
        "Agility, Cooking, Smithing and Sailing",
        "**Resource timers**: tree, rock, fishing spot, trap, fire and shipwreck timers",
        "**Hunters' Rumours** card: the creature, catches toward the rare part and where to find it",
        "**Agility**: course and Brimhaven arena cards, the quickest route to the tagged pillar, one-obstacle training",
        "**Minigames**: Wintertodt, Tempoross and Guardians of the Rift cards and alerts",
        "**Voice alerts**: inventory full, resource gone, Forestry events, bird nests, Motherlode sack full, boat "
        "health, level-ups and more",
    ],
    "pvm": [
        "**PvM HUD**: your target's health, weaknesses, what you're dealing and loot per hour",
        "**Monster hover card**: hitpoints, defences, weakness, max hit and attack speed",
        "**Buff bar**: potions, antifire, stamina, overload, vengeance, freezes and more, with low and expired alerts",
        "**Health and prayer warnings**, special attack ready, cannon, ammo and rune alerts, a death alert with a "
        "gravestone timer",
        "**Slayer card**: task, kills left and kill rate, where to go, points and streak with milestone warnings, "
        "supplies used, kill goals; task monster outlines and finish-off markers",
    ],
    "gemcrab": [
        "**Gemstone Crab HUD** with the crab's health and the burrow countdown",
        "**Radar** pointing to the next cove when the crab burrows",
        "**Gear check**, outlines of the crab, its shell and the tunnels, and voice alerts",
    ],
    "tickcounter": [
        "**Tick count** over your head, as numbered tick boxes or a smooth tick bar",
        "**Metronome** with a play-ahead option, and hotkeys to reset, hide and change the cycle",
        "**Attack timer** that follows your weapon's speed, counting up or down to your next attack",
    ],
    "voices": [
        "**One voice and volume** for all of Snuffy's plugins (4 voices)",
        "**Idle alerts**: AFK, about to be logged out, six-hour logout",
        "**Run energy** low and restored alerts",
        "**Clue drops** from monsters and skilling",
    ],
    "planner": [
        "**Today**: your own dailies, ticked off by you and due again at the daily reset",
        "**Weekly**: your own weeklies; adding either can start from a preset task (name, where and picture filled in)",
        "**Timers**: every farming patch, compost bin and Hespori you've been to, bird houses and timers of your own, "
        "with ready alerts",
        "**Slayer**: your task, kills left, points and streak",
        "**Drops**: each monster or chest tracked on its own: kills, loot with the drop rates you've seen, Grand Exchange "
        "value, this session beside all time, and your latest drops",
        "**Notes & to-do**: a to-do list and a notes box for each account",
        "**Calculator**: pick a skill and a target (your goal, or the next level): every action with its XP (with the outfit, diary and altar bonuses you tick), how many to go, the time where the wiki's training guides give an XP rate, what the materials cost and the products are worth at Grand Exchange prices, what you own of it, and Set as goal (members-only actions left out on free worlds); Banked view shows the XP, level and value your bank gets you; Plan view makes a plan of milestones each with its own method (from the wiki's suggestions or by hand) with its XP, time and cost, and exports it to the Skilling Helper",
    ],
}
FEATURE_TITLES = {"skilltracker": "Skilling Helper", "pvm": "PvM/Slayer", "gemcrab": "GemCrab Tracker",
                  "tickcounter": "Tick Counter", "planner": "Planner", "voices": "Alerts"}


def features(plugin_id):
    """The README's feature list: the plugin's own, or for the Toolbelt every plugin's under its name."""
    if plugin_id != "multitool":
        return "## Features\n\n" + "".join("- %s\n" % f for f in FEATURES[plugin_id]) + "\n"
    out = "## Features\n\nEverything in Snuffy's six separate plugins, in one plugin:\n\n"
    for pid, title in FEATURE_TITLES.items():
        out += "**Snuffy's %s**\n\n" % title + "".join("- %s\n" % f for f in FEATURES[pid]) + "\n"
    return out


def family(plugin_id):
    if plugin_id == "multitool":
        return ("This is all of Snuffy's plugins together. Install either the Toolbelt or the separate ones you want "
                "(Skilling Helper, PvM/Slayer, GemCrab Tracker, Tick Counter, Planner, Alerts), not both.\n")
    return ("It's one of Snuffy's plugins: install just the ones you want (Skilling Helper, PvM/Slayer, GemCrab Tracker, "
            "Tick Counter, Planner, and the optional Alerts for one voice across them); they work together. **Snuffy's Toolbelt** "
            "has all of them in one; don't install it together with the separate ones.\n")



def version():
    """The release version, from this repository's build.gradle."""
    with open(os.path.join(ROOT, "build.gradle"), encoding="utf-8") as f:
        return re.search(r"version = '([^']+)'", f.read()).group(1)


# Strings, character literals and comments, matched in one pass so a "//" inside a string ("https://...") isn't
# taken for a comment, nor a quote inside a comment for a string
TOKENS = re.compile(r'"""(?:.|\n)*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\])+\'|/\*.*?\*/|//[^\n]*', re.S)


def strip_comments_and_strings(text):
    return TOKENS.sub(lambda m: '""' if m.group(0)[0] in "\"'" else " ", text)


def classes():
    return {f[:-5] for f in os.listdir(SRC) if f.endswith(".java")}


def closure(start, known):
    """Every class reachable from start through references in code (comments and strings don't count)."""
    seen, todo = set(), [start]
    while todo:
        name = todo.pop()
        if name in seen:
            continue
        seen.add(name)
        with open(os.path.join(SRC, name + ".java"), encoding="utf-8") as f:
            code = strip_comments_and_strings(f.read())
        for other in set(re.findall(r"\b([A-Z][A-Za-z0-9_]*)\b", code)) & known:
            if other not in seen:
                todo.append(other)
    return seen


def data_files(names):
    """The data files the included classes load (their RESOURCE constants), plus the Slayer pictures and the sidebar
    tab icons they load (icons/<name>.png)."""
    files = set()
    for name in names:
        with open(os.path.join(SRC, name + ".java"), encoding="utf-8") as f:
            text = f.read()
        files.update(re.findall(r'RESOURCE = "([^"]+)"', text))
        if 'loadImageResource(SlayerData.class, "slayer/"' in text:
            files.add("slayer/")
        files.update(re.findall(r'loadImageResource\(\w+\.class, "(icons/[\w.-]+)"\)', text))
    return files


def descriptor(name):
    """name, description and tags from the plugin class's @PluginDescriptor."""
    with open(os.path.join(SRC, name + ".java"), encoding="utf-8") as f:
        text = f.read()
    text = dev_only_removed(text)[0]
    title = re.search(r'name = "([^"]+)"', text).group(1)
    description = re.search(r'description = "([^"]+)"', text).group(1)
    tags = re.findall(r'"([^"]+)"', re.search(r"tags = \{([^}]*)\}", text).group(1))
    return title, description, tags


def published_personas(text):
    """VoiceAlertService with only the published voices (and Random) in its VoicePersona enum."""
    out = []
    in_enum = False
    for line in text.split("\n"):
        if "public enum VoicePersona" in line:
            in_enum = True
        elif in_enum:
            constant = re.match(r"\s+([A-Z_]+)\(\"", line)
            if constant and constant.group(1) != "RANDOM" and constant.group(1).lower() not in PUBLISHED_VOICES:
                continue
            if re.match(r"\s+// Character voices", line):
                continue
            if constant and constant.group(1) == "RANDOM":
                in_enum = False
        out.append(line)
    return "\n".join(out)


SCREENSHOTS = os.path.join(ROOT, "assets", "screenshots")


def copy_screenshots(plugin_id, out):
    """The plugin's screenshots (assets/screenshots/<id>/*.png; the Toolbelt shows every plugin's) copied to
    screenshots/ in its folder, as the README's Screenshots section. A file's name is its caption: "01-planner-
    timers.png" is shown first, as "Planner timers"."""
    ids = [plugin_id] if plugin_id != "multitool" else ["multitool"] + [p for p in PLUGINS if p != "multitool"]
    lines = []
    for source_id in ids:
        folder = os.path.join(SCREENSHOTS, source_id)
        if not os.path.isdir(folder):
            continue
        for name in sorted(f for f in os.listdir(folder) if f.lower().endswith(".png")):
            os.makedirs(os.path.join(out, "screenshots"), exist_ok=True)
            target = source_id + "-" + name if plugin_id == "multitool" else name
            shutil.copy2(os.path.join(folder, name), os.path.join(out, "screenshots", target))
            caption = re.sub(r"^\d+[-_ ]*", "", os.path.splitext(name)[0]).replace("-", " ").replace("_", " ")
            caption = caption[:1].upper() + caption[1:]
            lines.append("![%s](screenshots/%s)" % (caption, target))
    return "## Screenshots\n\n" + "\n".join(lines) + "\n\n" if lines else ""


def build(plugin_id, out_root=OUT, quiet=False):
    spec = PLUGINS[plugin_id]
    known = classes()
    names = closure(spec["class"], known)
    out = os.path.join(out_root, spec["folder"])
    shutil.rmtree(out, ignore_errors=True)
    package_dir = spec["package"].replace(".", "/")
    java_out = os.path.join(out, "src", "main", "java", package_dir)
    res_out = os.path.join(out, "src", "main", "resources", package_dir)
    os.makedirs(java_out)
    os.makedirs(res_out)
    sources = {}
    gone = set()
    for name in sorted(names):
        with open(os.path.join(SRC, name + ".java"), encoding="utf-8") as f:
            sources[name], removed = dev_only_removed(f.read())
        gone |= removed
    for name in sorted(names):
        text = sources[name]
        # The generated defaults list every setting: not the ones left out
        if name.endswith("ConfigDefaults") and gone:
            text = "\n".join(line for line in text.split("\n")
                             if not any("config.%s()" % method in line for method in gone))
        if name == "VoiceAlertService":
            text = published_personas(text)
        text = text.replace("package %s;" % OLD_PACKAGE, "package %s;" % spec["package"])
        text = text.replace(OLD_PACKAGE + ".", spec["package"] + ".")
        with open(os.path.join(java_out, name + ".java"), "w", encoding="utf-8") as f:
            f.write(text)
    for data in sorted(data_files(names)):
        source = os.path.join(RES, data)
        if data.endswith("/"):
            shutil.copytree(source, os.path.join(res_out, data))
        else:
            os.makedirs(os.path.dirname(os.path.join(res_out, data)), exist_ok=True)
            shutil.copy2(source, os.path.join(res_out, data))
    # Voice clips: voices/<persona>/<folder>/ for this plugin's folders
    voices = os.path.join(RES, "voices")
    if "VoiceAlertService" in names:
        for persona in sorted(p for p in os.listdir(voices) if p in PUBLISHED_VOICES):
            for folder in spec["voices"]:
                source = os.path.join(voices, persona, folder)
                if os.path.isdir(source):
                    shutil.copytree(source, os.path.join(res_out, "voices", persona, folder))
    title, description, tags = descriptor(spec["class"])
    with open(os.path.join(out, "runelite-plugin.properties"), "w", encoding="utf-8") as f:
        # build=standard: the Plugin Hub builds it with its standard build (ours only needs RuneLite and Lombok)
        f.write("displayName=%s\nauthor=%s\ndescription=%s\ntags=%s\nplugins=%s.%s\nbuild=standard\n" % (
            title, AUTHOR, description, ",".join(tags), spec["package"], spec["class"]))
    with open(os.path.join(out, "build.gradle"), "w", encoding="utf-8") as f:
        f.write(BUILD_GRADLE % dict(spec, version=version()))
    with open(os.path.join(out, "settings.gradle"), "w", encoding="utf-8") as f:
        f.write("rootProject.name = '%s'\n" % spec["name"])
    # The Plugin Hub shows icon.png from the root of what it builds (made by update_icons.py)
    shutil.copy2(os.path.join(ROOT, "assets", "hub", plugin_id + ".png"), os.path.join(out, "icon.png"))
    # Only what the Plugin Hub needs to build it: licence, Gradle wrapper, and the voice credits if it has voices
    ships_voices = "VoiceAlertService" in names and spec["voices"]
    for keep in ("LICENSE", "gradlew", "gradlew.bat") + (("VOICE_CREDITS.md",) if ships_voices else ()):
        shutil.copy2(os.path.join(ROOT, keep), os.path.join(out, keep))
    shutil.copytree(os.path.join(ROOT, "gradle"), os.path.join(out, "gradle"))
    with open(os.path.join(out, ".gitignore"), "w", encoding="utf-8") as f:
        f.write(".gradle/\nbuild/\n.idea/\n*.iml\nout/\n")
    shots = copy_screenshots(plugin_id, out)
    with open(os.path.join(out, "README.md"), "w", encoding="utf-8") as f:
        f.write(README % {"title": title, "description": description, "features": features(plugin_id),
                          "family": family(plugin_id), "screenshots": shots})
    if not quiet:
        print("%-12s %3d classes, %s, voices: %s" % (plugin_id, len(names), ", ".join(sorted(data_files(names))) or "no data",
                                                  "+".join(spec["voices"]) if "VoiceAlertService" in names else "none"))
    return out


def gradle(out, *tasks):
    """Runs Gradle tasks in a plugin's folder, with this repository's Gradle."""
    result = subprocess.run([os.path.join(ROOT, "gradlew"), "-q", "-p", out] + list(tasks), capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(result.stdout + result.stderr)
        raise SystemExit("%s: %s failed" % (os.path.basename(out), " ".join(tasks)))


def release(plugin_id, out):
    """Builds the plugin's jar and copies it to releases/<name>-<version>.jar."""
    gradle(out, "clean", "jar")
    spec = PLUGINS[plugin_id]
    libs = os.path.join(out, "build", "libs")
    jar = [f for f in os.listdir(libs) if f.endswith(".jar")][0]
    os.makedirs(RELEASES, exist_ok=True)
    target = os.path.join(RELEASES, "%s-%s.jar" % (spec["name"], version()))
    shutil.copy2(os.path.join(libs, jar), target)
    print("%-12s %s" % (plugin_id, os.path.relpath(target, ROOT)))


def same_tree(a, b):
    """Whether two folders hold the same files (ignoring build output)."""
    def files(root):
        out = {}
        for d, dirs, names in os.walk(root):
            dirs[:] = [x for x in dirs if x not in ("build", ".gradle")]
            for n in names:
                path = os.path.join(d, n)
                with open(path, "rb") as f:
                    out[os.path.relpath(path, root)] = f.read()
        return out
    return files(a) == files(b)


def check(wanted):
    """Regenerates into a scratch folder and compares with plugins/; lists any that are out of date."""
    import tempfile
    stale = []
    with tempfile.TemporaryDirectory() as scratch:
        for plugin_id in wanted:
            fresh = build(plugin_id, scratch, quiet=True)
            if not same_tree(fresh, os.path.join(OUT, PLUGINS[plugin_id]["folder"])):
                stale.append(PLUGINS[plugin_id]["folder"])
    if stale:
        raise SystemExit("Out of date: plugins/%s (run python3 scripts/split_plugins.py)" % ", plugins/".join(stale))
    print("plugins/ is up to date")


# ---- The friends launcher: Snuffy's Launcher without the Dev tab, in a repository of its own, that downloads the
# plugins' own repositories (not this one), builds them and starts RuneLite with them ----

# The launcher's keys for each plugin (what .launch-choice holds), as the dev launcher uses them
DISPLAY = {"multitool": "Snuffy's Toolbelt", "skilltracker": "Skilling Helper", "pvm": "PvM/Slayer",
           "gemcrab": "GemCrab Tracker", "tickcounter": "Tick Counter", "planner": "Planner", "voices": "Alerts"}

LAUNCH_KEYS = {"multitool": "toolbelt", "skilltracker": "skilling", "pvm": "pvm", "gemcrab": "gemcrab",
               "tickcounter": "tick", "planner": "planner", "voices": "alerts"}

LAUNCHER_BAT = r"""@echo off
rem Opens Snuffy's Launcher (scripts\launcher.ps1): it downloads and updates Snuffy's plugins, checks your setup, lets
rem you pick which plugins to load and starts RuneLite with them. The desktop shortcut it makes opens it with no window.
cd /d "%~dp0"
rem A script that can't be read (a broken update) opens its errors in Notepad instead of nothing happening. (Its path
rem goes in through a variable: a folder named with an apostrophe, "Snuffy's Launcher", can't break the check.)
set "SNUFFYS_SCRIPT=%~dp0scripts\launcher.ps1"
set "SNUFFYS_ERRORS=%TEMP%\snuffys-launcher-error.log"
if exist "%SNUFFYS_ERRORS%" del "%SNUFFYS_ERRORS%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile($env:SNUFFYS_SCRIPT, [ref]$null, [ref]$e); if ($e) { ($e | Out-String) | Set-Content $env:SNUFFYS_ERRORS }"
if exist "%SNUFFYS_ERRORS%" (
  start "" notepad "%SNUFFYS_ERRORS%"
  exit /b 1
)
start "" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%SNUFFYS_SCRIPT%"
"""

LAUNCHER_VBS = r'''' Opens Snuffy's Launcher with no window at all (scripts\launcher.ps1, hidden): double-click this.
' If the launcher's script can't be read (a broken update) its errors open in Notepad instead. Its path goes to the
' check through a variable, so a folder named with an apostrophe ("Snuffy's Launcher") can't break it; and Notepad only
' opens when there are errors to show (if the check itself fails, it opens the launcher anyway).
Set shell = CreateObject("WScript.Shell")
Set files = CreateObject("Scripting.FileSystemObject")
folder = files.GetParentFolderName(WScript.ScriptFullName)
script = folder & "\scripts\launcher.ps1"
errors = shell.ExpandEnvironmentStrings("%TEMP%") & "\snuffys-launcher-error.log"
shell.Environment("Process")("SNUFFYS_SCRIPT") = script
shell.Environment("Process")("SNUFFYS_ERRORS") = errors
If files.FileExists(errors) Then files.DeleteFile errors, True
ps = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden "
check = ps & "-Command ""$e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile($env:SNUFFYS_SCRIPT, " & _
    "[ref]$null, [ref]$e); if ($e) { ($e | Out-String) | Set-Content $env:SNUFFYS_ERRORS }"""
shell.Run check, 0, True
If files.FileExists(errors) Then
    If files.GetFile(errors).Size > 0 Then
        shell.Run "notepad.exe """ & errors & """", 1, False
        WScript.Quit 1
    End If
End If
shell.Run ps & "-File """ & script & """", 0, False
'''

RUNNER_SETTINGS = """plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'snuffys-runner'
"""

RUNNER_BUILD = """// Builds the plugins the launcher downloaded (../plugins/<name>, each its own repository) together and starts a RuneLite
// developer client with the ones chosen: gradlew runSnuffys -Pclasses=com.snuffys.planner.PlannerPlugin,...
plugins {
    id 'java'
}

repositories {
    mavenLocal()
    maven {
        url = 'https://repo.runelite.net'
        content {
            includeGroupByRegex("net\\\\.runelite.*")
        }
    }
    mavenCentral()
}

def runeLiteVersion = 'latest.release'

dependencies {
    implementation group: 'net.runelite', name: 'client', version: runeLiteVersion
    implementation group: 'net.runelite', name: 'jshell', version: runeLiteVersion
    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
}

// Every downloaded plugin's code and resources (each in its own package, so they build side by side)
def pluginsDir = file('../plugins')
sourceSets {
    main {
        java {
            srcDir 'src/main/java'
            if (pluginsDir.isDirectory()) {
                pluginsDir.eachDir { srcDir new File(it, 'src/main/java') }
            }
        }
        resources {
            if (pluginsDir.isDirectory()) {
                pluginsDir.eachDir { srcDir new File(it, 'src/main/resources') }
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType(JavaExec).configureEach {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release.set(11)
}

tasks.register('runSnuffys', JavaExec) {
    group = 'application'
    description = "Run RuneLite with Snuffy's plugins (-Pclasses=their plugin classes)"
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'com.snuffys.launcher.SnuffysRunner'
    args = ['--developer-mode']
    // (RuneLite's developer client won't load plugins without assertions on)
    jvmArgs '-ea'
    systemProperty 'snuffys.classes', project.findProperty('classes') ?: ''
}
"""

RUNNER_MAIN = """package com.snuffys.launcher;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Starts RuneLite with Snuffy's plugins loaded (the launcher passes their classes in snuffys.classes, comma separated).
 */
public class SnuffysRunner {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        List<Class<? extends Plugin>> plugins = new ArrayList<>();
        for (String name : System.getProperty("snuffys.classes", "").split(",")) {
            if (!name.trim().isEmpty()) {
                plugins.add((Class<? extends Plugin>) Class.forName(name.trim()));
            }
        }
        System.out.println("Loading Snuffy's: " + plugins);
        ExternalPluginManager.loadBuiltin(plugins.toArray(new Class[0]));
        RuneLite.main(args);
    }
}
"""

LAUNCHER_README = """# Snuffy's Launcher

Runs [Snuffy's plugins](https://github.com/bburge14/snuffys-toolbelt) in RuneLite before (or alongside) the Plugin Hub:
it downloads each plugin, keeps them up to date, and starts RuneLite with the ones you pick. No commands to type and
nothing else to install: it gets what it needs itself.

## Getting it

**Download [Snuffys-Launcher-Setup.exe](https://github.com/bburge14/snuffys-launcher/releases/latest/download/Snuffys-Launcher-Setup.exe)
and double-click it.** It installs Snuffy's Launcher in your own folder (no admin rights needed), puts it on your
desktop and opens it. ([All releases](https://github.com/bburge14/snuffys-launcher/releases).)

Your browser and Windows may warn about it, because it's new and not code-signed (paid certificates are what make
that go away): in **Edge**, "isn't commonly downloaded": hover the download, click **...** > **Keep** > **Show more** >
**Keep anyway**; in **Chrome**, click **Keep**. When you open it, if Windows says "Windows protected your PC": **More
info** > **Run anyway**. You only need to do this once: the launcher updates itself after that.

Or without the installer: download the [zip](https://github.com/bburge14/snuffys-launcher/archive/refs/heads/main.zip),
unzip it where you'd like to keep it, double-click **Snuffy's Launcher** in the folder, and click **Make a desktop
shortcut** at the bottom of its window.

You need **Windows** and **RuneLite** ([runelite.net](https://runelite.net/)). The first time, the launcher downloads
the plugins and, if your PC has no Java, a Java of its own into its folder (about 190 MB, once). Nothing is installed
in Windows.

## Using it

- Work down the **Setup** rows until they're ticked: RuneLite found, session saving turned on (the launcher can do it
  for you), a Jagex session saved (log in once through the Jagex Launcher), Java.
- Pick **Snuffy's Toolbelt** (everything in one) or the separate plugins you want (the ✕ beside one removes it; + adds
  it back), and press **Launch RuneLite**. The first launch downloads RuneLite and builds the plugins, so it takes a
  few minutes.
- It updates the plugins (and itself) on its own while it's open. When the chip under its name says RuneLite is
  behind, press **Relaunch** to get the update.

Your settings and plans are kept in RuneLite, per account, as with any plugin.
"""

LAUNCHER_IGNORE = """plugins/
runner/build/
runner/.gradle/
.gradle/
.launch-choice
.launch-runelite
.running-commit
.hub-seen
.removed-plugins
.plugin-state.json
.update-result.json
.java-download.txt
java/
"""


SETUP_EXE = "Snuffys-Launcher-Setup.exe"


def compile_setup(source, target):
    """Builds the installer with mcs (Mono) or csc (.NET Framework, in every Windows); leaves the old one if neither."""
    icon = os.path.join(ROOT, "assets", "launcher.ico")
    refs = ["System.Windows.Forms.dll", "System.Drawing.dll"]
    csc = os.path.join(os.environ.get("WINDIR", "C:\\Windows"), "Microsoft.NET", "Framework64", "v4.0.30319", "csc.exe")
    if shutil.which("mcs"):
        command = ["mcs", "-langversion:5", "-target:winexe", "-out:" + target] + ["-r:" + ref for ref in refs]
    elif os.path.exists(csc):
        command = [csc, "/nologo", "/target:winexe", "/out:" + target] + ["/r:" + ref for ref in refs]
    else:
        return
    if os.path.exists(icon):
        command.append(("-" if command[0] == "mcs" else "/") + "win32icon:" + icon)
    command.append(source)
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL)


def build_launcher(publish_path=os.path.join(ROOT, "publish.json")):
    """plugins/launcher: the friends launcher (pushed to its own repository like the plugins)."""
    out = os.path.join(OUT, "launcher")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(os.path.join(out, "scripts"))
    with open(os.path.join(out, "Launch-Snuffys.bat"), "w", newline="\r\n") as f:
        f.write(LAUNCHER_BAT)
    for name in ("launcher.ps1", "update-plugins.ps1", "get-java.ps1"):
        shutil.copy2(os.path.join(ROOT, "scripts", name), os.path.join(out, "scripts", name))
    # The double-click that opens it with no window at all (a .bat always flashes a console)
    with open(os.path.join(out, "Snuffy's Launcher.vbs"), "w", newline="\r\n") as f:
        f.write(LAUNCHER_VBS)
    os.makedirs(os.path.join(out, "assets", "hub"))
    for name in os.listdir(os.path.join(ROOT, "assets", "hub")):
        if name.endswith(".png"):
            shutil.copy2(os.path.join(ROOT, "assets", "hub", name), os.path.join(out, "assets", "hub", name))
    icon = os.path.join(ROOT, "assets", "launcher.ico")
    if os.path.exists(icon):
        shutil.copy2(icon, os.path.join(out, "assets", "launcher.ico"))
    # Each plugin: its repository (from publish.json), its plugin class, its launcher key
    with open(publish_path, encoding="utf-8") as f:
        repos = json.load(f)["plugins"]
    plugins = []
    for plugin_id, spec in PLUGINS.items():
        entry = repos.get(spec["folder"])
        if not entry or not entry.get("repo"):
            continue
        plugins.append({"key": LAUNCH_KEYS[plugin_id], "folder": spec["folder"], "name": DISPLAY.get(plugin_id, spec["name"]),
                        "repo": entry["repo"], "branch": entry.get("branch", "master"),
                        "class": spec["package"] + "." + spec["class"]})
    launcher = repos.get("launcher", {})
    with open(os.path.join(out, "friends.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"_about": "The plugins Snuffy's Launcher downloads (each its own repository) and the class it loads; "
                             "launcher is its own repository (it updates itself from there)",
                   "launcher": {"repo": launcher.get("repo", ""), "branch": launcher.get("branch", "main")},
                   "plugins": plugins}, f, indent=2)
        f.write("\n")
    runner = os.path.join(out, "runner")
    os.makedirs(os.path.join(runner, "src", "main", "java", "com", "snuffys", "launcher"))
    for name, text in (("settings.gradle", RUNNER_SETTINGS), ("build.gradle", RUNNER_BUILD)):
        with open(os.path.join(runner, name), "w", newline="\n") as f:
            f.write(text)
    with open(os.path.join(runner, "src", "main", "java", "com", "snuffys", "launcher", "SnuffysRunner.java"), "w",
              newline="\n") as f:
        f.write(RUNNER_MAIN)
    for name in ("gradlew", "gradlew.bat"):
        shutil.copy2(os.path.join(ROOT, name), os.path.join(runner, name))
    shutil.copytree(os.path.join(ROOT, "gradle"), os.path.join(runner, "gradle"))
    # The one-file installer to send friends (Snuffys-Launcher-Setup.exe): built here when there's a C# compiler (mcs, or
    # Windows' own csc), kept in scripts/setup/ for when there isn't
    setup = os.path.join(ROOT, "scripts", "setup")
    built = os.path.join(setup, SETUP_EXE)
    # Rebuilt only when its source or icon changed: the same file every release, so Windows SmartScreen's download
    # reputation (kept per file) builds up instead of starting over ("isn't commonly downloaded") each push
    source = os.path.join(setup, "SnuffysSetup.cs")
    stamp = os.path.join(setup, "source.sha256")
    digest = hashlib.sha256()
    for part in (source, os.path.join(ROOT, "assets", "launcher.ico")):
        if os.path.exists(part):
            with open(part, "rb") as f:
                data = f.read()
            # (the source with its line endings evened out: git on Windows checks it out with CRLF, which isn't a change)
            digest.update(data.replace(b"\r\n", b"\n") if part.endswith(".cs") else data)
    was = open(stamp).read().strip() if os.path.exists(stamp) else ""
    if not os.path.exists(built) or was != digest.hexdigest():
        compile_setup(source, built)
        if os.path.exists(built):
            with open(stamp, "w", newline="\n") as f:
                f.write(digest.hexdigest() + "\n")
    if os.path.exists(built):
        shutil.copy2(built, os.path.join(out, SETUP_EXE))
    for name, text in (("README.md", LAUNCHER_README), (".gitignore", LAUNCHER_IGNORE)):
        with open(os.path.join(out, name), "w", newline="\n") as f:
            f.write(text)
    return out


def main():
    wanted = [a for a in sys.argv[1:] if not a.startswith("--")] or list(PLUGINS)
    if "--check" in sys.argv:
        check(wanted)
        return
    for plugin_id in wanted:
        out = build(plugin_id)
        if "--verify" in sys.argv or "--build" in sys.argv:
            gradle(out, "compileJava")
            print("%-12s compiles on its own" % plugin_id)
        if "--build" in sys.argv:
            release(plugin_id, out)
    if wanted == list(PLUGINS):
        out = build_launcher()
        print("%-12s friends launcher written" % "launcher")


if __name__ == "__main__":
    main()
