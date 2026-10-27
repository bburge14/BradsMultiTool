package com.bradsmultitool;

/**
 * Icons for combat styles and weaknesses: item sprites for weapons, spellbook sprites for spells.
 */
final class CombatIcons {

    static final class Icon {
        /** True for a game sprite (SpriteManager), false for an item image (ItemManager). */
        final boolean sprite;
        final int id;

        private Icon(boolean sprite, int id) {
            this.sprite = sprite;
            this.id = id;
        }
    }

    private static final Icon STEEL_DAGGER = item(1207);
    private static final Icon STEEL_SCIMITAR = item(1325);
    private static final Icon STEEL_MACE = item(1424);
    private static final Icon STEEL_SWORD = item(1281);
    private static final Icon STEEL_KNIFE = item(865);
    private static final Icon MAPLE_SHORTBOW = item(853);
    private static final Icon STEEL_CROSSBOW = item(9179);
    private static final Icon CHINCHOMPA = item(10033);
    private static final Icon WIZARD_HAT = item(579);

    // Spellbook sprite ids (net.runelite.api.SpriteID.SPELL_*), rows by tier, columns Air/Water/Earth/Fire
    private static final String[] TIERS = {"Strike", "Bolt", "Blast", "Wave", "Surge"};
    private static final int[][] SPELL_SPRITES = {
        {15, 17, 19, 21},
        {23, 26, 29, 32},
        {35, 38, 40, 44},
        {46, 48, 51, 52},
        {362, 363, 364, 365},
    };

    private CombatIcons() {
    }

    private static Icon item(int itemId) {
        return new Icon(false, itemId);
    }

    static Icon sprite(int spriteId) {
        return new Icon(true, spriteId);
    }

    /**
     * Icon for the player's current combat style, or null.
     * Melee and ranged show the equipped weapon itself when its id is known (> 0).
     */
    static Icon forStyle(WeaponStyles.Style style, int equippedWeaponId) {
        switch (style.type()) {
            case MELEE:
                return equippedWeaponId > 0 ? item(equippedWeaponId) : melee(style.damageType);
            case RANGED:
                if (equippedWeaponId > 0) {
                    return item(equippedWeaponId);
                }
                if ("Light".equals(style.damageType)) {
                    return STEEL_KNIFE;
                }
                if ("Chinchompa".equals(style.weaponKind)) {
                    return CHINCHOMPA;
                }
                return rangedAmmo(style.damageType);
            case MAGIC:
                return style.element != null ? spell(style.element, style.spellTier) : WIZARD_HAT;
            default:
                return null;
        }
    }

    /** Icon for a monster's weakness, or null when unknown. */
    static Icon forWeakness(MonsterWeakness weakness) {
        switch (weakness.type) {
            case MELEE:
                return melee(weakness.subType);
            case RANGED:
                return rangedAmmo(weakness.subType);
            case MAGIC:
                return weakness.element != null ? spell(weakness.element, null) : WIZARD_HAT;
            default:
                return null;
        }
    }

    /** Icon for a monster's attack style as written on the wiki ("Slash", "Magic", ...), or null. */
    static Icon forAttackStyle(String attackStyle) {
        String s = attackStyle.toLowerCase();
        if (s.contains("ranged") && !s.contains("magic")) {
            return MAPLE_SHORTBOW;
        }
        if (s.contains("magic")) {
            return WIZARD_HAT;
        }
        for (String type : new String[]{"stab", "slash", "crush"}) {
            if (s.contains(type)) {
                return melee(type);
            }
        }
        return s.contains("melee") ? STEEL_SWORD : null;
    }

    /** Spell icon for an element; uses the Strike spell when no tier is given. */
    static Icon spell(MonsterWeakness.Element element, String tier) {
        int row = 0;
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equals(tier)) {
                row = i;
            }
        }
        return new Icon(true, SPELL_SPRITES[row][element.ordinal()]);
    }

    private static Icon melee(String damageType) {
        String type = damageType != null ? damageType.toLowerCase() : "";
        switch (type) {
            case "stab": return STEEL_DAGGER;
            case "slash": return STEEL_SCIMITAR;
            case "crush": return STEEL_MACE;
            default: return STEEL_SWORD;
        }
    }

    private static Icon rangedAmmo(String ammoType) {
        String type = ammoType != null ? ammoType.toLowerCase() : "";
        switch (type) {
            case "light": return STEEL_KNIFE;
            case "heavy": return STEEL_CROSSBOW;
            default: return MAPLE_SHORTBOW;
        }
    }
}
