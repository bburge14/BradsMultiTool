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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-NPC combat stats from the OSRS Wiki, bundled as monsters.json
 * (regenerate with scripts/update_monster_data.py).
 */
@Slf4j
public class MonsterData {

    private static final String RESOURCE = "monsters.json";

    public static final class Stats {
        public final int hitpoints;
        final int defenceLevel;
        final int magicLevel;
        final int stabDefence;
        final int slashDefence;
        final int crushDefence;
        final int magicDefence;
        final int lightRangedDefence;
        final int standardRangedDefence;
        final int heavyRangedDefence;
        final MonsterWeakness.Element elementalWeakness;
        final int elementalWeaknessPercent;
        public final boolean aggressive;
        final List<String> attributes;

        private Stats(JsonArray a) {
            hitpoints = intAt(a, 0);
            defenceLevel = intAt(a, 1);
            magicLevel = intAt(a, 2);
            stabDefence = intAt(a, 3);
            slashDefence = intAt(a, 4);
            crushDefence = intAt(a, 5);
            magicDefence = intAt(a, 6);
            lightRangedDefence = intAt(a, 7);
            standardRangedDefence = intAt(a, 8);
            heavyRangedDefence = intAt(a, 9);
            elementalWeakness = elementAt(a, 10);
            elementalWeaknessPercent = intAt(a, 11);
            aggressive = intAt(a, 12) == 1;
            List<String> attrs = new ArrayList<>();
            if (a.size() > 13 && a.get(13).isJsonArray()) {
                for (JsonElement e : a.get(13).getAsJsonArray()) {
                    attrs.add(e.getAsString());
                }
            }
            attributes = Collections.unmodifiableList(attrs);
        }

        private static int intAt(JsonArray a, int i) {
            JsonElement e = i < a.size() ? a.get(i) : null;
            return e == null || e.isJsonNull() ? 0 : e.getAsInt();
        }

        private static MonsterWeakness.Element elementAt(JsonArray a, int i) {
            JsonElement e = i < a.size() ? a.get(i) : null;
            if (e == null || e.isJsonNull()) {
                return null;
            }
            try {
                return MonsterWeakness.Element.valueOf(e.getAsString().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private final Map<Integer, Stats> byId = new HashMap<>();

    public void load(Gson gson) {
        byId.clear();
        try (InputStream in = MonsterData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; monster weaknesses will fall back to name matching", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject monsters = gson.fromJson(reader, JsonObject.class).getAsJsonObject("monsters");
                for (Map.Entry<String, JsonElement> entry : monsters.entrySet()) {
                    byId.put(Integer.parseInt(entry.getKey()), new Stats(entry.getValue().getAsJsonArray()));
                }
            }
            log.debug("Loaded {} monsters from {}", byId.size(), RESOURCE);
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    public Stats get(int npcId) {
        return byId.get(npcId);
    }

    /**
     * The style with the lowest effective defence roll, using the OSRS formula
     * (level + 9) * (bonus + 64): melee and ranged use Defence level, magic uses Magic level.
     */
    public static MonsterWeakness weaknessOf(Stats s) {
        String note = baneNote(s.attributes);
        MonsterWeakness.Element element = s.elementalWeakness;
        int elementPercent = s.elementalWeaknessPercent;

        int meleeLevel = s.defenceLevel + 9;
        long best = (long) (s.magicLevel + 9) * (s.magicDefence + 64);
        MonsterWeakness result = MonsterWeakness.magic(element, elementPercent, note);

        String[] meleeNames = {"Stab", "Slash", "Crush"};
        int[] meleeBonuses = {s.stabDefence, s.slashDefence, s.crushDefence};
        for (int i = 0; i < meleeNames.length; i++) {
            long roll = (long) meleeLevel * (meleeBonuses[i] + 64);
            if (roll < best) {
                best = roll;
                result = MonsterWeakness.melee(meleeNames[i], element, elementPercent, note);
            }
        }

        String[] rangedNames = {"Light", "Standard", "Heavy"};
        int[] rangedBonuses = {s.lightRangedDefence, s.standardRangedDefence, s.heavyRangedDefence};
        for (int i = 0; i < rangedNames.length; i++) {
            long roll = (long) meleeLevel * (rangedBonuses[i] + 64);
            if (roll < best) {
                best = roll;
                result = MonsterWeakness.ranged(rangedNames[i], element, elementPercent, note);
            }
        }
        return result;
    }

    private static String baneNote(List<String> attributes) {
        for (String attribute : attributes) {
            switch (attribute) {
                case "demon": return "Demonbane";
                case "dragon": return "Dragonbane";
                case "undead": return "Salve amulet";
                case "kalphite": return "Keris";
                case "leafy": return "Leaf-bladed";
                case "golem": return "Barronite";
                case "shade": return "Gadderhammer";
                case "vampyre2": return "Silver weapon";
                case "vampyre3": return "Blisterwood";
                default: break;
            }
        }
        return null;
    }
}
