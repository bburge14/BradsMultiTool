package com.bradsmultitool;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import org.junit.Test;

public class GoalTargetTest {

    @Test
    public void fishingPicksKeepTheSpotTheWayAndTheNpc() {
        String target = GoalTarget.fish("Rod Fishing spot", "Lure", 1526);
        assertEquals("fish:rod fishing spot|lure|1526", target);
        assertArrayEquals(new String[]{"rod fishing spot", "lure", "1526"}, GoalTarget.parts(target));
        assertEquals("rod fishing spot", GoalTarget.npcName(target));
        assertNull(GoalTarget.itemName(target));
    }

    @Test
    public void itemPicksCanNameOneRecipe() {
        assertNull(GoalTarget.recipeName(GoalTarget.item("Logs", null)));
        assertEquals("logs", GoalTarget.itemName(GoalTarget.item("Logs", null)));
        String bow = GoalTarget.item("Logs", "Longbow (u)");
        assertEquals("logs", GoalTarget.itemName(bow));
        assertEquals("Longbow (u)", GoalTarget.recipeName(bow));
        // Saved before recipes could be picked
        assertEquals("logs", GoalTarget.itemName("item:logs"));
        assertEquals("ruby harvest", GoalTarget.npcName("npc:ruby harvest"));
    }

    @Test
    public void mixedDropsAverage() {
        assertEquals(60, SkillTracker.averageOf(Arrays.asList(50, 70, 50, 70)), 0.001);
        assertEquals(17.5, SkillTracker.averageOf(Arrays.asList(17, 18)), 0.001);
        assertEquals(0, SkillTracker.averageOf(Arrays.asList()), 0.001);
    }
}
