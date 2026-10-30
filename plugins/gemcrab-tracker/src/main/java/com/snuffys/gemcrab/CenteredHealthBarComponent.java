package com.snuffys.gemcrab;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

class CenteredHealthBarComponent implements LayoutableRenderableEntity {
    private static final Color GAME_GREEN = new Color(0, 200, 0);
    private static final Color GAME_RED = new Color(200, 0, 0);

    private final int percent;
    private final int curHp;
    private final int maxHp;
    /** True for the in-game look: green health left over red health lost. */
    private final boolean gameStyle;
    private Rectangle bounds = new Rectangle();

    CenteredHealthBarComponent(int percent, int curHp, int maxHp) {
        this(percent, curHp, maxHp, false);
    }

    CenteredHealthBarComponent(int percent, int curHp, int maxHp, boolean gameStyle) {
        this.percent = Math.max(0, Math.min(100, percent));
        this.curHp = curHp;
        this.maxHp = maxHp;
        this.gameStyle = gameStyle;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        int width = bounds.width > 0 ? bounds.width : 185;
        int height = 15;

        int x = bounds.x;
        int y = bounds.y + 3;

        graphics.setColor(gameStyle ? GAME_RED : new Color(30, 30, 30, 230));
        graphics.fillRect(x, y, width, height);

        int fillWidth = (int) ((width * percent) / 100.0);
        Color fillColor;
        if (gameStyle) {
            fillColor = GAME_GREEN;
        } else {
            fillColor = (percent > 50) ? new Color(0, 190, 80) : ((percent > 20) ? new Color(240, 170, 0) : new Color(230, 40, 40));
        }
        graphics.setColor(fillColor);
        graphics.fillRect(x, y, fillWidth, height);

        graphics.setColor(new Color(15, 15, 15, 240));
        graphics.drawRect(x, y, width, height);

        String text = (maxHp > 0) ? (curHp + " / " + maxHp + " (" + percent + "%)") : (percent + "%");
        Font font = FontManager.getRunescapeSmallFont();
        graphics.setFont(font);
        FontMetrics fm = graphics.getFontMetrics(font);
        int textX = x + (width - fm.stringWidth(text)) / 2;
        // Centre on the glyphs actually drawn: the RuneScape font's ascent is taller than its digits,
        // so centring on font metrics sits the text a couple of pixels high
        Rectangle2D glyphs = font.createGlyphVector(graphics.getFontRenderContext(), text).getVisualBounds();
        int textY = (int) Math.round(y + (height - glyphs.getHeight()) / 2.0 - glyphs.getY());

        graphics.setColor(Color.BLACK);
        graphics.drawString(text, textX + 1, textY + 1);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, textX, textY);

        return new Dimension(width, height + 6);
    }

    @Override
    public void setPreferredLocation(java.awt.Point position) {
        this.bounds.setLocation(position);
    }

    @Override
    public void setPreferredSize(Dimension dimension) {
        this.bounds.setSize(dimension);
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }
}
