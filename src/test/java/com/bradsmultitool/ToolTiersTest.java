package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Felling axes, infernal and 3rd age tools count as their tier on the skilling hover card. */
public class ToolTiersTest {
    @Test
    public void fellingAxesAreAxes() {
        List<SkillingData.Tool> axes = SkillingData.toolsFor("Woodcutting");
        SkillingData.Tool owned = SkillingData.bestOwnedTool(axes, Arrays.asList("rune felling axe", "lobster"));
        assertEquals("Rune felling axe", owned.name);
        // At 47, the best is rune: no better tier than the felling axe you have
        SkillingData.Tool best = SkillingData.bestToolForLevel(axes, 47);
        assertEquals("Rune axe", best.name);
        assertTrue(best.level <= owned.level);
        assertEquals(61, SkillingData.bestOwnedTool(axes, Arrays.asList("infernal axe")).level);
        assertEquals(61, SkillingData.bestOwnedTool(SkillingData.toolsFor("Mining"), Arrays.asList("3rd age pickaxe")).level);
        assertTrue(GoalItems.isTool("rune felling axe", "axe"));
    }
}
