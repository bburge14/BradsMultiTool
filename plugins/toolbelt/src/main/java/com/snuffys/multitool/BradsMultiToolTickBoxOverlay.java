package com.snuffys.multitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The tick box: a square that flashes on each tick (the first tick of a cycle in its own colour) with the
 * count in it. Moved like any overlay (Alt + drag).
 */
public class BradsMultiToolTickBoxOverlay extends Overlay {

    private static final long TICK_MILLIS = 600;

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

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !config.tickDisplay().box() || ticks.getCount() <= 0) {
            return null;
        }
        int size = config.tickBoxSize();
        int count = ticks.getCount();
        Color color = count == 1 ? config.tickFirstColor() : config.tickColor();

        // Bright as the tick comes, fading over the tick
        long since = System.currentTimeMillis() - ticks.getLastTickMillis();
        double fade = 1 - Math.min(1, since / (double) TICK_MILLIS) * 0.7;
        graphics.setColor(new Color(20, 20, 20, 170));
        graphics.fillRect(0, 0, size, size);
        graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * fade * 0.6)));
        graphics.fillRect(0, 0, size, size);
        graphics.setColor(color);
        graphics.drawRect(0, 0, size - 1, size - 1);

        String text = String.valueOf(count);
        graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, size * 0.55f));
        FontMetrics metrics = graphics.getFontMetrics();
        int x = (size - metrics.stringWidth(text)) / 2;
        int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, x, y);
        return new Dimension(size, size);
    }
}
