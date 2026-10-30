package com.snuffys.voices;

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

    void start(Lifecycle lifecycle) {
        lifecycle.subscribe(alerts, idleWarnings);
        lifecycle.overlay(idleWarningOverlay);
        pluginFamily.start();
        lifecycle.onStop(() -> {
            pluginFamily.stop();
            alerts.shutDown();
        });
    }
}
