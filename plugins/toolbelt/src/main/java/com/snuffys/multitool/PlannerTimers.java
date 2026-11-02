package com.snuffys.multitool;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * The Planner's own timers (a name and a length you set; start, stop, and an alert when one ends) and the bird
 * houses ready alert (from the seeding times BirdhouseTracker keeps). Timers are saved with your RuneScape
 * profile, so one keeps running through a logout; one that ends while you're away is said when you're back.
 */
@Singleton
class PlannerTimers {

    private static final String GROUP = PlannerChores.GROUP;
    private static final String KEY = "planner.timers";

    /** One of your timers. */
    static final class Timer {
        String id;
        String name;
        long seconds;
        /** When it ends (epoch seconds), 0 while stopped. */
        long endsAt;
        /** The same to the millisecond, for the countdown shown (0 in timers saved before it was kept). */
        long endsAtMs;
        boolean alerted;
        /** Its own done sound ({@link PlannerConfig.TimerSound}'s name), null for the Ready Sound setting. */
        String sound;
        /** How many times its sound plays, 0 for the setting's. */
        int repeats;

        /** Seconds left (its whole length while stopped, 0 once ended). */
        long left(long now) {
            return endsAt == 0 ? seconds : Math.max(0, endsAt - now);
        }

        /** Seconds left at this moment (milliseconds), rounded up, so each second ticks over on time. */
        long leftNow(long nowMs) {
            if (endsAt == 0) {
                return seconds;
            }
            long end = endsAtMs > 0 ? endsAtMs : endsAt * 1000;
            return Math.max(0, (end - nowMs + 999) / 1000);
        }

        boolean running(long now) {
            return endsAt > 0 && now < endsAt;
        }

        boolean ended(long now) {
            return endsAt > 0 && now >= endsAt;
        }
    }

    private static final class Saved {
        List<Timer> timers = new ArrayList<>();
    }

    private final ConfigManager configManager;
    private final PlannerConfig config;
    private final Alerts alerts;
    private final BirdhouseTracker birdhouses;
    private final PlannerView view;
    private final Gson gson;

    @Inject
    PlannerTimers(ConfigManager configManager, PlannerConfig config, Alerts alerts, BirdhouseTracker birdhouses,
                  PlannerView view, Gson gson) {
        this.configManager = configManager;
        this.config = config;
        this.alerts = alerts;
        this.birdhouses = birdhouses;
        this.view = view;
        this.gson = gson;
    }

    @Inject
    private PlannerSound sound;

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!alerts.ready() || configManager.getRSProfileKey() == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        Saved saved = saved();
        boolean changed = false;
        for (Timer timer : saved.timers) {
            if (timer.ended(now) && !timer.alerted) {
                timer.alerted = true;
                changed = true;
                sound.done(VoiceAlertService.VoiceLine.TIMER_DONE, timer.name, timer.sound, timer.repeats);
            }
        }
        if (changed) {
            save(saved);
        }
        birdhouseAlert(now);
    }

    // Once every seeded bird house with a known time is full, say so once (until one is seeded again)
    private void birdhouseAlert(long now) {
        if (!config.birdhouseAlert() || configManager.getRSProfileConfiguration(GROUP, BirdhouseTracker.ALERTED_KEY) != null) {
            return;
        }
        long full = birdhouses.allFullAt();
        if (full > 0 && now >= full) {
            configManager.setRSProfileConfiguration(GROUP, BirdhouseTracker.ALERTED_KEY, "true");
            sound.done(VoiceAlertService.VoiceLine.BIRDHOUSES_READY, null);
            view.update();
        }
    }

    /** When your bird houses are all full (epoch seconds), or 0 when none is seeded (or seeding wasn't seen). */
    long birdhousesFullAt() {
        return configManager.getRSProfileKey() == null ? 0 : birdhouses.allFullAt();
    }

    List<Timer> timers() {
        return saved().timers;
    }

    void add(String name, long seconds, String soundName, int repeats) {
        Saved saved = saved();
        Timer timer = new Timer();
        timer.id = "t" + System.currentTimeMillis();
        timer.name = name;
        timer.seconds = seconds;
        timer.sound = soundName;
        timer.repeats = repeats;
        saved.timers.add(timer);
        save(saved);
    }

    /** Starts it (again from the top if it had ended). */
    void start(String id) {
        change(id, timer -> {
            long nowMs = System.currentTimeMillis();
            timer.endsAtMs = nowMs + timer.seconds * 1000;
            timer.endsAt = (timer.endsAtMs + 999) / 1000;
            timer.alerted = false;
        });
    }

    void stop(String id) {
        change(id, timer -> {
            timer.endsAt = 0;
            timer.endsAtMs = 0;
            timer.alerted = false;
        });
    }

    void edit(String id, String name, long seconds, String soundName, int repeats) {
        change(id, timer -> {
            timer.name = name;
            timer.seconds = seconds;
            timer.sound = soundName;
            timer.repeats = repeats;
        });
    }

    /** Plays a sound once to try it. */
    void preview(PlannerConfig.TimerSound pick) {
        sound.preview(pick);
    }

    /** The sound a timer plays (its own, else the Ready Sound setting). */
    PlannerConfig.TimerSound soundOf(Timer timer) {
        return sound.sound(timer.sound);
    }

    void delete(String id) {
        Saved saved = saved();
        saved.timers.removeIf(timer -> timer.id.equals(id));
        save(saved);
    }

    private void change(String id, java.util.function.Consumer<Timer> how) {
        Saved saved = saved();
        for (Timer timer : saved.timers) {
            if (timer.id.equals(id)) {
                how.accept(timer);
            }
        }
        save(saved);
    }

    private synchronized Saved saved() {
        String json = configManager.getRSProfileKey() == null ? null : configManager.getRSProfileConfiguration(GROUP, KEY);
        try {
            Saved saved = json == null ? null : gson.fromJson(json, Saved.class);
            if (saved != null && saved.timers != null) {
                saved.timers.removeIf(timer -> timer == null || timer.id == null || timer.name == null);
                return saved;
            }
        } catch (JsonSyntaxException e) {
            // Unreadable: start again
        }
        return new Saved();
    }

    private synchronized void save(Saved saved) {
        if (configManager.getRSProfileKey() != null) {
            configManager.setRSProfileConfiguration(GROUP, KEY, gson.toJson(saved));
        }
        view.update();
    }

    /** "1:05:09", "4:30". */
    static String clock(long seconds) {
        long h = seconds / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /**
     * A length as seconds; -1 if it isn't one: "1:30:00" (hours:minutes:seconds), "4:30" (minutes:seconds, as the
     * timers count down) or "90" (minutes).
     */
    static long parseLength(String text) {
        if (text == null) {
            return -1;
        }
        String t = text.trim();
        try {
            if (t.contains(":")) {
                String[] parts = t.split(":", -1);
                if (parts.length > 3) {
                    return -1;
                }
                long total = 0;
                for (int i = 0; i < parts.length; i++) {
                    long value = parts[i].trim().isEmpty() ? 0 : Long.parseLong(parts[i].trim());
                    // Minutes and seconds under 60 (the first part, hours or minutes, can be anything)
                    if (value < 0 || i > 0 && value >= 60) {
                        return -1;
                    }
                    total = total * 60 + value;
                }
                return total > 0 && total <= 30L * 24 * 3600 ? total : -1;
            }
            double minutes = Double.parseDouble(t);
            return minutes > 0 && minutes <= 60 * 24 * 30 ? Math.round(minutes * 60) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** A length as HH:MM:SS ("01:30:00"), for editing. */
    static String hms(long seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
    }
}
