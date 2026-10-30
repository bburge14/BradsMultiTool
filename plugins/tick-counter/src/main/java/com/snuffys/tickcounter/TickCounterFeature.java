package com.snuffys.tickcounter;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Snuffy's Tick Counter: the tick count over your head, the tick boxes and bar, the metronome and click timing. */
@Singleton
class TickCounterFeature {

    @Inject private TickCounter tickCounter;
    @Inject private InGame inGame;
    @Inject private CombatStyle combatStyle;
    @Inject private ConfigDefaults configDefaults;
    @Inject private TickConfig config;
    @Inject private BradsMultiToolTickHeadOverlay headOverlay;
    @Inject private BradsMultiToolTickBoxOverlay boxOverlay;
    @Inject private BradsMultiToolTickBarOverlay barOverlay;

    void start(Lifecycle lifecycle) {
        configDefaults.add(() -> TickConfigDefaults.store(configDefaults, config));
        // CombatStyle knows the weapon's style (Rapid) for Follow Attack Speed
        lifecycle.subscribe(inGame, combatStyle, tickCounter, configDefaults);
        lifecycle.overlay(headOverlay, boxOverlay, barOverlay);
        lifecycle.onStop(configDefaults::clear);
    }
}
