package com.snuffys.tickcounter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
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
 * landed, rising and fading like an XP drop (green in the ideal zone, red late).
 */
@Singleton
public class BradsMultiToolTickHeadOverlay extends Overlay {

    private static final long CLICK_SHOW_MILLIS = 1000;
    private static final int CLICK_RISE = 25;
    /** Pixels between the top of your character and the text. */
    private static final int HEAD_GAP = 4;

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
        Rectangle top = topOfModel(player);
        if (config.tickOverHead()) {
            String text = String.valueOf(ticks.getCount());
            graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, (float) config.tickTextSize()));
            Point location = above(graphics, player, top, text);
            if (location != null) {
                OverlayUtil.renderTextLocation(graphics, location, text, BradsMultiToolTickBoxOverlay.tickColor(config, ticks.getCount()));
            }
        }
        long since = System.currentTimeMillis() - ticks.getLastClickMillis();
        if (config.clickTiming() && since < CLICK_SHOW_MILLIS) {
            String text = ticks.getLastClickOffset() + "ms";
            graphics.setFont(FontManager.getRunescapeSmallFont());
            Point location = above(graphics, player, top, text);
            if (location != null) {
                int rise = (int) (CLICK_RISE * since / CLICK_SHOW_MILLIS) + (config.tickOverHead() ? config.tickTextSize() : 0);
                Color color = ticks.clickColor(ticks.getLastClickOffset());
                int alpha = (int) (255 * (1 - since / (double) CLICK_SHOW_MILLIS));
                OverlayUtil.renderTextLocation(graphics, new Point(location.getX(), location.getY() - rise), text,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, alpha)));
            }
        }
        return null;
    }

    // The character as drawn on screen, or null when the game hasn't drawn it
    private static Rectangle topOfModel(Player player) {
        Shape hull = player.getConvexHull();
        return hull == null ? null : hull.getBounds();
    }

    // Where text goes so it sits just clear of the top of your character as drawn, whatever the camera angle:
    // anchored to the head alone, a camera looking down puts the text on your body and the model hides it
    private static Point above(Graphics2D graphics, Player player, Rectangle model, String text) {
        int width = graphics.getFontMetrics().stringWidth(text);
        if (model == null || model.isEmpty()) {
            return player.getCanvasTextLocation(graphics, text, player.getLogicalHeight());
        }
        return new Point((int) model.getCenterX() - width / 2, model.y - HEAD_GAP);
    }
}
