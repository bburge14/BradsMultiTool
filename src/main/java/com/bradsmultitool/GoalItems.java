package com.bradsmultitool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final RecipeData recipes;
    private final ItemManager itemManager;
    private final ItemPrices prices;

    private final Map<String, Integer> bank = new HashMap<>();
    // Replaced whole after each update, so readers (the panel, the item overlay) never see a half-built map
    private volatile Map<Skill, RecipeData.Recipe> suggestions = Collections.emptyMap();
    // Lower-case material name -> "Suggested for your Cooking goal: Lobster"
    private volatile Map<String, String> highlighted = Collections.emptyMap();
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
        if (event.getContainerId() == InventoryID.BANK || event.getContainerId() == InventoryID.INV) {
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
        // Training Focus picks the score: cheapest = most profit per XP, money maker = most profit;
        // otherwise the Best Choice setting (most XP or most profit)
        BradsMultiToolConfig.TrainingFocus focus = config.trainingFocus();
        boolean byProfit = focus == BradsMultiToolConfig.TrainingFocus.MONEY
            || (focus != BradsMultiToolConfig.TrainingFocus.CHEAPEST && config.bestChoice() == BradsMultiToolConfig.BestChoice.PROFIT);
        boolean perXp = focus == BradsMultiToolConfig.TrainingFocus.CHEAPEST;
        Map<Skill, RecipeData.Recipe> suggestions = new HashMap<>();
        Map<String, String> highlighted = new HashMap<>();
        for (Skill skill : plugin.getGoalSkills()) {
            if (!recipes.covers(skill) || plugin.getSkillGoal(skill) <= client.getSkillExperience(skill)) {
                continue;
            }
            RecipeData.Recipe best = recipes.best(skill, client.getRealSkillLevel(skill), have,
                recipe -> perXp ? prices.profit(recipe) / recipe.xp : byProfit ? prices.profit(recipe) : recipe.xp);
            if (best == null) {
                continue;
            }
            suggestions.put(skill, best);
            for (String material : best.materials.keySet()) {
                highlighted.putIfAbsent(material.toLowerCase(), "Suggested for your " + skill.getName() + " goal: " + best.name);
            }
        }
        this.suggestions = suggestions;
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
