package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A ring round each timed fishing spot's minimap icon: cyan while it can't move yet, yellow to red through the
 * window it can move in (no green or filled dots, which the minimap uses for friends and players). Shown when
 * its timer is.
 */
@Singleton
public class BradsMultiToolFishingMinimapOverlay extends Overlay {

    private static final int RING = 12;
    // Not green: friends are green dots on the minimap
    private static final Color SAFE = new Color(0, 210, 255);
    private static final Color SOON = new Color(255, 220, 0);
    private static final Color LATE = new Color(230, 40, 40);

    private final Client client;
    private final SkillTrackerConfig config;
    private final ResourceTimers timers;
    private final BradsMultiToolResourceTimerOverlay timerOverlay;

    @Inject
    public BradsMultiToolFishingMinimapOverlay(Client client, SkillTrackerConfig config, ResourceTimers timers,
                                               BradsMultiToolResourceTimerOverlay timerOverlay) {
        this.client = client;
        this.config = config;
        this.timers = timers;
        this.timerOverlay = timerOverlay;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.fishingMinimapDots() || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (ResourceTimers.Timer timer : timers.all()) {
            if (timer.kind != ResourceTimers.Kind.FISHING_SPOT || timer.npc == null || !timerOverlay.shown(timer)) {
                continue;
            }
            Point at = timer.npc.getMinimapLocation();
            if (at == null) {
                continue;
            }
            // A ring round the spot's own minimap icon: filled dots would look like player or item dots
            graphics.setStroke(new java.awt.BasicStroke(3f));
            graphics.setColor(Color.BLACK);
            graphics.drawOval(at.getX() - RING / 2, at.getY() - RING / 2, RING, RING);
            graphics.setStroke(new java.awt.BasicStroke(2f));
            graphics.setColor(color(timer));
            graphics.drawOval(at.getX() - RING / 2, at.getY() - RING / 2, RING, RING);
        }
        return null;
    }

    // Cyan while it can't move; then from yellow to red across its window
    static Color color(ResourceTimers.Timer timer) {
        if (timer.ticksToEarliest() > 0) {
            return SAFE;
        }
        double t = timer.windowPassed();
        Color from = SOON;
        Color to = LATE;
        return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * t),
            (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
            (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }
}
