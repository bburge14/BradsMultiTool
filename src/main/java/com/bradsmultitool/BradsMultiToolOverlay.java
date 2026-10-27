package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

public class BradsMultiToolOverlay extends Overlay {

    // Text is white unless colour carries meaning: good/bad state, or a warning
    static final Color GOOD = new Color(50, 220, 50);
    static final Color BAD = new Color(255, 70, 70);
    static final Color WARN = new Color(255, 150, 0);

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final ModelOutlineRenderer modelOutlineRenderer;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public BradsMultiToolOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config, ModelOutlineRenderer modelOutlineRenderer, ItemManager itemManager, SpriteManager spriteManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.modelOutlineRenderer = modelOutlineRenderer;
        this.itemManager = itemManager;
        this.spriteManager = spriteManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        panelComponent.setPreferredSize(new Dimension(205, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || isLobbyScreenOpen()) {
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(FontManager.getRunescapeSmallFont());

        panelComponent.getChildren().clear();

        // -------------------------------------------------------------
        // BRANCH 1: General PvM HUD (Active or Lingering Post-Combat)
        // -------------------------------------------------------------
        if (config.enablePvmOverlay() && plugin.isPvmOverlayActive()) {
            plugin.updateRealtimeCombatMethod();
            String name = plugin.getLastPvmTargetName();
            int cbLvl = plugin.getLastPvmCombatLevel();

            panelComponent.getChildren().add(TitleComponent.builder()
                .text(name + (cbLvl > 0 ? " (Lvl " + cbLvl + ")" : ""))
                .color(new Color(255, 185, 0))
                .build());

            // Embedded Health Bar: <current> / <total> (<percent>%)
            panelComponent.getChildren().add(new CenteredHealthBarComponent(
                plugin.getPvmHpPercent(),
                plugin.getPvmCurrentHp(),
                plugin.getPvmMaxHp()
            ));

            if (config.showPvmAggro()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Aggro:")
                    .right(plugin.getPvmAggroStatusText())
                    .rightColor(plugin.isPvmTargetIsAggressive() ? WARN : Color.WHITE)
                    .build());
            }

            if (config.showPvmWeakness()) {
                MonsterWeakness weakness = plugin.getPvmTargetWeakness();
                panelComponent.getChildren().add(new IconLineComponent(
                    "Weakness:",
                    icon(weakness.type == MonsterWeakness.Type.MAGIC && weakness.element != null
                        ? plugin.elementSpellIcon(weakness.element)
                        : CombatIcons.forWeakness(weakness)),
                    weakness.label(),
                    Color.WHITE));

                if (weakness.hasSeparateElement()) {
                    panelComponent.getChildren().add(new IconLineComponent(
                        "Elemental:",
                        icon(plugin.elementSpellIcon(weakness.element)),
                        weakness.elementLabel(),
                        Color.WHITE));
                }

                if (weakness.note != null) {
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("Bane:")
                        .right(weakness.note)
                        .build());
                }
            }

            panelComponent.getChildren().add(new IconLineComponent(
                "Style:",
                icon(plugin.getCombatMethodIcon()),
                plugin.getCombatMethod(),
                plugin.getCombatMethodColor()));

            if (config.showPvmDps()) {
                String dpsLabel = "Encounter DPS:";
                if (config.dpsMode() == BradsMultiToolConfig.DpsTrackingMode.CONTINUOUS_SESSION) {
                    dpsLabel = "Session DPS:";
                } else if (config.dpsMode() == BradsMultiToolConfig.DpsTrackingMode.PER_TARGET_TYPE) {
                    dpsLabel = "Type DPS:";
                }
                panelComponent.getChildren().add(LineComponent.builder()
                    .left(dpsLabel)
                    .right(String.format("%.1f", plugin.getPvmLiveDps()))
                    .build());
            }

            if (config.showPvmLastHit() && plugin.getPvmLastHit() > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Last Hit:")
                    .right(String.valueOf(plugin.getPvmLastHit()))
                    .build());
            }

            if (config.showPvmMaxHit() && plugin.getPvmMaxHit() > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Max Hit:")
                    .right(String.valueOf(plugin.getPvmMaxHit()))
                    .build());
            }

            if (config.enableBuffStatus()) {
                Color buffColor = getBuffColor(plugin.getBuffState());
                boolean isFlashingState = (plugin.getBuffState() == BradsMultiToolPlugin.BuffState.EXPIRED);
                long millis = System.currentTimeMillis();

                if (config.flashExpiredBuff() && isFlashingState && (millis / 350) % 2 == 0) {
                    buffColor = Color.WHITE;
                }

                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Buff Status:")
                    .right(plugin.getBuffStatusText())
                    .rightColor(buffColor)
                    .build());
            }

            if (config.enableLowPrayerAlert() && plugin.isLowPrayerAlert()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer:")
                    .right("LOW")
                    .rightColor(BAD)
                    .build());
            }

            return panelComponent.render(graphics);
        }

        // -------------------------------------------------------------
        // BRANCH 2: Gemstone Crab Features & HUD
        // -------------------------------------------------------------
        if (!config.enableCrabFeatures() || !plugin.isInSpawnVicinity()) {
            return null;
        }

        NPC crab = plugin.getActiveCrab();
        if (crab != null && !crab.isDead() && plugin.isCrabActive()) {
            modelOutlineRenderer.drawOutline(crab, 2, config.activeCrabColor(), 1);
        }

        NPC shellNpc = plugin.getDormantCrabShellNpc();
        if (shellNpc != null && !shellNpc.isDead()) {
            modelOutlineRenderer.drawOutline(shellNpc, 2, shellColor(plugin.isShellMineable(shellNpc)), 1);
        }

        for (TileObject shellObj : plugin.getTrackedShellObjects()) {
            modelOutlineRenderer.drawOutline(shellObj, 2, shellColor(plugin.isShellMineable(shellObj)), 1);
        }

        if (plugin.isCrabBurrowed()) {
            for (TileObject tunnel : plugin.getTrackedTunnels()) {
                if (tunnel != null) {
                    modelOutlineRenderer.drawOutline(tunnel, 2, config.tunnelColor(), 1);
                }
            }
        }

        Color buffColor = getBuffColor(plugin.getBuffState());
        boolean isFlashingState = (plugin.getBuffState() == BradsMultiToolPlugin.BuffState.EXPIRED);
        long millis = System.currentTimeMillis();
        if (config.flashExpiredBuff() && isFlashingState && (millis / 350) % 2 == 0) {
            buffColor = Color.WHITE;
        }

        // Mode A: Active fighting crab in cove
        if (plugin.isCrabActive()) {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Gemstone Crab (In Vicinity)")
                .color(new Color(0, 220, 220))
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Target:")
                .right(plugin.getTargetCrabHp())
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Burrows in:")
                .right(plugin.getEstimatedBurrowTime())
                .build());

            boolean attacking = plugin.isPlayerAttackingCrab();
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Attacking:")
                .right(attacking ? "YES" : "NO (Idle)")
                .rightColor(attacking ? GOOD : BAD)
                .build());

            // Preserved: Exact XP Type ground truth
            panelComponent.getChildren().add(LineComponent.builder()
                .left("XP Type:")
                .right(plugin.getCrabCombatXpType())
                .build());

            if (config.showCrabLastHit() && plugin.getCrabLastHit() > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Last Hit:")
                    .right(String.valueOf(plugin.getCrabLastHit()))
                    .build());
            }

            if (config.showCrabMaxHit() && plugin.getCrabMaxHit() > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Max Hit:")
                    .right(String.valueOf(plugin.getCrabMaxHit()))
                    .build());
            }

            if (config.enableBuffStatus()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Buff Status:")
                    .right(plugin.getBuffStatusText())
                    .rightColor(buffColor)
                    .build());
            }

            if (config.enableLowPrayerAlert() && plugin.isLowPrayerAlert()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer:")
                    .right("LOW")
                    .rightColor(BAD)
                    .build());
            }

            String dpsFormatted = String.format("%.1f", plugin.getCrabLiveDps());
            String damageLine = String.format("%,d (%s DPS)", plugin.getCurrentCrabDamage(), dpsFormatted);

            // Crab damage reflects only current active crab
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Crab Dmg:")
                .right(damageLine)
                .build());
        }
        // Mode B: Crab Radar
        else {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Gemstone Crab Radar")
                .color(new Color(255, 180, 0))
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Cove:")
                .right(plugin.getCurrentCoveName())
                .build());

            boolean shellPresent = plugin.getDormantCrabShellNpc() != null || !plugin.getTrackedShellObjects().isEmpty();
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Crab Status:")
                .right(shellPresent ? "Burrowed (Shell)" : "In Other Cave")
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Next Action:")
                .right("Use Tunnel")
                .build());
        }

        return panelComponent.render(graphics);
    }

    private Color shellColor(boolean mineable) {
        return mineable ? config.mineableShellColor() : config.depletedShellColor();
    }

    private Color getBuffColor(BradsMultiToolPlugin.BuffState state) {
        switch (state) {
            case EXPIRED: return BAD;
            case ACTIVE: return GOOD;
            default: return Color.WHITE;
        }
    }

    private boolean isLobbyScreenOpen() {
        Player local = client.getLocalPlayer();
        if (local == null) {
            return true;
        }
        return client.getWidget(378, 87) != null 
            || client.getWidget(378, 0) != null;
    }

    private Image icon(CombatIcons.Icon icon) {
        if (icon == null) {
            return null;
        }
        return icon.sprite ? spriteManager.getSprite(icon.id, 0) : itemManager.getImage(icon.id);
    }

    /**
     * A label on the left and an icon followed by coloured text on the right, sized like a LineComponent row.
     */
    private static class IconLineComponent implements LayoutableRenderableEntity {
        private static final int ICON_SIZE = 16;
        private static final int ICON_GAP = 3;
        private static final int LABEL_GAP = 6;

        private final String left;
        private final Image icon;
        private final String right;
        private final Color rightColor;
        private final Rectangle bounds = new Rectangle();

        IconLineComponent(String left, Image icon, String right, Color rightColor) {
            this.left = left;
            this.icon = icon;
            this.right = right;
            this.rightColor = rightColor;
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            FontMetrics fm = graphics.getFontMetrics();
            int width = bounds.width > 0 ? bounds.width : 185;
            int height = Math.max(ICON_SIZE, fm.getHeight());
            int x = bounds.x;
            int baseline = bounds.y + (height - fm.getHeight()) / 2 + fm.getAscent();

            drawShadowed(graphics, left, x, baseline, Color.WHITE);

            // Never let the right side run into the label: trim it with "..." if it doesn't fit
            int available = width - fm.stringWidth(left) - LABEL_GAP - (icon != null ? ICON_SIZE + ICON_GAP : 0);
            String text = fitText(right, fm, available);
            int textX = x + width - fm.stringWidth(text);
            drawShadowed(graphics, text, textX, baseline, rightColor);

            if (icon != null) {
                int iconX = textX - ICON_GAP - ICON_SIZE;
                graphics.drawImage(icon, iconX, bounds.y, ICON_SIZE, ICON_SIZE, null);
            }

            return new Dimension(width, height);
        }

        private static String fitText(String text, FontMetrics fm, int maxWidth) {
            if (fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            String ellipsis = "...";
            int end = text.length();
            while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
                end--;
            }
            return text.substring(0, end) + ellipsis;
        }

        private static void drawShadowed(Graphics2D graphics, String text, int x, int y, Color color) {
            graphics.setColor(Color.BLACK);
            graphics.drawString(text, x + 1, y + 1);
            graphics.setColor(color);
            graphics.drawString(text, x, y);
        }

        @Override
        public void setPreferredLocation(java.awt.Point position) {
            bounds.setLocation(position);
        }

        @Override
        public void setPreferredSize(Dimension dimension) {
            bounds.setSize(dimension);
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }
    }

    private static class CenteredHealthBarComponent implements LayoutableRenderableEntity {
        private final int percent;
        private final int curHp;
        private final int maxHp;
        private Rectangle bounds = new Rectangle();

        CenteredHealthBarComponent(int percent, int curHp, int maxHp) {
            this.percent = Math.max(0, Math.min(100, percent));
            this.curHp = curHp;
            this.maxHp = maxHp;
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            int width = bounds.width > 0 ? bounds.width : 185;
            int height = 15;

            int x = bounds.x;
            int y = bounds.y + 3;

            graphics.setColor(new Color(30, 30, 30, 230));
            graphics.fillRect(x, y, width, height);

            int fillWidth = (int) ((width * percent) / 100.0);
            Color fillColor = (percent > 50) ? new Color(0, 190, 80) : ((percent > 20) ? new Color(240, 170, 0) : new Color(230, 40, 40));
            graphics.setColor(fillColor);
            graphics.fillRect(x, y, fillWidth, height);

            graphics.setColor(new Color(15, 15, 15, 240));
            graphics.drawRect(x, y, width, height);

            String text = (maxHp > 0) ? (curHp + " / " + maxHp + " (" + percent + "%)") : (percent + "%");
            Font font = FontManager.getRunescapeSmallFont();
            graphics.setFont(font);
            FontMetrics fm = graphics.getFontMetrics(font);
            int textX = x + (width - fm.stringWidth(text)) / 2;
            int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();

            graphics.setColor(Color.BLACK);
            graphics.drawString(text, textX + 1, textY + 1);
            graphics.setColor(Color.WHITE);
            graphics.drawString(text, textX, textY);

            return new Dimension(width, height + 6);
        }

        @Override
        public void setPreferredLocation(java.awt.Point position) {
            this.bounds.setLocation(position);
        }

        @Override
        public void setPreferredSize(Dimension dimension) {
            this.bounds.setSize(dimension);
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }
    }
}
