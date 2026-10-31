package com.bradsmultitool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoalToolsTest {

    @Test
    public void betterToolsCount() {
        assertTrue(GoalItems.isTool("dragon axe", "axe"));
        assertTrue(GoalItems.isTool("crystal pickaxe", "pickaxe"));
        assertTrue(GoalItems.isTool("feather", "feather"));
        assertTrue(GoalItems.isTool("pearl fishing rod", "fishing rod"));
        assertTrue(GoalItems.isTool("fly fishing rod", "fly fishing rod"));
    }

    @Test
    public void otherThingsDont() {
        // A fly fishing rod can't bait fish, and fish aren't tools
        assertFalse(GoalItems.isTool("fly fishing rod", "fishing rod"));
        assertFalse(GoalItems.isTool("oily fishing rod", "fishing rod"));
        assertFalse(GoalItems.isTool("raw trout", "fly fishing rod"));
        assertFalse(GoalItems.isTool("pickaxe handle", "pickaxe"));
    }
}
