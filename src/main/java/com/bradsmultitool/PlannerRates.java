package com.bradsmultitool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Estimated XP rates for the actions the wiki's training guides give no rate for: how many of the action a typical
 * player does in an hour (at a normal pace, banking included), times its XP each. They're averages, not anyone's
 * real speed: the Planner shows them as estimates (~), and a guide's own rate always comes first.
 *
 * <p>The counts come from the usual tick timings (smithing an item takes 5 ticks, cooking or smelting 4, cutting a
 * gem 2...) with a bank trip each inventory, and from the wiki's quoted rates for the methods it does list; they
 * aim to be within 20-30% for an average player. Farming runs and bird houses are timed by their crops and
 * houses, not by the hour, so they get none.
 */
final class PlannerRates {
    private PlannerRates() {
    }

    /** Herbs as Herblore's actions name them (cleaning a grimy one). */
    private static final Set<String> HERBS = new HashSet<>(Arrays.asList("guam leaf", "marrentill", "tarromin",
        "harralander", "ranarr weed", "toadflax", "irit leaf", "avantoe", "kwuarm", "huasca", "snapdragon",
        "cadantine", "lantadyme", "dwarf weed", "torstol"));

    /** Crafting's cut gems. */
    private static final Set<String> GEMS = new HashSet<>(Arrays.asList("opal", "jade", "red topaz", "sapphire",
        "emerald", "ruby", "diamond", "dragonstone", "onyx", "zenyte"));

    /** The agility courses' laps an hour. */
    private static final String[][] LAPS = {
        {"leaping", "900"},
        {"gnome stronghold", "75"}, {"draynor village rooftop", "75"}, {"shayzien basic", "65"},
        {"al kharid rooftop", "60"}, {"varrock rooftop", "50"}, {"penguin", "45"}, {"barbarian outpost", "85"},
        {"canifis rooftop", "80"}, {"shayzien advanced", "60"}, {"ape atoll", "90"}, {"colossal wyrm basic", "70"},
        {"falador rooftop", "45"}, {"wilderness agility course", "85"}, {"seers' village rooftop", "80"},
        {"werewolf", "70"}, {"colossal wyrm advanced", "60"}, {"pollnivneach rooftop", "58"},
        {"dorgesh-kaan", "20"}, {"prifddinas", "50"}, {"rellekka rooftop", "67"}, {"ardougne rooftop", "65"},
        {"sepulchre floor 1", "100"}, {"sepulchre floor 2", "70"}, {"sepulchre floor 3", "50"},
        {"sepulchre floor 4", "30"}, {"sepulchre floor 5", "18"},
    };

    /** Fish caught an hour (gathered, banked or dropped as people usually do). */
    private static final String[][] FISH = {
        {"leaping", "900"},
        {"shrimps", "900"}, {"karambwanji", "1500"}, {"sardine", "600"}, {"herring", "500"}, {"anchovies", "600"},
        {"mackerel", "350"}, {"trout", "600"}, {"cod", "300"}, {"pike", "400"}, {"slimy eel", "300"},
        {"salmon", "450"}, {"tuna", "250"}, {"cave eel", "300"}, {"rainbow fish", "300"}, {"lobster", "250"},
        {"bass", "300"}, {"swordfish", "180"}, {"monkfish", "290"}, {"karambwan", "600"},
        {"shark", "150"}, {"sea turtle", "200"}, {"infernal eel", "260"}, {"manta ray", "150"}, {"minnow", "1500"},
        {"anglerfish", "125"}, {"dark crab", "300"}, {"sacred eel", "190"},
    };

    /** Ore and the like mined an hour. */
    private static final String[][] ORE = {
        {"clay", "1000"}, {"essence", "1800"}, {"copper", "900"}, {"tin", "900"}, {"limestone", "600"},
        {"barronite", "500"}, {"iron", "1100"}, {"silver", "400"}, {"coal", "400"}, {"sandstone", "1000"},
        {"granite", "1000"}, {"gem rocks", "380"}, {"gold", "400"}, {"calcified", "600"}, {"rubium", "540"}, {"lead", "2400"},
        {"mithril", "200"}, {"adamantite", "120"}, {"runite", "40"}, {"amethyst", "90"},
    };

    /** Logs cut an hour. */
    private static final String[][] LOGS = {
        {"achey", "1000"}, {"oak", "700"}, {"willow", "500"}, {"teak", "700"}, {"jatoba", "400"}, {"juniper", "900"},
        {"bark", "300"}, {"maple", "350"}, {"mahogany", "300"}, {"arctic pine", "600"}, {"yew", "150"},
        {"blisterwood", "450"}, {"sulliusceps", "300"}, {"camphor", "250"}, {"magic", "80"}, {"ironwood", "450"},
        {"redwood", "160"}, {"rosewood", "400"}, {"logs", "1000"},
    };

    /** Creatures caught an hour. */
    private static final String[][] CATCHES = {
        {"bird house", "0"}, {"impling", "150"}, {"chinchompa", "900"}, {"salamander", "450"}, {"lizard", "400"},
        {"crab", "500"}, {"moth", "1100"}, {"monkey", "140"}, {"graahk", "250"}, {"larupia", "250"},
        {"kyatt", "250"}, {"antelope", "250"}, {"kebbit", "300"}, {"jerboa", "300"}, {"fox", "300"},
    };

    /** What a pace saying "the training guide's rate" is called (a step's {@code pace}). */
    static final String GUIDE = "Wiki guide";

    /** A way of doing an action and its count an hour: the normal pace, or a faster one (tick manipulation...). */
    static final class Way {
        final String name;
        final double perHour;
        final String about;

        Way(String name, double perHour, String about) {
            this.name = name;
            this.perHour = perHour;
            this.about = about;
        }
    }

    /** The normal pace's name. */
    static final String NORMAL = "Normal";

    // The faster ways people train some actions: skill, a key in the action's name, the way's name, its count an
    // hour, and what it is
    private static final Object[][] FASTER = {
        {Skill.WOODCUTTING, "teak", "2-tick", 3000.0, "2-tick teaks: tick manipulation on the Fossil Island or Ape Atoll teaks"},
        {Skill.MINING, "granite", "3-tick", 1800.0, "3-tick granite in the Kharidian Desert (waterskins, a herb and tar)"},
        {Skill.MINING, "sandstone", "3-tick", 1700.0, "3-tick sandstone in the Kharidian Desert"},
        {Skill.MINING, "iron ore", "3-tick", 1900.0, "3-tick iron (a herb and tar, or a knife and logs)"},
        {Skill.MINING, "copper", "3-tick", 1900.0, "3-tick copper or tin"},
        {Skill.MINING, "tin ore", "3-tick", 1900.0, "3-tick copper or tin"},
        {Skill.FISHING, "leaping", "3-tick", 1350.0, "3-tick barbarian fishing (a herb and tar, or a knife and logs)"},
        {Skill.COOKING, "karambwan", "1-tick", 4700.0, "1-tick karambwans (cooking a full inventory by clicking each)"},
        {Skill.FLETCHING, " dart", "Click fast", 45000.0, "darts, clicking every tick"},
        {Skill.FLETCHING, " bolts", "Click fast", 30000.0, "bolts, clicking every tick"},
        {Skill.FLETCHING, " arrow", "Click fast", 25000.0, "arrows, clicking every tick"},
    };

    /**
     * The ways of doing an action with their counts an hour: the normal pace first, then any faster ones. Empty when
     * there's no estimate (farming runs, bird houses).
     */
    static List<Way> ways(Skill skill, CalculatorData.Action action, List<RecipeData.Recipe> made) {
        List<Way> out = new java.util.ArrayList<>();
        double normal = perHour(skill, action, made);
        if (normal <= 0) {
            return out;
        }
        out.add(new Way(NORMAL, normal, "a normal pace, banking included"));
        String name = action.name.toLowerCase(Locale.ROOT);
        for (Object[] faster : FASTER) {
            String key = (String) faster[1];
            // (a key starting with a space is the name's last word: " dart" is darts, not dart tips)
            boolean match = key.startsWith(" ") ? name.endsWith(key) || name.endsWith(key + "s") : name.contains(key);
            if (faster[0] == skill && match && (double) faster[3] > normal) {
                out.add(new Way((String) faster[2], (double) faster[3], (String) faster[4]));
            }
        }
        return out;
    }

    /** The way of this name, or null. */
    static Way way(List<Way> ways, String name) {
        for (Way way : ways) {
            if (way.name.equals(name)) {
                return way;
            }
        }
        return null;
    }

    /**
     * Roughly how many of the action an hour, in the action's own units (a dart, a lap, a cast), or 0 for no
     * estimate. {@code made} are its recipes (for what each one uses up: bars, planks); may be empty.
     */
    static double perHour(Skill skill, CalculatorData.Action action, List<RecipeData.Recipe> made) {
        String name = action.name.toLowerCase(Locale.ROOT);
        switch (skill) {
            case AGILITY:
                return find(LAPS, name, 60);
            case FISHING:
                return find(FISH, name, 300);
            case MINING:
                return find(ORE, name, 500);
            case WOODCUTTING:
                return find(LOGS, name, 300);
            case HUNTER:
                return find(CATCHES, name, 300);
            case SMITHING:
                return smithing(name, action, made);
            case HERBLORE:
                if (HERBS.contains(name)) {
                    return 5000;
                }
                return name.contains("tar") && !name.contains("(") ? 2000 : 2500;
            case COOKING:
                if (name.contains("wine")) {
                    return 2200;
                }
                if (name.endsWith("ale") || name.contains("cider") || name.contains("stout") || name.contains("mind bomb")
                    || name.contains("bitter") || name.contains("mead") || name.contains("folly") || name.contains("respite")
                    || name.contains("delight")) {
                    return 100;
                }
                return 1300;
            case FIREMAKING:
                if (name.contains("sacred oil")) {
                    return 500;
                }
                return name.contains("pyre") ? 300 : 1300;
            case PRAYER:
                if (name.startsWith("ensouled")) {
                    return 250;
                }
                if (name.contains("shard")) {
                    return 12000;
                }
                if (name.contains("remains")) {
                    return 1000;
                }
                return name.contains("ashes") ? 1800 : 1600;
            case CRAFTING:
                return crafting(name);
            case FLETCHING:
                return fletching(name);
            case MAGIC:
                return magic(name);
            case RUNECRAFT:
                return runecraft(name);
            case THIEVING:
                return thieving(name);
            case CONSTRUCTION:
                return construction(name, action, made);
            case SAILING:
                if (name.contains("salvage")) {
                    return 500;
                }
                if (name.contains("trial")) {
                    return 12;
                }
                return name.contains("bounty") ? 6 : 0;
            default:
                // Farming is timed by its crops, combat by its monsters
                return 0;
        }
    }

    // The first entry whose key is in the name (the tables put the longer, likelier keys first where it matters)
    private static double find(String[][] table, String name, double otherwise) {
        for (String[] entry : table) {
            if (name.contains(entry[0])) {
                return Double.parseDouble(entry[1]);
            }
        }
        return otherwise;
    }

    // Smelting at a furnace, about 1 bar every 4 ticks with banking; at an anvil every item takes 5 ticks and an
    // inventory holds 27 bars (a bank trip of about 12 seconds), so items of more bars come slower
    private static double smithing(String name, CalculatorData.Action action, List<RecipeData.Recipe> made) {
        if (name.endsWith(" bar")) {
            return 1200;
        }
        if (name.contains("cannonball")) {
            return 1000;
        }
        int bars = 0;
        for (RecipeData.Recipe recipe : made) {
            int count = 0;
            for (Map.Entry<String, Integer> material : recipe.materials.entrySet()) {
                if (material.getKey().toLowerCase(Locale.ROOT).endsWith(" bar")) {
                    count += material.getValue();
                }
            }
            if (count > 0) {
                bars = count / Math.max(1, PlannerCalculator.perMake(recipe, action));
                break;
            }
        }
        if (bars <= 0) {
            return 900;
        }
        return 3600 / (3.0 + 12.0 * bars / 27);
    }

    private static double crafting(String name) {
        if (GEMS.contains(name)) {
            return 2700;
        }
        if (name.contains("bird house")) {
            return 0;
        }
        if (name.contains("battlestaff")) {
            return 2700;
        }
        if (name.contains("bolt tips") || name.contains("arrowtips") || name.contains("javelin tips") || name.contains("dart tip")) {
            return 2400;
        }
        if (name.contains("ring") || name.contains("necklace") || name.contains("bracelet") || name.contains("amulet")
            || name.contains("tiara") || name.contains("symbol")) {
            return 1400;
        }
        if (name.contains("d'hide") || name.contains("leather") || name.contains("snakeskin") || name.contains("coif")
            || name.contains("vambraces")) {
            return name.contains("vambraces") ? 2300 : 1700;
        }
        if (name.contains("glass") || name.contains("vial") || name.contains("orb") || name.contains("lantern")
            || name.contains("lamp") || name.contains("fishbowl") || name.contains("lens")) {
            return 1600;
        }
        if (name.contains("wool") || name.contains("string") || name.contains("yarn") || name.contains("bolt of")
            || name.contains("rope")) {
            return 1500;
        }
        if (name.contains("pot") || name.contains("bowl") || name.contains("dish")) {
            return 1000;
        }
        return 1200;
    }

    // Ammo comes 10 or 15 at a go, about a go every tick or two while you keep clicking
    private static double fletching(String name) {
        if (name.endsWith(" dart") || name.equals("atlatl dart")) {
            return 15000;
        }
        if (name.contains("bolts (unf)")) {
            return 1200;
        }
        if (name.endsWith(" bolts")) {
            return 10000;
        }
        if (name.contains("arrow shaft") || name.contains("dart shaft")) {
            return 12000;
        }
        if (name.contains("arrow") || name.contains("javelin") || name.contains("dart tips")) {
            return 9000;
        }
        if (name.contains("vale totem")) {
            return 0;
        }
        if (name.endsWith("(u)") && !name.contains("crossbow")) {
            return 1600;
        }
        if (name.contains("crossbow")) {
            return 2000;
        }
        if (name.endsWith("bow")) {
            return 2500;
        }
        if (name.contains("shield")) {
            return 900;
        }
        return 1600;
    }

    private static double magic(String name) {
        if (name.contains("alchemy") || name.contains("superheat")) {
            return 1200;
        }
        if (name.contains("enchant") && name.contains("bolt")) {
            return 1300;
        }
        if (name.contains("reanimation")) {
            return 400;
        }
        if (name.contains("teleport") || name.contains("tele group")) {
            return 1300;
        }
        if (name.contains("plank make") || name.contains("string jewellery") || name.contains("spin flax")
            || name.contains("tan leather") || name.contains("bake pie")) {
            return 1200;
        }
        if (name.contains("superglass") || name.contains("humidify") || name.contains("charge") && name.contains("orb")) {
            return 1000;
        }
        return 1000;
    }

    // Essence an hour (each crafts one rune's XP)
    private static double runecraft(String name) {
        if (name.contains("tiara")) {
            return 1500;
        }
        if (name.contains("core")) {
            return 300;
        }
        if (name.contains("zeah") || name.contains("soul")) {
            return 1600;
        }
        if (name.contains("wrath")) {
            return 5000;
        }
        if (name.contains("aether")) {
            return 4000;
        }
        if (name.contains("mist") || name.contains("dust") || name.contains("mud") || name.contains("smoke")
            || name.contains("steam") || name.contains("lava")) {
            return 3000;
        }
        return 2200;
    }

    private static double thieving(String name) {
        if (name.contains("sq'irkjuice")) {
            return 20;
        }
        if (name.contains("stall")) {
            return name.contains("fruit") ? 1400 : 800;
        }
        if (name.contains("chest") || name.contains("safe")) {
            return 300;
        }
        if (name.contains("knight")) {
            return 2700;
        }
        if (name.equals("elf") || name.equals("vyre")) {
            return 800;
        }
        return 2200;
    }

    // Building with a butler goes at about 4,000 planks an hour, so furniture of more planks comes slower
    private static double construction(String name, CalculatorData.Action action, List<RecipeData.Recipe> made) {
        if (name.contains("bone")) {
            return 0;
        }
        // (the two quick ones people spam: dungeon doors and mounted mythical capes)
        if (name.equals("oak door")) {
            return 900;
        }
        if (name.contains("mythical cape")) {
            return 1100;
        }
        // (and none goes past about 600k an hour: a gold sink or a throne takes a while)
        double most = 600_000.0 / Math.max(1, action.xp);
        for (RecipeData.Recipe recipe : made) {
            int planks = 0;
            for (Map.Entry<String, Integer> material : recipe.materials.entrySet()) {
                if (material.getKey().toLowerCase(Locale.ROOT).contains("plank")) {
                    planks += material.getValue();
                }
            }
            if (planks > 0) {
                return Math.min(most, Math.min(1200, 4000.0 / planks));
            }
        }
        // (a few big ones, a throne or a mounted cape, come slower)
        return Math.min(600, 400_000.0 / Math.max(1, action.xp));
    }
}
