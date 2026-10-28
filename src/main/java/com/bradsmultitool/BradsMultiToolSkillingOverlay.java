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

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final SkillingData data;
    private final FarmingData farming;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public BradsMultiToolSkillingOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config,
                                         SkillingData data, FarmingData farming) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.data = data;
        this.farming = farming;
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
            SkillingData.Resource resource = data.object(entry.getIdentifier(), Text.removeTags(entry.getTarget()));
            if (resource != null) {
                buildResourceCard(resource);
                return true;
            }
        }
        return false;
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
        line("Location:", patch.region, Color.WHITE);
        if (state == null) {
            line("Status:", "Unknown", Color.WHITE);
            return true;
        }
        int level = client.getRealSkillLevel(Skill.FARMING);
        if (state.isEmpty()) {
            boolean raked = FarmingData.WEEDS.equals(state.produce.id) && state.stage == 0;
            line("Status:", raked ? "Ready to plant" : "Weeds (rake it)", raked ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.WARN);
            addPlantable(patch, level);
        } else {
            FarmingData.Crop crop = farming.crop(state.produce.name);
            line("Crop:", state.produce.name, Color.WHITE);
            line("Status:", statusText(state), statusColor(state));
            if (FarmingData.GROWING.equals(state.cropState)) {
                long seconds = FarmingData.secondsUntilGrown(state, System.currentTimeMillis() / 1000);
                line("Grown In:", "~" + duration(seconds), Color.WHITE);
            }
            if (crop != null) {
                line("Requires:", "Lvl " + crop.level + " Farming", level >= crop.level ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD);
                if (crop.payment != null) {
                    line("Protection:", crop.payment, Color.WHITE);
                }
            }
            if (FarmingData.GROWING.equals(state.cropState) && FarmingData.WATERABLE.contains(patch.implementation)) {
                line("Watered:", state.watered ? "Yes" : "No", state.watered ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.WARN);
            }
        }
        String compost = farming.compost(patch);
        line("Compost:", compost != null ? compost : "None seen", compost == null ? BradsMultiToolOverlay.WARN
            : "Ultracompost".equals(compost) ? BradsMultiToolOverlay.GOOD : Color.WHITE);
        return true;
    }

    // The best crops you can plant here, plus the next one you'll unlock
    private void addPlantable(FarmingData.Patch patch, int level) {
        List<FarmingData.Crop> crops = farming.cropsFor(patch.implementation);
        if (crops.isEmpty()) {
            return;
        }
        List<FarmingData.Crop> usable = new ArrayList<>();
        FarmingData.Crop next = null;
        for (FarmingData.Crop crop : crops) {
            if (crop.level <= level) {
                usable.add(crop);
            } else if (next == null) {
                next = crop;
            }
        }
        String label = "Can Plant:";
        for (int i = Math.max(0, usable.size() - 3); i < usable.size(); i++) {
            FarmingData.Crop crop = usable.get(i);
            line(label, crop.name + " (" + crop.level + ")", BradsMultiToolOverlay.GOOD);
            label = "";
        }
        if (usable.isEmpty()) {
            line("Can Plant:", "Nothing yet", BradsMultiToolOverlay.BAD);
        }
        if (next != null) {
            line("Next Unlock:", next.name + " (" + next.level + ")", BradsMultiToolOverlay.BAD);
        }
    }

    private static String statusText(FarmingData.State state) {
        switch (state.cropState) {
            case FarmingData.HARVESTABLE: return "Ready";
            case FarmingData.DISEASED: return "Diseased (cure it)";
            case FarmingData.DEAD: return "Dead (clear it)";
            case FarmingData.GROWING: return "Growing (" + (state.stage + 1) + "/" + state.produce.stages + ")";
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
                line("Never Fails At:", "Lvl " + pickpocket.ardyCloakLevel + " (Ardy cloak)",
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
            return false;
        }
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text(npc.getName()).color(TITLE_COLOR).build());
        int level = client.getRealSkillLevel(Skill.FISHING);
        for (String option : fishingOptions) {
            // One fish per row, green when your level can catch it
            String label = option + ":";
            for (SkillingData.Fish fish : SkillingData.fishFor(option, fishingOptions)) {
                line(label, fish.name + " (" + fish.level + ")",
                    fish.level <= level ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD);
                label = "";
            }
            String tool = SkillingData.fishingTool(option);
            if (tool != null) {
                line("", tool, new Color(180, 180, 180));
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

    private void addRequirement(Skill skill, int level) {
        if (skill == null || level <= 0) {
            return;
        }
        boolean ok = client.getRealSkillLevel(skill) >= level;
        line("Requires:", "Lvl " + level + " " + skill.getName(), ok ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD);
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
        List<String> names = new ArrayList<>();
        for (int containerId : new int[]{93, 94}) {
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
