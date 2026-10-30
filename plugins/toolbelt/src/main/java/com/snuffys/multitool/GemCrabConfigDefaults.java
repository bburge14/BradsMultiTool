package com.snuffys.multitool;

/**
 * Saves the default of every setting GemCrabConfig declares, where nothing is saved yet (see ConfigDefaults).
 * Generated from GemCrabConfig.java by scripts/generate_config_defaults.py: regenerate rather than editing.
 */
final class GemCrabConfigDefaults {

    private GemCrabConfigDefaults() {
    }

    static void store(ConfigDefaults defaults, GemCrabConfig config) {
        defaults.store("enableCrabFeatures", config.enableCrabFeatures());
        defaults.store("crabLowHealthPercent", config.crabLowHealthPercent());
        defaults.store("showCrabLastHit", config.showCrabLastHit());
        defaults.store("showCrabMaxHit", config.showCrabMaxHit());
        defaults.store("activeCrabColor", config.activeCrabColor());
        defaults.store("mineableShellColor", config.mineableShellColor());
        defaults.store("depletedShellColor", config.depletedShellColor());
        defaults.store("tunnelColor", config.tunnelColor());
        defaults.store("voiceCrabMoved", config.voiceCrabMoved());
        defaults.store("chatCrabMoved", config.chatCrabMoved());
        defaults.store("voiceCrabLowHealth", config.voiceCrabLowHealth());
        defaults.store("chatCrabLowHealth", config.chatCrabLowHealth());
    }
}
