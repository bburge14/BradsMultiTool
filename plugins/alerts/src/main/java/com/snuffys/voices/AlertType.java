package com.snuffys.voices;

/**
 * Every alert the plugin can raise. Each one has a "voice&lt;Key&gt;" toggle under Voice Alerts and a
 * "chat&lt;Key&gt;" toggle under Chat Messages, plus the line played when its voice toggle is turned on.
 */
enum AlertType {
    BUFF_LOW("BuffLow", VoiceAlertService.VoiceLine.BUFF_LOW),
    BUFF_EXPIRED("BuffExpired", VoiceAlertService.VoiceLine.BUFF_EXPIRED),
    HEALTH_LOW("HealthLow", VoiceAlertService.VoiceLine.HEALTH_LOW),
    PRAYER_LOW("PrayerLow", VoiceAlertService.VoiceLine.PRAYER_LOW),
    SPEC_READY("SpecReady", VoiceAlertService.VoiceLine.SPEC_READY),
    ANTIFIRE("Antifire", VoiceAlertService.VoiceLine.ANTIFIRE_LOW),
    POISON("Poison", VoiceAlertService.VoiceLine.POISONED),
    STAMINA("Stamina", VoiceAlertService.VoiceLine.STAMINA_EXPIRED),
    AMMO("Ammo", VoiceAlertService.VoiceLine.AMMO_LOW),
    RUNES("Runes", VoiceAlertService.VoiceLine.OUT_OF_RUNES),
    CANNON("Cannon", VoiceAlertService.VoiceLine.CANNON_LOW),
    SLAYER_ITEM("SlayerItem", VoiceAlertService.VoiceLine.SLAYER_ITEM_MISSING),
    SUPERIOR("Superior", VoiceAlertService.VoiceLine.SUPERIOR_SPAWNED),
    TASK_COMPLETE("TaskComplete", VoiceAlertService.VoiceLine.TASK_COMPLETE),
    TASK_HALFWAY("TaskHalfway", VoiceAlertService.VoiceLine.TASK_HALFWAY),
    TASK_NEARLY_DONE("TaskNearlyDone", VoiceAlertService.VoiceLine.TASK_NEARLY_DONE),
    CHARACTER_IDLE("CharacterIdle", VoiceAlertService.VoiceLine.CHARACTER_IDLE),
    IDLE_LOGOUT("IdleLogout", VoiceAlertService.VoiceLine.IDLE_LOGOUT),
    CRAB_MOVED("CrabMoved", VoiceAlertService.VoiceLine.CRAB_MOVED),
    CRAB_LOW_HEALTH("CrabLowHealth", VoiceAlertService.VoiceLine.CRAB_LOW_HEALTH),
    INVENTORY_FULL("InventoryFull", VoiceAlertService.VoiceLine.INVENTORY_FULL),
    TREE_GONE("TreeGone", VoiceAlertService.VoiceLine.TREE_GONE),
    ROCK_DEPLETED("RockDepleted", VoiceAlertService.VoiceLine.ROCK_DEPLETED),
    FISHING_SPOT("FishingSpot", VoiceAlertService.VoiceLine.FISHING_SPOT_MOVED),
    COIN_POUCH("CoinPouch", VoiceAlertService.VoiceLine.COIN_POUCH_FULL),
    BIRDHOUSES("Birdhouses", VoiceAlertService.VoiceLine.BIRDHOUSES_READY),
    BATCH_COMPLETE("BatchComplete", VoiceAlertService.VoiceLine.BATCH_COMPLETE),
    LEVEL_UP("LevelUp", VoiceAlertService.levelUpClips(net.runelite.api.Skill.WOODCUTTING, 99));

    final String voiceKey;
    final String chatKey;
    /** Clips played when the voice toggle is turned on. */
    final String[] preview;

    AlertType(String key, VoiceAlertService.VoiceLine preview) {
        this(key, new String[]{preview.fileName});
    }

    AlertType(String key, String[] preview) {
        this.voiceKey = "voice" + key;
        this.chatKey = "chat" + key;
        this.preview = preview;
    }

    /** The alert a voice line belongs to. */
    static AlertType of(VoiceAlertService.VoiceLine line) {
        switch (line) {
            case BUFF_LOW: case MELEE_BUFF_LOW: case RANGED_BUFF_LOW: case MAGIC_BUFF_LOW: case DEFENCE_BUFF_LOW:
                return BUFF_LOW;
            case BUFF_EXPIRED: case MELEE_BUFF_EXPIRED: case RANGED_BUFF_EXPIRED: case MAGIC_BUFF_EXPIRED:
            case DEFENCE_BUFF_EXPIRED:
                return BUFF_EXPIRED;
            case HEALTH_LOW: return HEALTH_LOW;
            case PRAYER_LOW: case OUT_OF_PRAYER: return PRAYER_LOW;
            case SPEC_READY: return SPEC_READY;
            case ANTIFIRE_LOW: return ANTIFIRE;
            case POISONED: case VENOMED: case ANTIPOISON_LOW: return POISON;
            case STAMINA_EXPIRED: return STAMINA;
            case AMMO_LOW: return AMMO;
            case OUT_OF_RUNES: return RUNES;
            case CANNON_LOW: return CANNON;
            case SLAYER_ITEM_MISSING: return SLAYER_ITEM;
            case SUPERIOR_SPAWNED: return SUPERIOR;
            case TASK_COMPLETE: return TASK_COMPLETE;
            case TASK_HALFWAY: return TASK_HALFWAY;
            case TASK_NEARLY_DONE: return TASK_NEARLY_DONE;
            case CHARACTER_IDLE: return CHARACTER_IDLE;
            case IDLE_LOGOUT: return IDLE_LOGOUT;
            case CRAB_MOVED: return CRAB_MOVED;
            case CRAB_LOW_HEALTH: return CRAB_LOW_HEALTH;
            case INVENTORY_FULL: return INVENTORY_FULL;
            case TREE_GONE: return TREE_GONE;
            case ROCK_DEPLETED: return ROCK_DEPLETED;
            case FISHING_SPOT_MOVED: return FISHING_SPOT;
            case COIN_POUCH_FULL: return COIN_POUCH;
            case BIRDHOUSES_READY: return BIRDHOUSES;
            case BATCH_COMPLETE: return BATCH_COMPLETE;
            default:
                // Per-potion lines: POTION_<NAME>_LOW / _EXPIRED
                return line.name().endsWith("_LOW") ? BUFF_LOW : BUFF_EXPIRED;
        }
    }
}
