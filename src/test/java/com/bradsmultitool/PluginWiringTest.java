package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Stage;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import org.junit.Test;

/**
 * Checks that everything a plugin injects can be built: Guice's TOOL stage works out the whole dependency
 * graph without creating anything, so a missing binding (a settings interface the plugin doesn't provide,
 * say) fails here instead of when the client starts the plugin. RuneLite's own services are stand-ins.
 */
public class PluginWiringTest {

    private static <T> Provider<T> none() {
        return () -> null;
    }

    private static final Module RUNELITE = new AbstractModule() {
        @Override
        protected void configure() {
            bind(Client.class).toProvider(none());
            bind(ClientThread.class).toProvider(none());
            bind(ConfigManager.class).toProvider(none());
            bind(OverlayManager.class).toProvider(none());
            bind(EventBus.class).toProvider(none());
            bind(ItemManager.class).toProvider(none());
            bind(SpriteManager.class).toProvider(none());
            bind(SkillIconManager.class).toProvider(none());
            bind(ClientToolbar.class).toProvider(none());
            bind(KeyManager.class).toProvider(none());
            bind(Notifier.class).toProvider(none());
            bind(Gson.class).toProvider(none());
            bind(PluginManager.class).toProvider(none());
            bind(ModelOutlineRenderer.class).toProvider(none());
            bind(ScheduledExecutorService.class).toProvider(none());
        }
    };

    // A RuneLite plugin is itself a Guice module: its @Provides methods (settings, identity) come with it
    private static void assertWired(Class<? extends net.runelite.client.plugins.Plugin> plugin) throws ReflectiveOperationException {
        Module pluginModule = plugin.getDeclaredConstructor().newInstance();
        Guice.createInjector(Stage.TOOL, RUNELITE, pluginModule, binder -> binder.bind(plugin));
    }

    @Test
    public void multiToolIsWired() throws ReflectiveOperationException {
        assertWired(BradsMultiToolPlugin.class);
    }

    @Test
    public void separatePluginsAreWired() throws ReflectiveOperationException {
        assertWired(SkillTrackerPlugin.class);
        assertWired(PvmPlugin.class);
        assertWired(GemCrabPlugin.class);
        assertWired(VoicesPlugin.class);
    }

    /**
     * Builds each plugin the way RuneLite does (a child injector from the plugin, then its fields) with stand-in
     * services, creating every class it injects. Catches what the TOOL stage can't: a constructor cycle, which
     * RuneLite's Guice doesn't report but hangs on (the plugin then never loads), hence the time limit.
     */
    // A do-nothing stand-in for a RuneLite service: a proxy for an interface, an object made without running
    // its constructor for a class (test code only; it isn't shipped with the plugin)
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        if (type.isInterface()) {
            return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                Class<?> r = method.getReturnType();
                if (r == boolean.class) {
                    return false;
                }
                if (r.isPrimitive() && r != void.class) {
                    return r == long.class ? 0L : r == double.class ? 0.0 : r == float.class ? 0f : 0;
                }
                return null;
            });
        }
        try {
            java.lang.reflect.Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);
            return (T) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test(timeout = 30_000)
    public void everyPluginBuilds() throws ReflectiveOperationException {
        Module services = new AbstractModule() {
            @Override
            protected void configure() {
                for (Class<?> service : new Class<?>[]{Client.class, ClientThread.class, ConfigManager.class, OverlayManager.class,
                    ItemManager.class, SpriteManager.class, SkillIconManager.class, ClientToolbar.class, KeyManager.class,
                    Notifier.class, Gson.class, PluginManager.class, ModelOutlineRenderer.class, ScheduledExecutorService.class}) {
                    bindStub(service);
                }
                bind(EventBus.class).toInstance(new EventBus());
            }

            private <T> void bindStub(Class<T> service) {
                bind(service).toInstance(stub(service));
            }
        };
        for (Class<? extends net.runelite.client.plugins.Plugin> type : java.util.Arrays.asList(BradsMultiToolPlugin.class,
            SkillTrackerPlugin.class, PvmPlugin.class, GemCrabPlugin.class, VoicesPlugin.class)) {
            net.runelite.client.plugins.Plugin plugin = type.getDeclaredConstructor().newInstance();
            // The settings come from RuneLite's ConfigManager, which can't run here: stand-ins instead
            Module settings = com.google.inject.util.Modules.override(plugin).with(binder -> {
                binder.bind(BradsMultiToolConfig.class).toInstance(stub(BradsMultiToolConfig.class));
                binder.bind(SkillTrackerConfig.class).toInstance(stub(SkillTrackerConfig.class));
                binder.bind(PvmConfig.class).toInstance(stub(PvmConfig.class));
                binder.bind(GemCrabConfig.class).toInstance(stub(GemCrabConfig.class));
                binder.bind(VoicesConfig.class).toInstance(stub(VoicesConfig.class));
                binder.bind(CommonConfig.class).toInstance(stub(CommonConfig.class));
            });
            Guice.createInjector(services).createChildInjector(settings).injectMembers(plugin);
        }
    }
}
