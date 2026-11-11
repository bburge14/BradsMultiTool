package com.bradsmultitool;

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
import net.runelite.api.gameval.VarbitID;
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
    private static final String DOCK_KEY = "plannerCalcDockOpen";
    private static final String SORT_KEY = "plannerCalcSort";

    /** How the Actions list is ordered (picked from its Sort menu, remembered). */
    enum Sort {
        LEVEL_UP("Level (low to high)", "Level ↑"),
        LEVEL_DOWN("Level (high to low)", "Level ↓"),
        CAN_DO("What you can do first", "Can do"),
        XP_EACH("Most XP each", "XP each"),
        XP_HOUR("Fastest (XP an hour)", "XP/hr"),
        CHEAPEST("Cheapest per XP", "Cheapest"),
        PROFIT("Most profit", "Profit"),
        NAME("Name", "Name");

        final String label;
        final String shortLabel;

        Sort(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }
    }

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
    // High alchemy values by item id (from its item composition, on the client thread), as the prices are
    private final Map<Integer, Integer> alchPrices = new ConcurrentHashMap<>();
    private final Set<Integer> wantedAlch = ConcurrentHashMap.newKeySet();
    private volatile int priceVersion;
    private boolean dirty = true;
    private boolean bankLoaded;
    // For the preview: whether the Skilling Helper is "installed"
    private Boolean sampleTracker;
    // Typed targets when there's no profile to keep them in (the preview, logged out)
    private final Map<Skill, Integer> sampleTargets = new EnumMap<>(Skill.class);

    @Inject
    PlannerCalculator(Client client, ConfigManager configManager, ItemManager itemManager, PlannerView view,
                      PlannerConfig config, PluginFamily family, Alerts alerts, PlannerGeLimits geLimits) {
        this.alerts = alerts;
        this.geLimits = geLimits;
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
        // The skill you're training: the last to gain XP (not the login's report of every skill)
        Integer before = seenXp.put(event.getSkill(), event.getXp());
        if (before != null && event.getXp() > before) {
            lastTrained = event.getSkill();
            milestonesPassed(event.getSkill(), before, event.getXp());
        }
    }

    private final Alerts alerts;
    private final PlannerGeLimits geLimits;

    /** An item's GE buy limit (0: unknown or not looked up yet). */
    int buyLimit(int itemId) {
        return geLimits == null ? 0 : geLimits.limit(itemId);
    }

    /** Your own price for an item from your trades this week (null: none, or the setting's off). */
    Long yourPrice(String name) {
        return geLimits == null || !config.plannerYourPrices() ? null : geLimits.yourPrice(name);
    }

    /** What you really spent and got for these items since then: {spent, brought in}. */
    long[] traded(java.util.Collection<String> names, long since) {
        return geLimits == null ? new long[2] : geLimits.traded(names, since);
    }

    /**
     * Your XP an hour in a skill from the Skilling Helper's tracker (it runs, and you're training it now), for a plan
     * linked to its goal; 0 otherwise. The Planner never shows a rate of its own: it times the plan by this.
     */
    int trackerXpRate(Skill skill) {
        return family == null || !canPlan() ? 0 : family.xpRate(skill);
    }

    /** Changes when a trade is recorded (or where an item came from). */
    int tradesVersion() {
        return (geLimits == null ? 0 : geLimits.tradesVersion()) * 31 + (origins == null ? 0 : origins.version());
    }

    // Where your items came from (bought, traded, got yourself); null in the preview
    @Inject
    private PlannerOrigins origins;

    /** Where an item you have came from, as far as it knows (null: nothing known). */
    PlannerOrigins.Origin origin(int itemId) {
        return origins == null || itemId <= 0 ? null : origins.of(itemId);
    }

    /**
     * You sold, alched or dropped something (client thread, from {@link PlannerOrigins}): counted on the plan
     * milestone that makes it (in the skill you're training it in: the one you're on, else the latest you've reached),
     * as Record sales... does by hand, so its money so far is what really happened.
     */
    void recordGone(int itemId, long quantity, long gp, PlannerOrigins.Gone how) {
        if (!canPlan() || itemManager == null || quantity <= 0) {
            return;
        }
        String name;
        try {
            name = itemManager.getItemComposition(itemId).getName();
        } catch (RuntimeException e) {
            return;
        }
        for (Skill skill : Skill.values()) {
            SkillPlans.Plan plan = plan(skill);
            if (plan == null) {
                continue;
            }
            int level = Experience.getLevelForXp(Math.max(0, xp(skill)));
            SkillPlans.Step best = null;
            for (SkillPlans.Step step : plan.steps) {
                if (step.action == null || !step.action.equalsIgnoreCase(name) || step.from > level) {
                    continue;
                }
                if (best == null || step.covers(level) || !best.covers(level) && step.from > best.from) {
                    best = step;
                }
            }
            if (best == null) {
                continue;
            }
            SkillPlans.Plan before = plan.copy();
            switch (how) {
                case SOLD:
                    // (what each went for, averaged with what was recorded before)
                    best.soldEach = ((best.soldEach > 0 ? best.soldEach : (double) gp / quantity) * best.sold + gp)
                        / (best.sold + quantity);
                    best.sold += quantity;
                    break;
                case ALCHED:
                    best.alchedEach = ((best.alchedEach > 0 ? best.alchedEach : (double) gp / quantity) * best.alched + gp)
                        / (best.alched + quantity);
                    best.alched += quantity;
                    break;
                default:
                    best.dropped += quantity;
                    break;
            }
            savePlanQuietly(skill, plan, before);
            view.update();
            return;
        }
    }

    /** Records by hand where some of an item came from: bought for {@code gp} in all, or got yourself. */
    void recordOrigin(int itemId, long quantity, long gp, boolean bought) {
        if (origins == null || itemId <= 0 || !canPlan()) {
            return;
        }
        if (bought) {
            origins.bought(itemId, quantity, gp);
        } else {
            origins.gathered(itemId, quantity);
        }
    }

    /** Clears what it knows of where an item came from. */
    void forgetOrigin(int itemId) {
        if (origins != null && itemId > 0 && canPlan()) {
            origins.forget(itemId);
        }
    }

    /** How many buy limits are known so far (for redrawing when one comes in). */
    int buyLimitsKnown() {
        return geLimits == null ? 0 : geLimits.known();
    }

    /** What's left of an item's buy limit right now (-1: its whole limit, or not known). */
    int buyLimitLeft(int itemId) {
        return geLimits == null ? -1 : geLimits.leftNow(itemId);
    }

    // Your plan's milestones ticked off as you pass them: a chat message for each one finished (with the next), and
    // one when you reach the plan's goal
    private void milestonesPassed(Skill skill, int before, int now) {
        SkillPlans.Plan plan = canPlan() ? plan(skill) : null;
        for (String message : milestoneMessages(skill, plan, before, now)) {
            alerts.chat("chatPlanMilestone", message);
        }
    }

    /** What to say when XP goes from {@code before} to {@code now}: each milestone ended between them, and the goal. */
    static List<String> milestoneMessages(Skill skill, SkillPlans.Plan plan, int before, int now) {
        List<String> out = new ArrayList<>();
        if (plan == null || now <= before) {
            return out;
        }
        int goal = Experience.getXpForLevel(Math.min(99, Math.max(2, plan.goal)));
        for (SkillPlans.Step step : plan.steps) {
            int end = Experience.getXpForLevel(Math.min(99, Math.max(2, step.to)));
            if (before < end && now >= end && end < goal) {
                SkillPlans.Step next = null;
                for (SkillPlans.Step other : plan.steps) {
                    if (other != step && other.from <= step.to && step.to < other.to && (next == null || other.from > next.from)) {
                        next = other;
                    }
                }
                out.add(skill.getName() + " plan: milestone done, " + step.action + " (" + step.from + "-" + step.to + ")."
                    + (next != null ? " Next: " + next.action + " to " + next.to + "." : ""));
            }
        }
        if (before < goal && now >= goal) {
            out.add(skill.getName() + " plan complete: level " + plan.goal + " reached!");
        }
        return out;
    }

    private final Map<Skill, Integer> seenXp = new EnumMap<>(Skill.class);
    private volatile Skill lastTrained;

    /** The skill whose open plan is pinned to the game screen (the plan overlay), or null: it follows your training. */
    Skill pinnedPlanSkill() {
        String saved = canPlan() ? configManager.getRSProfileConfiguration(GROUP, "plannerPinnedPlan") : null;
        for (Skill skill : Skill.values()) {
            if (skill.name().equals(saved)) {
                return skill;
            }
        }
        return null;
    }

    void setPinnedPlanSkill(Skill skill) {
        if (!canPlan()) {
            return;
        }
        if (skill == null) {
            configManager.unsetRSProfileConfiguration(GROUP, "plannerPinnedPlan");
        } else {
            configManager.setRSProfileConfiguration(GROUP, "plannerPinnedPlan", skill.name());
        }
    }

    /** The skill you last gained XP in (this session), or null. */
    Skill lastTrained() {
        return lastTrained;
    }

    /** For the preview: the skill being trained. */
    void setSampleTrained(Skill skill) {
        lastTrained = skill;
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
            // At a deposit box (the Woodcutting Guild's): what leaves your inventory goes into the bank, which it
            // doesn't show, so it's added to the bank as last seen
            Map<String, Integer> now = new HashMap<>();
            count(event.getItemContainer(), now);
            if (depositBox && lastCarried != null) {
                Map<String, Integer> in = SkillGoals.deposited(lastCarried, now);
                if (!in.isEmpty()) {
                    Map<String, Integer> more = new HashMap<>(bank);
                    in.forEach((name, count) -> more.merge(name, count, Integer::sum));
                    bank = more;
                    if (configManager.getRSProfileKey() != null) {
                        SkillGoals.saveBank(configManager, more);
                    }
                    view.update();
                }
            }
            lastCarried = now;
        }
    }

    // A deposit box is open, and the inventory as last seen (what leaves it there goes into the bank)
    private boolean depositBox;
    private Map<String, Integer> lastCarried;

    @Subscribe
    public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded event) {
        if (event.getGroupId() == net.runelite.api.gameval.InterfaceID.BANK_DEPOSITBOX) {
            depositBox = true;
        }
    }

    @Subscribe
    public void onWidgetClosed(net.runelite.api.events.WidgetClosed event) {
        if (event.getGroupId() == net.runelite.api.gameval.InterfaceID.BANK_DEPOSITBOX) {
            depositBox = false;
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
            } else if (event.getKey().startsWith("goal") || event.getKey().startsWith(SkillPlans.PLAN_KEY)
                || event.getKey().startsWith(SkillPlans.PLANS_KEY) || event.getKey().startsWith(SkillPlans.OPEN_KEY)) {
                // A goal set (or plan exported or removed) in the Skilling Helper or here: the target follows it
                view.update();
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // An ironman account (any kind): its plans get everything themselves (kept, so logged out still knows)
        if (client.getGameState() == GameState.LOGGED_IN && configManager.getRSProfileKey() != null) {
            boolean iron = client.getVarbitValue(VarbitID.IRONMAN) != 0;
            if (accountIron == null || accountIron != iron) {
                accountIron = iron;
                configManager.setRSProfileConfiguration(GROUP, "accountIronman", String.valueOf(iron));
                view.update();
            }
        }
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
        if (!wantedPrices.isEmpty() || !wantedAlch.isEmpty()) {
            for (Integer id : new ArrayList<>(wantedPrices)) {
                idPrices.put(id, (int) Math.max(0, Math.min(Integer.MAX_VALUE, itemManager.getItemPrice(id))));
                wantedPrices.remove(id);
            }
            for (Integer id : new ArrayList<>(wantedAlch)) {
                alchPrices.put(id, Math.max(0, itemManager.getItemComposition(id).getHaPrice()));
                wantedAlch.remove(id);
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

    /** An item's high alchemy value by id (looked up on the next game tick the first time; 0 until then or none). */
    int alchPrice(int itemId) {
        if (itemId <= 0) {
            return 0;
        }
        Integer value = alchPrices.get(itemId);
        if (value == null) {
            if (itemManager != null) {
                wantedAlch.add(itemId);
            }
            return 0;
        }
        return value;
    }

    /** What the plans count products as worth (Sell By). */
    PlannerConfig.SellBy sellBy() {
        return config.plannerSellBy();
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

    // Goals are the Skilling Helper's: the Planner only reads or sets them while it runs too (installed and on), and
    // never looks at them otherwise
    int goal(Skill skill) {
        return configManager.getRSProfileKey() == null || !trackerInstalled() ? 0 : SkillGoals.goal(configManager, skill);
    }

    int goalStart(Skill skill) {
        return configManager.getRSProfileKey() == null || !trackerInstalled() ? 0 : SkillGoals.goalStart(configManager, skill);
    }

    /**
     * What the goal is set to train in the Skilling Helper: its Track for goal pick ("Blessed bone shards") or the
     * method picked on its card; null for neither (it follows the suggestion, or an exported plan).
     */
    /** What the Skilling Helper's goal follows for the skill (its Follow), or null for the usual order. */
    SkillGoals.Follow follow(Skill skill) {
        return canPlan() && trackerInstalled() ? SkillGoals.follow(configManager, skill) : null;
    }

    void setFollow(Skill skill, SkillGoals.Follow follow) {
        if (canPlan() && trackerInstalled()) {
            SkillGoals.setFollow(configManager, skill, follow);
        }
    }

    String goalPick(Skill skill) {
        if (!canPlan() || !trackerInstalled()) {
            return null;
        }
        String target = configManager.getRSProfileConfiguration(GROUP, "goalTarget." + skill.name());
        if (target != null && !target.isEmpty()) {
            String name = target.substring(target.indexOf(':') + 1).split("\\|", -1)[0].trim();
            return name.isEmpty() ? null : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        String method = configManager.getRSProfileConfiguration(GROUP, "goalMethod." + skill.name());
        return method == null || method.isEmpty() ? null : method;
    }

    /** Sets the skill's goal as the tracker's Set Goal does (its goal bar starts from your XP now). */
    void setGoal(Skill skill, int goalXp, int xpNow) {
        if (configManager.getRSProfileKey() != null && trackerInstalled()) {
            SkillGoals.setGoal(configManager, skill, goalXp, xpNow);
        }
    }

    /** The Actions list's order (level, low to high, at first). */
    Sort sort() {
        String saved = configManager.getConfiguration(GROUP, SORT_KEY);
        for (Sort sort : Sort.values()) {
            if (sort.name().equals(saved)) {
                return sort;
            }
        }
        return Sort.LEVEL_UP;
    }

    void setSort(Sort sort) {
        configManager.setConfiguration(GROUP, SORT_KEY, sort.name());
    }

    /**
     * Rows in this order. By level they're in level order whatever you can do; otherwise what you can do comes
     * first (in that order), then what you can't yet, lowest level first. Ties go by level, then name.
     */
    static List<Row> sorted(List<Row> rows, Sort sort) {
        List<Row> out = new ArrayList<>(rows);
        java.util.Comparator<Row> byLevel = java.util.Comparator.comparingInt((Row r) -> r.action.level)
            .thenComparing(r -> r.action.name);
        java.util.Comparator<Row> order;
        switch (sort) {
            case LEVEL_UP:
                order = byLevel;
                break;
            case LEVEL_DOWN:
                order = byLevel.reversed();
                break;
            case XP_EACH:
                order = java.util.Comparator.comparingDouble((Row r) -> -r.xpEach).thenComparing(byLevel);
                break;
            case XP_HOUR:
                // (no guide rate last)
                order = java.util.Comparator.comparingInt((Row r) -> -r.xpHour).thenComparing(byLevel);
                break;
            case CHEAPEST:
                // Most gp per XP back (a smaller cost) first; no price last
                order = java.util.Comparator.comparingDouble((Row r) -> r.perXp() == null ? Double.MAX_VALUE : -r.perXp())
                    .thenComparing(byLevel);
                break;
            case PROFIT:
                order = java.util.Comparator.comparingDouble((Row r) -> r.profit == null ? Double.MAX_VALUE : -r.profit)
                    .thenComparing(byLevel);
                break;
            case NAME:
                order = java.util.Comparator.comparing((Row r) -> r.action.name.toLowerCase(Locale.ROOT));
                break;
            case CAN_DO:
            default:
                // What you can do, then what you can't yet: each lowest level first
                order = byLevel;
                break;
        }
        if (sort != Sort.LEVEL_UP && sort != Sort.LEVEL_DOWN && sort != Sort.NAME) {
            java.util.Comparator<Row> inner = order;
            order = (a, b) -> a.locked != b.locked ? (a.locked ? 1 : -1) : a.locked ? byLevel.compare(a, b) : inner.compare(a, b);
        }
        out.sort(order);
        return out;
    }

    /** Whether the open plan is unfolded in the Plan view (it starts unfolded). */
    boolean planExpanded() {
        return !"false".equals(configManager.getConfiguration(GROUP, "plannerCalcPlanExpanded"));
    }

    void setPlanExpanded(boolean expanded) {
        configManager.setConfiguration(GROUP, "plannerCalcPlanExpanded", expanded);
    }

    /** Whether the plan bar at the foot of the Calculator is open (it starts open). */
    boolean dockOpen() {
        return !"false".equals(configManager.getConfiguration(GROUP, DOCK_KEY));
    }

    void setDockOpen(boolean open) {
        configManager.setConfiguration(GROUP, DOCK_KEY, open);
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

    /** The money set aside for a skill's plan, in gp (0: all your coins). Kept per account. */
    long planBudget(Skill skill) {
        if (!canPlan()) {
            return 0;
        }
        String saved = configManager.getRSProfileConfiguration(GROUP, "planBudget." + skill.name());
        try {
            return saved == null ? 0 : Math.max(0, Long.parseLong(saved));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    void setPlanBudget(Skill skill, long gp) {
        if (!canPlan()) {
            return;
        }
        if (gp <= 0) {
            configManager.unsetRSProfileConfiguration(GROUP, "planBudget." + skill.name());
            configManager.unsetRSProfileConfiguration(GROUP, "planBudgetAt." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(GROUP, "planBudget." + skill.name(), String.valueOf(gp));
            configManager.setRSProfileConfiguration(GROUP, "planBudgetAt." + skill.name(), String.valueOf(System.currentTimeMillis()));
        }
    }

    /** When the budget was set (its real spending counts from then), or 0. */
    long planBudgetSince(Skill skill) {
        String saved = canPlan() ? configManager.getRSProfileConfiguration(GROUP, "planBudgetAt." + skill.name()) : null;
        try {
            return saved == null ? 0 : Long.parseLong(saved);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The plan view's sections folded away ("milestones", "materials", "money"), as a comma list. */
    String planFolds() {
        String saved = configManager.getConfiguration(GROUP, "planFolded");
        return saved == null ? "" : saved;
    }

    boolean planFolded(String section) {
        return ("," + planFolds() + ",").contains("," + section + ",");
    }

    void setPlanFolded(String section, boolean folded) {
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        for (String part : planFolds().split(",")) {
            if (!part.isEmpty()) {
                all.add(part);
            }
        }
        if (folded) {
            all.add(section);
        } else {
            all.remove(section);
        }
        configManager.setConfiguration(GROUP, "planFolded", String.join(",", all));
    }

    /** Coins in your bank (as last opened) and inventory. */
    long coins() {
        return owned().getOrDefault("coins", 0);
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

    /** The skill's saved plans (copies: change them through the methods here). */
    List<SkillPlans.Plan> plans(Skill skill) {
        return canPlan() ? SkillPlans.plans(configManager, skill) : new ArrayList<>();
    }

    /** Which of {@link #plans} is open, or -1 when there are none. */
    int openIndex(Skill skill) {
        return canPlan() ? SkillPlans.open(configManager, skill, plans(skill).size()) : -1;
    }

    /** The open plan (the one being edited, that Add to plan adds to), or null when there are none. */
    SkillPlans.Plan plan(Skill skill) {
        List<SkillPlans.Plan> plans = plans(skill);
        int open = canPlan() ? SkillPlans.open(configManager, skill, plans.size()) : -1;
        return open < 0 ? null : plans.get(open);
    }

    /** Saves the open plan (a new one when there are none); null deletes it, opening the one before it. */
    void savePlan(Skill skill, SkillPlans.Plan plan) {
        if (!canPlan()) {
            return;
        }
        List<SkillPlans.Plan> plans = plans(skill);
        int open = SkillPlans.open(configManager, skill, plans.size());
        if (plan == null) {
            if (open >= 0) {
                plans.remove(open);
            }
            SkillPlans.savePlans(configManager, skill, plans, Math.max(0, open - 1));
        } else if (open < 0) {
            plans.add(plan);
            SkillPlans.savePlans(configManager, skill, plans, 0);
        } else {
            // (a change to the plan you sent stays a draft: it's still the one you sent)
            plan.sent = plans.get(open).sent;
            plans.set(open, plan);
            SkillPlans.savePlans(configManager, skill, plans, open);
        }
    }

    /** Adds a plan and opens it. */
    void newPlan(Skill skill, SkillPlans.Plan plan) {
        if (canPlan()) {
            List<SkillPlans.Plan> plans = plans(skill);
            plans.add(plan);
            SkillPlans.savePlans(configManager, skill, plans, plans.size() - 1);
        }
    }

    /** Opens one of the saved plans. */
    void openPlan(Skill skill, int index) {
        if (canPlan()) {
            SkillPlans.savePlans(configManager, skill, plans(skill), index);
        }
    }

    /** Deletes one of the saved plans (the open one stays open, or the one before it if it was this). */
    void deletePlan(Skill skill, int index) {
        if (!canPlan()) {
            return;
        }
        List<SkillPlans.Plan> plans = plans(skill);
        if (index < 0 || index >= plans.size()) {
            return;
        }
        int open = SkillPlans.open(configManager, skill, plans.size());
        plans.remove(index);
        SkillPlans.savePlans(configManager, skill, plans, open > index ? open - 1 : open == index ? Math.max(0, index - 1) : open);
    }

    /** Replaces one of the saved plans (renamed), keeping which is open. */
    void replacePlan(Skill skill, int index, SkillPlans.Plan plan) {
        if (!canPlan()) {
            return;
        }
        List<SkillPlans.Plan> plans = plans(skill);
        if (index >= 0 && index < plans.size()) {
            plan.sent = plans.get(index).sent;
            plans.set(index, plan);
            SkillPlans.savePlans(configManager, skill, plans, SkillPlans.open(configManager, skill, plans.size()));
        }
    }

    /** The skills with saved plans (for the list of other skills' plans), and how many each has. */
    Map<Skill, Integer> planCounts(List<Skill> skills) {
        Map<Skill, Integer> out = new java.util.LinkedHashMap<>();
        for (Skill skill : skills) {
            int count = plans(skill).size();
            if (count > 0) {
                out.put(skill, count);
            }
        }
        return out;
    }

    /** The plan exported to the tracker for this skill, or null. */
    SkillPlans.Plan trackedPlan(Skill skill) {
        return canPlan() && trackerInstalled() ? SkillPlans.tracked(configManager, skill) : null;
    }

    /** Whether this plan is what the tracker has (exported and unchanged since). */
    boolean isTracked(Skill skill, SkillPlans.Plan plan) {
        return canPlan() && trackerInstalled() && SkillPlans.isTracked(configManager, skill, plan);
    }

    /** Saves where each supply milestone stands, for the Skilling Helper (only when it changed). */
    void saveSupplyStates(Skill skill, Map<String, SkillPlans.SupplyState> states) {
        if (configManager.getRSProfileKey() != null) {
            SkillPlans.setSupplyStates(configManager, skill, states);
        }
    }

    /**
     * Saves the tracker's copy of a plan after the Planner moved its supply milestones to follow your supplies (the link
     * a send or take made stays live for them, even while your own copy has unsent edits).
     */
    void saveTrackedSupplies(Skill skill, SkillPlans.Plan tracked) {
        if (canPlan() && trackerInstalled()) {
            configManager.setRSProfileConfiguration(GROUP, SkillPlans.TRACKED_KEY + skill.name(), SkillPlans.encode(tracked));
        }
    }

    /**
     * Saves a change the Planner made by itself (a supply milestone's end following its supplies): when the tracker had
     * the plan as it was, it gets the change too, so it isn't left as an unsent edit of yours.
     */
    void savePlanQuietly(Skill skill, SkillPlans.Plan plan, SkillPlans.Plan before) {
        boolean wasTracked = isTracked(skill, before);
        savePlan(skill, plan);
        if (wasTracked) {
            configManager.setRSProfileConfiguration(GROUP, SkillPlans.TRACKED_KEY + skill.name(), SkillPlans.encode(plan));
        }
    }

    /** Sends the plan at {@code index} of the skill's list to the tracker; it becomes the one you sent. */
    void export(Skill skill, int index, SkillPlans.Plan plan, int xpNow) {
        if (canPlan()) {
            SkillPlans.export(configManager, skill, plan, xpNow);
            markSent(skill, index);
        }
    }

    void untrack(Skill skill) {
        if (canPlan()) {
            SkillPlans.untrack(configManager, skill);
            markSent(skill, -1);
        }
    }

    /** The plan you sent to the tracker (its index in the list), or -1. */
    int sentIndex(Skill skill) {
        List<SkillPlans.Plan> plans = plans(skill);
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).sent) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Puts the plan you sent back as the tracker has it (your unsent changes go; its name stays), or does nothing when
     * the tracker has none.
     */
    void undoChanges(Skill skill, int index) {
        SkillPlans.Plan tracked = trackedPlan(skill);
        List<SkillPlans.Plan> plans = plans(skill);
        if (tracked == null || index < 0 || index >= plans.size()) {
            return;
        }
        SkillPlans.Plan back = tracked.copy();
        SkillPlans.Plan old = plans.get(index);
        back.name = old.name;
        back.fromGoal = old.fromGoal;
        back.fromPick = old.fromPick;
        replacePlan(skill, index, back);
    }

    /**
     * Replaces the plan at {@code index} with the tracker's (its milestones and goal; the plan's name stays): the one
     * your goal follows becomes your plan, and the one sent.
     */
    void takeTracked(Skill skill, int index) {
        undoChanges(skill, index);
        markSent(skill, index);
    }

    /** The last plan in the list (one just added) becomes the one sent. */
    void markSentLast(Skill skill) {
        if (canPlan()) {
            markSent(skill, plans(skill).size() - 1);
        }
    }

    // Only one plan a skill is the one sent
    private void markSent(Skill skill, int index) {
        List<SkillPlans.Plan> plans = plans(skill);
        boolean changed = false;
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).sent != (i == index)) {
                plans.get(i).sent = i == index;
                changed = true;
            }
        }
        if (changed) {
            SkillPlans.savePlans(configManager, skill, plans, SkillPlans.open(configManager, skill, plans.size()));
        }
    }

    /** The skill's own Training Focus from the shared group says "AFK / Other" (else the fastest methods). */
    boolean otherMethods(Skill skill) {
        return canPlan() && trackerInstalled()
            && "OTHER".equals(configManager.getRSProfileConfiguration(GROUP, "trainingFocus." + skill.name()));
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
        /**
         * Seconds to the target at the training guide's rate for a matching method, else at an estimate (see
         * {@link #estimated}), or 0 when there's neither.
         */
        long seconds;
        /** That method's XP an hour (or the estimate's). */
        int xpHour;
        /** The rate is an estimate (a typical count an hour times the XP each, {@link PlannerRates}), not a guide's. */
        boolean estimated;
        /** The pace the rate is ({@link PlannerRates.Way#name}, or {@link PlannerRates#GUIDE}), or null with no rate. */
        String pace;
        /** What making one earns (product less materials, GE): negative when it costs; null when not known. */
        Double profit;
        /** What one's materials cost (GE), or null (no recipe, or a material without a price). */
        Double costEach;
        /**
         * What one product gets you (GE price less its 2% tax, or its high alch value less a nature rune, as
         * {@link #selling} says); null when it's nothing that sells.
         */
        Double earnEach;
        /** One sold on the GE (after tax), and one high alched (less a nature rune); null when it has none. */
        Double geEach;
        Double alchEach;
        /** {@link #earnEach} is the alch value (alching pays more, or Sell By says to). */
        boolean alchWins;
        /** Its products are dropped (powerlevelling): worth nothing. */
        boolean dropped;
        /** Its estimated rate is the quicker dropping one (no bank trips). */
        boolean droppedFaster;
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

        /** How many of its products are kept for other plans (not sold). */
        long kept;

        /** The products' worth for this many (less what's kept for other plans), or null. */
        Double earn(int count) {
            return earnEach == null ? null : earnEach * Math.max(0, count - kept);
        }
    }

    /** Gathering that's quicker dropping than banking, and by how much (no bank trips). */
    private static final Set<Skill> DROP_FASTER = java.util.EnumSet.of(Skill.WOODCUTTING, Skill.MINING, Skill.FISHING);
    static final double DROP_SPEEDUP = 1.2;

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
        return row(skill, action, on, xp, target, level, recipes, training, have, price, itemPrice, null, null);
    }

    // ... at this pace (a step's; null: the guide's rate, else the normal pace)
    private static Row row(Skill skill, CalculatorData.Action action, Collection<CalculatorData.Bonus> on, int xp, int target,
                           int level, RecipeData recipes, TrainingData training, Map<String, Integer> have,
                           ToIntFunction<String> price, IntUnaryOperator itemPrice, String pace, String sellBy) {
        PlannerConfig.SellBy sell = sellBy == null ? null : "ALCH".equals(sellBy) ? PlannerConfig.SellBy.ALCH
            : "GE".equals(sellBy) ? PlannerConfig.SellBy.GE : "DROP".equals(sellBy) ? PlannerConfig.SellBy.DROP : null;
        Row row = new Row(action, CalculatorData.xpEach(action, on), CalculatorData.needed(action, on, xp, target),
            action.level > level);
        List<RecipeData.Recipe> made = recipes == null ? Collections.emptyList() : recipesFor(skill, action, recipes);
        if (!made.isEmpty()) {
            for (RecipeData.Recipe recipe : made) {
                int per = perMake(recipe, action);
                row.have = Math.max(row.have, recipe.makeable(have) * per);
                Double cost = cost(recipe, price);
                if (cost == null) {
                    continue;
                }
                // The action is one item; a recipe can make several at once (10 darts from 10 tips and 10 feathers)
                cost /= per;
                Sale sale = consumed(recipe) || RecipeData.builtInPlace(skill, recipe) ? new Sale(0, 0, sell)
                    : new Sale(price.applyAsInt(recipe.name), selling.alch(recipe.name), sell);
                double worth = sale.worth();
                double profit = worth - cost;
                if (row.profit == null || profit > row.profit) {
                    row.profit = profit;
                    row.costEach = cost;
                    row.earnEach = worth > 0 ? worth : null;
                    sale.fill(row);
                }
            }
        } else {
            row.have = have.getOrDefault(action.name.toLowerCase(Locale.ROOT), 0);
            if (GATHERING.contains(skill)) {
                int worth = action.icon > 0 ? itemPrice.applyAsInt(action.icon) : 0;
                if (worth <= 0) {
                    worth = price.applyAsInt(action.name);
                }
                Sale sale = new Sale(worth, selling.alch(action.name), sell);
                row.earnEach = sale.worth() > 0 ? sale.worth() : null;
                sale.fill(row);
            }
        }
        TrainingData.Method method = training == null ? null : rated(training, skill, action, level);
        PlannerRates.Way way = pace == null || PlannerRates.GUIDE.equals(pace) ? null
            : PlannerRates.way(PlannerRates.ways(skill, action, made), pace);
        if (method != null && way == null) {
            // (a minigame's rate by your level: the wiki's at the levels it gives, in a straight line between)
            row.xpHour = method.xpHourAt(Math.max(level, action.level));
            row.pace = PlannerRates.GUIDE;
        } else {
            if (way == null) {
                List<PlannerRates.Way> ways = PlannerRates.ways(skill, action, made);
                way = ways.isEmpty() ? null : ways.get(0);
            }
            if (way != null && row.xpEach > 0) {
                double perHour = way.perHour;
                // Dropping what you gather (powerlevelling) skips the bank trips: about a fifth quicker for logs, ore
                // and fish (the normal pace counts banking; the faster ways already drop)
                PlannerConfig.SellBy by = sell != null ? sell : selling.by;
                if (by == PlannerConfig.SellBy.DROP && DROP_FASTER.contains(skill) && PlannerRates.NORMAL.equals(way.name)) {
                    perHour *= DROP_SPEEDUP;
                    row.droppedFaster = true;
                }
                row.xpHour = (int) Math.round(perHour * row.xpEach);
                row.estimated = row.xpHour > 0;
                row.pace = row.estimated ? way.name : null;
            }
        }
        if (row.xpHour > 0 && target > xp) {
            row.seconds = (long) Math.ceil((target - xp) * 3600.0 / row.xpHour);
        }
        return row;
    }

    /**
     * The paces a step of this action can be timed at, with their XP an hour (the bonuses ticked): the wiki guide's
     * rate when it has one, then the estimates (the normal pace, then any faster ways).
     */
    static Map<String, Integer> paces(Skill skill, CalculatorData.Action action, Collection<CalculatorData.Bonus> on, int level,
                                      RecipeData recipes, TrainingData training) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        TrainingData.Method method = training == null ? null : rated(training, skill, action, level);
        if (method != null) {
            out.put(PlannerRates.GUIDE, method.xpHourAt(Math.max(level, action.level)));
        }
        List<RecipeData.Recipe> made = recipes == null ? Collections.emptyList() : recipesFor(skill, action, recipes);
        double each = CalculatorData.xpEach(action, on);
        for (PlannerRates.Way way : PlannerRates.ways(skill, action, made)) {
            out.put(way.name, (int) Math.round(way.perHour * each));
        }
        return out;
    }

    /**
     * How the plans count products: the Sell By setting, each item's high alch value and a nature rune's
     * price. Set by the Calculator before it works things out; on its own (the tests), GE prices only.
     */
    static final class Selling {
        static final Selling GE_ONLY = new Selling(PlannerConfig.SellBy.GE, name -> 0, 0);

        final PlannerConfig.SellBy by;
        final ToIntFunction<String> alchValue;
        final int nature;

        Selling(PlannerConfig.SellBy by, ToIntFunction<String> alchValue, int nature) {
            this.by = by;
            this.alchValue = alchValue;
            this.nature = nature;
        }

        /** One alched: its alch value less the nature rune it takes, or 0 (none, or it isn't worth the rune). */
        double alch(String name) {
            int value = alchValue.applyAsInt(name);
            return value > nature ? value - nature : 0;
        }
    }

    /** The Calculator's selling now ({@link Selling}). */
    static volatile Selling selling = Selling.GE_ONLY;

    // One product's sale both ways, and which counts
    private static final class Sale {
        final double ge;
        final double alch;
        // The milestone's own choice, else the setting's
        final PlannerConfig.SellBy by;

        Sale(int gePrice, double alch, PlannerConfig.SellBy by) {
            this.ge = gePrice > 0 ? afterTax(gePrice) : 0;
            this.alch = alch;
            this.by = by != null ? by : selling.by;
        }

        boolean alchWins() {
            return alch > 0 && by != PlannerConfig.SellBy.GE && by != PlannerConfig.SellBy.DROP
                && (by == PlannerConfig.SellBy.ALCH || alch > ge);
        }

        double worth() {
            // (dropped to powerlevel: worth nothing)
            return by == PlannerConfig.SellBy.DROP ? 0 : alchWins() ? alch : ge;
        }

        void fill(Row row) {
            row.geEach = ge > 0 ? ge : null;
            row.alchEach = alch > 0 ? alch : null;
            row.alchWins = alchWins();
            row.dropped = by == PlannerConfig.SellBy.DROP;
        }
    }

    /**
     * What selling one at this price gets you: the Grand Exchange keeps 2% of each sale (rounded down, so nothing on
     * one under 50 gp), at most 5M an item.
     */
    static double afterTax(double price) {
        if (price <= 0) {
            return price;
        }
        return price - Math.min(Math.floor(price * 0.02), 5_000_000);
    }

    /**
     * How many of the action's items one go of the recipe makes: darts, arrows and bolts come 10 or 15 at a time
     * (the recipe gives the XP of all of them, the Calculator's action one's). 1 for everything else.
     */
    static int perMake(RecipeData.Recipe recipe, CalculatorData.Action action) {
        if (action.xp <= 0 || recipe.xp <= 0) {
            return 1;
        }
        double ratio = recipe.xp / action.xp;
        return ratio >= 1.5 ? (int) Math.round(ratio) : 1;
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
        // (several recipes can share a name, one per kind of feather: count each name once)
        Set<String> counted = new java.util.HashSet<>();
        for (RecipeData.Recipe recipe : mine) {
            if (counted.add(recipe.name.toLowerCase())) {
                times += made.getOrDefault(recipe.name.toLowerCase(), 0) * perMake(recipe, action);
            }
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
                // (a go of the recipe: all the items it makes at once)
                each.put(recipe.name.toLowerCase(Locale.ROOT), CalculatorData.xpEach(action, on) * perMake(recipe, action));
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
        /** How far through it you are, 0 (not started) to 1 (done). */
        double progress;
        /** Actions for the whole milestone, start to end (0 without an action): with {@code row.needed}, how many are done. */
        long total;
        /** Its time is at your XP an hour from the Skilling Helper (the milestone you're on, its plan linked to your goal). */
        boolean yourRate;
        /** How much of its pace the GE buy limits allow for its materials (1: all of it), and the scarcest material. */
        double buyable = 1;
        String scarce;

        StepRow(SkillPlans.Step step, CalculatorData.Action action, Row row, int xp, boolean done) {
            this.step = step;
            this.action = action;
            this.row = row;
            this.xp = xp;
            this.done = done;
        }

        /** Actions done of it so far (0 without an action). */
        long made() {
            return row == null ? 0 : Math.max(0, total - row.needed);
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
        /** Steps whose time is an estimate ({@link Row#estimated}). */
        int estimated;
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
        // (a supply milestone ends partway into a level, exactly where its supplies take you: the one after it starts
        // there, so no XP is counted twice)
        SkillPlans.Step previous = null;
        for (SkillPlans.Step step : plan.steps) {
            int start = Math.max(xpNow, Experience.getXpForLevel(step.from));
            if (step.gotAll || step.covered) {
                // (all its supplies got, or a level the supplies before it take you past: done, and no XP of its own)
                out.add(new StepRow(step, CalculatorData.named(calc, step.action), null, 0, true));
                out.get(out.size() - 1).progress = 1;
                continue;
            }
            if (previous != null && previous.endXp > 0 && previous.to == step.from) {
                start = (int) Math.max(start, Math.min(Experience.MAX_SKILL_XP, previous.endXp));
            }
            int end = step.endXp > 0 ? (int) Math.min(Experience.MAX_SKILL_XP, step.endXp)
                : Experience.getXpForLevel(Math.min(99, step.to));
            end = Math.max(end, start);
            previous = step;
            CalculatorData.Action action = CalculatorData.named(calc, step.action);
            boolean done = xpNow >= end || step.gotAll;
            Row row = action == null ? null : row(skill, action, on, start, end, Math.max(step.from,
                CalculatorData.level(xpNow, false)), recipes, training, Collections.emptyMap(), price, itemPrice, step.pace, step.sellBy);
            if (row != null) {
                // (what it makes for other skills' plans isn't sold)
                row.kept = Math.min(step.supplied(), row.needed);
            }
            StepRow s = new StepRow(step, action, row, done ? 0 : Math.max(0, end - start), done);
            int from = Experience.getXpForLevel(Math.max(1, step.from));
            s.progress = done ? 1 : end > from ? Math.max(0, Math.min(1, (double) (xpNow - from) / (end - from))) : 0;
            s.total = action == null ? 0 : CalculatorData.needed(action, on, from, end);
            out.add(s);
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
                if (s.row.estimated) {
                    totals.estimated++;
                }
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
     * Adds a milestone for this action (right-click > Add to plan) from its own level (or yours, if later): inside a
     * milestone, it takes over from there to where that one ended (Atlatl darts at 74 in Adamant darts 67-81: Adamant
     * 67-74, Atlatl 74-81); in a gap, it fills it. One that would start where a milestone starts goes after the
     * milestones there instead, so nothing is replaced. False when it can't fit before the plan's goal.
     */
    static boolean addStep(SkillPlans.Plan plan, CalculatorData.Action action, double xpEach, String method, int level) {
        int start = Math.max(Math.max(1, level), action.level);
        SkillPlans.Step covering;
        while (true) {
            if (start >= plan.goal) {
                return false;
            }
            covering = null;
            for (SkillPlans.Step step : plan.steps) {
                if (step.from <= start && start < step.to) {
                    covering = step;
                }
            }
            if (covering == null || (covering.from < start && covering.supplies.isEmpty())) {
                break;
            }
            start = covering.to;
        }
        int end = plan.goal;
        if (covering != null) {
            end = covering.to;
            covering.to = start;
        } else {
            for (SkillPlans.Step step : plan.steps) {
                if (step.from > start) {
                    end = Math.min(end, step.from);
                }
            }
        }
        plan.steps.add(new SkillPlans.Step(action.name, start, end, method, xpEach));
        plan.steps.sort((x, y) -> Integer.compare(x.from, y.from));
        return true;
    }

    /** A material (or tool) a plan needs: how many in all, and how many you have. */
    static final class Need {
        final String name;
        long needed;
        final int have;
        final boolean tool;

        Need(String name, long needed, int have, boolean tool) {
            this.name = name;
            this.needed = needed;
            this.have = have;
            this.tool = tool;
        }

        boolean enough() {
            return have >= needed;
        }
    }

    /**
     * Everything the plan's milestones still need, added up: each milestone's actions to its end, in goes of its
     * recipe (10 darts a go), times the recipe's materials; and the tools. For an action with several recipes (one per
     * kind of feather) the one you have the most goes of. Gathering milestones need nothing. {@code owned}: lower-case
     * names to how many you carry and bank.
     */
    static List<Need> planMaterials(Skill skill, List<StepRow> steps, RecipeData recipes, Map<String, Integer> owned) {
        Map<String, Need> out = new java.util.LinkedHashMap<>();
        for (StepRow s : steps) {
            if (s.done || s.row == null || s.action == null || s.row.needed <= 0) {
                continue;
            }
            RecipeData.Recipe best = null;
            int bestGoes = -1;
            for (RecipeData.Recipe recipe : recipesFor(skill, s.action, recipes)) {
                int goes = recipe.makeable(owned);
                if (goes > bestGoes) {
                    best = recipe;
                    bestGoes = goes;
                }
            }
            if (best == null) {
                continue;
            }
            int per = perMake(best, s.action);
            long goes = (s.row.needed + per - 1) / per;
            for (Map.Entry<String, Integer> material : best.materials.entrySet()) {
                String key = material.getKey().toLowerCase(Locale.ROOT);
                out.computeIfAbsent(key, k -> new Need(material.getKey(), 0, owned.getOrDefault(k, 0), false)).needed
                    += goes * material.getValue();
            }
            for (String tool : best.tools) {
                String key = "tool:" + tool.toLowerCase(Locale.ROOT);
                out.computeIfAbsent(key, k -> new Need(tool, 1, ownsTool(owned, tool) ? 1 : 0, true));
            }
        }
        return new ArrayList<>(out.values());
    }

    // ---- Getting materials yourself: gather or make one in another skill instead of buying it ----

    /** Where a material can come from: a skill and its action, gathered there (logs, ore, fish) or made by its recipe. */
    static final class Source {
        final Skill skill;
        final CalculatorData.Action action;
        final boolean gathered;

        Source(Skill skill, CalculatorData.Action action, boolean gathered) {
            this.skill = skill;
            this.action = action;
            this.gathered = gathered;
        }
    }

    /**
     * Where to get a material yourself instead of buying it, for a plan in {@code consumer}: a gathering skill with an
     * action of its name first (Woodcutting for logs), else a skill whose recipe makes it (Smithing for bars), not
     * one that uses it up (Firemaking's "Yew logs" burns them) or the plan's own skill. Null when there's none.
     */
    static Source sourceOf(String material, Skill consumer, CalculatorData data, RecipeData recipes) {
        for (Skill skill : GATHERING) {
            if (skill == consumer) {
                continue;
            }
            CalculatorData.Calc calc = data.calc(skill);
            CalculatorData.Action action = calc == null ? null : CalculatorData.named(calc, material);
            if (action != null) {
                return new Source(skill, action, true);
            }
        }
        for (Skill skill : data.skills()) {
            if (skill == consumer || GATHERING.contains(skill)) {
                continue;
            }
            CalculatorData.Action action = CalculatorData.named(data.calc(skill), material);
            if (action == null) {
                continue;
            }
            for (RecipeData.Recipe recipe : recipesFor(skill, action, recipes)) {
                boolean fromItself = recipe.materials.keySet().stream().anyMatch(m -> m.equalsIgnoreCase(material));
                if (!consumed(recipe) && !fromItself) {
                    return new Source(skill, action, false);
                }
            }
        }
        return null;
    }

    private volatile Boolean accountIron;

    /** What an hour of your time is worth (Suggest a plan's Balanced). */
    long hourWorth() {
        return Math.max(0, config.plannerHourWorth());
    }

    /** Whether plans are an ironman's (Plan As Ironman: from the account, or always, or never). */
    boolean ironman() {
        switch (config.plannerIronman()) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            default:
                if (accountIron != null) {
                    return accountIron;
                }
                String saved = configManager.getRSProfileKey() == null ? null
                    : configManager.getRSProfileConfiguration(GROUP, "accountIronman");
                return Boolean.parseBoolean(saved);
        }
    }

    // The materials you get yourself, per plan's skill ("FIREMAKING:yew logs"), kept per account; and the ones you buy
    // (only matters for an ironman, where getting it yourself is the default)
    private static final String GATHER_KEY = "planGather";
    private static final String BUY_KEY = "planBuy";

    /** The choices as saved: "SKILL:material" each. */
    List<String> gatherChoices() {
        String saved = canPlan() ? configManager.getRSProfileConfiguration(GROUP, GATHER_KEY) : null;
        List<String> out = new ArrayList<>();
        for (String part : saved == null ? new String[0] : saved.split(",")) {
            if (part.contains(":")) {
                out.add(part);
            }
        }
        return out;
    }

    private List<String> choices(String key) {
        String saved = canPlan() ? configManager.getRSProfileConfiguration(GROUP, key) : null;
        List<String> out = new ArrayList<>();
        for (String part : saved == null ? new String[0] : saved.split(",")) {
            if (part.contains(":")) {
                out.add(part);
            }
        }
        return out;
    }

    /** Whether a plan in {@code skill} gets this material itself (else it's bought): chosen, or an ironman's default. */
    boolean gathers(Skill skill, String material) {
        String entry = skill.name() + ":" + material.toLowerCase(Locale.ROOT);
        return choices(GATHER_KEY).contains(entry) || (ironman() && !choices(BUY_KEY).contains(entry));
    }

    void setGathers(Skill skill, String material, boolean gather) {
        if (!canPlan()) {
            return;
        }
        String entry = skill.name() + ":" + material.toLowerCase(Locale.ROOT);
        List<String> gathered = choices(GATHER_KEY);
        List<String> bought = choices(BUY_KEY);
        gathered.remove(entry);
        bought.remove(entry);
        (gather ? gathered : bought).add(entry);
        configManager.setRSProfileConfiguration(GROUP, GATHER_KEY, String.join(",", gathered));
        configManager.setRSProfileConfiguration(GROUP, BUY_KEY, String.join(",", bought));
    }

    /** What the choices are now, for redrawing when one changes. */
    String gatherState() {
        return ironman() + "|" + choices(GATHER_KEY) + "|" + choices(BUY_KEY);
    }

    /** One step of getting a material yourself: in this skill, this many of the item (its action that many times). */
    static final class Link {
        /** The skill, or null: nothing gathers or makes it (a drop, a shop: get it yourself). */
        final Skill skill;
        final CalculatorData.Action action;
        final String item;
        /** How many it takes in all, how many you have toward it, and how many times the action. */
        final long total;
        final long have;
        final long actions;
        final boolean gathered;
        /** 0 for the plan's own material, 1 for what that's made from, and on. */
        final int depth;

        Link(Skill skill, CalculatorData.Action action, String item, long total, long have, long actions, boolean gathered,
             int depth) {
            this.skill = skill;
            this.action = action;
            this.item = item;
            this.total = total;
            this.have = have;
            this.actions = actions;
            this.gathered = gathered;
            this.depth = depth;
        }

        long toGet() {
            return Math.max(0, total - have);
        }
    }

    /**
     * Everything getting {@code quantity} of a material yourself takes, all the way down: where it comes from (gathered,
     * or made, and then what that's made from, where {@code gathers} says that's got yourself too: an ironman's whole
     * chain, bars from Smithing and their ore from Mining). What you own counts first ({@code pool} is used up as it
     * goes). Four steps deep at most; a material nothing makes is a link with no skill.
     */
    static List<Link> chain(String material, long quantity, Skill consumer, CalculatorData data, RecipeData recipes,
                            Map<String, Integer> pool, java.util.function.BiPredicate<Skill, String> gathers) {
        List<Link> out = new ArrayList<>();
        chain(material, quantity, consumer, data, recipes, pool, gathers, 0, out);
        return out;
    }

    private static void chain(String material, long quantity, Skill consumer, CalculatorData data, RecipeData recipes,
                              Map<String, Integer> pool, java.util.function.BiPredicate<Skill, String> gathers, int depth,
                              List<Link> out) {
        String key = material.toLowerCase(Locale.ROOT);
        long have = Math.min(quantity, pool.getOrDefault(key, 0));
        pool.put(key, (int) Math.max(0, pool.getOrDefault(key, 0) - have));
        long need = quantity - have;
        // (the plan's own skill isn't a source for its own materials; further down the same skill can be: dart tips and
        // the bars they're made from are both Smithing)
        Source source = sourceOf(material, depth == 0 ? consumer : null, data, recipes);
        if (source == null) {
            out.add(new Link(null, null, material, quantity, have, 0, false, depth));
            return;
        }
        if (source.gathered) {
            out.add(new Link(source.skill, source.action, material, quantity, have, need, true, depth));
            return;
        }
        RecipeData.Recipe recipe = null;
        for (RecipeData.Recipe r : recipesFor(source.skill, source.action, recipes)) {
            if (!consumed(r)) {
                recipe = r;
                break;
            }
        }
        if (recipe == null) {
            out.add(new Link(null, null, material, quantity, have, 0, false, depth));
            return;
        }
        int per = perMake(recipe, source.action);
        long goes = (need + per - 1) / per;
        out.add(new Link(source.skill, source.action, material, quantity, have, goes, false, depth));
        if (depth >= 3 || goes <= 0) {
            return;
        }
        for (Map.Entry<String, Integer> sub : recipe.materials.entrySet()) {
            if (gathers.test(source.skill, sub.getKey()) && sourceOf(sub.getKey(), null, data, recipes) != null) {
                chain(sub.getKey(), goes * sub.getValue(), source.skill, data, recipes, pool, gathers, depth + 1, out);
            }
        }
    }

    // ---- The money plan: buying, making and selling within a budget ----

    /** One thing to buy for a milestone. */
    static final class Buy {
        final String name;
        final long quantity;
        final long cost;
        /** Its buy limit every 4 hours (0: not known). */
        final int limit;

        Buy(String name, long quantity, long cost, int limit) {
            this.name = name;
            this.quantity = quantity;
            this.cost = cost;
            this.limit = limit;
        }
    }

    /** A milestone's money: what to buy, in how many rounds of buy, make and sell, and the budget after it. */
    static final class MoneyStep {
        final StepRow step;
        final List<Buy> buys = new ArrayList<>();
        /** What buying it all costs, and what selling the products gets you (after tax). */
        long cost;
        long earn;
        /** Rounds: the most of the budget's and the buy limits'. */
        long rounds;
        long limitRounds;
        long cashRounds;
        /** Cash one round needs. */
        long perRound;
        long budgetBefore;
        long budgetAfter;
        /** The budget can't carry it (not even a round at a time). */
        boolean runsOut;

        MoneyStep(StepRow step) {
            this.step = step;
        }

        long loss() {
            return cost - earn;
        }
    }

    /** The whole money plan: each milestone's, the most cash needed at once, and the time the buy limits take at least. */
    static final class MoneyPlan {
        long budget;
        final List<MoneyStep> steps = new ArrayList<>();
        long peak;
        long loss;
        long limitHours;
        /** The milestone the budget runs out at, or null. */
        MoneyStep runsOut;

        long end() {
            return steps.isEmpty() ? budget : steps.get(steps.size() - 1).budgetAfter;
        }
    }

    /**
     * Plans the money for the milestones still to do, in order, within {@code budget}: each one buys what it needs that
     * you don't own (owned materials are used up milestone by milestone), makes and sells in rounds, as many as the buy
     * limits (one round a 4 hours each) and the budget need: a round buys only what the budget left then can pay for
     * (losses come out of it as it goes). Stops where the budget can't carry a milestone.
     */
    static MoneyPlan moneyPlan(Skill skill, List<StepRow> steps, RecipeData recipes, Map<String, Integer> owned, long budget,
                               ToIntFunction<String> price, ToIntFunction<String> limit) {
        return moneyPlan(skill, steps, recipes, owned, budget, price, limit, java.util.Collections.emptySet());
    }

    /** As above; the materials in {@code gathered} (lower case) you get yourself, so they're never bought. */
    static MoneyPlan moneyPlan(Skill skill, List<StepRow> steps, RecipeData recipes, Map<String, Integer> owned, long budget,
                               ToIntFunction<String> price, ToIntFunction<String> limit, Set<String> gathered) {
        MoneyPlan plan = new MoneyPlan();
        plan.budget = budget;
        Map<String, Integer> pool = new HashMap<>(owned);
        long cash = budget;
        for (StepRow s : steps) {
            if (s.done || s.row == null || s.row.needed <= 0) {
                continue;
            }
            MoneyStep m = new MoneyStep(s);
            m.budgetBefore = cash;
            for (Need need : planMaterials(skill, java.util.Collections.singletonList(s), recipes, pool)) {
                String key = need.name.toLowerCase(Locale.ROOT);
                long have = need.tool ? need.have : pool.getOrDefault(key, 0);
                long toBuy = Math.max(0, need.needed - have);
                if (!need.tool) {
                    pool.put(key, (int) Math.max(0, have - need.needed));
                }
                if (toBuy > 0 && !gathered.contains(key)) {
                    long each = Math.max(0, price.applyAsInt(need.name));
                    m.buys.add(new Buy(need.name, toBuy, toBuy * each, need.tool ? 0 : Math.max(0, limit.applyAsInt(need.name))));
                    m.cost += toBuy * each;
                    if (need.tool) {
                        // (a tool, once: in your pool from here on)
                        pool.put(key, 1);
                    }
                }
            }
            Double earn = s.row.earn(s.row.needed);
            m.earn = earn == null ? 0 : Math.round(earn);
            for (Buy buy : m.buys) {
                if (buy.limit > 0) {
                    m.limitRounds = Math.max(m.limitRounds, (buy.quantity + buy.limit - 1) / buy.limit);
                }
            }
            long loss = m.loss();
            if (m.cost <= 0) {
                m.cashRounds = 1;
            } else if (loss > 0) {
                // Each round's sale pays for the next round's buy; the budget shrinks by the loss as it goes, so the
                // last round has the least: rounds x (budget - loss) >= what the sales bring back
                long spare = cash - loss;
                if (spare <= 0 || (m.earn == 0 && cash < m.cost)) {
                    m.runsOut = true;
                } else {
                    m.cashRounds = Math.max(1, (m.earn + spare - 1) / spare);
                }
            } else {
                m.cashRounds = cash <= 0 ? 0 : Math.max(1, (m.cost + cash - 1) / cash);
                m.runsOut = cash <= 0;
            }
            m.rounds = Math.max(1, Math.max(m.limitRounds, m.cashRounds));
            m.perRound = m.cost <= 0 ? 0 : (m.cost + m.rounds - 1) / m.rounds;
            plan.steps.add(m);
            if (m.runsOut) {
                m.budgetAfter = cash;
                plan.runsOut = m;
                break;
            }
            cash -= loss;
            m.budgetAfter = cash;
            plan.peak = Math.max(plan.peak, m.perRound);
            plan.loss += loss;
            plan.limitHours += Math.max(0, m.limitRounds - 1) * 4;
        }
        return plan;
    }

    /**
     * The smallest budget that carries the whole plan (to about 1%): the money plan run at budgets in between, the
     * least one that doesn't run out. 0 when it needs nothing.
     */
    static long budgetNeeded(Skill skill, List<StepRow> steps, RecipeData recipes, Map<String, Integer> owned,
                             ToIntFunction<String> price, ToIntFunction<String> limit) {
        return budgetNeeded(skill, steps, recipes, owned, price, limit, java.util.Collections.emptySet());
    }

    static long budgetNeeded(Skill skill, List<StepRow> steps, RecipeData recipes, Map<String, Integer> owned,
                             ToIntFunction<String> price, ToIntFunction<String> limit, Set<String> gathered) {
        long high = 0;
        for (MoneyStep m : moneyPlan(skill, steps, recipes, owned, Long.MAX_VALUE / 4, price, limit, gathered).steps) {
            high += Math.max(0, m.cost);
        }
        if (high <= 0) {
            return 0;
        }
        long low = 0;
        while (high - low > Math.max(1000, high / 100)) {
            long mid = low + (high - low) / 2;
            if (moneyPlan(skill, steps, recipes, owned, mid, price, limit, gathered).runsOut == null) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return high;
    }

    // A tool you have: any item named for it ("Hammer" in "Imcando hammer", "Knife")
    private static boolean ownsTool(Map<String, Integer> owned, String tool) {
        String t = tool.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> item : owned.entrySet()) {
            if (item.getValue() > 0 && (item.getKey().equals(t) || item.getKey().endsWith(" " + t))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts a milestone in at its own levels: the ones it overlaps make room (cut short, cut off at the start, split in
     * two around it, or gone when it covers them). The step may already be in the plan (one being edited).
     */
    /**
     * Your milestones kept as they are, with the suggested plan's filling only the levels they leave open (before,
     * between and after them). How many milestones the suggestions add is {@link #added}.
     */
    static SkillPlans.Plan keepAndFill(SkillPlans.Plan suggested, SkillPlans.Plan mine) {
        SkillPlans.Plan out = new SkillPlans.Plan(Math.max(suggested.goal, mine.goal));
        for (SkillPlans.Step step : suggested.steps) {
            out.steps.add(step.copy());
        }
        // Yours over the suggestions (the supply ones last: they're fixed, so nothing of yours gets cut by them)
        for (boolean supplies : new boolean[]{false, true}) {
            for (SkillPlans.Step step : mine.steps) {
                if (step.supplies.isEmpty() != supplies) {
                    insertStep(out, step.copy());
                }
            }
        }
        out.goal = Math.max(out.goal, out.end());
        return out;
    }

    /** How many of the plan's milestones aren't one of yours (same action and levels). */
    static int added(SkillPlans.Plan plan, SkillPlans.Plan mine) {
        int added = 0;
        for (SkillPlans.Step step : plan.steps) {
            boolean yours = false;
            for (SkillPlans.Step old : mine.steps) {
                if (old.action.equals(step.action) && old.from == step.from && old.to == step.to) {
                    yours = true;
                }
            }
            if (!yours) {
                added++;
            }
        }
        return added;
    }

    static void insertStep(SkillPlans.Plan plan, SkillPlans.Step step) {
        // A supply milestone (kept for another plan) is fixed: a new one fits around it, never over it
        if (step.supplies.isEmpty()) {
            for (SkillPlans.Step other : plan.steps) {
                if (other == step || other.supplies.isEmpty() || other.to <= step.from || other.from >= step.to) {
                    continue;
                }
                if (other.from <= step.from) {
                    step.from = other.to;
                } else {
                    step.to = other.from;
                }
            }
            if (step.to <= step.from) {
                plan.steps.remove(step);
                return;
            }
        }
        List<SkillPlans.Step> after = new ArrayList<>();
        for (SkillPlans.Step other : plan.steps) {
            // (supply milestones are never cut)
            if (other == step || other.to <= step.from || other.from >= step.to || !other.supplies.isEmpty()) {
                continue;
            }
            if (other.from < step.from && other.to > step.to) {
                // Around it: split, its second half after the new one
                SkillPlans.Step rest = new SkillPlans.Step(other.action, step.to, other.to, other.method, other.xp);
                rest.pace = other.pace;
                after.add(rest);
                other.to = step.from;
            } else if (other.from < step.from) {
                other.to = step.from;
            } else {
                other.from = step.to;
            }
        }
        plan.steps.removeIf(other -> other != step && other.to <= other.from);
        if (!plan.steps.contains(step)) {
            plan.steps.add(step);
        }
        plan.steps.addAll(after);
        plan.steps.sort((a, b) -> Integer.compare(a.from, b.from));
        plan.goal = Math.max(plan.goal, step.to);
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
        // (never below a supply milestone's end: those are fixed)
        for (SkillPlans.Step step : plan.steps) {
            if (!step.supplies.isEmpty()) {
                goal = Math.max(goal, step.to);
            }
        }
        int end = goal;
        plan.goal = end;
        plan.steps.removeIf(step -> step.from >= end);
        for (SkillPlans.Step step : plan.steps) {
            step.to = Math.min(step.to, end);
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
    /**
     * Fills the levels from {@code from} to {@code to} that no milestone covers yet with {@code suggestions}' milestones
     * there (cut to fit), each with this note: the climb to a supply's level. Milestones already in the plan stay as
     * they are; what's added is ordinary (its method can be changed). How many it added.
     */
    static int fillGaps(SkillPlans.Plan plan, SkillPlans.Plan suggestions, int from, int to, String note) {
        int added = 0;
        int level = from;
        while (level < to) {
            // The next uncovered stretch
            boolean covered = false;
            for (SkillPlans.Step step : plan.steps) {
                if (step.covers(level)) {
                    level = step.to;
                    covered = true;
                    break;
                }
            }
            if (covered) {
                continue;
            }
            int end = to;
            for (SkillPlans.Step step : plan.steps) {
                if (step.from > level && step.from < end) {
                    end = step.from;
                }
            }
            for (SkillPlans.Step suggested : suggestions.steps) {
                int a = Math.max(level, suggested.from);
                int b = Math.min(end, suggested.to);
                if (b > a) {
                    SkillPlans.Step part = new SkillPlans.Step(suggested.action, a, b, suggested.method, suggested.xp);
                    part.note = note;
                    plan.steps.add(part);
                    added++;
                }
            }
            level = end;
        }
        plan.steps.sort((x, y) -> Integer.compare(x.from, y.from));
        return added;
    }

    /** What a suggested plan aims for. */
    enum PlanAim {
        FASTEST("Fastest", "the most XP an hour at each level"),
        CHEAPEST("Cheapest", "the fewest gp lost for each XP"),
        MONEY("Money maker", "the most profit (an hour where there's a rate)"),
        BALANCED("Balanced", "the least gp and time together (your time at what you say an hour's worth)"),
        AFK("AFK", "the wiki's AFK and other methods"),
        GUIDE("Wiki guide", "the wiki's training guide's fastest methods");

        final String label;
        final String about;

        PlanAim(String label, String about) {
            this.label = label;
            this.about = about;
        }

        /** Whether it goes by prices (not for an ironman). */
        boolean priced() {
            return this == CHEAPEST || this == MONEY || this == BALANCED;
        }
    }

    /**
     * A plan from {@code level} to {@code goal} picking, at each level, the best action you can do there by {@code aim}
     * (its XP an hour, gp per XP or profit, from the training guides' rates and these prices; Balanced counts an hour as
     * {@code hourWorth} gp), runs of the same one joined into milestones. A level where nothing has what the aim needs
     * (no rate, no price) takes the training guide's method. AFK and Wiki guide are the guides' own.
     */
    static SkillPlans.Plan aimedPlan(Skill skill, List<CalculatorData.Action> actions, Collection<CalculatorData.Bonus> on,
                                     TrainingData training, RecipeData recipes, int level, int goal, PlanAim aim,
                                     long hourWorth, ToIntFunction<String> price, IntUnaryOperator itemPrice) {
        return aimedPlan(skill, actions, on, training, recipes, level, goal, aim, hourWorth, price, itemPrice, name -> 0);
    }

    /**
     * As above, with the GE buy limits (an item's every 4 hours by name, 0: not known): an action whose materials can't
     * be bought as fast as you'd use them goes only as fast as they can (its XP and gp an hour scaled down), so a
     * suggestion isn't 80,000 onyx you could never buy.
     */
    static SkillPlans.Plan aimedPlan(Skill skill, List<CalculatorData.Action> actions, Collection<CalculatorData.Bonus> on,
                                     TrainingData training, RecipeData recipes, int level, int goal, PlanAim aim,
                                     long hourWorth, ToIntFunction<String> price, IntUnaryOperator itemPrice,
                                     ToIntFunction<String> limit) {
        if (aim == PlanAim.AFK || aim == PlanAim.GUIDE) {
            return suggestedPlan(skill, actions, on, training, level, goal, aim == PlanAim.AFK);
        }
        SkillPlans.Plan guide = suggestedPlan(skill, actions, on, training, level, goal, false);
        SkillPlans.Plan plan = new SkillPlans.Plan(goal);
        SkillPlans.Step current = null;
        Map<String, Integer> nothing = new HashMap<>();
        for (int at = Math.max(1, level); at < goal; at++) {
            int from = Experience.getXpForLevel(at);
            int to = Experience.getXpForLevel(at + 1);
            CalculatorData.Action best = null;
            double bestScore = 0;
            for (CalculatorData.Action action : actions) {
                if (action.level > at) {
                    continue;
                }
                Row row = row(skill, action, on, from, to, at, recipes, training, nothing, price, itemPrice);
                Double score = score(row, aim, hourWorth, buyable(skill, action, row, recipes, limit));
                if (score != null && (best == null || score > bestScore)) {
                    best = action;
                    bestScore = score;
                }
            }
            String name = best != null ? best.name : guide.at(at) != null ? guide.at(at).action : null;
            if (name == null) {
                continue;
            }
            if (current != null && current.action.equals(name) && current.to == at) {
                current.to = at + 1;
                continue;
            }
            CalculatorData.Action action = best;
            if (action == null) {
                for (CalculatorData.Action a : actions) {
                    if (a.name.equals(name)) {
                        action = a;
                    }
                }
            }
            TrainingData.Method method = SkillPlans.methodFor(training, skill, name, at, goal);
            current = new SkillPlans.Step(name, at, at + 1, method == null ? null : method.name,
                action == null ? 0 : CalculatorData.xpEach(action, on));
            plan.steps.add(current);
        }
        return plan;
    }

    /**
     * How much of an action's pace its materials can be bought at, 0 to 1: the scarcest material's GE buy limit (every
     * 4 hours) against how many an hour it uses; 1 when nothing's limited (or the limits aren't known).
     */
    static double buyable(Skill skill, CalculatorData.Action action, Row row, RecipeData recipes, ToIntFunction<String> limit) {
        if (recipes == null || row.xpHour <= 0 || row.xpEach <= 0) {
            return 1;
        }
        double actionsHour = row.xpHour / row.xpEach;
        double fraction = 1;
        for (RecipeData.Recipe recipe : recipesFor(skill, action, recipes)) {
            int per = perMake(recipe, action);
            for (Map.Entry<String, Integer> material : recipe.materials.entrySet()) {
                int every4h = limit.applyAsInt(material.getKey());
                if (every4h <= 0) {
                    continue;
                }
                double usedHour = actionsHour * material.getValue() / per;
                fraction = Math.min(fraction, every4h / 4.0 / usedHour);
            }
            // (the first recipe is the action's own)
            break;
        }
        return Math.max(0, Math.min(1, fraction));
    }

    // How good an action is for an aim (higher is better), or null when it hasn't what the aim needs; {@code buyable}:
    // how much of its pace the GE buy limits allow
    private static Double score(Row row, PlanAim aim, long hourWorth, double buyable) {
        if (buyable < 1 && row.xpHour > 0) {
            // (only as fast as its materials can be bought: a trickle isn't a plan)
            if (buyable < 0.05) {
                return null;
            }
            row.xpHour = (int) Math.round(row.xpHour * buyable);
        }
        Double perXp = row.perXp();
        if (perXp == null && row.earnEach != null && row.xpEach > 0) {
            // (gathering: what it sells for is its gain)
            perXp = row.earnEach / row.xpEach;
        }
        switch (aim) {
            case FASTEST:
                return row.xpHour > 0 ? (double) row.xpHour : null;
            case CHEAPEST:
                return perXp;
            case MONEY:
                if (perXp == null) {
                    return null;
                }
                // gp an hour where there's a rate (comparable), else gp per XP scaled down below any rated one
                return row.xpHour > 0 ? perXp * row.xpHour : perXp - 1e9;
            case BALANCED:
                if (row.xpHour <= 0) {
                    return null;
                }
                // The gp and the time one XP costs, together (negative: less is better)
                double gp = perXp == null ? 0 : -perXp;
                return -(gp + (double) hourWorth / row.xpHour);
            default:
                return null;
        }
    }

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
