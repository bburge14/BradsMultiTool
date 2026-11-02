package com.snuffys.voices;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Daily reminders every one of Snuffy's plugins has (said by one of them, PluginFamily.handlesShared): at login
 * and at the daily reset, the daily rewards you've unlocked but not collected, in one chat line (and a voice
 * line). The same checks as RuneLite's Daily Task Indicator plugin (DailyTasksPlugin, BSD-2): each reward's
 * diary or quest, and the varbit the game sets once it's collected today. Members only, as the rewards are.
 */
@Singleton
class DailyReminders {

    private static final long ONE_DAY = 86_400_000L;
    // Nightmare Zone herb boxes: 9,500 points each, up to 15 a day
    private static final int HERB_BOX_MAX = 15;
    private static final int HERB_BOX_COST = 9500;
    // The Hand in the Sand's varbit once the quest is done (Bert's sand)
    private static final int SAND_QUEST_COMPLETE = 160;
    // Robin's bonemeal: 13 a day for each Morytania diary from medium up
    private static final int BONEMEAL_PER_DIARY = 13;
    private static final int ULTIMATE_IRONMAN = 2;

    private final Client client;
    private final CommonConfig config;
    private final Alerts alerts;
    private final PluginFamily family;

    // Start of the UTC day last checked; checked again at login and when a new day starts
    private long lastReset;
    private boolean loggingIn = true;

    @Inject
    DailyReminders(Client client, CommonConfig config, Alerts alerts, PluginFamily family) {
        this.client = client;
        this.config = config;
        this.alerts = alerts;
        this.family = family;
    }

    /** Checks again at the next tick in game (the plugin starting, perhaps while logged in). */
    void start() {
        loggingIn = true;
        lastReset = 0;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGING_IN) {
            loggingIn = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        long now = System.currentTimeMillis();
        boolean dailyReset = !loggingIn && now - lastReset > ONE_DAY;
        // Past the welcome screen, so the chat line is seen; members only
        if (!(dailyReset || loggingIn) || !alerts.ready() || client.getVarcIntValue(VarClientID.PLAYERMEMBER) != 1) {
            return;
        }
        lastReset = now / ONE_DAY * ONE_DAY;
        loggingIn = false;
        // With several of ours installed, only one says it
        if (!config.dailyReminders() || !family.handlesShared()) {
            return;
        }
        List<String> due = due(dailyReset);
        if (!due.isEmpty()) {
            alerts.speak(VoiceAlertService.VoiceLine.DAILIES);
            alerts.chat(AlertType.DAILIES, "Dailies to collect: " + String.join(", ", due));
        }
    }

    // What's waiting; at the daily reset everything unlocked is (its varbit may not have reset yet this tick)
    private List<String> due(boolean reset) {
        List<String> due = new ArrayList<>();
        if (config.dailyStaves() && done(VarbitID.VARROCK_DIARY_EASY_COMPLETE) && unclaimed(VarbitID.ZAFF_LAST_CLAIMED, reset)) {
            due.add("battlestaves (Zaff)");
        }
        if (config.dailyHerbBoxes() && client.getVarbitValue(VarbitID.IRONMAN) == 0
            && client.getVarpValue(VarPlayerID.NZONE_REWARDPOINTS) >= HERB_BOX_COST
            && (client.getVarbitValue(VarbitID.NZONE_HERBBOXES_PURCHASED) < HERB_BOX_MAX || reset)) {
            due.add("herb boxes (Nightmare Zone)");
        }
        if (config.dailyEssence() && done(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) && unclaimed(VarbitID.ARDOUGNE_FREE_ESSENCE, reset)) {
            due.add("pure essence (Wizard Cromperty)");
        }
        if (config.dailyRunes() && done(VarbitID.WILDERNESS_DIARY_EASY_COMPLETE) && unclaimed(VarbitID.LUNDAIL_LAST_CLAIMED, reset)) {
            due.add("random runes (Lundail)");
        }
        if (config.dailySand() && client.getVarbitValue(VarbitID.IRONMAN) != ULTIMATE_IRONMAN
            && client.getVarbitValue(VarbitID.HANDSAND_QUEST) >= SAND_QUEST_COMPLETE
            && unclaimed(VarbitID.YANILLE_SAND_CLAIMED, reset)) {
            due.add("sand (Bert)");
        }
        if (config.dailyFlax() && done(VarbitID.KANDARIN_DIARY_EASY_COMPLETE) && unclaimed(VarbitID.SEERS_FREE_FLAX, reset)) {
            due.add("bowstrings (Flax keeper)");
        }
        if (config.dailyBonemeal() && done(VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE)) {
            int max = BONEMEAL_PER_DIARY;
            if (done(VarbitID.MORYTANIA_DIARY_HARD_COMPLETE)) {
                max += BONEMEAL_PER_DIARY;
                if (done(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE)) {
                    max += BONEMEAL_PER_DIARY;
                }
            }
            if (client.getVarbitValue(VarbitID.MORYTANIA_SLIME_CLAIMED) < max || reset) {
                due.add("bonemeal and slime (Robin)");
            }
        }
        if (config.dailyDynamite() && done(VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE) && unclaimed(VarbitID.KOUREND_FREE_DYNAMITE, reset)) {
            due.add("dynamite (Thirus)");
        }
        if (config.dailyArrows() && done(VarbitID.WESTERN_DIARY_EASY_COMPLETE) && unclaimed(VarbitID.WESTERN_RANTZ_ARROWS, reset)) {
            due.add("ogre arrows (Rantz)");
        }
        return due;
    }

    private boolean done(int diaryVarbit) {
        return client.getVarbitValue(diaryVarbit) == 1;
    }

    private boolean unclaimed(int claimedVarbit, boolean reset) {
        return client.getVarbitValue(claimedVarbit) == 0 || reset;
    }
}
