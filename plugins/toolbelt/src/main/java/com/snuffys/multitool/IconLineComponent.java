package com.snuffys.multitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * A label on the left and an icon followed by coloured text on the right, sized like a LineComponent row.
 */
class IconLineComponent implements LayoutableRenderableEntity {
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 3;
    private static final int LABEL_GAP = 6;

    private final String left;
    private final List<Image> icons;
    private final String right;
    private final Color rightColor;
    private boolean iconsAfterText;
    private final Rectangle bounds = new Rectangle();

    IconLineComponent(String left, Image icon, String right, Color rightColor) {
        this(left, icon != null ? Collections.singletonList(icon) : Collections.emptyList(), right, rightColor);
    }

    /** Several icons in a row before the text, e.g. one per skill. Null entries are skipped. */
    IconLineComponent(String left, List<Image> icons, String right, Color rightColor) {
        this.left = left;
        this.icons = new ArrayList<>();
        for (Image icon : icons) {
            if (icon != null) {
                this.icons.add(icon);
            }
        }
        this.right = right;
        this.rightColor = rightColor;
    }

    /** Text first, then the icon(s): "Level 55 [icon]". */
    static IconLineComponent textThenIcon(String left, Image icon, String right, Color rightColor) {
        IconLineComponent line = new IconLineComponent(left, icon, right, rightColor);
        line.iconsAfterText = true;
        return line;
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
        int available = width - fm.stringWidth(left) - LABEL_GAP - icons.size() * (ICON_SIZE + ICON_GAP);
        String text = fitText(right, fm, available);
        int iconsWidth = icons.size() * (ICON_SIZE + ICON_GAP);
        int textX = x + width - fm.stringWidth(text) - (iconsAfterText ? iconsWidth : 0);
        drawShadowed(graphics, text, textX, baseline, rightColor);

        int iconX = iconsAfterText ? x + width + ICON_GAP : textX;
        for (int i = icons.size() - 1; i >= 0; i--) {
            iconX -= ICON_GAP + ICON_SIZE;
            drawFitted(graphics, icons.get(i), iconX, bounds.y);
        }

        return new Dimension(width, height);
    }

    // Scale the icon to fit the slot without stretching it
    private static void drawFitted(Graphics2D graphics, Image icon, int x, int y) {
        int w = icon.getWidth(null);
        int h = icon.getHeight(null);
        if (w <= 0 || h <= 0) {
            return;
        }
        double scale = Math.min((double) ICON_SIZE / w, (double) ICON_SIZE / h);
        int dw = (int) Math.round(w * scale);
        int dh = (int) Math.round(h * scale);
        graphics.drawImage(icon, x + (ICON_SIZE - dw) / 2, y + (ICON_SIZE - dh) / 2, dw, dh, null);
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
