package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Skill;
import org.junit.Test;

public class VoiceClipTest {

    @Test
    public void levelUpClipsJoinIntoOneSound() {
        VoiceAlertService.Wav joined = null;
        long millis = 0;
        for (String clip : VoiceAlertService.levelUpClips(Skill.WOODCUTTING, 45)) {
            VoiceAlertService.Wav wav = VoiceAlertService.read(
                VoiceAlertService.clipResource(VoiceAlertService.VoicePersona.AMY, clip));
            assertNotNull(clip, wav);
            millis += wav.millis();
            joined = joined == null ? wav : joined.then(wav);
        }
        assertEquals(millis, joined.millis(), 2);
        // Written out and read back as a WAV, the same
        VoiceAlertService.Wav back = VoiceAlertService.parse(joined.bytes());
        assertNotNull(back);
        assertEquals(joined.data.length, back.data.length);
        // What RuneLite's AudioPlayer does with it first (tests may use Java Sound; the plugins can't)
        try {
            javax.sound.sampled.AudioInputStream audio = javax.sound.sampled.AudioSystem.getAudioInputStream(
                new java.io.ByteArrayInputStream(joined.bytes()));
            assertEquals(8, audio.getFormat().getSampleSizeInBits());
            assertEquals(joined.data.length, audio.getFrameLength());
        } catch (Exception e) {
            throw new AssertionError("Java Sound can't read the joined clip", e);
        }
    }
}
