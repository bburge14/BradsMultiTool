package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;

public class SkillHelperDataTest {

    private static final SkillHelperData DATA = new SkillHelperData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void hunterCreatures() {
        SkillHelperData.Creature swift = DATA.creature("Crimson swift");
        assertEquals(1, swift.level);
        assertEquals(34, swift.xp, 0.01);
    }

    @Test
    public void runecraftAltarsAndMultiples() {
        SkillHelperData.Rune air = DATA.altar(34760);
        assertEquals("Air", air.name);
        assertEquals(3, air.perEssence(22)); // the wiki's example
        assertEquals(33, air.nextMultiple(22));
        assertEquals(1, DATA.altar(34766).perEssence(27)); // Cosmic
    }

    @Test
    public void burnLevelsFromRawNames() {
        SkillHelperData.Food lobster = DATA.food("Raw lobster");
        assertEquals(74, lobster.fire);
        assertEquals(64, lobster.gauntlets);
        assertEquals(100, DATA.food("Raw shark").fire); // never stops burning without help
        assertNotNull(DATA.food("Raw chicken")); // -> Cooked chicken
        assertNull(DATA.food("Lobster"));
    }

    @Test
    public void agilityCoursesAndShortcuts() {
        SkillHelperData.Course seers = DATA.course(10806);
        assertEquals(60, seers.level);
        assertEquals(570, seers.xpPerLap, 0.01);
    }

    @Test
    public void birdhouseVariables() {
        assertNull(BirdhouseTracker.type(0));
        assertEquals("Bird house", BirdhouseTracker.type(1));
        assertEquals("Oak bird house", BirdhouseTracker.type(4));
        assertEquals(true, BirdhouseTracker.isSeeded(6));
        assertEquals(false, BirdhouseTracker.isSeeded(5));
    }
}
