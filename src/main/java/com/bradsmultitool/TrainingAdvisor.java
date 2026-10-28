package com.bradsmultitool;

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

        Suggestion(String label, String text, TrainingData.Method method) {
            this.label = label;
            this.text = text;
            this.method = method;
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
    // The cheapest recipe per skill and level (prices only looked up once)
    private final Map<String, Object> cheapest = new ConcurrentHashMap<>();
    private static final Object NONE = new Object();

    @Inject
    TrainingAdvisor(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config, TrainingData training,
                    RecipeData recipes, MoneyData money, ItemPrices prices) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.training = training;
        this.recipes = recipes;
        this.money = money;
        this.prices = prices;
    }

    /** What to train at this level, or null. */
    Suggestion suggest(Skill skill, int level) {
        if (skill == Skill.SLAYER) {
            SlayerData.Master master = plugin.getSlayerData().bestMaster(plugin.getCombatLevel(), level);
            return master == null ? null : new Suggestion("Train:", "Tasks from " + master.name, null);
        }
        switch (config.trainingFocus()) {
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
                    return new Suggestion("Cheapest:", recipe.name + " (" + String.format("%+.1f", gpPerXp) + " gp/xp)", null);
                }
                break;
            default:
                break;
        }
        TrainingData.Method method = training.suggest(skill, level, config.trainingFocus() == BradsMultiToolConfig.TrainingFocus.OTHER);
        return method == null ? null : new Suggestion("Train:", method.name, method);
    }

    /** The first level on the way to the goal where the suggestion changes, and what it changes to, or null. */
    Map.Entry<Integer, Suggestion> next(Skill skill, int level, int goalLevel) {
        Suggestion current = suggest(skill, level);
        for (int at = level + 1; at <= Math.min(goalLevel, 99); at++) {
            Suggestion later = suggest(skill, at);
            if (later != null && !Objects.equals(later, current)
                && (later.method == null || !later.method.isQuesting())) {
                return Map.entry(at, later);
            }
        }
        return null;
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
