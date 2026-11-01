package com.snuffys.skilltracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The Brimhaven Arena route (see {@link ArenaRoute}) on the minimap: a line from you through each obstacle to the
 * tagged pillar, and a dot on that pillar. Parts off the edge of the minimap aren't drawn.
 */
@Singleton
class BradsMultiToolArenaMinimapOverlay extends Overlay {

    private static final Color ROUTE = new Color(0, 220, 255);
    private static final int DOT = 5;

    private final Client client;
    private final SkillTrackerConfig config;
    private final ArenaRoute arena;
    private final AgilityTracker agility;

    @Inject
    BradsMultiToolArenaMinimapOverlay(Client client, SkillTrackerConfig config, ArenaRoute arena, AgilityTracker agility) {
        this.client = client;
        this.config = config;
        this.arena = arena;
        this.agility = agility;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Player you = client.getLocalPlayer();
        WorldPoint at = arena.getStart();
        if (client.getGameState() != GameState.LOGGED_IN || !config.arenaRouteMinimap() || !config.arenaRoute()
            || !agility.inArena() || you == null || at == null || arena.getRoute().isEmpty()) {
            return null;
        }
        // You, then each obstacle's middle and the pillar after it
        List<Point> points = new ArrayList<>();
        points.add(minimap(you.getWorldLocation()));
        for (ArenaRoute.Link link : arena.getRoute()) {
            double[] m = ArenaRoute.middle(link.objects);
            points.add(link.objects.isEmpty() ? null : minimap(new WorldPoint((int) Math.round(m[0]), (int) Math.round(m[1]), 0)));
            at = link.other(at);
            points.add(minimap(at));
        }
        for (int pass = 0; pass < 2; pass++) {
            graphics.setStroke(new BasicStroke(pass == 0 ? 3f : 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(pass == 0 ? new Color(0, 0, 0, 140) : ROUTE);
            for (int i = 1; i < points.size(); i++) {
                Point a = points.get(i - 1);
                Point b = points.get(i);
                if (a != null && b != null) {
                    graphics.drawLine(a.getX(), a.getY(), b.getX(), b.getY());
                }
            }
        }
        Point target = points.get(points.size() - 1);
        if (target != null) {
            graphics.setColor(Color.BLACK);
            graphics.fillOval(target.getX() - DOT / 2 - 1, target.getY() - DOT / 2 - 1, DOT + 2, DOT + 2);
            graphics.setColor(ROUTE);
            graphics.fillOval(target.getX() - DOT / 2, target.getY() - DOT / 2, DOT, DOT);
        }
        return null;
    }

    // Where a tile is on the minimap (on the plane you're on), or null when it's off the minimap
    private Point minimap(WorldPoint point) {
        int plane = client.getTopLevelWorldView().getPlane();
        LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), new WorldPoint(point.getX(), point.getY(), plane));
        return local == null ? null : Perspective.localToMinimap(client, local);
    }
}
