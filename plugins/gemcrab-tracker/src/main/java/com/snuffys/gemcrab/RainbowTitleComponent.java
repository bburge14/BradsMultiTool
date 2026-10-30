package com.snuffys.gemcrab;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * A centred title whose first part is drawn letter by letter in shifting rainbow colours
 * (like light catching a gem), followed by an optional plain-coloured part.
 */
class RainbowTitleComponent implements LayoutableRenderableEntity {
    // Hue step between letters, and how long one full colour cycle takes
    private static final float HUE_PER_LETTER = 0.07f;
    private static final long CYCLE_MILLIS = 4000;

    private final String rainbowText;
    private final String plainText;
    private final Color plainColor;
    private final Rectangle bounds = new Rectangle();

    RainbowTitleComponent(String rainbowText, String plainText, Color plainColor) {
        this.rainbowText = rainbowText;
        this.plainText = plainText;
        this.plainColor = plainColor;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        FontMetrics fm = graphics.getFontMetrics();
        int width = bounds.width > 0 ? bounds.width : fm.stringWidth(rainbowText + plainText);
        int x = bounds.x + (width - fm.stringWidth(rainbowText + plainText)) / 2;
        int y = bounds.y + fm.getAscent();

        float offset = (System.currentTimeMillis() % CYCLE_MILLIS) / (float) CYCLE_MILLIS;
        for (int i = 0; i < rainbowText.length(); i++) {
            String letter = String.valueOf(rainbowText.charAt(i));
            Color color = Color.getHSBColor((offset + i * HUE_PER_LETTER) % 1f, 0.65f, 1f);
            drawShadowed(graphics, letter, x, y, color);
            x += fm.stringWidth(letter);
        }
        if (!plainText.isEmpty()) {
            drawShadowed(graphics, plainText, x, y, plainColor);
        }

        return new Dimension(width, fm.getHeight());
    }

    private static void drawShadowed(Graphics2D graphics, String text, int x, int y, Color color) {
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    @Override
    public void setPreferredLocation(java.awt.Point position) {
        bounds.setLocation(position);
    }

    @Override
    public void setPreferredSize(Dimension dimension) {
        bounds.setSize(dimension);
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }
}
