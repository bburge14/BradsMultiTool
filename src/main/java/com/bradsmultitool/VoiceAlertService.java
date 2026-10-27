package com.bradsmultitool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VoiceAlertService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Each persona lists Windows voices in order of preference, plus a speaking rate (-10..10) and
     * pitch (-10..10). Zira, David and Mark come with English (US) Windows; the UK and Australian
     * voices come from free language voice packs (Settings > Time & language > Speech > Add voices).
     * FEMALE and MALE keep their names so existing saved settings still load.
     */
    public enum VoicePersona {
        FEMALE("Zira", true, 1, 0, "Zira"),
        MALE("David", false, 1, 0, "David"),
        MARK("Mark", false, 1, 0, "Mark"),
        HAZEL("Hazel", true, 1, 0, "Hazel", "Susan", "Libby"),
        GEORGE("George", false, 1, 0, "George", "Ryan"),
        CATHERINE("Catherine", true, 1, 0, "Catherine", "Natasha"),
        JAMES("James", false, 1, 0, "James", "William"),
        COMMANDER("Commander", false, 0, -6, "David"),
        PIXIE("Pixie", true, 3, 8, "Zira"),
        ROBOT("Robot", false, -1, -10, "David");

        private final String displayName;
        final int rate;
        final int pitch;
        final String[] voiceNames;

        VoicePersona(String displayName, boolean female, int rate, int pitch, String... preferred) {
            this.displayName = displayName;
            this.rate = rate;
            this.pitch = pitch;
            // If none of the preferred voices is installed, fall back to the built-in voice of the same gender
            String[] names = java.util.Arrays.copyOf(preferred, preferred.length + 1);
            names[preferred.length] = female ? "Zira" : "David";
            this.voiceNames = names;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public void speakAsync(String phrase, VoicePersona persona, String customVoice) {
        speakAsync(phrase, persona, customVoice, null);
    }

    /**
     * @param customVoice part of an installed voice's name to try before the persona's own voices, or blank
     * @param onVoiceChosen called (on a background thread) with the description of the voice actually used
     */
    public void speakAsync(String phrase, VoicePersona persona, String customVoice, Consumer<String> onVoiceChosen) {
        executor.submit(() -> {
            File tempVbs = null;
            try {
                tempVbs = File.createTempFile("runelite_tts_", ".vbs");
                tempVbs.deleteOnExit();
                try (FileWriter writer = new FileWriter(tempVbs)) {
                    writer.write(buildScript(phrase, persona, customVoice));
                }

                Process process = new ProcessBuilder("cscript.exe", "//NoLogo", tempVbs.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
                String chosenVoice = null;
                try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = out.readLine()) != null) {
                        if (line.startsWith("VOICE=")) {
                            chosenVoice = line.substring("VOICE=".length());
                        } else if (line.startsWith("AVAILABLE=")) {
                            log.debug("Installed voices: {}", line.substring("AVAILABLE=".length()));
                        } else if (!line.trim().isEmpty()) {
                            log.warn("Voice script: {}", line);
                        }
                    }
                }
                process.waitFor();
                log.debug("Spoke '{}' with {} ({})", phrase, chosenVoice, persona.name());
                if (onVoiceChosen != null && chosenVoice != null) {
                    onVoiceChosen.accept(chosenVoice);
                }
            } catch (Exception e) {
                log.warn("SAPI voice speech error: {}", e.getMessage());
            } finally {
                if (tempVbs != null) {
                    tempVbs.delete();
                }
            }
        });
    }

    /**
     * VBScript that picks the first matching voice from the classic SAPI list or the newer OneCore list
     * (where Mark and most language-pack voices live), prints what it chose, then speaks.
     */
    private static String buildScript(String phrase, VoicePersona persona, String customVoice) {
        StringBuilder wanted = new StringBuilder();
        if (customVoice != null && !customVoice.trim().isEmpty()) {
            wanted.append('"').append(vbsSafe(customVoice.trim())).append('"');
        }
        for (String name : persona.voiceNames) {
            if (wanted.length() > 0) {
                wanted.append(", ");
            }
            wanted.append('"').append(name).append('"');
        }

        return "Set voice = CreateObject(\"SAPI.SpVoice\")\r\n"
            + "Set chosen = Nothing\r\n"
            + "available = \"\"\r\n"
            + "For Each t In AllVoices()\r\n"
            + "  available = available & t.GetDescription & \"; \"\r\n"
            + "Next\r\n"
            + "WScript.Echo \"AVAILABLE=\" & available\r\n"
            + "For Each wantedName In Array(" + wanted + ")\r\n"
            + "  If chosen Is Nothing Then\r\n"
            + "    For Each t In AllVoices()\r\n"
            + "      If chosen Is Nothing And InStr(1, t.GetDescription, wantedName, 1) > 0 Then Set chosen = t\r\n"
            + "    Next\r\n"
            + "  End If\r\n"
            + "Next\r\n"
            + "If Not chosen Is Nothing Then Set voice.Voice = chosen\r\n"
            + "WScript.Echo \"VOICE=\" & voice.Voice.GetDescription\r\n"
            + "voice.Rate = " + persona.rate + "\r\n"
            // Flag 8 (SVSFIsXML) lets the SAPI <pitch> tag shift the voice up or down
            + "voice.Speak \"<pitch absmiddle='" + persona.pitch + "'>" + vbsSafe(phrase) + "</pitch>\", 8\r\n"
            + "\r\n"
            + "Function AllVoices()\r\n"
            + "  Dim list(), n, t, cat\r\n"
            + "  n = 0\r\n"
            + "  ReDim list(0)\r\n"
            + "  For Each t In voice.GetVoices\r\n"
            + "    ReDim Preserve list(n): Set list(n) = t: n = n + 1\r\n"
            + "  Next\r\n"
            + "  On Error Resume Next\r\n"
            + "  Set cat = CreateObject(\"SAPI.SpObjectTokenCategory\")\r\n"
            + "  cat.SetId \"HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Speech_OneCore\\Voices\", False\r\n"
            + "  If Err.Number = 0 Then\r\n"
            + "    For Each t In cat.EnumerateTokens\r\n"
            + "      ReDim Preserve list(n): Set list(n) = t: n = n + 1\r\n"
            + "    Next\r\n"
            + "  End If\r\n"
            + "  Err.Clear\r\n"
            + "  On Error GoTo 0\r\n"
            + "  If n = 0 Then AllVoices = Array() Else AllVoices = list\r\n"
            + "End Function\r\n";
    }

    // Strip characters that would break the VBScript string or the SAPI XML
    private static String vbsSafe(String text) {
        return text.replaceAll("[\"'<>&]", "");
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
