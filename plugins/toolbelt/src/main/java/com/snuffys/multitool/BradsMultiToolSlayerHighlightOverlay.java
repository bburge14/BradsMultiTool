package com.snuffys.multitool;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines the monsters that count for your Slayer task. On a Konar task, ones outside her assigned area
 * aren't outlined, since killing them there doesn't count.
 */
@Singleton
public class BradsMultiToolSlayerHighlightOverlay extends Overlay {

    private static final int OUTLINE_WIDTH = 2;
    private static final int OUTLINE_FEATHER = 4;

    private final Client client;
    private final PvmTracker plugin;
    private final PvmConfig config;
    private final SlayerMonsterInfo slayerInfo;
    private final ModelOutlineRenderer outlines;

    @Inject
    BradsMultiToolSlayerHighlightOverlay(Client client, PvmTracker plugin, PvmConfig config,
                                         SlayerMonsterInfo slayerInfo, ModelOutlineRenderer outlines) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.slayerInfo = slayerInfo;
        this.outlines = outlines;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.highlightTaskMonsters() || client.getGameState() != GameState.LOGGED_IN
            || !plugin.getSlayerTracker().hadTask()) {
            return null;
        }
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc.isDead()) {
                continue;
            }
            if (slayerInfo.match(npc, plugin.statsFor(npc)) == SlayerMonsterInfo.Match.TASK) {
                outlines.drawOutline(npc, OUTLINE_WIDTH, config.taskMonsterColor(), OUTLINE_FEATHER);
            }
        }
        return null;
    }
}
