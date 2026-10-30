package com.bradsmultitool;

/**
 * Which of Snuffy's plugins this build is (each plugin provides its own): where its settings and saved data
 * live, the tag on its chat messages, and its id among the others (PluginFamily).
 */
final class PluginIdentity {

    /** The all-in-one Snuffy's Multi-Tool; its group is the original one, so saved settings carry over. */
    static final PluginIdentity MULTI_TOOL = new PluginIdentity("bradsmultitool", "Snuffy's Multi-Tool", "multitool");
    static final PluginIdentity SKILL_TRACKER = new PluginIdentity("snuffysskillinghelper", "Snuffy's Skilling Helper", "skilltracker");
    static final PluginIdentity PVM = new PluginIdentity("snuffyspvm", "Snuffy's PvM & Slayer", "pvm");
    static final PluginIdentity GEM_CRAB = new PluginIdentity("snuffysgemcrab", "Snuffy's Gemstone Crab", "gemcrab");
    static final PluginIdentity VOICES = new PluginIdentity("snuffysvoices", "Snuffy's Voices", PluginFamily.VOICES);

    final String configGroup;
    final String chatName;
    final String familyId;

    PluginIdentity(String configGroup, String chatName, String familyId) {
        this.configGroup = configGroup;
        this.chatName = chatName;
        this.familyId = familyId;
    }
}
