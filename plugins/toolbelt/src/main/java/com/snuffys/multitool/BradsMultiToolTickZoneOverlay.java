package com.snuffys.multitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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
 * The Ideal Zone Box: a box that lights up between ticks, during your ideal click zone (Ideal From to Ideal To
 * after each tick), for methods where you act part way through a tick. Moved like any overlay (Alt + drag); same
 * shape, size and opacity as the tick boxes. Like RuneLite's Prayer flick indicator, it only shows the timing.
 */
@Singleton
public class BradsMultiToolTickZoneOverlay extends Overlay {

    private static final Color BACKGROUND = new Color(20, 20, 20, 120);

    private final Client client;
    private final TickConfig config;
    private final TickCounter ticks;

    @Inject
    public BradsMultiToolTickZoneOverlay(Client client, TickConfig config, TickCounter ticks) {
        this.client = client;
        this.config = config;
        this.ticks = ticks;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    /** Whether this long after the tick is inside the ideal zone. */
    static boolean inZone(long sinceTick, int from, int to) {
        return to > from && sinceTick >= from && sinceTick < to;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !config.tickZoneBox() || ticks.getLastTickMillis() == 0) {
            return null;
        }
        long since = System.currentTimeMillis() - ticks.getLastTickMillis();
        boolean lit = inZone(since, config.clickIdealFrom(), config.clickIdealTo());
        int size = config.tickBoxSize();
        graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, size * 0.4f));
        // Drawn as a tick box: lit in the first-tick colour while in the zone
        BradsMultiToolTickBoxOverlay.drawBox(graphics, graphics.getFontMetrics(), config, 0, 0, size, 1, lit, 1.0,
            BACKGROUND, "GO");
        return new Dimension(size, size);
    }
}
