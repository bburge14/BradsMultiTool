package com.snuffys.pvm;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * A paragraph wrapped to the panel's width (optionally as a bullet with a hanging indent). Measures its
 * own height, so the next row never draws over it.
 */
class TextBlockComponent implements LayoutableRenderableEntity {
    private static final int HEADING_SPACE = 4;

    private final String text;
    private final Color color;
    private final boolean bullet;
    private int spaceAbove;
    private final Rectangle bounds = new Rectangle();

    TextBlockComponent(String text, Color color, boolean bullet) {
        this.text = text;
        this.color = color;
        this.bullet = bullet;
    }

    /** A section heading: a little space above it to set it apart from the rows before. */
    static TextBlockComponent heading(String text, Color color) {
        TextBlockComponent block = new TextBlockComponent(text, color, false);
        block.spaceAbove = HEADING_SPACE;
        return block;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        FontMetrics fm = graphics.getFontMetrics();
        int width = bounds.width > 0 ? bounds.width : 200;
        int indent = bullet ? fm.stringWidth("\u2022 ") + 1 : 0;
        List<String> lines = wrap(text, fm, width - indent);
        int y = bounds.y + spaceAbove + fm.getAscent();
        for (int i = 0; i < lines.size(); i++) {
            if (bullet && i == 0) {
                shadowed(graphics, "\u2022", bounds.x, y, color);
            }
            shadowed(graphics, lines.get(i), bounds.x + indent, y, color);
            y += fm.getHeight();
        }
        return new Dimension(width, spaceAbove + lines.size() * fm.getHeight());
    }

    static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(next) > width && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    static void shadowed(Graphics2D graphics, String text, int x, int y, Color color) {
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
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
