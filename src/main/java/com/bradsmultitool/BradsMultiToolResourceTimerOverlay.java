package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;

/**
 * Countdown pies over trees, Motherlode veins, calcified rocks, Daeyalt essence, shooting stars, fishing
 * spots and shipwrecks
 * (see {@link ResourceTimers}): green, then yellow, then red as each runs out, pulsing once it's due.
 */
public class BradsMultiToolResourceTimerOverlay extends Overlay {

    private static final int DIAMETER = 22;

    private final Client client;
    private final BradsMultiToolConfig config;
    private final ResourceTimers timers;
    private final ProgressPieComponent pie = new ProgressPieComponent();

    @Inject
    public BradsMultiToolResourceTimerOverlay(Client client, BradsMultiToolConfig config, ResourceTimers timers) {
        this.client = client;
        this.config = config;
        this.timers = timers;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        pie.setDiameter(DIAMETER);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        for (ResourceTimers.Timer timer : timers.all()) {
            if (!enabled(timer.kind) || !timer.visible()) {
                continue;
            }
            Point at = position(timer);
            if (at == null) {
                continue;
            }
            double left = timer.progress();
            Color color = left > 0.5 ? BradsMultiToolOverlay.GOOD : left > 0.2 ? BradsMultiToolOverlay.WARN : BradsMultiToolOverlay.BAD;
            // A trap goes green once something's caught, red if it sprang empty
            if (timer.kind == ResourceTimers.Kind.TRAP && timer.trapState != ResourceTimers.TrapState.SET) {
                color = timer.trapState == ResourceTimers.TrapState.CAUGHT ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD;
            }
            int alpha = 150;
            if (timer.due()) {
                alpha = (int) (90 + 110 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 120.0)));
            }
            pie.setPosition(at);
            pie.setProgress(left);
            pie.setFill(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            pie.setBorderColor(color);
            pie.render(graphics);
        }
        return null;
    }

    private boolean enabled(ResourceTimers.Kind kind) {
        switch (kind) {
            case TREE:
                return config.showTreeTimer();
            case FISHING_SPOT:
                return config.showFishingSpotTimer();
            case SHIPWRECK:
                return config.showShipwreckTimer();
            case TRAP:
                return config.showTrapTimer();
            case FIRE:
                return config.showFireTimer();
            default:
                return config.showMiningTimer();
        }
    }

    // Trees and rocks: the middle of the model as drawn (canopy included). Veins: a little up the wall.
    // Fishing spots: on the water.
    private Point position(ResourceTimers.Timer timer) {
        if (timer.npc != null) {
            LocalPoint local = timer.npc.getLocalLocation();
            return local == null ? null : Perspective.localToCanvas(client, local, client.getTopLevelWorldView().getPlane(), 0);
        }
        if (timer.object instanceof GameObject) {
            Shape hull = ((GameObject) timer.object).getConvexHull();
            if (hull != null) {
                Rectangle bounds = hull.getBounds();
                return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
            }
        }
        LocalPoint local = timer.object.getLocalLocation();
        return local == null ? null : Perspective.localToCanvas(client, local, client.getTopLevelWorldView().getPlane(), 150);
    }
}
