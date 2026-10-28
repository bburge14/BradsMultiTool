package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
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
import net.runelite.api.Skill;
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
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final SkillingData data;
    private final FarmingData farming;
    private final SkillHelperData helpers;
    private final BirdhouseTracker birdhouses;
    private final SkillIconManager skillIcons;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public BradsMultiToolSkillingOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config,
                                         SkillingData data, FarmingData farming, SkillHelperData helpers,
                                         BirdhouseTracker birdhouses, SkillIconManager skillIcons) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.data = data;
        this.farming = farming;
        this.helpers = helpers;
        this.birdhouses = birdhouses;
        this.skillIcons = skillIcons;
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

    private boolean buildSkillHelperCard(MenuEntry entry) {
        int id = entry.getIdentifier();
        ObjectComposition composition = client.getObjectDefinition(id);
        String option = Text.removeTags(entry.getOption()).toLowerCase();
        String target = Text.removeTags(entry.getTarget());

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
        if (config.showCookingHover() && isCookingSpot(target, option)) {
            return buildCookingCard(target.toLowerCase().contains("fire"));
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
            line("Status:", "Empty", BradsMultiToolOverlay.WARN);
            return;
        }
        if (!BirdhouseTracker.isSeeded(value)) {
            line("Status:", "Built (add seeds)", BradsMultiToolOverlay.WARN);
            return;
        }
        long seeded = birdhouses.seededAt(spot);
        if (seeded == 0) {
            line("Status:", "Seeded", Color.WHITE);
            return;
        }
        long left = seeded + BirdhouseTracker.FULL_AFTER_SECONDS - System.currentTimeMillis() / 1000;
        line("Status:", left <= 0 ? "Full" : "Filling", left <= 0 ? BradsMultiToolOverlay.GOOD : Color.WHITE);
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
            line("Runes / Essence:", String.valueOf(rune.perEssence(level)), BradsMultiToolOverlay.GOOD);
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

    // Burn level of each raw food you're carrying, for this fire or range (and with cooking gauntlets if worn)
    private boolean buildCookingCard(boolean fire) {
        ItemContainer inventory = client.getItemContainer(93);
        if (inventory == null) {
            return false;
        }
        boolean gauntlets = ownedItemNames(94).contains("cooking gauntlets");
        int level = client.getBoostedSkillLevel(Skill.COOKING);
        java.util.Set<String> seen = new java.util.HashSet<>();
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(fire ? "Fire" : "Range").color(TITLE_COLOR).build());
        for (Item item : inventory.getItems()) {
            if (item.getId() <= 0) {
                continue;
            }
            String name = client.getItemDefinition(item.getId()).getName();
            SkillHelperData.Food food = helpers.food(name);
            if (food == null || !seen.add(food.name)) {
                continue;
            }
            int stop = gauntlets && food.gauntlets > 0 ? food.gauntlets : fire ? food.fire : food.range;
            if (stop <= 0) {
                continue;
            }
            boolean safe = level >= stop;
            line(food.name + ":", stop >= 100 ? "Can always burn" : safe ? "Never burns" : "Stops at " + stop,
                safe ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.WARN);
        }
        if (seen.isEmpty()) {
            line("Raw Food:", "None carried", Color.WHITE);
        }
        if (gauntlets) {
            line("", "With cooking gauntlets", new Color(180, 180, 180));
        }
        addYourRate(Skill.COOKING);
        return true;
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
                raked ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.WARN);
            // Compost goes on before planting
            line("Compost:", compost != null ? compost : "Not added yet", compost != null ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.WARN);
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
                line("Watered:", state.watered ? "Yes" : "No", state.watered ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.WARN);
            }
        }
        line("Compost:", compost != null ? compost : "None", compost == null ? BradsMultiToolOverlay.WARN
            : "Ultracompost".equals(compost) ? BradsMultiToolOverlay.GOOD : Color.WHITE);
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
        header("Can Grow Here", unlocked == 0 ? "None at your level" : "", BradsMultiToolOverlay.BAD);
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
            case FarmingData.HARVESTABLE: return BradsMultiToolOverlay.GOOD;
            case FarmingData.DISEASED: return BradsMultiToolOverlay.WARN;
            case FarmingData.DEAD: return BradsMultiToolOverlay.BAD;
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
        if (pickpocket != null && hasAction(comp, "pickpocket") && !"attack".equalsIgnoreCase(topOption)) {
            panel.getChildren().clear();
            panel.getChildren().add(TitleComponent.builder().text(npc.getName()).color(TITLE_COLOR).build());
            addRequirement(Skill.THIEVING, pickpocket.level);
            line("XP Each:", trim(pickpocket.xp), Color.WHITE);
            if (pickpocket.ardyCloakLevel > 0) {
                boolean never = client.getRealSkillLevel(Skill.THIEVING) >= pickpocket.ardyCloakLevel;
                line("Never Fails At:", "Level " + pickpocket.ardyCloakLevel + " (Ardy cloak)",
                    never ? BradsMultiToolOverlay.GOOD : Color.WHITE);
            }
            if (!pickpocket.stunDamage.isEmpty()) {
                line("Stun Damage:", pickpocket.stunDamage, Color.WHITE);
            }
            addYourRate(Skill.THIEVING);
            return true;
        }

        List<String> fishingOptions = new ArrayList<>();
        for (String action : comp.getActions()) {
            if (action != null && SkillingData.isFishingOption(action)) {
                fishingOptions.add(action);
            }
        }
        if (fishingOptions.isEmpty()) {
            return config.showHunterHover() && buildHunterCard(npc);
        }
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(npc.getName()).color(TITLE_COLOR).build());
        List<String> owned = ownedItemNames();
        for (String option : fishingOptions) {
            // Each method: its name and the tool it needs (green if you have it), then each fish with its level
            String tool = SkillingData.fishingTool(option);
            boolean hasTool = SkillingData.hasFishingTool(option, owned);
            header(option, tool != null ? tool : "", hasTool ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD);
            for (SkillingData.Fish fish : SkillingData.fishFor(option, fishingOptions)) {
                levelLine("  " + fish.name + " (" + trim(fish.xp) + " xp)", Skill.FISHING, fish.level);
            }
        }
        addYourRate(Skill.FISHING);
        return true;
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

    // "Shrimps (10 xp)        Level 1 [icon]", green if you have the level
    private void levelLine(String left, Skill skill, int level) {
        boolean ok = client.getRealSkillLevel(skill) >= level;
        panel.getChildren().add(IconLineComponent.textThenIcon(left, skillIcons.getSkillImage(skill, true),
            "Level " + level, ok ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD));
    }

    private void addRequirement(Skill skill, int level) {
        if (skill == null || level <= 0) {
            return;
        }
        boolean ok = client.getRealSkillLevel(skill) >= level;
        panel.getChildren().add(IconLineComponent.textThenIcon("Requires:", skillIcons.getSkillImage(skill, true),
            "Level " + level, ok ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD));
    }

    private void addTools(String skillName, Skill skill) {
        List<SkillingData.Tool> tools = SkillingData.toolsFor(skillName);
        if (tools.isEmpty() || skill == null) {
            return;
        }
        SkillingData.Tool owned = SkillingData.bestOwnedTool(tools, ownedItemNames());
        SkillingData.Tool best = SkillingData.bestToolForLevel(tools, client.getRealSkillLevel(skill));
        // Each tool in its metal's colour
        line("Your Tool:", owned != null ? owned.name : "None", owned != null ? owned.color : BradsMultiToolOverlay.BAD);
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
