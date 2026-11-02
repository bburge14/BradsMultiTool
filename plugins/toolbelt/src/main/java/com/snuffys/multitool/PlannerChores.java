package com.snuffys.multitool;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * The Planner's TODAY and WEEKLY lists. Presets are the ones the game tells us about: the daily rewards RuneLite's
 * Daily Task Indicator knows (each one's diary or quest, and the variable the game sets once it's collected
 * today), and Tears of Guthix (noticed when your game there ends; Juna lets you back in at the start of the
 * seventh day after). You can add your own (daily or weekly, with a note), rename or edit them, hide presets you
 * don't do, and tick anything by hand; your own ones untick at their reset. Also the weekly list's two read-only
 * lines: the Kingdom of Miscellania (approval and coffer, as RuneLite's Kingdom plugin reads them) and your farming
 * contract (as RuneLite's Time Tracking reads it). Everything is saved with your RuneScape profile.
 * <p>
 * Alerts are one generic voice line ("You have dailies to do", "...weeklies...") at login and at the reset, never
 * one per item, and nothing in chat unless its Chat switch is on.
 */
@Singleton
class PlannerChores {

    static final String GROUP = "bradsmultitool";
    private static final String ITEMS_KEY = "planner.chores";
    private static final String SEEN_KEY = "planner.choresSeen";
    private static final String TOG_KEY = "planner.togLastPlayed";
    private static final String KINGDOM_KEY = "planner.kingdom";
    private static final String CONTRACT_KEY = "planner.contract";
    static final long DAY = 86_400;
    static final long WEEK = 7 * DAY;
    // Read the game's variables this often (ticks)
    private static final int READ_TICKS = 5;

    // Nightmare Zone herb boxes: 9,500 points each, up to 15 a day
    private static final int HERB_BOX_MAX = 15;
    private static final int HERB_BOX_COST = 9500;
    // The Hand in the Sand's varbit once the quest is done (Bert's sand)
    private static final int SAND_QUEST_COMPLETE = 160;
    // Robin's bonemeal: 13 a day for each Morytania diary from medium up
    private static final int BONEMEAL_PER_DIARY = 13;
    private static final int ULTIMATE_IRONMAN = 2;
    // Kingdom of Miscellania (RuneLite's Kingdom plugin): the kingdom's regions, approval out of 127
    private static final List<Integer> KINGDOM_REGIONS = Arrays.asList(10044, 10300);
    private static final int MAX_APPROVAL = 127;

    /** A preset's state as read from the game. */
    static final int LOCKED = -1;
    static final int DUE = 0;
    static final int DONE = 1;

    /** A daily or weekly the game tells us about. */
    static final class Preset {
        final String id;
        final String name;
        final String note;
        final boolean weekly;

        Preset(String id, String name, String note, boolean weekly) {
            this.id = id;
            this.name = name;
            this.note = note;
            this.weekly = weekly;
        }
    }

    static final String TOG = "tog";
    static final List<Preset> PRESETS = Arrays.asList(
        new Preset("staves", "Battlestaves", "Zaff in Varrock (Varrock easy diary)", false),
        new Preset("herbBoxes", "Herb boxes", "Nightmare Zone: 9,500 points each, up to 15 a day (not for Ironmen)", false),
        new Preset("essence", "Pure essence", "Wizard Cromperty in East Ardougne (Ardougne medium diary)", false),
        new Preset("runes", "Random runes", "Lundail at the Mage Arena bank (Wilderness easy diary)", false),
        new Preset("sand", "Buckets of sand", "Bert in Yanille (The Hand in the Sand; not for Ultimate Ironmen)", false),
        new Preset("flax", "Bowstrings", "The Flax keeper in Seers' Village (Kandarin easy diary)", false),
        new Preset("bonemeal", "Bonemeal and slime", "Robin at Port Phasmatys (Morytania medium diary and up)", false),
        new Preset("dynamite", "Dynamite", "Thirus at the Lovakengj blast mine (Kourend & Kebos medium diary)", false),
        new Preset("arrows", "Ogre arrows", "Rantz near Feldip Hills (Western Provinces easy diary)", false),
        new Preset(TOG, "Tears of Guthix", "Juna in the Lumbridge Swamp caves: back at the start of the seventh day after "
            + "your last game, once you've gained a quest point or 100,000 XP since", true));

    /** One of your own dailies or weeklies, or a preset's changes (name, note, hidden, ticked by hand). */
    static final class Item {
        String id;
        String name;
        String note;
        boolean weekly;
        boolean custom;
        boolean hidden;
        /** When it was ticked by hand (epoch seconds); counts until its next reset. */
        long doneAt;
    }

    private static final class Saved {
        List<Item> items = new ArrayList<>();
    }

    private static final class SeenSaved {
        Map<String, Seen> presets = new HashMap<>();
    }

    /** What the game last said about a preset: its state, when, and the value that said it was collected. */
    private static final class Seen {
        int state;
        long at;
        int claimValue;
    }

    /** A line on the TODAY or WEEKLY list. */
    static final class Row {
        final String id;
        final String name;
        final String note;
        final boolean weekly;
        final boolean custom;
        final boolean done;
        /** True when the game ticks it (a preset it can read), false for ones only ticked by hand. */
        final boolean fromGame;
        /** Extra words after the name ("3 of 15 bought", "back in 2d 4h"), or null. */
        final String detail;

        Row(String id, String name, String note, boolean weekly, boolean custom, boolean done, boolean fromGame, String detail) {
            this.id = id;
            this.name = name;
            this.note = note;
            this.weekly = weekly;
            this.custom = custom;
            this.done = done;
            this.fromGame = fromGame;
            this.detail = detail;
        }
    }

    private final Client client;
    private final ConfigManager configManager;
    private final PlannerConfig config;
    private final Alerts alerts;
    private final PlannerView view;
    private final FarmingData farming;
    private final Gson gson;

    // Daily presets the reset just passed for, with the value they read then: due until that value changes (the
    // game may only clear it when you next log in)
    private final Map<String, Integer> pendingReset = new HashMap<>();
    private boolean loggingIn = true;
    private long lastDaily = -1;
    private long lastWeekly = -1;

    @Inject
    PlannerChores(Client client, ConfigManager configManager, PlannerConfig config, Alerts alerts, PlannerView view,
                  FarmingData farming, Gson gson) {
        this.client = client;
        this.configManager = configManager;
        this.config = config;
        this.alerts = alerts;
        this.view = view;
        this.farming = farming;
        this.gson = gson;
    }

    /** Checks again at the next tick in game (the plugin starting, perhaps while logged in). */
    void start() {
        loggingIn = true;
        lastDaily = -1;
        lastWeekly = -1;
    }

    // ---- Resets ----

    /** The last daily reset (00:00 UTC), epoch seconds. */
    static long lastDailyReset(long now) {
        return now - Math.floorMod(now, DAY);
    }

    /** The last weekly reset (Wednesday 00:00 UTC), epoch seconds. */
    static long lastWeeklyReset(long now) {
        long day = Math.floorDiv(now, DAY);
        // 1 January 1970 was a Thursday: (day + 3) % 7 is 0 on Mondays, 2 on Wednesdays
        long sinceWednesday = Math.floorMod(day + 3 - 2, 7);
        return (day - sinceWednesday) * DAY;
    }

    /** When Juna lets you back after a game at this time: the start of the seventh day after. */
    static long togAvailableAt(long lastPlayed) {
        return lastDailyReset(lastPlayed) + WEEK;
    }

    private static long lastReset(boolean weekly, long now) {
        return weekly ? lastWeeklyReset(now) : lastDailyReset(now);
    }

    // ---- Reading the game ----

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGING_IN) {
            loggingIn = true;
            pendingReset.clear();
        }
    }

    // Tears of Guthix: the game ends when the collecting flag goes back to 0. The kingdom's numbers when they change
    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == VarbitID.TOG_MINIGAME_COLLECTING && event.getValue() == 0 && alerts.ready()
            && configManager.getRSProfileKey() != null) {
            configManager.setRSProfileConfiguration(GROUP, TOG_KEY, System.currentTimeMillis() / 1000);
            view.update();
        } else if (event.getVarbitId() == VarbitID.MISC_COFFERS || event.getVarbitId() == VarbitID.MISC_APPROVAL) {
            readKingdom();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!alerts.ready() || configManager.getRSProfileKey() == null || client.getTickCount() % READ_TICKS != 0 && !loggingIn) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        long daily = lastDailyReset(now);
        long weekly = lastWeeklyReset(now);
        // A reset passed while you're logged in: the daily presets you'd collected are due again
        boolean dailyReset = !loggingIn && lastDaily >= 0 && daily != lastDaily;
        boolean weeklyReset = !loggingIn && lastWeekly >= 0 && weekly != lastWeekly;
        if (dailyReset) {
            for (Preset preset : PRESETS) {
                if (!preset.weekly) {
                    pendingReset.put(preset.id, read(preset)[1]);
                }
            }
        }
        readPresets(now);
        readKingdom();
        readContract();
        boolean login = loggingIn;
        loggingIn = false;
        lastDaily = daily;
        lastWeekly = weekly;
        if (login || dailyReset) {
            remind(false, login);
        }
        if (login || weeklyReset) {
            remind(true, login);
        }
        view.update();
    }

    // One voice line if anything's left (and a chat line only if its switch is on)
    private void remind(boolean weekly, boolean login) {
        if (!(weekly ? config.plannerWeekliesAlert() : config.plannerDailiesAlert())) {
            return;
        }
        int left = 0;
        for (Row row : rows(weekly)) {
            // (not a preset the game hasn't told us about yet, like Tears of Guthix before your first game)
            if (!row.done && (row.custom || row.fromGame)) {
                left++;
            }
        }
        if (left > 0) {
            VoiceAlertService.VoiceLine line = weekly ? VoiceAlertService.VoiceLine.WEEKLIES : VoiceAlertService.VoiceLine.DAILIES;
            alerts.alert(line, left + (weekly ? (left == 1 ? " weekly" : " weeklies") : (left == 1 ? " daily" : " dailies"))
                + " left in Snuffy's Planner" + (login ? "" : " after the reset"));
        }
    }

    private void readPresets(long now) {
        Map<String, Seen> seen = seen();
        boolean changed = false;
        for (Preset preset : PRESETS) {
            if (TOG.equals(preset.id)) {
                continue;
            }
            int[] read = read(preset);
            Integer pending = pendingReset.get(preset.id);
            if (pending != null) {
                if (pending == read[1]) {
                    read[0] = read[0] == LOCKED ? LOCKED : DUE;
                } else {
                    pendingReset.remove(preset.id);
                }
            }
            Seen old = seen.get(preset.id);
            if (old == null || old.state != read[0] || old.claimValue != read[1] || lastDailyReset(old.at) != lastDailyReset(now)) {
                Seen fresh = new Seen();
                fresh.state = read[0];
                fresh.claimValue = read[1];
                fresh.at = now;
                seen.put(preset.id, fresh);
                changed = true;
            }
        }
        if (changed) {
            SeenSaved saved = new SeenSaved();
            saved.presets = seen;
            configManager.setRSProfileConfiguration(GROUP, SEEN_KEY, gson.toJson(saved));
        }
    }

    /** A daily preset's state (LOCKED, DUE, DONE) and the value that says it's collected, as the game has them now. */
    private int[] read(Preset preset) {
        boolean member = client.getVarcIntValue(VarClientID.PLAYERMEMBER) == 1;
        if (!member) {
            return new int[]{LOCKED, 0};
        }
        switch (preset.id) {
            case "staves":
                return claimed(done(VarbitID.VARROCK_DIARY_EASY_COMPLETE), VarbitID.ZAFF_LAST_CLAIMED);
            case "herbBoxes": {
                int bought = client.getVarbitValue(VarbitID.NZONE_HERBBOXES_PURCHASED);
                if (client.getVarbitValue(VarbitID.IRONMAN) != 0) {
                    return new int[]{LOCKED, bought};
                }
                if (bought >= HERB_BOX_MAX) {
                    return new int[]{DONE, bought};
                }
                // Not enough points to buy one: nothing to collect today
                return new int[]{client.getVarpValue(VarPlayerID.NZONE_REWARDPOINTS) >= HERB_BOX_COST ? DUE : LOCKED, bought};
            }
            case "essence":
                return claimed(done(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE), VarbitID.ARDOUGNE_FREE_ESSENCE);
            case "runes":
                return claimed(done(VarbitID.WILDERNESS_DIARY_EASY_COMPLETE), VarbitID.LUNDAIL_LAST_CLAIMED);
            case "sand":
                return claimed(client.getVarbitValue(VarbitID.IRONMAN) != ULTIMATE_IRONMAN
                    && client.getVarbitValue(VarbitID.HANDSAND_QUEST) >= SAND_QUEST_COMPLETE, VarbitID.YANILLE_SAND_CLAIMED);
            case "flax":
                return claimed(done(VarbitID.KANDARIN_DIARY_EASY_COMPLETE), VarbitID.SEERS_FREE_FLAX);
            case "bonemeal": {
                int taken = client.getVarbitValue(VarbitID.MORYTANIA_SLIME_CLAIMED);
                if (!done(VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE)) {
                    return new int[]{LOCKED, taken};
                }
                int max = BONEMEAL_PER_DIARY;
                if (done(VarbitID.MORYTANIA_DIARY_HARD_COMPLETE)) {
                    max += BONEMEAL_PER_DIARY;
                    if (done(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE)) {
                        max += BONEMEAL_PER_DIARY;
                    }
                }
                return new int[]{taken < max ? DUE : DONE, taken};
            }
            case "dynamite":
                return claimed(done(VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE), VarbitID.KOUREND_FREE_DYNAMITE);
            case "arrows":
                return claimed(done(VarbitID.WESTERN_DIARY_EASY_COMPLETE), VarbitID.WESTERN_RANTZ_ARROWS);
            default:
                return new int[]{LOCKED, 0};
        }
    }

    private int[] claimed(boolean unlocked, int claimedVarbit) {
        int value = client.getVarbitValue(claimedVarbit);
        return new int[]{!unlocked ? LOCKED : value == 0 ? DUE : DONE, value};
    }

    private boolean done(int diaryVarbit) {
        return client.getVarbitValue(diaryVarbit) == 1;
    }

    // As RuneLite's Kingdom plugin: after Throne of Miscellania, while you're there or once both numbers are set
    private void readKingdom() {
        if (client.getGameState() != GameState.LOGGED_IN || configManager.getRSProfileKey() == null
            || client.getVarpValue(VarPlayerID.MISC_QUEST) <= 0) {
            return;
        }
        int coffer = client.getVarbitValue(VarbitID.MISC_COFFERS);
        int approval = client.getVarbitValue(VarbitID.MISC_APPROVAL);
        Player you = client.getLocalPlayer();
        boolean there = you != null && KINGDOM_REGIONS.contains(you.getWorldLocation().getRegionID());
        if (!there && (coffer <= 0 || approval <= 0)) {
            return;
        }
        long[] old = kingdom();
        if (old == null || old[0] != approval || old[1] != coffer) {
            configManager.setRSProfileConfiguration(GROUP, KINGDOM_KEY, approval + "|" + coffer + "|" + System.currentTimeMillis() / 1000);
            view.update();
        }
    }

    // As RuneLite's Time Tracking: the contract's crop, or that it's done (collect the reward)
    private void readContract() {
        int complete = client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COMPLETE);
        int type = client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE);
        String value = complete > 0 ? "done" : String.valueOf(type);
        if (!value.equals(configManager.getRSProfileConfiguration(GROUP, CONTRACT_KEY))) {
            configManager.setRSProfileConfiguration(GROUP, CONTRACT_KEY, value);
        }
    }

    // ---- What the lists show ----

    /** The TODAY (or WEEKLY) list: presets you have (not hidden), then your own, in the order added. */
    List<Row> rows(boolean weekly) {
        List<Row> out = new ArrayList<>();
        if (configManager.getRSProfileKey() == null) {
            return out;
        }
        long now = System.currentTimeMillis() / 1000;
        long reset = lastReset(weekly, now);
        Map<String, Item> changes = new HashMap<>();
        List<Item> own = new ArrayList<>();
        for (Item item : saved().items) {
            if (item.custom) {
                own.add(item);
            } else {
                changes.put(item.id, item);
            }
        }
        Map<String, Seen> seen = seen();
        for (Preset preset : PRESETS) {
            Item change = changes.get(preset.id);
            if (preset.weekly != weekly || change != null && change.hidden) {
                continue;
            }
            String name = change != null && change.name != null ? change.name : preset.name;
            String note = change != null && change.note != null ? change.note : preset.note;
            boolean byHand = change != null && change.doneAt >= reset;
            if (TOG.equals(preset.id)) {
                long last = tog();
                long back = last > 0 ? togAvailableAt(last) : 0;
                boolean done = now < back;
                String detail = done ? "back in " + PlannerPanel.formatDuration(back - now) : last > 0 ? null : "not seen yet";
                out.add(new Row(preset.id, name, note, true, false, done, last > 0, detail));
                continue;
            }
            Seen state = seen.get(preset.id);
            if (state == null || state.state == LOCKED) {
                // Not unlocked (or not seen yet: shown once you log in)
                continue;
            }
            // Read before the last reset: what was collected then is due again now
            boolean done = state.state == DONE && state.at >= lastDailyReset(now);
            String detail = null;
            if ("herbBoxes".equals(preset.id) && state.at >= lastDailyReset(now)) {
                detail = state.claimValue + " of " + HERB_BOX_MAX + " bought";
            }
            out.add(new Row(preset.id, name, note, false, false, done || byHand, true, detail));
        }
        for (Item item : own) {
            if (item.weekly == weekly) {
                out.add(new Row(item.id, item.name, item.note, item.weekly, true, item.doneAt >= reset, false, null));
            }
        }
        return out;
    }

    /** How many presets of this list are hidden. */
    int hiddenCount(boolean weekly) {
        int count = 0;
        for (Item item : saved().items) {
            if (!item.custom && item.hidden) {
                Preset preset = preset(item.id);
                if (preset != null && preset.weekly == weekly) {
                    count++;
                }
            }
        }
        return count;
    }

    /** "87% approval, coffer 750K", seen when (epoch seconds); null if never seen. */
    long[] kingdom() {
        String value = configManager.getRSProfileConfiguration(GROUP, KINGDOM_KEY);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|");
        try {
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
        } catch (RuntimeException e) {
            return null;
        }
    }

    static int approvalPercent(long approval) {
        return (int) (approval * 100 / MAX_APPROVAL);
    }

    /** Your farming contract: the crop's name, "done" when it's grown and handed in... or null when you have none. */
    String contract() {
        String value = configManager.getRSProfileConfiguration(GROUP, CONTRACT_KEY);
        if (value == null || "0".equals(value)) {
            return null;
        }
        if ("done".equals(value)) {
            return value;
        }
        try {
            return farming.contractCrop(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long tog() {
        String value = configManager.getRSProfileConfiguration(GROUP, TOG_KEY);
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- Changes from the sidebar ----

    /** Ticks (or unticks) a line by hand. Tears of Guthix ticked counts as played now. */
    void setDone(String id, boolean done) {
        long now = System.currentTimeMillis() / 1000;
        if (TOG.equals(id)) {
            if (done) {
                configManager.setRSProfileConfiguration(GROUP, TOG_KEY, now);
            } else {
                configManager.unsetRSProfileConfiguration(GROUP, TOG_KEY);
            }
            view.update();
            return;
        }
        change(id, item -> item.doneAt = done ? now : 0);
    }

    /** Adds one of your own. */
    void add(String name, boolean weekly, String note) {
        Saved saved = saved();
        Item item = new Item();
        item.id = "c" + System.currentTimeMillis();
        item.name = name;
        item.note = note == null || note.trim().isEmpty() ? null : note.trim();
        item.weekly = weekly;
        item.custom = true;
        saved.items.add(item);
        save(saved);
    }

    /** Renames a line and changes its note (presets keep their game checks). */
    void edit(String id, String name, String note) {
        change(id, item -> {
            item.name = name;
            item.note = note == null || note.trim().isEmpty() ? null : note.trim();
        });
    }

    /** Deletes one of your own, or hides a preset. */
    void remove(String id) {
        Saved saved = saved();
        if (saved.items.removeIf(item -> item.custom && item.id.equals(id))) {
            save(saved);
        } else {
            change(id, item -> item.hidden = true);
        }
    }

    /** Shows this list's hidden presets again. */
    void showHidden(boolean weekly) {
        Saved saved = saved();
        for (Item item : saved.items) {
            Preset preset = preset(item.id);
            if (!item.custom && preset != null && preset.weekly == weekly) {
                item.hidden = false;
            }
        }
        save(saved);
    }

    private void change(String id, java.util.function.Consumer<Item> how) {
        Saved saved = saved();
        Item found = null;
        for (Item item : saved.items) {
            if (item.id.equals(id)) {
                found = item;
            }
        }
        if (found == null) {
            Preset preset = preset(id);
            if (preset == null) {
                return;
            }
            found = new Item();
            found.id = id;
            found.weekly = preset.weekly;
            saved.items.add(found);
        }
        how.accept(found);
        save(saved);
    }

    private static Preset preset(String id) {
        for (Preset preset : PRESETS) {
            if (preset.id.equals(id)) {
                return preset;
            }
        }
        return null;
    }

    // ---- Saving ----

    private Saved saved() {
        String json = configManager.getRSProfileKey() == null ? null : configManager.getRSProfileConfiguration(GROUP, ITEMS_KEY);
        try {
            Saved saved = json == null ? null : gson.fromJson(json, Saved.class);
            if (saved != null && saved.items != null) {
                saved.items.removeIf(item -> item == null || item.id == null);
                return saved;
            }
        } catch (JsonSyntaxException e) {
            // Unreadable: start again
        }
        return new Saved();
    }

    private void save(Saved saved) {
        if (configManager.getRSProfileKey() != null) {
            configManager.setRSProfileConfiguration(GROUP, ITEMS_KEY, gson.toJson(saved));
        }
        view.update();
    }

    private Map<String, Seen> seen() {
        String json = configManager.getRSProfileConfiguration(GROUP, SEEN_KEY);
        Map<String, Seen> out = new HashMap<>();
        try {
            SeenSaved saved = json == null ? null : gson.fromJson(json, SeenSaved.class);
            if (saved != null && saved.presets != null) {
                out.putAll(saved.presets);
            }
        } catch (JsonSyntaxException e) {
            // Unreadable: read afresh
        }
        return out;
    }
}
