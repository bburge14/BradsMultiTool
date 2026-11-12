package com.snuffys.skilltracker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.eventbus.Subscribe;

/**
 * Your recent kills, for the combat goal cards' kill rate: a monster you hit that dies soon after counts as
 * yours. Potions and better gear show up here as faster kills (the kills a goal needs don't change: combat XP
 * comes from damage, and a kill can only take its hitpoints).
 */
@Singleton
class KillTracker {

    // A monster you hit in the last 30 seconds that dies is your kill
    private static final int HIT_MEMORY_TICKS = 50;
    // The rate is over the last hour of kills
    private static final long WINDOW_MILLIS = 60 * 60_000L;

    /** How fast you're killing a monster: kills an hour and time each, over your recent kills of it. */
    static final class Pace {
        final int perHour;
        final long millisEach;
        final int kills;

        Pace(int perHour, long millisEach, int kills) {
            this.perHour = perHour;
            this.millisEach = millisEach;
            this.kills = kills;
        }
    }

    private static final class Kill {
        final String monster;
        final long millis;
        /** Your XP in each skill when it died (for the XP a kill really gives). */
        final int[] xp;

        Kill(String monster, long millis, int[] xp) {
            this.monster = monster;
            this.millis = millis;
            this.xp = xp;
        }
    }

    private final Client client;
    private final Map<NPC, Integer> lastHit = new HashMap<>();
    private final Deque<Kill> kills = new ArrayDeque<>();
    private volatile String lastKilled;

    @Inject
    KillTracker(Client client) {
        this.client = client;
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getHitsplat().isMine() && event.getActor() instanceof NPC) {
            lastHit.put((NPC) event.getActor(), client.getTickCount());
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (!(event.getActor() instanceof NPC)) {
            return;
        }
        NPC npc = (NPC) event.getActor();
        Integer hit = lastHit.remove(npc);
        if (hit != null && client.getTickCount() - hit <= HIT_MEMORY_TICKS && npc.getName() != null) {
            synchronized (kills) {
                int[] xp = new int[net.runelite.api.Skill.values().length];
                for (net.runelite.api.Skill skill : net.runelite.api.Skill.values()) {
                    xp[skill.ordinal()] = client.getSkillExperience(skill);
                }
                kills.addLast(new Kill(npc.getName(), System.currentTimeMillis(), xp));
                prune();
            }
            lastKilled = npc.getName();
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        lastHit.remove(event.getNpc());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            lastHit.clear();
        }
    }

    /** The tracker's Reset all. */
    void reset() {
        synchronized (kills) {
            kills.clear();
        }
        lastKilled = null;
    }

    /** The monster you killed last, or null. */
    String lastKilled() {
        return lastKilled;
    }

    /** Your pace killing this monster (by name), or null until you've killed it twice in the last hour. */
    Pace pace(String monster) {
        if (monster == null) {
            return null;
        }
        long first = -1;
        long last = -1;
        int count = 0;
        synchronized (kills) {
            prune();
            for (Kill kill : kills) {
                if (kill.monster.equalsIgnoreCase(monster)) {
                    first = first < 0 ? kill.millis : first;
                    last = kill.millis;
                    count++;
                }
            }
        }
        return pace(count, first, last);
    }

    /**
     * The XP a kill of this monster really gives you in a skill, from your recent kills of it (what you gained between
     * the first and the last, over the kills between): your misses, damage and style, and monsters that don't die of
     * their hitpoints (a Gemstone crab burrows after ten minutes) counted as they are. 0 until you've killed it twice.
     */
    double xpPerKill(String monster, net.runelite.api.Skill skill) {
        if (monster == null) {
            return 0;
        }
        Kill first = null;
        Kill last = null;
        int count = 0;
        synchronized (kills) {
            prune();
            for (Kill kill : kills) {
                if (kill.monster.equalsIgnoreCase(monster)) {
                    first = first == null ? kill : first;
                    last = kill;
                    count++;
                }
            }
        }
        return xpPerKill(count, first == null ? 0 : first.xp[skill.ordinal()], last == null ? 0 : last.xp[skill.ordinal()]);
    }

    static double xpPerKill(int count, int firstXp, int lastXp) {
        return count < 2 || lastXp <= firstXp ? 0 : (double) (lastXp - firstXp) / (count - 1);
    }

    static Pace pace(int count, long firstMillis, long lastMillis) {
        if (count < 2 || lastMillis <= firstMillis) {
            return null;
        }
        long each = (lastMillis - firstMillis) / (count - 1);
        return new Pace((int) Math.round(3_600_000.0 / each), each, count);
    }

    private void prune() {
        long oldest = System.currentTimeMillis() - WINDOW_MILLIS;
        for (Iterator<Kill> it = kills.iterator(); it.hasNext(); ) {
            if (it.next().millis < oldest) {
                it.remove();
            } else {
                break;
            }
        }
    }
}
