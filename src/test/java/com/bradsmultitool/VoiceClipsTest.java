package com.bradsmultitool;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
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
        for (VoiceAlertService.VoicePersona persona : VoiceAlertService.VoicePersona.voices()) {
            for (VoiceAlertService.VoiceLine line : VoiceAlertService.VoiceLine.values()) {
                String resource = VoiceAlertService.clipResource(persona, line.fileName);
                // Plain 8-bit PCM (RuneLite's AudioPlayer plays it as it is), between 0.3 and 5 seconds
                VoiceAlertService.Wav wav = VoiceAlertService.read(resource);
                assertNotNull("missing " + resource, wav);
                assertTrue(resource, wav.format[0] == 1 && wav.format[14] == 8);
                assertTrue(resource + " is " + wav.millis() + "ms", wav.millis() > 300 && wav.millis() < 5000);
            }
        }
    }

    @Test
    public void everyPersonaCanSayEveryLevelUp() throws Exception {
        for (VoiceAlertService.VoicePersona persona : VoiceAlertService.VoicePersona.voices()) {
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
