package com.snuffys.multitool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        /** Trees felled after one log (regular, dead, evergreen...). */
        final boolean oneLog;
        /** What a thievable stall gives ("Gems", "Raw fish"), or null. */
        final String loot;
        final int xpPerHour;

        Resource(JsonObject o) {
            name = o.get("page").getAsString();
            skill = o.get("skill").getAsString();
            level = o.has("level") ? o.get("level").getAsInt() : 0;
            xp = o.has("xp") ? o.get("xp").getAsDouble() : 0;
            chopTicks = o.has("chopTicks") ? o.get("chopTicks").getAsInt() : 0;
            oneLog = o.has("oneLog") && o.get("oneLog").getAsBoolean();
            loot = o.has("loot") && !o.get("loot").isJsonNull() ? o.get("loot").getAsString() : null;
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
        /** The metal's colour, for the hover card. */
        final Color color;

        Tool(String name, String keyword, int level, Color color) {
            this.name = name;
            this.keyword = keyword;
            this.level = level;
            this.color = color;
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
            new Tool("Bronze" + k, "bronze" + k, 1, new Color(196, 128, 62)),
            new Tool("Iron" + k, "iron" + k, 1, new Color(160, 160, 160)),
            new Tool("Steel" + k, "steel" + k, 6, new Color(215, 222, 230)),
            new Tool("Black" + k, "black" + k, 11, new Color(125, 125, 135)),
            new Tool("Mithril" + k, "mithril" + k, 21, new Color(110, 125, 220)),
            new Tool("Adamant" + k, "adamant" + k, 31, new Color(90, 180, 95)),
            new Tool("Rune" + k, "rune" + k, 41, new Color(80, 200, 220)),
            new Tool("Dragon" + k, "dragon" + k, 61, new Color(230, 55, 45)),
            new Tool("Crystal" + k, "crystal" + k, 71, new Color(190, 240, 255)));
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

    // The same option catches different fish depending on the spot, which its other options give away
    private static final List<Fish> SEA_BAIT = Arrays.asList(new Fish("Sardine", 5, 20), new Fish("Herring", 10, 30));
    private static final List<Fish> RIVER_BAIT = Collections.singletonList(new Fish("Pike", 25, 60));
    private static final List<Fish> TUNA_SWORDFISH = Arrays.asList(new Fish("Tuna", 35, 80), new Fish("Swordfish", 50, 100));
    private static final List<Fish> SHARK = Collections.singletonList(new Fish("Shark", 76, 110));
    private static final List<Fish> MONKFISH = Collections.singletonList(new Fish("Monkfish", 62, 120));

    /**
     * What {@code option} catches at a spot offering {@code spotOptions}: Bait is sardine/herring at sea
     * (Net/Bait spots) but pike in rivers (Lure/Bait); Harpoon is shark at Big Net spots, tuna/swordfish
     * otherwise; Net at a Net/Harpoon spot is monkfish.
     */
    static List<Fish> fishFor(String option, Collection<String> spotOptions) {
        String o = option.toLowerCase();
        Set<String> spot = new HashSet<>();
        for (String s : spotOptions) {
            spot.add(s.toLowerCase());
        }
        switch (o) {
            case "bait":
                return spot.contains("lure") ? RIVER_BAIT : SEA_BAIT;
            case "harpoon":
                return spot.contains("big net") ? SHARK : TUNA_SWORDFISH;
            case "net":
                return spot.contains("harpoon") ? MONKFISH : FISHING.get("net");
            default:
                return FISHING.getOrDefault(o, Collections.emptyList());
        }
    }

    // What you need to carry for each option: every group must be matched by an item starting with one of its names
    private static final Map<String, List<List<String>>> FISHING_NEEDS = new HashMap<>();

    static {
        List<String> net = Collections.singletonList("small fishing net");
        List<String> rod = Collections.singletonList("fishing rod");
        List<String> bait = Arrays.asList("fishing bait", "sandworms");
        FISHING_NEEDS.put("net", Collections.singletonList(net));
        FISHING_NEEDS.put("small net", Collections.singletonList(net));
        FISHING_NEEDS.put("bait", Arrays.asList(rod, bait));
        FISHING_NEEDS.put("lure", Arrays.asList(Arrays.asList("fly fishing rod", "pearl fly fishing rod"),
            Arrays.asList("feather", "stripy feather")));
        FISHING_NEEDS.put("cage", Collections.singletonList(Collections.singletonList("lobster pot")));
        FISHING_NEEDS.put("harpoon", Collections.singletonList(Arrays.asList("harpoon", "barb-tail harpoon",
            "dragon harpoon", "infernal harpoon", "crystal harpoon", "trailblazer harpoon")));
        FISHING_NEEDS.put("big net", Collections.singletonList(Collections.singletonList("big fishing net")));
        FISHING_NEEDS.put("use-rod", Arrays.asList(Collections.singletonList("barbarian rod"),
            Arrays.asList("fishing bait", "feather", "fish offcuts", "roe", "caviar")));
    }

    /** True if the items you carry or wear (lowercase names) cover what this option needs; true if unknown. */
    static boolean hasFishingTool(String option, List<String> owned) {
        List<List<String>> needs = FISHING_NEEDS.get(option.toLowerCase());
        if (needs == null) {
            return true;
        }
        for (List<String> group : needs) {
            boolean found = false;
            for (String item : owned) {
                for (String name : group) {
                    // "harpoon" also matches "dragon harpoon"; "fishing rod" must not match "fly fishing rod"
                    if (item.equals(name) || item.startsWith(name + " (") || (name.equals("harpoon") && item.endsWith(" harpoon"))) {
                        found = true;
                    }
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
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
