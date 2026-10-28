package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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

    private final Map<String, Creature> creatures = new HashMap<>();
    private final Map<String, Rune> runes = new HashMap<>();
    private final Map<Integer, Rune> altars = new HashMap<>();
    private final Map<String, Food> foods = new HashMap<>();
    private final Map<Integer, Course> courses = new HashMap<>();
    private final Map<Integer, Shortcut> shortcuts = new HashMap<>();

    void load(Gson gson) {
        creatures.clear();
        runes.clear();
        altars.clear();
        foods.clear();
        courses.clear();
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
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("courses").entrySet()) {
                    courses.put(Integer.parseInt(e.getKey()), new Course(e.getValue().getAsJsonObject()));
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("shortcuts").entrySet()) {
                    shortcuts.put(Integer.parseInt(e.getKey()), new Shortcut(e.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
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

    Course course(int regionId) {
        return courses.get(regionId);
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
