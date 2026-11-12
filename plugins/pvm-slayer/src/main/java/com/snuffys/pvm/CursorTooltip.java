package com.snuffys.pvm;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
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

    /**
     * Whether something else is drawn under the mouse: another overlay's panel (another plugin's, or one of ours),
     * which the game can't know about (its hover text still names what's behind it). A hover card stays away then.
     * Overlays drawn over the whole game view (tile and object highlights) and tooltips don't count. Only reads where
     * they were drawn last.
     */
    static boolean covered(Client client, OverlayManager overlays, Overlay self) {
        Point mouse = client.getMouseCanvasPosition();
        if (overlays == null || mouse == null || mouse.getX() < 0 || mouse.getY() < 0) {
            return false;
        }
        int width = client.getCanvasWidth();
        int height = client.getCanvasHeight();
        return overlays.anyMatch(overlay -> {
            if (overlay == self || overlay.getPosition() == OverlayPosition.DYNAMIC
                || overlay.getPosition() == OverlayPosition.TOOLTIP) {
                return false;
            }
            Rectangle bounds = overlay.getBounds();
            if (bounds == null || bounds.isEmpty() || (bounds.width >= width * 0.8 && bounds.height >= height * 0.8)) {
                return false;
            }
            return bounds.contains(mouse.getX(), mouse.getY());
        });
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
