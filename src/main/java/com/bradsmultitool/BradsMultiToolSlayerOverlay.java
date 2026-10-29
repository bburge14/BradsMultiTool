package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * Slayer card. On a task: the monster's picture, kills left, kill rate, time to finish, required item,
 * where to kill it and how to get there. With no task: the best Slayer master for you (with their
 * chathead), where they are, how to get there and the next master. Each part has its own setting.
 */
public class BradsMultiToolSlayerOverlay extends Overlay {

    private static final Color TITLE_COLOR = new Color(255, 185, 0);
    private static final Color PLACE_COLOR = Color.WHITE;
    private static final Color SOFT = new Color(180, 180, 180);

    private final Client client;
    private final PvmTracker plugin;
    private final BradsMultiToolConfig config;
    private final SlayerData slayerData;
    private final ItemManager itemManager;
    private final PanelComponent panel = new PanelComponent();
    private FontMetrics metrics;

    @Inject
    public BradsMultiToolSlayerOverlay(Client client, PvmTracker plugin, BradsMultiToolConfig config,
                                       SlayerData slayerData, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.slayerData = slayerData;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.TOP_LEFT);
        panel.setPreferredSize(new Dimension(210, 0));
        panel.setGap(new java.awt.Point(0, 3));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        SlayerTracker tracker = plugin.getSlayerTracker();
        if (!config.showSlayerTask() || plugin.isSlayerCardHidden() || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        graphics.setFont(FontManager.getRunescapeSmallFont());
        metrics = graphics.getFontMetrics();
        panel.getChildren().clear();
        if (tracker.hadTask()) {
            buildTaskCard(tracker);
        } else if (config.showSlayerMasterCard()) {
            buildMasterCard();
        } else {
            return null;
        }
        return panel.render(graphics);
    }

    private void buildTaskCard(SlayerTracker tracker) {
        SlayerData.Task task = slayerData.task(tracker.getTaskName());
        String area = tracker.getKonarArea();
        String subtitle = tracker.getRemaining() + " left" + (area != null ? " in " + area : "");
        panel.getChildren().add(new PortraitHeaderComponent(picture(task != null ? task.icon : null),
            tracker.getTaskName(), TITLE_COLOR, subtitle));

        if (config.slayerKillRate()) {
            double rate = tracker.killsPerHour();
            line("Kill Rate:", rate > 0 ? Math.round(rate) + " /hr" : "--", Color.WHITE);
            if (rate > 0) {
                long minutes = Math.round(tracker.getRemaining() / rate * 60);
                line("Done In:", minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m", Color.WHITE);
            }
        }
        SlayerTracker.Requirement requirement = tracker.getRequirement();
        if (config.slayerRequiredItem() && requirement != null) {
            boolean has = tracker.isHasRequiredItem();
            line("Needs:", requirement.display + (has ? "" : " (missing)"), has ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD);
        }
        if (config.showSlayerDirections() && task != null && !task.places.isEmpty()) {
            addPlaces(task, area);
        }
    }

    // Where the wiki says to kill it (Konar's area first, Wilderness last) and how to reach the first one
    private void addPlaces(SlayerData.Task task, String konar) {
        List<SlayerData.Place> places = new ArrayList<>(task.places);
        places.sort(Comparator.comparing((SlayerData.Place p) -> !(konar != null && p.name.toLowerCase().contains(konar.toLowerCase())))
            .thenComparing(SlayerData.Place::inWilderness));
        heading("Where To Go");
        int shown = Math.min(config.slayerPlacesShown(), places.size());
        for (SlayerData.Place place : places.subList(0, shown)) {
            line(place.name, place.tags(), PLACE_COLOR, SOFT);
        }
        if (places.size() > shown) {
            panel.getChildren().add(new TextBlockComponent("+" + (places.size() - shown) + " more on the wiki", SOFT, false));
        }
        if (!config.slayerTravelRoutes()) {
            return;
        }
        for (SlayerData.Place place : places.subList(0, shown)) {
            List<String> routes = slayerData.travel(place.page);
            if (!routes.isEmpty()) {
                heading("Getting To " + place.name);
                bullets(routes);
                return;
            }
        }
    }

    // No task: which master to see (the best one for your levels), where they are and how to get there
    private void buildMasterCard() {
        int combat = client.getLocalPlayer() != null ? client.getLocalPlayer().getCombatLevel() : 3;
        int slayer = client.getRealSkillLevel(Skill.SLAYER);
        SlayerData.Master master = slayerData.bestMaster(combat, slayer);
        if (master == null) {
            return;
        }
        panel.getChildren().add(new PortraitHeaderComponent(picture(master.icon), master.name, BradsMultiToolOverlay.GOOD,
            "No task: get one here" + (master.area != null ? "\n" + master.area : "")));
        if (master.quest != null) {
            line("Quest:", master.quest, SOFT);
        }
        if (config.slayerTravelRoutes() && !master.routes.isEmpty()) {
            heading("Getting There");
            bullets(master.routes);
        }
        SlayerData.Master next = slayerData.nextMaster(master);
        if (config.slayerNextMaster() && next != null) {
            String needs = "Combat " + next.combat + (next.slayer > 0 ? ", Slayer " + next.slayer : "");
            panel.getChildren().add(TextBlockComponent.heading("Next: " + next.name + " (" + needs + ")", SOFT));
        }
    }

    // The bundled picture, else the Slayer gem (or nothing with pictures off)
    private Image picture(String file) {
        if (!config.slayerPictures()) {
            return null;
        }
        Image image = slayerData.picture(file);
        return image != null ? image : itemManager.getImage(ItemID.SLAYER_GEM);
    }

    private void heading(String text) {
        panel.getChildren().add(TextBlockComponent.heading(text, TITLE_COLOR));
    }

    private void bullets(List<String> items) {
        for (String item : items.subList(0, Math.min(config.slayerRoutesShown(), items.size()))) {
            panel.getChildren().add(new TextBlockComponent(item, Color.WHITE, true));
        }
    }

    private void line(String left, String right, Color rightColor) {
        line(left, right, Color.WHITE, rightColor);
    }

    // One row when both sides fit; otherwise each side as its own wrapped block, so nothing overlaps
    private void line(String left, String right, Color leftColor, Color rightColor) {
        int width = panel.getPreferredSize().width - 10;
        if (metrics == null || metrics.stringWidth(left) + metrics.stringWidth(right) + 8 <= width) {
            panel.getChildren().add(LineComponent.builder().left(left).leftColor(leftColor).right(right).rightColor(rightColor).build());
            return;
        }
        panel.getChildren().add(new TextBlockComponent(left, leftColor, false));
        if (!right.isEmpty()) {
            panel.getChildren().add(new TextBlockComponent("  " + right, rightColor, false));
        }
    }
}
