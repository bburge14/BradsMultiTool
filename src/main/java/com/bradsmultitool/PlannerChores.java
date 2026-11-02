package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * The Planner's TODAY and WEEKLY lists: your own dailies and weeklies (a name, an optional note and picture), ticked
 * by hand and due again at their reset. Nothing is read from the game. Adding one can start from a preset task
 * ({@link #TEMPLATES}: its name, note and picture filled in); while a list is empty the panel shows a few of them as
 * examples, not tracked. Everything is saved with your RuneScape profile.
 * <p>
 * Alerts are one generic voice line ("You have dailies to do", "...weeklies...") at login and at the reset, never
 * one per item, and nothing in chat unless its Chat switch is on.
 */
@Singleton
class PlannerChores {

    static final String GROUP = "bradsmultitool";
    private static final String ITEMS_KEY = "planner.chores";
    static final long DAY = 86_400;
    static final long WEEK = 7 * DAY;
    // Check for a reset this often (ticks)
    private static final int CHECK_TICKS = 5;

    /** A preset task to start from when adding one: its name, where (the note), picture and reset. */
    static final class Template {
        final String name;
        final String note;
        final int icon;
        final boolean weekly;

        Template(String name, String note, int icon, boolean weekly) {
            this.name = name;
            this.note = note;
            this.icon = icon;
            this.weekly = weekly;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final List<Template> TEMPLATES = Arrays.asList(
        new Template("Battlestaves", "Zaff in Varrock (Varrock easy diary)", ItemID.BATTLESTAFF, false),
        new Template("Buckets of sand", "Bert in Yanille (The Hand in the Sand)", ItemID.BUCKET_SAND, false),
        new Template("Pure essence", "Wizard Cromperty in East Ardougne (Ardougne medium diary)", ItemID.BLANKRUNE_HIGH, false),
        new Template("Random runes", "Lundail at the Mage Arena bank (Wilderness easy diary)", ItemID.CHAOSRUNE, false),
        new Template("Bowstrings", "The Flax keeper in Seers' Village (Kandarin easy diary)", ItemID.BOW_STRING, false),
        new Template("Bonemeal and slime", "Robin at Port Phasmatys (Morytania medium diary)", ItemID.BUCKET_ECTOPLASM, false),
        new Template("Dynamite", "Thirus at the Lovakengj blast mine (Kourend & Kebos medium diary)",
            ItemID.LOVAKENGJ_DYNAMITE_FUSED, false),
        new Template("Ogre arrows", "Rantz near Feldip Hills (Western Provinces easy diary)", ItemID.OGRE_ARROW, false),
        new Template("Herb boxes", "Nightmare Zone: 9,500 points each, up to 15 a day", ItemID.NZONE_HERBBOX, false),
        new Template("Herb run", "Catherby, Ardougne, Falador, the Farming Guild...", ItemID.UNIDENTIFIED_RANARR, false),
        new Template("Bird house run", "Fossil Island", ItemID.BIRDHOUSE_NORMAL, false),
        new Template("Clue scroll", "Do a clue", ItemID.CASKET, false),
        new Template("Tears of Guthix", "Juna in the Lumbridge Swamp caves", ItemID.TOG_BOWL, true),
        new Template("Kingdom of Miscellania", "Keep its approval up, collect from Advisor Ghrim", ItemID.COINS, true),
        new Template("Penguin hunting", "Spy on the penguins for Larry", ItemID.PENGUIN_MASK, true),
        new Template("Farming contract", "Guildmaster Jane at the Farming Guild", ItemID.WATERING_CAN_8, true));

    /** One of your dailies or weeklies. */
    static final class Item {
        String id;
        String name;
        String note;
        boolean weekly;
        /** (kept for older saves: lines that were presets the game read, now left out) */
        boolean custom;
        /** Its picture's item id, 0 for none. */
        int icon;
        /** When it was ticked (epoch seconds); counts until its next reset. */
        long doneAt;
    }

    private static final class Saved {
        List<Item> items = new ArrayList<>();
    }

    /** A line on the TODAY or WEEKLY list. */
    static final class Row {
        final String id;
        final String name;
        final String note;
        final boolean weekly;
        final boolean done;
        /** Its picture's item id, 0 for none. */
        final int icon;

        Row(String id, String name, String note, boolean weekly, boolean done, int icon) {
            this.id = id;
            this.name = name;
            this.note = note;
            this.weekly = weekly;
            this.done = done;
            this.icon = icon;
        }
    }

    private final Client client;
    private final ConfigManager configManager;
    private final PlannerConfig config;
    private final Alerts alerts;
    private final PlannerView view;
    private final Gson gson;

    private boolean loggingIn = true;
    private long lastDaily = -1;
    private long lastWeekly = -1;

    @Inject
    PlannerChores(Client client, ConfigManager configManager, PlannerConfig config, Alerts alerts, PlannerView view,
                  Gson gson) {
        this.client = client;
        this.configManager = configManager;
        this.config = config;
        this.alerts = alerts;
        this.view = view;
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

    private static long lastReset(boolean weekly, long now) {
        return weekly ? lastWeeklyReset(now) : lastDailyReset(now);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGING_IN) {
            loggingIn = true;
        }
    }

    // At login and at each reset (while you're in game): the one reminder line if anything's left
    @Subscribe
    public void onGameTick(GameTick event) {
        if (!alerts.ready() || configManager.getRSProfileKey() == null || client.getTickCount() % CHECK_TICKS != 0 && !loggingIn) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        long daily = lastDailyReset(now);
        long weekly = lastWeeklyReset(now);
        boolean dailyReset = !loggingIn && lastDaily >= 0 && daily != lastDaily;
        boolean weeklyReset = !loggingIn && lastWeekly >= 0 && weekly != lastWeekly;
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
        if (dailyReset || weeklyReset) {
            view.update();
        }
    }

    // One voice line if anything's left (and a chat line only if its switch is on)
    private void remind(boolean weekly, boolean login) {
        if (!(weekly ? config.plannerWeekliesAlert() : config.plannerDailiesAlert())) {
            return;
        }
        int left = 0;
        for (Row row : rows(weekly)) {
            if (!row.done) {
                left++;
            }
        }
        if (left > 0) {
            VoiceAlertService.VoiceLine line = weekly ? VoiceAlertService.VoiceLine.WEEKLIES : VoiceAlertService.VoiceLine.DAILIES;
            alerts.alert(line, left + (weekly ? (left == 1 ? " weekly" : " weeklies") : (left == 1 ? " daily" : " dailies"))
                + " left in Snuffy's Planner" + (login ? "" : " after the reset"));
        }
    }

    // ---- What the lists show ----

    /** The TODAY (or WEEKLY) list: yours, in the order added. */
    List<Row> rows(boolean weekly) {
        List<Row> out = new ArrayList<>();
        if (configManager.getRSProfileKey() == null) {
            return out;
        }
        long reset = lastReset(weekly, System.currentTimeMillis() / 1000);
        for (Item item : saved().items) {
            // (older saves also kept changes to the presets the game was read for: those are left out)
            if (item.custom && item.weekly == weekly && item.name != null) {
                out.add(new Row(item.id, item.name, item.note, item.weekly, item.doneAt >= reset, item.icon));
            }
        }
        return out;
    }

    // ---- Changes from the sidebar ----

    /** Ticks (or unticks) one. */
    void setDone(String id, boolean done) {
        long now = System.currentTimeMillis() / 1000;
        change(id, item -> item.doneAt = done ? now : 0);
    }

    /** Adds one, with its picture's item id (0 for none). */
    void add(String name, boolean weekly, String note, int icon) {
        Saved saved = saved();
        Item item = new Item();
        item.id = "c" + System.currentTimeMillis();
        item.name = name;
        item.note = note == null || note.trim().isEmpty() ? null : note.trim();
        item.weekly = weekly;
        item.custom = true;
        item.icon = icon;
        saved.items.add(item);
        save(saved);
    }

    /** Renames one, and changes its note and picture. */
    void edit(String id, String name, String note, int icon) {
        change(id, item -> {
            item.name = name;
            item.note = note == null || note.trim().isEmpty() ? null : note.trim();
            item.icon = icon;
        });
    }

    void remove(String id) {
        Saved saved = saved();
        if (saved.items.removeIf(item -> item.id.equals(id))) {
            save(saved);
        }
    }

    private void change(String id, java.util.function.Consumer<Item> how) {
        Saved saved = saved();
        for (Item item : saved.items) {
            if (item.id.equals(id)) {
                how.accept(item);
                save(saved);
                return;
            }
        }
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
}
