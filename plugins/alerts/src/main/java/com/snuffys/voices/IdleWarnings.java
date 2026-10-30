package com.snuffys.voices;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.eventbus.Subscribe;

/**
 * The idle warnings every one of Snuffy's plugins has: "You are idle" once you stop doing what you were
 * doing (and you're not banking or in a dialogue), and the idle logout warning before the game logs you out.
 * A feature can add what counts as busy for it (attacking the Gemstone Crab, whose hits come slowly).
 */
@Singleton
class IdleWarnings {

    // Client idle timeouts in 20ms client cycles: 5 minutes normally, 20 minutes (clicks/keys only) in combat
    private static final int DEFAULT_IDLE_LOGOUT_CYCLES = 15000;
    private static final int COMBAT_IDLE_LOGOUT_CYCLES = 60000;
    // Bank, bank side panel, deposit box, bank PIN, shop, collection box, GE, GE side panel
    private static final int[] BANKING_INTERFACES = {12, 15, 192, 213, 300, 402, 465, 631};
    // Talking or choosing: NPC and player dialogue, options, sprite and message boxes, double item box,
    // Make-X; you're busy with the game, not idle, while one is open
    private static final int[] DIALOGUE_INTERFACES = {231, 217, 219, 193, 229, 11, 270};
    // An action counts towards the AFK alert only while it's giving XP (an XP drop in the last 30 seconds, for slow trees)
    private static final int XP_ACTION_TICKS = 50;
    // XP within this many ticks (about 5s) counts as doing something for the AFK alert
    private static final int RECENT_XP_TICKS = 8;
    // Character idle: what counts as having been doing something before going AFK
    private static final int MIN_ACTION_TICKS = 5;
    // Ticks of "in combat" after you hit or are hit (same 8-tick window as RuneLite's Idle Notifier)
    private static final int COMBAT_WINDOW_TICKS = 8;

    private final Client client;
    private final CommonConfig config;
    private final Alerts alerts;
    private final Notifier notifier;
    private final PluginIdentity identity;
    private final PluginFamily family;
    private final List<BooleanSupplier> busy = new CopyOnWriteArrayList<>();

    @Getter
    private boolean logoutImminent = false;
    @Getter
    private int engineIdleSeconds = 0;
    private int timesIdleAlerted = 0;
    private int idleLogoutAfterSeconds = 300;
    private int combatTicks = 0;
    private WorldPoint lastPlayerLocation = null;
    private boolean characterWasActive = false;
    private boolean characterIdleAlerted = false;
    private int lastActiveTick = 0;
    private int actionTicks = 0;
    private int lastXpTick = -1000;
    // Each skill's XP as last reported: logging in reports every skill's, which isn't training
    private final Map<Skill, Integer> knownXp = new EnumMap<>(Skill.class);
    private Instant lastIdleAlertTime = Instant.EPOCH;

    @Inject
    IdleWarnings(Client client, CommonConfig config, Alerts alerts, Notifier notifier, PluginIdentity identity, PluginFamily family) {
        this.client = client;
        this.config = config;
        this.alerts = alerts;
        this.notifier = notifier;
        this.identity = identity;
        this.family = family;
    }

    /** Something a feature counts as being busy (not idle) while it's true. */
    void addBusy(BooleanSupplier check) {
        busy.add(check);
    }

    void removeBusy(BooleanSupplier check) {
        busy.remove(check);
    }

    private boolean busyElsewhere() {
        for (BooleanSupplier check : busy) {
            if (check.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // With several of ours installed, only one says these
        if (client.getGameState() != GameState.LOGGED_IN || !family.handlesShared()) {
            return;
        }
        Player player = client.getLocalPlayer();
        if (player != null) {
            updateCharacterIdle(player);
        }
        updateIdleState();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            characterWasActive = false;
            actionTicks = 0;
            lastPlayerLocation = null;
            logoutImminent = false;
        }
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            // Maybe another account next
            knownXp.clear();
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        // Only XP going up is training: not the XP the game reports at login, nor a boost or drain (same XP)
        Integer before = knownXp.put(event.getSkill(), event.getXp());
        if (before != null && event.getXp() > before) {
            lastXpTick = client.getTickCount();
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getActor() == client.getLocalPlayer() || event.getHitsplat().isMine()) {
            combatTicks = COMBAT_WINDOW_TICKS;
        }
    }

    private boolean isWidgetVisible(int group) {
        Widget widget = client.getWidget(group, 0);
        return widget != null && !widget.isHidden();
    }

    private void updateCharacterIdle(Player player) {
        WorldPoint location = player.getWorldLocation();
        boolean moved = lastPlayerLocation != null && !lastPlayerLocation.equals(location);
        lastPlayerLocation = location;
        int tick = client.getTickCount();
        // Gaining XP means you're not idle, even between attacks when you aren't animating or your target
        // flickers (big monsters like the Gemstone Crab); so does attacking the crab
        boolean doing = player.getAnimation() != -1 || player.getInteracting() != null
            || tick - lastXpTick <= RECENT_XP_TICKS || busyElsewhere();

        // Banking, a shop or a dialogue: not idle, and the idle clock starts again once it's closed
        if (isBankingInterfaceOpen() || isDialogueOpen()) {
            characterWasActive = false;
            characterIdleAlerted = false;
            actionTicks = 0;
            lastActiveTick = tick;
            return;
        }
        if (doing && tick - lastXpTick <= XP_ACTION_TICKS) {
            actionTicks++;
            if (actionTicks >= MIN_ACTION_TICKS) {
                characterWasActive = true;
            }
        }
        if (doing || moved) {
            characterIdleAlerted = false;
            lastActiveTick = tick;
            return;
        }
        if (!config.characterIdleAlert() || !characterWasActive || characterIdleAlerted) {
            return;
        }

        // Idle is the shorter of: time since the character stopped, time since your last click or key press
        long sinceInputMillis = Math.min(client.getKeyboardIdleTicks() * 20L,
            System.currentTimeMillis() - client.getMouseLastPressedMillis());
        int idleSeconds = (int) Math.min((tick - lastActiveTick) * 0.6, sinceInputMillis / 1000);
        if (idleSeconds >= config.characterIdleSeconds()) {
            characterIdleAlerted = true;
            characterWasActive = false;
            actionTicks = 0;
            alerts.alert(VoiceAlertService.VoiceLine.CHARACTER_IDLE, idleSeconds + " seconds");
        }
    }

    private boolean isDialogueOpen() {
        for (int group : DIALOGUE_INTERFACES) {
            if (isWidgetVisible(group)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBankingInterfaceOpen() {
        for (int group : BANKING_INTERFACES) {
            if (isWidgetVisible(group)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Idle logout follows the game's rules (as RuneLite's Idle Notifier does): out of combat you're logged
     * out after the client idle timeout (5 minutes, or what the Logout Timer plugin sets) without mouse or
     * keyboard input. In combat that timeout doesn't apply; you're only logged out after 20 minutes
     * without a mouse click or key press (moving the mouse doesn't count).
     */
    private void updateIdleState() {
        if (combatTicks > 0) {
            combatTicks--;
        }
        int idleCycles;
        int timeoutCycles;
        if (combatTicks > 0) {
            long sinceClick = (System.currentTimeMillis() - client.getMouseLastPressedMillis()) / 20;
            idleCycles = (int) Math.min(client.getKeyboardIdleTicks(), sinceClick);
            timeoutCycles = COMBAT_IDLE_LOGOUT_CYCLES;
        } else {
            idleCycles = Math.min(client.getKeyboardIdleTicks(), client.getMouseIdleTicks());
            timeoutCycles = client.getIdleTimeout() > 0 ? client.getIdleTimeout() : DEFAULT_IDLE_LOGOUT_CYCLES;
        }
        engineIdleSeconds = idleCycles / 50;
        idleLogoutAfterSeconds = timeoutCycles / 50;

        int logoutAfter = idleLogoutAfterSeconds;
        int warnAt = logoutAfter - Math.max(5, Math.min(logoutAfter - 5, config.idleWarningSeconds()));
        boolean imminent = engineIdleSeconds >= warnAt;
        if (!imminent) {
            logoutImminent = false;
            timesIdleAlerted = 0;
            return;
        }

        boolean alert = false;
        if (!logoutImminent) {
            alert = true;
            if (alerts.ready()) {
                notifier.notify(identity.chatName + ": You are about to be logged out for inactivity!");
            }
        } else if (config.repeatIdleAlert()
            && Duration.between(lastIdleAlertTime, Instant.now()).getSeconds() >= config.repeatIdleInterval()
            && timesIdleAlerted <= config.maxIdleRepeats()) {
            alert = true;
        }
        logoutImminent = true;

        if (alert) {
            timesIdleAlerted++;
            lastIdleAlertTime = Instant.now();
            if (timesIdleAlerted == 1) {
                alerts.alert(VoiceAlertService.VoiceLine.IDLE_LOGOUT, null);
            } else {
                alerts.speak(VoiceAlertService.VoiceLine.IDLE_LOGOUT);
            }
        }
    }

    public int getSecondsUntilIdleLogout() {
        return Math.max(0, idleLogoutAfterSeconds - engineIdleSeconds);
    }

}
