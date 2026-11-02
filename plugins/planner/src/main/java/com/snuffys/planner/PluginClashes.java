package com.snuffys.planner;

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
 * of it that does), a warning icon shows among RuneLite's info boxes (hover it for the list); nothing goes in chat.
 * Turning the other plugin or setting off takes it away. Only reads which plugins and settings are on; never changes
 * them.
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

    // A separate plugin of ours that the Toolbelt already has in it (only the Toolbelt warns about these)
    private static Clash own(String plugin, String name) {
        return plugin(plugin, name, name + " is on, and the Toolbelt already has it in it; turn off one or the other",
            "toolbelt-only");
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
        plugin("HunterRumoursPlugin", "Hunter Rumours",
            "Hunter Rumours is on and tracks your rumour too; turn off Snuffy's Rumour Card (Hunter) to see just one",
            "skilltracker"),
        plugin("SkillsTabProgressBarsPlugin", "Skills Tab Progress Bars",
            "Skills Tab Progress Bars is on and draws level progress on the Skills tab, so Snuffy's level bars there are hidden; Snuffy's goal bars still show on the left edge",
            "skilltracker"),
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
        setting(CORE + "agility.AgilityPlugin", "RuneLite's Agility (obstacle outlines)", "agility", true,
            "RuneLite's Agility plugin outlines most Brimhaven Arena obstacles, so Snuffy's level colours only show on the rest; turn off its Show Clickboxes to see Snuffy's colours on all of them",
            "skilltracker", "showClickboxes"),
        setting(CORE + "agility.AgilityPlugin", "RuneLite's Agility (lap counter)", "agility", true,
            "RuneLite's Agility plugin also counts your laps; turn off its Show Lap Count or Snuffy's Course Card to see just one",
            "skilltracker", "showLapCount"),
        setting(CORE + "slayer.SlayerPlugin", "RuneLite's Slayer (task highlight)", "slayer", false,
            "RuneLite's Slayer plugin also highlights your task monsters; turn off its highlight or Snuffy's Highlight Task Monsters to see just one",
            "pvm", "highlightOutline", "highlightHull", "highlightTile"),
        plugin(CORE + "wintertodt.WintertodtPlugin", "RuneLite's Wintertodt",
            "RuneLite's Wintertodt plugin is on, so it and Snuffy's Wintertodt alerts can both warn you (round starting, brazier, roots, cold); turn off its notifications or Snuffy's Minigames alerts to hear just one",
            "skilltracker"),
        setting("com.tempoross.TemporossPlugin", "Tempoross (notifications)", "tempoross", false,
            "Tempoross's notifications are on, so it and Snuffy's Tempoross alerts can both warn you (wave, fire, double spot, vulnerable); turn off one of them to hear just one",
            "skilltracker", "waveNotification", "fireNotification", "doubleSpotNotification", "vulnerableNotification"),
        setting("com.datbear.GuardiansOfTheRiftHelperPlugin", "Guardians of the Rift Helper (portal)", "guardiansOfTheRiftHelper", true,
            "Guardians of the Rift Helper's portal notification is on, so it and Snuffy's Portal Opened alert can both warn you; turn off one of them to hear just one",
            "skilltracker", "portalSpawn"),
        setting("com.duckblade.osrs.sailing.SailingPlugin", "Sailing (low boat HP)", "sailing", false,
            "Sailing's low boat HP notification is on, so it and Snuffy's Boat Health Low can both warn you; turn one off to hear just one",
            "skilltracker", "lowBoatHPNotify"),
        // Snuffy's own: the Toolbelt has every separate plugin in it, so install one or the other
        plugin("com.snuffys.multitool.BradsMultiToolPlugin", "Snuffy's Toolbelt",
            "Snuffy's Toolbelt is on and already has this plugin's features in it; turn off one or the other",
            "skilltracker", "pvm", "gemcrab", "tickcounter", "planner", "voices"),
        own("com.snuffys.skilltracker.SkillTrackerPlugin", "Snuffy's Skilling Helper"),
        own("com.snuffys.pvm.PvmPlugin", "Snuffy's PvM/Slayer"),
        own("com.snuffys.gemcrab.GemCrabPlugin", "Snuffy's GemCrab Tracker"),
        own("com.snuffys.tickcounter.TickCounterPlugin", "Snuffy's Tick Counter"),
        own("com.snuffys.planner.PlannerPlugin", "Snuffy's Planner"),
        own("com.snuffys.voices.VoicesPlugin", "Snuffy's Alerts"),
        plugin(CORE + "metronome.MetronomePlugin", "RuneLite's Metronome",
            "RuneLite's Metronome is on; with Snuffy's Metronome on too, you'll hear both", "tickcounter"),
        plugin(CORE + "idlenotifier.IdleNotifierPlugin", "RuneLite's Idle Notifier",
            "RuneLite's Idle Notifier is on, so it and Snuffy's idle alerts can both warn you", "skilltracker", "pvm", "gemcrab",
            "planner", "voices"),
        plugin(CORE + "dailytaskindicators.DailyTasksPlugin", "RuneLite's Daily Task Indicator",
            "RuneLite's Daily Task Indicator is on, so it reminds you of your dailies in chat too; turn it off or Snuffy's Voice: Dailies to hear of them once",
            "planner"),
        plugin(CORE + "loottracker.LootTrackerPlugin", "RuneLite's Loot Tracker",
            "RuneLite's Loot Tracker is on and lists your drops too; Snuffy's DROPS counts chests, caskets and raids from it, so keep it on and hide whichever list you don't need",
            "planner"),
        // RuneLite's own Death Indicator left the core client; a plugin by that class name (from the Plugin Hub) does the same
        plugin("DeathIndicatorPlugin", "Death Indicator",
            "Death Indicator is on and also marks your death; turn off its info box or Snuffy's Gravestone Timer to see just one", "pvm"));

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

    // What's on now: shown as the warning icon (hover it for the list)
    private void check() {
        Set<String> on = new HashSet<>();
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (pluginManager.isPluginEnabled(plugin)) {
                on.add(plugin.getClass().getName());
                on.add(plugin.getClass().getSimpleName());
            }
        }
        List<Clash> now = new java.util.ArrayList<>();
        // Clash Warnings off: nothing shown
        boolean warn = !"false".equals(configManager.getConfiguration(identity.configGroup, "showClashes"));
        String self = lifecycle.plugin != null ? lifecycle.plugin.getClass().getName() : "";
        for (Clash clash : warn ? forFamily(identity.familyId) : Collections.<Clash>emptyList()) {
            // Never a clash with itself
            if (on.contains(clash.plugin) && !clash.plugin.equals(self) && settingOn(clash)) {
                now.add(clash);
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
            tip.append(wrap("- " + clash.message));
        }
        if (infoBox == null && lifecycle.plugin != null) {
            infoBox = new ClashInfoBox(lifecycle.plugin);
            infoBoxes.addInfoBox(infoBox);
        }
        if (infoBox != null) {
            infoBox.setTooltip(tip.toString());
        }
    }

    // RuneLite's tooltips don't wrap: break each note into short lines so it fits beside an open sidebar
    private static final int TIP_WIDTH = 55;

    static String wrap(String text) {
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > TIP_WIDTH) {
                out.append("</br>").append(line);
                line = new StringBuilder("  ");
            } else if (line.length() > 0 && !line.toString().equals("  ")) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.toString().trim().length() > 0) {
            out.append("</br>").append(line);
        }
        return out.toString();
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
            if (value == null ? clash.keyDefault : on(value)) {
                return true;
            }
        }
        return false;
    }

    // A switch ("true"), or a notification setting, which RuneLite saves as JSON ({"enabled":true,...})
    static boolean on(String value) {
        return Boolean.parseBoolean(value) || value.replace(" ", "").contains("\"enabled\":true");
    }
}
