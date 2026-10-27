package com.bradsmultitool;

/**
 * What a monster is weakest to: a combat type, plus either a melee damage type
 * (Stab/Slash/Crush) or a magic element, and an optional slayer/bane note.
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

    public static final MonsterWeakness UNKNOWN = new MonsterWeakness(Type.UNKNOWN, null, null, null);

    public final Type type;
    /** Melee damage type ("Slash"), or null. */
    public final String damageType;
    /** Magic element, or null. */
    public final Element element;
    /** Extra hint such as "Demonbane", or null. */
    public final String note;

    private MonsterWeakness(Type type, String damageType, Element element, String note) {
        this.type = type;
        this.damageType = damageType;
        this.element = element;
        this.note = note;
    }

    static MonsterWeakness melee(String damageType, String note) {
        return new MonsterWeakness(Type.MELEE, damageType, null, note);
    }

    static MonsterWeakness ranged(String note) {
        return new MonsterWeakness(Type.RANGED, null, null, note);
    }

    static MonsterWeakness magic(Element element, String note) {
        return new MonsterWeakness(Type.MAGIC, null, element, note);
    }

    /** Text shown next to the icon, e.g. "Slash / Demonbane", "Fire", "Unknown". */
    public String label() {
        String main;
        switch (type) {
            case MELEE:
                main = damageType != null ? damageType : "Melee";
                break;
            case RANGED:
                main = "Ranged";
                break;
            case MAGIC:
                main = element != null ? element.toString() : "Magic";
                break;
            default:
                main = "Unknown";
                break;
        }
        return note != null ? main + " / " + note : main;
    }

    /**
     * Best-guess weakness from the monster's name. Placeholder until real per-monster data is wired in.
     */
    public static MonsterWeakness fromName(String npcName) {
        if (npcName == null) {
            return UNKNOWN;
        }
        String n = npcName.toLowerCase();

        if (n.contains("demon") || n.contains("nechryael") || n.contains("cerberus") || n.contains("skotizo") || n.contains("k'ril")) {
            return melee("Slash", "Demonbane");
        }
        if (n.contains("dragon") || n.contains("wyvern") || n.contains("drake") || n.contains("vorkath") || n.contains("wyrm")) {
            return melee("Stab", "Dragonbane");
        }
        if (n.contains("zombie") || n.contains("skeleton") || n.contains("ghost") || n.contains("ankou") || n.contains("vet'ion")) {
            return melee("Crush", "Salve");
        }
        if (n.contains("vampyre") || n.contains("vyre")) {
            return melee("Stab", "Blisterwood");
        }
        if (n.contains("kalphite") || n.contains("scarab")) {
            return melee("Stab", "Keris");
        }
        if (n.contains("kurask") || n.contains("turoth")) {
            return melee("Stab", "Leaf-bladed");
        }
        if (n.contains("gargoyle") || n.contains("golem")) {
            return melee("Crush", null);
        }
        if (n.contains("fire giant")) {
            return magic(Element.WATER, null);
        }
        if (n.contains("moss giant") || n.contains("ice giant") || n.contains("ice warrior")) {
            return magic(Element.FIRE, null);
        }
        return UNKNOWN;
    }
}
