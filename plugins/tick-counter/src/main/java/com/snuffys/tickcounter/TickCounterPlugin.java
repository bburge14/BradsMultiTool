package com.snuffys.tickcounter;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Snuffy's Tick Counter on its own (see docs/DEVELOPMENT.md "Planned: separate plugins"). */
@PluginDescriptor(
    name = "Snuffy's Tick Counter",
    description = "Game tick counter as boxes or a smooth bar, a metronome, an attack timer and click timing for tick skilling",
    tags = {"snuffy", "tick", "metronome", "counter", "attack timer", "timing"}
)
public class TickCounterPlugin extends Plugin {

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private TickCounterFeature feature;

    @Override
    protected void startUp() throws Exception {
        lifecycle.plugin = this;
        feature.start(lifecycle);
    }

    @Override
    protected void shutDown() throws Exception {
        lifecycle.stopAll();
    }

    @Provides
    TickConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TickConfig.class);
    }

    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.TICK_COUNTER;
    }
}
