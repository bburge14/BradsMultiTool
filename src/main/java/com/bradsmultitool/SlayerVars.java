package com.bradsmultitool;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Your Slayer task, points and streak straight from the game's variables, read the way RuneLite's Slayer plugin
 * reads them. Shared by PvM/Slayer's task card and the Planner's SLAYER list, so neither needs the other.
 * Call on the client thread (the task's name comes from the game's database tables).
 */
final class SlayerVars {

    private static final int BOSS_TASK = 98;
    // Game database tables/columns used by RuneLite's Slayer plugin
    private static final int TABLE_BOSS_TASKS = 116;
    private static final int TABLE_TASKS = 113;
    private static final int COLUMN_TASK_NAME = 10;
    // Slayer masters with their own streak (RuneLite's Slayer plugin: VarbitID.SLAYER_MASTER values)
    private static final int KRYSTILIA = 7;
    private static final int MORTIMER = 10;

    private SlayerVars() {
    }

    /** Kills left on your task (0: no task). */
    static int remaining(Client client) {
        return client.getVarpValue(VarPlayerID.SLAYER_COUNT);
    }

    /** The task's id (what SLAYER_TARGET holds). */
    static int taskId(Client client) {
        return client.getVarpValue(VarPlayerID.SLAYER_TARGET);
    }

    /** How many the task started with, counting any extension or reduction. */
    static int assigned(Client client) {
        int amount = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
        if (client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID) == 2) {
            int change = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
            amount += client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1 ? -change : change;
        }
        return amount;
    }

    /** The task's name ("Gargoyles"), "Task #id" if the game's tables don't have it. */
    static String taskName(Client client, int id) {
        try {
            if (id == BOSS_TASK) {
                // The boss table's task column is the task table's row itself (as RuneLite's Slayer plugin reads it)
                List<Integer> bossRows = client.getDBRowsByValue(TABLE_BOSS_TASKS, 1, 0, client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
                if (!bossRows.isEmpty()) {
                    int taskRow = (Integer) client.getDBTableField(bossRows.get(0), 4, 0)[0];
                    return (String) client.getDBTableField(taskRow, COLUMN_TASK_NAME, 0)[0];
                }
            }
            List<Integer> rows = client.getDBRowsByValue(TABLE_TASKS, 0, 0, id);
            if (!rows.isEmpty()) {
                return (String) client.getDBTableField(rows.get(0), COLUMN_TASK_NAME, 0)[0];
            }
        } catch (RuntimeException e) {
            // Unknown task id or table layout change: fall through
        }
        return "Task #" + id;
    }

    /** The area Konar sent you to with this task (area id from SLAYER_AREA), or null. */
    static String area(Client client, int areaId) {
        if (areaId <= 0) {
            return null;
        }
        try {
            List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
            if (!rows.isEmpty()) {
                return (String) client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0];
            }
        } catch (RuntimeException e) {
            // Table layout change: no area
        }
        return null;
    }

    static int areaId(Client client) {
        return client.getVarpValue(VarPlayerID.SLAYER_AREA);
    }

    /** Your Slayer reward points. */
    static int points(Client client) {
        return client.getVarbitValue(VarbitID.SLAYER_POINTS);
    }

    /**
     * Tasks done in a row: Krystilia and Mortimer keep their own streak, every other master shares one. Goes by the
     * master of your current (or last) task.
     */
    static int streak(Client client) {
        switch (client.getVarbitValue(VarbitID.SLAYER_MASTER)) {
            case KRYSTILIA:
                return client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
            case MORTIMER:
                return client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
            default:
                return client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
        }
    }
}
