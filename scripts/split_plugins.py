#!/usr/bin/env python3
"""Build each of Snuffy's plugins as its own Plugin Hub repository, from this one.

Usage:
    python3 scripts/split_plugins.py                 # write build/split/<plugin>/ for every plugin
    python3 scripts/split_plugins.py --verify        # ...and compile each one on its own
    python3 scripts/split_plugins.py skilltracker    # just one

This repository stays where everything is worked on. Each plugin's repository gets only what that plugin
uses: starting from its plugin class, every class it refers to (and so on), the data files those classes
load, and its voice clip folders (docs/DEVELOPMENT.md "Planned: separate plugins"). Its package is renamed
(com.bradsmultitool -> com.snuffys.<plugin>) and it gets the Plugin Hub's build files, a
runelite-plugin.properties, the licence and a short README. The GitHub Action in .github/workflows pushes the
results to the plugin repositories.
"""
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "src", "main", "java", "com", "bradsmultitool")
RES = os.path.join(ROOT, "src", "main", "resources", "com", "bradsmultitool")
OUT = os.path.join(ROOT, "build", "split")
OLD_PACKAGE = "com.bradsmultitool"

# Each plugin: its plugin class, its package and repository, and which voice clip folders it ships
PLUGINS = {
    "multitool": {
        "class": "BradsMultiToolPlugin", "package": "com.snuffys.multitool", "repo": "snuffys-multi-tool",
        "voices": ["skilling", "pvm", "gemcrab", "shared"],
    },
    "skilltracker": {
        "class": "SkillTrackerPlugin", "package": "com.snuffys.skilltracker", "repo": "snuffys-skilling-helper",
        "voices": ["skilling", "shared"],
    },
    "pvm": {
        "class": "PvmPlugin", "package": "com.snuffys.pvm", "repo": "snuffys-pvm-slayer",
        "voices": ["pvm", "shared"],
    },
    "gemcrab": {
        "class": "GemCrabPlugin", "package": "com.snuffys.gemcrab", "repo": "snuffys-gemstone-crab",
        "voices": ["gemcrab", "shared"],
    },
    "voices": {
        "class": "VoicesPlugin", "package": "com.snuffys.voices", "repo": "snuffys-voices",
        "voices": ["shared"],
    },
}
AUTHOR = "bburge14"
# Voices whose recordings are licensed non-commercially (VOICE_CREDITS.md); see docs/PLUGIN_HUB_COMPLIANCE.md
VOICE_PERSONAS = None  # every persona folder

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
version = '1.0.0'

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release.set(11)
}
"""


def strip_comments_and_strings(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', text)


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
    """The data files the included classes load (their RESOURCE constants), plus the Slayer pictures."""
    files = set()
    for name in names:
        with open(os.path.join(SRC, name + ".java"), encoding="utf-8") as f:
            text = f.read()
        files.update(re.findall(r'RESOURCE = "([^"]+)"', text))
        if 'loadImageResource(SlayerData.class, "slayer/"' in text:
            files.add("slayer/")
        if 'loadImageResource(SkillTracker.class, "icons/' in text:
            files.add("icons/")
    return files


def descriptor(name):
    """name, description and tags from the plugin class's @PluginDescriptor."""
    with open(os.path.join(SRC, name + ".java"), encoding="utf-8") as f:
        text = f.read()
    title = re.search(r'name = "([^"]+)"', text).group(1)
    description = re.search(r'description = "([^"]+)"', text).group(1)
    tags = re.findall(r'"([^"]+)"', re.search(r"tags = \{([^}]*)\}", text).group(1))
    return title, description, tags


def build(plugin_id):
    spec = PLUGINS[plugin_id]
    known = classes()
    names = closure(spec["class"], known)
    out = os.path.join(OUT, plugin_id)
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
        f.write(BUILD_GRADLE % spec)
    with open(os.path.join(out, "settings.gradle"), "w", encoding="utf-8") as f:
        f.write("rootProject.name = '%s'\n" % spec["repo"])
    # The Plugin Hub shows icon.png from the repository's root (made by update_icons.py)
    shutil.copy2(os.path.join(ROOT, "assets", "hub", plugin_id + ".png"), os.path.join(out, "icon.png"))
    for keep in ("LICENSE", "VOICE_CREDITS.md", "gradlew", "gradlew.bat"):
        shutil.copy2(os.path.join(ROOT, keep), os.path.join(out, keep))
    shutil.copytree(os.path.join(ROOT, "gradle"), os.path.join(out, "gradle"))
    with open(os.path.join(out, ".gitignore"), "w", encoding="utf-8") as f:
        f.write(".gradle/\nbuild/\n.idea/\n*.iml\nout/\n")
    with open(os.path.join(out, "README.md"), "w", encoding="utf-8") as f:
        f.write("# %s\n\n%s.\n\nPart of Snuffy's plugins for RuneLite. This repository is generated from "
                "[bburge14/bradsmultitool](https://github.com/bburge14/bradsmultitool), where the plugins are "
                "developed; changes made here directly are overwritten.\n" % (title, description))
    print("%-12s %3d classes, %s, voices: %s" % (plugin_id, len(names), ", ".join(sorted(data_files(names))) or "no data",
                                              "+".join(spec["voices"]) if "VoiceAlertService" in names else "none"))
    return out


def verify(out):
    """Compiles a generated plugin on its own, with this repository's Gradle."""
    result = subprocess.run([os.path.join(ROOT, "gradlew"), "-q", "-p", out, "compileJava"],
                            capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(result.stdout + result.stderr)
        raise SystemExit("%s doesn't compile on its own" % os.path.basename(out))


def main():
    wanted = [a for a in sys.argv[1:] if not a.startswith("--")] or list(PLUGINS)
    for plugin_id in wanted:
        out = build(plugin_id)
        if "--verify" in sys.argv:
            verify(out)
            print("%-12s compiles on its own" % plugin_id)


if __name__ == "__main__":
    main()
