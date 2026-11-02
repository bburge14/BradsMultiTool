package com.snuffys.skilltracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * Countdown timers over trees, Motherlode veins, calcified rocks, depleted rocks (until their ore is back),
 * Daeyalt essence, shooting stars, fishing spots, shipwrecks, traps, fires and Forester's Campfires (see {@link ResourceTimers}), each only while
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
    private final MethodDetector methods;
    private final net.runelite.client.game.SkillIconManager skillIcons;

    @Inject
    public BradsMultiToolResourceTimerOverlay(Client client, SkillTrackerConfig config, ResourceTimers timers,
                                              OtherTimerPlugins otherPlugins, SkillTracker plugin,
                                              SkillHelperData helpers, ItemIcons itemIcons, TooltipManager tooltips,
                                              net.runelite.client.game.SkillIconManager skillIcons, MethodDetector methods) {
        this.methods = methods;
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
        drawUntimedSpots(graphics);
        for (ResourceTimers.Timer timer : timers.all()) {
            if (!shown(timer)) {
                continue;
            }
            // Trees and rocks: just a pie on the tree or rock, like RuneLite's own (no tile, pictures or labels)
            boolean plain = plainPie(timer.kind);
            boolean tile = config.pieStyle() == SkillTrackerConfig.PieStyle.TILE && !plain;
            if (tile && !keepsPie(timer.kind)) {
                // No pie: the tile's outline is the timer (green, yellow, red; pulsing red once it's due)
                Point at = zone(graphics, timer, outlineColor(timer), false);
                if (at != null) {
                    drawLabels(graphics, timer, at);
                    hoverWindow(timer);
                }
                continue;
            }
            // Traps, fires and campfires keep a pie: a set time they run out by (with the tile style, over the picture)
            Point at = tile ? zone(graphics, timer, null, true) : position(timer);
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
            // A depleted rock the other way round: red while its ore is far off, green as it's about to come back
            if (timer.kind == ResourceTimers.Kind.ROCK) {
                color = left > 0.5 ? HudColors.BAD : left > 0.2 ? HudColors.WARN : HudColors.GOOD;
            }
            // A trap goes green once something's caught
            if (timer.kind == ResourceTimers.Kind.TRAP && timer.trapState == ResourceTimers.TrapState.CAUGHT) {
                color = HudColors.GOOD;
            }
            int alpha = 150;
            if (failedTrap(timer)) {
                // Sprung empty: a full pie in its own colour (it needs resetting), not one running out
                color = config.failedTrapColor();
                alpha = Math.max(90, Math.min(200, color.getAlpha()));
                left = 1;
            } else if (timer.due()) {
                // Due (a tree falls on the next log): a full red pie pulsing, rather than an empty ring
                alpha = (int) (60 + 140 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 120.0)));
                left = 1;
            }
            drawPie(graphics, at, left, color, color, alpha);
            if (!plain) {
                drawLabels(graphics, timer, at);
            }
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
            graphics.setStroke(new BasicStroke(1f));
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
            case ROCK:
                return timer.due() ? "Now" : clock(timer.ticksToLatest());
            case TRAP:
                if (timer.trapState == ResourceTimers.TrapState.CAUGHT) {
                    return "Caught";
                }
                // Sprung with nothing in it: reset it
                return timer.trapState == ResourceTimers.TrapState.FAILED ? "Empty" : clock(timer.ticksToLatest());
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

    // Fishing spots with no timer (there before you arrived): with the tile style, still their tile and fish, so
    // the spots show before you start; no timer, as how long they've been there isn't known
    private void drawUntimedSpots(Graphics2D graphics) {
        SkillTrackerConfig.TimerShow when = when(ResourceTimers.Kind.FISHING_SPOT);
        if (config.pieStyle() != SkillTrackerConfig.PieStyle.TILE || when == SkillTrackerConfig.TimerShow.OFF
            || (when == SkillTrackerConfig.TimerShow.TRAINING && !training(ResourceTimers.Kind.FISHING_SPOT))
            || (config.deferToOtherTimerPlugins() && otherPlugins.covers(ResourceTimers.Kind.FISHING_SPOT))) {
            return;
        }
        // The spot you're fishing drops its plain outline (its fish picture stays)
        for (net.runelite.api.NPC npc : timers.untimedSpots()) {
            Color outline = config.hideOutlinesWhileWorking() && plugin.isWorkingOn(npc) ? NO_OUTLINE : null;
            zone(graphics, new ResourceTimers.Timer(ResourceTimers.Kind.FISHING_SPOT, null, npc, 0, 0), outline, false);
        }
    }

    /** Whether a timer is drawn: its setting is on, it has something to show, you train its skill (or its setting is Always), and no other timer plugin already draws it. */
    boolean shown(ResourceTimers.Timer timer) {
        SkillTrackerConfig.TimerShow when = when(timer.kind);
        return when != SkillTrackerConfig.TimerShow.OFF && timer.visible()
            && (when == SkillTrackerConfig.TimerShow.ALWAYS || training(timer.kind))
            && !(config.deferToOtherTimerPlugins() && otherPlugins.covers(timer));
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

    private SkillTrackerConfig.TimerShow when(ResourceTimers.Kind kind) {
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
    /** Woodcutting and Mining timers (trees, rocks coming back, veins, calcified rocks, Daeyalt, stars): only a pie. */
    static boolean plainPie(ResourceTimers.Kind kind) {
        switch (kind) {
            case TREE:
            case ROCK:
            case VEIN:
            case CALCIFIED:
            case DAEYALT:
            case STAR:
                return true;
            default:
                return false;
        }
    }

    /** A trap that sprang with nothing caught: it needs resetting (RuneLite's Hunter plugin colours these too). */
    static boolean failedTrap(ResourceTimers.Timer timer) {
        return timer.kind == ResourceTimers.Kind.TRAP && timer.trapState == ResourceTimers.TrapState.FAILED;
    }

    /** Traps, fires, campfires and depleted rocks: a pie timer even with the tile style (they go by a set time). */
    static boolean keepsPie(ResourceTimers.Kind kind) {
        return kind == ResourceTimers.Kind.TRAP || kind == ResourceTimers.Kind.FIRE || kind == ResourceTimers.Kind.CAMPFIRE
            || kind == ResourceTimers.Kind.ROCK;
    }

    // The tile outline as the timer, at the Timer Opacity setting: windowColor for fishing spots and fires; green,
    // yellow, red as the others run down (a trap green once caught, Failed Trap Colour if it sprang empty); pulsing
    // red once due
    private Color outlineColor(ResourceTimers.Timer timer) {
        double scale = config.timerOpacity() / 60.0;
        if (timer.windowed()) {
            return withAlpha(windowColor(timer), 230 * scale);
        }
        if (failedTrap(timer)) {
            return withAlpha(config.failedTrapColor(), 230 * scale);
        }
        double left = timer.progress();
        Color color = left > 0.5 ? HudColors.GOOD : left > 0.2 ? HudColors.WARN : HudColors.BAD;
        // A depleted rock the other way round: red while its ore is far off, green as it's about to come back
        if (timer.kind == ResourceTimers.Kind.ROCK) {
            return withAlpha(left > 0.5 ? HudColors.BAD : left > 0.2 ? HudColors.WARN : HudColors.GOOD, 230 * scale);
        }
        if (timer.kind == ResourceTimers.Kind.TRAP && timer.trapState != ResourceTimers.TrapState.SET) {
            color = timer.trapState == ResourceTimers.TrapState.CAUGHT ? HudColors.GOOD : HudColors.BAD;
        } else if (timer.due()) {
            return withAlpha(HudColors.BAD, (90 + 165 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 120.0))) * scale);
        }
        return withAlpha(color, 230 * scale);
    }

    // Hovering a fishing spot's or fire's tile: when it can go ("Won't move for 1:20 (gone by 4:00 at the latest)")
    private void hoverWindow(ResourceTimers.Timer timer) {
        if (!timer.windowed()) {
            return;
        }
        Polygon poly = timer.npc != null ? timer.npc.getCanvasTilePoly() : timer.object.getCanvasTilePoly();
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (poly != null && mouse != null && poly.contains(mouse.getX(), mouse.getY())) {
            tooltips.add(new Tooltip(windowText(timer)));
        }
    }

    /** A fishing spot's or fire's colour: green while it can't go yet, then yellow to red through its window. */
    static Color windowColor(ResourceTimers.Timer timer) {
        if (timer.ticksToEarliest() > 0) {
            return HudColors.GOOD;
        }
        double t = timer.windowPassed();
        Color from = HudColors.WARN;
        Color to = HudColors.BAD;
        return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * t),
            (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
            (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }

    // Outline in this colour (null: Zone Colour, or Your Spot Colour on the spot you fish), with the picture of
    // what it gives; returns where the pie goes (forPie) or the middle top of the picture (for the labels)
    private Point zone(Graphics2D graphics, ResourceTimers.Timer timer, Color outline, boolean forPie) {
        int plane = client.getTopLevelWorldView().getPlane();
        LocalPoint local = timer.npc != null ? timer.npc.getLocalLocation() : timer.object.getLocalLocation();
        if (local == null) {
            return null;
        }
        // Another plugin's own icon or tile outline on it (RuneLite's Fishing or Motherlode Mine plugin): leave ours out
        boolean yours = yours(timer);
        Polygon poly = otherPlugins.drawsTile(timer.kind, yours) ? null
            : timer.npc != null ? timer.npc.getCanvasTilePoly() : timer.object.getCanvasTilePoly();
        if (poly != null && outline != NO_OUTLINE) {
            boolean yourSpot = yours && timer.kind == ResourceTimers.Kind.FISHING_SPOT;
            Color zone = outline != null ? outline : yourSpot ? config.fishingActiveColor() : config.zoneColor();
            graphics.setColor(new Color(0, 0, 0, 50));
            graphics.fill(poly);
            // The spot you fish: a thicker outline (its colour is its timer's)
            graphics.setStroke(new BasicStroke(outline != null && yourSpot ? 2f : 1f));
            graphics.setColor(zone);
            graphics.draw(poly);
        }
        Point middle = Perspective.localToCanvas(client, local, plane, 0);
        if (middle == null) {
            return null;
        }
        List<BufferedImage> icons = new ArrayList<>();
        if (!otherPlugins.drawsIcon(timer.kind, yours)) {
            for (String product : products(timer)) {
                BufferedImage image = itemIcons.get(product);
                if (image != null) {
                    icons.add(image);
                }
            }
        }
        if (icons.isEmpty()) {
            return forPie ? middle : new Point(middle.getX(), middle.getY() + DIAMETER / 2);
        }
        // Several (trout and salmon from one way of fishing): stacked, each a little down and right of the last
        int width = icons.get(0).getWidth() + STACK_STEP * (icons.size() - 1);
        int height = icons.get(0).getHeight() + STACK_STEP * (icons.size() - 1);
        int x = middle.getX() - width / 2;
        int y = middle.getY() - height / 2;
        for (int i = 0; i < icons.size(); i++) {
            graphics.drawImage(icons.get(i), x + STACK_STEP * i, y + STACK_STEP * i, null);
        }
        return forPie ? new Point(x + width - DIAMETER / 4, y + DIAMETER / 4)
            // drawLabels puts the time above a pie-sized space centred here: just above the picture
            : new Point(middle.getX(), y + DIAMETER / 2 - 2);
    }

    private static final int STACK_STEP = 9;
    /** Passed to zone for no tile outline at all (just the picture). */
    private static final Color NO_OUTLINE = new Color(0, 0, 0, 0);

    // What a timed resource gives, by item name: logs for trees, ore for rocks; for a fishing spot, the fish of the
    // way you're fishing (when the spot has it), else of the best way for your level: every fish it gives you
    private List<String> products(ResourceTimers.Timer timer) {
        if (timer.kind == ResourceTimers.Kind.FISHING_SPOT) {
            return fishOf(timer.npc.getId());
        }
        if (timer.kind == ResourceTimers.Kind.ROCK) {
            return oreOf(timer.resource);
        }
        String product = product(timer);
        return product == null ? Collections.emptyList() : Collections.singletonList(product);
    }

    // A rock's ore by the rock's name, whichever has a picture: "Lead rocks" -> Lead ore, "Coal rocks" -> Coal,
    // "Amethyst crystals" -> Amethyst
    private List<String> oreOf(String rock) {
        if (rock == null) {
            return Collections.emptyList();
        }
        String base = rock.replaceAll("(?i)\\s+(rocks?|crystals|vein|deposit)$", "");
        for (String name : new String[]{base + " ore", base}) {
            if (itemIcons.get(name) != null) {
                return Collections.singletonList(name);
            }
        }
        return Collections.emptyList();
    }

    private List<String> fishOf(int npcId) {
        SkillHelperData.FishingSpot spot = helpers.fishingSpot(npcId);
        if (spot == null) {
            return Collections.emptyList();
        }
        int level = client.getRealSkillLevel(Skill.FISHING);
        String way = methods.fishingWay();
        List<SkillHelperData.Catch> catches = way != null ? TrainingAdvisor.catchesFor(spot, way) : Collections.emptyList();
        if (catches.isEmpty()) {
            // The way with the highest-level fish you can catch
            int best = -1;
            for (List<SkillHelperData.Catch> option : spot.options.values()) {
                SkillHelperData.Catch top = TrainingAdvisor.bestCatch(option, level);
                if (top != null && top.level > best) {
                    best = top.level;
                    catches = option;
                }
            }
        }
        List<String> fish = new ArrayList<>();
        for (SkillHelperData.Catch c : catches) {
            if (c.level <= level) {
                fish.add(c.name);
            }
        }
        if (fish.isEmpty() && !catches.isEmpty()) {
            fish.add(catches.get(0).name);
        }
        return fish;
    }

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
