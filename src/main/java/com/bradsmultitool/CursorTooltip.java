package com.bradsmultitool;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * Draws a hover card below the cursor, flipped to stay on screen.
 */
final class CursorTooltip {

    // Sit well below the cursor so the game's hover text ("Attack Guard (level-21)") stays readable
    private static final int OFFSET_X = 0;
    private static final int OFFSET_Y = 52;

    private CursorTooltip() {
    }

    static void render(Client client, Graphics2D graphics, PanelComponent panel) {
        // Measure first (drawing clipped away), then place next to the cursor, kept on screen
        Graphics2D measure = (Graphics2D) graphics.create();
        measure.setClip(new Rectangle(0, 0, 0, 0));
        Dimension size = panel.render(measure);
        measure.dispose();

        Point mouse = client.getMouseCanvasPosition();
        int x = mouse.getX() + OFFSET_X;
        int y = mouse.getY() + OFFSET_Y;
        if (x + size.width > client.getCanvasWidth()) {
            x = mouse.getX() - OFFSET_X - size.width;
        }
        if (y + size.height > client.getCanvasHeight()) {
            y = mouse.getY() - OFFSET_Y - size.height;
        }

        Graphics2D placed = (Graphics2D) graphics.create();
        placed.translate(Math.max(0, x), Math.max(0, y));
        panel.render(placed);
        placed.dispose();
    }
}
