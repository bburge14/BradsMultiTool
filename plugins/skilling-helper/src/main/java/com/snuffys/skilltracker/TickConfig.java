package com.snuffys.skilltracker;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/** The tick counter and metronome, in both Snuffy's Skilling Helper and PvM/Slayer. */
public interface TickConfig extends Config {

    @ConfigSection(
        name = "Tick Counter",
        description = "Counts game ticks over your head or in a box you can move, with an optional metronome",
        position = 85,
        closedByDefault = true
    )
    String tickSection = "tickSection";

    @ConfigItem(
        keyName = "tickDisplay",
        name = "Show Ticks",
        description = "Count the ticks over your character's head, in a box you can move (Alt + drag), or both",
        section = tickSection,
        position = 0
    )
    default TickCounter.Display tickDisplay() {
        return TickCounter.Display.OFF;
    }

    @Range(min
    =
    1,
    max
    =
    10)
    @ConfigItem(
        keyName = "tickCycle",
        name = "Ticks Per Cycle",
        description = "The count goes 1 to this and starts over: 3 for 3-tick skilling, your weapon's attack speed for combat",
        section = tickSection,
        position = 1
    )
    default int tickCycle() {
        return 4;
    }

    @ConfigItem(
        keyName = "tickFollowWeapon",
        name = "Follow Attack Speed",
        description = "Use your weapon's attack speed for the cycle (one faster on Rapid) and start the count again each time you attack, so 1 is your attack tick",
        section = tickSection,
        position = 2
    )
    default boolean tickFollowWeapon() {
        return false;
    }

    @Alpha
    @ConfigItem(
        keyName = "tickColor",
        name = "Tick Colour",
        description = "Colour of the count on most ticks",
        section = tickSection,
        position = 3
    )
    default Color tickColor() {
        return new Color(255, 255, 255, 230);
    }

    @Alpha
    @ConfigItem(
        keyName = "tickFirstColor",
        name = "First Tick Colour",
        description = "Colour of the count on the first tick of each cycle",
        section = tickSection,
        position = 4
    )
    default Color tickFirstColor() {
        return new Color(0, 230, 120, 230);
    }

    @Range(min
    =
    10,
    max
    =
    60)
    @ConfigItem(
        keyName = "tickTextSize",
        name = "Over Head Text Size",
        description = "Size of the count over your head",
        section = tickSection,
        position = 5
    )
    default int tickTextSize() {
        return 22;
    }

    @Range(min
    =
    20,
    max
    =
    120)
    @ConfigItem(
        keyName = "tickBoxSize",
        name = "Box Size",
        description = "Width and height of the tick box",
        section = tickSection,
        position = 6
    )
    default int tickBoxSize() {
        return 40;
    }

    @ConfigItem(
        keyName = "metronomeSound",
        name = "Metronome Sound",
        description = "A game sound each tick; the first tick of each cycle plays the second sound of the pair",
        section = tickSection,
        position = 7
    )
    default TickCounter.Sound metronomeSound() {
        return TickCounter.Sound.OFF;
    }

    @ConfigItem(
        keyName = "metronomeFirstOnly",
        name = "Sound On First Tick Only",
        description = "Only play the metronome on the first tick of each cycle",
        section = tickSection,
        position = 8
    )
    default boolean metronomeFirstOnly() {
        return false;
    }

    @Range(max
    =
    100)
    @ConfigItem(
        keyName = "metronomeVolume",
        name = "Metronome Volume",
        description = "Volume of the metronome (0-100); 0 uses the game's sound effect volume",
        section = tickSection,
        position = 9
    )
    default int metronomeVolume() {
        return 50;
    }
}
