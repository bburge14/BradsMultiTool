package com.bradsmultitool;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.DBTableID.CombatInterfaceWeaponCategory.Row;

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

        Style withWeaponKind(String kind) {
            return new Style(discipline, buttonName, damageType, kind, element, spellTier);
        }

        public MonsterWeakness.Type type() {
            switch (discipline) {
                case MELEE: return MonsterWeakness.Type.MELEE;
                case RANGED: return MonsterWeakness.Type.RANGED;
                case MAGIC: return MonsterWeakness.Type.MAGIC;
                default: return MonsterWeakness.Type.UNKNOWN;
            }
        }

        /** e.g. "Slash", "Lunge (Stab)", "Rapid (Thrown)", "Wind Strike", "Ice Barrage", "Fire Wave (Def)". */
        public String label() {
            if (MAGIC.equals(discipline)) {
                String spell = weaponKind != null ? weaponKind : spellName(element, spellTier);
                if (spell == null) {
                    return buttonName;
                }
                return "Defensive Cast".equals(buttonName) ? spell + " (Def)" : spell;
            }

            String detail;
            if (weaponKind != null) {
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
    private static final String THROWN = "Thrown";

    // Combat tab button index 4 means a spell is being autocast
    private static final int AUTOCAST_INDEX = 4;

    // Keyed by the game's weapon category rows (DB table 78, looked up from varbit 357), not the raw
    // category numbers, which the game renumbers when it adds weapon types
    private static final Map<Integer, Style[]> CATEGORIES = new HashMap<>();

    static {
        melee(Row.COMBAT_INTERFACE_UNARMED, "Punch", "Crush", "Kick", "Crush", null, null, "Block", "Crush");
        melee(Row.COMBAT_INTERFACE_AXE, "Chop", "Slash", "Hack", "Slash", "Smash", "Crush", "Block", "Slash");
        melee(Row.COMBAT_INTERFACE_BLUNT, "Pound", "Crush", "Pummel", "Crush", null, null, "Block", "Crush");
        melee(Row.COMBAT_INTERFACE_BLUNT_BLUDGEON, "Pound", "Crush", "Pummel", "Crush", "Smash", "Crush", null, null);
        ranged(Row.COMBAT_INTERFACE_BOW, "Standard", "Bow");
        melee(Row.COMBAT_INTERFACE_CLAW, "Chop", "Slash", "Slash", "Slash", "Lunge", "Stab", "Block", "Slash");
        ranged(Row.COMBAT_INTERFACE_CROSSBOW, "Heavy", "Crossbow");
        CATEGORIES.put(Row.COMBAT_INTERFACE_FLAMER, new Style[]{                                 // Salamander
            new Style(MELEE, "Scorch", "Slash"),
            new Style(RANGED, "Flare", "Standard", "Salamander", null, null),
            new Style(MAGIC, "Blaze", null),
            null
        });
        CATEGORIES.put(Row.COMBAT_INTERFACE_GRENADE, new Style[]{                                // Chinchompa
            new Style(RANGED, "Short fuse", "Heavy", "Chinchompa", null, null),
            new Style(RANGED, "Medium fuse", "Heavy", "Chinchompa", null, null),
            null,
            new Style(RANGED, "Long fuse", "Heavy", "Chinchompa", null, null)
        });
        melee(Row.COMBAT_INTERFACE_GUN, "Aim and Fire", "Crush", "Kick", "Crush", null, null, null, null);
        melee(Row.COMBAT_INTERFACE_HACKSWORD, "Chop", "Slash", "Slash", "Slash", "Lunge", "Stab", "Block", "Slash");
        melee(Row.COMBAT_INTERFACE_HEAVYSWORD, "Chop", "Slash", "Slash", "Slash", "Smash", "Crush", "Block", "Slash");
        melee(Row.COMBAT_INTERFACE_HEAVYSWORD_LARGE, "Chop", "Slash", "Slash", "Slash", "Smash", "Crush", "Block", "Slash");
        melee(Row.COMBAT_INTERFACE_PICKAXE, "Spike", "Stab", "Impale", "Stab", "Smash", "Crush", "Block", "Stab");
        melee(Row.COMBAT_INTERFACE_POLEARM, "Jab", "Stab", "Swipe", "Slash", null, null, "Fend", "Stab");
        melee(Row.COMBAT_INTERFACE_CHARGESPEAR, "Lunge", "Stab", "Swipe", "Slash", "Pound", "Crush", "Block", "Stab");
        melee(Row.COMBAT_INTERFACE_POLESTAFF, "Bash", "Crush", "Pound", "Crush", null, null, "Block", "Crush");
        melee(Row.COMBAT_INTERFACE_SCYTHE, "Reap", "Slash", "Chop", "Slash", "Jab", "Crush", "Block", "Slash");
        melee(Row.COMBAT_INTERFACE_SPEAR, "Lunge", "Stab", "Swipe", "Slash", "Pound", "Crush", "Block", "Stab");
        melee(Row.COMBAT_INTERFACE_BANNER, "Lunge", "Stab", "Swipe", "Slash", "Pound", "Crush", "Block", "Stab");
        melee(Row.COMBAT_INTERFACE_SPIKED, "Pound", "Crush", "Pummel", "Crush", "Spike", "Stab", "Block", "Crush");
        melee(Row.COMBAT_INTERFACE_STABSWORD, "Stab", "Stab", "Lunge", "Stab", "Slash", "Slash", "Block", "Stab");
        melee(Row.COMBAT_INTERFACE_STAFF, "Bash", "Crush", "Pound", "Crush", null, null, "Focus", "Crush");
        melee(Row.COMBAT_INTERFACE_STAFF_BLADED, "Jab", "Stab", "Swipe", "Slash", null, null, "Fend", "Crush");
        melee(Row.COMBAT_INTERFACE_STAFF_SPELLBLADE, "Jab", "Stab", "Swipe", "Slash", null, null, "Fend", "Crush");
        ranged(Row.COMBAT_INTERFACE_THROWN, "Light", THROWN);                                 // Darts, knives, thrownaxes, blowpipe
        melee(Row.COMBAT_INTERFACE_WHIP, "Flick", "Slash", "Lash", "Slash", null, null, "Deflect", "Slash");
        Style[] powered = {
            new Style(MAGIC, "Accurate", null),
            new Style(MAGIC, "Accurate", null),
            null,
            new Style(MAGIC, "Longrange", null)
        };
        CATEGORIES.put(Row.COMBAT_INTERFACE_STAFF_SELFPOWERING, powered);
        CATEGORIES.put(Row.COMBAT_INTERFACE_WAND_SELFPOWERING, powered);
        CATEGORIES.put(Row.COMBAT_INTERFACE_BOOK_SELFPOWERING, powered);
        melee(Row.COMBAT_INTERFACE_BULWARK, "Pummel", "Crush", null, null, null, null, "Block", "Crush");
        melee(Row.COMBAT_INTERFACE_PARTISAN, "Stab", "Stab", "Lunge", "Stab", "Pound", "Crush", "Block", "Stab");
    }

    // Weapon types that only ever attack with melee: unknown buttons on them are still melee
    private static final Set<Integer> MELEE_ONLY = new HashSet<>(Arrays.asList(
        Row.COMBAT_INTERFACE_MULTI_MELEE, Row.COMBAT_INTERFACE_SLASHFLAIL, Row.COMBAT_INTERFACE_EXTENDING_CHAINMACE));

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

    /**
     * Names the thrown weapon from its item name, since the game puts every thrown weapon in one category.
     * Returns "Thrown" for anything unrecognised.
     */
    static String thrownKind(String itemName) {
        if (itemName == null) {
            return THROWN;
        }
        String n = itemName.toLowerCase();
        if (n.contains("blowpipe")) {
            return "Blowpipe";
        }
        if (n.contains("toktz-xil-ul")) {
            return "Toktz-xil-ul";
        }
        if (n.contains("tonalztics")) {
            return "Tonalztics";
        }
        if (n.contains("thrownaxe") || n.contains("throwing axe")) {
            return "Thrownaxe";
        }
        if (n.contains("knife")) {
            return "Knife";
        }
        if (n.contains("dart")) {
            return "Dart";
        }
        return THROWN;
    }

    /**
     * Style for a spell the player cast by hand, e.g. "Fire Strike" or "Ice Barrage".
     * Standard elemental spells get their element and tier; other spells only their name.
     */
    public static Style manualCast(String spellName) {
        return spellStyle(spellName, false);
    }

    /** Style for a named spell (cast by hand or autocast); defensive autocasts get "(Def)". */
    public static Style spellStyle(String spellName, boolean defensive) {
        MonsterWeakness.Element element = null;
        String tier = null;
        String[] words = spellName.trim().split("\\s+");
        if (words.length == 2) {
            element = elementFromSpellWord(words[0]);
            for (String t : SPELL_TIERS_WITH_SURGE) {
                if (t.equalsIgnoreCase(words[1])) {
                    tier = t;
                }
            }
            if (tier == null) {
                element = null;
            }
        }
        return new Style(MAGIC, defensive ? "Defensive Cast" : "Cast", null, spellName.trim(), element, tier);
    }

    private static final String[] SPELL_TIERS_WITH_SURGE = {"Strike", "Bolt", "Blast", "Wave", "Surge"};

    /** In-game name of a standard elemental spell; air spells are called "Wind". */
    static String spellName(MonsterWeakness.Element element, String tier) {
        if (element == null || tier == null) {
            return null;
        }
        return (element == MonsterWeakness.Element.AIR ? "Wind" : element.toString()) + " " + tier;
    }

    private static MonsterWeakness.Element elementFromSpellWord(String word) {
        switch (word.toLowerCase()) {
            case "wind": return MonsterWeakness.Element.AIR;
            case "water": return MonsterWeakness.Element.WATER;
            case "earth": return MonsterWeakness.Element.EARTH;
            case "fire": return MonsterWeakness.Element.FIRE;
            default: return null;
        }
    }

    /** Adds the specific thrown weapon to a thrown-category style; other styles are returned unchanged. */
    public static Style withEquippedWeapon(Style style, String weaponName) {
        if (!THROWN.equals(style.weaponKind)) {
            return style;
        }
        return style.withWeaponKind(thrownKind(weaponName));
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

    /**
     * The style for the combat tab button {@code styleIndex} (varp 43) of the weapon category row
     * {@code categoryRow} (DB table 78, -1 if the game has none). {@code gameStyleName} is the game's own
     * name for that button's XP style ("Accurate", "Rapid", "Casting"...), or null; it's used for weapon
     * types this table doesn't know yet.
     */
    public static Style resolve(int categoryRow, int styleIndex, boolean defensiveCasting, int autocastSpellId,
                                String gameStyleName) {
        if (styleIndex == AUTOCAST_INDEX) {
            return new Style(MAGIC, defensiveCasting ? "Defensive Cast" : "Spell Cast", null, null,
                autocastElement(autocastSpellId), autocastTier(autocastSpellId));
        }

        Style[] styles = CATEGORIES.get(categoryRow);
        if (styles != null && styleIndex >= 0 && styleIndex < styles.length && styles[styleIndex] != null) {
            return styles[styleIndex];
        }
        if (gameStyleName == null || gameStyleName.isEmpty()) {
            return new Style(MELEE, "Unknown", null);
        }
        String name = Character.toUpperCase(gameStyleName.charAt(0)) + gameStyleName.substring(1).toLowerCase();
        if (MELEE_ONLY.contains(categoryRow)) {
            return new Style(MELEE, name, null);
        }
        String n = name.toLowerCase();
        if (n.contains("cast")) {
            return new Style(MAGIC, name, null);
        }
        if (n.equals("rapid") || n.equals("longrange") || n.equals("ranging")) {
            return new Style(RANGED, name, null);
        }
        return new Style(MELEE, name, null);
    }
}
