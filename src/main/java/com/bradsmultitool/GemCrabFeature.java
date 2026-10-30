package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Snuffy's Gemstone Crab: the crab HUD and radar, outlines and the crab alerts. */
@Singleton
class GemCrabFeature {

    @Inject private GemCrabTracker gemCrab;
    @Inject private IdleWarnings idleWarnings;
    @Inject private CrabHudOverlay crabHudOverlay;
    @Inject private BradsMultiToolCrabOutlineOverlay crabOutlineOverlay;

    GemCrabTracker tracker() {
        return gemCrab;
    }

    CrabHudOverlay hud() {
        return crabHudOverlay;
    }

    void start(Lifecycle lifecycle) {
        lifecycle.subscribe(gemCrab);
        lifecycle.overlay(crabHudOverlay, crabOutlineOverlay);
        // Hitting the crab counts as busy for the idle alert (its hits come slowly)
        java.util.function.BooleanSupplier attacking = gemCrab::isPlayerAttackingCrab;
        idleWarnings.addBusy(attacking);
        lifecycle.onStop(() -> {
            idleWarnings.removeBusy(attacking);
            gemCrab.reset();
        });
    }
}
