package com.snuffys.multitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BradsMultiToolOverlay extends Overlay {

    @Inject
    private CombatStyle combatStyle;

    private static final Color GOOD = HudColors.GOOD;
    private static final Color BAD = HudColors.BAD;
    private static final Color WARN = HudColors.WARN;

    private final Client client;
    private final PvmTracker plugin;
    private final PvmConfig config;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private final SkillIconManager skillIconManager;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    private SlayerMonsterInfo slayerInfo;

    @Inject
    public BradsMultiToolOverlay(Client client, PvmTracker plugin, PvmConfig config, ItemManager itemManager, SpriteManager spriteManager, SkillIconManager skillIconManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.spriteManager = spriteManager;
        this.skillIconManager = skillIconManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        panelComponent.setPreferredSize(new Dimension(205, 0));
        // Its own reset, in its right-click menu
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "PvM session", e -> plugin.resetSession());
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
            combatStyle.update();
            String name = plugin.getLastPvmTargetName();
            int cbLvl = plugin.getLastPvmCombatLevel();

            panelComponent.getChildren().add(TitleComponent.builder()
                .text(name + (cbLvl > 0 ? " (Lvl " + cbLvl + ")" : ""))
                .color(client.getLocalPlayer() != null
                    ? combatLevelColor(client.getLocalPlayer().getCombatLevel(), cbLvl)
                    : Color.WHITE)
                .build());

            // Embedded Health Bar: <current> / <total> (<percent>%)
            // Read the target's health bar live each frame so the HUD doesn't trail the game by a tick
            int hpPercent = plugin.getPvmHpPercent();
            int currentHp = plugin.getPvmCurrentHp();
            NPC target = plugin.getCurrentPvmTarget();
            if (target != null && target.getHealthRatio() >= 0 && target.getHealthScale() > 0) {
                hpPercent = target.getHealthRatio() * 100 / target.getHealthScale();
                currentHp = (int) Math.ceil((double) target.getHealthRatio() * plugin.getPvmMaxHp() / target.getHealthScale());
            }
            panelComponent.getChildren().add(new CenteredHealthBarComponent(hpPercent, currentHp, plugin.getPvmMaxHp()));

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
                        ? CombatStyle.elementSpellIcon(weakness.element)
                        : CombatIcons.forWeakness(weakness)),
                    weakness.label(),
                    weaknessColor(weakness)));

                if (weakness.hasSeparateElement()) {
                    panelComponent.getChildren().add(new IconLineComponent(
                        "Elemental:",
                        icon(CombatStyle.elementSpellIcon(weakness.element)),
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

            if (config.showSlayerMonsterInfo() && target != null) {
                slayerInfo.addTo(panelComponent, target, plugin.statsFor(target));
            }

            panelComponent.getChildren().add(new IconLineComponent(
                "Style:",
                icon(combatStyle.getCombatMethodIcon()),
                combatStyle.getCombatMethod(),
                plugin.getCombatMethodColor()));

            if (config.showPvmDps()) {
                String dpsLabel = "Encounter DPS:";
                if (config.dpsMode() == PvmConfig.DpsTrackingMode.CONTINUOUS_SESSION) {
                    dpsLabel = "Session DPS:";
                } else if (config.dpsMode() == PvmConfig.DpsTrackingMode.PER_TARGET_TYPE) {
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

            addStatusRows(panelComponent);

            return panelComponent.render(graphics);
        }

        return null;
    }


    static Color combatLevelColor(int playerLevel, int npcLevel) {
        if (npcLevel <= 0) {
            return Color.WHITE;
        }
        int diff = playerLevel - npcLevel;
        if (diff < -9) {
            return new Color(0xff0000);
        }
        if (diff < -6) {
            return new Color(0xff3000);
        }
        if (diff < -3) {
            return new Color(0xff7000);
        }
        if (diff < 0) {
            return new Color(0xffb000);
        }
        if (diff > 9) {
            return new Color(0x00ff00);
        }
        if (diff > 6) {
            return new Color(0x40ff00);
        }
        if (diff > 3) {
            return new Color(0x80ff00);
        }
        if (diff > 0) {
            return new Color(0xc0ff00);
        }
        return new Color(0xffff00);
    }

    /**
     * One row per active buff with its potion icon, boost and time left (counting down smoothly
     * between game ticks); otherwise a single Buff Status line, flashing when expired.
     */
    /** Buff timers (or Buff Status), and LOW prayer and health warnings, on this HUD. */
    private void addStatusRows(PanelComponent panel) {
        if (config.enableBuffStatus()) {
            addBuffRows(panel);
        }
        if (config.enableLowPrayerAlert() && plugin.isLowPrayerAlert()) {
            panel.getChildren().add(LineComponent.builder().left("Prayer:").right("LOW").rightColor(BAD).build());
        }
        if (config.enableLowHealthAlert() && plugin.isLowHealthAlert()) {
            panel.getChildren().add(LineComponent.builder().left("Health:").right("LOW").rightColor(BAD).build());
        }
    }

    private void addBuffRows(PanelComponent panelComponent) {
        if (plugin.getBuffState() == PotionAlerts.BuffState.ACTIVE && !plugin.getActiveBuffs().isEmpty()) {
            long now = System.currentTimeMillis();
            for (ActiveBuffs.Buff buff : plugin.getActiveBuffs()) {
                String time = (buff.exact ? "" : "~") + BuffTimer.formatMillis(buff.millisLeft(now));
                // Red while it's within the "running out" window, green above it
                panelComponent.getChildren().add(new IconLineComponent(buff.name + ":",
                    itemManager.getImage(buff.iconItemId), "+" + buff.boost + " " + time,
                    plugin.isBuffRunningOut(buff) ? BAD : GOOD));
            }
            return;
        }

        Color color = getBuffColor(plugin.getBuffState());
        boolean flashing = plugin.getBuffState() == PotionAlerts.BuffState.EXPIRED;
        if (config.flashExpiredBuff() && flashing && (System.currentTimeMillis() / 350) % 2 == 0) {
            color = Color.WHITE;
        }
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Buff Status:")
            .right(plugin.getBuffStatusText())
            .rightColor(color)
            .build());
    }

    // Green when your current style hits the weakness, red when it doesn't, white when it can't be told
    private Color weaknessColor(MonsterWeakness weakness) {
        WeaponStyles.Style style = combatStyle.getCurrentStyle();
        return style != null ? CombatStyle.weaknessMatchColor(style, weakness) : Color.WHITE;
    }

    private Color getBuffColor(PotionAlerts.BuffState state) {
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
}
