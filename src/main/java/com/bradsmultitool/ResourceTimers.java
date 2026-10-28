package com.bradsmultitool;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.Angle;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Countdown timers for resources that run out on a timer rather than per harvest, worked out the way the
 * Plugin Hub's dedicated timer plugins do (Tree Despawn Timer, MLM Despawn Timer, Calcified Rock Despawn
 * Timer, Daeyalt Mining Timer, Fishing Spot Timer):
 * <ul>
 * <li>Trees (Forestry): the wiki's timer starts with the first log, drops 1 a tick while anyone chops and
 * recovers 1 a tick while nobody does; at zero the tree falls with the next log. Every player chopping is
 * counted. A tree already being chopped when you arrived is left blank, since its time can't be known.</li>
 * <li>Motherlode Mine ore veins: deplete 23-27s (upper floor 36-40s) after mining starts; no recovery.</li>
 * <li>Calcified rocks: about 70s once mining starts; reset after ~34s with nobody mining.</li>
 * <li>Daeyalt essence: the active rock moves 92-110 ticks after it appears.</li>
 * <li>Fishing spots: move 250-530 ticks after they appear.</li>
 * </ul>
 * The game doesn't send any of these timers to the client, so all are estimates from what's visible.
 */
@Singleton
class ResourceTimers {

    enum Kind { TREE, VEIN, CALCIFIED, DAEYALT, FISHING_SPOT }

    /** One timed resource. Progress is 1 when fresh, 0 when it's due to go. */
    static final class Timer {
        final Kind kind;
        final TileObject object;
        final NPC npc;
        final List<WorldPoint> tiles = new ArrayList<>();
        final int maxTicks;
        /** Earliest it can go, for randomised timers (veins, Daeyalt, fishing spots); equals maxTicks otherwise. */
        final int minTicks;
        int ticksLeft;
        boolean started;
        boolean hidden;
        // Trees
        boolean youCutLog;
        int choppers;
        // Calcified rocks
        int unminedTicks;
        // Veins, Daeyalt, fishing spots
        int elapsed;

        Timer(Kind kind, TileObject object, NPC npc, int minTicks, int maxTicks) {
            this.kind = kind;
            this.object = object;
            this.npc = npc;
            this.minTicks = minTicks;
            this.maxTicks = maxTicks;
            this.ticksLeft = maxTicks;
        }

        double progress() {
            switch (kind) {
                case TREE:
                case CALCIFIED:
                    return Math.max(0, (double) ticksLeft / maxTicks);
                default:
                    return Math.max(0, 1 - (double) elapsed / maxTicks);
            }
        }

        /** Past its earliest time: it can go any moment (or, for a tree, with the next log). */
        boolean due() {
            return kind == Kind.TREE || kind == Kind.CALCIFIED ? ticksLeft <= 0 : elapsed >= minTicks;
        }

        boolean visible() {
            if (hidden) {
                return false;
            }
            switch (kind) {
                case TREE:
                    return choppers > 0 || ticksLeft < maxTicks;
                case VEIN:
                case CALCIFIED:
                    return started;
                default:
                    return true;
            }
        }
    }

    private static final double TICK_SECONDS = 0.6;
    // Motherlode Mine (MLM Despawn Timer): 23-27s downstairs, 36-40s upstairs; the upper floor sits below this height
    private static final Set<Integer> VEINS = Set.of(26661, 26662, 26663, 26664);
    private static final int UPPER_FLOOR_HEIGHT = -490;
    // Cam Torum calcified rocks (Calcified Rock Despawn Timer): ~70s, reset after 56 ticks unmined
    private static final Set<Integer> CALCIFIED_ROCKS = Set.of(51485, 51487, 51491);
    private static final int CALCIFIED_TICKS = (int) Math.round(70 / TICK_SECONDS);
    private static final int CALCIFIED_RESET_TICKS = 56;
    // Daeyalt essence mine (Daeyalt Mining Timer)
    private static final int DAEYALT_ACTIVE = 39095;
    // Fishing spots (OSRS Wiki, Fishing spots): most move after 250-530 ticks
    private static final int FISHING_MIN_TICKS = 250;
    private static final int FISHING_MAX_TICKS = 530;
    // Miscellania and Etceteria trees are managed by the kingdom, not Forestry timers
    private static final Set<Integer> NO_FORESTRY_REGIONS = Set.of(10044, 10300);
    // A player who appears already chopping (and hasn't moved) was chopping before we could see
    private static final int NEW_PLAYER_TICKS = 8;

    static final Set<Integer> CHOP_ANIMATIONS = animations("HUMAN_WOODCUTTING_", "FORESTRY_2H_AXE_CHOPPING_",
        "DRAGON_SMALLAXE_");
    static final Set<Integer> MINE_ANIMATIONS = animations("HUMAN_MINING_", "MINING_MOTHERLODE_");

    private final Client client;
    private final SkillingData data;

    private final Map<WorldPoint, Timer> byTile = new HashMap<>();
    private final Set<Timer> timers = new HashSet<>();
    private final Map<NPC, Timer> fishingSpots = new HashMap<>();
    private final Map<Player, Integer> playerSpawnTick = new HashMap<>();
    private final Map<Player, WorldPoint> playerSpawnPoint = new HashMap<>();

    @Inject
    ResourceTimers(Client client, SkillingData data) {
        this.client = client;
        this.data = data;
    }

    /** Every timer currently tracked (check {@link Timer#visible()}). */
    Collection<Timer> all() {
        return timers;
    }

    // Every animation constant with one of these prefixes, so new axes and pickaxes are picked up automatically
    private static Set<Integer> animations(String... prefixes) {
        Set<Integer> ids = new HashSet<>();
        for (Field field : AnimationID.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                continue;
            }
            for (String prefix : prefixes) {
                if (field.getName().startsWith(prefix)) {
                    try {
                        ids.add(field.getInt(null));
                    } catch (IllegalAccessException ignored) {
                        // public constants
                    }
                }
            }
        }
        return ids;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            clear();
        }
    }

    private void clear() {
        byTile.clear();
        timers.clear();
        fishingSpots.clear();
        playerSpawnTick.clear();
        playerSpawnPoint.clear();
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject object = event.getGameObject();
        int id = object.getId();
        if (CALCIFIED_ROCKS.contains(id)) {
            add(new Timer(Kind.CALCIFIED, object, null, CALCIFIED_TICKS, CALCIFIED_TICKS), object);
            return;
        }
        if (id == DAEYALT_ACTIVE) {
            // Only a fresh spawn has a known start; one already there when the area loaded doesn't
            if (client.getGameState() == GameState.LOGGED_IN) {
                add(new Timer(Kind.DAEYALT, object, null, 92, 110), object);
            }
            return;
        }
        if (NO_FORESTRY_REGIONS.contains(object.getWorldLocation().getRegionID())) {
            return;
        }
        SkillingData.Resource resource = data.object(id, objectName(id));
        if (resource != null && resource.chopTicks > 0 && byTile.get(object.getWorldLocation()) == null) {
            add(new Timer(Kind.TREE, object, null, resource.chopTicks, resource.chopTicks), object);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        remove(event.getGameObject());
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event) {
        WallObject wall = event.getWallObject();
        if (!VEINS.contains(wall.getId())) {
            return;
        }
        LocalPoint local = wall.getLocalLocation();
        boolean upper = local != null && Perspective.getTileHeight(client, local, 0) < UPPER_FLOOR_HEIGHT;
        int min = (int) Math.round((upper ? 36 : 23) / TICK_SECONDS);
        int max = (int) Math.round((upper ? 40 : 27) / TICK_SECONDS);
        add(new Timer(Kind.VEIN, wall, null, min, max), wall);
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned event) {
        remove(event.getWallObject());
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        // A spot appearing while you're in the area has just moved there; ones loaded with the area are unknown
        if (client.getGameState() == GameState.LOGGED_IN && isFishingSpot(npc)) {
            Timer timer = new Timer(Kind.FISHING_SPOT, null, npc, FISHING_MIN_TICKS, FISHING_MAX_TICKS);
            fishingSpots.put(npc, timer);
            timers.add(timer);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        Timer timer = fishingSpots.remove(event.getNpc());
        if (timer != null) {
            timers.remove(timer);
        }
    }

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned event) {
        Player player = event.getPlayer();
        if (player != client.getLocalPlayer()) {
            playerSpawnTick.put(player, client.getTickCount());
            playerSpawnPoint.put(player, player.getWorldLocation());
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        // Your first log starts a tree's timer (you can't see when others get theirs)
        if (event.getSkill() == Skill.WOODCUTTING) {
            Player local = client.getLocalPlayer();
            Timer tree = local != null && CHOP_ANIMATIONS.contains(local.getAnimation()) ? facing(local, Kind.TREE) : null;
            if (tree != null) {
                tree.youCutLog = true;
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        int now = client.getTickCount();
        Map<Timer, Integer> choppers = new HashMap<>();
        Map<Timer, Boolean> youChop = new HashMap<>();
        Set<Timer> mined = new HashSet<>();
        Player local = client.getLocalPlayer();

        for (Player player : client.getTopLevelWorldView().players()) {
            int animation = player.getAnimation();
            if (CHOP_ANIMATIONS.contains(animation)) {
                Timer tree = facing(player, Kind.TREE);
                if (tree == null) {
                    continue;
                }
                // Someone who showed up already chopping a tree nobody else is on: we missed the start
                if (player != local && isNewArrival(player, now) && tree.choppers == 0 && !choppers.containsKey(tree)) {
                    tree.hidden = true;
                }
                choppers.merge(tree, 1, Integer::sum);
                if (player == local) {
                    youChop.put(tree, true);
                }
            } else if (MINE_ANIMATIONS.contains(animation)) {
                Timer rock = facing(player, Kind.VEIN);
                if (rock == null) {
                    rock = facing(player, Kind.CALCIFIED);
                }
                if (rock != null) {
                    if (!rock.started && player != local && isNewArrival(player, now)) {
                        rock.hidden = true;
                    }
                    rock.started = true;
                    mined.add(rock);
                }
            }
        }
        playerSpawnTick.entrySet().removeIf(e -> now - e.getValue() > NEW_PLAYER_TICKS);
        playerSpawnPoint.keySet().retainAll(playerSpawnTick.keySet());

        for (Timer timer : timers) {
            switch (timer.kind) {
                case TREE:
                    tickTree(timer, choppers.getOrDefault(timer, 0), youChop.containsKey(timer));
                    break;
                case CALCIFIED:
                    tickCalcified(timer, mined.contains(timer));
                    break;
                case VEIN:
                    if (timer.started) {
                        timer.elapsed++;
                    }
                    break;
                default:
                    timer.elapsed++;
                    break;
            }
        }
    }

    // Tree Despawn Timer's rule: the timer runs while anyone chops, except while you're the only one
    // chopping and haven't had a log yet (it hasn't started); it recovers while nobody chops
    static void tickTree(Timer tree, int choppers, boolean youChop) {
        tree.choppers = choppers;
        if (choppers > 0) {
            if (choppers >= 2 || !youChop || tree.youCutLog) {
                tree.ticksLeft--;
            }
        } else if (tree.ticksLeft < tree.maxTicks) {
            tree.ticksLeft++;
        } else {
            tree.youCutLog = false;
            tree.hidden = false;
        }
    }

    static void tickCalcified(Timer rock, boolean minedThisTick) {
        if (!rock.started) {
            return;
        }
        if (minedThisTick) {
            rock.unminedTicks = 0;
        } else if (++rock.unminedTicks >= CALCIFIED_RESET_TICKS) {
            rock.ticksLeft = rock.maxTicks;
            rock.started = false;
            rock.hidden = false;
            rock.unminedTicks = 0;
            return;
        }
        if (rock.ticksLeft > 0) {
            rock.ticksLeft--;
        }
    }

    private boolean isNewArrival(Player player, int now) {
        Integer spawned = playerSpawnTick.get(player);
        return spawned != null && now - spawned <= NEW_PLAYER_TICKS
            && player.getWorldLocation().equals(playerSpawnPoint.get(player));
    }

    /**
     * The resource of this kind the player is working: the only one next to them, else the one they
     * face. Players chopping face a tree's south-west corner, so a tree just north or east also counts.
     */
    private Timer facing(Player player, Kind kind) {
        WorldPoint at = player.getWorldLocation();
        Timer only = null;
        int count = 0;
        for (Direction direction : Direction.values()) {
            Timer next = timerAt(step(at, direction), kind);
            if (next != null && next != only) {
                only = next;
                count++;
            }
        }
        if (count == 1) {
            return only;
        }
        Direction facing = new Angle(player.getOrientation()).getNearestDirection();
        Timer ahead = timerAt(step(at, facing), kind);
        if (ahead != null) {
            return ahead;
        }
        if (facing == Direction.SOUTH) {
            return timerAt(step(at, Direction.EAST), kind);
        }
        if (facing == Direction.WEST) {
            return timerAt(step(at, Direction.NORTH), kind);
        }
        return null;
    }

    private Timer timerAt(WorldPoint point, Kind kind) {
        Timer timer = byTile.get(point);
        return timer != null && timer.kind == kind ? timer : null;
    }

    private static WorldPoint step(WorldPoint point, Direction direction) {
        switch (direction) {
            case NORTH: return point.dy(1);
            case SOUTH: return point.dy(-1);
            case EAST: return point.dx(1);
            default: return point.dx(-1);
        }
    }

    private void add(Timer timer, TileObject object) {
        timer.tiles.addAll(tilesOf(object));
        for (WorldPoint tile : timer.tiles) {
            Timer old = byTile.put(tile, timer);
            if (old != null && old != timer) {
                timers.remove(old);
            }
        }
        timers.add(timer);
    }

    private void remove(TileObject object) {
        Timer timer = byTile.get(object.getWorldLocation());
        if (timer == null || timer.object == null || timer.object.getId() != object.getId()) {
            return;
        }
        for (WorldPoint tile : timer.tiles) {
            byTile.remove(tile, timer);
        }
        timers.remove(timer);
    }

    // Every tile a (possibly multi-tile) object covers
    private List<WorldPoint> tilesOf(TileObject object) {
        List<WorldPoint> tiles = new ArrayList<>();
        if (object instanceof GameObject) {
            GameObject game = (GameObject) object;
            WorldPoint min = WorldPoint.fromScene(client.getTopLevelWorldView(), game.getSceneMinLocation().getX(),
                game.getSceneMinLocation().getY(), game.getPlane());
            for (int dx = 0; dx < game.sizeX(); dx++) {
                for (int dy = 0; dy < game.sizeY(); dy++) {
                    tiles.add(min.dx(dx).dy(dy));
                }
            }
        } else {
            tiles.add(object.getWorldLocation());
        }
        return tiles;
    }

    private String objectName(int id) {
        ObjectComposition composition = client.getObjectDefinition(id);
        if (composition != null && composition.getImpostorIds() != null) {
            ObjectComposition impostor = composition.getImpostor();
            if (impostor != null) {
                composition = impostor;
            }
        }
        return composition != null ? composition.getName() : null;
    }

    private static boolean isFishingSpot(NPC npc) {
        NPCComposition composition = npc.getComposition();
        String name = npc.getName();
        if (composition == null || name == null || !name.toLowerCase().contains("fishing spot")) {
            return false;
        }
        for (String action : composition.getActions()) {
            if (action != null && SkillingData.isFishingOption(action)) {
                return true;
            }
        }
        return false;
    }
}
