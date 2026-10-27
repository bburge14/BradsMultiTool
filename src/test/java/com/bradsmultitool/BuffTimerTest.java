package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BuffTimerTest {

    @Test
    public void upperBoundBeforeAnyDropIsSeen() {
        BuffTimer timer = new BuffTimer();
        assertFalse(timer.isSynced());
        assertEquals(16 * 100, timer.ticksRemaining(500, 16, false));
        assertEquals(4 * 150, timer.ticksRemaining(500, 4, true));
    }

    @Test
    public void exactOnceADropIsSeen() {
        BuffTimer timer = new BuffTimer();
        timer.onDecay(1000);
        assertTrue(timer.isSynced());
        // 40 ticks after a drop: 60 ticks to the next one, then 15 more full minutes
        assertEquals(60 + 15 * 100, timer.ticksRemaining(1040, 16, false));
        // Phase carries across cycles with no boost in between
        assertEquals(100, timer.ticksRemaining(1300, 1, false));
        assertEquals(0, timer.ticksRemaining(1300, 0, false));
    }

    @Test
    public void formatsTicksAsMinutesAndSeconds() {
        assertEquals("16:00", BuffTimer.format(1600));
        assertEquals("0:36", BuffTimer.format(60));
        assertEquals("0:01", BuffTimer.format(1));
    }
}
