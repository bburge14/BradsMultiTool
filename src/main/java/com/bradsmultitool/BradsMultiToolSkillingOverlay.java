package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.Text;

/**
 * Hover card for trees, rocks, stalls, pickpocket targets and fishing spots.
 */
public class BradsMultiToolSkillingOverlay extends Overlay {

    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance();
    private static final Color TITLE_COLOR = new Color(0, 220, 220);
    private static final Color HEADER_COLOR = new Color(255, 200, 80);

    private final Client client;
    private final SkillTracker plugin;
    private final SkillTrackerConfig config;
    private final SkillingData data;
    private final FarmingData farming;
    private final SkillHelperData helpers;
    private final BirdhouseTracker birdhouses;
    private final SkillIconManager skillIcons;
    private final ItemManager itemManager;
    private final PluginFamily family;
    // The NPC whose card we last told the others we show (PluginFamily "skilling-card")
    private int sentCardNpc = -1;
    private final PanelComponent panel = new PanelComponent();
    private final Map<NPC, Integer> duckStopped = new HashMap<>();
    // A current duck despawns two minutes after it stops
    private static final int DUCK_DESPAWN_TICKS = 200;
    // Grand Exchange price by item name (0 if unknown), looked up once
    private final Map<String, Integer> prices = new HashMap<>();
    // Item name -> id for pictures, -1 when not found (looked up once)
    private final ItemIcons itemIcons;

    @Inject
    public BradsMultiToolSkillingOverlay(Client client, SkillTracker plugin, SkillTrackerConfig config,
                                         SkillingData data, FarmingData farming, SkillHelperData helpers,
                                         BirdhouseTracker birdhouses, SkillIconManager skillIcons, ItemManager itemManager,
                                         PluginFamily family, ItemIcons itemIcons) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.data = data;
        this.farming = farming;
        this.helpers = helpers;
        this.birdhouses = birdhouses;
        this.skillIcons = skillIcons;
        this.itemManager = itemManager;
        this.family = family;
        this.itemIcons = itemIcons;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGHEST);
        panel.setPreferredSize(new Dimension(205, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(FontManager.getRunescapeSmallFont());

        if (config.showSkillingHover() && !client.isMenuOpen() && buildCard()) {
            CursorTooltip.render(client, graphics, panel);
        }
        return null;
    }

    /**
     * Fills the panel for the skilling target of the left-click option (what the game's top-left
     * tooltip names), so an NPC walking past a tree doesn't change the card; false if there isn't one.
     */
    private boolean buildCard() {
        MenuEntry[] entries = client.getMenu().getMenuEntries();
        if (entries.length == 0) {
            return false;
        }
        MenuEntry entry = entries[entries.length - 1];
        NPC npc = entry.getNpc();
        if (npc != null) {
            return buildNpcCard(npc, Text.removeTags(entry.getOption()));
        }
        // Sea monsters and the current duck often have no left-click option (it's "Set heading" or
        // "Walk here"), so look for them further down the menu
        if (config.showSailingHover() && buildSeaCard(entries)) {
            return true;
        }
        if (isObjectAction(entry.getType())) {
            if (config.showFarmingHover() && buildFarmingCard(entry)) {
                return true;
            }
            if (buildSkillHelperCard(entry)) {
                return true;
            }
            SkillingData.Resource resource = data.object(entry.getIdentifier(), Text.removeTags(entry.getTarget()));
            if (resource != null) {
                buildResourceCard(resource);
                return true;
            }
        }
        return false;
    }

    // ---- Hunter, Runecraft, Agility, Cooking ----

    // The Teomat's libation bowl, empty and filled (OSRS Wiki, Libation bowl)
    private static final Set<Integer> LIBATION_BOWLS = Set.of(52797, 52798, 53016, 53017);
    // Per jug of blessed (sunfire) wine poured in: 400 shards; per 100-shard offering: 2 prayer points
    private static final int SHARDS_PER_WINE = 400;
    private static final int SHARDS_PER_OFFERING = 100;

    // What your blessed bone shards are worth here: XP, the wine and prayer points they take, the level they reach
    private void buildLibationBowlCard() {
        GoalItems items = plugin.getGoalItems();
        int shards = items.amount("Blessed bone shards");
        int wine = items.carried("Jug of blessed wine");
        int sunfire = items.carried("Jug of blessed sunfire wine");
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text("Libation Bowl").color(TITLE_COLOR).build());
        addRequirement(Skill.PRAYER, 30);
        line("Bone Shards:", NUMBER.format(shards), shards > 0 ? Color.WHITE : HudColors.BAD);
        // 5 XP a shard with blessed wine, 6 with sunfire wine (the sunfire jugs are used first here)
        int sunfireShards = Math.min(shards, sunfire * SHARDS_PER_WINE);
        int xp = sunfireShards * 6 + (shards - sunfireShards) * 5;
        line("XP From Them:", NUMBER.format(xp) + (sunfire > 0 ? "" : " (" + NUMBER.format(shards * 6) + " sunfire)"), Color.WHITE);
        int jugsNeeded = (shards + SHARDS_PER_WINE - 1) / SHARDS_PER_WINE;
        int jugs = wine + sunfire;
        line("Wine Needed:", NUMBER.format(jugsNeeded) + " jugs (you carry " + jugs + ")",
            jugs >= jugsNeeded ? HudColors.GOOD : HudColors.WARN);
        int prayerNeeded = (shards + SHARDS_PER_OFFERING - 1) / SHARDS_PER_OFFERING * 2;
        line("Prayer Points:", NUMBER.format(prayerNeeded) + " (you have " + client.getBoostedSkillLevel(Skill.PRAYER) + ")", Color.WHITE);
        int now = client.getSkillExperience(Skill.PRAYER);
        int after = Experience.getLevelForXp(Math.min(Experience.MAX_SKILL_XP, now + xp));
        line("Level:", Experience.getLevelForXp(now) + " -> " + after, HudColors.GOOD);
        addYourRate(Skill.PRAYER);
    }

    private boolean buildSkillHelperCard(MenuEntry entry) {
        int id = entry.getIdentifier();
        ObjectComposition composition = client.getObjectDefinition(id);
        String option = Text.removeTags(entry.getOption()).toLowerCase();
        String target = Text.removeTags(entry.getTarget());

        if (LIBATION_BOWLS.contains(id) && config.showSkillingHover()) {
            buildLibationBowlCard();
            return true;
        }
        int spot = composition != null ? BirdhouseTracker.spotFor(composition.getVarPlayerId()) : -1;
        if (spot >= 0 && config.showBirdhouseHover()) {
            buildBirdhouseCard(spot);
            return true;
        }
        SkillHelperData.Rune rune = helpers.altar(id);
        if (rune != null && config.showRunecraftHover()) {
            buildAltarCard(rune);
            return true;
        }
        if (config.showAgilityHover()) {
            // Course obstacles by id (RuneLite's agility plugin list), so it works wherever the obstacle stands
            SkillHelperData.Course course = helpers.courseFor(id);
            if (course != null) {
                buildCourseCard(course);
                return true;
            }
            SkillHelperData.Shortcut shortcut = helpers.shortcut(id);
            if (shortcut != null) {
                panel.getChildren().clear();
                panel.getChildren().add(TitleComponent.builder().text("Agility Shortcut").color(TITLE_COLOR).build());
                line("Obstacle:", shortcut.name, Color.WHITE);
                addRequirement(Skill.AGILITY, shortcut.level);
                return true;
            }
        }
        if (config.showHunterHover() && buildTrapSpotCard(entry, option, target)) {
            return true;
        }
        if (config.showSailingHover() && buildSailingCard(entry, option, target)) {
            return true;
        }
        if (config.showCookingHover() && isCookingSpot(target, option)) {
            return buildCookingCard(target.toLowerCase().contains("fire"));
        }
        if (config.showSmithingHover() && (option.equals("smith") || target.equalsIgnoreCase("anvil"))) {
            return buildAnvilCard();
        }
        if (config.showSmithingHover() && (option.equals("smelt") || target.equalsIgnoreCase("furnace"))) {
            return buildFurnaceCard();
        }
        return false;
    }

    private void buildBirdhouseCard(int spot) {
        int value = birdhouses.value(spot);
        String type = BirdhouseTracker.type(value);
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(type != null ? type : "Bird house space").color(TITLE_COLOR).build());
        line("Spot:", BirdhouseTracker.SPOT_NAMES[spot], Color.WHITE);
        if (type == null) {
            line("Status:", "Empty", HudColors.WARN);
            return;
        }
        if (!BirdhouseTracker.isSeeded(value)) {
            line("Status:", "Built (add seeds)", HudColors.WARN);
            return;
        }
        long seeded = birdhouses.seededAt(spot);
        if (seeded == 0) {
            line("Status:", "Seeded", Color.WHITE);
            return;
        }
        long left = seeded + BirdhouseTracker.FULL_AFTER_SECONDS - System.currentTimeMillis() / 1000;
        line("Status:", left <= 0 ? "Full" : "Filling", left <= 0 ? HudColors.GOOD : Color.WHITE);
        if (left > 0) {
            line("Full In:", duration(left), Color.WHITE);
        }
    }

    private void buildAltarCard(SkillHelperData.Rune rune) {
        int level = client.getRealSkillLevel(Skill.RUNECRAFT);
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(rune.name + " Altar").color(TITLE_COLOR).build());
        addRequirement(Skill.RUNECRAFT, rune.level);
        if (rune.xp > 0) {
            line("XP Each:", trim(rune.xp), Color.WHITE);
        }
        if (level >= rune.level) {
            line("Runes / Essence:", String.valueOf(rune.perEssence(level)), HudColors.GOOD);
            int next = rune.nextMultiple(level);
            if (next > 0) {
                line("Next Multiple:", "Level " + next, Color.WHITE);
            }
        }
        addYourRate(Skill.RUNECRAFT);
    }

    private void buildCourseCard(SkillHelperData.Course course) {
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(course.name).color(TITLE_COLOR).build());
        if (course.level > 0) {
            addRequirement(Skill.AGILITY, course.level);
        }
        if (course.xpPerLap > 0) {
            line("XP / Lap:", trim(course.xpPerLap), Color.WHITE);
            int xp = client.getSkillExperience(Skill.AGILITY);
            int level = net.runelite.api.Experience.getLevelForXp(xp);
            if (level < net.runelite.api.Experience.MAX_REAL_LEVEL) {
                int left = net.runelite.api.Experience.getXpForLevel(level + 1) - xp;
                line("Laps to Lvl " + (level + 1) + ":", String.valueOf((int) Math.ceil(left / course.xpPerLap)), Color.WHITE);
            }
        }
        addYourRate(Skill.AGILITY);
    }

    private static boolean isCookingSpot(String target, String option) {
        String name = target.toLowerCase();
        if (name.contains("->")) {
            name = name.substring(name.indexOf("->") + 2).trim();
        }
        return option.equals("cook") || name.equals("range") || name.equals("cooking range") || name.equals("fire")
            || name.equals("stove") || name.equals("clay oven") || name.endsWith(" range");
    }

    // Each raw food you're carrying: what it cooks into, its level and XP, and when it stops burning on
    // this fire or range (with cooking gauntlets if worn)
    private boolean buildCookingCard(boolean fire) {
        Map<String, Integer> carried = inventoryCounts();
        boolean gauntlets = ownedItemNames(94).contains("cooking gauntlets");
        int level = client.getBoostedSkillLevel(Skill.COOKING);
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(fire ? "Fire" : "Range").color(TITLE_COLOR).build());
        double totalXp = 0;
        boolean any = false;
        for (Map.Entry<String, Integer> entry : carried.entrySet()) {
            SkillHelperData.Recipe recipe = helpers.cooks(entry.getKey());
            SkillHelperData.Food food = helpers.food(entry.getKey());
            if (recipe == null && food == null) {
                continue;
            }
            any = true;
            String name = (recipe != null ? recipe.name : food.name) + " x" + entry.getValue();
            if (recipe != null && level < recipe.level) {
                header(name, "Needs Level " + recipe.level, HudColors.BAD);
                continue;
            }
            header(name, recipe != null ? trim(recipe.xp) + " XP" : "", HudColors.GOOD);
            if (recipe != null) {
                totalXp += recipe.xp * entry.getValue();
            }
            int stop = food == null ? 0 : gauntlets && food.gauntlets > 0 ? food.gauntlets : fire ? food.fire : food.range;
            if (stop > 0) {
                line("  Burning:", stop >= 100 ? "Can always burn" : level >= stop ? "Never burns" : "Stops at Level " + stop,
                    level >= stop && stop < 100 ? HudColors.GOOD : HudColors.WARN);
            }
        }
        if (!any) {
            line("Raw Food:", "None carried", Color.WHITE);
        } else if (totalXp > 0) {
            line("XP For All:", String.format("%,d", Math.round(totalXp)), Color.WHITE);
        }
        if (gauntlets) {
            line("", "With cooking gauntlets", new Color(180, 180, 180));
        }
        addYourRate(Skill.COOKING);
        return true;
    }

    // ---- Smithing ----

    // For each kind of bar you carry: the best item you can make, how many, and the next unlock
    private boolean buildAnvilCard() {
        Map<String, Integer> carried = inventoryCounts();
        int level = client.getBoostedSkillLevel(Skill.SMITHING);
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text("Anvil").color(TITLE_COLOR).build());
        boolean any = false;
        for (Map.Entry<String, Integer> entry : carried.entrySet()) {
            List<SkillHelperData.Recipe> items = helpers.anvilItems(entry.getKey());
            if (items.isEmpty()) {
                continue;
            }
            any = true;
            int bars = entry.getValue();
            SkillHelperData.Recipe best = null;
            SkillHelperData.Recipe next = null;
            boolean byProfit = config.bestChoice() == SkillTrackerConfig.BestChoice.PROFIT;
            for (SkillHelperData.Recipe item : items) {
                if (item.level > level) {
                    if (next == null) {
                        next = item;
                    }
                    continue;
                }
                // Most XP (or profit) per bar; on a tie the higher-level item, which uses more bars per click
                if (best == null || perBar(item, entry.getKey(), byProfit) >= perBar(best, entry.getKey(), byProfit)) {
                    best = item;
                }
            }
            if (best == null) {
                header(entry.getKey() + " x" + bars, "Needs Level " + items.get(0).level, HudColors.BAD);
                continue;
            }
            header(entry.getKey() + " x" + bars, "", Color.WHITE);
            int perItem = best.materials.get(entry.getKey());
            int makes = bars / perItem;
            line("  Best: " + best.name, perItem + (perItem == 1 ? " bar" : " bars"), Color.WHITE);
            line("  Makes:", makes + " (" + String.format("%,d", Math.round(makes * best.xp)) + " XP)",
                makes > 0 ? HudColors.GOOD : HudColors.WARN);
            if (byProfit && makes > 0) {
                long profit = Math.round(perBar(best, entry.getKey(), true) * makes * perItem);
                line("  Profit:", String.format("%+,d gp", profit), profit >= 0 ? HudColors.GOOD : HudColors.BAD);
            }
            if (next != null) {
                line("  Next: " + next.name, "Level " + next.level, new Color(180, 180, 180));
            }
        }
        if (!any) {
            line("Bars:", "None carried", Color.WHITE);
        }
        addYourRate(Skill.SMITHING);
        return true;
    }

    // Each bar the ores you carry can make: how many (coal included) and the XP, or the level it needs
    private boolean buildFurnaceCard() {
        Map<String, Integer> carried = inventoryCounts();
        int level = client.getBoostedSkillLevel(Skill.SMITHING);
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text("Furnace").color(TITLE_COLOR).build());
        boolean any = false;
        for (SkillHelperData.Recipe bar : helpers.furnaceBars()) {
            boolean hasOres = true;
            int makes = Integer.MAX_VALUE;
            for (Map.Entry<String, Integer> material : bar.materials.entrySet()) {
                int have = carried.getOrDefault(material.getKey(), 0);
                if (!"Coal".equals(material.getKey()) && have == 0) {
                    hasOres = false;
                }
                makes = Math.min(makes, have / material.getValue());
            }
            if (!hasOres) {
                continue;
            }
            any = true;
            if (level < bar.level) {
                header(bar.name, "Needs Level " + bar.level, HudColors.BAD);
                continue;
            }
            header(bar.name, trim(bar.xp) + " XP each", Color.WHITE);
            Integer coal = bar.materials.get("Coal");
            line("  Makes:", makes + (makes > 0 ? " (" + String.format("%,d", Math.round(makes * bar.xp)) + " XP)" : ""),
                makes > 0 ? HudColors.GOOD : HudColors.WARN);
            if (coal != null && makes == 0) {
                line("  Needs:", coal + " coal per bar", HudColors.WARN);
            }
            if (config.bestChoice() == SkillTrackerConfig.BestChoice.PROFIT && makes > 0) {
                double each = price(bar.name);
                for (Map.Entry<String, Integer> material : bar.materials.entrySet()) {
                    each -= price(material.getKey()) * (double) material.getValue();
                }
                long profit = Math.round(each * makes);
                line("  Profit:", String.format("%+,d gp", profit), profit >= 0 ? HudColors.GOOD : HudColors.BAD);
            }
        }
        if (!any) {
            line("Ores:", "None carried", Color.WHITE);
        }
        addYourRate(Skill.SMITHING);
        return true;
    }

    // XP, or GE profit (item value less the bars), per bar used
    private double perBar(SkillHelperData.Recipe recipe, String bar, boolean byProfit) {
        int bars = recipe.materials.get(bar);
        if (!byProfit) {
            return recipe.xp / bars;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*) \\((\\d+)\\)$").matcher(recipe.name);
        String item = m.matches() ? m.group(1) : recipe.name;
        int quantity = m.matches() ? Integer.parseInt(m.group(2)) : 1;
        return (price(item) * (double) quantity - price(bar) * (double) bars) / bars;
    }

    private int price(String itemName) {
        return prices.computeIfAbsent(itemName, name -> {
            for (ItemPrice price : itemManager.search(name)) {
                if (price.getName().equalsIgnoreCase(name)) {
                    return (int) price.getPrice();
                }
            }
            return 0;
        });
    }

    /** Your inventory as item name -> how many, in inventory order. */
    private Map<String, Integer> inventoryCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ItemContainer inventory = client.getItemContainer(93);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item.getId() > 0) {
                    counts.merge(client.getItemDefinition(item.getId()).getName(), item.getQuantity(), Integer::sum);
                }
            }
        }
        return counts;
    }

    // ---- Hunter trap spots ----

    // The trap a spot takes, by the spot's name ("Set-trap Young tree" is a net trap)
    private static String trapFor(String target) {
        String name = target.toLowerCase();
        if (name.contains("young tree")) {
            return "Net trap";
        }
        if (name.contains("boulder")) {
            return "Deadfall";
        }
        if (name.contains("pit")) {
            return "Spiked pit";
        }
        return null;
    }

    // What you can catch with this trap: the creatures of that kind nearby, else all of them, with levels
    private boolean buildTrapSpotCard(MenuEntry entry, String option, String target) {
        String trap = trapFor(target);
        if (trap == null || !(option.contains("trap") || option.startsWith("set") || option.startsWith("lay"))) {
            return false;
        }
        List<SkillHelperData.Creature> creatures = helpers.creaturesFor(trap);
        if (creatures.isEmpty()) {
            return false;
        }
        WorldView view = client.getTopLevelWorldView();
        WorldPoint at = WorldPoint.fromScene(view, entry.getParam0(), entry.getParam1(), view.getPlane());
        java.util.Set<String> nearby = new java.util.HashSet<>();
        for (NPC npc : view.npcs()) {
            if (npc.getName() != null && npc.getWorldLocation().distanceTo(at) <= 15) {
                nearby.add(npc.getName().toLowerCase());
            }
        }
        List<SkillHelperData.Creature> here = new ArrayList<>();
        for (SkillHelperData.Creature creature : creatures) {
            if (nearby.contains(creature.name.toLowerCase())) {
                here.add(creature);
            }
        }
        int level = client.getRealSkillLevel(Skill.HUNTER);
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(target + " (" + trap + ")").color(TITLE_COLOR).build());
        for (SkillHelperData.Creature creature : here.isEmpty() ? creatures : here) {
            boolean can = level >= creature.level;
            line(creature.name + ":", "Lvl " + creature.level + (creature.xp > 0 ? ", " + trim(creature.xp) + " XP" : ""),
                can ? HudColors.GOOD : HudColors.BAD);
        }
        addYourRate(Skill.HUNTER);
        return true;
    }

    // ---- Sailing ----

    private boolean buildSailingCard(MenuEntry entry, String option, String target) {
        SkillHelperData.Shipwreck wreck = helpers.shipwreck(target);
        if (wreck != null) {
            int level = client.getRealSkillLevel(Skill.SAILING);
            panel.getChildren().clear();
            panel.getChildren().add(TitleComponent.builder().text(wreck.name).color(TITLE_COLOR).build());
            addRequirement(Skill.SAILING, wreck.level);
            line("Salvage XP:", trim(wreck.xp), Color.WHITE);
            if (wreck.salvage != null) {
                line("Gives:", wreck.salvage, Color.WHITE);
            }
            if (wreck.sortXp > 0) {
                line("Sorting XP:", trim(wreck.sortXp), Color.WHITE);
            }
            if (wreck.duration != null) {
                line("Lasts:", wreck.duration, Color.WHITE);
            }
            String hook = helpers.bestHook(level);
            line("Best Hook For You:", hook != null ? hook : "None yet (level 15)", hook != null ? Color.WHITE : HudColors.BAD);
            addYourRate(Skill.SAILING);
            return true;
        }
        // Sea charting: "Chart" objects, matched to the wiki's task list by where they are
        WorldView view = client.getTopLevelWorldView();
        WorldPoint at = WorldPoint.fromScene(view, entry.getParam0(), entry.getParam1(), view.getPlane());
        SkillHelperData.ChartTask task = option.equals("chart") ? helpers.chartTaskNear(at.getX(), at.getY(), 12) : null;
        if (task == null) {
            return false;
        }
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text("Sea Charting").color(TITLE_COLOR).build());
        // Long text gets the card's full width rather than the narrow right column
        panel.getChildren().add(LineComponent.builder().left(task.task).leftColor(Color.WHITE).build());
        addRequirement(Skill.SAILING, task.level);
        line("Type:", task.typeDescription(), Color.WHITE);
        if (task.xp > 0) {
            line("XP:", trim(task.xp), Color.WHITE);
        }
        if (task.sea != null) {
            line("Sea:", task.sea, new Color(180, 180, 180));
        }
        if (task.answer != null) {
            panel.getChildren().add(LineComponent.builder().left("Riddle Answer:").build());
            panel.getChildren().add(LineComponent.builder().left(task.answer).leftColor(HudColors.GOOD).build());
        }
        if (task.note != null) {
            panel.getChildren().add(LineComponent.builder().left(task.note).leftColor(new Color(180, 180, 180)).build());
        }
        return true;
    }

    private boolean buildShoalCard(NPC npc) {
        SkillHelperData.Shoal shoal = helpers.shoal(npc.getName());
        if (shoal == null) {
            return false;
        }
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(shoal.name).color(TITLE_COLOR).build());
        addRequirement(Skill.FISHING, shoal.level);
        line("Catch:", shoal.fish, Color.WHITE);
        line("Depths:", shoal.depths, Color.WHITE);
        line("Bait:", shoal.bait, Color.WHITE);
        line("", "Trawling net on your boat", new Color(180, 180, 180));
        addYourRate(Skill.FISHING);
        return true;
    }

    private boolean buildSeaCard(MenuEntry[] entries) {
        for (int i = entries.length - 2; i >= 0; i--) {
            NPC npc = entries[i].getNpc();
            if (npc != null) {
                return buildSeaMonsterCard(npc) || buildCurrentDuckCard(npc);
            }
        }
        return false;
    }

    /** A sea monster fought from your boat: its HP, what keel it can't hurt you through, and bounty parts. */
    private boolean buildSeaMonsterCard(NPC npc) {
        SkillHelperData.SeaMonster monster = helpers.seaMonster(npc.getName());
        if (monster == null) {
            return false;
        }
        panel.getChildren().clear();
        boolean defeated = hasAction(npc, "harvest");
        panel.getChildren().add(TitleComponent.builder()
            .text(monster.name + (monster.combat > 0 ? " (Lvl " + monster.combat + ")" : ""))
            .color(TITLE_COLOR).build());
        if (defeated) {
            line("Defeated:", "Harvest it for its drops", HudColors.GOOD);
        } else {
            int ratio = npc.getHealthRatio();
            int scale = npc.getHealthScale();
            int percent = ratio >= 0 && scale > 0 ? ratio * 100 / scale : 100;
            panel.getChildren().add(new CenteredHealthBarComponent(percent,
                (int) Math.ceil(percent * monster.hp / 100.0), monster.hp));
            line("Aggressive:", monster.aggressive ? "Yes" : "No",
                monster.aggressive ? HudColors.WARN : HudColors.GOOD);
            line("Max Hit (No Keel):", String.valueOf(monster.maxHits.length > 0 ? monster.maxHits[0] : 0), Color.WHITE);
            String safe = monster.safeKeel();
            line("No Damage With:", safe != null ? safe : "Nothing yet", safe != null ? Color.WHITE : HudColors.WARN);
            line("Fight With:", "Cannons, ranged or magic", new Color(180, 180, 180));
        }
        if (!monster.bounties.isEmpty()) {
            header("Bounty Parts", "", Color.WHITE);
            for (Map.Entry<String, Integer> bounty : monster.bounties.entrySet()) {
                levelLine("  " + bounty.getKey(), Skill.SAILING, bounty.getValue());
            }
        }
        return true;
    }

    /** Your current duck from a sea charting task: follow it, then collect it before it despawns. */
    private boolean buildCurrentDuckCard(NPC npc) {
        if (!"current duck".equalsIgnoreCase(npc.getName())) {
            return false;
        }
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text("Current Duck").color(TITLE_COLOR).build());
        line("Task:", "Sea charting (current)", Color.WHITE);
        if (hasAction(npc, "collect")) {
            line("Stopped:", "Collect it for the XP", HudColors.GOOD);
            Integer stoppedTick = duckStopped.get(npc);
            if (stoppedTick != null) {
                int left = Math.max(0, DUCK_DESPAWN_TICKS - (client.getTickCount() - stoppedTick)) * 600 / 1000;
                line("Despawns In:", String.format("%d:%02d", left / 60, left % 60),
                    left < 30 ? HudColors.WARN : Color.WHITE);
            }
        } else {
            line("Drifting:", "Follow it, or sail to", Color.WHITE);
            line("", "where it stops", Color.WHITE);
        }
        return true;
    }

    // Separate PvM/Slayer steps its monster card aside for ours (the Toolbelt joins them directly): before each
    // frame, tell it which NPC under the cursor our card shows, when that changes
    @Subscribe
    public void onBeforeRender(BeforeRender event) {
        int covered = -1;
        MenuEntry[] entries = client.getGameState() == GameState.LOGGED_IN ? client.getMenu().getMenuEntries() : new MenuEntry[0];
        if (entries.length > 0) {
            MenuEntry top = entries[entries.length - 1];
            NPC npc = top.getNpc();
            if (npc != null && coversNpc(npc, "attack".equalsIgnoreCase(Text.removeTags(top.getOption())))) {
                covered = npc.getIndex();
            }
        }
        if (covered != sentCardNpc) {
            sentCardNpc = covered;
            family.sendSkillingCard(covered);
        }
    }

    // When each current duck came to a stop (it despawns two minutes later)
    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        noteDuckStopped(event.getNpc());
    }

    // Also when it appears already stopped (the stopped duck can be a new NPC): counted from then
    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        noteDuckStopped(event.getNpc());
    }

    private void noteDuckStopped(NPC npc) {
        if ("current duck".equalsIgnoreCase(npc.getName()) && hasAction(npc, "collect")) {
            duckStopped.putIfAbsent(npc, client.getTickCount());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        duckStopped.remove(event.getNpc());
    }

    private static boolean hasAction(NPC npc, String action) {
        NPCComposition comp = npc.getTransformedComposition() != null ? npc.getTransformedComposition() : npc.getComposition();
        return comp != null && hasAction(comp, action);
    }

    private boolean buildHunterCard(NPC npc) {
        SkillHelperData.Creature creature = helpers.creature(npc.getName());
        if (creature == null) {
            return false;
        }
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(creature.name).color(TITLE_COLOR).build());
        addRequirement(Skill.HUNTER, creature.level);
        if (creature.xp > 0) {
            line("XP Each:", trim(creature.xp), Color.WHITE);
        }
        if (creature.trap != null) {
            line("Caught With:", creature.trap, Color.WHITE);
        }
        addYourRate(Skill.HUNTER);
        return true;
    }

    // ---- Farming patches ----

    private boolean buildFarmingCard(MenuEntry entry) {
        ObjectComposition composition = client.getObjectDefinition(entry.getIdentifier());
        if (composition == null) {
            return false;
        }
        WorldView view = client.getTopLevelWorldView();
        FarmingData.Patch patch = farming.patch(composition.getVarbitId(),
            WorldPoint.fromScene(view, entry.getParam0(), entry.getParam1(), view.getPlane()));
        if (patch == null) {
            return false;
        }
        FarmingData.State state = farming.state(patch);
        farming.clearCompostIfEmpty(patch, state);

        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(patch.title()).color(TITLE_COLOR).build());
        line("Location:", patch.region, new Color(180, 180, 180));
        if (state == null) {
            line("Status:", "Unknown", Color.WHITE);
            return true;
        }
        String compost = farming.compost(patch);
        if (state.isEmpty()) {
            boolean raked = FarmingData.WEEDS.equals(state.produce.id) && state.stage == 0;
            line("Status:", raked ? "Empty, ready to plant" : "Overgrown, rake it first",
                raked ? HudColors.GOOD : HudColors.WARN);
            // Compost goes on before planting
            line("Compost:", compost != null ? compost : "Not added yet", compost != null ? HudColors.GOOD : HudColors.WARN);
            addPlantable(patch);
            return true;
        }

        FarmingData.Crop crop = farming.crop(state.produce.name);
        line("Growing:", state.produce.name, Color.WHITE);
        line("Status:", statusText(state), statusColor(state));
        if (FarmingData.GROWING.equals(state.cropState)) {
            line("Stage:", (state.stage + 1) + " of " + state.produce.stages, Color.WHITE);
            long seconds = FarmingData.secondsUntilGrown(state, System.currentTimeMillis() / 1000);
            line("Ready In:", "~" + duration(seconds), Color.WHITE);
            if (FarmingData.WATERABLE.contains(patch.implementation)) {
                line("Watered:", state.watered ? "Yes" : "No", state.watered ? HudColors.GOOD : HudColors.WARN);
            }
        }
        line("Compost:", compost != null ? compost : "None", compost == null ? HudColors.WARN
            : "Ultracompost".equals(compost) ? HudColors.GOOD : Color.WHITE);
        if (crop != null) {
            addRequirement(Skill.FARMING, crop.level);
            if (crop.payment != null) {
                line("Pay Farmer:", crop.payment, Color.WHITE);
            }
        }
        return true;
    }

    // What grows here, each with its Farming level: the best few you can plant, then the next ones you'll unlock
    private void addPlantable(FarmingData.Patch patch) {
        List<FarmingData.Crop> crops = farming.cropsFor(patch.implementation);
        if (crops.isEmpty()) {
            return;
        }
        int level = client.getRealSkillLevel(Skill.FARMING);
        List<FarmingData.Crop> shown = new ArrayList<>();
        int unlocked = 0;
        for (FarmingData.Crop crop : crops) {
            if (crop.level <= level) {
                unlocked++;
            }
        }
        // Up to 4 you can plant (highest first), then up to 2 still locked
        for (int i = unlocked - 1; i >= 0 && shown.size() < 4; i--) {
            shown.add(crops.get(i));
        }
        for (int i = unlocked; i < crops.size() && i < unlocked + 2; i++) {
            shown.add(crops.get(i));
        }
        header("Can Grow Here", unlocked == 0 ? "None at your level" : "", HudColors.BAD);
        for (FarmingData.Crop crop : shown) {
            levelLine("  " + crop.name, Skill.FARMING, crop.level);
        }
    }

    private static String statusText(FarmingData.State state) {
        switch (state.cropState) {
            case FarmingData.HARVESTABLE: return "Ready to harvest";
            case FarmingData.DISEASED: return "Diseased (cure it)";
            case FarmingData.DEAD: return "Dead (clear it)";
            case FarmingData.GROWING: return "Growing";
            default: return state.cropState.charAt(0) + state.cropState.substring(1).toLowerCase();
        }
    }

    private static Color statusColor(FarmingData.State state) {
        switch (state.cropState) {
            case FarmingData.HARVESTABLE: return HudColors.GOOD;
            case FarmingData.DISEASED: return HudColors.WARN;
            case FarmingData.DEAD: return HudColors.BAD;
            default: return Color.WHITE;
        }
    }

    // 2h 05m, 35m
    private static String duration(long seconds) {
        long minutes = (seconds + 59) / 60;
        return minutes >= 60 ? (minutes / 60) + "h " + String.format("%02d", minutes % 60) + "m" : minutes + "m";
    }

    private static boolean isObjectAction(MenuAction type) {
        switch (type) {
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case WIDGET_TARGET_ON_GAME_OBJECT:
                return true;
            default:
                return false;
        }
    }

    private void buildResourceCard(SkillingData.Resource r) {
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(r.name).color(TITLE_COLOR).build());
        Skill skill = skill(r.skill);
        addRequirement(skill, r.level);
        if (r.xp > 0) {
            line("XP Each:", trim(r.xp), Color.WHITE);
        }
        if (r.loot != null) {
            line("Loot:", r.loot, Color.WHITE);
        }
        if (r.xpPerHour > 0 && showRates(skill)) {
            line("Wiki XP/hr:", NUMBER.format(r.xpPerHour), Color.WHITE);
        }
        addTools(r.skill, skill);
        addYourRate(skill);
    }

    private boolean buildNpcCard(NPC npc, String topOption) {
        NPCComposition comp = npc.getTransformedComposition() != null ? npc.getTransformedComposition() : npc.getComposition();
        if (comp == null) {
            return false;
        }
        SkillingData.Pickpocket pickpocket = data.pickpocket(npc.getName());
        // Pickpocket is often not the left-click option (Talk-to is), so check all of the NPC's actions;
        // when Attack is left-click the monster card shows instead
        if (pickpocket != null && hasAction(comp, "pickpocket")
            && (!"attack".equalsIgnoreCase(topOption) || preferSkilling(client, config))) {
            panel.getChildren().clear();
            panel.getChildren().add(TitleComponent.builder().text(npc.getName()).color(TITLE_COLOR).build());
            addRequirement(Skill.THIEVING, pickpocket.level);
            line("XP Each:", trim(pickpocket.xp), Color.WHITE);
            if (pickpocket.ardyCloakLevel > 0) {
                boolean never = client.getRealSkillLevel(Skill.THIEVING) >= pickpocket.ardyCloakLevel;
                line("Never Fails At:", "Level " + pickpocket.ardyCloakLevel + " (Ardy cloak)",
                    never ? HudColors.GOOD : Color.WHITE);
            }
            if (!pickpocket.stunDamage.isEmpty()) {
                line("Stun Damage:", pickpocket.stunDamage, Color.WHITE);
            }
            addYourRate(Skill.THIEVING);
            return true;
        }

        // Every kind of fishing spot, from the wiki (karambwan, anglerfish, dark crabs, eels, minnows, boat spots...)
        SkillHelperData.FishingSpot spot = helpers.fishingSpot(npc.getId());
        if (spot == null && npc.getTransformedComposition() != null) {
            spot = helpers.fishingSpot(npc.getTransformedComposition().getId());
        }
        if (spot != null) {
            buildFishingSpotCard(npc, spot);
            return true;
        }

        List<String> fishingOptions = new ArrayList<>();
        for (String action : comp.getActions()) {
            if (action != null && SkillingData.isFishingOption(action)) {
                fishingOptions.add(action);
            }
        }
        if (fishingOptions.isEmpty()) {
            return (config.showSailingHover() && (buildShoalCard(npc) || buildSeaMonsterCard(npc) || buildCurrentDuckCard(npc)))
                || (config.showHunterHover() && buildHunterCard(npc));
        }
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(npc.getName()).color(TITLE_COLOR).build());
        List<String> owned = ownedItemNames();
        for (String option : fishingOptions) {
            // Each method: its name and the tool it needs (green if you have it), then each fish with its level
            String tool = SkillingData.fishingTool(option);
            boolean hasTool = SkillingData.hasFishingTool(option, owned);
            header(option, tool != null ? tool : "", hasTool ? HudColors.GOOD : HudColors.BAD);
            for (SkillingData.Fish fish : SkillingData.fishFor(option, fishingOptions)) {
                catchLine(fish.name, fish.xp, fish.level);
            }
        }
        addYourRate(Skill.FISHING);
        return true;
    }

    // Each way to fish the spot with the tool it needs (green if you have it), then each catch with its level
    private void buildFishingSpotCard(NPC npc, SkillHelperData.FishingSpot spot) {
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(npc.getName()).color(TITLE_COLOR).build());
        List<String> owned = ownedItemNames();
        for (Map.Entry<String, List<SkillHelperData.Catch>> option : spot.options.entrySet()) {
            String tool = option.getValue().isEmpty() ? "" : option.getValue().get(0).tool;
            boolean hasTool = tool == null || tool.isEmpty() || hasTool(tool, owned);
            header(option.getKey(), tool != null ? tool : "", hasTool ? HudColors.GOOD : HudColors.BAD);
            for (SkillHelperData.Catch fish : option.getValue()) {
                catchLine(fish.name, fish.xp, fish.level);
            }
        }
        addYourRate(Skill.FISHING);
    }

    // Every part of "Harpoon + Lantern" carried or worn; better versions count ("Dragon harpoon", "Pearl fishing rod")
    static boolean hasTool(String tool, List<String> owned) {
        for (String part : tool.toLowerCase().split("\\s*\\+\\s*")) {
            boolean found = false;
            for (String item : owned) {
                // ("fly fishing rod" and "oily fishing rod" aren't a "fishing rod" for bait)
                boolean better = item.endsWith(" " + part) && !item.startsWith("fly ") && !item.startsWith("oily ");
                if (item.equals(part) || item.startsWith(part + " (") || better) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this card shows the NPC rather than PvM's monster card: a sea monster (Sailing card), or a
     * pickpocket target when Attack isn't the left-click option or you're not out to fight.
     */
    boolean coversNpc(NPC npc, boolean attackIsLeftClick) {
        if (config.showSailingHover() && helpers.seaMonster(npc.getName()) != null) {
            return true;
        }
        boolean skillingFirst = !attackIsLeftClick || preferSkilling(client, config);
        return skillingFirst && config.showSkillingHover() && isPickpocketTarget(data, npc);
    }

    /** Auto retaliate is off and the player wants skilling cards then (they're not out to fight). */
    static boolean preferSkilling(Client client, SkillTrackerConfig config) {
        return config.skillingWhenNotRetaliating() && client.getVarpValue(VarPlayerID.OPTION_NODEF) == 1;
    }

    static boolean isPickpocketTarget(SkillingData data, NPC npc) {
        NPCComposition comp = npc.getTransformedComposition() != null ? npc.getTransformedComposition() : npc.getComposition();
        return comp != null && data.pickpocket(npc.getName()) != null && hasAction(comp, "pickpocket");
    }

    private static boolean hasAction(NPCComposition comp, String action) {
        for (String a : comp.getActions()) {
            if (a != null && a.equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }

    // A sub-heading inside a card, e.g. a fishing method, with an optional note on the right
    private void header(String text, String right, Color rightColor) {
        panel.getChildren().add(LineComponent.builder().left(text).leftColor(HEADER_COLOR)
            .right(right).rightColor(rightColor).build());
    }

    // A catch: its picture, its name (without "Raw ": the picture shows that) and XP, and the level it needs
    private void catchLine(String name, double xp, int level) {
        boolean ok = client.getRealSkillLevel(Skill.FISHING) >= level;
        String shown = name.startsWith("Raw ") ? Character.toUpperCase(name.charAt(4)) + name.substring(5) : name;
        panel.getChildren().add(IconLineComponent.iconThenLabel(itemIcons.get(name),
            shown + (xp > 0 ? " (" + trim(xp) + " xp)" : ""), "Lvl " + level, ok ? HudColors.GOOD : HudColors.BAD));
    }

    private void levelLine(String left, Skill skill, int level) {
        boolean ok = client.getRealSkillLevel(skill) >= level;
        panel.getChildren().add(IconLineComponent.textThenIcon(left, skillIcons.getSkillImage(skill, true),
            "Level " + level, ok ? HudColors.GOOD : HudColors.BAD));
    }

    private void addRequirement(Skill skill, int level) {
        if (skill == null || level <= 0) {
            return;
        }
        boolean ok = client.getRealSkillLevel(skill) >= level;
        panel.getChildren().add(IconLineComponent.textThenIcon("Requires:", skillIcons.getSkillImage(skill, true),
            "Level " + level, ok ? HudColors.GOOD : HudColors.BAD));
    }

    private void addTools(String skillName, Skill skill) {
        List<SkillingData.Tool> tools = SkillingData.toolsFor(skillName);
        if (tools.isEmpty() || skill == null) {
            return;
        }
        SkillingData.Tool owned = SkillingData.bestOwnedTool(tools, ownedItemNames());
        SkillingData.Tool best = SkillingData.bestToolForLevel(tools, client.getRealSkillLevel(skill));
        // Each tool in its metal's colour
        line("Your Tool:", owned != null ? owned.name : "None", owned != null ? owned.color : HudColors.BAD);
        if (best != null && best != owned) {
            line("Best For You:", best.name, best.color);
        }
    }

    // XP rates only while you're training that skill (and the option is on)
    private boolean showRates(Skill skill) {
        return skill != null && config.showSkillingRates() && plugin.isTraining(skill);
    }

    private void addYourRate(Skill skill) {
        if (!showRates(skill)) {
            return;
        }
        int rate = plugin.getSkillXpPerHour(skill);
        if (rate > 0) {
            line("Your Rate:", NUMBER.format(rate) + " /hr", Color.WHITE);
        }
    }

    private List<String> ownedItemNames() {
        return ownedItemNames(93, 94);
    }

    private List<String> ownedItemNames(int... containers) {
        List<String> names = new ArrayList<>();
        for (int containerId : containers) {
            ItemContainer container = client.getItemContainer(containerId);
            if (container == null) {
                continue;
            }
            for (Item item : container.getItems()) {
                if (item.getId() > 0) {
                    names.add(client.getItemDefinition(item.getId()).getName().toLowerCase());
                }
            }
        }
        return names;
    }

    private void line(String left, String right, Color color) {
        panel.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(color).build());
    }

    private static Skill skill(String name) {
        try {
            return Skill.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
