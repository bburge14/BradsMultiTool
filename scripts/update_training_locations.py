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
        ("Prifddinas", "Safe: rabbits can't hit you (2-tick with them); Song of the Elves"),
        ("Hardwood Grove", "9 teaks; 100 trading sticks an entry (free with the elite Karamja diary); logs sent to the bank for 10 sticks each"),
        ("Fossil Island", "Grown teaks (35 Farming): the 1.5-tick spot, fastest Woodcutting in the game; Bone Voyage"),
        # (players find the aggressive jungle spiders keep them in combat: the wiki's 2-tick spot, but last)
        ("Ape Atoll", "Aggressive jungle spiders keep you in combat (poison, max hit 10): the wiki's 2-tick spot, but only once they leave you alone; Monkey Madness I"),
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


# Areas that always need a quest to reach, whatever their location row says (Song of the Elves for Prifddinas...):
# a place whose name has the key needs the quest (True: only started)
REGIONS = [
    ("Prifddinas", "Song of the Elves", False), ("Trahaearn", "Song of the Elves", False),
    ("Ape Atoll", "Monkey Madness I", True), ("Kruk's Dungeon", "Monkey Madness II", True),
    ("Fossil Island", "Bone Voyage", False), ("Tar Swamp", "Bone Voyage", False), ("House on the Hill", "Bone Voyage", False),
    ("Mushroom Forest", "Bone Voyage", False), ("Volcanic Mine", "Bone Voyage", False),
    ("Kharazi Jungle", "Legends' Quest", True), ("Legends' Guild", "Legends' Quest", False),
    ("Shilo Village", "Shilo Village", False), ("Piscatoris Fishing Colony", "Swan Song", False),
    ("Darkmeyer", "Sins of the Father", True), ("Vampyrium", "The Blood Moon Rises", False),
    ("Lletya", "Mourning's End Part I", True), ("Iorwerth", "Regicide", False), ("Isafdar", "Regicide", False),
    ("Zul-Andra", "Regicide", False), ("Port Tyras", "Regicide", False), ("Gwenith", "Regicide", False),
    ("Mos Le'Harmless", "Cabin Fever", False), ("Lunar Isle", "Lunar Diplomacy", False),
    ("Neitiznot", "The Fremennik Isles", True), ("Jatizso", "The Fremennik Isles", True),
    ("Keldagrim", "The Giant Dwarf", True), ("Zanaris", "Lost City", False), ("Lost City", "Lost City", False),
    ("Canifis", "Priest in Peril", False), ("Burgh de Rott", "In Aid of the Myreque", False),
    ("Mort'ton", "Shades of Mort'ton", True), ("Port Phasmatys", "Priest in Peril", False),
    ("Mor Ul Rek", "TzHaar Fight Cave", False), ("Myths' Guild", "Dragon Slayer II", False),
    ("Corsair Cove", "The Corsair Curse", False), ("Waterbirth", "The Fremennik Trials", False),
    ("Hardwood Grove", "Jungle Potion", False), ("Miscellania", "The Fremennik Trials", False),
    ("Etceteria", "Royal Trouble", False), ("Sophanem", "Icthlarin's Little Helper", True),
    # Varlamore
    ("Civitas illa Fortis", "Children of the Sun", False), ("Outer Fortis", "Children of the Sun", False),
    ("Aldarin", "Children of the Sun", False), ("Mistrock", "Children of the Sun", False),
    ("Auburnvale", "Children of the Sun", False), ("Auburn Valley", "Children of the Sun", False),
    ("Kastori", "Children of the Sun", False), ("Nemus Retreat", "Children of the Sun", False),
    ("Tal Teklan", "Children of the Sun", False), ("Tlati Rainforest", "Children of the Sun", False),
    ("Locus Oasis", "Children of the Sun", False), ("Quetzacalli Gorge", "Children of the Sun", False),
    ("Ortus Farm", "Children of the Sun", False), ("Hunter Guild", "Children of the Sun", False),
    ("Sunset Coast", "Children of the Sun", False), ("Cam Torum", "Perilous Moons", True), ("Neypotzli", "Perilous Moons", True),
]

# The quests and miniquests the wiki lists (names matched in a place's notes), longest first
QUESTS = []


def quest_names():
    names = []
    for category in ("Category:Quests", "Category:Miniquests"):
        data = api({"action": "query", "list": "categorymembers", "cmtitle": category, "cmlimit": 500, "cmnamespace": 0})
        for member in data.get("query", {}).get("categorymembers", []):
            title = member["title"]
            if "/" not in title and title not in ("Quests", "Miniquests", "Quest", "Miniquest"):
                names.append(title)
    return sorted(set(names), key=len, reverse=True)


def requirements(place, texts):
    """The quests a place needs: named in its notes ("partially complete Legends' Quest": only started), and its area's."""
    out = {}
    # (the area's own rule first: it knows whether starting the quest is enough)
    for key, quest, started in REGIONS:
        if key.lower() in place.lower():
            out.setdefault(quest, started)
    text = " ".join(t for t in texts if t)
    for name in QUESTS:
        at = text.find(name)
        if at < 0:
            continue
        sentence = text[max(0, text.rfind(".", 0, at)):text.find(".", at) if text.find(".", at) > 0 else len(text)]
        started = bool(re.search(r"partial|start|begun|part of", sentence, re.I))
        out.setdefault(name, started)
        text = text.replace(name, "")
    for key, quest, started in REGIONS:
        if key.lower() in place.lower():
            out.setdefault(quest, started)
    return [{"quest": q, "started": st} for q, st in out.items()]


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
        # Its area on the map (every row of it: west, south, east, north, plane), so the Skilling Helper can tell
        # you're there while you train
        points = [(int(a), int(b)) for a, b in re.findall(r"\|\s*(?:x:)?(\d{3,5}),\s*(?:y:)?(\d{3,5})", block)]
        plane = re.search(r"\|\s*plane\s*=\s*(\d)", block)
        if points:
            xs = [p[0] for p in points]
            ys = [p[1] for p in points]
            box = [min(xs), min(ys), max(xs), max(ys), int(plane.group(1)) if plane else 0]
            old = entry.get("area")
            entry["area"] = box if not old else [min(old[0], box[0]), min(old[1], box[1]), max(old[2], box[2]),
                                                 max(old[3], box[3]), old[4]]
    return list(merged.values())


def ordered(page, found):
    """The guide's picks first (with why), then the rest by how many there are; a page with none, the guides' places."""
    fallback = {k.lower(): v for k, v in FALLBACK.items()}.get(page.lower())
    best = {k.lower(): v for k, v in BEST.items()}.get(page.lower(), [])
    out, used = [], set()
    if not found and fallback:
        out = [{"place": name, "count": 0, "members": True, "best": why} for name, why in fallback]
        best = []
    for name, why in best:
        exact = [e for e in found if e["place"] == name and e["place"] not in used]
        loose = [e for e in found if e["place"].startswith(name) and e["place"] not in used]
        for entry in (exact or loose)[:1]:
            out.append(dict(entry, best=why) if why else dict(entry))
            used.add(entry["place"])
    rest = sorted((e for e in found if e["place"] not in used), key=lambda e: (-e["count"], e["place"]))
    listed = out + rest
    for entry in listed:
        needs = requirements(entry["place"], [entry.get("note"), entry.get("best")])
        if needs:
            entry["quests"] = needs
    return listed


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
                # (one place's spots of each kind: one area round them all)
                if entry.get("area") and have.get("area") and have["area"] is not entry["area"]:
                    a, b = have["area"], entry["area"]
                    have["area"] = [min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3]), a[4]]
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
    QUESTS.extend(quest_names())
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
