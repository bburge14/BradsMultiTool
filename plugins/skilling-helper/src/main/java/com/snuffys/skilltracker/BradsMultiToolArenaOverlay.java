package com.snuffys.skilltracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The Brimhaven Agility Arena on screen (see {@link ArenaRoute}): each obstacle outlined green (you can do it
 * safely), yellow (you can, but may fail) or red (your level is too low), and the quickest route to the tagged
 * pillar as a line across the pillars, its obstacles outlined thicker and the pillar's tile marked. Obstacles
 * RuneLite's Agility plugin already outlines (its Show Clickboxes) are left to it.
 */
@Singleton
class BradsMultiToolArenaOverlay extends Overlay {

    static final Color SAFE = new Color(50, 220, 50);
    static final Color MAY_FAIL = new Color(255, 230, 0);
    static final Color TOO_LOW = new Color(255, 70, 70);
    private static final Color ROUTE = new Color(0, 220, 255);

    private final Client client;
    private final SkillTrackerConfig config;
    private final ArenaRoute arena;
    private final AgilityTracker agility;
    private final SkillHelperData helpers;
    private final OtherTimerPlugins otherPlugins;

    @Inject
    BradsMultiToolArenaOverlay(Client client, SkillTrackerConfig config, ArenaRoute arena, AgilityTracker agility,
                               SkillHelperData helpers, OtherTimerPlugins otherPlugins) {
        this.client = client;
        this.config = config;
        this.arena = arena;
        this.agility = agility;
        this.helpers = helpers;
        this.otherPlugins = otherPlugins;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    static Color colour(ArenaRoute.Status status) {
        return status == ArenaRoute.Status.SAFE ? SAFE : status == ArenaRoute.Status.MAY_FAIL ? MAY_FAIL : TOO_LOW;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !agility.inArena()) {
            return null;
        }
        Set<TileObject> onRoute = new HashSet<>();
        for (ArenaRoute.Link link : arena.getRoute()) {
            onRoute.addAll(link.objects);
        }
        boolean theirs = otherPlugins.outlinesAgilityObstacles();
        for (Map.Entry<TileObject, SkillHelperData.ArenaObstacle> e : arena.getObstacles().entrySet()) {
            TileObject object = e.getKey();
            boolean route = onRoute.contains(object);
            // RuneLite's Agility plugin outlines the ones in its course list: leave those to it
            if (theirs && helpers.courseFor(object.getId()) != null) {
                continue;
            }
            if (!config.arenaObstacleColours() && !route) {
                continue;
            }
            Shape box = object.getClickbox();
            if (box == null) {
                continue;
            }
            Color color = config.arenaObstacleColours() ? colour(arena.status(e.getValue())) : ROUTE;
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), route ? 70 : 35));
            graphics.fill(box);
            graphics.setStroke(new BasicStroke(route ? 3f : 1.5f));
            graphics.setColor(color);
            graphics.draw(box);
        }
        if (config.arenaRoute()) {
            drawRoute(graphics);
        }
        return null;
    }

    // A line from your pillar through each obstacle's middle to the tagged pillar, whose tile is marked
    private void drawRoute(Graphics2D graphics) {
        WorldPoint at = arena.getStart();
        if (at == null || arena.getRoute().isEmpty()) {
            return;
        }
        Path2D path = new Path2D.Double();
        Point first = canvas(at);
        boolean started = false;
        if (first != null) {
            path.moveTo(first.getX(), first.getY());
            started = true;
        }
        for (ArenaRoute.Link link : arena.getRoute()) {
            WorldPoint next = link.other(at);
            for (Point p : new Point[] {middle(link), canvas(next)}) {
                if (p == null) {
                    continue;
                }
                if (started) {
                    path.lineTo(p.getX(), p.getY());
                } else {
                    path.moveTo(p.getX(), p.getY());
                    started = true;
                }
            }
            at = next;
        }
        graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(0, 0, 0, 120));
        graphics.draw(path);
        graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(ROUTE);
        graphics.draw(path);
        WorldPoint target = arena.getTarget();
        LocalPoint local = target == null ? null : LocalPoint.fromWorld(client.getTopLevelWorldView(), target);
        Polygon tile = local == null ? null : Perspective.getCanvasTilePoly(client, local);
        if (tile != null) {
            graphics.setColor(new Color(0, 220, 255, 60));
            graphics.fill(tile);
            graphics.setColor(ROUTE);
            graphics.draw(tile);
        }
    }

    // The middle of an obstacle's objects
    private Point middle(ArenaRoute.Link link) {
        double x = 0;
        double y = 0;
        for (TileObject object : link.objects) {
            x += object.getWorldLocation().getX();
            y += object.getWorldLocation().getY();
        }
        int n = link.objects.size();
        return n == 0 ? null : canvas(new WorldPoint((int) Math.round(x / n), (int) Math.round(y / n), link.a.getPlane()));
    }

    private Point canvas(WorldPoint point) {
        LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
        return local == null ? null : Perspective.localToCanvas(client, local, point.getPlane(), 0);
    }
}
