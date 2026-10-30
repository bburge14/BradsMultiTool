package com.snuffys.multitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * The buff bar: your buffs (green border) then debuffs (red), each an icon with its time left underneath and
 * its boost or drain in the corner, like RS3's. Hover one for its name. Moved like any overlay (Alt + drag).
 */
public class BradsMultiToolBuffBarOverlay extends Overlay {

    private static final int GAP = 2;
    private static final Color BACKGROUND = new Color(20, 20, 20, 170);

    private final Client client;
    private final PvmConfig config;
    private final StatusEffects effects;
    private final ItemManager itemManager;
    private final SkillIconManager skillIcons;
    private final TooltipManager tooltipManager;

    @Inject
    public BradsMultiToolBuffBarOverlay(Client client, PvmConfig config, StatusEffects effects, ItemManager itemManager,
                                        SkillIconManager skillIcons, TooltipManager tooltipManager) {
        this.client = client;
        this.config = config;
        this.effects = effects;
        this.itemManager = itemManager;
        this.skillIcons = skillIcons;
        this.tooltipManager = tooltipManager;
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showBuffBar() || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        List<StatusEffects.Effect> list = effects.effects();
        int size = config.buffBarIconSize();
        boolean horizontal = config.buffBarLayout() == PvmConfig.BarLayout.HORIZONTAL;
        graphics.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics metrics = graphics.getFontMetrics();
        long now = System.currentTimeMillis();
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        Rectangle bounds = getBounds();
        int drawn = 0;
        for (StatusEffects.Effect effect : list) {
            if (effect.debuff && !config.buffBarDebuffs()) {
                continue;
            }
            int x = horizontal ? drawn * (size + GAP) : 0;
            int y = horizontal ? 0 : drawn * (size + GAP);
            drawn++;
            graphics.setColor(BACKGROUND);
            graphics.fillRect(x, y, size, size);
            graphics.setColor(effect.debuff ? HudColors.BAD : HudColors.GOOD);
            graphics.drawRect(x, y, size - 1, size - 1);

            Image icon = effect.itemId > 0 ? itemManager.getImage(effect.itemId)
                : effect.skill != null ? skillIcons.getSkillImage(effect.skill, true) : null;
            if (icon != null) {
                int inner = size - 6;
                int w = icon.getWidth(null);
                int h = icon.getHeight(null);
                double scale = Math.min(1.0, Math.min(inner / (double) w, inner / (double) h));
                int dw = (int) (w * scale);
                int dh = (int) (h * scale);
                graphics.drawImage(icon, x + (size - dw) / 2, y + (size - dh) / 2 - 2, dw, dh, null);
            }
            if (effect.amount != null) {
                text(graphics, effect.amount, x + 2, y + metrics.getAscent(), effect.debuff ? HudColors.BAD : Color.WHITE);
            }
            if (effect.endMillis > 0) {
                String time = (effect.exact ? "" : "~") + BuffTimer.formatMillis(Math.max(0, effect.endMillis - now));
                text(graphics, time, x + size - metrics.stringWidth(time) - 2, y + size - 2, Color.WHITE);
            }
            if (mouse != null && bounds != null && new Rectangle(bounds.x + x, bounds.y + y, size, size).contains(mouse.getX(), mouse.getY())) {
                tooltipManager.add(new Tooltip(effect.name));
            }
        }
        if (drawn == 0) {
            return null;
        }
        int length = drawn * size + (drawn - 1) * GAP;
        return horizontal ? new Dimension(length, size) : new Dimension(size, length);
    }

    private static void text(Graphics2D graphics, String text, int x, int y, Color color) {
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }
}
