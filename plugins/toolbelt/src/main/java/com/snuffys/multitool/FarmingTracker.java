package com.snuffys.multitool;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WidgetNode;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * The Planner's farming list: every farming patch, compost bin and Hespori patch you've been to, what's in it and
 * when it'll be grown. The game only tells us a patch's state while you're near it, so each time you are, its
 * crop, condition and grown-by time are saved to your RuneScape profile (under the shared group, as before the
 * list moved here from the Skilling Helper, so nothing saved is lost); the sidebar lists them and an alert says
 * when patches you've planted are ready. Only reads the patch game variables (decoded by {@link FarmingData}).
 * <p>
 * Patches of one kind share a game variable, so it's only read with the same care RuneLite's Time Tracking takes:
 * after two ticks in the same region (right after a teleport the old area's value can still be there), and not
 * while a full-screen interface is open (the game doesn't send patch values then).
 */
@Singleton
class FarmingTracker {

    private static final String GROUP = "bradsmultitool";
    private static final String PREFIX = "farmPatch.";
    // Check for ready patches once a minute
    private static final int CHECK_TICKS = 100;

    /** A patch as last seen. */
    static final class Record {
        final String key;
        final String title;
        final String crop;
        final String state;
        /** When it's grown (epoch seconds), 0 when it isn't growing. */
        final long grownAt;
        final boolean alerted;
        /** Its growth stage when seen (-1 if not known: saved before stages were kept). */
        final int stage;
        /** How far out grownAt may be (seconds): a growth cycle until the account's tick offset is known, else 0. */
        final long slack;

        Record(String key, String title, String crop, String state, long grownAt, boolean alerted) {
            this(key, title, crop, state, grownAt, alerted, -1, 0);
        }

        Record(String key, String title, String crop, String state, long grownAt, boolean alerted, int stage, long slack) {
            this.key = key;
            this.title = title;
            this.crop = crop;
            this.state = state;
            this.grownAt = grownAt;
            this.alerted = alerted;
            this.stage = stage;
            this.slack = slack;
        }

        String encode() {
            return title.replace("|", "/") + "|" + crop.replace("|", "/") + "|" + state + "|" + grownAt + "|" + alerted
                + "|" + stage + "|" + slack;
        }

        static Record decode(String key, String value) {
            String[] p = value == null ? new String[0] : value.split("\\|", -1);
            if (p.length < 5) {
                return null;
            }
            try {
                // (stage and slack were added later: older records have neither)
                int stage = p.length > 5 ? Integer.parseInt(p[5]) : -1;
                long slack = p.length > 6 ? Long.parseLong(p[6]) : 0;
                return new Record(key, p[0], p[1], p[2], Long.parseLong(p[3]), Boolean.parseBoolean(p[4]), stage, slack);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        Record alerted() {
            return new Record(key, title, crop, state, grownAt, true, stage, slack);
        }

        /** Grown (harvestable, or past its grown-by time, give or take its slack). */
        boolean ready(long now) {
            return FarmingData.HARVESTABLE.equals(state) || FarmingData.GROWING.equals(state) && grownAt > 0 && now >= grownAt + slack;
        }
    }

    private final Client client;
    private final ConfigManager configManager;
    private final FarmingData farming;
    private final PlannerConfig config;
    @javax.inject.Inject
    private PlannerSound sound;
    private final Alerts alerts;
    private final PlannerView view;

    // The region you were in last tick and at the last read, and when a full-screen interface last closed
    private int lastTickRegion = -1;
    private int lastReadRegion = -1;
    private int lastModalClose;

    @Inject
    FarmingTracker(Client client, ConfigManager configManager, FarmingData farming, PlannerConfig config, Alerts alerts,
                   PlannerView view) {
        this.client = client;
        this.configManager = configManager;
        this.farming = farming;
        this.config = config;
        this.alerts = alerts;
        this.view = view;
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        if (event.getModalMode() != WidgetModalMode.NON_MODAL) {
            lastModalClose = client.getTickCount();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN || configManager.getRSProfileKey() == null) {
            return;
        }
        int tick = client.getTickCount();
        if (tick % CHECK_TICKS == 0) {
            alertReady();
            view.update();
        }
        Player you = client.getLocalPlayer();
        int region = you == null ? -1 : you.getWorldLocation().getRegionID();
        int last = lastTickRegion;
        lastTickRegion = region;
        // In the same region two ticks running, so the values are this area's
        if (region < 0 || region != last) {
            return;
        }
        for (WidgetNode node : client.getComponentTable()) {
            if (node.getModalMode() != WidgetModalMode.NON_MODAL) {
                return;
            }
        }
        boolean arrived = region != lastReadRegion;
        lastReadRegion = region;
        readNearby(region, !arrived && tick - lastModalClose > 1);
    }

    // The patches whose state the game gives us here: save what's in them. A change seen while you've been standing
    // here (not one found on arriving) happened just now, so a crop growing a stage tells us the account's tick offset
    private void readNearby(int region, boolean watching) {
        long now = System.currentTimeMillis() / 1000;
        boolean changed = false;
        for (FarmingData.Patch patch : farming.patchesNear(region)) {
            FarmingData.State state = farming.state(patch);
            String key = PREFIX + patch.key();
            if (state == null || state.isEmpty() || "EMPTY".equals(state.cropState)) {
                if (configManager.getRSProfileConfiguration(GROUP, key) != null) {
                    configManager.unsetRSProfileConfiguration(GROUP, key);
                    changed = true;
                }
                continue;
            }
            Record old = Record.decode(key, configManager.getRSProfileConfiguration(GROUP, key));
            boolean sameCrop = old != null && old.crop.equals(state.produce.name);
            if (watching && sameCrop && grewJustNow(old, state)) {
                farming.observedGrowthTick(state.produce.tickrate, now);
            }
            boolean exact = farming.tickOffset(state.produce.tickrate) >= 0;
            long slack = FarmingData.GROWING.equals(state.cropState) && !exact ? state.produce.tickrate * 60L : 0;
            long grownAt = FarmingData.GROWING.equals(state.cropState) ? now + farming.untilGrown(state, now)
                : FarmingData.HARVESTABLE.equals(state.cropState) ? now : 0;
            // Without the offset each re-read can move the time by up to a growth cycle: keep the one it had while
            // it's the same crop
            if (!exact && sameCrop && old.state.equals(state.cropState) && old.grownAt > 0 && old.slack > 0) {
                grownAt = old.grownAt;
            }
            // Seen grown with your own eyes: no need to tell you; a new crop is told once it grows
            boolean alerted = FarmingData.HARVESTABLE.equals(state.cropState)
                || old != null && old.alerted && sameCrop && old.state.equals(state.cropState);
            Record record = new Record(key, patch.region + ": " + patch.title(), state.produce.name, state.cropState, grownAt,
                alerted, state.stage, slack);
            String encoded = record.encode();
            if (!encoded.equals(configManager.getRSProfileConfiguration(GROUP, key))) {
                configManager.setRSProfileConfiguration(GROUP, key, encoded);
                changed = true;
            }
        }
        if (changed) {
            view.update();
        }
    }

    // As Time Tracking tells a growth tick: a growing crop one stage on, or turning diseased; a diseased one dying
    static boolean grewJustNow(Record old, FarmingData.State now) {
        if (FarmingData.GROWING.equals(old.state)) {
            return FarmingData.GROWING.equals(now.cropState) && old.stage >= 0 && now.stage == old.stage + 1
                || FarmingData.DISEASED.equals(now.cropState);
        }
        return FarmingData.DISEASED.equals(old.state) && FarmingData.DEAD.equals(now.cropState);
    }

    // Patches that have grown since you planted them: one alert naming them
    private void alertReady() {
        if (!config.farmingReadyAlert() || !alerts.ready()) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        List<String> ready = new ArrayList<>();
        for (Record record : records()) {
            if (!record.alerted && record.ready(now)) {
                ready.add(record.title);
                configManager.setRSProfileConfiguration(GROUP, record.key, record.alerted().encode());
            }
        }
        if (!ready.isEmpty()) {
            sound.done(VoiceAlertService.VoiceLine.FARMING_READY, String.join(", ", ready));
        }
    }

    /** Every patch you've been to with something in it, as last seen, soonest grown first. */
    List<Record> records() {
        List<Record> out = new ArrayList<>();
        if (configManager.getRSProfileKey() == null) {
            return out;
        }
        for (String key : configManager.getRSProfileConfigurationKeys(GROUP, configManager.getRSProfileKey(), PREFIX)) {
            // The keys come back whole ("farmPatch.11061.4774")
            Record record = Record.decode(key, configManager.getRSProfileConfiguration(GROUP, key));
            if (record != null) {
                out.add(record);
            }
        }
        out.sort((a, b) -> Long.compare(order(a), order(b)));
        return out;
    }

    // Ready ones first, then by grown-by time; diseased and dead at the end
    private static long order(Record record) {
        if (FarmingData.HARVESTABLE.equals(record.state)) {
            return 0;
        }
        return record.grownAt > 0 ? record.grownAt : Long.MAX_VALUE;
    }

    /** How long a patch's crop takes to grow (seconds), for the sidebar's progress bars; 0 when not known. */
    long growthSeconds(Record record) {
        return farming.growthSeconds(record.crop);
    }

    /** Forget a patch (from the sidebar). */
    void forget(String key) {
        configManager.unsetRSProfileConfiguration(GROUP, key);
        view.update();
    }
}
