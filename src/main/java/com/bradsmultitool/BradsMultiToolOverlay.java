package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
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

    // Text is white unless colour carries meaning: good/bad state, or a warning
    static final Color GOOD = new Color(50, 220, 50);
    static final Color BAD = new Color(255, 70, 70);
    static final Color WARN = new Color(255, 150, 0);

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private final SkillIconManager skillIconManager;
    private final PanelComponent panelComponent = new PanelComponent();
    @Inject
    private SlayerMonsterInfo slayerInfo;

    @Inject
    public BradsMultiToolOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config, ItemManager itemManager, SpriteManager spriteManager, SkillIconManager skillIconManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.spriteManager = spriteManager;
        this.skillIconManager = skillIconManager;
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

            if (config.showSlayerMonsterInfo() && target != null) {
                slayerInfo.addTo(panelComponent, target, plugin.statsFor(target));
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
                addBuffRows();
            }

            if (config.enableLowPrayerAlert() && plugin.isLowPrayerAlert()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer:")
                    .right("LOW")
                    .rightColor(BAD)
                    .build());
            }

            if (config.enableLowHealthAlert() && plugin.isLowHealthAlert()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Health:")
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

        // Outlines are drawn by BradsMultiToolCrabOutlineOverlay, under the game's interface

        // Mode A: Active fighting crab in cove
        if (plugin.isCrabActive()) {
            panelComponent.getChildren().add(new RainbowTitleComponent("Gemstone Crab", "", Color.WHITE));

            if (plugin.getCrabHpPercent() >= 0) {
                // No max HP for the crab, so the bar shows just the percentage
                panelComponent.getChildren().add(new CenteredHealthBarComponent(liveCrabHpPercent(), 0, 0, true));
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Target:")
                    .right(plugin.getTargetCrabHp())
                    .build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Burrows in:")
                .right(plugin.getEstimatedBurrowTime())
                .build());

            boolean attacking = plugin.isPlayerAttackingCrab();
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Attacking:")
                .right(attacking ? "YES" : "NO")
                .rightColor(attacking ? GOOD : BAD)
                .build());

            // Preserved: Exact XP Type ground truth
            List<Image> skillIcons = new ArrayList<>();
            for (Skill skill : plugin.getCrabCombatXpSkills()) {
                skillIcons.add(skillIconManager.getSkillImage(skill, true));
            }
            panelComponent.getChildren().add(new IconLineComponent("XP Type:", skillIcons,
                plugin.getCrabCombatXpType(), Color.WHITE));

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
                addBuffRows();
            }

            if (config.enableLowPrayerAlert() && plugin.isLowPrayerAlert()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer:")
                    .right("LOW")
                    .rightColor(BAD)
                    .build());
            }

            if (config.enableLowHealthAlert() && plugin.isLowHealthAlert()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Health:")
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
            panelComponent.getChildren().add(new RainbowTitleComponent("Gemstone Crab", " Radar", new Color(255, 180, 0)));

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

    /**
     * The game's right-click colour for a monster's combat level relative to the player's: green when
     * lower, yellow when equal, through orange to red when higher.
     */
    // Live health bar reading, falling back to the last known value while the bar isn't shown
    private int liveCrabHpPercent() {
        NPC crab = plugin.getActiveCrab();
        if (crab != null && crab.getHealthRatio() >= 0 && crab.getHealthScale() > 0) {
            return crab.getHealthRatio() * 100 / crab.getHealthScale();
        }
        return plugin.getCrabHpPercent();
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
    private void addBuffRows() {
        if (plugin.getBuffState() == BradsMultiToolPlugin.BuffState.ACTIVE && !plugin.getActiveBuffs().isEmpty()) {
            long now = System.currentTimeMillis();
            for (ActiveBuffs.Buff buff : plugin.getActiveBuffs()) {
                String time = (buff.exact ? "" : "~") + BuffTimer.formatMillis(buff.millisLeft(now));
                panelComponent.getChildren().add(new IconLineComponent(buff.name + ":",
                    itemManager.getImage(buff.iconItemId), "+" + buff.boost + " " + time, GOOD));
            }
            return;
        }

        Color color = getBuffColor(plugin.getBuffState());
        boolean flashing = plugin.getBuffState() == BradsMultiToolPlugin.BuffState.EXPIRED;
        if (config.flashExpiredBuff() && flashing && (System.currentTimeMillis() / 350) % 2 == 0) {
            color = Color.WHITE;
        }
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Buff Status:")
            .right(plugin.getBuffStatusText())
            .rightColor(color)
            .build());
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
}
