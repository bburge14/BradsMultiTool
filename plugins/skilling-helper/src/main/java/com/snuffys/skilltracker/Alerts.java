package com.snuffys.skilltracker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientUI;

/**
 * Spoken and chat alerts, shared by every feature: each alert's voice and chat switches, the voice and volume
 * (the Alerts plugin's while it runs, see PluginFamily), and holding everything back until you're really in
 * the game.
 */
@Singleton
class Alerts {

    private final Client client;
    private final CommonConfig config;
    private final ConfigManager configManager;
    private final PluginFamily family;
    private final PluginIdentity identity;
    private final InGame inGame;
    private final VoiceSync voiceSync;
    private final ClientUI clientUI;
    private final VoiceAlertService voice;

    @Inject
    Alerts(Client client, CommonConfig config, ConfigManager configManager, PluginFamily family, PluginIdentity identity, InGame inGame,
           VoiceSync voiceSync, ClientUI clientUI, net.runelite.client.audio.AudioPlayer audioPlayer) {
        this.voice = new VoiceAlertService(audioPlayer);
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.family = family;
        this.identity = identity;
        this.inGame = inGame;
        this.voiceSync = voiceSync;
        this.clientUI = clientUI;
    }

    void shutDown() {
        voice.shutdown();
    }

    /**
     * True once you're actually in the game: past the welcome screen for a few ticks. Before that, alerts
     * stay silent and anything already true at login (low prayer, say) counts as already announced.
     */
    boolean ready() {
        return inGame.ready();
    }

    /** Speaks an alert, if you're in game and its voice is turned on. */
    void speak(VoiceAlertService.VoiceLine line) {
        speak(AlertType.of(line), line);
    }

    // Under this alert's switches (a line two alerts share: Agility's run energy and the one everywhere)
    private void speak(AlertType type, VoiceAlertService.VoiceLine line) {
        if (ready() && voiceEnabled() && toggle(type.voiceKey) && speakingNow()) {
            voice.speakAsync(line, persona(), volume());
        }
    }

    /** Speaks the line and posts the same words in chat, with any detail in brackets. */
    void alert(VoiceAlertService.VoiceLine line, String detail) {
        alert(AlertType.of(line), line, detail);
    }

    /** As above, under this alert's Voice and Chat switches. */
    void alert(AlertType type, VoiceAlertService.VoiceLine line, String detail) {
        speak(type, line);
        chat(type, detail == null ? line.text : line.text + " (" + detail + ")");
    }

    // Speak Only When Unfocused: alerts stay quiet while you're using the RuneLite window (like RuneLite's
    // notifications, which check the same thing); previews of a setting you just changed still play
    private boolean speakingNow() {
        return !config.voiceOnlyUnfocused() || clientUI == null || !clientUI.isFocused();
    }

    /** Posts an alert in chat, if you're in game and its chat message is turned on. */
    void chat(AlertType type, String message) {
        chat(type.chatKey, type.reminder, message);
    }

    /** A chat-only message under its own switch ({@code chatKey}), to the game chat. */
    void chat(String chatKey, String message) {
        chat(chatKey, false, message);
    }

    private void chat(String chatKey, boolean reminder, String message) {
        if (config.sendChatMessages() && ready() && toggle(chatKey)) {
            // The Planner's reminders go where friends' logins show: the split private chat (when that's on in the
            // game's settings), which fades after a while, instead of staying in the chat box
            ChatMessageType channel = reminder ? ChatMessageType.LOGINLOGOUTNOTIFICATION : ChatMessageType.GAMEMESSAGE;
            client.addChatMessage(channel, "", "<col=00ffff>[" + identity.chatName + "]</col> " + message, null);
        }
    }

    /** Plays several clips as one alert (a level-up: "Woodcutting level" then "45"), if its voice is on. */
    void speakClips(AlertType type, String... clips) {
        if (voiceEnabled() && toggle(type.voiceKey) && speakingNow()) {
            voice.speakClipsAsync(persona(), volume(), clips);
        }
    }

    // A Voice Alerts / Chat Messages switch, falling back to its default
    boolean toggle(String key) {
        String value = configManager.getConfiguration(identity.configGroup, key);
        return value == null ? !"chatLevelUp".equals(key) && !"voiceRockDepletedEach".equals(key) : Boolean.parseBoolean(value);
    }

    // Changing the voice or volume plays a sample in it; turning an alert's voice on plays that alert
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!identity.configGroup.equals(event.getGroup())) {
            return;
        }
        if (event.getOldValue() == null || voiceSync.isApplying()) {
            // A default being saved (ConfigDefaults), or the voice matching another of our plugins (Same Voice
            // For All): not the player changing it here, so no sample
            return;
        }
        String key = event.getKey();
        if ("voicePersona".equals(key) || "voiceVolume".equals(key)) {
            // The voice just picked here, even while the Alerts plugin sets the one alerts use; a line this
            // plugin ships (each plugin carries only its own clips)
            VoiceAlertService.VoicePersona persona = pick(config.voicePersona());
            List<VoiceAlertService.VoiceLine> lines = new ArrayList<>();
            for (VoiceAlertService.VoiceLine line : VoiceAlertService.VoiceLine.values()) {
                if (VoiceAlertService.class.getResource(VoiceAlertService.clipResource(persona, line.fileName)) != null) {
                    lines.add(line);
                }
            }
            if (!lines.isEmpty()) {
                voice.speakAsync(lines.get(ThreadLocalRandom.current().nextInt(lines.size())), persona, config.voiceVolume());
            }
        } else if (Boolean.parseBoolean(event.getNewValue())) {
            for (AlertType type : AlertType.values()) {
                if (type.voiceKey.equals(key)) {
                    voice.speakClipsAsync(persona(), volume(), type.preview);
                }
            }
        }
    }

    // The Alerts plugin's voice and volume while it's running (PluginFamily), else this plugin's settings
    private boolean voiceEnabled() {
        PluginFamily.VoiceSettings shared = family.voiceSettings();
        return shared != null ? shared.enabled : config.voiceEnabled();
    }

    private VoiceAlertService.VoicePersona persona() {
        PluginFamily.VoiceSettings shared = family.voiceSettings();
        return pick(shared != null ? shared.persona : config.voicePersona());
    }

    private int volume() {
        PluginFamily.VoiceSettings shared = family.voiceSettings();
        return shared != null ? shared.volume : config.voiceVolume();
    }

    // Random: one of the voices, picked afresh for each alert
    private static VoiceAlertService.VoicePersona pick(VoiceAlertService.VoicePersona persona) {
        if (persona != VoiceAlertService.VoicePersona.RANDOM) {
            return persona;
        }
        VoiceAlertService.VoicePersona[] voices = VoiceAlertService.VoicePersona.voices();
        return voices[ThreadLocalRandom.current().nextInt(voices.length)];
    }
}
