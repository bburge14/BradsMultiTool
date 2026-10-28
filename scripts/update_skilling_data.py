#!/usr/bin/env python3
"""Regenerate src/main/resources/com/bradsmultitool/skilling.json from the OSRS Wiki.

Usage: python3 scripts/update_skilling_data.py

Trees come from the Woodcutting page table (level, XP, chop-down time), rocks from the Rock page table
(level); each tree and rock page gives XP and respawn time. Stalls from the Stall/Thievable table (level, XP, respawn, XP/hr), and
pickpocket targets from the Thieving page table. Object ids come from each page's Infobox Scenery.
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
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "skilling.json")


def api(params):
    url = API + "?" + urllib.parse.urlencode(dict(params, format="json"))
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def wikitext(page):
    data = api({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1})
    if "parse" not in data:
        return ""
    return data["parse"]["wikitext"]["*"]


def object_ids(page):
    query = "bucket('infobox_scenery').select('page_name','object_id').where('page_name','%s').run()" % page.replace("'", "\\'")
    rows = api({"action": "bucket", "query": query}).get("bucket", [])
    ids = []
    for row in rows:
        for i in row.get("object_id") or []:
            if int(i) not in ids:
                ids.append(int(i))
    return ids


def first_table(text, after=None):
    start = text.find(after) if after else 0
    start = text.find("{|", max(start, 0))
    end = text.find("\n|}", start)
    return text[start:end]


def rows_of(table):
    """Table rows as lists of raw cell strings."""
    rows = []
    for raw in table.split("\n|-")[1:]:
        cells = []
        for line in raw.strip("\n").split("\n"):
            if line.startswith("|") and not line.startswith("|}"):
                cells.extend(c.strip() for c in line[1:].split("||"))
            elif cells and not line.startswith("!"):
                cells[-1] += " " + line.strip()
        if cells:
            rows.append(cells)
    return rows


def link_text(cell):
    m = re.search(r"\[\[([^|\]]+)", cell)
    return m.group(1).strip() if m else None


def number(cell):
    m = re.search(r"(\d+(?:\.\d+)?)", re.sub(r"<[^>]+>|\{\{[^}]*\}\}", "", cell).replace(",", ""))
    return float(m.group(1)) if m else None


TIME = re.compile(r"(\d+(?:\.\d+)?)(?:\s*(?:–|-|to|and)\s*(\d+(?:\.\d+)?))?\s*(seconds?|minutes?|ticks?)", re.I)

# Rocks whose pages don't state a respawn time (seconds, commonly cited values)
ROCK_RESPAWN = {"Copper rocks": 2.4, "Tin rocks": 2.4, "Coal rocks": 29.4, "Silver rocks": 59.4, "Gold rocks": 59.4,
                "Mithril rocks": 120, "Adamantite rocks": 240, "Gem rocks": 59.4}


def respawn(text):
    """(min, max) respawn seconds from a page's prose, e.g. "take 12 minutes to respawn" or
    "respawns between 36–60 seconds"; None if the page doesn't say."""
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    for m in re.finditer(r"respawn", text, re.I):
        before = list(TIME.finditer(text[max(0, m.start() - 60):m.start()]))
        after = list(TIME.finditer(text[m.end():m.end() + 60]))
        # Nearest time before the word ("takes 5.4 seconds to respawn"), else the first after it
        for t in ([before[-1]] if before else []) + after[:1]:
            unit = t.group(3).lower()
            if unit.startswith("tick"):
                continue  # the seconds equivalent follows in brackets
            factor = 60 if unit.startswith("min") else 1
            lo = float(t.group(1)) * factor
            hi = float(t.group(2)) * factor if t.group(2) else lo
            return lo, hi
    return None


def with_respawn(entry, text):
    found = respawn(text)
    if found is None and entry["page"] in ROCK_RESPAWN:
        found = (ROCK_RESPAWN[entry["page"]],) * 2
    if found:
        entry["respawnSeconds"] = found[0]
        if found[1] != found[0]:
            entry["respawnMaxSeconds"] = found[1]
    return entry


def trees():
    out = []
    for cells in rows_of(first_table(wikitext("Woodcutting"))):
        if len(cells) < 6:
            continue
        level = number(cells[0])
        page = link_text(cells[2])
        # Some rows merge the resource cells; XP is the first numeric cell after the tree name
        rest = cells[3:]
        xp = None
        chop = None
        for i, cell in enumerate(rest):
            if re.fullmatch(r"\s*\d+(\.\d+)?\s*", cell):
                xp = float(cell)
                chop = rest[i + 1] if i + 1 < len(rest) else None
                break
        if level is None or not page or xp is None:
            continue
        # Forestry trees fall after being chopped for a fixed time, e.g. "50 [[ticks]] (30 seconds)"
        ticks = None
        one_log = bool(chop) and "one log" in chop.lower()
        if chop:
            m = re.search(r"(\d+)\s*\[\[ticks", chop)
            ticks = int(m.group(1)) if m else None
        out.append(with_respawn({"page": page, "skill": "Woodcutting", "level": int(level), "xp": xp, "chopTicks": ticks,
                                 "oneLog": one_log or None},
                                wikitext(page)))
        time.sleep(0.2)
    return out


def rocks():
    out = []
    for cells in rows_of(first_table(wikitext("Rock"))):
        if len(cells) < 2:
            continue
        page, level = link_text(cells[0]), number(cells[1])
        if not page or level is None:
            continue
        text = wikitext(page)
        m = re.search(r"([\d.]+)\s*(?:\[\[)?(?:Mining )?(?:\[\[)?experience", text, re.I)
        xp = float(m.group(1)) if m else None
        out.append(with_respawn({"page": page, "skill": "Mining", "level": int(level), "xp": xp}, text))
        time.sleep(0.2)
    return out


def stalls():
    """Thievable stalls: name, level, XP, respawn and the wiki's XP/hr (page Stall/Thievable)."""
    out = []
    for cells in rows_of(first_table(wikitext("Stall/Thievable"))):
        if len(cells) < 7:
            continue
        m = re.search(r"plinkt\|([^|}]+)", cells[0])
        page = m.group(1).strip() if m else link_text(cells[0])
        level, xp = number(cells[1]), number(cells[2])
        if not page or level is None or xp is None:
            continue
        respawn = re.search(r"([\d.]+)\s*seconds", cells[5])
        xp_hr = number(cells[6].split("<ref")[0])
        out.append({"page": page, "skill": "Thieving", "level": int(level), "xp": xp,
                    "respawnSeconds": float(respawn.group(1)) if respawn else None,
                    "xpPerHour": int(xp_hr) if xp_hr else None})
    return out


def pickpockets():
    out = {}
    for cells in rows_of(first_table(wikitext("Thieving"))):
        if len(cells) < 4:
            continue
        # Name cell: "{{plinkt|Man|pic=...}}/[[Woman]]/[[Citizen (...)|Citizen]]"
        names = re.findall(r"plinkt\|([^|}]+)", cells[0])
        names += [label or page for page, label in re.findall(r"\[\[([^|\]]+)(?:\|([^\]]+))?\]\]", cells[0])]
        level, xp = number(cells[1]), number(cells[2])
        if not names or level is None or xp is None:
            continue
        ardy = number(cells[3])
        stun = cells[4] if len(cells) > 4 else ""
        for raw in names:
            name = raw.strip().lower()
            out[name] = {"level": int(level), "xp": xp, "ardyCloakLevel": int(ardy) if ardy else None,
                         "stun": re.sub(r"<[^>]+>|\[\[|\]\]|\{\{[^}]*\}\}", "", stun).strip()}
    return out


def main():
    objects = {}
    for entry in trees() + rocks() + stalls():
        ids = object_ids(entry["page"])
        time.sleep(0.2)
        for i in ids:
            objects.setdefault(str(i), {k: v for k, v in entry.items() if v is not None})
    data = {"source": "oldschool.runescape.wiki", "objects": objects, "pickpocket": pickpockets()}
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True)
    print("Wrote %d objects and %d pickpocket targets to %s" % (len(objects), len(data["pickpocket"]), os.path.normpath(OUT)))


if __name__ == "__main__":
    if "--peek" in sys.argv:
        print(json.dumps(trees()[:8], indent=1))
        print(list(pickpockets().items())[:6])
    else:
        main()
