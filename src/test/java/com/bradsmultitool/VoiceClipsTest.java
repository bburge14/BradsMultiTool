package com.bradsmultitool;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import net.runelite.api.Skill;
import org.junit.Test;

public class VoiceClipsTest {

    @Test
    public void everyBuffKindHasItsOwnLines() {
        for (String name : new String[]{"Super Combat", "Divine Super Combat", "Ranging", "Divine Battlemage", "Combat"}) {
            assertNotNull(name, VoiceAlertService.VoiceLine.forPotion(name, true));
            assertNotNull(name, VoiceAlertService.VoiceLine.forPotion(name, false));
        }
    }

    @Test
    public void everyPersonaHasAPlayableClipForEveryLine() throws Exception {
        for (VoiceAlertService.VoicePersona persona : VoiceAlertService.VoicePersona.values()) {
            for (VoiceAlertService.VoiceLine line : VoiceAlertService.VoiceLine.values()) {
                String resource = VoiceAlertService.clipResource(persona, line.fileName);
                try (InputStream raw = VoiceAlertService.class.getResourceAsStream(resource)) {
                    assertNotNull("missing " + resource, raw);
                    try (AudioInputStream audio = VoiceAlertService.toPcm(
                        AudioSystem.getAudioInputStream(new BufferedInputStream(raw)))) {
                        assertTrue(resource, audio.getFormat().getSampleSizeInBits() == 16);
                        float seconds = audio.getFrameLength() / audio.getFormat().getFrameRate();
                        assertTrue(resource + " is " + seconds + "s", seconds > 0.3 && seconds < 5);
                    }
                }
            }
        }
    }

    @Test
    public void everyPersonaCanSayEveryLevelUp() throws Exception {
        for (VoiceAlertService.VoicePersona persona : VoiceAlertService.VoicePersona.values()) {
            for (Skill skill : Skill.values()) {
                if (skill.ordinal() >= Skill.SAILING.ordinal() + 1) {
                    continue; // Overall
                }
                for (int level = 2; level <= 99; level++) {
                    for (String clip : VoiceAlertService.levelUpClips(skill, level)) {
                        String resource = VoiceAlertService.clipResource(persona, clip);
                        try (InputStream raw = VoiceAlertService.class.getResourceAsStream(resource)) {
                            assertNotNull("missing " + resource, raw);
                        }
                    }
                }
            }
        }
    }
}
