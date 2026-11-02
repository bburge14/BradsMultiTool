package com.bradsmultitool;

import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;

/**
 * The Tick Counter's hotkeys (none set at first): reset the count to 1, show or hide the tick overlays, and step
 * Ticks Per Cycle up or down. Like the Slayer card hotkey, they only change our own overlays and settings.
 */
@Singleton
class TickHotkeys {

    private static final int MOST_TICKS = 10;

    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final KeyManager keyManager;
    private final PluginIdentity identity;
    private final TickConfig config;
    private final TickCounter ticks;
    private final List<HotkeyListener> listeners;

    @Inject
    TickHotkeys(Client client, ClientThread clientThread, ConfigManager configManager, KeyManager keyManager,
                PluginIdentity identity, TickConfig config, TickCounter ticks) {
        this.client = client;
        this.clientThread = clientThread;
        this.configManager = configManager;
        this.keyManager = keyManager;
        this.identity = identity;
        this.config = config;
        this.ticks = ticks;
        listeners = Arrays.asList(
            hotkey(config::tickResetKey, ticks::resetCycle),
            hotkey(config::tickToggleKey, () -> {
                ticks.toggleHidden();
                chat("Tick counter " + (ticks.isHidden() ? "hidden" : "shown"));
            }),
            hotkey(config::tickCycleUpKey, () -> stepCycle(1)),
            hotkey(config::tickCycleDownKey, () -> stepCycle(-1)));
    }

    // Keys come in on the window's thread; the count and the settings change on the client's
    private HotkeyListener hotkey(java.util.function.Supplier<net.runelite.client.config.Keybind> key, Runnable action) {
        return new HotkeyListener(key) {
            @Override
            public void hotkeyPressed() {
                clientThread.invokeLater(action);
            }
        };
    }

    void start() {
        listeners.forEach(keyManager::registerKeyListener);
    }

    void stop() {
        listeners.forEach(keyManager::unregisterKeyListener);
    }

    private void stepCycle(int step) {
        int cycle = Math.max(1, Math.min(MOST_TICKS, config.tickCycle() + step));
        configManager.setConfiguration(identity.configGroup, "tickCycle", cycle);
        String note = config.tickFollowWeapon() ? " (Follow Attack Speed is on: the cycle is your weapon's)"
            : config.tickMethod() != TickConfig.TickMethod.CUSTOM ? " (a Tick Pattern is picked: it sets the cycle)" : "";
        chat("Ticks Per Cycle: " + cycle + note);
    }

    private void chat(String message) {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName + "]</col> " + message, null);
    }
}
