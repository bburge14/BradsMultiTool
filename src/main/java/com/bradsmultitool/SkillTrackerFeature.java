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
    @Inject private SkillingAlerts skillingAlerts;
    @Inject private ResourceTimers resourceTimers;
    @Inject private BirdhouseTracker birdhouseTracker;
    @Inject private OtherTimerPlugins otherTimerPlugins;
    @Inject private BradsMultiToolSkillingOverlay skillingOverlay;
    @Inject private BradsMultiToolResourceTimerOverlay resourceTimerOverlay;
    @Inject private BradsMultiToolFishingMinimapOverlay fishingMinimapOverlay;
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

    void start(Lifecycle lifecycle) {
        configDefaults.add(() -> SkillTrackerConfigDefaults.store(configDefaults, config));
        // Kill counts use monster hitpoints; Slayer goals suggest a Slayer master
        lifecycle.load(monsterData, monsterData::load);
        lifecycle.load(skillingData, skillingData::load);
        lifecycle.load(farmingData, farmingData::load);
        lifecycle.load(skillHelperData, skillHelperData::load);
        lifecycle.load(slayerData, slayerData::load);
        lifecycle.load(trainingData, trainingData::load);
        lifecycle.load(recipeData, recipeData::load);
        lifecycle.load(moneyData, moneyData::load);
        lifecycle.subscribe(skillTracker, combatStyle, questProgress, killTracker, goalItems, goalTargetMenu, skillingAlerts,
            resourceTimers, farmingData, birdhouseTracker, otherTimerPlugins, skillingOverlay, trainingOverlay);
        otherTimerPlugins.refresh();
        lifecycle.overlay(skillingOverlay, resourceTimerOverlay, fishingMinimapOverlay, trainingOverlay, goalItemOverlay, pinnedSkillOverlay);
        skillTracker.startUp();
        lifecycle.onStop(skillTracker::shutDown);
    }
}
