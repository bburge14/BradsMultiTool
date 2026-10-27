package com.bradsmultitool;

/**
 * Time left on a stat boost from a regular potion. Boosted stats drop by 1 on a fixed timer: every
 * 100 game ticks (60s), or 150 ticks (90s) while Preserve is active. Once one drop has been seen the
 * timer's phase is known, so the time until the boost runs out is exact; before that only an upper
 * bound is known. (Same mechanic RuneLite's Boosts plugin uses for "next change".)
 */
final class BuffTimer {

    static final int DECAY_TICKS = 100;
    static final int PRESERVE_DECAY_TICKS = 150;

    private int lastDecayTick = -1;

    /** Call when a boosted stat drops by exactly 1 while still above its real level. */
    void onDecay(int tick) {
        lastDecayTick = tick;
    }

    void reset() {
        lastDecayTick = -1;
    }

    boolean isSynced() {
        return lastDecayTick >= 0;
    }

    /**
     * Ticks until a boost of {@code boost} levels is gone: exact once synced, otherwise the upper bound
     * (the next drop could be a full cycle away). 0 when there's no boost.
     */
    int ticksRemaining(int tick, int boost, boolean preserve) {
        if (boost <= 0) {
            return 0;
        }
        int period = preserve ? PRESERVE_DECAY_TICKS : DECAY_TICKS;
        if (!isSynced()) {
            return boost * period;
        }
        int sinceDrop = Math.floorMod(tick - lastDecayTick, period);
        return (period - sinceDrop) + (boost - 1) * period;
    }

    /** Game ticks as m:ss. */
    static String format(int ticks) {
        int seconds = (int) Math.ceil(ticks * 0.6);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
