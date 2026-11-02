package com.snuffys.multitool;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;

/**
 * For each production skill you have a goal in (Cooking, Smithing, Fletching...): the best thing you can
 * make at your level from what's in your inventory and bank (most XP, or most profit, per the Best Choice
 * setting), and which of your items are its materials, so they can be highlighted. For gathering goals
 * (Fishing, Mining, Woodcutting, Hunter), the tools and bait their method needs instead. The bank is what you
 * last saw when it was open.
 */
@Singleton
class GoalItems {

    private final Client client;
    private final SkillTracker plugin;
    private final SkillTrackerConfig config;
    private final RecipeData recipes;
    private final ItemManager itemManager;
    private final ItemPrices prices;
    private final PluginIdentity identity;

    private final Map<String, Integer> bank = new HashMap<>();
    // Replaced whole after each update, so readers (the panel, the item overlay) never see a half-built map
    private volatile Map<Skill, RecipeData.Recipe> suggestions = Collections.emptyMap();
    // How many times each suggestion can be made from what you have
    private volatile Map<Skill, Integer> counts = Collections.emptyMap();
    // The XP in your bank and inventory per goal skill (once the bank's been opened)
    private volatile Map<Skill, Double> banked = Collections.emptyMap();
    // The GE value of the materials counted in banked, per goal skill
    private volatile Map<Skill, Long> materialsValue = Collections.emptyMap();
    private boolean bankSeen;
    // The bank as last seen is kept with your RuneScape profile (item names and amounts), so Banked and Materials
    // worth show straight after login instead of only once you've opened the bank this session
    private static final String BANK_KEY = SkillGoals.BANK_KEY;
    @javax.inject.Inject
    private net.runelite.client.config.ConfigManager configManager;
    private boolean bankLoaded;

    /** Another account logged in: its bank isn't this one's. */
    void forgetBank() {
        bank.clear();
        bankSeen = false;
        bankLoaded = false;
        dirty = true;
    }

    // The saved bank, once per account, until the bank's opened this session
    private void loadSavedBank() {
        if (bankLoaded || bankSeen || configManager == null || configManager.getRSProfileKey() == null) {
            return;
        }
        bankLoaded = true;
        Map<String, Integer> saved = decodeBank(configManager.getRSProfileConfiguration(SkillTracker.SHARED_GROUP, BANK_KEY));
        if (!saved.isEmpty()) {
            bank.putAll(saved);
            bankSeen = true;
            dirty = true;
        }
    }

    private void saveBank() {
        if (configManager != null && configManager.getRSProfileKey() != null) {
            configManager.setRSProfileConfiguration(SkillTracker.SHARED_GROUP, BANK_KEY, encodeBank(bank));
        }
    }

    /** "logs\t250\nraw lobster\t1200": each item (lower-case name) and how many (kept in {@link SkillGoals}). */
    static String encodeBank(Map<String, Integer> items) {
        return SkillGoals.encodeBank(items);
    }

    static Map<String, Integer> decodeBank(String text) {
        return SkillGoals.decodeBank(text);
    }
    // Lower-case material name -> "Suggested for your Cooking goal: Lobster"
    private volatile Map<String, String> highlighted = Collections.emptyMap();
    // Gathering goals (Fishing, Mining, Woodcutting, Hunter...): lower-case tool or bait -> "Needed for your
    // Fishing goal: Feather"; matched to items by isTool, so a dragon axe counts for "axe"
    private volatile Map<String, String> tools = Collections.emptyMap();
    // How many of each item you carry or bank (lower-case names)
    private volatile Map<String, Integer> amounts = Collections.emptyMap();
    // Everything you carry, wear or bank (lower-case names), for checking tools
    private volatile Set<String> owned = Collections.emptySet();
    private boolean dirty = true;

    @Inject
    GoalItems(Client client, SkillTracker plugin, SkillTrackerConfig config, RecipeData recipes,
              ItemManager itemManager, ItemPrices prices, PluginIdentity identity) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.recipes = recipes;
        this.itemManager = itemManager;
        this.prices = prices;
        this.identity = identity;
    }

    /** What to make for this skill's goal, or null. */
    RecipeData.Recipe suggestion(Skill skill) {
        return suggestions.get(skill);
    }

    /** How many of this skill's suggestion you can make from your inventory and bank, 0 if none. */
    int count(Skill skill) {
        return counts.getOrDefault(skill, 0);
    }

    /** The XP in your bank and inventory for this goal skill at your level (0 before the bank's opened). */
    double bankedXp(Skill skill) {
        return banked.getOrDefault(skill, 0.0);
    }

    /** The GE value of the materials counted in {@link #bankedXp} (0 before the bank's opened, or unpriced). */
    long materialsValue(Skill skill) {
        return materialsValue.getOrDefault(skill, 0L);
    }

    /** How many of an item you carry or bank (the bank as last opened). */
    int amount(String itemName) {
        return amounts.getOrDefault(itemName.toLowerCase(), 0);
    }

    /** How many of an item are in your inventory now. */
    int carried(String itemName) {
        Map<String, Integer> inventory = new HashMap<>();
        count(client.getItemContainer(InventoryID.INV), inventory);
        return inventory.getOrDefault(itemName.toLowerCase(), 0);
    }

    /**
     * Whether you carry, wear or (as last seen) bank this tool, or a better kind of it: "Axe" matches a rune
     * axe, "Hammer" an imcando hammer, "Pickaxe" any pickaxe.
     */
    boolean owns(String tool) {
        String t = tool.toLowerCase();
        for (String item : owned) {
            if (isTool(item, t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an item (lower-case) is this tool (lower-case) or a better kind of it: "dragon axe" for "axe",
     * "fishing rod (...)" for "fishing rod". A fly fishing or oily rod isn't a "fishing rod" for bait fishing.
     */
    static boolean isTool(String item, String tool) {
        if (item.equals(tool) || item.startsWith(tool + " (") || variantOf(item, tool)) {
            return true;
        }
        return item.endsWith(" " + tool) && !(tool.equals("fishing rod") && (item.startsWith("fly ") || item.startsWith("oily ")));
    }

    /**
     * A kind of the tool with an extra word in the middle: "jug of blessed sunfire wine" for "jug of blessed
     * wine" (every word of the tool, in order, starting and ending the same).
     */
    static boolean variantOf(String item, String tool) {
        String[] words = tool.split(" ");
        String[] have = item.split(" ");
        if (words.length < 2 || !have[0].equals(words[0]) || !have[have.length - 1].equals(words[words.length - 1])) {
            return false;
        }
        int at = 0;
        for (String word : have) {
            if (at < words.length && word.equals(words[at])) {
                at++;
            }
        }
        return at == words.length;
    }

    /** XP per action of this skill's suggestion, by your Prayer method (an altar, sunfire wine). */
    double xpEach(Skill skill) {
        RecipeData.Recipe make = suggestion(skill);
        return make == null ? 0 : xp(make);
    }

    /**
     * XP for one of a recipe: for Prayer by how you train it (bones at an altar give several times their
     * burying XP; blessed bone shards 5 with blessed wine, 6 with sunfire wine).
     */
    double xp(RecipeData.Recipe recipe) {
        PrayerMethod method = prayerMethod(recipe);
        if (method == null) {
            return recipe.xp;
        }
        return method.shards ? method.shardXp : recipe.xp * method.multiplier;
    }

    /**
     * How you train this Prayer recipe: what you picked with "Track for goal", else burying for bones and, for
     * shards, the wine you carry. Null for anything else (ashes, other skills).
     */
    PrayerMethod prayerMethod(RecipeData.Recipe recipe) {
        if (recipe == null) {
            return null;
        }
        boolean shards = PrayerMethod.isShards(recipe);
        if (!shards && !PrayerMethod.isBones(recipe)) {
            return null;
        }
        PrayerMethod picked = plugin.getPrayerMethod(shards);
        if (picked != null) {
            return picked;
        }
        if (shards) {
            return carried("Jug of blessed sunfire wine") > 0 ? PrayerMethod.SUNFIRE_WINE : PrayerMethod.BLESSED_WINE;
        }
        return PrayerMethod.BURY;
    }

    /** The tools this skill's suggestion needs: its recipe's, or for Prayer its method's (a pot at the Ectofuntus). */
    List<String> tools(Skill skill) {
        RecipeData.Recipe make = suggestion(skill);
        if (make == null) {
            return Collections.emptyList();
        }
        PrayerMethod method = prayerMethod(make);
        return method != null ? method.tools : make.tools;
    }

    /** The tooltip for an item that's a material for a goal suggestion, or a gathering goal's tool or bait; else null. */
    String tooltip(String itemName) {
        if (itemName == null) {
            return null;
        }
        String item = itemName.toLowerCase();
        String material = highlighted.get(item);
        if (material != null) {
            return material;
        }
        for (Map.Entry<String, String> tool : tools.entrySet()) {
            if (isTool(item, tool.getKey())) {
                return tool.getValue();
            }
        }
        return null;
    }

    /** Whether anything is highlighted at all (materials, tools or bait). */
    boolean hasHighlights() {
        return !highlighted.isEmpty() || !tools.isEmpty();
    }

    Map<String, String> highlighted() {
        return Collections.unmodifiableMap(highlighted);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.BANK) {
            bank.clear();
            count(event.getItemContainer(), bank);
            bankSeen = true;
            saveBank();
        }
        if (event.getContainerId() == InventoryID.BANK || event.getContainerId() == InventoryID.INV
            || event.getContainerId() == InventoryID.WORN) {
            dirty = true;
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        dirty = true;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (identity.configGroup.equals(event.getGroup()) || SkillTracker.SHARED_GROUP.equals(event.getGroup())) {
            dirty = true;
        }
    }

    // Worked out at most once a tick, after whatever changed
    @Subscribe
    public void onGameTick(GameTick event) {
        loadSavedBank();
        if (dirty) {
            dirty = false;
            update();
        }
    }

    private void update() {
        Map<String, Integer> have = new HashMap<>(bank);
        count(client.getItemContainer(InventoryID.INV), have);
        Map<String, Integer> all = new HashMap<>(have);
        count(client.getItemContainer(InventoryID.WORN), all);
        owned = new HashSet<>(all.keySet());
        amounts = have;
        Map<Skill, RecipeData.Recipe> suggestions = new HashMap<>();
        Map<String, String> highlighted = new HashMap<>();
        Map<Skill, Integer> counts = new HashMap<>();
        Map<Skill, Double> banked = new HashMap<>();
        Map<Skill, Long> materialsValue = new HashMap<>();
        Map<String, String> tools = new HashMap<>();
        for (Skill skill : plugin.getGoalSkills()) {
            if (plugin.getSkillGoal(skill) <= client.getSkillExperience(skill)) {
                continue;
            }
            // Gathering skills use nothing up but bait: outline the tools and bait the method needs instead
            if (!recipes.covers(skill)) {
                for (String tool : plugin.getTrainingAdvisor().goalTools(skill, client.getRealSkillLevel(skill))) {
                    tools.putIfAbsent(tool.toLowerCase(), "Needed for your " + skill.getName() + " goal: " + tool);
                }
                continue;
            }
            // The skill's training focus picks the score: cheapest = most profit per XP, money maker = most
            // profit; otherwise the Best Choice setting (most XP or most profit)
            SkillTrackerConfig.TrainingFocus focus = plugin.getTrainingFocus(skill);
            boolean byProfit = focus == SkillTrackerConfig.TrainingFocus.MONEY
                || (focus != SkillTrackerConfig.TrainingFocus.CHEAPEST && config.bestChoice() == SkillTrackerConfig.BestChoice.PROFIT);
            boolean perXp = focus == SkillTrackerConfig.TrainingFocus.CHEAPEST;
            // Prayer: whatever's worth the most XP in all (4,000 bone shards beat ten dragon bones); you offer
            // everything you have, so XP per item isn't the point there
            boolean total = skill == Skill.PRAYER && !perXp && !byProfit;
            ToDoubleFunction<RecipeData.Recipe> score = recipe -> {
                double xp = xp(recipe);
                PrayerMethod method = prayerMethod(recipe);
                return total ? xp * recipe.makeable(have) / (method != null ? method.bonesUsedEach() : 1)
                    : perXp ? prices.profit(recipe) / xp : byProfit ? prices.profit(recipe) : xp;
            };
            // Your "Track for goal" item: only what uses it, shown even when you've run out
            String target = plugin.getGoalTarget(skill);
            String item = GoalTarget.itemName(target);
            String recipe = GoalTarget.recipeName(target);
            int level = client.getRealSkillLevel(skill);
            // Following a skill plan: the milestone you're on says what to make (Mithril dart, not the best thing
            // in your bank); your Track for goal pick still comes first
            SkillPlans.Step step = item == null ? plugin.getPlanMilestone(skill, level) : null;
            String stepItem = step != null && step.covers(level) ? step.action : null;
            Predicate<RecipeData.Recipe> allowed = item != null ? r -> uses(r, item) && (recipe == null || r.name.equalsIgnoreCase(recipe))
                : stepItem != null ? r -> r.name.equalsIgnoreCase(stepItem) : r -> true;
            if (bankSeen) {
                banked.put(skill, recipes.bankedXp(skill, level, have, this::xp));
                if (config.showMaterialsValue()) {
                    long value = 0;
                    for (Map.Entry<String, Integer> material : recipes.bankedMaterials(skill, level, have, this::xp).entrySet()) {
                        value += (long) prices.price(material.getKey()) * material.getValue();
                    }
                    materialsValue.put(skill, value);
                }
            }
            RecipeData.Recipe best = recipes.best(skill, level, have, score, allowed);
            if (best == null && (item != null || stepItem != null)) {
                best = recipes.best(skill, level, null, score, allowed);
            }
            if (best == null) {
                continue;
            }
            suggestions.put(skill, best);
            // How many items: a go of a recipe can make several (10 darts), the milestone's XP is one of them
            int per = step != null && step.xp > 0 && xp(best) / step.xp >= 1.5 ? (int) Math.round(xp(best) / step.xp) : 1;
            counts.put(skill, best.makeable(have) * per);
            for (String material : best.materials.keySet()) {
                highlighted.putIfAbsent(material.toLowerCase(), "Suggested for your " + skill.getName() + " goal: " + best.name);
            }
        }
        this.suggestions = suggestions;
        this.counts = counts;
        this.banked = banked;
        this.materialsValue = materialsValue;
        this.highlighted = highlighted;
        this.tools = tools;
    }

    /** Whether a recipe uses this item (lower-case name) as a material. */
    static boolean uses(RecipeData.Recipe recipe, String item) {
        return recipe.uses(item);
    }

    private void count(ItemContainer container, Map<String, Integer> into) {
        if (container == null) {
            return;
        }
        for (Item item : container.getItems()) {
            if (item.getId() > 0) {
                String name = itemManager.getItemComposition(item.getId()).getName().toLowerCase();
                into.merge(name, item.getQuantity(), Integer::sum);
            }
        }
    }
}
