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
    public void trapSpots() {
        java.util.List<SkillHelperData.Creature> net = DATA.creaturesFor("Net trap");
        assertEquals("Swamp lizard", net.get(0).name); // lowest level first
        assertEquals(29, net.get(0).level);
        assertNotNull(DATA.creaturesFor("Deadfall").isEmpty() ? null : "deadfall");
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
    public void cookingAndSmithingRecipes() {
        SkillHelperData.Recipe lobster = DATA.cooks("Raw lobster");
        assertEquals("Lobster", lobster.name);
        assertEquals(40, lobster.level);
        assertEquals(120, lobster.xp, 0.01);
        assertNull(DATA.cooks("Lobster"));

        SkillHelperData.Recipe platebody = null;
        for (SkillHelperData.Recipe item : DATA.anvilItems("Steel bar")) {
            if (item.name.equals("Steel platebody")) {
                platebody = item;
            }
        }
        assertNotNull(platebody);
        assertEquals(48, platebody.level);
        assertEquals(5, (int) platebody.materials.get("Steel bar"));

        SkillHelperData.Recipe steel = null;
        for (SkillHelperData.Recipe bar : DATA.furnaceBars()) {
            if (bar.name.equals("Steel bar")) {
                steel = bar;
            }
        }
        assertNotNull(steel);
        assertEquals(2, (int) steel.materials.get("Coal"));
        assertEquals(1, (int) steel.materials.get("Iron ore"));
    }

    @Test
    public void agilityCoursesAndShortcuts() {
        // Draynor rooftop's first obstacle, the rough wall
        SkillHelperData.Course draynor = DATA.courseFor(11404);
        assertEquals("Draynor Village Rooftop Course", draynor.name);
        assertEquals(1, draynor.level);
        assertEquals(120, draynor.xpPerLap, 0.01);
    }

    @Test
    public void birdhouseVariables() {
        assertNull(BirdhouseTracker.type(0));
        assertEquals("Bird house", BirdhouseTracker.type(1));
        assertEquals("Oak bird house", BirdhouseTracker.type(4));
        assertEquals(true, BirdhouseTracker.isSeeded(6));
        assertEquals(false, BirdhouseTracker.isSeeded(5));
    }

    @Test
    public void sailing() {
        SkillHelperData.Shipwreck small = DATA.shipwreck("Small shipwreck");
        assertEquals(15, small.level);
        assertEquals(10, small.xp, 0.01);
        assertEquals("Rune salvaging hook", DATA.bestHook(80));
        assertNull(DATA.bestHook(10));
        assertEquals(91, DATA.shoal("Marlin shoal").level);
        // The crashed glider task south of the Karamja Shipyard
        SkillHelperData.ChartTask glider = DATA.chartTaskNear(2988, 3011, 5);
        assertEquals("Generic", glider.type);
        assertEquals(35, glider.xp, 0.01);
    }

    @Test
    public void seaMonsters() {
        SkillHelperData.SeaMonster shark = DATA.seaMonster("Bull shark");
        assertEquals(65, shark.combat);
        assertEquals(81, shark.hp);
        assertEquals("Adamant keel", shark.safeKeel());
        assertEquals(Integer.valueOf(30), shark.bounties.get("Bull shark jaw"));
        assertNull(DATA.seaMonster("Current duck"));
    }

    @Test
    public void fishingSpots() {
        SkillHelperData.FishingSpot karambwan = DATA.fishingSpot(4712);
        assertEquals("Raw karambwan", karambwan.options.get("Fish").get(0).name);
        assertEquals(65, karambwan.options.get("Fish").get(0).level);
        assertEquals("Karambwan vessel", karambwan.options.get("Fish").get(0).tool);
        java.util.List<String> owned = java.util.Arrays.asList("dragon harpoon", "fly fishing rod", "lantern");
        org.junit.Assert.assertTrue(BradsMultiToolSkillingOverlay.hasTool("Harpoon + Lantern", owned));
        org.junit.Assert.assertFalse(BradsMultiToolSkillingOverlay.hasTool("Fishing rod", owned));
    }
}
