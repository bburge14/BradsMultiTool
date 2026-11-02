package com.snuffys.multitool;

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
import net.runelite.api.coords.WorldPoint;
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
    @Inject
    private Provider<HunterRumours> hunterRumours;


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
    // Logs, fish, ore, food cooked and burnt this session (from chat), shown with their rate on the skill's card
    private final Map<ItemsGathered.Kind, Integer> itemCounts = new java.util.concurrent.ConcurrentHashMap<>();
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
        // Turned on while logged in: the profile's already known, so no profile change comes to move old goals
        moveToSharedGroup();
    }

    void shutDown() {
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
        }
        if (client.getGameState() == GameState.LOGGED_IN) {
            saveSession(true);
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
        itemCounts.clear();
        // Not brought back after a restart either
        if (configManager.getRSProfileKey() != null) {
            configManager.unsetRSProfileConfiguration(identity.configGroup, SESSION_KEY);
        }
        savedSession = null;
        updatePanelUI();
    }

    @Subscribe
    public void onChatMessage(net.runelite.api.events.ChatMessage event) {
        ItemsGathered.Kind kind = ItemsGathered.of(event.getType(), event.getMessage());
        if (kind != null && !isSkillIgnored(kind.skill)) {
            itemCounts.merge(kind, 1, Integer::sum);
        }
    }

    /**
     * The skill's items this session, as card lines: {"Logs", "342 (1,050/hr)"}; Cooking's burnt food goes with
     * what's cooked ("120 (1,400/hr), 4 burnt (3%)"). Empty for skills with nothing counted.
     */
    public List<String[]> itemLines(Skill skill) {
        List<String[]> lines = new ArrayList<>();
        long seconds = (long) (skillActiveTicks.getOrDefault(skill, 0L) * 0.6);
        int burnt = itemCounts.getOrDefault(ItemsGathered.Kind.BURNT, 0);
        for (ItemsGathered.Kind kind : ItemsGathered.Kind.values()) {
            int count = itemCounts.getOrDefault(kind, 0);
            if (kind.skill != skill || kind == ItemsGathered.Kind.BURNT || count == 0 && !(kind == ItemsGathered.Kind.COOKED && burnt > 0)) {
                continue;
            }
            String value = ItemsGathered.countAndRate(count, seconds);
            if (kind == ItemsGathered.Kind.COOKED && burnt > 0) {
                value += ", " + String.format("%,d", burnt) + " burnt (" + Math.round(100.0 * burnt / (count + burnt)) + "%)";
            }
            lines.add(new String[]{kind.label, value});
        }
        return lines;
    }

    private void clearItems(Skill skill) {
        itemCounts.keySet().removeIf(kind -> kind.skill == skill);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            realLevels.clear();
            // Logged out: the session as of now (if the profile is still known)
            saveSession(true);
        }
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            reachedChecked = false;
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
            noteWhatYoureDoing(player);
        }
        // Once a login, when your stats and profile are both in: goals reached while away clear
        if (!reachedChecked && configManager.getRSProfileKey() != null && client.getSkillExperience(Skill.HITPOINTS) > 0) {
            reachedChecked = true;
            for (Skill skill : getGoalSkills()) {
                clearIfReached(skill, client.getSkillExperience(skill));
            }
            restoreSession();
        }
        // Kept with your profile every half a minute, for Keep Session After Restart
        if (reachedChecked && client.getTickCount() % SAVE_TICKS == 0) {
            saveSession();
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
        clearIfReached(skill, currentXp);

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
                // By game tick (600ms), so skills gaining XP on the same tick (Attack and Hitpoints) tie
                lastXpMillis.put(skill, System.currentTimeMillis() / 600 * 600);
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

    /**
     * Where goals, their Track for goal picks, Suggest choices, Prayer methods and the pinned skill are saved: one
     * place for the Toolbelt and the Skilling Helper alike, so either shows the same goals. (The Toolbelt's old
     * group, so its goals stay where they were; bird houses and compost are kept there too.)
     */
    static final String SHARED_GROUP = "bradsmultitool";
    private static final String[] SHARED_PREFIXES = {"goal.", "goalStart.", "goalTarget.", "goalMethod.", "trainingFocus.", "prayerMethod."};

    // Goals are kept with your RuneScape profile, which RuneLite only knows a moment after login (and
    // which changes when synced settings arrive), so redraw the tracker then
    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        moveToSharedGroup();
        // Goals reached while away (or before this setting) clear now
        for (Skill skill : getGoalSkills()) {
            int xp = client.getSkillExperience(skill);
            if (xp > 0) {
                clearIfReached(skill, xp);
            }
        }
        updatePanelUI();
    }

    // Goals the Skilling Helper saved under its own group before they were shared: moved to the shared group
    // (unless that already has one), then removed, so they're never copied back over a goal you change later
    private void moveToSharedGroup() {
        String profile = configManager.getRSProfileKey();
        if (profile == null || SHARED_GROUP.equals(identity.configGroup)) {
            return;
        }
        for (String prefix : SHARED_PREFIXES) {
            for (String key : configManager.getRSProfileConfigurationKeys(identity.configGroup, profile, prefix)) {
                String value = configManager.getRSProfileConfiguration(identity.configGroup, key);
                if (value != null && configManager.getRSProfileConfiguration(SHARED_GROUP, key) == null) {
                    configManager.setRSProfileConfiguration(SHARED_GROUP, key, value);
                }
                configManager.unsetRSProfileConfiguration(identity.configGroup, key);
            }
        }
        String pinned = configManager.getConfiguration(identity.configGroup, "pinnedSkill");
        if (pinned != null) {
            if (configManager.getConfiguration(SHARED_GROUP, "pinnedSkill") == null) {
                configManager.setConfiguration(SHARED_GROUP, "pinnedSkill", pinned);
            }
            configManager.unsetConfiguration(identity.configGroup, "pinnedSkill");
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!identity.configGroup.equals(event.getGroup()) && !SHARED_GROUP.equals(event.getGroup())) {
            return;
        }

        String key = event.getKey();
        if (key.startsWith("showTracker") || key.startsWith("tracker") || key.startsWith("goal") || "showXpPerMinute".equals(key)
            || key.startsWith("autoPause") || key.startsWith("rumour")) {
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
        clearItems(skill);
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
        String value = configManager.getRSProfileConfiguration(SHARED_GROUP, "goal." + skill.name());
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setSkillGoal(Skill skill, int goalXp) {
        if (goalXp <= 0) {
            configManager.unsetRSProfileConfiguration(SHARED_GROUP, "goal." + skill.name());
            configManager.unsetRSProfileConfiguration(SHARED_GROUP, "goalStart." + skill.name());
            configManager.unsetRSProfileConfiguration(SHARED_GROUP, "goalTarget." + skill.name());
        } else {
            // Modifying a goal still underway keeps where it started (the goal bar's progress)
            int xp = client.getSkillExperience(skill);
            boolean underway = getSkillGoal(skill) > xp && getSkillGoalStart(skill) > 0;
            configManager.setRSProfileConfiguration(SHARED_GROUP, "goal." + skill.name(), goalXp);
            if (!underway) {
                configManager.setRSProfileConfiguration(SHARED_GROUP, "goalStart." + skill.name(), xp);
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


    HunterRumours getHunterRumours() {
        return hunterRumours.get();
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
            : configManager.getRSProfileConfiguration(SHARED_GROUP, "goalTarget." + skill.name());
    }

    /** The guide method you picked for this skill's suggestion (its name), or null to have it picked for you. */
    public String getChosenMethod(Skill skill) {
        return configManager.getRSProfileKey() == null ? null
            : configManager.getRSProfileConfiguration(SHARED_GROUP, "goalMethod." + skill.name());
    }

    /** Pick the suggestion's method (null: automatic). It replaces a Track for goal pick, as it's what you'll train. */
    public void setChosenMethod(Skill skill, String method) {
        if (method == null) {
            configManager.unsetRSProfileConfiguration(SHARED_GROUP, "goalMethod." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(SHARED_GROUP, "goalMethod." + skill.name(), method);
            configManager.unsetRSProfileConfiguration(SHARED_GROUP, "goalTarget." + skill.name());
        }
        updatePanelUI();
    }

    public void setGoalTarget(Skill skill, String target) {
        if (target == null) {
            configManager.unsetRSProfileConfiguration(SHARED_GROUP, "goalTarget." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(SHARED_GROUP, "goalTarget." + skill.name(), target);
        }
        updatePanelUI();
    }

    /**
     * How you train Prayer with bones (bury, or which altar) or with blessed bone shards (which wine), as
     * picked from an item's "Track for goal" menu; null if not picked (bury; shards by the wine you carry).
     */
    public PrayerMethod getPrayerMethod(boolean shards) {
        return configManager.getRSProfileKey() == null ? null
            : PrayerMethod.parse(configManager.getRSProfileConfiguration(SHARED_GROUP, prayerMethodKey(shards)));
    }

    public void setPrayerMethod(PrayerMethod method) {
        configManager.setRSProfileConfiguration(SHARED_GROUP, prayerMethodKey(method.shards), method.name());
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
            : configManager.getRSProfileConfiguration(SHARED_GROUP, "trainingFocus." + skill.name());
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
            configManager.unsetRSProfileConfiguration(SHARED_GROUP, "trainingFocus." + skill.name());
        } else {
            configManager.setRSProfileConfiguration(SHARED_GROUP, "trainingFocus." + skill.name(), focus.name());
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
        String name = configManager.getConfiguration(SHARED_GROUP, "pinnedSkill");
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
            configManager.unsetConfiguration(SHARED_GROUP, "pinnedSkill");
        } else {
            configManager.setConfiguration(SHARED_GROUP, "pinnedSkill", skill.name());
        }
        updatePanelUI();
    }

    /** Your XP in the skill when you set its goal (for the goal bar), or 0 if unknown. */
    public int getSkillGoalStart(Skill skill) {
        String value = configManager.getRSProfileConfiguration(SHARED_GROUP, "goalStart." + skill.name());
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
        clearItems(skill);
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

    // When each skill last gained XP (wall clock, so it survives hopping and loading screens)
    private final Map<Skill, Long> lastXpMillis = new EnumMap<>(Skill.class);

    private boolean reachedChecked;

    // Clear Goals When Reached: the goal goes, with a chat line, once your XP gets there
    private void clearIfReached(Skill skill, int xp) {
        int goal = getSkillGoal(skill);
        if (!config.clearReachedGoals() || goal <= 0 || xp < goal || configManager.getRSProfileKey() == null) {
            return;
        }
        int level = Experience.getLevelForXp(goal);
        String target = goal == Experience.getXpForLevel(level) ? "level " + level : String.format("%,d XP", goal);
        setSkillGoal(skill, 0);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName + "]</col> "
            + skill.getName() + " goal reached (" + target + "): cleared. Set Goal for your next one.", null);
    }

    /** Whether the tracker's "goals" or "session" list is folded away (kept between launches). */
    public boolean isListCollapsed(String list) {
        return Boolean.parseBoolean(configManager.getConfiguration(SHARED_GROUP, "trackerCollapsed." + list));
    }

    public void setListCollapsed(String list, boolean collapsed) {
        if (collapsed) {
            configManager.setConfiguration(SHARED_GROUP, "trackerCollapsed." + list, true);
        } else {
            configManager.unsetConfiguration(SHARED_GROUP, "trackerCollapsed." + list);
        }
        updatePanelUI();
    }

    /** When a skill last gained XP (wall clock), 0 if not this session: the tracker lists the latest first. */
    public long lastXpTime(Skill skill) {
        return lastXpMillis.getOrDefault(skill, 0L);
    }

    /** Whether a skill gained XP recently enough to stay in the tracker (see Drop Idle Skills After). */
    public boolean isRecentlyTrained(Skill skill) {
        int minutes = config.dropIdleSkillsAfter();
        Long last = lastXpMillis.get(skill);
        return minutes <= 0 || (last != null && System.currentTimeMillis() - last <= minutes * 60_000L);
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
    /**
     * Whether you're training this skill lately: an XP drop in it, or you started doing it (fishing a spot,
     * chopping, mining) in the last two minutes. The second shows a skill's timers as soon as you start, rather
     * than at your first catch.
     */
    boolean trainedRecently(Skill skill) {
        Integer last = lastXpTicks.get(skill);
        Integer doing = lastDoingTicks.get(skill);
        int now = client.getTickCount();
        return (last != null && now - last <= PIE_TRAINING_TICKS) || (doing != null && now - doing <= PIE_TRAINING_TICKS);
    }

    /** Whether you're fishing, chopping or mining right now (seen in the last few ticks). */
    boolean isWorking(Skill skill) {
        Integer doing = skill == null ? null : lastDoingTicks.get(skill);
        return doing != null && client.getTickCount() - doing <= WORKING_TICKS;
    }

    private static final int WORKING_TICKS = 5;

    // The tree or rock you last clicked to chop or mine, for isWorkingOn
    private WorldPoint workedTile;

    @Subscribe
    public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event) {
        String option = net.runelite.client.util.Text.removeTags(event.getMenuOption()).toLowerCase();
        if (event.getMenuAction().name().startsWith("GAME_OBJECT") && (option.startsWith("chop") || option.equals("mine"))) {
            workedTile = WorldPoint.fromScene(client.getTopLevelWorldView(), event.getParam0(), event.getParam1(),
                client.getTopLevelWorldView().getPlane());
        }
    }

    /** Whether this is the tree or rock you're chopping or mining right now. */
    boolean isWorkingOn(net.runelite.api.TileObject object) {
        if (workedTile == null || !(isWorking(Skill.WOODCUTTING) || isWorking(Skill.MINING))) {
            return false;
        }
        // A click gives a big tree's south-west tile, which its location isn't
        if (object instanceof net.runelite.api.GameObject) {
            net.runelite.api.GameObject game = (net.runelite.api.GameObject) object;
            WorldPoint min = WorldPoint.fromScene(client.getTopLevelWorldView(), game.getSceneMinLocation().getX(),
                game.getSceneMinLocation().getY(), game.getPlane());
            if (workedTile.equals(min)) {
                return true;
            }
        }
        return workedTile.equals(object.getWorldLocation());
    }

    /** Whether this is the fishing spot (or other NPC) you're working right now. */
    boolean isWorkingOn(net.runelite.api.NPC npc) {
        Player you = client.getLocalPlayer();
        return you != null && you.getInteracting() == npc;
    }

    // Each skill you were last seen doing, by tick: fishing a spot, or chopping or mining (by animation)
    private final Map<Skill, Integer> lastDoingTicks = new EnumMap<>(Skill.class);

    private void noteWhatYoureDoing(Player player) {
        Skill doing = null;
        int animation = player.getAnimation();
        if (ResourceTimers.CHOP_ANIMATIONS.contains(animation)) {
            doing = Skill.WOODCUTTING;
        } else if (ResourceTimers.MINE_ANIMATIONS.contains(animation)) {
            doing = Skill.MINING;
        } else if (player.getInteracting() instanceof net.runelite.api.NPC && isFishingSpot((net.runelite.api.NPC) player.getInteracting())) {
            doing = Skill.FISHING;
        }
        if (doing != null) {
            lastDoingTicks.put(doing, client.getTickCount());
        }
    }

    private static boolean isFishingSpot(net.runelite.api.NPC npc) {
        net.runelite.api.NPCComposition composition = npc.getTransformedComposition() != null
            ? npc.getTransformedComposition() : npc.getComposition();
        if (composition == null) {
            return false;
        }
        for (String action : composition.getActions()) {
            if (action != null && SkillingData.isFishingOption(action)) {
                return true;
            }
        }
        return false;
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

    /**
     * Your usual XP drop in a skill: the most common of your last drops (the latest of those tied), 0 before the
     * first. One method gives the same XP each time, so a one-off (a pillar tagged between floor spikes) doesn't
     * change it, and changing method changes it once the new drops outnumber the old.
     */
    public int getUsualXpDrop(Skill skill) {
        java.util.ArrayDeque<Integer> recent = recentXpDrops.get(skill);
        return recent == null ? 0 : usualOf(recent);
    }

    static int usualOf(Iterable<Integer> drops) {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        int best = 0;
        int bestCount = 0;
        for (int drop : drops) {
            int count = counts.merge(drop, 1, Integer::sum);
            // Oldest first: a later drop wins a tie
            if (count >= bestCount) {
                best = drop;
                bestCount = count;
            }
        }
        return best;
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

    // ---- Keep Session After Restart (like RuneLite's XP Tracker keeping its session) ----

    static final String SESSION_KEY = "trackerSession";
    private static final int SAVE_TICKS = 50;
    // What was last saved, so an unchanged session isn't written again
    private String savedSession;
    // Profiles whose saved session has been looked at this run (once each, at their first login)
    private final Set<String> restoreChecked = new HashSet<>();

    /** A saved session: when it was saved, each skill's {gained, active ticks, last drop, last XP time}, items. */
    static final class SavedSession {
        final long savedAt;
        final Map<Skill, long[]> skills = new EnumMap<>(Skill.class);
        final Map<ItemsGathered.Kind, Integer> items = new EnumMap<>(ItemsGathered.Kind.class);

        SavedSession(long savedAt) {
            this.savedAt = savedAt;
        }
    }

    /** "1;savedAt;WOODCUTTING,gained,ticks,drop,lastXp;...;items,LOGS=342,FISH=12": kept as plain text. */
    static String encode(SavedSession session) {
        StringBuilder out = new StringBuilder("1;").append(session.savedAt);
        session.skills.forEach((skill, v) -> out.append(';').append(skill.name()).append(',').append(v[0]).append(',')
            .append(v[1]).append(',').append(v[2]).append(',').append(v[3]));
        if (!session.items.isEmpty()) {
            out.append(";items");
            session.items.forEach((kind, n) -> out.append(',').append(kind.name()).append('=').append(n));
        }
        return out.toString();
    }

    /** The session in {@link #encode}'s text, or null if it isn't one (a newer or broken format). */
    static SavedSession decode(String text) {
        if (text == null || !text.startsWith("1;")) {
            return null;
        }
        try {
            String[] parts = text.split(";");
            SavedSession session = new SavedSession(Long.parseLong(parts[1]));
            for (int i = 2; i < parts.length; i++) {
                String[] fields = parts[i].split(",");
                if ("items".equals(fields[0])) {
                    for (int f = 1; f < fields.length; f++) {
                        String[] kv = fields[f].split("=");
                        session.items.put(ItemsGathered.Kind.valueOf(kv[0]), Integer.parseInt(kv[1]));
                    }
                } else if (fields.length == 5) {
                    session.skills.put(Skill.valueOf(fields[0]), new long[]{Long.parseLong(fields[1]),
                        Long.parseLong(fields[2]), Long.parseLong(fields[3]), Long.parseLong(fields[4])});
                }
            }
            return session;
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    /** Whether a session saved then can be carried on now (Keep Session After Restart, 0: never). */
    static boolean recentEnough(long savedAt, long now, int minutes) {
        return minutes > 0 && savedAt <= now && now - savedAt <= minutes * 60_000L;
    }

    private SavedSession currentSession(long at) {
        SavedSession session = new SavedSession(at);
        for (Map.Entry<Skill, Integer> entry : gainedSkillXp.entrySet()) {
            if (entry.getValue() > 0) {
                Skill skill = entry.getKey();
                session.skills.put(skill, new long[]{entry.getValue(), skillActiveTicks.getOrDefault(skill, 0L),
                    lastXpDrops.getOrDefault(skill, 0), lastXpMillis.getOrDefault(skill, 0L)});
            }
        }
        session.items.putAll(itemCounts);
        return session;
    }

    // Every half a minute it's only written when it changed (its time aside), so an idle client doesn't keep
    // writing it; logging out and stopping the plugin write it with that time (always)
    private void saveSession() {
        saveSession(false);
    }

    private void saveSession(boolean always) {
        if (config.keepSessionMinutes() <= 0 || configManager.getRSProfileKey() == null) {
            return;
        }
        SavedSession session = currentSession(System.currentTimeMillis());
        if (session.skills.isEmpty() && session.items.isEmpty()) {
            return;
        }
        String content = encode(currentSession(0));
        if (!always && content.equals(savedSession)) {
            return;
        }
        savedSession = content;
        configManager.setRSProfileConfiguration(identity.configGroup, SESSION_KEY, encode(session));
    }

    // At an account's first login this run: carry on its saved session if it was saved recently enough, unless
    // this run already has one (a relog without restarting keeps its session anyway)
    private void restoreSession() {
        String profile = configManager.getRSProfileKey();
        if (profile == null || !restoreChecked.add(profile)) {
            return;
        }
        SavedSession saved = decode(configManager.getRSProfileConfiguration(identity.configGroup, SESSION_KEY));
        long now = System.currentTimeMillis();
        if (saved == null || !recentEnough(saved.savedAt, now, config.keepSessionMinutes())
            || gainedSkillXp.values().stream().anyMatch(gained -> gained > 0) || !itemCounts.isEmpty()) {
            return;
        }
        int tick = client.getTickCount();
        saved.skills.forEach((skill, v) -> {
            int gained = (int) v[0];
            // What you'd gained, from where you are now: XP gained elsewhere meanwhile (mobile) doesn't count
            baseSkillXp.put(skill, client.getSkillExperience(skill) - gained);
            gainedSkillXp.put(skill, gained);
            skillActiveTicks.put(skill, v[1]);
            if (v[2] > 0) {
                lastXpDrops.put(skill, (int) v[2]);
                recentXpDrops.computeIfAbsent(skill, k -> new java.util.ArrayDeque<>()).addLast((int) v[2]);
            }
            if (v[3] > 0) {
                lastXpMillis.put(skill, v[3]);
                // So it auto-pauses as it would have (the ticks since then, as if the client had run on)
                lastXpTicks.put(skill, tick - (int) Math.min(Integer.MAX_VALUE / 2, (now - v[3]) / 600));
            }
        });
        itemCounts.putAll(saved.items);
        long minutes = (now - saved.savedAt) / 60_000;
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName + "]</col> "
            + "Carried on your skill tracker session from " + (minutes < 1 ? "just now" : minutes + " min ago")
            + " (Keep Session After Restart; Reset all skills to start again).", null);
        updatePanelUI();
    }

    // Combat asks for an update on every hit; queue at most one at a time
    private final java.util.concurrent.atomic.AtomicBoolean panelUpdateQueued = new java.util.concurrent.atomic.AtomicBoolean();
}
