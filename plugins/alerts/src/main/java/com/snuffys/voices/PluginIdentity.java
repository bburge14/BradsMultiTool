package com.snuffys.voices;

/**
 * Which of Snuffy's plugins this build is (each plugin provides its own): where its settings and saved data
 * live, the tag on its chat messages, and its id among the others (PluginFamily).
 */
final class PluginIdentity {

    /** The all-in-one Snuffy's Toolbelt; its group is the original one, so saved settings carry over. */
    static final PluginIdentity MULTI_TOOL = new PluginIdentity("bradsmultitool", "Snuffy's Toolbelt", "multitool");
    static final PluginIdentity SKILL_TRACKER = new PluginIdentity("snuffysskillinghelper", "Snuffy's Skilling Helper", "skilltracker");
    static final PluginIdentity PVM = new PluginIdentity("snuffyspvm", "Snuffy's PvM/Slayer", "pvm");
    static final PluginIdentity GEM_CRAB = new PluginIdentity("snuffysgemcrab", "Snuffy's GemCrab Tracker", "gemcrab");
    static final PluginIdentity TICK_COUNTER = new PluginIdentity("snuffystickcounter", "Snuffy's Tick Counter", "tickcounter");
    // "voices" is PluginFamily.VOICES, written out so the Tick Counter, which isn't in the family, needn't carry it
    static final PluginIdentity VOICES = new PluginIdentity("snuffysalerts", "Snuffy's Alerts", "voices");

    final String configGroup;
    final String chatName;
    final String familyId;

    PluginIdentity(String configGroup, String chatName, String familyId) {
        this.configGroup = configGroup;
        this.chatName = chatName;
        this.familyId = familyId;
    }
}
