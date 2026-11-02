package com.bradsmultitool;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * "Finish it off": the item's picture over a lizard, gargoyle, rockslug or zygomite once its health is low enough
 * to use the item that kills it (ice cooler, rock hammer, bag of salt, fungicide spray), as RuneLite's Slayer plugin
 * does ("Show monster weakness"; its thresholds, BSD-2, credited in the README). Health is worked out from its health
 * bar and the wiki's hitpoints, so it only shows once the bar does. Only draws; never uses the item for you.
 * RuneLite's Slayer plugin marks its task monsters itself (not lizards), so while it does, ours leaves those to it.
 */
@Singleton
public class BradsMultiToolFinishOffOverlay extends Overlay {

    /** A monster that needs an item to finish, by name, with the hitpoints at or below which the item works. */
    static final class Finisher {
        final List<String> names;
        final int threshold;
        final int itemId;
        /** RuneLite's Slayer plugin marks this one too (on task). */
        final boolean coreMarks;

        Finisher(int threshold, int itemId, boolean coreMarks, String... names) {
            this.names = Arrays.asList(names);
            this.threshold = threshold;
            this.itemId = itemId;
            this.coreMarks = coreMarks;
        }
    }

    // Thresholds: RuneLite's Slayer plugin (Task.java) and the OSRS Wiki (lizards: 4 or fewer hitpoints)
    static final List<Finisher> FINISHERS = Arrays.asList(
        new Finisher(4, ItemID.SLAYER_ICY_WATER, false, "lizard", "small lizard", "desert lizard"),
        new Finisher(9, ItemID.SLAYER_ROCK_HAMMER, true, "gargoyle"),
        new Finisher(4, ItemID.SLAYER_BAG_OF_SALT, true, "rockslug"),
        new Finisher(7, ItemID.SLAYER_SPRAY_PUMP_4, true, "mutated zygomite", "ancient zygomite"));

    private static final String CORE_SLAYER = "net.runelite.client.plugins.slayer.SlayerPlugin";
    private static final long CHECK_MILLIS = 2000;

    private final Client client;
    private final PvmTracker plugin;
    private final PvmConfig config;
    private final ItemManager itemManager;
    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private long checkedAt;
    private boolean coreMarking;

    @Inject
    BradsMultiToolFinishOffOverlay(Client client, PvmTracker plugin, PvmConfig config, ItemManager itemManager,
                                   PluginManager pluginManager, ConfigManager configManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.pluginManager = pluginManager;
        this.configManager = configManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);
    }

    /** The monster's finisher by its name ("Desert Lizard"), or null when it doesn't need one. */
    static Finisher finisherFor(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase();
        for (Finisher finisher : FINISHERS) {
            if (finisher.names.contains(lower)) {
                return finisher;
            }
        }
        return null;
    }

    /** Hitpoints from the health bar, to the nearest whole one, or -1 while the bar isn't showing. */
    static int health(int ratio, int scale, int maxHp) {
        if (ratio < 0 || scale <= 0 || maxHp <= 0) {
            return -1;
        }
        return (int) (maxHp * ratio / (double) scale + 0.5);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.finishOffMarker() || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        boolean coreOn = coreMarking();
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            Finisher finisher = finisherFor(npc.getName());
            if (finisher == null || npc.isDead()
                || (coreOn && finisher.coreMarks && plugin.getSlayerTracker().isTaskMonster(npc))) {
                continue;
            }
            MonsterData.Stats stats = plugin.statsFor(npc);
            int hp = health(npc.getHealthRatio(), npc.getHealthScale(), stats != null ? stats.hitpoints : 0);
            if (hp < 0 || hp > finisher.threshold) {
                continue;
            }
            BufferedImage image = itemManager.getImage(finisher.itemId);
            LocalPoint at = npc.getLocalLocation();
            if (image == null || at == null) {
                continue;
            }
            Point spot = Perspective.getCanvasImageLocation(client, at, image, npc.getLogicalHeight() + 40);
            if (spot != null) {
                OverlayUtil.renderImageLocation(graphics, spot, image);
            }
        }
        return null;
    }

    // RuneLite's Slayer plugin on with its "Show monster weakness" (on unless changed): checked every few seconds,
    // only read
    private boolean coreMarking() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < CHECK_MILLIS) {
            return coreMarking;
        }
        checkedAt = now;
        boolean on = false;
        for (Plugin other : pluginManager.getPlugins()) {
            if (other.getClass().getName().equals(CORE_SLAYER) && pluginManager.isPluginEnabled(other)) {
                on = true;
                break;
            }
        }
        String setting = configManager.getConfiguration("slayer", "weaknessPrompt");
        coreMarking = on && (setting == null || Boolean.parseBoolean(setting));
        return coreMarking;
    }
}
