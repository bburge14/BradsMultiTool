package com.bradsmultitool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Two reminders, both off by default:
 * - Wrong protection prayer: a monster attacking you only attacks one way (the wiki's attack styles, all stopped by
 *   one prayer), and the protection prayer you have on is a different one. Once per monster and prayer. Static wiki
 *   facts only: nothing about when or how it attacks next.
 * - Unpotted: you start a fight with none of the boosts that count (any combat stat, or the one your style uses)
 *   above your level. At most once every two minutes.
 */
@Singleton
class CombatChecks {

    // A fight starts after this long without one (a minute), and the unpotted reminder waits this long to repeat
    private static final int FIGHT_GAP_TICKS = 100;
    private static final int UNPOTTED_REPEAT_TICKS = 200;

    private final Client client;
    private final PvmConfig config;
    private final Alerts alerts;
    private final MonsterData monsterData;
    private final CombatStyle combatStyle;
    // Monster (by index) -> the prayer you had on when we last said it was wrong
    private final Map<Integer, String> warned = new HashMap<>();
    private int lastFightTick = -1000;
    private int lastUnpottedTick = -1000;

    @Inject
    CombatChecks(Client client, PvmConfig config, Alerts alerts, MonsterData monsterData, CombatStyle combatStyle) {
        this.client = client;
        this.config = config;
        this.alerts = alerts;
        this.monsterData = monsterData;
        this.combatStyle = combatStyle;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            warned.clear();
            lastFightTick = -1000;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN || player == null) {
            return;
        }
        if (config.wrongPrayerAlert()) {
            checkPrayer(player);
        }
        checkUnpotted(player);
    }

    private void checkPrayer(Player player) {
        String active = activeProtection();
        Set<Integer> attacking = new HashSet<>();
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc == null || npc.isDead() || npc.getInteracting() != player || GemstoneCrabs.isGemstoneCrab(npc)) {
                continue;
            }
            attacking.add(npc.getIndex());
            MonsterData.Stats stats = monsterData.statsFor(npc);
            String needed = stats != null ? stats.onlyProtection() : null;
            // No protection prayer on is a choice (and the norm), so only a wrong one is said
            if (active == null || needed == null || needed.equals(active) || active.equals(warned.get(npc.getIndex()))) {
                continue;
            }
            warned.put(npc.getIndex(), active);
            alerts.alert(VoiceAlertService.VoiceLine.WRONG_PRAYER, npc.getName() + " attacks with "
                + ("Missiles".equals(needed) ? "ranged" : needed.toLowerCase()) + ": pray Protect from " + needed);
        }
        warned.keySet().retainAll(attacking);
    }

    /** "Melee", "Missiles" or "Magic": the protection prayer on now, or null for none. */
    private String activeProtection() {
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)) {
            return "Melee";
        }
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)) {
            return "Missiles";
        }
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC)) {
            return "Magic";
        }
        return null;
    }

    private void checkUnpotted(Player player) {
        Actor target = player.getInteracting();
        boolean fighting = target instanceof NPC && isMonster((NPC) target) && !GemstoneCrabs.isGemstoneCrab((NPC) target);
        int tick = client.getTickCount();
        boolean starting = fighting && tick - lastFightTick > FIGHT_GAP_TICKS;
        if (fighting) {
            lastFightTick = tick;
        }
        if (!starting || !config.unpottedAlert() || tick - lastUnpottedTick < UNPOTTED_REPEAT_TICKS) {
            return;
        }
        WeaponStyles.Style style = combatStyle.getCurrentStyle();
        if (!boosted(config.unpottedBoosts(), style != null ? style.type() : null)) {
            lastUnpottedTick = tick;
            alerts.alert(VoiceAlertService.VoiceLine.UNPOTTED, ((NPC) target).getName());
        }
    }

    // Any boost that counts: any combat stat, or (Your Style's) Attack or Strength for melee, Ranged, or Magic
    private boolean boosted(PvmConfig.UnpottedBoosts which, MonsterWeakness.Type styleType) {
        Skill[] skills;
        if (which == PvmConfig.UnpottedBoosts.YOUR_STYLE && styleType == MonsterWeakness.Type.RANGED) {
            skills = new Skill[]{Skill.RANGED};
        } else if (which == PvmConfig.UnpottedBoosts.YOUR_STYLE && styleType == MonsterWeakness.Type.MAGIC) {
            skills = new Skill[]{Skill.MAGIC};
        } else if (which == PvmConfig.UnpottedBoosts.YOUR_STYLE && styleType == MonsterWeakness.Type.MELEE) {
            skills = new Skill[]{Skill.ATTACK, Skill.STRENGTH};
        } else {
            skills = PotionBuffs.BOOSTABLE_COMBAT_SKILLS.toArray(new Skill[0]);
        }
        for (Skill skill : skills) {
            if (client.getBoostedSkillLevel(skill) > client.getRealSkillLevel(skill)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMonster(NPC npc) {
        net.runelite.api.NPCComposition comp = npc.getTransformedComposition() != null ? npc.getTransformedComposition() : npc.getComposition();
        if (comp == null || comp.getActions() == null) {
            return false;
        }
        for (String action : comp.getActions()) {
            if ("Attack".equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }
}
