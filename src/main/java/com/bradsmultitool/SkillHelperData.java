package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Data for the Hunter, Runecraft, Cooking and Agility hover cards (skill_helpers.json, regenerate with
 * scripts/update_skill_helpers.py): from the OSRS Wiki and RuneLite's agility plugin (BSD-2-Clause).
 */
@Slf4j
@Singleton
class SkillHelperData {

    private static final String RESOURCE = "skill_helpers.json";

    static final class Creature {
        final String name;
        final int level;
        final double xp;
        final String trap;

        Creature(JsonObject o) {
            name = o.get("name").getAsString();
            level = o.get("level").getAsInt();
            xp = number(o, "xp");
            trap = text(o, "trap");
        }
    }

    static final class Rune {
        final String name;
        final int level;
        final double xp;
        /** Level for 1x, 2x, 3x... runes per essence. */
        final List<Integer> multiples = new ArrayList<>();

        Rune(JsonObject o) {
            name = o.get("rune").getAsString();
            level = o.get("level").getAsInt();
            xp = number(o, "xp");
            for (JsonElement e : o.getAsJsonArray("multiples")) {
                multiples.add(e.getAsInt());
            }
        }

        /** Runes per essence at this level (at least 1). */
        int perEssence(int runecraftLevel) {
            int count = 0;
            for (int level : multiples) {
                if (runecraftLevel >= level) {
                    count++;
                }
            }
            return Math.max(1, count);
        }

        /** The level for one more rune per essence, or 0 if there isn't one. */
        int nextMultiple(int runecraftLevel) {
            for (int level : multiples) {
                if (level > runecraftLevel) {
                    return level;
                }
            }
            return 0;
        }
    }

    /** Level at which a food stops burning; 100 means it never stops (without other help), 0 unknown. */
    static final class Food {
        final String name;
        final int fire;
        final int range;
        final int gauntlets;

        Food(JsonObject o) {
            name = o.get("name").getAsString();
            fire = (int) number(o, "fire");
            range = (int) number(o, "range");
            gauntlets = (int) number(o, "gauntlets");
        }
    }

    static final class Course {
        final String name;
        final int level;
        final double xpPerLap;

        Course(JsonObject o) {
            name = o.get("name").getAsString();
            level = (int) number(o, "level");
            xpPerLap = number(o, "xpPerLap");
        }
    }

    static final class Shortcut {
        final String name;
        final int level;

        Shortcut(JsonObject o) {
            name = o.get("name").getAsString();
            level = o.get("level").getAsInt();
        }
    }

    /** A sea charting task: where it is, its Sailing level, type and XP. */
    static final class ChartTask {
        final int level;
        final String type;
        final double xp;
        final String task;
        final String sea;
        /** The riddle's answer (mermaid diving tasks), or null. */
        final String answer;
        final String note;
        final int x;
        final int y;

        ChartTask(JsonObject o) {
            level = o.get("level").getAsInt();
            type = o.get("type").getAsString();
            xp = number(o, "xp");
            task = o.get("task").getAsString();
            sea = text(o, "sea");
            answer = text(o, "answer");
            note = text(o, "note");
            x = o.get("x").getAsInt();
            y = o.get("y").getAsInt();
        }

        /** What the task type asks of you, in plain words. */
        String typeDescription() {
            switch (type.toLowerCase()) {
                case "generic": return "Find and chart it";
                case "crate": return "Sample a sealed crate";
                case "spyglass": return "Look with a spyglass";
                case "diving": return "Dive with a mermaid";
                case "current": return "Test the current";
                case "weather": return "Record the weather";
                default: return type;
            }
        }
    }

    static final class Shipwreck {
        final String name;
        final int level;
        final double xp;
        final String salvage;
        final double sortXp;
        final String duration;

        Shipwreck(JsonObject o) {
            name = o.get("name").getAsString();
            level = o.get("level").getAsInt();
            xp = number(o, "xp");
            salvage = text(o, "salvage");
            sortXp = number(o, "sortXp");
            duration = text(o, "duration");
        }
    }

    static final class Shoal {
        final String name;
        final int level;
        final String depths;
        final String bait;
        final String fish;

        Shoal(JsonObject o) {
            name = o.get("name").getAsString();
            level = o.get("level").getAsInt();
            depths = text(o, "depths");
            bait = text(o, "bait");
            fish = text(o, "fish");
        }
    }

    /** A sea monster fought from a boat. */
    static final class SeaMonster {
        static final String[] KEELS = {"No", "Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune", "Dragon"};

        final String name;
        final int combat;
        final int hp;
        final boolean aggressive;
        /** Its max hit on your boat with no keel, then each keel from bronze to dragon. */
        final int[] maxHits;
        /** Bounty task part -> the lowest Sailing level a task for it needs. */
        final Map<String, Integer> bounties = new LinkedHashMap<>();

        SeaMonster(JsonObject o) {
            name = o.get("name").getAsString();
            combat = o.has("combat") ? o.get("combat").getAsInt() : 0;
            hp = o.get("hp").getAsInt();
            aggressive = o.get("aggressive").getAsBoolean();
            JsonArray hits = o.getAsJsonArray("maxHits");
            maxHits = new int[hits.size()];
            for (int i = 0; i < maxHits.length; i++) {
                maxHits[i] = hits.get(i).getAsInt();
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("bounties").entrySet()) {
                bounties.put(e.getKey(), e.getValue().getAsJsonObject().get("level").getAsInt());
            }
        }

        /** The weakest keel it can't damage your boat through ("Adamant keel"), or null if none. */
        String safeKeel() {
            for (int i = 0; i < maxHits.length && i < KEELS.length; i++) {
                if (maxHits[i] == 0) {
                    return i == 0 ? "Any boat" : KEELS[i] + " keel";
                }
            }
            return null;
        }
    }

    /** Something made from materials: a cooked food, a smithed item or a bar. */
    static final class Recipe {
        final String name;
        final int level;
        final double xp;
        /** Material name -> how many one needs. */
        final Map<String, Integer> materials = new LinkedHashMap<>();

        Recipe(String name, int level, double xp) {
            this.name = name;
            this.level = level;
            this.xp = xp;
        }
    }

    private final Map<String, Recipe> cooking = new HashMap<>();
    private final List<Recipe> anvil = new ArrayList<>();
    private final List<Recipe> furnace = new ArrayList<>();
    private final List<ChartTask> chartTasks = new ArrayList<>();
    private final Map<String, Shipwreck> shipwrecks = new HashMap<>();
    private final List<int[]> hooks = new ArrayList<>();
    private final List<String> hookNames = new ArrayList<>();
    private final Map<String, Shoal> shoals = new HashMap<>();
    private final Map<String, SeaMonster> seaMonsters = new HashMap<>();
    private final Map<String, Creature> creatures = new HashMap<>();
    private final Map<String, Rune> runes = new HashMap<>();
    private final Map<Integer, Rune> altars = new HashMap<>();
    private final Map<String, Food> foods = new HashMap<>();
    private final Map<Integer, Course> obstacles = new HashMap<>();
    private final Map<Integer, Shortcut> shortcuts = new HashMap<>();

    void load(Gson gson) {
        creatures.clear();
        cooking.clear();
        anvil.clear();
        furnace.clear();
        chartTasks.clear();
        shipwrecks.clear();
        hooks.clear();
        hookNames.clear();
        shoals.clear();
        seaMonsters.clear();
        runes.clear();
        altars.clear();
        foods.clear();
        obstacles.clear();
        shortcuts.clear();
        try (InputStream in = SkillHelperData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; Hunter/Runecraft/Cooking/Agility hover info disabled", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("hunter").entrySet()) {
                    creatures.put(e.getKey(), new Creature(e.getValue().getAsJsonObject()));
                }
                JsonObject runecraft = root.getAsJsonObject("runecraft");
                for (Map.Entry<String, JsonElement> e : runecraft.getAsJsonObject("runes").entrySet()) {
                    runes.put(e.getKey(), new Rune(e.getValue().getAsJsonObject()));
                }
                for (Map.Entry<String, JsonElement> e : runecraft.getAsJsonObject("altars").entrySet()) {
                    Rune rune = runes.get(e.getValue().getAsString());
                    if (rune != null) {
                        altars.put(Integer.parseInt(e.getKey()), rune);
                    }
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("burn").entrySet()) {
                    foods.put(e.getKey(), new Food(e.getValue().getAsJsonObject()));
                }
                JsonObject agility = root.getAsJsonObject("agility");
                Map<String, Course> courses = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : agility.getAsJsonObject("courses").entrySet()) {
                    courses.put(e.getKey(), new Course(e.getValue().getAsJsonObject()));
                }
                for (Map.Entry<String, JsonElement> e : agility.getAsJsonObject("obstacles").entrySet()) {
                    Course course = courses.get(e.getValue().getAsString());
                    if (course != null) {
                        obstacles.put(Integer.parseInt(e.getKey()), course);
                    }
                }
                JsonObject sailing = root.getAsJsonObject("sailing");
                for (JsonElement e : sailing.getAsJsonArray("charting")) {
                    chartTasks.add(new ChartTask(e.getAsJsonObject()));
                }
                for (Map.Entry<String, JsonElement> e : sailing.getAsJsonObject("shipwrecks").entrySet()) {
                    shipwrecks.put(e.getKey(), new Shipwreck(e.getValue().getAsJsonObject()));
                }
                for (JsonElement e : sailing.getAsJsonArray("hooks")) {
                    hookNames.add(e.getAsJsonObject().get("name").getAsString());
                    hooks.add(new int[]{e.getAsJsonObject().get("level").getAsInt()});
                }
                for (Map.Entry<String, JsonElement> e : sailing.getAsJsonObject("shoals").entrySet()) {
                    shoals.put(e.getKey(), new Shoal(e.getValue().getAsJsonObject()));
                }
                if (sailing.has("monsters")) {
                    for (Map.Entry<String, JsonElement> e : sailing.getAsJsonObject("monsters").entrySet()) {
                        seaMonsters.put(e.getKey(), new SeaMonster(e.getValue().getAsJsonObject()));
                    }
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("cooking").entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Recipe recipe = new Recipe(o.get("name").getAsString(), o.get("level").getAsInt(), number(o, "xp"));
                    recipe.materials.put(e.getKey(), 1);
                    cooking.put(e.getKey(), recipe);
                }
                JsonObject smithing = root.getAsJsonObject("smithing");
                for (JsonElement e : smithing.getAsJsonArray("anvil")) {
                    JsonObject o = e.getAsJsonObject();
                    Recipe recipe = new Recipe(o.get("name").getAsString(), o.get("level").getAsInt(), number(o, "xp"));
                    recipe.materials.put(o.get("bar").getAsString(), o.get("bars").getAsInt());
                    anvil.add(recipe);
                }
                for (JsonElement e : smithing.getAsJsonArray("furnace")) {
                    JsonObject o = e.getAsJsonObject();
                    Recipe recipe = new Recipe(o.get("name").getAsString(), o.get("level").getAsInt(), number(o, "xp"));
                    for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("materials").entrySet()) {
                        recipe.materials.put(m.getKey(), m.getValue().getAsInt());
                    }
                    furnace.add(recipe);
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("shortcuts").entrySet()) {
                    shortcuts.put(Integer.parseInt(e.getKey()), new Shortcut(e.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    /** Creatures caught with this trap ("Net trap", "Deadfall"...), lowest level first. */
    List<Creature> creaturesFor(String trap) {
        List<Creature> out = new ArrayList<>();
        for (Creature creature : creatures.values()) {
            if (creature.trap != null && creature.trap.equalsIgnoreCase(trap)) {
                out.add(creature);
            }
        }
        out.sort((a, b) -> Integer.compare(a.level, b.level));
        return out;
    }

    Creature creature(String npcName) {
        return npcName == null ? null : creatures.get(npcName.toLowerCase());
    }

    Rune altar(int objectId) {
        return altars.get(objectId);
    }

    /**
     * Burn levels for a raw food ("Raw lobster" -> Lobster, "Raw chicken" -> Cooked chicken,
     * "Raw bird meat" -> Roast bird meat), or null.
     */
    Food food(String rawName) {
        if (rawName == null || !rawName.toLowerCase().startsWith("raw ")) {
            return null;
        }
        String cooked = rawName.substring(4).toLowerCase();
        for (String candidate : new String[]{cooked, "cooked " + cooked, "roast " + cooked}) {
            Food food = foods.get(candidate);
            if (food != null) {
                return food;
            }
        }
        return null;
    }

    /** What a raw food cooks into on a range or fire ("Raw lobster" -> Lobster, level 40), or null. */
    Recipe cooks(String rawName) {
        return rawName == null ? null : cooking.get(rawName.toLowerCase());
    }

    /** Anvil items made from this bar, lowest level first. */
    List<Recipe> anvilItems(String bar) {
        List<Recipe> out = new ArrayList<>();
        for (Recipe recipe : anvil) {
            if (recipe.materials.containsKey(bar)) {
                out.add(recipe);
            }
        }
        return out;
    }

    /** Furnace bars, lowest level first. */
    List<Recipe> furnaceBars() {
        return furnace;
    }

    /** The Agility course this obstacle belongs to, or null. */
    Course courseFor(int obstacleId) {
        return obstacles.get(obstacleId);
    }

    /** The charting task nearest this spot, if one is within {@code maxDistance} tiles. */
    ChartTask chartTaskNear(int x, int y, int maxDistance) {
        ChartTask best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ChartTask task : chartTasks) {
            int distance = Math.max(Math.abs(task.x - x), Math.abs(task.y - y));
            if (distance <= maxDistance && distance < bestDistance) {
                best = task;
                bestDistance = distance;
            }
        }
        return best;
    }

    Shipwreck shipwreck(String name) {
        return name == null ? null : shipwrecks.get(name.toLowerCase());
    }

    Shoal shoal(String name) {
        return name == null ? null : shoals.get(name.toLowerCase());
    }

    SeaMonster seaMonster(String name) {
        return name == null ? null : seaMonsters.get(name.toLowerCase());
    }

    /** The best salvaging hook for this Sailing level, or null. */
    String bestHook(int sailingLevel) {
        String best = null;
        for (int i = 0; i < hooks.size(); i++) {
            if (hooks.get(i)[0] <= sailingLevel) {
                best = hookNames.get(i);
            }
        }
        return best;
    }

    Shortcut shortcut(int objectId) {
        return shortcuts.get(objectId);
    }

    private static double number(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : 0;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
