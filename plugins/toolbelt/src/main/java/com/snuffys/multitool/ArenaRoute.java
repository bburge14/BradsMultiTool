package com.snuffys.multitool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
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
 * The Brimhaven Agility Arena's obstacles and the quickest way to the tagged pillar. The pillars stand on a square
 * grid (from the wiki's ticket dispenser pins: not every pillar has a dispenser, so the grid, not the dispensers,
 * gives the pillars) and each obstacle sits midway between two neighbouring pillars. Every few ticks it looks
 * through the loaded scene for the obstacles and links each to the two pillars it sits between. Every tick the
 * route is worked out again, from the pillar nearest you to the one the hint arrow points at, using only obstacles
 * your Agility level allows, counting each one's time, the walk across the pillar and the time a fall costs times
 * how likely you are to fall. Only reads the scene, your level and the hint arrow; it draws a route, like a map,
 * and never clicks anything.
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
    // An obstacle belongs to the gap whose middle is this close (tiles)
    private static final double GAP = 4.5;
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
    private final Provider<ObstacleReps> reps;

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
    /** Obstacles not in a gap between two pillars (the readout counts them). */
    @Getter
    private int unlinked;
    // Plank pieces in the scene, by the id they show now (a piece can change what it shows)
    private final Map<TileObject, Integer> plankPieces = new LinkedHashMap<>();
    /** The plank ends of the smooth (safe) and broken planks, when the broken pieces tell them apart. */
    @Getter
    private final java.util.Set<TileObject> goodPlanks = new java.util.HashSet<>();
    @Getter
    private final java.util.Set<TileObject> badPlanks = new java.util.HashSet<>();
    private int lastScan = -SCAN_TICKS;

    @Inject
    ArenaRoute(Client client, SkillTrackerConfig config, SkillHelperData helpers, AgilityTracker agility,
               Provider<ObstacleReps> reps) {
        this.client = client;
        this.config = config;
        this.helpers = helpers;
        this.agility = agility;
        this.reps = reps;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!agility.inArena() || helpers.arenaGrid().length < 5 || !(config.arenaObstacleColours() || config.arenaRoute()
            || config.arenaDebug() || config.oneObstacleTraining())) {
            clear();
            return;
        }
        int now = client.getTickCount();
        if (now - lastScan >= SCAN_TICKS || obstacles.isEmpty()) {
            lastScan = now;
            scan();
        }
        findRoute();
    }

    private void clear() {
        plankPieces.clear();
        goodPlanks.clear();
        badPlanks.clear();
        obstacles.clear();
        links.clear();
        route = Collections.emptyList();
        start = null;
        target = null;
        unlinked = 0;
    }

    // The obstacles in the loaded scene, and the gaps between pillars they sit in
    private void scan() {
        plankPieces.clear();
        goodPlanks.clear();
        badPlanks.clear();
        obstacles.clear();
        links.clear();
        unlinked = 0;
        Scene scene = client.getTopLevelWorldView().getScene();
        for (Tile[][] plane : scene.getTiles()) {
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
        links.addAll(link(obstacles, helpers.arenaGrid()));
        for (Link link : links) {
            if (link.obstacle.name.equals("Plank")) {
                sortPlanks(link);
            }
        }
        for (TileObject object : obstacles.keySet()) {
            boolean linked = false;
            for (Link link : links) {
                linked |= link.objects.contains(object);
            }
            unlinked += linked ? 0 : 1;
        }
    }

    private void add(TileObject object) {
        if (object != null && helpers.isPlankPiece(object.getId())) {
            plankPieces.put(object, shownId(object.getId()));
        }
        if (object != null) {
            SkillHelperData.ArenaObstacle obstacle = helpers.arenaObstacle(object.getId());
            if (obstacle != null) {
                obstacles.put(object, obstacle);
            }
        }
    }

    // The id an object shows now (one that changes with the game's state shows another object)
    private int shownId(int id) {
        net.runelite.api.ObjectComposition composition = client.getObjectDefinition(id);
        if (composition != null && composition.getImpostorIds() != null) {
            net.runelite.api.ObjectComposition shown = composition.getImpostor();
            return shown != null ? shown.getId() : id;
        }
        return id;
    }

    // The three planks of a gap lie side by side; the two bad ones have broken pieces. Sorts the gap's plank ends
    // into the smooth one and the broken ones (only when some line has broken pieces and some doesn't)
    private void sortPlanks(Link link) {
        boolean alongX = link.a.getY() == link.b.getY();
        java.util.Set<Integer> brokenLines = new java.util.HashSet<>();
        java.util.Set<Integer> lines = new java.util.HashSet<>();
        for (Map.Entry<TileObject, Integer> e : plankPieces.entrySet()) {
            WorldPoint at = e.getKey().getWorldLocation();
            if (!between(at, link, alongX)) {
                continue;
            }
            int line = alongX ? at.getY() : at.getX();
            lines.add(line);
            if (helpers.isBrokenPlank(e.getValue())) {
                brokenLines.add(line);
            }
        }
        if (brokenLines.isEmpty() || brokenLines.size() == lines.size()) {
            return;
        }
        for (TileObject end : link.objects) {
            int line = alongX ? end.getWorldLocation().getY() : end.getWorldLocation().getX();
            (brokenLines.contains(line) ? badPlanks : goodPlanks).add(end);
        }
    }

    // Whether this tile is in the gap between the link's two pillars (along it, and near its line)
    private static boolean between(WorldPoint at, Link link, boolean alongX) {
        int lo = alongX ? Math.min(link.a.getX(), link.b.getX()) : Math.min(link.a.getY(), link.b.getY());
        int hi = alongX ? Math.max(link.a.getX(), link.b.getX()) : Math.max(link.a.getY(), link.b.getY());
        int along = alongX ? at.getX() : at.getY();
        int across = alongX ? at.getY() - link.a.getY() : at.getX() - link.a.getX();
        return along > lo && along < hi && Math.abs(across) <= 4;
    }

    /** What the piece shows now and whether it's broken, for the readout: "3573 3576x" per plank line. */
    String plankReadout() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<TileObject, Integer> e : plankPieces.entrySet()) {
            if (out.length() < 60) {
                out.append(e.getValue()).append(helpers.isBrokenPlank(e.getValue()) ? "x " : " ");
            }
        }
        return out.length() == 0 ? "-" : out.toString().trim();
    }

    /** This object's status for you: a known smooth plank is safe, a known broken one is red. */
    Status status(TileObject object, SkillHelperData.ArenaObstacle obstacle) {
        if (goodPlanks.contains(object)) {
            return Status.SAFE;
        }
        if (badPlanks.contains(object)) {
            return Status.TOO_LOW;
        }
        return status(obstacle);
    }

    /** The arena obstacle object at this tile with this id, or null. */
    TileObject objectAt(int id, WorldPoint at) {
        for (TileObject object : obstacles.keySet()) {
            if (object.getId() == id && object.getWorldLocation().getX() == at.getX() && object.getWorldLocation().getY() == at.getY()) {
                return object;
            }
        }
        return null;
    }

    /**
     * Links each obstacle object to the gap between the two neighbouring pillars it sits in; a gap's obstacle is
     * the one most of its objects are.
     */
    static List<Link> link(Map<TileObject, SkillHelperData.ArenaObstacle> obstacles, int[] grid) {
        Map<String, WorldPoint[]> ends = new LinkedHashMap<>();
        Map<String, List<TileObject>> objects = new HashMap<>();
        Map<String, Map<SkillHelperData.ArenaObstacle, Integer>> votes = new HashMap<>();
        for (Map.Entry<TileObject, SkillHelperData.ArenaObstacle> e : obstacles.entrySet()) {
            WorldPoint[] gap = gap(e.getKey().getWorldLocation(), grid);
            if (gap == null) {
                continue;
            }
            String key = gap[0] + "|" + gap[1];
            ends.putIfAbsent(key, gap);
            objects.computeIfAbsent(key, k -> new ArrayList<>()).add(e.getKey());
            votes.computeIfAbsent(key, k -> new HashMap<>()).merge(e.getValue(), 1, Integer::sum);
        }
        List<Link> links = new ArrayList<>();
        for (Map.Entry<String, WorldPoint[]> e : ends.entrySet()) {
            SkillHelperData.ArenaObstacle most = Collections.max(votes.get(e.getKey()).entrySet(), Map.Entry.comparingByValue()).getKey();
            Link link = new Link(e.getValue()[0], e.getValue()[1], most);
            link.objects.addAll(objects.get(e.getKey()));
            links.add(link);
        }
        return links;
    }

    /** The two neighbouring pillars whose gap this tile is in, or null. */
    static WorldPoint[] gap(WorldPoint at, int[] grid) {
        int x0 = grid[0];
        int y0 = grid[1];
        int step = grid[2];
        double fx = (at.getX() - x0) / (double) step;
        double fy = (at.getY() - y0) / (double) step;
        WorldPoint[] best = null;
        double bestDistance = GAP;
        // An east-west gap (between columns i and i+1 on row j), then a north-south one
        for (int horizontal = 0; horizontal < 2; horizontal++) {
            int i = horizontal == 0 ? (int) Math.floor(fx) : (int) Math.round(fx);
            int j = horizontal == 0 ? (int) Math.round(fy) : (int) Math.floor(fy);
            int i2 = horizontal == 0 ? i + 1 : i;
            int j2 = horizontal == 0 ? j : j + 1;
            if (i < 0 || j < 0 || i2 >= grid[3] || j2 >= grid[4]) {
                continue;
            }
            double mx = x0 + (i + i2) * step / 2.0;
            double my = y0 + (j + j2) * step / 2.0;
            double distance = Math.hypot(at.getX() - mx, at.getY() - my);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new WorldPoint[] {pillar(grid, i, j), pillar(grid, i2, j2)};
            }
        }
        return best;
    }

    // Pillars are kept on plane 0 so they compare by x/y (you, the hint arrow and obstacles can be on other planes)
    private static WorldPoint pillar(int[] grid, int i, int j) {
        return new WorldPoint(grid[0] + i * grid[2], grid[1] + j * grid[2], 0);
    }

    /** The pillar nearest this tile (the grid's pillars all count, dispenser or not). */
    static WorldPoint nearestPillar(WorldPoint at, int[] grid) {
        int i = Math.max(0, Math.min(grid[3] - 1, (int) Math.round((at.getX() - grid[0]) / (double) grid[2])));
        int j = Math.max(0, Math.min(grid[4] - 1, (int) Math.round((at.getY() - grid[1]) / (double) grid[2])));
        return pillar(grid, i, j);
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

    // Every tick: Dijkstra from the pillar nearest you to the tagged one
    private void findRoute() {
        route = Collections.emptyList();
        int[] grid = helpers.arenaGrid();
        Player you = client.getLocalPlayer();
        WorldPoint hint = client.hasHintArrow() ? client.getHintArrowPoint() : null;
        start = you == null ? null : nearestPillar(you.getWorldLocation(), grid);
        target = hint == null ? null : nearestPillar(hint, grid);
        int level = client.getBoostedSkillLevel(Skill.AGILITY);
        for (Link link : links) {
            link.cost = cost(link.obstacle, level);
            // A gap whose smooth plank is known: no fall to count
            if (link.cost >= 0 && link.objects.stream().anyMatch(goodPlanks::contains)) {
                link.cost = link.obstacle.ticks + WALK_TICKS;
            }
        }
        if (start == null || target == null || !config.arenaRoute() || reps.get().trainingOne()) {
            return;
        }
        if (!start.equals(target)) {
            route = shortest(links, start, target);
        }
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

    static double[] middle(List<TileObject> objects) {
        double x = 0;
        double y = 0;
        for (TileObject object : objects) {
            x += object.getWorldLocation().getX();
            y += object.getWorldLocation().getY();
        }
        int n = Math.max(1, objects.size());
        return new double[] {x / n, y / n};
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

    /** Why there's no route right now, or null when there is one (shown on the arena card). */
    String whyNoRoute() {
        if (!route.isEmpty()) {
            return null;
        }
        if (target == null) {
            return "no pillar arrow yet";
        }
        if (reps.get().trainingOne()) {
            return "hidden (training)";
        }
        if (start == null) {
            return "finding you";
        }
        if (start.equals(target)) {
            return "you're at the pillar";
        }
        if (links.isEmpty()) {
            return "no obstacles loaded";
        }
        return "no way there at your level";
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
