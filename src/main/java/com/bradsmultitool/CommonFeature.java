package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;

/** What every one of Snuffy's plugins has: alerts (voice and chat), the idle warnings and finding the others. */
@Singleton
class CommonFeature {

    @Inject
    private PluginFamily pluginFamily;
    @Inject
    private Alerts alerts;
    @Inject
    private IdleWarnings idleWarnings;
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
    @Inject
    private ClueDrops clueDrops;
    @Inject
    private DailyReminders dailyReminders;

    void start(Lifecycle lifecycle) {
        lifecycle.subscribe(inGame, alerts, idleWarnings, configDefaults, voiceSync, clashes, clueDrops, dailyReminders);
        dailyReminders.start();
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
