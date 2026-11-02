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
## Installing

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
        "Track for goal, and goal bars on the Skills tab",
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
        "**Click timing**: how far into the tick each click lands, for tick skilling practice",
    ],
    "voices": [
        "**One voice and volume** for all of Snuffy's plugins (10 voices)",
        "**Idle alerts**: AFK, about to be logged out, six-hour logout",
        "**Run energy** low and restored alerts",
        "**Clue drops** from monsters and skilling",
    ],
    "planner": [
        "**Today**: the daily rewards you've unlocked (battlestaves, herb boxes, essence, runes, sand, flax, bonemeal, "
        "dynamite, ogre arrows), ticked off as you collect them, plus your own dailies, with the time to the reset",
        "**Weekly**: Tears of Guthix, your own weeklies, the Kingdom of Miscellania's approval and coffer, and your "
        "farming contract",
        "**Timers**: every farming patch, compost bin and Hespori you've been to, bird houses and timers of your own, "
        "with ready alerts",
        "**Slayer**: your task, kills left, points and streak",
        "**Drops**: each monster or chest tracked on its own: kills, loot with the drop rates you've seen, Grand Exchange "
        "value, this session beside all time, and your latest drops",
        "**Notes & to-do**: a to-do list and a notes box for each account",
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
    title = re.search(r'name = "([^"]+)"', text).group(1)
    description = re.search(r'description = "([^"]+)"', text).group(1)
    tags = re.findall(r'"([^"]+)"', re.search(r"tags = \{([^}]*)\}", text).group(1))
    return title, description, tags


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
    for name in sorted(names):
        with open(os.path.join(SRC, name + ".java"), encoding="utf-8") as f:
            text = f.read()
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
        for persona in sorted(os.listdir(voices)):
            for folder in spec["voices"]:
                source = os.path.join(voices, persona, folder)
                if os.path.isdir(source):
                    shutil.copytree(source, os.path.join(res_out, "voices", persona, folder))
    title, description, tags = descriptor(spec["class"])
    with open(os.path.join(out, "runelite-plugin.properties"), "w", encoding="utf-8") as f:
        f.write("displayName=%s\nauthor=%s\ndescription=%s\ntags=%s\nplugins=%s.%s\n" % (
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
    with open(os.path.join(out, "README.md"), "w", encoding="utf-8") as f:
        f.write(README % {"title": title, "description": description, "features": features(plugin_id),
                          "family": family(plugin_id)})
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


if __name__ == "__main__":
    main()
