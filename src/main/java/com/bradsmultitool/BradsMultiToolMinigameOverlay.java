package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The minigame cards, each only in its minigame: Wintertodt (your warmth, its energy, your points, roots and
 * kindling, the next round), Tempoross (its energy and essence, the storm, your points, fish and their damage) and
 * Guardians of the Rift (the Great Guardian's power, your energy, guardians built, the open altars with the level
 * each needs, the portal). Everything is read from the game's own boxes and your inventory.
 */
@Singleton
class BradsMultiToolMinigameOverlay extends Overlay {

    private static final Color TITLE = new Color(255, 185, 0);

    @Inject
    private net.runelite.client.config.RuneLiteConfig runeLiteConfig;

    private final Client client;
    private final SkillTrackerConfig config;
    private final WintertodtHelper wintertodt;
    private final TemporossHelper tempoross;
    private final RiftHelper rift;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    BradsMultiToolMinigameOverlay(Client client, SkillTrackerConfig config, WintertodtHelper wintertodt,
                                  TemporossHelper tempoross, RiftHelper rift) {
        this.client = client;
        this.config = config;
        this.wintertodt = wintertodt;
        this.tempoross = tempoross;
        this.rift = rift;
        setPosition(OverlayPosition.TOP_LEFT);
        panel.setPreferredSize(new Dimension(170, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        HudColors.theme(panel, runeLiteConfig.overlayBackgroundColor());
        panel.getChildren().clear();
        if (config.wintertodtCard() && wintertodt.inRoom()) {
            wintertodtCard();
        } else if (config.temporossCard() && tempoross.inGame()) {
            temporossCard();
        } else if (config.riftCard() && rift.inGame()) {
            riftCard();
        } else {
            return null;
        }
        return panel.render(graphics);
    }

    private void title(String text) {
        panel.getChildren().add(TitleComponent.builder().text(text).color(TITLE).build());
    }

    private void line(String left, String right, Color color) {
        panel.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(color).build());
    }

    // Green when high, orange in the middle, red when low
    static Color level(int percent, int low) {
        return percent < low ? HudColors.BAD : percent < low * 2 ? HudColors.WARN : HudColors.GOOD;
    }

    private void wintertodtCard() {
        title("Wintertodt");
        int startsIn = wintertodt.roundStartsIn();
        if (startsIn > 0) {
            line("Round starts in", startsIn + "s", Color.WHITE);
        }
        int warmth = wintertodt.warmth();
        if (warmth > 0) {
            line("Warmth", warmth + "%", level(warmth, config.wintertodtWarmthPercent()));
        }
        int energy = wintertodt.energy();
        if (energy >= 0 && startsIn == 0) {
            line("Wintertodt energy", energy + "%", Color.WHITE);
        }
        int points = wintertodt.points();
        if (points >= 0) {
            // 500 points earns a reward crate (the game turns them white there too)
            line("Points", String.valueOf(points), points >= 500 ? HudColors.GOOD : Color.WHITE);
        }
        int[] carried = wintertodt.carried();
        line("Roots / kindling", carried[0] + " / " + carried[1], Color.WHITE);
    }

    private void temporossCard() {
        title("Tempoross");
        int energy = tempoross.energy();
        if (energy >= 0) {
            line("Energy", energy + "%", Color.WHITE);
        }
        int essence = tempoross.essence();
        if (essence >= 0) {
            line("Essence", essence + "%", Color.WHITE);
        }
        int storm = tempoross.storm();
        if (storm >= 0) {
            // The game is lost when the storm fills up
            line("Storm", storm + "%", storm >= 90 ? HudColors.BAD : storm >= 70 ? HudColors.WARN : Color.WHITE);
        }
        int points = tempoross.points();
        if (points >= 0) {
            // 2,000 points earns a reward permit
            line("Points", String.format("%,d", points), points >= 2000 ? HudColors.GOOD : Color.WHITE);
        }
        int[] fish = tempoross.fish();
        line("Raw / cooked fish", fish[0] + " / " + fish[1], Color.WHITE);
        line("Fish damage", String.valueOf(fish[2]), Color.WHITE);
    }

    private void riftCard() {
        title("Guardians of the Rift");
        int power = rift.power();
        if (power >= 0) {
            line("Guardian's power", power + "%", Color.WHITE);
        }
        int elemental = rift.elementalEnergy();
        int catalytic = rift.catalyticEnergy();
        if (elemental >= 0 && catalytic >= 0) {
            line("Elemental / catalytic", String.format("%,d / %,d", elemental, catalytic), Color.WHITE);
        }
        String built = rift.guardiansBuilt();
        if (built != null && !built.isEmpty()) {
            line("Guardians built", built, Color.WHITE);
        }
        int level = rift.level();
        for (RiftHelper.Guardian altar : rift.openAltars()) {
            if (altar != null) {
                boolean can = level >= altar.level;
                line((altar.catalytic ? "Catalytic: " : "Elemental: ") + altar.name,
                    can ? "level " + altar.level : "needs " + altar.level, can ? HudColors.GOOD : HudColors.BAD);
            }
        }
        String portal = rift.portal();
        if (portal != null) {
            line("Portal", portal, HudColors.GOOD);
        }
    }
}
