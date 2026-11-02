package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Motherlode Mine: says when the pay-dirt you carry fills the sack (what's in it plus yours reaches its size, 108,
 * or 189 upgraded), so you empty it before mining more. Once until the sack has room again. RuneLite's Motherlode
 * plugin turns the sack numbers red at the same point; this is the voice and chat for it. Only reads the sack's
 * game variables and your inventory.
 */
@Singleton
class MotherlodeSack {

    // The mine's map regions (as RuneLite's Motherlode plugin)
    private static final java.util.Set<Integer> REGIONS = java.util.Set.of(14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193);
    private static final int SACK_SIZE = 108;
    private static final int SACK_LARGE_SIZE = 189;

    private final Client client;
    private final Alerts alerts;
    private boolean warned;
    private int lastInSack;

    @Inject
    MotherlodeSack(Client client, Alerts alerts) {
        this.client = client;
        this.alerts = alerts;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Player you = client.getLocalPlayer();
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (you == null || inventory == null || !REGIONS.contains(you.getWorldLocation().getRegionID())) {
            return;
        }
        int inSack = client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
        int size = client.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK) == 1 ? SACK_LARGE_SIZE : SACK_SIZE;
        int carried = inventory.count(ItemID.PAYDIRT);
        boolean full = full(inSack, carried, size);
        if (full && !warned && alerts.ready()) {
            warned = true;
            alerts.alert(VoiceAlertService.VoiceLine.SACK_FULL, inSack + carried + "/" + size);
        } else if (!full && (carried > 0 || inSack < lastInSack)) {
            // Re-armed once you carry pay-dirt that fits, or the sack's been emptied; not just because you
            // deposited (the deposited dirt is still in the water, the sack count catches up a moment later)
            warned = false;
        }
        lastInSack = inSack;
    }

    /** Whether the pay-dirt you carry fills the sack (there's some to carry and it won't all fit after it). */
    static boolean full(int inSack, int carried, int size) {
        return carried > 0 && carried >= size - inSack;
    }
}
