package com.bradsmultitool;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** What the items you have cost you, by where they came from. */
public class OriginsTest {

    @Test
    public void nothingKnownIsAtItsPrice() {
        assertArrayEquals(new long[] {1000, 0, 0, 10}, PlannerOrigins.ownedCost(null, 10, 100));
    }

    @Test
    public void boughtAtWhatYouPaidGotYourselfFree() {
        PlannerOrigins.Origin o = new PlannerOrigins.Origin();
        o.bought = 300;
        o.paid = 30_000;
        o.gathered = 1200;
        // 1,500 known: a fifth bought (at 100 each), the rest yours
        assertArrayEquals(new long[] {30_000, 300, 1200, 0}, PlannerOrigins.ownedCost(o, 1500, 250));
        // More than it knows of: the rest at the price
        assertArrayEquals(new long[] {30_000 + 100 * 250, 300, 1200, 100}, PlannerOrigins.ownedCost(o, 1600, 250));
    }
}
