package com.snuffys.tickcounter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
 * and clashes with other plugins, checked live: while one that draws or says the same thing is on (or a setting
 * of it that does), it's listed in the Skilling Helper's sidebar and said once a login in chat; turning the other
 * plugin or setting off ends it, and turning it back on says it again. Only reads which plugins and settings are on; never changes them.
 */
@Singleton
class PluginClashes {

    private static final String GUIDE = "https://github.com/bburge14/BradsMultiTool/blob/HEAD/docs/FEATURES.md";

    /**
     * Another plugin (by class name; RuneLite's own by full name), optionally only while one of its settings is on
     * (keyDefault: that setting's default, when nothing's saved), with its name for the sidebar and the note.
     */
    static final class Clash {
        final String plugin;
        final String name;
        final String group;
        final List<String> keys;
        final boolean keyDefault;
        final String message;
        final Set<String> families;

        Clash(String plugin, String name, String group, List<String> keys, boolean keyDefault, String message, String... families) {
            this.plugin = plugin;
            this.name = name;
            this.group = group;
            this.keys = keys;
            this.keyDefault = keyDefault;
            this.message = message;
            this.families = new HashSet<>(Arrays.asList(families));
        }
    }

    private static Clash plugin(String plugin, String name, String message, String... families) {
        return new Clash(plugin, name, null, Collections.emptyList(), false, message, families);
    }

    private static Clash setting(String plugin, String name, String group, boolean byDefault, String message, String family,
                                 String... keys) {
        return new Clash(plugin, name, group, Arrays.asList(keys), byDefault, message, family);
    }

    private static Clash timer(String plugin, String name, String what) {
        return plugin(plugin, name, name + " is on: Snuffy's " + what + " are hidden while it is (Use Other Timer Plugins)",
            "skilltracker");
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
        plugin(CORE + "hunter.HunterPlugin", "RuneLite's Hunter",
            "RuneLite's Hunter plugin is on: Snuffy's trap timers are hidden while it is (Use Other Timer Plugins)", "skilltracker"),
        plugin(CORE + "mining.MiningPlugin", "RuneLite's Mining",
            "RuneLite's Mining plugin is on: Snuffy's depleted rock and Daeyalt timers are hidden while it is (it draws its own)",
            "skilltracker"),
        setting(CORE + "fishing.FishingPlugin", "RuneLite's Fishing (spot icons)", "fishing", true,
            "RuneLite's Fishing plugin shows fishing spot icons: Snuffy's fishing spot tiles leave out their fish pictures while it does",
            "skilltracker", "showIcons"),
        setting(CORE + "fishing.FishingPlugin", "RuneLite's Fishing (spot tiles)", "fishing", false,
            "RuneLite's Fishing plugin outlines fishing spot tiles: Snuffy's leave out their own outline while it does",
            "skilltracker", "showTiles"),
        setting(CORE + "fishing.FishingPlugin", "RuneLite's Fishing (minnows)", "fishing", true,
            "RuneLite's Fishing plugin times minnow spots: Snuffy's minnow spot timers are hidden while it does",
            "skilltracker", "showMinnowOverlay"),
        setting(CORE + "motherlode.MotherlodePlugin", "RuneLite's Motherlode Mine (veins)", "motherlode", true,
            "RuneLite's Motherlode Mine plugin marks the veins: Snuffy's vein timers leave out their pictures while it does",
            "skilltracker", "showVeins"),
        setting(CORE + "slayer.SlayerPlugin", "RuneLite's Slayer (task highlight)", "slayer", false,
            "RuneLite's Slayer plugin highlights your task monsters too: turn off its highlight or Snuffy's Task Monster Outlines to see one",
            "pvm", "highlightOutline", "highlightHull", "highlightTile"),
        plugin(CORE + "metronome.MetronomePlugin", "RuneLite's Metronome",
            "RuneLite's Metronome is on: with Snuffy's Metronome on too you'll hear both", "tickcounter"),
        plugin(CORE + "idlenotifier.IdleNotifierPlugin", "RuneLite's Idle Notifier",
            "RuneLite's Idle Notifier is on: it and Snuffy's idle alerts can both warn you", "skilltracker", "pvm", "gemcrab", "voices"));

    private final Client client;
    private final ClientThread clientThread;
    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final PluginIdentity identity;
    // Clashes on right now; and the ones said in chat this login
    private volatile List<Clash> active = Collections.emptyList();
    private final Set<Clash> said = new HashSet<>();
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
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            // Said again at the next login
            announced = false;
            said.clear();
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

    // What's on now, kept for the sidebar; each clash is said in chat once a login (and again if it ends and
    // comes back)
    private void check() {
        Set<String> on = new HashSet<>();
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (pluginManager.isPluginEnabled(plugin)) {
                on.add(plugin.getClass().getName());
                on.add(plugin.getClass().getSimpleName());
            }
        }
        List<Clash> now = new java.util.ArrayList<>();
        for (Clash clash : forFamily(identity.familyId)) {
            if (on.contains(clash.plugin) && settingOn(clash)) {
                now.add(clash);
            }
        }
        said.retainAll(now);
        if (client.getGameState() == GameState.LOGGED_IN && announced) {
            for (Clash clash : now) {
                if (said.add(clash)) {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName
                        + "]</col> Heads up: " + clash.message + ".", null);
                }
            }
        }
        active = Collections.unmodifiableList(now);
    }

    /** The clashes on right now (for the sidebar). */
    List<Clash> active() {
        return active;
    }

    // No settings named: the plugin being on is enough; else any of them on
    private boolean settingOn(Clash clash) {
        if (clash.keys.isEmpty()) {
            return true;
        }
        for (String key : clash.keys) {
            String value = configManager.getConfiguration(clash.group, key);
            if (value == null ? clash.keyDefault : Boolean.parseBoolean(value)) {
                return true;
            }
        }
        return false;
    }
}
