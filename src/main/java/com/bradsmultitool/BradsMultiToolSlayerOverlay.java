package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Slayer card: on a task, kills left, kill rate, time to finish, required item and where to go (with how
 * to get there); with no task, the best Slayer master for you, where they are and how to get there.
 */
public class BradsMultiToolSlayerOverlay extends Overlay {

    private static final Color TITLE_COLOR = new Color(255, 185, 0);
    private static final Color PLACE_COLOR = new Color(255, 200, 80);
    private static final Color SOFT = new Color(180, 180, 180);
    private static final int MAX_PLACES = 3;
    private static final int MAX_ROUTES = 3;

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final SlayerData slayerData;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public BradsMultiToolSlayerOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config,
                                       SlayerData slayerData) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.slayerData = slayerData;
        setPosition(OverlayPosition.TOP_LEFT);
        panel.setPreferredSize(new Dimension(220, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        SlayerTracker tracker = plugin.getSlayerTracker();
        if (!config.showSlayerTask() || plugin.isSlayerCardHidden() || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        graphics.setFont(FontManager.getRunescapeSmallFont());
        panel.getChildren().clear();
        if (!tracker.hadTask()) {
            if (!config.showSlayerMasterCard()) {
                return null;
            }
            buildMasterCard();
            return panel.render(graphics);
        }
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
        if (config.showSlayerDirections()) {
            addDirections(slayerData.task(tracker.getTaskName()));
        }
        return panel.render(graphics);
    }

    // No task: which master to see (the best one for your levels), where they are and how to get there
    private void buildMasterCard() {
        int combat = client.getLocalPlayer() != null ? client.getLocalPlayer().getCombatLevel() : 3;
        int slayer = client.getRealSkillLevel(Skill.SLAYER);
        SlayerData.Master master = slayerData.bestMaster(combat, slayer);
        panel.getChildren().add(TitleComponent.builder().text("Slayer: No Task").color(TITLE_COLOR).build());
        if (master == null) {
            return;
        }
        panel.getChildren().add(LineComponent.builder().left("Get One From:").right(master.name)
            .rightColor(BradsMultiToolOverlay.GOOD).build());
        if (master.quest != null) {
            panel.getChildren().add(LineComponent.builder().left("Quest:").right(master.quest).rightColor(SOFT).build());
        }
        if (master.where != null) {
            wrapped(master.where, Color.WHITE);
        }
        routes("Getting There:", master.routes);
        SlayerData.Master next = slayerData.nextMaster(master);
        if (next != null) {
            String needs = "Combat " + next.combat + (next.slayer > 0 ? ", Slayer " + next.slayer : "");
            panel.getChildren().add(LineComponent.builder().left("Next: " + next.name).right(needs).rightColor(SOFT).build());
        }
    }

    // On task: where the wiki says to kill it (Wilderness places last) and how to reach the first one
    private void addDirections(SlayerData.Task task) {
        if (task == null || task.places.isEmpty()) {
            return;
        }
        List<SlayerData.Place> places = new ArrayList<>(task.places);
        places.sort(Comparator.comparing(SlayerData.Place::inWilderness));
        panel.getChildren().add(LineComponent.builder().left("Where To Go:").leftColor(TITLE_COLOR).build());
        for (SlayerData.Place place : places.subList(0, Math.min(MAX_PLACES, places.size()))) {
            panel.getChildren().add(LineComponent.builder().left(place.name).leftColor(PLACE_COLOR)
                .right(place.tags()).rightColor(SOFT).build());
        }
        if (places.size() > MAX_PLACES) {
            wrapped("+" + (places.size() - MAX_PLACES) + " more on the wiki", SOFT);
        }
        for (SlayerData.Place place : places) {
            List<String> routes = slayerData.travel(place.page);
            if (!routes.isEmpty()) {
                routes("To " + place.name + ":", routes);
                break;
            }
        }
    }

    private void routes(String heading, List<String> routes) {
        if (routes.isEmpty()) {
            return;
        }
        panel.getChildren().add(LineComponent.builder().left(heading).leftColor(TITLE_COLOR).build());
        for (String route : routes.subList(0, Math.min(MAX_ROUTES, routes.size()))) {
            wrapped("- " + route, Color.WHITE);
        }
    }

    // Long text across the card's full width
    private void wrapped(String text, Color color) {
        panel.getChildren().add(LineComponent.builder().left(text).leftColor(color).build());
    }
}
