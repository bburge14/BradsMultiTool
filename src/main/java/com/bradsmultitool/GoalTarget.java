package com.bradsmultitool;

/**
 * The "Track for goal" picks as saved: "npc:ruby harvest", "object:Teak tree", "item:logs" (the best recipe
 * that uses it), "item:logs|Longbow (u)" (that one recipe), and "fish:rod fishing spot|lure|1526" (a fishing
 * spot's NPC name, the way you fish it, and its NPC id for the spot's catches).
 */
final class GoalTarget {

    static final String NPC = "npc:";
    static final String OBJECT = "object:";
    static final String ITEM = "item:";
    static final String FISH = "fish:";

    private GoalTarget() {
    }

    static String fish(String spotName, String option, int npcId) {
        return FISH + spotName.toLowerCase() + "|" + option.toLowerCase() + "|" + npcId;
    }

    static String item(String item, String recipe) {
        return ITEM + item.toLowerCase() + (recipe == null ? "" : "|" + recipe);
    }

    /** The part after the prefix, split at "|": {"rod fishing spot", "lure", "1526"}. */
    static String[] parts(String target) {
        return target.substring(target.indexOf(':') + 1).split("\\|", -1);
    }

    /** For an item pick: the item (lower-case); null for other picks. */
    static String itemName(String target) {
        return target != null && target.startsWith(ITEM) ? parts(target)[0] : null;
    }

    /** For an item pick of one recipe: that recipe's name, else null. */
    static String recipeName(String target) {
        if (target == null || !target.startsWith(ITEM)) {
            return null;
        }
        String[] parts = parts(target);
        return parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
    }

    /** The NPC to outline for an npc or fish pick (lower-case), else null. */
    static String npcName(String target) {
        if (target == null) {
            return null;
        }
        return target.startsWith(NPC) || target.startsWith(FISH) ? parts(target)[0] : null;
    }
}
