package com.snuffys.gemcrab;

import javax.inject.Inject;
import javax.inject.Singleton;

/** What every one of Snuffy's plugins has: alerts (voice and chat), the idle warnings, run energy alerts and finding the others. */
@Singleton
class CommonFeature {

    @Inject
    private PluginFamily pluginFamily;
    @Inject
    private Alerts alerts;
    @Inject
    private IdleWarnings idleWarnings;
    @Inject
    private RunEnergyAlerts runEnergyAlerts;
    @Inject
    private BradsMultiToolIdleWarningOverlay idleWarningOverlay;
    @Inject
    private ConfigDefaults configDefaults;
    @Inject
    private InGame inGame;
    @Inject
    private VoiceSync voiceSync;
    @Inject
    private CommonConfig config;
    @Inject
    private PluginClashes clashes;

    void start(Lifecycle lifecycle) {
        lifecycle.subscribe(inGame, alerts, idleWarnings, runEnergyAlerts, configDefaults, voiceSync, clashes);
        configDefaults.add(() -> CommonConfigDefaults.store(configDefaults, config));
        lifecycle.overlay(idleWarningOverlay);
        voiceSync.start();
        pluginFamily.start();
        lifecycle.onStop(() -> {
            pluginFamily.stop();
            alerts.shutDown();
            clashes.stop();
            configDefaults.clear();
        });
    }
}
