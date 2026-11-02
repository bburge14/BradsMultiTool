package com.bradsmultitool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.gameval.AnimationID;

/**
 * Your own attack delay, for Follow Attack Speed: how many ticks until you can attack again, and how many ticks
 * late you are once you could have. Only ever about your attacks, never a monster's.
 *
 * The cases follow the Attack Timer Metronome plugin (github.com/ngraves95/attacktimer, BSD-2-Clause, by Nick
 * Graves and contributors): an attack only counts once the last one's delay has run out; eating adds its delay
 * (3 ticks, 2 for fast foods, 4 for the Stymphike tartare) on top, also when it lands on the attack tick; the
 * count of late ticks stops after a while; and animations that aren't attacks (eating, blocking a hit,
 * alching) never count as one.
 */
final class AttackTimer {

    /** How far the delay counts below zero, so food eaten long after your last attack adds nothing. */
    static final int MOST_LATE = 20;

    /** Animations that are never an attack, even while you're fighting something. */
    static final Set<Integer> NOT_ATTACKS = new HashSet<>(Arrays.asList(
        AnimationID.HUMAN_EAT, AnimationID.HUMAN_KILLERWATT_ELECTRICSHOCK,
        AnimationID.HUMAN_CASTLOWLVLALCHEMY, AnimationID.HUMAN_CASTHIGHLVLALCHEMY, AnimationID.HUMAN_PICKPOCKET,
        // Blocking a hit, for each kind of weapon
        AnimationID.HUMAN_AXE_BLOCK, AnimationID.HUMAN_DHSWORD_BLOCK, AnimationID.BRAIN_PLAYER_ANCHOR_DEFEND,
        AnimationID.IVANDIS_FLAIL_DEFEND, AnimationID.HUMAN_SPEAR_BLOCK, AnimationID.HUMAN_DINHS_BULWARK_BLOCK,
        AnimationID.WILD_CAVE_CHAINMACE_DEFEND, AnimationID.HUMAN_CHINCHOMPA_DEFEND, AnimationID.HUMAN_DDAGGER_BLOCK,
        AnimationID.WARGUILD_PARRY_DEFEND, AnimationID.HUMAN_SWORD_DEF, AnimationID.DH_SWORD_UPDATE_DEFEND,
        AnimationID.HUMAN_DSPEAR_BLOCK, AnimationID.HUMAN_STAFFORB_BLOCK, AnimationID.HUMAN_BLUNT_BLOCK,
        AnimationID.SLAYER_GRANITE_MAUL_DEFEND, AnimationID.HUMAN_SCYTHE_BLOCK, AnimationID.HUMAN_SHIELD_DEFENCE,
        AnimationID.HUMAN_ZAMORAKSPEAR_BLOCK, AnimationID.HUMAN_STAFF_BLOCK, AnimationID.HUMAN_UNARMEDBLOCK,
        AnimationID.BARROW_GUTHAN_DEFEND, AnimationID.SLAYER_ABYSSAL_WHIP_DEFEND,
        AnimationID.HUMAN_WEAPONS_CRIMSON_KISTEN_DEF, AnimationID.HUMAN_HALLOWFELL_DEFEND,
        AnimationID.TFA_HUMAN_OBLIGATOR_DEF, AnimationID.TFA_HUMAN_OBLIGATOR_DEF_FEMALE,
        AnimationID.TFA_HUMAN_ZORYA_TOME_DEFEND, AnimationID.TFA_HUMAN_ZORYA_TOME_DEFEND_FEMALE));

    // The chat lines for eating (and the two drinks that delay attacks like food); potions don't delay attacks
    private static final String TARTARE = "Your stomach doesn't like it... but it heals some health";
    private static final Pattern EAT = Pattern.compile(
        "^(You eat|You drink the lumpy potion|You drink the wine|" + Pattern.quote(TARTARE) + ")", Pattern.CASE_INSENSITIVE);
    // Fast foods (the name as the chat line gives it): karambwans, halibut and the gnome foods
    private static final Pattern FAST_FOOD = Pattern.compile("karambwan|halibut|worm hole|tangled toads legs|veg ball|"
        + "chocolate bomb|worm crunchies|toad crunchies|choc chip crunchies|spicy crunchies|fruit batta|"
        + "cheese and tomato batta|toad batta|vegetable batta|worm batta", Pattern.CASE_INSENSITIVE);

    private int delay;
    private int speed;
    private boolean tracking;
    private int food;

    /** How many ticks a chat line's eating delays your next attack (0: not eating). */
    static int foodDelay(String message) {
        if (message == null || !EAT.matcher(message).find()) {
            return 0;
        }
        if (message.startsWith(TARTARE)) {
            return 4;
        }
        return FAST_FOOD.matcher(message).find() ? 2 : 3;
    }

    /**
     * One game tick. {@code attackStarted}: an attack animation started since the last tick; {@code speedNow}: your
     * attack speed for an attack now; {@code mostLate}: how many late ticks to count before giving up. True when
     * this tick is an attack (the delay had run out).
     */
    boolean tick(boolean attackStarted, int speedNow, int mostLate) {
        delay = Math.max(-MOST_LATE, delay - 1);
        boolean attack = attackStarted && delay <= 0;
        if (attack) {
            delay = speedNow;
            speed = speedNow;
            tracking = true;
        }
        // After the attack: food eaten on the attack tick still delays the next one
        delay += food;
        food = 0;
        if (tracking && late() > mostLate) {
            tracking = false;
        }
        return attack;
    }

    /** You ate (or drank wine): the next attack waits this many more ticks. */
    void ate(int ticks) {
        food += ticks;
    }

    /** The weapon changed on the attack tick itself (a swap the same tick): the attack goes at the new speed. */
    void respeed(int newSpeed) {
        delay += newSpeed - speed;
        speed = newSpeed;
    }

    void reset() {
        delay = 0;
        speed = 0;
        tracking = false;
        food = 0;
    }

    /** Ticks until you can attack again (food included); 0 or less once you can. */
    int delay() {
        return delay;
    }

    /** The attack speed of your last attack. */
    int speed() {
        return speed;
    }

    /** Since your last attack, until the late ticks have run out. */
    boolean tracking() {
        return tracking;
    }

    /** How many ticks late you are: 1 on the tick you could first attack again but didn't, 0 before then. */
    int late() {
        return delay <= 0 ? 1 - delay : 0;
    }
}
