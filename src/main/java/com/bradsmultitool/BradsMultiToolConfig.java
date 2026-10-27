package com.bradsmultitool;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Units;

@ConfigGroup("bradsmultitool")
public interface BradsMultiToolConfig extends Config {

    enum DpsTrackingMode {
        PER_ENCOUNTER("Per Individual Monster"),
        PER_TARGET_TYPE("Per Monster Type (Resets on different mob)"),
        CONTINUOUS_SESSION("Continuous Session (Rolling average)");

        private final String name;
        DpsTrackingMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    @ConfigSection(
        name = "Voice Alerts & Persona",
        description = "Spoken voice warnings, persona selection, and preview",
        position = 0
    )
    String voiceSection = "voiceSection";

    @ConfigItem(
        keyName = "voiceGender",
        name = "Voice Persona",
        description = "Select Male (David) or Female (Zira) voice",
        section = voiceSection,
        position = 0
    )
    default VoiceAlertService.VoiceGender voiceGender() {
        return VoiceAlertService.VoiceGender.FEMALE;
    }

    @ConfigItem(
        keyName = "testVoice",
        name = "Test Voice",
        description = "Click this checkbox (on or off) to hear the selected voice",
        section = voiceSection,
        position = 1
    )
    default boolean testVoice() {
        return false;
    }

    @ConfigItem(
        keyName = "sendChatMessages",
        name = "Send In-Game Chat Messages",
        description = "Print an in-game chat message for alerts alongside voice",
        section = voiceSection,
        position = 2
    )
    default boolean sendChatMessages() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBuffExpired",
        name = "Voice: Buff Expired",
        description = "Announce when combat potion boost runs out (click to preview)",
        section = voiceSection,
        position = 3
    )
    default boolean voiceBuffExpired() {
        return true;
    }

    @ConfigItem(
        keyName = "repeatBuffAlert",
        name = "Repeat Buff Expired Alert",
        description = "Repeat the expired buff announcement",
        section = voiceSection,
        position = 4
    )
    default boolean repeatBuffAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "repeatBuffInterval",
        name = "Buff Repeat Delay",
        description = "Seconds between repeat buff alerts",
        section = voiceSection,
        position = 5
    )
    @Units(Units.SECONDS)
    default int repeatBuffInterval() {
        return 30;
    }

    @ConfigItem(
        keyName = "maxBuffRepeats",
        name = "Max Buff Repeats",
        description = "Maximum repeat count for expired buff alert",
        section = voiceSection,
        position = 6
    )
    default int maxBuffRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voicePrayerLow",
        name = "Voice: Prayer Low",
        description = "Announce when prayer falls below threshold (click to preview)",
        section = voiceSection,
        position = 7
    )
    default boolean voicePrayerLow() {
        return true;
    }

    @ConfigItem(
        keyName = "repeatPrayerAlert",
        name = "Repeat Prayer Low Alert",
        description = "Repeat low prayer alert until restored",
        section = voiceSection,
        position = 8
    )
    default boolean repeatPrayerAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "repeatPrayerInterval",
        name = "Prayer Repeat Delay",
        description = "Seconds between low prayer alerts",
        section = voiceSection,
        position = 9
    )
    @Units(Units.SECONDS)
    default int repeatPrayerInterval() {
        return 30;
    }

    @ConfigItem(
        keyName = "maxPrayerRepeats",
        name = "Max Prayer Repeats",
        description = "Maximum repeat count for low prayer alert",
        section = voiceSection,
        position = 10
    )
    default int maxPrayerRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voiceCrabMoved",
        name = "Voice: Crab Relocated",
        description = "Announce when Gemstone Crab burrows to another cove (click to preview)",
        section = voiceSection,
        position = 11
    )
    default boolean voiceCrabMoved() {
        return true;
    }

    @ConfigSection(
        name = "Combat Buff & Maintenance",
        description = "Potion status and low prayer settings",
        position = 20
    )
    String buffSection = "buffSection";

    @ConfigItem(
        keyName = "enableBuffStatus",
        name = "Show Buff Status on HUD",
        description = "Show active boost status and alert on the HUD",
        section = buffSection,
        position = 21
    )
    default boolean enableBuffStatus() {
        return true;
    }

    @ConfigItem(
        keyName = "flashExpiredBuff",
        name = "Flash Expired Buff",
        description = "Flash the Buff line on the HUD when buff is expired",
        section = buffSection,
        position = 22
    )
    default boolean flashExpiredBuff() {
        return true;
    }

    @ConfigItem(
        keyName = "enableLowPrayerAlert",
        name = "Low Prayer Alert",
        description = "Alert when prayer points fall below warning threshold",
        section = buffSection,
        position = 23
    )
    default boolean enableLowPrayerAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "lowPrayerThreshold",
        name = "Prayer Alert Threshold",
        description = "Points remaining to trigger low prayer notification",
        section = buffSection,
        position = 24
    )
    default int lowPrayerThreshold() {
        return 15;
    }

    @ConfigItem(
        keyName = "flashOutOfPrayerOverhead",
        name = "Overhead 'Out of Prayer' Flash",
        description = "Flash an 'OUT OF PRAYER' box above the player in the game world",
        section = buffSection,
        position = 25
    )
    default boolean flashOutOfPrayerOverhead() {
        return true;
    }

    @ConfigSection(
        name = "General PvM Combat HUD",
        description = "Combat target overlay for non-crab monsters",
        position = 30
    )
    String pvmSection = "pvmSection";

    @ConfigItem(
        keyName = "enablePvmOverlay",
        name = "Enable General PvM HUD",
        description = "Show combat HUD (DPS, target HP bar, aggro state, weaknesses) when attacking any monster",
        section = pvmSection,
        position = 31
    )
    default boolean enablePvmOverlay() {
        return true;
    }

    @ConfigItem(
        keyName = "dpsMode",
        name = "DPS Tracking Mode",
        description = "Per Individual Target vs Per Monster Type vs Continuous Session",
        section = pvmSection,
        position = 32
    )
    default DpsTrackingMode dpsMode() {
        return DpsTrackingMode.PER_TARGET_TYPE;
    }

    @ConfigItem(
        keyName = "pvmLingerSeconds",
        name = "Overlay Linger Duration",
        description = "Seconds the PvM HUD and DPS history remain visible after combat ends",
        section = pvmSection,
        position = 33
    )
    @Units(Units.SECONDS)
    default int pvmLingerSeconds() {
        return 15;
    }

    @ConfigItem(
        keyName = "showPvmWeakness",
        name = "Show Monster Weakness",
        description = "Show style, elemental, or slayer bane weakness on the PvM overlay",
        section = pvmSection,
        position = 34
    )
    default boolean showPvmWeakness() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmDps",
        name = "Show DPS",
        description = "Display live or rolling session DPS on the PvM overlay",
        section = pvmSection,
        position = 35
    )
    default boolean showPvmDps() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmLastHit",
        name = "Show PvM Last Hit",
        description = "Display continuous most recent hit dealt to PvM monsters",
        section = pvmSection,
        position = 36
    )
    default boolean showPvmLastHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmMaxHit",
        name = "Show PvM Max Hit",
        description = "Display continuous highest hit dealt to PvM monsters",
        section = pvmSection,
        position = 37
    )
    default boolean showPvmMaxHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmAggro",
        name = "Show Aggro Status",
        description = "Show whether the monster is aggressive and its tolerance timer",
        section = pvmSection,
        position = 39
    )
    default boolean showPvmAggro() {
        return true;
    }

    @ConfigSection(
        name = "Skill Tracker",
        description = "Sidebar panel configuration",
        position = 40
    )
    String trackerSection = "trackerSection";

    @ConfigItem(
        keyName = "showXpPerMinute",
        name = "Show XP/Min",
        description = "Display XP per minute alongside XP/hr inside each skill card",
        section = trackerSection,
        position = 41
    )
    default boolean showXpPerMinute() {
        return true;
    }

    @ConfigItem(
        keyName = "ignoredSkills",
        name = "Ignored Skills",
        description = "Comma-separated list of skills to filter out",
        section = trackerSection,
        position = 42
    )
    default String ignoredSkills() {
        return "";
    }

    @ConfigSection(
        name = "Gemstone Crab Features",
        description = "Unified Crab HUD, Radar, and entity outlines",
        position = 50
    )
    String crabSection = "crabSection";

    @ConfigItem(
        keyName = "enableCrabFeatures",
        name = "Enable Gemstone Crab Features",
        description = "Master toggle for Crab HUD, Radar, and 3D highlights in the caves",
        section = crabSection,
        position = 51
    )
    default boolean enableCrabFeatures() {
        return true;
    }

    @ConfigItem(
        keyName = "showCrabLastHit",
        name = "Show Crab Last Hit",
        description = "Display continuous most recent hit dealt to Gemstone Crabs",
        section = crabSection,
        position = 52
    )
    default boolean showCrabLastHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showCrabMaxHit",
        name = "Show Crab Max Hit",
        description = "Display continuous highest hit dealt to Gemstone Crabs",
        section = crabSection,
        position = 53
    )
    default boolean showCrabMaxHit() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "activeCrabColor",
        name = "Active Crab Color",
        description = "Soft translucent outline for active fighting crab",
        section = crabSection,
        position = 54
    )
    default Color activeCrabColor() {
        return new Color(0, 220, 255, 130);
    }

    @Alpha
    @ConfigItem(
        keyName = "shellColor",
        name = "Shell Color",
        description = "Soft translucent outline for dormant minable shell",
        section = crabSection,
        position = 55
    )
    default Color shellColor() {
        return new Color(255, 150, 0, 130);
    }

    @Alpha
    @ConfigItem(
        keyName = "tunnelColor",
        name = "Tunnel Color",
        description = "Soft translucent outline for cave tunnels when crab is burrowed",
        section = crabSection,
        position = 56
    )
    default Color tunnelColor() {
        return new Color(40, 230, 80, 140);
    }
}
