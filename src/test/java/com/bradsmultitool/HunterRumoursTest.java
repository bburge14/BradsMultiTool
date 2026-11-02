package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;

public class HunterRumoursTest {

    private static final SkillHelperData DATA = new SkillHelperData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void rumoursFromTheWiki() {
        SkillHelperData.Rumour fox = HunterRumours.referenced(DATA.rumours(), "your current rumour target is a pyre fox.");
        assertNotNull(fox);
        assertEquals("Deadfall", fox.trap);
        assertEquals(30, fox.pityWith(0));
        assertEquals(28, fox.pityWith(4)); // the whole guild hunter outfit
        assertEquals(29, fox.pityWith(2));
        assertTrue(fox.places.contains("Avium Savannah"));
        // XP per catch comes from the creature data
        assertEquals(177.6, DATA.creature(fox).xp, 0.01);
        // The list's name for it and its page's both work
        SkillHelperData.Rumour red = HunterRumours.referenced(DATA.rumours(), "go and catch some carnivorous chinchompas");
        assertEquals("Red chinchompa", red.name);
        assertEquals(100, red.pity);
        assertNull(HunterRumours.referenced(DATA.rumours(), "hello there"));
    }

    @Test
    public void longestNameWins() {
        assertEquals("Sabre-toothed kyatt", HunterRumours.referenced(DATA.rumours(), "a sabre-toothed kyatt").name);
        assertEquals("Moonlight antelope", HunterRumours.referenced(DATA.rumours(), "moonlight antelope, in the burrow").name);
    }

    @Test
    public void huntersAsWholeWords() {
        assertEquals("Aco", HunterRumours.hunterIn("aco has a rumour for you"));
        assertNull(HunterRumours.hunterIn("some tobacco"));
        assertEquals("Gilman", HunterRumours.hunterIn("huntmaster gilman (novice)"));
        assertTrue(HunterRumours.handedIn("ah, another one done? here's your reward."));
        assertFalse(HunterRumours.handedIn("go and find me a pyre fox."));
    }

    @Test
    public void catchesByTheirXp() {
        assertTrue(HunterRumours.isCatch(177, 177.6, 0)); // fractions of XP add up: 177 or 178
        assertTrue(HunterRumours.isCatch(178, 177.6, 0));
        assertFalse(HunterRumours.isCatch(176, 177.6, 0));
        assertTrue(HunterRumours.isCatch(265, 265, 0));
        assertTrue(HunterRumours.isCatch(2100, 1950, 2461)); // Herbiboar: by your level
        assertFalse(HunterRumours.isCatch(60, 1950, 2461));
        assertFalse(HunterRumours.isCatch(10, 0, 0));
    }
}
