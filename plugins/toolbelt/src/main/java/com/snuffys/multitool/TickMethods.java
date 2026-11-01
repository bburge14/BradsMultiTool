package com.snuffys.multitool;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Tick Method setting's timing patterns: numbered boxes, each lit on certain ticks of the cycle and in a certain
 * half of the tick. They only show timing: what each box stands for is up to you (no labels, nothing clicked).
 */
final class TickMethods {

    /** Which part of the tick a step lights for. */
    enum Half { WHOLE, FIRST, SECOND }

    static final class Step {
        final String label;
        final List<Integer> ticks;
        final Half half;

        Step(int number, Half half, Integer... ticks) {
            this.label = String.valueOf(number);
            this.half = half;
            this.ticks = Arrays.asList(ticks);
        }

        /** Lit now: on one of its ticks, in its half. */
        boolean lit(int count, long sinceTick) {
            if (!ticks.contains(count)) {
                return false;
            }
            boolean secondHalf = sinceTick >= BradsMultiToolTickBoxOverlay.TICK_MILLIS / 2;
            return half == Half.WHOLE || (half == Half.SECOND) == secondHalf;
        }
    }

    private TickMethods() {
    }

    /** The method's cycle length in ticks (0 for Custom: Ticks Per Cycle). */
    static int cycle(TickConfig.TickMethod method) {
        switch (method) {
            case ONE_TICK_HALVES:
                return 1;
            case THREE_TICK:
                return 3;
            default:
                return 0;
        }
    }

    /** The pattern's boxes, numbered in order; empty for Custom (the boxes from Ticks Per Cycle). */
    static List<Step> steps(TickConfig.TickMethod method) {
        switch (method) {
            case ONE_TICK_HALVES:
                // Box 1 for the first half of each tick, box 2 for the second
                return Arrays.asList(new Step(1, Half.FIRST, 1), new Step(2, Half.SECOND, 1));
            case THREE_TICK:
                // Boxes 1, 2 and 3 together on tick 1; box 4 on ticks 2 and 3
                return Arrays.asList(new Step(1, Half.WHOLE, 1), new Step(2, Half.WHOLE, 1),
                    new Step(3, Half.WHOLE, 1), new Step(4, Half.WHOLE, 2, 3));
            default:
                return Collections.emptyList();
        }
    }

    /** The most steps any method has (separate boxes made for each). */
    static final int MOST_STEPS = 4;
}
