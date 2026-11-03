package com.snuffys.planner;

/**
 * Saves the default of every setting PlannerConfig declares, where nothing is saved yet (see ConfigDefaults).
 * Generated from PlannerConfig.java by scripts/generate_config_defaults.py: regenerate rather than editing.
 */
final class PlannerConfigDefaults {

    private PlannerConfigDefaults() {
    }

    static void store(ConfigDefaults defaults, PlannerConfig config) {
        defaults.store("openGuide", config.openGuide());
        defaults.store("showClashes", config.showClashes());
        defaults.store("plannerShowToday", config.plannerShowToday());
        defaults.store("plannerDailiesAlert", config.plannerDailiesAlert());
        defaults.store("voiceDailies", config.voiceDailies());
        defaults.store("chatDailies", config.chatDailies());
        defaults.store("plannerShowWeekly", config.plannerShowWeekly());
        defaults.store("plannerWeekliesAlert", config.plannerWeekliesAlert());
        defaults.store("voiceWeeklies", config.voiceWeeklies());
        defaults.store("chatWeeklies", config.chatWeeklies());
        defaults.store("plannerShowTimers", config.plannerShowTimers());
        defaults.store("farmingPatchList", config.farmingPatchList());
        defaults.store("farmingReadyAlert", config.farmingReadyAlert());
        defaults.store("voiceFarmingReady", config.voiceFarmingReady());
        defaults.store("chatFarmingReady", config.chatFarmingReady());
        defaults.store("birdhouseAlert", config.birdhouseAlert());
        defaults.store("voiceBirdhouses", config.voiceBirdhouses());
        defaults.store("chatBirdhouses", config.chatBirdhouses());
        defaults.store("timerSound", config.timerSound());
        defaults.store("timerSoundRepeats", config.timerSoundRepeats());
        defaults.store("voiceTimerDone", config.voiceTimerDone());
        defaults.store("chatTimerDone", config.chatTimerDone());
        defaults.store("plannerGeLimits", config.plannerGeLimits());
        defaults.store("chatGeLimitReset", config.chatGeLimitReset());
        defaults.store("plannerShowSlayer", config.plannerShowSlayer());
        defaults.store("plannerShowDrops", config.plannerShowDrops());
        defaults.store("plannerOtherLoot", config.plannerOtherLoot());
        defaults.store("plannerValuableDrop", config.plannerValuableDrop());
        defaults.store("plannerValuableColor", config.plannerValuableColor());
        defaults.store("plannerRecentDrops", config.plannerRecentDrops());
        defaults.store("plannerShowNotes", config.plannerShowNotes());
        defaults.store("plannerShowCalculator", config.plannerShowCalculator());
        defaults.store("plannerCalcPrices", config.plannerCalcPrices());
        defaults.store("plannerCalcHideMembers", config.plannerCalcHideMembers());
        defaults.store("plannerPlanOverlay", config.plannerPlanOverlay());
        defaults.store("plannerIronman", config.plannerIronman());
        defaults.store("plannerYourPrices", config.plannerYourPrices());
        defaults.store("chatPlanMilestone", config.chatPlanMilestone());
        defaults.store("compactOverlays", config.compactOverlays());
    }
}
