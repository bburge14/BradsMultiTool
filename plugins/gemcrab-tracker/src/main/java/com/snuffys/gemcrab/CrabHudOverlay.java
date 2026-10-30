package com.snuffys.gemcrab;

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
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * Snuffy's GemCrab Tracker HUD, near the crab's coves: the fight (health, burrow countdown, whether you're
 * attacking, XP type, hits and damage, kept up a few seconds after it moves), else the radar pointing you to
 * the next cove.
 */
public class CrabHudOverlay extends Overlay {

    private static final Color GOOD = HudColors.GOOD;
    private static final Color BAD = HudColors.BAD;
    // After the crab moves its stats stay up this long before the radar takes over
    private static final long CRAB_STATS_LINGER_MILLIS = 12_000;

    private final Client client;
    private final GemCrabTracker gemCrab;
    private final GemCrabConfig config;
    private final SkillIconManager skillIconManager;
    private final PotionAlerts potionAlerts;
    private final ItemManager itemManager;
    private final GearCheck gearCheck;
    private final CombatStyle combatStyle;
    private final PanelComponent panelComponent = new PanelComponent();
    private long lastCrabActiveMillis;
    private CrabFight lastFight;
    /** The crab fight's numbers, as of the last frame the crab was up. */
    private static final class CrabFight {
        final long damage;
        final double dps;
        final int lastHit;
        final int maxHit;
        final List<Skill> xpSkills;
        final String xpType;

        CrabFight(long damage, double dps, int lastHit, int maxHit, List<Skill> xpSkills, String xpType) {
            this.damage = damage;
            this.dps = dps;
            this.lastHit = lastHit;
            this.maxHit = maxHit;
            this.xpSkills = xpSkills;
            this.xpType = xpType;
        }
    }

    @Inject
    CrabHudOverlay(Client client, GemCrabTracker gemCrab, GemCrabConfig config, SkillIconManager skillIconManager,
                   PotionAlerts potionAlerts, ItemManager itemManager, GearCheck gearCheck, CombatStyle combatStyle) {
        this.potionAlerts = potionAlerts;
        this.itemManager = itemManager;
        this.gearCheck = gearCheck;
        this.combatStyle = combatStyle;
        this.client = client;
        this.gemCrab = gemCrab;
        this.config = config;
        this.skillIconManager = skillIconManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        panelComponent.setPreferredSize(new Dimension(205, 0));
        // Its own reset, in its right-click menu
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "Gemstone Crab session", e -> gemCrab.resetSession());
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
        // BRANCH 2: Gemstone Crab Features & HUD
        // -------------------------------------------------------------
        if (!config.enableCrabFeatures() || !gemCrab.isInSpawnVicinity()) {
            return null;
        }

        // Outlines are drawn by BradsMultiToolCrabOutlineOverlay, under the game's interface

        // Mode A: Active fighting crab in cove, and for a few seconds after it moves so you can read the
        // final stats; then Mode B, the radar
        long now = System.currentTimeMillis();
        boolean active = gemCrab.isCrabActive();
        if (active) {
            lastCrabActiveMillis = now;
        }
        long lingerLeft = CRAB_STATS_LINGER_MILLIS - (now - lastCrabActiveMillis);
        if (active) {
            // Kept so the stats can still be read for a few seconds after the crab moves (they reset then)
            lastFight = new CrabFight(gemCrab.getCurrentCrabDamage(), gemCrab.getCrabLiveDps(), gemCrab.getCrabLastHit(),
                gemCrab.getCrabMaxHit(), new ArrayList<>(gemCrab.getCrabCombatXpSkills()), gemCrab.getCrabCombatXpType());
        }
        if (active || (lingerLeft > 0 && lastFight != null)) {
            CrabFight fight = lastFight;
            panelComponent.getChildren().add(new RainbowTitleComponent("Gemstone Crab", active ? "" : " (moved)", Color.WHITE));

            if (!active) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Radar in:")
                    .right((lingerLeft / 1000 + 1) + "s")
                    .build());
            } else if (gemCrab.getCrabHpPercent() >= 0) {
                // No max HP for the crab, so the bar shows just the percentage
                panelComponent.getChildren().add(new CenteredHealthBarComponent(liveCrabHpPercent(), 0, 0, true));
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Target:")
                    .right(gemCrab.getTargetCrabHp())
                    .build());
            }

            if (active) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Burrows in:")
                    .right(gemCrab.getEstimatedBurrowTime())
                    .build());

                boolean attacking = gemCrab.isPlayerAttackingCrab();
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Attacking:")
                    .right(attacking ? "YES" : "NO")
                    .rightColor(attacking ? GOOD : BAD)
                    .build());
            }

            // Preserved: Exact XP Type ground truth
            List<Image> skillIcons = new ArrayList<>();
            for (Skill skill : fight.xpSkills) {
                skillIcons.add(skillIconManager.getSkillImage(skill, true));
            }
            panelComponent.getChildren().add(new IconLineComponent("XP Type:", skillIcons, fight.xpType, Color.WHITE));

            if (config.showCrabLastHit() && fight.lastHit > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Last Hit:")
                    .right(String.valueOf(fight.lastHit))
                    .build());
            }

            if (config.showCrabMaxHit() && fight.maxHit > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Max Hit:")
                    .right(String.valueOf(fight.maxHit))
                    .build());
            }

            if (config.crabGearCheck()) {
                addGearRows();
            }

            // Your potion buffs (PotionAlerts) and LOW prayer/health: the same in the Toolbelt and on its own
            if (config.enableBuffStatus()) {
                addBuffRows();
            }
            addLowRows();

            // Crab damage reflects only the current (or just-moved) crab
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Crab Dmg:")
                .right(String.format("%,d (%.1f DPS)", fight.damage, fight.dps))
                .build());
        }
        // Mode B: Crab Radar
        else {
            panelComponent.getChildren().add(new RainbowTitleComponent("Gemstone Crab", " Radar", new Color(255, 180, 0)));

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Cove:")
                .right(gemCrab.getCurrentCoveName())
                .build());

            boolean shellPresent = gemCrab.getDormantCrabShellNpc() != null || !gemCrab.getTrackedShellObjects().isEmpty();
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

    // Your gear's damage bonus for the style you're using (green, red with none), and items whose damage bonus
    // is for another style ("Off-style: Archer helm")
    private void addGearRows() {
        GearCheck.Style style = GearCheck.of(combatStyle.getCombatDiscipline());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Gear:")
            .right(gearCheck.total(style))
            .rightColor(gearCheck.hasDamage(style) ? HudColors.GOOD : HudColors.BAD)
            .build());
        java.util.List<String> off = gearCheck.offStyle(style);
        if (!off.isEmpty()) {
            panelComponent.getChildren().add(new TextBlockComponent("Off-style: " + String.join(", ", off), HudColors.BAD, false));
        }
    }

    // "LOW" while prayer or health is at or below the set share of its level (0: off)
    private void addLowRows() {
        int percent = config.crabLowStatsPercent();
        if (percent <= 0) {
            return;
        }
        if (isLow(Skill.PRAYER, percent)) {
            panelComponent.getChildren().add(LineComponent.builder().left("Prayer:").right("LOW").rightColor(HudColors.BAD).build());
        }
        if (isLow(Skill.HITPOINTS, percent)) {
            panelComponent.getChildren().add(LineComponent.builder().left("Health:").right("LOW").rightColor(HudColors.BAD).build());
        }
    }

    private boolean isLow(Skill skill, int percent) {
        int points = client.getBoostedSkillLevel(skill);
        return points > 0 && points <= Math.max(1, (int) Math.ceil(client.getRealSkillLevel(skill) * percent / 100.0));
    }

    // Each potion buff with its time left ("~" until the boost timer has been seen), red while it's running out
    // (Warn Before Ending), as on the PvM HUD
    private void addBuffRows() {
        long now = System.currentTimeMillis();
        for (ActiveBuffs.Buff buff : potionAlerts.getActiveBuffs()) {
            String time = (buff.exact ? "" : "~") + BuffTimer.formatMillis(buff.millisLeft(now));
            panelComponent.getChildren().add(new IconLineComponent(buff.name + ":", itemManager.getImage(buff.iconItemId),
                "+" + buff.boost + " " + time, potionAlerts.isBuffRunningOut(buff) ? HudColors.BAD : HudColors.GOOD));
        }
    }

    /**
     * The game's right-click colour for a monster's combat level relative to the player's: green when
     * lower, yellow when equal, through orange to red when higher.
     */
    // Live health bar reading, falling back to the last known value while the bar isn't shown
    private int liveCrabHpPercent() {
        NPC crab = gemCrab.getActiveCrab();
        if (crab != null && crab.getHealthRatio() >= 0 && crab.getHealthScale() > 0) {
            return crab.getHealthRatio() * 100 / crab.getHealthScale();
        }
        return gemCrab.getCrabHpPercent();
    }

    private boolean isLobbyScreenOpen() {
        Player local = client.getLocalPlayer();
        if (local == null) {
            return true;
        }
        return client.getWidget(378, 87) != null
            || client.getWidget(378, 0) != null;
    }
}
