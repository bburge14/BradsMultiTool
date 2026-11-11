package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The Grand Exchange as it really trades, from the OSRS Wiki's real-time prices (prices.runescape.wiki, its 24-hour
 * averages): for each item what buyers paid (the high price: what buying costs you) and what sellers got (the low
 * price: what selling gets you), and how many traded at each in the last day. Fetched with RuneLite's own HTTP client,
 * off the client thread, every half hour while the Planner runs (one request for every item). Only reads public prices.
 */
@Slf4j
@Singleton
class PlannerMarket {

    private static final String URL = "https://prices.runescape.wiki/api/v1/osrs/24h";
    // The wiki asks for a User-Agent that says who's asking
    private static final String USER_AGENT = "Snuffy's Planner (RuneLite plugin)";
    private static final long EVERY_MILLIS = 30 * 60_000L;
    private static final long RETRY_MILLIS = 5 * 60_000L;

    /** An item's last day: average price paid (high) and got (low), 0 when none; how many traded at each. */
    static final class Day {
        final int high;
        final int low;
        final long volume;

        Day(int high, int low, long volume) {
            this.high = high;
            this.low = low;
            this.volume = volume;
        }
    }

    private final OkHttpClient http;
    private final Gson gson;
    private final PlannerView view;
    private final PlannerConfig config;
    private volatile Map<Integer, Day> days = new HashMap<>();
    private volatile long nextAt;
    private volatile boolean asking;
    private volatile int version;

    @Inject
    PlannerMarket(OkHttpClient http, Gson gson, PlannerView view, PlannerConfig config) {
        this.http = http;
        this.gson = gson;
        this.view = view;
        this.config = config;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        long now = System.currentTimeMillis();
        if (!config.plannerMarketPrices() || asking || now < nextAt) {
            return;
        }
        asking = true;
        nextAt = now + RETRY_MILLIS;
        Request request = new Request.Builder().url(URL).header("User-Agent", USER_AGENT).build();
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                asking = false;
                log.debug("Couldn't get the wiki's prices", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        Map<Integer, Day> read = parse(gson, body.string());
                        if (!read.isEmpty()) {
                            days = read;
                            version++;
                            nextAt = System.currentTimeMillis() + EVERY_MILLIS;
                            view.update();
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    log.debug("Couldn't read the wiki's prices", e);
                } finally {
                    asking = false;
                }
            }
        });
    }

    /** The wiki's 24-hour prices: {"data": {"4151": {"avgHighPrice": .., "highPriceVolume": .., ...}}}. */
    static Map<Integer, Day> parse(Gson gson, String json) {
        Map<Integer, Day> out = new HashMap<>();
        JsonObject root = gson.fromJson(json, JsonObject.class);
        if (root == null || !root.has("data")) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("data").entrySet()) {
            try {
                JsonObject o = e.getValue().getAsJsonObject();
                out.put(Integer.parseInt(e.getKey()), new Day(number(o, "avgHighPrice"), number(o, "avgLowPrice"),
                    (long) number(o, "highPriceVolume") + number(o, "lowPriceVolume")));
            } catch (RuntimeException ignored) {
                // (an odd entry: left out)
            }
        }
        return out;
    }

    private static int number(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
    }

    /** An item's last day on the GE, or null (not known, or the setting's off). */
    Day day(int itemId) {
        return config.plannerMarketPrices() && itemId > 0 ? days.get(itemId) : null;
    }

    /**
     * Whether the wiki's day has come in (then an item it doesn't list didn't trade at all in it: the feed only
     * lists what traded).
     */
    boolean loaded() {
        return config.plannerMarketPrices() && !days.isEmpty();
    }

    /** Changes when new prices come in (the Planner redraws). */
    int version() {
        return version;
    }
}
