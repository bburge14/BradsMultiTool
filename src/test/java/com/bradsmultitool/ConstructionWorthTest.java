package com.bradsmultitool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import net.runelite.api.Skill;
import org.junit.Test;

/** What Construction builds in your house isn't an item to sell; what a workbench makes, repair kits and hull parts are. */
public class ConstructionWorthTest {

    @Test
    public void furnitureIsWorthNothing() {
        RecipeData recipes = new RecipeData();
        recipes.load(new Gson());
        int furniture = 0;
        int kept = 0;
        for (RecipeData.Recipe recipe : recipes.recipes(Skill.CONSTRUCTION)) {
            if (recipe.name.equals("Mahogany table") || recipe.name.equals("Gnome bench") || recipe.name.equals("Oak larder")) {
                assertTrue(recipe.name, RecipeData.builtInPlace(Skill.CONSTRUCTION, recipe));
                furniture++;
            }
            if (recipe.name.equals("Repair kit")) {
                assertFalse(RecipeData.builtInPlace(Skill.CONSTRUCTION, recipe));
                kept++;
            }
        }
        assertTrue(furniture >= 3 && kept >= 1);
        // Other skills' products are items as ever
        for (RecipeData.Recipe recipe : recipes.recipes(Skill.FLETCHING)) {
            assertFalse(RecipeData.builtInPlace(Skill.FLETCHING, recipe));
        }
    }
}
