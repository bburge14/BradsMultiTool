package com.bradsmultitool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.util.Text;

/**
 * Kill-count goals per monster ("kill 200 Gargoyles"): set from a monster's right-click menu (Set kill goal, which
 * asks for the number in the chat box), shown on the monster hover card and the Slayer card, and said when reached.
 * A monster you hit that dies soon after counts as your kill. Saved with your RuneScape profile. Only changes what
 * we show: the menu entry is RuneLite's own kind (like NPC Indicators' Tag) and never acts in game.
 */
@Singleton
class KillGoals {

    private static final String SET = "Set kill goal";
    private static final String CLEAR = "Clear kill goal";
    private static final String KEY = "killGoals";
    // A monster you hit in the last 30 seconds that dies is your kill
    private static final int HIT_MEMORY_TICKS = 50;
    private static final int MAX_TARGET = 1_000_000;

    /** One goal: the monster's name as shown in game, how many to kill, and how many you have since setting it. */
    static final class Goal {
        final String monster;
        final int target;
        int kills;

        Goal(String monster, int target, int kills) {
            this.monster = monster;
            this.target = target;
            this.kills = kills;
        }

        boolean done() {
            return kills >= target;
        }

        /** "37 / 200", or "200 / 200 (done)". */
        String progress() {
            return Math.min(kills, target) + " / " + target + (done() ? " (done)" : "");
        }
    }

    private final Client client;
    private final PvmConfig config;
    private final ConfigManager configManager;
    private final PluginIdentity identity;
    private final Alerts alerts;
    private final ChatboxPanelManager chatbox;
    // Lower-case name -> goal, in the order they were set
    private final Map<String, Goal> goals = new LinkedHashMap<>();
    private final Map<NPC, Integer> lastHit = new HashMap<>();
    private boolean loaded;

    @Inject
    KillGoals(Client client, PvmConfig config, ConfigManager configManager, PluginIdentity identity, Alerts alerts,
              ChatboxPanelManager chatbox) {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.identity = identity;
        this.alerts = alerts;
        this.chatbox = chatbox;
    }

    /** The goal for a monster (by name), or null. */
    synchronized Goal goal(String monster) {
        return monster == null ? null : goals.get(monster.toLowerCase());
    }

    /** Every goal, unfinished ones first. */
    synchronized List<Goal> all() {
        List<Goal> out = new ArrayList<>();
        goals.values().stream().filter(g -> !g.done()).forEach(out::add);
        goals.values().stream().filter(Goal::done).forEach(out::add);
        return out;
    }

    synchronized void set(String monster, int target) {
        goals.put(monster.toLowerCase(), new Goal(monster, target, 0));
        save();
    }

    synchronized void clear(String monster) {
        goals.remove(monster.toLowerCase());
        save();
    }

    /** The Slayer card's "Clear finished kill goals". */
    synchronized void clearFinished() {
        goals.values().removeIf(Goal::done);
        save();
    }

    // Next to Examine on anything you can attack, like Track for goal
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        MenuEntry entry = event.getMenuEntry();
        if (!config.killGoals() || entry.getType() != MenuAction.EXAMINE_NPC || entry.getNpc() == null
            || !attackable(entry.getNpc())) {
            return;
        }
        String name = Text.removeTags(entry.getNpc().getName());
        boolean has = goal(name) != null;
        client.getMenu().createMenuEntry(-1)
            .setOption(has ? CLEAR : SET)
            .setTarget(event.getTarget())
            .setType(MenuAction.RUNELITE)
            .onClick(e -> {
                if (has) {
                    clear(name);
                } else {
                    ask(name);
                }
            });
    }

    // The number, typed in the chat box (RuneLite's own input, like Bank Tags')
    private void ask(String monster) {
        chatbox.openTextInput("Kill goal for " + monster + " (how many kills):")
            .charValidator(c -> c >= '0' && c <= '9')
            .onDone((Consumer<String>) text -> {
                int target = parseTarget(text);
                if (target > 0) {
                    set(monster, target);
                }
            })
            .build();
    }

    /** The typed number (1 to a million), or 0 when it isn't one. */
    static int parseTarget(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 && value <= MAX_TARGET ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean attackable(NPC npc) {
        NPCComposition comp = npc.getTransformedComposition() != null ? npc.getTransformedComposition() : npc.getComposition();
        if (comp == null || comp.getActions() == null || npc.getName() == null) {
            return false;
        }
        for (String action : comp.getActions()) {
            if ("Attack".equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getHitsplat().isMine() && event.getActor() instanceof NPC) {
            lastHit.put((NPC) event.getActor(), client.getTickCount());
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (!(event.getActor() instanceof NPC)) {
            return;
        }
        NPC npc = (NPC) event.getActor();
        Integer hit = lastHit.remove(npc);
        if (hit == null || client.getTickCount() - hit > HIT_MEMORY_TICKS) {
            return;
        }
        Goal reached = countKill(npc.getName());
        if (reached != null) {
            alerts.alert(VoiceAlertService.VoiceLine.KILL_GOAL_REACHED, reached.target + " " + reached.monster);
        }
    }

    /** Adds a kill to the monster's goal; the goal when this kill reaches it, else null. */
    synchronized Goal countKill(String monster) {
        Goal goal = goal(monster);
        if (goal == null || goal.done()) {
            return null;
        }
        goal.kills++;
        save();
        return goal.done() ? goal : null;
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        lastHit.remove(event.getNpc());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            lastHit.clear();
        }
        if (event.getGameState() == GameState.LOGGED_IN && !loaded) {
            load();
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        load();
    }

    private synchronized void load() {
        goals.clear();
        String saved = configManager.getRSProfileConfiguration(identity.configGroup, KEY);
        loaded = configManager.getRSProfileKey() != null;
        goals.putAll(decode(saved));
    }

    private void save() {
        if (configManager.getRSProfileKey() == null) {
            return;
        }
        if (goals.isEmpty()) {
            configManager.unsetRSProfileConfiguration(identity.configGroup, KEY);
        } else {
            configManager.setRSProfileConfiguration(identity.configGroup, KEY, encode(goals.values()));
        }
    }

    /** "Gargoyle|200|37;Lizard|100|0": each goal's name, target and kills. */
    static String encode(Iterable<Goal> goals) {
        StringBuilder out = new StringBuilder();
        for (Goal goal : goals) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(goal.monster.replaceAll("[|;]", "")).append('|').append(goal.target).append('|').append(goal.kills);
        }
        return out.toString();
    }

    static Map<String, Goal> decode(String saved) {
        Map<String, Goal> out = new LinkedHashMap<>();
        if (saved == null || saved.isEmpty()) {
            return out;
        }
        for (String part : saved.split(";")) {
            String[] fields = part.split("\\|");
            if (fields.length != 3) {
                continue;
            }
            try {
                Goal goal = new Goal(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2]));
                if (goal.target > 0 && !goal.monster.isEmpty()) {
                    out.put(goal.monster.toLowerCase(), goal);
                }
            } catch (NumberFormatException e) {
                // A damaged entry: skipped
            }
        }
        return out;
    }
}
