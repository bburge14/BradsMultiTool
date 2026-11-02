package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class LootAndCluesTest {

    @Test
    public void cluesAreToldApartByName() {
        assertTrue(ClueDrops.isClue("Clue scroll (hard)"));
        assertTrue(ClueDrops.isClue("Scroll box (beginner)"));
        assertTrue(ClueDrops.isSkillingClue("Clue geode (medium)"));
        assertFalse(ClueDrops.isSkillingClue("Clue scroll (elite)"));
        assertFalse(ClueDrops.isClue("Clue box"));
        assertFalse(ClueDrops.isClue("Reward casket (easy)"));
    }

    @Test
    public void clueNamesSayTheTier() {
        assertEquals("a hard clue scroll", ClueDrops.describe("Clue scroll (hard)"));
        assertEquals("an elite clue nest", ClueDrops.describe("Clue nest (elite)"));
        assertEquals("an easy clue bottle", ClueDrops.describe("Clue bottle (easy)"));
    }

    @Test
    public void lootValuesAreShort() {
        assertEquals("950", PvmLoot.format(950));
        assertEquals("12.5K", PvmLoot.format(12_500));
        assertEquals("1.25M", PvmLoot.format(1_250_000));
    }

    @Test
    public void gravestoneTimeAndPlace() {
        assertEquals("15:00", DeathAlerts.formatTicks(DeathAlerts.GRAVESTONE_TICKS));
        assertEquals("0:06", DeathAlerts.formatTicks(10));
        assertEquals("at 3222, 3218", DeathAlerts.where(new WorldPoint(3222, 3218, 0), false, false));
        assertEquals("in the Wilderness, at 3100, 3600, floor 1", DeathAlerts.where(new WorldPoint(3100, 3600, 1), false, true));
    }
}
