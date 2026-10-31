package com.snuffys.skilltracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Snuffy's Skilling Helper: XP gained and rates per skill this session (auto-pausing idle skills), goals and
 * their suggested training, the pinned skill, level-up alerts, and the sidebar panel that shows it all.
 * Goals, pins, training targets and focus are saved per account (RuneScape profile settings).
 */
@Singleton
class SkillTracker {

    private static final int TRAINING_WINDOW_TICKS = 25;
    private static final int PIE_TRAINING_TICKS = 200;

    private final Client client;
    private final SkillTrackerConfig config;
    private final ConfigManager configManager;
    private final PluginIdentity identity;
    private final Alerts alerts;
    private final ClientToolbar clientToolbar;
    private final SkillIconManager skillIconManager;
    private final TrainingData trainingData;
    private final SlayerData slayerData;
    // These two look the tracker up in turn, so they're filled in after it's made (a constructor cycle hangs
    // RuneLite's Guice instead of failing) and fetched when first needed
    @Inject
    private Provider<TrainingAdvisor> trainingAdvisor;
    @Inject
    private Provider<GoalItems> goalItems;
    @Inject
    private KillTracker killTracker;
    @Inject
    private Provider<MethodDetector> methodDetector;

    private final Map<Skill, Integer> baseSkillXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> gainedSkillXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> realLevels = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> lastXpTicks = new EnumMap<>(Skill.class);
    private final Map<Skill, Long> skillActiveTicks = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> lastXpDrops = new EnumMap<>(Skill.class);
    /** Each skill's last XP drops, newest last, for an average when one method gives different XP (trout and salmon). */
    private final Map<Skill, java.util.ArrayDeque<Integer>> recentXpDrops = new EnumMap<>(Skill.class);
    static final int RECENT_DROPS = 30;
    private final Set<Skill> pausedSkills = EnumSet.noneOf(Skill.class);
    private volatile int combatLevel = 3;
    private NavigationButton navButton;
    private BradsMultiToolPanel panel;

    @Inject
    SkillTracker(Client client, SkillTrackerConfig config, ConfigManager configManager, PluginIdentity identity, Alerts alerts,
                 ClientToolbar clientToolbar, SkillIconManager skillIconManager, TrainingData trainingData, SlayerData slayerData) {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.identity = identity;
        this.alerts = alerts;
        this.clientToolbar = clientToolbar;
        this.skillIconManager = skillIconManager;
        this.trainingData = trainingData;
        this.slayerData = slayerData;
    }

    /** Adds the tracker to the sidebar. */
    void startUp() {
        panel = new BradsMultiToolPanel(this, config, skillIconManager);
        navButton = NavigationButton.builder()
            .tooltip("Snuffy's Skilling Helper")
            // The Skill Tracker's logo (also in the all-in-one: the tab is the tracker), made by update_icons.py
            .icon(ImageUtil.loadImageResource(SkillTracker.class, "icons/skilltracker.png"))
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);
    }

    void shutDown() {
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
        }
        pausedSkills.clear();
    }

    /** The panel's Reset all skills. */
    public void resetSession() {
        killTracker.reset();
        baseSkillXp.clear();
        gainedSkillXp.clear();
        skillActiveTicks.clear();
        lastXpDrops.clear();
        recentXpDrops.clear();
        pausedSkills.clear();
        updatePanelUI();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            realLevels.clear();
        }
        if (event.getGameState() != GameState.LOGGED_IN) {
            updatePanelUI();
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        Player player = client.getLocalPlayer();
        if (player != null) {
            combatLevel = player.getCombatLevel();
        }
        for (Skill s : gainedSkillXp.keySet()) {
            if (gainedSkillXp.get(s) > 0 && !isSkillPaused(s) && !isSkillIgnored(s)) {
                long current = skillActiveTicks.getOrDefault(s, 0L);
                skillActiveTicks.put(s, current + 1);
            }
        }
        updatePanelUI();
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        Skill skill = event.getSkill();
        int currentXp = event.getXp();

        announceLevelUp(skill, currentXp);

        if (!baseSkillXp.containsKey(skill)) {
            baseSkillXp.put(skill, currentXp);
            return;
        }

        int gained = currentXp - baseSkillXp.get(skill);
        if (gained > 0) {
            int previousGained = gainedSkillXp.getOrDefault(skill, 0);
            int drop = gained - previousGained;
            if (drop > 0) {
                lastXpDrops.put(skill, drop);
                java.util.ArrayDeque<Integer> recent = recentXpDrops.computeIfAbsent(skill, k -> new java.util.ArrayDeque<>());
                recent.addLast(drop);
                while (recent.size() > RECENT_DROPS) {
                    recent.removeFirst();
                }
                lastXpTicks.put(skill, client.getTickCount());
            }
            gainedSkillXp.put(skill, gained);
            updatePanelUI();
        }
    }

    // Goals are kept with your RuneScape profile, which RuneLite only knows a moment after login (and
    // which changes when synced settings arrive), so redraw the tracker then
    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        updatePanelUI();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!identity.configGroup.equals(event.getGroup())) {
            return;
        }

        String key = event.getKey();
        if (key.startsWith("showTracker") || key.startsWith("tracker") || key.startsWith("goal") || "showXpPerMinute".equals(key) || key.startsWith("autoPause")) {
            updatePanelUI();
        }
    }

    public boolean isSkillIgnored(Skill skill) {
        String ignored = config.ignoredSkills();
        if (ignored == null || ignored.trim().isEmpty()) {
            return false;
        }
        String skillName = skill.getName().toLowerCase();
        for (String part : ignored.split(",")) {
            if (part.trim().toLowerCase().equals(skillName)) {
                return true;
            }
        }
        return false;
    }

    public void addIgnoredSkill(Skill skill) {
        String raw = config.ignoredSkills();
        Set<String> set = new HashSet<>();
        if (raw != null && !raw.trim().isEmpty()) {
            set = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        }
        set.add(skill.getName());
        configManager.setConfiguration(identity.configGroup, "ignoredSkills", String.join(", ", set));
        updatePanelUI();
    }

    public void resetSkillRate(Skill skill) {
        int currentXp = client.getSkillExperience(skill);
        baseSkillXp.put(skill, currentXp);
        gainedSkillXp.put(skill, 0);
        skillActiveTicks.put(skill, 0L);
        updatePanelUI();
    }

    public void toggleSkillPause(Skill skill) {
        if (pausedSkills.contains(skill)) {
            pausedSkills.remove(skill);
        } else {
            pausedSkills.add(skill);
        }
        updatePanelUI();
    }

    /** Paused by you, or auto-paused after going without XP in the skill for a while. */
    public boolean isSkillPaused(Skill skill) {
        return pausedSkills.contains(skill) || isSkillAutoPaused(skill);
    }

    /** No XP in the skill for the auto-pause time: the rate stops counting until you gain XP again. */
    public boolean isSkillAutoPaused(Skill skill) {
        if (!config.autoPauseSkills() || pausedSkills.contains(skill)) {
            return false;
        }
        Integer last = lastXpTicks.get(skill);
        return last != null && client.getTickCount() - last > config.autoPauseSeconds() / 0.6;
    }

    /** The skill's goal in XP, or 0 for none. */
    public int getSkillGoal(Skill skill) {
        String value = configManager.getRSProfileConfiguration(identity.configGroup, "goal." + skill.name());
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setSkillGoal(Skill skill, int goalXp) {
        if (goalXp <= 0) {
            configManager.unsetRSProfileConfiguration(identity.configGroup, "goal." + skill.name());
            configManager.unsetRSProfileConfiguration(identity.configGroup, "goalStart." + skill.name());
            configManager.unsetRSProfileConfiguration(identity.configGroup, "goalTarget." + skill.name());
        } else {
            // Modifying a goal still underway keeps where it started (the goal bar's progress)
            int xp = client.getSkillExperience(skill);
            boolean underway = getSkillGoal(skill) > xp && getSkillGoalStart(skill) > 0;
            configManager.setRSProfileConfiguration(identity.configGroup, "goal." + skill.name(), goalXp);
            if (!underway) {
                configManager.setRSProfileConfiguration(identity.configGroup, "goalStart." + skill.name(), xp);
            }
        }
        updatePanelUI();
    }

    /** What to train for a goal, by the Training Focus setting. */
    public TrainingAdvisor getTrainingAdvisor() {
        return trainingAdvisor.get();
    }

    /** The wiki's training methods by level, for the tracker's goal suggestions. */
    public TrainingData getTrainingData() {
        return trainingData;
    }

    /** Your combat level as of the last game tick (safe to read from the panel). */
    public int getCombatLevel() {
        return combatLevel;
    }

    public int getCurrentSkillLevel(Skill skill) {
        return Experience.getLevelForXp(client.getSkillExperience(skill));
    }

    public SlayerData getSlayerData() {
        return slayerData;
    }

    /** What to make for production-skill goals, from your inventory and bank. */
    /** Your recent kills, for combat goals' kill rate. */
    public KillTracker getKillTracker() {
        return killTracker;
    }

    public MethodDetector getMethodDetector() {
        return methodDetector.get();
    }

    public GoalItems getGoalItems() {
        return goalItems.get();
    }

    /**
     * What you chose to train for this skill's goal instead of the suggestion ("npc:ruby harvest",
     * "object:Teak tree", see {@link GoalTarget}), or null to follow the suggestion.
     */
    public String getGoalTarget(Skill skill) {
        return configManager.getRSProfileKey() == null ? null
            : configManager.getRSProfileConfiguration(identity.configGroup, "goalTarget." + skill.name());
    }

    public void setGoalTarget(Skill skill, String target) {
        if (target == null) {
            configManager.unsetRSProfileConfiguration(identity.configGroup, "goalTarget." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(identity.configGroup, "goalTarget." + skill.name(), target);
        }
        updatePanelUI();
    }

    /**
     * How you train Prayer with bones (bury, or which altar) or with blessed bone shards (which wine), as
     * picked from an item's "Track for goal" menu; null if not picked (bury; shards by the wine you carry).
     */
    public PrayerMethod getPrayerMethod(boolean shards) {
        return configManager.getRSProfileKey() == null ? null
            : PrayerMethod.parse(configManager.getRSProfileConfiguration(identity.configGroup, prayerMethodKey(shards)));
    }

    public void setPrayerMethod(PrayerMethod method) {
        configManager.setRSProfileConfiguration(identity.configGroup, prayerMethodKey(method.shards), method.name());
        updatePanelUI();
    }

    private static String prayerMethodKey(boolean shards) {
        return "prayerMethod." + (shards ? "shards" : "bones");
    }

    /** Your "Track for goal" pick as shown on the cards ("Dragon bones (Gilded altar)"), or null. */
    public String describeGoalTarget(Skill skill) {
        return describeTarget(skill, getGoalTarget(skill));
    }

    /** A "Track for goal" pick as the cards show it ("Fly fishing: Trout, Salmon (Fly fishing rod + Feather)"). */
    public String describeTarget(Skill skill, String target) {
        if (target == null) {
            return null;
        }
        if (target.startsWith(GoalTarget.FISH)) {
            return getTrainingAdvisor().describeFish(target);
        }
        String recipe = GoalTarget.recipeName(target);
        if (recipe != null) {
            return recipe + " (from " + goalTargetName(":" + GoalTarget.itemName(target)) + ")";
        }
        String name = goalTargetName(target);
        if (skill == Skill.PRAYER && target.startsWith("item:")) {
            PrayerMethod method = getGoalItems().prayerMethod(getGoalItems().suggestion(skill));
            if (method != null) {
                name += " (" + method.label + ")";
            }
        }
        return name;
    }

    /** What this skill's goal suggestions aim for: its own choice, else the Training Focus setting. */
    public SkillTrackerConfig.TrainingFocus getTrainingFocus(Skill skill) {
        SkillTrackerConfig.TrainingFocus own = getOwnTrainingFocus(skill);
        return own != null ? own : config.trainingFocus();
    }

    /** The focus picked for this skill, or null when it follows the default. */
    public SkillTrackerConfig.TrainingFocus getOwnTrainingFocus(Skill skill) {
        String value = configManager.getRSProfileKey() == null ? null
            : configManager.getRSProfileConfiguration(identity.configGroup, "trainingFocus." + skill.name());
        if (value == null) {
            return null;
        }
        try {
            return SkillTrackerConfig.TrainingFocus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTrainingFocus(Skill skill, SkillTrackerConfig.TrainingFocus focus) {
        if (focus == null) {
            configManager.unsetRSProfileConfiguration(identity.configGroup, "trainingFocus." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(identity.configGroup, "trainingFocus." + skill.name(), focus.name());
        }
        updatePanelUI();
    }

    /** "Ruby harvest" from "npc:ruby harvest". */
    static String goalTargetName(String target) {
        String name = target.substring(target.indexOf(':') + 1);
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** The one skill pinned to the game screen from the tracker, or null. Kept across restarts. */
    public Skill getPinnedSkill() {
        String name = configManager.getConfiguration(identity.configGroup, "pinnedSkill");
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Skill.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Pin a skill to the screen (replacing any other), or unpin with null. */
    public void setPinnedSkill(Skill skill) {
        if (skill == null) {
            configManager.unsetConfiguration(identity.configGroup, "pinnedSkill");
        } else {
            configManager.setConfiguration(identity.configGroup, "pinnedSkill", skill.name());
        }
        updatePanelUI();
    }

    /** Your XP in the skill when you set its goal (for the goal bar), or 0 if unknown. */
    public int getSkillGoalStart(Skill skill) {
        String value = configManager.getRSProfileConfiguration(identity.configGroup, "goalStart." + skill.name());
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void resetSkill(Skill skill) {
        baseSkillXp.remove(skill);
        gainedSkillXp.remove(skill);
        skillActiveTicks.remove(skill);
        lastXpDrops.remove(skill);
        recentXpDrops.remove(skill);
        pausedSkills.remove(skill);
        updatePanelUI();
    }

    // "Woodcutting level 45!" when a real level goes up (levels already reached at login aren't announced)
    private void announceLevelUp(Skill skill, int xp) {
        int level = Experience.getLevelForXp(xp);
        Integer previous = realLevels.put(skill, level);
        if (previous == null || level <= previous || !alerts.ready()) {
            return;
        }
        alerts.speakClips(AlertType.LEVEL_UP, VoiceAlertService.levelUpClips(skill, level));
        alerts.chat(AlertType.LEVEL_UP, skill.getName() + " level " + level + "!");
        announceNewMethod(skill, previous, level);
    }

    // A level-up that moves a goal skill onto a different suggested method says so in chat
    private void announceNewMethod(Skill skill, int before, int now) {
        if (!config.showTrainingSuggestion() || getSkillGoal(skill) <= client.getSkillExperience(skill)
            || getGoalTarget(skill) != null) {
            return;
        }
        if (skill == Skill.SLAYER) {
            SlayerData.Master was = slayerData.bestMaster(combatLevel, before);
            SlayerData.Master master = slayerData.bestMaster(combatLevel, now);
            if (master != null && master != was) {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName + "]</col> "
                    + "Slayer goal: you can now get tasks from " + master.name, null);
            }
            return;
        }
        TrainingAdvisor.Suggestion was = trainingAdvisor.get().suggest(skill, before);
        TrainingAdvisor.Suggestion suggestion = trainingAdvisor.get().suggest(skill, now);
        if (suggestion != null && !suggestion.equals(was)) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName + "]</col> "
                + skill.getName() + " goal: you can now switch to " + suggestion.text, null);
        }
    }

    void updatePanelUI() {
        if (panel != null && panelUpdateQueued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                panelUpdateQueued.set(false);
                panel.updatePanel();
            });
        }
    }

    public int getCurrentSkillXp(Skill skill) {
        return client.getSkillExperience(skill);
    }

    public List<Skill> getTrackedSkills() {
        List<Skill> skills = new ArrayList<>();
        for (Map.Entry<Skill, Integer> entry : gainedSkillXp.entrySet()) {
            if (entry.getValue() > 0 && !isSkillIgnored(entry.getKey())) {
                skills.add(entry.getKey());
            }
        }
        return skills;
    }

    /** True while you're training the skill: XP from it in the last 15 seconds. */
    /**
     * An XP drop in the skill in the last two minutes: long enough to cover slow trees and walking between
     * resources, for showing that skill's timer pies only while you train it.
     */
    boolean trainedRecently(Skill skill) {
        Integer last = lastXpTicks.get(skill);
        return last != null && client.getTickCount() - last <= PIE_TRAINING_TICKS;
    }

    boolean isTraining(Skill skill) {
        Integer last = lastXpTicks.get(skill);
        return last != null && client.getTickCount() - last <= TRAINING_WINDOW_TICKS;
    }

    public int getLastXpDrop(Skill skill) {
        return lastXpDrops.getOrDefault(skill, 0);
    }

    /**
     * The average of your last XP drops in a skill (up to {@link #RECENT_DROPS}), 0 before the first: steadier
     * than the last drop when one method gives different XP each time (trout and salmon, 17 and 18 for 17.5 XP).
     */
    public double getAverageXpDrop(Skill skill) {
        java.util.ArrayDeque<Integer> recent = recentXpDrops.get(skill);
        return recent == null || recent.isEmpty() ? 0 : averageOf(recent);
    }

    /** How many XP drops {@link #getAverageXpDrop} covers. */
    public int getAverageXpDropCount(Skill skill) {
        java.util.ArrayDeque<Integer> recent = recentXpDrops.get(skill);
        return recent == null ? 0 : recent.size();
    }

    static double averageOf(Iterable<Integer> drops) {
        long total = 0;
        int count = 0;
        for (int drop : drops) {
            total += drop;
            count++;
        }
        return count == 0 ? 0 : (double) total / count;
    }

    public int getSkillGained(Skill skill) {
        return gainedSkillXp.getOrDefault(skill, 0);
    }

    public int getSkillXpPerHour(Skill skill) {
        int gained = getSkillGained(skill);
        if (gained <= 0) return 0;
        long ticks = skillActiveTicks.getOrDefault(skill, 0L);
        if (ticks < 8) return 0;
        long elapsedSeconds = (long) (ticks * 0.6);
        return (int) ((gained * 3600L) / elapsedSeconds);
    }

    public int getSkillXpPerMinute(Skill skill) {
        int gained = getSkillGained(skill);
        if (gained <= 0) return 0;
        long ticks = skillActiveTicks.getOrDefault(skill, 0L);
        if (ticks < 8) return 0;
        long elapsedSeconds = (long) (ticks * 0.6);
        return (int) ((gained * 60L) / elapsedSeconds);
    }

    public int getTotalCombatXp() {
        int total = 0;
        for (Map.Entry<Skill, Integer> entry : gainedSkillXp.entrySet()) {
            if (!isSkillIgnored(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }

    /** Skills you've set a goal for, in skill order. */
    public List<Skill> getGoalSkills() {
        List<Skill> skills = new ArrayList<>();
        if (configManager.getRSProfileKey() == null) {
            return skills;
        }
        for (Skill skill : Skill.values()) {
            if (!"OVERALL".equals(skill.name()) && getSkillGoal(skill) > 0) {
                skills.add(skill);
            }
        }
        return skills;
    }

    // Combat asks for an update on every hit; queue at most one at a time
    private final java.util.concurrent.atomic.AtomicBoolean panelUpdateQueued = new java.util.concurrent.atomic.AtomicBoolean();
}
