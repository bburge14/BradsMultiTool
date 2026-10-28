#!/usr/bin/env python3
"""Regenerate src/main/resources/com/bradsmultitool/skill_helpers.json.

Usage: python3 scripts/update_skill_helpers.py

- Hunter creatures: level, XP and trap from each page's {{Hunter info}} (OSRS Wiki)
- Runecraft: each rune's level, XP and multiple-rune levels (Runecraft page) and its altar's object ids
- Cooking: burn levels on a fire, a range, and with cooking gauntlets (Cooking/Burn level)
- Agility courses: level (Agility page), XP per lap and every obstacle id (RuneLite's agility plugin, BSD-2-Clause)
- Agility shortcuts: level, name and object ids (RuneLite's AgilityShortcut, BSD-2-Clause)
"""
import json
import os
import re
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
from update_skilling_data import api, number, rows_of, wikitext  # noqa: E402
from update_farming_data import tables  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "skill_helpers.json")
RUNELITE = "https://raw.githubusercontent.com/runelite/runelite/master/"


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "BradsMultiTool data updater"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.read().decode("utf-8")


def plain(text):
    text = re.sub(r"\{\{plink[pt]?\|([^|}]+)[^}]*\}\}", r"\1", text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\{\{[^}]*\}\}|<[^>]+>", "", text)
    return re.sub(r"\s+", " ", text).strip()


def hunter():
    out, cont = {}, {}
    while True:
        data = api(dict({"action": "query", "list": "embeddedin", "eititle": "Template:Hunter info",
                         "eilimit": 500, "einamespace": 0}, **cont))
        pages = [p["title"] for p in data["query"]["embeddedin"]]
        for page in pages:
            text = wikitext(page)
            for block in re.findall(r"\{\{Hunter info(.*?)\n\}\}", text, re.S):
                fields = dict(re.findall(r"\|\s*(\w+)\s*=\s*([^\n]*)", block))
                level, xp = number(fields.get("level", "")), number(fields.get("xp", ""))
                name = plain(fields.get("name", page))
                if level is None or not name or re.search(r"[|{}=]", name):
                    continue
                out.setdefault(name.lower(), {"name": name, "level": int(level), "xp": xp,
                                              "trap": plain(fields.get("trap", "")) or None})
            time.sleep(0.1)
        if "continue" not in data:
            return out
        cont = data["continue"]


def runecraft():
    text = wikitext("Runecraft")
    runes = {}
    for cells in rows_of(tables(text)[0]):
        m = re.search(r"plinkt\|([^|}]+) rune", cells[1] if len(cells) > 1 else "")
        level, xp = number(cells[0]), number(cells[2]) if len(cells) > 2 else None
        if m and level is not None:
            runes[m.group(1)] = {"rune": m.group(1), "level": int(level), "xp": xp, "multiples": []}
    multiple = text[text.find("==Multiple runes"):]
    for cells in rows_of(tables(multiple)[0]):
        m = re.search(r"plinkt\|([^|}]+) rune", cells[0])
        if m and m.group(1) in runes:
            runes[m.group(1)]["multiples"] = [int(number(c)) for c in cells[1:] if number(c) is not None]
    altars = {}
    for rune in runes:
        rows = api({"action": "bucket", "query": "bucket('infobox_scenery').select('object_id').where('page_name','%s Altar').run()" % rune}).get("bucket", [])
        for row in rows:
            for object_id in row.get("object_id") or []:
                altars[str(object_id)] = rune
        time.sleep(0.1)
    return {"runes": runes, "altars": altars}


def burn_levels():
    out = {}
    for cells in (row for table in tables(wikitext("Cooking/Burn level")) for row in rows_of(table)):
        if len(cells) < 6:
            continue
        name = plain(cells[0])
        # "-" means it never stops burning there (stored as 100); N/A means it doesn't apply
        values = [100 if c.strip() == "-" else number(c) if "NA" not in c else None for c in cells[1:8]]
        fire, rng, gauntlets = values[0], values[1], values[4] if len(values) > 4 else None
        if name and "=" not in name and (fire or rng or gauntlets) and name.lower() not in out:
            out[name.lower()] = {"name": name, "fire": int(fire) if fire else None, "range": int(rng) if rng else None,
                                 "gauntlets": int(gauntlets) if gauntlets else None}
    return out


# RuneLite agility plugin's obstacle groups (Obstacles.java comments) -> (wiki course name, Courses.java entry)
COURSE_GROUPS = {
    "Gnome": ("Gnome Stronghold Agility Course", "GNOME"),
    "Brimhaven": ("Brimhaven Agility Arena", None),
    "Draynor": ("Draynor Village Rooftop Course", "DRAYNOR"),
    "Al-Kharid": ("Al Kharid Rooftop Course", "AL_KHARID"),
    "Pyramid": ("Agility Pyramid", "PYRAMID"),
    "Varrock": ("Varrock Rooftop Course", "VARROCK"),
    "Penguin": ("Penguin Agility Course", "PENGUIN"),
    "Barbarian": ("Barbarian Outpost Agility Course", "BARBARIAN"),
    "Canifis": ("Canifis Rooftop Course", "CANIFIS"),
    "Ape atoll": ("Ape Atoll Agility Course", "APE_ATOLL"),
    "Falador": ("Falador Rooftop Course", "FALADOR"),
    "Wilderness": ("Wilderness Agility Course", "WILDERNESS"),
    "Seers": ("Seers' Village Rooftop Course", "SEERS"),
    "Dorgesh-Kaan": ("Dorgesh-Kaan Agility Course", None),
    "Pollniveach": ("Pollnivneach Rooftop Course", "POLLNIVNEACH"),
    "Rellaka": ("Rellekka Rooftop Course", "RELLEKA"),
    "Ardougne": ("Ardougne Rooftop Course", "ARDOUGNE"),
    "Werewolf": ("Werewolf Agility Course", "WEREWOLF"),
    "Prifddinas": ("Prifddinas Agility Course", "PRIFDDINAS"),
    "Shayzien": ("Shayzien Basic Course", "SHAYZIEN_BASIC"),
    "Shayzien basic": ("Shayzien Basic Course", "SHAYZIEN_BASIC"),
    "Shayzien hard": ("Shayzien Advanced Course", "SHAYZIEN_ADVANCED"),
    "Colossal Wyrm": ("Colossal Wyrm Agility Course (basic)", "COLOSSAL_WYRM_BASIC"),
    "Colossal Wyrm basic": ("Colossal Wyrm Agility Course (basic)", "COLOSSAL_WYRM_BASIC"),
    "Colossal Wyrm advanced": ("Colossal Wyrm Agility Course (advanced)", "COLOSSAL_WYRM_ADVANCED"),
}
# Courses whose wiki level can't be told apart by name (the wiki lists Colossal Wyrm twice)
LEVEL_OVERRIDES = {"Colossal Wyrm Agility Course (basic)": 50, "Colossal Wyrm Agility Course (advanced)": 62}


def object_ids():
    ids = {}
    for part in ["ObjectID.java", "ObjectID1.java"]:
        try:
            src = fetch(RUNELITE + "runelite-api/src/main/java/net/runelite/api/gameval/" + part)
        except Exception:
            continue
        ids.update({k: int(v) for k, v in re.findall(r"public static final int ([A-Z0-9_]+) = (\d+);", src)})
    return ids


def courses(ids):
    """{courses: {name: {level, xpPerLap}}, obstacles: {objectId: course name}}"""
    levels = {}
    text = wikitext("Agility")
    full = text[text.find("=== Full list"):]
    for cells in rows_of(tables(full)[0]):
        m = re.search(r"plinkt\|([^|}]+)", cells[1] if len(cells) > 1 else "")
        if m and number(cells[0]) is not None:
            levels.setdefault(m.group(1).strip(), int(number(cells[0])))
    agility = RUNELITE + "runelite-client/src/main/java/net/runelite/client/plugins/agility/"
    xp = {name: float(value) for name, value in re.findall(
        r"^\t([A-Z_]+)\((?:\(client\) -> [^?]+\? [\d.]+ : )?([\d.]+)", fetch(agility + "Courses.java"), re.M)}
    out, obstacles = {}, {}
    source = fetch(agility + "Obstacles.java")
    block = source[source.find("OBSTACLE_IDS = ImmutableSet.of("):source.find("PORTAL_OBSTACLE_IDS")]
    group = None
    for line in block.splitlines():
        comment = re.match(r"\s*// (.+)", line)
        if comment:
            group = COURSE_GROUPS.get(comment.group(1).strip())
            continue
        if not group:
            continue
        name, course = group
        out.setdefault(name, {"name": name, "level": LEVEL_OVERRIDES.get(name, levels.get(name.split(" (")[0])),
                              "xpPerLap": xp.get(course)})
        for constant in re.findall(r"ObjectID\.([A-Z0-9_]+)", line):
            if constant in ids:
                obstacles.setdefault(str(ids[constant]), name)
    return {"courses": out, "obstacles": obstacles}


def shortcuts(ids):
    source = fetch(RUNELITE + "runelite-client/src/main/java/net/runelite/client/game/AgilityShortcut.java")
    out = {}
    for m in re.finditer(r"^\t([A-Z0-9_]+)\((\d+), \"([^\"]+)\",(.*?)\)[,;]\s*$", source, re.M | re.S):
        level, description, body = int(m.group(2)), m.group(3), m.group(4)
        for name in re.findall(r"ObjectID\.([A-Z0-9_]+)", body):
            if name in ids:
                out.setdefault(str(ids[name]), {"level": level, "name": description})
    return out


def main():
    ids = object_ids()
    data = {"source": "oldschool.runescape.wiki; RuneLite (BSD-2-Clause)", "hunter": hunter(), "runecraft": runecraft(),
            "burn": burn_levels(), "agility": courses(ids), "shortcuts": shortcuts(ids)}
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True, ensure_ascii=False)
    print("Wrote %d hunter creatures, %d runes (%d altars), %d foods, %d courses (%d obstacles), %d shortcut objects to %s" % (
        len(data["hunter"]), len(data["runecraft"]["runes"]), len(data["runecraft"]["altars"]), len(data["burn"]),
        len(data["agility"]["courses"]), len(data["agility"]["obstacles"]), len(data["shortcuts"]), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
