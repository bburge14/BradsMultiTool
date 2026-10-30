package com.snuffys.pvm;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Snuffy's PvM/Slayer on its own (see docs/DEVELOPMENT.md "Planned: separate plugins"). */
@PluginDescriptor(
    name = "Snuffy's PvM/Slayer",
    description = "PvM HUD and monster hover card, buff timers and voice alerts, health and prayer warnings, Slayer task card and outlines",
    tags = {"snuffy", "pvm", "slayer", "buffs", "potions", "voice", "combat"}
)
public class PvmPlugin extends Plugin {

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private CommonFeature common;

    @Inject
    private PvmFeature feature;

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
    PvmConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PvmConfig.class);
    }

    @Provides
    CommonConfig provideCommonConfig(PvmConfig config) {
        return config;
    }

    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.PVM;
    }
}
