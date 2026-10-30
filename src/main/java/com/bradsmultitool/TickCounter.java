package com.bradsmultitool;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Preferences;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Counts game ticks in cycles (1, 2, 3, 1, 2, 3...) for the tick overlays, plays the metronome, and times your
 * clicks against the tick (how long after it each landed, a ding or a dun). Only reads your clicks and shows or
 * sounds the ticks: it never clicks, changes a menu or does anything in the game.
 */
@Singleton
class TickCounter {

    private static final int UNARMED_SPEED = 4;
    /** How many recent clicks (and missed ticks) the summary covers. */
    static final int RECENT = 50;
    private static final int DING = SoundEffectID.TOWN_CRIER_BELL_DING;
    // The welcome screen after logging in ("Click here to play")
    private static final int WELCOME_SCREEN_GROUP = 378;

    /** A click (how long after the tick, in ms) or a missed tick (-1), for the summary. */
    private final Deque<Integer> recent = new ArrayDeque<>();

    private final Client client;
    private final TickConfig config;
    private final ItemManager itemManager;
    private final CombatStyle combatStyle;

    /** Where in the cycle this tick is (1 is the first tick), 0 before the first tick. */
    @Getter
    private int count;
    /** The cycle length the count is using. */
    @Getter
    private int cycle;
    /** When the last tick came (System.currentTimeMillis), for the box's flash. */
    @Getter
    private long lastTickMillis;
    /** How long after the tick your last click landed, and when (System.currentTimeMillis), for the over-head text. */
    @Getter
    private int lastClickOffset;
    @Getter
    private long lastClickMillis;
    private int clicksThisTick;
    private boolean clickedLastTick;
    private boolean attacked;
    private int speedWeaponId = Integer.MIN_VALUE;
    private int weaponSpeed = UNARMED_SPEED;

    @Inject
    TickCounter(Client client, TickConfig config, ItemManager itemManager, CombatStyle combatStyle) {
        this.client = client;
        this.config = config;
        this.itemManager = itemManager;
        this.combatStyle = combatStyle;
    }

    /** Whether the count is shown or sounded at all. */
    boolean active() {
        return config.tickOverHead() || config.tickBoxes() || config.tickBar() || config.metronomeSound() != TickConfig.Sound.OFF
            || timingClicks();
    }

    boolean timingClicks() {
        return config.clickTiming() || config.clickSounds();
    }

    /** Whether a click this long after the tick is late. */
    boolean isLate(int offset) {
        return offset >= config.clickLateMs();
    }

    /** The last clicks (ms after the tick) and missed ticks (-1), oldest first. */
    Iterable<Integer> recent() {
        return recent;
    }

    /** The next tick's place in the cycle: 1 after an attack (or once the cycle is done), else one more. */
    static int next(int count, int cycle, boolean attacked) {
        return attacked || count >= cycle ? 1 : count + 1;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Player player = client.getLocalPlayer();
        if (event.getActor() != player || player == null || player.getAnimation() == -1) {
            return;
        }
        // Starting an animation while fighting something is (near enough) an attack
        Actor target = player.getInteracting();
        if (target instanceof NPC || target instanceof Player) {
            attacked = true;
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        // Our own right-click entries (Track for goal...) aren't game clicks
        if (!timingClicks() || lastTickMillis == 0 || event.getMenuAction() == MenuAction.RUNELITE) {
            return;
        }
        long now = System.currentTimeMillis();
        lastClickOffset = (int) Math.max(0, now - lastTickMillis);
        lastClickMillis = now;
        clicksThisTick++;
        remember(lastClickOffset);
        if (config.clickSounds()) {
            play(isLate(lastClickOffset) ? config.clickLateSound().id : DING);
        }
    }

    private void remember(int offset) {
        recent.addLast(offset);
        while (recent.size() > RECENT) {
            recent.removeFirst();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN || !active() || client.getWidget(WELCOME_SCREEN_GROUP, 0) != null) {
            count = 0;
            attacked = false;
            lastTickMillis = 0;
            clicksThisTick = 0;
            clickedLastTick = false;
            recent.clear();
            return;
        }
        // 1-ticking: a tick with no click, straight after one with a click, is a missed tick
        boolean missed = config.clickEveryTick() && timingClicks() && clickedLastTick && clicksThisTick == 0;
        clickedLastTick = clicksThisTick > 0;
        clicksThisTick = 0;
        if (missed) {
            remember(-1);
            if (config.clickSounds()) {
                play(config.clickLateSound().id);
            }
        }

        boolean follow = config.tickFollowWeapon();
        cycle = follow ? attackSpeed() : config.tickCycle();
        count = next(count, cycle, follow && attacked);
        attacked = false;
        lastTickMillis = System.currentTimeMillis();

        TickConfig.Sound sound = config.metronomeSound();
        if (sound != TickConfig.Sound.OFF && !missed && (count == 1 || !config.metronomeFirstOnly())) {
            play(count == 1 ? sound.first : sound.tick);
        }
    }

    /** Your weapon's attack speed in ticks, one faster on Rapid. */
    private int attackSpeed() {
        int weaponId = combatStyle.equippedWeaponId();
        if (weaponId != speedWeaponId) {
            speedWeaponId = weaponId;
            ItemStats stats = weaponId > 0 ? itemManager.getItemStats(weaponId) : null;
            int speed = stats != null && stats.getEquipment() != null ? stats.getEquipment().getAspeed() : 0;
            weaponSpeed = speed > 0 ? speed : UNARMED_SPEED;
        }
        return Math.max(1, combatStyle.isRapid() ? weaponSpeed - 1 : weaponSpeed);
    }

    // Like RuneLite's own Metronome plugin: plays at the chosen volume by setting the sound effect volume
    // for this one sound and putting the player's setting back straight after.
    private void play(int soundId) {
        int volume = config.metronomeVolume();
        if (volume <= 0) {
            client.playSoundEffect(soundId);
            return;
        }
        Preferences preferences = client.getPreferences();
        int before = preferences.getSoundEffectVolume();
        preferences.setSoundEffectVolume(volume * 127 / 100);
        client.playSoundEffect(soundId);
        preferences.setSoundEffectVolume(before);
    }
}
