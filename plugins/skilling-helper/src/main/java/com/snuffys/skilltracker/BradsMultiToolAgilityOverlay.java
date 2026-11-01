package com.snuffys.skilltracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Agility's cards (see {@link AgilityTracker}): in the Brimhaven Agility Arena, the time until the ticket pillar
 * moves and your tickets; on a course, your laps, rates, lap time, laps to your next level and goal, and marks.
 */
@Singleton
public class BradsMultiToolAgilityOverlay extends Overlay {

    private static final Color TITLE = new Color(255, 185, 0);

    @Inject
    private net.runelite.client.config.RuneLiteConfig runeLiteConfig;

    private final Client client;
    private final SkillTrackerConfig config;
    private final AgilityTracker agility;
    private final SkillTracker plugin;
    @Inject
    private ArenaRoute route;
    @Inject
    private ObstacleReps reps;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public BradsMultiToolAgilityOverlay(Client client, SkillTrackerConfig config, AgilityTracker agility, SkillTracker plugin) {
        this.client = client;
        this.config = config;
        this.agility = agility;
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        panel.setPreferredSize(new Dimension(190, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        HudColors.theme(panel, runeLiteConfig.overlayBackgroundColor());
        panel.getChildren().clear();
        if (config.agilityArenaCard() && agility.inArena()) {
            arenaCard();
        } else if (reps.trainingOne()) {
            panel.getChildren().add(TitleComponent.builder().text(reps.getName()).color(TITLE).build());
            trainingLines();
        } else if (config.agilityCourseCard() && agility.courseActive()) {
            courseCard();
        } else {
            return null;
        }
        return panel.render(graphics);
    }

    // The arena: time until the pillar moves (red near the end), tickets
    private void arenaCard() {
        panel.getChildren().add(TitleComponent.builder().text("Brimhaven Agility Arena").color(TITLE).build());
        int left = agility.getArenaTicksLeft();
        if (left < 0) {
            panel.getChildren().add(LineComponent.builder().left("Pillar moves in").right("tag a pillar").build());
        } else {
            int warn = (int) Math.round(config.arenaWarnSeconds() / 0.6);
            Color color = left <= warn ? HudColors.BAD : left <= warn * 2 ? HudColors.WARN : HudColors.GOOD;
            ProgressBarComponent bar = new ProgressBarComponent();
            bar.setMinimum(0);
            bar.setMaximum(AgilityTracker.ARENA_TICKS);
            bar.setValue(left);
            bar.setForegroundColor(color);
            bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
            bar.setCenterLabel("Pillar moves in " + clock(left));
            panel.getChildren().add(bar);
        }
        panel.getChildren().add(LineComponent.builder().left("Tickets").right(String.valueOf(agility.getTickets())).build());
        if (agility.ticketsPerHour() > 0) {
            panel.getChildren().add(LineComponent.builder().left("Tickets/hr").right(String.valueOf((int) agility.ticketsPerHour())).build());
        }
        if (reps.trainingOne()) {
            trainingLines();
        }
        if (config.arenaRoute()) {
            String why = route.whyNoRoute();
            panel.getChildren().add(LineComponent.builder().left("Route").right(why != null ? why
                : route.getRoute().size() + " obstacles, ~" + clock((int) Math.round(route.routeTicks())))
                .rightColor(why != null ? HudColors.WARN : Color.WHITE).build());
        }
        if (config.arenaDebug()) {
            debug();
        }
    }

    // One-obstacle training: reps, rates and reps to your next level and goal
    private void trainingLines() {
        if (agility.inArena()) {
            add("Training", reps.getName());
        }
        add("Reps", String.valueOf(reps.getReps()));
        if (reps.repsPerHour() > 0) {
            add("Reps/hr", String.format("%,.0f", reps.repsPerHour()));
            add("XP/hr", BradsMultiToolPanel.shortNumber((long) (reps.repsPerHour() * reps.getRepXp())));
        }
        int xp = client.getSkillExperience(Skill.AGILITY);
        int level = Experience.getLevelForXp(xp);
        if (level < Experience.MAX_REAL_LEVEL && reps.repsTo(Experience.getXpForLevel(level + 1)) > 0) {
            add("Reps to " + (level + 1), String.format("%,d", reps.repsTo(Experience.getXpForLevel(level + 1))));
        }
        int goal = plugin.getSkillGoal(Skill.AGILITY);
        if (goal > xp && reps.repsTo(goal) > 0) {
            add("Reps to goal", String.format("%,d", reps.repsTo(goal)));
        }
    }

    // The route readout: what the scene scan found, and the route's steps
    private void debug() {
        int safe = 0;
        int mayFail = 0;
        int tooLow = 0;
        for (SkillHelperData.ArenaObstacle obstacle : route.getObstacles().values()) {
            ArenaRoute.Status status = route.status(obstacle);
            safe += status == ArenaRoute.Status.SAFE ? 1 : 0;
            mayFail += status == ArenaRoute.Status.MAY_FAIL ? 1 : 0;
            tooLow += status == ArenaRoute.Status.TOO_LOW ? 1 : 0;
        }
        add("Obstacle objects", route.getObstacles().size() + " (" + route.getUnlinked() + " unlinked)");
        add("Safe/may fail/too low", safe + "/" + mayFail + "/" + tooLow);
        add("Links", String.valueOf(route.getLinks().size()));
        add("From (pillar nearest you)", point(route.getStart()));
        add("To (hint arrow)", point(route.getTarget()));
        if (reps.getName() != null) {
            add("Last obstacle", reps.getName() + " x" + reps.getReps());
        }
        WorldPoint at = route.getStart();
        for (ArenaRoute.Link link : route.getRoute()) {
            at = link.other(at);
            add(" " + link.obstacle.name, "to " + point(at));
        }
    }

    private void add(String left, String right) {
        panel.getChildren().add(LineComponent.builder().left(left).right(right).build());
    }

    private static String point(WorldPoint p) {
        return p == null ? "-" : p.getX() + "," + p.getY();
    }

    // The course: laps, rates, lap time, laps to level and goal, marks
    private void courseCard() {
        panel.getChildren().add(TitleComponent.builder().text(agility.getCourse()).color(TITLE).build());
        panel.getChildren().add(LineComponent.builder().left("Laps").right(String.valueOf(agility.getLaps())).build());
        if (agility.lapsPerHour() > 0) {
            panel.getChildren().add(LineComponent.builder().left("Laps/hr").right(String.format("%.0f", agility.lapsPerHour())).build());
            if (agility.getXpPerLap() > 0) {
                panel.getChildren().add(LineComponent.builder().left("XP/hr")
                    .right(BradsMultiToolPanel.shortNumber((long) (agility.lapsPerHour() * agility.getXpPerLap()))).build());
            }
            panel.getChildren().add(LineComponent.builder().left("Lap time").right(clock((int) Math.round(agility.ticksPerLap()))).build());
        }
        int xp = client.getSkillExperience(Skill.AGILITY);
        int level = Experience.getLevelForXp(xp);
        if (level < Experience.MAX_REAL_LEVEL && agility.lapsTo(Experience.getXpForLevel(level + 1)) > 0) {
            panel.getChildren().add(LineComponent.builder().left("Laps to " + (level + 1))
                .right(String.format("%,d", agility.lapsTo(Experience.getXpForLevel(level + 1)))).build());
        }
        int goal = plugin.getSkillGoal(Skill.AGILITY);
        if (goal > xp && agility.lapsTo(goal) > 0) {
            panel.getChildren().add(LineComponent.builder().left("Laps to goal").right(String.format("%,d", agility.lapsTo(goal))).build());
        }
        if (agility.getMarks() > 0) {
            panel.getChildren().add(LineComponent.builder().left("Marks of grace").right(String.valueOf(agility.getMarks())).build());
        }
    }

    /** Ticks as m:ss. */
    static String clock(int ticks) {
        int seconds = (int) Math.round(ticks * 0.6);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
