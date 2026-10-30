package com.snuffys.multitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * A card header: a picture (a Slayer master's chathead or the monster) on the left, a coloured title and a
 * grey subtitle beside it.
 */
class PortraitHeaderComponent implements LayoutableRenderableEntity {
    private static final int PICTURE = 32;
    private static final int GAP = 6;
    private static final Color SUBTITLE = new Color(190, 190, 190);

    private final Image picture;
    private final String title;
    private final Color titleColor;
    private final String subtitle;
    private final Rectangle bounds = new Rectangle();

    PortraitHeaderComponent(Image picture, String title, Color titleColor, String subtitle) {
        this.picture = picture;
        this.title = title;
        this.titleColor = titleColor;
        this.subtitle = subtitle;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        FontMetrics fm = graphics.getFontMetrics();
        int width = bounds.width > 0 ? bounds.width : 200;
        int textX = bounds.x;
        if (picture != null) {
            drawFitted(graphics, picture, bounds.x, bounds.y);
            textX += PICTURE + GAP;
        }
        int textWidth = width - (textX - bounds.x);
        List<String> titleLines = TextBlockComponent.wrap(title, fm, textWidth);
        // The subtitle can have several lines ("\n"), each wrapped to fit
        List<String> subLines = new java.util.ArrayList<>();
        if (subtitle != null) {
            for (String part : subtitle.split("\n")) {
                subLines.addAll(TextBlockComponent.wrap(part, fm, textWidth));
            }
        }
        int textHeight = (titleLines.size() + subLines.size()) * fm.getHeight();
        int height = Math.max(picture != null ? PICTURE : 0, textHeight);
        // Text centred against the picture
        int y = bounds.y + (height - textHeight) / 2 + fm.getAscent();
        for (String line : titleLines) {
            TextBlockComponent.shadowed(graphics, line, textX, y, titleColor);
            y += fm.getHeight();
        }
        for (String line : subLines) {
            TextBlockComponent.shadowed(graphics, line, textX, y, SUBTITLE);
            y += fm.getHeight();
        }
        return new Dimension(width, height);
    }

    // Scale into the picture box without stretching
    private static void drawFitted(Graphics2D graphics, Image image, int x, int y) {
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        if (w <= 0 || h <= 0) {
            return;
        }
        double scale = Math.min((double) PICTURE / w, (double) PICTURE / h);
        int dw = (int) Math.round(w * scale);
        int dh = (int) Math.round(h * scale);
        graphics.drawImage(image, x + (PICTURE - dw) / 2, y + (PICTURE - dh) / 2, dw, dh, null);
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void setPreferredLocation(Point position) {
        bounds.setLocation(position);
    }

    @Override
    public void setPreferredSize(Dimension dimension) {
        bounds.setSize(dimension);
    }
}
