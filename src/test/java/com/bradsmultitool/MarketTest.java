package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.Test;

/** The wiki's 24-hour prices, read. */
public class MarketTest {

    @Test
    public void readsTheWikisDay() {
        String json = "{\"data\":{\"6573\":{\"avgHighPrice\":2650000,\"highPriceVolume\":40,\"avgLowPrice\":2600000,"
            + "\"lowPriceVolume\":35},\"11130\":{\"avgHighPrice\":null,\"highPriceVolume\":0,\"avgLowPrice\":2400000,"
            + "\"lowPriceVolume\":3}},\"timestamp\":1700000000}";
        Map<Integer, PlannerMarket.Day> days = PlannerMarket.parse(new Gson(), json);
        assertEquals(2, days.size());
        PlannerMarket.Day onyx = days.get(6573);
        assertEquals(2650000, onyx.high);
        assertEquals(2600000, onyx.low);
        assertEquals(75, onyx.volume);
        // (no buys in the day: no high price)
        assertEquals(0, days.get(11130).high);
        assertTrue(PlannerMarket.parse(new Gson(), "{}").isEmpty());
    }
}
