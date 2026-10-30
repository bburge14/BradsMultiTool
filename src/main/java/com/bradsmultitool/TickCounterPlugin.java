package com.bradsmultitool;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Snuffy's Tick Counter on its own (see docs/DEVELOPMENT.md "Planned: separate plugins"). */
@PluginDescriptor(
    name = "Snuffy's Tick Counter",
    description = "Game tick counter over your head, as tick boxes or a smooth tick bar, with a metronome and click timing for tick skilling",
    tags = {"snuffy", "tick", "metronome", "counter", "1-tick", "3-tick", "timing"}
)
public class TickCounterPlugin extends Plugin {

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private TickCounterFeature feature;

    @Override
    protected void startUp() throws Exception {
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
