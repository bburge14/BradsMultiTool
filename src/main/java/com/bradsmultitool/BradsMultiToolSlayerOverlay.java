package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Slayer task card: task, kills left, kill rate, time to finish and required item.
 */
public class BradsMultiToolSlayerOverlay extends Overlay {

    private static final Color TITLE_COLOR = new Color(255, 185, 0);

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public BradsMultiToolSlayerOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        panel.setPreferredSize(new Dimension(170, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        SlayerTracker tracker = plugin.getSlayerTracker();
        if (!config.showSlayerTask() || client.getGameState() != GameState.LOGGED_IN || !tracker.hadTask()) {
            return null;
        }
        graphics.setFont(FontManager.getRunescapeSmallFont());
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder().text("Slayer: " + tracker.getTaskName()).color(TITLE_COLOR).build());
        panel.getChildren().add(LineComponent.builder().left("Left:").right(String.valueOf(tracker.getRemaining())).build());

        double rate = tracker.killsPerHour();
        panel.getChildren().add(LineComponent.builder()
            .left("Kill Rate:")
            .right(rate > 0 ? Math.round(rate) + " /hr" : "--")
            .build());
        if (rate > 0) {
            long minutes = Math.round(tracker.getRemaining() / rate * 60);
            panel.getChildren().add(LineComponent.builder()
                .left("Done In:")
                .right(minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m")
                .build());
        }

        SlayerTracker.Requirement requirement = tracker.getRequirement();
        if (requirement != null) {
            boolean has = tracker.isHasRequiredItem();
            panel.getChildren().add(LineComponent.builder()
                .left("Needs:")
                .right(requirement.display + (has ? "" : " (missing)"))
                .rightColor(has ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD)
                .build());
        }
        return panel.render(graphics);
    }
}
