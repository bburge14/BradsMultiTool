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
        PluginFamily family = new PluginFamily(bus);
        family.start();
        assertNull(family.voiceSettings());

        bus.post(message("voice-settings", "plugin", "voices", "persona", "GNOME_CHILD", "volume", 70, "enabled", true));
        assertTrue(family.isRunning("voices"));
        assertEquals(VoiceAlertService.VoicePersona.GNOME_CHILD, family.voiceSettings().persona);
        assertEquals(70, family.voiceSettings().volume);

        bus.post(message("bye", "plugin", "voices"));
        assertNull(family.voiceSettings());
        family.stop();
    }

    @Test
    public void answersHelloAndIgnoresItself() {
        EventBus bus = new EventBus();
        PluginFamily family = new PluginFamily(bus);
        family.start();
        bus.post(message("hello", "plugin", "pvm"));
        assertTrue(family.isRunning("pvm"));
        assertFalse(family.isRunning(PluginFamily.SELF));
        assertNull(PluginFamily.parseVoiceSettings(new HashMap<>()));
        family.stop();
    }
}
