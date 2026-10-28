package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines on the Gemstone Crab, its shell and the cave tunnels, drawn in the game world under the
 * interface (minimap, inventory, chat) rather than over it.
 */
public class BradsMultiToolCrabOutlineOverlay extends Overlay {

    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final ModelOutlineRenderer outlines;

    @Inject
    public BradsMultiToolCrabOutlineOverlay(BradsMultiToolPlugin plugin, BradsMultiToolConfig config, ModelOutlineRenderer outlines) {
        this.plugin = plugin;
        this.config = config;
        this.outlines = outlines;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.enableCrabFeatures() || !plugin.isInSpawnVicinity()) {
            return null;
        }
        NPC crab = plugin.getActiveCrab();
        if (crab != null && !crab.isDead() && plugin.isCrabActive()) {
            outlines.drawOutline(crab, 2, config.activeCrabColor(), 1);
        }
        Color shell = plugin.isShellMineable() ? config.mineableShellColor() : config.depletedShellColor();
        NPC shellNpc = plugin.getDormantCrabShellNpc();
        if (shellNpc != null && !shellNpc.isDead()) {
            outlines.drawOutline(shellNpc, 2, shell, 1);
        }
        for (TileObject shellObject : plugin.getTrackedShellObjects()) {
            outlines.drawOutline(shellObject, 2, shell, 1);
        }
        if (plugin.isCrabBurrowed()) {
            for (TileObject tunnel : plugin.getTrackedTunnels()) {
                if (tunnel != null) {
                    outlines.drawOutline(tunnel, 2, config.tunnelColor(), 1);
                }
            }
        }
        return null;
    }
}
