package com.bradsmultitool;

import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

/**
 * Opens each plugin's settings in RuneLite's own settings panel on a fresh install (nothing saved), after what
 * the client saves (setDefaultConfiguration) and what the plugin saves (ConfigDefaults). RuneLite only saves the
 * settings an interface declares itself, and the panel throws on a choice setting with nothing saved, which is
 * what broke the Toolbelt's settings when the Tick Counter's were added. Reaches into RuneLite's internals with
 * reflection: test code only, never shipped.
 */
public class SettingsPanelTest {

    // Shared by every test that makes a ConfigManager (here and VoiceSyncTest); a daemon, so it never holds the JVM
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "settings-test");
        thread.setDaemon(true);
        return thread;
    });

    @Test
    public void toolbelt() throws Exception {
        ConfigDescriptor toolbelt = open(BradsMultiToolConfig.class, PluginIdentity.MULTI_TOOL, (defaults) -> {
            BradsMultiToolConfig config = (BradsMultiToolConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            SkillTrackerConfigDefaults.store(defaults.store, config);
            TickConfigDefaults.store(defaults.store, config);
            PvmConfigDefaults.store(defaults.store, config);
            BuffConfigDefaults.store(defaults.store, config);
            GemCrabConfigDefaults.store(defaults.store, config);
            PlannerConfigDefaults.store(defaults.store, config);
        });
        // Every separate plugin's setting is in the Toolbelt's panel too, in the same section (Timer Opacity...)
        java.util.Map<String, String> sections = new java.util.HashMap<>();
        for (ConfigItemDescriptor item : toolbelt.getItems()) {
            org.junit.Assert.assertNull("Toolbelt shows " + item.getItem().keyName() + " twice",
                sections.put(item.getItem().keyName(), item.getItem().section()));
        }
        for (Class<?> part : new Class<?>[]{SkillTrackerConfig.class, PvmConfig.class, GemCrabConfig.class, TickConfig.class, PlannerConfig.class,
            CommonConfig.class}) {
            for (Method method : part.getDeclaredMethods()) {
                net.runelite.client.config.ConfigItem item = method.getAnnotation(net.runelite.client.config.ConfigItem.class);
                if (item != null) {
                    org.junit.Assert.assertTrue("Toolbelt is missing " + item.keyName(), sections.containsKey(item.keyName()));
                    // (the Toolbelt keeps the smaller skills together in Other Skills)
                    if (!"otherSkillsSection".equals(sections.get(item.keyName()))) {
                        org.junit.Assert.assertEquals("Toolbelt section of " + item.keyName(), item.section(), sections.get(item.keyName()));
                    }
                }
            }
        }
    }

    @Test
    public void skillingHelper() throws Exception {
        open(SkillTrackerConfig.class, PluginIdentity.SKILL_TRACKER, (defaults) -> {
            SkillTrackerConfig config = (SkillTrackerConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            SkillTrackerConfigDefaults.store(defaults.store, config);
        });
    }

    @Test
    public void tickCounter() throws Exception {
        open(TickConfig.class, PluginIdentity.TICK_COUNTER,
            (defaults) -> TickConfigDefaults.store(defaults.store, (TickConfig) defaults.config));
    }

    @Test
    public void pvm() throws Exception {
        open(PvmConfig.class, PluginIdentity.PVM, (defaults) -> {
            PvmConfig config = (PvmConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            PvmConfigDefaults.store(defaults.store, config);
            BuffConfigDefaults.store(defaults.store, config);
        });
    }

    @Test
    public void gemCrab() throws Exception {
        open(GemCrabConfig.class, PluginIdentity.GEM_CRAB, (defaults) -> {
            GemCrabConfig config = (GemCrabConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            GemCrabConfigDefaults.store(defaults.store, config);
            BuffConfigDefaults.store(defaults.store, config);
        });
    }

    @Test
    public void planner() throws Exception {
        open(PlannerConfig.class, PluginIdentity.PLANNER, (defaults) -> {
            PlannerConfig config = (PlannerConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            PlannerConfigDefaults.store(defaults.store, config);
        });
    }

    @Test
    public void alerts() throws Exception {
        open(VoicesConfig.class, PluginIdentity.VOICES,
            (defaults) -> CommonConfigDefaults.store(defaults.store, (CommonConfig) defaults.config));
    }

    private static final class Defaults {
        final Config config;
        final ConfigDefaults store;

        Defaults(Config config, ConfigDefaults store) {
            this.config = config;
            this.store = store;
        }
    }

    private static <T extends Config> ConfigDescriptor open(Class<T> type, PluginIdentity identity, Consumer<Defaults> pluginDefaults) throws Exception {
        ConfigManager configManager = freshConfigManager();
        T config = configManager.getConfig(type);
        configManager.setDefaultConfiguration(config, false);
        pluginDefaults.accept(new Defaults(config, new ConfigDefaults(configManager, identity)));

        ConfigDescriptor descriptor = configManager.getConfigDescriptor(config);
        for (ConfigItemDescriptor item : descriptor.getItems()) {
            assertNotNull(type.getSimpleName() + ": nothing saved for " + item.getItem().keyName(),
                configManager.getConfiguration(identity.configGroup, item.getItem().keyName()));
        }

        Class<?> descriptorClass = Class.forName("net.runelite.client.plugins.config.PluginConfigurationDescriptor");
        Constructor<?> descriptorConstructor = descriptorClass.getDeclaredConstructor(
            String.class, String.class, String[].class, Config.class, ConfigDescriptor.class);
        descriptorConstructor.setAccessible(true);
        Object pluginConfig = descriptorConstructor.newInstance(type.getSimpleName(), "", new String[0], config, descriptor);
        Class<?> panelClass = Class.forName("net.runelite.client.plugins.config.ConfigPanel");
        Constructor<?> panelConstructor = panelClass.getDeclaredConstructors()[0];
        panelConstructor.setAccessible(true);
        Object[] args = new Object[panelConstructor.getParameterCount()];
        Class<?>[] params = panelConstructor.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (params[i] == ConfigManager.class) {
                args[i] = configManager;
            }
        }
        Method init = panelClass.getDeclaredMethod("init", descriptorClass);
        init.setAccessible(true);
        Throwable[] failure = new Throwable[1];
        java.util.List<String> clipped = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                Object panel = panelConstructor.newInstance(args);
                init.invoke(panel, pluginConfig);
                clipped.addAll(clipped((java.awt.Container) panel));
            } catch (Throwable t) {
                failure[0] = t.getCause() != null ? t.getCause() : t;
            }
        });
        // Every setting's name and choice fit the sidebar: nothing cut short ("Sell Pro..." beside its drop-down)
        org.junit.Assert.assertTrue(type.getSimpleName() + ": clipped in the settings panel: " + clipped, clipped.isEmpty());
        if (failure[0] != null) {
            throw new AssertionError(type.getSimpleName() + "'s settings panel failed to open", failure[0]);
        }
        // Long descriptions wrap in their tooltip instead of running across the screen
        for (ConfigItemDescriptor item : descriptor.getItems()) {
            String text = item.getItem().description();
            org.junit.Assert.assertTrue(type.getSimpleName() + ": wrap the description of " + item.getItem().keyName()
                + " in <div width='300'>", text.length() <= 60 || text.startsWith("<div width='300'>"));
        }
        return descriptor;
    }

    // The settings panel laid out at the sidebar's width with every section open: the labels and drop-downs (and the
    // choices in them) drawn narrower than their text
    private static java.util.List<String> clipped(java.awt.Container panel) {
        showAll(panel);
        panel.setSize(net.runelite.client.ui.PluginPanel.PANEL_WIDTH + net.runelite.client.ui.PluginPanel.SCROLLBAR_WIDTH, 20000);
        layoutAll(panel);
        java.util.List<String> out = new java.util.ArrayList<>();
        collect(panel, out);
        return out;
    }

    private static void showAll(java.awt.Component c) {
        c.setVisible(true);
        if (c instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) c).getComponents()) {
                showAll(child);
            }
        }
    }

    private static void layoutAll(java.awt.Component c) {
        if (c instanceof java.awt.Container) {
            ((java.awt.Container) c).doLayout();
            for (java.awt.Component child : ((java.awt.Container) c).getComponents()) {
                layoutAll(child);
            }
        }
    }

    private static void collect(java.awt.Component c, java.util.List<String> out) {
        if (c instanceof javax.swing.JLabel) {
            javax.swing.JLabel label = (javax.swing.JLabel) c;
            String text = label.getText();
            if (text != null && !text.isEmpty() && !text.startsWith("<html") && label.getWidth() > 0
                && label.getWidth() + 1 < label.getPreferredSize().width) {
                out.add("label \"" + text + "\" " + label.getWidth() + "<" + label.getPreferredSize().width);
            }
        }
        if (c instanceof javax.swing.JComboBox && c.getWidth() > 0 && c.getWidth() + 1 < c.getPreferredSize().width) {
            out.add("choice " + ((javax.swing.JComboBox<?>) c).getSelectedItem() + " " + c.getWidth() + "<" + c.getPreferredSize().width);
        }
        if (c instanceof java.awt.Container && !(c instanceof javax.swing.JComboBox)) {
            for (java.awt.Component child : ((java.awt.Container) c).getComponents()) {
                collect(child, out);
            }
        }
    }

    // RuneLite's ConfigManager with an empty settings file instead of a profile
    private static ConfigManager freshConfigManager() throws Exception {
        return freshConfigManager(new EventBus());
    }

    static ConfigManager freshConfigManager(EventBus eventBus) throws Exception {
        Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] params = constructor.getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i] == String.class) {
                args[i] = "test";
            } else if (params[i] == ScheduledExecutorService.class) {
                args[i] = EXECUTOR;
            } else if (params[i] == Gson.class) {
                args[i] = new Gson();
            } else if (params[i] == EventBus.class) {
                args[i] = eventBus;
            }
        }
        ConfigManager configManager = (ConfigManager) constructor.newInstance(args);
        Constructor<?> data = Class.forName("net.runelite.client.config.ConfigData").getDeclaredConstructor(File.class);
        data.setAccessible(true);
        File file = File.createTempFile("snuffys-settings", ".properties");
        file.deleteOnExit();
        Field profile = ConfigManager.class.getDeclaredField("configProfile");
        profile.setAccessible(true);
        profile.set(configManager, data.newInstance(file));
        return configManager;
    }
}
