package com.bradsmultitool;

import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

/**
 * The PvM HUD's loot line: the Grand Exchange value of what your kills dropped this session, and per hour. Counts
 * RuneLite's NpcLootReceived (the drops of monsters you killed, the same event RuneLite's Loot Tracker records),
 * priced like the Loot Tracker does (ItemManager, noted items as their unnoted kind). The clock starts at your
 * first hit on a monster; the HUD's Reset clears it.
 */
@Singleton
class PvmLoot {

    // Below this the per-hour figure would swing wildly with one drop, so it isn't shown yet
    private static final long MIN_SECONDS_FOR_RATE = 60;

    private final ItemManager itemManager;
    private volatile long value;
    private volatile Instant start;

    @Inject
    PvmLoot(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (start == null && event.getHitsplat().isMine() && event.getActor() instanceof NPC) {
            start = Instant.now();
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event) {
        long total = 0;
        for (ItemStack item : event.getItems()) {
            total += itemManager.getItemPrice(itemManager.canonicalize(item.getId())) * (long) item.getQuantity();
        }
        if (start == null) {
            start = Instant.now();
        }
        value += total;
    }

    /** The GE value of this session's kill drops. */
    long value() {
        return value;
    }

    /** That value per hour since your first hit, or -1 while there's too little time to tell. */
    long perHour() {
        Instant from = start;
        if (from == null) {
            return -1;
        }
        long seconds = Duration.between(from, Instant.now()).getSeconds();
        return seconds < MIN_SECONDS_FOR_RATE ? -1 : value * 3600 / seconds;
    }

    void reset() {
        value = 0;
        start = null;
    }

    /** "950", "12.5K", "1.25M", "2.1B". */
    static String format(long gp) {
        if (gp >= 1_000_000_000L) {
            return String.format("%.1fB", gp / 1_000_000_000.0);
        }
        if (gp >= 1_000_000) {
            return String.format("%.2fM", gp / 1_000_000.0);
        }
        if (gp >= 100_000) {
            return String.format("%.0fK", gp / 1_000.0);
        }
        if (gp >= 1_000) {
            return String.format("%.1fK", gp / 1_000.0);
        }
        return String.valueOf(gp);
    }
}
