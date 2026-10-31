package com.bradsmultitool;

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
 * setting), and which of your items are its materials, so they can be highlighted. The bank is what you
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
    // Lower-case material name -> "Suggested for your Cooking goal: Lobster"
    private volatile Map<String, String> highlighted = Collections.emptyMap();
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
        if (identity.configGroup.equals(event.getGroup())) {
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
            Predicate<RecipeData.Recipe> allowed = item == null ? r -> true
                : r -> uses(r, item) && (recipe == null || r.name.equalsIgnoreCase(recipe));
            int level = client.getRealSkillLevel(skill);
            RecipeData.Recipe best = recipes.best(skill, level, have, score, allowed);
            if (best == null && item != null) {
                best = recipes.best(skill, level, null, score, allowed);
            }
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

    /** Whether a recipe uses this item (lower-case name) as a material. */
    static boolean uses(RecipeData.Recipe recipe, String item) {
        for (String material : recipe.materials.keySet()) {
            if (material.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
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
