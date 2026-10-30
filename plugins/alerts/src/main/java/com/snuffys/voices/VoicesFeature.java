package com.snuffys.voices;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Snuffy's Alerts: sends its voice, volume and on/off to the other plugins of ours that are running (when it
 * starts, when one of them starts, and when you change them), so they all speak the same way.
 */
@Singleton
class VoicesFeature {

    @Inject private PluginFamily family;
    @Inject private VoicesConfig config;
    @Inject private PluginIdentity identity;

    void start(Lifecycle lifecycle) {
        lifecycle.subscribe(this);
        family.onHello(this::send);
        send();
    }

    private void send() {
        family.sendVoiceSettings(config.voicePersona(), config.voiceVolume(), config.voiceEnabled());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (identity.configGroup.equals(event.getGroup())) {
            send();
        }
    }
}
