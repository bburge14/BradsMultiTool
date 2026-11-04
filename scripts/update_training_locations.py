#!/usr/bin/env python3
"""Regenerate src/main/resources/com/bradsmultitool/locations.json from the OSRS Wiki.

Usage: python3 scripts/update_training_locations.py

Where to train: for every tree, rock and stall in skilling.json and every creature and pickpocket in
skill_helpers.json, the places its wiki page lists ({{ObjectLocLine}} / {{LocLine}} rows): the place, how many there
are, members or not, and the page's note on what you need to use it ("Players must partially complete Legends' Quest").
The training guides' best spots (Pay-to-play Woodcutting training, Mining training, Thieving training, Hunter
training...) come first with why ("2-tick spot", "next to a bank"): BEST below, from those guides.

OSRS Wiki content is CC BY-NC-SA 3.0: the file says so and where it's from.
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "BradsMultiTool RuneLite plugin data updater (github.com/bburge14/bradsmultitool)"
HERE = os.path.dirname(__file__)
RES = os.path.join(HERE, "..", "src", "main", "resources", "com", "bradsmultitool")
OUT = os.path.join(RES, "locations.json")

# The training guides' picks, best first, with why (Pay-to-play Woodcutting/Mining/Fishing training, Thieving training,
# Hunter training, Tick manipulation; read November 2026). Keys are the wiki page; places as its location rows name them
BEST = {
    "Teak tree": [
        ("Isle of Souls", "No requirements; birds there make 2-tick possible; the bank is far"),
        ("Ape Atoll", "The wiki's 2-tick spot (2 birds); Monkey Madness I; jungle spiders"),
        ("Prifddinas", "2-tick with rabbits (they can't hit you); Song of the Elves"),
        ("Hardwood Grove", "9 teaks; 100 trading sticks an entry (free with the elite Karamja diary); logs sent to the bank for 10 sticks each"),
        ("Fossil Island", "Grown teaks (35 Farming): the 1.5-tick spot, fastest Woodcutting in the game; Bone Voyage"),
    ],
    "Mahogany tree": [("Hardwood Grove", "With the teaks; trading sticks"), ("Fossil Island", "Grown mahoganies; Bone Voyage")],
    "Oak tree": [("Varrock", "3 oaks south-west of Varrock West bank"), ("Draynor Village", "Next to the bank"),
                 ("Seers' Village", "4 by the bank")],
    "Willow tree": [("Draynor Village", "South-west of the bank (dark wizards nearby)"),
                    ("Barbarian Outpost", "The closest willows to a bank")],
    "Maple tree": [("Seers' Village", "North of the bank; +10% XP with the medium Kandarin diary"),
                   ("Mistrock", "Next to a deposit box (Varlamore)")],
    "Yew tree": [("Woodcutting Guild", "60 Woodcutting; invisible +7 boost"), ("Edgeville", "Shortest walk to a bank")],
    "Magic tree": [("Woodcutting Guild", "60 Woodcutting; bank chest"), ("Prifddinas", "Song of the Elves")],
    "Redwood tree": [("Woodcutting Guild", "Upper floors; bank chest; AFK")],
    "Granite rocks": [("Cape Conch mine", "The best 3-tick (3t4g) spot: no desert heat; part of Troubled Tortugans"),
                      ("Desert Quarry", "3-tick (3t4g): fastest Mining XP; desert heat protection needed")],
    "Sandstone rocks": [("Desert Quarry", "3-tick with the granite; desert heat protection needed")],
    "Iron rocks": [("Mining Guild (P2P)", "60 Mining; halved respawns, +7"), ("Legends' Guild mine", "3-tick iron (4 rocks)"),
                   ("Isle of Souls mine", "3-tick iron (4 rocks)")],
    "Gem rocks": [("Shilo Village mine (underground)", "Deposit chest; Shilo Village quest; a charged glory triples gems")],
    "Coal rocks": [("Mining Guild (P2P)", "60 Mining"), ("Lovakite mine", "Near a bank")],
    "Runite rocks": [("Mining Guild", "85 Mining; 6-minute respawn")],
    # Fish (Pay-to-play Fishing training): keyed by the catch
    "Raw shark": [("Fishing Guild", "68 Fishing; +7 invisible boost; bank next to it"), ("Catherby", "Bank nearby")],
    "Raw lobster": [("Catherby", "Bank nearby"), ("Fishing Guild", "68 Fishing; bank next to it"),
                    ("Musa Point", "F2P; deposit box at Port Sarim (boat)")],
    "Raw swordfish": [("Catherby", "Bank nearby"), ("Fishing Guild", "68 Fishing")],
    "Raw tuna": [("Port Piscarilius", "West of the bank: the 2-tick harpoon spot"), ("Catherby", "Bank nearby")],
    "Raw trout": [("Prifddinas", "South-east of the south gate: spots never move"),
                  ("Shilo Village", "Best if banking; Shilo Village quest"), ("Barbarian Village", "F2P; permanent fire")],
    "Raw salmon": [("Prifddinas", "South-east of the south gate: spots never move"),
                   ("Shilo Village", "Best if banking; Shilo Village quest"), ("Barbarian Village", "F2P; permanent fire")],
    "Leaping trout": [("Barbarian Outpost", "Otto's Grotto: the 3-tick and cut-eat spot; games necklace to Barbarian Assault")],
    "Leaping salmon": [("Barbarian Outpost", "Otto's Grotto: the 3-tick and cut-eat spot")],
    "Leaping sturgeon": [("Barbarian Outpost", "Otto's Grotto: the 3-tick and cut-eat spot")],
    "Raw monkfish": [("Piscatoris Fishing Colony", "Swan Song; fairy ring AKQ; bank in the colony")],
    "Raw karambwan": [("North-eastern Karamja", "North of fairy ring DKP: spots never move; Tai Bwo Wannai Trio")],
    "Raw anglerfish": [("Port Piscarilius", "South of Tynan's; sandworms")],
    "Raw dark crab": [("Wilderness Resource Area", "Entry fee (less with the Wilderness diary); PKers")],
    "Raw shrimps": [("Lumbridge Swamp", "F2P"), ("Draynor Village", "F2P; south of the bank")],
    "Knight of Ardougne": [("East Ardougne", "The market: splashers keep the guards busy; medium Ardougne diary"),
                           ("West Ardougne", "")],
    "Master Farmer": [("Outer Fortis", "Hard Ardougne diary helps"), ("Farming Guild", "")],
    "Amethyst crystals": [("Mining Guild", "92 Mining; AFK")],
}


# Pages the wiki gives no location rows for (oaks are everywhere; some creatures only in the training guides): the
# guides' places (Hunter training, Thieving training, Pay-to-play Woodcutting training)
FALLBACK = {
    "Oak tree": [("Varrock", "3 oaks south-west of Varrock West bank"), ("Draynor Village", "Next to the bank"),
                 ("Seers' Village", "4 by the bank")],
    "Black chinchompa": [("Wilderness Hunter area", "Level 32-36 Wilderness: PKers; Eagles' Peak; hard Wilderness diary helps")],
    "Carnivorous chinchompa": [("Tlati Rainforest", "North-east of Tal Teklan; Children of the Sun")],
    "Chinchompa": [("Piscatoris Hunter area", "Box traps")],
    "Red salamander": [("South of the Ourania Cave", "Net traps; no requirements")],
    "Black salamander": [("Bone Yard", "Wilderness: an extra trap there")],
    "Orange salamander": [("Uzer Hunter area", "Fairy ring DLQ; desert heat protection")],
    "Elf": [("Prifddinas", "Song of the Elves")],
    "Vyre": [("Darkmeyer", "Sins of the Father")],
    "Razor-backed kebbit": [("Piscatoris Hunter area", "Noose wand; ring of pursuit")],
    "Moonlight moth": [("Neypotzli", "The northern Earthbound Cavern; Perilous Moons"),
                       ("Hunter Guild", "The basement: closer to a bank")],
}


def api(params):
    url = API + "?" + urllib.parse.urlencode(dict(params, format="json"))
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.load(resp)
        except Exception:
            time.sleep(2 ** attempt)
    raise RuntimeError("couldn't reach the wiki: " + url)


def wikitext(page):
    data = api({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1})
    return data["parse"]["wikitext"]["*"] if "parse" in data else ""


def plain(text):
    """Wiki markup to plain text: links to their words, bold and templates dropped."""
    text = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = re.sub(r"'''?|<[^>]+>", "", text)
    return re.sub(r"\s+", " ", text).strip()


def templates(text, names):
    """Each {{Name ...}} block (balanced braces) of these template names."""
    out = []
    for m in re.finditer(r"\{\{(" + "|".join(names) + r")\s*\n?\|", text):
        depth, i = 0, m.start()
        while i < len(text):
            if text.startswith("{{", i):
                depth += 1
                i += 2
            elif text.startswith("}}", i):
                depth -= 1
                i += 2
                if depth == 0:
                    break
            else:
                i += 1
        out.append(text[m.start():i])
    return out


def places(page):
    """The page's location rows, merged by place: count, members, a note."""
    text = wikitext(page)
    merged = {}
    for block in templates(text, ["ObjectLocLine", "LocLine"]):
        loc = re.search(r"\|\s*location\s*=\s*(.*)", block)
        if not loc:
            continue
        raw = loc.group(1)
        notes = [plain(n) for n in re.findall(r"<ref[^>]*>(.*?)</ref>", raw)]
        notes += [plain(n) for n in re.findall(r"\{\{refn\|(?:[^|{}]*=[^|{}]*\|)*([^{}]*)\}\}", raw)]
        place = plain(re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>|\{\{refn\|[^{}]*\}\}", "", raw)).strip(" ,;")
        if not place:
            continue
        members = bool(re.search(r"\|\s*members\s*=\s*Yes", block, re.I))
        count = len(re.findall(r"\|\s*(?:x:)?\d{3,5},\s*(?:y:)?\d{3,5}", block)) or 1
        coords = re.search(r"\|\s*(?:x:)?(\d{3,5}),\s*(?:y:)?(\d{3,5})", block)
        entry = merged.setdefault(place, {"place": place, "count": 0, "members": members})
        entry["count"] += count
        if notes and "note" not in entry:
            entry["note"] = notes[0]
        if coords and "x" not in entry:
            entry["x"], entry["y"] = int(coords.group(1)), int(coords.group(2))
    return list(merged.values())


def ordered(page, found):
    """The guide's picks first (with why), then the rest by how many there are; a page with none, the guides' places."""
    fallback = {k.lower(): v for k, v in FALLBACK.items()}.get(page.lower())
    if not found and fallback:
        return [{"place": name, "count": 0, "members": True, "best": why} for name, why in fallback]
    best = {k.lower(): v for k, v in BEST.items()}.get(page.lower(), [])
    out, used = [], set()
    for name, why in best:
        exact = [e for e in found if e["place"] == name and e["place"] not in used]
        loose = [e for e in found if e["place"].startswith(name) and e["place"] not in used]
        for entry in (exact or loose)[:1]:
            out.append(dict(entry, best=why) if why else dict(entry))
            used.add(entry["place"])
    rest = sorted((e for e in found if e["place"] not in used), key=lambda e: (-e["count"], e["place"]))
    return out + rest


def fish_places(things):
    """Fishing: the Fishing spot page's table (each kind of spot, what it catches), each kind's own page for where it
    is (place, how many), merged by catch: "raw shark" gets every place a shark spot is."""
    text = wikitext("Fishing spot")
    start = text.find("!Locations")
    table = text[start:text.find("\n|}", start)]
    by_fish = {}
    for row in table.split("\n|-")[1:]:
        cells = [c for c in re.split(r"\n\|", "\n" + row.strip()) if c.strip()]
        if len(cells) < 3:
            continue
        spot = re.search(r"\[\[([^\]|]+)", cells[0])
        catches = re.findall(r"\{\{plink\|([^}|]+)", cells[2])
        if not spot or not catches:
            continue
        found = places(spot.group(1))
        if not found:
            # (no location rows: the table's own places)
            found = [{"place": plain(p).strip(" ,"), "count": 0, "members": "Free-to-play" not in p}
                     for p in re.split(r"<br\s*/?>", cells[3] if len(cells) > 3 else "") if plain(p).strip()]
        for catch in catches:
            merged = by_fish.setdefault(catch.strip(), {})
            for entry in found:
                have = merged.setdefault(entry["place"], dict(entry, count=0))
                have["count"] += entry.get("count", 0)
        print("fishing %-40s %d places" % (spot.group(1), len(found)), file=sys.stderr)
        time.sleep(0.2)
    for fish, merged in by_fish.items():
        listed = ordered(fish, list(merged.values()))
        if listed:
            things[fish.lower()] = {"name": fish, "skill": "Fishing", "places": listed}


def main():
    with open(os.path.join(RES, "skilling.json")) as f:
        skilling = json.load(f)
    with open(os.path.join(RES, "skill_helpers.json")) as f:
        helpers = json.load(f)
    pages = {}
    for resource in skilling.get("objects", {}).values():
        pages.setdefault(resource["page"], resource["skill"])
    for name in skilling.get("pickpocket", {}):
        # ("bandit (bandit camp)": the page is the name before its brackets)
        pages.setdefault(re.sub(r"\s*\(.*\)$", "", name).capitalize(), "Thieving")
    for name, creature in (helpers.get("hunter") or {}).items():
        if "bird house" not in name and "birdhouse" not in name:
            pages.setdefault(creature.get("name", name), "Hunter")
    things = {}
    fish_places(things)
    for i, (page, skill) in enumerate(sorted(pages.items())):
        found = ordered(page, places(page))
        if found:
            things[page.lower()] = {"name": page, "skill": skill, "places": found}
        print("%3d/%d %-32s %d places" % (i + 1, len(pages), page, len(found)), file=sys.stderr)
        time.sleep(0.2)
    out = {
        "source": "OSRS Wiki (oldschool.runescape.wiki), each thing's page and the skills' training guides; "
                  "CC BY-NC-SA 3.0. Made by scripts/update_training_locations.py",
        "things": things,
    }
    with open(OUT, "w", newline="\n") as f:
        json.dump(out, f, indent=1, sort_keys=True)
        f.write("\n")
    print("wrote %s (%d things)" % (OUT, len(things)))


if __name__ == "__main__":
    main()
