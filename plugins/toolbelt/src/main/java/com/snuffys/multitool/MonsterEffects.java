package com.snuffys.multitool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Debuffs on monsters for the PvM HUD, from what the game shows: poisoned, venomed or burning (their
 * hitsplats), frozen (a freeze spell's impact, for that spell's length), and Defence lowered by your special
 * attacks (Dragon warhammer, Elder maul, Bandos godsword, Arclight/Emberlight), estimated from the wiki's
 * Defence level. Only reads the game.
 */
@Singleton
class MonsterEffects {

    private static final int VARP_SPEC_ENERGY = 300;
    // Poison and venom hit every 30 ticks, burns every few: still on if one landed this recently
    private static final int POISON_WINDOW_TICKS = 36;
    private static final int BURN_WINDOW_TICKS = 8;
    // A special attack's hit lands within this many ticks of the energy going
    private static final int SPEC_HIT_TICKS = 3;

    /** Freeze spells' impact graphics and how long each freezes for, in ticks. */
    private static final int[][] FREEZES = {
        {SpotanimID.ICE_RUSH_IMPACT, 8}, {SpotanimID.ICE_BURST_IMPACT, 16}, {SpotanimID.ICE_BLITZ_IMPACT, 24},
        {SpotanimID.ICE_BARRAGE_IMPACT, 32}, {SpotanimID.BIND_IMPACT, 8}, {SpotanimID.SNARE_IMPACT, 16},
        {SpotanimID.ENTANGLE_IMPACT, 24},
    };

    private static final class State {
        int poisonTick = -1000;
        int venomTick = -1000;
        int burnTick = -1000;
        int frozenUntil = -1;
        /** Defence after your drains, or -1 when not lowered. */
        int defence = -1;
    }

    private final Client client;
    private final MonsterData monsterData;
    private final CombatStyle combatStyle;
    private final Map<Integer, State> byIndex = new HashMap<>();
    private int lastSpecEnergy = -1;
    private int specTick = -1000;
    private int specWeapon = -1;

    @Inject
    MonsterEffects(Client client, MonsterData monsterData, CombatStyle combatStyle) {
        this.client = client;
        this.monsterData = monsterData;
        this.combatStyle = combatStyle;
    }

    /** "Venomed", "Frozen 0:12"...: what's on the monster now. */
    List<String> statuses(NPC npc) {
        List<String> out = new ArrayList<>();
        State state = byIndex.get(npc.getIndex());
        if (state == null) {
            return out;
        }
        int tick = client.getTickCount();
        if (tick - state.venomTick <= POISON_WINDOW_TICKS) {
            out.add("Venomed");
        } else if (tick - state.poisonTick <= POISON_WINDOW_TICKS) {
            out.add("Poisoned");
        }
        if (tick - state.burnTick <= BURN_WINDOW_TICKS) {
            out.add("Burning");
        }
        if (state.frozenUntil > tick) {
            out.add("Frozen " + BuffTimer.format(state.frozenUntil - tick));
        }
        return out;
    }

    /** "112 (-48)" when your special attacks have lowered its Defence, else null. */
    String defence(NPC npc) {
        State state = byIndex.get(npc.getIndex());
        MonsterData.Stats stats = monsterData.statsFor(npc);
        if (state == null || state.defence < 0 || stats == null || stats.defenceLevel == null) {
            return null;
        }
        return state.defence + " (-" + (stats.defenceLevel - state.defence) + ")";
    }

    /** Its Defence after a special attack from this weapon landed for this much (the wiki's rules). */
    static int drained(int weapon, int defence, int baseDefence, int damage, boolean demon) {
        switch (weapon) {
            case ItemID.DRAGON_WARHAMMER:
            case ItemID.DRAGON_WARHAMMER_ORNAMENT:
                return damage > 0 ? defence - defence * 30 / 100 : defence;
            case ItemID.ELDER_MAUL:
            case ItemID.ELDER_MAUL_ORNAMENT:
                return damage > 0 ? defence - defence * 35 / 100 : defence;
            case ItemID.BGS:
            case ItemID.BGSG:
                return Math.max(0, defence - damage);
            case ItemID.ARCLIGHT:
            case ItemID.EMBERLIGHT:
                return damage > 0 ? Math.max(0, defence - (baseDefence * (demon ? 10 : 5) / 100 + 1)) : defence;
            default:
                return defence;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarpId() != VARP_SPEC_ENERGY) {
            return;
        }
        if (lastSpecEnergy >= 0 && event.getValue() < lastSpecEnergy) {
            specTick = client.getTickCount();
            specWeapon = combatStyle.equippedWeaponId();
        }
        lastSpecEnergy = event.getValue();
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC)) {
            return;
        }
        NPC npc = (NPC) actor;
        State state = byIndex.computeIfAbsent(npc.getIndex(), i -> new State());
        int tick = client.getTickCount();
        switch (event.getHitsplat().getHitsplatType()) {
            case HitsplatID.POISON:
                state.poisonTick = tick;
                return;
            case HitsplatID.VENOM:
                state.venomTick = tick;
                return;
            case HitsplatID.BURN:
                state.burnTick = tick;
                return;
            default:
                break;
        }
        if (event.getHitsplat().isMine() && tick - specTick <= SPEC_HIT_TICKS) {
            specTick = -1000;
            MonsterData.Stats stats = monsterData.statsFor(npc);
            if (stats != null && stats.defenceLevel != null) {
                int before = state.defence >= 0 ? state.defence : stats.defenceLevel;
                int after = drained(specWeapon, before, stats.defenceLevel, event.getHitsplat().getAmount(),
                    stats.attributes.contains("demon"));
                if (after < before) {
                    state.defence = after;
                }
            }
        }
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {
        if (!(event.getActor() instanceof NPC)) {
            return;
        }
        Actor actor = event.getActor();
        for (int[] freeze : FREEZES) {
            if (actor.hasSpotAnim(freeze[0])) {
                byIndex.computeIfAbsent(((NPC) actor).getIndex(), i -> new State()).frozenUntil = client.getTickCount() + freeze[1];
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        byIndex.remove(event.getNpc().getIndex());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            byIndex.clear();
            lastSpecEnergy = -1;
        }
    }
}
