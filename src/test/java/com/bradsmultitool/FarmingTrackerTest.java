package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FarmingTrackerTest {

    @Test
    public void recordsRoundTripAndKnowWhenReady() {
        FarmingTracker.Record herb = new FarmingTracker.Record("farmPatch.11062.4774", "Catherby: Herb patch", "Ranarr",
            FarmingData.GROWING, 1000, false);
        FarmingTracker.Record back = FarmingTracker.Record.decode(herb.key, herb.encode());
        assertEquals("Catherby: Herb patch", back.title);
        assertEquals("Ranarr", back.crop);
        assertEquals(1000, back.grownAt);
        assertFalse(back.ready(999));
        assertTrue(back.ready(1000));
        assertTrue(new FarmingTracker.Record("k", "t", "c", FarmingData.HARVESTABLE, 0, true).ready(0));
        assertFalse(new FarmingTracker.Record("k", "t", "c", FarmingData.DISEASED, 0, false).ready(5000));
        assertNull(FarmingTracker.Record.decode("k", "broken"));
        assertEquals("ready", PlannerPanel.patchStatus(back, 2000));
        assertEquals("in 10m", PlannerPanel.patchStatus(back, 400));
    }
}
