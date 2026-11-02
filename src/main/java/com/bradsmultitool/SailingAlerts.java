package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Sailing alerts, only while you're on your own boat: its hitpoints dropping below your setting (once until it's
 * repaired above it), and a gust opening the window to trim the sails (the game's buff bar times the same window).
 * Reads the boat's game variables the game's own side panel and buff bar use; the low-health idea is from the
 * Sailing Plugin Hub plugin (BSD-2-Clause, LlemonDuck). Never trims or steers for you.
 */
@Singleton
class SailingAlerts {

    private final Client client;
    private final Alerts alerts;
    private final SkillTrackerConfig config;
    private boolean healthWarned;
    private boolean trimOpen;

    @Inject
    SailingAlerts(Client client, Alerts alerts, SkillTrackerConfig config) {
        this.client = client;
        this.alerts = alerts;
        this.config = config;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT) != 1) {
            healthWarned = false;
            trimOpen = false;
            return;
        }
        int hp = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_HP);
        int max = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_HP_MAX);
        int percent = percent(hp, max);
        if (percent >= 0 && percent < config.boatHealthPercent()) {
            if (!healthWarned) {
                healthWarned = true;
                alerts.alert(VoiceAlertService.VoiceLine.BOAT_HEALTH_LOW, hp + "/" + max);
            }
        } else if (percent >= config.boatHealthPercent()) {
            healthWarned = false;
        }
        boolean open = client.getVarbitValue(VarbitID.SAILING_BOAT_TIME_TRIM_WINDOW) > 0;
        if (open && !trimOpen) {
            alerts.alert(VoiceAlertService.VoiceLine.TRIM_SAILS, null);
        }
        trimOpen = open;
    }

    /** The boat's hitpoints as a percent of its most, or -1 when there's no boat (max 0). */
    static int percent(int hp, int max) {
        return max <= 0 ? -1 : hp * 100 / max;
    }
}
