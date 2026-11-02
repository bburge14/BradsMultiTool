package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Run energy alerts anywhere (the Idle section, every plugin but the Tick Counter): low below a level, and restored
 * once it's back up to one. With several of ours installed only one says them (PluginFamily.handlesShared). Both
 * are off at first; while Low is on, Agility's own run energy alert stays quiet (AgilityTracker).
 */
@Singleton
class RunEnergyAlerts {

    /** How far energy has to come back (or drop) past a level before that alert can go again. */
    static final int REARM = 10;

    private final Client client;
    private final CommonConfig config;
    private final Alerts alerts;
    private final PluginFamily family;

    private boolean lowWarned;
    private boolean restoreArmed;

    @Inject
    RunEnergyAlerts(Client client, CommonConfig config, Alerts alerts, PluginFamily family) {
        this.client = client;
        this.config = config;
        this.alerts = alerts;
        this.family = family;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN || !family.handlesShared()) {
            return;
        }
        int energy = client.getEnergy() / 100;
        if (!alerts.ready()) {
            // Low at login counts as already said; full at login isn't "restored"
            lowWarned = isLow(energy, config.runEnergyLowAt());
            restoreArmed = false;
            return;
        }
        int lowAt = config.runEnergyLowAt();
        if (lowAt > 0 && isLow(energy, lowAt) && !lowWarned) {
            lowWarned = true;
            alerts.alert(AlertType.RUN_LOW, VoiceAlertService.VoiceLine.RUN_ENERGY_LOW, energy + "%");
        } else if (energy >= lowAt + REARM) {
            lowWarned = false;
        }
        int restoredAt = config.runEnergyRestoredAt();
        if (restoredAt <= 0) {
            restoreArmed = false;
        } else if (restoreArmed && energy >= restoredAt) {
            restoreArmed = false;
            alerts.alert(AlertType.RUN_RESTORED, VoiceAlertService.VoiceLine.RUN_ENERGY_RESTORED, energy + "%");
        } else if (droppedEnough(energy, restoredAt)) {
            restoreArmed = true;
        }
    }

    static boolean isLow(int energy, int lowAt) {
        return lowAt > 0 && energy < lowAt;
    }

    /** Energy has dropped far enough below the Restored level that coming back up to it is worth saying. */
    static boolean droppedEnough(int energy, int restoredAt) {
        return energy <= Math.max(0, restoredAt - REARM);
    }
}
