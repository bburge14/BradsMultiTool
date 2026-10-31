package com.snuffys.skilltracker;

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
 * A dot on the minimap for each timed fishing spot, coloured like its timer: green while it can't move yet,
 * orange to red through the window it can move in. Shown when its timer is.
 */
@Singleton
public class BradsMultiToolFishingMinimapOverlay extends Overlay {

    private static final int DOT = 5;

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
            graphics.setColor(color(timer));
            graphics.fillOval(at.getX() - DOT / 2, at.getY() - DOT / 2, DOT, DOT);
            graphics.setColor(Color.BLACK);
            graphics.drawOval(at.getX() - DOT / 2, at.getY() - DOT / 2, DOT, DOT);
        }
        return null;
    }

    // Green while it can't move; then from orange to red across its window
    static Color color(ResourceTimers.Timer timer) {
        if (timer.ticksToEarliest() > 0) {
            return HudColors.GOOD;
        }
        double t = timer.windowPassed();
        Color from = HudColors.WARN;
        Color to = HudColors.BAD;
        return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * t),
            (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
            (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }
}
