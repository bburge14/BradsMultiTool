package com.snuffys.multitool;

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

    /** One tick's box at (x, y): its number, lit (and fading) on its tick. */
    static void drawBox(Graphics2D graphics, FontMetrics metrics, TickConfig config, int x, int y, int size, int tick,
                        boolean lit, double fade, Color background) {
        drawBox(graphics, metrics, config, x, y, size, tick, lit, fade, background, String.valueOf(tick));
    }

    /** As above, with this label in the box instead of the tick's number. */
    static void drawBox(Graphics2D graphics, FontMetrics metrics, TickConfig config, int x, int y, int size, int tick,
                        boolean lit, double fade, Color background, String label) {
        Color color = tickColor(config, tick);
        java.awt.Composite before = graphics.getComposite();
        graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER,
            Math.max(0.1f, Math.min(1f, config.tickBoxOpacity() / 100f))));
        Object antialias = graphics.getRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.Shape outer = shape(config.tickBoxShape(), x, y, size - 1);
        graphics.setColor(background);
        graphics.fill(outer);
        if (lit) {
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * fade * 0.7)));
            graphics.fill(outer);
        }
        graphics.setColor(lit ? color : IDLE_BORDER);
        graphics.draw(outer);
        if (lit) {
            graphics.draw(shape(config.tickBoxShape(), x + 1, y + 1, size - 3));
        }
        String text = label;
        int textX = x + (size - metrics.stringWidth(text)) / 2;
        int textY = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, textX + 1, textY + 1);
        graphics.setColor(lit ? Color.WHITE : Color.GRAY);
        graphics.drawString(text, textX, textY);
        graphics.setComposite(before);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            antialias != null ? antialias : java.awt.RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    private static java.awt.Shape shape(TickConfig.BoxShape shape, int x, int y, int size) {
        switch (shape) {
            case CIRCLE:
                return new java.awt.geom.Ellipse2D.Float(x, y, size, size);
            case ROUNDED:
                return new java.awt.geom.RoundRectangle2D.Float(x, y, size, size, size / 3f, size / 3f);
            default:
                return new java.awt.Rectangle(x, y, size, size);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !config.tickBoxes() || ticks.getCount() <= 0
            || config.tickBoxesSeparate()) {
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
            drawBox(graphics, metrics, config, x, y, size, tick, tick == current, fade, BACKGROUND);
        }
        int length = cycle * size + (cycle - 1) * GAP;
        return horizontal ? new Dimension(length, size) : new Dimension(size, length);
    }
}
