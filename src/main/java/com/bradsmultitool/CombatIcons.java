package com.bradsmultitool;

/**
 * Item sprites used as icons for combat styles and weaknesses.
 */
final class CombatIcons {

    static final int NONE = -1;

    private static final int BRONZE_DAGGER = 1205;
    private static final int BRONZE_SCIMITAR = 1321;
    private static final int BRONZE_MACE = 1422;
    private static final int BRONZE_SWORD = 1277;
    private static final int BRONZE_DART = 806;
    private static final int BRONZE_ARROW = 882;
    private static final int BRONZE_BOLTS = 877;
    private static final int SHORTBOW = 841;
    private static final int WIZARD_HAT = 579;
    private static final int AIR_RUNE = 556;
    private static final int WATER_RUNE = 555;
    private static final int EARTH_RUNE = 557;
    private static final int FIRE_RUNE = 554;

    private CombatIcons() {
    }

    /**
     * @param subType melee damage type (Stab/Slash/Crush) or ranged ammo type (Light/Standard/Heavy), may be null
     * @param element magic element, may be null
     */
    static int itemId(MonsterWeakness.Type type, String subType, MonsterWeakness.Element element) {
        String sub = subType != null ? subType.toLowerCase() : "";
        switch (type) {
            case MELEE:
                switch (sub) {
                    case "stab": return BRONZE_DAGGER;
                    case "slash": return BRONZE_SCIMITAR;
                    case "crush": return BRONZE_MACE;
                    default: return BRONZE_SWORD;
                }
            case RANGED:
                switch (sub) {
                    case "light": return BRONZE_DART;
                    case "standard": return BRONZE_ARROW;
                    case "heavy": return BRONZE_BOLTS;
                    default: return SHORTBOW;
                }
            case MAGIC:
                return element != null ? rune(element) : WIZARD_HAT;
            default:
                return NONE;
        }
    }

    static int rune(MonsterWeakness.Element element) {
        switch (element) {
            case AIR: return AIR_RUNE;
            case WATER: return WATER_RUNE;
            case EARTH: return EARTH_RUNE;
            default: return FIRE_RUNE;
        }
    }
}
