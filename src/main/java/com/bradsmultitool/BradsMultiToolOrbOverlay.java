package com.bradsmultitool;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Pulses a red glow over the minimap hitpoints / prayer orb while that stat is below its alert threshold.
 */
public class BradsMultiToolOrbOverlay extends Overlay {

    // Orb backing circles in each orb layout (InterfaceID.Orbs, OrbsNomap, OrbsOsm, OrbsOsmNomap)
    private static final int[] HEALTH_ORB_BACKINGS = {10485768, 58654726, 58785796, 58851333};
    private static final int[] PRAYER_ORB_BACKINGS = {10485779, 58654737, 58785807, 58851344};
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
            pulse(graphics, visibleWidget(HEALTH_ORB_BACKINGS));
        }
        if (config.enableLowPrayerAlert() && config.flashPrayerOrb()
            && (plugin.isLowPrayerAlert() || plugin.isZeroPrayerAlert())) {
            pulse(graphics, visibleWidget(PRAYER_ORB_BACKINGS));
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

    private static void pulse(Graphics2D graphics, Widget orb) {
        if (orb == null) {
            return;
        }
        Rectangle b = orb.getBounds();
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
