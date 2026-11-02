package com.snuffys.multitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The tick bar: fills smoothly from left to right over each tick (drawn every frame, so it moves rather than
 * flashes) and snaps back when the next tick comes. Moved like any overlay (Alt + drag).
 */
@Singleton
public class BradsMultiToolTickBarOverlay extends Overlay {

    private static final int HEIGHT = 14;
    private static final Color BACKGROUND = new Color(20, 20, 20, 170);
    private static final Color LATE_ZONE = new Color(255, 140, 0, 90);
    private static final Color IDEAL_ZONE = new Color(50, 220, 50, 70);

    private final Client client;
    private final TickConfig config;
    private final TickCounter ticks;

    @Inject
    public BradsMultiToolTickBarOverlay(Client client, TickConfig config, TickCounter ticks) {
        this.client = client;
        this.config = config;
        this.ticks = ticks;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        boolean bar = config.tickBar();
        boolean summary = false;
        if (client.getGameState() != GameState.LOGGED_IN || (!bar && !summary) || ticks.getCount() <= 0
            || ticks.isHidden()) {
            return null;
        }
        int width = config.tickBarWidth();
        int y = 0;
        if (bar) {
            long since = System.currentTimeMillis() - ticks.getLastTickMillis();
            double progress = Math.min(1, since / (double) BradsMultiToolTickBoxOverlay.TICK_MILLIS);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, width, HEIGHT);
            Color fill = BradsMultiToolTickBoxOverlay.tickColor(config, ticks.getCount());
            graphics.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.min(fill.getAlpha(), 150)));
            graphics.fillRect(0, 0, (int) (width * progress), HEIGHT);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(0, 0, width - 1, HEIGHT - 1);
            y = HEIGHT;
        }
        return new Dimension(width, y);
    }

    /** Where this many ms into the tick is on a bar this wide. */
    private static int x(int width, int millis) {
        return (int) (width * Math.min(1, millis / (double) BradsMultiToolTickBoxOverlay.TICK_MILLIS));
    }

}
