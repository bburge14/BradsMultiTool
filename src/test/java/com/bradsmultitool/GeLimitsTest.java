package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

public class GeLimitsTest {

    private static final long H = 60 * 60 * 1000L;

    @Test
    public void windowStartsAtTheFirstBuyAndResetsFourHoursLater() {
        long t = 1_000_000_000L;
        Map<Integer, PlannerGeLimits.Window> w = PlannerGeLimits.windows(Arrays.asList(
            new PlannerGeLimits.Buy(1, 4000, t, true),
            new PlannerGeLimits.Buy(1, 3000, t + H, true)), t + 2 * H, new PlannerGeLimits.Slot[0]);
        PlannerGeLimits.Window window = w.get(1);
        assertEquals(7000, window.bought);
        assertEquals(t + 4 * H, window.resetsAt);
        assertTrue(window.exact);
        window.limit = 7000;
        assertTrue(window.atLimit());
        assertEquals(0, window.left());
    }

    @Test
    public void aBuyAfterTheWindowStartsTheNextAndAnEndedOneIsGone() {
        long t = 1_000_000_000L;
        Map<Integer, PlannerGeLimits.Window> w = PlannerGeLimits.windows(Arrays.asList(
            new PlannerGeLimits.Buy(1, 4000, t, true),
            new PlannerGeLimits.Buy(1, 500, t + 5 * H, false)), t + 6 * H, new PlannerGeLimits.Slot[0]);
        assertEquals(500, w.get(1).bought);
        assertEquals(t + 9 * H, w.get(1).resetsAt);
        // (its first buy came in while away: "by" then)
        assertFalse(w.get(1).exact);
        assertNull(PlannerGeLimits.windows(Arrays.asList(new PlannerGeLimits.Buy(2, 10, t, true)), t + 5 * H,
            new PlannerGeLimits.Slot[0]).get(2));
        // ...unless asked for ended ones (saying it reset)
        assertNotNull(PlannerGeLimits.windows(Arrays.asList(new PlannerGeLimits.Buy(2, 10, t, true)), t + 5 * H,
            new PlannerGeLimits.Slot[0], true).get(2));
    }

    @Test
    public void anOpenBuyOfferShowsWithOrWithoutAWindow() {
        PlannerGeLimits.Slot buying = new PlannerGeLimits.Slot(3, 0, 1000, 100, GrandExchangeOfferState.BUYING);
        PlannerGeLimits.Slot selling = new PlannerGeLimits.Slot(4, 0, 1000, 100, GrandExchangeOfferState.SELLING);
        Map<Integer, PlannerGeLimits.Window> w = PlannerGeLimits.windows(Arrays.asList(), 0, new PlannerGeLimits.Slot[] {buying, selling});
        assertSame(buying, w.get(3).offer);
        assertEquals(0, w.get(3).resetsAt);
        assertNull(w.get(4));
    }

    @Test
    public void yourPriceIsYourTradesAverageAndTradedAddsThemUp() {
        PlannerGeLimits limits = new PlannerGeLimits(null, null, null, null, null, null);
        long now = System.currentTimeMillis();
        // Bought 1,000 tips at 180 and 3,000 at 190; sold 2,000 darts at 200
        limits.addSampleTrade(1, "Adamant dart tip", 1000, 180_000, now - 60_000, false);
        limits.addSampleTrade(1, "Adamant dart tip", 3000, 570_000, now - 30_000, false);
        limits.addSampleTrade(2, "Adamant dart", 2000, 400_000, now - 10_000, true);
        assertEquals(Long.valueOf(188), limits.yourPrice("adamant dart tip"));
        // A product you sell goes at your sales
        assertEquals(Long.valueOf(200), limits.yourPrice("Adamant dart"));
        assertNull(limits.yourPrice("Feather"));
        long[] traded = limits.traded(Arrays.asList("Adamant dart tip", "Adamant dart"), now - 45_000);
        // (only since then: the 3,000 tips, and the darts after the 2% tax)
        assertEquals(570_000, traded[0]);
        assertEquals(392_000, traded[1]);
    }
}
