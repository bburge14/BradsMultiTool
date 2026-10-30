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
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;

/**
 * The current slayer task (read the same way as RuneLite's Slayer plugin), kill rate, time to finish,
 * and whether you carry the item the task's monsters require.
 */
@Singleton
class SlayerTracker {

    @Inject
    private Alerts alerts;

    private static final int VARP_SLAYER_COUNT = 394;   // VarPlayerID.SLAYER_COUNT
    private static final int VARP_SLAYER_TARGET = 395;  // VarPlayerID.SLAYER_TARGET
    private static final int VARBIT_BOSS_TARGET = 4723; // VarbitID.SLAYER_TARGET_BOSSID
    private static final int BOSS_TASK = 98;
    // Game database tables/columns used by RuneLite's Slayer plugin
    private static final int TABLE_BOSS_TASKS = 116;
    private static final int TABLE_TASKS = 113;
    private static final int COLUMN_TASK_NAME = 10;
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
        int count = client.getVarpValue(VARP_SLAYER_COUNT);
        int id = client.getVarpValue(VARP_SLAYER_TARGET);
        if (count <= 0) {
            reset();
            return;
        }
        if (id != taskId || count > remaining) {
            taskId = id;
            taskName = lookupTaskName(id);
            killTimes.clear();
            missingItemAlerted = false;
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

    // Same lookup as RuneLite's Slayer plugin: boss tasks map to a creature first, then the task table has the name
    private String lookupTaskName(int id) {
        try {
            int creature = id;
            if (id == BOSS_TASK) {
                List<Integer> bossRows = client.getDBRowsByValue(TABLE_BOSS_TASKS, 1, 0, client.getVarbitValue(VARBIT_BOSS_TARGET));
                if (!bossRows.isEmpty()) {
                    creature = (Integer) client.getDBTableField(bossRows.get(0), 4, 0)[0];
                }
            }
            List<Integer> rows = client.getDBRowsByValue(TABLE_TASKS, 0, 0, creature);
            if (!rows.isEmpty()) {
                return (String) client.getDBTableField(rows.get(0), COLUMN_TASK_NAME, 0)[0];
            }
        } catch (RuntimeException e) {
            // Unknown task id or table layout change: fall through
        }
        return "Task #" + id;
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
        int id = client.getVarpValue(VarPlayerID.SLAYER_AREA);
        if (id != konarAreaId) {
            konarAreaId = id;
            konarArea = null;
            if (id > 0) {
                try {
                    List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, id);
                    if (!rows.isEmpty()) {
                        konarArea = (String) client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0];
                    }
                } catch (RuntimeException e) {
                    konarArea = null;
                }
            }
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

    /** Kills per hour over the recent kills, or 0 before two kills. */
    double killsPerHour() {
        if (killTimes.size() < 2) {
            return 0;
        }
        double hours = (killTimes.getLast() - killTimes.getFirst()) / 3_600_000.0;
        return hours > 0 ? (killTimes.size() - 1) / hours : 0;
    }
}
