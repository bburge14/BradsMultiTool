package com.snuffys.gemcrab;

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

    /** A box's label: the tick's number, or "1.5" for its half box. */
    static String label(int tick, boolean half) {
        return label(tick, half, false);
    }

    /** As above; counting down (4, 3, 2, 1), a half box comes before the next lower number ("3.5" after 4). */
    static String label(int number, boolean half, boolean countingDown) {
        if (!half) {
            return String.valueOf(number);
        }
        return (countingDown ? number - 1 : number) + ".5";
    }

    /**
     * Whether a box is lit now: a tick's box on its tick, or with Half-Tick Boxes only for the tick's first half,
     * its half box for the second.
     */
    static boolean isLit(TickCounter ticks, TickConfig config, int tick, boolean half) {
        if (tick != ticks.getCount()) {
            return false;
        }
        if (!config.tickHalfBoxes()) {
            return !half;
        }
        boolean secondHalf = System.currentTimeMillis() - ticks.getLastTickMillis() >= TICK_MILLIS / 2;
        return half == secondHalf;
    }

    /** How lit: fading over the tick, or over each half with Half-Tick Boxes. */
    static double halfFade(TickCounter ticks, TickConfig config) {
        if (!config.tickHalfBoxes()) {
            return fade(ticks);
        }
        long since = (System.currentTimeMillis() - ticks.getLastTickMillis()) % (TICK_MILLIS / 2);
        return 1 - Math.min(1, since / (double) (TICK_MILLIS / 2)) * 0.7;
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
        if (!config.tickBoxNumbers()) {
            graphics.setComposite(before);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                antialias != null ? antialias : java.awt.RenderingHints.VALUE_ANTIALIAS_DEFAULT);
            return;
        }
        String text = label;
        // A label too wide for its box shrinks to fit (just this box)
        java.awt.Font original = graphics.getFont();
        java.awt.Font font = original;
        while (metrics.stringWidth(text) > size - 4 && font.getSize2D() > 8) {
            font = font.deriveFont(font.getSize2D() - 1);
            graphics.setFont(font);
            metrics = graphics.getFontMetrics();
        }
        int textX = x + (size - metrics.stringWidth(text)) / 2;
        int textY = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, textX + 1, textY + 1);
        graphics.setColor(lit ? Color.WHITE : Color.GRAY);
        graphics.drawString(text, textX, textY);
        graphics.setFont(original);
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
            || config.tickBoxesSeparate() || ticks.isHidden()) {
            return null;
        }
        int size = config.tickBoxSize();
        int cycle = Math.max(1, ticks.getCycle());
        int current = ticks.getCount();
        boolean horizontal = config.tickBoxLayout() == TickConfig.Layout.HORIZONTAL;
        graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, size * 0.55f));
        FontMetrics metrics = graphics.getFontMetrics();
        double fade = fade(ticks);

        java.util.List<TickMethods.Step> steps = TickMethods.steps(config.tickMethod());
        if (!steps.isEmpty()) {
            // A tick method: one box per step, labelled, lit when it's time
            long since = System.currentTimeMillis() - ticks.getLastTickMillis();
            for (int i = 0; i < steps.size(); i++) {
                TickMethods.Step step = steps.get(i);
                int x = horizontal ? i * (size + GAP) : 0;
                int y = horizontal ? 0 : i * (size + GAP);
                drawBox(graphics, metrics, config, x, y, size, i == 0 ? 1 : 2, step.lit(current, since),
                    halfFade(ticks, config), BACKGROUND, step.label);
            }
            int length = steps.size() * size + (steps.size() - 1) * GAP;
            return horizontal ? new Dimension(length, size) : new Dimension(size, length);
        }
        boolean halves = config.tickHalfBoxes();
        int slots = halves ? cycle * 2 : cycle;
        for (int slot = 0; slot < slots; slot++) {
            int tick = halves ? slot / 2 + 1 : slot + 1;
            boolean half = halves && slot % 2 == 1;
            int x = horizontal ? slot * (size + GAP) : 0;
            int y = horizontal ? 0 : slot * (size + GAP);
            drawBox(graphics, metrics, config, x, y, size, tick, isLit(ticks, config, tick, half), halfFade(ticks, config),
                BACKGROUND, label(ticks.boxNumber(tick), half, ticks.countingDown()));
        }
        int length = slots * size + (slots - 1) * GAP;
        return horizontal ? new Dimension(length, size) : new Dimension(size, length);
    }
}
