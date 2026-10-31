package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;

/**
 * Countdown pies over trees, Motherlode veins, calcified rocks, Daeyalt essence, shooting stars, fishing
 * spots, shipwrecks, traps, fires and Forester's Campfires (see {@link ResourceTimers}), each only while
 * you train its skill unless Pies When Not Training is on: green, then yellow, then red as each runs out, pulsing once it's due.
 * With Pie Style on Tile, icon and pie (the default), each one's tile is outlined in the Zone Colour with a picture of
 * what it gives (logs, ore, the best fish you can catch there) in the middle and the pie over the picture, except where
 * another plugin already draws the icon or the outline (see {@link OtherTimerPlugins}).
 */
public class BradsMultiToolResourceTimerOverlay extends Overlay {

    private static final int DIAMETER = 22;

    private final Client client;
    private final SkillTrackerConfig config;
    private final ResourceTimers timers;
    private final OtherTimerPlugins otherPlugins;
    private final SkillTracker plugin;
    private final SkillHelperData helpers;
    private final ItemIcons itemIcons;
    private final ProgressPieComponent pie = new ProgressPieComponent();

    @Inject
    public BradsMultiToolResourceTimerOverlay(Client client, SkillTrackerConfig config, ResourceTimers timers,
                                              OtherTimerPlugins otherPlugins, SkillTracker plugin,
                                              SkillHelperData helpers, ItemIcons itemIcons) {
        this.helpers = helpers;
        this.itemIcons = itemIcons;
        this.otherPlugins = otherPlugins;
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.timers = timers;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        pie.setDiameter(DIAMETER);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        for (ResourceTimers.Timer timer : timers.all()) {
            if (!enabled(timer.kind) || !timer.visible() || !(config.piesAlwaysShown() || training(timer.kind))
                || (config.deferToOtherTimerPlugins() && otherPlugins.covers(timer.kind))) {
                continue;
            }
            boolean tile = config.pieStyle() == SkillTrackerConfig.PieStyle.TILE;
            Point at = tile ? zone(graphics, timer) : position(timer);
            if (at == null) {
                continue;
            }
            double left = timer.progress();
            Color color = left > 0.5 ? HudColors.GOOD : left > 0.2 ? HudColors.WARN : HudColors.BAD;
            // A trap goes green once something's caught, red if it sprang empty
            if (timer.kind == ResourceTimers.Kind.TRAP && timer.trapState != ResourceTimers.TrapState.SET) {
                color = timer.trapState == ResourceTimers.TrapState.CAUGHT ? HudColors.GOOD : HudColors.BAD;
            }
            int alpha = 150;
            if (timer.due()) {
                // Due (a tree falls on the next log): a full red pie pulsing, rather than an empty ring
                alpha = (int) (60 + 140 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 120.0)));
                left = 1;
            }
            pie.setPosition(at);
            pie.setProgress(left);
            pie.setFill(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            pie.setBorderColor(color);
            pie.render(graphics);
        }
        return null;
    }

    // Whether you're training the skill the resource is for (an XP drop in it lately)
    private boolean training(ResourceTimers.Kind kind) {
        switch (kind) {
            case TREE:
                return plugin.trainedRecently(Skill.WOODCUTTING);
            case FISHING_SPOT:
                return plugin.trainedRecently(Skill.FISHING);
            case SHIPWRECK:
                return plugin.trainedRecently(Skill.SAILING);
            case TRAP:
                return plugin.trainedRecently(Skill.HUNTER);
            case FIRE:
            case CAMPFIRE:
                return plugin.trainedRecently(Skill.COOKING) || plugin.trainedRecently(Skill.FIREMAKING);
            default:
                return plugin.trainedRecently(Skill.MINING);
        }
    }

    private boolean enabled(ResourceTimers.Kind kind) {
        switch (kind) {
            case TREE:
                return config.showTreeTimer();
            case FISHING_SPOT:
                return config.showFishingSpotTimer();
            case SHIPWRECK:
                return config.showShipwreckTimer();
            case TRAP:
                return config.showTrapTimer();
            case FIRE:
            case CAMPFIRE:
                return config.showFireTimer();
            default:
                return config.showMiningTimer();
        }
    }

    private static final int FIRE_HEIGHT = 60;

    // The tile style: outlines the resource's tiles, draws what it gives in the middle, and returns where the pie
    // goes (over the picture's top right corner, or the middle of the tile when there's no picture)
    private Point zone(Graphics2D graphics, ResourceTimers.Timer timer) {
        int plane = client.getTopLevelWorldView().getPlane();
        LocalPoint local = timer.npc != null ? timer.npc.getLocalLocation() : timer.object.getLocalLocation();
        if (local == null) {
            return null;
        }
        // Another plugin's own icon or tile outline on it (RuneLite's Fishing or Motherlode Mine plugin): leave ours out
        net.runelite.api.Player you = client.getLocalPlayer();
        boolean yours = timer.npc != null && you != null && you.getInteracting() == timer.npc;
        Polygon poly = otherPlugins.drawsTile(timer.kind, yours) ? null
            : timer.npc != null ? timer.npc.getCanvasTilePoly() : timer.object.getCanvasTilePoly();
        if (poly != null) {
            Color zone = config.zoneColor();
            graphics.setColor(new Color(0, 0, 0, 50));
            graphics.fill(poly);
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.setColor(zone);
            graphics.draw(poly);
        }
        Point middle = Perspective.localToCanvas(client, local, plane, 0);
        if (middle == null) {
            return null;
        }
        BufferedImage icon = otherPlugins.drawsIcon(timer.kind, yours) ? null : itemIcons.get(product(timer));
        if (icon == null) {
            return middle;
        }
        int x = middle.getX() - icon.getWidth() / 2;
        int y = middle.getY() - icon.getHeight() / 2;
        graphics.drawImage(icon, x, y, null);
        return new Point(x + icon.getWidth() - DIAMETER / 4, y + DIAMETER / 4);
    }

    // What a timed resource gives, by item name: logs for trees, ore for rocks, the best fish you can catch at a spot
    private String product(ResourceTimers.Timer timer) {
        switch (timer.kind) {
            case VEIN:
                return "Pay-dirt";
            case CALCIFIED:
                return "Calcified deposit";
            case DAEYALT:
                return "Daeyalt essence";
            case STAR:
                return "Stardust";
            case FISHING_SPOT:
                return bestCatch(timer.npc.getId());
            case TREE:
                return logsOf(objectName(timer.object.getId()));
            case TRAP:
                return objectName(timer.object.getId());
            default:
                return null;
        }
    }

    /** The logs a tree gives, by the tree's name: "Tree" gives Logs, "Oak tree" Oak logs, "Arctic pine" Arctic pine logs. */
    static String logsOf(String tree) {
        if (tree == null || tree.isEmpty()) {
            return null;
        }
        if (tree.equalsIgnoreCase("tree") || tree.equalsIgnoreCase("dead tree")) {
            return "Logs";
        }
        String lower = tree.toLowerCase();
        String base = lower.endsWith(" tree") ? tree.substring(0, tree.length() - 5) : tree;
        return base + " logs";
    }

    // The highest-level fish you can catch at this spot, else its lowest-level one
    private String bestCatch(int npcId) {
        SkillHelperData.FishingSpot spot = helpers.fishingSpot(npcId);
        if (spot == null) {
            return null;
        }
        int level = client.getRealSkillLevel(Skill.FISHING);
        SkillHelperData.Catch best = null;
        SkillHelperData.Catch lowest = null;
        for (java.util.List<SkillHelperData.Catch> catches : spot.options.values()) {
            for (SkillHelperData.Catch c : catches) {
                if (c.level <= level && (best == null || c.level > best.level)) {
                    best = c;
                }
                if (lowest == null || c.level < lowest.level) {
                    lowest = c;
                }
            }
        }
        SkillHelperData.Catch chosen = best != null ? best : lowest;
        return chosen == null ? null : chosen.name;
    }

    private String objectName(int id) {
        ObjectComposition composition = client.getObjectDefinition(id);
        if (composition != null && composition.getImpostorIds() != null && composition.getImpostor() != null) {
            composition = composition.getImpostor();
        }
        return composition == null ? null : composition.getName();
    }

    // Trees: the middle of the canopy. Rocks: the middle of the model as drawn. Fishing spots: on the water.
    private Point position(ResourceTimers.Timer timer) {
        int plane = client.getTopLevelWorldView().getPlane();
        if (timer.npc != null) {
            LocalPoint local = timer.npc.getLocalLocation();
            return local == null ? null : Perspective.localToCanvas(client, local, plane, 0);
        }
        LocalPoint local = timer.object.getLocalLocation();
        // Fires: a fixed point just above the logs, not the flames' shape, which moves every frame
        if (timer.kind == ResourceTimers.Kind.FIRE || timer.kind == ResourceTimers.Kind.CAMPFIRE) {
            return local == null ? null : Perspective.localToCanvas(client, local, plane, FIRE_HEIGHT);
        }
        if (timer.object instanceof GameObject) {
            GameObject object = (GameObject) timer.object;
            // A tree's leaves sit in the top part of its model, so aim about two-thirds of the way up
            int height = object.getRenderable() != null ? object.getRenderable().getModelHeight() : 0;
            if (timer.kind == ResourceTimers.Kind.TREE && height > 0 && local != null) {
                return Perspective.localToCanvas(client, local, plane, height * 2 / 3);
            }
            Shape hull = object.getConvexHull();
            if (hull != null) {
                Rectangle bounds = hull.getBounds();
                return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
            }
            if (height > 0 && local != null) {
                return Perspective.localToCanvas(client, local, plane, height / 2);
            }
        }
        return local == null ? null : Perspective.localToCanvas(client, local, plane, 150);
    }
}
