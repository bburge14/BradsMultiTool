package com.snuffys.skilltracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Over your character's head: the tick count, and with Click Timing on, how long after the tick each click
 * landed, rising and fading like an XP drop (green on time, red late).
 */
@Singleton
public class BradsMultiToolTickHeadOverlay extends Overlay {

    private static final long CLICK_SHOW_MILLIS = 1000;
    private static final int CLICK_RISE = 25;

    private final Client client;
    private final TickConfig config;
    private final TickCounter ticks;

    @Inject
    public BradsMultiToolTickHeadOverlay(Client client, TickConfig config, TickCounter ticks) {
        this.client = client;
        this.config = config;
        this.ticks = ticks;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Player player = client.getLocalPlayer();
        if (player == null || ticks.getCount() <= 0) {
            return null;
        }
        // Just above the head, where the game puts overhead text
        int height = player.getLogicalHeight();
        if (config.tickOverHead()) {
            String text = String.valueOf(ticks.getCount());
            graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, (float) config.tickTextSize()));
            Point location = player.getCanvasTextLocation(graphics, text, height);
            if (location != null) {
                OverlayUtil.renderTextLocation(graphics, location, text, BradsMultiToolTickBoxOverlay.tickColor(config, ticks.getCount()));
            }
        }
        long since = System.currentTimeMillis() - ticks.getLastClickMillis();
        if (config.clickTiming() && since < CLICK_SHOW_MILLIS) {
            String text = ticks.getLastClickOffset() + "ms";
            graphics.setFont(FontManager.getRunescapeSmallFont());
            Point location = player.getCanvasTextLocation(graphics, text, height);
            if (location != null) {
                int rise = (int) (CLICK_RISE * since / CLICK_SHOW_MILLIS) + (config.tickOverHead() ? config.tickTextSize() : 0);
                Color color = ticks.isLate(ticks.getLastClickOffset()) ? HudColors.BAD : HudColors.GOOD;
                int alpha = (int) (255 * (1 - since / (double) CLICK_SHOW_MILLIS));
                OverlayUtil.renderTextLocation(graphics, new Point(location.getX(), location.getY() - rise), text,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, alpha)));
            }
        }
        return null;
    }
}
