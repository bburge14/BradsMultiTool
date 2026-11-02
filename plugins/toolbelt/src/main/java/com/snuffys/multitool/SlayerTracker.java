package com.snuffys.multitool;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;

/**
 * The current slayer task (read the same way as RuneLite's Slayer plugin), kill rate, time to finish,
 * and whether you carry the item the task's monsters require.
 */
@Singleton
class SlayerTracker {

    @Inject
    private Alerts alerts;

    // Kills used for the kill rate
    private static final int RATE_WINDOW = 30;

    static final class Requirement {
        final String display;
        final List<String> itemKeywords;

        Requirement(String display, String... itemKeywords) {
            this.display = display;
            this.itemKeywords = Arrays.asList(itemKeywords);
        }
    }

    // Task name fragment -> item needed to damage / survive that monster (any listed item counts)
    private static final Map<String, Requirement> REQUIREMENTS = new LinkedHashMap<>();

    static {
        REQUIREMENTS.put("gargoyle", new Requirement("Rock hammer", "rock hammer", "rock thrownhammer", "granite hammer"));
        REQUIREMENTS.put("rockslug", new Requirement("Bag of salt", "bag of salt"));
        REQUIREMENTS.put("lizard", new Requirement("Ice cooler", "ice cooler"));
        REQUIREMENTS.put("zygomite", new Requirement("Fungicide spray", "fungicide spray"));
        REQUIREMENTS.put("kurask", new Requirement("Leaf-bladed weapon", "leaf-bladed", "broad arrows", "broad bolts"));
        REQUIREMENTS.put("turoth", new Requirement("Leaf-bladed weapon", "leaf-bladed", "broad arrows", "broad bolts"));
        REQUIREMENTS.put("banshee", new Requirement("Earmuffs", "earmuffs", "slayer helmet"));
        REQUIREMENTS.put("aberrant spectre", new Requirement("Nose peg", "nose peg", "slayer helmet"));
        REQUIREMENTS.put("cockatrice", new Requirement("Mirror shield", "mirror shield", "v's shield"));
        REQUIREMENTS.put("basilisk", new Requirement("Mirror shield", "mirror shield", "v's shield"));
        REQUIREMENTS.put("wall beast", new Requirement("Spiny helmet", "spiny helmet", "slayer helmet"));
        REQUIREMENTS.put("dust devil", new Requirement("Facemask", "facemask", "gas mask", "slayer helmet"));
        REQUIREMENTS.put("smoke devil", new Requirement("Facemask", "facemask", "gas mask", "slayer helmet"));
        REQUIREMENTS.put("killerwatt", new Requirement("Insulated boots", "insulated boots"));
        REQUIREMENTS.put("cave horror", new Requirement("Witchwood icon", "witchwood icon"));
        REQUIREMENTS.put("harpie bug", new Requirement("Lit bug lantern", "bug lantern"));
        REQUIREMENTS.put("molanisk", new Requirement("Slayer bell", "slayer bell"));
    }

    private final Client client;
    private final PvmConfig config;
    // PvM holds this tracker too, so it's filled in after this is made (a constructor cycle hangs RuneLite's
    // Guice instead of failing) and fetched when first needed
    @Inject
    private Provider<PvmTracker> pvm;

    @Getter
    private String taskName = null;
    @Getter
    private int remaining = 0;
    private int taskId = -1;
    private final Deque<Long> killTimes = new ArrayDeque<>();
    @Getter
    private boolean hasRequiredItem = true;
    private boolean missingItemAlerted = false;

    // Supplies used on this task (food eaten, potion doses drunk), saved with the task so a relog keeps them
    @Getter
    private int foodEaten;
    @Getter
    private int dosesDrunk;
    private static final String SUPPLIES_KEY = "slayerSupplies";
    @Inject
    private ConfigManager configManager;
    @Inject
    private PluginIdentity identity;

    /** Task streaks with bonus points, biggest first (every 10th task, 50th, 100th, 250th and 1000th). */
    private static final int[] MILESTONES = {1000, 250, 100, 50, 10};
    /** The bonus each milestone gives, as a multiple of the master's usual points (OSRS Wiki, Slayer reward point). */
    private static final int[] MILESTONE_MULTIPLIERS = {50, 35, 25, 15, 5};

    @Inject
    SlayerTracker(Client client, PvmConfig config) {
        this.client = client;
        this.config = config;
    }

    boolean hadTask() {
        return taskName != null;
    }

    void reset() {
        taskName = null;
        taskId = -1;
        remaining = 0;
        killTimes.clear();
    }

    /** Call once per game tick. */
    void update() {
        int count = SlayerVars.remaining(client);
        int id = SlayerVars.taskId(client);
        if (count <= 0) {
            reset();
            return;
        }
        if (id != taskId || count > remaining) {
            // Just logged in (or started): the task we had before may be this one; otherwise it's a new task
            boolean resumed = taskId == -1;
            taskId = id;
            taskName = SlayerVars.taskName(client, id);
            killTimes.clear();
            missingItemAlerted = false;
            loadSupplies(resumed);
        } else if (count < remaining) {
            for (int i = 0; i < remaining - count; i++) {
                killTimes.addLast(System.currentTimeMillis());
            }
            while (killTimes.size() > RATE_WINDOW) {
                killTimes.removeFirst();
            }
        }
        remaining = count;

        Requirement requirement = getRequirement();
        hasRequiredItem = requirement == null || carries(requirement);
        warnIfFightingWithoutItem(requirement);
    }

    Requirement getRequirement() {
        if (taskName == null) {
            return null;
        }
        return requirementFor(taskName);
    }

    /** The item a monster or task needs ("Banshee" -> Earmuffs), matched by name, or null. */
    static Requirement requirementFor(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase();
        for (Map.Entry<String, Requirement> e : REQUIREMENTS.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** True when it's equipped or in your inventory. */
    boolean carries(Requirement requirement) {
        for (int containerId : new int[]{93, 94}) {
            ItemContainer container = client.getItemContainer(containerId);
            if (container == null) {
                continue;
            }
            for (Item item : container.getItems()) {
                if (item.getId() <= 0) {
                    continue;
                }
                String itemName = client.getItemDefinition(item.getId()).getName().toLowerCase();
                for (String keyword : requirement.itemKeywords) {
                    if (itemName.contains(keyword)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void warnIfFightingWithoutItem(Requirement requirement) {
        if (requirement == null || hasRequiredItem || missingItemAlerted || !config.slayerItemAlert()) {
            return;
        }
        NPC target = pvm.get().getCurrentPvmTarget();
        if (target != null && isTaskMonster(target)) {
            missingItemAlerted = true;
            alerts.alert(VoiceAlertService.VoiceLine.SLAYER_ITEM_MISSING, requirement.display.toLowerCase() + " for " + taskName.toLowerCase());
        }
    }

    // Konar's assigned area, looked up once per area id
    private int konarAreaId = -1;
    private String konarArea;

    /** The area Konar assigned with this task (from the game's Slayer area table), or null. */
    String getKonarArea() {
        int id = SlayerVars.areaId(client);
        if (id != konarAreaId) {
            konarAreaId = id;
            konarArea = SlayerVars.area(client, id);
        }
        return konarArea;
    }

    /** True when this Slayer category ("Banshees") is your current task. */
    boolean isTask(String category) {
        return taskName != null && category != null && taskName.equalsIgnoreCase(category);
    }

    /** True when the NPC's name matches the task, e.g. "Gargoyle" for "Gargoyles". */
    boolean isTaskMonster(NPC npc) {
        if (taskName == null || npc.getName() == null) {
            return false;
        }
        String stem = taskName.toLowerCase();
        if (stem.endsWith("s")) {
            stem = stem.substring(0, stem.length() - 1);
        }
        return npc.getName().toLowerCase().contains(stem);
    }

    /** Your Slayer reward points (the same varbit RuneLite's Slayer plugin shows). */
    int points() {
        return SlayerVars.points(client);
    }

    /**
     * Tasks done in a row, as RuneLite's Slayer plugin reads it: Krystilia and Mortimer keep their own streak,
     * every other master shares one. Goes by the master of your current (or last) task.
     */
    int streak() {
        return SlayerVars.streak(client);
    }

    /** The milestone (10, 50, 100, 250 or 1000) the task after {@code streak} done lands on, or 0 if none. */
    static int nextMilestone(int streak) {
        int next = streak + 1;
        for (int milestone : MILESTONES) {
            if (next > 0 && next % milestone == 0) {
                return milestone;
            }
        }
        return 0;
    }

    /** The bonus a milestone task gives, as a multiple of the usual points ("15x" for the 50th), or 0. */
    static int milestoneMultiplier(int milestone) {
        for (int i = 0; i < MILESTONES.length; i++) {
            if (MILESTONES[i] == milestone) {
                return MILESTONE_MULTIPLIERS[i];
            }
        }
        return 0;
    }

    /** Counts a food eaten (from the game's "You eat" message) while on a task. */
    void ateFood() {
        if (hadTask()) {
            foodEaten++;
            saveSupplies();
        }
    }

    /** Counts a potion dose drunk (the game's "You drink some of your" message) while on a task. */
    void drankDose() {
        if (hadTask()) {
            dosesDrunk++;
            saveSupplies();
        }
    }

    // "taskId|task size|food|doses": restored after a relog onto the same task, else this task starts at none
    private void loadSupplies(boolean resumed) {
        foodEaten = 0;
        dosesDrunk = 0;
        if (!resumed || configManager == null || identity == null) {
            return;
        }
        String saved = configManager.getRSProfileConfiguration(identity.configGroup, SUPPLIES_KEY);
        int[] parts = parseSupplies(saved);
        if (parts != null && parts[0] == taskId && parts[1] == client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL)) {
            foodEaten = parts[2];
            dosesDrunk = parts[3];
        }
    }

    private void saveSupplies() {
        if (configManager != null && identity != null && configManager.getRSProfileKey() != null) {
            configManager.setRSProfileConfiguration(identity.configGroup, SUPPLIES_KEY, taskId + "|"
                + client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL) + "|" + foodEaten + "|" + dosesDrunk);
        }
    }

    /** The saved supplies record as {task id, task size, food, doses}, or null when there's none or it's damaged. */
    static int[] parseSupplies(String saved) {
        if (saved == null) {
            return null;
        }
        String[] fields = saved.split("\\|");
        if (fields.length != 4) {
            return null;
        }
        try {
            int[] out = new int[4];
            for (int i = 0; i < 4; i++) {
                out[i] = Integer.parseInt(fields[i]);
            }
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Kills per hour over the recent kills, or 0 before two kills. */
    double killsPerHour() {
        if (killTimes.size() < 2) {
            return 0;
        }
        double hours = (killTimes.getLast() - killTimes.getFirst()) / 3_600_000.0;
        return hours > 0 ? (killTimes.size() - 1) / hours : 0;
    }
}
