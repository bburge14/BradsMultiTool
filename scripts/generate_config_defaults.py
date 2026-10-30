#!/usr/bin/env python3
"""Write <Name>Defaults.java for each settings interface: code that saves every setting's default value.

Usage:
    python3 scripts/generate_config_defaults.py           # (re)write them
    python3 scripts/generate_config_defaults.py --check   # are they up to date? (changes nothing)

RuneLite only saves the defaults of the settings an interface declares itself (ConfigManager.setDefaultConfiguration
reads getDeclaredMethods), not ones it inherits. Every plugin's settings inherit theirs (CommonConfig, TickConfig,
and in the Toolbelt all of them), and the settings panel breaks on a choice (enum) setting with nothing saved, and
shows other unsaved settings wrong (a default-on switch as off). So each plugin saves them itself at start
(ConfigDefaults), through these classes: plain calls to each setting, since the Plugin Hub doesn't allow reflection.
"""
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "src", "main", "java", "com", "bradsmultitool")
CONFIGS = ["CommonConfig", "TickConfig", "SkillTrackerConfig", "PvmConfig", "BuffConfig", "GemCrabConfig"]

TEMPLATE = """package com.bradsmultitool;

/**
 * Saves the default of every setting {name} declares, where nothing is saved yet (see ConfigDefaults).
 * Generated from {name}.java by scripts/generate_config_defaults.py: regenerate rather than editing.
 */
final class {name}Defaults {{

    private {name}Defaults() {{
    }}

    static void store(ConfigDefaults defaults, {name} config) {{
{lines}    }}
}}
"""


def settings(name):
    """(keyName, method) of each @ConfigItem the interface declares, in file order."""
    with open(os.path.join(SRC, name + ".java"), encoding="utf-8") as f:
        text = f.read()
    found = []
    for item in re.finditer(r"@ConfigItem\((.*?)\)\s*(?:@\w+(?:\([^)]*\))?\s*)*default\s+[\w.<>, ]+?\s+(\w+)\(\)", text, re.S):
        key = re.search(r'keyName\s*=\s*"([^"]+)"', item.group(1)).group(1)
        found.append((key, item.group(2)))
    return found


def generate(name):
    lines = "".join('        defaults.store("%s", config.%s());\n' % (key, method) for key, method in settings(name))
    return TEMPLATE.format(name=name, lines=lines)


def main():
    stale = []
    for name in CONFIGS:
        path = os.path.join(SRC, name + "Defaults.java")
        text = generate(name)
        current = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if current == text:
            continue
        if "--check" in sys.argv:
            stale.append(name + "Defaults.java")
        else:
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
            print("wrote %s (%d settings)" % (os.path.relpath(path, ROOT), len(settings(name))))
    if stale:
        raise SystemExit("Out of date: %s (run python3 scripts/generate_config_defaults.py)" % ", ".join(stale))


if __name__ == "__main__":
    main()
