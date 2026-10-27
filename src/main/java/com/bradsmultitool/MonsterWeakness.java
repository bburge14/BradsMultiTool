package com.bradsmultitool;

/**
 * What a monster is weakest to: a combat type with its sub-type (Stab/Slash/Crush,
 * Light/Standard/Heavy, or a magic element), plus any elemental weakness and slayer/bane note.
 */
public final class MonsterWeakness {

    public enum Type {
        MELEE,
        RANGED,
        MAGIC,
        UNKNOWN
    }

    public enum Element {
        AIR("Air"),
        WATER("Water"),
        EARTH("Earth"),
        FIRE("Fire");

        private final String displayName;

        Element(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final MonsterWeakness UNKNOWN = new MonsterWeakness(Type.UNKNOWN, null, null, 0, null);

    public final Type type;
    /** Melee damage type ("Slash") or ranged ammo type ("Light"), or null. */
    public final String subType;
    /** Elemental weakness from the wiki, or null. */
    public final Element element;
    /** Extra damage from that element, e.g. 50 for +50%. */
    public final int elementPercent;
    /** Bane hint such as "Demonbane", or null. */
    public final String note;

    private MonsterWeakness(Type type, String subType, Element element, int elementPercent, String note) {
        this.type = type;
        this.subType = subType;
        this.element = element;
        this.elementPercent = elementPercent;
        this.note = note;
    }

    static MonsterWeakness melee(String damageType, Element element, int elementPercent, String note) {
        return new MonsterWeakness(Type.MELEE, damageType, element, elementPercent, note);
    }

    static MonsterWeakness ranged(String ammoType, Element element, int elementPercent, String note) {
        return new MonsterWeakness(Type.RANGED, ammoType, element, elementPercent, note);
    }

    static MonsterWeakness unknown(Element element, int elementPercent, String note) {
        return new MonsterWeakness(Type.UNKNOWN, null, element, elementPercent, note);
    }

    static MonsterWeakness magic(Element element, int elementPercent, String note) {
        return new MonsterWeakness(Type.MAGIC, null, element, elementPercent, note);
    }

    /** Main weakness text shown next to the icon, e.g. "Slash", "Light Ranged", "Water", "Unknown". */
    public String label() {
        switch (type) {
            case MELEE:
                return subType != null ? subType : "Melee";
            case RANGED:
                return subType != null ? subType + " Ranged" : "Ranged";
            case MAGIC:
                return element != null ? element.toString() : "Magic (any element)";
            default:
                return "Unknown";
        }
    }

    /** True when the elemental weakness should get its own row (it isn't already the main label). */
    public boolean hasSeparateElement() {
        return element != null && type != Type.MAGIC;
    }

    /** e.g. "Fire +50%". */
    public String elementLabel() {
        return elementPercent > 0 ? element + " +" + elementPercent + "%" : String.valueOf(element);
    }

    /**
     * Fallback for NPCs missing from monsters.json: a rough guess from the name.
     */
    public static MonsterWeakness fromName(String npcName) {
        if (npcName == null) {
            return UNKNOWN;
        }
        String n = npcName.toLowerCase();

        if (n.contains("demon")) {
            return melee("Slash", null, 0, "Demonbane");
        }
        if (n.contains("dragon") || n.contains("wyvern") || n.contains("drake") || n.contains("wyrm")) {
            return melee("Stab", null, 0, "Dragonbane");
        }
        if (n.contains("zombie") || n.contains("skeleton") || n.contains("ghost")) {
            return melee("Crush", null, 0, "Salve amulet");
        }
        return UNKNOWN;
    }
}
