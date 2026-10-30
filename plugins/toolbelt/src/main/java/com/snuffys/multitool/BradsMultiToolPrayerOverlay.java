package com.snuffys.multitool;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class BradsMultiToolPrayerOverlay extends Overlay {

    private final Client client;
    private final PvmTracker plugin;
    private final PvmConfig config;

    @Inject
    public BradsMultiToolPrayerOverlay(Client client, PvmTracker plugin, PvmConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        // Above your character in the world, so under the game's interface
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !config.flashOutOfPrayerOverhead() || !plugin.isZeroPrayerAlert()) {
            return null;
        }

        Player player = client.getLocalPlayer();
        if (player == null) return null;

        if ((System.currentTimeMillis() / 300) % 2 == 0) {
            return null;
        }

        LocalPoint lp = player.getLocalLocation();
        if (lp == null) return null;

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // The game's bold font at the size picked in the settings (set before placing it, so it centres)
        Font boldFont = FontManager.getRunescapeBoldFont().deriveFont((float) config.outOfPrayerTextSize());
        graphics.setFont(boldFont);
        Point p = Perspective.getCanvasTextLocation(client, graphics, lp, "OUT OF PRAYER", player.getLogicalHeight() + 35);
        if (p == null) return null;

        FontMetrics fm = graphics.getFontMetrics(boldFont);
        int textWidth = fm.stringWidth("OUT OF PRAYER");

        int boxWidth = textWidth + 16;
        int boxHeight = fm.getAscent() + fm.getDescent() + 6;
        int boxX = p.getX() - 8;
        int boxY = p.getY() - fm.getAscent() - 3;

        graphics.setColor(new Color(0, 0, 0, 225));
        graphics.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 6, 6);

        graphics.setColor(new Color(255, 30, 30));
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 6, 6);

        graphics.setColor(Color.WHITE);
        graphics.drawString("OUT OF PRAYER", p.getX(), p.getY());

        return null;
    }
}
