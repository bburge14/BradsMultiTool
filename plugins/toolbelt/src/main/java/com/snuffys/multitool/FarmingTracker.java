package com.snuffys.multitool;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * Every farming patch you've been to: what's in it and when it'll be grown. The game only tells us a patch's state
 * while you're near it, so each time you are, its crop, condition and grown-by time are saved to your RuneLite
 * profile; the sidebar lists them and an alert says when patches you've planted are ready. Only reads the patch
 * game variables (decoded by {@link FarmingData}).
 */
@Singleton
class FarmingTracker {

    private static final String GROUP = "bradsmultitool";
    private static final String PREFIX = "farmPatch.";
    // Read the patches around you this often (ticks); check for ready ones once a minute
    private static final int READ_TICKS = 5;
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

        Record(String key, String title, String crop, String state, long grownAt, boolean alerted) {
            this.key = key;
            this.title = title;
            this.crop = crop;
            this.state = state;
            this.grownAt = grownAt;
            this.alerted = alerted;
        }

        String encode() {
            return title.replace("|", "/") + "|" + crop.replace("|", "/") + "|" + state + "|" + grownAt + "|" + alerted;
        }

        static Record decode(String key, String value) {
            String[] p = value == null ? new String[0] : value.split("\\|", -1);
            if (p.length < 5) {
                return null;
            }
            try {
                return new Record(key, p[0], p[1], p[2], Long.parseLong(p[3]), Boolean.parseBoolean(p[4]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** Grown (harvestable, or past its grown-by time). */
        boolean ready(long now) {
            return FarmingData.HARVESTABLE.equals(state) || FarmingData.GROWING.equals(state) && grownAt > 0 && now >= grownAt;
        }
    }

    private final Client client;
    private final ConfigManager configManager;
    private final FarmingData farming;
    private final SkillTrackerConfig config;
    private final Alerts alerts;
    private final SkillTracker plugin;

    @Inject
    FarmingTracker(Client client, ConfigManager configManager, FarmingData farming, SkillTrackerConfig config, Alerts alerts,
                   SkillTracker plugin) {
        this.client = client;
        this.configManager = configManager;
        this.farming = farming;
        this.config = config;
        this.alerts = alerts;
        this.plugin = plugin;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN || configManager.getRSProfileKey() == null) {
            return;
        }
        int tick = client.getTickCount();
        if (tick % READ_TICKS == 0) {
            readNearby();
        }
        if (tick % CHECK_TICKS == 0) {
            alertReady();
            plugin.updatePanelUI();
        }
    }

    // The patches whose state the game gives us here: save what's in them
    private void readNearby() {
        Player you = client.getLocalPlayer();
        if (you == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        boolean changed = false;
        for (FarmingData.Patch patch : farming.patchesNear(you.getWorldLocation().getRegionID())) {
            FarmingData.State state = farming.state(patch);
            String key = PREFIX + patch.key();
            if (state == null || state.isEmpty()) {
                if (configManager.getRSProfileConfiguration(GROUP, key) != null) {
                    configManager.unsetRSProfileConfiguration(GROUP, key);
                    changed = true;
                }
                continue;
            }
            Record old = Record.decode(key, configManager.getRSProfileConfiguration(GROUP, key));
            long grownAt = FarmingData.GROWING.equals(state.cropState) ? now + FarmingData.secondsUntilGrown(state, now)
                : FarmingData.HARVESTABLE.equals(state.cropState) ? now : 0;
            // Keep the grown-by time it had (a re-read moves it by up to a growth cycle) while it's the same crop
            if (old != null && old.crop.equals(state.produce.name) && old.state.equals(state.cropState) && old.grownAt > 0) {
                grownAt = old.grownAt;
            }
            // Seen grown with your own eyes: no need to tell you; a new crop is told once it grows
            boolean alerted = FarmingData.HARVESTABLE.equals(state.cropState)
                || old != null && old.alerted && old.crop.equals(state.produce.name) && old.state.equals(state.cropState);
            Record record = new Record(key, patch.region + ": " + patch.title(), state.produce.name, state.cropState, grownAt, alerted);
            String encoded = record.encode();
            if (!encoded.equals(configManager.getRSProfileConfiguration(GROUP, key))) {
                configManager.setRSProfileConfiguration(GROUP, key, encoded);
                changed = true;
            }
        }
        if (changed) {
            plugin.updatePanelUI();
        }
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
                configManager.setRSProfileConfiguration(GROUP, record.key,
                    new Record(record.key, record.title, record.crop, record.state, record.grownAt, true).encode());
            }
        }
        if (!ready.isEmpty()) {
            alerts.alert(VoiceAlertService.VoiceLine.FARMING_READY, String.join(", ", ready));
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

    /** Forget a patch (from the sidebar). */
    void forget(String key) {
        configManager.unsetRSProfileConfiguration(GROUP, key);
        plugin.updatePanelUI();
    }
}
