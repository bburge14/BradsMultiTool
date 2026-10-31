package com.snuffys.multitool;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Adds "Track for goal" to the right-click menu of a Hunter creature, pickpocket NPC, fishing spot, tree,
 * rock or stall when its skill has a goal, or of a monster when a combat skill has one, so you can outline what you'd rather train on than the
 * suggestion; and of an item in your inventory or bank that a production skill or Prayer with a goal uses, so
 * the goal card works it out from that item. For bones and blessed bone shards it opens a list of how you train
 * them (bury or which altar; blessed or sunfire wine). Choosing "Untrack" goes back to the suggestion. It only
 * changes what we show.
 */
@Singleton
class GoalTargetMenu {

    private static final String TRACK = "Track for goal";
    private static final String UNTRACK = "Untrack for goal";

    private final Client client;
    private final SkillTracker plugin;
    private final SkillingData skilling;
    private final SkillHelperData helpers;
    private final RecipeData recipes;
    private final ItemManager itemManager;

    @Inject
    GoalTargetMenu(Client client, SkillTracker plugin, SkillingData skilling,
                   SkillHelperData helpers, RecipeData recipes, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.skilling = skilling;
        this.helpers = helpers;
        this.recipes = recipes;
        this.itemManager = itemManager;
    }

    // Next to Examine, like RuneLite's own NPC and object tagging. Offered whenever the skill has a goal (your
    // pick also sets the goal card's suggestion and kill count, not only the outline)
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        MenuEntry entry = event.getMenuEntry();
        // An item in your inventory or bank
        if (entry.getItemId() > 0 && entry.getWidget() != null && "Examine".equals(entry.getOption())) {
            addItemEntry(event, entry.getItemId());
            return;
        }
        List<Skill> skills = new ArrayList<>();
        String key = null;
        if (entry.getType() == MenuAction.EXAMINE_NPC && entry.getNpc() != null && entry.getNpc().getName() != null) {
            NPC npc = entry.getNpc();
            key = "npc:" + npc.getName().toLowerCase();
            Skill skill = npcSkill(npc);
            if (skill != null) {
                skills.add(skill);
            } else if (isMonster(npc)) {
                // A monster: every combat skill with a goal (fighting it trains several at once)
                skills.addAll(GoalLines.KILL_SKILLS);
                skills.add(Skill.MAGIC);
            }
        } else if (entry.getType() == MenuAction.EXAMINE_OBJECT) {
            SkillingData.Resource resource = skilling.object(entry.getIdentifier(), Text.removeTags(entry.getTarget()));
            if (resource != null && skill(resource.skill) != null) {
                skills.add(skill(resource.skill));
                key = "object:" + resource.name;
            }
        }
        skills.removeIf(s -> plugin.getSkillGoal(s) <= client.getSkillExperience(s));
        if (skills.isEmpty()) {
            return;
        }
        // A fishing spot: pick the way you fish it (and so the bait and catch), not just the spot
        if (skills.contains(Skill.FISHING) && entry.getNpc() != null && addFishingEntry(event, entry.getNpc())) {
            return;
        }
        final String target = key;
        boolean tracked = skills.stream().anyMatch(s -> target.equals(plugin.getGoalTarget(s)));
        client.getMenu().createMenuEntry(-1)
            .setOption(tracked ? UNTRACK : TRACK)
            .setTarget(event.getTarget())
            .setType(MenuAction.RUNELITE)
            .onClick(e -> skills.forEach(s -> plugin.setGoalTarget(s, tracked ? null : target)));
    }

    // Each way of fishing the spot with what it catches and needs ("Lure: Trout 20, Salmon 30 (Fly fishing rod + Feathers)"),
    // the one you track in green. False when the spot's catches aren't known (a plain entry is added instead).
    private boolean addFishingEntry(MenuEntryAdded event, NPC npc) {
        SkillHelperData.FishingSpot spot = helpers.fishingSpot(npc.getId());
        if (spot == null || spot.options.isEmpty()) {
            return false;
        }
        String current = plugin.getGoalTarget(Skill.FISHING);
        boolean tracked = current != null && current.startsWith(GoalTarget.FISH)
            && GoalTarget.parts(current)[0].equalsIgnoreCase(npc.getName());
        MenuEntry parent = client.getMenu().createMenuEntry(-1)
            .setOption(TRACK)
            .setTarget(event.getTarget())
            .setType(MenuAction.RUNELITE);
        Menu sub = parent.createSubMenu();
        int level = client.getRealSkillLevel(Skill.FISHING);
        List<String> options = new ArrayList<>(spot.options.keySet());
        // Index 0 each time: added bottom up, so the spot's first option ends up on top
        if (tracked) {
            sub.createMenuEntry(0)
                .setOption(UNTRACK)
                .setType(MenuAction.RUNELITE)
                .onClick(e -> plugin.setGoalTarget(Skill.FISHING, null));
        }
        for (int i = options.size() - 1; i >= 0; i--) {
            String option = options.get(i);
            List<SkillHelperData.Catch> catches = spot.options.get(option);
            String target = GoalTarget.fish(npc.getName(), option, npc.getId());
            boolean canDo = TrainingAdvisor.bestCatch(catches, level) != null;
            String label = option + ": " + catchLevels(catches)
                + (!catches.isEmpty() && catches.get(0).needs() != null ? " (" + catches.get(0).needs() + ")" : "");
            Color color = target.equals(current) ? Color.GREEN : canDo ? null : Color.GRAY;
            sub.createMenuEntry(0)
                .setOption(color != null ? ColorUtil.wrapWithColorTag(label, color) : label)
                .setType(MenuAction.RUNELITE)
                .onClick(e -> plugin.setGoalTarget(Skill.FISHING, target));
        }
        return true;
    }

    // "Trout 20, Salmon 30"
    private static String catchLevels(List<SkillHelperData.Catch> catches) {
        List<String> parts = new ArrayList<>();
        for (SkillHelperData.Catch c : catches) {
            parts.add(TrainingAdvisor.catchNames(java.util.Collections.singletonList(c)) + " " + c.level);
        }
        return String.join(", ", parts);
    }

    private void addItemEntry(MenuEntryAdded event, int itemId) {
        String name = itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName();
        if (name == null) {
            return;
        }
        String target = "item:" + name.toLowerCase();
        List<Skill> skills = new ArrayList<>();
        List<RecipeData.Recipe> prayer = new ArrayList<>();
        for (Skill skill : plugin.getGoalSkills()) {
            if (plugin.getSkillGoal(skill) <= client.getSkillExperience(skill) || !recipes.covers(skill)) {
                continue;
            }
            List<RecipeData.Recipe> using = recipes.using(skill, name);
            if (!using.isEmpty()) {
                skills.add(skill);
                if (skill == Skill.PRAYER) {
                    prayer = using;
                }
            }
        }
        if (skills.isEmpty()) {
            return;
        }
        boolean tracked = skills.stream().anyMatch(s -> target.equals(plugin.getGoalTarget(s)));
        // Bones and shards: pick how you train them too
        RecipeData.Recipe recipe = prayer.isEmpty() ? null : prayer.get(0);
        boolean methods = recipe != null && (PrayerMethod.isBones(recipe) || PrayerMethod.isShards(recipe));
        MenuEntry parent = client.getMenu().createMenuEntry(-1)
            .setOption(tracked && !methods ? UNTRACK : TRACK)
            .setTarget(event.getTarget())
            .setType(MenuAction.RUNELITE);
        if (!methods) {
            List<Choice> choices = recipeChoices(skills, name);
            if (choices.size() <= 1) {
                parent.onClick(e -> skills.forEach(s -> plugin.setGoalTarget(s, tracked ? null : target)));
                return;
            }
            // Used for several things (logs: arrow shafts, bows; a bar: each thing you can smith): pick which
            addRecipeEntries(parent.setOption(TRACK), skills, name, choices);
            return;
        }
        boolean shards = PrayerMethod.isShards(recipe);
        PrayerMethod current = tracked ? plugin.getPrayerMethod(shards) : null;
        // Index 0 each time keeps them in this order, top to bottom
        Menu sub = parent.createSubMenu();
        for (PrayerMethod method : PrayerMethod.forShards(shards)) {
            sub.createMenuEntry(0)
                .setOption(method == current ? ColorUtil.wrapWithColorTag(method.label, Color.GREEN) : method.label)
                .setType(MenuAction.RUNELITE)
                .onClick(e -> {
                    plugin.setPrayerMethod(method);
                    skills.forEach(s -> plugin.setGoalTarget(s, target));
                });
        }
        if (tracked) {
            sub.createMenuEntry(0)
                .setOption(UNTRACK)
                .setType(MenuAction.RUNELITE)
                .onClick(e -> skills.forEach(s -> plugin.setGoalTarget(s, null)));
        }
    }

    private static final int MOST_RECIPES = 12;

    private static final class Choice {
        final Skill skill;
        final RecipeData.Recipe recipe;

        Choice(Skill skill, RecipeData.Recipe recipe) {
            this.skill = skill;
            this.recipe = recipe;
        }
    }

    // What you can make from the item in the goal skills at your level, highest level first (at most 12)
    private List<Choice> recipeChoices(List<Skill> skills, String item) {
        List<Choice> out = new ArrayList<>();
        for (Skill skill : skills) {
            int level = client.getRealSkillLevel(skill);
            for (RecipeData.Recipe recipe : recipes.using(skill, item)) {
                if (recipe.level <= level && out.stream().noneMatch(c -> c.recipe.name.equalsIgnoreCase(recipe.name))) {
                    out.add(new Choice(skill, recipe));
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b.recipe.level, a.recipe.level));
        return out.size() > MOST_RECIPES ? out.subList(0, MOST_RECIPES) : out;
    }

    // "Best I can make" (the suggestion from your items), then each recipe ("Fletching: Longbow (u) 40"), your pick
    // in green, and Untrack when something's tracked
    private void addRecipeEntries(MenuEntry parent, List<Skill> skills, String item, List<Choice> choices) {
        Menu sub = parent.createSubMenu();
        String any = GoalTarget.item(item, null);
        boolean tracked = false;
        for (Skill skill : skills) {
            String current = plugin.getGoalTarget(skill);
            tracked |= current != null && item.equalsIgnoreCase(GoalTarget.itemName(current));
        }
        // Index 0 each time: added bottom up
        if (tracked) {
            sub.createMenuEntry(0)
                .setOption(UNTRACK)
                .setType(MenuAction.RUNELITE)
                .onClick(e -> skills.forEach(s -> plugin.setGoalTarget(s, null)));
        }
        for (int i = choices.size() - 1; i >= 0; i--) {
            RecipeData.Recipe recipe = choices.get(i).recipe;
            Skill skill = choices.get(i).skill;
            String target = GoalTarget.item(item, recipe.name);
            String label = (skills.size() > 1 ? skill.getName() + ": " : "") + recipe.name + " " + recipe.level;
            sub.createMenuEntry(0)
                .setOption(target.equals(plugin.getGoalTarget(skill)) ? ColorUtil.wrapWithColorTag(label, Color.GREEN) : label)
                .setType(MenuAction.RUNELITE)
                .onClick(e -> {
                    // Only that recipe's skill; the item's other skills go back to their suggestion
                    for (Skill s : skills) {
                        plugin.setGoalTarget(s, s == skill ? target : null);
                    }
                });
        }
        boolean auto = skills.stream().allMatch(s -> any.equals(plugin.getGoalTarget(s)));
        String best = "Best I can make";
        sub.createMenuEntry(0)
            .setOption(auto ? ColorUtil.wrapWithColorTag(best, Color.GREEN) : best)
            .setType(MenuAction.RUNELITE)
            .onClick(e -> skills.forEach(s -> plugin.setGoalTarget(s, any)));
    }

    // Something you can attack (the Gemstone Crab too: it has no kill count, as it burrows rather than dies)
    private static boolean isMonster(NPC npc) {
        NPCComposition comp = npc.getTransformedComposition() != null ? npc.getTransformedComposition() : npc.getComposition();
        if (comp == null) {
            return false;
        }
        for (String action : comp.getActions()) {
            if ("Attack".equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }

    // Hunter creature, pickpocket target or fishing spot
    private Skill npcSkill(NPC npc) {
        if (helpers.creature(npc.getName()) != null) {
            return Skill.HUNTER;
        }
        if (skilling.pickpocket(npc.getName()) != null) {
            return Skill.THIEVING;
        }
        NPCComposition comp = npc.getTransformedComposition() != null ? npc.getTransformedComposition() : npc.getComposition();
        if (comp != null) {
            for (String action : comp.getActions()) {
                if (action != null && SkillingData.isFishingOption(action)) {
                    return Skill.FISHING;
                }
            }
        }
        return null;
    }

    private static Skill skill(String name) {
        try {
            return Skill.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
