package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;

public class SlayerDataTest {

    private static final SlayerData DATA = new SlayerData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void bestMasterForYourLevels() {
        assertTrue(DATA.bestMaster(3, 1).name.startsWith("Turael"));
        assertEquals("Vannaka", DATA.bestMaster(55, 30).name);
        assertEquals("Nieve/Steve", DATA.bestMaster(100, 40).name); // Duradel needs 50 Slayer
        assertTrue(DATA.bestMaster(110, 80).name.startsWith("Duradel"));
        assertEquals("Chaeldar", DATA.nextMaster(DATA.bestMaster(55, 30)).name);
        assertFalse(DATA.bestMaster(3, 1).routes.isEmpty());
    }

    @Test
    public void taskPlacesAndTravel() {
        SlayerData.Task abyssals = DATA.task("Abyssal demons");
        assertNotNull(abyssals);
        SlayerData.Place catacombs = null;
        for (SlayerData.Place place : abyssals.places) {
            if (place.name.equals("Catacombs of Kourend")) {
                catacombs = place;
            }
        }
        assertNotNull(catacombs);
        assertTrue(catacombs.multi);
        assertFalse(DATA.travel("Catacombs of Kourend").isEmpty());
        assertNotNull(DATA.task("Lesser demons")); // the wiki page is "Lesser demon"
        assertNull(DATA.task("Not a task"));
    }
}
