package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
    name = "Snuffy's Multi-Tool",
    description = "Skill tracker with goals and training suggestions, skilling hover cards and timers, PvM HUD with buff timers and voice alerts, Slayer helper, Gemstone Crab tracker",
    tags = {"snuffy", "xp", "skills", "gemstone", "crab", "shell", "tunnel", "logout", "voice"}
)
public class BradsMultiToolPlugin extends Plugin {

    private static final String CONFIG_GROUP = "bradsmultitool";

    @Inject
    private Client client;

    @Inject
    private Gson gson;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BradsMultiToolOverlay overlay;

    @Inject
    private BradsMultiToolPrayerOverlay prayerOverlay;

    @Inject
    private BradsMultiToolIdleWarningOverlay idleWarningOverlay;

    @Inject
    private BradsMultiToolHoverOverlay hoverOverlay;

    @Inject
    private BradsMultiToolOrbOverlay orbOverlay;

    @Inject
    private BradsMultiToolSlayerOverlay slayerOverlay;

    @Inject
    private BradsMultiToolSkillingOverlay skillingOverlay;

    @Inject
    private EventBus eventBus;

    @Inject
    private CombatAlerts combatAlerts;

    @Inject
    private SkillingAlerts skillingAlerts;

    @Inject
    private PluginFamily pluginFamily;

    @Inject
    private QuestProgress questProgress;


    @Inject
    private BradsMultiToolCrabOutlineOverlay crabOutlineOverlay;

    @Inject
    private CrabHudOverlay crabHudOverlay;

    @Inject
    private ResourceTimers resourceTimers;

    @Inject
    private FarmingData farmingData;

    @Inject
    private SkillHelperData skillHelperData;

    @Inject
    private BirdhouseTracker birdhouseTracker;

    @Inject
    private OtherTimerPlugins otherTimerPlugins;

    @Inject
    private BradsMultiToolResourceTimerOverlay resourceTimerOverlay;

    @Inject
    private SkillingData skillingData;

    @Inject
    private MonsterData monsterData;

    @Inject
    private BradsMultiToolConfig config;

    @Inject
    private ClientThread clientThread;

    @Inject
    private SlayerData slayerData;


    @Inject
    private TrainingData trainingData;

    @Inject
    private BradsMultiToolTrainingOverlay trainingOverlay;

    @Inject
    private RecipeData recipeData;

    @Inject
    private MoneyData moneyData;

    @Inject
    private TrainingAdvisor trainingAdvisor;

    @Inject
    private GoalItems goalItems;

    @Inject
    private BradsMultiToolGoalItemOverlay goalItemOverlay;

    @Inject
    private BradsMultiToolSlayerHighlightOverlay slayerHighlightOverlay;

    @Inject
    private GoalTargetMenu goalTargetMenu;

    @Inject
    private BradsMultiToolPinnedSkillOverlay pinnedSkillOverlay;

    @Inject
    private ConfigManager configManager;

    @Inject
    private Notifier notifier;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private SkillIconManager skillIconManager;

    @Inject
    private Alerts alerts;

    @Inject
    private IdleWarnings idleWarnings;

    @Inject
    @Getter
    private GemCrabTracker gemCrab;

    @Inject
    @Getter
    private CombatStyle combatStyle;

    @Inject
    @Getter
    private SkillTracker skillTracker;

    @Inject
    @Getter
    private PvmTracker pvm;

    @Override
    protected void startUp() throws Exception {
        monsterData.load(gson);
        skillingData.load(gson);
        farmingData.load(gson);
        skillHelperData.load(gson);
        slayerData.load(gson);
        trainingData.load(gson);
        recipeData.load(gson);
        moneyData.load(gson);
        eventBus.register(trainingOverlay);
        eventBus.register(goalItems);
        eventBus.register(goalTargetMenu);
        eventBus.register(combatAlerts);
        eventBus.register(skillingAlerts);
        eventBus.register(resourceTimers);
        eventBus.register(farmingData);
        eventBus.register(birdhouseTracker);
        eventBus.register(otherTimerPlugins);
        pluginFamily.start();
        eventBus.register(alerts);
        eventBus.register(idleWarnings);
        // Hitting the Gemstone Crab counts as busy for the idle alert (its hits come slowly)
        idleWarnings.addBusy(gemCrab::isPlayerAttackingCrab);
        eventBus.register(gemCrab);
        eventBus.register(combatStyle);
        eventBus.register(questProgress);
        eventBus.register(skillingOverlay);
        otherTimerPlugins.refresh();
        eventBus.register(pvm);
        pvm.startUp();
        overlayManager.add(overlay);
        overlayManager.add(prayerOverlay);
        overlayManager.add(idleWarningOverlay);
        overlayManager.add(hoverOverlay);
        overlayManager.add(orbOverlay);
        overlayManager.add(slayerOverlay);
        overlayManager.add(skillingOverlay);
        overlayManager.add(resourceTimerOverlay);
        overlayManager.add(crabOutlineOverlay);
        overlayManager.add(crabHudOverlay);
        // With PvM running too, the crab's card also shows buff timers and LOW prayer/health
        crabHudOverlay.setExtraRows(overlay::addStatusRows);
        overlayManager.add(trainingOverlay);
        overlayManager.add(goalItemOverlay);
        overlayManager.add(slayerHighlightOverlay);
        overlayManager.add(pinnedSkillOverlay);

        eventBus.register(skillTracker);
        skillTracker.startUp();
        // The tracker's Reset all also clears the crab's and PvM's session numbers here
        skillTracker.addResetListener(gemCrab::resetSession);
        skillTracker.addResetListener(pvm::resetSession);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        overlayManager.remove(prayerOverlay);
        overlayManager.remove(idleWarningOverlay);
        overlayManager.remove(hoverOverlay);
        overlayManager.remove(orbOverlay);
        overlayManager.remove(slayerOverlay);
        overlayManager.remove(skillingOverlay);
        overlayManager.remove(resourceTimerOverlay);
        overlayManager.remove(crabOutlineOverlay);
        overlayManager.remove(crabHudOverlay);
        overlayManager.remove(trainingOverlay);
        overlayManager.remove(goalItemOverlay);
        overlayManager.remove(slayerHighlightOverlay);
        overlayManager.remove(pinnedSkillOverlay);
        eventBus.unregister(trainingOverlay);
        pluginFamily.stop();
        eventBus.unregister(questProgress);
        eventBus.unregister(skillingOverlay);
        eventBus.unregister(goalItems);
        eventBus.unregister(goalTargetMenu);
        eventBus.unregister(combatAlerts);
        eventBus.unregister(skillingAlerts);
        eventBus.unregister(resourceTimers);
        eventBus.unregister(farmingData);
        eventBus.unregister(birdhouseTracker);
        eventBus.unregister(otherTimerPlugins);
        eventBus.unregister(pvm);
        pvm.shutDown();
        eventBus.unregister(skillTracker);
        skillTracker.shutDown();
        eventBus.unregister(alerts);
        eventBus.unregister(idleWarnings);
        alerts.shutDown();
        eventBus.unregister(gemCrab);
        eventBus.unregister(combatStyle);
        gemCrab.reset();
    }

    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.MULTI_TOOL;
    }

    // The all-in-one's settings stand in for each plugin's own (same keys, one group)
    @Provides
    CommonConfig provideCommonConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    SkillTrackerConfig provideSkillTrackerConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    PvmConfig providePvmConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    GemCrabConfig provideGemCrabConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    BradsMultiToolConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BradsMultiToolConfig.class);
    }
}
