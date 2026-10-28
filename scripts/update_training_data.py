"""Regenerate src/main/resources/com/bradsmultitool/training.json from the OSRS Wiki training guides.

Usage: python3 scripts/update_training_data.py   (run update_skilling_data.py first)

Each skill's "Pay-to-play <Skill> training" guide (or its named guide: melee for Attack, Strength, Defence and
Hitpoints) splits its methods by level ("Levels 35–99: Teak trees") under "Fastest experience" and "Other
methods". Guides without levels (Prayer, Herblore, Sailing) give their sections' methods for all levels. Methods that name a tree, rock, stall or pickpocket target
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
SKILLS = ["Agility", "Attack", "Construction", "Cooking", "Crafting", "Defence", "Farming", "Firemaking", "Fishing",
          "Fletching", "Herblore", "Hitpoints", "Hunter", "Magic", "Mining", "Prayer", "Ranged", "Runecraft", "Sailing",
          "Smithing", "Strength", "Thieving", "Woodcutting"]
# Skills whose guide is under another name (melee skills and Hitpoints share the melee guide)
GUIDES = {"Attack": "Pay-to-play Melee training", "Strength": "Pay-to-play Melee training",
          "Defence": "Pay-to-play Melee training", "Hitpoints": "Pay-to-play Melee training",
          "Prayer": "Prayer training", "Construction": "Construction training", "Sailing": "Sailing training"}
# Headings that aren't training methods (for guides that don't split methods by level)
NOT_METHODS = re.compile(r"quest|equipment|overview|^starting off$|general|reference|experience|inventory|recommended|"
                         r"tips|strateg|comparison|notes|information|obtaining|setup|calculator|route|efficiency|cost|"
                         r"crew|requirement|^method$|choice|weapon|post 99|table|^fish$|unlock", re.I)
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
    text = wikitext(GUIDES.get(skill, "Pay-to-play %s training" % skill)) or wikitext("%s training" % skill)
    out = by_level(text, objects, npcs)
    # A guide with only one or two level headings (Herblore) is really organised by section
    return out if len(out) >= 3 else by_heading(text, objects, npcs)


def plain_name(text):
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    return re.sub(r"\{\{[^}]*\}\}", "", text).strip()


def entry(name, start, end, fastest, objects, npcs):
    ids, names = targets(name, objects, npcs)
    out = {"name": name, "from": start, "to": end, "fastest": fastest}
    if ids:
        out["objects"] = ids
    if names:
        out["npcs"] = names
    return out


def by_heading(text, objects, npcs):
    """For guides that don't split by level (Prayer, Herblore, Sailing): each section's methods, all levels.
    A section with method sub-headings gives those; otherwise the section itself is the method."""
    sections, current = [], None
    for m in re.finditer(r"\n(==+)\s*([^=\n]+?)\s*\1", text):
        depth, heading = len(m.group(1)), plain_name(m.group(2))
        if depth == 2:
            current = [heading, []]
            sections.append(current)
        elif depth == 3 and current is not None and not NOT_METHODS.search(heading):
            current[1].append(heading)
    out = []
    for heading, children in sections:
        other = re.search(r"other|passive|alternat", heading, re.I) is not None
        names = children if children else ([] if NOT_METHODS.search(heading) or other else [heading])
        for name in names:
            level = re.match(r"Levels?\s*(\d+)", name)
            start = int(level.group(1)) if level else 1
            name = re.sub(r"^Levels?\s*[\d/+–\-, ]+:?\s*", "", name)
            if name and not any(e["name"] == name for e in out):
                out.append(entry(name, start, 99, not other and not out, objects, npcs))
    return out


def by_level(text, objects, npcs):
    out, section = [], ""
    for m in re.finditer(r"\n(==+)\s*([^=\n]+?)\s*\1", text):
        depth, heading = len(m.group(1)), m.group(2)
        found = METHOD.match(heading)
        if depth == 2:
            section = heading.lower()
            # A top-level "Levels 15–99: Tree runs" section is a method of its own (Farming's guide)
            if not found:
                continue
        elif depth != 3 or not found:
            continue
        start = int(found.group(1))
        ends = [int(n) for n in re.findall(r"[–\-]\s*([\d/]+)", heading)[:1][0].split("/")] if found.group(2) else []
        end = max(ends) if ends else 99
        # The method is whatever follows the level range ("Levels 52-99 Fixing goblin lamps" has no colon)
        name = plain_name(re.sub(r"^Levels?\s*[\d/+–\-, ]+:?\s*", "", heading))
        if len(name) < 3:
            continue
        out.append(entry(name, start, end, section.startswith("fastest"), objects, npcs))
    return out


def main():
    with open(os.path.join(RESOURCES, "skilling.json")) as f:
        skilling = json.load(f)
    objects = {object_id: o["page"] for object_id, o in skilling["objects"].items()}
    npcs = list(skilling["pickpocket"])
    # Hunter creatures (skill_helpers.json) can be highlighted too
    with open(os.path.join(RESOURCES, "skill_helpers.json")) as f:
        npcs += list(json.load(f)["hunter"])
    data = {"source": "oldschool.runescape.wiki (Pay-to-play training guides)",
            "skills": {skill.upper(): methods(skill, objects, npcs) for skill in SKILLS}}
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True, ensure_ascii=False)
    total = sum(len(v) for v in data["skills"].values())
    linked = sum(1 for v in data["skills"].values() for m in v if "objects" in m or "npcs" in m)
    print("Wrote %d methods (%d with things to highlight) to %s" % (total, linked, os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
