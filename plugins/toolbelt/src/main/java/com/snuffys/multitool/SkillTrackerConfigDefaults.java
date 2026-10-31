package com.snuffys.multitool;

/**
 * Saves the default of every setting SkillTrackerConfig declares, where nothing is saved yet (see ConfigDefaults).
 * Generated from SkillTrackerConfig.java by scripts/generate_config_defaults.py: regenerate rather than editing.
 */
final class SkillTrackerConfigDefaults {

    private SkillTrackerConfigDefaults() {
    }

    static void store(ConfigDefaults defaults, SkillTrackerConfig config) {
        defaults.store("showSkillingHover", config.showSkillingHover());
        defaults.store("showSkillingRates", config.showSkillingRates());
        defaults.store("deferToOtherTimerPlugins", config.deferToOtherTimerPlugins());
        defaults.store("piesAlwaysShown", config.piesAlwaysShown());
        defaults.store("pieStyle", config.pieStyle());
        defaults.store("zoneColor", config.zoneColor());
        defaults.store("bestChoice", config.bestChoice());
        defaults.store("skillingWhenNotRetaliating", config.skillingWhenNotRetaliating());
        defaults.store("inventoryFullAlert", config.inventoryFullAlert());
        defaults.store("voiceInventoryFull", config.voiceInventoryFull());
        defaults.store("chatInventoryFull", config.chatInventoryFull());
        defaults.store("resourceGoneAlert", config.resourceGoneAlert());
        defaults.store("batchCompleteAlert", config.batchCompleteAlert());
        defaults.store("voiceBatchComplete", config.voiceBatchComplete());
        defaults.store("chatBatchComplete", config.chatBatchComplete());
        defaults.store("showTreeTimer", config.showTreeTimer());
        defaults.store("voiceTreeGone", config.voiceTreeGone());
        defaults.store("chatTreeGone", config.chatTreeGone());
        defaults.store("showMiningTimer", config.showMiningTimer());
        defaults.store("voiceRockDepleted", config.voiceRockDepleted());
        defaults.store("chatRockDepleted", config.chatRockDepleted());
        defaults.store("showFishingSpotTimer", config.showFishingSpotTimer());
        defaults.store("voiceFishingSpot", config.voiceFishingSpot());
        defaults.store("chatFishingSpot", config.chatFishingSpot());
        defaults.store("coinPouchAlert", config.coinPouchAlert());
        defaults.store("voiceCoinPouch", config.voiceCoinPouch());
        defaults.store("chatCoinPouch", config.chatCoinPouch());
        defaults.store("showFarmingHover", config.showFarmingHover());
        defaults.store("showHunterHover", config.showHunterHover());
        defaults.store("showTrapTimer", config.showTrapTimer());
        defaults.store("showBirdhouseHover", config.showBirdhouseHover());
        defaults.store("birdhouseAlert", config.birdhouseAlert());
        defaults.store("voiceBirdhouses", config.voiceBirdhouses());
        defaults.store("chatBirdhouses", config.chatBirdhouses());
        defaults.store("showFireTimer", config.showFireTimer());
        defaults.store("showCampfireTimer", config.showCampfireTimer());
        defaults.store("showRunecraftHover", config.showRunecraftHover());
        defaults.store("showAgilityHover", config.showAgilityHover());
        defaults.store("showCookingHover", config.showCookingHover());
        defaults.store("showShipwreckTimer", config.showShipwreckTimer());
        defaults.store("showSailingHover", config.showSailingHover());
        defaults.store("showSmithingHover", config.showSmithingHover());
        defaults.store("showTrackerProgressBar", config.showTrackerProgressBar());
        defaults.store("showTrackerXpGained", config.showTrackerXpGained());
        defaults.store("showTrackerXpToLevel", config.showTrackerXpToLevel());
        defaults.store("showTrackerActions", config.showTrackerActions());
        defaults.store("trackerActionsToGoal", config.trackerActionsToGoal());
        defaults.store("showTrackerXpPerHour", config.showTrackerXpPerHour());
        defaults.store("showXpPerMinute", config.showXpPerMinute());
        defaults.store("showTrackerTimeToLevel", config.showTrackerTimeToLevel());
        defaults.store("showTrackerGoal", config.showTrackerGoal());
        defaults.store("showTrainingSuggestion", config.showTrainingSuggestion());
        defaults.store("trainingFocus", config.trainingFocus());
        defaults.store("highlightSuggestedTraining", config.highlightSuggestedTraining());
        defaults.store("highlightGoalItems", config.highlightGoalItems());
        defaults.store("trainingHighlightColor", config.trainingHighlightColor());
        defaults.store("autoPauseSkills", config.autoPauseSkills());
        defaults.store("autoPauseSeconds", config.autoPauseSeconds());
        defaults.store("ignoredSkills", config.ignoredSkills());
        defaults.store("voiceLevelUp", config.voiceLevelUp());
        defaults.store("chatLevelUp", config.chatLevelUp());
    }
}
