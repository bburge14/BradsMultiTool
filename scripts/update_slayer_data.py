"""Regenerate src/main/resources/com/bradsmultitool/slayer.json from the OSRS Wiki.

Usage: python3 scripts/update_slayer_data.py

Slayer masters (combat/Slayer level, quest, where they are and the fastest ways there) come from the
"Slayer master" page. Each task's places to kill it (multicombat, cannonable, safespots, notes) come from
its "Slayer task/<name>" page, and how to reach each of those places from the place's own page.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from update_skilling_data import api, rows_of, wikitext  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "slayer.json")
TRAVEL_HEADINGS = re.compile(r"^=+\s*(Transportation|Getting there|Travel|Access|How to get there|Getting to [^=]+|Location)\s*=+\s*$",
                             re.I | re.M)
MAX_ROUTES = 4


def plain(text):
    """Wikitext to plain text: links to their label, templates and tags dropped."""
    text = re.sub(r"\{\{[Ff]airycode\|([^}|]+)[^}]*\}\}", lambda m: m.group(1).upper(), text)
    text = re.sub(r"\{\{FloorNumber\|uk=(\d+)\}\}", r"floor \1", text)
    text = re.sub(r"\{\{SCP\|[^|}]+\|(\d+)[^}]*\}\}", r"\1", text)
    text = re.sub(r"\[\[File:[^\]]*\]\]", "", text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"<ref[^>]*/>|<ref[^>]*>.*?</ref>", "", text, flags=re.S)
    for _ in range(3):
        text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = re.sub(r"<[^>]+>|'''?", "", text)
    return re.sub(r"\s+", " ", text).strip(" .:")


def first_link(text):
    m = re.search(r"\[\[([^|\]#]+)", text)
    return m.group(1).strip() if m else None


def strip_templates(text, name):
    """Remove every {{name ...}} template, nested braces included."""
    out, i = [], 0
    while True:
        start = text.find("{{" + name, i)
        if start < 0:
            return "".join(out) + text[i:]
        out.append(text[i:start])
        depth, j = 0, start
        while j < len(text):
            if text.startswith("{{", j):
                depth += 1
                j += 2
            elif text.startswith("}}", j):
                depth -= 1
                j += 2
                if depth == 0:
                    break
            else:
                j += 1
        i = j


def bullets_after(text, start):
    """The bullet list that follows position start (up to the first non-bullet line after it)."""
    lines, seen = [], False
    for line in text[start:].split("\n")[1:]:
        if line.startswith("*"):
            seen = True
            lines.append(plain(line.lstrip("*")))
        elif seen or line.startswith("="):
            break
    return [line for line in lines if line]


def masters():
    text = wikitext("Slayer master")
    table = text[text.find("{|"):text.find("\n|}")]
    out = []
    for cells in rows_of(table):
        if len(cells) < 5:
            continue
        page = first_link(cells[0])
        levels = re.findall(r"\{\{SCP\|(Combat|Slayer)\|(\d+)", cells[1])
        reqs = {skill.lower(): int(level) for skill, level in levels}
        quest = re.search(r"Requires (partial )?completion of \[\[([^|\]]+)", cells[4])
        section = re.search(r"\n==\s*" + re.escape(page) + r"[^=\n]*==\n(.*?)(?=\n==[^=])", text, re.S)
        body = section.group(1) if section else ""
        where = re.search(r"([^.\n]*\b(?:is|are) located[^.\n]*\.)", body) or re.search(r"([^.\n]*can be found[^.\n]*\.)", body)
        route = re.search(r"(fastest|only) ways?[^\n]*", body)
        coords = re.search(r"\|x=(\d+)\|y=(\d+)", body)
        out.append({
            "name": plain(cells[0]).replace(" / ", "/"),
            "combat": reqs.get("combat", 0),
            "slayer": reqs.get("slayer", 0),
            "quest": (("Part of " if quest.group(1) else "") + quest.group(2)) if quest else None,
            "where": plain(where.group(1)) if where else None,
            "routes": bullets_after(body, route.start()) if route else [],
            "routeNote": plain(route.group(0)) if route and not bullets_after(body, route.start()) else None,
            "wilderness": "Wilderness" in cells[4],
            "x": int(coords.group(1)) if coords else None,
            "y": int(coords.group(2)) if coords else None,
        })
    return out


def task_pages():
    data = api({"action": "query", "list": "allpages", "apprefix": "Slayer task/", "apnamespace": 0,
                "aplimit": 500, "apfilterredir": "nonredirects"})
    return [p["title"] for p in data["query"]["allpages"]]


def batch_wikitext(titles):
    """Wikitext of many pages, 50 at a time (redirects followed); {asked title: text}."""
    out = {}
    for i in range(0, len(titles), 50):
        chunk = titles[i:i + 50]
        data = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                    "titles": "|".join(chunk), "redirects": 1})
        query = data.get("query", {})
        target = {t: t for t in chunk}
        for r in query.get("normalized", []) + query.get("redirects", []):
            for asked, now in list(target.items()):
                if now == r["from"]:
                    target[asked] = r["to"]
        texts = {}
        for page in query.get("pages", {}).values():
            revs = page.get("revisions")
            if revs:
                texts[page["title"]] = revs[0]["slots"]["main"]["*"]
        for asked, now in target.items():
            if now.split("#")[0] in texts:
                out[asked] = texts[now.split("#")[0]]
    return out


def yes(cell):
    return "{{yes" in cell.lower()


def task(title, text):
    infobox = re.search(r"\{\{Infobox Slayer(.*?)\n\}\}", text, re.S)
    fields = dict(re.findall(r"\|\s*(\w+)\s*=\s*([^\n]*)", infobox.group(1))) if infobox else {}
    places = []
    # The location comparison table (its heading varies): find it by its columns, read cells by name
    for match in re.finditer(r"\{\|", text):
        table = text[match.start():text.find("\n|}", match.start())]
        header = table.split("\n|-")[0]
        names = [h.strip().lower() for line in header.split("\n") if line.startswith("!")
                 for h in re.sub(r"^![^|\n]*\|(?!\|)", "!", line)[1:].split("!!")]
        if "location" not in names or not any(n.startswith("multi") for n in names):
            continue
        column = {name: i for i, name in enumerate(names)}
        multi = next(i for n, i in column.items() if n.startswith("multi"))
        cannon = next((i for n, i in column.items() if n.startswith("cannon")), None)
        safespot = next((i for n, i in column.items() if n.startswith("safe")), None)
        notes = next((i for n, i in column.items() if n.startswith("note")), None)
        for cells in rows_of(strip_templates(table, "Map")):
            if len(cells) != len(names) or not first_link(cells[column["location"]]):
                continue
            place = cells[column["location"]]
            places.append({"name": plain(place), "page": first_link(place), "multi": yes(cells[multi]),
                           "cannon": cannon is not None and yes(cells[cannon]),
                           "safespot": safespot is not None and yes(cells[safespot]),
                           "note": (plain(cells[notes]).lstrip("* ").replace(" * ", " ") or None)
                           if notes is not None else None})
        break
    return {"name": title.split("/", 1)[1], "slayer": int(re.sub(r"\D", "", plain(fields.get("skillreq", ""))) or 1),
            "combat": int(re.sub(r"\D", "", plain(fields.get("combatreq", ""))) or 0), "places": places}


def routes(text):
    """The first few bullet points of a place page's travel section."""
    m = TRAVEL_HEADINGS.search(text)
    if not m:
        return []
    # Bullets that were only a teleport template lose their name ("— southern camp entrance"): skip them
    return [r for r in bullets_after(text, m.start()) if 15 < len(r) < 160 and not r.startswith("—")][:MAX_ROUTES]


def main():
    titles = task_pages()
    pages = batch_wikitext(titles)
    tasks = {}
    for title in titles:
        if title in pages:
            t = task(title, pages[title])
            tasks[t["name"].lower()] = t
    place_pages = sorted({p["page"] for t in tasks.values() for p in t["places"]})
    texts = batch_wikitext(place_pages)
    travel = {page: routes(texts[page]) for page in place_pages if page in texts and routes(texts[page])}
    data = {"source": "oldschool.runescape.wiki", "masters": masters(), "tasks": tasks, "travel": travel}
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True, ensure_ascii=False)
    print("Wrote %d masters, %d tasks (%d places, %d with travel) to %s" % (
        len(data["masters"]), len(tasks), sum(len(t["places"]) for t in tasks.values()), len(travel),
        os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
