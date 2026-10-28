package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Skilling resources from the OSRS Wiki (skilling.json, regenerate with scripts/update_skilling_data.py):
 * trees, rocks and stalls by object id, pickpocket targets by NPC name, plus fishing methods and tool tiers.
 */
@Slf4j
@Singleton
class SkillingData {

    private static final String RESOURCE = "skilling.json";

    static final class Resource {
        final String name;
        final String skill;
        final int level;
        final double xp;
        /** Woodcutting: ticks of chopping before the tree falls (Forestry), 0 if unknown or one log. */
        final int chopTicks;
        final double respawnSeconds;
        final int xpPerHour;

        Resource(JsonObject o) {
            name = o.get("page").getAsString();
            skill = o.get("skill").getAsString();
            level = o.has("level") ? o.get("level").getAsInt() : 0;
            xp = o.has("xp") ? o.get("xp").getAsDouble() : 0;
            chopTicks = o.has("chopTicks") ? o.get("chopTicks").getAsInt() : 0;
            respawnSeconds = o.has("respawnSeconds") ? o.get("respawnSeconds").getAsDouble() : 0;
            xpPerHour = o.has("xpPerHour") ? o.get("xpPerHour").getAsInt() : 0;
        }
    }

    static final class Pickpocket {
        final int level;
        final double xp;
        /** Level at which pickpocketing never fails while wearing the Ardougne cloak 3+, 0 if unknown. */
        final int ardyCloakLevel;
        final String stunDamage;

        Pickpocket(JsonObject o) {
            level = o.get("level").getAsInt();
            xp = o.get("xp").getAsDouble();
            ardyCloakLevel = o.has("ardyCloakLevel") && !o.get("ardyCloakLevel").isJsonNull() ? o.get("ardyCloakLevel").getAsInt() : 0;
            stunDamage = o.has("stun") ? o.get("stun").getAsString() : "";
        }
    }

    static final class Fish {
        final String name;
        final int level;
        final double xp;

        Fish(String name, int level, double xp) {
            this.name = name;
            this.level = level;
            this.xp = xp;
        }
    }

    /** A tool tier, e.g. Rune axe at level 41. */
    static final class Tool {
        final String name;
        final String keyword;
        final int level;

        Tool(String name, String keyword, int level) {
            this.name = name;
            this.keyword = keyword;
            this.level = level;
        }
    }

    // Fishing spot option -> what it catches (OSRS Wiki; common spots)
    private static final Map<String, List<Fish>> FISHING = new HashMap<>();
    private static final Map<String, String> FISHING_TOOL = new HashMap<>();

    static {
        FISHING.put("net", Arrays.asList(new Fish("Shrimps", 1, 10), new Fish("Anchovies", 15, 40)));
        FISHING.put("small net", FISHING.get("net"));
        FISHING.put("bait", Arrays.asList(new Fish("Sardine", 5, 20), new Fish("Herring", 10, 30), new Fish("Pike", 25, 60)));
        FISHING.put("lure", Arrays.asList(new Fish("Trout", 20, 50), new Fish("Salmon", 30, 70)));
        FISHING.put("cage", Collections.singletonList(new Fish("Lobster", 40, 90)));
        FISHING.put("harpoon", Arrays.asList(new Fish("Tuna", 35, 80), new Fish("Swordfish", 50, 100), new Fish("Shark", 76, 110)));
        FISHING.put("big net", Arrays.asList(new Fish("Mackerel", 16, 20), new Fish("Cod", 23, 45), new Fish("Bass", 46, 100)));
        FISHING.put("use-rod", Arrays.asList(new Fish("Leaping trout", 48, 50), new Fish("Leaping salmon", 58, 70), new Fish("Leaping sturgeon", 70, 80)));
        FISHING_TOOL.put("net", "Small fishing net");
        FISHING_TOOL.put("small net", "Small fishing net");
        FISHING_TOOL.put("bait", "Fishing rod + bait");
        FISHING_TOOL.put("lure", "Fly fishing rod + feathers");
        FISHING_TOOL.put("cage", "Lobster pot");
        FISHING_TOOL.put("harpoon", "Harpoon");
        FISHING_TOOL.put("big net", "Big fishing net");
        FISHING_TOOL.put("use-rod", "Barbarian rod + bait");
    }

    private static final List<Tool> AXES = tiers("axe");
    private static final List<Tool> PICKAXES = tiers("pickaxe");

    private static List<Tool> tiers(String kind) {
        String k = " " + kind;
        return Arrays.asList(
            new Tool("Bronze" + k, "bronze" + k, 1), new Tool("Iron" + k, "iron" + k, 1),
            new Tool("Steel" + k, "steel" + k, 6), new Tool("Black" + k, "black" + k, 11),
            new Tool("Mithril" + k, "mithril" + k, 21), new Tool("Adamant" + k, "adamant" + k, 31),
            new Tool("Rune" + k, "rune" + k, 41), new Tool("Dragon" + k, "dragon" + k, 61),
            new Tool("Crystal" + k, "crystal" + k, 71));
    }

    private final Map<Integer, Resource> objects = new HashMap<>();
    // Same resources by lowercase name, for object ids the wiki doesn't list
    private final Map<String, Resource> objectsByName = new HashMap<>();
    private final Map<String, Pickpocket> pickpockets = new HashMap<>();

    void load(Gson gson) {
        objects.clear();
        objectsByName.clear();
        pickpockets.clear();
        try (InputStream in = SkillingData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; skilling hover info disabled", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("objects").entrySet()) {
                    Resource resource = new Resource(e.getValue().getAsJsonObject());
                    objects.put(Integer.parseInt(e.getKey()), resource);
                    objectsByName.putIfAbsent(resource.name.toLowerCase(), resource);
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("pickpocket").entrySet()) {
                    pickpockets.put(e.getKey(), new Pickpocket(e.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    /** By object id, falling back to the object's name (e.g. "Oak tree") when the id isn't listed. */
    Resource object(int objectId, String objectName) {
        Resource resource = objects.get(objectId);
        if (resource == null && objectName != null) {
            resource = objectsByName.get(objectName.toLowerCase());
        }
        return resource;
    }

    Pickpocket pickpocket(String npcName) {
        return npcName == null ? null : pickpockets.get(npcName.toLowerCase());
    }

    static boolean isFishingOption(String option) {
        return FISHING.containsKey(option.toLowerCase());
    }

    static List<Fish> fishFor(String option) {
        return FISHING.getOrDefault(option.toLowerCase(), Collections.emptyList());
    }

    static String fishingTool(String option) {
        return FISHING_TOOL.get(option.toLowerCase());
    }

    /** Axe tiers for Woodcutting, pickaxe tiers for Mining, otherwise empty. */
    static List<Tool> toolsFor(String skill) {
        if ("Woodcutting".equals(skill)) {
            return AXES;
        }
        if ("Mining".equals(skill)) {
            return PICKAXES;
        }
        return Collections.emptyList();
    }

    /** Best tool tier usable at {@code level}, or null. */
    static Tool bestToolForLevel(List<Tool> tools, int level) {
        Tool best = null;
        for (Tool tool : tools) {
            if (tool.level <= level) {
                best = tool;
            }
        }
        return best;
    }

    /** Best tool tier among the given item names (lowercase), or null. */
    static Tool bestOwnedTool(List<Tool> tools, List<String> itemNames) {
        Tool best = null;
        for (Tool tool : tools) {
            for (String name : itemNames) {
                if (name.startsWith(tool.keyword)) {
                    best = tool;
                }
            }
        }
        return best;
    }
}
