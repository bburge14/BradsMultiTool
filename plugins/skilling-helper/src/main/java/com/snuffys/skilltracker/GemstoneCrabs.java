package com.snuffys.skilltracker;

import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;

/** Telling the Gemstone Crab (and its shell) from other NPCs, for the crab tracker and for PvM to leave it out. */
final class GemstoneCrabs {

    static final int GEMSTONE_CRAB_ID = 14779;
    static final int GEMSTONE_CRAB_SHELL_ID = 14780;

    enum CrabForm {
        NONE,
        ACTIVE,
        SHELL
    }

    private GemstoneCrabs() {
    }

    static boolean isGemstoneCrab(NPC npc) {
        return classify(npc) != CrabForm.NONE;
    }

    static CrabForm classify(NPC npc) {
        if (npc == null) {
            return CrabForm.NONE;
        }
        if (npc.getId() == GEMSTONE_CRAB_ID) {
            return CrabForm.ACTIVE;
        }
        if (npc.getId() == GEMSTONE_CRAB_SHELL_ID) {
            return CrabForm.SHELL;
        }

        // Fallback in case the ids change: match the name and tell the shell apart by its "Mine" option
        NPCComposition comp = npc.getTransformedComposition();
        if (comp == null) {
            comp = npc.getComposition();
        }
        String name = comp != null && comp.getName() != null ? comp.getName() : npc.getName();
        if (name == null) {
            return CrabForm.NONE;
        }
        name = name.toLowerCase();
        if (!name.contains("gemstone crab") && !name.contains("gem crab")) {
            return CrabForm.NONE;
        }
        if (name.contains("shell")) {
            return CrabForm.SHELL;
        }
        if (comp != null && comp.getActions() != null) {
            for (String action : comp.getActions()) {
                if ("Mine".equalsIgnoreCase(action)) {
                    return CrabForm.SHELL;
                }
            }
        }
        return CrabForm.ACTIVE;
    }
}
