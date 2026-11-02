package com.bradsmultitool;

import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Guardians of the Rift: alerts for the rift about to open (game starting) and the portal to the huge guardian
 * remains opening (with where it is); and what the card shows (the Great Guardian's power, your energy, guardians
 * built, the two altars open now with the level each needs, in red when yours is too low, and where the portal is).
 * Only while the game's Guardians of the Rift box shows. Messages, the altar icons and levels, and the remains area
 * come from the Guardians of the Rift Helper Plugin Hub plugin (BSD-2-Clause, Joe Rankin). Information only: it
 * doesn't outline, hide or reorder anything.
 */
@Singleton
class RiftHelper {

    /** A guardian (altar): its name, Runecraft level, and the icon the game's box shows while it's open. */
    static final class Guardian {
        final String name;
        final int level;
        final boolean catalytic;
        final int sprite;

        Guardian(String name, int level, boolean catalytic, int sprite) {
            this.name = name;
            this.level = level;
            this.catalytic = catalytic;
            this.sprite = sprite;
        }
    }

    static final Guardian[] GUARDIANS = {
        new Guardian("Air", 1, false, 4353), new Guardian("Mind", 2, true, 4354),
        new Guardian("Water", 5, false, 4355), new Guardian("Earth", 9, false, 4356),
        new Guardian("Fire", 14, false, 4357), new Guardian("Body", 20, true, 4358),
        new Guardian("Cosmic", 27, true, 4359), new Guardian("Chaos", 35, true, 4360),
        new Guardian("Nature", 44, true, 4361), new Guardian("Law", 54, true, 4362),
        new Guardian("Death", 65, true, 4363), new Guardian("Blood", 77, true, 4364)};

    private static final int TEMPLE_REGION = 14484;
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    private final Client client;
    private final Alerts alerts;
    private boolean inGame;
    private boolean portalOpen;

    @Inject
    RiftHelper(Client client, Alerts alerts) {
        this.client = client;
        this.alerts = alerts;
    }

    boolean inGame() {
        return inGame;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Widget hud = client.getWidget(InterfaceID.GotrHud.CONTENT);
        inGame = hud != null && !hud.isHidden();
        String portal = inGame ? portal() : null;
        if (portal != null && !portalOpen) {
            Player you = client.getLocalPlayer();
            // Not when you're the one in the remains already
            if (you != null && !inRemains(you.getWorldLocation())) {
                alerts.alert(VoiceAlertService.VoiceLine.RIFT_PORTAL, direction(portal));
            }
        }
        portalOpen = portal != null;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!inGame && !inTemple() || event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        if (Text.removeTags(event.getMessage()).contains("The rift will become active in 10 seconds")) {
            alerts.alert(VoiceAlertService.VoiceLine.RIFT_STARTING, null);
        }
    }

    // The temple's main room (as the hub plugin), where you wait for the rift to open
    private boolean inTemple() {
        for (int region : client.getTopLevelWorldView().getMapRegions()) {
            if (region == TEMPLE_REGION) {
                return true;
            }
        }
        return false;
    }

    // The huge guardian remains, where the portal leads (the hub plugin's bounds)
    static boolean inRemains(WorldPoint at) {
        return at.getX() > 3587 && at.getX() < 3594 && at.getY() > 9496 && at.getY() < 9516;
    }

    /** Where the portal is, as the game's box says it ("SW 0:25"), or null while there's none. */
    String portal() {
        Widget widget = client.getWidget(InterfaceID.GotrHud.PORTAL_POSITION);
        if (widget == null || widget.isHidden() || widget.getText() == null || widget.getText().trim().isEmpty()) {
            return null;
        }
        return Text.removeTags(widget.getText()).trim();
    }

    /** "SW 0:25" as "south west". */
    static String direction(String portal) {
        String compass = portal.split(" ")[0];
        StringBuilder out = new StringBuilder();
        for (char c : compass.toCharArray()) {
            String word = c == 'N' ? "north" : c == 'S' ? "south" : c == 'E' ? "east" : c == 'W' ? "west" : null;
            if (word == null) {
                return portal;
            }
            out.append(out.length() == 0 ? "" : " ").append(word);
        }
        return out.length() == 0 ? portal : out.toString();
    }

    /** The Great Guardian's power (percent), or -1. */
    int power() {
        return WintertodtHelper.number(client.getWidget(InterfaceID.GotrHud.ENERGY_TITLE), PERCENT);
    }

    int elementalEnergy() {
        return WintertodtHelper.number(client.getWidget(InterfaceID.GotrHud.ELEMENTAL_ENERGY_TEXT), NUMBER);
    }

    int catalyticEnergy() {
        return WintertodtHelper.number(client.getWidget(InterfaceID.GotrHud.CATALYTIC_ENERGY_TEXT), NUMBER);
    }

    /** "7/10": guardians built so far. */
    String guardiansBuilt() {
        Widget widget = client.getWidget(InterfaceID.GotrHud.GUARDIAN_LIMIT);
        return widget == null || widget.isHidden() || widget.getText() == null ? null : Text.removeTags(widget.getText());
    }

    /** The elemental and the catalytic altar open now (either can be null). */
    Guardian[] openAltars() {
        return new Guardian[]{bySprite(client.getWidget(InterfaceID.GotrHud.ELEMENTAL_PORTAL)),
            bySprite(client.getWidget(InterfaceID.GotrHud.CATALYTIC_PORTAL))};
    }

    private static Guardian bySprite(Widget widget) {
        return widget == null || widget.isHidden() ? null : guardian(widget.getSpriteId());
    }

    static Guardian guardian(int sprite) {
        for (Guardian g : GUARDIANS) {
            if (g.sprite == sprite) {
                return g;
            }
        }
        return null;
    }

    /** Your Runecraft level for the altars (boosts count, as in the game). */
    int level() {
        return client.getBoostedSkillLevel(Skill.RUNECRAFT);
    }
}
