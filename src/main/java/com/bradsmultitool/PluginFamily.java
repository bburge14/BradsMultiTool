package com.bradsmultitool;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final List<Runnable> helloListeners = new CopyOnWriteArrayList<>();

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
        helloListeners.clear();
        voiceSettings = null;
    }

    /** Whether another of Snuffy's plugins ("voices", "pvm"...) is running. */
    boolean isRunning(String plugin) {
        return others.contains(plugin);
    }

    // Who runs the warnings every plugin has (idle, logout) when several are installed, so they're said once
    // (and tagged with its name in chat): the first of these that's running. The all-in-one comes first, so
    // with it installed every message says Snuffy's Multi-Tool (in the Voices plugin's voice, if that runs)
    private static final List<String> SHARED_ORDER = Arrays.asList("multitool", VOICES, "skilltracker", "pvm", "gemcrab");

    /** Whether this plugin should run the shared idle and logout warnings (no other of ours ahead of it is running). */
    boolean handlesShared() {
        int mine = SHARED_ORDER.indexOf(self);
        for (String other : others) {
            int theirs = SHARED_ORDER.indexOf(other);
            if (theirs >= 0 && (mine < 0 || theirs < mine)) {
                return false;
            }
        }
        return true;
    }

    /** Sends this plugin's voice settings to the others (the Voices plugin does, on start and when asked). */
    void sendVoiceSettings(VoiceAlertService.VoicePersona persona, int volume, boolean enabled) {
        Map<String, Object> data = new HashMap<>();
        data.put("plugin", self);
        data.put("persona", persona.name());
        data.put("volume", volume);
        data.put("enabled", enabled);
        eventBus.post(new PluginMessage(NAMESPACE, "voice-settings", data));
    }

    /** Called when another of ours says hello (the Voices plugin answers with its voice settings). */
    void onHello(Runnable action) {
        helloListeners.add(action);
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
                helloListeners.forEach(Runnable::run);
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
