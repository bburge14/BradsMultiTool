#!/usr/bin/env python3
"""Regenerate src/main/resources/com/bradsmultitool/farming_patches.json from RuneLite's source.

Usage: python3 scripts/update_farming_patches.py

RuneLite's Time Tracking plugin (BSD-2-Clause, (c) the RuneLite contributors) knows every farming patch:
which region and varbit it lives in, and how each varbit value maps to a crop, its condition and growth
stage. Those classes are internal to that plugin, and Plugin Hub plugins can't reach them (no reflection),
so this converts them into a data file instead. Re-run after RuneLite updates its farming data.
"""
import json
import os
import re
import urllib.request

BASE = "https://raw.githubusercontent.com/runelite/runelite/master/"
FARMING = BASE + "runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/"
VARBITS = BASE + "runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java"
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "farming_patches.json")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "BradsMultiTool data updater"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8")


def produce(source):
    """ENUM -> {name, patch, tickrate, stages}, from Produce's constructor calls."""
    out = {}
    for m in re.finditer(r"^\t([A-Z_]+)\((.*)\)[,;]?\s*(?://.*)?$", source, re.M):
        args = [a.strip() for a in re.split(r",(?![^(]*\))", m.group(2))]
        name = args[0].strip('"')
        impl = next((a.split(".")[1] for a in args if a.startswith("PatchImplementation.")), None)
        numbers = [a for a in args[1:] if re.fullmatch(r"\d+", a)]
        if impl is None:
            tickrate, stages = int(numbers[0]), int(numbers[1])  # (name, itemID, tickrate, stages)
        else:
            after = args[args.index("PatchImplementation." + impl) + 2:]  # skip itemID
            tickrate, stages = int(after[0]), int(after[1])
        out[m.group(1)] = {"name": name, "patch": impl, "tickrate": tickrate, "stages": stages}
    return out


def linear(expr):
    """'3 + value - 201' -> (1, -198): the stage expressions are sums of whole numbers and 'value'."""
    coef = const = 0
    for sign, term in re.findall(r"([+-]?)\s*(value|\d+)", expr.replace(" ", "")):
        k = -1 if sign == "-" else 1
        if term == "value":
            coef += k
        else:
            const += k * int(term)
    if re.sub(r"[\d\s+\-]|value", "", expr):
        raise ValueError("Unknown stage expression " + expr)
    return coef, const


def implementations(source, crops):
    """IMPL -> [[low, high, PRODUCE, CROPSTATE, a, b], ...] in source order; the stage is a * value + b."""
    out = {}
    blocks = re.split(r"^\t([A-Z_]+)\(Tab\.", source, flags=re.M)
    for name, body in zip(blocks[1::2], blocks[2::2]):
        rules = []
        condition = None
        for line in body.splitlines():
            line = line.strip()
            m = re.match(r"if \(value >= (\d+) && value <= (\d+)\)", line)
            if m:
                condition = (int(m.group(1)), int(m.group(2)))
                continue
            m = re.match(r"if \(value == (\d+)\)", line)
            if m:
                condition = (int(m.group(1)), int(m.group(1)))
                continue
            m = re.match(r"return new PatchState\(Produce\.([A-Z_]+), CropState\.([A-Z]+), (.+)\);", line)
            if m:
                low, high = condition if condition else (0, 1 << 20)
                expr = re.sub(r"Produce\.([A-Z_]+)\.getStages\(\)", lambda g: str(crops[g.group(1)]["stages"]), m.group(3))
                coef, const = linear(expr)
                rules.append([low, high, m.group(1), m.group(2), coef, const])
                condition = None
        out[name] = rules
    return out


def regions(source, varbits):
    """[{name, regionId, extraRegions, patches: [{name, varbit, implementation}]}]"""
    out = []
    for m in re.finditer(r'add\((?:farmingGuildRegion = )?new FarmingRegion\("([^"]+)", (\d+), (?:true|false),(.*?)\)\s*(?:\{.*?\n\t\t\})?(?:, ([\d, ]+))?\);',
                         source, re.S):
        patches = []
        for p in re.finditer(r'new FarmingPatch\("([^"]*)", VarbitID\.([A-Z0-9_]+), PatchImplementation\.([A-Z_]+)', m.group(3)):
            patches.append({"name": p.group(1), "varbit": varbits[p.group(2)], "implementation": p.group(3)})
        extra = [int(x) for x in m.group(4).split(",")] if m.group(4) else []
        out.append({"name": m.group(1), "regionId": int(m.group(2)), "extraRegions": extra, "patches": patches})
    return out


def main():
    varbits = {k: int(v) for k, v in re.findall(r"public static final int ([A-Z0-9_]+) = (\d+);", fetch(VARBITS))}
    crops = produce(fetch(FARMING + "Produce.java"))
    data = {
        "source": "RuneLite Time Tracking plugin (BSD-2-Clause)",
        "produce": crops,
        "implementations": implementations(fetch(FARMING + "PatchImplementation.java"), crops),
        "regions": regions(fetch(FARMING + "FarmingWorld.java"), varbits),
    }
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True)
    print("Wrote %d crops, %d patch types, %d regions (%d patches) to %s" % (
        len(data["produce"]), len(data["implementations"]), len(data["regions"]),
        sum(len(r["patches"]) for r in data["regions"]), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
