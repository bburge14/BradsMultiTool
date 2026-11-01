package com.snuffys.skilltracker;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * One-obstacle training: crossing the same obstacle over and over (the Brimhaven Arena's floor spikes back and
 * forth, or an Agility shortcut anywhere) instead of running a course. Each Agility XP drop counts as a rep of the
 * obstacle or shortcut next to you; a few in a row of the same one and it's training, with reps, rates and reps to
 * your level and goal, until you stop for a while or move to another. Only reads your XP and the objects near you.
 */
@Singleton
class ObstacleReps {

    // An XP drop counts as a rep of the obstacle this close to you (tiles); in the arena, drops bigger than any
    // obstacle gives (Karamja gloves included) are a pillar's tag
    private static final int NEAR = 4;
    private static final int MAX_ARENA_OBSTACLE_XP = 40;
    // It's training after this many reps in a row, and ends after this long without one (ticks)
    static final int TRAINING_REPS = 3;
    private static final int IDLE_TICKS = 200;

    private final Client client;
    private final SkillTrackerConfig config;
    private final SkillHelperData helpers;
    private final AgilityTracker agility;
    private final ArenaRoute arena;

    /** What you're crossing: the obstacle or shortcut's name. */
    @Getter
    private String name;
    /** The object you last crossed (to outline it). */
    @Getter
    private TileObject object;
    @Getter
    private int reps;
    @Getter
    private double repXp;
    private int id = -1;
    private int firstRepTick = -1;
    private int lastRepTick = -1;
    private int lastXp = -1;

    @Inject
    ObstacleReps(Client client, SkillTrackerConfig config, SkillHelperData helpers, AgilityTracker agility, ArenaRoute arena) {
        this.client = client;
        this.config = config;
        this.helpers = helpers;
        this.agility = agility;
        this.arena = arena;
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() != Skill.AGILITY) {
            return;
        }
        int xp = event.getXp();
        int gain = lastXp < 0 ? 0 : xp - lastXp;
        lastXp = xp;
        Player you = client.getLocalPlayer();
        if (gain <= 0 || you == null || !config.oneObstacleTraining() || agility.courseActive()) {
            return;
        }
        WorldPoint at = you.getWorldLocation();
        TileObject crossed;
        String crossedName;
        if (agility.inArena()) {
            if (gain > MAX_ARENA_OBSTACLE_XP) {
                return;
            }
            crossed = nearestArenaObstacle(at);
            crossedName = crossed == null ? null : arena.getObstacles().get(crossed).name;
        } else {
            crossed = nearestShortcut(at);
            crossedName = crossed == null ? null : helpers.shortcut(crossed.getId()).name;
        }
        if (crossed == null) {
            return;
        }
        rep(crossed, crossedName, gain, client.getTickCount());
    }

    // Counts a rep: the same obstacle again carries on, another one (or a long break) starts over
    void rep(TileObject crossed, String crossedName, int gain, int now) {
        if (!crossedName.equals(name) || now - lastRepTick > IDLE_TICKS) {
            name = crossedName;
            reps = 0;
            repXp = 0;
            firstRepTick = now;
        }
        id = crossed.getId();
        object = crossed;
        repXp = (repXp * reps + gain) / (reps + 1);
        reps++;
        lastRepTick = now;
    }

    private TileObject nearestArenaObstacle(WorldPoint at) {
        TileObject best = null;
        int bestDistance = NEAR + 1;
        for (Map.Entry<TileObject, SkillHelperData.ArenaObstacle> e : arena.getObstacles().entrySet()) {
            int distance = e.getKey().getWorldLocation().distanceTo2D(at);
            if (distance < bestDistance && e.getValue().xp > 0) {
                bestDistance = distance;
                best = e.getKey();
            }
        }
        return best;
    }

    // The Agility shortcut nearest you, looking only at the tiles around you
    private TileObject nearestShortcut(WorldPoint at) {
        LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), at);
        if (local == null) {
            return null;
        }
        Tile[][] tiles = client.getTopLevelWorldView().getScene().getTiles()[client.getTopLevelWorldView().getPlane()];
        int sx = local.getSceneX();
        int sy = local.getSceneY();
        TileObject best = null;
        int bestDistance = NEAR + 1;
        for (int x = Math.max(0, sx - NEAR); x <= Math.min(tiles.length - 1, sx + NEAR); x++) {
            for (int y = Math.max(0, sy - NEAR); y <= Math.min(tiles[x].length - 1, sy + NEAR); y++) {
                Tile tile = tiles[x][y];
                if (tile == null) {
                    continue;
                }
                for (TileObject object : objects(tile)) {
                    if (object != null && helpers.shortcut(object.getId()) != null) {
                        int distance = object.getWorldLocation().distanceTo2D(at);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = object;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static TileObject[] objects(Tile tile) {
        GameObject[] games = tile.getGameObjects();
        TileObject[] out = new TileObject[games.length + 3];
        System.arraycopy(games, 0, out, 0, games.length);
        out[games.length] = tile.getWallObject();
        out[games.length + 1] = tile.getGroundObject();
        out[games.length + 2] = tile.getDecorativeObject();
        return out;
    }

    /** Whether you're crossing one obstacle over and over (a few reps in a row, the last one lately). */
    boolean trainingOne() {
        return config.oneObstacleTraining() && name != null && reps >= TRAINING_REPS
            && client.getTickCount() - lastRepTick <= IDLE_TICKS;
    }

    /** Whether this object is one you're training on. */
    boolean isTraining(TileObject candidate) {
        return trainingOne() && candidate != null && (candidate == object || candidate.getId() == id
            && object != null && candidate.getWorldLocation().distanceTo2D(object.getWorldLocation()) <= NEAR);
    }

    /** Reps per hour (0 until there are two). */
    double repsPerHour() {
        int span = lastRepTick - firstRepTick;
        return reps < 2 || span <= 0 ? 0 : (reps - 1) * 6000.0 / span;
    }

    /** Reps to this much XP (0 if unknown or there). */
    int repsTo(int targetXp) {
        int xp = client.getSkillExperience(Skill.AGILITY);
        return repXp <= 0 || targetXp <= xp ? 0 : (int) Math.ceil((targetXp - xp) / repXp);
    }
}
