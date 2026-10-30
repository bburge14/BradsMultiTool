package com.bradsmultitool;

/**
 * Saves the default of every setting BuffConfig declares, where nothing is saved yet (see ConfigDefaults).
 * Generated from BuffConfig.java by scripts/generate_config_defaults.py: regenerate rather than editing.
 */
final class BuffConfigDefaults {

    private BuffConfigDefaults() {
    }

    static void store(ConfigDefaults defaults, BuffConfig config) {
        defaults.store("enableBuffStatus", config.enableBuffStatus());
        defaults.store("flashExpiredBuff", config.flashExpiredBuff());
        defaults.store("buffWarningSeconds", config.buffWarningSeconds());
        defaults.store("repeatBuffAlert", config.repeatBuffAlert());
        defaults.store("repeatBuffInterval", config.repeatBuffInterval());
        defaults.store("maxBuffRepeats", config.maxBuffRepeats());
        defaults.store("voiceBuffLow", config.voiceBuffLow());
        defaults.store("chatBuffLow", config.chatBuffLow());
        defaults.store("voiceBuffExpired", config.voiceBuffExpired());
        defaults.store("chatBuffExpired", config.chatBuffExpired());
    }
}
