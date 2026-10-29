package com.bradsmultitool;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * setting), and which of your items are its materials, so they can be highlighted. The bank is what you
 * last saw when it was open.
 */
@Singleton
class GoalItems {

    private static final String SHARDS = "Blessed bone shards";
    private static final double SUNFIRE_SHARD_XP = 6;

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final RecipeData recipes;
    private final ItemManager itemManager;
    private final ItemPrices prices;

    private final Map<String, Integer> bank = new HashMap<>();
    // Replaced whole after each update, so readers (the panel, the item overlay) never see a half-built map
    private volatile Map<Skill, RecipeData.Recipe> suggestions = Collections.emptyMap();
    // How many times each suggestion can be made from what you have
    private volatile Map<Skill, Integer> counts = Collections.emptyMap();
    // Lower-case material name -> "Suggested for your Cooking goal: Lobster"
    private volatile Map<String, String> highlighted = Collections.emptyMap();
    // How many of each item you carry or bank (lower-case names)
    private volatile Map<String, Integer> amounts = Collections.emptyMap();
    // Everything you carry, wear or bank (lower-case names), for checking tools
    private volatile Set<String> owned = Collections.emptySet();
    private boolean dirty = true;

    @Inject
    GoalItems(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config, RecipeData recipes,
              ItemManager itemManager, ItemPrices prices) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.recipes = recipes;
        this.itemManager = itemManager;
        this.prices = prices;
    }

    /** What to make for this skill's goal, or null. */
    RecipeData.Recipe suggestion(Skill skill) {
        return suggestions.get(skill);
    }

    /** How many of this skill's suggestion you can make from your inventory and bank, 0 if none. */
    int count(Skill skill) {
        return counts.getOrDefault(skill, 0);
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
            if (item.equals(t) || item.startsWith(t + " (") || item.endsWith(" " + t) || variantOf(item, t)) {
                return true;
            }
        }
        return false;
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

    /** XP per action of this skill's suggestion: blessed bone shards give 6 with sunfire wine on you, not 5. */
    double xpEach(Skill skill) {
        RecipeData.Recipe make = suggestion(skill);
        if (make == null) {
            return 0;
        }
        if (make.materials.containsKey(SHARDS) && carried("Jug of blessed sunfire wine") > 0) {
            return SUNFIRE_SHARD_XP;
        }
        return make.xp;
    }

    /** The tooltip for an item that's a material for a goal suggestion, or null. */
    String tooltip(String itemName) {
        return itemName == null ? null : highlighted.get(itemName.toLowerCase());
    }

    Map<String, String> highlighted() {
        return Collections.unmodifiableMap(highlighted);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.BANK) {
            bank.clear();
            count(event.getItemContainer(), bank);
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
        if ("bradsmultitool".equals(event.getGroup())) {
            dirty = true;
        }
    }

    // Worked out at most once a tick, after whatever changed
    @Subscribe
    public void onGameTick(GameTick event) {
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
        for (Skill skill : plugin.getGoalSkills()) {
            if (!recipes.covers(skill) || plugin.getSkillGoal(skill) <= client.getSkillExperience(skill)) {
                continue;
            }
            // The skill's training focus picks the score: cheapest = most profit per XP, money maker = most
            // profit; otherwise the Best Choice setting (most XP or most profit)
            BradsMultiToolConfig.TrainingFocus focus = plugin.getTrainingFocus(skill);
            boolean byProfit = focus == BradsMultiToolConfig.TrainingFocus.MONEY
                || (focus != BradsMultiToolConfig.TrainingFocus.CHEAPEST && config.bestChoice() == BradsMultiToolConfig.BestChoice.PROFIT);
            boolean perXp = focus == BradsMultiToolConfig.TrainingFocus.CHEAPEST;
            // Prayer: whatever's worth the most XP in all (4,000 bone shards beat ten dragon bones); you offer
            // everything you have, so XP per item isn't the point there
            boolean total = skill == Skill.PRAYER && !perXp && !byProfit;
            RecipeData.Recipe best = recipes.best(skill, client.getRealSkillLevel(skill), have,
                recipe -> total ? recipe.xp * recipe.makeable(have)
                    : perXp ? prices.profit(recipe) / recipe.xp : byProfit ? prices.profit(recipe) : recipe.xp);
            if (best == null) {
                continue;
            }
            suggestions.put(skill, best);
            counts.put(skill, best.makeable(have));
            for (String material : best.materials.keySet()) {
                highlighted.putIfAbsent(material.toLowerCase(), "Suggested for your " + skill.getName() + " goal: " + best.name);
            }
        }
        this.suggestions = suggestions;
        this.counts = counts;
        this.highlighted = highlighted;
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
