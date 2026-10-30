package com.snuffys.voices;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Snuffy's Alerts on its own (see docs/DEVELOPMENT.md "Planned: separate plugins"). */
@PluginDescriptor(
    name = "Snuffy's Alerts",
    description = "One voice and volume for all of Snuffy's plugins, plus the idle and logout warnings",
    tags = {"snuffy", "voice", "alerts", "idle", "logout"}
)
public class VoicesPlugin extends Plugin {

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private CommonFeature common;

    @Inject
    private VoicesFeature feature;

    @Override
    protected void startUp() throws Exception {
        common.start(lifecycle);
        feature.start(lifecycle);
    }

    @Override
    protected void shutDown() throws Exception {
        lifecycle.stopAll();
    }

    @Provides
    VoicesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VoicesConfig.class);
    }

    @Provides
    CommonConfig provideCommonConfig(VoicesConfig config) {
        return config;
    }

    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.VOICES;
    }
}
