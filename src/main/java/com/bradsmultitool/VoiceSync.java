package com.bradsmultitool;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;

/**
 * Same Voice For All: when you change the voice or volume in one of Snuffy's plugins, the others running (with
 * the setting on) change theirs to match, and one that starts later takes it from them. Each plugin only ever
 * changes its own settings, from a {@link PluginMessage} ("voice-sync": "persona", "volume").
 */
@Singleton
class VoiceSync {

    private static final String MESSAGE = "voice-sync";

    private final EventBus eventBus;
    private final ConfigManager configManager;
    private final CommonConfig config;
    private final PluginIdentity identity;
    private final PluginFamily family;
    // While we're taking another plugin's voice: don't send it back, and don't play the change-voice sample
    private boolean applying;

    @Inject
    VoiceSync(EventBus eventBus, ConfigManager configManager, CommonConfig config, PluginIdentity identity, PluginFamily family) {
        this.eventBus = eventBus;
        this.configManager = configManager;
        this.config = config;
        this.identity = identity;
        this.family = family;
    }

    void start() {
        // A plugin that starts after us takes our voice
        family.onHello(this::send);
    }

    /** Whether the voice is being changed to match another plugin's (not by the player here). */
    boolean isApplying() {
        return applying;
    }

    private void send() {
        if (!config.syncVoice()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("plugin", identity.familyId);
        data.put("persona", config.voicePersona().name());
        data.put("volume", config.voiceVolume());
        eventBus.post(new PluginMessage(PluginFamily.NAMESPACE, MESSAGE, data));
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        // You changed it here (not a default being saved, nor us matching another plugin): tell the others
        if (applying || !identity.configGroup.equals(event.getGroup()) || event.getOldValue() == null) {
            return;
        }
        if ("voicePersona".equals(event.getKey()) || "voiceVolume".equals(event.getKey())
            || ("syncVoice".equals(event.getKey()) && config.syncVoice())) {
            send();
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessage message) {
        Map<String, Object> data = message.getData();
        if (!PluginFamily.NAMESPACE.equals(message.getNamespace()) || !MESSAGE.equals(message.getName())
            || identity.familyId.equals(data.get("plugin")) || !config.syncVoice()) {
            return;
        }
        VoiceAlertService.VoicePersona persona;
        try {
            persona = VoiceAlertService.VoicePersona.valueOf(String.valueOf(data.get("persona")));
        } catch (IllegalArgumentException e) {
            return;
        }
        int volume = data.get("volume") instanceof Number
            ? Math.max(0, Math.min(100, ((Number) data.get("volume")).intValue())) : config.voiceVolume();
        applying = true;
        try {
            if (persona != config.voicePersona()) {
                configManager.setConfiguration(identity.configGroup, "voicePersona", persona);
            }
            if (volume != config.voiceVolume()) {
                configManager.setConfiguration(identity.configGroup, "voiceVolume", volume);
            }
        } finally {
            applying = false;
        }
    }
}
