package com.snuffys.multitool;

import com.google.common.collect.ImmutableSet;
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
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

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
 * <li>Fishing spots (Fishing Spot Tracker): most move 250-530 ticks after they appear; aerial fishing
 * 10-19, minnows 12-25, sacred/infernal eels 100-300, anglerfish 8-830; karambwan and Tempoross spots never
 * move. A spot's timer is remembered for 15 minutes if you walk out of range and back.</li>
 * <li>Shooting stars (Star AFK Timer): the star has a hidden health bar for its current layer, so this one
 * is exact.</li>
 * <li>Sailing shipwrecks (Salvaging AFK Timer): each type lasts a set time from when it surfaces.</li>
 * <li>Hunter traps you lay (RuneLite's Hunter plugin): collapse a minute after being laid.</li>
 * <li>Fires: burn out 100-200 ticks after being lit (Fire Timer). Shown only on a fire you cook on, counting
 * down to the earliest it can go out (100 ticks), then "any moment" like a tree.</li>
 * </ul>
 * Apart from stars, the game doesn't send these timers to the client, so they're estimates from what's
 * visible.
 */
@Singleton
class ResourceTimers {

    enum Kind { TREE, VEIN, CALCIFIED, DAEYALT, FISHING_SPOT, STAR, SHIPWRECK, TRAP, FIRE, CAMPFIRE }

    enum TrapState { SET, CAUGHT, FAILED }

    /** One timed resource. Progress is 1 when fresh, 0 when it's due to go. */
    static final class Timer {
        final Kind kind;
        /** The resource as currently loaded; swapped for the new copy when the area reloads. */
        TileObject object;
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
        // Veins, Daeyalt, fishing spots, shipwrecks
        int elapsed;
        // Stars: the layer's health, 0-1 (-1 until the star's health bar has been seen)
        double health = -1;
        // Hunter traps
        TrapState trapState = TrapState.SET;
        // Fires: you've cooked on it
        boolean cookedOn;
        // Fishing spots: where it was last tick, to see it jump to a new tile without despawning
        WorldPoint spotAt;

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
                case CAMPFIRE:
                    return Math.max(0, (double) ticksLeft / maxTicks);
                case STAR:
                    return Math.max(0, health);
                case FIRE:
                    // To the earliest it can go out; after that it can any moment (due)
                    return Math.max(0, 1 - (double) elapsed / minTicks);
                default:
                    return Math.max(0, 1 - (double) elapsed / maxTicks);
            }
        }

        /**
         * Fishing spots and fires: the game picks a random time between minTicks and maxTicks (fishing spots
         * 250-530 ticks, per Mod Ash), so all that can be shown is that window, not a countdown.
         */
        boolean windowed() {
            return (kind == Kind.FISHING_SPOT || kind == Kind.FIRE) && minTicks < maxTicks;
        }

        /** Ticks until it can first go (0 once it can). */
        int ticksToEarliest() {
            return Math.max(0, minTicks - elapsed);
        }

        /** Ticks until it's gone at the latest. */
        int ticksToLatest() {
            return Math.max(0, maxTicks - elapsed);
        }

        /** How far through its window it is: 0 at the earliest time, 1 at the latest (its chance of being gone). */
        double windowPassed() {
            return windowPassed(elapsed, minTicks, maxTicks);
        }

        static double windowPassed(int elapsed, int min, int max) {
            return max <= min ? (elapsed >= max ? 1 : 0) : Math.max(0, Math.min(1, (double) (elapsed - min) / (max - min)));
        }

        /** Past its earliest time: it can go any moment (or, for a tree, with the next log). */
        boolean due() {
            switch (kind) {
                case TREE:
                case CALCIFIED:
                case CAMPFIRE:
                    return ticksLeft <= 0;
                case STAR:
                    return health >= 0 && health < 0.1;
                default:
                    return elapsed >= minTicks;
            }
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
                case CAMPFIRE:
                    return started;
                case STAR:
                    return health >= 0;
                case FIRE:
                    return cookedOn;
                default:
                    return true;
            }
        }
    }

    private static final double TICK_SECONDS = 0.6;
    // Forester's Campfires (OSRS Wiki, Forester's Campfire): burn up to 300 ticks; the log that turns a fire
    // into one sets its time, and each log added after adds more. By the log's Firemaking XP:
    // {xp, ticks when it starts the campfire, ticks it adds}; the wiki's unknowns are filled in between
    private static final Set<Integer> CAMPFIRES = Set.of(49927, 49928, 49929, 49930, 49931, 49932);
    private static final int CAMPFIRE_MAX_TICKS = 300;
    private static final int CAMPFIRE_DEFAULT_TICKS = 110;
    private static final double[][] CAMPFIRE_LOGS = {
        {40, 102, 3}, {60, 109, 10}, {90, 116, 17}, {96, 131, 32}, {105, 118, 19}, {125, 121, 22},
        {135, 123, 24}, {157.5, 125, 26}, {180, 128, 29}, {202.5, 130, 31}, {220.5, 134, 35}, {268, 140, 40},
        {303.8, 137, 38}, {350, 144, 45}};
    // "Check" on a campfire: its message and the time left it means, in ticks {min, max}
    private static final Map<String, int[]> CAMPFIRE_CHECKS = Map.of(
        "The embers glow softly.", new int[]{1, 58},
        "The flames flicker gently.", new int[]{59, 118},
        "The fire burns steadily.", new int[]{119, 178},
        "The fire burns brightly.", new int[]{179, 238},
        "The roaring fire crackles invitingly.", new int[]{239, 299});
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
    // Spots with other timings, by NPC id: {min, max}; 0 = never moves
    private static final Map<Integer, int[]> FISHING_TIMINGS = new HashMap<>();
    private static final int FISHING_MEMORY_TICKS = 1500;
    private static final int OUT_OF_VIEW_DISTANCE = 14;
    /** A spot that appears this far away or more has (most likely) just come into view, not moved there. */
    private static final int INTO_VIEW_DISTANCE = 12;
    private static final int FIRE = ObjectID.FIRE;
    private static final int TRAP_TICKS = 100;
    // Hunter traps as laid (from RuneLite's Hunter plugin; crab traps never expire so aren't timed)
    private static final Set<Integer> TRAPS_SET = ImmutableSet.of(
        ObjectID.HUNTING_DEADFALL_TRAP, ObjectID.HUNTING_MONKEYTRAP_SET, ObjectID.HUNTING_IMPTRAP_EMPTY,
        ObjectID.HUNTING_BOXTRAP_EMPTY, ObjectID.HUNTING_OJIBWAY_TRAP, ObjectID.HUNTING_SAPLING_NET_SET_SWAMP,
        ObjectID.HUNTING_SAPLING_NET_SET_ORANGE, ObjectID.HUNTING_SAPLING_NET_SET_RED,
        ObjectID.HUNTING_SAPLING_NET_SET_BLACK, ObjectID.HUNTING_SAPLING_NET_SET_MOUNTAIN);
    // Something caught
    private static final Set<Integer> TRAPS_CAUGHT = ImmutableSet.of(
        ObjectID.HUNTING_IMPTRAP_FULL, ObjectID.HUNTING_BOXTRAP_FULL_CHINCHOMPA_BLACK,
        ObjectID.HUNTING_BOXTRAP_FULL_CHINCHOMPA, ObjectID.HUNTING_BOXTRAP_FULL_CHINCHOMPA_BIG,
        ObjectID.HUNTING_BOXTRAP_FULL_FERRET, ObjectID.HUNTING_BOXTRAP_FULL_JERBOA,
        ObjectID.HUNTING_BOXTRAP_FULL_LETVEK, ObjectID.HUNTING_DEADFALL_FULL_SPIKE,
        ObjectID.HUNTING_DEADFALL_FULL_SABRE, ObjectID.HUNTING_DEADFALL_FULL_BARBED,
        ObjectID.HUNTING_DEADFALL_FULL_CLAW, ObjectID.HUNTING_DEADFALL_FULL_FENNEC,
        ObjectID.HUNTING_OJIBWAY_TRAP_FULL_JUNGLE, ObjectID.HUNTING_OJIBWAY_TRAP_FULL_POLAR,
        ObjectID.HUNTING_OJIBWAY_TRAP_FULL_DESERT, ObjectID.HUNTING_OJIBWAY_TRAP_FULL_WOODLAND,
        ObjectID.HUNTING_OJIBWAY_TRAP_FULL_COLOURED, ObjectID.HUNTING_SAPLING_FULL_GREEN,
        ObjectID.HUNTING_SAPLING_FULL_RED, ObjectID.HUNTING_SAPLING_FULL_ORANGE, ObjectID.HUNTING_SAPLING_FULL_BLACK,
        ObjectID.HUNTING_SAPLING_FULL_MOUNTAIN, ObjectID.HUNTING_MONKEYTRAP_FULL_0, ObjectID.HUNTING_MONKEYTRAP_FULL_1);
    // Sprung with nothing caught
    private static final Set<Integer> TRAPS_FAILED = ImmutableSet.of(
        ObjectID.HUNTING_IMPTRAP_FAILED, ObjectID.HUNTING_BOXTRAP_FAILED, ObjectID.HUNTING_OJIBWAY_TRAP_BROKEN,
        ObjectID.HUNTING_DEADFALL_BOULDER, ObjectID.HUNTING_SAPLING_FAILED_MOUNTAIN);
    // Mid-catch (the animal is going in)
    private static final Set<Integer> TRAPS_TRIGGERING = ImmutableSet.of(
        ObjectID.HUNTING_IMPTRAP_TRAPPING, ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BLACK_N,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BLACK_E, ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BLACK_S,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BLACK_W, ObjectID.HUNTING_BOXTRAP_FAILING,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BIG_N, ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BIG_E,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BIG_S, ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BIG_W,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_N, ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_E,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_S, ObjectID.HUNTING_BOXTRAP_TRAPPING_FERRET_N,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_FERRET_S, ObjectID.HUNTING_BOXTRAP_TRAPPING_FERRET_W,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_JERBOA_N, ObjectID.HUNTING_BOXTRAP_TRAPPING_JERBOA_E,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_JERBOA_S, ObjectID.HUNTING_BOXTRAP_TRAPPING_JERBOA_W,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_LETVEK_N, ObjectID.HUNTING_BOXTRAP_TRAPPING_LETVEK_E,
        ObjectID.HUNTING_BOXTRAP_TRAPPING_LETVEK_S, ObjectID.HUNTING_BOXTRAP_TRAPPING_LETVEK_W,
        ObjectID.HUNTING_OJIBWAY_TRAP_FAILING, ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_COLOURED,
        ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_JUNGLE, ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_POLAR,
        ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_DESERT, ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_WOODLAND,
        ObjectID.HUNTING_DEADFALL_TRAPPING_SPIKE, ObjectID.HUNTING_DEADFALL_TRAPPING_SABRE,
        ObjectID.HUNTING_DEADFALL_TRAPPING_SABRE_M, ObjectID.HUNTING_DEADFALL_TRAPPING_BARBED,
        ObjectID.HUNTING_DEADFALL_TRAPPING_BARBED_M, ObjectID.HUNTING_DEADFALL_TRAPPING_CLAW,
        ObjectID.HUNTING_DEADFALL_TRAPPING_FENNEC, ObjectID.HUNTING_DEADFALL_TRAPPING_FENNEC_M,
        ObjectID.HUNTING_SAPLING_CATCHING_GREEN, ObjectID.HUNTING_SAPLING_FAILING_SWAMP,
        ObjectID.HUNTING_SAPLING_CATCHING_ORANGE, ObjectID.HUNTING_SAPLING_FAILING_ORANGE,
        ObjectID.HUNTING_SAPLING_CATCHING_RED, ObjectID.HUNTING_SAPLING_FAILING_RED,
        ObjectID.HUNTING_SAPLING_CATCHING_BLACK, ObjectID.HUNTING_SAPLING_FAILING_BLACK,
        ObjectID.HUNTING_SAPLING_CATCHING_MOUNTAIN, ObjectID.HUNTING_SAPLING_FAILING_MOUNTAIN,
        ObjectID.HUNTING_MONKEYTRAP_TRAPPING_0, ObjectID.HUNTING_MONKEYTRAP_TRAPPING_1);
    // Shooting stars: one object per tier, plus the hidden NPC carrying the layer's health bar
    private static final Set<Integer> STARS = Set.of(41020, 41021, 41223, 41224, 41225, 41226, 41227, 41228, 41229);
    private static final int STAR_HEALTH_NPC = 10629;
    // Sailing shipwrecks: active id -> {stump id, average lifetime in seconds} (Salvaging AFK Timer)
    private static final Map<Integer, int[]> SHIPWRECKS = new HashMap<>();

    static {
        int[] never = {0, 0};
        int[] aerial = {10, 19};
        int[] minnow = {12, 25};
        int[] eel = {100, 300};
        FISHING_TIMINGS.put(8523, aerial);
        for (int id : new int[]{7730, 7731, 7732, 7733}) {
            FISHING_TIMINGS.put(id, minnow);
        }
        FISHING_TIMINGS.put(6488, eel);   // sacred eel
        FISHING_TIMINGS.put(7676, eel);   // infernal eel
        FISHING_TIMINGS.put(6825, new int[]{8, 830}); // anglerfish
        for (int id : new int[]{4710, 4712, 4713, 10565, 10568, 10569}) {
            FISHING_TIMINGS.put(id, never); // karambwan(ji), Tempoross
        }
        int[][] wrecks = {
            {60464, 60465, 60}, {60466, 60467, 180}, {60468, 60469, 180}, {60470, 60471, 180},
            {60472, 60473, 180}, {60474, 60475, 195}, {60476, 60477, 225}, {60478, 60479, 240}};
        for (int[] wreck : wrecks) {
            SHIPWRECKS.put(wreck[0], new int[]{wreck[1], wreck[2]});
        }
    }
    // Miscellania and Etceteria trees are managed by the kingdom, not Forestry timers
    private static final Set<Integer> NO_FORESTRY_REGIONS = Set.of(10044, 10300);
    // A player who appears already chopping (and hasn't moved) was chopping before we could see
    private static final int NEW_PLAYER_TICKS = 8;

    // Every axe's and pickaxe's animation (listed out: Plugin Hub plugins can't use reflection)
    static final Set<Integer> CHOP_ANIMATIONS = ImmutableSet.of(
        AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL, AnimationID.HUMAN_WOODCUTTING_RUNE_AXE,
        AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE, AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE,
        AnimationID.HUMAN_WOODCUTTING_BLACK_AXE, AnimationID.HUMAN_WOODCUTTING_STEEL_AXE,
        AnimationID.HUMAN_WOODCUTTING_IRON_AXE, AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE,
        AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE, AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE,
        AnimationID.DRAGON_SMALLAXE_ANIM, AnimationID.DRAGON_SMALLAXE_SWOOSH,
        AnimationID.HUMAN_WOODCUTTING_BLESSED_AXE, AnimationID.HUMAN_WOODCUTTING_BLESSED_AXE_MISS,
        AnimationID.HUMAN_WOODCUTTING_3A_AXE, AnimationID.HUMAN_WOODCUTTING_GILDED_AXE,
        AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE, AnimationID.HUMAN_WOODCUTTING_GAUNTLET_AXE_HM,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE, AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL, AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL, AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE, AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL, AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_3A, AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE,
        AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL, AnimationID.HUMAN_WOODCUTTING_LEAGUE_AXE,
        AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE, AnimationID.HUMAN_WOODCUTTING_DEMONIC_PACT);
    static final Set<Integer> MINE_ANIMATIONS = ImmutableSet.of(
        AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_WALL, AnimationID.HUMAN_MINING_RUNE_PICKAXE,
        AnimationID.HUMAN_MINING_BRONZE_PICKAXE, AnimationID.HUMAN_MINING_IRON_PICKAXE,
        AnimationID.HUMAN_MINING_STEEL_PICKAXE, AnimationID.HUMAN_MINING_ADAMANT_PICKAXE,
        AnimationID.HUMAN_MINING_MITHRIL_PICKAXE, AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY,
        AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_NOREACHFORWARD, AnimationID.HUMAN_MINING_BLACK_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_BLACK_PICKAXE, AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_INFERNAL_PICKAXE, AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_BLACK_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_RUNE_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_BRONZE_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_IRON_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_STEEL_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_BRONZE_PICKAXE_WALL, AnimationID.HUMAN_MINING_IRON_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_STEEL_PICKAXE_WALL, AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL, AnimationID.HUMAN_MINING_DRAGON_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_DRAGON_PICKAXE, AnimationID.HUMAN_MINING_DRAGON_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_3A_PICKAXE_WALL, AnimationID.HUMAN_MINING_3A_PICKAXE,
        AnimationID.HUMAN_MINING_3A_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_GILDED_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_GILDED_PICKAXE, AnimationID.HUMAN_MINING_GILDED_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_WALL, AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_ZALCANO_PICKAXE, AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE,
        AnimationID.HUMAN_MINING_GAUNTLET_PICKAXE_HM, AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_ZALCANO_BRONZE_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_IRON_PICKAXE, AnimationID.HUMAN_MINING_ZALCANO_BLACK_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_STEEL_PICKAXE, AnimationID.HUMAN_MINING_ZALCANO_MITHRIL_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_ADAMANT_PICKAXE, AnimationID.HUMAN_MINING_ZALCANO_RUNE_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_GILDED_PICKAXE, AnimationID.HUMAN_MINING_ZALCANO_DRAGON_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_DRAGON_PICKAXE_PRETTY, AnimationID.HUMAN_MINING_ZALCANO_ZALCANO_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_INFERNAL_PICKAXE, AnimationID.HUMAN_MINING_ZALCANO_3A_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_CRYSTAL_PICKAXE, AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE,
        AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_ZALCANO_LEAGUE_TRAILBLAZER_PICKAXE,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_WALL,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_ZALCANO_TRAILBLAZER_PICKAXE_NO_INFERNAL,
        AnimationID.HUMAN_MINING_HET_BRONZE_PICKAXE, AnimationID.HUMAN_MINING_HET_IRON_PICKAXE,
        AnimationID.HUMAN_MINING_HET_BLACK_PICKAXE, AnimationID.HUMAN_MINING_HET_STEEL_PICKAXE,
        AnimationID.HUMAN_MINING_HET_MITHRIL_PICKAXE, AnimationID.HUMAN_MINING_HET_ADAMANT_PICKAXE,
        AnimationID.HUMAN_MINING_HET_RUNE_PICKAXE, AnimationID.HUMAN_MINING_HET_GILDED_PICKAXE,
        AnimationID.HUMAN_MINING_HET_DRAGON_PICKAXE, AnimationID.HUMAN_MINING_HET_DRAGON_PICKAXE_PRETTY,
        AnimationID.HUMAN_MINING_HET_ZALCANO_PICKAXE, AnimationID.HUMAN_MINING_HET_INFERNAL_PICKAXE,
        AnimationID.HUMAN_MINING_HET_3A_PICKAXE, AnimationID.HUMAN_MINING_HET_CRYSTAL_PICKAXE,
        AnimationID.HUMAN_MINING_HET_LEAGUE_TRAILBLAZER_PICKAXE,
        AnimationID.HUMAN_MINING_HET_TRAILBLAZER_PICKAXE_NO_INFERNAL,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_WALL,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE, AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE,
        AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL,
        AnimationID.HUMAN_MINING_HET_TRAILBLAZER_PICKAXE, AnimationID.HUMAN_MINING_HET_TRAILBLAZER_RELOADED_PICKAXE,
        AnimationID.HUMAN_MINING_HET_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_ZALCANO_TRAILBLAZER_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_TRAILBLAZER_RELOADED_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL,
        AnimationID.HUMAN_MINING_DEMONIC_PACT);

    private final Client client;
    private final SkillingData data;

    private final Map<WorldPoint, Timer> byTile = new HashMap<>();
    private Integer firemakingXp;
    private final Set<Timer> timers = new HashSet<>();
    private final Map<NPC, Timer> fishingSpots = new HashMap<>();
    /** Fishing spots seen without a timer (there when we arrived): they get one when they move. */
    private final Map<NPC, WorldPoint> watchedSpots = new HashMap<>();
    // Spots that went out of view: where they were and when they appeared there (tick) / left view (tick)
    private final Map<WorldPoint, int[]> rememberedSpots = new HashMap<>();
    // Shipwreck sites seen sunk (as a stump), so a wreck surfacing there has a known start
    private final Set<WorldPoint> sunkWrecks = new HashSet<>();
    // A trap that just changed (e.g. something walked in): the old object goes before the new one appears
    private final Map<WorldPoint, Timer> changingTraps = new HashMap<>();
    private final Map<WorldPoint, Integer> changingTrapTicks = new HashMap<>();
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
        watchedSpots.clear();
        rememberedSpots.clear();
        changingTraps.clear();
        changingTrapTicks.clear();
        sunkWrecks.clear();
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
        if (CAMPFIRES.contains(id)) {
            Timer campfire = new Timer(Kind.CAMPFIRE, object, null, 0, CAMPFIRE_MAX_TICKS);
            campfire.ticksLeft = CAMPFIRE_DEFAULT_TICKS;
            // Lit just now: its time is known (roughly, until your log or a Check says more)
            campfire.started = client.getGameState() == GameState.LOGGED_IN;
            campfire.elapsed = 0;
            add(campfire, object);
            return;
        }
        if (id == FIRE) {
            // Only fires lit while you're here have a known start
            if (client.getGameState() == GameState.LOGGED_IN) {
                add(new Timer(Kind.FIRE, object, null, 100, 200), object);
            }
            return;
        }
        if (isTrap(id)) {
            onTrapSpawned(object);
            return;
        }
        if (STARS.contains(id)) {
            add(new Timer(Kind.STAR, object, null, 0, 1), object);
            return;
        }
        int[] wreck = SHIPWRECKS.get(id);
        if (wreck != null) {
            // Only a wreck seen surfacing (from a sunk stump) has a known start
            Timer timer = new Timer(Kind.SHIPWRECK, object, null, (int) (wreck[1] / TICK_SECONDS), (int) (wreck[1] / TICK_SECONDS));
            timer.hidden = !sunkWrecks.remove(object.getWorldLocation());
            add(timer, object);
            return;
        }
        for (int[] w : SHIPWRECKS.values()) {
            if (w[0] == id) {
                sunkWrecks.add(object.getWorldLocation());
                return;
            }
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
        if (resource != null && resource.chopTicks > 0) {
            add(new Timer(Kind.TREE, object, null, resource.chopTicks, resource.chopTicks), object);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject object = event.getGameObject();
        Timer timer = byTile.get(object.getWorldLocation());
        if (timer != null && timer.kind == Kind.TRAP && timer.object != null && timer.object.getId() == object.getId()) {
            // Keep it for a moment in case the trap is only changing state
            changingTraps.put(object.getWorldLocation(), timer);
            changingTrapTicks.put(object.getWorldLocation(), client.getTickCount());
        }
        remove(object);
    }

    private static boolean isTrap(int id) {
        return TRAPS_SET.contains(id) || TRAPS_CAUGHT.contains(id) || TRAPS_FAILED.contains(id) || TRAPS_TRIGGERING.contains(id);
    }

    // A trap appearing is either one of yours changing state, or newly laid (yours if you're standing on or next to it)
    private void onTrapSpawned(GameObject object) {
        WorldPoint at = object.getWorldLocation();
        Timer timer = changingTraps.remove(at);
        changingTrapTicks.remove(at);
        if (timer == null) {
            Player local = client.getLocalPlayer();
            if (!TRAPS_SET.contains(object.getId()) || local == null || local.getWorldLocation().distanceTo(at) > 1) {
                return;
            }
            timer = new Timer(Kind.TRAP, object, null, TRAP_TICKS, TRAP_TICKS);
        }
        timer.object = object;
        if (TRAPS_CAUGHT.contains(object.getId())) {
            timer.trapState = TrapState.CAUGHT;
        } else if (TRAPS_FAILED.contains(object.getId())) {
            timer.trapState = TrapState.FAILED;
        }
        timer.tiles.clear();
        add(timer, object);
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
        if (!isFishingSpot(npc)) {
            return;
        }
        int[] timing = timing(npc);
        if (timing[1] == 0) {
            return; // never moves
        }
        // Back in view of a spot we were timing: carry on from when it appeared
        int[] remembered = rememberedSpots.remove(npc.getWorldLocation());
        int now = client.getTickCount();
        Player local = client.getLocalPlayer();
        int elapsed = 0;
        if (remembered != null && now - remembered[0] <= timing[1]) {
            elapsed = now - remembered[0];
        } else if (client.getGameState() != GameState.LOGGED_IN || local == null
            || local.getWorldLocation().distanceTo(npc.getWorldLocation()) >= INTO_VIEW_DISTANCE) {
            // Loaded with the area, or just come into view as you walked up: we don't know when it got there.
            // Watched without a pie, so it gets one when it moves.
            watchedSpots.put(npc, npc.getWorldLocation());
            return;
        }
        startSpot(npc, timing, elapsed);
    }

    private static int[] timing(NPC npc) {
        return FISHING_TIMINGS.getOrDefault(npc.getId(), new int[]{FISHING_MIN_TICKS, FISHING_MAX_TICKS});
    }

    private void startSpot(NPC npc, int[] timing, int elapsed) {
        Timer old = fishingSpots.remove(npc);
        if (old != null) {
            timers.remove(old);
        }
        watchedSpots.remove(npc);
        Timer timer = new Timer(Kind.FISHING_SPOT, null, npc, timing[0], timing[1]);
        timer.elapsed = elapsed;
        timer.spotAt = npc.getWorldLocation();
        fishingSpots.put(npc, timer);
        timers.add(timer);
    }

    // Some spots (minnows, and others now and then) jump to a new tile without despawning: that's a move too
    private void checkSpotJumps() {
        for (Timer timer : new ArrayList<>(fishingSpots.values())) {
            WorldPoint at = timer.npc.getWorldLocation();
            if (timer.spotAt != null && !timer.spotAt.equals(at)) {
                startSpot(timer.npc, timing(timer.npc), 0);
            }
        }
        for (Map.Entry<NPC, WorldPoint> e : new ArrayList<>(watchedSpots.entrySet())) {
            if (!e.getValue().equals(e.getKey().getWorldLocation())) {
                startSpot(e.getKey(), timing(e.getKey()), 0);
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        watchedSpots.remove(npc);
        Timer timer = fishingSpots.remove(npc);
        if (timer != null) {
            timers.remove(timer);
            // A spot that vanished far away went out of view rather than moving: remember it for when we're back
            Player local = client.getLocalPlayer();
            if (local != null && local.getWorldLocation().distanceTo(npc.getWorldLocation()) > OUT_OF_VIEW_DISTANCE) {
                int now = client.getTickCount();
                rememberedSpots.put(npc.getWorldLocation(), new int[]{now - timer.elapsed, now});
            }
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

    /** The campfire log row for this Firemaking XP (outfit bonuses allowed for), or null. */
    static double[] campfireLog(double xp) {
        for (double[] log : CAMPFIRE_LOGS) {
            if (xp >= log[0] && xp <= log[0] * 1.03) {
                return log;
            }
        }
        return null;
    }

    // A log you put on a campfire next to you: it sets the time if it's what lit the campfire, else adds to it
    private void onFiremakingXp(int gained) {
        Player local = client.getLocalPlayer();
        double[] log = campfireLog(gained);
        if (local == null || log == null) {
            return;
        }
        for (Timer timer : timers) {
            if (timer.kind == Kind.CAMPFIRE && timer.object != null
                && timer.object.getWorldLocation().distanceTo(local.getWorldLocation()) <= 2) {
                if (timer.elapsed <= 3) {
                    timer.ticksLeft = (int) log[1];
                } else {
                    timer.ticksLeft = Math.min(CAMPFIRE_MAX_TICKS, timer.ticksLeft + (int) log[2]);
                }
                timer.started = true;
                return;
            }
        }
    }

    // "Check" says roughly how long the campfire next to you has left: pull our count into that range
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        int[] range = CAMPFIRE_CHECKS.get(Text.removeTags(event.getMessage()));
        Player local = client.getLocalPlayer();
        if (range == null || local == null) {
            return;
        }
        for (Timer timer : timers) {
            if (timer.kind == Kind.CAMPFIRE && timer.object != null
                && timer.object.getWorldLocation().distanceTo(local.getWorldLocation()) <= 3) {
                if (!timer.started || timer.ticksLeft < range[0] || timer.ticksLeft > range[1]) {
                    timer.ticksLeft = (range[0] + range[1]) / 2;
                }
                timer.started = true;
                return;
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() == Skill.FIREMAKING) {
            Integer before = firemakingXp;
            firemakingXp = event.getXp();
            if (before != null && event.getXp() > before) {
                onFiremakingXp(event.getXp() - before);
            }
        }
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
        checkSpotJumps();
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
        rememberedSpots.values().removeIf(spot -> now - spot[1] > FISHING_MEMORY_TICKS);
        changingTrapTicks.entrySet().removeIf(e -> {
            boolean gone = now - e.getValue() > 2;
            if (gone) {
                changingTraps.remove(e.getKey());
            }
            return gone;
        });
        updateStars();
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
                case CAMPFIRE:
                    timer.elapsed++;
                    if (timer.ticksLeft > 0) {
                        timer.ticksLeft--;
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

    // A star's layer health comes from the hidden NPC on it; the bar is only sent while it's being mined
    private void updateStars() {
        for (Timer timer : timers) {
            if (timer.kind != Kind.STAR) {
                continue;
            }
            for (NPC npc : client.getTopLevelWorldView().npcs()) {
                if (npc.getId() == STAR_HEALTH_NPC && npc.getWorldLocation().distanceTo(timer.object.getWorldLocation()) <= 3) {
                    int ratio = npc.getHealthRatio();
                    int scale = npc.getHealthScale();
                    if (ratio >= 0 && scale > 0) {
                        timer.health = (double) ratio / scale;
                    }
                    break;
                }
            }
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

    // Cooking on a fire (using food on it, or its Cook option): only then does it get a pie
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getId() != FIRE) {
            return;
        }
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();
        if (!option.equals("use") && !option.startsWith("cook")) {
            return;
        }
        WorldPoint at = WorldPoint.fromScene(client.getTopLevelWorldView(), event.getParam0(), event.getParam1(),
            client.getTopLevelWorldView().getPlane());
        Timer fire = timerAt(at, Kind.FIRE);
        if (fire != null) {
            fire.cookedOn = true;
        }
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
        // The same resource loaded again (the area reloaded): keep its timer, point it at the new object
        Timer existing = byTile.get(object.getWorldLocation());
        if (existing != null && existing.kind == timer.kind && existing.object != null
            && existing.object.getId() == object.getId()) {
            existing.object = object;
            return;
        }
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
