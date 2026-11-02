package com.snuffys.planner;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Snuffy's Planner on its own (see docs/DEVELOPMENT.md "Planned: separate plugins"). */
@PluginDescriptor(
    name = "Snuffy's Planner",
    description = "A sidebar tab for dailies and weeklies, farming, bird house and your own timers, your Slayer task, a drop tracker, and a to-do list with notes",
    tags = {"snuffy", "planner", "dailies", "weekly", "farming", "timers", "birdhouse", "slayer", "loot", "drops", "notes", "todo"}
)
public class PlannerPlugin extends Plugin {

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private CommonFeature common;

    @Inject
    private PlannerFeature feature;

    @Override
    protected void startUp() throws Exception {
        lifecycle.plugin = this;
        common.start(lifecycle);
        feature.start(lifecycle);
    }

    @Override
    protected void shutDown() throws Exception {
        lifecycle.stopAll();
    }

    @Provides
    PlannerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PlannerConfig.class);
    }

    @Provides
    CommonConfig provideCommonConfig(PlannerConfig config) {
        return config;
    }

    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.PLANNER;
    }
}
