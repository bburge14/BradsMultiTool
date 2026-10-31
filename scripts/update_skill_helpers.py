#!/usr/bin/env python3
"""Regenerate src/main/resources/com/bradsmultitool/skill_helpers.json.

Usage: python3 scripts/update_skill_helpers.py

- Hunter creatures: level, XP and trap from each page's {{Hunter info}} (OSRS Wiki)
- Runecraft: each rune's level, XP and multiple-rune levels (Runecraft page) and its altar's object ids
- Cooking: burn levels on a fire, a range, and with cooking gauntlets (Cooking/Burn level)
- Agility courses: level (Agility page), XP per lap and every obstacle id (RuneLite's agility plugin, BSD-2-Clause)
- Agility shortcuts: level, name and object ids (RuneLite's AgilityShortcut, BSD-2-Clause)
- Sailing: sea charting tasks (level, type, XP, location), shipwrecks, salvaging hooks, trawling shoals
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


def sailing():
    """Sea charting tasks (level, type, XP, where), shipwrecks, salvaging hooks and trawling shoals."""
    text = wikitext("Sea charting")
    xp_by_type = {}
    for cells in rows_of(tables(text)[0]):
        if len(cells) >= 4 and number(cells[-2]) is not None:
            xp_by_type[plain(cells[-3]).lower()] = number(cells[-2])
    tasks = []
    for row in re.findall(r"\{\{SeaChartRow\|(.*?)\}\}\n", text):
        fields = dict(re.findall(r"\|(\w+)=([^|]*)", "|" + row.split("|id=", 1)[-1] and "|" + row))
        location = re.match(r"(\d+),(\d+)", fields.get("location", ""))
        if not location or not fields.get("level", "").isdigit():
            continue
        # "Task text.''Answer to the riddle: X''" / "''Note: Y''": split the extras off the task
        description = plain(row.split("|id=")[0]).replace("''", " ")
        parts = re.split(r"\s*(Answer to the riddle:|Note:)\s*", description)
        extras = {label: re.sub(r"\s+", " ", value).strip() for label, value in zip(parts[1::2], parts[2::2])}
        kind = fields.get("type", "")
        task = {"level": int(fields["level"]), "type": kind, "xp": xp_by_type.get(kind.lower()),
                "task": re.sub(r"\s+", " ", parts[0]).strip(), "sea": plain(fields.get("sea", "")),
                "x": int(location.group(1)), "y": int(location.group(2))}
        if extras.get("Answer to the riddle:"):
            task["answer"] = extras["Answer to the riddle:"]
        if extras.get("Note:"):
            task["note"] = extras["Note:"]
        tasks.append(task)
    wrecks = {}
    salvage = wikitext("Shipwreck salvaging")
    for cells in rows_of(tables(salvage[salvage.find("==Shipwrecks"):])[0]):
        if len(cells) >= 7 and number(cells[0]) is not None:
            name = plain(cells[1])
            wrecks[name.lower()] = {"name": name, "level": int(number(cells[0])), "xp": number(cells[3]),
                                    "salvage": plain(cells[4]), "sortXp": number(cells[5]), "duration": plain(cells[6])}
    hooks = []
    for cells in rows_of(tables(wikitext("Salvaging hook"))[0]):
        m = re.search(r"txt=([^|}]+)", cells[0])
        if m and len(cells) > 1 and number(cells[1]) is not None:
            hooks.append({"name": m.group(1) + " salvaging hook", "level": int(number(cells[1]))})
    shoals = {}
    trawl = wikitext("Deep sea trawling")
    section = trawl[trawl.find("==Shoals"):]
    for index, table in enumerate(tables(section)[:2]):
        for cells in rows_of(table):
            cells = [re.sub(r'^\s*rowspan="\d+"\s*\|', "", c) for c in cells]
            if len(cells) < 5 or number(cells[0]) is None:
                continue
            name = plain(cells[1])
            mixed = index == 1  # mixed shoals: level, shoal, depths, bait, (first) fish, where it spawns
            shoals[name.lower()] = {"name": name, "level": int(number(cells[0])), "depths": plain(cells[2]),
                                    "bait": plain(cells[3] if mixed else cells[4]),
                                    "fish": "Mixed deep sea fish" if mixed else plain(cells[5])}
    return {"charting": tasks, "shipwrecks": wrecks, "hooks": hooks, "shoals": shoals, "monsters": sea_monsters()}


KEELS = ["None", "Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune", "Dragon"]


def sea_monsters():
    """Sea monsters fought from a boat: HP, max hit on each keel, aggression (Boat combat), combat level, and the
    parts bounty tasks ask for with the lowest Sailing level for each (Bounty tasks)."""
    monsters = {}
    combat = wikitext("Boat combat")
    for cells in rows_of(tables(combat[combat.find("==Monsters"):])[0]):
        if len(cells) < 11 or number(cells[1]) is None:
            continue
        hits = [number(re.sub(r"\{\{\w+\|(\d+)\}\}", r"\1", c)) for c in cells[2:10]]
        if any(h is None for h in hits):
            continue
        page = re.search(r"\[\[([^|\]]+)", cells[0]).group(1)
        name = plain(cells[0])
        monsters[name.lower()] = {"name": name, "page": page, "hp": int(number(cells[1])),
                                  "maxHits": [int(h) for h in hits], "aggressive": "|Yes}}" in cells[10], "bounties": {}}
    for line in re.findall(r"\{\{BountyTaskLine\|([^}]*)\}\}", wikitext("Bounty tasks")):
        fields = dict(re.findall(r"(\w+)=([^|]*)", line))
        monster = monsters.get(fields.get("monster", "").lower())
        if monster is None or not fields.get("level", "").isdigit():
            continue
        item = fields["item"]
        level = int(fields["level"])
        bounty = monster["bounties"].setdefault(item, {"level": level, "xp": int(number(fields.get("xp", "0")) or 0)})
        bounty["level"] = min(bounty["level"], level)
    for m in monsters.values():
        rows = api({"action": "bucket", "query": "bucket('infobox_monster').select('combat_level').where('page_name','%s').run()"
                    % m["page"].replace("'", "\\'")}).get("bucket", [])
        if rows and rows[0].get("combat_level"):
            m["combat"] = int(rows[0]["combat_level"])
        del m["page"]
    return monsters


def recipes(skill):
    """Every wiki recipe for a skill (the 'recipe' bucket's production data)."""
    out, offset = [], 0
    while True:
        query = "bucket('recipe').select('page_name','production_json').where('uses_skill','%s').limit(500).offset(%d).run()"
        rows = api({"action": "bucket", "query": query % (skill, offset)}).get("bucket", [])
        out += [json.loads(row["production_json"]) for row in rows if row.get("production_json")]
        if len(rows) < 500:
            return out
        offset += 500


def skill_of(recipe, skill):
    """(level, xp) for the skill in a recipe, or None when unknown."""
    for entry in recipe.get("skills", []):
        if entry.get("name") == skill and str(entry.get("level", "")).isdigit():
            return int(entry["level"]), number(str(entry.get("experience", "0"))) or 0
    return None


def fishing_spots():
    """Every fishing spot type (Fishing spots page): its NPC ids, and for each option the fish it catches with
    their level, XP, the tool needed and the bait it uses up (each spot page's drops, the 'recipe' bucket's Fishing entries)."""
    fish = {}
    for recipe in recipes("Fishing"):
        stats = skill_of(recipe, "Fishing")
        name = (recipe.get("name") or "").lower()
        # Several recipes for a fish (barbarian fishing without a harpoon, special harpoons...): the usual way
        # gives the most XP, at the lowest level
        if stats and name and (name not in fish or (-stats[1], stats[0]) < (-fish[name]["xp"], fish[name]["level"])):
            # Rendered links ("[[File:Fishing rod.png|...]][[Fishing rod|Fishing rod]]"); of "A OR B", the first
            tools = re.sub(r"\[\[File:[^\]]*\]\]", "", recipe.get("tools") or "")
            tools = re.split(r"<br\s*/?>\s*OR\s*<br\s*/?>", tools)[0]
            fish[name] = {"level": stats[0], "xp": stats[1], "tool": plain(tools).split(",")[0].strip()}
            # What it uses up: the bait (Feathers, Fishing bait, Sandworms...)
            bait = [m.get("name") for m in recipe.get("materials") or [] if isinstance(m, dict) and m.get("name")]
            if bait:
                fish[name]["bait"] = bait[0]
    index = wikitext("Fishing spots")
    pages = []
    for page in re.findall(r"^\|\[\[((?:Rod |Boat )?[Ff]ishing spot[^|\]]*)", index, re.M):
        if page not in pages:
            pages.append(page)
    spots, ids = {}, {}
    for page in pages:
        text = wikitext(page)
        npc_ids = [int(i) for field in re.findall(r"\|id\d* *= *([\d, ]+)", text) for i in re.findall(r"\d+", field)]
        options_field = re.search(r"\|options\d* *= *([^\n]*)", text)
        options = [o.strip() for o in re.split(r",", options_field.group(1))] if options_field else []
        if not npc_ids or not options:
            continue
        catches = {}
        # Each option's main drops; a spot with one option has no drop versions
        # (only fish count, so tables of clue bottles and other extras add nothing)
        for section in text.split("{{DropsTableHead")[1:]:
            head = re.match(r"(?:\|dropversion=([^}|]*))?", section)
            body = section[:section.find("{{DropsTableBottom")]
            version = (head.group(1) if head.group(1) else options[0]).strip()
            for name in re.findall(r"\{\{DropsLineSkill\|name=([^|}]+)", body):
                info = fish.get(name.strip().lower())
                # "Big swordfish" and the like are rare extras of the normal catch
                if info and not name.startswith("Big "):
                    catches.setdefault(version, []).append(dict(info, name=name.strip()))
        if not catches:
            continue
        key = page.lower()
        spots[key] = {"name": page, "options": {o: sorted(c, key=lambda f: f["level"]) for o, c in catches.items()}}
        for i in npc_ids:
            ids.setdefault(str(i), key)
    return {"spots": spots, "npcs": ids}


def cooking():
    """What each raw food cooks into on a range or fire: {raw name: {name, level, xp}}."""
    out = {}
    for recipe in recipes("Cooking"):
        stats = skill_of(recipe, "Cooking")
        if recipe.get("facilities") not in ("Cooking range", "Fire") or len(recipe["materials"]) != 1 or not stats:
            continue
        if not isinstance(recipe.get("output"), dict):
            continue
        name = recipe["output"]["name"]
        raw = recipe["materials"][0]["name"]
        if stats[1] <= 0 or name.lower().startswith("burnt"):
            continue
        if raw.lower() not in out or stats[1] > out[raw.lower()]["xp"]:
            out[raw.lower()] = {"name": name, "level": stats[0], "xp": stats[1]}
    return out


def smithing():
    """Anvil items (bar, bars needed, level, XP) and furnace bars (ores needed, level, XP)."""
    anvil, furnace = [], []
    for recipe in recipes("Smithing"):
        stats = skill_of(recipe, "Smithing")
        materials = {m["name"]: int(number(str(m.get("quantity", "1"))) or 1) for m in recipe.get("materials", [])}
        if not stats or stats[1] <= 0 or "Smithing catalyst" in materials:
            continue
        output = recipe.get("output")
        if not isinstance(output, dict):
            continue
        name = output["name"]
        quantity = int(number(str(output.get("quantity", "1"))) or 1)
        if recipe.get("facilities") == "Anvil" and len(materials) == 1:
            bar, bars = next(iter(materials.items()))
            if bar.endswith(" bar"):
                anvil.append({"name": name + (" (%d)" % quantity if quantity > 1 else ""), "bar": bar, "bars": bars,
                              "level": stats[0], "xp": stats[1]})
        elif recipe.get("facilities") == "Furnace" and name.endswith(" bar") and "'" not in name:
            furnace.append({"name": name, "materials": materials, "level": stats[0], "xp": stats[1]})
    anvil.sort(key=lambda r: (r["bar"], r["level"], r["name"]))
    furnace.sort(key=lambda r: r["level"])
    return {"anvil": anvil, "furnace": furnace}


def main():
    ids = object_ids()
    data = {"source": "oldschool.runescape.wiki; RuneLite (BSD-2-Clause)", "hunter": hunter(), "runecraft": runecraft(),
            "burn": burn_levels(), "agility": courses(ids), "shortcuts": shortcuts(ids), "sailing": sailing(),
            "cooking": cooking(), "smithing": smithing(), "fishing": fishing_spots()}
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True, ensure_ascii=False)
    print("Cooking: %d foods; Smithing: %d anvil items, %d bars" % (
        len(data["cooking"]), len(data["smithing"]["anvil"]), len(data["smithing"]["furnace"])))
    print("Wrote %d hunter creatures, %d runes (%d altars), %d foods, %d courses (%d obstacles), %d shortcut objects to %s" % (
        len(data["hunter"]), len(data["runecraft"]["runes"]), len(data["runecraft"]["altars"]), len(data["burn"]),
        len(data["agility"]["courses"]), len(data["agility"]["obstacles"]), len(data["shortcuts"]), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
