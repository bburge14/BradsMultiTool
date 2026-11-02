package com.bradsmultitool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;

/**
 * The Planner's CALCULATOR tab, apart from its drawing: your XP in each skill and what you own (the bank as last seen
 * plus your inventory), read on the client thread and kept for the Swing thread; which skill, bonuses and view you
 * picked (remembered); and the sums for each action (how many to the target, its time where the training guides give
 * an XP rate, its Grand Exchange cost per XP from the recipe, what you own of it and what that's worth in XP).
 * Information only: nothing here acts in the game.
 */
@Singleton
class PlannerCalculator {

    private static final String GROUP = SkillGoals.GROUP;
    private static final String SKILL_KEY = "plannerCalcSkill";
    private static final String BONUS_KEY = "plannerCalcBonus.";
    private static final String BANKED_KEY = "plannerCalcBanked";

    private final Client client;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final PlannerView view;

    // Read on the client thread, replaced whole so the Swing thread never sees a half-made one
    private volatile Map<Skill, Integer> xp = Collections.emptyMap();
    private volatile Map<String, Integer> inventory = Collections.emptyMap();
    private volatile Map<String, Integer> bank = Collections.emptyMap();
    private volatile boolean bankSeen;
    private boolean dirty = true;
    private boolean bankLoaded;

    @Inject
    PlannerCalculator(Client client, ConfigManager configManager, ItemManager itemManager, PlannerView view) {
        this.client = client;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.view = view;
    }

    // ---- From the game (client thread) ----

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            dirty = true;
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        dirty = true;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.BANK) {
            // The bank as you last saw it, in the same place and format as the Skilling Helper keeps it (either
            // plugin alone has it)
            Map<String, Integer> seen = new HashMap<>();
            count(event.getItemContainer(), seen);
            bank = seen;
            bankSeen = true;
            bankLoaded = true;
            if (configManager.getRSProfileKey() != null) {
                SkillGoals.saveBank(configManager, seen);
            }
            view.update();
        } else if (event.getContainerId() == InventoryID.INV) {
            dirty = true;
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        bankLoaded = false;
        dirty = true;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (GROUP.equals(event.getGroup()) && event.getKey() != null) {
            if (event.getKey().equals(SkillGoals.BANK_KEY)) {
                bankLoaded = false;
                dirty = true;
            } else if (event.getKey().startsWith("goal")) {
                // A goal set in the Skilling Helper (or here): the target follows it
                view.update();
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!bankLoaded && configManager.getRSProfileKey() != null) {
            bankLoaded = true;
            Map<String, Integer> saved = SkillGoals.savedBank(configManager);
            bank = saved;
            bankSeen = !saved.isEmpty();
            view.update();
        }
        if (!dirty || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        dirty = false;
        Map<Skill, Integer> now = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            now.put(skill, client.getSkillExperience(skill));
        }
        Map<String, Integer> carried = new HashMap<>();
        count(client.getItemContainer(InventoryID.INV), carried);
        if (!now.equals(xp) || !carried.equals(inventory)) {
            xp = now;
            inventory = carried;
            view.update();
        }
    }

    private void count(ItemContainer container, Map<String, Integer> into) {
        if (container == null) {
            return;
        }
        for (Item item : container.getItems()) {
            if (item.getId() > 0) {
                String name = itemManager.getItemComposition(item.getId()).getName().toLowerCase(Locale.ROOT);
                into.merge(name, item.getQuantity(), Integer::sum);
            }
        }
    }

    /** For the preview and tests: the game's numbers. */
    void setSample(Map<Skill, Integer> xp, Map<String, Integer> inventory, Map<String, Integer> bank) {
        this.xp = new EnumMap<>(xp);
        this.inventory = new HashMap<>(inventory);
        this.bank = new HashMap<>(bank);
        bankSeen = !bank.isEmpty();
        bankLoaded = true;
    }

    // ---- For the panel (Swing thread) ----

    /** Your XP in the skill as the game last said, or -1 before you've logged in this session. */
    int xp(Skill skill) {
        Integer value = xp.get(skill);
        return value == null ? -1 : value;
    }

    /** What you carry and bank (lower-case names; the bank as last opened). */
    Map<String, Integer> owned() {
        Map<String, Integer> out = new HashMap<>(bank);
        inventory.forEach((name, count) -> out.merge(name, count, Integer::sum));
        return out;
    }

    /** Whether the bank has been seen (opened once, this session or before). */
    boolean bankSeen() {
        return bankSeen;
    }

    int goal(Skill skill) {
        return configManager.getRSProfileKey() == null ? 0 : SkillGoals.goal(configManager, skill);
    }

    int goalStart(Skill skill) {
        return configManager.getRSProfileKey() == null ? 0 : SkillGoals.goalStart(configManager, skill);
    }

    /** Sets the skill's goal as the tracker's Set Goal does (its goal bar starts from your XP now). */
    void setGoal(Skill skill, int goalXp, int xpNow) {
        if (configManager.getRSProfileKey() != null) {
            SkillGoals.setGoal(configManager, skill, goalXp, xpNow);
        }
    }

    /** The skill picked last (Woodcutting at first), among those the calculator has. */
    Skill skill(List<Skill> choices) {
        String saved = configManager.getConfiguration(GROUP, SKILL_KEY);
        for (Skill skill : choices) {
            if (skill.name().equals(saved)) {
                return skill;
            }
        }
        return choices.contains(Skill.WOODCUTTING) ? Skill.WOODCUTTING : choices.isEmpty() ? null : choices.get(0);
    }

    void setSkill(Skill skill) {
        configManager.setConfiguration(GROUP, SKILL_KEY, skill.name());
    }

    /** The bonuses ticked for this skill (remembered). */
    Set<String> bonuses(Skill skill) {
        String saved = configManager.getConfiguration(GROUP, BONUS_KEY + skill.name());
        Set<String> out = new LinkedHashSet<>();
        if (saved != null) {
            for (String id : saved.split(",")) {
                if (!id.trim().isEmpty()) {
                    out.add(id.trim());
                }
            }
        }
        return out;
    }

    void setBonuses(Skill skill, Set<String> on) {
        if (on.isEmpty()) {
            configManager.unsetConfiguration(GROUP, BONUS_KEY + skill.name());
        } else {
            configManager.setConfiguration(GROUP, BONUS_KEY + skill.name(), String.join(",", on));
        }
    }

    /** Whether it shows what your bank gets you (rather than every action). */
    boolean banked() {
        return Boolean.parseBoolean(configManager.getConfiguration(GROUP, BANKED_KEY));
    }

    void setBanked(boolean banked) {
        configManager.setConfiguration(GROUP, BANKED_KEY, banked);
    }

    // ---- The sums ----

    /** One action's line, worked out. */
    static final class Row {
        final CalculatorData.Action action;
        /** XP each with the bonuses. */
        final double xpEach;
        /** Times to the target. */
        final int needed;
        /** You can't do it yet. */
        final boolean locked;
        /** Seconds to the target at the training guide's rate for a matching method, or 0 when there's none. */
        long seconds;
        /** That method's XP an hour. */
        int xpHour;
        /** What making one earns (product less materials, GE): negative when it costs; null when not known. */
        Double profit;
        /** How many you have or can make from what you own. */
        int have;
        /** The guide's method at your level is this. */
        boolean suggested;

        Row(CalculatorData.Action action, double xpEach, int needed, boolean locked) {
            this.action = action;
            this.xpEach = xpEach;
            this.needed = needed;
            this.locked = locked;
        }

        /** GE gp per XP (negative: costs), or null. */
        Double perXp() {
            return profit == null || xpEach <= 0 ? null : profit / xpEach;
        }
    }

    /**
     * Every action of the skill with its sums: XP each with the ticked bonuses, how many from {@code xp} to
     * {@code target}, whether your level allows it, its recipe's GE profit, what you own of it, the training guides'
     * time to the target where a method matches it, and which is the guide's pick at your level. Those you can do
     * come first (highest level first), then those you can't yet (lowest first); the sort is stable, so actions of one
     * level keep the calculator's order.
     */
    static List<Row> rows(Skill skill, CalculatorData.Calc calc, Collection<CalculatorData.Bonus> on, int xp, int target,
                          RecipeData recipes, TrainingData training, Map<String, Integer> have, ToIntFunction<String> price) {
        int level = CalculatorData.level(xp, false);
        TrainingData.Method pick = training == null ? null : training.suggest(skill, level, false);
        String pickName = suggestedAction(pick, calc.actions, level);
        List<Row> out = new ArrayList<>();
        for (CalculatorData.Action action : calc.actions) {
            Row row = new Row(action, CalculatorData.xpEach(action, on), CalculatorData.needed(action, on, xp, target),
                action.level > level);
            List<RecipeData.Recipe> made = recipes == null ? Collections.emptyList() : recipesFor(skill, action, recipes);
            if (!made.isEmpty()) {
                for (RecipeData.Recipe recipe : made) {
                    row.have = Math.max(row.have, recipe.makeable(have));
                    Double profit = profit(recipe, price);
                    if (profit != null && (row.profit == null || profit > row.profit)) {
                        row.profit = profit;
                    }
                }
            } else {
                row.have = have.getOrDefault(action.name.toLowerCase(Locale.ROOT), 0);
            }
            TrainingData.Method method = training == null ? null : rated(training, skill, action, level);
            if (method != null && target > xp) {
                row.xpHour = method.xpHour;
                row.seconds = (long) Math.ceil((target - xp) * 3600.0 / method.xpHour);
            }
            row.suggested = action.name.equals(pickName);
            out.add(row);
        }
        // What you can do first, the highest level first (the likeliest picks at the top), then what you can't yet,
        // nearest first
        out.sort((a, b) -> a.locked != b.locked ? (a.locked ? 1 : -1)
            : a.locked ? Integer.compare(a.action.level, b.action.level) : Integer.compare(b.action.level, a.action.level));
        return out;
    }

    /** The recipes that are this action (same name; Firemaking's "Burn ...", Prayer's "Bury ..." and "Scatter ..."). */
    static List<RecipeData.Recipe> recipesFor(Skill skill, CalculatorData.Action action, RecipeData recipes) {
        String name = action.name.toLowerCase(Locale.ROOT);
        List<RecipeData.Recipe> out = new ArrayList<>();
        for (RecipeData.Recipe recipe : recipes.recipes(skill)) {
            String made = recipe.name.toLowerCase(Locale.ROOT);
            if (made.equals(name) || made.equals("burn " + name) || made.equals("bury " + name) || made.equals("scatter " + name)) {
                out.add(recipe);
            }
        }
        return out;
    }

    // Product less materials at GE prices; null when a material has no price
    private static Double profit(RecipeData.Recipe recipe, ToIntFunction<String> price) {
        double value = price.applyAsInt(recipe.name);
        for (Map.Entry<String, Integer> material : recipe.materials.entrySet()) {
            int each = price.applyAsInt(material.getKey());
            if (each <= 0) {
                return null;
            }
            value -= (double) each * material.getValue();
        }
        return value;
    }

    // Words that don't tell methods apart ("Teak trees" and "Teak logs" are the same thing)
    private static final Set<String> GENERIC = new HashSet<>(java.util.Arrays.asList("logs", "log", "tree", "trees", "ore",
        "ores", "rocks", "rock", "raw", "course", "courses", "agility", "rooftop", "the", "of", "a", "on", "and", "in",
        "training", "fishing", "cooking", "making", "burning", "creating"));

    // The words of a name, singular, without generic ones
    private static Set<String> words(String name) {
        Set<String> out = new HashSet<>();
        for (String word : name.toLowerCase(Locale.ROOT).split("[^a-z0-9']+")) {
            String w = word.endsWith("s") && word.length() > 3 ? word.substring(0, word.length() - 1) : word;
            if (!w.isEmpty() && !GENERIC.contains(word) && !GENERIC.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * Whether a training guide's method is this action: one of its listed targets or table rows has its name, or
     * every telling word of the method's name is in the action's ("Teak trees" for Teak logs, "Hallowed Sepulchre"
     * for its floors).
     */
    static boolean matches(TrainingData.Method method, String action) {
        for (String target : method.targets) {
            if (target.equalsIgnoreCase(action)) {
                return true;
            }
        }
        for (TrainingData.Option option : method.options) {
            if (option.name.equalsIgnoreCase(action)) {
                return true;
            }
        }
        Set<String> need = words(method.name);
        return !need.isEmpty() && words(action).containsAll(need);
    }

    // A method matching the action that the guide gives an XP rate for: one covering your level first
    private static TrainingData.Method rated(TrainingData training, Skill skill, CalculatorData.Action action, int level) {
        TrainingData.Method best = null;
        for (int at : new int[]{level, Math.max(level, action.level), 99}) {
            for (TrainingData.Method method : training.available(skill, at, m -> true)) {
                if (method.xpHour > 0 && matches(method, action.name) && (best == null || method.covers(level) && !best.covers(level))) {
                    best = method;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * The action the guide's method at your level means: its table's pick at your level, else the highest-level
     * matching action you can do.
     */
    static String suggestedAction(TrainingData.Method method, List<CalculatorData.Action> actions, int level) {
        if (method == null) {
            return null;
        }
        TrainingData.Option option = method.bestOption(level);
        if (option != null) {
            for (CalculatorData.Action action : actions) {
                if (action.name.equalsIgnoreCase(option.name)) {
                    return action.name;
                }
            }
        }
        CalculatorData.Action best = null;
        for (CalculatorData.Action action : actions) {
            if (action.level <= level && matches(method, action.name) && (best == null || action.level > best.level)) {
                best = action;
            }
        }
        return best == null ? null : best.name;
    }

    /**
     * The XP in what you own for this skill at this level, each item used once, the most XP-per-action first
     * (RecipeData's banked XP, with the ticked bonuses on each action's XP).
     */
    static double bankedXp(Skill skill, CalculatorData.Calc calc, Collection<CalculatorData.Bonus> on, int level,
                           RecipeData recipes, Map<String, Integer> have) {
        Map<String, Double> each = new HashMap<>();
        for (CalculatorData.Action action : calc.actions) {
            for (RecipeData.Recipe recipe : recipesFor(skill, action, recipes)) {
                each.put(recipe.name.toLowerCase(Locale.ROOT), CalculatorData.xpEach(action, on));
            }
        }
        return recipes.bankedXp(skill, level, have, r -> each.getOrDefault(r.name.toLowerCase(Locale.ROOT), r.xp));
    }

    /** The level your XP plus {@code gained} reaches (to 99). */
    static int bankedLevel(int xp, double gained) {
        return CalculatorData.level((int) Math.min(Experience.MAX_SKILL_XP, xp + Math.floor(gained)), false);
    }
}
