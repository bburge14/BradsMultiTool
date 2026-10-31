package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ResourceTimerOverlayTest {

    @Test
    public void treesGiveTheirLogs() {
        assertEquals("Logs", BradsMultiToolResourceTimerOverlay.logsOf("Tree"));
        assertEquals("Logs", BradsMultiToolResourceTimerOverlay.logsOf("Dead tree"));
        assertEquals("Oak logs", BradsMultiToolResourceTimerOverlay.logsOf("Oak tree"));
        assertEquals("Magic logs", BradsMultiToolResourceTimerOverlay.logsOf("Magic tree"));
        assertEquals("Arctic pine logs", BradsMultiToolResourceTimerOverlay.logsOf("Arctic pine"));
        assertNull(BradsMultiToolResourceTimerOverlay.logsOf(null));
    }

    @Test
    public void randomTimersShowTheirWindow() {
        // Fishing spots: 250-530 ticks
        assertEquals(0, ResourceTimers.Timer.windowPassed(100, 250, 530), 0.001);
        assertEquals(0, ResourceTimers.Timer.windowPassed(250, 250, 530), 0.001);
        assertEquals(0.5, ResourceTimers.Timer.windowPassed(390, 250, 530), 0.001);
        assertEquals(1, ResourceTimers.Timer.windowPassed(600, 250, 530), 0.001);
        assertEquals("2:30", BradsMultiToolResourceTimerOverlay.clock(250));
        assertEquals("0:06", BradsMultiToolResourceTimerOverlay.clock(10));
    }

    @Test
    public void timersSayTheirTime() {
        ResourceTimers.Timer spot = new ResourceTimers.Timer(ResourceTimers.Kind.FISHING_SPOT, null, null, 250, 530);
        spot.elapsed = 100;
        assertEquals("1:00", BradsMultiToolResourceTimerOverlay.timerText(spot)); // how long ago it appeared
        ResourceTimers.Timer vein = new ResourceTimers.Timer(ResourceTimers.Kind.VEIN, null, null, 40, 45);
        vein.elapsed = 5;
        assertEquals("0:24", BradsMultiToolResourceTimerOverlay.timerText(vein)); // time left
        ResourceTimers.Timer trap = new ResourceTimers.Timer(ResourceTimers.Kind.TRAP, null, null, 100, 100);
        trap.trapState = ResourceTimers.TrapState.CAUGHT;
        assertEquals("Caught", BradsMultiToolResourceTimerOverlay.timerText(trap));
    }
}
