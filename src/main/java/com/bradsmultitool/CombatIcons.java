package com.bradsmultitool;

/**
 * Item sprites used as icons for combat styles and weaknesses.
 */
final class CombatIcons {

    static final int NONE = -1;

    private static final int STEEL_DAGGER = 1207;
    private static final int STEEL_SCIMITAR = 1325;
    private static final int STEEL_MACE = 1424;
    private static final int STEEL_SWORD = 1281;
    private static final int STEEL_DART = 808;
    private static final int STEEL_ARROW = 886;
    private static final int STEEL_BOLTS = 9141;
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
                    case "stab": return STEEL_DAGGER;
                    case "slash": return STEEL_SCIMITAR;
                    case "crush": return STEEL_MACE;
                    default: return STEEL_SWORD;
                }
            case RANGED:
                switch (sub) {
                    case "light": return STEEL_DART;
                    case "standard": return STEEL_ARROW;
                    case "heavy": return STEEL_BOLTS;
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
