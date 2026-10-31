package com.snuffys.skilltracker;

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
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Countdown pies over trees, Motherlode veins, calcified rocks, Daeyalt essence, shooting stars, fishing
 * spots, shipwrecks, traps, fires and Forester's Campfires (see {@link ResourceTimers}), each only while
 * you train its skill unless Timers When Not Training is on: green, then yellow, then red as each runs out, pulsing once it's due.
 * Fishing spots and fires go at a random time in a window, so theirs show the window instead (see {@link #drawWindow}).
 * With Pie Style on Tile, icon and pie (the default), each one's tile is outlined in the Zone Colour with a picture of
 * what it gives (logs, ore, the best fish you can catch there) in the middle and the pie over the picture, except where
 * another plugin already draws the icon or the outline (see {@link OtherTimerPlugins}).
 */
@javax.inject.Singleton
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
    private final TooltipManager tooltips;
    private final net.runelite.client.game.SkillIconManager skillIcons;

    @Inject
    public BradsMultiToolResourceTimerOverlay(Client client, SkillTrackerConfig config, ResourceTimers timers,
                                              OtherTimerPlugins otherPlugins, SkillTracker plugin,
                                              SkillHelperData helpers, ItemIcons itemIcons, TooltipManager tooltips,
                                              net.runelite.client.game.SkillIconManager skillIcons) {
        this.tooltips = tooltips;
        this.skillIcons = skillIcons;
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
        updateNewestSpot();
        for (ResourceTimers.Timer timer : timers.all()) {
            if (!shown(timer)) {
                continue;
            }
            boolean tile = config.pieStyle() == SkillTrackerConfig.PieStyle.TILE;
            Point at = tile ? zone(graphics, timer) : position(timer);
            if (at == null) {
                continue;
            }
            if (timer.windowed()) {
                drawWindow(graphics, timer, at, !tile && yours(timer) ? config.fishingActiveColor() : null);
                drawLabels(graphics, timer, at);
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
            drawPie(graphics, at, left, color, color, alpha);
            drawLabels(graphics, timer, at);
        }
        return null;
    }

    // A fishing spot or fire, which goes at a random time in a window: a green pie running down to the earliest it
    // can go ("won't go yet"), then a red ring filling up to the latest (how likely it's gone by now). Hover for times.
    private void drawWindow(Graphics2D graphics, ResourceTimers.Timer timer, Point at, Color border) {
        boolean safe = timer.ticksToEarliest() > 0;
        Color color = safe ? HudColors.GOOD : HudColors.BAD;
        drawPie(graphics, at, safe ? (double) timer.ticksToEarliest() / timer.minTicks : timer.windowPassed(),
            color, border != null ? border : safe ? color : HudColors.WARN, 150);
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (mouse != null && Math.hypot(mouse.getX() - at.getX(), mouse.getY() - at.getY()) <= DIAMETER / 2.0 + 2) {
            tooltips.add(new Tooltip(windowText(timer)));
        }
    }

    // A pie at the Timer Opacity setting: fillAlpha is its look at the default 60%, the border solid there
    private void drawPie(Graphics2D graphics, Point at, double progress, Color fill, Color border, int fillAlpha) {
        double scale = config.timerOpacity() / 60.0;
        pie.setPosition(at);
        pie.setProgress(progress);
        pie.setFill(withAlpha(fill, fillAlpha * scale));
        pie.setBorderColor(withAlpha(border, 255 * scale));
        pie.render(graphics);
    }

    static Color withAlpha(Color color, double alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.max(0, Math.min(255, Math.round(alpha))));
    }

    // The newest timed fishing spot (the least likely to move soon); kept until a strictly newer one appears
    // so it doesn't flick between spots that appeared on the same tick
    private ResourceTimers.Timer newestSpot;

    private void updateNewestSpot() {
        ResourceTimers.Timer best = null;
        boolean kept = false;
        for (ResourceTimers.Timer timer : timers.all()) {
            if (timer.kind != ResourceTimers.Kind.FISHING_SPOT || !timer.windowed()) {
                continue;
            }
            kept |= timer == newestSpot;
            if (best == null || timer.elapsed < best.elapsed) {
                best = timer;
            }
        }
        if (!kept || (best != null && best.elapsed < newestSpot.elapsed)) {
            newestSpot = best;
        }
    }

    private static final int ICON_SIZE = 16;

    // Above a timer: its time (see timerText) and the skill's icon, with a gold ring on the newest fishing spot
    private void drawLabels(Graphics2D graphics, ResourceTimers.Timer timer, Point at) {
        int top = at.getY() - DIAMETER / 2 - 3;
        String text = config.timerText() ? timerText(timer) : null;
        if (text != null) {
            graphics.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
            int width = graphics.getFontMetrics().stringWidth(text);
            net.runelite.client.ui.overlay.OverlayUtil.renderTextLocation(graphics,
                new Point(at.getX() - width / 2, top), text, Color.WHITE);
            top -= graphics.getFontMetrics().getHeight();
        }
        boolean newest = config.fishingNewestSpot() && timer == newestSpot;
        java.awt.image.BufferedImage icon = config.timerSkillIcon() ? skillIcons.getSkillImage(skillOf(timer.kind), true) : null;
        if (icon == null && !newest) {
            return;
        }
        int x = at.getX() - ICON_SIZE / 2;
        int y = top - ICON_SIZE - 1;
        if (newest) {
            graphics.setColor(new Color(255, 200, 0));
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawOval(x - 3, y - 3, ICON_SIZE + 6, ICON_SIZE + 6);
        }
        if (icon != null) {
            graphics.drawImage(icon, x, y, ICON_SIZE, ICON_SIZE, null);
        }
    }

    /**
     * The time shown over a timer: for fishing spots and fires (they go at a random time) how long ago they
     * appeared; a star's layer health; a trap's catch; otherwise the time it has left. Null when there's no time.
     */
    static String timerText(ResourceTimers.Timer timer) {
        switch (timer.kind) {
            case STAR:
                return timer.health >= 0 ? Math.round(timer.health * 100) + "%" : null;
            case TRAP:
                if (timer.trapState == ResourceTimers.TrapState.CAUGHT) {
                    return "Caught";
                }
                return timer.trapState == ResourceTimers.TrapState.FAILED ? "Collapsed" : clock(timer.ticksToLatest());
            case TREE:
            case CALCIFIED:
            case CAMPFIRE:
                return timer.due() ? "Due" : clock(Math.max(0, timer.ticksLeft));
            default:
                if (timer.windowed()) {
                    return clock(timer.elapsed);
                }
                return timer.due() ? "Due" : clock(timer.ticksToLatest());
        }
    }

    private static Skill skillOf(ResourceTimers.Kind kind) {
        switch (kind) {
            case TREE:
                return Skill.WOODCUTTING;
            case FISHING_SPOT:
                return Skill.FISHING;
            case SHIPWRECK:
                return Skill.SAILING;
            case TRAP:
                return Skill.HUNTER;
            case FIRE:
            case CAMPFIRE:
                return Skill.FIREMAKING;
            default:
                return Skill.MINING;
        }
    }

    private boolean yours(ResourceTimers.Timer timer) {
        net.runelite.api.Player you = client.getLocalPlayer();
        return timer.npc != null && you != null && you.getInteracting() == timer.npc;
    }

    /** "Won't move for 1:20 (gone by 4:00 at the latest)", "Can move any time (gone by 2:40 at the latest)". */
    static String windowText(ResourceTimers.Timer timer) {
        String verb = timer.kind == ResourceTimers.Kind.FIRE ? "go out" : "move";
        String latest = " (" + (timer.kind == ResourceTimers.Kind.FIRE ? "out" : "gone") + " by "
            + clock(timer.ticksToLatest()) + " at the latest)";
        return timer.ticksToEarliest() > 0
            ? "Won't " + verb + " for " + clock(timer.ticksToEarliest()) + latest
            : "Can " + verb + " any time" + latest;
    }

    /** Ticks as m:ss. */
    static String clock(int ticks) {
        int seconds = (int) Math.round(ticks * 0.6);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** Whether a timer is drawn: its setting is on, it has something to show, you train its skill (or Timers When
     * Not Training is on), and no other timer plugin already draws it. */
    boolean shown(ResourceTimers.Timer timer) {
        return enabled(timer.kind) && timer.visible() && (config.piesAlwaysShown() || training(timer.kind))
            && !(config.deferToOtherTimerPlugins() && otherPlugins.covers(timer.kind));
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
                return config.showFireTimer();
            case CAMPFIRE:
                return config.showCampfireTimer();
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
        boolean yours = yours(timer);
        Polygon poly = otherPlugins.drawsTile(timer.kind, yours) ? null
            : timer.npc != null ? timer.npc.getCanvasTilePoly() : timer.object.getCanvasTilePoly();
        if (poly != null) {
            Color zone = yours && timer.kind == ResourceTimers.Kind.FISHING_SPOT ? config.fishingActiveColor() : config.zoneColor();
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
