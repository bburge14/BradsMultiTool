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
}
