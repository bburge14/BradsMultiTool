package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Snuffy's PvM/Slayer: the PvM HUD and monster hover card, buffs and boosts, health and prayer, combat
 * alerts, and the Slayer card and task outlines.
 */
@Singleton
class PvmFeature {

    @Inject private PvmTracker pvm;
    @Inject private MonsterData monsterData;
    @Inject private SlayerData slayerData;
    @Inject private CombatStyle combatStyle;
    @Inject private CombatAlerts combatAlerts;
    @Inject private PotionBuffs potionBuffs;
    @Inject private PotionAlerts potionAlerts;
    @Inject private StatusEffects statusEffects;
    @Inject private MonsterEffects monsterEffects;
    @Inject private BradsMultiToolBuffBarOverlay buffBarOverlay;
    @Inject private BradsMultiToolOverlay hudOverlay;
    @Inject private BradsMultiToolPrayerOverlay prayerOverlay;
    @Inject private BradsMultiToolHoverOverlay hoverOverlay;
    @Inject private BradsMultiToolOrbOverlay orbOverlay;
    @Inject private BradsMultiToolSlayerOverlay slayerOverlay;
    @Inject private BradsMultiToolSlayerHighlightOverlay slayerHighlightOverlay;

    PvmTracker tracker() {
        return pvm;
    }

    BradsMultiToolHoverOverlay hoverCard() {
        return hoverOverlay;
    }


    @Inject private ConfigDefaults configDefaults;
    @Inject private PvmConfig config;

    void start(Lifecycle lifecycle) {
        configDefaults.add(() -> {
            PvmConfigDefaults.store(configDefaults, config);
            BuffConfigDefaults.store(configDefaults, config);
        });
        lifecycle.load(monsterData, monsterData::load);
        lifecycle.load(slayerData, slayerData::load);
        lifecycle.subscribe(pvm, potionBuffs, potionAlerts, statusEffects, monsterEffects, combatStyle, combatAlerts);
        lifecycle.overlay(hudOverlay, prayerOverlay, hoverOverlay, orbOverlay, slayerOverlay, slayerHighlightOverlay, buffBarOverlay);
        pvm.startUp();
        lifecycle.onStop(pvm::shutDown);
    }
}
