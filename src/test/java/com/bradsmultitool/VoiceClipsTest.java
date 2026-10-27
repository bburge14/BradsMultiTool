package com.bradsmultitool;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;

public class VoiceClipsTest {

    @Test
    public void everyPersonaHasAPlayableClipForEveryLine() throws Exception {
        for (VoiceAlertService.VoicePersona persona : VoiceAlertService.VoicePersona.values()) {
            for (VoiceAlertService.VoiceLine line : VoiceAlertService.VoiceLine.values()) {
                String resource = "voices/" + persona.folder() + "/" + line.fileName + ".wav";
                try (InputStream raw = VoiceAlertService.class.getResourceAsStream(resource)) {
                    assertNotNull("missing " + resource, raw);
                    try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                        float seconds = audio.getFrameLength() / audio.getFormat().getFrameRate();
                        assertTrue(resource + " is " + seconds + "s", seconds > 0.3 && seconds < 5);
                    }
                }
            }
        }
    }
}
