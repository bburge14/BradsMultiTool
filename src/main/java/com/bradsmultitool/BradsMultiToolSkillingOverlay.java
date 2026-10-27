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
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.Text;

/**
 * Hover card for trees, rocks, stalls, pickpocket targets and fishing spots, plus a time-left label
 * above the tree you're chopping.
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
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(FontManager.getRunescapeSmallFont());

        if (config.showTreeTimer()) {
            drawTreeLabel(graphics);
        }
        if (config.showSkillingHover() && !client.isMenuOpen() && buildCard()) {
            CursorTooltip.render(client, graphics, panel);
        }
        return null;
    }

    private void drawTreeLabel(Graphics2D graphics) {
        WorldPoint tree = treeTimer.getTree();
        int ticks = treeTimer.ticksLeft();
        if (tree == null || ticks < 0) {
            return;
        }
        LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), tree);
        if (local == null) {
            return;
        }
        String text = "~" + Math.round(ticks * 0.6) + "s";
        Point at = Perspective.getCanvasTextLocation(client, graphics, local, text, 200);
        if (at == null) {
            return;
        }
        double left = treeTimer.totalTicks() > 0 ? (double) ticks / treeTimer.totalTicks() : 1;
        Color color = left > 0.5 ? BradsMultiToolOverlay.GOOD : left > 0.2 ? BradsMultiToolOverlay.WARN : BradsMultiToolOverlay.BAD;
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, at.getX() + 1, at.getY() + 1);
        graphics.setColor(color);
        graphics.drawString(text, at.getX(), at.getY());
    }

    /** Fills the panel for whatever skilling target is under the cursor; false if there isn't one. */
    private boolean buildCard() {
        MenuEntry[] entries = client.getMenu().getMenuEntries();
        for (int i = entries.length - 1; i >= 0; i--) {
            MenuEntry entry = entries[i];
            NPC npc = entry.getNpc();
            if (npc != null) {
                return buildNpcCard(npc, Text.removeTags(entry.getOption()));
            }
            if (isObjectAction(entry.getType())) {
                SkillingData.Resource resource = data.object(entry.getIdentifier());
                if (resource != null) {
                    buildResourceCard(resource);
                    return true;
                }
                return false;
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
        if (r.chopTicks > 0) {
            line("Lasts:", "~" + Math.round(r.chopTicks * 0.6) + "s of chopping", Color.WHITE);
            if (treeTimer.getTree() != null && treeTimer.totalTicks() == r.chopTicks && treeTimer.ticksLeft() >= 0) {
                line("Time Left:", "~" + Math.round(treeTimer.ticksLeft() * 0.6) + "s", Color.WHITE);
            }
        }
        if (r.respawnSeconds > 0) {
            line("Respawn:", trim(r.respawnSeconds) + "s", Color.WHITE);
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
        if (pickpocket != null && "pickpocket".equalsIgnoreCase(topOption)) {
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
        line("Your Tool:", owned != null ? owned.name : "None",
            owned == null ? BradsMultiToolOverlay.BAD : owned == best ? BradsMultiToolOverlay.GOOD : Color.WHITE);
        if (best != null && best != owned) {
            line("Best For You:", best.name, Color.WHITE);
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
