package com.bradsmultitool;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        IDLE_LOGOUT("idle_logout", "You are about to be logged out.");

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
        KRISTIN("Kristin"),
        CORI("Cori"),
        ALBA("Alba"),
        NORMAN("Norman"),
        ALAN("Alan"),
        NORTHERN("Northern");

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
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
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
