package com.bradsmultitool;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether a monster will attack you, from the wiki's aggression (and its conditions) plus the game's
 * level rule: an aggressive monster ignores players whose combat level is more than twice its own,
 * except in the Wilderness and for God Wars followers, who go by the god items you wear.
 */
final class Aggression {

    enum State { YES, NO, MAYBE, UNKNOWN }

    static final class Verdict {
        final State state;
        /** Short text for the HUD, e.g. "Yes", "No (lvl 57+)", "No (Bandos item)". */
        final String text;

        Verdict(State state, String text) {
            this.state = state;
            this.text = text;
        }

        boolean aggressive() {
            return state == State.YES;
        }
    }

    private static final Pattern GOD = Pattern.compile("unless wearing an? (\\w+)-affiliated item", Pattern.CASE_INSENSITIVE);

    private Aggression() {
    }

    /**
     * @param equipped lowercase names of the items you're wearing
     */
    static Verdict of(MonsterData.Stats stats, int npcLevel, int playerLevel, boolean inWilderness, List<String> equipped) {
        if (stats == null) {
            return new Verdict(State.UNKNOWN, "Unknown");
        }
        String note = stats.aggroNote;
        if (!stats.aggressive) {
            return new Verdict(State.NO, "No");
        }
        if (note != null) {
            String lower = note.toLowerCase(Locale.ROOT);
            Matcher god = GOD.matcher(note);
            if (god.find()) {
                // God Wars followers ignore combat level; only your god items protect you
                String name = god.group(1);
                return wearsGodItem(name.toLowerCase(Locale.ROOT), equipped)
                    ? new Verdict(State.NO, "No (" + name + " item)")
                    : new Verdict(State.YES, "Yes (no " + name + " item)");
            }
            if (lower.contains("vyre noble")) {
                if (countContaining(equipped, "vyre noble") >= 3) {
                    return new Verdict(State.NO, "No (vyre noble clothing)");
                }
            } else if (lower.contains("greegree")) {
                if (countContaining(equipped, "greegree") > 0) {
                    return new Verdict(State.NO, "No (greegree)");
                }
            } else if (!lower.startsWith("yes, unless the player has completed")) {
                // A condition we can't check from here (quest state, location, team...): show the wiki's words
                return new Verdict(State.MAYBE, note);
            }
        }
        if (!inWilderness && npcLevel > 0 && playerLevel > npcLevel * 2) {
            return new Verdict(State.NO, "No (lvl " + (npcLevel * 2 + 1) + "+)");
        }
        return new Verdict(State.YES, note != null && note.toLowerCase(Locale.ROOT).contains("monkey madness ii")
            ? "Yes (until Monkey Madness II)" : "Yes");
    }

    // God item keywords; most god items carry the god's name
    static boolean wearsGodItem(String god, List<String> equipped) {
        for (String item : equipped) {
            if (item.contains(god)) {
                return true;
            }
            switch (god) {
                case "saradomin":
                    if (item.startsWith("holy book") || item.contains("monk's robe")) {
                        return true;
                    }
                    break;
                case "zamorak":
                    if (item.startsWith("unholy book") || item.contains("zamorakian")) {
                        return true;
                    }
                    break;
                case "armadyl":
                    if (item.startsWith("book of law")) {
                        return true;
                    }
                    break;
                case "bandos":
                    if (item.startsWith("book of war")) {
                        return true;
                    }
                    break;
                case "zaros":
                    if (item.startsWith("ancient") || item.contains("virtus")) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private static int countContaining(List<String> items, String part) {
        int count = 0;
        for (String item : items) {
            if (item.contains(part)) {
                count++;
            }
        }
        return count;
    }
}
