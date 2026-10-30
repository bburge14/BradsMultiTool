package com.bradsmultitool;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Snuffy's Skilling Helper on its own (see docs/DEVELOPMENT.md "Planned: separate plugins"). */
@PluginDescriptor(
    name = "Snuffy's Skilling Helper",
    description = "XP tracker with goals and suggested training, a pinned skill overlay, skilling hover cards, timer pies and skilling alerts",
    tags = {"snuffy", "xp", "skills", "tracker", "goals", "skilling", "timers"}
)
public class SkillTrackerPlugin extends Plugin {

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private CommonFeature common;

    @Inject
    private SkillTrackerFeature feature;

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
    SkillTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SkillTrackerConfig.class);
    }

    @Provides
    CommonConfig provideCommonConfig(SkillTrackerConfig config) {
        return config;
    }

    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.SKILL_TRACKER;
    }
}
