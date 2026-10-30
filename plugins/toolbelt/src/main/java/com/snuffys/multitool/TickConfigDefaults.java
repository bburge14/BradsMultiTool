package com.snuffys.multitool;

/**
 * Saves the default of every setting TickConfig declares, where nothing is saved yet (see ConfigDefaults).
 * Generated from TickConfig.java by scripts/generate_config_defaults.py: regenerate rather than editing.
 */
final class TickConfigDefaults {

    private TickConfigDefaults() {
    }

    static void store(ConfigDefaults defaults, TickConfig config) {
        defaults.store("tickOverHead", config.tickOverHead());
        defaults.store("tickBoxes", config.tickBoxes());
        defaults.store("tickBoxLayout", config.tickBoxLayout());
        defaults.store("tickBar", config.tickBar());
        defaults.store("tickCycle", config.tickCycle());
        defaults.store("tickFollowWeapon", config.tickFollowWeapon());
        defaults.store("tickColor", config.tickColor());
        defaults.store("tickFirstColor", config.tickFirstColor());
        defaults.store("tickTextSize", config.tickTextSize());
        defaults.store("tickBoxSize", config.tickBoxSize());
        defaults.store("tickBarWidth", config.tickBarWidth());
        defaults.store("metronomeSound", config.metronomeSound());
        defaults.store("metronomeFirstOnly", config.metronomeFirstOnly());
        defaults.store("metronomeVolume", config.metronomeVolume());
        defaults.store("clickTiming", config.clickTiming());
        defaults.store("clickSounds", config.clickSounds());
        defaults.store("clickLateSound", config.clickLateSound());
        defaults.store("clickLateMs", config.clickLateMs());
        defaults.store("clickEveryTick", config.clickEveryTick());
    }
}
