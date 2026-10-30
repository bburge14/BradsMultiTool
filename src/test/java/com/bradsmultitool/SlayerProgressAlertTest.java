package com.bradsmultitool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the halfway and nearly done alerts go off: once, on the kill that reaches the mark. */
public class SlayerProgressAlertTest {

    @Test
    public void firesOnTheKillThatReachesTheMark() {
        // A task of 140: halfway is 70 left
        assertTrue(CombatAlerts.crossed(71, 70, 70));
        assertFalse(CombatAlerts.crossed(70, 69, 70));
        assertFalse(CombatAlerts.crossed(72, 71, 70));
        // Two kills at once (a cannon) still count
        assertTrue(CombatAlerts.crossed(72, 69, 70));
        // Nearly done at 10 left
        assertTrue(CombatAlerts.crossed(11, 10, 10));
        assertFalse(CombatAlerts.crossed(11, 10, 0));
    }
}
