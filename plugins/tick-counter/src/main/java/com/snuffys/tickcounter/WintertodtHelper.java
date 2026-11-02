package com.snuffys.tickcounter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Wintertodt: alerts for the round about to start, your brazier going out or breaking, running out of roots, the
 * cold or snowfall stopping what you were doing, and your warmth getting low; and what the card shows (your warmth,
 * the Wintertodt's energy, your points, roots and kindling). Only in the Wintertodt's room. The messages, the round
 * timer's game variable and the room are the ones RuneLite's own Wintertodt plugin uses (BSD-2-Clause); the energy
 * and points are read from the game's own Wintertodt box. Only reads; never acts for you.
 */
@Singleton
class WintertodtHelper {

    // The Wintertodt's room (as RuneLite's Wintertodt plugin)
    static final int REGION = 6462;
    // Say the round is about to start at this many seconds (RuneLite's plugin's default)
    private static final int ROUND_WARN_SECONDS = 5;
    // You count as busy (fletching, feeding, lighting or fixing) this many ticks after your last such animation
    private static final int BUSY_TICKS = 3;
    // Warmth must climb this far back above your setting before it can warn again
    private static final int WARMTH_RESET = 10;
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    private final Client client;
    private final Alerts alerts;
    private final SkillTrackerConfig config;

    private boolean inRoom;
    // The round timer's last value, and whether we've left since (it can still tick down for a moment)
    private int lastRoundTimer;
    private boolean justLeft;
    private int busyTick = -100;
    private boolean warmthWarned;

    @Inject
    WintertodtHelper(Client client, Alerts alerts, SkillTrackerConfig config) {
        this.client = client;
        this.alerts = alerts;
        this.config = config;
    }

    boolean inRoom() {
        return inRoom;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Player you = client.getLocalPlayer();
        boolean now = you != null && you.getWorldLocation().getRegionID() == REGION;
        if (inRoom && !now) {
            justLeft = true;
        }
        inRoom = now;
        if (!inRoom) {
            warmthWarned = false;
            return;
        }
        int animation = you.getAnimation();
        if (animation != -1 && !ResourceTimers.CHOP_ANIMATIONS.contains(animation)) {
            busyTick = client.getTickCount();
        }
        int warmth = warmth();
        if (warmth > 0 && warmth < config.wintertodtWarmthPercent()) {
            if (!warmthWarned) {
                warmthWarned = true;
                alerts.alert(VoiceAlertService.VoiceLine.WARMTH_LOW, warmth + "%");
            }
        } else if (warmth >= config.wintertodtWarmthPercent() + WARMTH_RESET || warmth == 0) {
            warmthWarned = false;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() != VarbitID.WINT_TRANSMIT_RESPAWNDELAY) {
            return;
        }
        int before = seconds(lastRoundTimer);
        int after = seconds(event.getValue());
        lastRoundTimer = event.getValue();
        if ((inRoom || justLeft) && before > ROUND_WARN_SECONDS && after <= ROUND_WARN_SECONDS && after > 0) {
            justLeft = false;
            alerts.alert(VoiceAlertService.VoiceLine.WINTERTODT_ROUND, null);
        }
    }

    // The round timer in seconds (RuneLite's Wintertodt plugin's sum)
    static int seconds(int timer) {
        return timer * 30 / 50;
    }

    /** Seconds until the next round starts, or 0 while one is on. */
    int roundStartsIn() {
        return seconds(client.getVarbitValue(VarbitID.WINT_TRANSMIT_RESPAWNDELAY));
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!inRoom || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)) {
            return;
        }
        String message = Text.removeTags(event.getMessage());
        if (message.startsWith("The brazier has gone out")) {
            alerts.alert(VoiceAlertService.VoiceLine.BRAZIER_OUT, null);
        } else if (message.startsWith("The brazier is broken and shrapnel")) {
            alerts.alert(VoiceAlertService.VoiceLine.BRAZIER_BROKEN, null);
        } else if (message.startsWith("You have run out of bruma roots")) {
            alerts.alert(VoiceAlertService.VoiceLine.OUT_OF_ROOTS, null);
        } else if (message.startsWith("The cold of") || message.startsWith("The freezing cold attack")) {
            // Only when it stopped something (chopping carries on through it), as RuneLite's plugin does by default
            if (client.getTickCount() - busyTick <= BUSY_TICKS) {
                alerts.alert(VoiceAlertService.VoiceLine.WINTERTODT_HIT,
                    message.startsWith("The freezing") ? "snowfall" : "the cold");
            }
        }
    }

    /** Your warmth, 0-100 (the game keeps it as 0-1000). */
    int warmth() {
        return client.getVarbitValue(VarbitID.WINT_WARMTH) / 10;
    }

    /** The Wintertodt's energy (percent) from the game's box, or -1 if it isn't showing. */
    int energy() {
        return number(client.getWidget(InterfaceID.WintStatus.ENERGY_TITLE), PERCENT);
    }

    /** Your points this round from the game's box, or -1 if it isn't showing. */
    int points() {
        return number(client.getWidget(InterfaceID.WintStatus.POINTS), NUMBER);
    }

    static int number(Widget widget, Pattern pattern) {
        if (widget == null || widget.isHidden() || widget.getText() == null) {
            return -1;
        }
        return number(Text.removeTags(widget.getText().replace("<br>", " ")), pattern);
    }

    static int number(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text.replace(",", ""));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Roots and kindling you carry. */
    int[] carried() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) {
            return new int[]{0, 0};
        }
        return new int[]{inventory.count(ItemID.WINT_BRUMA_ROOT), inventory.count(ItemID.WINT_BRUMA_KINDLING)};
    }
}
