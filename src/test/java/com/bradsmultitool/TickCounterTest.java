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
        org.junit.Assert.assertTrue(TickMethods.steps(TickConfig.TickMethod.CUSTOM).isEmpty());
    }

    // Follow Attack Speed: a 4-tick weapon attacking, then idling: 4 3 2 1 to the next attack, then 1, 2, 3 late
    @Test
    public void attackTimerCountsDownThenLate() {
        AttackTimer timer = new AttackTimer();
        assertTrue(timer.tick(true, 4, 5));
        StringBuilder seen = new StringBuilder().append(timer.delay());
        for (int i = 0; i < 6; i++) {
            timer.tick(false, 4, 5);
            seen.append(' ').append(timer.delay() > 0 ? String.valueOf(timer.delay()) : "-" + timer.late());
        }
        assertEquals("4 3 2 1 -1 -2 -3", seen.toString());
        assertTrue(timer.tracking());
        // Past Late Ticks it stops counting
        timer.tick(false, 4, 5);
        timer.tick(false, 4, 5);
        timer.tick(false, 4, 5);
        org.junit.Assert.assertFalse(timer.tracking());
    }

    @Test
    public void anAttackOnlyCountsOnceTheDelayHasRunOut() {
        AttackTimer timer = new AttackTimer();
        timer.tick(true, 5, 5);
        // An animation two ticks later (blocking a hit that slipped through, say) isn't an attack
        timer.tick(false, 5, 5);
        org.junit.Assert.assertFalse(timer.tick(true, 5, 5));
        timer.tick(false, 5, 5);
        timer.tick(false, 5, 5);
        // The fifth tick: the next attack, on time
        assertTrue(timer.tick(true, 5, 5));
        assertEquals(0, timer.late());
    }

    @Test
    public void eatingDelaysTheNextAttack() {
        AttackTimer timer = new AttackTimer();
        timer.tick(true, 4, 5);
        timer.tick(false, 4, 5);          // 3 to go
        timer.ate(AttackTimer.foodDelay("You eat the shark."));
        timer.tick(false, 4, 5);          // 2, plus 3 for the food
        assertEquals(5, timer.delay());
        // The count holds on 1 while the food's extra ticks pass
        assertEquals(1, TickCounter.followCount(4, 5));
        assertEquals(4, TickCounter.followCount(4, 1));
        // Food eaten on the attack tick counts too
        AttackTimer fresh = new AttackTimer();
        fresh.ate(2);
        fresh.tick(true, 4, 5);
        assertEquals(6, fresh.delay());
    }

    @Test
    public void foodDelays() {
        assertEquals(3, AttackTimer.foodDelay("You eat the shark."));
        assertEquals(2, AttackTimer.foodDelay("You eat the karambwan."));
        assertEquals(2, AttackTimer.foodDelay("You eat the Halibut."));
        assertEquals(3, AttackTimer.foodDelay("You drink the wine."));
        assertEquals(4, AttackTimer.foodDelay("Your stomach doesn't like it... but it heals some health."));
        assertEquals(0, AttackTimer.foodDelay("You drink some of your super combat potion."));
        assertEquals(0, AttackTimer.foodDelay(null));
    }

    @Test
    public void aSwapOnTheAttackTickTakesTheNewSpeed() {
        AttackTimer timer = new AttackTimer();
        timer.tick(true, 4, 5);
        timer.respeed(6);
        assertEquals(6, timer.delay());
        assertEquals(6, timer.speed());
    }

    @Test
    public void countingDownLabelsHalfBoxesBetweenNumbers() {
        assertEquals("4", BradsMultiToolTickBoxOverlay.label(4, false, true));
        assertEquals("3.5", BradsMultiToolTickBoxOverlay.label(4, true, true));
        assertEquals("1.5", BradsMultiToolTickBoxOverlay.label(1, true, false));
    }

    @Test
    public void runEnergyRestoredNeedsADropFirst() {
        assertTrue(RunEnergyAlerts.droppedEnough(90, 100));
        org.junit.Assert.assertFalse(RunEnergyAlerts.droppedEnough(95, 100));
        assertTrue(RunEnergyAlerts.isLow(14, 15));
        org.junit.Assert.assertFalse(RunEnergyAlerts.isLow(14, 0));
    }

    @Test
    public void attackAnimationsLeaveOutEatingAndBlocking() {
        assertTrue(AttackTimer.NOT_ATTACKS.contains(net.runelite.api.gameval.AnimationID.HUMAN_EAT));
        assertTrue(AttackTimer.NOT_ATTACKS.contains(net.runelite.api.gameval.AnimationID.HUMAN_SHIELD_DEFENCE));
        org.junit.Assert.assertFalse(AttackTimer.NOT_ATTACKS.contains(net.runelite.api.gameval.AnimationID.SLAYER_ABYSSAL_WHIP_ATTACK));
    }
}

