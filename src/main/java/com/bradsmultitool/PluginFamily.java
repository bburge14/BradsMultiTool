package com.bradsmultitool;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

/**
 * Lets Snuffy's plugins find each other once they're split up (docs/DEVELOPMENT.md "Planned: separate
 * plugins"), with RuneLite's {@link PluginMessage} events, the way Plugin Hub plugins talk to each other.
 * Every message is in the "snuffys" namespace and carries the sender's id as "plugin":
 * <ul>
 * <li>"hello": a plugin started; everyone else answers "here".</li>
 * <li>"bye": a plugin stopped.</li>
 * <li>"voice-settings" (from the Voices plugin): "persona" (a VoicePersona name), "volume" (0-100) and
 * "enabled". While the Voices plugin is installed, every plugin speaks with its voice and volume; each
 * plugin's own switches for its lines still apply.</li>
 * </ul>
 */
@Singleton
class PluginFamily {

    static final String NAMESPACE = "snuffys";
    static final String VOICES = "voices";

    /** The voice the Voices plugin asked everyone to use. */
    static final class VoiceSettings {
        final VoiceAlertService.VoicePersona persona;
        final int volume;
        final boolean enabled;

        VoiceSettings(VoiceAlertService.VoicePersona persona, int volume, boolean enabled) {
            this.persona = persona;
            this.volume = volume;
            this.enabled = enabled;
        }
    }

    private final EventBus eventBus;
    // This build's id ("multitool", "skilltracker", "pvm", "gemcrab", "voices")
    private final String self;
    private final Set<String> others = ConcurrentHashMap.newKeySet();
    private volatile VoiceSettings voiceSettings;

    @Inject
    PluginFamily(EventBus eventBus, PluginIdentity identity) {
        this.eventBus = eventBus;
        this.self = identity.familyId;
    }

    void start() {
        eventBus.register(this);
        post("hello");
    }

    void stop() {
        post("bye");
        eventBus.unregister(this);
        others.clear();
        voiceSettings = null;
    }

    /** Whether another of Snuffy's plugins ("voices", "pvm"...) is running. */
    boolean isRunning(String plugin) {
        return others.contains(plugin);
    }

    /** The Voices plugin's voice settings, or null when it isn't running (use this plugin's own). */
    VoiceSettings voiceSettings() {
        return others.contains(VOICES) ? voiceSettings : null;
    }

    @Subscribe
    public void onPluginMessage(PluginMessage message) {
        if (!NAMESPACE.equals(message.getNamespace())) {
            return;
        }
        Map<String, Object> data = message.getData();
        Object sender = data.get("plugin");
        if (!(sender instanceof String) || self.equals(sender)) {
            return;
        }
        String plugin = (String) sender;
        switch (message.getName()) {
            case "hello":
                others.add(plugin);
                post("here");
                break;
            case "here":
                others.add(plugin);
                break;
            case "bye":
                others.remove(plugin);
                break;
            case "voice-settings":
                others.add(plugin);
                voiceSettings = parseVoiceSettings(data);
                break;
            default:
                break;
        }
    }

    static VoiceSettings parseVoiceSettings(Map<String, Object> data) {
        try {
            VoiceAlertService.VoicePersona persona = VoiceAlertService.VoicePersona.valueOf(String.valueOf(data.get("persona")));
            int volume = data.get("volume") instanceof Number ? ((Number) data.get("volume")).intValue() : 100;
            boolean enabled = !Boolean.FALSE.equals(data.get("enabled"));
            return new VoiceSettings(persona, Math.max(0, Math.min(100, volume)), enabled);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void post(String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("plugin", self);
        eventBus.post(new PluginMessage(NAMESPACE, name, data));
    }
}
