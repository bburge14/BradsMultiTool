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

    // Raw ids rather than the deprecated VarPlayer/Varbits/InventoryID constants
    private static final int VARP_ATTACK_STYLE = 43;
    private static final int VARBIT_EQUIPPED_WEAPON_TYPE = 357;
    private static final int VARBIT_DEFENSIVE_CASTING = 2668;
    private static final int VARBIT_AUTOCAST_SPELL = 276;
    private static final int CONTAINER_EQUIPMENT = 94;
    // A manual cast stays the "active" style this long; casting repeats every 5 ticks
    private static final int MANUAL_CAST_TIMEOUT_TICKS = 10;
    private static final int EQUIPMENT_WEAPON_SLOT = 3;
    // The game's own combat style data: enum of weapon category -> enum of style structs, and the
    // struct param naming each style's XP type ("Accurate", "Rapid", "Casting"...)
    private static final int ENUM_WEAPON_STYLES = 3908;
    private static final int PARAM_ATTACK_STYLE_NAME = 1407;

    private static final int INSIDE_WILDERNESS_VARBIT = 5963;
    // Repeat counter value meaning "already handled, don't sound or repeat"
    private static final int SILENCED = Integer.MAX_VALUE / 2;
    private static final int TRAINING_WINDOW_TICKS = 25;
    private static final int PIE_TRAINING_TICKS = 200;

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

    private volatile int combatLevel = 3;

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
    private NavigationButton navButton;
    private BradsMultiToolPanel panel;

    @Getter
    private String combatDiscipline = "Melee";

    @Getter
    private String combatMethod = "Slash";

    @Getter
    private Color combatMethodColor = Color.WHITE;

    @Getter
    private CombatIcons.Icon combatMethodIcon = null;

    /** The player's current combat style (weapon style, autocast or hand-cast spell). */
    @Getter
    private WeaponStyles.Style currentStyle = null;
    private int xpStyleKey = -1;
    private String xpStyle = "";

    private final SpellbookReader spellbook = new SpellbookReader();
    private String manualSpellName = null;
    private int manualSpellSpriteId = -1;
    private int manualCastTick = -1;

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

    private final MonsterData monsterData = new MonsterData();

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

    private final Map<Skill, Integer> baseSkillXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> gainedSkillXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> realLevels = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> lastXpTicks = new EnumMap<>(Skill.class);
    private final Map<Skill, Long> skillActiveTicks = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> lastXpDrops = new EnumMap<>(Skill.class);
    private final Set<Skill> pausedSkills = EnumSet.noneOf(Skill.class);

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

        panel = new BradsMultiToolPanel(this, config, skillIconManager);

        navButton = NavigationButton.builder()
            .tooltip("Snuffy's Skill Tracker")
            .icon(createCrabToolIcon())
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
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
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
        }
        eventBus.unregister(alerts);
        eventBus.unregister(idleWarnings);
        alerts.shutDown();
        eventBus.unregister(gemCrab);
        gemCrab.reset();
        currentPvmTarget = null;
        pausedSkills.clear();
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



    // Goals are kept with your RuneScape profile, which RuneLite only knows a moment after login (and
    // which changes when synced settings arrive), so redraw the tracker then
    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        updatePanelUI();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

        String key = event.getKey();
        if (key.startsWith("showTracker") || key.startsWith("tracker") || key.startsWith("goal") || "showXpPerMinute".equals(key) || key.startsWith("autoPause")) {
            updatePanelUI();
        }
    }

    private BufferedImage createCrabToolIcon() {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(24, 32, 45));
        g.fillRoundRect(0, 0, 16, 16, 4, 4);

        g.setColor(new Color(0, 230, 230));
        g.fillOval(3, 4, 10, 8);

        g.setColor(new Color(255, 170, 0));
        g.fillOval(1, 2, 4, 4);
        g.fillOval(11, 2, 4, 4);

        g.setColor(new Color(255, 255, 255, 210));
        g.fillOval(6, 6, 4, 4);

        g.dispose();
        return icon;
    }



    public boolean isSkillIgnored(Skill skill) {
        String ignored = config.ignoredSkills();
        if (ignored == null || ignored.trim().isEmpty()) {
            return false;
        }
        String skillName = skill.getName().toLowerCase();
        for (String part : ignored.split(",")) {
            if (part.trim().toLowerCase().equals(skillName)) {
                return true;
            }
        }
        return false;
    }

    public void addIgnoredSkill(Skill skill) {
        String raw = config.ignoredSkills();
        Set<String> set = new HashSet<>();
        if (raw != null && !raw.trim().isEmpty()) {
            set = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        }
        set.add(skill.getName());
        configManager.setConfiguration(CONFIG_GROUP, "ignoredSkills", String.join(", ", set));
        updatePanelUI();
    }

    public void resetSession() {
        gemCrab.resetSession();
        sessionPvmDamage = 0;
        sessionPvmActiveSeconds = 0;
        sessionCombatSecondMarker = null;
        baseSkillXp.clear();
        gainedSkillXp.clear();
        skillActiveTicks.clear();
        lastXpDrops.clear();
        pausedSkills.clear();
        buffState = BuffState.UNPOTTED;
        pvmLiveDps = 0.0;
        pvmOverlayActive = false;
        updatePanelUI();
    }


    public void resetSkillRate(Skill skill) {
        int currentXp = client.getSkillExperience(skill);
        baseSkillXp.put(skill, currentXp);
        gainedSkillXp.put(skill, 0);
        skillActiveTicks.put(skill, 0L);
        updatePanelUI();
    }

    public void toggleSkillPause(Skill skill) {
        if (pausedSkills.contains(skill)) {
            pausedSkills.remove(skill);
        } else {
            pausedSkills.add(skill);
        }
        updatePanelUI();
    }

    /** Paused by you, or auto-paused after going without XP in the skill for a while. */
    public boolean isSkillPaused(Skill skill) {
        return pausedSkills.contains(skill) || isSkillAutoPaused(skill);
    }

    /** No XP in the skill for the auto-pause time: the rate stops counting until you gain XP again. */
    public boolean isSkillAutoPaused(Skill skill) {
        if (!config.autoPauseSkills() || pausedSkills.contains(skill)) {
            return false;
        }
        Integer last = lastXpTicks.get(skill);
        return last != null && client.getTickCount() - last > config.autoPauseSeconds() / 0.6;
    }

    // ---- Goals: a target XP per skill, kept with your RuneScape profile ----

    /** The skill's goal in XP, or 0 for none. */
    public int getSkillGoal(Skill skill) {
        String value = configManager.getRSProfileConfiguration(CONFIG_GROUP, "goal." + skill.name());
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setSkillGoal(Skill skill, int goalXp) {
        if (goalXp <= 0) {
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, "goal." + skill.name());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, "goalStart." + skill.name());
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, "goalTarget." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(CONFIG_GROUP, "goal." + skill.name(), goalXp);
            configManager.setRSProfileConfiguration(CONFIG_GROUP, "goalStart." + skill.name(), client.getSkillExperience(skill));
        }
        updatePanelUI();
    }

    /** What to train for a goal, by the Training Focus setting. */
    public TrainingAdvisor getTrainingAdvisor() {
        return trainingAdvisor;
    }

    /** The wiki's training methods by level, for the tracker's goal suggestions. */
    public TrainingData getTrainingData() {
        return trainingData;
    }

    /** Your combat level as of the last game tick (safe to read from the panel). */
    public int getCombatLevel() {
        return combatLevel;
    }

    public int getCurrentSkillLevel(Skill skill) {
        return Experience.getLevelForXp(client.getSkillExperience(skill));
    }

    public SlayerData getSlayerData() {
        return slayerData;
    }

    /** What to make for production-skill goals, from your inventory and bank. */
    public GoalItems getGoalItems() {
        return goalItems;
    }

    /**
     * What you chose to train for this skill's goal instead of the suggestion ("npc:ruby harvest",
     * "object:Teak tree"), or null to follow the suggestion.
     */
    public String getGoalTarget(Skill skill) {
        return configManager.getRSProfileKey() == null ? null
            : configManager.getRSProfileConfiguration(CONFIG_GROUP, "goalTarget." + skill.name());
    }

    public void setGoalTarget(Skill skill, String target) {
        if (target == null) {
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, "goalTarget." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(CONFIG_GROUP, "goalTarget." + skill.name(), target);
        }
        updatePanelUI();
    }

    /** What this skill's goal suggestions aim for: its own choice, else the Default Training Focus setting. */
    public BradsMultiToolConfig.TrainingFocus getTrainingFocus(Skill skill) {
        BradsMultiToolConfig.TrainingFocus own = getOwnTrainingFocus(skill);
        return own != null ? own : config.trainingFocus();
    }

    /** The focus picked for this skill, or null when it follows the default. */
    public BradsMultiToolConfig.TrainingFocus getOwnTrainingFocus(Skill skill) {
        String value = configManager.getRSProfileKey() == null ? null
            : configManager.getRSProfileConfiguration(CONFIG_GROUP, "trainingFocus." + skill.name());
        if (value == null) {
            return null;
        }
        try {
            return BradsMultiToolConfig.TrainingFocus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTrainingFocus(Skill skill, BradsMultiToolConfig.TrainingFocus focus) {
        if (focus == null) {
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, "trainingFocus." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(CONFIG_GROUP, "trainingFocus." + skill.name(), focus.name());
        }
        updatePanelUI();
    }

    /** "Ruby harvest" from "npc:ruby harvest". */
    static String goalTargetName(String target) {
        String name = target.substring(target.indexOf(':') + 1);
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** The one skill pinned to the game screen from the tracker, or null. Kept across restarts. */
    public Skill getPinnedSkill() {
        String name = configManager.getConfiguration(CONFIG_GROUP, "pinnedSkill");
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Skill.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Pin a skill to the screen (replacing any other), or unpin with null. */
    public void setPinnedSkill(Skill skill) {
        if (skill == null) {
            configManager.unsetConfiguration(CONFIG_GROUP, "pinnedSkill");
        } else {
            configManager.setConfiguration(CONFIG_GROUP, "pinnedSkill", skill.name());
        }
        updatePanelUI();
    }

    /** Skills you've set a goal for, in skill order. */
    public List<Skill> getGoalSkills() {
        List<Skill> skills = new ArrayList<>();
        if (configManager.getRSProfileKey() == null) {
            return skills;
        }
        for (Skill skill : Skill.values()) {
            if (!"OVERALL".equals(skill.name()) && getSkillGoal(skill) > 0) {
                skills.add(skill);
            }
        }
        return skills;
    }

    /** Your XP in the skill when you set its goal (for the goal bar), or 0 if unknown. */
    public int getSkillGoalStart(Skill skill) {
        String value = configManager.getRSProfileConfiguration(CONFIG_GROUP, "goalStart." + skill.name());
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void resetSkill(Skill skill) {
        baseSkillXp.remove(skill);
        gainedSkillXp.remove(skill);
        skillActiveTicks.remove(skill);
        lastXpDrops.remove(skill);
        pausedSkills.remove(skill);
        updatePanelUI();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // The boost decay timer keeps running across area loads; only a new session resets it
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            buffTimer.reset();
            lastBoostedLevels.clear();
            divineEndMillis.clear();
        }
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            realLevels.clear();
            slayerTracker.reset();
        }
        if (event.getGameState() != GameState.LOGGED_IN) {
            currentPvmTarget = null;
            buffState = BuffState.UNPOTTED;
            pvmOverlayActive = false;
            aggroTimerStart = null;
            lastAggroAnchor = null;
            updatePanelUI();
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
    // By the NPC's id, its transformed id (NPCs that change form), then its name
    public MonsterData.Stats statsFor(NPC npc) {
        MonsterData.Stats stats = monsterData.get(npc.getId());
        if (stats == null && npc.getTransformedComposition() != null) {
            stats = monsterData.get(npc.getTransformedComposition().getId());
        }
        return stats != null ? stats : monsterData.get(npc.getId(), npc.getName());
    }

    /**
     * XP per damage your current combat style gives in this skill, for a kill estimate. A style that doesn't
     * train it counts as if you switched to one that does (4 per damage; 2 for Defence by Longrange, 1 by
     * defensive casting). Controlled splits 4/3 to each of Attack, Strength and Defence; Longrange 2 each to
     * Ranged and Defence; Hitpoints is always 4/3.
     */
    public double xpPerDamage(Skill skill) {
        if (skill == Skill.HITPOINTS) {
            return 4.0 / 3;
        }
        boolean melee = skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE;
        String discipline = currentStyle != null ? currentStyle.discipline : "";
        if (melee && "Melee".equals(discipline) && xpStyle.contains("controlled")) {
            return 4.0 / 3;
        }
        if ((skill == Skill.RANGED || skill == Skill.DEFENCE) && "Ranged".equals(discipline) && xpStyle.contains("longrange")) {
            return 2;
        }
        if (skill == Skill.DEFENCE && "Defensive Cast".equals(currentStyle != null ? currentStyle.buttonName : null)) {
            return 1;
        }
        return 4;
    }

    /** Wiki stats for a monster by name ("Sulphur Nagua"), or null. */
    public MonsterData.Stats statsFor(String name) {
        return name == null || name.isEmpty() ? null : monsterData.get(-1, name);
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
            combatLevel = player.getCombatLevel();
            WorldPoint wp = player.getWorldLocation();
            slayerTracker.update();
            updateGeneralPvmTarget(player);
            updateAggroCountdown(wp);
        }

        processSimpleBuffStatus();
        monitorPrayer();
        monitorHealth();
        updateRealtimeCombatMethod();

        for (Skill s : gainedSkillXp.keySet()) {
            if (gainedSkillXp.get(s) > 0 && !isSkillPaused(s) && !isSkillIgnored(s)) {
                long current = skillActiveTicks.getOrDefault(s, 0L);
                skillActiveTicks.put(s, current + 1);
            }
        }

        updatePanelUI();
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

    // Weapon category (varbit 357) -> its row in the game's weapon category table, or -1
    private final Map<Integer, Integer> weaponCategoryRows = new HashMap<>();

    private int weaponCategoryRow(int category) {
        Integer row = weaponCategoryRows.get(category);
        if (row == null) {
            row = -1;
            try {
                List<Integer> rows = client.getDBRowsByValue(DBTableID.CombatInterfaceWeaponCategory.ID,
                    DBTableID.CombatInterfaceWeaponCategory.COL_ID, 0, category);
                if (!rows.isEmpty()) {
                    row = rows.get(0);
                }
            } catch (RuntimeException e) {
                log.debug("No weapon category row for {}", category, e);
            }
            log.debug("Weapon category {} is row {}", category, row);
            weaponCategoryRows.put(category, row);
        }
        return row;
    }

    /** The game's name for a combat tab button's XP style ("Accurate", "Rapid"...), or null. */
    private String gameStyleName(int category, int styleIndex) {
        try {
            int stylesEnum = client.getEnum(ENUM_WEAPON_STYLES).getIntValue(category);
            if (stylesEnum == -1) {
                return null;
            }
            int[] structs = client.getEnum(stylesEnum).getIntVals();
            if (styleIndex < 0 || styleIndex >= structs.length) {
                return null;
            }
            return client.getStructComposition(structs[styleIndex]).getStringValue(PARAM_ATTACK_STYLE_NAME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void updateRealtimeCombatMethod() {
        int category = client.getVarbitValue(VARBIT_EQUIPPED_WEAPON_TYPE);
        int styleIndex = client.getVarpValue(VARP_ATTACK_STYLE);
        int categoryRow = weaponCategoryRow(category);
        boolean defensiveCasting = client.getVarbitValue(VARBIT_DEFENSIVE_CASTING) == 1;
        int autocastSpell = client.getVarbitValue(VARBIT_AUTOCAST_SPELL);
        // The game's name for the style's XP type ("Accurate", "Controlled", "Longrange"...), looked up once per style
        int styleKey = category * 16 + styleIndex;
        if (styleKey != xpStyleKey) {
            xpStyleKey = styleKey;
            String name = styleIndex == 4 ? "Casting" : gameStyleName(category, styleIndex);
            xpStyle = name != null ? name.toLowerCase() : "";
        }
        WeaponStyles.Style style = WeaponStyles.resolve(categoryRow, styleIndex, defensiveCasting, autocastSpell, null);
        if ("Unknown".equals(style.buttonName)) {
            // A weapon type the table doesn't know: go by the game's name for the style
            style = WeaponStyles.resolve(categoryRow, styleIndex, defensiveCasting, autocastSpell,
                gameStyleName(category, styleIndex));
        }

        int weaponId = equippedWeaponId();
        if (weaponId > 0) {
            style = WeaponStyles.withEquippedWeapon(style, client.getItemDefinition(weaponId).getName());
        }

        spellbook.refresh(client);

        // Which spell is being cast: a recent hand cast wins, then the combat tab's autocast slot
        String spellName = null;
        int fallbackSprite = -1;
        if (manualSpellName != null && client.getTickCount() - manualCastTick <= MANUAL_CAST_TIMEOUT_TICKS) {
            spellName = manualSpellName;
            fallbackSprite = manualSpellSpriteId;
            style = WeaponStyles.manualCast(spellName);
        } else if ("Spell Cast".equals(style.buttonName) || "Defensive Cast".equals(style.buttonName)) {
            boolean defensive = "Defensive Cast".equals(style.buttonName);
            spellName = spellbook.autocastSpell(client, defensive);
            if (spellName != null) {
                style = WeaponStyles.spellStyle(spellName, defensive);
            }
        }

        combatDiscipline = style.discipline;
        combatMethod = style.label();

        int spellSprite = spellName != null ? spellbook.spriteFor(spellName) : -1;
        if (spellSprite <= 0) {
            spellSprite = fallbackSprite;
        }
        combatMethodIcon = spellSprite > 0 ? CombatIcons.sprite(spellSprite) : CombatIcons.forStyle(style, weaponId);

        currentStyle = style;
        combatMethodColor = weaknessMatchColor(style, pvmTargetWeakness);
    }

    // Spells cast by hand (not autocast) override the weapon's style for a few ticks after each cast
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() != MenuAction.WIDGET_TARGET_ON_NPC || !"Cast".equals(event.getMenuOption())) {
            return;
        }

        String target = Text.removeTags(event.getMenuTarget());
        int arrow = target.indexOf("->");
        String spell = (arrow >= 0 ? target.substring(0, arrow) : target).trim();
        if (spell.isEmpty()) {
            return;
        }

        Widget selectedSpell = client.getSelectedWidget();
        manualSpellName = spell;
        manualSpellSpriteId = selectedSpell != null ? selectedSpell.getSpriteId() : -1;
        manualCastTick = client.getTickCount();
    }

    /** Spellbook icon of the Strike spell for an element, as shown in the player's own spellbook. */
    // The element's basic Strike spell icon (standard spellbook), whatever spellbook you're on
    public CombatIcons.Icon elementSpellIcon(MonsterWeakness.Element element) {
        switch (element) {
            case AIR: return CombatIcons.sprite(SpriteID.Magicon.WIND_STRIKE);
            case WATER: return CombatIcons.sprite(SpriteID.Magicon.WATER_STRIKE);
            case EARTH: return CombatIcons.sprite(SpriteID.Magicon.EARTH_STRIKE);
            case FIRE: return CombatIcons.sprite(SpriteID.Magicon.FIRE_STRIKE);
            default: return CombatIcons.spell(element, null);
        }
    }

    private int equippedWeaponId() {
        ItemContainer equipment = client.getItemContainer(CONTAINER_EQUIPMENT);
        if (equipment == null) {
            return -1;
        }
        Item weapon = equipment.getItem(EQUIPMENT_WEAPON_SLOT);
        return weapon != null ? weapon.getId() : -1;
    }

    // Green = your style hits the weakness, red = it doesn't, white = can't tell
    // (weakness unknown, or the ammo type / spell element couldn't be detected).
    public Color weaknessMatchColor(WeaponStyles.Style style, MonsterWeakness weakness) {
        Color match = BradsMultiToolOverlay.GOOD;
        Color mismatch = BradsMultiToolOverlay.BAD;
        Color unsure = Color.WHITE;

        if (weakness.type == MonsterWeakness.Type.UNKNOWN) {
            return unsure;
        }
        if (weakness.type != style.type()) {
            return mismatch;
        }
        if (weakness.type == MonsterWeakness.Type.MAGIC) {
            if (weakness.element == null) {
                return match;
            }
            if (style.element == null) {
                return unsure;
            }
            return weakness.element == style.element ? match : mismatch;
        }
        if (weakness.subType == null) {
            return match;
        }
        if (style.damageType == null) {
            return unsure;
        }
        return weakness.subType.equalsIgnoreCase(style.damageType) ? match : mismatch;
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
            updatePanelUI();
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

        announceLevelUp(skill, currentXp);

        if (!baseSkillXp.containsKey(skill)) {
            baseSkillXp.put(skill, currentXp);
            return;
        }

        int gained = currentXp - baseSkillXp.get(skill);
        if (gained > 0) {
            int previousGained = gainedSkillXp.getOrDefault(skill, 0);
            int drop = gained - previousGained;
            if (drop > 0) {
                lastXpDrops.put(skill, drop);
                lastXpTicks.put(skill, client.getTickCount());
            }
            gainedSkillXp.put(skill, gained);
            updatePanelUI();
        }
    }

    // "Woodcutting level 45!" when a real level goes up (levels already reached at login aren't announced)
    private void announceLevelUp(Skill skill, int xp) {
        int level = Experience.getLevelForXp(xp);
        Integer previous = realLevels.put(skill, level);
        if (previous == null || level <= previous || !alertsReady()) {
            return;
        }
        alerts.speakClips(AlertType.LEVEL_UP, VoiceAlertService.levelUpClips(skill, level));
        alerts.chat(AlertType.LEVEL_UP, skill.getName() + " level " + level + "!");
        announceNewMethod(skill, previous, level);
    }

    // A level-up that moves a goal skill onto a different suggested method says so in chat
    private void announceNewMethod(Skill skill, int before, int now) {
        if (!config.showTrainingSuggestion() || getSkillGoal(skill) <= client.getSkillExperience(skill)
            || getGoalTarget(skill) != null) {
            return;
        }
        if (skill == Skill.SLAYER) {
            SlayerData.Master was = slayerData.bestMaster(combatLevel, before);
            SlayerData.Master master = slayerData.bestMaster(combatLevel, now);
            if (master != null && master != was) {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[Snuffy's Multi-Tool]</col> "
                    + "Slayer goal: you can now get tasks from " + master.name, null);
            }
            return;
        }
        TrainingAdvisor.Suggestion was = trainingAdvisor.suggest(skill, before);
        TrainingAdvisor.Suggestion suggestion = trainingAdvisor.suggest(skill, now);
        if (suggestion != null && !suggestion.equals(was)) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[Snuffy's Multi-Tool]</col> "
                + skill.getName() + " goal: you can now switch to " + suggestion.text, null);
        }
    }

    // Combat asks for an update on every hit; queue at most one at a time
    private final java.util.concurrent.atomic.AtomicBoolean panelUpdateQueued = new java.util.concurrent.atomic.AtomicBoolean();

    private void updatePanelUI() {
        if (panel != null && panelUpdateQueued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                panelUpdateQueued.set(false);
                panel.updatePanel();
            });
        }
    }

    public int getCurrentSkillXp(Skill skill) {
        return client.getSkillExperience(skill);
    }

    public List<Skill> getTrackedSkills() {
        List<Skill> skills = new ArrayList<>();
        for (Map.Entry<Skill, Integer> entry : gainedSkillXp.entrySet()) {
            if (entry.getValue() > 0 && !isSkillIgnored(entry.getKey())) {
                skills.add(entry.getKey());
            }
        }
        return skills;
    }

    /** True while you're training the skill: XP from it in the last 15 seconds. */
    /**
     * An XP drop in the skill in the last two minutes: long enough to cover slow trees and walking between
     * resources, for showing that skill's timer pies only while you train it.
     */
    boolean trainedRecently(Skill skill) {
        Integer last = lastXpTicks.get(skill);
        return last != null && client.getTickCount() - last <= PIE_TRAINING_TICKS;
    }

    boolean isTraining(Skill skill) {
        Integer last = lastXpTicks.get(skill);
        return last != null && client.getTickCount() - last <= TRAINING_WINDOW_TICKS;
    }

    public int getLastXpDrop(Skill skill) {
        return lastXpDrops.getOrDefault(skill, 0);
    }

    public int getSkillGained(Skill skill) {
        return gainedSkillXp.getOrDefault(skill, 0);
    }

    public int getSkillXpPerHour(Skill skill) {
        int gained = getSkillGained(skill);
        if (gained <= 0) return 0;
        long ticks = skillActiveTicks.getOrDefault(skill, 0L);
        if (ticks < 8) return 0;
        long elapsedSeconds = (long) (ticks * 0.6);
        return (int) ((gained * 3600L) / elapsedSeconds);
    }

    public int getSkillXpPerMinute(Skill skill) {
        int gained = getSkillGained(skill);
        if (gained <= 0) return 0;
        long ticks = skillActiveTicks.getOrDefault(skill, 0L);
        if (ticks < 8) return 0;
        long elapsedSeconds = (long) (ticks * 0.6);
        return (int) ((gained * 60L) / elapsedSeconds);
    }

    public int getTotalCombatXp() {
        int total = 0;
        for (Map.Entry<Skill, Integer> entry : gainedSkillXp.entrySet()) {
            if (!isSkillIgnored(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }


    @Provides
    PluginIdentity provideIdentity() {
        return PluginIdentity.MULTI_TOOL;
    }

    @Provides
    CommonConfig provideCommonConfig(BradsMultiToolConfig config) {
        return config;
    }

    @Provides
    BradsMultiToolConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BradsMultiToolConfig.class);
    }
}
