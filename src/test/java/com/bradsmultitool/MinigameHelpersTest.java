package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class MinigameHelpersTest {

    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    @Test
    public void readsTheGamesBoxes() {
        assertEquals(54, WintertodtHelper.number("Wintertodt's Energy: 54%", PERCENT));
        assertEquals(1234, WintertodtHelper.number("Elemental Energy: 1,234", NUMBER));
        assertEquals(2150, WintertodtHelper.number("Points: 2150", NUMBER));
        assertEquals(-1, WintertodtHelper.number("Points", NUMBER));
    }

    @Test
    public void roundTimerInSeconds() {
        // RuneLite's Wintertodt plugin: value * 30 / 50
        assertEquals(6, WintertodtHelper.seconds(10));
        assertEquals(0, WintertodtHelper.seconds(0));
    }

    @Test
    public void temporossFishDamage() {
        assertEquals(10 * 3 + 15 * 2 + 10, TemporossHelper.damage(3, 2, 1));
    }

    @Test
    public void riftPortalDirectionAndAltars() {
        assertEquals("south west", RiftHelper.direction("SW 0:25"));
        assertEquals("east", RiftHelper.direction("E 1:02"));
        assertEquals("Portal", RiftHelper.direction("Portal"));
        assertEquals("Fire", RiftHelper.guardian(4357).name);
        assertEquals(77, RiftHelper.guardian(4364).level);
        assertNull(RiftHelper.guardian(1));
        assertTrue(RiftHelper.inRemains(new WorldPoint(3590, 9505, 0)));
        assertFalse(RiftHelper.inRemains(new WorldPoint(3615, 9490, 0)));
    }

    @Test
    public void boatHealthPercent() {
        assertEquals(25, SailingAlerts.percent(50, 200));
        assertEquals(-1, SailingAlerts.percent(0, 0));
    }

    @Test
    public void clashReadsNotificationSettings() {
        assertTrue(PluginClashes.on("true"));
        assertTrue(PluginClashes.on("{\"enabled\":true,\"initialized\":true}"));
        assertFalse(PluginClashes.on("{\"enabled\":false}"));
        assertFalse(PluginClashes.on("false"));
    }
}
