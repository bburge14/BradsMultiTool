package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Tooltip-style card next to the cursor when hovering an attackable monster: level (coloured like the
 * game's right-click menu), health, weakness, your style against it, its attack style and aggression.
 */
public class BradsMultiToolHoverOverlay extends Overlay {

    // Sit well below the cursor so the game's hover text ("Attack Guard (level-21)") stays readable
    private static final int CURSOR_OFFSET_X = 0;
    private static final int CURSOR_OFFSET_Y = 52;
    private static final int WIDTH = 205;

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public BradsMultiToolHoverOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config,
                                      ItemManager itemManager, SpriteManager spriteManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.spriteManager = spriteManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGHEST);
        panel.setPreferredSize(new Dimension(WIDTH, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showHoverInfo() || client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen()) {
            return null;
        }

        NPC npc = hoveredNpc();
        if (npc == null || !plugin.isAttackable(npc) || plugin.isGemstoneCrab(npc) || isAlreadyShownByPvmHud(npc)) {
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(FontManager.getRunescapeSmallFont());
        buildPanel(npc);

        // Measure first (drawing clipped away), then place next to the cursor, kept on screen
        Graphics2D measure = (Graphics2D) graphics.create();
        measure.setClip(new Rectangle(0, 0, 0, 0));
        Dimension size = panel.render(measure);
        measure.dispose();

        Point mouse = client.getMouseCanvasPosition();
        int x = mouse.getX() + CURSOR_OFFSET_X;
        int y = mouse.getY() + CURSOR_OFFSET_Y;
        if (x + size.width > client.getCanvasWidth()) {
            x = mouse.getX() - CURSOR_OFFSET_X - size.width;
        }
        if (y + size.height > client.getCanvasHeight()) {
            y = mouse.getY() - CURSOR_OFFSET_Y - size.height;
        }

        Graphics2D placed = (Graphics2D) graphics.create();
        placed.translate(Math.max(0, x), Math.max(0, y));
        panel.render(placed);
        placed.dispose();
        return null;
    }

    /** The NPC under the cursor, from the top-most menu entry that targets one. */
    private NPC hoveredNpc() {
        MenuEntry[] entries = client.getMenu().getMenuEntries();
        for (int i = entries.length - 1; i >= 0; i--) {
            NPC npc = entries[i].getNpc();
            if (npc != null) {
                return npc;
            }
        }
        return null;
    }

    // The PvM HUD already covers the monster being fought, and others of the same kind
    private boolean isAlreadyShownByPvmHud(NPC npc) {
        if (!config.enablePvmOverlay() || !plugin.isPvmOverlayActive()) {
            return false;
        }
        NPC target = plugin.getCurrentPvmTarget();
        if (target != null && (target == npc || target.getId() == npc.getId())) {
            return true;
        }
        return npc.getName() != null && npc.getName().equalsIgnoreCase(plugin.getLastPvmTargetName());
    }

    private void buildPanel(NPC npc) {
        panel.getChildren().clear();
        MonsterData.Stats stats = plugin.statsFor(npc);
        MonsterWeakness weakness = plugin.weaknessFor(npc);

        int level = npc.getCombatLevel();
        panel.getChildren().add(TitleComponent.builder()
            .text(npc.getName() + (level > 0 ? " (Lvl " + level + ")" : ""))
            .color(levelColor(level))
            .build());

        int maxHp = stats != null ? stats.hitpoints : 0;
        int ratio = npc.getHealthRatio();
        int scale = npc.getHealthScale();
        int percent = ratio >= 0 && scale > 0 ? ratio * 100 / scale : 100;
        int currentHp = maxHp > 0 ? (int) Math.ceil(percent * maxHp / 100.0) : 0;
        panel.getChildren().add(new CenteredHealthBarComponent(percent, currentHp, maxHp));

        panel.getChildren().add(new IconLineComponent("Weakness:", icon(
            weakness.type == MonsterWeakness.Type.MAGIC && weakness.element != null
                ? plugin.elementSpellIcon(weakness.element)
                : CombatIcons.forWeakness(weakness)),
            weakness.label(), Color.WHITE));

        if (weakness.hasSeparateElement()) {
            panel.getChildren().add(new IconLineComponent("Elemental:",
                icon(plugin.elementSpellIcon(weakness.element)), weakness.elementLabel(), Color.WHITE));
        }

        WeaponStyles.Style style = plugin.getCurrentStyle();
        if (style != null) {
            panel.getChildren().add(new IconLineComponent("Style:", icon(plugin.getCombatMethodIcon()),
                style.label(), plugin.weaknessMatchColor(style, weakness)));
        }

        if (stats != null && !stats.attackStyles.isEmpty()) {
            panel.getChildren().add(new IconLineComponent("Attacks With:",
                icon(CombatIcons.forAttackStyle(stats.attackStyles.get(0))),
                String.join(", ", stats.attackStyles), Color.WHITE));
        }

        boolean aggressive = stats != null && stats.aggressive;
        panel.getChildren().add(LineComponent.builder()
            .left("Aggressive:")
            .right(stats == null ? "Unknown" : aggressive ? "Yes" : "No")
            .rightColor(aggressive ? BradsMultiToolOverlay.WARN : Color.WHITE)
            .build());
    }

    private Color levelColor(int npcLevel) {
        Player local = client.getLocalPlayer();
        return local != null ? BradsMultiToolOverlay.combatLevelColor(local.getCombatLevel(), npcLevel) : Color.WHITE;
    }

    private Image icon(CombatIcons.Icon icon) {
        if (icon == null) {
            return null;
        }
        return icon.sprite ? spriteManager.getSprite(icon.id, 0) : itemManager.getImage(icon.id);
    }
}
