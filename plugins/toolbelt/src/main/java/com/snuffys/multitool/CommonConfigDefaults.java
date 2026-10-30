package com.snuffys.multitool;

/**
 * Saves the default of every setting CommonConfig declares, where nothing is saved yet (see ConfigDefaults).
 * Generated from CommonConfig.java by scripts/generate_config_defaults.py: regenerate rather than editing.
 */
final class CommonConfigDefaults {

    private CommonConfigDefaults() {
    }

    static void store(ConfigDefaults defaults, CommonConfig config) {
        defaults.store("voicePersona", config.voicePersona());
        defaults.store("voiceVolume", config.voiceVolume());
        defaults.store("voiceEnabled", config.voiceEnabled());
        defaults.store("syncVoice", config.syncVoice());
        defaults.store("sendChatMessages", config.sendChatMessages());
        defaults.store("characterIdleAlert", config.characterIdleAlert());
        defaults.store("characterIdleSeconds", config.characterIdleSeconds());
        defaults.store("idleWarningSeconds", config.idleWarningSeconds());
        defaults.store("repeatIdleAlert", config.repeatIdleAlert());
        defaults.store("repeatIdleInterval", config.repeatIdleInterval());
        defaults.store("maxIdleRepeats", config.maxIdleRepeats());
        defaults.store("voiceCharacterIdle", config.voiceCharacterIdle());
        defaults.store("chatCharacterIdle", config.chatCharacterIdle());
        defaults.store("voiceIdleLogout", config.voiceIdleLogout());
        defaults.store("chatIdleLogout", config.chatIdleLogout());
    }
}
