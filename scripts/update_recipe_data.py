"""Regenerate src/main/resources/com/bradsmultitool/recipes.json from the OSRS Wiki.

Usage: python3 scripts/update_recipe_data.py

Every wiki recipe (the 'recipe' bucket) for the production skills: what it makes, its level, XP and the
materials it uses. Firemaking's logs come from the Firemaking page table, Prayer's bones and ashes from
each item's Prayer info. Used to suggest what to make for
a skill goal from what you have, and to highlight those materials in your inventory and bank.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from update_skill_helpers import recipes, skill_of  # noqa: E402
from update_skilling_data import api, number, rows_of, wikitext  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "recipes.json")
SKILLS = ["Cooking", "Crafting", "Construction", "Fletching", "Herblore", "Magic", "Runecraft", "Smithing"]


def tool_names(text):
    """'[[File:Knife.png|...]][[Knife|Knife]], [[Chisel]]' -> ['Knife', 'Chisel']; of 'A OR B', the first."""
    text = re.sub(r"\[\[File:[^\]]*\]\]", "", text or "")
    text = re.split(r"<br\s*/?>\s*OR\s*<br\s*/?>", text)[0]
    names = [re.sub(r"<[^>]+>|\{\{[^}]*\}\}", "", n).strip() for n in re.findall(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", text)]
    if not names:
        names = [re.sub(r"<[^>]+>", "", n).strip() for n in text.split(",")]
    return [n for n in dict.fromkeys(names) if n and n.lower() not in ("none", "n/a")]


def skill_recipes(skill):
    out, seen = [], set()
    for recipe in recipes(skill):
        stats = skill_of(recipe, skill)
        output = recipe.get("output")
        if not stats or stats[1] <= 0 or not isinstance(output, dict) or not recipe.get("materials"):
            continue
        materials = {}
        for m in recipe["materials"]:
            materials[m["name"]] = materials.get(m["name"], 0) + int(number(str(m.get("quantity", "1"))) or 1)
        key = (output["name"], tuple(sorted(materials.items())))
        if key in seen:
            continue
        seen.add(key)
        entry = {"name": output["name"], "level": stats[0], "xp": stats[1], "materials": materials}
        facility = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", recipe.get("facilities") or "").strip()
        if facility:
            entry["facility"] = facility
        tools = tool_names(recipe.get("tools"))
        if tools:
            entry["tools"] = tools
        out.append(entry)
    return sorted(out, key=lambda r: (r["level"], r["name"]))


def firemaking():
    """Burning each kind of log (the Firemaking page's first table)."""
    text = wikitext("Firemaking")
    table = text[text.find("{|"):text.find("\n|}")]
    out = []
    for cells in rows_of(table):
        logs = re.search(r"plinkt\|([^|}]+)", cells[1]) if len(cells) > 2 else None
        if logs and number(cells[0]) is not None and number(cells[2]) is not None:
            out.append({"name": "Burn " + logs.group(1).strip().lower(), "level": int(number(cells[0])),
                        "xp": number(cells[2]), "materials": {logs.group(1).strip(): 1}, "tools": ["Tinderbox"]})
    return out


def prayer():
    """Burying/scattering each kind of bones and ashes (every page using the wiki's Prayer info template)."""
    pages = api({"action": "query", "list": "embeddedin", "eititle": "Template:Prayer info", "einamespace": 0,
                 "eilimit": 500})["query"]["embeddedin"]
    out = []
    for i in range(0, len(pages), 50):
        data = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                    "titles": "|".join(p["title"] for p in pages[i:i + 50])})
        for page in data["query"]["pages"].values():
            text = page["revisions"][0]["slots"]["main"]["*"]
            for info in re.findall(r"\{\{Prayer info(.*?)\}\}", text, re.S):
                fields = dict(re.findall(r"\|\s*(\w+)\s*=\s*([^|\n]*)", info))
                name = fields.get("name", page["title"]).strip()
                xp = number(fields.get("xp", ""))
                kind = fields.get("type", "bone").strip().lower()
                # Only what you bury or scatter (ensouled heads and the like are reanimated with Magic)
                if xp and kind in ("bone", "bones", "ash", "ashes", ""):
                    verb = "Scatter" if "ashes" in name.lower() else "Bury"
                    out.append({"name": verb + " " + name.lower(), "level": int(number(fields.get("level", "1")) or 1),
                                "xp": xp, "materials": {name: 1}})
    return sorted(out, key=lambda r: (r["xp"], r["name"]))


def main():
    data = {"source": "oldschool.runescape.wiki", "skills": {skill.upper(): skill_recipes(skill) for skill in SKILLS}}
    data["skills"]["FIREMAKING"] = firemaking()
    data["skills"]["PRAYER"] = prayer()
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True, ensure_ascii=False)
    print("Wrote %s to %s" % (", ".join("%s %d" % (k.title(), len(v)) for k, v in sorted(data["skills"].items())),
                              os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
