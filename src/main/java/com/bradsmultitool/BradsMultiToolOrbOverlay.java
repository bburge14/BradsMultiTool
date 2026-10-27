package com.bradsmultitool;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Pulses a red glow over the round part of the minimap hitpoints / prayer orb while that stat is below
 * its alert threshold.
 */
public class BradsMultiToolOrbOverlay extends Overlay {

    // Orb containers (number + circle) in each orb layout (InterfaceID.Orbs, OrbsNomap, OrbsOsm, OrbsOsmNomap)
    private static final int[] HEALTH_ORBS = {10485767, 58654725, 58785795, 58851332};
    private static final int[] PRAYER_ORBS = {10485778, 58654736, 58785806, 58851343};
    private static final long PULSE_MILLIS = 900;

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;

    @Inject
    public BradsMultiToolOrbOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        if (config.enableLowHealthAlert() && config.flashHealthOrb() && plugin.isLowHealthAlert()) {
            pulse(graphics, circleOf(visibleWidget(HEALTH_ORBS)));
        }
        if (config.enableLowPrayerAlert() && config.flashPrayerOrb()
            && (plugin.isLowPrayerAlert() || plugin.isZeroPrayerAlert())) {
            pulse(graphics, circleOf(visibleWidget(PRAYER_ORBS)));
        }
        return null;
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
        // 0 -> 1 -> 0 over each pulse
        double phase = (System.currentTimeMillis() % PULSE_MILLIS) / (double) PULSE_MILLIS;
        float strength = (float) (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI));

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(255, 30, 30, (int) (40 + 120 * strength)));
        graphics.fillOval(b.x, b.y, b.width, b.height);
        graphics.setColor(new Color(255, 60, 60, (int) (120 + 135 * strength)));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawOval(b.x - 1, b.y - 1, b.width + 1, b.height + 1);
    }
}
