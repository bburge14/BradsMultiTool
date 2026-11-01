package com.snuffys.skilltracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * The Brimhaven Agility Arena's obstacles and the quickest way to the tagged pillar. Every few ticks in the arena it
 * looks through the loaded scene for the ticket dispensers (one on each pillar) and the obstacles; each obstacle
 * links the two pillars it sits between. The route is the quickest chain of links from the pillar you're nearest to
 * the one the hint arrow points at, using only obstacles your Agility level allows, counting each one's time, the
 * walk across the pillar and the time a fall costs times how likely you are to fall. Only reads the scene, your
 * level and the hint arrow; it draws a route, like a map, and never clicks anything.
 */
@Singleton
class ArenaRoute {

    /** Green: you can do it and won't fail. Yellow: you can do it but may fail. Red: your level is too low. */
    enum Status { SAFE, MAY_FAIL, TOO_LOW }

    /** An obstacle between two pillars: the objects that make it up and what it is. */
    static final class Link {
        final WorldPoint a;
        final WorldPoint b;
        final SkillHelperData.ArenaObstacle obstacle;
        final List<TileObject> objects = new ArrayList<>();
        double cost;

        Link(WorldPoint a, WorldPoint b, SkillHelperData.ArenaObstacle obstacle) {
            this.a = a;
            this.b = b;
            this.obstacle = obstacle;
        }

        WorldPoint other(WorldPoint end) {
            return end.equals(a) ? b : a;
        }
    }

    // Rescan the scene this often (ticks) while in the arena
    private static final int SCAN_TICKS = 5;
    // Pillars sit 11 tiles apart; neighbours are this far apart along one line
    private static final int NEAR = 7;
    private static final int FAR = 15;
    private static final int OFF_LINE = 4;
    // An obstacle belongs to the pair of pillars whose middle is this close
    private static final double MIDDLE = 7;
    // Walking across a pillar between obstacles, a fall (back up the climbing rope), and darts' lowered level
    private static final int WALK_TICKS = 6;
    private static final int FALL_TICKS = 30;
    private static final int DART_FALL_TICKS = 70;
    // Obstacles that can fail where the wiki gives no chance: a guess
    private static final double UNKNOWN_FAIL = 0.15;

    private final Client client;
    private final SkillTrackerConfig config;
    private final SkillHelperData helpers;
    private final AgilityTracker agility;

    @Getter
    private final List<WorldPoint> pillars = new ArrayList<>();
    /** Every arena obstacle object in the scene, with what it is. */
    @Getter
    private final Map<TileObject, SkillHelperData.ArenaObstacle> obstacles = new LinkedHashMap<>();
    @Getter
    private final List<Link> links = new ArrayList<>();
    /** The quickest way to the tagged pillar, in order (empty when there's none, or you're on it). */
    @Getter
    private List<Link> route = Collections.emptyList();
    @Getter
    private WorldPoint start;
    @Getter
    private WorldPoint target;
    /** Obstacles not between two pillars (the readout counts them). */
    @Getter
    private int unlinked;
    private int lastScan = -SCAN_TICKS;

    @Inject
    ArenaRoute(Client client, SkillTrackerConfig config, SkillHelperData helpers, AgilityTracker agility) {
        this.client = client;
        this.config = config;
        this.helpers = helpers;
        this.agility = agility;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!agility.inArena() || !(config.arenaObstacleColours() || config.arenaRoute() || config.arenaDebug())) {
            clear();
            return;
        }
        int now = client.getTickCount();
        if (now - lastScan >= SCAN_TICKS || pillars.isEmpty()) {
            lastScan = now;
            scan();
        }
        findRoute();
    }

    private void clear() {
        pillars.clear();
        obstacles.clear();
        links.clear();
        route = Collections.emptyList();
        start = null;
        target = null;
        unlinked = 0;
    }

    // The dispensers and obstacles in the loaded scene, and the links between pillars
    private void scan() {
        clear();
        Scene scene = client.getTopLevelWorldView().getScene();
        Tile[][][] tiles = scene.getTiles();
        for (Tile[][] plane : tiles) {
            for (Tile[] column : plane) {
                for (Tile tile : column) {
                    if (tile == null) {
                        continue;
                    }
                    for (GameObject object : tile.getGameObjects()) {
                        add(object);
                    }
                    add(tile.getWallObject());
                    add(tile.getGroundObject());
                    add(tile.getDecorativeObject());
                }
            }
        }
        // Each obstacle object joins the pair of neighbouring pillars it sits between; a link is the obstacle most
        // of its objects are
        Map<String, Link> byPair = new HashMap<>();
        Map<String, Map<SkillHelperData.ArenaObstacle, Integer>> votes = new HashMap<>();
        for (Map.Entry<TileObject, SkillHelperData.ArenaObstacle> e : obstacles.entrySet()) {
            WorldPoint at = e.getKey().getWorldLocation();
            WorldPoint[] pair = pairFor(at);
            if (pair == null) {
                unlinked++;
                continue;
            }
            String key = pair[0] + "|" + pair[1];
            Link link = byPair.computeIfAbsent(key, k -> new Link(pair[0], pair[1], null));
            link.objects.add(e.getKey());
            votes.computeIfAbsent(key, k -> new HashMap<>()).merge(e.getValue(), 1, Integer::sum);
        }
        for (Map.Entry<String, Link> e : byPair.entrySet()) {
            SkillHelperData.ArenaObstacle most = Collections.max(votes.get(e.getKey()).entrySet(), Map.Entry.comparingByValue()).getKey();
            Link link = new Link(e.getValue().a, e.getValue().b, most);
            link.objects.addAll(e.getValue().objects);
            links.add(link);
        }
    }

    private void add(TileObject object) {
        if (object == null) {
            return;
        }
        int id = object.getId();
        if (helpers.isArenaDispenser(id)) {
            WorldPoint at = object.getWorldLocation();
            for (WorldPoint pillar : pillars) {
                if (pillar.distanceTo2D(at) <= 2) {
                    return;
                }
            }
            pillars.add(at);
            return;
        }
        SkillHelperData.ArenaObstacle obstacle = helpers.arenaObstacle(id);
        if (obstacle != null) {
            obstacles.put(object, obstacle);
        }
    }

    // The two neighbouring pillars this tile sits between, or null
    private WorldPoint[] pairFor(WorldPoint at) {
        WorldPoint[] best = null;
        double bestDistance = MIDDLE;
        for (int i = 0; i < pillars.size(); i++) {
            for (int j = i + 1; j < pillars.size(); j++) {
                WorldPoint p = pillars.get(i);
                WorldPoint q = pillars.get(j);
                if (!neighbours(p, q)) {
                    continue;
                }
                double mx = (p.getX() + q.getX()) / 2.0;
                double my = (p.getY() + q.getY()) / 2.0;
                double distance = Math.hypot(at.getX() - mx, at.getY() - my);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new WorldPoint[] {p, q};
                }
            }
        }
        return best;
    }

    static boolean neighbours(WorldPoint p, WorldPoint q) {
        int dx = Math.abs(p.getX() - q.getX());
        int dy = Math.abs(p.getY() - q.getY());
        return dx >= NEAR && dx <= FAR && dy <= OFF_LINE || dy >= NEAR && dy <= FAR && dx <= OFF_LINE;
    }

    /** How this obstacle is for you at your current (boosted) Agility level. */
    Status status(SkillHelperData.ArenaObstacle obstacle) {
        return status(obstacle, client.getBoostedSkillLevel(Skill.AGILITY));
    }

    static Status status(SkillHelperData.ArenaObstacle obstacle, int level) {
        if (level < obstacle.level) {
            return Status.TOO_LOW;
        }
        return obstacle.successChance(level) == 1 ? Status.SAFE : Status.MAY_FAIL;
    }

    /** Ticks this obstacle costs you on the way to the pillar, falls included; -1 if you can't do it. */
    static double cost(SkillHelperData.ArenaObstacle obstacle, int level) {
        if (level < obstacle.level) {
            return -1;
        }
        double chance = obstacle.successChance(level);
        double fail = chance < 0 ? UNKNOWN_FAIL : 1 - chance;
        int fall = obstacle.note != null && obstacle.note.contains("drains") ? DART_FALL_TICKS : FALL_TICKS;
        return obstacle.ticks + WALK_TICKS + fail * fall;
    }

    // Dijkstra from the pillar you're nearest to the tagged one
    private void findRoute() {
        route = Collections.emptyList();
        Player you = client.getLocalPlayer();
        WorldPoint hint = client.hasHintArrow() ? client.getHintArrowPoint() : null;
        start = you == null ? null : nearest(you.getWorldLocation());
        target = hint == null ? null : nearest(hint);
        int level = client.getBoostedSkillLevel(Skill.AGILITY);
        for (Link link : links) {
            link.cost = cost(link.obstacle, level);
        }
        if (start == null || target == null || start.equals(target) || !config.arenaRoute()) {
            return;
        }
        route = shortest(links, start, target);
    }

    /** The cheapest chain of links from one pillar to another (links that cost under 0 can't be used). */
    static List<Link> shortest(List<Link> links, WorldPoint from, WorldPoint to) {
        Map<WorldPoint, Double> distance = new HashMap<>();
        Map<WorldPoint, Link> via = new HashMap<>();
        List<WorldPoint> open = new ArrayList<>();
        distance.put(from, 0.0);
        open.add(from);
        while (!open.isEmpty()) {
            WorldPoint here = open.get(0);
            for (WorldPoint p : open) {
                if (distance.get(p) < distance.get(here)) {
                    here = p;
                }
            }
            open.remove(here);
            if (here.equals(to)) {
                break;
            }
            for (Link link : links) {
                if (link.cost < 0 || !link.a.equals(here) && !link.b.equals(here)) {
                    continue;
                }
                WorldPoint next = link.other(here);
                double d = distance.get(here) + link.cost;
                if (d < distance.getOrDefault(next, Double.MAX_VALUE)) {
                    if (!distance.containsKey(next)) {
                        open.add(next);
                    }
                    distance.put(next, d);
                    via.put(next, link);
                }
            }
        }
        if (!via.containsKey(to)) {
            return Collections.emptyList();
        }
        List<Link> path = new ArrayList<>();
        for (WorldPoint at = to; !at.equals(from); ) {
            Link link = via.get(at);
            path.add(0, link);
            at = link.other(at);
        }
        return path;
    }

    // The pillar nearest this tile, or null when none are loaded
    private WorldPoint nearest(WorldPoint at) {
        WorldPoint best = null;
        for (WorldPoint pillar : pillars) {
            if (best == null || pillar.distanceTo2D(at) < best.distanceTo2D(at)) {
                best = pillar;
            }
        }
        return best;
    }

    /** The best XP per tick among the obstacles you can do safely in the scene (0 if none). */
    double bestSafeXpPerTick() {
        int level = client.getBoostedSkillLevel(Skill.AGILITY);
        double best = 0;
        for (SkillHelperData.ArenaObstacle obstacle : obstacles.values()) {
            if (status(obstacle, level) == Status.SAFE) {
                best = Math.max(best, obstacle.xpPerTick());
            }
        }
        return best;
    }

    /** The route's total time in ticks (0 when there's no route). */
    double routeTicks() {
        double total = 0;
        for (Link link : route) {
            total += link.cost;
        }
        return total;
    }
}
