package com.bradsmultitool;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
    name = "Snuffy's Multi-Tool",
    description = "Skill tracker with goals and training suggestions, skilling hover cards and timers, PvM HUD with buff timers and voice alerts, Slayer helper, Gemstone Crab tracker",
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

    @Override
    protected void startUp() throws Exception {
        common.start(lifecycle);
        skillTracker.start(lifecycle);
        pvm.start(lifecycle);
        gemCrab.start(lifecycle);
        // Together: the tracker's Reset all also clears the crab's and PvM's session numbers, and the
        // crab's card shows PvM's buff timers and LOW prayer/health
        skillTracker.tracker().addResetListener(gemCrab.tracker()::resetSession);
        skillTracker.tracker().addResetListener(pvm.tracker()::resetSession);
        gemCrab.hud().setExtraRows(pvm.hud()::addStatusRows);
        lifecycle.onStop(() -> gemCrab.hud().setExtraRows(null));
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
