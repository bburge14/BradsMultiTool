package com.bradsmultitool;

import java.util.Locale;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Hunters' Rumours: which creature your guild hunter asked for, how many you've caught since (toward the catch the
 * rare part is guaranteed by), where to find it, and an alert when the rare part drops. There's no game variable
 * for the rumour, so it's read the way the Hunter Rumours plugin (geel9/runelite-hunter-rumours, BSD-2-Clause) does
 * it: from the guild hunters' dialogue, the Quetzal whistle's "Rumour" message and the rare part's message, with
 * catches counted from Hunter XP drops of the creature's XP. Kept with your profile. Only reads chat and XP.
 */
@Singleton
class HunterRumours {

    private static final String GROUP = SkillTracker.SHARED_GROUP;
    private static final String[] HUNTERS = {"Gilman", "Cervus", "Ornus", "Aco", "Teco", "Wolf"};
    private static final int[] OUTFIT = {ItemID.HG_HUNTER_HOOD, ItemID.HG_HUNTER_TOP, ItemID.HG_HUNTER_LEGS, ItemID.HG_HUNTER_BOOTS};
    // A guild hunter speaking ("Huntmaster Gilman (Novice)|...", "Guild Hunter Wolf (Master)|...")
    private static final Pattern SPEAKER = Pattern.compile("^[^|]*hunt[^|]*\\|", Pattern.CASE_INSENSITIVE);

    private final Client client;
    private final ConfigManager configManager;
    private final SkillHelperData data;
    private final Alerts alerts;
    private final SkillTrackerConfig config;

    // The rare part's message comes before the catch's XP drop some ticks: it's handled on the next tick
    private boolean partPending;
    private int lastHunterXp = -1;
    private volatile int outfitPieces;

    @Inject
    HunterRumours(Client client, ConfigManager configManager, SkillHelperData data, Alerts alerts, SkillTrackerConfig config) {
        this.client = client;
        this.configManager = configManager;
        this.data = data;
        this.alerts = alerts;
        this.config = config;
    }

    /** Your current rumour's creature, or null when you have none (or it isn't known yet). */
    SkillHelperData.Rumour current() {
        String name = get("rumour.creature");
        if (name == null) {
            return null;
        }
        for (SkillHelperData.Rumour rumour : data.rumours()) {
            if (rumour.name.equalsIgnoreCase(name)) {
                return rumour;
            }
        }
        return null;
    }

    /** The guild hunter who gave it ("Gilman"), or null. */
    String hunter() {
        return get("rumour.hunter");
    }

    /** Catches of the creature counted since the rumour was given. */
    int caught() {
        try {
            String value = get("rumour.caught");
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Whether the rare part has dropped (take it back to the guild). */
    boolean partFound() {
        return "true".equals(get("rumour.found"));
    }

    /** The catch the rare part is guaranteed by, with the guild hunter outfit pieces you're wearing; 0 if unknown. */
    int pity(SkillHelperData.Rumour rumour) {
        return rumour == null ? 0 : rumour.pityWith(outfitPieces);
    }

    /** Whether this creature's catches can be counted from your XP drops (its XP per catch is known). */
    boolean counts(SkillHelperData.Rumour rumour) {
        SkillHelperData.Creature creature = rumour == null ? null : data.creature(rumour);
        return creature != null && creature.xp > 0;
    }

    /**
     * The rumour card's lines, {label, text}: the creature (and method, hunter), catches so far toward the pity
     * threshold, the rare part once found, and where to find it. Empty with no rumour (or the card turned off).
     */
    java.util.List<String[]> lines() {
        java.util.List<String[]> lines = new java.util.ArrayList<>();
        SkillHelperData.Rumour rumour = config.rumourCard() ? current() : null;
        if (rumour == null) {
            return lines;
        }
        String hunter = hunter();
        lines.add(new String[]{"Rumour:", rumour.name + (rumour.trap != null ? " (" + rumour.trap + ")" : "")
            + (hunter != null ? ", for " + hunter : "")});
        if (partFound()) {
            lines.add(new String[]{"Rare part:", "found! Take it to the Hunter Guild"});
        } else if (counts(rumour)) {
            int pity = pity(rumour);
            lines.add(new String[]{"Caught:", caught() + (pity > 0 ? " (rare part by " + pity + ")" : "")});
        }
        if (!rumour.places.isEmpty()) {
            lines.add(new String[]{"Where:", rumour.places.get(0)
                + (rumour.places.size() > 1 ? " (+" + (rumour.places.size() - 1) + " more)" : "")});
        }
        return lines;
    }

    /** Every place the rumour creature lives, for the card's tooltip; empty with no rumour. */
    java.util.List<String> places() {
        SkillHelperData.Rumour rumour = current();
        return rumour == null ? java.util.Collections.emptyList() : rumour.places;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // The first XP after login is where catches count from (not after a loading screen: a catch right after
        // one would be lost)
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING
            || event.getGameState() == GameState.CONNECTION_LOST) {
            lastHunterXp = -1;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        String message = event.getMessage();
        if (event.getType() == ChatMessageType.GAMEMESSAGE) {
            String plain = Text.removeTags(message).toLowerCase(Locale.ROOT);
            if (plain.contains("you find a rare piece of the creature")) {
                partPending = true;
            } else if (plain.contains("your current rumour target is")) {
                // The Quetzal whistle's Rumour option
                assign(referenced(plain), hunterIn(plain));
            }
            return;
        }
        if (event.getType() != ChatMessageType.DIALOG || !SPEAKER.matcher(message).find()) {
            return;
        }
        String speaker = message.substring(0, message.indexOf('|'));
        String said = message.substring(message.indexOf('|') + 1).toLowerCase(Locale.ROOT);
        String speakerName = hunterIn(speaker.toLowerCase(Locale.ROOT));
        if (speakerName == null) {
            return;
        }
        if (handedIn(said)) {
            clear();
            return;
        }
        // Wolf's line in At First Light, and Gilman offering another hunter's rumour instead, aren't your rumour
        if (said.contains("stopped off for a bit of hunting first") || said.contains("would you prefer that one, or a new one entirely")) {
            return;
        }
        SkillHelperData.Rumour rumour = referenced(said);
        if (rumour != null) {
            // A hunter naming another hunter's rumour ("Ornus has a rumour about...") means that one's yours
            String other = hunterIn(said);
            assign(rumour, other != null ? other : speakerName);
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() != Skill.HUNTER) {
            return;
        }
        int xp = event.getXp();
        int drop = lastHunterXp < 0 ? 0 : xp - lastHunterXp;
        lastHunterXp = xp;
        SkillHelperData.Rumour rumour = current();
        if (drop <= 0 || rumour == null || partFound()) {
            return;
        }
        SkillHelperData.Creature creature = data.creature(rumour);
        if (creature != null && isCatch(drop, creature.xp, creature.xpMax)) {
            set("rumour.caught", String.valueOf(caught() + 1));
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        int pieces = 0;
        if (worn != null) {
            for (Item item : worn.getItems()) {
                for (int id : OUTFIT) {
                    if (item.getId() == id) {
                        pieces++;
                    }
                }
            }
        }
        outfitPieces = pieces;
        if (!partPending) {
            return;
        }
        partPending = false;
        SkillHelperData.Rumour rumour = current();
        if (configManager.getRSProfileKey() != null) {
            set("rumour.found", "true");
        }
        if (config.rumourCard()) {
            alerts.alert(VoiceAlertService.VoiceLine.RUMOUR_COMPLETE, rumour == null ? null : rumour.name
                + (counts(rumour) ? ", " + caught() + " caught" + (pity(rumour) > 0 ? " of " + pity(rumour) : "") : ""));
        }
    }

    /** Whether an XP drop is a catch of a creature giving this XP (its two roundings, or within its range). */
    static boolean isCatch(int drop, double xp, double xpMax) {
        if (xp <= 0) {
            return false;
        }
        if (xpMax > xp) {
            return drop >= Math.floor(xp) && drop <= Math.ceil(xpMax);
        }
        return drop == (int) Math.floor(xp) || drop == (int) Math.ceil(xp);
    }

    /** A guild hunter's line when you hand a rumour in. */
    static boolean handedIn(String said) {
        return said.contains("would you like another rumour?") || said.contains("here's your reward.")
            || said.contains("another one done?");
    }

    /** The rumour creature named in this (lower-case) text, the longest name first; null if none. */
    SkillHelperData.Rumour referenced(String text) {
        return referenced(data.rumours(), text);
    }

    static SkillHelperData.Rumour referenced(java.util.List<SkillHelperData.Rumour> rumours, String text) {
        SkillHelperData.Rumour best = null;
        int bestLength = 0;
        for (SkillHelperData.Rumour rumour : rumours) {
            for (String name : new String[]{rumour.name, rumour.page}) {
                if (name != null && name.length() > bestLength && text.contains(name.toLowerCase(Locale.ROOT))) {
                    best = rumour;
                    bestLength = name.length();
                }
            }
        }
        return best;
    }

    /** The guild hunter named in this (lower-case) text as a whole word ("Aco", not "tobacco"); null if none. */
    static String hunterIn(String text) {
        for (String hunter : HUNTERS) {
            if (Pattern.compile("\\b" + hunter.toLowerCase(Locale.ROOT) + "\\b").matcher(text).find()) {
                return hunter;
            }
        }
        return null;
    }

    // A new rumour (or the same one again, from the whistle): the count starts over only when it changes
    private void assign(SkillHelperData.Rumour rumour, String hunter) {
        if (rumour == null || configManager.getRSProfileKey() == null) {
            return;
        }
        SkillHelperData.Rumour was = current();
        if (was == rumour && (hunter == null || hunter.equals(hunter()))) {
            return;
        }
        set("rumour.creature", rumour.name);
        if (hunter != null) {
            set("rumour.hunter", hunter);
        } else {
            configManager.unsetRSProfileConfiguration(GROUP, "rumour.hunter");
        }
        if (was != rumour) {
            set("rumour.caught", "0");
            configManager.unsetRSProfileConfiguration(GROUP, "rumour.found");
        }
    }

    private void clear() {
        for (String key : new String[]{"rumour.creature", "rumour.hunter", "rumour.caught", "rumour.found"}) {
            configManager.unsetRSProfileConfiguration(GROUP, key);
        }
    }

    private String get(String key) {
        return configManager.getRSProfileKey() == null ? null : configManager.getRSProfileConfiguration(GROUP, key);
    }

    private void set(String key, String value) {
        if (configManager.getRSProfileKey() != null) {
            configManager.setRSProfileConfiguration(GROUP, key, value);
        }
    }
}
