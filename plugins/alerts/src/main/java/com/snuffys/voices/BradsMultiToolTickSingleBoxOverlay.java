package com.snuffys.voices;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * One tick's box on its own, with Move Boxes Separately on: each box is its own overlay, Alt + dragged wherever
 * you like (over the item you click on that tick, say), and kept there under its own name. Shown while its tick
 * is in the cycle. Made by {@link TickSingleBoxes}.
 */
public class BradsMultiToolTickSingleBoxOverlay extends Overlay {

    // A see-through box over whatever it's placed on, lit on its tick
    private static final Color BACKGROUND = new Color(20, 20, 20, 60);

    private final Client client;
    private final TickConfig config;
    private final TickCounter ticks;
    private final int tick;
    // The tick's half box ("1.5"), with Half-Tick Boxes
    private final boolean half;
    // A tick method's step (its index), or -1 for a numbered box
    private final int step;

    BradsMultiToolTickSingleBoxOverlay(Client client, TickConfig config, TickCounter ticks, int tick, boolean half) {
        this(client, config, ticks, tick, half, -1);
    }

    BradsMultiToolTickSingleBoxOverlay(Client client, TickConfig config, TickCounter ticks, int tick, boolean half, int step) {
        this.step = step;
        this.client = client;
        this.config = config;
        this.ticks = ticks;
        this.tick = tick;
        this.half = half;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    /** Each box's own name, so RuneLite keeps each one where you put it. */
    @Override
    public String getName() {
        return step >= 0 ? "SnuffysTickStep" + (step + 1) : (half ? "SnuffysTickHalfBox" : "SnuffysTickBox") + tick;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !config.tickBoxes() || !config.tickBoxesSeparate()
            || ticks.getCount() <= 0 || ticks.isHidden()) {
            return null;
        }
        java.util.List<TickMethods.Step> steps = TickMethods.steps(config.tickMethod());
        int size = config.tickBoxSize();
        if (step >= 0 || !steps.isEmpty()) {
            // A tick method's step box (the numbered boxes are off while a method is picked)
            if (step < 0 || step >= steps.size()) {
                return null;
            }
            TickMethods.Step s = steps.get(step);
            graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, size * 0.55f));
            BradsMultiToolTickBoxOverlay.drawBox(graphics, graphics.getFontMetrics(), config, 0, 0, size, step == 0 ? 1 : 2,
                s.lit(ticks.getCount(), System.currentTimeMillis() - ticks.getLastTickMillis()),
                BradsMultiToolTickBoxOverlay.halfFade(ticks, config), BACKGROUND, s.label);
            return new Dimension(size, size);
        }
        if (tick > Math.max(1, ticks.getCycle()) || (half && !config.tickHalfBoxes())) {
            return null;
        }
        graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, size * 0.55f));
        BradsMultiToolTickBoxOverlay.drawBox(graphics, graphics.getFontMetrics(), config, 0, 0, size, tick,
            BradsMultiToolTickBoxOverlay.isLit(ticks, config, tick, half), BradsMultiToolTickBoxOverlay.halfFade(ticks, config),
            BACKGROUND, BradsMultiToolTickBoxOverlay.label(ticks.boxNumber(tick), half, ticks.countingDown()));
        return new Dimension(size, size);
    }
}
