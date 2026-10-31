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

    /** A line for the card: "Train: Oak trees", and the method to outline (null if nothing to outline). */
    static final class Suggestion {
        final String label;
        final String text;
        final TrainingData.Method method;
        /** XP for one action of it (one log, one item made), 0 if unknown. */
        final double xpEach;
        /** Tools it needs ("Axe", "Knife", "Box trap"), not used up. */
        final List<String> tools;

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
        SkillTrackerConfig.TrainingFocus focus = plugin.getTrainingFocus(skill);
        switch (focus) {
            case MONEY:
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
        TrainingData.Method method = training.suggest(skill, level, focus == SkillTrackerConfig.TrainingFocus.OTHER, this::canDo);
        if (method == null) {
            return null;
        }
        // The guide's pick at your level from its table ("Strength potion (Tarromin potion (unf) + Limpwurt
        // root)", "Cutting gems: Ruby"), else what it trains on ("Fly fishing: Trout, Salmon")
        TrainingData.Option option = method.bestOption(level, o -> o.started.stream().allMatch(quests::isStarted));
        if (option != null) {
            String text = option.with.isEmpty() ? method.name + ": " + option.name
                : option.name + " (" + String.join(" + ", option.with) + ")";
            double each = option.xpEach() > 0 ? option.xpEach() : xpEach(method, level);
            return new Suggestion("Train:", text, method, each, toolsFor(skill, method));
        }
        String text = method.targets.isEmpty() ? method.name : method.name + ": " + String.join(", ", method.targets);
        return new Suggestion("Train:", text, method, xpEach(method, level), toolsFor(skill, method));
    }

    // What a guide method needs to hand: an axe or pickaxe for its trees or rocks, the trap for its creature,
    // a tinderbox to burn logs
    private List<String> toolsFor(Skill skill, TrainingData.Method method) {
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
        return tools;
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
