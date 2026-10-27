package com.bradsmultitool;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VoiceAlertService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public enum VoiceGender {
        FEMALE,
        MALE
    }

    public void speakAsync(String phrase, VoiceGender gender) {
        executor.submit(() -> {
            try {
                String sanitized = phrase.replace("\"", "").replace("'", "");
                File tempVbs = File.createTempFile("runelite_tts_", ".vbs");
                tempVbs.deleteOnExit();

                try (FileWriter writer = new FileWriter(tempVbs)) {
                    writer.write("Set voice = CreateObject(\"SAPI.SpVoice\")\r\n");
                    writer.write("For Each v In voice.GetVoices\r\n");
                    writer.write("  If InStr(1, v.GetDescription, \"" + (gender == VoiceGender.MALE ? "David" : "Zira") + "\", 1) > 0 Then\r\n");
                    writer.write("    Set voice.Voice = v\r\n");
                    writer.write("    Exit For\r\n");
                    writer.write("  End If\r\n");
                    writer.write("Next\r\n");
                    writer.write("voice.Rate = 1\r\n");
                    writer.write("voice.Speak \"" + sanitized + "\"\r\n");
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

    public void shutdown() {
        executor.shutdownNow();
    }
}
