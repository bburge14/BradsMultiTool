package com.snuffys.skilltracker;

import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;

/**
 * What a skilling chat message says you got: a log, a fish, some ore or a gem, a cooked or burnt food. The
 * patterns are RuneLite's Woodcutting, Fishing and Cooking plugins' (BSD-2-Clause), plus the game's "You manage
 * to mine some ..." and gem messages for Mining. One message is one item.
 */
final class ItemsGathered {

    enum Kind {
        LOGS(Skill.WOODCUTTING, "Logs"),
        FISH(Skill.FISHING, "Fish"),
        ORE(Skill.MINING, "Ore"),
        GEMS(Skill.MINING, "Gems"),
        COOKED(Skill.COOKING, "Cooked"),
        BURNT(Skill.COOKING, "Burnt");

        final Skill skill;
        final String label;

        Kind(Skill skill, String label) {
            this.skill = skill;
            this.label = label;
        }
    }

    // RuneLite's Woodcutting plugin
    private static final Pattern LOGS = Pattern.compile("You get (?:some|an)[\\w ]+(?:logs?|mushrooms)\\.");
    // RuneLite's Fishing plugin
    private static final Pattern FISH = Pattern.compile("You catch (?:a|an|some) |Your cormorant returns with its catch.|You catch .* Karambwanji");
    private static final Pattern ORE = Pattern.compile("You manage to (?:mine|quarry) some [\\w '-]+\\.");
    private static final Pattern GEM = Pattern.compile("You just (?:found|mined) (?:a|an) [\\w ]+!");
    // RuneLite's Cooking plugin
    private static final String[] COOKED = {"You successfully cook", "You successfully bake", "You successfully fry",
        "You manage to cook", "You roast a", "You spit-roast", "You cook", "Eventually the Jubbly", "You half-cook",
        "The undead meat is now cooked", "The undead chicken is now cooked", "You successfully scramble",
        "You dry a piece of meat"};
    private static final String[] BURNT = {"You accidentally burn", "You burn the mushroom in the fire.",
        "Unfortunately the Jubbly", "You accidentally spoil"};

    private ItemsGathered() {
    }

    /** What this message says you got, or null. */
    static Kind of(ChatMessageType type, String message) {
        if (message == null || (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE
            && type != ChatMessageType.MESBOX)) {
            return null;
        }
        if (LOGS.matcher(message).matches()) {
            return Kind.LOGS;
        }
        if (ORE.matcher(message).matches()) {
            return Kind.ORE;
        }
        if (GEM.matcher(message).matches()) {
            return Kind.GEMS;
        }
        // Fishing and Cooking messages only come as filterable (spam) messages, as RuneLite's plugins read them
        if (type != ChatMessageType.SPAM) {
            return null;
        }
        if (FISH.matcher(message).find()) {
            return Kind.FISH;
        }
        for (String start : BURNT) {
            if (message.startsWith(start)) {
                return Kind.BURNT;
            }
        }
        for (String start : COOKED) {
            if (message.startsWith(start)) {
                return Kind.COOKED;
            }
        }
        return null;
    }

    /** "342 (1,050/hr)": a count and how many an hour over this many seconds of training (no rate under a minute). */
    static String countAndRate(int count, long seconds) {
        String text = String.format("%,d", count);
        return seconds >= 60 ? text + " (" + String.format("%,d", count * 3600L / seconds) + "/hr)" : text;
    }
}
