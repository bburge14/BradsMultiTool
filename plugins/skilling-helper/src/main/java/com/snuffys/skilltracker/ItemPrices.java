package com.snuffys.skilltracker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/** Grand Exchange prices by item name, from RuneLite's ItemManager (0 when not on the GE). Looked up once. */
@Singleton
class ItemPrices {

    private final ItemManager itemManager;
    private final Map<String, Integer> prices = new ConcurrentHashMap<>();

    @Inject
    ItemPrices(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    int price(String itemName) {
        return prices.computeIfAbsent(itemName.toLowerCase(), name -> {
            for (ItemPrice price : itemManager.search(name)) {
                if (price.getName().equalsIgnoreCase(name)) {
                    return (int) price.getPrice();
                }
            }
            return 0;
        });
    }

    /** What making the recipe once earns (output less materials); negative when it costs. */
    double profit(RecipeData.Recipe recipe) {
        return profit(null, recipe);
    }

    /** As above, for a skill: Construction's furniture is worth nothing (it's part of your house, not an item). */
    double profit(net.runelite.api.Skill skill, RecipeData.Recipe recipe) {
        double value = RecipeData.builtInPlace(skill, recipe) ? 0 : price(recipe.name);
        for (Map.Entry<String, Integer> material : recipe.materials.entrySet()) {
            value -= price(material.getKey()) * (double) material.getValue();
        }
        return value;
    }

    /** True when every material has a GE price (so a cost can be worked out). */
    boolean pricedMaterials(RecipeData.Recipe recipe) {
        for (String material : recipe.materials.keySet()) {
            if (price(material) <= 0) {
                return false;
            }
        }
        return true;
    }
}
