package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResourceTimersTest {

    private static ResourceTimers.Timer tree(int ticks) {
        return new ResourceTimers.Timer(ResourceTimers.Kind.TREE, null, null, ticks, ticks);
    }

    @Test
    public void treeWaitsForYourFirstLogWhenChoppingAlone() {
        ResourceTimers.Timer oak = tree(45);
        ResourceTimers.tickTree(oak, 1, true);
        assertEquals(45, oak.ticksLeft);
        oak.youCutLog = true;
        ResourceTimers.tickTree(oak, 1, true);
        assertEquals(44, oak.ticksLeft);
    }

    @Test
    public void treeRunsWhileOthersChopAndRecoversWhenNobodyDoes() {
        ResourceTimers.Timer oak = tree(45);
        for (int i = 0; i < 10; i++) {
            ResourceTimers.tickTree(oak, 2, true);
        }
        assertEquals(35, oak.ticksLeft);
        assertTrue(oak.visible());
        for (int i = 0; i < 4; i++) {
            ResourceTimers.tickTree(oak, 0, false);
        }
        assertEquals(39, oak.ticksLeft);
        for (int i = 0; i < 20; i++) {
            ResourceTimers.tickTree(oak, 0, false);
        }
        assertEquals(45, oak.ticksLeft);
        assertFalse(oak.visible());
    }

    @Test
    public void calcifiedRockResetsAfterBeingLeftAlone() {
        ResourceTimers.Timer rock = new ResourceTimers.Timer(ResourceTimers.Kind.CALCIFIED, null, null, 117, 117);
        rock.started = true;
        ResourceTimers.tickCalcified(rock, true);
        assertEquals(116, rock.ticksLeft);
        for (int i = 0; i < 56; i++) {
            ResourceTimers.tickCalcified(rock, false);
        }
        assertFalse(rock.started);
        assertEquals(117, rock.ticksLeft);
    }

    @Test
    public void animationsAreFound() {
        assertTrue(ResourceTimers.CHOP_ANIMATIONS.contains(867)); // rune axe
        assertTrue(ResourceTimers.CHOP_ANIMATIONS.contains(10071)); // dragon felling axe
        assertTrue(ResourceTimers.MINE_ANIMATIONS.contains(624)); // rune pickaxe
    }

    @Test
    public void campfireLogsByXp() {
        assertEquals(31, ResourceTimers.campfireLog(202.5)[2], 0); // yew logs add 31 ticks
        assertEquals(31, ResourceTimers.campfireLog(207.5)[2], 0); // with a pyromancer piece
        assertEquals(102, ResourceTimers.campfireLog(40)[1], 0);
        assertTrue(ResourceTimers.campfireLog(12) == null);
    }
}
