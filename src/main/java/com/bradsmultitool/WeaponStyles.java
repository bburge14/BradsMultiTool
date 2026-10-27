package com.bradsmultitool;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps the equipped weapon category (varbit 357) and selected combat tab button (varp 43)
 * to the button name, damage type, and combat discipline, matching the in-game combat tab.
 */
public final class WeaponStyles {

    public static final class Style {
        public final String discipline;
        public final String buttonName;
        public final String damageType;

        Style(String discipline, String buttonName, String damageType) {
            this.discipline = discipline;
            this.buttonName = buttonName;
            this.damageType = damageType;
        }

        /** e.g. "Slash", "Lunge (Stab)", "Rapid", "Spell Cast". */
        public String label() {
            if (damageType == null || damageType.equalsIgnoreCase(buttonName)) {
                return buttonName;
            }
            return buttonName + " (" + damageType + ")";
        }
    }

    private static final String MELEE = "Melee";
    private static final String RANGED = "Ranged";
    private static final String MAGIC = "Magic";

    // Combat tab button index 4 means a spell is being autocast
    private static final int AUTOCAST_INDEX = 4;

    private static final Map<Integer, Style[]> CATEGORIES = new HashMap<>();

    static {
        melee(0, "Punch", "Crush", "Kick", "Crush", null, null, "Block", "Crush");           // Unarmed
        melee(1, "Chop", "Slash", "Hack", "Slash", "Smash", "Crush", "Block", "Slash");       // Axe
        melee(2, "Pound", "Crush", "Pummel", "Crush", null, null, "Block", "Crush");          // Blunt
        ranged(3);                                                                            // Bow
        melee(4, "Chop", "Slash", "Slash", "Slash", "Lunge", "Stab", "Block", "Slash");       // Claws
        ranged(5);                                                                            // Crossbow
        CATEGORIES.put(6, new Style[]{                                                        // Salamander
            new Style(MELEE, "Scorch", "Slash"),
            new Style(RANGED, "Flare", null),
            new Style(MAGIC, "Blaze", null),
            null
        });
        CATEGORIES.put(7, new Style[]{                                                        // Chinchompa
            new Style(RANGED, "Short fuse", null),
            new Style(RANGED, "Medium fuse", null),
            null,
            new Style(RANGED, "Long fuse", null)
        });
        melee(9, "Chop", "Slash", "Slash", "Slash", "Lunge", "Stab", "Block", "Slash");       // Slash sword
        melee(10, "Chop", "Slash", "Slash", "Slash", "Smash", "Crush", "Block", "Slash");     // 2h sword
        melee(11, "Spike", "Stab", "Impale", "Stab", "Smash", "Crush", "Block", "Stab");      // Pickaxe
        melee(12, "Jab", "Stab", "Swipe", "Slash", null, null, "Fend", "Stab");               // Polearm
        melee(13, "Bash", "Crush", "Pound", "Crush", null, null, "Block", "Crush");           // Polestaff
        melee(14, "Reap", "Slash", "Chop", "Slash", "Jab", "Crush", "Block", "Slash");        // Scythe
        melee(15, "Lunge", "Stab", "Swipe", "Slash", "Pound", "Crush", "Block", "Stab");      // Spear
        melee(16, "Pound", "Crush", "Pummel", "Crush", "Spike", "Stab", "Block", "Crush");    // Spiked
        melee(17, "Stab", "Stab", "Lunge", "Stab", "Slash", "Slash", "Block", "Stab");        // Stab sword
        melee(18, "Bash", "Crush", "Pound", "Crush", null, null, "Focus", "Crush");           // Staff
        ranged(19);                                                                           // Thrown
        melee(20, "Flick", "Slash", "Lash", "Slash", null, null, "Deflect", "Slash");         // Whip
        melee(21, "Jab", "Stab", "Swipe", "Slash", null, null, "Fend", "Crush");              // Bladed staff
        CATEGORIES.put(23, new Style[]{                                                       // Powered staff
            new Style(MAGIC, "Accurate", null),
            new Style(MAGIC, "Accurate", null),
            null,
            new Style(MAGIC, "Longrange", null)
        });
        melee(24, "Lunge", "Stab", "Swipe", "Slash", "Pound", "Crush", "Block", "Stab");      // Banner
        melee(26, "Pound", "Crush", "Pummel", "Crush", "Smash", "Crush", null, null);         // Bludgeon
        melee(27, "Pummel", "Crush", null, null, null, null, "Block", "Crush");               // Bulwark
        CATEGORIES.put(28, CATEGORIES.get(23));                                               // Powered wand
        melee(29, "Stab", "Stab", "Lunge", "Stab", "Pound", "Crush", "Block", "Stab");        // Partisan
    }

    private WeaponStyles() {
    }

    private static void melee(int category, String... namesAndTypes) {
        Style[] styles = new Style[4];
        for (int i = 0; i < 4; i++) {
            String name = namesAndTypes[i * 2];
            if (name != null) {
                styles[i] = new Style(MELEE, name, namesAndTypes[i * 2 + 1]);
            }
        }
        CATEGORIES.put(category, styles);
    }

    private static void ranged(int category) {
        CATEGORIES.put(category, new Style[]{
            new Style(RANGED, "Accurate", null),
            new Style(RANGED, "Rapid", null),
            null,
            new Style(RANGED, "Longrange", null)
        });
    }

    public static Style resolve(int category, int styleIndex, boolean defensiveCasting) {
        if (styleIndex == AUTOCAST_INDEX) {
            return new Style(MAGIC, defensiveCasting ? "Defensive Cast" : "Spell Cast", null);
        }

        Style[] styles = CATEGORIES.get(category);
        if (styles != null && styleIndex >= 0 && styleIndex < styles.length && styles[styleIndex] != null) {
            return styles[styleIndex];
        }
        return new Style(MELEE, "Unknown", null);
    }
}
