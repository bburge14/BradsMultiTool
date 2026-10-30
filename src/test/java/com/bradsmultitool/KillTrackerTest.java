package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class KillTrackerTest {

    @Test
    public void paceFromRecentKills() {
        // 5 kills over 4 minutes: one a minute, 60 an hour
        KillTracker.Pace pace = KillTracker.pace(5, 0, 4 * 60_000L);
        assertEquals(60, pace.perHour);
        assertEquals(60_000L, pace.millisEach);
        assertNull(KillTracker.pace(1, 0, 0));
    }
}
