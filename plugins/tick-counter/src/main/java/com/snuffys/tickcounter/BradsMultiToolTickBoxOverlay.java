package com.snuffys.tickcounter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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
 * The tick boxes: one for each tick of the cycle, in a row or a column, the current one lit (the first tick of a
 * cycle in its own colour) and fading over the tick. Moved like any overlay (Alt + drag). One instance however
 * many features show it, so the Toolbelt draws it once.
 */
@Singleton
public class BradsMultiToolTickBoxOverlay extends Overlay {

    static final long TICK_MILLIS = 600;
    private static final int GAP = 3;
    private static final Color BACKGROUND = new Color(20, 20, 20, 170);
    private static final Color IDLE_BORDER = new Color(90, 90, 90, 200);

    private final Client client;
    private final TickConfig config;
    private final TickCounter ticks;

    @Inject
    public BradsMultiToolTickBoxOverlay(Client client, TickConfig config, TickCounter ticks) {
        this.client = client;
        this.config = config;
        this.ticks = ticks;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    /** The colour for this tick of the cycle. */
    static Color tickColor(TickConfig config, int count) {
        return count == 1 ? config.tickFirstColor() : config.tickColor();
    }

    /** How lit the current tick is: 1 as it comes, fading to 0.3 over the tick. */
    static double fade(TickCounter ticks) {
        long since = System.currentTimeMillis() - ticks.getLastTickMillis();
        return 1 - Math.min(1, since / (double) TICK_MILLIS) * 0.7;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !config.tickBoxes() || ticks.getCount() <= 0) {
            return null;
        }
        int size = config.tickBoxSize();
        int cycle = Math.max(1, ticks.getCycle());
        int current = ticks.getCount();
        boolean horizontal = config.tickBoxLayout() == TickConfig.Layout.HORIZONTAL;
        graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, size * 0.55f));
        FontMetrics metrics = graphics.getFontMetrics();
        double fade = fade(ticks);

        for (int tick = 1; tick <= cycle; tick++) {
            int x = horizontal ? (tick - 1) * (size + GAP) : 0;
            int y = horizontal ? 0 : (tick - 1) * (size + GAP);
            Color color = tickColor(config, tick);
            boolean lit = tick == current;
            graphics.setColor(BACKGROUND);
            graphics.fillRect(x, y, size, size);
            if (lit) {
                graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * fade * 0.7)));
                graphics.fillRect(x, y, size, size);
            }
            graphics.setColor(lit ? color : IDLE_BORDER);
            graphics.drawRect(x, y, size - 1, size - 1);
            if (lit) {
                graphics.drawRect(x + 1, y + 1, size - 3, size - 3);
            }

            String text = String.valueOf(tick);
            int textX = x + (size - metrics.stringWidth(text)) / 2;
            int textY = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();
            graphics.setColor(Color.BLACK);
            graphics.drawString(text, textX + 1, textY + 1);
            graphics.setColor(lit ? Color.WHITE : Color.GRAY);
            graphics.drawString(text, textX, textY);
        }
        int length = cycle * size + (cycle - 1) * GAP;
        return horizontal ? new Dimension(length, size) : new Dimension(size, length);
    }
}
