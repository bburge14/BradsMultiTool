package com.snuffys.pvm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Works out which combat buffs are active from which stats are boosted (and, for divine potions, which
 * have a game timer running), so each can be shown with its own name, potion icon and time left.
 * Doses aren't visible, so e.g. a Strength and a Super strength potion both show as "Strength".
 */
final class ActiveBuffs {

    /** What a buff boosts, for picking its voice line. */
    enum Category {
        MELEE,
        RANGED,
        MAGIC,
        DEFENCE
    }

    static final class Buff {
        final String name;
        final Category category;
        final int iconItemId;
        final List<Skill> skills;
        final int boost;
        /** Wall-clock time the boost runs out. */
        final long endMillis;
        final boolean exact;

        Buff(String name, Category category, int iconItemId, List<Skill> skills, int boost, long endMillis, boolean exact) {
            this.name = name;
            this.category = category;
            this.iconItemId = iconItemId;
            this.skills = skills;
            this.boost = boost;
            this.endMillis = endMillis;
            this.exact = exact;
        }

        long millisLeft(long now) {
            return Math.max(0, endMillis - now);
        }
    }

    private static final class Kind {
        final Set<Skill> skills;
        final String name;
        final Category category;
        final int iconItemId;

        Kind(String name, Category category, int iconItemId, Skill... skills) {
            this.skills = EnumSet.copyOf(Arrays.asList(skills));
            this.name = name;
            this.category = category;
            this.iconItemId = iconItemId;
        }
    }

    // Multi-stat potions first so their stats aren't split into single buffs; icons are the (4) doses
    private static final List<Kind> REGULAR = Arrays.asList(
        new Kind("Super Combat", Category.MELEE, 12695, Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE),
        new Kind("Bastion", Category.RANGED, 22461, Skill.RANGED, Skill.DEFENCE),
        new Kind("Battlemage", Category.MAGIC, 22449, Skill.MAGIC, Skill.DEFENCE),
        new Kind("Combat", Category.MELEE, 9739, Skill.ATTACK, Skill.STRENGTH),
        new Kind("Attack", Category.MELEE, 2436, Skill.ATTACK),
        new Kind("Strength", Category.MELEE, 2440, Skill.STRENGTH),
        new Kind("Defence", Category.DEFENCE, 2442, Skill.DEFENCE),
        new Kind("Ranging", Category.RANGED, 2444, Skill.RANGED),
        new Kind("Magic", Category.MAGIC, 3040, Skill.MAGIC));

    private static final List<Kind> DIVINE = Arrays.asList(
        new Kind("Divine Super Combat", Category.MELEE, 23685, Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE),
        new Kind("Divine Bastion", Category.RANGED, 24635, Skill.RANGED, Skill.DEFENCE),
        new Kind("Divine Battlemage", Category.MAGIC, 24623, Skill.MAGIC, Skill.DEFENCE),
        new Kind("Divine Attack", Category.MELEE, 23697, Skill.ATTACK),
        new Kind("Divine Strength", Category.MELEE, 23709, Skill.STRENGTH),
        new Kind("Divine Defence", Category.DEFENCE, 23721, Skill.DEFENCE),
        new Kind("Divine Ranging", Category.RANGED, 23733, Skill.RANGED),
        new Kind("Divine Magic", Category.MAGIC, 23745, Skill.MAGIC));

    private ActiveBuffs() {
    }

    /**
     * @param boosts          boost (boosted - real level) per combat skill; missing or <= 0 means not boosted
     * @param divineEndMillis when each skill's divine potion runs out; missing when none is active
     * @param tickMillis      wall-clock time of the current game tick
     */
    static List<Buff> detect(Map<Skill, Integer> boosts, Map<Skill, Long> divineEndMillis,
                             BuffTimer timer, int tick, long tickMillis, boolean preserve) {
        List<Buff> buffs = new ArrayList<>();

        // Divine: stats whose countdowns end on the same tick came from the same potion
        Map<Long, Set<Skill>> byEnd = new LinkedHashMap<>();
        Set<Skill> regular = EnumSet.noneOf(Skill.class);
        for (Map.Entry<Skill, Integer> e : boosts.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            Long end = divineEndMillis.get(e.getKey());
            if (end != null && end > tickMillis) {
                byEnd.computeIfAbsent(Math.round(end / 600.0), t -> EnumSet.noneOf(Skill.class)).add(e.getKey());
            } else {
                regular.add(e.getKey());
            }
        }
        for (Set<Skill> group : byEnd.values()) {
            long end = divineEndMillis.get(group.iterator().next());
            for (Kind kind : match(group, DIVINE)) {
                buffs.add(new Buff(kind.name, kind.category, kind.iconItemId, sorted(kind.skills), maxBoost(boosts, kind.skills), end, true));
            }
        }

        // Regular: every boost drops on the same timer, so time left depends on the boost size
        for (Kind kind : match(regular, REGULAR)) {
            int boost = maxBoost(boosts, kind.skills);
            long end = tickMillis + timer.ticksRemaining(tick, boost, preserve) * 600L;
            buffs.add(new Buff(kind.name, kind.category, kind.iconItemId, sorted(kind.skills), boost, end, timer.isSynced()));
        }
        return buffs;
    }

    // Greedily take the first kind whose stats are all boosted, then match what's left
    private static List<Kind> match(Set<Skill> boosted, List<Kind> kinds) {
        Set<Skill> left = EnumSet.noneOf(Skill.class);
        left.addAll(boosted);
        List<Kind> found = new ArrayList<>();
        for (Kind kind : kinds) {
            if (!left.isEmpty() && left.containsAll(kind.skills)) {
                found.add(kind);
                left.removeAll(kind.skills);
            }
        }
        return found;
    }

    private static int maxBoost(Map<Skill, Integer> boosts, Set<Skill> skills) {
        int max = 0;
        for (Skill skill : skills) {
            max = Math.max(max, boosts.getOrDefault(skill, 0));
        }
        return max;
    }

    private static List<Skill> sorted(Set<Skill> skills) {
        return Collections.unmodifiableList(new ArrayList<>(skills));
    }
}
