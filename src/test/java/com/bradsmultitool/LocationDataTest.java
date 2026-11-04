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

    @Test
    public void fishHaveTheirSpots() {
        List<LocationData.Place> shark = LOCATIONS.forAction("Fishing", "Raw shark");
        assertEquals("Fishing Guild", shark.get(0).place);
        assertTrue(shark.size() > 3);
        assertEquals("Barbarian Outpost", LOCATIONS.forAction("Fishing", "Leaping sturgeon").get(0).place);
        assertFalse(LOCATIONS.forAction("Fishing", "Raw monkfish").isEmpty());
    }

    @Test
    public void placesSayTheQuestsTheyNeed() {
        List<LocationData.Place> teak = LOCATIONS.forAction("Woodcutting", "Teak logs");
        LocationData.Place prif = teak.stream().filter(p -> p.place.equals("Prifddinas")).findFirst().orElse(null);
        assertNotNull(prif);
        assertTrue(prif.quests.stream().anyMatch(q -> q.quest.equals("Song of the Elves") && !q.started));
        LocationData.Place ape = teak.stream().filter(p -> p.place.equals("Ape Atoll")).findFirst().orElse(null);
        assertTrue(ape.quests.stream().anyMatch(q -> q.quest.equals("Monkey Madness I") && q.started));
        assertTrue(teak.get(0).quests.isEmpty());
        // Without quest progress (the panel before login), nothing's held back
        assertTrue(prif.missing(null).isEmpty());
    }
}
