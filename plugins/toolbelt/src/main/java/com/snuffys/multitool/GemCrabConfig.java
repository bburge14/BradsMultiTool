package com.snuffys.multitool;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/** Snuffy's GemCrab Tracker: the crab HUD, outlines and alerts. */
@ConfigGroup("snuffysgemcrab")
public interface GemCrabConfig extends CommonConfig {

    // Repeated from CommonConfig: RuneLite only shows sections an interface declares itself

    @ConfigSection(
        name = "General",
        description = "Voice and chat settings shared by all alerts",
        position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
        name = "Idle",
        description = "Idle character and idle logout warnings",
        position = 30,
        closedByDefault = true
    )
    String idleSection = "idleSection";

    @ConfigSection(
        name = "Gemstone Crab",
        description = "Crab HUD, radar, alerts and outlines",
        position = 49,
        closedByDefault = true
    )
    String crabSection = "crabSection";

    @ConfigItem(
        keyName = "enableCrabFeatures",
        name = "Enable Crab Features",
        description = "Master toggle for Crab HUD, Radar, and 3D highlights in the caves",
        section = crabSection,
        position = 50
    )
    default boolean enableCrabFeatures() {
        return true;
    }

    @Range(min
    =
    1,
    max
    =
    99)
    @ConfigItem(
        keyName = "crabLowHealthPercent",
        name = "Low Health Alert",
        description = "Alert when the crab's health falls to this percentage or below",
        section = crabSection,
        position = 51
    )
    @Units(Units.PERCENT)
    default int crabLowHealthPercent() {
        return 5;
    }

    @ConfigItem(
        keyName = "showCrabBuffs",
        name = "Show Potion Buffs",
        description = "Your potion buffs on the crab card, each with its time left (red in its last minute)",
        section = crabSection,
        position = 52
    )
    default boolean showCrabBuffs() {
        return true;
    }

    @Range(max
    =
    99)
    @ConfigItem(
        keyName = "crabLowStatsPercent",
        name = "Low Prayer/HP",
        description = "Show Prayer: LOW / Health: LOW on the crab card at or below this share of your level (0: off)",
        section = crabSection,
        position = 53
    )
    @Units(Units.PERCENT)
    default int crabLowStatsPercent() {
        return 25;
    }

    @ConfigItem(
        keyName = "showCrabLastHit",
        name = "Show Last Hit",
        description = "Display continuous most recent hit dealt to Gemstone Crabs",
        section = crabSection,
        position = 54
    )
    default boolean showCrabLastHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showCrabMaxHit",
        name = "Show Max Hit",
        description = "Display continuous highest hit dealt to Gemstone Crabs",
        section = crabSection,
        position = 55
    )
    default boolean showCrabMaxHit() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "activeCrabColor",
        name = "Crab Colour",
        description = "Soft translucent outline for active fighting crab",
        section = crabSection,
        position = 56
    )
    default Color activeCrabColor() {
        return new Color(0, 220, 255, 130);
    }

    @Alpha
    @ConfigItem(
        keyName = "mineableShellColor",
        name = "Shell (Mineable)",
        description = "Outline for a crab shell you can mine (you were in the top 16 damage dealers and have gems left)",
        section = crabSection,
        position = 57
    )
    default Color mineableShellColor() {
        return new Color(40, 230, 80, 140);
    }

    @Alpha
    @ConfigItem(
        keyName = "depletedShellColor",
        name = "Shell (Can't Mine)",
        description = "Outline for a crab shell you can't mine (not in the top 16 damage dealers, or already mined)",
        section = crabSection,
        position = 58
    )
    default Color depletedShellColor() {
        return new Color(255, 60, 60, 140);
    }

    @Alpha
    @ConfigItem(
        keyName = "tunnelColor",
        name = "Tunnel Colour",
        description = "Soft translucent outline for cave tunnels when crab is burrowed",
        section = crabSection,
        position = 59
    )
    default Color tunnelColor() {
        return new Color(40, 230, 80, 140);
    }

    @ConfigItem(
        keyName = "voiceCrabMoved",
        name = "Voice: Crab Moved",
        description = "When the Gemstone Crab relocates (turning it on plays a preview)",
        section = crabSection,
        position = 60
    )
    default boolean voiceCrabMoved() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCrabMoved",
        name = "Chat: Crab Moved",
        description = "Chat message when the Gemstone Crab relocates",
        section = crabSection,
        position = 61
    )
    default boolean chatCrabMoved() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCrabLowHealth",
        name = "Voice: Crab About to Burrow",
        description = "When the Gemstone Crab is low on health (turning it on plays a preview)",
        section = crabSection,
        position = 62
    )
    default boolean voiceCrabLowHealth() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCrabLowHealth",
        name = "Chat: Crab About to Burrow",
        description = "Chat message when the Gemstone Crab is low on health",
        section = crabSection,
        position = 63
    )
    default boolean chatCrabLowHealth() {
        return true;
    }
}
