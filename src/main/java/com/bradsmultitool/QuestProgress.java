package com.bradsmultitool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Which quests you've finished, for leaving out training methods you can't do yet. RuneLite reads a quest's
 * state with a (read-only) client script, which has to run on the client thread, so the states are read
 * here after login, after a quest is completed and every few minutes, and kept for the panel to look up.
 */
@Singleton
class QuestProgress {

    // Every 5 minutes, to catch anything the triggers below miss
    private static final int REFRESH_TICKS = 500;

    private final Client client;
    // Lower-case quest name -> its state; replaced whole on each refresh
    private volatile Map<String, QuestState> finished = Collections.emptyMap();
    private boolean refreshNeeded = true;
    private int lastRefreshTick = -REFRESH_TICKS;

    @Inject
    QuestProgress(Client client) {
        this.client = client;
    }

    /**
     * Whether you've finished this quest or miniquest. True when it isn't known (a name RuneLite doesn't
     * list, or before the first read), so nothing is hidden by mistake.
     */
    boolean isFinished(String quest) {
        QuestState state = finished.get(quest.toLowerCase());
        return state == null || state == QuestState.FINISHED;
    }

    /** Whether you've at least started this quest (for what only needs part of it done). True when unknown. */
    boolean isStarted(String quest) {
        QuestState state = finished.get(quest.toLowerCase());
        return state == null || state != QuestState.NOT_STARTED;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            refreshNeeded = true;
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            finished = Collections.emptyMap();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().contains("ompleted")) {
            refreshNeeded = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        int tick = client.getTickCount();
        if (client.getGameState() != GameState.LOGGED_IN || (!refreshNeeded && tick - lastRefreshTick < REFRESH_TICKS)) {
            return;
        }
        refreshNeeded = false;
        lastRefreshTick = tick;
        Map<String, QuestState> states = new HashMap<>();
        for (Quest quest : Quest.values()) {
            states.put(quest.getName().toLowerCase(), quest.getState(client));
        }
        finished = states;
    }
}
