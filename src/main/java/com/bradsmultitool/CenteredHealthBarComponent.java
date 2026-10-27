package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

class CenteredHealthBarComponent implements LayoutableRenderableEntity {
    private final int percent;
    private final int curHp;
    private final int maxHp;
    private Rectangle bounds = new Rectangle();

    CenteredHealthBarComponent(int percent, int curHp, int maxHp) {
        this.percent = Math.max(0, Math.min(100, percent));
        this.curHp = curHp;
        this.maxHp = maxHp;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        int width = bounds.width > 0 ? bounds.width : 185;
        int height = 15;

        int x = bounds.x;
        int y = bounds.y + 3;

        graphics.setColor(new Color(30, 30, 30, 230));
        graphics.fillRect(x, y, width, height);

        int fillWidth = (int) ((width * percent) / 100.0);
        Color fillColor = (percent > 50) ? new Color(0, 190, 80) : ((percent > 20) ? new Color(240, 170, 0) : new Color(230, 40, 40));
        graphics.setColor(fillColor);
        graphics.fillRect(x, y, fillWidth, height);

        graphics.setColor(new Color(15, 15, 15, 240));
        graphics.drawRect(x, y, width, height);

        String text = (maxHp > 0) ? (curHp + " / " + maxHp + " (" + percent + "%)") : (percent + "%");
        Font font = FontManager.getRunescapeSmallFont();
        graphics.setFont(font);
        FontMetrics fm = graphics.getFontMetrics(font);
        int textX = x + (width - fm.stringWidth(text)) / 2;
        int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();

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
