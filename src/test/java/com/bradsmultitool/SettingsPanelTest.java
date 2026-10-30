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
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Opens each plugin's settings in RuneLite's own settings panel on a fresh install (nothing saved), after what
 * the client saves (setDefaultConfiguration) and what the plugin saves (ConfigDefaults). RuneLite only saves the
 * settings an interface declares itself, and the panel throws on a choice setting with nothing saved, which is
 * what broke the Toolbelt's settings when the Tick Counter's were added. Reaches into RuneLite's internals with
 * reflection: test code only, never shipped.
 */
public class SettingsPanelTest {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    @AfterClass
    public static void stop() {
        EXECUTOR.shutdownNow();
    }

    @Test
    public void toolbelt() throws Exception {
        open(BradsMultiToolConfig.class, PluginIdentity.MULTI_TOOL, (defaults) -> {
            BradsMultiToolConfig config = (BradsMultiToolConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            SkillTrackerConfigDefaults.store(defaults.store, config);
            TickConfigDefaults.store(defaults.store, config);
            PvmConfigDefaults.store(defaults.store, config);
            GemCrabConfigDefaults.store(defaults.store, config);
        });
    }

    @Test
    public void skillingHelper() throws Exception {
        open(SkillTrackerConfig.class, PluginIdentity.SKILL_TRACKER, (defaults) -> {
            SkillTrackerConfig config = (SkillTrackerConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            SkillTrackerConfigDefaults.store(defaults.store, config);
            TickConfigDefaults.store(defaults.store, config);
        });
    }

    @Test
    public void pvm() throws Exception {
        open(PvmConfig.class, PluginIdentity.PVM, (defaults) -> {
            PvmConfig config = (PvmConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            PvmConfigDefaults.store(defaults.store, config);
            TickConfigDefaults.store(defaults.store, config);
        });
    }

    @Test
    public void gemCrab() throws Exception {
        open(GemCrabConfig.class, PluginIdentity.GEM_CRAB, (defaults) -> {
            GemCrabConfig config = (GemCrabConfig) defaults.config;
            CommonConfigDefaults.store(defaults.store, config);
            GemCrabConfigDefaults.store(defaults.store, config);
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

    private static <T extends Config> void open(Class<T> type, PluginIdentity identity, Consumer<Defaults> pluginDefaults) throws Exception {
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
        SwingUtilities.invokeAndWait(() -> {
            try {
                init.invoke(panelConstructor.newInstance(args), pluginConfig);
            } catch (Throwable t) {
                failure[0] = t.getCause() != null ? t.getCause() : t;
            }
        });
        if (failure[0] != null) {
            throw new AssertionError(type.getSimpleName() + "'s settings panel failed to open", failure[0]);
        }
    }

    // RuneLite's ConfigManager with an empty settings file instead of a profile
    private static ConfigManager freshConfigManager() throws Exception {
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
                args[i] = new EventBus();
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
