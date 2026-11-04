package com.bradsmultitool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Estimated XP rates for the actions the wiki's training guides give no rate for: how many of the action a player
 * does in an hour, times its XP each. Each action has a normal pace (attentive play, banking included) and, where
 * people train it faster (tick manipulation, clicking every tick, a better setup), those ways too: a milestone's Pace
 * picks one. The Planner shows them as estimates, and a guide's own rate comes first.
 *
 * <p>The counts are the OSRS Wiki's: its training guides (Fletching, Pay-to-play Smithing, Cooking, Crafting, Mining,
 * Woodcutting, Fishing, Firemaking, Magic, Prayer, Runecraft training; Construction, Thieving, Hunter, Herblore and
 * Agility training) and money making guides (their actions per hour), read November 2026; where they give only XP an
 * hour, that over the XP each; where they give a tick timing (an anvil item 5 ticks, cooking 4, leather 3...), from
 * that with a bank trip. Farming runs and bird houses are timed by their crops and houses, not by the hour, so they get
 * none.
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

    /** The agility courses' laps an hour (the wiki's XP an hour over the XP a lap, within its tick-perfect most). */
    private static final String[][] LAPS = {
        {"leaping", "900"},
        {"gnome stronghold", "72"}, {"draynor village rooftop", "79"}, {"shayzien basic", "65"},
        {"al kharid rooftop", "54"}, {"varrock rooftop", "48"}, {"penguin", "45"}, {"barbarian outpost", "85"},
        {"canifis rooftop", "65"}, {"shayzien advanced", "60"}, {"ape atoll", "86"}, {"colossal wyrm basic", "70"},
        {"falador rooftop", "54"}, {"wilderness agility course", "90"}, {"seers' village rooftop", "74"},
        {"werewolf", "80"}, {"colossal wyrm advanced", "60"}, {"pollnivneach rooftop", "55"},
        {"dorgesh-kaan", "20"}, {"prifddinas", "47"}, {"rellekka rooftop", "66"}, {"ardougne rooftop", "77"},
        {"sepulchre floor 1", "100"}, {"sepulchre floor 2", "70"}, {"sepulchre floor 3", "50"},
        {"sepulchre floor 4", "30"}, {"sepulchre floor 5", "18"},
    };

    /** Fish caught an hour (gathered, banked or dropped as people usually do). */
    private static final String[][] FISH = {
        {"leaping", "900"},
        {"shrimps", "900"}, {"karambwanji", "1500"}, {"sardine", "600"}, {"herring", "500"}, {"anchovies", "600"},
        {"mackerel", "350"}, {"trout", "850"}, {"cod", "300"}, {"pike", "400"}, {"slimy eel", "300"},
        {"salmon", "850"}, {"tuna", "250"}, {"cave eel", "300"}, {"rainbow fish", "300"}, {"lobster", "250"},
        {"bass", "300"}, {"swordfish", "180"}, {"monkfish", "325"}, {"karambwan", "700"},
        {"shark", "160"}, {"sea turtle", "200"}, {"infernal eel", "260"}, {"manta ray", "150"}, {"minnow", "1500"},
        {"anglerfish", "160"}, {"dark crab", "308"}, {"sacred eel", "190"},
    };

    /** Ore and the like mined an hour. */
    private static final String[][] ORE = {
        {"clay", "1000"}, {"essence", "1800"}, {"copper", "900"}, {"tin", "900"}, {"limestone", "600"},
        {"barronite", "500"}, {"iron", "1600"}, {"silver", "400"}, {"coal", "425"}, {"sandstone", "1000"},
        {"granite", "1000"}, {"gem rocks", "700"}, {"gold", "400"}, {"calcified", "600"}, {"rubium", "540"}, {"lead", "2400"},
        {"mithril", "200"}, {"adamantite", "120"}, {"runite", "55"}, {"amethyst", "90"},
    };

    /** Logs cut an hour. */
    private static final String[][] LOGS = {
        {"achey", "1000"}, {"oak", "1000"}, {"willow", "1000"}, {"teak", "1000"}, {"jatoba", "400"}, {"juniper", "900"},
        {"bark", "300"}, {"maple", "700"}, {"mahogany", "300"}, {"arctic pine", "600"}, {"yew", "260"},
        {"blisterwood", "450"}, {"sulliusceps", "815"}, {"camphor", "250"}, {"magic", "110"}, {"ironwood", "450"},
        {"redwood", "175"}, {"rosewood", "400"}, {"logs", "1000"},
    };

    /** Creatures caught an hour. */
    private static final String[][] CATCHES = {
        {"bird house", "0"}, {"impling", "150"}, {"black chinchompa", "600"}, {"carnivorous chinchompa", "585"},
        {"chinchompa", "400"}, {"orange salamander", "230"}, {"red salamander", "303"}, {"black salamander", "350"},
        {"salamander", "300"}, {"lizard", "400"}, {"crab", "500"}, {"moth", "1190"}, {"monkey", "140"},
        {"graahk", "250"}, {"larupia", "250"}, {"kyatt", "250"}, {"antelope", "250"}, {"razor-backed kebbit", "373"},
        {"spotted kebbit", "620"}, {"dark kebbit", "585"}, {"dashing kebbit", "575"}, {"kebbit", "300"},
        {"jerboa", "300"}, {"fox", "300"},
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

    // The faster ways people train some actions (the wiki's figures): skill, a key in the action's name, the way's
    // name, its count an hour, and what it is. A key starting with a space is the name's last word (" dart" is darts,
    // not dart tips).
    private static final Object[][] FASTER = {
        {Skill.WOODCUTTING, "teak", "2-tick", 2530.0, "2-tick teaks (tick manipulation)"},
        {Skill.WOODCUTTING, "teak", "1.5-tick", 2765.0, "1.5-tick teaks on Fossil Island"},
        {Skill.WOODCUTTING, "redwood", "1.5-tick", 474.0, "1.5-tick redwoods"},
        {Skill.MINING, "granite", "3-tick", 1900.0, "3-tick granite in the Kharidian Desert (3t4g)"},
        {Skill.MINING, "sandstone", "3-tick", 1700.0, "3-tick sandstone in the Kharidian Desert"},
        {Skill.MINING, "gem rocks", "3-tick", 1650.0, "3-tick gem rocks"},
        {Skill.MINING, "iron ore", "Mining Guild", 2100.0, "iron in the Mining Guild (more rocks, its boost)"},
        {Skill.FISHING, "leaping", "3-tick", 1650.0, "3-tick barbarian fishing"},
        {Skill.FISHING, "trout", "3-tick", 1600.0, "3-tick fly fishing"},
        {Skill.FISHING, "salmon", "3-tick", 1600.0, "3-tick fly fishing"},
        {Skill.FISHING, "tuna", "2-tick", 1500.0, "2-tick harpooning (crystal harpoon)"},
        {Skill.FISHING, "swordfish", "2-tick", 1500.0, "2-tick harpooning (crystal harpoon)"},
        {Skill.FISHING, "karambwan", "Efficient", 900.0, "karambwans with the best setup"},
        {Skill.FISHING, "anglerfish", "Diabolic worms", 290.0, "anglerfish with diabolic worms"},
        {Skill.COOKING, "karambwan", "1-tick", 5000.0, "1-tick karambwans (clicking each in turn)"},
        {Skill.FIREMAKING, "pyre", "1-tick", 4600.0, "1-tick pyre logs"},
        {Skill.FLETCHING, " dart", "Click fast", 150000.0, "darts, 2-3 sets of 10 a tick by clicking"},
        {Skill.FLETCHING, " bolts", "Click fast", 60000.0, "bolts, a set every tick by clicking"},
        {Skill.FLETCHING, " javelin", "1-tick", 90000.0, "1-tick javelins"},
        {Skill.FLETCHING, "(u)", "Fletching knife", 2700.0, "cutting bows with a fletching knife (2 ticks)"},
        {Skill.FLETCHING, "battlestaff", "Fletching knife", 1787.0, "with a fletching knife"},
        {Skill.FLETCHING, "shield", "Fletching knife", 893.0, "with a fletching knife"},
        {Skill.HERBLORE, "stamina", "Tick manipulation", 4583.0, "a potion a tick (27 + 1)"},
        {Skill.HERBLORE, "extended", "Tick manipulation", 4583.0, "a potion a tick (27 + 1)"},
        {Skill.HERBLORE, "anti-venom", "Tick manipulation", 4583.0, "a potion a tick (27 + 1)"},
        {Skill.HERBLORE, "serum 207", "Tick manipulation", 4583.0, "a potion a tick"},
        {Skill.CRAFTING, "d'hide body", "Tick manipulation", 1783.0, "d'hide bodies with tick manipulation"},
        {Skill.MAGIC, "enchant", "Click every tick", 6000.0, "enchanting bolts a cast a tick"},
        {Skill.MAGIC, "plank make", "Manual", 1844.0, "Plank Make clicked by hand, not auto-cast"},
        {Skill.MAGIC, "superglass", "Efficient", 850.0, "Superglass Make with the best setup"},
        {Skill.MAGIC, "cure", "Click every tick", 6000.0, "a cast a tick"},
        {Skill.PRAYER, "shard", "Sunfire wine", 228000.0, "blessed bone shards with sunfire wine"},
        {Skill.THIEVING, "knight", "Tick-perfect", 3000.0, "Ardougne knights tick-perfect (95+)"},
        {Skill.THIEVING, "master farmer", "Efficient", 3000.0, "the best setup"},
        {Skill.THIEVING, "elf", "Efficient", 560.0, "the best setup"},
        {Skill.THIEVING, "vyre", "Efficient", 722.0, "the best setup (99)"},
        {Skill.THIEVING, "bandit", "Blackjacking", 1930.0, "blackjacking"},
        {Skill.THIEVING, "menaphite thug", "Blackjacking", 1930.0, "blackjacking"},
        {Skill.THIEVING, "fruit stall", "Two stalls", 1500.0, "stealing from two stalls"},
        {Skill.HUNTER, "black chinchompa", "Tick manipulation", 840.0, "tick-perfect trap resets"},
        {Skill.HUNTER, "carnivorous chinchompa", "Tick manipulation", 830.0, "tick-perfect trap resets"},
        {Skill.HUNTER, "orange salamander", "Efficient", 650.0, "the best setup"},
        {Skill.HUNTER, "red salamander", "Efficient", 377.0, "the best setup"},
        {Skill.HUNTER, "black salamander", "Efficient", 470.0, "six traps"},
        {Skill.RUNECRAFT, "nature", "Diary cape", 7680.0, "with the Achievement diary cape's teleports"},
        {Skill.RUNECRAFT, "wrath", "Efficient", 5610.0, "the best setup"},
        {Skill.RUNECRAFT, "astral", "Efficient", 6320.0, "at 99, the best setup"},
        {Skill.RUNECRAFT, "lava", "Efficient", 8000.0, "75+, the best setup"},
        {Skill.CONSTRUCTION, "oak door", "Tick-perfect", 917.0, "a door every few ticks with a demon butler"},
        {Skill.CONSTRUCTION, "mythical cape", "Tick-perfect", 1160.0, "with a demon butler"},
        {Skill.CONSTRUCTION, "mahogany table", "Tick-perfect", 1090.0, "a 22-tick cycle of four tables"},
        {Skill.CONSTRUCTION, "gnome bench", "Tick-perfect", 1310.0, "with a demon butler"},
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
            boolean match = key.startsWith(" ") ? name.endsWith(key) || name.endsWith(key + "s") : name.contains(key);
            if (faster[0] == skill && match && (double) faster[3] > normal) {
                out.add(new Way((String) faster[2], (double) faster[3], (String) faster[4]));
            }
        }
        // Smithing at an anvil in the Smiths' uniform (4 ticks an item), and bars at the Blast Furnace
        if (skill == Skill.SMITHING) {
            int bars = barsIn(action, made);
            if (bars > 0) {
                out.add(new Way("Smiths' uniform", 3600 / (2.4 + 0.25 * bars), "the full Smiths' uniform: an item every 4 ticks"));
            } else if (name.endsWith(" bar")) {
                double blast = blastFurnace(name);
                if (blast > normal) {
                    out.add(new Way("Blast Furnace", blast, "at the Blast Furnace (gold with goldsmith gauntlets)"));
                }
            } else if (name.contains("cannonball")) {
                out.add(new Way("Double mould", 1200, "the double ammo mould (8 a bar)"));
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
     * Roughly how many of the action an hour at a normal pace, in the action's own units (a dart, a lap, a cast), or 0
     * for no estimate. {@code made} are its recipes (for what each one uses up: bars, planks); may be empty.
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
                return herblore(name);
            case COOKING:
                if (name.contains("wine")) {
                    return 2350;
                }
                if (name.endsWith("ale") || name.contains("cider") || name.contains("stout") || name.contains("mind bomb")
                    || name.contains("bitter") || name.contains("mead") || name.contains("folly") || name.contains("respite")
                    || name.contains("delight")) {
                    // (brewing: a vat at a time)
                    return 100;
                }
                // 4 ticks each with banking (the wiki: 1,365-1,435 an hour)
                return 1400;
            case FIREMAKING:
                if (name.contains("sacred oil")) {
                    return 500;
                }
                return name.contains("pyre") ? 1470 : 1485;
            case PRAYER:
                if (name.startsWith("ensouled")) {
                    return 220;
                }
                if (name.contains("shard")) {
                    return 150_000;
                }
                if (name.contains("remains")) {
                    return 1000;
                }
                // (bones at an altar, used by hand: the wiki's gilded altar is 2,550, the chaos altar about 2,000)
                return name.contains("ashes") ? 1800 : 2200;
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
                    // (the wiki: 10-30 trials an hour, fewer at the higher ranks)
                    return name.contains("marlin") ? 11 : name.contains("shark") ? 17 : 28;
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

    // The bars one of the action takes (an anvil item), or 0
    private static int barsIn(CalculatorData.Action action, List<RecipeData.Recipe> made) {
        for (RecipeData.Recipe recipe : made) {
            if (!"Anvil".equalsIgnoreCase(recipe.facility == null ? "" : recipe.facility)) {
                continue;
            }
            int count = 0;
            for (Map.Entry<String, Integer> material : recipe.materials.entrySet()) {
                if (material.getKey().toLowerCase(Locale.ROOT).endsWith(" bar")) {
                    count += material.getValue();
                }
            }
            if (count > 0) {
                return Math.max(1, count / Math.max(1, PlannerCalculator.perMake(recipe, action)));
            }
        }
        return 0;
    }

    // At an anvil an item takes 5 ticks and an inventory holds 27 bars: the wiki's rates work out to about 3 seconds an
    // item and a quarter of a second a bar for the banking (1,112 one-bar items an hour, 834 five-bar ones). Smelting
    // at a furnace (Edgeville): the wiki's money making guides' counts; cannonballs 8 ticks a bar
    private static double smithing(String name, CalculatorData.Action action, List<RecipeData.Recipe> made) {
        if (name.endsWith(" bar")) {
            if (name.contains("steel")) {
                return 775;
            }
            if (name.contains("mithril")) {
                return 600;
            }
            if (name.contains("adamant")) {
                return 500;
            }
            if (name.contains("rune") || name.contains("runite")) {
                return 400;
            }
            if (name.contains("bronze")) {
                return 650;
            }
            return 980;
        }
        if (name.contains("cannonball")) {
            return 600;
        }
        int bars = barsIn(action, made);
        if (bars <= 0) {
            return 900;
        }
        return 3600 / (3.0 + 0.25 * bars);
    }

    // The Blast Furnace's bars an hour (the wiki's table); 0 for one it doesn't make
    private static double blastFurnace(String name) {
        if (name.contains("gold")) {
            return 6760;
        }
        if (name.contains("iron")) {
            return 6000;
        }
        if (name.contains("steel")) {
            return 5400;
        }
        if (name.contains("mithril")) {
            return 3600;
        }
        if (name.contains("adamant")) {
            return 2700;
        }
        if (name.contains("rune") || name.contains("runite")) {
            return 2160;
        }
        return 0;
    }

    private static double herblore(String name) {
        if (HERBS.contains(name)) {
            // (cleaning: about 3,000 with auto-clean; clicking each, over 10,000)
            return 3000;
        }
        if (name.contains("tar") && !name.contains("(")) {
            return 2000;
        }
        if (name.contains("guthix rest")) {
            return 1400;
        }
        if (name.contains("super combat")) {
            return 2166;
        }
        if (name.contains("forgotten brew") || name.contains("extended anti-venom+")) {
            return 1833;
        }
        if (name.contains("stamina") || name.contains("extended") || name.contains("anti-venom")) {
            return 2750;
        }
        // 14 and 14 an inventory
        return 2500;
    }

    private static double crafting(String name) {
        if (GEMS.contains(name)) {
            return 2780;
        }
        if (name.contains("bird house")) {
            return 0;
        }
        if (name.contains("battlestaff")) {
            return 2450;
        }
        // Amethyst: about 2,600 cut an hour, each into 15 bolt tips or arrowtips, 8 dart tips or 5 javelin tips
        if (name.contains("amethyst")) {
            int each = name.contains("dart") ? 8 : name.contains("javelin") ? 5 : 15;
            return 2600.0 * each;
        }
        if (name.contains("ring") || name.contains("necklace") || name.contains("bracelet") || name.contains("amulet")
            || name.contains("tiara") || name.contains("symbol")) {
            return 1400;
        }
        // Leather: 3 ticks an item and a bank trip each inventory (the wiki's d'hide bodies: 1,685 an hour)
        if (name.contains("d'hide") || name.contains("leather") || name.contains("snakeskin") || name.contains("coif")
            || name.contains("vambraces")) {
            if (name.contains("vambraces") || name.contains("gloves") || name.contains("boots") || name.contains("bandana")) {
                return 1880;
            }
            return name.contains("body") ? 1695 : 1770;
        }
        if (name.contains("molten glass")) {
            return 1300;
        }
        if (name.contains("glass") || name.contains("vial") || name.contains("orb") || name.contains("lantern")
            || name.contains("lamp") || name.contains("fishbowl") || name.contains("lens")) {
            return 1750;
        }
        if (name.contains("bolt of")) {
            return 1008;
        }
        if (name.contains("wool") || name.contains("string") || name.contains("yarn") || name.contains("rope")) {
            return 1430;
        }
        if (name.contains("drift net")) {
            return 1000;
        }
        if (name.contains("pot") || name.contains("bowl") || name.contains("dish")) {
            return 1000;
        }
        return 1200;
    }

    // Make-X ammo is a set every 2 ticks (3,000 sets an hour: 30,000 bolts, 45,000 arrows); darts are clicked, about a
    // set of 10 a tick (a slow player's adamant darts at 1.3M xp an hour: 87,000)
    private static double fletching(String name) {
        if (name.endsWith(" dart") || name.equals("atlatl dart")) {
            return 75000;
        }
        if (name.contains("bolts (unf)")) {
            return 1060;
        }
        if (name.endsWith(" bolts")) {
            return 30000;
        }
        if (name.contains("arrow shaft") || name.contains("dart shaft")) {
            return 30000;
        }
        if (name.contains("headless")) {
            return 42000;
        }
        if (name.contains("arrow") || name.contains("javelin") || name.contains("dart tips")) {
            return 45000;
        }
        if (name.contains("vale totem")) {
            return 0;
        }
        if (name.contains("battlestaff")) {
            return 1375;
        }
        if (name.contains("shield")) {
            return 778;
        }
        // Cutting: 3 ticks a bow (or stock); stringing: 2 ticks, 14 an inventory
        if (name.endsWith("(u)") || name.contains("stock")) {
            return 1800;
        }
        if (name.endsWith("bow")) {
            return 2450;
        }
        return 1600;
    }

    private static double magic(String name) {
        if (name.contains("high level alchemy")) {
            return 1200;
        }
        if (name.contains("low level alchemy")) {
            return 1935;
        }
        if (name.contains("superheat")) {
            return 1830;
        }
        if (name.contains("enchant") && name.contains("bolt")) {
            return 2000;
        }
        if (name.contains("enchant")) {
            return name.contains("sapphire") || name.contains("opal") ? 1830 : 1600;
        }
        if (name.contains("reanimation")) {
            return 220;
        }
        if (name.contains("teleport") || name.contains("tele group")) {
            return 1440;
        }
        if (name.contains("string jewellery")) {
            return 1807;
        }
        if (name.contains("tan leather")) {
            return 1600;
        }
        if (name.contains("plank make")) {
            return 970;
        }
        if (name.contains("spin flax")) {
            return 1035;
        }
        if (name.contains("bake pie")) {
            return 1885;
        }
        if (name.contains("superglass")) {
            return 600;
        }
        if (name.contains("hunter kit")) {
            return 743;
        }
        if (name.contains("cure")) {
            return 3000;
        }
        if (name.contains("pot share")) {
            return 1725;
        }
        if (name.contains("humidify") || name.contains("charge") && name.contains("orb")) {
            return 1000;
        }
        // Combat spells: about a cast every 5 ticks
        return 1000;
    }

    // Essence an hour (each crafts one rune's XP)
    private static double runecraft(String name) {
        if (name.contains("tiara")) {
            return 1450;
        }
        if (name.contains("core")) {
            return 300;
        }
        if (name.contains("zeah") || name.contains("soul")) {
            return 1460;
        }
        if (name.contains("wrath")) {
            return 4950;
        }
        if (name.contains("aether")) {
            return 5000;
        }
        if (name.contains("lava")) {
            return 6000;
        }
        if (name.contains("astral")) {
            return 4830;
        }
        if (name.contains("nature")) {
            return 3520;
        }
        if (name.contains("law")) {
            return 3800;
        }
        if (name.contains("cosmic")) {
            return 2250;
        }
        if (name.contains("mist") || name.contains("dust") || name.contains("mud") || name.contains("smoke")
            || name.contains("steam")) {
            return 3000;
        }
        return 2500;
    }

    private static double thieving(String name) {
        if (name.contains("sq'irkjuice")) {
            return 20;
        }
        if (name.contains("stall")) {
            return name.contains("fruit") || name.contains("cake") ? 1200 : 800;
        }
        if (name.contains("chest") || name.contains("safe")) {
            return 340;
        }
        if (name.contains("knight")) {
            return 2000;
        }
        if (name.contains("master farmer")) {
            return 2730;
        }
        if (name.equals("elf")) {
            return 425;
        }
        if (name.equals("vyre")) {
            return 490;
        }
        if (name.contains("bandit") || name.contains("menaphite thug")) {
            return 1600;
        }
        return 2000;
    }

    // Building with a demon butler: about 7,500 planks an hour (the wiki's oak larders: 8,000), so furniture of more
    // planks comes slower, and no more than about 1,000 pieces an hour
    private static double construction(String name, CalculatorData.Action action, List<RecipeData.Recipe> made) {
        if (name.contains("bone")) {
            return 0;
        }
        if (name.equals("oak door")) {
            return 833;
        }
        if (name.contains("mythical cape")) {
            return 1000;
        }
        if (name.contains("repair kit")) {
            return 1200;
        }
        if (name.contains("hull parts")) {
            return 1000;
        }
        // (and none goes past about 800k an hour: a gold sink or a throne takes a while)
        double most = 800_000.0 / Math.max(1, action.xp);
        for (RecipeData.Recipe recipe : made) {
            int planks = 0;
            for (Map.Entry<String, Integer> material : recipe.materials.entrySet()) {
                if (material.getKey().toLowerCase(Locale.ROOT).contains("plank")) {
                    planks += material.getValue();
                }
            }
            if (planks > 0) {
                return Math.min(most, Math.min(1000, 7500.0 / planks));
            }
        }
        return Math.min(600, 400_000.0 / Math.max(1, action.xp));
    }
}
