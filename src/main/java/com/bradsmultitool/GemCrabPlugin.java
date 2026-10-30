package com.bradsmultitool;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Snuffy's GemCrab Tracker on its own (see docs/DEVELOPMENT.md "Planned: separate plugins"). */
@PluginDescriptor(
    name = "Snuffy's GemCrab Tracker",
    description = "Gemstone Crab HUD, burrow countdown, radar between coves, outlines and alerts",
    tags = {"snuffy", "gemstone", "crab", "shell", "tunnel"}
)
public class GemCrabPlugin extends Plugin {

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private CommonFeature common;

    @Inject
    private GemCrabFeature feature;

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
    GemCrabConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GemCrabConfig.class);
    }

    @Provides
    CommonConfig provideCommonConfig(GemCrabConfig config) {
        return config;
    }

    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.GEM_CRAB;
    }
}
