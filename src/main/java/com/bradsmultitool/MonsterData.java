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
        // Combat stats are null where the wiki has no value
        final Integer defenceLevel;
        final Integer magicLevel;
        final Integer stabDefence;
        final Integer slashDefence;
        final Integer crushDefence;
        final Integer magicDefence;
        final Integer lightRangedDefence;
        final Integer standardRangedDefence;
        final Integer heavyRangedDefence;
        final MonsterWeakness.Element elementalWeakness;
        final int elementalWeaknessPercent;
        public final boolean aggressive;
        final List<String> attributes;
        /** How the monster attacks, as written on the wiki, e.g. "Slash", "Magic", "Dragonfire". */
        public final List<String> attackStyles;

        private Stats(JsonArray a) {
            hitpoints = intAt(a, 0);
            defenceLevel = nullableIntAt(a, 1);
            magicLevel = nullableIntAt(a, 2);
            stabDefence = nullableIntAt(a, 3);
            slashDefence = nullableIntAt(a, 4);
            crushDefence = nullableIntAt(a, 5);
            magicDefence = nullableIntAt(a, 6);
            lightRangedDefence = nullableIntAt(a, 7);
            standardRangedDefence = nullableIntAt(a, 8);
            heavyRangedDefence = nullableIntAt(a, 9);
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
            List<String> styles = new ArrayList<>();
            if (a.size() > 14 && a.get(14).isJsonArray()) {
                for (JsonElement e : a.get(14).getAsJsonArray()) {
                    styles.add(e.getAsString());
                }
            }
            attackStyles = Collections.unmodifiableList(styles);
        }

        private static int intAt(JsonArray a, int i) {
            Integer value = nullableIntAt(a, i);
            return value != null ? value : 0;
        }

        private static Integer nullableIntAt(JsonArray a, int i) {
            JsonElement e = i < a.size() ? a.get(i) : null;
            return e == null || e.isJsonNull() ? null : e.getAsInt();
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

        if (s.defenceLevel == null || s.magicLevel == null) {
            return MonsterWeakness.unknown(element, elementPercent, note);
        }

        long best = Long.MAX_VALUE;
        MonsterWeakness result = MonsterWeakness.unknown(element, elementPercent, note);

        Long magicRoll = defenceRoll(s.magicLevel, s.magicDefence);
        if (magicRoll != null) {
            best = magicRoll;
            result = MonsterWeakness.magic(element, elementPercent, note);
        }

        String[] meleeNames = {"Stab", "Slash", "Crush"};
        Integer[] meleeBonuses = {s.stabDefence, s.slashDefence, s.crushDefence};
        for (int i = 0; i < meleeNames.length; i++) {
            Long roll = defenceRoll(s.defenceLevel, meleeBonuses[i]);
            if (roll != null && roll < best) {
                best = roll;
                result = MonsterWeakness.melee(meleeNames[i], element, elementPercent, note);
            }
        }

        String[] rangedNames = {"Light", "Standard", "Heavy"};
        Integer[] rangedBonuses = {s.lightRangedDefence, s.standardRangedDefence, s.heavyRangedDefence};
        for (int i = 0; i < rangedNames.length; i++) {
            Long roll = defenceRoll(s.defenceLevel, rangedBonuses[i]);
            if (roll != null && roll < best) {
                best = roll;
                result = MonsterWeakness.ranged(rangedNames[i], element, elementPercent, note);
            }
        }
        return result;
    }

    // Skipped (null) when the wiki has no bonus for this style
    private static Long defenceRoll(int level, Integer bonus) {
        return bonus == null ? null : (long) (level + 9) * (bonus + 64);
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
