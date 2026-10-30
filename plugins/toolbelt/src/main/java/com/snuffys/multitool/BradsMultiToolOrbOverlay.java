package com.snuffys.multitool;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Pulses a red glow in the shape of the minimap hitpoints heart / prayer star while that stat is below
 * its alert threshold.
 */
public class BradsMultiToolOrbOverlay extends Overlay {

    // Orb containers (number + circle) in each orb layout (InterfaceID.Orbs, OrbsNomap, OrbsOsm, OrbsOsmNomap)
    private static final int[] HEALTH_ORBS = {10485767, 58654725, 58785795, 58851332};
    private static final int[] PRAYER_ORBS = {10485778, 58654736, 58785806, 58851343};
    // The heart / prayer-star icons inside those orbs (ORB_HEALTH_HEART_ICON, PRAYER_ICON)
    private static final int[] HEALTH_ICONS = {10485777, 58654735, 58785805, 58851342};
    private static final int[] PRAYER_ICONS = {10485785, 58654743, 58785813, 58851350};
    private static final long PULSE_MILLIS = 900;

    private final Client client;
    private final PvmTracker plugin;
    private final PvmConfig config;
    private final SpriteManager spriteManager;
    private final Map<Integer, BufferedImage> masks = new HashMap<>();

    @Inject
    public BradsMultiToolOrbOverlay(Client client, PvmTracker plugin, PvmConfig config,
                                    SpriteManager spriteManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.spriteManager = spriteManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        if (config.enableLowHealthAlert() && config.flashHealthOrb() && plugin.isLowHealthAlert()) {
            pulseIcon(graphics, visibleWidget(HEALTH_ICONS), HEALTH_ORBS);
        }
        if (config.enableLowPrayerAlert() && config.flashPrayerOrb()
            && (plugin.isLowPrayerAlert() || plugin.isZeroPrayerAlert())) {
            pulseIcon(graphics, visibleWidget(PRAYER_ICONS), PRAYER_ORBS);
        }
        return null;
    }

    /**
     * Pulses a red glow in the exact shape of the orb's icon (the heart, or the prayer star), using the
     * icon sprite's transparency as the mask. Falls back to the round orb if the icon can't be read.
     */
    private void pulseIcon(Graphics2D graphics, Widget icon, int[] orbIds) {
        BufferedImage mask = icon != null ? redMask(icon.getSpriteId()) : null;
        if (mask == null) {
            pulse(graphics, circleOf(visibleWidget(orbIds)));
            return;
        }
        Rectangle b = icon.getBounds();
        float strength = strength();
        Composite old = graphics.getComposite();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // The shape itself, drawn a little inside the icon so the flash stays small
        int w = Math.round(b.width * 0.7f);
        int h = Math.round(b.height * 0.7f);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f + 0.55f * strength));
        graphics.drawImage(mask, b.x + (b.width - w) / 2, b.y + (b.height - h) / 2, w, h, null);
        graphics.setComposite(old);
    }

    // Icon sprite recoloured solid red, keeping its alpha; cached per sprite
    private BufferedImage redMask(int spriteId) {
        if (spriteId <= 0) {
            return null;
        }
        return masks.computeIfAbsent(spriteId, id -> {
            BufferedImage sprite = spriteManager.getSprite(id, 0);
            if (sprite == null) {
                return null;
            }
            BufferedImage out = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < sprite.getHeight(); y++) {
                for (int x = 0; x < sprite.getWidth(); x++) {
                    int alpha = sprite.getRGB(x, y) >>> 24;
                    out.setRGB(x, y, (alpha << 24) | 0xFF2020);
                }
            }
            return out;
        });
    }

    private static float strength() {
        // 0 -> 1 -> 0 over each pulse
        double phase = (System.currentTimeMillis() % PULSE_MILLIS) / (double) PULSE_MILLIS;
        return (float) (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI));
    }

    private Widget visibleWidget(int[] ids) {
        for (int id : ids) {
            Widget widget = client.getWidget(id);
            if (widget != null && !widget.isHidden()) {
                return widget;
            }
        }
        return null;
    }

    /**
     * The orb's round part: the largest roughly square visible piece inside the orb container
     * (the container itself also holds the number box).
     */
    private static Rectangle circleOf(Widget container) {
        if (container == null) {
            return null;
        }
        Rectangle best = null;
        Deque<Widget> pending = new ArrayDeque<>();
        pending.add(container);
        while (!pending.isEmpty()) {
            Widget w = pending.poll();
            Rectangle b = w.getBounds();
            if (w != container && !w.isHidden() && b.width >= 10 && Math.abs(b.width - b.height) <= 3
                && (best == null || b.width * b.height > best.width * best.height)) {
                best = b;
            }
            for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()}) {
                if (children != null) {
                    for (Widget child : children) {
                        if (child != null) {
                            pending.add(child);
                        }
                    }
                }
            }
        }
        return best;
    }

    private static void pulse(Graphics2D graphics, Rectangle b) {
        if (b == null) {
            return;
        }
        if (b.width <= 0 || b.height <= 0) {
            return;
        }
        float strength = strength();

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(255, 30, 30, (int) (40 + 120 * strength)));
        graphics.fillOval(b.x, b.y, b.width, b.height);
        graphics.setColor(new Color(255, 60, 60, (int) (120 + 135 * strength)));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawOval(b.x - 1, b.y - 1, b.width + 1, b.height + 1);
    }
}
