package com.snuffys.skilltracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;

/**
 * What to train for a skill goal, by the Training Focus setting, shared by the tracker card, the pinned
 * overlay, the world highlight and the level-up note:
 * <ul>
 * <li>Fastest XP / Other methods: the wiki training guide's method for your level ({@link TrainingData}).</li>
 * <li>Cheapest: for skills that make things, the recipe with the fewest GP per XP at GE prices; gathering
 * skills cost nothing, so their fastest method.</li>
 * <li>Money maker: the most profitable wiki money making guide you can do ({@link MoneyData}).</li>
 * </ul>
 * Slayer always suggests the best Slayer master.
 */
@Singleton
class TrainingAdvisor {

    @Inject
    private CombatStyle combatStyle;

    @Inject
    private MonsterData monsterData;

    // Where to train (the places of a method's trees, rocks and creatures)
    @Inject
    private LocationData locations;

    /** A line for the card: "Train: Oak trees", and the method to outline (null if nothing to outline). */
    static final class Suggestion {
        final String label;
        final String text;
        final TrainingData.Method method;
        /** XP for one action of it (one log, one item made), 0 if unknown. */
        final double xpEach;
        /** Tools it needs ("Axe", "Knife", "Box trap"), not used up. */
        final List<String> tools;
        /** The skill plan's milestone it comes from (exported from the Planner), or null for the wiki's. */
        SkillPlans.Step planStep;

        Suggestion(String label, String text, TrainingData.Method method) {
            this(label, text, method, 0, new ArrayList<>());
        }

        Suggestion(String label, String text, TrainingData.Method method, double xpEach, List<String> tools) {
            this.label = label;
            this.text = text;
            this.method = method;
            this.xpEach = xpEach;
            this.tools = tools;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Suggestion && ((Suggestion) o).text.equals(text);
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }
    }

    private final Client client;
    private final SkillTracker plugin;
    private final SkillTrackerConfig config;
    private final TrainingData training;
    private final RecipeData recipes;
    private final MoneyData money;
    private final ItemPrices prices;
    private final SkillingData skilling;
    private final SkillHelperData helpers;
    private final QuestProgress quests;
    // The cheapest recipe per skill and level (prices only looked up once)
    private final Map<String, Object> cheapest = new ConcurrentHashMap<>();
    private static final Object NONE = new Object();

    @Inject
    TrainingAdvisor(Client client, SkillTracker plugin, SkillTrackerConfig config, TrainingData training,
                    RecipeData recipes, MoneyData money, ItemPrices prices, SkillingData skilling,
                    SkillHelperData helpers, QuestProgress quests) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.training = training;
        this.recipes = recipes;
        this.money = money;
        this.prices = prices;
        this.skilling = skilling;
        this.helpers = helpers;
        this.quests = quests;
    }

    /** What to train at this level, or null. */
    Suggestion suggest(Skill skill, int level) {
        if (skill == Skill.SLAYER) {
            SlayerData.Master master = plugin.getSlayerData().bestMaster(plugin.getCombatLevel(), level);
            return master == null ? null : new Suggestion("Train:", "Tasks from " + master.name, null);
        }
        // The method you picked from the card's Method list, while your level and quests allow it
        String chosen = plugin.getChosenMethod(skill);
        TrainingData.Method picked = null;
        if (chosen != null) {
            for (TrainingData.Method m : methodsToPick(skill, level)) {
                if (m.name.equals(chosen)) {
                    picked = m;
                    break;
                }
            }
        }
        // Your skill plan's milestone at this level (exported from the Planner): ahead of the wiki's suggestion and
        // the Training Focus, behind a method you pick (and a Track for goal pick, which callers check first)
        SkillPlans.Plan skillPlan = picked == null ? plugin.getSkillPlan(skill) : null;
        // The milestone you're on, else the next one (between milestones, or before the first, you're heading there)
        SkillPlans.Step step = skillPlan == null ? null : skillPlan.milestone(level);
        if (step != null) {
            TrainingData.Method method = planMethod(skill, step);
            Suggestion fromPlan = new Suggestion("Plan:", step.action, method, step.xp,
                method == null ? new ArrayList<>() : toolsFor(skill, method));
            fromPlan.planStep = step;
            return fromPlan;
        }
        SkillTrackerConfig.TrainingFocus focus = plugin.getTrainingFocus(skill);
        switch (picked != null ? SkillTrackerConfig.TrainingFocus.FASTEST : focus) {
            case MONEY:
                // The money maker you picked from the card's Method list, while you still meet it
                for (MoneyData.Guide g : moneyToPick(skill, level)) {
                    if (g.name.equals(chosen)) {
                        return new Suggestion("Your money maker:", g.name + " (" + g.profitText() + ")", null);
                    }
                }
                MoneyData.Guide guide = money.best(skill, level, this::levelOf);
                if (guide != null) {
                    return new Suggestion("Money:", guide.name + " (" + guide.profitText() + ")", null);
                }
                break;
            case CHEAPEST:
                RecipeData.Recipe recipe = cheapest(skill, level);
                if (recipe != null) {
                    double gpPerXp = prices.profit(recipe) / recipe.xp;
                    return new Suggestion("Cheapest:", recipe.name + " (" + String.format("%+.1f", gpPerXp) + " gp/xp)", null, recipe.xp,
                        recipe.tools);
                }
                break;
            default:
                break;
        }
        TrainingData.Method method = picked != null ? picked
            : training.suggest(skill, level, focus == SkillTrackerConfig.TrainingFocus.OTHER, this::canDo);
        if (method == null) {
            return null;
        }
        addPlaces(method);
        // The guide's pick at your level from its table ("Strength potion (Tarromin potion (unf) + Limpwurt
        // root)", "Cutting gems: Ruby"), else what it trains on ("Fly fishing: Trout, Salmon")
        TrainingData.Option option = method.bestOption(level, o -> o.started.stream().allMatch(quests::isStarted));
        if (option != null) {
            String text = option.with.isEmpty() ? method.name + ": " + option.name
                : option.name + " (" + String.join(" + ", option.with) + ")";
            double each = option.xpEach() > 0 ? option.xpEach() : xpEach(method, level);
            return new Suggestion(picked != null ? "Your method:" : "Train:", text, method, each, toolsFor(skill, method));
        }
        String text = method.targets.isEmpty() ? method.name : method.name + ": " + String.join(", ", method.targets);
        return new Suggestion(picked != null ? "Your method:" : "Train:", text, method, xpEach(method, level), toolsFor(skill, method));
    }

    // What a guide method needs to hand: an axe or pickaxe for its trees or rocks, the trap for its creature,
    // a tinderbox to burn logs
    /**
     * A plan milestone in the goal's terms, from its action: the trees or rocks it's from ("Maple logs": every maple
     * tree, so an axe), the fish it catches (its rod and bait), the creature it hunts (its trap); what the wiki's guide
     * says of the same method (its places, its about) added where it has one. Null when there's nothing to go on.
     */
    TrainingData.Method planMethod(Skill skill, SkillPlans.Step step) {
        TrainingData.Method guide = SkillPlans.method(training, skill, step);
        java.util.Set<Integer> ids = skilling.objectIds(skill.getName(), name -> LocationData.sameThing(name, step.action));
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("name", step.action);
        o.addProperty("from", step.from);
        o.addProperty("to", step.to);
        o.addProperty("fastest", false);
        com.google.gson.JsonArray objects = new com.google.gson.JsonArray();
        ids.forEach(objects::add);
        if (ids.isEmpty() && guide != null) {
            guide.objects.forEach(objects::add);
        }
        o.add("objects", objects);
        com.google.gson.JsonArray npcs = new com.google.gson.JsonArray();
        com.google.gson.JsonArray targets = new com.google.gson.JsonArray();
        String what = step.action.replaceFirst("(?i)^raw ", "");
        if (skill == Skill.FISHING) {
            targets.add(what);
        } else if (skill == Skill.HUNTER || skill == Skill.THIEVING) {
            npcs.add(step.action);
        }
        if (guide != null) {
            guide.npcs.forEach(npcs::add);
            if (skill != Skill.FISHING) {
                guide.targets.forEach(targets::add);
            }
        }
        o.add("npcs", npcs);
        o.add("targets", targets);
        // Where: the places of what it's from (best first), else the guide's
        com.google.gson.JsonArray places = new com.google.gson.JsonArray();
        if (locations != null) {
            for (LocationData.Place place : locations.forAction(skill.getName(), step.action)) {
                places.add(place.label());
            }
        }
        if (places.size() == 0 && guide != null) {
            guide.places.forEach(places::add);
        }
        o.add("places", places);
        if (guide != null) {
            if (guide.about != null) {
                o.addProperty("about", guide.about);
            }
        }
        if (objects.size() == 0 && npcs.size() == 0 && targets.size() == 0 && guide == null) {
            return null;
        }
        return new TrainingData.Method(o);
    }

    // A guide method the wiki gives no places for: those of its trees, rocks and creatures (best first; once, kept)
    private void addPlaces(TrainingData.Method method) {
        if (locations == null || !method.places.isEmpty()) {
            return;
        }
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (int id : method.objects) {
            SkillingData.Resource resource = skilling.object(id, null);
            if (resource != null) {
                names.add(resource.name);
            }
        }
        names.addAll(method.npcs);
        // (fish a method catches, "Trout": its spots, by the catch's name)
        for (String target : method.targets) {
            names.add(target);
            names.add("Raw " + target.toLowerCase(java.util.Locale.ROOT));
        }
        for (String name : names) {
            for (LocationData.Place place : locations.of(name)) {
                if (!method.places.contains(place.label())) {
                    method.places.add(place.label());
                }
            }
        }
    }

    List<String> toolsFor(Skill skill, TrainingData.Method method) {
        List<String> tools = new ArrayList<>();
        for (int id : method.objects) {
            SkillingData.Resource resource = skilling.object(id, null);
            if (resource != null && "Woodcutting".equals(resource.skill)) {
                tools.add("Axe");
                break;
            }
            if (resource != null && "Mining".equals(resource.skill)) {
                tools.add("Pickaxe");
                break;
            }
        }
        for (String npc : method.npcs) {
            SkillHelperData.Creature creature = helpers.creature(npc);
            if (creature != null && creature.trap != null && !creature.trap.isEmpty() && !tools.contains(creature.trap)) {
                tools.add(creature.trap);
            }
        }
        if (skill == Skill.FIREMAKING && method.name.toLowerCase().contains("burn")) {
            tools.add("Tinderbox");
        }
        // Fish it catches: the rod, net or harpoon and the bait ("Fly fishing rod", "Feather")
        if (skill == Skill.FISHING) {
            for (String target : method.targets) {
                SkillHelperData.Catch fish = helpers.catchNamed(target);
                if (fish != null) {
                    addNeeds(tools, fish);
                }
            }
        }
        return tools;
    }

    private static void addNeeds(List<String> tools, SkillHelperData.Catch fish) {
        String needs = fish.needs();
        if (needs == null) {
            return;
        }
        for (String part : needs.split("\\s*\\+\\s*")) {
            if (!part.isEmpty() && !tools.contains(part)) {
                tools.add(part);
            }
        }
    }

    /**
     * The tools and bait this skill's goal needs: for your "Track for goal" pick (the fishing way's rod and bait,
     * an axe or pickaxe for a tree or rock, a creature's trap), else the suggested method's. Empty when none.
     */
    List<String> goalTools(Skill skill, int level) {
        String target = plugin.getGoalTarget(skill);
        List<String> tools = new ArrayList<>();
        if (target == null) {
            Suggestion suggestion = suggest(skill, level);
            if (suggestion != null) {
                tools.addAll(suggestion.tools);
            }
        } else if (target.startsWith(GoalTarget.FISH)) {
            List<SkillHelperData.Catch> catches = fishCatches(target);
            if (!catches.isEmpty()) {
                addNeeds(tools, catches.get(0));
            }
        } else if (target.startsWith(GoalTarget.OBJECT)) {
            SkillingData.Resource resource = skilling.object(-1, SkillTracker.goalTargetName(target));
            if (resource != null && "Woodcutting".equals(resource.skill)) {
                tools.add("Axe");
            } else if (resource != null && "Mining".equals(resource.skill)) {
                tools.add("Pickaxe");
            }
        } else if (target.startsWith(GoalTarget.NPC)) {
            SkillHelperData.Creature creature = helpers.creature(SkillTracker.goalTargetName(target));
            if (creature != null && creature.trap != null && !creature.trap.isEmpty()) {
                tools.add(creature.trap);
            }
        }
        return tools;
    }

    /** The money-making guides you could pick for this skill (you meet them at this level), best first. */
    List<MoneyData.Guide> moneyToPick(Skill skill, int level) {
        return money.available(skill, level, this::levelOf);
    }

    /** The guide methods you could pick at this level for this skill (see {@link TrainingData#available}). */
    List<TrainingData.Method> methodsToPick(Skill skill, int level) {
        List<TrainingData.Method> out = new ArrayList<>(training.available(skill, level, this::canDo));
        // and the guide's later methods you can already do: its trees, rocks or creatures are at or below your
        // level ("Lead rocks" from 60 in the guide, but lead can be mined from 25)
        for (TrainingData.Method method : training.later(skill, level)) {
            int needs = levelNeeded(method);
            if (needs > 0 && needs <= level && canDo(method) && out.stream().noneMatch(m -> m.name.equals(method.name))) {
                out.add(method);
            }
        }
        return out;
    }

    // The level a guide method's own trees, rocks, stalls or creatures need (the lowest of them), 0 if unknown
    private int levelNeeded(TrainingData.Method method) {
        int needs = 0;
        for (int id : method.objects) {
            SkillingData.Resource resource = skilling.object(id, null);
            if (resource != null && resource.level > 0 && (needs == 0 || resource.level < needs)) {
                needs = resource.level;
            }
        }
        for (String npc : method.npcs) {
            SkillHelperData.Creature creature = helpers.creature(npc);
            if (creature != null && creature.level > 0 && (needs == 0 || creature.level < needs)) {
                needs = creature.level;
            }
        }
        return needs;
    }

    /**
     * At low levels, the quests the guide recommends for this skill's XP that you haven't done, best first;
     * empty when there are none or you're past the guide's questing levels.
     */
    List<String> xpQuests(Skill skill, int level) {
        TrainingData.Method questing = training.questing(skill, level);
        List<String> out = new ArrayList<>();
        if (questing != null) {
            for (String quest : questing.rewards) {
                if (!quests.isFinished(quest)) {
                    out.add(quest);
                }
            }
        }
        return out;
    }

    /** Whether you meet a method's requirements: its quests finished, other skills at the levels it needs. */
    boolean canDo(TrainingData.Method method) {
        for (String quest : method.quests) {
            boolean done = method.started.contains(quest) ? quests.isStarted(quest) : quests.isFinished(quest);
            if (!done) {
                return false;
            }
        }
        for (Map.Entry<String, Integer> need : method.skills.entrySet()) {
            try {
                if (plugin.getCurrentSkillLevel(Skill.valueOf(need.getKey())) < need.getValue()) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                // A skill this client doesn't know: don't hold the method back for it
            }
        }
        return true;
    }

    // XP per action of a guide method: its tree/rock/stall's XP, else its NPC's (pickpocket or hunter creature),
    // else for fish it trains on, the average XP of those you can catch at your level ("Fly fishing: Trout,
    // Salmon": you catch both, mixed)
    private double xpEach(TrainingData.Method method, int level) {
        for (int id : method.objects) {
            SkillingData.Resource resource = skilling.object(id, null);
            if (resource != null && resource.xp > 0) {
                return resource.xp;
            }
        }
        for (String npc : method.npcs) {
            double xp = npcXp(npc);
            if (xp > 0) {
                return xp;
            }
        }
        List<SkillHelperData.Catch> fish = new ArrayList<>();
        for (String target : method.targets) {
            SkillHelperData.Catch c = helpers.catchNamed(target);
            if (c != null) {
                fish.add(c);
            }
        }
        return averageCatchXp(fish, level);
    }

    private double npcXp(String npc) {
        SkillingData.Pickpocket pickpocket = skilling.pickpocket(npc);
        if (pickpocket != null && pickpocket.xp > 0) {
            return pickpocket.xp;
        }
        SkillHelperData.Creature creature = helpers.creature(npc);
        return creature != null ? creature.xp : 0;
    }

    /** For a combat goal: the monster kills are counted on, and the XP one kill gives in the skill. */
    static final class KillEstimate {
        final String monster;
        final double xpEach;

        KillEstimate(String monster, double xpEach) {
            this.monster = monster;
            this.xpEach = xpEach;
        }
    }

    /**
     * XP per kill for a melee, Ranged or Hitpoints goal (XP per damage by your style, see
     * {@link CombatStyle#xpPerDamage}), on the monster you picked with "Track for goal", else the one you
     * last fought, else the suggested one. Null if none of them has known hitpoints (a Gemstone Crab doesn't die).
     */
    KillEstimate killEstimate(Skill skill) {
        // By your current style: Controlled splits the XP three ways, Longrange two
        double perDamage = combatStyle.xpPerDamage(skill);
        List<String> monsters = new ArrayList<>();
        // Your "Track for goal" pick first, then what you last fought, then the suggestion
        String target = plugin.getGoalTarget(skill);
        if (target != null && target.startsWith("npc:")) {
            monsters.add(SkillTracker.goalTargetName(target));
        }
        monsters.add(combatStyle.getLastTargetName());
        Suggestion suggestion = suggest(skill, plugin.getCurrentSkillLevel(skill));
        if (suggestion != null && suggestion.method != null) {
            monsters.addAll(suggestion.method.npcs);
            // The guide names them in the plural ("Sulphur Naguas")
            monsters.add(suggestion.method.name.replaceAll("s$", ""));
        }
        for (String monster : monsters) {
            MonsterData.Stats stats = monsterData.statsFor(monster);
            if (stats != null && stats.hitpoints > 0) {
                return new KillEstimate(monster, stats.hitpoints * perDamage);
            }
        }
        return null;
    }

    /**
     * XP you'll likely get per action toward this skill's goal, before you've had an XP drop: what you picked
     * with "Track for goal", else what to make from your items, else the suggested method. 0 if unknown.
     */
    double estimatedXpEach(Skill skill, int level) {
        String target = plugin.getGoalTarget(skill);
        if (target != null && target.startsWith(GoalTarget.FISH)) {
            // You catch every fish the method gives at your level, mixed: the average of their XP
            return averageCatchXp(fishCatches(target), level);
        }
        if (target != null && !target.startsWith("item:")) {
            String name = SkillTracker.goalTargetName(target);
            if (target.startsWith("object:")) {
                SkillingData.Resource resource = skilling.object(-1, name);
                return resource != null ? resource.xp : 0;
            }
            return npcXp(name);
        }
        double makeXp = plugin.getGoalItems().xpEach(skill);
        if (makeXp > 0) {
            return makeXp;
        }
        Suggestion suggestion = suggest(skill, level);
        if (suggestion != null && suggestion.xpEach > 0) {
            return suggestion.xpEach;
        }
        // A general method ("Making potions"): the most XP thing you can make at your level
        RecipeData.Recipe best = recipes.covers(skill) ? recipes.best(skill, level, null, r -> r.xp) : null;
        return best != null ? best.xp : 0;
    }

    /** The catches for a fishing pick ("fish:rod fishing spot|lure|1526"), lowest level first; empty if unknown. */
    List<SkillHelperData.Catch> fishCatches(String target) {
        String[] parts = GoalTarget.parts(target);
        if (parts.length < 3) {
            return java.util.Collections.emptyList();
        }
        SkillHelperData.FishingSpot spot;
        try {
            spot = helpers.fishingSpot(Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            spot = null;
        }
        return spot == null ? java.util.Collections.emptyList() : catchesFor(spot, parts[1]);
    }

    /** A spot's catches for one way of fishing it (any case: "lure", "Small Net"), lowest level first. */
    static List<SkillHelperData.Catch> catchesFor(SkillHelperData.FishingSpot spot, String option) {
        for (java.util.Map.Entry<String, List<SkillHelperData.Catch>> e : spot.options.entrySet()) {
            if (e.getKey().equalsIgnoreCase(option)) {
                return e.getValue();
            }
        }
        return java.util.Collections.emptyList();
    }

    /** The average XP of the catches you can get at this level (one method gives several fish), 0 if none. */
    static double averageCatchXp(List<SkillHelperData.Catch> catches, int level) {
        double total = 0;
        int count = 0;
        for (SkillHelperData.Catch c : catches) {
            if (c.level <= level) {
                total += c.xp;
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    /** The highest-level catch you can get at this level, else null. */
    static SkillHelperData.Catch bestCatch(List<SkillHelperData.Catch> catches, int level) {
        SkillHelperData.Catch best = null;
        for (SkillHelperData.Catch c : catches) {
            if (c.level <= level && (best == null || c.level > best.level)) {
                best = c;
            }
        }
        return best;
    }

    /**
     * How a fishing pick reads on the cards: "Lure: Trout, Salmon (Fly fishing rod + Feathers)", or the spot's
     * name when its catches aren't known.
     */
    String describeFish(String target) {
        String[] parts = GoalTarget.parts(target);
        List<SkillHelperData.Catch> catches = fishCatches(target);
        String option = parts.length > 1 ? SkillTracker.goalTargetName(":" + parts[1]) : "";
        if (catches.isEmpty()) {
            return SkillTracker.goalTargetName(":" + parts[0]) + (option.isEmpty() ? "" : " (" + option + ")");
        }
        return option + ": " + catchNames(catches) + (catches.get(0).needs() != null ? " (" + catches.get(0).needs() + ")" : "");
    }

    /** "Trout, Salmon" from Raw trout and Raw salmon. */
    static String catchNames(List<SkillHelperData.Catch> catches) {
        List<String> names = new ArrayList<>();
        for (SkillHelperData.Catch c : catches) {
            String name = c.name.startsWith("Raw ") ? Character.toUpperCase(c.name.charAt(4)) + c.name.substring(5) : c.name;
            names.add(name);
        }
        return String.join(", ", names);
    }

    /** One leg of the way to a goal: train this from one level until another. */
    static final class Stage {
        final int from;
        final int to;
        final Suggestion suggestion;

        Stage(int from, int to, Suggestion suggestion) {
            this.from = from;
            this.to = to;
            this.suggestion = suggestion;
        }
    }

    /**
     * What to train from your level to the goal, split where the suggestion changes ("Willow trees" to 60,
     * then "Maple trees" to 75...). Quest methods along the way don't start a new leg. Empty if there's
     * nothing to suggest.
     */
    List<Stage> plan(Skill skill, int level, int goalLevel) {
        List<Stage> stages = new ArrayList<>();
        int end = Math.max(level, Math.min(goalLevel, 99));
        Suggestion current = suggest(skill, level);
        int from = level;
        for (int at = level + 1; at <= end; at++) {
            Suggestion later = suggest(skill, at);
            if (later != null && !Objects.equals(later, current)
                && (later.method == null || !later.method.isQuesting())) {
                if (current != null) {
                    stages.add(new Stage(from, at, current));
                }
                current = later;
                from = at;
            }
        }
        if (current != null) {
            stages.add(new Stage(from, Math.max(goalLevel, from), current));
        }
        return stages;
    }

    // Fewest GP per XP (most profit per XP) among recipes at your level whose materials have GE prices
    private RecipeData.Recipe cheapest(Skill skill, int level) {
        if (!recipes.covers(skill)) {
            return null;
        }
        Object found = cheapest.computeIfAbsent(skill.name() + ":" + level, key -> {
            RecipeData.Recipe recipe = recipes.best(skill, level, null, r -> prices.profit(r) / r.xp, prices::pricedMaterials);
            return recipe != null ? recipe : NONE;
        });
        return found == NONE ? null : (RecipeData.Recipe) found;
    }

    private int levelOf(String skillName) {
        try {
            return client.getRealSkillLevel(Skill.valueOf(skillName));
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
