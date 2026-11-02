package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.Test;

public class PlannerTest {

    // 2026-11-04 is a Wednesday
    private static final long WEDNESDAY = java.time.LocalDate.of(2026, 11, 4).toEpochDay() * PlannerChores.DAY;

    @Test
    public void resetsAreMidnightUtcAndWednesdays() {
        long tuesdayEvening = WEDNESDAY - 3600;
        assertEquals(WEDNESDAY - PlannerChores.DAY, PlannerChores.lastDailyReset(tuesdayEvening));
        assertEquals(WEDNESDAY - PlannerChores.WEEK, PlannerChores.lastWeeklyReset(tuesdayEvening));
        assertEquals(WEDNESDAY, PlannerChores.lastWeeklyReset(WEDNESDAY));
        assertEquals(WEDNESDAY, PlannerChores.lastWeeklyReset(WEDNESDAY + 6 * PlannerChores.DAY + 5));
        // Tears of Guthix: back at the start of the seventh day after the last game
        assertEquals(WEDNESDAY + PlannerChores.WEEK, PlannerChores.togAvailableAt(WEDNESDAY + 15 * 3600));
    }

    @Test
    public void timerLengths() {
        assertEquals(90 * 60, PlannerTimers.parseLength("90"));
        assertEquals(90 * 60, PlannerTimers.parseLength("1:30"));
        assertEquals(-1, PlannerTimers.parseLength("soon"));
        assertEquals(-1, PlannerTimers.parseLength("0"));
        assertEquals("1:05:09", PlannerTimers.clock(3909));
        assertEquals("4:30", PlannerTimers.clock(270));
    }

    @Test
    public void durationsAndGp() {
        assertEquals("2d 4h", PlannerPanel.formatDuration(2 * 86400 + 4 * 3600));
        assertEquals("5h 12m", PlannerPanel.formatDuration(5 * 3600 + 12 * 60));
        assertEquals("1m", PlannerPanel.formatDuration(30));
        assertEquals("1.25M", PlannerPanel.gp(1_250_000));
    }

    @Test
    public void slayerTaskRoundTrips() {
        PlannerSlayer.Task task = new PlannerSlayer.Task("Gargoyles", 87, 150, null, 1200, 42);
        PlannerSlayer.Task back = PlannerSlayer.Task.decode(task.encode());
        assertEquals("Gargoyles", back.name);
        assertEquals(87, back.remaining);
        assertEquals(null, back.area);
        assertEquals(42, back.streak);
    }

    @Test
    public void farmingRecordsKeepOldOnesAndShowUncertainTimes() {
        // Saved before stages and slack were kept
        FarmingTracker.Record old = FarmingTracker.Record.decode("k", "Catherby: Herb patch|Ranarr|GROWING|1000|false");
        assertNotNull(old);
        assertEquals(-1, old.stage);
        // Until the tick offset is known it may be out by a cycle: shown with ~, and only ready after the slack
        FarmingTracker.Record unsure = new FarmingTracker.Record("k", "t", "Ranarr", FarmingData.GROWING, 1000, false, 2, 1200);
        assertEquals("~in 10m", PlannerPanel.patchStatus(unsure, 400));
        assertFalse(unsure.ready(1000));
        assertTrue(unsure.ready(2200));
        assertEquals(1200, FarmingTracker.Record.decode("k", unsure.encode()).slack);
    }

    @Test
    public void growthTicksAndOffsets() {
        FarmingData data = new FarmingData(null, null);
        data.load(new Gson());
        FarmingData.State stage2 = data.decode("ALLOTMENT", 8);
        FarmingTracker.Record stage1 = new FarmingTracker.Record("k", "t", stage2.produce.name, FarmingData.GROWING, 0, false,
            stage2.stage - 1, 0);
        assertTrue(FarmingTracker.grewJustNow(stage1, stage2));
        assertFalse(FarmingTracker.grewJustNow(new FarmingTracker.Record("k", "t", "c", FarmingData.GROWING, 0, false), stage2));
        // A 3-minute offset moves the next 10-minute cycle 3 minutes earlier
        FarmingData.State potato = data.decode("ALLOTMENT", 7);
        long now = 600 * 1000 + 360;
        assertEquals(240 + 2 * 600, FarmingData.secondsUntilGrown(potato, now, 0));
        assertEquals(60 + 2 * 600, FarmingData.secondsUntilGrown(potato, now, 180));
    }

    @Test
    public void contractCropsLoad() {
        FarmingData data = new FarmingData(null, null);
        data.load(new Gson());
        // RuneLite's Produce: cabbage is contract value 42
        assertEquals("Cabbage", data.contractCrop(42));
    }
}
