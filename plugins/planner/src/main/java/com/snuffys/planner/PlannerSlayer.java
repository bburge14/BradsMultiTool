package com.snuffys.planner;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * The Planner's SLAYER list: your task, kills left, points and streak, read from the game's variables
 * ({@link SlayerVars}, as RuneLite's Slayer plugin reads them), so it works without the PvM/Slayer plugin. Saved
 * with your RuneScape profile so the list still shows while you're logged out.
 */
@Singleton
class PlannerSlayer {

    private static final String KEY = "planner.slayer";
    private static final int READ_TICKS = 2;

    /** The task as last read. */
    static final class Task {
        final String name;
        final int remaining;
        final int assigned;
        final String area;
        final int points;
        final int streak;

        Task(String name, int remaining, int assigned, String area, int points, int streak) {
            this.name = name;
            this.remaining = remaining;
            this.assigned = assigned;
            this.area = area;
            this.points = points;
            this.streak = streak;
        }

        String encode() {
            return clean(name) + "|" + remaining + "|" + assigned + "|" + clean(area) + "|" + points + "|" + streak;
        }

        private static String clean(String text) {
            return text == null ? "" : text.replace("|", "/");
        }

        static Task decode(String value) {
            String[] p = value == null ? new String[0] : value.split("\\|", -1);
            if (p.length < 6) {
                return null;
            }
            try {
                return new Task(p[0].isEmpty() ? null : p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    p[3].isEmpty() ? null : p[3], Integer.parseInt(p[4]), Integer.parseInt(p[5]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private final Client client;
    private final ConfigManager configManager;
    private final Alerts alerts;
    private final PlannerView view;
    // The task the name was looked up for (the lookup reads the game's tables, so only when it changes)
    private int nameFor = -1;
    private int areaFor = -1;
    private String name;
    private String area;

    @Inject
    PlannerSlayer(Client client, ConfigManager configManager, Alerts alerts, PlannerView view) {
        this.client = client;
        this.configManager = configManager;
        this.alerts = alerts;
        this.view = view;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN || !alerts.ready() || configManager.getRSProfileKey() == null
            || client.getTickCount() % READ_TICKS != 0) {
            return;
        }
        int remaining = SlayerVars.remaining(client);
        int id = SlayerVars.taskId(client);
        if (remaining > 0 && id != nameFor) {
            nameFor = id;
            name = SlayerVars.taskName(client, id);
        }
        int areaId = SlayerVars.areaId(client);
        if (areaId != areaFor) {
            areaFor = areaId;
            area = SlayerVars.area(client, areaId);
        }
        Task task = new Task(remaining > 0 ? name : null, Math.max(0, remaining), remaining > 0 ? SlayerVars.assigned(client) : 0,
            remaining > 0 ? area : null, SlayerVars.points(client), SlayerVars.streak(client));
        String encoded = task.encode();
        if (!encoded.equals(configManager.getRSProfileConfiguration(PlannerChores.GROUP, KEY))) {
            configManager.setRSProfileConfiguration(PlannerChores.GROUP, KEY, encoded);
            view.update();
        }
    }

    /** Your task as last seen, or null if never seen. */
    Task task() {
        return configManager.getRSProfileKey() == null ? null
            : Task.decode(configManager.getRSProfileConfiguration(PlannerChores.GROUP, KEY));
    }
}
