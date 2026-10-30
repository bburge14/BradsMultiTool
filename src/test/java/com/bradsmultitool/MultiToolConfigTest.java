package com.bradsmultitool;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
        assertSectionsDeclared(TickConfig.class);
        assertSectionsDeclared(VoicesConfig.class);
        assertSectionsDeclared(BradsMultiToolConfig.class);
    }

    /**
     * RuneLite reads settings through a proxy in another module, so a setting's enum type must be public (and so
     * must the class holding it), or the client crashes the first time the setting is read.
     */
    @Test
    public void settingTypesArePublic() {
        for (Method method : BradsMultiToolConfig.class.getMethods()) {
            if (method.getAnnotation(ConfigItem.class) == null) {
                continue;
            }
            for (Class<?> type = method.getReturnType(); type != null; type = type.getEnclosingClass()) {
                assertTrue(method.getName() + "'s type " + type.getName() + " must be public",
                    type.isPrimitive() || Modifier.isPublic(type.getModifiers()));
            }
        }
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
