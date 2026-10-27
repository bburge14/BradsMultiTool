package com.bradsmultitool;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import lombok.extern.slf4j.Slf4j;

/**
 * Plays pre-recorded voice alerts bundled with the plugin (generated with Piper TTS by
 * scripts/generate_voice_clips.py), so voices sound natural and work on any OS.
 */
@Slf4j
public class VoiceAlertService {

    /** The fixed lines the plugin can say. Text is what the clips were generated from. */
    public enum VoiceLine {
        BUFF_EXPIRED("buff_expired", "Buff has run out."),
        PRAYER_LOW("prayer_low", "Prayer is low."),
        OUT_OF_PRAYER("out_of_prayer", "Out of prayer points."),
        CRAB_MOVED("crab_moved", "The crab has moved!"),
        IDLE_LOGOUT("idle_logout", "You are about to be logged out."),
        CRAB_LOW_HEALTH("crab_low_health", "The crab is about to burrow."),
        CHARACTER_IDLE("character_idle", "You are idle."),
        BUFF_LOW("buff_low", "Buff is running out."),
        HEALTH_LOW("health_low", "Health is low."),
        MELEE_BUFF_LOW("melee_buff_low", "Melee boost is running out."),
        MELEE_BUFF_EXPIRED("melee_buff_expired", "Melee boost has run out."),
        RANGED_BUFF_LOW("ranged_buff_low", "Ranged boost is running out."),
        RANGED_BUFF_EXPIRED("ranged_buff_expired", "Ranged boost has run out."),
        MAGIC_BUFF_LOW("magic_buff_low", "Magic boost is running out."),
        MAGIC_BUFF_EXPIRED("magic_buff_expired", "Magic boost has run out."),
        DEFENCE_BUFF_LOW("defence_buff_low", "Defence boost is running out."),
        DEFENCE_BUFF_EXPIRED("defence_buff_expired", "Defence boost has run out."),
        SPEC_READY("spec_ready", "Special attack is ready."),
        ANTIFIRE_LOW("antifire_low", "Antifire is running out."),
        ANTIPOISON_LOW("antipoison_low", "Poison protection is running out."),
        POISONED("poisoned", "You have been poisoned."),
        VENOMED("venomed", "You have been venomed."),
        STAMINA_EXPIRED("stamina_expired", "Stamina has worn off."),
        AMMO_LOW("ammo_low", "Ammo is running low."),
        OUT_OF_RUNES("out_of_runes", "You are out of runes."),
        CANNON_LOW("cannon_low", "Your cannon is running low."),
        SUPERIOR_SPAWNED("superior_spawned", "A superior has spawned!"),
        TASK_COMPLETE("task_complete", "Slayer task complete."),
        SLAYER_ITEM_MISSING("slayer_item_missing", "You are missing your slayer item."),
        INVENTORY_FULL("inventory_full", "Inventory is full."),
        TREE_GONE("tree_gone", "The tree is gone."),
        ROCK_DEPLETED("rock_depleted", "The rock is depleted."),
        FISHING_SPOT_MOVED("fishing_spot_moved", "The fishing spot has moved.");

        final String fileName;
        final String text;

        VoiceLine(String fileName, String text) {
            this.fileName = fileName;
            this.text = text;
        }
    }

    /** Voice personas; each folder under resources/voices holds one clip per VoiceLine. */
    public enum VoicePersona {
        AMY("Amy"),
        JOE("Joe"),
        MIKE("Mike"),
        CORI("Cori"),
        ALBA("Alba"),
        ALAN("Alan"),
        NORTHERN("Northern"),
        // Character voices: the voices above pitch-shifted with effects (see generate_voice_clips.py)
        GNOME("Gnome"),
        OGRE("Ogre"),
        WIZARD("Old Wizard"),
        ROBOT("Robot");

        private final String displayName;

        VoicePersona(String displayName) {
            this.displayName = displayName;
        }

        String folder() {
            return name().toLowerCase();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // One clip at a time so overlapping alerts queue instead of talking over each other
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * @param volumePercent 0-100
     */
    public void speakAsync(VoiceLine line, VoicePersona persona, int volumePercent) {
        executor.submit(() -> play(line, persona, volumePercent));
    }

    private void play(VoiceLine line, VoicePersona persona, int volumePercent) {
        String resource = "voices/" + persona.folder() + "/" + line.fileName + ".wav";
        try (InputStream raw = VoiceAlertService.class.getResourceAsStream(resource)) {
            if (raw == null) {
                log.warn("Missing voice clip {}", resource);
                return;
            }
            try (AudioInputStream audio = toPcm(AudioSystem.getAudioInputStream(new BufferedInputStream(raw)));
                 Clip clip = AudioSystem.getClip()) {
                clip.open(audio);
                setVolume(clip, volumePercent);
                clip.start();
                // Wait for the clip to finish so the next alert doesn't overlap it
                TimeUnit.MICROSECONDS.sleep(clip.getMicrosecondLength() + 50_000);
                clip.stop();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Could not play voice clip {}: {}", resource, e.getMessage());
        }
    }

    /** Clips are stored as 16 kHz mu-law to keep the plugin small; decode to 16-bit PCM for playback. */
    static AudioInputStream toPcm(AudioInputStream in) {
        AudioFormat f = in.getFormat();
        if (AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding())) {
            return in;
        }
        AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
            f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
        return AudioSystem.getAudioInputStream(pcm, in);
    }

    private static void setVolume(Clip clip, int volumePercent) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float linear = Math.max(0.0001f, Math.min(100, volumePercent) / 100f);
        float db = (float) (20 * Math.log10(linear));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
