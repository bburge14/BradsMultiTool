package com.snuffys.multitool;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.LinkBrowser;

/**
 * The plugin's About settings: Open the Guide (opens its part of the feature guide, then switches itself off),
 * and clashes with other plugins, checked live: when one that draws or says the same thing is on (or a setting of
 * it that does), a chat note says so once, remembered so it isn't repeated at every login; turning the other
 * plugin or setting off ends it, and turning it back on says it again. Only reads which plugins and settings are on; never changes them.
 */
@Singleton
class PluginClashes {

    private static final String SAID = "clashesSaid";
    private static final String GUIDE = "https://github.com/bburge14/BradsMultiTool/blob/HEAD/docs/FEATURES.md";

    /** Another plugin (by class name; RuneLite's own by full name), optionally only with one of its settings on. */
    static final class Clash {
        final String plugin;
        final String group;
        final List<String> keys;
        final String message;
        final Set<String> families;

        Clash(String plugin, String group, List<String> keys, String message, String... families) {
            this.plugin = plugin;
            this.group = group;
            this.keys = keys;
            this.message = message;
            this.families = new HashSet<>(Arrays.asList(families));
        }
    }

    private static Clash timer(String plugin, String name, String what) {
        return new Clash(plugin, null, Collections.emptyList(),
            name + " is on: Snuffy's " + what + " are hidden while it is (Use Other Timer Plugins)", "skilltracker");
    }

    private static final String CORE = "net.runelite.client.plugins.";

    static final List<Clash> CLASHES = Arrays.asList(
        timer("TreeDespawnTimerPlugin", "Tree Despawn Timer", "tree timers"),
        timer("MLMDespawnTimerPlugin", "MLM Despawn Timer", "Motherlode vein timers"),
        timer("CalcifiedRockDespawnTimerPlugin", "Calcified Rock Despawn Timer", "calcified rock timers"),
        timer("DaeyaltMiningTimerPlugin", "Daeyalt Mining Timer", "Daeyalt timers"),
        timer("FishingSpotTimerPlugin", "Fishing Spot Timer", "fishing spot timers"),
        timer("FishingSpotTrackerPlugin", "Fishing Spot Tracker", "fishing spot timers"),
        timer("BarbFishingPlugin", "Barbarian Fishing", "fishing spot timers"),
        timer("AerialFishingTimersPlugin", "Aerial Fishing Timers", "fishing spot timers"),
        timer("StarAfkTimerPlugin", "Star AFK Timer", "shooting star timers"),
        timer("SalvagingAfkTimerPlugin", "Salvaging AFK Timer", "shipwreck timers"),
        timer("FireTimerPlugin", "Fire Timer", "fire timers"),
        new Clash(CORE + "hunter.HunterPlugin", null, Collections.emptyList(),
            "RuneLite's Hunter plugin is on: Snuffy's trap timers are hidden while it is (Use Other Timer Plugins)", "skilltracker"),
        new Clash(CORE + "mining.MiningPlugin", null, Collections.emptyList(),
            "RuneLite's Mining plugin is on: Snuffy's depleted rock timers are hidden while it is (it draws its own)", "skilltracker"),
        new Clash(CORE + "slayer.SlayerPlugin", "slayer", Arrays.asList("highlightOutline", "highlightHull", "highlightTile"),
            "RuneLite's Slayer plugin highlights your task monsters too: turn off its highlight or Snuffy's Task Monster Outlines to see one", "pvm"),
        new Clash(CORE + "metronome.MetronomePlugin", null, Collections.emptyList(),
            "RuneLite's Metronome is on: with Snuffy's Metronome on too you'll hear both", "tickcounter"),
        new Clash(CORE + "idlenotifier.IdleNotifierPlugin", null, Collections.emptyList(),
            "RuneLite's Idle Notifier is on: it and Snuffy's idle alerts can both warn you", "skilltracker", "pvm", "gemcrab", "voices"));

    private final Client client;
    private final ClientThread clientThread;
    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final PluginIdentity identity;
    // Clashes currently on (said once each); the ones before the first login are said on it
    private final Set<Clash> active = new HashSet<>();
    private boolean announced;

    @Inject
    PluginClashes(Client client, ClientThread clientThread, PluginManager pluginManager, ConfigManager configManager,
                  PluginIdentity identity) {
        this.client = client;
        this.clientThread = clientThread;
        this.pluginManager = pluginManager;
        this.configManager = configManager;
        this.identity = identity;
    }

    /** The clashes for this plugin (the Toolbelt has them all). */
    static List<Clash> forFamily(String family) {
        if ("multitool".equals(family)) {
            return CLASHES;
        }
        List<Clash> out = new java.util.ArrayList<>();
        for (Clash clash : CLASHES) {
            if (clash.families.contains(family)) {
                out.add(clash);
            }
        }
        return out;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN && !announced) {
            announced = true;
            check();
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        clientThread.invokeLater(this::check);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (identity.configGroup.equals(event.getGroup()) && "openGuide".equals(event.getKey())
            && "true".equals(event.getNewValue())) {
            LinkBrowser.browse(GUIDE);
            configManager.setConfiguration(identity.configGroup, "openGuide", false);
            return;
        }
        if (!identity.configGroup.equals(event.getGroup())) {
            clientThread.invokeLater(this::check);
        }
    }

    // What's on now: new clashes are said (after login), ones that ended are forgotten so they're said again later
    private void check() {
        Set<String> on = new HashSet<>();
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (pluginManager.isPluginEnabled(plugin)) {
                on.add(plugin.getClass().getName());
                on.add(plugin.getClass().getSimpleName());
            }
        }
        Set<Clash> now = new LinkedHashSet<>();
        for (Clash clash : forFamily(identity.familyId)) {
            if (on.contains(clash.plugin) && settingOn(clash)) {
                now.add(clash);
            }
        }
        // Said once, and remembered (so not at every login); once a clash ends it's forgotten, to be said again
        Set<String> said = new HashSet<>(Arrays.asList(
            String.valueOf(configManager.getConfiguration(identity.configGroup, SAID)).split(",")));
        boolean changed = said.removeIf(name -> !name.isEmpty() && now.stream().noneMatch(c -> c.plugin.equals(name)));
        if (client.getGameState() == GameState.LOGGED_IN && announced) {
            for (Clash clash : now) {
                if (said.add(clash.plugin)) {
                    changed = true;
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName
                        + "]</col> Heads up: " + clash.message + ".", null);
                }
            }
        }
        if (changed) {
            said.remove("null");
            said.remove("");
            configManager.setConfiguration(identity.configGroup, SAID, String.join(",", said));
        }
        active.clear();
        active.addAll(now);
    }

    // No settings named: the plugin being on is enough; else any of them on
    private boolean settingOn(Clash clash) {
        if (clash.keys.isEmpty()) {
            return true;
        }
        for (String key : clash.keys) {
            if (Boolean.parseBoolean(configManager.getConfiguration(clash.group, key))) {
                return true;
            }
        }
        return false;
    }
}
