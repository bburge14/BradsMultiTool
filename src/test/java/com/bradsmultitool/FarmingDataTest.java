package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class FarmingDataTest {

    private static final FarmingData DATA = new FarmingData(null, null);

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void readsThePatchList() {
        assertTrue("patches: " + DATA.patches().size(), DATA.patches().size() > 100);
    }

    @Test
    public void decodesPatchVarbits() {
        // Allotment value 7: potato, stage 1 of 5; 71: the same, watered; 3: raked and empty
        FarmingData.State potato = DATA.decode("ALLOTMENT", 7);
        assertEquals("Potato", potato.produce.name);
        assertEquals(FarmingData.GROWING, potato.cropState);
        assertEquals(1, potato.stage);
        assertFalse(potato.watered);
        assertTrue(DATA.decode("ALLOTMENT", 71).watered);
        FarmingData.State empty = DATA.decode("ALLOTMENT", 3);
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.stage);
    }

    @Test
    public void cropLevelsAndPayments() {
        FarmingData.Crop ranarr = DATA.crop("Ranarr");
        assertEquals(32, ranarr.level);
        assertEquals(15, DATA.crop("Oak").level); // wiki lists it as Acorn
        assertNotNull(DATA.crop("Potato").payment);
        List<FarmingData.Crop> herbs = DATA.cropsFor("HERB");
        assertEquals("Guam", herbs.get(0).name);
        assertFalse(DATA.cropsFor("ALLOTMENT").isEmpty());
    }

    @Test
    public void growthCountsDownToTheNextCycles() {
        // Potato: 10-minute cycles, 5 stages; at stage 1 with 4 minutes to the next cycle -> 4 + 2 x 10 minutes
        FarmingData.State state = DATA.decode("ALLOTMENT", 7);
        long now = 600 * 1000 + 360;
        assertEquals(240 + 2 * 600, FarmingData.secondsUntilGrown(state, now));
    }
}
