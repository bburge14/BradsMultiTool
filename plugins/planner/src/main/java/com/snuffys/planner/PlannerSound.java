package com.snuffys.planner;

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

    /** Says it's done: the voice line and chat, or the chosen sound and chat. */
    void done(VoiceAlertService.VoiceLine line, String detail) {
        PlannerConfig.TimerSound sound = config.timerSound();
        if (sound == PlannerConfig.TimerSound.VOICE) {
            alerts.alert(line, detail);
            return;
        }
        if (alerts.ready()) {
            left = config.timerSoundRepeats();
            playing = sound;
        }
        alerts.chat(AlertType.of(line), detail == null ? line.text : line.text + " (" + detail + ")");
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
