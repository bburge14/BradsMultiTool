package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * How the Planner's Timers tab says something's done (your timers, farming patches, bird houses): the voice line, or
 * the game sound picked in Timer Done Sound, played Times times (one a tick). The chat line is posted either way.
 * Picking a sound plays it once.
 */
@Singleton
class PlannerSound {

    private final Client client;
    private final ClientThread clientThread;
    private final PlannerConfig config;
    private final Alerts alerts;
    private final PluginIdentity identity;
    // A sound still to play (one each tick) and which
    private int left;
    private PlannerConfig.TimerSound playing;

    @Inject
    PlannerSound(Client client, ClientThread clientThread, PlannerConfig config, Alerts alerts, PluginIdentity identity) {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.alerts = alerts;
        this.identity = identity;
    }

    /** Says it's done: the voice line and chat, or the chosen sound and chat (the Ready Sound setting). */
    void done(VoiceAlertService.VoiceLine line, String detail) {
        done(line, detail, null, 0);
    }

    /**
     * Says it's done with this sound ({@link PlannerConfig.TimerSound}'s name, a timer's own pick; null for the Ready
     * Sound setting), played this many times (0: the setting's).
     */
    void done(VoiceAlertService.VoiceLine line, String detail, String soundName, int repeats) {
        PlannerConfig.TimerSound sound = sound(soundName);
        if (sound == PlannerConfig.TimerSound.VOICE) {
            alerts.alert(line, detail);
            return;
        }
        if (alerts.ready()) {
            left = repeats > 0 ? repeats : config.timerSoundRepeats();
            playing = sound;
        }
        alerts.chat(AlertType.of(line), detail == null ? line.text : line.text + " (" + detail + ")");
    }

    /** A timer's sound by name, else the Ready Sound setting. */
    PlannerConfig.TimerSound sound(String name) {
        if (name != null) {
            for (PlannerConfig.TimerSound sound : PlannerConfig.TimerSound.values()) {
                if (sound.name().equals(name)) {
                    return sound;
                }
            }
        }
        return config.timerSound();
    }

    /** Plays a sound once to try it (picking one in a timer's window): the voice line is said. */
    void preview(PlannerConfig.TimerSound sound) {
        if (sound == PlannerConfig.TimerSound.VOICE) {
            alerts.speak(VoiceAlertService.VoiceLine.TIMER_DONE);
        } else if (sound != null && sound.id >= 0) {
            clientThread.invokeLater(() -> client.playSoundEffect(sound.id));
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (left > 0 && playing != null && playing.id >= 0) {
            left--;
            client.playSoundEffect(playing.id);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (identity.configGroup.equals(event.getGroup()) && "timerSound".equals(event.getKey())) {
            PlannerConfig.TimerSound sound = config.timerSound();
            if (sound.id >= 0) {
                clientThread.invokeLater(() -> client.playSoundEffect(sound.id));
            }
        }
    }
}
