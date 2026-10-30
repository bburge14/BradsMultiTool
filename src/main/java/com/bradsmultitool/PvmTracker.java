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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
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

/**
 * Snuffy's PvM/Slayer: the monster you're fighting (health, damage, DPS, weakness, aggression and its
 * timer), your potion buffs and boosts with their running-out and expired alerts, low health and prayer, and
 * the Slayer card's show/hide hotkey.
 */
@Slf4j
@Singleton
public class PvmTracker {

    private static final int AGGRO_DURATION_SECONDS = 600;

    private static final int CONTAINER_EQUIPMENT = 94;
    private static final int INSIDE_WILDERNESS_VARBIT = 5963;
    // Repeat counter value meaning "already handled, don't sound or repeat"
    private static final int SILENCED = Integer.MAX_VALUE / 2;


    private final Client client;
    private final PvmConfig config;
    private final ConfigManager configManager;
    private final ClientThread clientThread;
    private final PluginIdentity identity;
    private final Alerts alerts;
    private final KeyManager keyManager;
    private final MonsterData monsterData;
    private final CombatStyle combatStyle;
    @Getter
    private final SlayerTracker slayerTracker;

    /** Toggled by the slayer show/hide hotkey; kept in the settings so it stays that way across restarts. */
    @Getter
    private volatile boolean slayerCardHidden = false;
    private static final String SLAYER_CARD_HIDDEN = "slayerCardHidden";

    private final HotkeyListener slayerToggleListener;

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
    private final PotionAlerts potionAlerts;
    @Inject
    PvmTracker(Client client, PvmConfig config, ConfigManager configManager, ClientThread clientThread, PluginIdentity identity,
               Alerts alerts, KeyManager keyManager, MonsterData monsterData, CombatStyle combatStyle, SlayerTracker slayerTracker,
               PotionAlerts potionAlerts) {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.clientThread = clientThread;
        this.identity = identity;
        this.alerts = alerts;
        this.keyManager = keyManager;
        this.monsterData = monsterData;
        this.combatStyle = combatStyle;
        this.slayerTracker = slayerTracker;
        this.potionAlerts = potionAlerts;
        slayerToggleListener = new HotkeyListener(config::slayerToggleKey) {
            @Override
            public void hotkeyPressed() {
                slayerCardHidden = !slayerCardHidden;
                configManager.setConfiguration(identity.configGroup, SLAYER_CARD_HIDDEN, slayerCardHidden);
                // Say what happened, since without a task there's no card to see change
                String state = slayerCardHidden ? "hidden" : slayerTracker.hadTask() ? "shown" : "shown (no Slayer task right now)";
                clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=00ffff>[" + identity.chatName + "]</col> Slayer card " + state, null));
            }
        };
    }

    void startUp() {
        slayerCardHidden = Boolean.parseBoolean(configManager.getConfiguration(identity.configGroup, SLAYER_CARD_HIDDEN));
        keyManager.registerKeyListener(slayerToggleListener);
    }

    void shutDown() {
        keyManager.unregisterKeyListener(slayerToggleListener);
        currentPvmTarget = null;
        pvmOverlayActive = false;
    }

    /** The PvM HUD's Reset (its right-click menu): this session's damage and DPS. */
    void resetSession() {
        sessionPvmDamage = 0;
        sessionPvmActiveSeconds = 0;
        sessionCombatSecondMarker = null;
        pvmLiveDps = 0.0;
        pvmOverlayActive = false;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            slayerTracker.reset();
        }
        if (event.getGameState() != GameState.LOGGED_IN) {
            currentPvmTarget = null;
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

    // The potion buff state the PvM HUD shows (PotionAlerts, shared with the GemCrab Tracker)
    PotionAlerts.BuffState getBuffState() {
        return potionAlerts.getBuffState();
    }

    List<ActiveBuffs.Buff> getActiveBuffs() {
        return potionAlerts.getActiveBuffs();
    }

    String getBuffStatusText() {
        return potionAlerts.getBuffStatusText();
    }

    boolean isBuffRunningOut(ActiveBuffs.Buff buff) {
        return potionAlerts.isBuffRunningOut(buff);
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

        if (canSound && !alerts.ready()) {
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
            if (timesPrayerAlertSounded == 0 && !alerts.ready()) {
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

                if (config.dpsMode() == PvmConfig.DpsTrackingMode.PER_ENCOUNTER) {
                    pvmFightStart = Instant.now();
                    pvmEncounterDamage = 0;
                } else if (config.dpsMode() == PvmConfig.DpsTrackingMode.PER_TARGET_TYPE && isNewType) {
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
                if (config.dpsMode() == PvmConfig.DpsTrackingMode.CONTINUOUS_SESSION) {
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
}
