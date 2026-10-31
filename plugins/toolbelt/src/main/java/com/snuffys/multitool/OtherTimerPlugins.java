package com.snuffys.multitool;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Which of our timer pies another enabled plugin already draws, so ours can step aside instead of stacking
 * a second circle on the same tree, rock or fishing spot. Only reads which plugins are on; never changes
 * them or their settings. Also which already draw a resource's icon or tile outline (RuneLite's Fishing plugin on fishing
 * spots, its Motherlode Mine plugin on veins), so the tile style leaves those out.
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

    private static final String FISHING = "net.runelite.client.plugins.fishing.FishingPlugin";
    private static final String MOTHERLODE = "net.runelite.client.plugins.motherlode.MotherlodePlugin";

    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final Set<ResourceTimers.Kind> covered = EnumSet.noneOf(ResourceTimers.Kind.class);
    private boolean fishingOn;
    private boolean motherlodeOn;

    @Inject
    OtherTimerPlugins(PluginManager pluginManager, ConfigManager configManager) {
        this.pluginManager = pluginManager;
        this.configManager = configManager;
    }

    /** True when another plugin draws its own icon on this kind of resource (and for fishing spots with Only Current
     * Spot on, only on the spot you're fishing). */
    boolean drawsIcon(ResourceTimers.Kind kind, boolean yourSpot) {
        switch (kind) {
            case FISHING_SPOT:
                return fishingOn && setting("fishing", "showIcons", true)
                    && (yourSpot || !setting("fishing", "onlyCurrent", false));
            case VEIN:
                return motherlodeOn && setting("motherlode", "showVeins", true);
            default:
                return false;
        }
    }

    /** True when another plugin outlines this kind of resource's tile. */
    boolean drawsTile(ResourceTimers.Kind kind, boolean yourSpot) {
        return kind == ResourceTimers.Kind.FISHING_SPOT && fishingOn && setting("fishing", "showTiles", false)
            && (yourSpot || !setting("fishing", "onlyCurrent", false));
    }

    // Another plugin's setting as saved, or its default when never changed (only read, never written)
    private boolean setting(String group, String key, boolean byDefault) {
        String value = configManager.getConfiguration(group, key);
        return value == null ? byDefault : Boolean.parseBoolean(value);
    }

    /** True when another enabled plugin already draws this kind of timer. */
    boolean covers(ResourceTimers.Kind kind) {
        return covered.contains(kind);
    }

    void refresh() {
        covered.clear();
        fishingOn = false;
        motherlodeOn = false;
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (!pluginManager.isPluginEnabled(plugin)) {
                continue;
            }
            fishingOn |= plugin.getClass().getName().equals(FISHING);
            motherlodeOn |= plugin.getClass().getName().equals(MOTHERLODE);
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
