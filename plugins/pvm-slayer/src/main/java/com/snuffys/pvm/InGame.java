package com.snuffys.pvm;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Whether you're really in the game: logged in, past loading and the welcome screen's "Click here to play", for
 * a few ticks. Alerts and the tick counter wait for it (the game ticks on the welcome screen too).
 */
@Singleton
class InGame {

    private static final int WELCOME_SCREEN_GROUP = 378;
    private static final int SETTLE_TICKS = 3;

    private final Client client;
    private int ticks = 0;

    @Inject
    InGame(Client client) {
        this.client = client;
    }

    boolean ready() {
        return ticks > SETTLE_TICKS;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            ticks = 0;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        Widget welcome = client.getWidget(WELCOME_SCREEN_GROUP, 0);
        if (player == null || (welcome != null && !welcome.isHidden())) {
            ticks = 0;
        } else {
            ticks++;
        }
    }
}
