package com.snuffys.multitool;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Your potion buffs' state (active, expired, none) and their alerts: a buff running out, one running out while
 * others last, and the last one gone (with repeats). PvM/Slayer's HUD and the GemCrab Tracker's crab card both
 * show it, and each plugin has it on its own; with both installed separately only one of them speaks.
 */
@Singleton
class PotionAlerts {

    enum BuffState {
        UNPOTTED,
        ACTIVE,
        EXPIRED
    }

    // Who says the potion alerts when several of ours have them: the first of these that's running
    private static final List<String> SPEAKER_ORDER = Arrays.asList("multitool", "pvm", "gemcrab");

    private final BuffConfig config;
    private final PotionBuffs potionBuffs;
    private final Alerts alerts;
    private final PluginFamily family;

    @Getter
    private BuffState buffState = BuffState.UNPOTTED;
    /** Text for the Buff Status line when no buff rows are shown ("No Buffs", "EXPIRED", ...). */
    @Getter
    private String buffStatusText = "No Buffs";
    /** Buffs active as of the last game tick, each with its own end time (see ActiveBuffs). */
    @Getter
    private List<ActiveBuffs.Buff> activeBuffs = Collections.emptyList();
    // Buff name -> boost when its running-out warning played; a bigger boost (re-pot) re-arms it
    private final Map<String, Integer> buffWarnedAtBoost = new HashMap<>();
    private ActiveBuffs.Category lastEndedCategory = null;
    private String lastEndedBuffName = null;
    private int timesBuffExpiredSounded = 0;
    private Instant lastBuffExpiredSoundTime = Instant.EPOCH;

    @Inject
    PotionAlerts(BuffConfig config, PotionBuffs potionBuffs, Alerts alerts, PluginFamily family) {
        this.config = config;
        this.potionBuffs = potionBuffs;
        this.alerts = alerts;
        this.family = family;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            buffState = BuffState.UNPOTTED;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        potionBuffs.update();
        if (!config.enableBuffStatus()) {
            buffStatusText = "Disabled";
            buffState = BuffState.UNPOTTED;
            return;
        }

        int currentMax = potionBuffs.maxBoost();

        if (currentMax >= 4 || (currentMax > 0 && buffState == BuffState.ACTIVE)) {
            buffState = BuffState.ACTIVE;
            List<ActiveBuffs.Buff> buffs = potionBuffs.buffs();
            announceEndedBuffs(buffs);
            activeBuffs = buffs;
            buffStatusText = "ACTIVE";
            timesBuffExpiredSounded = 0;
            checkBuffRunningOut();
        } else if (currentMax == 0) {
            announceEndedBuffs(Collections.emptyList());
            activeBuffs = Collections.emptyList();
            buffWarnedAtBoost.clear();
            if (buffState == BuffState.ACTIVE) {
                buffState = BuffState.EXPIRED;
                buffStatusText = "EXPIRED";
                timesBuffExpiredSounded = 1;
                lastBuffExpiredSoundTime = Instant.now();
                say(lastExpiredLine(), null);
            } else if (buffState == BuffState.EXPIRED && config.repeatBuffAlert()
                && Duration.between(lastBuffExpiredSoundTime, Instant.now()).getSeconds() >= config.repeatBuffInterval()
                && timesBuffExpiredSounded <= config.maxBuffRepeats()) {
                timesBuffExpiredSounded++;
                lastBuffExpiredSoundTime = Instant.now();
                say(lastExpiredLine(), "still out");
            } else if (buffState == BuffState.UNPOTTED) {
                buffStatusText = "No Buffs";
            }
        }
    }

    // Only one of ours speaks these (in the Toolbelt there's just the one)
    private void say(VoiceAlertService.VoiceLine line, String detail) {
        if (family.isFirstOf(SPEAKER_ORDER)) {
            alerts.alert(line, detail);
        }
    }

    /**
     * True while the buff is inside the "running out" window (the same test the alert uses), so its line is
     * red only then and green again as soon as it's above the threshold.
     */
    boolean isBuffRunningOut(ActiveBuffs.Buff buff) {
        int warnSeconds = config.buffWarningSeconds();
        if (warnSeconds <= 0) {
            return false;
        }
        if (!buff.exact) {
            return buff.boost == 1;
        }
        return buff.millisLeft(System.currentTimeMillis()) <= warnSeconds * 1000L;
    }

    // Warn once each time a buff runs low
    private void checkBuffRunningOut() {
        long now = System.currentTimeMillis();
        for (ActiveBuffs.Buff buff : activeBuffs) {
            // Warn once each time a buff enters the "running out" window; leaving it (a new dose, a divine
            // refresh at the same boost) arms it again
            if (!isBuffRunningOut(buff)) {
                buffWarnedAtBoost.remove(buff.name);
                continue;
            }
            if (buffWarnedAtBoost.containsKey(buff.name)) {
                continue;
            }
            buffWarnedAtBoost.put(buff.name, buff.boost);
            // Before the decay timer is synced (no stat drop seen since login or a hop) the time left isn't
            // exact, but a buff down to +1 has at most one decay period left
            String left = (buff.exact ? "" : "under ") + BuffTimer.formatMillis(buff.millisLeft(now)) + " left";
            say(potionLine(buff.name, true, runningOutLine(buff.category)), left);
        }
    }

    // A buff that ends while others are still active gets its own "has run out"; when the last one ends,
    // the expired alert above speaks for it (using lastEndedCategory)
    private void announceEndedBuffs(List<ActiveBuffs.Buff> current) {
        for (ActiveBuffs.Buff before : activeBuffs) {
            boolean stillActive = current.stream().anyMatch(b -> b.name.equals(before.name));
            if (stillActive) {
                continue;
            }
            lastEndedCategory = before.category;
            lastEndedBuffName = before.name;
            if (!current.isEmpty()) {
                say(potionLine(before.name, false, expiredLine(before.category)), null);
            }
        }
    }

    // What to say for the buff that ran out last
    private VoiceAlertService.VoiceLine lastExpiredLine() {
        return lastEndedBuffName != null
            ? potionLine(lastEndedBuffName, false, expiredLine(lastEndedCategory))
            : expiredLine(lastEndedCategory);
    }

    // The potion's own line ("Divine super combat is running out"), else the boost-type line
    private static VoiceAlertService.VoiceLine potionLine(String buffName, boolean runningOut,
                                                          VoiceAlertService.VoiceLine fallback) {
        VoiceAlertService.VoiceLine line = VoiceAlertService.VoiceLine.forPotion(buffName, runningOut);
        return line != null ? line : fallback;
    }

    private static VoiceAlertService.VoiceLine runningOutLine(ActiveBuffs.Category category) {
        switch (category) {
            case MELEE: return VoiceAlertService.VoiceLine.MELEE_BUFF_LOW;
            case RANGED: return VoiceAlertService.VoiceLine.RANGED_BUFF_LOW;
            case MAGIC: return VoiceAlertService.VoiceLine.MAGIC_BUFF_LOW;
            case DEFENCE: return VoiceAlertService.VoiceLine.DEFENCE_BUFF_LOW;
            default: return VoiceAlertService.VoiceLine.BUFF_LOW;
        }
    }

    private static VoiceAlertService.VoiceLine expiredLine(ActiveBuffs.Category category) {
        if (category == null) {
            return VoiceAlertService.VoiceLine.BUFF_EXPIRED;
        }
        switch (category) {
            case MELEE: return VoiceAlertService.VoiceLine.MELEE_BUFF_EXPIRED;
            case RANGED: return VoiceAlertService.VoiceLine.RANGED_BUFF_EXPIRED;
            case MAGIC: return VoiceAlertService.VoiceLine.MAGIC_BUFF_EXPIRED;
            case DEFENCE: return VoiceAlertService.VoiceLine.DEFENCE_BUFF_EXPIRED;
            default: return VoiceAlertService.VoiceLine.BUFF_EXPIRED;
        }
    }
}
