package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Estimates how long the tree you're chopping has left. Since Forestry, a tree's timer (ticks from the
 * wiki) starts with the first log, runs down while it's being chopped and recovers at the same rate while
 * nobody chops it. Once it's out, the tree falls with the next successful log roll, which is luck: that
 * last stretch is estimated from your own average time per log. Only your own chopping is visible, so with
 * other players on the same tree it falls sooner than shown.
 */
@Singleton
class TreeTimer {

    // Chopping counts while you stay this close to the tree you clicked (big trees are 2-3 tiles wide)
    private static final int CHOP_DISTANCE = 3;

    private final Client client;
    private final ExtraAlerts extraAlerts;
    private final SkillingData skillingData;

    private WorldPoint tree = null;
    private int treeChopTicks = 0;
    private int chopped = 0;
    // The timer only starts once the tree has given its first log
    private boolean started = false;
    // Your average ticks between logs (rolls every 4 ticks; how often they succeed depends on axe and level)
    private static final double DEFAULT_TICKS_PER_LOG = 8;
    private double ticksPerLog = DEFAULT_TICKS_PER_LOG;
    private int lastLogTick = -1;
    private int ticksSinceLog = 0;

    @Inject
    TreeTimer(Client client, ExtraAlerts extraAlerts, SkillingData skillingData) {
        this.client = client;
        this.extraAlerts = extraAlerts;
        this.skillingData = skillingData;
    }

    /** Call once per game tick. */
    void update() {
        WorldPoint clicked = extraAlerts.lastTreeLocation();
        SkillingData.Resource resource = clicked != null ? skillingData.object(extraAlerts.lastResourceObjectId(), extraAlerts.lastResourceName()) : null;
        if (resource == null || resource.chopTicks <= 0) {
            tree = null;
            return;
        }
        if (!clicked.equals(tree)) {
            tree = clicked;
            treeChopTicks = resource.chopTicks;
            chopped = 0;
            started = false;
        }
        if (!started) {
            return;
        }
        ticksSinceLog++;

        Player player = client.getLocalPlayer();
        boolean chopping = player != null && player.getAnimation() != -1
            && player.getWorldLocation().distanceTo(tree) <= CHOP_DISTANCE;
        chopped = chopping ? Math.min(treeChopTicks, chopped + 1) : Math.max(0, chopped - 1);
    }

    /** Call on a Woodcutting XP drop: a log was cut, which starts the tree's timer. */
    void onLogCut() {
        int tick = client.getTickCount();
        int gap = tick - lastLogTick;
        // Only count gaps from steady chopping (not walking to a new tree or banking)
        if (lastLogTick >= 0 && gap > 0 && gap <= 40) {
            ticksPerLog = ticksPerLog * 0.8 + gap * 0.2;
        }
        lastLogTick = tick;
        ticksSinceLog = 0;
        started = true;
    }

    /**
     * How much of the tree is left, 1 (fresh) to 0, or -1 when no tree is being timed. Covers the timer
     * plus the expected wait for the final log.
     */
    double fractionLeft() {
        if (tree == null || treeChopTicks <= 0) {
            return -1;
        }
        double total = treeChopTicks + ticksPerLog;
        double left = (treeChopTicks - chopped) + (isOnLastLog() ? Math.max(0, ticksPerLog - ticksSinceLog) : ticksPerLog);
        return Math.max(0, Math.min(1, left / total));
    }

    /** True once the timer is out: the tree falls with the next log. */
    boolean isOnLastLog() {
        return tree != null && started && chopped >= treeChopTicks;
    }

    /** The tree being timed, or null. */
    WorldPoint getTree() {
        return tree;
    }
}
