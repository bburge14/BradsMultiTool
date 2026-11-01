package com.snuffys.skilltracker;

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
 * of it that does), a warning icon shows among RuneLite's info boxes (hover it for the list) and it's said once a
 * login in chat; turning the other
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
        return plugin(plugin, name, name + " is on, so Snuffy's " + what + " are hidden (Use Other Timer Plugins)",
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
            "RuneLite's Hunter plugin is on, so Snuffy's trap timers are hidden (Use Other Timer Plugins)", "skilltracker"),
        plugin(CORE + "mining.MiningPlugin", "RuneLite's Mining",
            "RuneLite's Mining plugin is on and draws its own rock timers, so Snuffy's depleted rock and Daeyalt timers are hidden",
            "skilltracker"),
        setting(CORE + "fishing.FishingPlugin", "RuneLite's Fishing (spot icons)", "fishing", true,
            "RuneLite's Fishing plugin shows fishing spot icons, so Snuffy's spot tiles leave out the fish pictures",
            "skilltracker", "showIcons"),
        setting(CORE + "fishing.FishingPlugin", "RuneLite's Fishing (spot tiles)", "fishing", false,
            "RuneLite's Fishing plugin outlines fishing spot tiles, so Snuffy's spot tiles leave out their outline",
            "skilltracker", "showTiles"),
        setting(CORE + "fishing.FishingPlugin", "RuneLite's Fishing (minnows)", "fishing", true,
            "RuneLite's Fishing plugin times minnow spots, so Snuffy's minnow timers are hidden",
            "skilltracker", "showMinnowOverlay"),
        setting(CORE + "motherlode.MotherlodePlugin", "RuneLite's Motherlode Mine (veins)", "motherlode", true,
            "RuneLite's Motherlode Mine plugin marks the veins, so Snuffy's vein timers leave out their pictures",
            "skilltracker", "showVeins"),
        setting(CORE + "agility.AgilityPlugin", "RuneLite's Agility (arena timer)", "agility", true,
            "RuneLite's Agility plugin also times the Brimhaven Arena pillar; turn off its Agility Arena timer or Snuffy's Brimhaven Arena Card to see just one",
            "skilltracker", "agilityArenaTimer"),
        setting(CORE + "agility.AgilityPlugin", "RuneLite's Agility (lap counter)", "agility", true,
            "RuneLite's Agility plugin also counts your laps; turn off its Show Lap Count or Snuffy's Course Card to see just one",
            "skilltracker", "showLapCount"),
        setting(CORE + "slayer.SlayerPlugin", "RuneLite's Slayer (task highlight)", "slayer", false,
            "RuneLite's Slayer plugin also highlights your task monsters; turn off its highlight or Snuffy's Highlight Task Monsters to see just one",
            "pvm", "highlightOutline", "highlightHull", "highlightTile"),
        plugin(CORE + "metronome.MetronomePlugin", "RuneLite's Metronome",
            "RuneLite's Metronome is on; with Snuffy's Metronome on too, you'll hear both", "tickcounter"),
        plugin(CORE + "idlenotifier.IdleNotifierPlugin", "RuneLite's Idle Notifier",
            "RuneLite's Idle Notifier is on, so it and Snuffy's idle alerts can both warn you", "skilltracker", "pvm", "gemcrab", "voices"));

    private final Client client;
    private final ClientThread clientThread;
    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final PluginIdentity identity;
    private final InGame inGame;
    private final Lifecycle lifecycle;
    private final net.runelite.client.ui.overlay.infobox.InfoBoxManager infoBoxes;
    // The warning icon among RuneLite's info boxes (top left) while something clashes; hover it for the list
    private ClashInfoBox infoBox;
    // The clashes said in chat this login
    private final Set<Clash> said = new HashSet<>();
    private static final int CHECK_TICKS = 5;

    @Inject
    PluginClashes(Client client, ClientThread clientThread, PluginManager pluginManager, ConfigManager configManager,
                  PluginIdentity identity, InGame inGame, Lifecycle lifecycle,
                  net.runelite.client.ui.overlay.infobox.InfoBoxManager infoBoxes) {
        this.inGame = inGame;
        this.lifecycle = lifecycle;
        this.infoBoxes = infoBoxes;
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
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            // Said again at the next login
            said.clear();
        }
    }

    // Checked every few seconds too, not only when a plugin or setting changes, so nothing is missed
    @Subscribe
    public void onGameTick(net.runelite.api.events.GameTick event) {
        if (client.getTickCount() % CHECK_TICKS == 0) {
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
        // Clash Warnings off: nothing shown or said
        boolean warn = !"false".equals(configManager.getConfiguration(identity.configGroup, "showClashes"));
        for (Clash clash : warn ? forFamily(identity.familyId) : Collections.<Clash>emptyList()) {
            if (on.contains(clash.plugin) && settingOn(clash)) {
                now.add(clash);
            }
        }
        said.retainAll(now);
        // In the game and past the welcome screen, so the chat box shows it
        if (client.getGameState() == GameState.LOGGED_IN && inGame.ready()) {
            for (Clash clash : now) {
                if (said.add(clash)) {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName
                        + "]</col> " + clash.message + ".", null);
                }
            }
        }
        showInfoBox(now);
    }

    // Every one of Snuffy's plugins shows clashes this way (not only the ones with a sidebar)
    private void showInfoBox(List<Clash> now) {
        if (now.isEmpty() || client.getGameState() != GameState.LOGGED_IN) {
            stop();
            return;
        }
        StringBuilder tip = new StringBuilder(identity.chatName + ": other plugins doing the same thing");
        for (Clash clash : now) {
            tip.append("</br>- ").append(clash.message);
        }
        if (infoBox == null && lifecycle.plugin != null) {
            infoBox = new ClashInfoBox(lifecycle.plugin);
            infoBoxes.addInfoBox(infoBox);
        }
        if (infoBox != null) {
            infoBox.setTooltip(tip.toString());
        }
    }

    /** Takes the warning icon away (no clashes, logged out, or the plugin stopping). */
    void stop() {
        if (infoBox != null) {
            infoBoxes.removeInfoBox(infoBox);
            infoBox = null;
        }
    }

    /** A small yellow warning sign, drawn here rather than shipped as an image. */
    static final class ClashInfoBox extends net.runelite.client.ui.overlay.infobox.InfoBox {
        ClashInfoBox(Plugin plugin) {
            super(icon(), plugin);
        }

        private static java.awt.image.BufferedImage icon() {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(24, 24, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.Polygon sign = new java.awt.Polygon(new int[]{12, 23, 1}, new int[]{1, 22, 22}, 3);
            g.setColor(new java.awt.Color(255, 200, 0));
            g.fill(sign);
            g.setColor(java.awt.Color.BLACK);
            g.draw(sign);
            g.fillRect(11, 8, 3, 8);
            g.fillRect(11, 18, 3, 2);
            g.dispose();
            return image;
        }

        @Override
        public String getText() {
            return "";
        }

        @Override
        public java.awt.Color getTextColor() {
            return java.awt.Color.WHITE;
        }
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
