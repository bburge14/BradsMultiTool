package com.snuffys.skilltracker;

import java.util.Arrays;
import java.util.List;

/**
 * How you train Prayer with what you picked (OSRS Wiki, Prayer training): bones are buried or offered at an
 * altar, blessed bone shards are offered at the libation bowl with blessed wine or blessed sunfire wine. Picked
 * from an item's "Track for goal" menu; it changes the XP each, the tools and the item line's verb.
 */
enum PrayerMethod {
    BURY("Bury", false, 1, 0, "Bury:"),
    // With both incense burners lit (250% unlit, 300% with one)
    GILDED_ALTAR("Gilded altar", false, 3.5, 0, "Offer:"),
    // The Wilderness Chaos Temple's: 50% chance to keep each bone, so twice the XP per bone on average
    CHAOS_ALTAR("Chaos altar", false, 3.5, 0, "Offer:"),
    // Ground into bonemeal, each with a pot and a bucket of slime
    ECTOFUNTUS("Ectofuntus", false, 4, 0, "Grind:", "Pot", "Bucket of slime"),
    BLESSED_WINE("Blessed wine", true, 0, 5, "Offer:", "Jug of blessed wine"),
    SUNFIRE_WINE("Sunfire wine", true, 0, 6, "Offer:", "Jug of blessed sunfire wine");

    final String label;
    /** For blessed bone shards rather than bones. */
    final boolean shards;
    /** For bones: times the bones' burying XP. */
    final double multiplier;
    /** For shards: XP each. */
    final double shardXp;
    /** The item line's label ("Bury:", "Offer:"). */
    final String verb;
    final List<String> tools;

    PrayerMethod(String label, boolean shards, double multiplier, double shardXp, String verb, String... tools) {
        this.label = label;
        this.shards = shards;
        this.multiplier = multiplier;
        this.shardXp = shardXp;
        this.verb = verb;
        this.tools = Arrays.asList(tools);
    }

    /** The methods for bones or for shards, in menu order. */
    static List<PrayerMethod> forShards(boolean shards) {
        return shards ? Arrays.asList(BLESSED_WINE, SUNFIRE_WINE) : Arrays.asList(BURY, GILDED_ALTAR, CHAOS_ALTAR, ECTOFUNTUS);
    }

    /** How many bones each offering uses on average (the Chaos altar keeps half). */
    double bonesUsedEach() {
        return this == CHAOS_ALTAR ? 0.5 : 1;
    }

    static PrayerMethod parse(String name) {
        try {
            return name == null ? null : valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Whether a Prayer recipe is burying bones (the altars take those), not scattering ashes or shards. */
    static boolean isBones(RecipeData.Recipe recipe) {
        return recipe.name.startsWith("Bury ");
    }

    /** Whether a Prayer recipe is offering blessed bone shards. */
    static boolean isShards(RecipeData.Recipe recipe) {
        return recipe.facility != null && recipe.facility.equalsIgnoreCase("Libation bowl");
    }
}
