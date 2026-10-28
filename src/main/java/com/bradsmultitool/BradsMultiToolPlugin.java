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
import net.runelite.client.eventbus.EventBus;
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
    name = "Brad's Multi-Tool",
    description = "Logout warning bell, Gemstone Crab 3D model tracker, multi-skill rate panel, voice announcements",
    tags = {"brad", "xp", "skills", "gemstone", "crab", "shell", "tunnel", "logout", "voice"}
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

    private static final int CRAB_BURROW_ALERT_DISTANCE = 10;
    private static final int GEMSTONE_CRAB_ID = 14779;
    private static final int GEMSTONE_CRAB_SHELL_ID = 14780;
    // The shell lasts 90 seconds (150 ticks); allow a little slack
    private static final int SHELL_LIFETIME_TICKS = 160;
    private static final int GEMS_PER_SHELL = 3;

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
    @Getter
    private SlayerTracker slayerTracker;

    @Inject
    private TreeTimer treeTimer;

    @Inject
    private SkillingData skillingData;

    @Inject
    private KeyManager keyManager;

    /** Toggled by the slayer show/hide hotkey. */
    @Getter
    private boolean slayerCardHidden = false;

    private final HotkeyListener slayerToggleListener = new HotkeyListener(() -> this.config.slayerToggleKey()) {
        @Override
        public void hotkeyPressed() {
            slayerCardHidden = !slayerCardHidden;
        }
    };

    @Inject
    private BradsMultiToolConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private Notifier notifier;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private SkillIconManager skillIconManager;

    private final VoiceAlertService voiceService = new VoiceAlertService();
    private NavigationButton navButton;
    private BradsMultiToolPanel panel;

    @Getter
    private NPC activeCrab = null;

    @Getter
    private NPC dormantCrabShellNpc = null;

    /** Active crab's health 0-100, or -1 before its health bar has been seen. */
    @Getter
    private int crabHpPercent = -1;
    private boolean crabLowHealthAlerted = false;

    // Tick of the "enough understanding" message for the latest shell, -1 when not eligible
    private int shellEligibleTick = -1;
    private int shellGemsMined = 0;

    @Getter
    private final List<TileObject> trackedShellObjects = new ArrayList<>();

    @Getter
    private final List<TileObject> trackedTunnels = new ArrayList<>();

    // Gemstone Crab Specific Damage & Telemetry
    @Getter
    private long currentCrabDamage = 0;

    @Getter
    private int crabLastHit = 0;

    @Getter
    private int crabMaxHit = 0;

    @Getter
    private double crabLiveDps = 0.0;
    private Instant crabFightStartTime = null;
    private boolean wasDirectlyTrackingCrab = false;

    @Getter
    private String combatDiscipline = "Melee";

    @Getter
    private String combatMethod = "Slash";

    @Getter
    private String crabCombatXpType = "Strength";

    /** The skills behind crabCombatXpType, for showing their icons. */
    @Getter
    private List<Skill> crabCombatXpSkills = Collections.singletonList(Skill.STRENGTH);

    @Getter
    private Color combatMethodColor = Color.WHITE;

    @Getter
    private CombatIcons.Icon combatMethodIcon = null;

    /** The player's current combat style (weapon style, autocast or hand-cast spell). */
    @Getter
    private WeaponStyles.Style currentStyle = null;

    private final SpellbookReader spellbook = new SpellbookReader();
    private String manualSpellName = null;
    private int manualSpellSpriteId = -1;
    private int manualCastTick = -1;

    @Getter
    private String targetCrabHp = "No Target";

    @Getter
    private String estimatedBurrowTime = "--:--";

    @Getter
    private boolean logoutImminent = false;
    private int timesIdleAlerted = 0;

    private WorldPoint lastPlayerLocation = null;
    private boolean characterWasActive = false;
    private boolean characterIdleAlerted = false;
    private int lastActiveTick = 0;
    private Instant lastIdleAlertTime = Instant.EPOCH;

    @Getter
    private int engineIdleSeconds = 0;

    @Getter
    private boolean inSpawnVicinity = false;

    @Getter
    private String currentCoveName = "Cavern Complex";

    @Getter
    private boolean playerAttackingCrab = false;

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

    private Instant fightStartTime = null;
    private int initialHealthRatio = -1;

    // Tick-level XP change detection accumulator
    private final Set<Skill> tickXpChanges = EnumSet.noneOf(Skill.class);

    private final Map<Skill, Integer> baseSkillXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> gainedSkillXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Long> skillActiveTicks = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> lastXpDrops = new EnumMap<>(Skill.class);
    private final Set<Skill> pausedSkills = EnumSet.noneOf(Skill.class);

    @Override
    protected void startUp() throws Exception {
        monsterData.load(gson);
        skillingData.load(gson);
        eventBus.register(extraAlerts);
        keyManager.registerKeyListener(slayerToggleListener);
        overlayManager.add(overlay);
        overlayManager.add(prayerOverlay);
        overlayManager.add(idleWarningOverlay);
        overlayManager.add(hoverOverlay);
        overlayManager.add(orbOverlay);
        overlayManager.add(slayerOverlay);
        overlayManager.add(skillingOverlay);

        panel = new BradsMultiToolPanel(this, config, skillIconManager);

        navButton = NavigationButton.builder()
            .tooltip("Brad's Multi-Tool")
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
        eventBus.unregister(extraAlerts);
        keyManager.unregisterKeyListener(slayerToggleListener);
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
        }
        voiceService.shutdown();
        activeCrab = null;
        dormantCrabShellNpc = null;
        currentPvmTarget = null;
        trackedShellObjects.clear();
        trackedTunnels.clear();
        pausedSkills.clear();
        logoutImminent = false;
        inSpawnVicinity = false;
        pvmOverlayActive = false;
        buffState = BuffState.UNPOTTED;
        wasDirectlyTrackingCrab = false;
        resetFightTracking();
    }

    // Plays a random real alert in the newly selected voice
    private void previewVoice() {
        VoiceAlertService.VoiceLine[] lines = VoiceAlertService.VoiceLine.values();
        speak(lines[ThreadLocalRandom.current().nextInt(lines.length)]);
    }

    void speak(VoiceAlertService.VoiceLine line) {
        voiceService.speakAsync(line, config.voicePersona(), config.voiceVolume());
    }

    public String getPvmTargetHp() {
        return pvmHpPercent + "%";
    }

    public String getCombatXpType() {
        return crabCombatXpType;
    }

    void sendGameChatMessage(String message) {
        if (config.sendChatMessages()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[Brad's Multi-Tool]</col> " + message, null);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

        switch (event.getKey()) {
            case "voicePersona":
            case "voiceVolume":
                previewVoice();
                break;
            case "voiceBuffExpired":
                if (config.voiceBuffExpired()) {
                    speak(VoiceAlertService.VoiceLine.BUFF_EXPIRED);
                }
                break;
            case "voicePrayerLow":
                if (config.voicePrayerLow()) {
                    speak(VoiceAlertService.VoiceLine.PRAYER_LOW);
                }
                break;
            case "voiceCrabLowHealth":
                if (config.voiceCrabLowHealth()) {
                    speak(VoiceAlertService.VoiceLine.CRAB_LOW_HEALTH);
                }
                break;
            case "voiceCharacterIdle":
                if (config.voiceCharacterIdle()) {
                    speak(VoiceAlertService.VoiceLine.CHARACTER_IDLE);
                }
                break;
            case "voiceBuffLow":
                if (config.voiceBuffLow()) {
                    speak(VoiceAlertService.VoiceLine.BUFF_LOW);
                }
                break;
            case "voiceHealthLow":
                if (config.voiceHealthLow()) {
                    speak(VoiceAlertService.VoiceLine.HEALTH_LOW);
                }
                break;
            case "voiceIdleLogout":
                if (config.voiceIdleLogout()) {
                    speak(VoiceAlertService.VoiceLine.IDLE_LOGOUT);
                }
                break;
            case "voiceCrabMoved":
                if (config.voiceCrabMoved()) {
                    speak(VoiceAlertService.VoiceLine.CRAB_MOVED);
                }
                break;
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

    public boolean isCrabActive() {
        return activeCrab != null && !activeCrab.isDead();
    }

    public boolean isCrabBurrowed() {
        return !isCrabActive() || dormantCrabShellNpc != null || !trackedShellObjects.isEmpty();
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
        currentCrabDamage = 0;
        crabLiveDps = 0.0;
        crabFightStartTime = null;
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
        resetFightTracking();
        updatePanelUI();
    }

    private void resetFightTracking() {
        fightStartTime = null;
        initialHealthRatio = -1;
        estimatedBurrowTime = "--:--";
        crabFightStartTime = null;
        currentCrabDamage = 0;
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

    public boolean isSkillPaused(Skill skill) {
        return pausedSkills.contains(skill);
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
        if (event.getGameState() != GameState.LOGGED_IN) {
            characterWasActive = false;
            slayerTracker.reset();
            lastPlayerLocation = null;
            activeCrab = null;
            dormantCrabShellNpc = null;
            currentPvmTarget = null;
            trackedShellObjects.clear();
            trackedTunnels.clear();
            inSpawnVicinity = false;
            buffState = BuffState.UNPOTTED;
            pvmOverlayActive = false;
            logoutImminent = false;
            wasDirectlyTrackingCrab = false;
            aggroTimerStart = null;
            lastAggroAnchor = null;
            resetFightTracking();
            targetCrabHp = "Logged Out";
            playerAttackingCrab = false;
            updatePanelUI();
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        evaluateNpc(event.getNpc());
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (npc == activeCrab) {
            activeCrab = null;
            targetCrabHp = "Burrowed";
            estimatedBurrowTime = "Burrowed";
            playerAttackingCrab = false;
            resetFightTracking();

            if (inSpawnVicinity && wasDirectlyTrackingCrab && isNearPlayer(npc, CRAB_BURROW_ALERT_DISTANCE)) {
                if (config.voiceCrabMoved()) {
                    speak(VoiceAlertService.VoiceLine.CRAB_MOVED);
                }
                sendGameChatMessage("The Gemstone Crab has relocated to another cave!");
            }
            wasDirectlyTrackingCrab = false;
        }
        if (npc == dormantCrabShellNpc) {
            dormantCrabShellNpc = null;
        }
        if (npc == currentPvmTarget) {
            currentPvmTarget = null;
            pvmHpPercent = 0;
            pvmCurrentHp = 0;
            pvmFightEnded = true;
            lastPvmCombatActivity = Instant.now();
        }
    }

    // NPCs also despawn when they leave the player's view (~15 tiles), so only a despawn
    // well inside that range is the crab actually burrowing rather than the player walking away.
    private boolean isNearPlayer(NPC npc, int maxDistance) {
        Player player = client.getLocalPlayer();
        if (player == null || npc.getWorldLocation() == null) {
            return false;
        }
        WorldPoint playerLocation = player.getWorldLocation();
        return playerLocation != null && playerLocation.distanceTo(npc.getWorldLocation()) <= maxDistance;
    }

    private enum CrabForm {
        NONE,
        ACTIVE,
        SHELL
    }

    public boolean isGemstoneCrab(NPC npc) {
        return classifyCrab(npc) != CrabForm.NONE;
    }

    /** Wiki stats for an NPC, or null if it isn't in the bundled data. */
    public MonsterData.Stats statsFor(NPC npc) {
        return monsterData.get(npc.getId());
    }

    public MonsterWeakness weaknessFor(NPC npc) {
        MonsterData.Stats stats = statsFor(npc);
        return stats != null ? MonsterData.weaknessOf(stats) : MonsterWeakness.fromName(npc.getName());
    }

    /** True for an NPC offering an "Attack" option. */
    public boolean isAttackable(NPC npc) {
        return hasAttackOption(npc);
    }

    private CrabForm classifyCrab(NPC npc) {
        if (npc == null) {
            return CrabForm.NONE;
        }
        if (npc.getId() == GEMSTONE_CRAB_ID) {
            return CrabForm.ACTIVE;
        }
        if (npc.getId() == GEMSTONE_CRAB_SHELL_ID) {
            return CrabForm.SHELL;
        }

        // Fallback in case the ids change: match the name and tell the shell apart by its "Mine" option
        NPCComposition comp = npc.getTransformedComposition();
        if (comp == null) {
            comp = npc.getComposition();
        }
        String name = comp != null && comp.getName() != null ? comp.getName() : npc.getName();
        if (name == null) {
            return CrabForm.NONE;
        }
        name = name.toLowerCase();
        if (!name.contains("gemstone crab") && !name.contains("gem crab")) {
            return CrabForm.NONE;
        }
        if (name.contains("shell")) {
            return CrabForm.SHELL;
        }
        if (comp != null && comp.getActions() != null) {
            for (String action : comp.getActions()) {
                if ("Mine".equalsIgnoreCase(action)) {
                    return CrabForm.SHELL;
                }
            }
        }
        return CrabForm.ACTIVE;
    }

    /**
     * Whether you can mine the current shell. Only the 16 players who dealt the most damage may mine it,
     * which the game announces in chat when the crab burrows; the shell lasts 90 seconds and gives each
     * eligible player 3 gems.
     */
    public boolean isShellMineable() {
        return shellEligibleTick >= 0
            && client.getTickCount() - shellEligibleTick <= SHELL_LIFETIME_TICKS
            && shellGemsMined < GEMS_PER_SHELL;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        String message = Text.removeTags(event.getMessage()).toLowerCase();
        if (message.contains("enough understanding of the crab to mine")) {
            shellEligibleTick = client.getTickCount();
            shellGemsMined = 0;
        } else if (message.contains("understanding of the gemstone crab is not great enough")) {
            shellEligibleTick = -1;
        } else if (message.contains("from the crab shell")) {
            shellGemsMined++;
        }
    }

    private void evaluateNpc(NPC npc) {
        switch (classifyCrab(npc)) {
            case ACTIVE:
                setActiveCrab(npc);
                break;
            case SHELL:
                dormantCrabShellNpc = npc;
                if (activeCrab == npc) {
                    activeCrab = null;
                    resetFightTracking();
                }
                break;
            default:
                break;
        }
    }

    private void setActiveCrab(NPC npc) {
        if (activeCrab != npc) {
            log.debug("Gemstone Crab detected: id={} at {}", npc.getId(), npc.getWorldLocation());
            resetFightTracking();
            crabHpPercent = -1;
            crabLowHealthAlerted = false;
        }
        activeCrab = npc;
        wasDirectlyTrackingCrab = true;
        dormantCrabShellNpc = null;
    }

    // The health bar is only sent while the crab is in view and in combat, so keep the last known value
    private void updateCrabHealth() {
        if (activeCrab == null) {
            return;
        }
        int ratio = activeCrab.getHealthRatio();
        int scale = activeCrab.getHealthScale();
        if (ratio >= 0 && scale > 0) {
            crabHpPercent = (ratio * 100) / scale;
        }
        if (crabHpPercent < 0) {
            return;
        }

        if (crabHpPercent > config.crabLowHealthPercent()) {
            crabLowHealthAlerted = false;
        } else if (!crabLowHealthAlerted && inSpawnVicinity) {
            crabLowHealthAlerted = true;
            if (config.voiceCrabLowHealth()) {
                speak(VoiceAlertService.VoiceLine.CRAB_LOW_HEALTH);
            }
            sendGameChatMessage("The Gemstone Crab is about to burrow (" + crabHpPercent + "% health).");
        }
    }

    // Spawn events can be missed (e.g. arriving through a cave reloads the scene and clears tracking),
    // so re-check the NPCs around the player every tick.
    private void scanForCrab() {
        NPC crab = null;
        NPC shell = null;
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            CrabForm form = classifyCrab(npc);
            if (form == CrabForm.ACTIVE && !npc.isDead()) {
                crab = npc;
            } else if (form == CrabForm.SHELL) {
                shell = npc;
            }
        }

        if (crab != null) {
            setActiveCrab(crab);
        } else if (shell != null) {
            dormantCrabShellNpc = shell;
        }
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
    public void onGameObjectSpawned(GameObjectSpawned event) {
        checkTileObject(event.getGameObject());
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        removeTileObject(event.getGameObject());
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        checkTileObject(event.getGroundObject());
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        removeTileObject(event.getGroundObject());
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event) {
        checkTileObject(event.getWallObject());
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned event) {
        removeTileObject(event.getWallObject());
    }

    private void checkTileObject(TileObject obj) {
        if (obj == null) return;

        ObjectComposition def = client.getObjectDefinition(obj.getId());
        if (def == null) return;

        String name = def.getName() != null ? def.getName().toLowerCase() : "";
        String[] actions = def.getActions();

        if (name.contains("shell") || (name.contains("gemstone") && name.contains("crab"))) {
            if (!trackedShellObjects.contains(obj)) trackedShellObjects.add(obj);
            return;
        }

        boolean isCavernTunnel = false;
        if (name.contains("crevice") || name.contains("cove tunnel") || name.contains("tunnel")) {
            isCavernTunnel = true;
        } else if (actions != null) {
            for (String act : actions) {
                if (act != null) {
                    String a = act.toLowerCase();
                    if (a.contains("crawl-through") || a.contains("squeeze-through")) {
                        isCavernTunnel = true;
                        break;
                    }
                }
            }
        }

        if (isCavernTunnel && !trackedTunnels.contains(obj)) {
            trackedTunnels.add(obj);
        }
    }

    private void removeTileObject(TileObject obj) {
        trackedShellObjects.remove(obj);
        trackedTunnels.remove(obj);
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        lastTickMillis = System.currentTimeMillis();
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // Process ground-truth XP drop accumulator from this tick
        resolveXpTypeFromTickDrops();
        tickXpChanges.clear();

        Player player = client.getLocalPlayer();
        if (player != null) {
            WorldPoint wp = player.getWorldLocation();
            scanForCrab();
            updateSpatialPresence(player, wp);
            updateCrabHealth();
            slayerTracker.update();
            treeTimer.update();
            updateTargetCrabStatus(player);
            updateGeneralPvmTarget(player);
            updateAggroCountdown(wp);

            if (crabFightStartTime != null && isCrabActive() && playerAttackingCrab) {
                long elapsed = Math.max(1, Duration.between(crabFightStartTime, Instant.now()).getSeconds());
                crabLiveDps = (double) currentCrabDamage / elapsed;
            }
        }

        processSimpleBuffStatus();
        if (player != null) {
            updateCharacterIdle(player);
        }
        monitorPrayer();
        monitorHealth();
        updateIdleState();
        updateRealtimeCombatMethod();

        for (Skill s : gainedSkillXp.keySet()) {
            if (gainedSkillXp.get(s) > 0 && !pausedSkills.contains(s) && !isSkillIgnored(s)) {
                long current = skillActiveTicks.getOrDefault(s, 0L);
                skillActiveTicks.put(s, current + 1);
            }
        }

        calculateBurrowCountdown();
        updatePanelUI();
    }

    private void resolveXpTypeFromTickDrops() {
        if (tickXpChanges.isEmpty()) return;

        boolean hasAtk = tickXpChanges.contains(Skill.ATTACK);
        boolean hasStr = tickXpChanges.contains(Skill.STRENGTH);
        boolean hasDef = tickXpChanges.contains(Skill.DEFENCE);
        boolean hasRng = tickXpChanges.contains(Skill.RANGED);
        boolean hasMag = tickXpChanges.contains(Skill.MAGIC);

        crabCombatXpSkills = new ArrayList<>();
        if (hasAtk && hasStr && hasDef) {
            crabCombatXpType = "Shared";
            crabCombatXpSkills.addAll(Arrays.asList(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE));
        } else if (hasRng && hasDef) {
            crabCombatXpType = "Ranged / Def";
            crabCombatXpSkills.addAll(Arrays.asList(Skill.RANGED, Skill.DEFENCE));
        } else if (hasMag && hasDef) {
            crabCombatXpType = "Magic / Def";
            crabCombatXpSkills.addAll(Arrays.asList(Skill.MAGIC, Skill.DEFENCE));
        } else if (hasAtk) {
            crabCombatXpType = "Attack";
            crabCombatXpSkills.add(Skill.ATTACK);
        } else if (hasStr) {
            crabCombatXpType = "Strength";
            crabCombatXpSkills.add(Skill.STRENGTH);
        } else if (hasDef) {
            crabCombatXpType = "Defence";
            crabCombatXpSkills.add(Skill.DEFENCE);
        } else if (hasRng) {
            crabCombatXpType = "Ranged";
            crabCombatXpSkills.add(Skill.RANGED);
        } else if (hasMag) {
            crabCombatXpType = "Magic";
            crabCombatXpSkills.add(Skill.MAGIC);
        }
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
            pvmAggroStatusText = "Passive";
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
                sendGameChatMessage("Your combat buff has run out.");
                if (config.voiceBuffExpired()) {
                    speak(lastEndedBuffName != null
                        ? potionLine(lastEndedBuffName, false, expiredLine(lastEndedCategory))
                        : expiredLine(lastEndedCategory));
                }
            } else if (buffState == BuffState.EXPIRED && config.repeatBuffAlert()
                && Duration.between(lastBuffExpiredSoundTime, Instant.now()).getSeconds() >= config.repeatBuffInterval()
                && timesBuffExpiredSounded <= config.maxBuffRepeats()) {
                timesBuffExpiredSounded++;
                lastBuffExpiredSoundTime = Instant.now();
                sendGameChatMessage("Your combat buff is still out.");
                if (config.voiceBuffExpired()) {
                    speak(lastEndedBuffName != null
                        ? potionLine(lastEndedBuffName, false, expiredLine(lastEndedCategory))
                        : expiredLine(lastEndedCategory));
                }
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

    // Warn once per dose for each buff, only when its time left is exact
    private void checkBuffRunningOut() {
        int warnSeconds = config.buffWarningSeconds();
        long now = System.currentTimeMillis();
        for (ActiveBuffs.Buff buff : activeBuffs) {
            Integer warnedAt = buffWarnedAtBoost.get(buff.name);
            if (warnedAt != null && buff.boost <= warnedAt) {
                continue;
            }
            if (warnSeconds <= 0 || !buff.exact || buff.millisLeft(now) > warnSeconds * 1000L) {
                if (warnedAt != null) {
                    buffWarnedAtBoost.remove(buff.name);
                }
                continue;
            }
            buffWarnedAtBoost.put(buff.name, buff.boost);
            if (config.voiceBuffLow()) {
                speak(potionLine(buff.name, true, runningOutLine(buff.category)));
            }
            sendGameChatMessage("Your " + buff.name + " buff runs out in " + BuffTimer.formatMillis(buff.millisLeft(now)) + ".");
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
                sendGameChatMessage("Your " + before.name + " buff has run out.");
                if (config.voiceBuffExpired()) {
                    speak(potionLine(before.name, false, expiredLine(before.category)));
                }
            }
        }
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

        if (canSound) {
            timesHealthAlertSounded++;
            lastHealthAlertTime = Instant.now();
            if (config.voiceHealthLow()) {
                speak(VoiceAlertService.VoiceLine.HEALTH_LOW);
            }
            sendGameChatMessage("Health low (" + hp + " hitpoints).");
        }
    }

    private void monitorPrayer() {
        int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
        boolean isZero = (currentPrayer <= 0);

        if (isZero && !zeroPrayerAlert) {
            zeroPrayerAlert = true;
            lowPrayerAlert = false;
            if (config.voicePrayerLow()) {
                speak(VoiceAlertService.VoiceLine.OUT_OF_PRAYER);
            }
            sendGameChatMessage("Out of prayer points.");
            return;
        } else if (!isZero) {
            zeroPrayerAlert = false;
        }

        if (!isZero && config.enableLowPrayerAlert() && currentPrayer <= threshold(Skill.PRAYER, config.lowPrayerPercent())) {
            lowPrayerAlert = true;

            boolean canSound = false;
            if (timesPrayerAlertSounded == 0) {
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
                if (config.voicePrayerLow()) {
                    speak(VoiceAlertService.VoiceLine.PRAYER_LOW);
                }
                sendGameChatMessage("Prayer points low (" + currentPrayer + " remaining).");
            }
        } else if (currentPrayer > threshold(Skill.PRAYER, config.lowPrayerPercent())) {
            lowPrayerAlert = false;
            timesPrayerAlertSounded = 0;
        }
    }

    // Active = animating (attacking, skilling), targeting something, or moving. After being active,
    // standing around for the configured time alerts once; doing anything re-arms it.
    private void updateCharacterIdle(Player player) {
        WorldPoint location = player.getWorldLocation();
        boolean moved = lastPlayerLocation != null && !lastPlayerLocation.equals(location);
        lastPlayerLocation = location;
        boolean active = player.getAnimation() != -1 || player.getInteracting() != null || moved;

        int tick = client.getTickCount();
        if (active) {
            characterWasActive = true;
            characterIdleAlerted = false;
            lastActiveTick = tick;
            return;
        }
        if (!config.characterIdleAlert() || !characterWasActive || characterIdleAlerted) {
            return;
        }

        int idleSeconds = (int) ((tick - lastActiveTick) * 0.6);
        if (idleSeconds >= config.characterIdleSeconds()) {
            characterIdleAlerted = true;
            if (config.voiceCharacterIdle()) {
                speak(VoiceAlertService.VoiceLine.CHARACTER_IDLE);
            }
            sendGameChatMessage("You have been idle for " + idleSeconds + " seconds.");
        }
    }

    private void updateIdleState() {
        int idleCycles = Math.min(client.getKeyboardIdleTicks(), client.getMouseIdleTicks());
        engineIdleSeconds = idleCycles / 50;

        int logoutAfter = idleLogoutSeconds();
        int warnAt = logoutAfter - Math.max(5, Math.min(logoutAfter - 5, config.idleWarningSeconds()));
        boolean imminent = engineIdleSeconds >= warnAt;
        if (!imminent) {
            logoutImminent = false;
            timesIdleAlerted = 0;
            return;
        }

        boolean alert = false;
        if (!logoutImminent) {
            alert = true;
            notifier.notify("Brad's Multi-Tool: You are about to be logged out for inactivity!");
            sendGameChatMessage("You will be logged out for inactivity soon.");
        } else if (config.repeatIdleAlert()
            && Duration.between(lastIdleAlertTime, Instant.now()).getSeconds() >= config.repeatIdleInterval()
            && timesIdleAlerted <= config.maxIdleRepeats()) {
            alert = true;
        }
        logoutImminent = true;

        if (alert) {
            timesIdleAlerted++;
            lastIdleAlertTime = Instant.now();
            if (config.voiceIdleLogout()) {
                speak(VoiceAlertService.VoiceLine.IDLE_LOGOUT);
            }
        }
    }

    public int getSecondsUntilIdleLogout() {
        return Math.max(0, idleLogoutSeconds() - engineIdleSeconds);
    }

    /**
     * Seconds of inactivity before the client logs out. Follows RuneLite's Logout Timer plugin
     * (or anything else that changes the client's idle timeout), which is counted in 20ms client cycles.
     */
    private int idleLogoutSeconds() {
        int cycles = client.getIdleTimeout();
        return cycles > 0 ? cycles / 50 : 300;
    }

    private void updateGeneralPvmTarget(Player player) {
        Actor target = player.getInteracting();

        NPC attackingNpc = null;
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc != null && npc.getInteracting() == player && !npc.isDead()) {
                if (classifyCrab(npc) == CrabForm.NONE && hasAttackOption(npc)) {
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
            if (classifyCrab(activeFightNpc) != CrabForm.NONE) {
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

                MonsterData.Stats stats = monsterData.get(activeFightNpc.getId());
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

        String n = npc.getName().toLowerCase();

        boolean aggressive = false;
        if (n.contains("crab") || n.contains("demon") || n.contains("dragon") || n.contains("wyvern")
            || n.contains("giant") || n.contains("skeleton") || n.contains("zombie") || n.contains("gargoyle")
            || n.contains("nechryael") || n.contains("abyssal") || n.contains("hellhound") || n.contains("bandit")
            || n.contains("kalphite") || n.contains("vampyre") || n.contains("vyre") || n.contains("drake")) {
            aggressive = true;
        } else {
            Player local = client.getLocalPlayer();
            if (local != null && npc.getCombatLevel() > 0) {
                aggressive = (local.getCombatLevel() <= (npc.getCombatLevel() * 2 + 1));
            }
        }
        MonsterData.Stats stats = monsterData.get(npc.getId());
        if (stats != null) {
            pvmTargetIsAggressive = stats.aggressive;
            pvmTargetWeakness = MonsterData.weaknessOf(stats);
        } else {
            pvmTargetIsAggressive = aggressive;
            pvmTargetWeakness = MonsterWeakness.fromName(npc.getName());
        }
    }

    public void updateRealtimeCombatMethod() {
        WeaponStyles.Style style = WeaponStyles.resolve(
            client.getVarbitValue(VARBIT_EQUIPPED_WEAPON_TYPE),
            client.getVarpValue(VARP_ATTACK_STYLE),
            client.getVarbitValue(VARBIT_DEFENSIVE_CASTING) == 1,
            client.getVarbitValue(VARBIT_AUTOCAST_SPELL));

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
    public CombatIcons.Icon elementSpellIcon(MonsterWeakness.Element element) {
        int sprite = spellbook.spriteFor(WeaponStyles.spellName(element, "Strike"));
        return sprite > 0 ? CombatIcons.sprite(sprite) : CombatIcons.spell(element, null);
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

    private void updateSpatialPresence(Player player, WorldPoint wp) {
        if (wp == null) {
            inSpawnVicinity = false;
            return;
        }

        if (activeCrab != null && !activeCrab.isDead()) {
            if (wp.distanceTo(activeCrab.getWorldLocation()) <= 28) {
                inSpawnVicinity = true;
                updateCoveName(wp);
                return;
            }
        }

        if (dormantCrabShellNpc != null && !dormantCrabShellNpc.isDead()) {
            if (wp.distanceTo(dormantCrabShellNpc.getWorldLocation()) <= 28) {
                inSpawnVicinity = true;
                updateCoveName(wp);
                return;
            }
        }

        for (TileObject tunnel : trackedTunnels) {
            if (tunnel != null && wp.distanceTo(tunnel.getWorldLocation()) <= 24) {
                inSpawnVicinity = true;
                updateCoveName(wp);
                return;
            }
        }

        for (TileObject shell : trackedShellObjects) {
            if (shell != null && wp.distanceTo(shell.getWorldLocation()) <= 24) {
                inSpawnVicinity = true;
                updateCoveName(wp);
                return;
            }
        }

        inSpawnVicinity = false;
    }

    private void updateCoveName(WorldPoint wp) {
        int ry = wp.getRegionY();
        int rx = wp.getRegionX();

        if (ry > 38) {
            currentCoveName = "North Cove";
        } else if (rx < 35) {
            currentCoveName = "West Cove";
        } else {
            currentCoveName = "East Cove";
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC npc = (NPC) event.getActor();
        Hitsplat hitsplat = event.getHitsplat();

        if (hitsplat.isMine()) {
            int dmg = hitsplat.getAmount();

            if (npc == activeCrab || classifyCrab(npc) == CrabForm.ACTIVE) {
                activeCrab = npc;
                playerAttackingCrab = true;
                wasDirectlyTrackingCrab = true;
                currentCrabDamage += dmg;

                // Persistent Last Hit and Max Hit for Gemstone Crab
                crabLastHit = dmg;
                if (dmg > crabMaxHit) {
                    crabMaxHit = dmg;
                }

                if (crabFightStartTime == null) {
                    crabFightStartTime = Instant.now();
                }
                long elapsed = Math.max(1, Duration.between(crabFightStartTime, Instant.now()).getSeconds());
                crabLiveDps = (double) currentCrabDamage / elapsed;
            }
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

    private void calculateBurrowCountdown() {
        if (activeCrab == null || activeCrab.isDead()) {
            resetFightTracking();
            return;
        }

        int currentRatio = activeCrab.getHealthRatio();
        int scale = activeCrab.getHealthScale();

        if (currentRatio <= 0 || scale <= 0) {
            estimatedBurrowTime = "Burrowing...";
            return;
        }

        if (initialHealthRatio == -1 || fightStartTime == null) {
            initialHealthRatio = currentRatio;
            fightStartTime = Instant.now();
            estimatedBurrowTime = "--:--";
            return;
        }

        long elapsedSec = Duration.between(fightStartTime, Instant.now()).getSeconds();
        int ratioLost = initialHealthRatio - currentRatio;

        if (elapsedSec < 3 || ratioLost <= 0) {
            estimatedBurrowTime = "--:--";
            return;
        }

        double ratioPerSecond = (double) ratioLost / elapsedSec;
        int secondsRemaining = (int) Math.ceil(currentRatio / ratioPerSecond);

        if (secondsRemaining <= 0) {
            estimatedBurrowTime = "00:00";
        } else {
            int m = secondsRemaining / 60;
            int s = secondsRemaining % 60;
            estimatedBurrowTime = String.format("%02d:%02d", m, s);
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
                tickXpChanges.add(skill);
            }
            gainedSkillXp.put(skill, gained);
            updatePanelUI();
        }
    }

    private void updatePanelUI() {
        if (panel != null) {
            SwingUtilities.invokeLater(() -> panel.updatePanel());
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

    private void updateTargetCrabStatus(Player player) {
        Actor target = player.getInteracting();
        boolean interactingWithCrab = false;

        if (target instanceof NPC) {
            NPC targetNpc = (NPC) target;
            if (classifyCrab(targetNpc) == CrabForm.ACTIVE) {
                interactingWithCrab = true;
                activeCrab = targetNpc;
            }
        }

        if (interactingWithCrab && activeCrab != null) {
            playerAttackingCrab = true;
            wasDirectlyTrackingCrab = true;
            if (activeCrab.getHealthRatio() > 0 && activeCrab.getHealthScale() > 0) {
                int hpPercent = (activeCrab.getHealthRatio() * 100) / activeCrab.getHealthScale();
                targetCrabHp = hpPercent + "%";
            } else {
                targetCrabHp = "Active (100%)";
            }
        } else if (activeCrab != null && !activeCrab.isDead()) {
            playerAttackingCrab = false;
            targetCrabHp = "Active (Idle)";
        } else if (dormantCrabShellNpc != null || !trackedShellObjects.isEmpty()) {
            playerAttackingCrab = false;
            targetCrabHp = "Shell (Burrowed)";
        } else {
            playerAttackingCrab = false;
            targetCrabHp = "No Target";
        }
    }

    @Provides
    BradsMultiToolConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BradsMultiToolConfig.class);
    }
}
