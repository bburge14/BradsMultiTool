package com.bradsmultitool;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Outlines, in your inventory and bank, the materials of what's suggested for a skill goal (see
 * {@link GoalItems}), with a tooltip saying which goal it's for.
 */
@Singleton
public class BradsMultiToolGoalItemOverlay extends WidgetItemOverlay {

    private final Client client;
    private final SkillTrackerConfig config;
    private final GoalItems goalItems;
    private final ItemManager itemManager;
    private final TooltipManager tooltipManager;

    @Inject
    BradsMultiToolGoalItemOverlay(Client client, SkillTrackerConfig config, GoalItems goalItems,
                                  ItemManager itemManager, TooltipManager tooltipManager) {
        this.client = client;
        this.config = config;
        this.goalItems = goalItems;
        this.itemManager = itemManager;
        this.tooltipManager = tooltipManager;
        showOnInventory();
        showOnBank();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
        if (!config.highlightGoalItems() || goalItems.highlighted().isEmpty()) {
            return;
        }
        String tooltip = goalItems.tooltip(itemManager.getItemComposition(itemId).getName());
        if (tooltip == null) {
            return;
        }
        Rectangle bounds = widgetItem.getCanvasBounds();
        BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.trainingHighlightColor());
        graphics.drawImage(outline, bounds.x, bounds.y, null);
        Point mouse = client.getMouseCanvasPosition();
        if (mouse != null && bounds.contains(mouse.getX(), mouse.getY())) {
            tooltipManager.add(new Tooltip(tooltip));
        }
    }
}
