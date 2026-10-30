package com.snuffys.multitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * Fossil Island bird houses, as RuneLite's Time Tracking reads them: each of the four spots is a player
 * variable (0 = empty; type = (value - 1) / 3; divisible by 3 = seeded), and a seeded bird house is full
 * 50 minutes later. The seeding time is noted when the variable changes and kept with your profile.
 */
@Singleton
class BirdhouseTracker {

    @Inject
    private Alerts alerts;

    static final int[] SPOTS = {VarPlayerID.BIRDHOUSE_TRANSMIT_A, VarPlayerID.BIRDHOUSE_TRANSMIT_B,
        VarPlayerID.BIRDHOUSE_TRANSMIT_C, VarPlayerID.BIRDHOUSE_TRANSMIT_D};
    static final String[] SPOT_NAMES = {"Mushroom Meadow (North)", "Mushroom Meadow (South)",
        "Verdant Valley (Northeast)", "Verdant Valley (Southwest)"};
    private static final String[] TYPES = {"Bird house", "Oak bird house", "Willow bird house", "Teak bird house",
        "Maple bird house", "Mahogany bird house", "Yew bird house", "Magic bird house", "Redwood bird house"};
    static final long FULL_AFTER_SECONDS = 50 * 60;
    private static final String CONFIG_GROUP = "bradsmultitool";

    private final Client client;
    private final ConfigManager configManager;
    private final SkillTrackerConfig config;

    @Inject
    BirdhouseTracker(Client client, ConfigManager configManager, SkillTrackerConfig config) {
        this.client = client;
        this.configManager = configManager;
        this.config = config;
    }

    /** Index into {@link #SPOTS} for a player variable, or -1. */
    static int spotFor(int varp) {
        for (int i = 0; i < SPOTS.length; i++) {
            if (SPOTS[i] == varp) {
                return i;
            }
        }
        return -1;
    }

    static boolean isSeeded(int value) {
        return value > 0 && value % 3 == 0;
    }

    /** "Oak bird house", or null when the spot is empty. */
    static String type(int value) {
        if (value <= 0) {
            return null;
        }
        int type = (value - 1) / 3;
        return type < TYPES.length ? TYPES[type] : "Bird house";
    }

    int value(int spot) {
        return client.getVarpValue(SPOTS[spot]);
    }

    /** When the spot was seeded (epoch seconds), or 0 if unknown. */
    long seededAt(int spot) {
        String value = configManager.getRSProfileConfiguration(CONFIG_GROUP, "birdhouse." + spot);
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        int spot = spotFor(event.getVarpId());
        if (spot < 0) {
            return;
        }
        boolean seeded = isSeeded(event.getValue());
        if (!seeded) {
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, "birdhouse." + spot);
        } else if (seededAt(spot) == 0 && alerts.ready()) {
            // Just seeded now (at login the value arrives too, but then we don't know when it was seeded)
            configManager.setRSProfileConfiguration(CONFIG_GROUP, "birdhouse." + spot, System.currentTimeMillis() / 1000);
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, "birdhousesAlerted");
        }
    }

    // Once every seeded bird house with a known time is full, say so once
    @Subscribe
    public void onGameTick(GameTick event) {
        if (!config.birdhouseAlert() || !alerts.ready()
            || configManager.getRSProfileConfiguration(CONFIG_GROUP, "birdhousesAlerted") != null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        boolean any = false;
        for (int spot = 0; spot < SPOTS.length; spot++) {
            long seeded = seededAt(spot);
            if (seeded == 0) {
                continue;
            }
            if (now < seeded + FULL_AFTER_SECONDS) {
                return;
            }
            any = true;
        }
        if (any) {
            configManager.setRSProfileConfiguration(CONFIG_GROUP, "birdhousesAlerted", "true");
            alerts.alert(VoiceAlertService.VoiceLine.BIRDHOUSES_READY, null);
        }
    }
}
