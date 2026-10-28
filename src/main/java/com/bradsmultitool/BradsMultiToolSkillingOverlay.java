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
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.Text;

/**
 * Hover card for trees, rocks, stalls, pickpocket targets and fishing spots, plus a time-left pie
 * over the tree you're chopping.
 */
public class BradsMultiToolSkillingOverlay extends Overlay {

    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance();
    private static final Color TITLE_COLOR = new Color(0, 220, 220);

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final SkillingData data;
    private final TreeTimer treeTimer;
    private final PanelComponent panel = new PanelComponent();
    private final ProgressPieComponent pie = new ProgressPieComponent();

    @Inject
    public BradsMultiToolSkillingOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config,
                                         SkillingData data, TreeTimer treeTimer) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.data = data;
        this.treeTimer = treeTimer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGHEST);
        panel.setPreferredSize(new Dimension(205, 0));
        pie.setDiameter(22);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(FontManager.getRunescapeSmallFont());

        if (config.showTreeTimer()) {
            drawTreePie(graphics);
        }
        if (config.showSkillingHover() && !client.isMenuOpen() && buildCard()) {
            CursorTooltip.render(client, graphics, panel);
        }
        return null;
    }

    // A pie over the tree you're chopping that empties as it nears falling (green, then yellow, then red)
    private void drawTreePie(Graphics2D graphics) {
        WorldPoint tree = treeTimer.getTree();
        int ticks = treeTimer.ticksLeft();
        if (tree == null || ticks < 0 || treeTimer.totalTicks() <= 0) {
            return;
        }
        LocalPoint local = treeCentre(tree);
        if (local == null) {
            return;
        }
        Point at = Perspective.localToCanvas(client, local, client.getTopLevelWorldView().getPlane(), 0);
        if (at == null) {
            return;
        }
        double left = Math.max(0, Math.min(1, (double) ticks / treeTimer.totalTicks()));
        Color color = left > 0.5 ? BradsMultiToolOverlay.GOOD : left > 0.2 ? BradsMultiToolOverlay.WARN : BradsMultiToolOverlay.BAD;
        pie.setPosition(at);
        pie.setProgress(left);
        pie.setFill(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
        pie.setBorderColor(color);
        pie.render(graphics);
    }

    /**
     * The middle of the tree's footprint. Big trees cover 2x2 or 3x3 tiles and the click gives one tile,
     * so take the biggest object on it (the tree) and work out its centre from the tiles it covers.
     */
    private LocalPoint treeCentre(WorldPoint tree) {
        WorldView view = client.getTopLevelWorldView();
        LocalPoint clicked = LocalPoint.fromWorld(view, tree);
        if (clicked == null) {
            return null;
        }
        Tile tile = view.getScene().getTiles()[view.getPlane()][clicked.getSceneX()][clicked.getSceneY()];
        GameObject biggest = null;
        int biggestArea = 0;
        if (tile != null) {
            for (GameObject object : tile.getGameObjects()) {
                if (object == null) {
                    continue;
                }
                int area = (object.getSceneMaxLocation().getX() - object.getSceneMinLocation().getX() + 1)
                    * (object.getSceneMaxLocation().getY() - object.getSceneMinLocation().getY() + 1);
                if (area > biggestArea) {
                    biggest = object;
                    biggestArea = area;
                }
            }
        }
        if (biggest == null) {
            return clicked;
        }
        // Average of the corner tiles' centres = centre of the footprint
        LocalPoint min = LocalPoint.fromScene(biggest.getSceneMinLocation().getX(), biggest.getSceneMinLocation().getY(), view);
        LocalPoint max = LocalPoint.fromScene(biggest.getSceneMaxLocation().getX(), biggest.getSceneMaxLocation().getY(), view);
        return new LocalPoint((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, view);
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
            SkillingData.Resource resource = data.object(entry.getIdentifier(), Text.removeTags(entry.getTarget()));
            if (resource != null) {
                buildResourceCard(resource);
                return true;
            }
        }
        return false;
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
        if (r.xpPerHour > 0) {
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
            for (SkillingData.Fish fish : SkillingData.fishFor(option)) {
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

    private void addYourRate(Skill skill) {
        if (skill == null) {
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
