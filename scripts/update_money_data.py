"""Regenerate src/main/resources/com/bradsmultitool/money.json from the OSRS Wiki's money making guides.

Usage: python3 scripts/update_money_data.py

Each guide (the 'money_making_guide' bucket) gives the activity, its skill requirements and the wiki's
estimated profit per hour (at the GE prices when this script ran). A guide trains the skill it's listed
under; combat guides train the combat skills they require; farming guides train Farming. Used for the
skill tracker's "Money maker" training suggestion.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from update_skilling_data import api  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "money.json")
# Combat guides train the combat skills they require (Prayer is only needed there, not trained)
COMBAT = ["Attack", "Strength", "Defence", "Ranged", "Magic", "Hitpoints", "Slayer"]


def guides():
    rows, offset = [], 0
    while True:
        query = "bucket('money_making_guide').select('page_name','json').limit(500).offset(%d).run()" % offset
        page = api({"action": "bucket", "query": query}).get("bucket", [])
        rows += page
        if len(page) < 500:
            return rows
        offset += 500


def plain(text):
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text or "")
    return re.sub(r"<[^>]+>|\{\{[^}]*\}\}|'''?", "", text).strip()


def main():
    out = []
    for row in guides():
        data = json.loads(row["json"])
        prices = data.get("prices") or {}
        # Most guides give profit per hour; recurring ones (farming runs) give profit per run
        per_run = "default_value" not in prices
        profit = prices.get("value") if per_run else prices.get("default_value")
        if not isinstance(profit, (int, float)) or profit <= 0:
            continue
        reqs = {}
        for skill, level in re.findall(r'data-skill="([A-Za-z]+)" data-level="(\d+)', data.get("skill") or ""):
            reqs[skill.upper()] = max(reqs.get(skill.upper(), 0), int(level))
        category = data.get("category") or ""
        trains = [s.strip() for s in (data.get("skillcategory") or "").split(",") if s.strip()]
        if not trains and category.startswith("Farming"):
            trains = ["Farming"]
        if not trains and category.startswith("Combat"):
            trains = [s for s in COMBAT if s.upper() in reqs]
        if not trains or category == "Ironman":
            continue
        out.append({"name": plain(data.get("activity")) or row["page_name"].split("/", 1)[-1],
                    "skills": [s.upper() for s in trains], "reqs": reqs, "profit": int(profit), "perRun": per_run,
                    "members": bool(data.get("members"))})
    out.sort(key=lambda g: -g["profit"])
    with open(OUT, "w") as f:
        json.dump({"source": "oldschool.runescape.wiki (money making guides)", "guides": out}, f,
                  separators=(",", ":"), sort_keys=True, ensure_ascii=False)
    print("Wrote %d money making guides to %s" % (len(out), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
