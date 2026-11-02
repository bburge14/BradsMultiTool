package com.snuffys.skilltracker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays pre-recorded voice alerts bundled with the plugin (generated with Piper TTS by
 * scripts/generate_voice_clips.py), so voices sound natural and work on any OS.
 */
@Slf4j
public class VoiceAlertService {

    /** The fixed lines the plugin can say. Text is what the clips were generated from. */
    public enum VoiceLine {
        BUFF_EXPIRED("buff_expired", "Buff has run out."),
        PRAYER_LOW("prayer_low", "Prayer is low."),
        OUT_OF_PRAYER("out_of_prayer", "Out of prayer points."),
        CRAB_MOVED("crab_moved", "The crab has moved!"),
        IDLE_LOGOUT("idle_logout", "You are about to be logged out."),
        SIX_HOUR_LOGOUT("six_hour_logout", "Six hour logout in twenty minutes."),
        CLUE_DROP("clue_drop", "You got a clue."),
        DAILIES("dailies", "You have dailies to do."),
        WEEKLIES("weeklies", "You have weeklies to do."),
        TIMER_DONE("timer_done", "Your timer is done."),
        DEATH("death", "You have died."),
        CRAB_LOW_HEALTH("crab_low_health", "The crab is about to burrow."),
        CHARACTER_IDLE("character_idle", "You are idle."),
        BUFF_LOW("buff_low", "Buff is running out."),
        HEALTH_LOW("health_low", "Health is low."),
        MELEE_BUFF_LOW("melee_buff_low", "Melee boost is running out."),
        MELEE_BUFF_EXPIRED("melee_buff_expired", "Melee boost has run out."),
        RANGED_BUFF_LOW("ranged_buff_low", "Ranged boost is running out."),
        RANGED_BUFF_EXPIRED("ranged_buff_expired", "Ranged boost has run out."),
        MAGIC_BUFF_LOW("magic_buff_low", "Magic boost is running out."),
        MAGIC_BUFF_EXPIRED("magic_buff_expired", "Magic boost has run out."),
        DEFENCE_BUFF_LOW("defence_buff_low", "Defence boost is running out."),
        DEFENCE_BUFF_EXPIRED("defence_buff_expired", "Defence boost has run out."),
        SPEC_READY("spec_ready", "Special attack is ready."),
        ANTIFIRE_LOW("antifire_low", "Antifire is running out."),
        ANTIPOISON_LOW("antipoison_low", "Poison protection is running out."),
        POISONED("poisoned", "You have been poisoned."),
        VENOMED("venomed", "You have been venomed."),
        STAMINA_EXPIRED("stamina_expired", "Stamina has worn off."),
        AMMO_LOW("ammo_low", "Ammo is running low."),
        OUT_OF_RUNES("out_of_runes", "You are out of runes."),
        CANNON_LOW("cannon_low", "Your cannon is running low."),
        SUPERIOR_SPAWNED("superior_spawned", "A superior has spawned!"),
        TASK_COMPLETE("task_complete", "Slayer task complete."),
        TASK_HALFWAY("task_halfway", "Slayer task halfway done."),
        TASK_NEARLY_DONE("task_nearly_done", "Slayer task nearly done."),
        SLAYER_ITEM_MISSING("slayer_item_missing", "You are missing your slayer item."),
        SLAYER_MILESTONE("slayer_milestone", "Your next slayer task is a milestone."),
        KILL_GOAL_REACHED("kill_goal_reached", "Kill goal reached."),
        WRONG_PRAYER("wrong_prayer", "Wrong protection prayer."),
        UNPOTTED("unpotted", "No combat boost active."),
        INVENTORY_FULL("inventory_full", "Inventory is full."),
        TREE_GONE("tree_gone", "The tree has fallen."),
        ROCK_DEPLETED("rock_depleted", "The rock is depleted."),
        FISHING_SPOT_MOVED("fishing_spot_moved", "The fishing spot has moved."),
        COIN_POUCH_FULL("coin_pouch_full", "Your coin pouches are full."),
        ARENA_PILLAR_MOVING("arena_pillar_moving", "The pillar is about to move."),
        RUN_ENERGY_LOW("run_energy_low", "Your run energy is low."),
        RUN_ENERGY_RESTORED("run_energy_restored", "Your run energy is restored."),
        FORESTRY_EVENT("forestry_event", "A forestry event has started."),
        SACK_FULL("sack_full", "The sack is full. Empty it before mining more."),
        BIRD_NEST("bird_nest", "A bird's nest has fallen."),
        WINTERTODT_ROUND("wintertodt_round", "The Wintertodt round is about to start."),
        BRAZIER_OUT("brazier_out", "Your brazier has gone out."),
        BRAZIER_BROKEN("brazier_broken", "Your brazier is broken."),
        OUT_OF_ROOTS("out_of_roots", "You're out of roots."),
        WINTERTODT_HIT("wintertodt_hit", "The cold stopped you. Start again."),
        WARMTH_LOW("warmth_low", "Your warmth is low."),
        TEMPOROSS_WAVE("tempoross_wave", "A wave is coming. Tether yourself."),
        TEMPOROSS_FIRE("tempoross_fire", "A fire is about to start near you."),
        TEMPOROSS_DOUBLE_SPOT("tempoross_double_spot", "A double fishing spot has appeared."),
        TEMPOROSS_VULNERABLE("tempoross_vulnerable", "Tempoross is vulnerable. Attack now."),
        RIFT_STARTING("rift_starting", "The rift opens in ten seconds."),
        RIFT_PORTAL("rift_portal", "A portal has opened."),
        BOAT_HEALTH_LOW("boat_health_low", "Your boat's health is low."),
        TRIM_SAILS("trim_sails", "Trim the sails now."),
        FARMING_READY("farming_ready", "Your farming patches are ready."),
        BIRDHOUSES_READY("birdhouses_ready", "Your bird houses are ready."),
        RUMOUR_COMPLETE("rumour_complete", "Rumour complete. Take the part to the Hunter Guild."),
        BATCH_COMPLETE("batch_complete", "Batch complete."),
        // Per-potion buff lines, named after ActiveBuffs kinds: POTION_<NAME>_LOW / _EXPIRED
        POTION_SUPER_COMBAT_LOW("potion_super_combat_low", "Super combat is running out."),
        POTION_SUPER_COMBAT_EXPIRED("potion_super_combat_expired", "Super combat has run out."),
        POTION_COMBAT_LOW("potion_combat_low", "Combat potion is running out."),
        POTION_COMBAT_EXPIRED("potion_combat_expired", "Combat potion has run out."),
        POTION_ATTACK_LOW("potion_attack_low", "Attack potion is running out."),
        POTION_ATTACK_EXPIRED("potion_attack_expired", "Attack potion has run out."),
        POTION_STRENGTH_LOW("potion_strength_low", "Strength potion is running out."),
        POTION_STRENGTH_EXPIRED("potion_strength_expired", "Strength potion has run out."),
        POTION_DEFENCE_LOW("potion_defence_low", "Defence potion is running out."),
        POTION_DEFENCE_EXPIRED("potion_defence_expired", "Defence potion has run out."),
        POTION_RANGING_LOW("potion_ranging_low", "Ranging potion is running out."),
        POTION_RANGING_EXPIRED("potion_ranging_expired", "Ranging potion has run out."),
        POTION_MAGIC_LOW("potion_magic_low", "Magic potion is running out."),
        POTION_MAGIC_EXPIRED("potion_magic_expired", "Magic potion has run out."),
        POTION_BASTION_LOW("potion_bastion_low", "Bastion potion is running out."),
        POTION_BASTION_EXPIRED("potion_bastion_expired", "Bastion potion has run out."),
        POTION_BATTLEMAGE_LOW("potion_battlemage_low", "Battlemage potion is running out."),
        POTION_BATTLEMAGE_EXPIRED("potion_battlemage_expired", "Battlemage potion has run out."),
        POTION_DIVINE_SUPER_COMBAT_LOW("potion_divine_super_combat_low", "Divine super combat is running out."),
        POTION_DIVINE_SUPER_COMBAT_EXPIRED("potion_divine_super_combat_expired", "Divine super combat has run out."),
        POTION_DIVINE_BASTION_LOW("potion_divine_bastion_low", "Divine bastion is running out."),
        POTION_DIVINE_BASTION_EXPIRED("potion_divine_bastion_expired", "Divine bastion has run out."),
        POTION_DIVINE_BATTLEMAGE_LOW("potion_divine_battlemage_low", "Divine battlemage is running out."),
        POTION_DIVINE_BATTLEMAGE_EXPIRED("potion_divine_battlemage_expired", "Divine battlemage has run out."),
        POTION_DIVINE_ATTACK_LOW("potion_divine_attack_low", "Divine attack is running out."),
        POTION_DIVINE_ATTACK_EXPIRED("potion_divine_attack_expired", "Divine attack has run out."),
        POTION_DIVINE_STRENGTH_LOW("potion_divine_strength_low", "Divine strength is running out."),
        POTION_DIVINE_STRENGTH_EXPIRED("potion_divine_strength_expired", "Divine strength has run out."),
        POTION_DIVINE_DEFENCE_LOW("potion_divine_defence_low", "Divine defence is running out."),
        POTION_DIVINE_DEFENCE_EXPIRED("potion_divine_defence_expired", "Divine defence has run out."),
        POTION_DIVINE_RANGING_LOW("potion_divine_ranging_low", "Divine ranging is running out."),
        POTION_DIVINE_RANGING_EXPIRED("potion_divine_ranging_expired", "Divine ranging has run out."),
        POTION_DIVINE_MAGIC_LOW("potion_divine_magic_low", "Divine magic is running out."),
        POTION_DIVINE_MAGIC_EXPIRED("potion_divine_magic_expired", "Divine magic has run out.");

        /** The line for a specific buff ("Divine Super Combat"), or null if there isn't one. */
        static VoiceLine forPotion(String buffName, boolean runningOut) {
            String constant = "POTION_" + buffName.toUpperCase().replaceAll("\\W+", "_") + (runningOut ? "_LOW" : "_EXPIRED");
            try {
                return valueOf(constant);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        final String fileName;
        final String text;

        VoiceLine(String fileName, String text) {
            this.fileName = fileName;
            this.text = text;
        }
    }

    /**
     * Which of the separate plugins a clip ships with (see docs/DEVELOPMENT.md "Planned: separate plugins");
     * each persona's clips are in one folder per plugin, so a plugin's build takes just its folder.
     * Keep in step with plugin_of() in scripts/generate_voice_clips.py.
     */
    enum VoicePlugin {
        /** Skill Tracker (with the skilling helpers): skilling alerts and level-ups. */
        SKILLING("skilling"),
        /** PvM/Slayer: health, prayer, combat and Slayer alerts. */
        PVM("pvm"),
        /** Potion buffs running out and run out: PvM/Slayer and the GemCrab Tracker both carry these. */
        POTIONS("potions"),
        GEM_CRAB("gemcrab"),
        /** Planner: dailies, weeklies, farming patches, bird houses and your own timers. */
        PLANNER("planner"),
        /** Idle and logout warnings and run energy: every plugin carries these (Agility's run energy alert too). */
        SHARED("shared");

        final String folder;

        VoicePlugin(String folder) {
            this.folder = folder;
        }

        static VoicePlugin of(String clip) {
            if (clip.startsWith("skill_") || clip.startsWith("num_") || clip.startsWith("tens_")) {
                return SKILLING;
            }
            if (clip.startsWith("potion_") || clip.startsWith("buff_") || clip.endsWith("_buff_low") || clip.endsWith("_buff_expired")) {
                return POTIONS;
            }
            switch (clip) {
                case "inventory_full":
                case "tree_gone":
                case "rock_depleted":
                case "fishing_spot_moved":
                case "coin_pouch_full":
                case "arena_pillar_moving":
                case "forestry_event":
                case "sack_full":
                case "wintertodt_round":
                case "brazier_out":
                case "brazier_broken":
                case "out_of_roots":
                case "wintertodt_hit":
                case "warmth_low":
                case "tempoross_wave":
                case "tempoross_fire":
                case "tempoross_double_spot":
                case "tempoross_vulnerable":
                case "rift_starting":
                case "rift_portal":
                case "boat_health_low":
                case "trim_sails":
                case "bird_nest":
                case "rumour_complete":
                case "batch_complete":
                    return SKILLING;
                case "crab_moved":
                case "crab_low_health":
                    return GEM_CRAB;
                case "dailies":
                case "weeklies":
                case "timer_done":
                case "farming_ready":
                case "birdhouses_ready":
                    return PLANNER;
                case "character_idle":
                case "idle_logout":
                case "six_hour_logout":
                case "run_energy_low":
                case "run_energy_restored":
                case "clue_drop":
                    return SHARED;
                default:
                    return PVM;
            }
        }
    }

    /** Voice personas; each folder under resources/voices holds one clip per VoiceLine. */
    public enum VoicePersona {
        AMY("Amy (US)"),
        CORI("Cori (UK)"),
        ALAN("Alan (UK)"),
        JOHN("John (US)"),
        /** A different one of the voices above for each alert (no clips of its own). */
        RANDOM("Random");

        /** The voices with clips, for Random to pick from. */
        static VoicePersona[] voices() {
            VoicePersona[] all = values();
            return java.util.Arrays.copyOf(all, all.length - 1);
        }

        private final String displayName;

        VoicePersona(String displayName) {
            this.displayName = displayName;
        }

        String folder() {
            return name().toLowerCase();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // Played through RuneLite's AudioPlayer (the Plugin Hub doesn't allow javax.sound in plugins). Clips are 8-bit
    // PCM WAV, which it plays as they are. An alert's clips are joined into one sound; alerts that overlap wait
    // their turn: each is scheduled for when the one before ends (its length from its size), never with a sleep
    private final AudioPlayer audioPlayer;
    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    // When the sound already queued finishes (System.currentTimeMillis)
    private long busyUntil;
    // A short pause between one alert and the next
    private static final long GAP_MILLIS = 150;

    public VoiceAlertService(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
    }

    /**
     * @param volumePercent 0-100
     */
    public void speakAsync(VoiceLine line, VoicePersona persona, int volumePercent) {
        speakClipsAsync(persona, volumePercent, line.fileName);
    }

    /** Where a clip is bundled: voices/amy/skilling/inventory_full.wav. */
    static String clipResource(VoicePersona persona, String clip) {
        return "voices/" + persona.folder() + "/" + VoicePlugin.of(clip).folder + "/" + clip + ".wav";
    }

    /** Plays several clips back to back as one alert (nothing else can cut in between). */
    public synchronized void speakClipsAsync(VoicePersona persona, int volumePercent, String... fileNames) {
        if (volumePercent <= 0) {
            return;
        }
        Wav joined = null;
        for (String fileName : fileNames) {
            Wav clip = read(clipResource(persona, fileName));
            joined = clip == null ? joined : joined == null ? clip : joined.then(clip);
        }
        if (joined == null) {
            return;
        }
        if (executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        long now = System.currentTimeMillis();
        long start = Math.max(now, busyUntil);
        busyUntil = start + joined.millis() + GAP_MILLIS;
        byte[] sound = joined.bytes();
        float gainDb = (float) (20 * Math.log10(Math.min(100, volumePercent) / 100.0));
        executor.schedule(() -> {
            try {
                audioPlayer.play(new ByteArrayInputStream(sound), gainDb);
            } catch (Exception e) {
                log.warn("Could not play a voice alert: {}", e.getMessage());
            }
        }, start - now, TimeUnit.MILLISECONDS);
    }

    /** "Woodcutting level", then "45". */
    /**
     * "Woodcutting level" then the number, built from parts to keep the plugin small: 2-19 and round tens
     * are one clip ("Forty!"), the rest a tens prefix and a unit ("forty" + "five!").
     */
    static String[] levelUpClips(Skill skill, int level) {
        String skillClip = "skill_" + skill.getName().toLowerCase();
        if (level < 20 || level % 10 == 0) {
            return new String[]{skillClip, "num_" + level};
        }
        return new String[]{skillClip, "tens_" + (level / 10 * 10), "num_" + (level % 10)};
    }

    /** A WAV's format chunk and its samples, to join clips of one voice (they share a format) into one sound. */
    static final class Wav {
        final byte[] format;
        final byte[] data;

        Wav(byte[] format, byte[] data) {
            this.format = format;
            this.data = data;
        }

        Wav then(Wav next) {
            byte[] both = new byte[data.length + next.data.length];
            System.arraycopy(data, 0, both, 0, data.length);
            System.arraycopy(next.data, 0, both, data.length, next.data.length);
            return new Wav(format, both);
        }

        /** How long it plays: the data's bytes over the bytes a second (in the format chunk). */
        long millis() {
            long perSecond = format.length >= 12 ? (format[8] & 0xFFL) | (format[9] & 0xFFL) << 8
                | (format[10] & 0xFFL) << 16 | (format[11] & 0xFFL) << 24 : 0;
            return perSecond > 0 ? data.length * 1000L / perSecond : 0;
        }

        /** As a WAV file. */
        byte[] bytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream(44 + data.length);
            int pad = data.length & 1;
            out.write('R');
            out.write('I');
            out.write('F');
            out.write('F');
            int32(out, 4 + 8 + format.length + 8 + data.length + pad);
            out.write('W');
            out.write('A');
            out.write('V');
            out.write('E');
            out.write('f');
            out.write('m');
            out.write('t');
            out.write(' ');
            int32(out, format.length);
            out.write(format, 0, format.length);
            out.write('d');
            out.write('a');
            out.write('t');
            out.write('a');
            int32(out, data.length);
            out.write(data, 0, data.length);
            if (pad == 1) {
                out.write(0);
            }
            return out.toByteArray();
        }

        private static void int32(ByteArrayOutputStream out, int value) {
            out.write(value);
            out.write(value >> 8);
            out.write(value >> 16);
            out.write(value >> 24);
        }
    }

    /** A bundled clip's format and samples, or null when it's missing or unreadable. */
    static Wav read(String resource) {
        try (InputStream in = VoiceAlertService.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("Missing voice clip {}", resource);
                return null;
            }
            return parse(in.readAllBytes());
        } catch (Exception e) {
            log.warn("Could not read voice clip {}: {}", resource, e.getMessage());
            return null;
        }
    }

    /** The "fmt " and "data" chunks of a RIFF WAV, or null when it isn't one. */
    static Wav parse(byte[] wav) {
        if (wav.length < 12 || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            return null;
        }
        byte[] format = null;
        int at = 12;
        while (at + 8 <= wav.length) {
            String id = new String(wav, at, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = (wav[at + 4] & 0xFF) | (wav[at + 5] & 0xFF) << 8 | (wav[at + 6] & 0xFF) << 16 | (wav[at + 7] & 0xFF) << 24;
            int body = at + 8;
            if (size < 0 || body + size > wav.length) {
                size = wav.length - body;
            }
            if (id.equals("fmt ")) {
                format = java.util.Arrays.copyOfRange(wav, body, body + size);
            } else if (id.equals("data") && format != null) {
                return new Wav(format, java.util.Arrays.copyOfRange(wav, body, body + size));
            }
            at = body + size + (size & 1);
        }
        return null;
    }

    public synchronized void shutdown() {
        executor.shutdownNow();
        busyUntil = 0;
    }
}
