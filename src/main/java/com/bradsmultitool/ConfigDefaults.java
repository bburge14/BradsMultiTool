package com.bradsmultitool;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;

/**
 * Saves the plugin's setting defaults that nothing has saved yet. RuneLite only saves the defaults of settings
 * the plugin's own settings interface declares, and ours inherit theirs (CommonConfig, TickConfig, all of them in
 * the Toolbelt); the settings panel can't open with a choice setting that has nothing saved. Each feature adds
 * its generated *Defaults class here (scripts/generate_config_defaults.py), and they run again when the player
 * switches RuneLite profile.
 */
@Singleton
class ConfigDefaults {

    private final ConfigManager configManager;
    private final PluginIdentity identity;
    private final List<Runnable> stores = new ArrayList<>();

    @Inject
    ConfigDefaults(ConfigManager configManager, PluginIdentity identity) {
        this.configManager = configManager;
        this.identity = identity;
    }

    /** Saves these defaults now and after each profile switch. */
    void add(Runnable store) {
        stores.add(store);
        store.run();
    }

    void clear() {
        stores.clear();
    }

    /** Saves a setting's value (its default, while nothing is saved) if nothing is saved for it. */
    void store(String key, Object value) {
        if (value != null && configManager.getConfiguration(identity.configGroup, key) == null) {
            configManager.setConfiguration(identity.configGroup, key, value);
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event) {
        for (Runnable store : stores) {
            store.run();
        }
    }
}
