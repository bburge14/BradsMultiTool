package com.bradsmultitool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;

/**
 * What to train for a skill goal, by the Training Focus setting, shared by the tracker card, the pinned
 * overlay, the world highlight and the level-up note:
 * <ul>
 * <li>Fastest XP / Other methods: the wiki training guide's method for your level ({@link TrainingData}).</li>
 * <li>Cheapest: for skills that make things, the recipe with the fewest GP per XP at GE prices; gathering
 * skills cost nothing, so their fastest method.</li>
 * <li>Money maker: the most profitable wiki money making guide you can do ({@link MoneyData}).</li>
 * </ul>
 * Slayer always suggests the best Slayer master.
 */
@Singleton
class TrainingAdvisor {

    /** A line for the card: "Train: Oak trees", and the method to outline (null if nothing to outline). */
    static final class Suggestion {
        final String label;
        final String text;
        final TrainingData.Method method;
        /** XP for one action of it (one log, one item made), 0 if unknown. */
        final double xpEach;

        Suggestion(String label, String text, TrainingData.Method method) {
            this(label, text, method, 0);
        }

        Suggestion(String label, String text, TrainingData.Method method, double xpEach) {
            this.label = label;
            this.text = text;
            this.method = method;
            this.xpEach = xpEach;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Suggestion && ((Suggestion) o).text.equals(text);
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }
    }

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final TrainingData training;
    private final RecipeData recipes;
    private final MoneyData money;
    private final ItemPrices prices;
    private final SkillingData skilling;
    private final SkillHelperData helpers;
    // The cheapest recipe per skill and level (prices only looked up once)
    private final Map<String, Object> cheapest = new ConcurrentHashMap<>();
    private static final Object NONE = new Object();

    @Inject
    TrainingAdvisor(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config, TrainingData training,
                    RecipeData recipes, MoneyData money, ItemPrices prices, SkillingData skilling,
                    SkillHelperData helpers) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.training = training;
        this.recipes = recipes;
        this.money = money;
        this.prices = prices;
        this.skilling = skilling;
        this.helpers = helpers;
    }

    /** What to train at this level, or null. */
    Suggestion suggest(Skill skill, int level) {
        if (skill == Skill.SLAYER) {
            SlayerData.Master master = plugin.getSlayerData().bestMaster(plugin.getCombatLevel(), level);
            return master == null ? null : new Suggestion("Train:", "Tasks from " + master.name, null);
        }
        BradsMultiToolConfig.TrainingFocus focus = plugin.getTrainingFocus(skill);
        switch (focus) {
            case MONEY:
                MoneyData.Guide guide = money.best(skill, level, this::levelOf);
                if (guide != null) {
                    return new Suggestion("Money:", guide.name + " (" + guide.profitText() + ")", null);
                }
                break;
            case CHEAPEST:
                RecipeData.Recipe recipe = cheapest(skill, level);
                if (recipe != null) {
                    double gpPerXp = prices.profit(recipe) / recipe.xp;
                    return new Suggestion("Cheapest:", recipe.name + " (" + String.format("%+.1f", gpPerXp) + " gp/xp)", null, recipe.xp);
                }
                break;
            default:
                break;
        }
        TrainingData.Method method = training.suggest(skill, level, focus == BradsMultiToolConfig.TrainingFocus.OTHER);
        return method == null ? null : new Suggestion("Train:", method.name, method, xpEach(method));
    }

    // XP per action of a guide method: its tree/rock/stall's XP, else its NPC's (pickpocket or hunter creature)
    private double xpEach(TrainingData.Method method) {
        for (int id : method.objects) {
            SkillingData.Resource resource = skilling.object(id, null);
            if (resource != null && resource.xp > 0) {
                return resource.xp;
            }
        }
        for (String npc : method.npcs) {
            double xp = npcXp(npc);
            if (xp > 0) {
                return xp;
            }
        }
        return 0;
    }

    private double npcXp(String npc) {
        SkillingData.Pickpocket pickpocket = skilling.pickpocket(npc);
        if (pickpocket != null && pickpocket.xp > 0) {
            return pickpocket.xp;
        }
        SkillHelperData.Creature creature = helpers.creature(npc);
        return creature != null ? creature.xp : 0;
    }

    /** For a combat goal: the monster kills are counted on, and the XP one kill gives in the skill. */
    static final class KillEstimate {
        final String monster;
        final double xpEach;

        KillEstimate(String monster, double xpEach) {
            this.monster = monster;
            this.xpEach = xpEach;
        }
    }

    /**
     * XP per kill for a melee, Ranged or Hitpoints goal (XP per damage by your style, see
     * {@link BradsMultiToolPlugin#xpPerDamage}), on the monster you last fought, else the one you picked with "Track for goal", else the
     * suggested one. Null if none of them has known hitpoints (a Gemstone Crab doesn't die).
     */
    KillEstimate killEstimate(Skill skill) {
        // By your current style: Controlled splits the XP three ways, Longrange two
        double perDamage = plugin.xpPerDamage(skill);
        List<String> monsters = new ArrayList<>();
        monsters.add(plugin.getLastPvmTargetName());
        String target = plugin.getGoalTarget(skill);
        if (target != null && target.startsWith("npc:")) {
            monsters.add(BradsMultiToolPlugin.goalTargetName(target));
        }
        Suggestion suggestion = suggest(skill, plugin.getCurrentSkillLevel(skill));
        if (suggestion != null && suggestion.method != null) {
            monsters.addAll(suggestion.method.npcs);
            // The guide names them in the plural ("Sulphur Naguas")
            monsters.add(suggestion.method.name.replaceAll("s$", ""));
        }
        for (String monster : monsters) {
            MonsterData.Stats stats = plugin.statsFor(monster);
            if (stats != null && stats.hitpoints > 0) {
                return new KillEstimate(monster, stats.hitpoints * perDamage);
            }
        }
        return null;
    }

    /**
     * XP you'll likely get per action toward this skill's goal, before you've had an XP drop: what you picked
     * with "Track for goal", else what to make from your items, else the suggested method. 0 if unknown.
     */
    double estimatedXpEach(Skill skill, int level) {
        String target = plugin.getGoalTarget(skill);
        if (target != null) {
            String name = BradsMultiToolPlugin.goalTargetName(target);
            if (target.startsWith("object:")) {
                SkillingData.Resource resource = skilling.object(-1, name);
                return resource != null ? resource.xp : 0;
            }
            return npcXp(name);
        }
        RecipeData.Recipe make = plugin.getGoalItems().suggestion(skill);
        if (make != null) {
            return make.xp;
        }
        Suggestion suggestion = suggest(skill, level);
        if (suggestion != null && suggestion.xpEach > 0) {
            return suggestion.xpEach;
        }
        // A general method ("Making potions"): the most XP thing you can make at your level
        RecipeData.Recipe best = recipes.covers(skill) ? recipes.best(skill, level, null, r -> r.xp) : null;
        return best != null ? best.xp : 0;
    }

    /** One leg of the way to a goal: train this from one level until another. */
    static final class Stage {
        final int from;
        final int to;
        final Suggestion suggestion;

        Stage(int from, int to, Suggestion suggestion) {
            this.from = from;
            this.to = to;
            this.suggestion = suggestion;
        }
    }

    /**
     * What to train from your level to the goal, split where the suggestion changes ("Willow trees" to 60,
     * then "Maple trees" to 75...). Quest methods along the way don't start a new leg. Empty if there's
     * nothing to suggest.
     */
    List<Stage> plan(Skill skill, int level, int goalLevel) {
        List<Stage> stages = new ArrayList<>();
        int end = Math.max(level, Math.min(goalLevel, 99));
        Suggestion current = suggest(skill, level);
        int from = level;
        for (int at = level + 1; at <= end; at++) {
            Suggestion later = suggest(skill, at);
            if (later != null && !Objects.equals(later, current)
                && (later.method == null || !later.method.isQuesting())) {
                if (current != null) {
                    stages.add(new Stage(from, at, current));
                }
                current = later;
                from = at;
            }
        }
        if (current != null) {
            stages.add(new Stage(from, Math.max(goalLevel, from), current));
        }
        return stages;
    }

    // Fewest GP per XP (most profit per XP) among recipes at your level whose materials have GE prices
    private RecipeData.Recipe cheapest(Skill skill, int level) {
        if (!recipes.covers(skill)) {
            return null;
        }
        Object found = cheapest.computeIfAbsent(skill.name() + ":" + level, key -> {
            RecipeData.Recipe recipe = recipes.best(skill, level, null, r -> prices.profit(r) / r.xp, prices::pricedMaterials);
            return recipe != null ? recipe : NONE;
        });
        return found == NONE ? null : (RecipeData.Recipe) found;
    }

    private int levelOf(String skillName) {
        try {
            return client.getRealSkillLevel(Skill.valueOf(skillName));
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
