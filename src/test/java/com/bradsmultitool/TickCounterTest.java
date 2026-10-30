package com.bradsmultitool;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TickCounterTest {

    @Test
    public void countsOneToCycleAndStartsOver() {
        int count = 0;
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            count = TickCounter.next(count, 3, false);
            seen.append(count);
        }
        assertEquals("1231231", seen.toString());
    }

    @Test
    public void anAttackStartsTheCycleAgain() {
        assertEquals(1, TickCounter.next(2, 5, true));
        // A shorter cycle (new weapon) wraps straight away
        assertEquals(1, TickCounter.next(5, 4, false));
    }
}
