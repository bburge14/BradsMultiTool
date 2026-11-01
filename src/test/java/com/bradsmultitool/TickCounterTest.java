package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.inject.Singleton;
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

    // Skilling Helper and PvM/Slayer both show the tick overlays: in the Toolbelt they must be one instance each,
    // or each is drawn twice
    @Test
    public void tickOverlaysAreShared() {
        for (Class<?> overlay : new Class<?>[]{BradsMultiToolTickBoxOverlay.class, BradsMultiToolTickBarOverlay.class,
            BradsMultiToolTickHeadOverlay.class, TickCounter.class}) {
            assertTrue(overlay.getSimpleName() + " must be @Singleton", overlay.isAnnotationPresent(Singleton.class));
        }
    }

    @org.junit.Test
    public void halfBoxesAreLabelled() {
        org.junit.Assert.assertEquals("1", BradsMultiToolTickBoxOverlay.label(1, false));
        org.junit.Assert.assertEquals("1.5", BradsMultiToolTickBoxOverlay.label(1, true));
    }

    @org.junit.Test
    public void tickMethodStepsLightOnTime() {
        java.util.List<TickMethods.Step> halves = TickMethods.steps(TickConfig.TickMethod.ONE_TICK_HALVES);
        org.junit.Assert.assertEquals(1, TickMethods.cycle(TickConfig.TickMethod.ONE_TICK_HALVES));
        org.junit.Assert.assertTrue(halves.get(0).lit(1, 100));   // box 1: first half
        org.junit.Assert.assertFalse(halves.get(0).lit(1, 400));
        org.junit.Assert.assertTrue(halves.get(1).lit(1, 400));   // box 2: second half
        java.util.List<TickMethods.Step> three = TickMethods.steps(TickConfig.TickMethod.THREE_TICK);
        org.junit.Assert.assertTrue(three.get(0).lit(1, 100));          // box 1 on tick 1
        org.junit.Assert.assertFalse(three.get(0).lit(2, 100));
        org.junit.Assert.assertTrue(three.get(3).lit(3, 500));          // box 4 on ticks 2 and 3
        org.junit.Assert.assertTrue(TickMethods.steps(TickConfig.TickMethod.CUSTOM).isEmpty());
    }
}

