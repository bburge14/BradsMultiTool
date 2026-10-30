package com.snuffys.pvm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * Your buffs and debuffs for the buff bar, like RS3's: potion boosts, antifire, stamina, poison protection and
 * other boosted stats; poison, venom and drained stats. Timers count down from when the game last set them (the
 * same units as RuneLite's Timers and Buffs plugin). Only reads the game.
 */
@Singleton
class StatusEffects {

    /** One icon on the bar. */
    static final class Effect {
        final String name;
        /** An item's picture (itemId > 0), else the skill's icon. */
        final int itemId;
        final Skill skill;
        /** Small text in the corner ("+19", "-5"), or null. */
        final String amount;
        /** When it runs out (System.currentTimeMillis), 0 when it doesn't have a timer. */
        final long endMillis;
        final boolean exact;
        final boolean debuff;

        Effect(String name, int itemId, Skill skill, String amount, long endMillis, boolean exact, boolean debuff) {
            this.name = name;
            this.itemId = itemId;
            this.skill = skill;
            this.amount = amount;
            this.endMillis = endMillis;
            this.exact = exact;
            this.debuff = debuff;
        }
    }

    // Game variables and their units in ticks (RuneLite's Timers and Buffs plugin)
    private static final int VARBIT_STAMINA_DURATION = 24;   // 10 ticks each
    private static final int VARBIT_ANTIFIRE = 3981;         // 30 ticks each
    private static final int VARBIT_SUPER_ANTIFIRE = 6101;   // 20 ticks each
    private static final int VARP_POISON = 102;              // < 0: protection, 30 ticks each; > 0 poisoned
    private static final int VENOM_PROTECTION_CUTOFF = -38;  // below this, antivenom
    private static final int VENOMED = 1_000_000;            // from this up, venom

    // Pictures: each potion's 4-dose item
    private static final int STAMINA_POTION = 12625;
    private static final int ANTIFIRE_POTION = 2452;
    private static final int SUPER_ANTIFIRE_POTION = 21978;
    private static final int ANTIPOISON = 2446;
    private static final int ANTIVENOM = 12913;
    private static final int WEAPON_POISON = 187;
    private static final int ZULRAHS_SCALES = 12934;

    private final Client client;
    private final PotionAlerts potionAlerts;
    private long staminaEnd;
    private long antifireEnd;
    private long superAntifireEnd;
    private long protectionEnd;
    private boolean venomProtection;
    private List<Effect> effects = Collections.emptyList();

    @Inject
    StatusEffects(Client client, PotionAlerts potionAlerts) {
        this.client = client;
        this.potionAlerts = potionAlerts;
    }

    /** This tick's buffs, then debuffs. */
    List<Effect> effects() {
        return effects;
    }

    static long endOf(int value, int ticksEach) {
        return value > 0 ? System.currentTimeMillis() + value * ticksEach * 600L : 0;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == VARBIT_STAMINA_DURATION) {
            staminaEnd = endOf(event.getValue(), 10);
        } else if (event.getVarbitId() == VARBIT_ANTIFIRE) {
            antifireEnd = endOf(event.getValue(), 30);
        } else if (event.getVarbitId() == VARBIT_SUPER_ANTIFIRE) {
            superAntifireEnd = endOf(event.getValue(), 20);
        } else if (event.getVarpId() == VARP_POISON) {
            int value = event.getValue();
            venomProtection = value < VENOM_PROTECTION_CUTOFF;
            protectionEnd = value < 0 ? endOf(venomProtection ? -value + VENOM_PROTECTION_CUTOFF : -value, 30) : 0;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            staminaEnd = antifireEnd = superAntifireEnd = protectionEnd = 0;
            effects = Collections.emptyList();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Effect> buffs = new ArrayList<>();
        List<Effect> debuffs = new ArrayList<>();

        for (ActiveBuffs.Buff buff : potionAlerts.getActiveBuffs()) {
            buffs.add(new Effect(buff.name, buff.iconItemId, null, "+" + buff.boost, buff.endMillis, buff.exact, false));
        }
        addTimed(buffs, "Super antifire", SUPER_ANTIFIRE_POTION, superAntifireEnd, now);
        if (superAntifireEnd <= now) {
            addTimed(buffs, "Antifire", ANTIFIRE_POTION, antifireEnd, now);
        }
        addTimed(buffs, "Stamina", STAMINA_POTION, staminaEnd, now);
        addTimed(buffs, venomProtection ? "Antivenom" : "Antipoison", venomProtection ? ANTIVENOM : ANTIPOISON, protectionEnd, now);

        int poison = client.getVarpValue(VARP_POISON);
        if (poison >= VENOMED) {
            debuffs.add(new Effect("Venomed", ZULRAHS_SCALES, null, null, 0, true, true));
        } else if (poison > 0) {
            debuffs.add(new Effect("Poisoned", WEAPON_POISON, null, null, 0, true, true));
        }

        // Stats above or below your level that the potion buffs don't already show (Hitpoints from an anglerfish,
        // a drained Defence...). Prayer and Hitpoints going down are just being used, not debuffs
        for (Skill skill : Skill.values()) {
            int change = client.getBoostedSkillLevel(skill) - client.getRealSkillLevel(skill);
            if (change > 0 && !PotionBuffs.BOOSTABLE_COMBAT_SKILLS.contains(skill)) {
                buffs.add(new Effect(skill.getName() + " boosted", 0, skill, "+" + change, 0, true, false));
            } else if (change > 0 && potionAlerts.getActiveBuffs().stream().noneMatch(b -> b.skills.contains(skill))) {
                buffs.add(new Effect(skill.getName() + " boosted", 0, skill, "+" + change, 0, true, false));
            } else if (change < 0 && skill != Skill.PRAYER && skill != Skill.HITPOINTS) {
                debuffs.add(new Effect(skill.getName() + " drained", 0, skill, String.valueOf(change), 0, true, true));
            }
        }
        buffs.addAll(debuffs);
        effects = buffs;
    }

    private static void addTimed(List<Effect> list, String name, int itemId, long end, long now) {
        if (end > now) {
            list.add(new Effect(name, itemId, null, null, end, true, false));
        }
    }
}
