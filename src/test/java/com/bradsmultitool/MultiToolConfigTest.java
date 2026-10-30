package com.bradsmultitool;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

/**
 * RuneLite only shows the sections a settings interface declares itself (not ones it inherits), so every
 * plugin's settings, and the all-in-one's, must declare each section its items (own or inherited) use.
 */
public class MultiToolConfigTest {

    private static void assertSectionsDeclared(Class<?> config) {
        Set<String> declared = new HashSet<>();
        for (Field field : config.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigSection.class)) {
                try {
                    declared.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        for (Method method : config.getMethods()) {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null && !item.section().isEmpty()) {
                assertTrue(config.getSimpleName() + " doesn't declare section " + item.section() + " (for " + item.keyName() + ")",
                    declared.contains(item.section()));
            }
        }
    }

    @Test
    public void everyConfigDeclaresItsSections() {
        assertSectionsDeclared(SkillTrackerConfig.class);
        assertSectionsDeclared(PvmConfig.class);
        assertSectionsDeclared(GemCrabConfig.class);
        assertSectionsDeclared(VoicesConfig.class);
        assertSectionsDeclared(BradsMultiToolConfig.class);
    }

    @Test
    public void keysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (Method method : BradsMultiToolConfig.class.getMethods()) {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null) {
                keys.add(item.keyName() + "/" + method.getName());
            }
        }
        Set<String> names = new HashSet<>();
        for (String key : keys) {
            assertTrue("Key used by two settings: " + key, names.add(key.substring(0, key.indexOf('/'))));
        }
    }
}
