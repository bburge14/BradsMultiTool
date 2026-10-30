package com.snuffys.pvm;

/**
 * Saves the default of every setting PvmConfig declares, where nothing is saved yet (see ConfigDefaults).
 * Generated from PvmConfig.java by scripts/generate_config_defaults.py: regenerate rather than editing.
 */
final class PvmConfigDefaults {

    private PvmConfigDefaults() {
    }

    static void store(ConfigDefaults defaults, PvmConfig config) {
        defaults.store("enableLowHealthAlert", config.enableLowHealthAlert());
        defaults.store("lowHealthPercent", config.lowHealthPercent());
        defaults.store("flashHealthOrb", config.flashHealthOrb());
        defaults.store("repeatHealthAlert", config.repeatHealthAlert());
        defaults.store("repeatHealthInterval", config.repeatHealthInterval());
        defaults.store("maxHealthRepeats", config.maxHealthRepeats());
        defaults.store("voiceHealthLow", config.voiceHealthLow());
        defaults.store("chatHealthLow", config.chatHealthLow());
        defaults.store("enableLowPrayerAlert", config.enableLowPrayerAlert());
        defaults.store("lowPrayerPercent", config.lowPrayerPercent());
        defaults.store("flashPrayerOrb", config.flashPrayerOrb());
        defaults.store("flashOutOfPrayerOverhead", config.flashOutOfPrayerOverhead());
        defaults.store("outOfPrayerTextSize", config.outOfPrayerTextSize());
        defaults.store("repeatPrayerAlert", config.repeatPrayerAlert());
        defaults.store("repeatPrayerInterval", config.repeatPrayerInterval());
        defaults.store("maxPrayerRepeats", config.maxPrayerRepeats());
        defaults.store("voicePrayerLow", config.voicePrayerLow());
        defaults.store("chatPrayerLow", config.chatPrayerLow());
        defaults.store("specReadyAlert", config.specReadyAlert());
        defaults.store("specThreshold", config.specThreshold());
        defaults.store("antifireAlert", config.antifireAlert());
        defaults.store("poisonAlert", config.poisonAlert());
        defaults.store("staminaAlert", config.staminaAlert());
        defaults.store("ammoAlert", config.ammoAlert());
        defaults.store("ammoThreshold", config.ammoThreshold());
        defaults.store("runesAlert", config.runesAlert());
        defaults.store("cannonAlert", config.cannonAlert());
        defaults.store("cannonThreshold", config.cannonThreshold());
        defaults.store("voiceSpecReady", config.voiceSpecReady());
        defaults.store("chatSpecReady", config.chatSpecReady());
        defaults.store("voiceAntifire", config.voiceAntifire());
        defaults.store("chatAntifire", config.chatAntifire());
        defaults.store("voicePoison", config.voicePoison());
        defaults.store("chatPoison", config.chatPoison());
        defaults.store("voiceStamina", config.voiceStamina());
        defaults.store("chatStamina", config.chatStamina());
        defaults.store("voiceAmmo", config.voiceAmmo());
        defaults.store("chatAmmo", config.chatAmmo());
        defaults.store("voiceRunes", config.voiceRunes());
        defaults.store("chatRunes", config.chatRunes());
        defaults.store("voiceCannon", config.voiceCannon());
        defaults.store("chatCannon", config.chatCannon());
        defaults.store("enablePvmOverlay", config.enablePvmOverlay());
        defaults.store("showHoverInfo", config.showHoverInfo());
        defaults.store("dpsMode", config.dpsMode());
        defaults.store("pvmLingerSeconds", config.pvmLingerSeconds());
        defaults.store("showPvmWeakness", config.showPvmWeakness());
        defaults.store("showPvmDps", config.showPvmDps());
        defaults.store("showPvmLastHit", config.showPvmLastHit());
        defaults.store("showPvmMaxHit", config.showPvmMaxHit());
        defaults.store("showPvmAggro", config.showPvmAggro());
        defaults.store("showSlayerTask", config.showSlayerTask());
        defaults.store("slayerToggleKey", config.slayerToggleKey());
        defaults.store("showSlayerDirections", config.showSlayerDirections());
        defaults.store("showSlayerMasterCard", config.showSlayerMasterCard());
        defaults.store("slayerPictures", config.slayerPictures());
        defaults.store("slayerKillRate", config.slayerKillRate());
        defaults.store("slayerPrayer", config.slayerPrayer());
        defaults.store("slayerRequiredItem", config.slayerRequiredItem());
        defaults.store("slayerTravelRoutes", config.slayerTravelRoutes());
        defaults.store("slayerNextMaster", config.slayerNextMaster());
        defaults.store("slayerPlacesShown", config.slayerPlacesShown());
        defaults.store("slayerRoutesShown", config.slayerRoutesShown());
        defaults.store("showSlayerMonsterInfo", config.showSlayerMonsterInfo());
        defaults.store("highlightTaskMonsters", config.highlightTaskMonsters());
        defaults.store("taskMonsterColor", config.taskMonsterColor());
        defaults.store("slayerItemAlert", config.slayerItemAlert());
        defaults.store("superiorAlert", config.superiorAlert());
        defaults.store("taskCompleteAlert", config.taskCompleteAlert());
        defaults.store("voiceSlayerItem", config.voiceSlayerItem());
        defaults.store("chatSlayerItem", config.chatSlayerItem());
        defaults.store("voiceSuperior", config.voiceSuperior());
        defaults.store("chatSuperior", config.chatSuperior());
        defaults.store("voiceTaskComplete", config.voiceTaskComplete());
        defaults.store("chatTaskComplete", config.chatTaskComplete());
        defaults.store("taskHalfwayAlert", config.taskHalfwayAlert());
        defaults.store("taskNearlyDoneAt", config.taskNearlyDoneAt());
        defaults.store("voiceTaskHalfway", config.voiceTaskHalfway());
        defaults.store("chatTaskHalfway", config.chatTaskHalfway());
        defaults.store("voiceTaskNearlyDone", config.voiceTaskNearlyDone());
        defaults.store("chatTaskNearlyDone", config.chatTaskNearlyDone());
        defaults.store("showBuffBar", config.showBuffBar());
        defaults.store("buffBarDebuffs", config.buffBarDebuffs());
        defaults.store("buffBarLayout", config.buffBarLayout());
        defaults.store("buffBarIconSize", config.buffBarIconSize());
        defaults.store("showMonsterEffects", config.showMonsterEffects());
    }
}
