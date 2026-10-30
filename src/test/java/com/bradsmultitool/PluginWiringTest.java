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
}
