package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** Where to train: the places of trees, rocks and creatures, the training guides' picks first. */
public class LocationDataTest {
    private static final LocationData LOCATIONS = new LocationData();

    @BeforeClass
    public static void load() {
        LOCATIONS.load(new Gson());
    }

    @Test
    public void teaksBestFirstWithWhy() {
        List<LocationData.Place> teak = LOCATIONS.forAction("Woodcutting", "Teak logs");
        assertFalse(teak.isEmpty());
        assertEquals("Isle of Souls", teak.get(0).place);
        assertNotNull(teak.get(0).best);
        assertTrue(teak.stream().anyMatch(p -> p.place.startsWith("Hardwood Grove") && p.note != null && p.note.contains("trading sticks")));
    }

    @Test
    public void everyGatheringSkillHasPlaces() {
        assertFalse(LOCATIONS.forAction("Woodcutting", "Maple logs").isEmpty());
        assertFalse(LOCATIONS.forAction("Mining", "Iron ore").isEmpty());
        assertFalse(LOCATIONS.forAction("Woodcutting", "Oak logs").isEmpty());
        assertFalse(LOCATIONS.forAction("Thieving", "Master Farmer").isEmpty());
        assertEquals("Mining Guild (P2P)", LOCATIONS.forAction("Mining", "Iron ore").get(0).place);
    }
}
