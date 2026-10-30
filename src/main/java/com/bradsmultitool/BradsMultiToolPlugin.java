package com.bradsmultitool;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
    name = "Snuffy's Toolbelt",
    description = "Skill tracker with goals and training suggestions, skilling hover cards and timers, PvM HUD with buff timers and voice alerts, Slayer helper, Gemstone Crab tracker, tick counter and metronome",
    tags = {"snuffy", "xp", "skills", "gemstone", "crab", "shell", "tunnel", "logout", "voice"}
)
public class BradsMultiToolPlugin extends Plugin {

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private CommonFeature common;

    @Inject
    private SkillTrackerFeature skillTracker;

    @Inject
    private PvmFeature pvm;

    @Inject
    private GemCrabFeature gemCrab;

    @Inject
    private TickCounterFeature tickCounter;

    @Override
    protected void startUp() throws Exception {
        common.start(lifecycle);
        skillTracker.start(lifecycle);
        pvm.start(lifecycle);
        gemCrab.start(lifecycle);
        tickCounter.start(lifecycle);
        // Together: PvM's monster card steps aside for the skilling and Sailing cards (separate plugins do the
        // same through PluginFamily)
        pvm.hoverCard().setSkillingCards(skillTracker.skillingCards()::coversNpc);
        lifecycle.onStop(() -> {
            pvm.hoverCard().setSkillingCards(null);
        });
    }

    @Override
    protected void shutDown() throws Exception {
        lifecycle.stopAll();
    }

    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.MULTI_TOOL;
    }

    // The all-in-one's settings stand in for each plugin's own (same keys, one group)
    @Provides
    TickConfig provideTickConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    BuffConfig provideBuffConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    CommonConfig provideCommonConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    SkillTrackerConfig provideSkillTrackerConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    PvmConfig providePvmConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    GemCrabConfig provideGemCrabConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    BradsMultiToolConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BradsMultiToolConfig.class);
    }
}
