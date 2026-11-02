package com.snuffys.tickcounter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Agility: the course you're running (laps this session from the game's lap count message, lap times, XP per lap
 * from your XP between laps, marks of grace picked up), the Brimhaven Agility Arena (the ticket pillar moves every
 * minute, seen by the hint arrow moving; tickets earned), and the run energy alert. Only reads chat, XP, the hint
 * arrow and your inventory.
 */
@Singleton
class AgilityTracker {

    // "Your Draynor Village Rooftop lap count is: 12."
    private static final Pattern LAP = Pattern.compile("Your (.+?) lap count is: (\\d+)\\.?");
    /** The Brimhaven Agility Arena (RuneLite's Agility plugin uses the same region). */
    static final int ARENA_REGION = 11157;
    /** The pillar moves every minute. */
    static final int ARENA_TICKS = 100;
    /** A course's card stays this long after your last lap (ticks: 5 minutes). */
    private static final int COURSE_LINGER_TICKS = 500;

    private final Client client;
    private final SkillTrackerConfig config;
    private final SkillTracker plugin;
    private final ItemManager itemManager;
    private final Alerts alerts;
    // The pillar warning steps aside while you train one obstacle
    @Inject
    private javax.inject.Provider<ObstacleReps> obstacleReps;

    // The course
    @Getter
    private String course;
    @Getter
    private int laps;
    private int firstLapTick = -1;
    private int lastLapTick = -1;
    private int lapXp = -1;
    @Getter
    private double xpPerLap;
    @Getter
    private int marks;
    private int markCount = -1;

    // The arena
    private WorldPoint pillar;
    @Getter
    private int arenaTicksLeft = -1;
    @Getter
    private int tickets;
    private int ticketCount = -1;
    private long firstTicketMillis;
    private boolean warned;

    // Run energy
    private boolean energyWarned;

    @Inject
    AgilityTracker(Client client, SkillTrackerConfig config, SkillTracker plugin, ItemManager itemManager, Alerts alerts) {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.alerts = alerts;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            pillar = null;
            arenaTicksLeft = -1;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        Matcher m = LAP.matcher(Text.removeTags(event.getMessage()));
        if (!m.find()) {
            return;
        }
        String name = m.group(1);
        int now = client.getTickCount();
        int xp = client.getSkillExperience(Skill.AGILITY);
        if (!name.equals(course)) {
            course = name;
            laps = 0;
            firstLapTick = now;
            xpPerLap = 0;
            marks = 0;
        } else if (lapXp >= 0 && xp > lapXp) {
            // XP per lap from your own laps (bonuses and all), averaged
            double lap = xp - lapXp;
            xpPerLap = xpPerLap <= 0 ? lap : (xpPerLap * (laps - 1) + lap) / laps;
        }
        laps++;
        lastLapTick = now;
        lapXp = xp;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INV) {
            return;
        }
        int markNow = count(event.getItemContainer(), "mark of grace");
        if (markCount >= 0 && markNow > markCount && courseActive()) {
            marks += markNow - markCount;
        }
        markCount = markNow;
        int ticketNow = count(event.getItemContainer(), "agility arena ticket");
        if (ticketCount >= 0 && ticketNow > ticketCount && inArena()) {
            if (tickets == 0) {
                firstTicketMillis = System.currentTimeMillis();
            }
            tickets += ticketNow - ticketCount;
        }
        ticketCount = ticketNow;
    }

    private int count(ItemContainer container, String name) {
        int total = 0;
        for (Item item : container.getItems()) {
            if (item.getId() > 0 && name.equalsIgnoreCase(itemManager.getItemComposition(item.getId()).getName())) {
                total += item.getQuantity();
            }
        }
        return total;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // The arena: the hint arrow moving to another pillar starts the minute again
        if (inArena()) {
            WorldPoint hint = client.hasHintArrow() ? client.getHintArrowPoint() : null;
            if (hint != null && !hint.equals(pillar)) {
                pillar = hint;
                arenaTicksLeft = ARENA_TICKS;
                warned = false;
            } else if (arenaTicksLeft > 0) {
                arenaTicksLeft--;
            }
            int warnTicks = (int) Math.round(config.arenaWarnSeconds() / 0.6);
            if (arenaTicksLeft > 0 && arenaTicksLeft <= warnTicks && !warned && config.agilityArenaCard()
                && (obstacleReps == null || !obstacleReps.get().trainingOne())) {
                warned = true;
                alerts.alert(VoiceAlertService.VoiceLine.ARENA_PILLAR_MOVING, null);
            }
        } else {
            pillar = null;
            arenaTicksLeft = -1;
        }
        // Run energy, only while training Agility (it's back to normal once well above the line)
        int below = config.runEnergyBelow();
        int energy = client.getEnergy() / 100;
        if (below > 0 && plugin.trainedRecently(Skill.AGILITY)) {
            if (energy < below && !energyWarned) {
                energyWarned = true;
                alerts.alert(VoiceAlertService.VoiceLine.RUN_ENERGY_LOW, energy + "%");
            } else if (energy >= below + 10) {
                energyWarned = false;
            }
        }
    }

    /** Whether you're in the Brimhaven Agility Arena. */
    boolean inArena() {
        Player you = client.getLocalPlayer();
        return you != null && you.getWorldLocation().getRegionID() == ARENA_REGION;
    }

    /** Whether you've run a lap of the course lately (its card shows). */
    boolean courseActive() {
        return course != null && lastLapTick >= 0 && client.getTickCount() - lastLapTick <= COURSE_LINGER_TICKS;
    }

    /** Laps per hour so far (0 until there are two laps). */
    double lapsPerHour() {
        int span = lastLapTick - firstLapTick;
        return laps < 2 || span <= 0 ? 0 : (laps - 1) * 6000.0 / span;
    }

    /** Average lap time in ticks (0 until there are two laps). */
    double ticksPerLap() {
        int span = lastLapTick - firstLapTick;
        return laps < 2 ? 0 : span / (double) (laps - 1);
    }

    /** Tickets per hour in the arena so far. */
    double ticketsPerHour() {
        long span = System.currentTimeMillis() - firstTicketMillis;
        return tickets < 2 || span <= 0 ? 0 : tickets * 3_600_000.0 / span;
    }

    /** Laps to this much XP at your XP per lap (0 if unknown). */
    int lapsTo(int targetXp) {
        int xp = client.getSkillExperience(Skill.AGILITY);
        return xpPerLap <= 0 || targetXp <= xp ? 0 : (int) Math.ceil((targetXp - xp) / xpPerLap);
    }
}
