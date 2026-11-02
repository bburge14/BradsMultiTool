#!/usr/bin/env python3
"""Regenerate src/main/resources/com/bradsmultitool/calculator.json from RuneLite's Skill Calculator.

Usage: python3 scripts/update_calculator_data.py [--cache DIR]

RuneLite's Skill Calculator plugin (BSD-2-Clause, (c) the RuneLite contributors; runelite-client's
plugins/skillcalculator) lists, for each skill, every action with its level, XP and picture (CalculatorType and the
skills/*Action enums), and the XP bonuses (outfits, diaries, altars...) with their multipliers, which bonuses can be
ticked together (getCanBeStackedWith) and which actions each one doesn't apply to (getExcludedSkillBonuses). Those
are Java classes inside that plugin, which a Plugin Hub plugin can't reach, so this reads their source and writes
the same facts as a data file for Snuffy's Planner's Calculator tab. The few rules written as code (switches over
an action or its method, EnumSet expressions) are worked out by a small interpreter below; an unknown construct
stops the script rather than guessing. Item names come from the OSRS Wiki (the infobox_item bucket) where the
calculator takes them from the game's item definitions. Re-run after RuneLite updates its calculator.

--cache DIR keeps the downloaded source files there (and reads them from there next time).
"""
import json
import os
import re
import sys
import urllib.parse
import urllib.request

BASE = "https://raw.githubusercontent.com/runelite/runelite/master/"
CALC = BASE + "runelite-client/src/main/java/net/runelite/client/plugins/skillcalculator/"
ITEM_IDS = BASE + "runelite-api/src/main/java/net/runelite/api/gameval/ItemID.java"
SPRITE_IDS = BASE + "runelite-api/src/main/java/net/runelite/api/gameval/SpriteID.java"
WIKI = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "BradsMultiTool RuneLite plugin data updater (github.com/bburge14/bradsmultitool)"
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "bradsmultitool", "calculator.json")

cache_dir = None


def fetch(url, cache_name=None):
    if cache_dir and cache_name:
        path = os.path.join(cache_dir, cache_name)
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                return f.read()
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as resp:
        text = resp.read().decode("utf-8")
    if cache_dir and cache_name:
        os.makedirs(cache_dir, exist_ok=True)
        with open(os.path.join(cache_dir, cache_name), "w", encoding="utf-8") as f:
            f.write(text)
    return text


def calculator_source(name):
    return fetch(CALC + name + ".java", name.split("/")[-1] + ".java")


# ---- Java source helpers ----

def strip_comments(text):
    return re.sub(r"/\*.*?\*/|//[^\n]*", " ", text, flags=re.S)


def split_top(text, sep=","):
    """Splits at sep where not inside brackets or a string."""
    parts, depth, cur, i, quote = [], 0, [], 0, None
    while i < len(text):
        c = text[i]
        if quote:
            cur.append(c)
            if c == "\\":
                cur.append(text[i + 1])
                i += 1
            elif c == quote:
                quote = None
        elif c in "\"'":
            quote = c
            cur.append(c)
        elif c in "([{":
            depth += 1
            cur.append(c)
        elif c in ")]}":
            depth -= 1
            cur.append(c)
        elif c == sep and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    parts.append("".join(cur))
    return [p.strip() for p in parts if p.strip()]


def matching(text, start, open_c, close_c):
    """The index just past the bracket that closes the one at text[start]."""
    depth, quote, i = 0, None, start
    while i < len(text):
        c = text[i]
        if quote:
            if c == "\\":
                i += 1
            elif c == quote:
                quote = None
        elif c in "\"'":
            quote = c
        elif c == open_c:
            depth += 1
        elif c == close_c:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise ValueError("unbalanced " + open_c)


def enum_body(source, name):
    m = re.search(r"\benum\s+%s\b[^{]*\{" % name, source)
    if not m:
        raise ValueError("enum %s not found" % name)
    start = m.end() - 1
    return source[start + 1:matching(source, start, "{", "}") - 1]


def enum_constants(body):
    """[(NAME, [args], extra body or '')] up to the ';' that ends the constants."""
    depth, quote, end = 0, None, len(body)
    for i, c in enumerate(body):
        if quote:
            if c == quote and body[i - 1] != "\\":
                quote = None
        elif c in "\"'":
            quote = c
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == ";" and depth == 0:
            end = i
            break
    out = []
    for part in split_top(body[:end]):
        m = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\s*(\((.*?)\))?\s*(\{.*\})?\s*$", part, re.S)
        if not m:
            raise ValueError("can't read enum constant: " + part[:80])
        args = split_top(m.group(3)) if m.group(2) else []
        out.append((m.group(1), args, m.group(4) or ""))
    return out, body[end + 1:]


def fields(rest):
    """The enum's instance fields in order: [(type, name)]."""
    return [(t, n) for t, n in re.findall(r"private\s+final\s+([\w<>]+)\s+(\w+)\s*;", rest)]


def method_body(rest, name):
    m = re.search(r"\b%s\s*\(\s*\)\s*\{" % name, rest)
    if not m:
        return None
    start = m.end() - 1
    return rest[start + 1:matching(rest, start, "{", "}") - 1]


def number(expr):
    expr = re.sub(r"(\d)[fFdD]\b", r"\1", expr.replace("_", ""))
    if not re.fullmatch(r"[\d.\s+\-*/()]+", expr):
        raise ValueError("not a number: " + expr)
    return float(eval(expr, {"__builtins__": {}}))  # digits and arithmetic only (checked above)


def last(name):
    return name.strip().split(".")[-1]


# ---- A small interpreter for the bonus rules (getExcludedSkillBonuses, getCanBeStackedWith) ----

class Env:
    def __init__(self, everything, this, values, statics):
        self.all = everything
        self.this = this
        self.values = values      # field name -> value (enum constant names, booleans)
        self.statics = statics    # static set name -> expression
        self.vars = {}


def eval_set(expr, env):
    expr = expr.strip()
    m = re.fullmatch(r"EnumSet\s*\.\s*(of|allOf|noneOf|complementOf|copyOf)\s*\((.*)\)", expr, re.S)
    if m:
        kind, inner = m.group(1), m.group(2)
        if kind == "allOf":
            return set(env.all)
        if kind == "noneOf":
            return set()
        if kind == "complementOf":
            return set(env.all) - eval_set(inner, env)
        if kind == "copyOf":
            return set(eval_set(inner, env))
        return {env.this if a.strip() == "this" else last(a) for a in split_top(inner)}
    if re.fullmatch(r"Collections\s*\.\s*emptySet\s*\(\s*\)", expr):
        return set()
    if expr in env.vars:
        return set(env.vars[expr])
    if expr in env.statics:
        return eval_set(env.statics[expr], env)
    raise ValueError("unknown set expression: " + expr)


def subject(expr, env):
    expr = expr.strip()
    if expr == "this":
        return env.this
    m = re.fullmatch(r"get(\w+)\s*\(\s*\)", expr)
    name = (m.group(1)[0].lower() + m.group(1)[1:]) if m else expr
    if name not in env.values:
        raise ValueError("unknown switch subject: " + expr)
    return env.values[name]


def condition(expr, env):
    expr = expr.strip()
    m = re.fullmatch(r"this\s*==\s*([\w.]+)", expr)
    if m:
        return env.this == last(m.group(1))
    if expr.startswith("!"):
        return not condition(expr[1:], env)
    if expr in env.values:
        return bool(env.values[expr])
    raise ValueError("unknown condition: " + expr)


class Return(Exception):
    def __init__(self, value):
        self.value = value


class Break(Exception):
    pass


def statements(src):
    """Splits a block into statements: switch/if with their blocks, or plain ones ending in ';'."""
    out, i = [], 0
    src = src.strip()
    while i < len(src):
        while i < len(src) and src[i].isspace():
            i += 1
        if i >= len(src):
            break
        m = re.match(r"(switch|if)\s*\(", src[i:])
        if m:
            p = i + m.end() - 1
            close = matching(src, p, "(", ")")
            j = close
            while src[j].isspace():
                j += 1
            if src[j] == "{":
                end = matching(src, j, "{", "}")
            else:
                end = src.index(";", j) + 1
            k = end
            while k < len(src) and src[k].isspace():
                k += 1
            if m.group(1) == "if" and src.startswith("else", k):
                raise ValueError("else isn't handled")
            out.append((m.group(1), src[p + 1:close - 1], src[j:end]))
            i = end
            continue
        m = re.match(r"(case\s+[\w.]+|default)\s*:", src[i:])
        if m:
            out.append(("label", m.group(1), None))
            i += m.end()
            continue
        end = i
        depth = 0
        while True:
            c = src[end]
            if c in "({":
                depth += 1
            elif c in ")}":
                depth -= 1
            elif c == ";" and depth == 0:
                break
            end += 1
        out.append(("stmt", src[i:end].strip(), None))
        i = end + 1
    return out


def block_of(text):
    text = text.strip()
    return text[1:-1] if text.startswith("{") else text


def run(src, env):
    run_items(statements(src), env)


def run_items(items, env):
    for kind, head, body in items:
        if kind == "if":
            if condition(head, env):
                run(block_of(body), env)
        elif kind == "switch":
            value = subject(head, env)
            inner = statements(block_of(body))
            labels = [h if h == "default" else "case " + last(h[len("case"):]) for k, h, _ in inner if k == "label"]
            target = "case " + value if "case " + value in labels else "default"
            at, seen = None, 0
            for index, (k, h, _) in enumerate(inner):
                if k == "label":
                    if labels[seen] == target:
                        at = index
                    seen += 1
            if at is None:
                continue
            try:
                run_items([item for item in inner[at:] if item[0] != "label"], env)
            except Break:
                pass
        elif kind == "label":
            raise ValueError("case outside a switch")
        else:
            simple(head, env)


def simple(stmt, env):
    if stmt == "break":
        raise Break()
    m = re.fullmatch(r"return\s+(.*)", stmt, re.S)
    if m:
        raise Return(eval_set(m.group(1), env))
    m = re.fullmatch(r"(?:final\s+)?(?:Enum)?Set\s*<\s*\w+\s*>\s+(\w+)\s*=\s*(.*)", stmt, re.S)
    if m:
        env.vars[m.group(1)] = eval_set(m.group(2), env)
        return
    m = re.fullmatch(r"(\w+)\s*\.\s*(remove|add)\s*\(\s*([\w.]+)\s*\)", stmt)
    if m and m.group(1) in env.vars:
        name = env.this if m.group(3) == "this" else last(m.group(3))
        (env.vars[m.group(1)].discard if m.group(2) == "remove" else env.vars[m.group(1)].add)(name)
        return
    raise ValueError("unknown statement: " + stmt)


def evaluate(body, env, default):
    if body is None:
        return set(default)
    try:
        run(body, env)
    except Return as r:
        return r.value
    raise ValueError("rule without a return")


def static_sets(rest):
    out = {}
    for m in re.finditer(r"static\s+final\s+(?:Enum)?Set\s*<\s*\w+\s*>\s+(\w+)\s*=\s*", rest):
        end = m.end()
        depth = 0
        while True:
            c = rest[end]
            if c in "({":
                depth += 1
            elif c in ")}":
                depth -= 1
            elif c == ";" and depth == 0:
                break
            end += 1
        out[m.group(1)] = rest[m.end():end]
    return out


# ---- Ids ----

def item_ids():
    # (only the class's own constants: its nested Cert and Placeholder classes reuse the names)
    return {m.group(1): int(m.group(2)) for m in
            re.finditer(r"^\tpublic static final int (\w+)\s*=\s*(\d+)\s*;", fetch(ITEM_IDS, "ItemID.java"), re.M)}


def sprite_ids():
    """'Magicon.WIND_STRIKE' -> id: each nested class's numbers, and its names for them."""
    source = strip_comments(fetch(SPRITE_IDS, "SpriteID.java"))
    out = {}
    for m in re.finditer(r"public static final class (\w+)\s*\{", source):
        body = source[m.end():matching(source, m.end() - 1, "{", "}") - 1]
        local = {}
        for n, v in re.findall(r"public static final int (\w+)\s*=\s*(\w+)\s*;", body):
            local[n] = int(v) if v.isdigit() else local.get(v)
        for n, v in local.items():
            if v is not None:
                out[m.group(1) + "." + n] = v
    for n, v in re.findall(r"^\tpublic static final int (\w+)\s*=\s*(\d+)\s*;", source, re.M):
        out[n] = int(v)
    return out


def wiki_names():
    """Item id -> name, from every item infobox on the wiki."""
    names, offset = {}, 0
    while True:
        query = "bucket('infobox_item').select('item_name','item_id').limit(5000).offset(%d).run()" % offset
        url = WIKI + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query})
        rows = json.loads(fetch(url, "wiki-items-%d.json" % offset)).get("bucket", [])
        for row in rows:
            for i in row.get("item_id") or []:
                try:
                    if row.get("item_name"):
                        names.setdefault(int(i), row["item_name"])
                except (TypeError, ValueError):
                    pass
        if len(rows) < 5000:
            return names
        offset += 5000


def pretty(constant):
    """OAK_LOGS -> 'Oak logs' (only when the wiki has no name for the item)."""
    words = constant.lower().split("_")
    return " ".join(words).capitalize()


# ---- The calculator ----

def arg_value(arg, ids, sprites):
    a = arg.strip()
    if a.startswith('"'):
        return "string", json.loads(a)
    if a in ("true", "false"):
        return "boolean", a == "true"
    if a == "null":
        return "null", None
    if a.startswith("ItemID."):
        return "item", ids[a[len("ItemID."):]]
    if a.startswith("SpriteID."):
        return "sprite", sprites[a[len("SpriteID."):]]
    if re.fullmatch(r"[A-Z]\w*\.[A-Z_0-9]+", a):
        return "enum", last(a)
    return "number", number(a)


def fits(kind, field_type):
    if kind == "null":
        return field_type == "String"
    return {"string": field_type == "String", "boolean": field_type == "boolean",
            "item": field_type == "int", "sprite": field_type == "int",
            "number": field_type in ("int", "float", "double"),
            "enum": field_type not in ("int", "float", "double", "boolean", "String")}[kind]


def assign(args, field_list, ids, sprites):
    """Constructor arguments to fields: in order, skipping fields an overloaded constructor leaves out (null/false)."""
    values = [arg_value(a, ids, sprites) for a in args]
    out, i = {}, 0
    for field_type, name in field_list:
        if i < len(values) and fits(values[i][0], field_type):
            out[name] = values[i][1]
            i += 1
        else:
            out[name] = False if field_type == "boolean" else None
    if i != len(values):
        raise ValueError("arguments %s don't fit fields %s" % (args, field_list))
    return out


def bonuses_of(enum_name):
    source = strip_comments(calculator_source("skills/" + enum_name))
    constants, rest = enum_constants(enum_body(source, enum_name))
    ids = [c[0] for c in constants]
    stack_rule = method_body(rest, "getCanBeStackedWith")
    out = []
    for name, args, _ in constants:
        stacks = evaluate(stack_rule, Env(ids, name, {}, static_sets(rest)), set())
        out.append({"id": name, "name": json.loads(args[0]), "value": round(number(args[1]), 6),
                    "stacks": [b for b in ids if b in stacks and b != name]})
    return out


def actions_of(enum_name, bonus_ids, ids, sprites, names):
    source = strip_comments(calculator_source("skills/" + enum_name))
    constants, rest = enum_constants(enum_body(source, enum_name))
    field_list = fields(rest)
    rule = method_body(rest, "getExcludedSkillBonuses")
    statics = static_sets(rest)
    out = []
    for constant, args, extra in constants:
        values = assign(args, field_list, ids, sprites)
        override = re.search(r"getName\s*\([^)]*\)\s*\{\s*return\s+(\"(?:\\.|[^\"])*\")\s*;", extra)
        icon = values.get("itemId") if values.get("itemId") is not None else values.get("icon")
        name = (json.loads(override.group(1)) if override else values.get("name") or values.get("nameOverride")
                or (names.get(icon) if icon is not None else None) or pretty(constant))
        action = {"name": name, "level": int(values["level"]), "xp": round(float(values["xp"]), 4)}
        if icon is not None:
            action["icon"] = icon
        if values.get("sprite") is not None:
            action["sprite"] = values["sprite"]
        if bonus_ids:
            excluded = evaluate(rule, Env(bonus_ids, constant, values, statics), set())
            unknown = excluded - set(bonus_ids)
            if unknown:
                raise ValueError("%s.%s excludes unknown bonuses %s" % (enum_name, constant, unknown))
            if excluded:
                action["noBonus"] = [b for b in bonus_ids if b in excluded]
        out.append(action)
    return out


def main():
    global cache_dir
    if "--cache" in sys.argv:
        cache_dir = sys.argv[sys.argv.index("--cache") + 1]
    calc_type = strip_comments(calculator_source("CalculatorType"))
    entries = re.findall(r"^\s*([A-Z_]+)\s*\(\s*Skill\.(\w+)\s*,\s*(null|(\w+)\.values\(\))\s*,\s*(\w+)\.values\(\)\s*\)",
                         calc_type, re.M)
    if not entries:
        sys.exit("No skills found in CalculatorType")
    ids, sprites, names = item_ids(), sprite_ids(), wiki_names()
    skills = {}
    for _, skill, _, bonus_enum, action_enum in entries:
        bonuses = bonuses_of(bonus_enum) if bonus_enum else []
        actions = actions_of(action_enum, [b["id"] for b in bonuses], ids, sprites, names)
        skills[skill] = {"bonuses": bonuses, "actions": actions}
        print("%-12s %3d actions, %d bonuses" % (skill, len(actions), len(bonuses)))
    data = {"source": "RuneLite's Skill Calculator (runelite-client plugins/skillcalculator, BSD-2-Clause, "
                      "(c) the RuneLite contributors); item names from the OSRS Wiki",
            "skills": skills}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print("Wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
