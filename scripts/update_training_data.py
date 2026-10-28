"""Regenerate src/main/resources/com/bradsmultitool/training.json from the OSRS Wiki training guides.

Usage: python3 scripts/update_training_data.py   (run update_skilling_data.py first)

Each skill's "Pay-to-play <Skill> training" guide splits its methods by level ("Levels 35–99: Teak trees")
under "Fastest experience" and "Other methods". Methods that name a tree, rock, stall or pickpocket target
we know (skilling.json) also list what to highlight in game.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from update_skilling_data import wikitext  # noqa: E402

HERE = os.path.dirname(__file__)
RESOURCES = os.path.join(HERE, "..", "src", "main", "resources", "com", "bradsmultitool")
OUT = os.path.join(RESOURCES, "training.json")
SKILLS = ["Agility", "Construction", "Cooking", "Crafting", "Farming", "Firemaking", "Fishing", "Fletching", "Herblore",
          "Hunter", "Magic", "Mining", "Runecraft", "Smithing", "Thieving", "Woodcutting"]
METHOD = re.compile(r"^Levels?\s*(\d+)(?:[/\d]*)\s*(?:[–\-]\s*(\d+)(?:/(\d+))*|(\+))?[^:]*:?\s*(.+)$")
SUFFIXES = (" ore vein", " rocks", " rock", " ore", " trees", " tree", " stalls", " stall")


def key(name):
    """'Teak trees' -> 'teak', 'Copper rocks' -> 'copper', 'Fruit Stall' -> 'fruit', 'Tree' -> 'regular'."""
    name = re.sub(r"\s*\([^)]*\)", "", name.lower()).strip()
    if name in ("tree", "trees"):
        return "regular"
    for suffix in SUFFIXES:
        if name.endswith(suffix):
            return name[:-len(suffix)].strip()
    return name


def singular(word):
    if word.endswith("men"):
        return word[:-3] + "man"
    return word[:-1] if word.endswith("s") and not word.endswith("ss") else word


def parts(method):
    """'Copper/Tin ore' -> ['Copper ore', 'Tin ore']; 'Men/Women' -> ['Men', 'Women']."""
    m = re.match(r"^(\w+)/(\w+)(\s.*)?$", method)
    if m:
        rest = m.group(3) or ""
        return [m.group(1) + rest, m.group(2) + rest]
    return [method]


def targets(method, objects, npcs):
    """Object ids and NPC names this method trains on, if we know them."""
    ids, names = set(), set()
    for part in parts(method):
        k = key(part)
        for object_id, page in objects.items():
            if key(page) == k and k:
                ids.add(int(object_id))
        words = re.sub(r"^pickpocketing\s+", "", part.lower()).split()
        if not words:
            continue
        # "Knights of Ardougne", "Master Farmers", "elves": singular first or last word
        wanted = {" ".join([singular(words[0])] + words[1:]), " ".join(words[:-1] + [singular(words[-1])]),
                  " ".join(words[:-1] + [re.sub(r"ves$", "f", words[-1])])}
        for name in npcs:
            plain_name = re.sub(r"\s*\([^)]*\)", "", name)
            if plain_name in wanted:
                names.add(plain_name)
    return sorted(ids), sorted(names)


def methods(skill, objects, npcs):
    text = wikitext("Pay-to-play %s training" % skill)
    out, section = [], ""
    for m in re.finditer(r"\n(==+)\s*([^=\n]+?)\s*\1", text):
        depth, heading = len(m.group(1)), m.group(2)
        if depth == 2:
            section = heading.lower()
            continue
        if depth != 3:
            continue
        found = METHOD.match(heading)
        if not found or "quest" in heading.lower():
            continue
        start = int(found.group(1))
        ends = [int(n) for n in re.findall(r"[–\-]\s*([\d/]+)", heading)[:1][0].split("/")] if found.group(2) else []
        end = max(ends) if ends else 99
        name = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", found.group(5)).strip()
        ids, names = targets(name, objects, npcs)
        entry = {"name": name, "from": start, "to": end, "fastest": section.startswith("fastest")}
        if ids:
            entry["objects"] = ids
        if names:
            entry["npcs"] = names
        out.append(entry)
    return out


def main():
    with open(os.path.join(RESOURCES, "skilling.json")) as f:
        skilling = json.load(f)
    objects = {object_id: o["page"] for object_id, o in skilling["objects"].items()}
    npcs = list(skilling["pickpocket"])
    data = {"source": "oldschool.runescape.wiki (Pay-to-play training guides)",
            "skills": {skill.upper(): methods(skill, objects, npcs) for skill in SKILLS}}
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True, ensure_ascii=False)
    total = sum(len(v) for v in data["skills"].values())
    linked = sum(1 for v in data["skills"].values() for m in v if "objects" in m or "npcs" in m)
    print("Wrote %d methods (%d with things to highlight) to %s" % (total, linked, os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
