package com.snuffys.multitool;

import java.awt.Color;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

/**
 * When you die: a voice and chat alert saying where, and an info box counting down until your gravestone's items
 * are gone. Information only (the old core Death Indicator plugin, BSD-2, timed the grave the same way from the
 * player's death).
 */
@Singleton
class DeathAlerts {

    // The game keeps your items at a gravestone for 15 minutes, and the time only runs while you're logged in
    // (OSRS Wiki, "Gravestone"); counted in game ticks (0.6s each), which also only come while you're logged in
    static final int GRAVESTONE_TICKS = 15 * 100;
    // After dying, how long to wait for the respawn before checking what you lost
    private static final int RESPAWN_WAIT_TICKS = 20;
    // Back within this many tiles of where you died: at your gravestone
    private static final int AT_GRAVE_TILES = 1;

    private final Client client;
    private final PvmConfig config;
    private final Alerts alerts;
    private final InfoBoxManager infoBoxes;
    private final ItemManager itemManager;
    private final Lifecycle lifecycle;

    // A death waiting for the respawn: where, how many items you had, and ticks since
    private WorldPoint pendingSpot;
    private int itemsAtDeath;
    private int pendingTicks;
    // The gravestone being timed
    private WorldPoint graveSpot;
    private int ticksLeft;
    private boolean leftGrave;
    private GraveInfoBox box;

    @Inject
    DeathAlerts(Client client, PvmConfig config, Alerts alerts, InfoBoxManager infoBoxes, ItemManager itemManager,
                Lifecycle lifecycle) {
        this.client = client;
        this.config = config;
        this.alerts = alerts;
        this.infoBoxes = infoBoxes;
        this.itemManager = itemManager;
        this.lifecycle = lifecycle;
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        Player player = client.getLocalPlayer();
        if (player == null || event.getActor() != player || !config.deathAlert()) {
            return;
        }
        boolean instance = client.getTopLevelWorldView().isInstance();
        boolean wilderness = client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1;
        // The real map square, also inside an instance
        WorldPoint spot = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
        alerts.alert(VoiceAlertService.VoiceLine.DEATH, where(spot, instance, wilderness));
        // No gravestone in an instance (Death's Office or the boss's reclaim keeps them) or the Wilderness (items
        // drop on the floor)
        if (config.deathTimer() && !instance && !wilderness) {
            pendingSpot = spot;
            itemsAtDeath = itemCount();
            pendingTicks = 0;
        }
    }

    static String where(WorldPoint spot, boolean instance, boolean wilderness) {
        String at = spot.getX() + ", " + spot.getY() + (spot.getPlane() > 0 ? ", floor " + spot.getPlane() : "");
        return (instance ? "in an instance, " : wilderness ? "in the Wilderness, " : "") + "at " + at;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }
        WorldPoint here = player.getWorldLocation();
        if (pendingSpot != null) {
            pendingTicks++;
            boolean respawned = here.getPlane() != pendingSpot.getPlane() || here.distanceTo2D(pendingSpot) > 5;
            if (respawned || pendingTicks >= RESPAWN_WAIT_TICKS) {
                // A gravestone only when you lost items: a safe death (minigames) keeps everything
                if (itemCount() < itemsAtDeath) {
                    startTimer(pendingSpot);
                }
                pendingSpot = null;
            }
        }
        if (graveSpot == null) {
            return;
        }
        ticksLeft--;
        boolean near = here.getPlane() == graveSpot.getPlane() && here.distanceTo2D(graveSpot) <= AT_GRAVE_TILES;
        if (!near) {
            leftGrave = true;
        }
        if (ticksLeft <= 0 || (leftGrave && near) || !config.deathTimer()) {
            graveSpot = null;
            removeBox();
        }
    }

    private void startTimer(WorldPoint spot) {
        graveSpot = spot;
        ticksLeft = GRAVESTONE_TICKS;
        leftGrave = false;
        if (box == null && lifecycle.plugin != null) {
            box = new GraveInfoBox(itemManager.getImage(ItemID.BONES), lifecycle.plugin);
            infoBoxes.addInfoBox(box);
        }
    }

    /** Takes the info box away and forgets the grave (the plugin stopping). */
    void stop() {
        pendingSpot = null;
        graveSpot = null;
        removeBox();
    }

    private void removeBox() {
        if (box != null) {
            infoBoxes.removeInfoBox(box);
            box = null;
        }
    }

    // Items in your inventory and worn, by slot (a stack counts once)
    private int itemCount() {
        return count(client.getItemContainer(InventoryID.INV)) + count(client.getItemContainer(InventoryID.WORN));
    }

    private static int count(ItemContainer container) {
        int n = 0;
        if (container != null) {
            for (Item item : container.getItems()) {
                if (item.getId() > 0) {
                    n++;
                }
            }
        }
        return n;
    }

    /** "12:34": minutes and seconds of logged-in time left. */
    static String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks) * 6 / 10;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private final class GraveInfoBox extends InfoBox {
        GraveInfoBox(java.awt.image.BufferedImage image, net.runelite.client.plugins.Plugin plugin) {
            super(image, plugin);
        }

        @Override
        public String getText() {
            return formatTicks(ticksLeft);
        }

        @Override
        public Color getTextColor() {
            return ticksLeft < 200 ? HudColors.BAD : Color.WHITE;
        }

        @Override
        public String getTooltip() {
            WorldPoint spot = graveSpot;
            return "Gravestone: your items are kept for " + formatTicks(ticksLeft) + " more (while you're logged in)"
                + (spot != null ? "</br>Died at " + spot.getX() + ", " + spot.getY() : "");
        }
    }
}
