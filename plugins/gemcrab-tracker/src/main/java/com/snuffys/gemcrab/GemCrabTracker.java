package com.snuffys.gemcrab;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
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
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * The Gemstone Crab (Snuffy's GemCrab Tracker): the crab or its shell nearby, its health and when it will
 * burrow, your damage and DPS on it, which XP you're getting, the tunnels and shells around its three coves,
 * whether you can mine the shell, and the crab-moved and about-to-burrow alerts.
 */
@Slf4j
@Singleton
class GemCrabTracker {

    private static final int CRAB_BURROW_ALERT_DISTANCE = 10;
    // The crab's three mines in the Tlati Rainforest (OSRS Wiki); crab tunnels and shells only count near these
    private static final WorldPoint[] CRAB_SPOTS = {
        new WorldPoint(1275, 3160, 0), new WorldPoint(1351, 3110, 0), new WorldPoint(1237, 3042, 0)};
    private static final String[] CRAB_COVE_NAMES = {"North Cove", "East Cove", "West Cove"};
    private static final int CRAB_AREA_RADIUS = 40;

    // The shell lasts 90 seconds (150 ticks); allow a little slack
    private static final int SHELL_LIFETIME_TICKS = 160;
    private static final int GEMS_PER_SHELL = 3;

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
    private String crabCombatXpType = "Strength";

    /** The skills behind crabCombatXpType, for showing their icons. */
    @Getter
    private List<Skill> crabCombatXpSkills = Collections.singletonList(Skill.STRENGTH);


    @Getter
    private String targetCrabHp = "No Target";

    @Getter
    private String estimatedBurrowTime = "--:--";

    @Getter
    private boolean inSpawnVicinity = false;

    @Getter
    private String currentCoveName = "Cavern Complex";

    @Getter
    private boolean playerAttackingCrab = false;


    private Instant fightStartTime = null;
    private int initialHealthRatio = -1;

    private final Set<Skill> tickXpChanges = EnumSet.noneOf(Skill.class);

    // Each skill's XP, for spotting which ones gained this tick
    private final java.util.Map<Skill, Integer> lastXp = new java.util.EnumMap<>(Skill.class);

    private final Client client;
    private final GemCrabConfig config;
    private final Alerts alerts;

    @Inject
    GemCrabTracker(Client client, GemCrabConfig config, Alerts alerts) {
        this.client = client;
        this.config = config;
        this.alerts = alerts;
    }

    /** Forget everything (logging out, or the plugin stopping). */
    void reset() {
        activeCrab = null;
        dormantCrabShellNpc = null;
        trackedShellObjects.clear();
        trackedTunnels.clear();
        inSpawnVicinity = false;
        wasDirectlyTrackingCrab = false;
        playerAttackingCrab = false;
        resetFightTracking();
    }

    /** The tracker's Reset all: your damage and DPS on the crab. */
    void resetSession() {
        currentCrabDamage = 0;
        crabLiveDps = 0.0;
        crabFightStartTime = null;
        resetFightTracking();
    }

    private void resetFightTracking() {
        fightStartTime = null;
        initialHealthRatio = -1;
        estimatedBurrowTime = "--:--";
        crabFightStartTime = null;
        currentCrabDamage = 0;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            reset();
            targetCrabHp = "Logged Out";
        }
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
                alerts.alert(VoiceAlertService.VoiceLine.CRAB_MOVED, null);
            }
            wasDirectlyTrackingCrab = false;
        }
        if (npc == dormantCrabShellNpc) {
            dormantCrabShellNpc = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        // Which combat XP came in this tick
        resolveXpTypeFromTickDrops();
        tickXpChanges.clear();

        Player player = client.getLocalPlayer();
        if (player != null) {
            WorldPoint wp = player.getWorldLocation();
            scanForCrab();
            updateSpatialPresence(player, wp);
            updateCrabHealth();
            updateTargetCrabStatus(player);
            if (crabFightStartTime != null && isCrabActive() && playerAttackingCrab) {
                long elapsed = Math.max(1, Duration.between(crabFightStartTime, Instant.now()).getSeconds());
                crabLiveDps = (double) currentCrabDamage / elapsed;
            }
        }
        calculateBurrowCountdown();
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        Integer before = lastXp.put(event.getSkill(), event.getXp());
        if (before != null && event.getXp() > before) {
            tickXpChanges.add(event.getSkill());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!(event.getActor() instanceof NPC) || !event.getHitsplat().isMine()) {
            return;
        }
        NPC npc = (NPC) event.getActor();
        int dmg = event.getHitsplat().getAmount();
        if (npc == activeCrab || GemstoneCrabs.classify(npc) == GemstoneCrabs.CrabForm.ACTIVE) {
            activeCrab = npc;
            playerAttackingCrab = true;
            wasDirectlyTrackingCrab = true;
            currentCrabDamage += dmg;
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
    }

    public boolean isCrabActive() {
        return activeCrab != null && !activeCrab.isDead();
    }

    public boolean isCrabBurrowed() {
        return !isCrabActive() || dormantCrabShellNpc != null || !trackedShellObjects.isEmpty();
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
        switch (GemstoneCrabs.classify(npc)) {
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
            alerts.alert(VoiceAlertService.VoiceLine.CRAB_LOW_HEALTH, crabHpPercent + "% health");
        }
    }

    // Spawn events can be missed (e.g. arriving through a cave reloads the scene and clears tracking),
    // so re-check the NPCs around the player every tick.
    private void scanForCrab() {
        NPC crab = null;
        NPC shell = null;
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            GemstoneCrabs.CrabForm form = GemstoneCrabs.classify(npc);
            if (form == GemstoneCrabs.CrabForm.ACTIVE && !npc.isDead()) {
                crab = npc;
            } else if (form == GemstoneCrabs.CrabForm.SHELL) {
                shell = npc;
            }
        }

        if (crab != null) {
            setActiveCrab(crab);
        } else if (shell != null) {
            dormantCrabShellNpc = shell;
        }
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
        // Tunnels and shells elsewhere (Draynor sewers, say) have nothing to do with the crab
        if (obj == null || crabSpotNear(obj.getWorldLocation()) < 0) return;

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

        if (crabSpotNear(wp) < 0) {
            inSpawnVicinity = false;
            return;
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
        int spot = crabSpotNear(wp);
        if (spot >= 0) {
            currentCoveName = CRAB_COVE_NAMES[spot];
        }
    }

    /** Index of the crab mine within range of {@code wp}, or -1 when it's nowhere near one. */
    private static int crabSpotNear(WorldPoint wp) {
        if (wp == null) {
            return -1;
        }
        for (int i = 0; i < CRAB_SPOTS.length; i++) {
            if (wp.getPlane() == CRAB_SPOTS[i].getPlane() && wp.distanceTo2D(CRAB_SPOTS[i]) <= CRAB_AREA_RADIUS) {
                return i;
            }
        }
        return -1;
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

    private void updateTargetCrabStatus(Player player) {
        Actor target = player.getInteracting();
        boolean interactingWithCrab = false;

        if (target instanceof NPC) {
            NPC targetNpc = (NPC) target;
            if (GemstoneCrabs.classify(targetNpc) == GemstoneCrabs.CrabForm.ACTIVE) {
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

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        evaluateNpc(event.getNpc());
    }



}
