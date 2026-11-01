package com.snuffys.multitool;

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

    BradsMultiToolTickSingleBoxOverlay(Client client, TickConfig config, TickCounter ticks, int tick) {
        this.client = client;
        this.config = config;
        this.ticks = ticks;
        this.tick = tick;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    /** Each box's own name, so RuneLite keeps each one where you put it. */
    @Override
    public String getName() {
        return "SnuffysTickBox" + tick;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !config.tickBoxes() || !config.tickBoxesSeparate()
            || ticks.getCount() <= 0 || tick > Math.max(1, ticks.getCycle())) {
            return null;
        }
        int size = config.tickBoxSize();
        graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, size * 0.55f));
        BradsMultiToolTickBoxOverlay.drawBox(graphics, graphics.getFontMetrics(), config, 0, 0, size, tick,
            tick == ticks.getCount(), BradsMultiToolTickBoxOverlay.fade(ticks), BACKGROUND);
        return new Dimension(size, size);
    }
}
