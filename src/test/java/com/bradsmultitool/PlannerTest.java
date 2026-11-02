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
        assertEquals(90 * 60, PlannerTimers.parseLength("1:30:00"));
        assertEquals(90 * 60 + 15, PlannerTimers.parseLength("01:30:15"));
        // Two parts are minutes:seconds, as the timers count down
        assertEquals(4 * 60 + 30, PlannerTimers.parseLength("4:30"));
        assertEquals(-1, PlannerTimers.parseLength("1:75:00"));
        assertEquals(-1, PlannerTimers.parseLength("00:00:00"));
        assertEquals("01:30:15", PlannerTimers.hms(90 * 60 + 15));
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

    private static java.util.List<DropTracker.Stack> loot(Object... items) {
        java.util.List<DropTracker.Stack> stacks = new java.util.ArrayList<>();
        for (int i = 0; i < items.length; i += 2) {
            long value = ((Number) items[i + 1]).longValue();
            stacks.add(new DropTracker.Stack((String) items[i], 0, 1, value));
        }
        return stacks;
    }

    @Test
    public void dropsCountTimesDroppedRatesAndDryStreaks() {
        DropTracker.Source source = new DropTracker.Source();
        long valuable = 100_000;
        DropTracker.add(source, false, 1, loot("Bones", 100, "Coins", 50, "Coins", 70), valuable, 10);
        DropTracker.add(source, false, 1, loot("Bones", 100, "Abyssal whip", 1_450_000), valuable, 20);
        DropTracker.add(source, false, 1, loot("Bones", 100), valuable, 30);
        DropTracker.add(source, false, 1, loot("Bones", 100, "Coins", 30), valuable, 40);
        assertEquals(4, source.count);
        assertEquals(4, source.rated);
        // Coins twice in one kill: dropped once that kill, both stacks counted
        long[] coins = source.items.get("Coins");
        assertEquals(2, coins[DropTracker.TIMES]);
        assertEquals(3, coins[DropTracker.QUANTITY]);
        assertEquals(150, coins[DropTracker.VALUE]);
        assertEquals("1 in 2", DropTracker.rateText(source, coins, false));
        assertEquals("every kill", DropTracker.rateText(source, source.items.get("Bones"), false));
        assertEquals("1 in 4", DropTracker.rateText(source, source.items.get("Abyssal whip"), false));
        assertEquals(4.0, DropTracker.rate(source, source.items.get("Abyssal whip")), 0.001);
        // Two kills since the whip; it's the best drop
        assertEquals(2, source.dry);
        assertTrue(source.valuableSeen);
        assertEquals("Abyssal whip", source.best.item);
        assertEquals(20, source.best.at);
        assertEquals(40, source.last);
        assertFalse(source.opened("Abyssal demon"));
        assertFalse(source.partlyRated());
    }

    @Test
    public void dropRatesOverManyKills() {
        DropTracker.Source source = new DropTracker.Source();
        for (int kill = 0; kill < 90; kill++) {
            DropTracker.add(source, true, 1, kill % 2 == 0 ? loot("Rune platelegs", 38_000) : loot("Coins", 900), 100_000, kill);
        }
        DropTracker.add(source, true, 1, loot("Coins", 900), 100_000, 99);
        assertEquals("1 in 2", DropTracker.rateText(source, source.items.get("Rune platelegs"), true));
        // 91 opens, 46 with coins: 1.98
        assertEquals("1 in 2", DropTracker.rateText(source, source.items.get("Coins"), true));
        assertEquals(91, source.dry);
        assertFalse(source.valuableSeen);
        assertTrue(source.opened("Anything"));
        DropTracker.Source rare = new DropTracker.Source();
        rare.rated = 1234;
        assertEquals("1 in 1,234", DropTracker.rateText(rare, new long[]{1, 5, 1, 0}, false));
        assertEquals("1 in 2.5", DropTracker.rateText(rare, new long[]{1, 5, 494, 0}, false));
    }

    @Test
    public void dropsSavedBeforeDropRatesStillLoad() {
        // The format before times dropped, kills since a valuable drop and item ids were kept
        String old = "{\"sources\":{\"Vorkath\":{\"count\":45,\"value\":2655000,\"items\":{"
            + "\"Dragonbone necklace\":[1,2610000],\"Coins\":[45000,45000]}},"
            + "\"Reward casket (hard)\":{\"count\":3,\"value\":9000,\"items\":{\"Purple sweets\":[18,9000]}}},"
            + "\"recent\":[{\"source\":\"Vorkath\",\"item\":\"Coins\",\"quantity\":1000,\"value\":1000,\"at\":5}]}";
        Gson gson = new Gson();
        DropTracker.Saved saved = DropTracker.parse(gson, old);
        DropTracker.Source vorkath = saved.sources.get("Vorkath");
        assertEquals(45, vorkath.count);
        assertEquals(2655000, vorkath.value);
        long[] necklace = vorkath.items.get("Dragonbone necklace");
        assertEquals(4, necklace.length);
        assertEquals(1, necklace[DropTracker.QUANTITY]);
        assertEquals(2610000, necklace[DropTracker.VALUE]);
        assertEquals(0, necklace[DropTracker.TIMES]);
        // How often isn't known for those kills
        assertEquals("?", DropTracker.rateText(vorkath, necklace, false));
        assertTrue(vorkath.partlyRated());
        assertEquals(null, vorkath.best);
        assertEquals(0, saved.recent.get(0).id);
        // Kind wasn't saved: guessed from the name
        assertFalse(vorkath.opened("Vorkath"));
        assertTrue(saved.sources.get("Reward casket (hard)").opened("Reward casket (hard)"));

        // New kills add to it: rates count from then
        DropTracker.add(vorkath, false, 1, loot("Coins", 900, "Wrath rune", 9000), 100_000, 50);
        DropTracker.add(vorkath, false, 1, loot("Coins", 900), 100_000, 60);
        assertEquals(47, vorkath.count);
        assertEquals(2, vorkath.rated);
        assertEquals("every kill", DropTracker.rateText(vorkath, vorkath.items.get("Coins"), false));
        assertEquals("1 in 2", DropTracker.rateText(vorkath, vorkath.items.get("Wrath rune"), false));
        assertEquals(45002, vorkath.items.get("Coins")[DropTracker.QUANTITY]);

        // Saved again and read back, the new fields are kept
        DropTracker.Saved back = DropTracker.parse(gson, gson.toJson(saved));
        assertEquals(2, back.sources.get("Vorkath").rated);
        assertEquals("kill", back.sources.get("Vorkath").kind);
        assertEquals(2, back.sources.get("Vorkath").items.get("Coins")[DropTracker.TIMES]);
        assertEquals("Wrath rune", back.sources.get("Vorkath").best.item);

        // Unreadable or missing: an empty list
        assertTrue(DropTracker.parse(gson, "{not json").sources.isEmpty());
        assertTrue(DropTracker.parse(gson, null).recent.isEmpty());
    }

    @Test
    public void dropSourcePictures() {
        assertEquals("AD", PlannerPanel.sourceInitials("Abyssal demon"));
        assertEquals("Vo", PlannerPanel.sourceInitials("Vorkath"));
        assertEquals("RC", PlannerPanel.sourceInitials("Reward casket (hard)"));
        assertEquals("?", PlannerPanel.sourceInitials(""));
        assertEquals(net.runelite.api.gameval.ItemID.TRAIL_REWARD_CASKET_HARD, PlannerPanel.sourceItem("Clue Scroll (Hard)"));
        assertEquals(-1, PlannerPanel.sourceItem("Vorkath"));
        assertEquals("5m ago", PlannerPanel.ago(1000, 1000 + 5 * 60 + 20));
        assertEquals("2 days ago", PlannerPanel.ago(1, 1 + 2 * PlannerChores.DAY));
    }
}
