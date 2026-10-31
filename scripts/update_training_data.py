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
from update_skilling_data import api, wikitext  # noqa: E402

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


def category(name):
    """Page titles in a wiki category (main namespace)."""
    out, cont = [], {}
    while True:
        r = api(dict({"action": "query", "list": "categorymembers", "cmtitle": name, "cmlimit": 500, "cmnamespace": 0}, **cont))
        out += [m["title"] for m in r["query"]["categorymembers"]]
        if "continue" not in r:
            return out
        cont = r["continue"]


QUESTS = None
SKILL_NAMES = {"Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic", "Runecraft", "Construction", "Hitpoints",
               "Agility", "Herblore", "Thieving", "Crafting", "Fletching", "Slayer", "Hunter", "Mining", "Smithing",
               "Fishing", "Cooking", "Firemaking", "Woodcutting", "Farming", "Sailing"}


COMPLETION = re.compile(r"\b(after|with|upon)\s+(the\s+)?complet|must\s+(have\s+(been\s+)?)?complet|"
                        r"need(s)?\s+to\s+have\s+complet|having\s+(started|completed)", re.I)


def requirements(body, skill):
    """Quests and other skill levels a method's section says it requires: quest/miniquest links and {{SCP}}
    levels in its sentences about requirements."""
    global QUESTS
    if QUESTS is None:
        QUESTS = {t for t in category("Category:Quests") + category("Category:Miniquests") if "/" not in t}
    quests, skills, started = [], {}, []
    for sentence in re.split(r"(?<=[.!?])\s+|\n", body):
        # Only what the guide says the method requires ("This requires completion of Shades of Mort'ton");
        # sentences about other places or optional unlocks would hide methods you can do
        # Not template fields, bracketed notes about optional gear ("(requires Family Crest)"), or one place
        # among several ("This area requires...")
        # (links are set aside first so "[[Shilo Village (location)|...]]" isn't cut); bullet points are about
        # places and teleports
        links = re.findall(r"\[\[[^\]]*\]\]", sentence)
        for i, link in enumerate(links):
            sentence = sentence.replace(link, "\x00%d\x00" % i, 1)
        sentence = re.sub(r"\([^)]*\)", "", sentence)
        sentence = re.sub(r"\x00(\d+)\x00", lambda m: links[int(m.group(1))], sentence)
        # "requires", "After completion of", "With the completion of", "must have completed"...
        # A sentence about one place among several is skipped, unless it says a quest must be done
        if sentence.lstrip().startswith(("|", "*")) or not REQUIRES.search(sentence) \
                or (re.search(r"\barea\b|\bisland\b|teleport", sentence, re.I) and not COMPLETION.search(sentence)):
            continue
        partial = re.search(r"partial|portion|\bstart(ed|ing)?\b", sentence, re.I) is not None
        for link in re.findall(r"\[\[([^|\]#]+)", sentence):
            link = link.strip()
            if link in QUESTS and link not in quests:
                quests.append(link)
                if partial:
                    started.append(link)
        levels = re.findall(r"\{\{SCP\|([A-Za-z]+)\|(\d+)", sentence)
        # Written out too: "level 79 Sailing", "70 [[Magic]]"
        levels += [(n, l) for l, n in re.findall(r"\b(\d{1,2})\s+\[?\[?([A-Z][a-z]+)", sentence)]
        for name, level in levels:
            if name in SKILL_NAMES and name != skill:
                skills[name.upper()] = max(skills.get(name.upper(), 0), int(level))
    return quests, skills, started


REQUIRES = re.compile(r"requir|" + COMPLETION.pattern, re.I)
ITEM_QUESTS = {}
# Spellbooks and the quest that unlocks them, for methods that only say which spellbook they use
SPELLBOOK_QUESTS = {"lunar spellbook": "Lunar Diplomacy", "lunar spell": "Lunar Diplomacy",
                    "ancient magicks": "Desert Treasure I", "ancient spellbook": "Desert Treasure I"}


def item_quests(name):
    """The quests the wiki's item infobox ties an item to ("Serum 207": Shades of Mort'ton)."""
    if name not in ITEM_QUESTS:
        query = "bucket('infobox_item').select('quest').where('page_name','%s').run()" % name.replace("'", "\\'")
        rows = api({"action": "bucket", "query": query}).get("bucket", [])
        found = []
        for row in rows:
            for quest in re.findall(r"\[\[([^|\]#]+)", str(row.get("quest") or "")):
                if quest.strip() in QUESTS and quest.strip() not in found:
                    found.append(quest.strip())
        ITEM_QUESTS[name] = found
    return ITEM_QUESTS[name]


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
    out = by_level(text, objects, npcs, skill)
    # A guide with only one or two level headings (Herblore) is really organised by section
    return out if len(out) >= 3 else by_heading(text, objects, npcs, skill)


def body_after(text, heading):
    """A heading's own text, up to the next heading of any depth."""
    nxt = re.search(r"\n=", text[heading.end():])
    return text[heading.end():heading.end() + nxt.start()] if nxt else text[heading.end():]


def plain_name(text):
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    return re.sub(r"\{\{[^}]*\}\}", "", text).strip()


def strip_templates(text):
    """Templates removed, nested ones included."""
    for _ in range(4):
        text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    return text


def targets_of(body):
    """What the method trains on, from its section's {{Main|Raw trout|Raw salmon}}: ["Trout", "Salmon"]."""
    found = re.search(r"\{\{\s*Main\s*\|([^}]+)\}\}", body)
    out = []
    for part in (found.group(1).split("|") if found else []):
        part = re.sub(r"#.*", "", part).strip()
        if part and "=" not in part and "/" not in part and "_" not in part:
            part = re.sub(r"^Raw ", "", part)
            out.append(part[0].upper() + part[1:])
    return out


def new_targets(name, found, skill=""):
    """The targets that say something the method's name doesn't ("Fly fishing": trout, salmon; not "Leechfin
    Fishing": leechfin, nor "Trimming sails": sails)."""
    words = set(re.findall(r"[a-z']+", (name + " " + skill).lower()))
    out = []
    for target in found:
        own = [w for w in re.findall(r"[a-z']+", target.lower()) if len(w) > 2]
        if own and not all(w in words or w.rstrip("s") in words or w + "s" in words for w in own):
            out.append(target)
    return out


def summary(body):
    """The section's first sentences as plain text, for the suggestion's hover."""
    text = re.sub(r"\{\|.*?\n\|\}", "", body, flags=re.S)
    text = re.sub(r"<ref[^>]*/>|<ref[^>]*>.*?</ref>", "", text, flags=re.S)
    text = re.sub(r"\[\[File:[^\]]*\]\]", "", text)
    text = strip_templates(text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"<[^>]+>|'{2,}", "", text)
    for para in text.split("\n"):
        para = para.strip()
        if len(para) < 40 or para[0] in "*#{|!:;=" or re.match(r"(For more|See |For in.depth)", para) \
                or "calculator" in para.lower():
            continue
        out = ""
        for sentence in re.split(r"(?<=[.!?])\s+", para):
            if out and len(out) + len(sentence) > 260:
                break
            out = (out + " " + sentence).strip()
        return out
    return ""


def plink(cell):
    found = re.search(r"\{\{[pi]link[tp]?\|([^|}]+)", cell)
    return found.group(1).strip() if found else None


def plinks(cell):
    return [m.strip() for m in re.findall(r"\{\{[pi]link[tp]?\|([^|}]+)", cell)]


def number(cell):
    cell = strip_templates(cell).replace(",", "").strip()
    return float(cell) if re.fullmatch(r"\d+(\.\d+)?", cell) else None


def options(body):
    """A method's table of what to make or use by level (Herblore's potions): the level, the item, what goes into
    it and its XP, from rows whose first cell is a level and whose second links the item."""
    out = []
    for table in re.findall(r"\{\|.*?\n\|\}", body, re.S):
        header = table.split("\n|-", 1)[0]
        if "level" not in header.lower():
            continue
        for row in table.split("\n|-")[1:]:
            cells = [c.strip() for c in re.split(r"\n\||\|\|", "\n" + row.strip()) if c.strip()]
            if len(cells) < 3:
                continue
            # The level ("3", or a range "1–4"), then the first linked item is what's made
            level = re.match(r"\s*(\d+)", strip_templates(cells[0]))
            at = next((i for i in range(1, len(cells)) if plink(cells[i])), None)
            if not level or at is None:
                continue
            level, name = int(level.group(1)), plink(cells[at])
            materials, xp = [], None
            for cell in cells[at + 1:]:
                items = plinks(cell)
                if items:
                    materials.extend(i for i in items if i not in materials)
                    continue
                xp = number(cell)
                if xp is not None:
                    break
            if xp:
                base = re.sub(r"\s*\([^)]*\)", "", name).lower()
                # Not the item itself in another form (the strung bow beside the unstrung one)
                materials = [i for i in materials if re.sub(r"\s*\([^)]*\)", "", i).lower() != base]
                row_quests = [q.strip() for q in re.findall(r"\[\[([^|\]#]+)", row) if q.strip() in QUESTS]
                for quest in item_quests(name):
                    if quest not in row_quests:
                        row_quests.append(quest)
                option = {"level": int(level), "name": re.sub(r"\s*\(\d\)$", "", name), "xp": xp, "with": materials}
                if row_quests:
                    # Making the item is part of its quest, so having started it is enough
                    option["started"] = list(dict.fromkeys(row_quests))
                out.append(option)
    # A by-level list: at least two rows, not all at one level (the cannonball moulds)
    return out if len(out) >= 2 and len({o["level"] for o in out}) > 1 else []


LOCATIONS = None


def locations():
    """Every page the wiki's location infobox is on (towns, dungeons, hunter areas, banks...)."""
    global LOCATIONS
    if LOCATIONS is None:
        LOCATIONS, offset = set(), 0
        while True:
            query = "bucket('infobox_location').select('page_name').limit(1000).offset(%d).run()" % offset
            rows = api({"action": "bucket", "query": query}).get("bucket", [])
            LOCATIONS.update(row["page_name"] for row in rows if row.get("page_name"))
            if len(rows) < 1000:
                return LOCATIONS
            offset += 1000
    return LOCATIONS


def places(body):
    """Where the guide says to do the method: the places in its list of recommended spots, and in sentences like
    "located in [[Kruk's Dungeon]]" or "at the [[Grand Exchange]]"; only links to the wiki's location pages."""
    out = []

    def add(link):
        target, _, label = link.partition("|")
        target = target.split("#")[0].strip()
        label = (label or target).strip()
        if target in locations() and label not in out:
            out.append(re.sub(r"\s*\((location|area)\)$", "", label))

    lines = body.split("\n")
    for i, line in enumerate(lines):
        # A bulleted list introduced by a line about locations or spots: each bullet's first link
        if re.search(r"location|places?\b|spots?\b|areas?\b", line, re.I) and not line.startswith("*"):
            for bullet in lines[i + 1:i + 12]:
                if not bullet.startswith("*"):
                    if bullet.strip():
                        break
                    continue
                links = re.findall(r"\[\[([^\]]+)\]\]", bullet)
                if links:
                    add(links[0])
    # In sentences, not ones about buying, selling, rewards, teleports or unlocks (the Grand Exchange for supplies)
    for sentence in re.split(r"(?<=[.!?])\s+|\n", body):
        if sentence.startswith("*") or re.search(r"buy|sell|purchas|obtain|reward|teleport|unlock|bought|sold|cost|trad",
                                                 sentence, re.I):
            continue
        for m in re.finditer(r"(?:located|found|situated|done|trained|cut|caught|mined)\s+(?:in|at|on|near)\s+"
                             r"(?:the\s+)?(?:[a-z ]{0,25})?\[\[([^\]]+)\]\]", sentence):
            add(m.group(1))
        # Looser "at the [[X]]": not in sentences about what's required (where to show a necklace first)
        if re.search(r"requir", sentence, re.I):
            continue
        for m in re.finditer(r"\b(?:at|in|to)\s+the\s+(?:[a-z]+\s+){0,3}(?:part of the\s+)?\[\[([^\]]+)\]\]", sentence):
            add(m.group(1))
        for m in re.finditer(r"\b(?:travel to|go to|head to|run to|walk to)\s+(?:the\s+)?\[\[([^\]]+)\]\]", sentence):
            add(m.group(1))
    return out[:5]


def entry(name, start, end, fastest, objects, npcs, body="", skill=""):
    ids, names = targets(name, objects, npcs)
    out = {"name": name, "from": start, "to": end, "fastest": fastest}
    # A questing method lists the quests that give XP, not ones it needs: keep those as its XP rewards
    if name.lower().startswith("quest"):
        quests, skills, started = [], {}, []
        requirements("", skill)
        rewards = [q for q in re.findall(r"\[\[([^|\]#]+)", body) if q.strip() in QUESTS and "experience" not in q]
        if rewards:
            out["rewards"] = list(dict.fromkeys(q.strip() for q in rewards))
    else:
        quests, skills, started = requirements(body, skill)
    # A Magic method that is a spell needs its spellbook's quest (elsewhere a spell is an optional extra)
    if skill == "Magic" and not name.lower().startswith("quest"):
        for book, quest in SPELLBOOK_QUESTS.items():
            if book in body.lower() and quest not in quests:
                quests.append(quest)
    if quests:
        out["quests"] = quests
    # Quests the guide only needs started ("partial completion of", "a portion of")
    if started:
        out["started"] = started
    if skills:
        out["skills"] = skills
    trains_on = new_targets(name, targets_of(body), skill)
    if trains_on:
        out["targets"] = trains_on
    about = summary(body)
    if about:
        out["about"] = about
    where = places(body) if not name.lower().startswith("quest") else []
    if where:
        out["places"] = where
    rows = options(body)
    if rows:
        out["options"] = rows
    if ids:
        out["objects"] = ids
    if names:
        out["npcs"] = names
    return out


def by_heading(text, objects, npcs, skill):
    """For guides that don't split by level (Prayer, Herblore, Sailing): each section's methods, all levels.
    A section with method sub-headings gives those; otherwise the section itself is the method."""
    sections, current = [], None
    for m in re.finditer(r"\n(==+)\s*([^=\n]+?)\s*\1", text):
        depth, heading = len(m.group(1)), plain_name(m.group(2))
        if depth == 2:
            current = [heading, [], body_after(text, m)]
            sections.append(current)
        elif depth == 3 and current is not None and not NOT_METHODS.search(heading):
            current[1].append((heading, body_after(text, m)))
    out = []
    for heading, children, body in sections:
        other = re.search(r"other|passive|alternat", heading, re.I) is not None
        names = children if children else ([] if NOT_METHODS.search(heading) or other else [(heading, body)])
        for name, section_body in names:
            level = re.match(r"Levels?\s*(\d+)", name)
            start = int(level.group(1)) if level else 1
            name = re.sub(r"^Levels?\s*[\d/+–\-, ]+:?\s*", "", name)
            if name and not any(e["name"] == name for e in out):
                out.append(entry(name, start, 99, not other and not out, objects, npcs, section_body, skill))
    return out


def by_level(text, objects, npcs, skill):
    out, section = [], ""
    for m in re.finditer(r"\n(==+)\s*([^=\n]+?)\s*\1", text):
        depth, heading = len(m.group(1)), m.group(2)
        found = METHOD.match(heading)
        if depth == 2:
            section = heading.lower()
            # A top-level "Levels 15–99: Tree runs" section is a method of its own (Farming's guide), unless it
            # groups methods under it (Magic's "Levels 1–55: Starting off")
            following = re.search(r"\n(==+)\s*([^=\n]+?)\s*\1", text[m.end():])
            if not found or (following and len(following.group(1)) == 3 and METHOD.match(following.group(2))):
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
        out.append(entry(name, start, end, section.startswith("fastest"), objects, npcs, body_after(text, m), skill))
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
