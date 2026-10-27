package com.bradsmultitool;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VoiceAlertService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * A Windows voice (David and Zira ship with every Windows install) plus speaking rate (-10..10)
     * and pitch (-10..10). FEMALE and MALE keep their names so existing saved settings still load.
     */
    public enum VoicePersona {
        FEMALE("Zira (Standard)", "Zira", 1, 0),
        MALE("David (Standard)", "David", 1, 0),
        NAVIGATOR("Navigator (calm)", "Zira", 0, -3),
        PIXIE("Pixie (high & quick)", "Zira", 3, 7),
        COMMANDER("Commander (deep & firm)", "David", 0, -5),
        ANNOUNCER("Announcer (slow & clear)", "David", -2, -2),
        AUCTIONEER("Auctioneer (fast)", "David", 6, 2),
        ROBOT("Robot (low & flat)", "David", -1, -10);

        private final String displayName;
        final String voiceName;
        final int rate;
        final int pitch;

        VoicePersona(String displayName, String voiceName, int rate, int pitch) {
            this.displayName = displayName;
            this.voiceName = voiceName;
            this.rate = rate;
            this.pitch = pitch;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * @param customVoice part of an installed voice's name (e.g. "Hazel") to use instead of the persona's
     *                    voice, keeping its rate and pitch; blank to use the persona's voice
     */
    public void speakAsync(String phrase, VoicePersona persona, String customVoice) {
        String voiceName = customVoice != null && !customVoice.trim().isEmpty() ? customVoice.trim() : persona.voiceName;
        executor.submit(() -> {
            try {
                File tempVbs = File.createTempFile("runelite_tts_", ".vbs");
                tempVbs.deleteOnExit();

                try (FileWriter writer = new FileWriter(tempVbs)) {
                    writer.write("Set voice = CreateObject(\"SAPI.SpVoice\")\r\n");
                    writer.write("For Each v In voice.GetVoices\r\n");
                    writer.write("  If InStr(1, v.GetDescription, \"" + vbsSafe(voiceName) + "\", 1) > 0 Then\r\n");
                    writer.write("    Set voice.Voice = v\r\n");
                    writer.write("    Exit For\r\n");
                    writer.write("  End If\r\n");
                    writer.write("Next\r\n");
                    writer.write("voice.Rate = " + persona.rate + "\r\n");
                    // Flag 8 (SVSFIsXML) lets the SAPI <pitch> tag shift the voice up or down
                    writer.write("voice.Speak \"<pitch absmiddle='" + persona.pitch + "'>" + vbsSafe(phrase) + "</pitch>\", 8\r\n");
                }

                ProcessBuilder pb = new ProcessBuilder("wscript.exe", "//B", tempVbs.getAbsolutePath());
                Process process = pb.start();
                process.waitFor();
                tempVbs.delete();
            } catch (Exception e) {
                log.warn("SAPI voice speech error: {}", e.getMessage());
            }
        });
    }

    // Strip characters that would break the VBScript string or the SAPI XML
    private static String vbsSafe(String text) {
        return text.replaceAll("[\"'<>&]", "");
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
