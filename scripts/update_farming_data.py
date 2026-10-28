#!/usr/bin/env python3
"""Regenerate src/main/resources/com/bradsmultitool/farming.json from the OSRS Wiki.

Usage: python3 scripts/update_farming_data.py

Reads each "<patch>/Seeds" table (the ones the wiki's Seeds page is built from) for every crop's Farming
level, what a farmer takes to protect it, and its growth time. Patch states themselves come from RuneLite's
Time Tracking data at runtime.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from update_skilling_data import number, rows_of, wikitext  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "farming.json")
PAGES = ["Allotment patch/Seeds", "Flower patch/Seeds", "Herb patch/Seeds", "Hops patch/Seeds", "Bush patch/Seeds",
         "Tree patch/Seeds", "Fruit tree patch/Seeds", "Special patches/Seeds"]


def clean(text):
    text = re.sub(r"\{\{efn[^}]*\}\}", "", text)
    text = re.sub(r"\{\{plinkp\|[^}]*\}\}", "", text)
    text = re.sub(r"\{\{x\}\}", "×", text)
    text = re.sub(r"\{\{NA\|([^}]*)\}\}", r"\1", text)
    text = re.sub(r"\{\{[^}]*\}\}", "", text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"<br\s*/?>|<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def crop_name(cell):
    # "[[Potato seed|Potato]]" -> Potato; "[[Acorn]]" -> Acorn
    links = re.findall(r"\[\[([^|\]]+)(?:\|([^\]]+))?\]\]", cell)
    for page, label in links:
        if not page.startswith("File:"):
            return (label or page).strip()
    return None


def tables(text):
    """Every wikitable on the page."""
    out, start = [], text.find("{|")
    while start >= 0:
        end = text.find("\n|}", start)
        if end < 0:
            break
        out.append(text[start:end])
        start = text.find("{|", end)
    return out


def crops():
    out = {}
    for page in PAGES:
        for cells in (row for table in tables(wikitext(page)) for row in rows_of(table)):
            if len(cells) < 4:
                continue
            level = number(cells[0])
            name = crop_name(cells[1])
            if level is None or not name:
                continue
            growth = payment = None
            for cell in cells[2:]:
                text = clean(cell)
                if growth is None and re.search(r"\bmins?\b|\bminutes?\b|\bhours?\b|\bhrs\b|\bdays?\b", text):
                    total = re.search(r"\(([^)]*)\)", text)
                    growth = (total.group(1) if total else text).strip()
                elif payment is None and growth is None and ("×" in text or re.search(r"free|unprotectable|none", text, re.I)):
                    payment = text
            if name.lower() in out:
                continue
            out[name.lower()] = {"name": name, "level": int(level), "patch": page.split(" patch/")[0].split("/")[0],
                                 "payment": payment, "growth": growth}
    return out


# Wiki seed label -> RuneLite Time Tracking crop name, where they differ
ALIASES = {"acorn": "oak", "giant seaweed": "seaweed", "grapes": "grape", "poison ivy seed": "poison ivy",
           "gout tuber": "goutweed"}


def keyed_by_runelite_name(found):
    """Key crops the way RuneLite names them ("Teak", "Palm", "Spirit tree"), keeping the wiki name too."""
    out = {}
    for key, crop in found.items():
        out[key] = crop
        out.setdefault(ALIASES.get(key, key), crop)
        if key.endswith(" tree"):
            out.setdefault(key[:-len(" tree")], crop)
    return out


def main():
    data = {"source": "oldschool.runescape.wiki", "crops": keyed_by_runelite_name(crops())}
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True, ensure_ascii=False)
    print("Wrote %d crops to %s" % (len(data["crops"]), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
