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
        /** Melee damage type (Stab/Slash/Crush) or ranged ammo type (Light/Standard/Heavy), or null. */
        public final String damageType;
        /** Ranged weapon kind shown to the player ("Thrown", "Bow", ...), or null. */
        public final String weaponKind;
        /** Element of the autocast spell, or null. */
        public final MonsterWeakness.Element element;
        /** Autocast spell tier ("Strike", "Bolt", "Blast", "Wave", "Surge"), or null. */
        public final String spellTier;

        Style(String discipline, String buttonName, String damageType) {
            this(discipline, buttonName, damageType, null, null, null);
        }

        Style(String discipline, String buttonName, String damageType, String weaponKind,
              MonsterWeakness.Element element, String spellTier) {
            this.discipline = discipline;
            this.buttonName = buttonName;
            this.damageType = damageType;
            this.weaponKind = weaponKind;
            this.element = element;
            this.spellTier = spellTier;
        }

        public MonsterWeakness.Type type() {
            switch (discipline) {
                case MELEE: return MonsterWeakness.Type.MELEE;
                case RANGED: return MonsterWeakness.Type.RANGED;
                case MAGIC: return MonsterWeakness.Type.MAGIC;
                default: return MonsterWeakness.Type.UNKNOWN;
            }
        }

        /** e.g. "Slash", "Lunge (Stab)", "Rapid (Thrown)", "Spell Cast (Fire Blast)". */
        public String label() {
            String detail;
            if (element != null) {
                detail = spellTier != null ? element + " " + spellTier : element.toString();
            } else if (weaponKind != null) {
                detail = weaponKind;
            } else {
                detail = damageType;
            }
            if (detail == null || detail.equalsIgnoreCase(buttonName)) {
                return buttonName;
            }
            return buttonName + " (" + detail + ")";
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
        ranged(3, "Standard", "Bow");                                                         // Bow (arrows)
        melee(4, "Chop", "Slash", "Slash", "Slash", "Lunge", "Stab", "Block", "Slash");       // Claws
        ranged(5, "Heavy", "Crossbow");                                                       // Crossbow (bolts)
        CATEGORIES.put(6, new Style[]{                                                        // Salamander
            new Style(MELEE, "Scorch", "Slash"),
            new Style(RANGED, "Flare", "Standard", "Salamander", null, null),
            new Style(MAGIC, "Blaze", null),
            null
        });
        CATEGORIES.put(7, new Style[]{                                                        // Chinchompa
            new Style(RANGED, "Short fuse", "Heavy", "Chinchompa", null, null),
            new Style(RANGED, "Medium fuse", "Heavy", "Chinchompa", null, null),
            null,
            new Style(RANGED, "Long fuse", "Heavy", "Chinchompa", null, null)
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
        ranged(19, "Light", "Thrown");                                                        // Darts, knives, thrownaxes, blowpipe
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

    private static void ranged(int category, String ammoType, String weaponKind) {
        CATEGORIES.put(category, new Style[]{
            new Style(RANGED, "Accurate", ammoType, weaponKind, null, null),
            new Style(RANGED, "Rapid", ammoType, weaponKind, null, null),
            null,
            new Style(RANGED, "Longrange", ammoType, weaponKind, null, null)
        });
    }

    private static final String[] SPELL_TIERS = {"Strike", "Bolt", "Blast", "Wave"};

    /** Tier of the autocast spell (varbit 276), or null when it isn't an elemental combat spell. */
    static String autocastTier(int spellId) {
        if (spellId >= 1 && spellId <= 16) {
            return SPELL_TIERS[(spellId - 1) / 4];
        }
        if (spellId >= 48 && spellId <= 51) {
            return "Surge";
        }
        return null;
    }

    /**
     * Element of the autocast spell (varbit 276). Strike/Bolt/Blast/Wave are ids 1-16 in
     * Wind, Water, Earth, Fire order; Surges are 48-51. Anything else has no element.
     */
    static MonsterWeakness.Element autocastElement(int spellId) {
        int offset;
        if (spellId >= 1 && spellId <= 16) {
            offset = (spellId - 1) % 4;
        } else if (spellId >= 48 && spellId <= 51) {
            offset = spellId - 48;
        } else {
            return null;
        }
        switch (offset) {
            case 0: return MonsterWeakness.Element.AIR;
            case 1: return MonsterWeakness.Element.WATER;
            case 2: return MonsterWeakness.Element.EARTH;
            default: return MonsterWeakness.Element.FIRE;
        }
    }

    public static Style resolve(int category, int styleIndex, boolean defensiveCasting, int autocastSpellId) {
        if (styleIndex == AUTOCAST_INDEX) {
            return new Style(MAGIC, defensiveCasting ? "Defensive Cast" : "Spell Cast", null, null,
                autocastElement(autocastSpellId), autocastTier(autocastSpellId));
        }

        Style[] styles = CATEGORIES.get(category);
        if (styles != null && styleIndex >= 0 && styleIndex < styles.length && styles[styleIndex] != null) {
            return styles[styleIndex];
        }
        return new Style(MELEE, "Unknown", null);
    }
}
