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
    private CommonConfig config;

    void start(Lifecycle lifecycle) {
        lifecycle.subscribe(alerts, idleWarnings, configDefaults);
        configDefaults.add(() -> CommonConfigDefaults.store(configDefaults, config));
        lifecycle.overlay(idleWarningOverlay);
        pluginFamily.start();
        lifecycle.onStop(() -> {
            pluginFamily.stop();
            alerts.shutDown();
            configDefaults.clear();
        });
    }
}
