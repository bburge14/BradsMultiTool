package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Preferences;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Counts game ticks in cycles (1, 2, 3, 1, 2, 3...) for the tick overlays, and plays the metronome. Only shows
 * and sounds the ticks: it never clicks or changes anything in the game.
 */
@Singleton
class TickCounter {

    private static final int UNARMED_SPEED = 4;

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
        return config.tickDisplay() != TickConfig.Display.OFF || config.metronomeSound() != TickConfig.Sound.OFF;
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
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN || !active()) {
            count = 0;
            attacked = false;
            return;
        }
        boolean follow = config.tickFollowWeapon();
        cycle = follow ? attackSpeed() : config.tickCycle();
        count = next(count, cycle, follow && attacked);
        attacked = false;
        lastTickMillis = System.currentTimeMillis();

        TickConfig.Sound sound = config.metronomeSound();
        if (sound != TickConfig.Sound.OFF && (count == 1 || !config.metronomeFirstOnly())) {
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
