package com.snuffys.multitool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
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
 * plus your inventory), whether you're on a members world, and Grand Exchange prices by item id, read on the client
 * thread and kept for the Swing thread; which skill, bonuses and view you picked (remembered); the skill plans
 * ({@link SkillPlans}); and the sums for each action (how many to the target, its time where the training guides give
 * an XP rate, what its materials cost and its products are worth, what you own of it and what that's worth in XP) and
 * for each milestone of a plan. Information only: nothing here acts in the game.
 */
@Singleton
class PlannerCalculator {

    private static final String GROUP = SkillGoals.GROUP;
    private static final String SKILL_KEY = "plannerCalcSkill";
    private static final String BONUS_KEY = "plannerCalcBonus.";
    private static final String BANKED_KEY = "plannerCalcBanked";
    private static final String PLAN_VIEW_KEY = "plannerCalcPlan";
    private static final String TARGET_KEY = "plannerCalcTarget.";

    /** The list's three views. */
    enum Mode { ACTIONS, BANKED, PLAN }

    private final Client client;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final PlannerView view;
    private final PlannerConfig config;
    private final PluginFamily family;

    // Read on the client thread, replaced whole so the Swing thread never sees a half-made one
    private volatile Map<Skill, Integer> xp = Collections.emptyMap();
    private volatile Map<String, Integer> inventory = Collections.emptyMap();
    private volatile Map<String, Integer> bank = Collections.emptyMap();
    private volatile boolean bankSeen;
    // On a members world (true), a free one (false), or logged out (null: everything shows)
    private volatile Boolean membersWorld;
    // GE prices by item id, looked up on the client thread when the panel asks for them
    private final Map<Integer, Integer> idPrices = new ConcurrentHashMap<>();
    private final Set<Integer> wantedPrices = ConcurrentHashMap.newKeySet();
    private volatile int priceVersion;
    private boolean dirty = true;
    private boolean bankLoaded;
    // For the preview: whether the Skilling Helper is "installed"
    private Boolean sampleTracker;
    // Typed targets when there's no profile to keep them in (the preview, logged out)
    private final Map<Skill, Integer> sampleTargets = new EnumMap<>(Skill.class);

    @Inject
    PlannerCalculator(Client client, ConfigManager configManager, ItemManager itemManager, PlannerView view,
                      PlannerConfig config, PluginFamily family) {
        this.client = client;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.view = view;
        this.config = config;
        this.family = family;
    }

    // ---- From the game (client thread) ----

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            dirty = true;
        } else if (event.getGameState() == GameState.LOGIN_SCREEN && membersWorld != null) {
            membersWorld = null;
            view.update();
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
            } else if (event.getKey().startsWith("goal") || event.getKey().startsWith(SkillPlans.PLAN_KEY)) {
                // A goal set (or plan exported or removed) in the Skilling Helper or here: the target follows it
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
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        Boolean members = client.getWorldType().contains(WorldType.MEMBERS);
        if (!members.equals(membersWorld)) {
            membersWorld = members;
            view.update();
        }
        if (!wantedPrices.isEmpty()) {
            for (Integer id : new ArrayList<>(wantedPrices)) {
                idPrices.put(id, (int) Math.max(0, Math.min(Integer.MAX_VALUE, itemManager.getItemPrice(id))));
                wantedPrices.remove(id);
            }
            priceVersion++;
            view.update();
        }
        if (!dirty) {
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

    /** For the preview: the world (null: logged out) and whether the Skilling Helper is installed. */
    void setSampleWorld(Boolean members, boolean tracker) {
        membersWorld = members;
        sampleTracker = tracker;
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

    /**
     * Whether to show members-only actions: on a members world or logged out, or with the setting off. On a free
     * world they're left out, as RuneLite's Skill Calculator does.
     */
    boolean showMembers() {
        return membersWorld == null || membersWorld || !config.plannerCalcHideMembers();
    }

    /** Whether to show Grand Exchange costs and earnings. */
    boolean showPrices() {
        return config.plannerCalcPrices();
    }

    /** An item's GE price by id (looked up on the next game tick the first time; 0 until then or without one). */
    int itemPrice(int itemId) {
        if (itemId <= 0) {
            return 0;
        }
        Integer price = idPrices.get(itemId);
        if (price == null) {
            if (itemManager != null) {
                wantedPrices.add(itemId);
            }
            return 0;
        }
        return price;
    }

    /** Goes up each time prices by id come in (so the list is redrawn with them). */
    int priceVersion() {
        return priceVersion;
    }

    /** Whether the Skilling Helper's tracker is there to export a plan to (it, or the Toolbelt). */
    boolean trackerInstalled() {
        if (sampleTracker != null) {
            return sampleTracker;
        }
        return family != null && family.hasSkillTracker();
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

    /** The target you typed for this skill (kept until you reset it or set it as the goal), or 0 for none. */
    int savedTarget(Skill skill) {
        if (configManager.getRSProfileKey() == null) {
            return sampleTargets.getOrDefault(skill, 0);
        }
        try {
            String saved = configManager.getRSProfileConfiguration(GROUP, TARGET_KEY + skill.name());
            return saved == null ? 0 : Math.max(0, Integer.parseInt(saved));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Keeps this target for the skill (0 forgets it). */
    void setSavedTarget(Skill skill, int xp) {
        if (configManager.getRSProfileKey() == null) {
            if (xp > 0) {
                sampleTargets.put(skill, xp);
            } else {
                sampleTargets.remove(skill);
            }
        } else if (xp > 0) {
            configManager.setRSProfileConfiguration(GROUP, TARGET_KEY + skill.name(), xp);
        } else {
            configManager.unsetRSProfileConfiguration(GROUP, TARGET_KEY + skill.name());
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
        return mode() == Mode.BANKED;
    }

    void setBanked(boolean banked) {
        setMode(banked ? Mode.BANKED : Mode.ACTIONS);
    }

    /** Which view the list shows (remembered). */
    Mode mode() {
        if (Boolean.parseBoolean(configManager.getConfiguration(GROUP, PLAN_VIEW_KEY))) {
            return Mode.PLAN;
        }
        return Boolean.parseBoolean(configManager.getConfiguration(GROUP, BANKED_KEY)) ? Mode.BANKED : Mode.ACTIONS;
    }

    void setMode(Mode mode) {
        configManager.setConfiguration(GROUP, BANKED_KEY, mode == Mode.BANKED);
        configManager.setConfiguration(GROUP, PLAN_VIEW_KEY, mode == Mode.PLAN);
    }

    // ---- Plans (RuneScape profile keys, through SkillPlans) ----

    /** Whether there's an account to keep a plan for (logged in once on it). */
    boolean canPlan() {
        return configManager.getRSProfileKey() != null;
    }

    /** The skill's plan as made here, or null. */
    SkillPlans.Plan plan(Skill skill) {
        return canPlan() ? SkillPlans.plan(configManager, skill) : null;
    }

    void savePlan(Skill skill, SkillPlans.Plan plan) {
        if (canPlan()) {
            SkillPlans.savePlan(configManager, skill, plan == null || plan.steps.isEmpty() && plan.goal <= 0 ? null : plan);
        }
    }

    /** The plan exported to the tracker for this skill, or null. */
    SkillPlans.Plan trackedPlan(Skill skill) {
        return canPlan() ? SkillPlans.tracked(configManager, skill) : null;
    }

    /** Whether this plan is what the tracker has (exported and unchanged since). */
    boolean isTracked(Skill skill, SkillPlans.Plan plan) {
        return canPlan() && SkillPlans.isTracked(configManager, skill, plan);
    }

    void export(Skill skill, SkillPlans.Plan plan, int xpNow) {
        if (canPlan()) {
            SkillPlans.export(configManager, skill, plan, xpNow);
        }
    }

    void untrack(Skill skill) {
        if (canPlan()) {
            SkillPlans.untrack(configManager, skill);
        }
    }

    /** The skill's own Training Focus from the shared group says "AFK / Other" (else the fastest methods). */
    boolean otherMethods(Skill skill) {
        return canPlan() && "OTHER".equals(configManager.getRSProfileConfiguration(GROUP, "trainingFocus." + skill.name()));
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
        /** What one's materials cost (GE), or null (no recipe, or a material without a price). */
        Double costEach;
        /** What one product is worth (GE): what it makes or gathers; null when it's nothing that sells. */
        Double earnEach;
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

        /** GE gp per XP (negative: costs), or null: where there's a cost (less what the product is worth). */
        Double perXp() {
            return profit == null || xpEach <= 0 ? null : profit / xpEach;
        }

        /** The materials' cost for this many, or null. */
        Double cost(int count) {
            return costEach == null ? null : costEach * count;
        }

        /** The products' worth for this many, or null. */
        Double earn(int count) {
            return earnEach == null ? null : earnEach * count;
        }
    }

    /** Skills whose actions gather what they're named after (logs, ore, fish, creatures), worth its GE price. */
    private static final Set<Skill> GATHERING = java.util.EnumSet.of(Skill.WOODCUTTING, Skill.MINING, Skill.FISHING,
        Skill.HUNTER);

    /** As below, with prices by name only. */
    static List<Row> rows(Skill skill, CalculatorData.Calc calc, Collection<CalculatorData.Bonus> on, int xp, int target,
                          RecipeData recipes, TrainingData training, Map<String, Integer> have, ToIntFunction<String> price) {
        return rows(skill, calc.actions, on, xp, target, recipes, training, have, price, id -> 0);
    }

    /**
     * Every action given with its sums: XP each with the ticked bonuses, how many from {@code xp} to {@code target},
     * whether your level allows it, its recipe's GE cost and product's worth (by name; a gathered product by its item
     * id first, {@code itemPrice}), what you own of it, the training guides' time to the target where a method
     * matches it, and which is the guide's pick at your level. Those you can do come first (highest level first),
     * then those you can't yet (lowest first); the sort is stable, so actions of one level keep the calculator's
     * order.
     */
    static List<Row> rows(Skill skill, List<CalculatorData.Action> actions, Collection<CalculatorData.Bonus> on, int xp,
                          int target, RecipeData recipes, TrainingData training, Map<String, Integer> have,
                          ToIntFunction<String> price, IntUnaryOperator itemPrice) {
        int level = CalculatorData.level(xp, false);
        TrainingData.Method pick = training == null ? null : training.suggest(skill, level, false);
        String pickName = suggestedAction(pick, actions, level);
        List<Row> out = new ArrayList<>();
        for (CalculatorData.Action action : actions) {
            Row row = row(skill, action, on, xp, target, level, recipes, training, have, price, itemPrice);
            row.suggested = action.name.equals(pickName);
            out.add(row);
        }
        // What you can do first, the highest level first (the likeliest picks at the top), then what you can't yet,
        // nearest first
        out.sort((a, b) -> a.locked != b.locked ? (a.locked ? 1 : -1)
            : a.locked ? Integer.compare(a.action.level, b.action.level) : Integer.compare(b.action.level, a.action.level));
        return out;
    }

    // One action's sums from xp to target, at this level (for the guide's rate and whether it's locked)
    private static Row row(Skill skill, CalculatorData.Action action, Collection<CalculatorData.Bonus> on, int xp, int target,
                           int level, RecipeData recipes, TrainingData training, Map<String, Integer> have,
                           ToIntFunction<String> price, IntUnaryOperator itemPrice) {
        Row row = new Row(action, CalculatorData.xpEach(action, on), CalculatorData.needed(action, on, xp, target),
            action.level > level);
        List<RecipeData.Recipe> made = recipes == null ? Collections.emptyList() : recipesFor(skill, action, recipes);
        if (!made.isEmpty()) {
            for (RecipeData.Recipe recipe : made) {
                row.have = Math.max(row.have, recipe.makeable(have));
                Double cost = cost(recipe, price);
                if (cost == null) {
                    continue;
                }
                double worth = consumed(recipe) ? 0 : price.applyAsInt(recipe.name);
                double profit = worth - cost;
                if (row.profit == null || profit > row.profit) {
                    row.profit = profit;
                    row.costEach = cost;
                    row.earnEach = worth > 0 ? worth : null;
                }
            }
        } else {
            row.have = have.getOrDefault(action.name.toLowerCase(Locale.ROOT), 0);
            if (GATHERING.contains(skill)) {
                int worth = action.icon > 0 ? itemPrice.applyAsInt(action.icon) : 0;
                if (worth <= 0) {
                    worth = price.applyAsInt(action.name);
                }
                row.earnEach = worth > 0 ? (double) worth : null;
            }
        }
        TrainingData.Method method = training == null ? null : rated(training, skill, action, level);
        if (method != null && target > xp) {
            row.xpHour = method.xpHour;
            row.seconds = (long) Math.ceil((target - xp) * 3600.0 / method.xpHour);
        }
        return row;
    }

    // A recipe that uses its item up rather than making something ("Burn logs", "Bury bones", "Scatter ashes")
    private static boolean consumed(RecipeData.Recipe recipe) {
        String name = recipe.name.toLowerCase(Locale.ROOT);
        return name.startsWith("burn ") || name.startsWith("bury ") || name.startsWith("scatter ");
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

    // The materials at GE prices; null when a material has no price
    private static Double cost(RecipeData.Recipe recipe, ToIntFunction<String> price) {
        double cost = 0;
        for (Map.Entry<String, Integer> material : recipe.materials.entrySet()) {
            int each = price.applyAsInt(material.getKey());
            if (each <= 0) {
                return null;
            }
            cost += (double) each * material.getValue();
        }
        return cost;
    }

    /** Whether a training guide's method is this action (see {@link SkillPlans#matches}). */
    static boolean matches(TrainingData.Method method, String action) {
        return SkillPlans.matches(method, action);
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
                if (action.name.equalsIgnoreCase(option.name) && action.level <= level) {
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
        return recipes.bankedXp(skill, level, have, xpOf(skill, calc, on, recipes));
    }

    /**
     * How many of each action {@link #bankedXp} counts (materials shared between actions used once, as there), or
     * null for an action without recipes.
     */
    static Integer bankedTimes(Skill skill, CalculatorData.Action action, RecipeData recipes, Map<String, Integer> made) {
        List<RecipeData.Recipe> mine = recipesFor(skill, action, recipes);
        if (mine.isEmpty()) {
            return null;
        }
        int times = 0;
        for (RecipeData.Recipe recipe : mine) {
            times += made.getOrDefault(recipe.name.toLowerCase(), 0);
        }
        return times;
    }

    /** Lower-case recipe name -> how many {@link #bankedXp} makes. */
    static Map<String, Integer> bankedMade(Skill skill, CalculatorData.Calc calc, Collection<CalculatorData.Bonus> on, int level,
                                           RecipeData recipes, Map<String, Integer> have) {
        return recipes.bankedTimes(skill, level, have, xpOf(skill, calc, on, recipes));
    }

    /** The GE value of what {@link #bankedXp} uses up (each item counted once, as there); 0 when nothing's priced. */
    static long bankedValue(Skill skill, CalculatorData.Calc calc, Collection<CalculatorData.Bonus> on, int level,
                            RecipeData recipes, Map<String, Integer> have, ToIntFunction<String> price) {
        long value = 0;
        for (Map.Entry<String, Integer> used : recipes.bankedMaterials(skill, level, have, xpOf(skill, calc, on, recipes)).entrySet()) {
            value += (long) price.applyAsInt(used.getKey()) * used.getValue();
        }
        return value;
    }

    private static java.util.function.ToDoubleFunction<RecipeData.Recipe> xpOf(Skill skill, CalculatorData.Calc calc,
                                                                                Collection<CalculatorData.Bonus> on, RecipeData recipes) {
        Map<String, Double> each = new HashMap<>();
        for (CalculatorData.Action action : calc.actions) {
            for (RecipeData.Recipe recipe : recipesFor(skill, action, recipes)) {
                each.put(recipe.name.toLowerCase(Locale.ROOT), CalculatorData.xpEach(action, on));
            }
        }
        return r -> each.getOrDefault(r.name.toLowerCase(Locale.ROOT), r.xp);
    }

    /** The level your XP plus {@code gained} reaches (to 99). */
    static int bankedLevel(int xp, double gained) {
        return CalculatorData.level((int) Math.min(Experience.MAX_SKILL_XP, xp + Math.floor(gained)), false);
    }

    // ---- Plan sums ----

    /** A milestone of a plan, worked out (what's left of it from your XP). */
    static final class StepRow {
        final SkillPlans.Step step;
        /** The Calculator's action (null when it's not in the data any more). */
        final CalculatorData.Action action;
        /** Its sums from where you are (or the step's start) to its end; null without an action. */
        final Row row;
        /** XP the step still needs. */
        final int xp;
        /** Already past its end. */
        final boolean done;

        StepRow(SkillPlans.Step step, CalculatorData.Action action, Row row, int xp, boolean done) {
            this.step = step;
            this.action = action;
            this.row = row;
            this.xp = xp;
            this.done = done;
        }

        /** You can't do its action at its start level. */
        boolean tooLow() {
            return action != null && action.level > step.from;
        }
    }

    /** A plan's totals: XP, time (only of the steps that have one), cost and earnings left, and its end level. */
    static final class Totals {
        long xp;
        long seconds;
        /** Steps without a time. */
        int untimed;
        double cost;
        double earn;
        boolean priced;
        int endLevel;
        /** Levels between the start and the goal with no step. */
        int gaps;
    }

    /** Each milestone's sums from your XP {@code xpNow}: what's left of each, at its own start level's rates. */
    static List<StepRow> stepRows(Skill skill, CalculatorData.Calc calc, Collection<CalculatorData.Bonus> on, SkillPlans.Plan plan,
                                  int xpNow, RecipeData recipes, TrainingData training, ToIntFunction<String> price,
                                  IntUnaryOperator itemPrice) {
        List<StepRow> out = new ArrayList<>();
        for (SkillPlans.Step step : plan.steps) {
            int start = Math.max(xpNow, Experience.getXpForLevel(step.from));
            int end = Experience.getXpForLevel(Math.min(99, step.to));
            CalculatorData.Action action = CalculatorData.named(calc, step.action);
            boolean done = xpNow >= end;
            Row row = action == null ? null : row(skill, action, on, start, end, Math.max(step.from,
                CalculatorData.level(xpNow, false)), recipes, training, Collections.emptyMap(), price, itemPrice);
            out.add(new StepRow(step, action, row, done ? 0 : Math.max(0, end - start), done));
        }
        return out;
    }

    static Totals totals(SkillPlans.Plan plan, List<StepRow> rows, int xpNow) {
        Totals totals = new Totals();
        for (StepRow s : rows) {
            if (s.done) {
                continue;
            }
            totals.xp += s.xp;
            if (s.row != null && s.row.seconds > 0) {
                totals.seconds += s.row.seconds;
            } else if (s.xp > 0) {
                totals.untimed++;
            }
            if (s.row != null) {
                Double cost = s.row.cost(s.row.needed);
                Double earn = s.row.earn(s.row.needed);
                if (cost != null) {
                    totals.cost += cost;
                    totals.priced = true;
                }
                if (earn != null) {
                    totals.earn += earn;
                    totals.priced = true;
                }
            }
        }
        totals.endLevel = Math.max(CalculatorData.level(xpNow, false), plan.end());
        int from = CalculatorData.level(xpNow, false);
        for (int level = from; level < Math.min(99, plan.goal); level++) {
            if (plan.at(level) == null) {
                totals.gaps++;
            }
        }
        return totals;
    }

    /**
     * Adds a milestone for this action at the end of the plan: from where the last one ends (your level for the
     * first), or from the action's level if that's later, the last milestone stretched or cut to meet it; up to the
     * plan's goal. False when it can't fit (it would start at or past the goal).
     */
    static boolean addStep(SkillPlans.Plan plan, CalculatorData.Action action, double xpEach, String method, int level) {
        SkillPlans.Step last = plan.steps.isEmpty() ? null : plan.steps.get(plan.steps.size() - 1);
        int start;
        if (last == null) {
            start = Math.max(Math.max(1, level), action.level);
        } else if (last.to < plan.goal) {
            start = Math.max(last.to, action.level);
        } else {
            start = Math.max(action.level, last.from + 1);
        }
        if (start >= plan.goal) {
            return false;
        }
        if (last != null) {
            last.to = start;
        }
        plan.steps.add(new SkillPlans.Step(action.name, start, plan.goal, method, xpEach));
        return true;
    }

    /**
     * Changes the plan's final goal: milestones past it go, the rest stop at it; when it's raised, the last milestone
     * that ended at the old goal doesn't stretch (raiseGoal leaves room for a new one after it).
     */
    static void setGoal(SkillPlans.Plan plan, int goal) {
        int old = plan.goal;
        raiseGoal(plan, goal);
        if (!plan.steps.isEmpty() && goal > old && plan.steps.get(plan.steps.size() - 1).to == old) {
            plan.steps.get(plan.steps.size() - 1).to = goal;
        }
    }

    /** Sets the plan's goal, leaving the milestones' levels as they are (cut to it when it's lower). */
    static void raiseGoal(SkillPlans.Plan plan, int goal) {
        plan.goal = goal;
        plan.steps.removeIf(step -> step.from >= goal);
        for (SkillPlans.Step step : plan.steps) {
            step.to = Math.min(step.to, goal);
        }
    }

    /** Removes a milestone: the one before stretches over its levels, else the one after starts where it did. */
    static void removeStep(SkillPlans.Plan plan, int index) {
        SkillPlans.Step gone = plan.steps.remove(index);
        if (index > 0 && plan.steps.get(index - 1).to == gone.from) {
            plan.steps.get(index - 1).to = gone.to;
        } else if (index < plan.steps.size() && plan.steps.get(index).from == gone.to) {
            plan.steps.get(index).from = gone.from;
        }
    }

    /** Swaps two milestones' methods (the levels stay in order). */
    static void swapSteps(SkillPlans.Plan plan, int a, int b) {
        SkillPlans.Step one = plan.steps.get(a);
        SkillPlans.Step two = plan.steps.get(b);
        String action = one.action;
        String method = one.method;
        double xp = one.xp;
        one.action = two.action;
        one.method = two.method;
        one.xp = two.xp;
        two.action = action;
        two.method = method;
        two.xp = xp;
    }

    /**
     * A plan from the wiki's training guides: from {@code level} to {@code goal}, each guide method on the way (the
     * fastest, or with {@code other} its other methods first) as the Calculator's action it means at that level; a
     * new milestone wherever that action changes. Empty steps when nothing matches.
     */
    static SkillPlans.Plan suggestedPlan(Skill skill, List<CalculatorData.Action> actions, Collection<CalculatorData.Bonus> on,
                                         TrainingData training, int level, int goal, boolean other) {
        SkillPlans.Plan plan = new SkillPlans.Plan(goal);
        SkillPlans.Step current = null;
        for (int at = Math.max(1, level); at < goal; at++) {
            TrainingData.Method method = training.suggest(skill, at, other);
            String name = suggestedAction(method, actions, at);
            if (name == null) {
                name = bestAt(actions, at);
            }
            if (name == null) {
                continue;
            }
            if (current != null && current.action.equals(name)) {
                current.to = at + 1;
                continue;
            }
            CalculatorData.Action action = null;
            for (CalculatorData.Action a : actions) {
                if (a.name.equals(name)) {
                    action = a;
                    break;
                }
            }
            if (current != null) {
                current.to = at;
            }
            TrainingData.Method matched = method != null && matches(method, name) ? method
                : SkillPlans.methodFor(training, skill, name, at, goal);
            current = new SkillPlans.Step(name, at, at + 1, matched == null ? null : matched.name,
                action == null ? 0 : CalculatorData.xpEach(action, on));
            plan.steps.add(current);
        }
        if (current != null) {
            current.to = goal;
        }
        return plan;
    }

    // The highest-level action you can do at this level (the most XP each among those), for levels no guide method
    // matches an action
    private static String bestAt(List<CalculatorData.Action> actions, int level) {
        CalculatorData.Action best = null;
        for (CalculatorData.Action action : actions) {
            if (action.level <= level && (best == null || action.level > best.level
                || action.level == best.level && action.xp > best.xp)) {
                best = action;
            }
        }
        return best == null ? null : best.name;
    }
}
