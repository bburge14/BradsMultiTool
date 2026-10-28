#!/usr/bin/env python3
"""Regenerate src/main/resources/com/bradsmultitool/monsters.json from the OSRS Wiki.

Usage: python3 scripts/update_monster_data.py
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
PAGE_SIZE = 1000
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "monsters.json")

# Order matters: the plugin reads each monster as an array in this order.
FIELDS = [
    "hitpoints", "defence_level", "magic_level",
    "stab_defence_bonus", "slash_defence_bonus", "crush_defence_bonus", "magic_defence_bonus",
    "light_range_defence_bonus", "standard_range_defence_bonus", "heavy_range_defence_bonus",
    "elemental_weakness", "elemental_weakness_percent", "is_aggressive", "attribute", "attack_style",
]
# Index 15 (after the fields above): the wiki's aggression condition, e.g.
# "Yes, unless wearing a Bandos-affiliated item"; null when it's a plain Yes or No.
# Indexes 16-18: Slayer level needed (null if none), Slayer XP per kill, Slayer task category ("Banshees").
SLAYER_FIELDS = ["slayer_level", "slayer_experience", "slayer_category"]


def aggro_note(value):
    if not isinstance(value, str):
        return None
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", value)
    text = re.sub(r"<[^>]+>|\{\{[^}]*\}\}", "", text).strip()
    return None if text.lower().rstrip(".") in ("yes", "no", "none", "n/a", "") else text


def fetch(offset):
    select = ",".join("'%s'" % f for f in ["page_name", "id"] + FIELDS + SLAYER_FIELDS)
    query = "bucket('infobox_monster').select(%s).limit(%d).offset(%d).run()" % (select, PAGE_SIZE, offset)
    url = API + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query})
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.load(resp)
    if "error" in data:
        sys.exit("Wiki API error: %s" % data["error"])
    return data["bucket"]


def to_int(value):
    if isinstance(value, list):
        value = value[0] if value else None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def to_float(value):
    if isinstance(value, list):
        value = value[0] if value else None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def first_text(value):
    if isinstance(value, list):
        value = value[0] if value else None
    return re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", value).strip() if isinstance(value, str) and value.strip() else None


def as_list(value):
    """Attack styles as clean strings: drops wiki reference markers, splits <br/> and | lists."""
    if not value:
        return []
    if isinstance(value, str):
        value = [value]
    out = []
    for v in value:
        if not isinstance(v, str):
            continue
        v = re.sub(r"\x7f.*?\x7f", "", v)
        for part in re.split(r"<br\s*/?>|\|", v):
            part = re.sub(r"<[^>]+>", "", part).strip()
            if part and part.lower() not in ("none", "n/a") and part not in out:
                out.append(part)
    return out


def normalize_element(value):
    if not isinstance(value, str) or value.strip().lower() in ("", "none"):
        return None
    return value.strip().capitalize()


def main():
    rows, offset = [], 0
    while True:
        page = fetch(offset)
        rows.extend(page)
        if len(page) < PAGE_SIZE:
            break
        offset += PAGE_SIZE
        time.sleep(1)

    monsters = {}
    for row in rows:
        aggressive = row.get("is_aggressive")
        attributes = row.get("attribute") or []
        if isinstance(attributes, str):
            attributes = [attributes]
        entry = [to_int(row.get(f)) for f in FIELDS[:10]] + [
            normalize_element(row.get("elemental_weakness")),
            to_int(row.get("elemental_weakness_percent")),
            1 if isinstance(aggressive, str) and aggressive.lower().startswith("yes") else 0,
            [a.lower() for a in attributes],
            as_list(row.get("attack_style")),
            aggro_note(aggressive),
            to_int(row.get("slayer_level")),
            to_float(row.get("slayer_experience")),
            first_text(row.get("slayer_category")),
        ]
        ids = row.get("id") or []
        if isinstance(ids, str):
            ids = [ids]
        for npc_id in ids:
            for part in str(npc_id).split(","):
                part = part.strip()
                if part.isdigit() and part not in monsters:
                    monsters[part] = entry

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump({"source": "oldschool.runescape.wiki", "fields": FIELDS, "monsters": monsters},
                  f, separators=(",", ":"), sort_keys=True)
    print("Wrote %d NPC ids from %d wiki rows to %s" % (len(monsters), len(rows), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
