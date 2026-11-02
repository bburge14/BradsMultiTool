package com.snuffys.pvm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Your buffs and debuffs for the buff bar, like RS3's: potion boosts, antifire, stamina, poison protection,
 * overload, prayer regeneration, vengeance, staff of the dead's special, the imbued heart's cooldown and other
 * boosted stats; poison, venom, frozen, teleblocked and drained stats. Timers count down from when the game last
 * set them, in the same units as RuneLite's Timers and Buffs plugin (BSD-2), which these follow. Only reads the
 * game.
 */
@Singleton
class StatusEffects {

    /** One icon on the bar. */
    static final class Effect {
        final String name;
        /** An item's picture (itemId > 0), a sprite (spriteId > 0), else the skill's icon. */
        final int itemId;
        final int spriteId;
        final Skill skill;
        /** Small text in the corner ("+19", "-5"), or null. */
        final String amount;
        /** When it runs out (System.currentTimeMillis), 0 when it doesn't have a timer. */
        final long endMillis;
        final boolean exact;
        final boolean debuff;

        Effect(String name, int itemId, Skill skill, String amount, long endMillis, boolean exact, boolean debuff) {
            this(name, itemId, 0, skill, amount, endMillis, exact, debuff);
        }

        Effect(String name, int itemId, int spriteId, Skill skill, String amount, long endMillis, boolean exact, boolean debuff) {
            this.name = name;
            this.itemId = itemId;
            this.spriteId = spriteId;
            this.skill = skill;
            this.amount = amount;
            this.endMillis = endMillis;
            this.exact = exact;
            this.debuff = debuff;
        }
    }

    // Game variables and their units in ticks (RuneLite's Timers and Buffs plugin)
    private static final int VARBIT_STAMINA_DURATION = 24;   // 10 ticks each
    private static final int VARBIT_ANTIFIRE = 3981;         // 30 ticks each
    private static final int VARBIT_SUPER_ANTIFIRE = 6101;   // 20 ticks each
    private static final int VARP_POISON = 102;              // < 0: protection, 30 ticks each; > 0 poisoned
    private static final int VENOM_PROTECTION_CUTOFF = -38;  // below this, antivenom
    private static final int VENOMED = 1_000_000;            // from this up, venom
    private static final int OVERLOAD_STEP_TICKS = 25;       // overloads count down in 25-tick steps
    private static final int IMBUED_HEART_STEP_TICKS = 10;
    private static final int PRAYER_REGEN_STEP_TICKS = 12;
    private static final int TELEBLOCK_OFFSET = 100;         // the teleblock varbit is ticks left + 100
    private static final long VENGEANCE_COOLDOWN_MILLIS = 30_000;
    private static final long STAFF_OF_THE_DEAD_MILLIS = 60_000;
    private static final int ICE_BARRAGE_TICKS = 32;

    // The game's messages (RuneLite's Timers and Buffs plugin)
    private static final String FROZEN_MESSAGE = "You have been frozen!";
    private static final String SOTD_MESSAGE = "Spirits of deceased evildoers offer you their protection";
    private static final String SOTD_EXPIRED_MESSAGE = "Your protection fades away";

    /** Freeze spells on you: impact graphic, ticks, icon. Ice burst and blitz use each other's graphic, as in core. */
    private static final int[][] FREEZES = {
        {SpotanimID.BIND_IMPACT, 8, SpriteID.Magicon2.BIND},
        {SpotanimID.SNARE_IMPACT, 16, SpriteID.Magicon2.SNARE},
        {SpotanimID.ENTANGLE_IMPACT, 24, SpriteID.Magicon2.ENTANGLE},
        {SpotanimID.ICE_RUSH_IMPACT, 8, SpriteID.Magicon2.ICE_RUSH},
        {SpotanimID.ICE_BLITZ_IMPACT, 16, SpriteID.Magicon2.ICE_BURST},
        {SpotanimID.ICE_BURST_IMPACT, 24, SpriteID.Magicon2.ICE_BLITZ},
    };

    // Pictures: each potion's 4-dose item
    private static final int STAMINA_POTION = 12625;
    private static final int ANTIFIRE_POTION = 2452;
    private static final int SUPER_ANTIFIRE_POTION = 21978;
    private static final int ANTIPOISON = 2446;
    private static final int ANTIVENOM = 12913;
    private static final int WEAPON_POISON = 187;
    private static final int ZULRAHS_SCALES = 12934;
    private static final int SLOT_WEAPON = 3;

    private final Client client;
    private final PotionAlerts potionAlerts;
    private long staminaEnd;
    private long antifireEnd;
    private long superAntifireEnd;
    private long protectionEnd;
    private boolean venomProtection;
    private long overloadEnd;
    private int overloadRefreshTick = -1;
    private long smellingSaltsEnd;
    private long prayerRegenEnd;
    private long imbuedHeartEnd;
    private long teleblockEnd;
    private long vengeanceCooldownEnd;
    private long sotdEnd;
    // Frozen: until when, the tick it started, its icon, and where you stood (moving means it's over)
    private long frozenEnd;
    private int frozenTick = -1;
    private int frozenSprite;
    private WorldPoint lastPoint;
    // Timers already running when the plugin starts are read on the first tick
    private boolean synced;
    private List<Effect> effects = Collections.emptyList();

    @Inject
    StatusEffects(Client client, PotionAlerts potionAlerts) {
        this.client = client;
        this.potionAlerts = potionAlerts;
    }

    /** This tick's buffs, then debuffs. */
    List<Effect> effects() {
        return effects;
    }

    static long endOf(int value, int ticksEach) {
        return value > 0 ? System.currentTimeMillis() + value * ticksEach * 600L : 0;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        int value = event.getValue();
        if (event.getVarpId() == VARP_POISON) {
            venomProtection = value < VENOM_PROTECTION_CUTOFF;
            protectionEnd = value < 0 ? endOf(venomProtection ? -value + VENOM_PROTECTION_CUTOFF : -value, 30) : 0;
            return;
        }
        switch (event.getVarbitId()) {
            case VARBIT_STAMINA_DURATION:
                staminaEnd = endOf(value, 10);
                break;
            case VARBIT_ANTIFIRE:
                antifireEnd = endOf(value, 30);
                break;
            case VARBIT_SUPER_ANTIFIRE:
                superAntifireEnd = endOf(value, 20);
                break;
            case VarbitID.NZONE_OVERLOAD_POTION_EFFECTS:
            case VarbitID.RAIDS_OVERLOAD_TIMER:
            case VarbitID.DEADMAN_OVERLOAD_POTION_EFFECTS:
                overload(value);
                break;
            case VarbitID.TOA_MIDRAIDLOOT_STATS_TIMER:
                smellingSaltsEnd = endOf(value, OVERLOAD_STEP_TICKS);
                break;
            case VarbitID.PRAYER_REGENERATION_POTION_TIMER:
                prayerRegenEnd = endOf(value, PRAYER_REGEN_STEP_TICKS);
                break;
            case VarbitID.IMBUED_HEART_TIMER:
                imbuedHeartEnd = endOf(value, IMBUED_HEART_STEP_TICKS);
                break;
            case VarbitID.TELEBLOCK_CYCLES:
                teleblockEnd = endOf(value - TELEBLOCK_OFFSET, 1);
                break;
            case VarbitID.VENGEANCE_TIMELIMIT:
                vengeanceCooldownEnd = value == 1 ? System.currentTimeMillis() + VENGEANCE_COOLDOWN_MILLIS : 0;
                break;
            default:
                break;
        }
    }

    // Steps of 25 ticks, the first ending 25 ticks after the step began (as RuneLite's Timers plugin works it out)
    private void overload(int steps) {
        int tick = client.getTickCount();
        if (steps <= 0) {
            overloadRefreshTick = -1;
            overloadEnd = 0;
            return;
        }
        if (overloadRefreshTick - tick <= 0) {
            overloadRefreshTick = tick + OVERLOAD_STEP_TICKS;
        }
        overloadEnd = System.currentTimeMillis() + (overloadRefreshTick - tick + (steps - 1) * OVERLOAD_STEP_TICKS) * 600L;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        String message = Text.removeTags(event.getMessage());
        if (message.contains(SOTD_MESSAGE)) {
            sotdEnd = System.currentTimeMillis() + STAFF_OF_THE_DEAD_MILLIS;
        } else if (message.contains(SOTD_EXPIRED_MESSAGE)) {
            sotdEnd = 0;
        } else if (message.endsWith(FROZEN_MESSAGE)) {
            // An ice spell: barrage's length unless its graphic this tick says a shorter one
            freeze(ICE_BARRAGE_TICKS, SpriteID.Magicon2.ICE_BARRAGE);
        }
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {
        Actor actor = event.getActor();
        if (actor == null || actor != client.getLocalPlayer()) {
            return;
        }
        int graphic = actor.getGraphic();
        for (int[] freeze : FREEZES) {
            boolean ice = freeze[0] != SpotanimID.BIND_IMPACT && freeze[0] != SpotanimID.SNARE_IMPACT
                && freeze[0] != SpotanimID.ENTANGLE_IMPACT;
            // Binds are known by their graphic alone; ice spells only with the frozen message on the same tick
            if (graphic == freeze[0] && (!ice || frozenTick == client.getTickCount())) {
                freeze(freeze[1], freeze[2]);
            }
        }
    }

    private void freeze(int ticks, int sprite) {
        frozenTick = client.getTickCount();
        frozenEnd = System.currentTimeMillis() + ticks * 600L;
        frozenSprite = sprite;
    }

    // Staff of the dead's protection ends when you take the staff off (the staffs of light and balance have the same
    // special)
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.WORN || sotdEnd == 0) {
            return;
        }
        Item weapon = event.getItemContainer().getItem(SLOT_WEAPON);
        String name = weapon == null || weapon.getId() <= 0 ? "" : client.getItemDefinition(weapon.getId()).getName().toLowerCase();
        if (!name.contains("staff of the dead") && !name.contains("staff of light") && !name.contains("staff of balance")) {
            sotdEnd = 0;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            staminaEnd = antifireEnd = superAntifireEnd = protectionEnd = 0;
            overloadEnd = smellingSaltsEnd = prayerRegenEnd = imbuedHeartEnd = teleblockEnd = 0;
            frozenEnd = 0;
            // Kept through a world hop, as RuneLite's Timers and Buffs keeps them (the game doesn't send them again)
            if (event.getGameState() == GameState.LOGIN_SCREEN) {
                vengeanceCooldownEnd = sotdEnd = 0;
            }
            overloadRefreshTick = -1;
            synced = false;
            effects = Collections.emptyList();
        }
    }

    // Timers already running when the plugin started (their changes come through onVarbitChanged after this)
    private void sync() {
        synced = true;
        if (prayerRegenEnd == 0) {
            prayerRegenEnd = endOf(client.getVarbitValue(VarbitID.PRAYER_REGENERATION_POTION_TIMER), PRAYER_REGEN_STEP_TICKS);
        }
        if (imbuedHeartEnd == 0) {
            imbuedHeartEnd = endOf(client.getVarbitValue(VarbitID.IMBUED_HEART_TIMER), IMBUED_HEART_STEP_TICKS);
        }
        if (teleblockEnd == 0) {
            teleblockEnd = endOf(client.getVarbitValue(VarbitID.TELEBLOCK_CYCLES) - TELEBLOCK_OFFSET, 1);
        }
        if (overloadEnd == 0) {
            int steps = Math.max(client.getVarbitValue(VarbitID.NZONE_OVERLOAD_POTION_EFFECTS),
                client.getVarbitValue(VarbitID.RAIDS_OVERLOAD_TIMER));
            overload(steps);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        if (!synced) {
            sync();
        }
        long now = System.currentTimeMillis();
        trackFreeze();
        List<Effect> buffs = new ArrayList<>();
        List<Effect> debuffs = new ArrayList<>();

        // An overload boosts every combat stat at once: one icon instead of potion guesses and boosted stats
        boolean overloaded = overloadEnd > now || smellingSaltsEnd > now;
        if (overloaded) {
            boolean salts = smellingSaltsEnd > now;
            buffs.add(new Effect(salts ? "Smelling salts" : "Overload",
                salts ? ItemID.TOA_SUPPLY_STATS_2 : ItemID.NZONE4DOSEOVERLOADPOTION, 0, null, null,
                salts ? smellingSaltsEnd : overloadEnd, salts, false));
        } else {
            for (ActiveBuffs.Buff buff : potionAlerts.getActiveBuffs()) {
                buffs.add(new Effect(buff.name, buff.iconItemId, null, "+" + buff.boost, buff.endMillis, buff.exact, false));
            }
        }
        addTimed(buffs, "Super antifire", SUPER_ANTIFIRE_POTION, superAntifireEnd, now);
        if (superAntifireEnd <= now) {
            addTimed(buffs, "Antifire", ANTIFIRE_POTION, antifireEnd, now);
        }
        addTimed(buffs, "Stamina", STAMINA_POTION, staminaEnd, now);
        addTimed(buffs, venomProtection ? "Antivenom" : "Antipoison", venomProtection ? ANTIVENOM : ANTIPOISON, protectionEnd, now);
        addTimed(buffs, "Prayer regeneration", ItemID._4DOSE1PRAYER_REGENERATION, prayerRegenEnd, now);
        addTimed(buffs, "Staff of the dead special", ItemID.SOTD, sotdEnd, now);
        if (client.getVarbitValue(VarbitID.VENGEANCE_REBOUND) == 1) {
            buffs.add(new Effect("Vengeance (ready to rebound)", 0, SpriteID.LunarMagicOn.VENGEANCE, null, null, 0, true, false));
        }
        if (vengeanceCooldownEnd > now) {
            buffs.add(new Effect("Vengeance cooldown", 0, SpriteID.LunarMagicOn.VENGEANCE, null, null, vengeanceCooldownEnd, true, false));
        }
        addTimed(buffs, "Imbued heart cooldown", ItemID.IMBUED_HEART, imbuedHeartEnd, now);

        int poison = client.getVarpValue(VARP_POISON);
        if (poison >= VENOMED) {
            debuffs.add(new Effect("Venomed", ZULRAHS_SCALES, null, null, 0, true, true));
        } else if (poison > 0) {
            debuffs.add(new Effect("Poisoned", WEAPON_POISON, null, null, 0, true, true));
        }
        if (frozenEnd > now) {
            // The spell's full length: shorter if you prayed against it, and over as soon as you can move
            debuffs.add(new Effect("Frozen", 0, frozenSprite, null, null, frozenEnd, false, true));
        }
        if (teleblockEnd > now) {
            debuffs.add(new Effect("Teleblocked", 0, SpriteID.Magicon2.TELE_BLOCK, null, null, teleblockEnd, true, true));
        }

        // Stats above or below your level that the potion buffs don't already show (Hitpoints from an anglerfish,
        // a drained Defence...). Prayer and Hitpoints going down are just being used, not debuffs
        for (Skill skill : Skill.values()) {
            // OVERALL isn't a skill: the game has no level for it
            if ("OVERALL".equals(skill.name())) {
                continue;
            }
            int change = client.getBoostedSkillLevel(skill) - client.getRealSkillLevel(skill);
            boolean combat = PotionBuffs.BOOSTABLE_COMBAT_SKILLS.contains(skill);
            if (change > 0 && !combat) {
                buffs.add(new Effect(skill.getName() + " boosted", 0, skill, "+" + change, 0, true, false));
            } else if (change > 0 && !overloaded && potionAlerts.getActiveBuffs().stream().noneMatch(b -> b.skills.contains(skill))) {
                buffs.add(new Effect(skill.getName() + " boosted", 0, skill, "+" + change, 0, true, false));
            } else if (change < 0 && skill != Skill.PRAYER && skill != Skill.HITPOINTS) {
                debuffs.add(new Effect(skill.getName() + " drained", 0, skill, String.valueOf(change), 0, true, true));
            }
        }
        buffs.addAll(debuffs);
        effects = buffs;
    }

    // Like RuneLite's Timers plugin: moving on a later tick means the freeze has worn off
    private void trackFreeze() {
        Player player = client.getLocalPlayer();
        WorldPoint here = player != null ? player.getWorldLocation() : null;
        if (frozenEnd > 0 && frozenTick != client.getTickCount() && here != null && lastPoint != null && !here.equals(lastPoint)) {
            frozenEnd = 0;
        }
        lastPoint = here;
    }

    private static void addTimed(List<Effect> list, String name, int itemId, long end, long now) {
        if (end > now) {
            list.add(new Effect(name, itemId, null, null, end, true, false));
        }
    }
}
