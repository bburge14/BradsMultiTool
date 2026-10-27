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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

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

    private static final int CRAB_BURROW_ALERT_DISTANCE = 10;

    // The client logs out after 15000 idle client cycles (20ms each = 5 minutes)
    private static final int IDLE_LOGOUT_SECONDS = 300;
    private static final int IDLE_WARNING_SECONDS = 270;

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

    @Getter
    private Color combatMethodColor = new Color(255, 215, 0);

    @Getter
    private CombatIcons.Icon combatMethodIcon = null;

    @Getter
    private String targetCrabHp = "No Target";

    @Getter
    private String estimatedBurrowTime = "--:--";

    @Getter
    private boolean logoutImminent = false;

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

    @Getter
    private String buffStatusText = "No Buffs";

    private int previousMaxBoost = 0;
    private int timesBuffExpiredSounded = 0;
    private Instant lastBuffExpiredSoundTime = Instant.EPOCH;

    @Getter
    private boolean lowPrayerAlert = false;
    @Getter
    private boolean zeroPrayerAlert = false;
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
        overlayManager.add(overlay);
        overlayManager.add(prayerOverlay);
        overlayManager.add(idleWarningOverlay);

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

    public void testVoiceAlert() {
        voiceService.speakAsync("Voice test online.", config.voiceGender());
    }

    public String getPvmTargetHp() {
        return pvmHpPercent + "%";
    }

    public String getCombatXpType() {
        return crabCombatXpType;
    }

    private void sendGameChatMessage(String message) {
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
            case "testVoice":
            case "voiceGender":
                testVoiceAlert();
                break;
            case "voiceBuffExpired":
                if (config.voiceBuffExpired()) {
                    voiceService.speakAsync("Combat buff has expired. Re-pot!", config.voiceGender());
                }
                break;
            case "voicePrayerLow":
                if (config.voicePrayerLow()) {
                    voiceService.speakAsync("Prayer points low.", config.voiceGender());
                }
                break;
            case "voiceCrabMoved":
                if (config.voiceCrabMoved()) {
                    voiceService.speakAsync("The crab has moved!", config.voiceGender());
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
        if (event.getGameState() != GameState.LOGGED_IN) {
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
                    voiceService.speakAsync("The crab has moved!", config.voiceGender());
                }
                sendGameChatMessage("<col=ff9900>The Gemstone Crab has relocated to another cave!</col>");
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

    private void evaluateNpc(NPC npc) {
        if (npc == null || npc.getName() == null) return;
        String name = npc.getName().toLowerCase();

        if (name.contains("gemstone crab") || name.contains("gem crab")) {
            boolean isMineableShell = false;
            NPCComposition comp = npc.getTransformedComposition();
            if (comp == null) comp = npc.getComposition();
            if (comp != null && comp.getActions() != null) {
                for (String a : comp.getActions()) {
                    if (a != null && a.equalsIgnoreCase("Mine")) {
                        isMineableShell = true;
                        break;
                    }
                }
            }

            if (!isMineableShell) {
                if (activeCrab != npc) {
                    resetFightTracking();
                }
                activeCrab = npc;
                wasDirectlyTrackingCrab = true;
                dormantCrabShellNpc = null;
            } else {
                dormantCrabShellNpc = npc;
                if (activeCrab == npc) {
                    activeCrab = null;
                    resetFightTracking();
                }
            }
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
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // Process ground-truth XP drop accumulator from this tick
        resolveXpTypeFromTickDrops();
        tickXpChanges.clear();

        Player player = client.getLocalPlayer();
        if (player != null) {
            WorldPoint wp = player.getWorldLocation();
            updateSpatialPresence(player, wp);
            updateTargetCrabStatus(player);
            updateGeneralPvmTarget(player);
            updateAggroCountdown(wp);

            if (crabFightStartTime != null && isCrabActive() && playerAttackingCrab) {
                long elapsed = Math.max(1, Duration.between(crabFightStartTime, Instant.now()).getSeconds());
                crabLiveDps = (double) currentCrabDamage / elapsed;
            }
        }

        processSimpleBuffStatus();
        monitorPrayer();
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

        if (hasAtk && hasStr && hasDef) {
            crabCombatXpType = "Shared";
        } else if (hasRng && hasDef) {
            crabCombatXpType = "Ranged / Def";
        } else if (hasMag && hasDef) {
            crabCombatXpType = "Magic / Def";
        } else if (hasAtk) {
            crabCombatXpType = "Attack";
        } else if (hasStr) {
            crabCombatXpType = "Strength";
        } else if (hasDef) {
            crabCombatXpType = "Defence";
        } else if (hasRng) {
            crabCombatXpType = "Ranged";
        } else if (hasMag) {
            crabCombatXpType = "Magic";
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

        int atkBoost = Math.max(0, client.getBoostedSkillLevel(Skill.ATTACK) - client.getRealSkillLevel(Skill.ATTACK));
        int strBoost = Math.max(0, client.getBoostedSkillLevel(Skill.STRENGTH) - client.getRealSkillLevel(Skill.STRENGTH));
        int defBoost = Math.max(0, client.getBoostedSkillLevel(Skill.DEFENCE) - client.getRealSkillLevel(Skill.DEFENCE));
        int rngBoost = Math.max(0, client.getBoostedSkillLevel(Skill.RANGED) - client.getRealSkillLevel(Skill.RANGED));
        int magBoost = Math.max(0, client.getBoostedSkillLevel(Skill.MAGIC) - client.getRealSkillLevel(Skill.MAGIC));

        int currentMax = Math.max(atkBoost, Math.max(strBoost, Math.max(defBoost, Math.max(rngBoost, magBoost))));

        if (currentMax >= 4) {
            buffState = BuffState.ACTIVE;
            buffStatusText = "ACTIVE (+" + currentMax + ")";
            timesBuffExpiredSounded = 0;
        } else if (currentMax == 0) {
            if (buffState == BuffState.ACTIVE) {
                buffState = BuffState.EXPIRED;
                buffStatusText = "EXPIRED (Re-pot)";

                boolean canSound = false;
                if (timesBuffExpiredSounded == 0) {
                    timesBuffExpiredSounded = 1;
                    lastBuffExpiredSoundTime = Instant.now();
                    canSound = true;
                    sendGameChatMessage("<col=ff0000>Alert: Combat potion buff has expired! Re-pot now.</col>");
                } else if (config.repeatBuffAlert()) {
                    long delay = Duration.between(lastBuffExpiredSoundTime, Instant.now()).getSeconds();
                    if (delay >= config.repeatBuffInterval() && timesBuffExpiredSounded < config.maxBuffRepeats()) {
                        timesBuffExpiredSounded++;
                        lastBuffExpiredSoundTime = Instant.now();
                        canSound = true;
                        sendGameChatMessage("<col=ff0000>Reminder: Combat potion buff is still expired! Re-pot.</col>");
                    }
                }

                if (canSound && config.voiceBuffExpired()) {
                    voiceService.speakAsync("Combat buff has expired. Re-pot!", config.voiceGender());
                }
            } else if (buffState == BuffState.UNPOTTED) {
                buffStatusText = "No Buffs";
            }
        }
        previousMaxBoost = currentMax;
    }

    private void monitorPrayer() {
        int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
        boolean isZero = (currentPrayer <= 0);

        if (isZero && !zeroPrayerAlert) {
            zeroPrayerAlert = true;
            lowPrayerAlert = false;
            if (config.voicePrayerLow()) {
                voiceService.speakAsync("Out of prayer points!", config.voiceGender());
            }
            sendGameChatMessage("<col=ff0000>Alert: Out of prayer points!</col>");
            return;
        } else if (!isZero) {
            zeroPrayerAlert = false;
        }

        if (!isZero && config.enableLowPrayerAlert() && currentPrayer <= config.lowPrayerThreshold()) {
            lowPrayerAlert = true;

            boolean canSound = false;
            if (timesPrayerAlertSounded == 0) {
                timesPrayerAlertSounded = 1;
                lastPrayerAlertTime = Instant.now();
                canSound = true;
            } else if (config.repeatPrayerAlert()) {
                long delay = Duration.between(lastPrayerAlertTime, Instant.now()).getSeconds();
                if (delay >= config.repeatPrayerInterval() && timesPrayerAlertSounded < config.maxPrayerRepeats()) {
                    timesPrayerAlertSounded++;
                    lastPrayerAlertTime = Instant.now();
                    canSound = true;
                }
            }

            if (canSound) {
                if (config.voicePrayerLow()) {
                    voiceService.speakAsync("Prayer points low.", config.voiceGender());
                }
                sendGameChatMessage("<col=ff3333>Warning: Prayer points low (" + currentPrayer + " remaining)!</col>");
            }
        } else if (currentPrayer > config.lowPrayerThreshold()) {
            lowPrayerAlert = false;
            timesPrayerAlertSounded = 0;
        }
    }

    private void updateIdleState() {
        int idleCycles = Math.min(client.getKeyboardIdleTicks(), client.getMouseIdleTicks());
        engineIdleSeconds = idleCycles / 50;

        boolean imminent = engineIdleSeconds >= IDLE_WARNING_SECONDS;
        if (imminent && !logoutImminent) {
            notifier.notify("Brad's Multi-Tool: You are about to be logged out for inactivity!");
            sendGameChatMessage("<col=ff0000>Warning: Idle logout imminent! Move the mouse or press a key.</col>");
        }
        logoutImminent = imminent;
    }

    public int getSecondsUntilIdleLogout() {
        return Math.max(0, IDLE_LOGOUT_SECONDS - engineIdleSeconds);
    }

    private void updateGeneralPvmTarget(Player player) {
        Actor target = player.getInteracting();

        NPC attackingNpc = null;
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc != null && npc.getInteracting() == player && !npc.isDead()) {
                String name = npc.getName() != null ? npc.getName().toLowerCase() : "";
                if (!name.contains("gemstone crab") && !name.contains("gem crab") && hasAttackOption(npc)) {
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
            String lowerName = name.toLowerCase();

            if (lowerName.contains("gemstone crab") || lowerName.contains("gem crab")) {
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

        combatDiscipline = style.discipline;
        combatMethod = style.label();
        combatMethodIcon = CombatIcons.forStyle(style);

        combatMethodColor = weaknessMatchColor(style);
    }

    // Green = your style hits the weakness, red = it doesn't, yellow = can't tell
    // (weakness unknown, or the ammo type / spell element couldn't be detected).
    private Color weaknessMatchColor(WeaponStyles.Style style) {
        Color match = new Color(50, 230, 50);
        Color mismatch = new Color(255, 75, 75);
        Color unsure = new Color(255, 215, 0);

        MonsterWeakness weakness = pvmTargetWeakness;
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

            String n = npc.getName() != null ? npc.getName().toLowerCase() : "";
            if (npc == activeCrab || n.contains("gemstone crab") || n.contains("gem crab")) {
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
            String name = targetNpc.getName() != null ? targetNpc.getName().toLowerCase() : "";
            if (name.contains("gemstone crab") || name.contains("gem crab")) {
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
