package com.snuffys.tickcounter;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Preferences;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
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
    /** Spells take 5 ticks whatever the weapon, except on these staves. */
    private static final int SPELL_SPEED = 5;
    /** How many recent clicks (and missed ticks) the summary covers. */
    static final int RECENT = 50;
    private static final int DING = SoundEffectID.TOWN_CRIER_BELL_DING;

    /** A click (how long after the tick, in ms) or a missed tick (-1), for the summary. */
    private final Deque<Integer> recent = new ArrayDeque<>();

    private final Client client;
    private final TickConfig config;
    private final ItemManager itemManager;
    private final CombatStyle combatStyle;
    private final InGame inGame;

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
    /** An attack animation started since the last tick (Follow Attack Speed). */
    private boolean attacked;
    private int speedWeaponId = Integer.MIN_VALUE;
    private int weaponSpeed = UNARMED_SPEED;
    /** Your attack delay and late ticks, for Follow Attack Speed. */
    private final AttackTimer attackTimer = new AttackTimer();
    private int attackTick = -1;
    /** Hidden by the Show/Hide hotkey (the overlays only; sounds keep playing). */
    @Getter
    private boolean hidden;

    @Inject
    TickCounter(Client client, TickConfig config, ItemManager itemManager, CombatStyle combatStyle, InGame inGame) {
        this.client = client;
        this.config = config;
        this.itemManager = itemManager;
        this.combatStyle = combatStyle;
        this.inGame = inGame;
    }

    /** Whether the count is shown or sounded at all. */
    boolean active() {
        return config.tickOverHead() || config.tickBoxes() || config.tickBar() || config.metronome()
            || timingClicks();
    }

    boolean timingClicks() {
        return config.clickTiming() || config.clickSounds();
    }

    /** Whether a click this long after the tick is late. */
    boolean isLate(int offset) {
        return offset >= config.clickLateMs();
    }

    /** Whether a click this long after the tick is in the ideal zone. */
    boolean isIdeal(int offset) {
        return offset >= config.clickIdealFrom() && offset < config.clickIdealTo() && !isLate(offset);
    }

    /** The colour for a click this long after the tick: green in the ideal zone, red late, else white. */
    java.awt.Color clickColor(int offset) {
        return isLate(offset) ? HudColors.BAD : isIdeal(offset) ? HudColors.GOOD : java.awt.Color.WHITE;
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
        // Starting an animation while fighting something is (near enough) an attack, unless it's one that never
        // is (eating, blocking a hit, alching)
        if (AttackTimer.NOT_ATTACKS.contains(player.getAnimation())) {
            return;
        }
        Actor target = player.getInteracting();
        if (target instanceof Player || target instanceof NPC && attackable((NPC) target)) {
            attacked = true;
        }
    }

    // Something you can fight: it has a combat level or an Attack option (not a fishing spot or a banker)
    private static boolean attackable(NPC npc) {
        if (npc.getCombatLevel() > 0) {
            return true;
        }
        NPCComposition composition = npc.getTransformedComposition();
        if (composition == null) {
            composition = npc.getComposition();
        }
        if (composition == null || composition.getActions() == null) {
            return false;
        }
        for (String action : composition.getActions()) {
            if ("Attack".equals(action)) {
                return true;
            }
        }
        return false;
    }

    // Eating delays your next attack
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!config.tickFollowWeapon()
            || event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }
        int delay = AttackTimer.foodDelay(event.getMessage());
        if (delay > 0) {
            attackTimer.ate(delay);
        }
    }

    // A weapon swapped on the attack tick itself: that attack goes at the new weapon's speed
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.WORN && config.tickFollowWeapon()
            && attackTick == client.getTickCount() && attackTimer.tracking()) {
            int speed = attackSpeed();
            if (speed != attackTimer.speed()) {
                attackTimer.respeed(speed);
                cycle = speed;
            }
        }
    }

    /** The Show/Hide hotkey. */
    void toggleHidden() {
        hidden = !hidden;
    }

    /** Shown again (the plugin stopping). */
    void show() {
        hidden = false;
    }

    /** The Reset Count hotkey: this tick becomes 1 of the cycle (and with Follow Attack Speed, no attack is remembered). */
    void resetCycle() {
        if (count > 0) {
            count = 1;
        }
        attackTimer.reset();
    }

    /**
     * The number over your head: the count, counting down to your next attack with Count Down To Attack, or how late
     * you are ("-2") once you could have attacked again but haven't.
     */
    String headText() {
        int late = lateTicks();
        if (late > 0) {
            return "-" + late;
        }
        if (countingDown() && attackTimer.tracking() && attackTimer.delay() > 0) {
            return String.valueOf(attackTimer.delay());
        }
        return String.valueOf(boxNumber(count));
    }

    /** How many ticks late your next attack is (Late Ticks), 0 when not late or not shown. */
    int lateTicks() {
        if (!config.tickFollowWeapon() || config.attackLateTicks() <= 0 || !attackTimer.tracking()) {
            return 0;
        }
        return attackTimer.late();
    }

    /** The number a tick's box shows: its place in the cycle, or the ticks left to your attack with Count Down To Attack. */
    int boxNumber(int tick) {
        return countingDown() ? Math.max(1, cycle) - tick + 1 : tick;
    }

    /** Count Down To Attack is in use (with Follow Attack Speed). */
    boolean countingDown() {
        return config.tickFollowWeapon() && config.attackCountDown();
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
        // Not until you're in the game: past loading and the welcome screen
        if (client.getGameState() != GameState.LOGGED_IN || !active() || !inGame.ready()) {
            count = 0;
            attacked = false;
            attackTimer.reset();
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
        if (follow) {
            followAttacks();
        } else {
            int methodCycle = TickMethods.cycle(config.tickMethod());
            cycle = methodCycle > 0 ? methodCycle : config.tickCycle();
            count = next(count, cycle, false);
            attackTimer.reset();
        }
        attacked = false;
        lastTickMillis = System.currentTimeMillis();

        // On the tick, unless Play Ahead sounds it early (onClientTick)
        if (config.metronomeAhead() <= 0 && !missed) {
            playMetronome(count);
        }
        aheadPlayed = false;
    }

    private void playMetronome(int tickCount) {
        TickConfig.Sound sound = config.metronome() ? config.metronomeSound() : null;
        if (sound != null && (tickCount == 1 || !config.metronomeFirstOnly())) {
            play(tickCount == 1 ? sound.first : sound.tick);
        }
    }

    // Play Ahead: the metronome for the coming tick, that long before it (ticks come every 600 ms); checked every
    // frame, so within a frame of the time
    private boolean aheadPlayed;

    @Subscribe
    public void onClientTick(net.runelite.api.events.ClientTick event) {
        int ahead = config.metronomeAhead();
        if (ahead <= 0 || aheadPlayed || lastTickMillis == 0 || !config.metronome()) {
            return;
        }
        long untilNext = lastTickMillis + 600 - System.currentTimeMillis();
        if (untilNext <= ahead) {
            aheadPlayed = true;
            // The coming tick's place in the cycle (an attack resetting it can't be known ahead)
            playMetronome(count >= Math.max(1, cycle) ? 1 : count + 1);
        }
    }

    // Follow Attack Speed: an attack (once the last one's delay has run out) starts the cycle at 1; the cycle is the
    // speed of that attack, so a weapon swapped later doesn't change it. Food delays hold the count on 1
    private void followAttacks() {
        int speedNow = attackSpeed();
        if (attackTimer.tick(attacked, speedNow, config.attackLateTicks())) {
            attackTick = client.getTickCount();
            cycle = attackTimer.speed();
            count = 1;
        } else if (attackTimer.tracking() && attackTimer.delay() > 0) {
            cycle = attackTimer.speed();
            count = followCount(cycle, attackTimer.delay());
        } else {
            cycle = attackTimer.tracking() ? attackTimer.speed() : speedNow;
            count = next(count, cycle, false);
        }
    }

    /** Where in the cycle you are with this many ticks to your next attack: 1 on the attack tick, held on 1 while food adds more. */
    static int followCount(int speed, int delay) {
        return Math.max(1, Math.min(speed, speed - delay + 1));
    }

    /** Your attack speed in ticks: the weapon's, one faster on Rapid, 5 for spells (6 on the Twinflame staff, 4 on the harmonised nightmare staff). */
    private int attackSpeed() {
        int weaponId = combatStyle.equippedWeaponId();
        if (combatStyle.isCasting()) {
            return weaponId == ItemID.TWINFLAME_STAFF ? 6 : weaponId == ItemID.NIGHTMARE_STAFF_HARMONISED ? 4 : SPELL_SPEED;
        }
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
