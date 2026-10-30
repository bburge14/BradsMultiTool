package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;

public class PluginFamilyTest {

    private static PluginMessage message(String name, Object... keysAndValues) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            data.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return new PluginMessage(PluginFamily.NAMESPACE, name, data);
    }

    @Test
    public void usesTheVoicesPluginsVoiceOnlyWhileItRuns() {
        EventBus bus = new EventBus();
        PluginFamily family = new PluginFamily(bus, PluginIdentity.MULTI_TOOL);
        family.start();
        assertNull(family.voiceSettings());

        bus.post(message("voice-settings", "plugin", "voices", "persona", "JOHN", "volume", 70, "enabled", true));
        assertTrue(family.isRunning("voices"));
        assertEquals(VoiceAlertService.VoicePersona.JOHN, family.voiceSettings().persona);
        assertEquals(70, family.voiceSettings().volume);

        bus.post(message("bye", "plugin", "voices"));
        assertNull(family.voiceSettings());
        family.stop();
    }

    @Test
    public void answersHelloAndIgnoresItself() {
        EventBus bus = new EventBus();
        PluginFamily family = new PluginFamily(bus, PluginIdentity.MULTI_TOOL);
        family.start();
        bus.post(message("hello", "plugin", "pvm"));
        assertTrue(family.isRunning("pvm"));
        assertFalse(family.isRunning(PluginIdentity.MULTI_TOOL.familyId));
        assertNull(PluginFamily.parseVoiceSettings(new HashMap<>()));
        family.stop();
    }

    @Test
    public void sharedWarningsAreSaidOnceByTheFirstInOrder() {
        EventBus bus = new EventBus();
        PluginFamily multiTool = new PluginFamily(bus, PluginIdentity.MULTI_TOOL);
        PluginFamily voices = new PluginFamily(bus, PluginIdentity.VOICES);
        PluginFamily pvm = new PluginFamily(bus, PluginIdentity.PVM);
        pvm.start();
        assertTrue(pvm.handlesShared());
        voices.start();
        assertFalse(pvm.handlesShared());
        assertTrue(voices.handlesShared());
        // With the all-in-one installed it says them, tagged Snuffy's Toolbelt
        multiTool.start();
        assertTrue(multiTool.handlesShared());
        assertFalse(voices.handlesShared());
        multiTool.stop();
        assertTrue(voices.handlesShared());
        voices.stop();
        pvm.stop();
    }

    @Test
    public void pvmKnowsWhichNpcTheSeparateSkillingHelpersCardShows() {
        EventBus bus = new EventBus();
        PluginFamily skilling = new PluginFamily(bus, PluginIdentity.SKILL_TRACKER);
        PluginFamily pvm = new PluginFamily(bus, PluginIdentity.PVM);
        pvm.start();
        skilling.start();
        skilling.sendSkillingCard(42);
        assertTrue(pvm.skillingCardShows(42));
        assertFalse(pvm.skillingCardShows(7));
        skilling.sendSkillingCard(-1);
        assertFalse(pvm.skillingCardShows(42));
        skilling.sendSkillingCard(42);
        skilling.stop();
        // Once the Skilling Helper stops, its last card no longer counts
        assertFalse(pvm.skillingCardShows(42));
        pvm.stop();
    }

    @Test
    public void potionAlertsAreSaidOnceWithPvmAndGemCrabSeparately() {
        EventBus bus = new EventBus();
        java.util.List<String> order = java.util.Arrays.asList("multitool", "pvm", "gemcrab");
        PluginFamily pvm = new PluginFamily(bus, PluginIdentity.PVM);
        PluginFamily gemCrab = new PluginFamily(bus, PluginIdentity.GEM_CRAB);
        gemCrab.start();
        assertTrue(gemCrab.isFirstOf(order));
        pvm.start();
        assertTrue(pvm.isFirstOf(order));
        assertFalse(gemCrab.isFirstOf(order));
        pvm.stop();
        assertTrue(gemCrab.isFirstOf(order));
        gemCrab.stop();
    }
}
