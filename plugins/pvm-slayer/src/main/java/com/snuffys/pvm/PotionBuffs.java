package com.snuffys.pvm;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * Your combat boosts and the potion buffs behind them, each with its time left (ActiveBuffs): divine potions from
 * the game's own countdown, regular ones from the boost decay timer. Read by PvM (its HUD and buff alerts) and the
 * GemCrab Tracker (its crab card), so each plugin has them on its own.
 */
@Singleton
class PotionBuffs {

    static final Set<Skill> BOOSTABLE_COMBAT_SKILLS =
        EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);
    // Divine potion time left in game ticks (VarbitID.DIVINEATTACK/STRENGTH/DEFENCE/RANGE/MAGIC_POTION_TIME)
    private static final int[] DIVINE_POTION_TIME_VARBITS = {8429, 8430, 8431, 8432, 8433};
    private static final Skill[] DIVINE_POTION_SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC};

    private final Client client;
    private final Map<Skill, Long> divineEndMillis = new EnumMap<>(Skill.class);
    private final BuffTimer buffTimer = new BuffTimer();
    private final Map<Skill, Integer> lastBoostedLevels = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> boosts = new EnumMap<>(Skill.class);
    private List<ActiveBuffs.Buff> buffs = Collections.emptyList();
    private int maxBoost;
    private int updatedTick = -1;

    @Inject
    PotionBuffs(Client client) {
        this.client = client;
    }

    /** The biggest boost on a combat stat this tick (0 when none). */
    int maxBoost() {
        update();
        return maxBoost;
    }

    /** The potion buffs active this tick, each with its end time. */
    List<ActiveBuffs.Buff> buffs() {
        update();
        return buffs;
    }

    /** Reads this tick's boosts (once a tick, whoever asks first: PvM's tick or this one's). */
    void update() {
        if (client.getGameState() != GameState.LOGGED_IN || updatedTick == client.getTickCount()) {
            return;
        }
        updatedTick = client.getTickCount();
        long tickMillis = System.currentTimeMillis();
        for (Skill skill : BOOSTABLE_COMBAT_SKILLS) {
            boosts.put(skill, Math.max(0, client.getBoostedSkillLevel(skill) - client.getRealSkillLevel(skill)));
        }
        maxBoost = Collections.max(boosts.values());
        if (maxBoost == 0) {
            buffs = Collections.emptyList();
            return;
        }
        syncDivineTimers();
        boolean preserve = client.getVarbitValue(Prayer.PRESERVE.getVarbit()) == 1;
        buffs = ActiveBuffs.detect(boosts, divineEndMillis, buffTimer, client.getTickCount(), tickMillis, preserve);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        update();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // The boost decay timer keeps running across area loads; only a new session resets it
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            buffTimer.reset();
            lastBoostedLevels.clear();
            divineEndMillis.clear();
            buffs = Collections.emptyList();
            maxBoost = 0;
        }
    }

    // Catch divine potions already running when the plugin starts; changes arrive via onVarbitChanged
    private void syncDivineTimers() {
        for (int i = 0; i < DIVINE_POTION_SKILLS.length; i++) {
            int ticks = client.getVarbitValue(DIVINE_POTION_TIME_VARBITS[i]);
            if (ticks <= 0) {
                divineEndMillis.remove(DIVINE_POTION_SKILLS[i]);
            } else {
                divineEndMillis.putIfAbsent(DIVINE_POTION_SKILLS[i], System.currentTimeMillis() + ticks * 600L);
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

    @Subscribe
    public void onStatChanged(StatChanged event) {
        // A boosted combat stat ticking down by 1 marks the boost decay timer's phase
        Skill skill = event.getSkill();
        if (BOOSTABLE_COMBAT_SKILLS.contains(skill)) {
            Integer previous = lastBoostedLevels.put(skill, event.getBoostedLevel());
            if (previous != null && event.getBoostedLevel() == previous - 1 && previous > event.getLevel()) {
                buffTimer.onDecay(client.getTickCount());
            }
        }
    }
}
