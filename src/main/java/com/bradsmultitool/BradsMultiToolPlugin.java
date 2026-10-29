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

    private static final int AGGRO_DURATION_SECONDS = 600;

    private static final int CONTAINER_EQUIPMENT = 94;
    private static final int INSIDE_WILDERNESS_VARBIT = 5963;
    // Repeat counter value meaning "already handled, don't sound or repeat"
    private static final int SILENCED = Integer.MAX_VALUE / 2;

    private static final Set<Skill> BOOSTABLE_COMBAT_SKILLS =
        EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);
    // Divine potion time left in game ticks (VarbitID.DIVINEATTACK/STRENGTH/DEFENCE/RANGE/MAGIC_POTION_TIME)
    private static final int[] DIVINE_POTION_TIME_VARBITS = {8429, 8430, 8431, 8432, 8433};
    private static final Skill[] DIVINE_POTION_SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC};

    public enum BuffState {
        UNPOTTED,
        ACTIVE,
        EXPIRED
    }

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
    private ExtraAlerts extraAlerts;

    @Inject
    private PluginFamily pluginFamily;

    @Inject
    private QuestProgress questProgress;

    @Inject
    @Getter
    private SlayerTracker slayerTracker;

    @Inject
    private BradsMultiToolCrabOutlineOverlay crabOutlineOverlay;

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
    private KeyManager keyManager;

    /** Toggled by the slayer show/hide hotkey; kept in the settings so it stays that way across restarts. */
    @Getter
    private volatile boolean slayerCardHidden = false;
    private static final String SLAYER_CARD_HIDDEN = "slayerCardHidden";

    private final HotkeyListener slayerToggleListener = new HotkeyListener(() -> this.config.slayerToggleKey()) {
        @Override
        public void hotkeyPressed() {
            slayerCardHidden = !slayerCardHidden;
            configManager.setConfiguration(CONFIG_GROUP, SLAYER_CARD_HIDDEN, slayerCardHidden);
            // Say what happened, since without a task there's no card to see change
            String state = slayerCardHidden ? "hidden" : slayerTracker.hadTask() ? "shown" : "shown (no Slayer task right now)";
            clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=00ffff>[Snuffy's Multi-Tool]</col> Slayer card " + state, null));
        }
    };

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

    // General PvM Target & Telemetry
    @Getter
    private NPC currentPvmTarget = null;

    @Getter
    private String lastPvmTargetName = "";

    @Getter
    private int lastPvmCombatLevel = 0;

    @Getter
    private int pvmHpPercent = 0;

    @Getter
    private int pvmCurrentHp = 0;

    @Getter
    private int pvmMaxHp = 0;

    @Getter
    private int pvmLastHit = 0;

    @Getter
    private int pvmMaxHit = 0;

    @Getter
    private double pvmLiveDps = 0.0;

    @Getter
    private boolean pvmTargetIsAggressive = false;
    private String pvmTargetAggroText = "";

    @Getter
    private String pvmAggroStatusText = "Passive";

    @Getter
    private MonsterWeakness pvmTargetWeakness = MonsterWeakness.UNKNOWN;

    @Getter
    private boolean pvmOverlayActive = false;

    private Instant lastPvmCombatActivity = Instant.EPOCH;

    private Instant aggroTimerStart = null;
    private WorldPoint lastAggroAnchor = null;

    private Instant pvmFightStart = null;
    private int pvmEncounterDamage = 0;
    private boolean pvmFightEnded = false;

    private String currentTargetType = "";

    private long sessionPvmDamage = 0;
    private long sessionPvmActiveSeconds = 0;
    private Instant sessionCombatSecondMarker = null;

    @Inject
    private MonsterData monsterData;

    // Buff state
    @Getter
    private BuffState buffState = BuffState.UNPOTTED;

    private String buffStatusText = "No Buffs";
    /** Buffs active as of the last game tick, each with its own end time (see ActiveBuffs). */
    @Getter
    private List<ActiveBuffs.Buff> activeBuffs = Collections.emptyList();
    private final Map<Skill, Long> divineEndMillis = new EnumMap<>(Skill.class);
    // Buff name -> boost when its running-out warning played; a bigger boost (re-pot) re-arms it
    private final Map<String, Integer> buffWarnedAtBoost = new HashMap<>();
    private ActiveBuffs.Category lastEndedCategory = null;
    private String lastEndedBuffName = null;
    private long lastTickMillis = 0;

    private int previousMaxBoost = 0;
    private final BuffTimer buffTimer = new BuffTimer();
    private final Map<Skill, Integer> lastBoostedLevels = new EnumMap<>(Skill.class);
    private int timesBuffExpiredSounded = 0;
    private Instant lastBuffExpiredSoundTime = Instant.EPOCH;

    @Getter
    private boolean lowPrayerAlert = false;
    @Getter
    private boolean zeroPrayerAlert = false;

    @Getter
    private boolean lowHealthAlert = false;
    private int timesHealthAlertSounded = 0;
    private Instant lastHealthAlertTime = Instant.EPOCH;
    private int timesPrayerAlertSounded = 0;
    private Instant lastPrayerAlertTime = Instant.EPOCH;


    // Tick-level XP change detection accumulator


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
        eventBus.register(extraAlerts);
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
        slayerCardHidden = Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, SLAYER_CARD_HIDDEN));
        keyManager.registerKeyListener(slayerToggleListener);
        overlayManager.add(overlay);
        overlayManager.add(prayerOverlay);
        overlayManager.add(idleWarningOverlay);
        overlayManager.add(hoverOverlay);
        overlayManager.add(orbOverlay);
        overlayManager.add(slayerOverlay);
        overlayManager.add(skillingOverlay);
        overlayManager.add(resourceTimerOverlay);
        overlayManager.add(crabOutlineOverlay);
        overlayManager.add(trainingOverlay);
        overlayManager.add(goalItemOverlay);
        overlayManager.add(slayerHighlightOverlay);
        overlayManager.add(pinnedSkillOverlay);

        eventBus.register(skillTracker);
        skillTracker.startUp();
        // The tracker's Reset all also clears the crab's and PvM's session numbers here
        skillTracker.addResetListener(this::resetSession);
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
        eventBus.unregister(extraAlerts);
        eventBus.unregister(resourceTimers);
        eventBus.unregister(farmingData);
        eventBus.unregister(birdhouseTracker);
        eventBus.unregister(otherTimerPlugins);
        keyManager.unregisterKeyListener(slayerToggleListener);
        eventBus.unregister(skillTracker);
        skillTracker.shutDown();
        eventBus.unregister(alerts);
        eventBus.unregister(idleWarnings);
        alerts.shutDown();
        eventBus.unregister(gemCrab);
        eventBus.unregister(combatStyle);
        gemCrab.reset();
        currentPvmTarget = null;
        pvmOverlayActive = false;
        buffState = BuffState.UNPOTTED;
    }

    /** Alerts are held back until you're really in the game (see Alerts.ready). */
    boolean alertsReady() {
        return alerts.ready();
    }

    private boolean isWidgetVisible(int group) {
        Widget widget = client.getWidget(group, 0);
        return widget != null && !widget.isHidden();
    }










    // The tracker's Reset all: the crab's and PvM's session numbers
    private void resetSession() {
        gemCrab.resetSession();
        sessionPvmDamage = 0;
        sessionPvmActiveSeconds = 0;
        sessionCombatSecondMarker = null;
        buffState = BuffState.UNPOTTED;
        pvmLiveDps = 0.0;
        pvmOverlayActive = false;
    }






    // ---- Goals: a target XP per skill, kept with your RuneScape profile ----




















    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // The boost decay timer keeps running across area loads; only a new session resets it
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            buffTimer.reset();
            lastBoostedLevels.clear();
            divineEndMillis.clear();
        }
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            slayerTracker.reset();
        }
        if (event.getGameState() != GameState.LOGGED_IN) {
            currentPvmTarget = null;
            buffState = BuffState.UNPOTTED;
            pvmOverlayActive = false;
            aggroTimerStart = null;
            lastAggroAnchor = null;
        }
    }


    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (npc == currentPvmTarget) {
            currentPvmTarget = null;
            pvmHpPercent = 0;
            pvmCurrentHp = 0;
            pvmFightEnded = true;
            lastPvmCombatActivity = Instant.now();
        }
    }




    /** Wiki stats for an NPC, or null if it isn't in the bundled data. */
    public MonsterData.Stats statsFor(NPC npc) {
        return monsterData.statsFor(npc);
    }


    public MonsterWeakness weaknessFor(NPC npc) {
        MonsterData.Stats stats = statsFor(npc);
        return stats != null ? MonsterData.weaknessOf(stats) : MonsterWeakness.fromName(npc.getName());
    }

    /** True for an NPC offering an "Attack" option. */
    public boolean isAttackable(NPC npc) {
        return hasAttackOption(npc);
    }








    private boolean hasAttackOption(NPC npc) {
        if (npc == null) return false;
        NPCComposition comp = npc.getTransformedComposition();
        if (comp == null) comp = npc.getComposition();
        if (comp != null && comp.getActions() != null) {
            for (String action : comp.getActions()) {
                if (action != null && action.equalsIgnoreCase("Attack")) {
                    return true;
                }
            }
        }
        return false;
    }









    @Subscribe
    public void onGameTick(GameTick tick) {
        lastTickMillis = System.currentTimeMillis();
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }


        Player player = client.getLocalPlayer();
        if (player != null) {
            WorldPoint wp = player.getWorldLocation();
            slayerTracker.update();
            updateGeneralPvmTarget(player);
            updateAggroCountdown(wp);
        }

        processSimpleBuffStatus();
        monitorPrayer();
        monitorHealth();
    }


    private void updateAggroCountdown(WorldPoint playerLocation) {
        if (playerLocation == null) return;

        if (aggroTimerStart == null && currentPvmTarget != null && pvmTargetIsAggressive) {
            aggroTimerStart = Instant.now();
            lastAggroAnchor = playerLocation;
        }

        if (lastAggroAnchor != null && playerLocation.distanceTo(lastAggroAnchor) > 32) {
            aggroTimerStart = Instant.now();
            lastAggroAnchor = playerLocation;
        }

        if (!pvmTargetIsAggressive) {
            // Why it won't attack you, e.g. "No (lvl 57+)" or "No (Bandos item)"
            pvmAggroStatusText = pvmTargetAggroText.startsWith("No (") ? "Passive" + pvmTargetAggroText.substring(2) : "Passive";
            return;
        }

        if (aggroTimerStart != null) {
            long elapsed = Duration.between(aggroTimerStart, Instant.now()).getSeconds();
            long remaining = AGGRO_DURATION_SECONDS - elapsed;
            if (remaining <= 0) {
                pvmAggroStatusText = "Un-aggroed";
            } else {
                long m = remaining / 60;
                long s = remaining % 60;
                pvmAggroStatusText = String.format("%02d:%02d", m, s);
            }
        } else {
            pvmAggroStatusText = "10:00";
        }
    }

    private void processSimpleBuffStatus() {
        if (!config.enableBuffStatus()) {
            buffStatusText = "Disabled";
            buffState = BuffState.UNPOTTED;
            return;
        }

        Map<Skill, Integer> boosts = new EnumMap<>(Skill.class);
        for (Skill skill : BOOSTABLE_COMBAT_SKILLS) {
            boosts.put(skill, Math.max(0, client.getBoostedSkillLevel(skill) - client.getRealSkillLevel(skill)));
        }
        int currentMax = Collections.max(boosts.values());

        if (currentMax >= 4 || (currentMax > 0 && buffState == BuffState.ACTIVE)) {
            buffState = BuffState.ACTIVE;
            syncDivineTimers();
            boolean preserve = client.getVarbitValue(Prayer.PRESERVE.getVarbit()) == 1;
            List<ActiveBuffs.Buff> buffs = ActiveBuffs.detect(boosts, divineEndMillis, buffTimer, client.getTickCount(), lastTickMillis, preserve);
            announceEndedBuffs(buffs);
            activeBuffs = buffs;
            buffStatusText = "ACTIVE";
            timesBuffExpiredSounded = 0;
            checkBuffRunningOut();
        } else if (currentMax == 0) {
            announceEndedBuffs(Collections.emptyList());
            activeBuffs = Collections.emptyList();
            buffWarnedAtBoost.clear();
            if (buffState == BuffState.ACTIVE) {
                buffState = BuffState.EXPIRED;
                buffStatusText = "EXPIRED";
                timesBuffExpiredSounded = 1;
                lastBuffExpiredSoundTime = Instant.now();
                alerts.alert(lastExpiredLine(), null);
            } else if (buffState == BuffState.EXPIRED && config.repeatBuffAlert()
                && Duration.between(lastBuffExpiredSoundTime, Instant.now()).getSeconds() >= config.repeatBuffInterval()
                && timesBuffExpiredSounded <= config.maxBuffRepeats()) {
                timesBuffExpiredSounded++;
                lastBuffExpiredSoundTime = Instant.now();
                alerts.alert(lastExpiredLine(), "still out");
            } else if (buffState == BuffState.UNPOTTED) {
                buffStatusText = "No Buffs";
            }
        }
        previousMaxBoost = currentMax;
    }

    /**
     * Time left on the boost: exact for divine potions (the game counts it down), exact for regular
     * potions once a stat drop has been seen, and "~" plus an upper bound before that.
     */
    // Catch divine potions already running when the plugin starts; changes arrive via onVarbitChanged
    private void syncDivineTimers() {
        Skill[] skills = DIVINE_POTION_SKILLS;
        for (int i = 0; i < skills.length; i++) {
            int ticks = client.getVarbitValue(DIVINE_POTION_TIME_VARBITS[i]);
            if (ticks <= 0) {
                divineEndMillis.remove(skills[i]);
            } else {
                divineEndMillis.putIfAbsent(skills[i], System.currentTimeMillis() + ticks * 600L);
            }
        }
    }

    // Like RuneLite's Timers plugin: start the countdown when the game sets the divine timer
    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        for (int i = 0; i < DIVINE_POTION_TIME_VARBITS.length; i++) {
            if (event.getVarbitId() == DIVINE_POTION_TIME_VARBITS[i]) {
                if (event.getValue() > 0) {
                    divineEndMillis.put(DIVINE_POTION_SKILLS[i], System.currentTimeMillis() + event.getValue() * 600L);
                } else {
                    divineEndMillis.remove(DIVINE_POTION_SKILLS[i]);
                }
            }
        }
    }

    /**
     * True while the buff is inside the "running out" window (the same test the alert uses), so its HUD
     * line is red only then and green again as soon as it's above the threshold.
     */
    boolean isBuffRunningOut(ActiveBuffs.Buff buff) {
        int warnSeconds = config.buffWarningSeconds();
        if (warnSeconds <= 0) {
            return false;
        }
        if (!buff.exact) {
            return buff.boost == 1;
        }
        return buff.millisLeft(System.currentTimeMillis()) <= warnSeconds * 1000L;
    }

    // Warn once each time a buff runs low
    private void checkBuffRunningOut() {
        long now = System.currentTimeMillis();
        for (ActiveBuffs.Buff buff : activeBuffs) {
            // Warn once each time a buff enters the "running out" window; leaving it (a new dose, a divine
            // refresh at the same boost) arms it again
            if (!isBuffRunningOut(buff)) {
                buffWarnedAtBoost.remove(buff.name);
                continue;
            }
            if (buffWarnedAtBoost.containsKey(buff.name)) {
                continue;
            }
            buffWarnedAtBoost.put(buff.name, buff.boost);
            // Before the decay timer is synced (no stat drop seen since login or a hop) the time left isn't
            // exact, but a buff down to +1 has at most one decay period left
            String left = (buff.exact ? "" : "under ") + BuffTimer.formatMillis(buff.millisLeft(now)) + " left";
            alerts.alert(potionLine(buff.name, true, runningOutLine(buff.category)), left);
        }
    }


    // A buff that ends while others are still active gets its own "has run out"; when the last one ends,
    // the expired alert above speaks for it (using lastEndedCategory)
    private void announceEndedBuffs(List<ActiveBuffs.Buff> current) {
        for (ActiveBuffs.Buff before : activeBuffs) {
            boolean stillActive = current.stream().anyMatch(b -> b.name.equals(before.name));
            if (stillActive) {
                continue;
            }
            lastEndedCategory = before.category;
            lastEndedBuffName = before.name;
            if (!current.isEmpty()) {
                alerts.alert(potionLine(before.name, false, expiredLine(before.category)), null);
            }
        }
    }

    // What to say for the buff that ran out last
    private VoiceAlertService.VoiceLine lastExpiredLine() {
        return lastEndedBuffName != null
            ? potionLine(lastEndedBuffName, false, expiredLine(lastEndedCategory))
            : expiredLine(lastEndedCategory);
    }

    // The potion's own line ("Divine super combat is running out"), else the boost-type line
    private static VoiceAlertService.VoiceLine potionLine(String buffName, boolean runningOut,
                                                          VoiceAlertService.VoiceLine fallback) {
        VoiceAlertService.VoiceLine line = VoiceAlertService.VoiceLine.forPotion(buffName, runningOut);
        return line != null ? line : fallback;
    }

    private static VoiceAlertService.VoiceLine runningOutLine(ActiveBuffs.Category category) {
        switch (category) {
            case MELEE: return VoiceAlertService.VoiceLine.MELEE_BUFF_LOW;
            case RANGED: return VoiceAlertService.VoiceLine.RANGED_BUFF_LOW;
            case MAGIC: return VoiceAlertService.VoiceLine.MAGIC_BUFF_LOW;
            case DEFENCE: return VoiceAlertService.VoiceLine.DEFENCE_BUFF_LOW;
            default: return VoiceAlertService.VoiceLine.BUFF_LOW;
        }
    }

    private static VoiceAlertService.VoiceLine expiredLine(ActiveBuffs.Category category) {
        if (category == null) {
            return VoiceAlertService.VoiceLine.BUFF_EXPIRED;
        }
        switch (category) {
            case MELEE: return VoiceAlertService.VoiceLine.MELEE_BUFF_EXPIRED;
            case RANGED: return VoiceAlertService.VoiceLine.RANGED_BUFF_EXPIRED;
            case MAGIC: return VoiceAlertService.VoiceLine.MAGIC_BUFF_EXPIRED;
            case DEFENCE: return VoiceAlertService.VoiceLine.DEFENCE_BUFF_EXPIRED;
            default: return VoiceAlertService.VoiceLine.BUFF_EXPIRED;
        }
    }

    /** Text for the Buff Status line when no buff rows are shown ("No Buffs", "EXPIRED", ...). */
    public String getBuffStatusText() {
        return buffStatusText;
    }

    /** Points equal to {@code percent}% of the skill's real level (at least 1). */
    private int threshold(Skill skill, int percent) {
        return Math.max(1, (int) Math.ceil(client.getRealSkillLevel(skill) * percent / 100.0));
    }

    private void monitorHealth() {
        int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
        if (!config.enableLowHealthAlert() || hp > threshold(Skill.HITPOINTS, config.lowHealthPercent()) || hp <= 0) {
            lowHealthAlert = false;
            timesHealthAlertSounded = 0;
            return;
        }
        lowHealthAlert = true;

        boolean canSound = false;
        if (timesHealthAlertSounded == 0) {
            canSound = true;
        } else if (config.repeatHealthAlert()
            && Duration.between(lastHealthAlertTime, Instant.now()).getSeconds() >= config.repeatHealthInterval()
            && timesHealthAlertSounded <= config.maxHealthRepeats()) {
            canSound = true;
        }

        if (canSound && !alertsReady()) {
            timesHealthAlertSounded = SILENCED;
        } else if (canSound) {
            timesHealthAlertSounded++;
            lastHealthAlertTime = Instant.now();
            alerts.alert(VoiceAlertService.VoiceLine.HEALTH_LOW, hp + " hitpoints");
        }
    }

    private void monitorPrayer() {
        int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
        boolean isZero = (currentPrayer <= 0);

        if (isZero && !zeroPrayerAlert) {
            zeroPrayerAlert = true;
            lowPrayerAlert = false;
            alerts.alert(VoiceAlertService.VoiceLine.OUT_OF_PRAYER, null);
            return;
        } else if (!isZero) {
            zeroPrayerAlert = false;
        }

        if (!isZero && config.enableLowPrayerAlert() && currentPrayer <= threshold(Skill.PRAYER, config.lowPrayerPercent())) {
            lowPrayerAlert = true;

            boolean canSound = false;
            if (timesPrayerAlertSounded == 0 && !alertsReady()) {
                timesPrayerAlertSounded = SILENCED;
            } else if (timesPrayerAlertSounded == 0) {
                timesPrayerAlertSounded = 1;
                lastPrayerAlertTime = Instant.now();
                canSound = true;
            } else if (config.repeatPrayerAlert()) {
                long delay = Duration.between(lastPrayerAlertTime, Instant.now()).getSeconds();
                if (delay >= config.repeatPrayerInterval() && timesPrayerAlertSounded <= config.maxPrayerRepeats()) {
                    timesPrayerAlertSounded++;
                    lastPrayerAlertTime = Instant.now();
                    canSound = true;
                }
            }

            if (canSound) {
                alerts.alert(VoiceAlertService.VoiceLine.PRAYER_LOW, currentPrayer + " points");
            }
        } else if (currentPrayer > threshold(Skill.PRAYER, config.lowPrayerPercent())) {
            lowPrayerAlert = false;
            timesPrayerAlertSounded = 0;
        }
    }

    /**
     * AFK alert: you were training (gaining XP while animating or fighting), then your character stopped
     * and you haven't clicked or typed since. Only XP-giving actions count, so banking, talking to people
     * or walking around never arm it, and opening a bank, deposit box, shop, Grand Exchange or seed vault
     * disarms it until you're training again.
     */
    private void updateGeneralPvmTarget(Player player) {
        Actor target = player.getInteracting();

        NPC attackingNpc = null;
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc != null && npc.getInteracting() == player && !npc.isDead()) {
                if (!GemstoneCrabs.isGemstoneCrab(npc) && hasAttackOption(npc)) {
                    attackingNpc = npc;
                    break;
                }
            }
        }

        NPC activeFightNpc = null;
        if (target instanceof NPC && hasAttackOption((NPC) target)) {
            activeFightNpc = (NPC) target;
        } else if (attackingNpc != null) {
            activeFightNpc = attackingNpc;
        }

        if (activeFightNpc != null) {
            String name = activeFightNpc.getName() != null ? activeFightNpc.getName() : "Monster";
            if (GemstoneCrabs.isGemstoneCrab(activeFightNpc)) {
                currentPvmTarget = null;
                pvmOverlayActive = false;
                return;
            }

            boolean isNewMonster = (currentPvmTarget != activeFightNpc);
            boolean isNewType = !name.equalsIgnoreCase(currentTargetType);

            if (isNewMonster) {
                currentPvmTarget = activeFightNpc;
                lastPvmTargetName = name;
                lastPvmCombatLevel = activeFightNpc.getCombatLevel();
                pvmFightEnded = false;
                resolveNpcWeaknessAndAggression(activeFightNpc);

                MonsterData.Stats stats = statsFor(activeFightNpc);
                int baseHp = stats != null ? stats.hitpoints : 0;
                if (baseHp <= 0) {
                    baseHp = Math.max(10, activeFightNpc.getCombatLevel() * 2);
                }
                pvmMaxHp = baseHp;

                if (config.dpsMode() == BradsMultiToolConfig.DpsTrackingMode.PER_ENCOUNTER) {
                    pvmFightStart = Instant.now();
                    pvmEncounterDamage = 0;
                } else if (config.dpsMode() == BradsMultiToolConfig.DpsTrackingMode.PER_TARGET_TYPE && isNewType) {
                    currentTargetType = name;
                    pvmFightStart = Instant.now();
                    pvmEncounterDamage = 0;
                }
            }

            lastPvmCombatActivity = Instant.now();
            pvmOverlayActive = true;

            int ratio = activeFightNpc.getHealthRatio();
            int scale = activeFightNpc.getHealthScale();
            if (ratio > 0 && scale > 0) {
                pvmHpPercent = (ratio * 100) / scale;
                pvmCurrentHp = (int) Math.ceil((double) ratio * pvmMaxHp / scale);
            } else if (activeFightNpc.isDead()) {
                pvmHpPercent = 0;
                pvmCurrentHp = 0;
                pvmFightEnded = true;
            } else {
                pvmHpPercent = 100;
                pvmCurrentHp = pvmMaxHp;
            }

            if (sessionCombatSecondMarker == null) {
                sessionCombatSecondMarker = Instant.now();
            } else {
                long delta = Duration.between(sessionCombatSecondMarker, Instant.now()).getSeconds();
                if (delta >= 1) {
                    sessionPvmActiveSeconds += delta;
                    sessionCombatSecondMarker = Instant.now();
                }
            }

            if (!pvmFightEnded) {
                if (config.dpsMode() == BradsMultiToolConfig.DpsTrackingMode.CONTINUOUS_SESSION) {
                    long totalSec = Math.max(1, sessionPvmActiveSeconds);
                    pvmLiveDps = (double) sessionPvmDamage / totalSec;
                } else {
                    long elapsed = (pvmFightStart != null) ? Math.max(1, Duration.between(pvmFightStart, Instant.now()).getSeconds()) : 1;
                    pvmLiveDps = (double) pvmEncounterDamage / elapsed;
                }
            }
        } else {
            currentPvmTarget = null;
            sessionCombatSecondMarker = null;

            long lingerDuration = config.pvmLingerSeconds();
            long elapsedSinceFight = Duration.between(lastPvmCombatActivity, Instant.now()).getSeconds();

            if (elapsedSinceFight < lingerDuration && !lastPvmTargetName.isEmpty()) {
                pvmOverlayActive = true;
            } else {
                pvmOverlayActive = false;
                pvmFightStart = null;
                pvmEncounterDamage = 0;
            }
        }
    }

    private void resolveNpcWeaknessAndAggression(NPC npc) {
        if (npc == null || npc.getName() == null) {
            pvmTargetWeakness = MonsterWeakness.UNKNOWN;
            pvmTargetIsAggressive = false;
            return;
        }

        MonsterData.Stats stats = statsFor(npc);
        Aggression.Verdict aggro = aggressionFor(npc);
        pvmTargetIsAggressive = aggro.aggressive();
        pvmTargetAggroText = aggro.text;
        pvmTargetWeakness = stats != null ? MonsterData.weaknessOf(stats) : MonsterWeakness.fromName(npc.getName());
    }

    /** Green when your combat style hits the current target's weakness, red when it doesn't, white if unsure. */
    public Color getCombatMethodColor() {
        WeaponStyles.Style style = combatStyle.getCurrentStyle();
        return style != null ? CombatStyle.weaknessMatchColor(style, pvmTargetWeakness) : Color.WHITE;
    }

    /** Whether this monster will attack you, given your combat level, the Wilderness and your gear. */
    Aggression.Verdict aggressionFor(NPC npc) {
        Player local = client.getLocalPlayer();
        List<String> equipped = new ArrayList<>();
        ItemContainer equipment = client.getItemContainer(CONTAINER_EQUIPMENT);
        if (equipment != null) {
            for (Item item : equipment.getItems()) {
                if (item.getId() > 0) {
                    equipped.add(client.getItemDefinition(item.getId()).getName().toLowerCase());
                }
            }
        }
        return Aggression.of(statsFor(npc), npc.getCombatLevel(),
            local != null ? local.getCombatLevel() : 0, client.getVarbitValue(INSIDE_WILDERNESS_VARBIT) == 1, equipped);
    }









    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC npc = (NPC) event.getActor();
        Hitsplat hitsplat = event.getHitsplat();

        if (hitsplat.isMine()) {
            int dmg = hitsplat.getAmount();

            if (npc == currentPvmTarget) {
                // Persistent Last Hit and Max Hit for PvM
                pvmLastHit = dmg;
                if (dmg > pvmMaxHit) {
                    pvmMaxHit = dmg;
                }
                pvmEncounterDamage += dmg;
                sessionPvmDamage += dmg;
                lastPvmCombatActivity = Instant.now();
            }
        }
    }


    @Subscribe
    public void onStatChanged(StatChanged event) {
        Skill skill = event.getSkill();
        int currentXp = event.getXp();

        // A boosted combat stat ticking down by 1 marks the boost decay timer's phase
        if (BOOSTABLE_COMBAT_SKILLS.contains(skill)) {
            Integer previous = lastBoostedLevels.put(skill, event.getBoostedLevel());
            if (previous != null && event.getBoostedLevel() == previous - 1 && previous > event.getLevel()) {
                buffTimer.onDecay(client.getTickCount());
            }
        }
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
