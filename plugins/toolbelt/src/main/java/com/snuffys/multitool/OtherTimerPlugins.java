package com.snuffys.multitool;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Which of our timer pies another enabled plugin already draws, so ours can step aside instead of stacking
 * a second circle on the same tree, rock or fishing spot. Only reads which plugins are on; never changes
 * them or their settings.
 */
@Singleton
class OtherTimerPlugins {

    // Plugins (by class name, as listed on the Plugin Hub / in RuneLite) and the timers they draw
    private static final Map<String, Set<ResourceTimers.Kind>> PLUGINS = Map.ofEntries(
        Map.entry("TreeDespawnTimerPlugin", EnumSet.of(ResourceTimers.Kind.TREE)),
        Map.entry("MLMDespawnTimerPlugin", EnumSet.of(ResourceTimers.Kind.VEIN)),
        Map.entry("CalcifiedRockDespawnTimerPlugin", EnumSet.of(ResourceTimers.Kind.CALCIFIED)),
        Map.entry("DaeyaltMiningTimerPlugin", EnumSet.of(ResourceTimers.Kind.DAEYALT)),
        Map.entry("FishingSpotTimerPlugin", EnumSet.of(ResourceTimers.Kind.FISHING_SPOT)),
        Map.entry("FishingSpotTrackerPlugin", EnumSet.of(ResourceTimers.Kind.FISHING_SPOT)),
        Map.entry("BarbFishingPlugin", EnumSet.of(ResourceTimers.Kind.FISHING_SPOT)),
        Map.entry("AerialFishingTimersPlugin", EnumSet.of(ResourceTimers.Kind.FISHING_SPOT)),
        Map.entry("StarAfkTimerPlugin", EnumSet.of(ResourceTimers.Kind.STAR)),
        Map.entry("SalvagingAfkTimerPlugin", EnumSet.of(ResourceTimers.Kind.SHIPWRECK)),
        Map.entry("FireTimerPlugin", EnumSet.of(ResourceTimers.Kind.FIRE)),
        // RuneLite's own Hunter plugin draws trap timers
        Map.entry("net.runelite.client.plugins.hunter.HunterPlugin", EnumSet.of(ResourceTimers.Kind.TRAP)));

    private final PluginManager pluginManager;
    private final Set<ResourceTimers.Kind> covered = EnumSet.noneOf(ResourceTimers.Kind.class);

    @Inject
    OtherTimerPlugins(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    /** True when another enabled plugin already draws this kind of timer. */
    boolean covers(ResourceTimers.Kind kind) {
        return covered.contains(kind);
    }

    void refresh() {
        covered.clear();
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (!pluginManager.isPluginEnabled(plugin)) {
                continue;
            }
            Set<ResourceTimers.Kind> kinds = PLUGINS.get(plugin.getClass().getSimpleName());
            if (kinds == null) {
                kinds = PLUGINS.get(plugin.getClass().getName());
            }
            if (kinds != null) {
                covered.addAll(kinds);
            }
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        refresh();
    }
}
