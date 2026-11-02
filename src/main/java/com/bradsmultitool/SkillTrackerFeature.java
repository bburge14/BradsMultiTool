package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Snuffy's Skilling Helper: the sidebar tracker with goals and suggested training, the pinned skill, goal
 * outlines and items, and the skilling helpers (hover cards, timer pies, skilling alerts).
 */
@Singleton
class SkillTrackerFeature {

    @Inject private SkillTracker skillTracker;
    @Inject private MonsterData monsterData;
    @Inject private SkillingData skillingData;
    @Inject private FarmingData farmingData;
    @Inject private SkillHelperData skillHelperData;
    @Inject private SlayerData slayerData;
    @Inject private TrainingData trainingData;
    @Inject private RecipeData recipeData;
    @Inject private MoneyData moneyData;
    @Inject private CombatStyle combatStyle;
    @Inject private QuestProgress questProgress;
    @Inject private KillTracker killTracker;
    @Inject private GoalItems goalItems;
    @Inject private GoalTargetMenu goalTargetMenu;
    @Inject private MethodDetector methodDetector;
    @Inject private AgilityTracker agilityTracker;
    @Inject private BradsMultiToolAgilityOverlay agilityOverlay;
    @Inject private ArenaRoute arenaRoute;
    @Inject private ObstacleReps obstacleReps;
    @Inject private BradsMultiToolArenaOverlay arenaOverlay;
    @Inject private BradsMultiToolArenaMinimapOverlay arenaMinimapOverlay;
    @Inject private SkillingAlerts skillingAlerts;
    @Inject private ForestryAlerts forestryAlerts;
    @Inject private MotherlodeSack motherlodeSack;
    @Inject private WintertodtHelper wintertodtHelper;
    @Inject private TemporossHelper temporossHelper;
    @Inject private RiftHelper riftHelper;
    @Inject private SailingAlerts sailingAlerts;
    @Inject private BradsMultiToolMinigameOverlay minigameOverlay;
    @Inject private ResourceTimers resourceTimers;
    @Inject private BirdhouseTracker birdhouseTracker;
    @Inject private HunterRumours hunterRumours;
    @Inject private BradsMultiToolSkillsTabOverlay skillsTabOverlay;
    @Inject private OtherTimerPlugins otherTimerPlugins;
    @Inject private BradsMultiToolSkillingOverlay skillingOverlay;
    @Inject private BradsMultiToolResourceTimerOverlay resourceTimerOverlay;
    @Inject private BradsMultiToolTrainingOverlay trainingOverlay;
    @Inject private BradsMultiToolGoalItemOverlay goalItemOverlay;
    @Inject private BradsMultiToolPinnedSkillOverlay pinnedSkillOverlay;

    BradsMultiToolSkillingOverlay skillingCards() {
        return skillingOverlay;
    }

    SkillTracker tracker() {
        return skillTracker;
    }


    @Inject private ConfigDefaults configDefaults;
    @Inject private SkillTrackerConfig config;
    @Inject private net.runelite.client.config.ConfigManager configManager;
    @Inject private PluginIdentity identity;

    // The resource timers' on/off switches (and the one Timers When Not Training switch) became an Always /
    // While training / Off choice per kind under new keys: carry your old choice over the first time
    private static final String[][] TIMER_KEYS = {
        {"showTreeTimer", "treeTimers"}, {"showMiningTimer", "miningTimers"}, {"showFishingSpotTimer", "fishingSpotTimers"},
        {"showTrapTimer", "trapTimers"}, {"showFireTimer", "fireTimers"}, {"showCampfireTimer", "campfireTimers"},
        {"showShipwreckTimer", "shipwreckTimers"}};

    private void migrateTimerSettings() {
        String group = identity.configGroup;
        boolean always = "true".equals(configManager.getConfiguration(group, "piesAlwaysShown"));
        for (String[] keys : TIMER_KEYS) {
            String old = configManager.getConfiguration(group, keys[0]);
            if (old != null && configManager.getConfiguration(group, keys[1]) == null) {
                SkillTrackerConfig.TimerShow when = "false".equals(old) ? SkillTrackerConfig.TimerShow.OFF
                    : always ? SkillTrackerConfig.TimerShow.ALWAYS : SkillTrackerConfig.TimerShow.TRAINING;
                configManager.setConfiguration(group, keys[1], when);
            }
        }
    }

    // "Chat: Pillar About to Move" started off and is now on: turn it on once for players who already had it saved
    private void turnOnPillarChat() {
        String group = identity.configGroup;
        if (configManager.getConfiguration(group, "pillarChatTurnedOn") == null) {
            configManager.setConfiguration(group, "pillarChatTurnedOn", true);
            configManager.setConfiguration(group, "chatArenaPillar", true);
        }
    }

    // Auto-Pause After went from 300 seconds to 10 by default: move players still on the old default, once
    private void shortenAutoPause() {
        String group = identity.configGroup;
        if (configManager.getConfiguration(group, "autoPauseShortened") == null) {
            configManager.setConfiguration(group, "autoPauseShortened", true);
            if ("300".equals(configManager.getConfiguration(group, "autoPauseSeconds"))) {
                configManager.setConfiguration(group, "autoPauseSeconds", 10);
            }
        }
    }

    void start(Lifecycle lifecycle) {
        configDefaults.add(() -> {
            migrateTimerSettings();
            turnOnPillarChat();
            shortenAutoPause();
            SkillTrackerConfigDefaults.store(configDefaults, config);
        });
        // Kill counts use monster hitpoints; Slayer goals suggest a Slayer master
        lifecycle.load(monsterData, monsterData::load);
        lifecycle.load(skillingData, skillingData::load);
        lifecycle.load(farmingData, farmingData::load);
        lifecycle.load(skillHelperData, skillHelperData::load);
        lifecycle.load(slayerData, slayerData::load);
        lifecycle.load(trainingData, trainingData::load);
        lifecycle.load(recipeData, recipeData::load);
        lifecycle.load(moneyData, moneyData::load);
        lifecycle.subscribe(skillTracker, combatStyle, questProgress, killTracker, goalItems, goalTargetMenu, methodDetector, agilityTracker, arenaRoute, obstacleReps, skillingAlerts, forestryAlerts, motherlodeSack, wintertodtHelper,
            temporossHelper, riftHelper, sailingAlerts,
            resourceTimers, farmingData, birdhouseTracker, hunterRumours, otherTimerPlugins, skillingOverlay, trainingOverlay);
        otherTimerPlugins.refresh();
        lifecycle.overlay(skillingOverlay, resourceTimerOverlay, agilityOverlay, arenaOverlay, arenaMinimapOverlay, trainingOverlay, goalItemOverlay, pinnedSkillOverlay, minigameOverlay, skillsTabOverlay);
        skillTracker.startUp();
        lifecycle.onStop(skillTracker::shutDown);
    }
}
