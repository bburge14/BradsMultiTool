package com.bradsmultitool;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** The tick count over your character's head. */
public class BradsMultiToolTickHeadOverlay extends Overlay {

    private final Client client;
    private final TickConfig config;
    private final TickCounter ticks;

    @Inject
    public BradsMultiToolTickHeadOverlay(Client client, TickConfig config, TickCounter ticks) {
        this.client = client;
        this.config = config;
        this.ticks = ticks;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Player player = client.getLocalPlayer();
        if (player == null || !config.tickDisplay().overHead() || ticks.getCount() <= 0) {
            return null;
        }
        String text = String.valueOf(ticks.getCount());
        graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, (float) config.tickTextSize()));
        Point location = player.getCanvasTextLocation(graphics, text, player.getLogicalHeight() + 40);
        if (location != null) {
            OverlayUtil.renderTextLocation(graphics, location, text,
                ticks.getCount() == 1 ? config.tickFirstColor() : config.tickColor());
        }
        return null;
    }
}
