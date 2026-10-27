package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * A label on the left and an icon followed by coloured text on the right, sized like a LineComponent row.
 */
class IconLineComponent implements LayoutableRenderableEntity {
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 3;
    private static final int LABEL_GAP = 6;

    private final String left;
    private final Image icon;
    private final String right;
    private final Color rightColor;
    private final Rectangle bounds = new Rectangle();

    IconLineComponent(String left, Image icon, String right, Color rightColor) {
        this.left = left;
        this.icon = icon;
        this.right = right;
        this.rightColor = rightColor;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        FontMetrics fm = graphics.getFontMetrics();
        int width = bounds.width > 0 ? bounds.width : 185;
        int height = Math.max(ICON_SIZE, fm.getHeight());
        int x = bounds.x;
        int baseline = bounds.y + (height - fm.getHeight()) / 2 + fm.getAscent();

        drawShadowed(graphics, left, x, baseline, Color.WHITE);

        // Never let the right side run into the label: trim it with "..." if it doesn't fit
        int available = width - fm.stringWidth(left) - LABEL_GAP - (icon != null ? ICON_SIZE + ICON_GAP : 0);
        String text = fitText(right, fm, available);
        int textX = x + width - fm.stringWidth(text);
        drawShadowed(graphics, text, textX, baseline, rightColor);

        if (icon != null) {
            int iconX = textX - ICON_GAP - ICON_SIZE;
            graphics.drawImage(icon, iconX, bounds.y, ICON_SIZE, ICON_SIZE, null);
        }

        return new Dimension(width, height);
    }

    private static String fitText(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
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
