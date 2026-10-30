package com.bradsmultitool;

import java.awt.Color;
import net.runelite.api.SoundEffectID;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/** The tick counter and metronome, in both Snuffy's Skilling Helper and PvM/Slayer. */
public interface TickConfig extends Config {

    @ConfigSection(
        name = "Tick Counter",
        description = "Counts game ticks over your head or in a box you can move, with an optional metronome",
        position = 85,
        closedByDefault = true
    )
    String tickSection = "tickSection";

    enum Layout {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Pairs of game sounds: one for most ticks, the other for the first tick of each cycle. */
    enum Sound {
        OFF("Off", -1, -1),
        PLOP("Plop", SoundEffectID.GE_INCREMENT_PLOP, SoundEffectID.GE_DECREMENT_PLOP),
        BELL("Bell", SoundEffectID.TOWN_CRIER_BELL_DING, SoundEffectID.TOWN_CRIER_BELL_DONG),
        ANVIL("Anvil", SoundEffectID.SMITH_ANVIL_TINK, SoundEffectID.SMITH_ANVIL_TONK),
        BOOP("Boop", SoundEffectID.UI_BOOP, SoundEffectID.GE_COLLECT_BLOOP),
        COINS("Coins", SoundEffectID.GE_COIN_TINKLE, SoundEffectID.GE_ADD_OFFER_DINGALING),
        PICKAXE("Pickaxe and axe", SoundEffectID.MINING_TINK, SoundEffectID.TREE_CHOP),
        TINDERBOX("Tinderbox", SoundEffectID.TINDER_STRIKE, SoundEffectID.FIRE_WOOSH);

        private final String label;
        final int tick;
        final int first;

        Sound(String label, int tick, int first) {
            this.label = label;
            this.tick = tick;
            this.first = first;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigItem(
        keyName = "tickOverHead",
        name = "Count Over Head",
        description = "Show the tick count over your character's head",
        section = tickSection,
        position = 0
    )
    default boolean tickOverHead() {
        return false;
    }

    @ConfigItem(
        keyName = "tickBoxes",
        name = "Tick Boxes",
        description = "A box for each tick of the cycle, lighting up in turn. Move them with Alt + drag",
        section = tickSection,
        position = 1
    )
    default boolean tickBoxes() {
        return false;
    }

    @ConfigItem(
        keyName = "tickBoxLayout",
        name = "Box Layout",
        description = "Tick boxes in a row or a column",
        section = tickSection,
        position = 2
    )
    default Layout tickBoxLayout() {
        return Layout.HORIZONTAL;
    }

    @ConfigItem(
        keyName = "tickBar",
        name = "Tick Bar",
        description = "A bar that fills smoothly over each tick and snaps back on the next, so you can see the tick coming instead of reacting to it. Move it with Alt + drag",
        section = tickSection,
        position = 3
    )
    default boolean tickBar() {
        return false;
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
        position = 4
    )
    default int tickCycle() {
        return 4;
    }

    @ConfigItem(
        keyName = "tickFollowWeapon",
        name = "Follow Attack Speed",
        description = "Use your weapon's attack speed for the cycle (one faster on Rapid) and start the count again each time you attack, so 1 is your attack tick",
        section = tickSection,
        position = 5
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
        position = 6
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
        position = 7
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
        position = 8
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
        description = "Width and height of each tick box",
        section = tickSection,
        position = 9
    )
    default int tickBoxSize() {
        return 40;
    }

    @Range(min
    =
    60,
    max
    =
    400)
    @ConfigItem(
        keyName = "tickBarWidth",
        name = "Bar Width",
        description = "Width of the tick bar",
        section = tickSection,
        position = 10
    )
    default int tickBarWidth() {
        return 150;
    }

    @ConfigItem(
        keyName = "metronomeSound",
        name = "Metronome Sound",
        description = "A game sound each tick; the first tick of each cycle plays the second sound of the pair",
        section = tickSection,
        position = 11
    )
    default Sound metronomeSound() {
        return Sound.OFF;
    }

    @ConfigItem(
        keyName = "metronomeFirstOnly",
        name = "Sound On First Tick Only",
        description = "Only play the metronome on the first tick of each cycle",
        section = tickSection,
        position = 12
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
        description = "Volume of the metronome and the click sounds (0-100); 0 uses the game's sound effect volume",
        section = tickSection,
        position = 13
    )
    default int metronomeVolume() {
        return 50;
    }

    @ConfigItem(
        keyName = "clickTiming",
        name = "Click Timing",
        description = "Show how long after the tick each click landed (in ms) over your head, and a summary of your last clicks with the tick bar (Alt + drag to move it)",
        section = tickSection,
        position = 14
    )
    default boolean clickTiming() {
        return false;
    }

    @ConfigItem(
        keyName = "clickSounds",
        name = "Click Sounds",
        description = "A ding for each click on time, a dun for a late one (after Late After)",
        section = tickSection,
        position = 15
    )
    default boolean clickSounds() {
        return false;
    }

    @Range(min
    =
    100,
    max
    =
    590)
    @ConfigItem(
        keyName = "clickLateMs",
        name = "Late After",
        description = "A click this long after the tick or later counts as late: with your ping it may land on the next tick",
        section = tickSection,
        position = 16
    )
    @Units(Units.MILLISECONDS)
    default int clickLateMs() {
        return 450;
    }

    @ConfigItem(
        keyName = "clickEveryTick",
        name = "Click Every Tick",
        description = "For 1-ticking: a tick without a click, after one with a click, counts as missed (and plays a dun)",
        section = tickSection,
        position = 17
    )
    default boolean clickEveryTick() {
        return false;
    }
}
