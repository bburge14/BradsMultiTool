package com.snuffys.skilltracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Forestry events on the game screen: what to click, outlined and labelled ("Chop", "Disarm", "Stand here"...), from
 * {@link ForestryGuide}. Each event is left to RuneLite's Woodcutting plugin while it's on and highlights it.
 */
@Singleton
class ForestryOverlay extends Overlay {

    private static final Color GO = new Color(60, 230, 90);
    private static final Color GOLD = new Color(250, 200, 50);
    private static final Color DANGER = new Color(255, 80, 70);
    private static final Color HIVE = new Color(255, 150, 30);

    private final Client client;
    private final ForestryGuide guide;
    private final SkillTrackerConfig config;

    @Inject
    ForestryOverlay(Client client, ForestryGuide guide, SkillTrackerConfig config) {
        this.client = client;
        this.guide = guide;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.forestryHighlights() || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        graphics.setFont(FontManager.getRunescapeBoldFont());
        // Rising Roots: the glowing (anima-infused) roots
        if (!guide.theyHighlight("highlightGlowingRoots")) {
            for (GameObject root : guide.roots) {
                if (root.getId() == ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL) {
                    clickbox(graphics, root.getClickbox(), GO);
                    label(graphics, root, "Chop this root", GO);
                }
            }
        }
        // Struggling Sapling: each ingredient, with its place in the order once the sapling has said
        if (!guide.theyHighlight("highlightMulch")) {
            int known = 0;
            for (GameObject o : guide.order) {
                known += o != null ? 1 : 0;
            }
            for (GameObject ingredient : guide.ingredients) {
                StringBuilder at = new StringBuilder();
                for (int i = 0; i < guide.order.length; i++) {
                    if (guide.order[i] == ingredient) {
                        at.append(at.length() == 0 ? "" : ", ").append(i == 0 ? "1st" : i == 1 ? "2nd" : "3rd");
                    }
                }
                // (once all three are known, only those)
                if (known >= 3 && at.length() == 0) {
                    continue;
                }
                Color colour = at.length() > 0 ? GO : GOLD;
                tile(graphics, ingredient.getCanvasTilePoly(), colour);
                // (the sapling only says a pile's place when your mulch has it there: the ones not known yet say
                // which places are still to find)
                label(graphics, ingredient, at.length() > 0 ? "Mulch " + at : "Try for " + missing(), colour);
            }
        }
        // Flowering bushes: all, then the pair you're pollinating between
        if (!guide.theyHighlight("highlightFlowers")) {
            for (NPC flower : guide.flowers) {
                if (guide.activeFlowers.size() == 2 && !guide.activeFlowers.contains(flower)) {
                    continue;
                }
                tile(graphics, flower.getCanvasTilePoly(), GOLD);
                label(graphics, flower, guide.activeFlowers.size() == 2 ? "Pollinate" : "Tend", GOLD);
            }
        }
        // Poachers: the fox trap to disarm
        if (guide.foxTrap != null && !guide.theyHighlight("highlightFoxTrap")) {
            tile(graphics, guide.foxTrap.getCanvasTilePoly(), DANGER);
            label(graphics, guide.foxTrap, "Disarm", DANGER);
        }
        // Pheasant Control: nests with an egg, and the Freaky Forester to take it to
        if (!guide.theyHighlight("highlightPheasantNest")) {
            for (GameObject nest : guide.nests) {
                if (nest.getId() == ObjectID.GATHERING_EVENT_PHEASANT_NEST02) {
                    tile(graphics, nest.getCanvasTilePoly(), GO);
                    label(graphics, nest, "Take egg", GO);
                }
            }
            if (guide.forester != null && !guide.nests.isEmpty()) {
                tile(graphics, guide.forester.getCanvasTilePoly(), GOLD);
                label(graphics, guide.forester, "Give egg", GOLD);
            }
        }
        // Bee Hive: the unfinished hive to build with your logs
        if (guide.unfinishedHive != null && !guide.theyHighlight("highlightBeeHive")) {
            tile(graphics, guide.unfinishedHive.getCanvasTilePoly(), HIVE);
            label(graphics, guide.unfinishedHive, "Build (logs)", HIVE);
        }
        // Enchantment Ritual: the circle to stand on
        NPC circle = guide.theyHighlight("highlightRitualCircle") ? null : guide.solveCircles();
        if (circle != null) {
            tile(graphics, circle.getCanvasTilePoly(), GO);
            label(graphics, circle, "Stand here", GO);
        }
        // Leprechaun: the end of the rainbow banks your logs
        if (!guide.theyHighlight("highlightLeprechaunRainbow")) {
            for (GameObject rainbow : guide.rainbows) {
                tile(graphics, rainbow.getCanvasTilePoly(), GO);
                label(graphics, rainbow, "Bank logs here", GO);
            }
        }
        return null;
    }

    // "1st, 2nd": the places in the sapling's order no pile is known for yet
    private String missing() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < guide.order.length; i++) {
            if (guide.order[i] == null) {
                out.append(out.length() == 0 ? "" : ", ").append(i == 0 ? "1st" : i == 1 ? "2nd" : "3rd");
            }
        }
        return out.toString();
    }

    private static void tile(Graphics2D graphics, Polygon poly, Color colour) {
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, colour, new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 50),
                new BasicStroke(2));
        }
    }

    private static void clickbox(Graphics2D graphics, Shape shape, Color colour) {
        if (shape != null) {
            graphics.setColor(colour);
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(shape);
            graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 50));
            graphics.fill(shape);
        }
    }

    private void label(Graphics2D graphics, GameObject object, String text, Color colour) {
        Point at = object.getCanvasTextLocation(graphics, text, 120);
        if (at != null) {
            OverlayUtil.renderTextLocation(graphics, at, text, colour);
        }
    }

    private void label(Graphics2D graphics, NPC npc, String text, Color colour) {
        Point at = npc.getCanvasTextLocation(graphics, text, npc.getLogicalHeight() + 30);
        if (at != null) {
            OverlayUtil.renderTextLocation(graphics, at, text, colour);
        }
    }
}
