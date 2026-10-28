package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Estimates how long the tree you're chopping has left. Since Forestry, a tree falls after being chopped
 * for a set number of ticks (from the wiki), and the timer recovers while nobody chops it. Only your own
 * chopping is visible, so with other players on the same tree it falls sooner than shown.
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
        }

        Player player = client.getLocalPlayer();
        boolean chopping = player != null && player.getAnimation() != -1
            && player.getWorldLocation().distanceTo(tree) <= CHOP_DISTANCE;
        chopped = chopping ? Math.min(treeChopTicks, chopped + 1) : Math.max(0, chopped - 1);
    }

    /** The tree being timed, or null. */
    WorldPoint getTree() {
        return tree;
    }

    /** Estimated ticks of chopping left, or -1 when no tree is being timed. */
    int ticksLeft() {
        return tree == null ? -1 : treeChopTicks - chopped;
    }

    int totalTicks() {
        return treeChopTicks;
    }
}
