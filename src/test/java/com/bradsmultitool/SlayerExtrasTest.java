package com.bradsmultitool;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

/** Slayer streak milestones, saved supplies, kill goals and the finish-off marker's numbers. */
public class SlayerExtrasTest {

    @Test
    public void nextTaskMilestones() {
        assertEquals(10, SlayerTracker.nextMilestone(9));
        assertEquals(50, SlayerTracker.nextMilestone(49));
        assertEquals(100, SlayerTracker.nextMilestone(99));
        assertEquals(250, SlayerTracker.nextMilestone(249));
        assertEquals(250, SlayerTracker.nextMilestone(499));
        assertEquals(1000, SlayerTracker.nextMilestone(999));
        assertEquals(0, SlayerTracker.nextMilestone(10));
        assertEquals(0, SlayerTracker.nextMilestone(-1));
        assertEquals(15, SlayerTracker.milestoneMultiplier(50));
        assertEquals(0, SlayerTracker.milestoneMultiplier(7));
    }

    @Test
    public void savedSupplies() {
        assertArrayEquals(new int[]{42, 180, 12, 8}, SlayerTracker.parseSupplies("42|180|12|8"));
        assertNull(SlayerTracker.parseSupplies("42|180|x|8"));
        assertNull(SlayerTracker.parseSupplies(null));
    }

    @Test
    public void killGoalsRoundTrip() {
        Map<String, KillGoals.Goal> goals = KillGoals.decode("Gargoyle|200|37;Desert Lizard|50|50;broken");
        assertEquals(2, goals.size());
        KillGoals.Goal gargoyle = goals.get("gargoyle");
        assertEquals("37 / 200", gargoyle.progress());
        assertFalse(gargoyle.done());
        assertTrue(goals.get("desert lizard").done());
        assertEquals("Gargoyle|200|37;Desert Lizard|50|50", KillGoals.encode(goals.values()));
        assertEquals(150, KillGoals.parseTarget(" 150 "));
        assertEquals(0, KillGoals.parseTarget("lots"));
        assertEquals(0, KillGoals.parseTarget("0"));
    }

    @Test
    public void finishOffThresholds() {
        assertEquals(9, BradsMultiToolFinishOffOverlay.finisherFor("Gargoyle").threshold);
        assertEquals(4, BradsMultiToolFinishOffOverlay.finisherFor("Desert Lizard").threshold);
        assertNull(BradsMultiToolFinishOffOverlay.finisherFor("Lizardman shaman"));
        // A gargoyle (105 HP) with 3/30 of its bar left has about 11 HP: not yet; 2/30 is 7
        assertEquals(11, BradsMultiToolFinishOffOverlay.health(3, 30, 105));
        assertEquals(7, BradsMultiToolFinishOffOverlay.health(2, 30, 105));
        assertEquals(-1, BradsMultiToolFinishOffOverlay.health(-1, 30, 105));
    }
}
