package com.bradsmultitool;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

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
        SLAYER_ITEM_MISSING("slayer_item_missing", "You are missing your slayer item."),
        INVENTORY_FULL("inventory_full", "Inventory is full."),
        TREE_GONE("tree_gone", "The tree is gone."),
        ROCK_DEPLETED("rock_depleted", "The rock is depleted."),
        FISHING_SPOT_MOVED("fishing_spot_moved", "The fishing spot has moved."),
        COIN_POUCH_FULL("coin_pouch_full", "Your coin pouches are full."),
        BIRDHOUSES_READY("birdhouses_ready", "Your bird houses are ready."),
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

    /** Voice personas; each folder under resources/voices holds one clip per VoiceLine. */
    public enum VoicePersona {
        AMY("Amy"),
        JOE("Joe"),
        MIKE("Mike"),
        CORI("Cori"),
        ALBA("Alba"),
        ALAN("Alan"),
        NORTHERN("Northern"),
        // Character voices: the voices above pitch-shifted with effects (see generate_voice_clips.py)
        GNOME("Gnome"),
        OGRE("Ogre"),
        WIZARD("Old Wizard"),
        ROBOT("Robot");

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

    // One clip at a time so overlapping alerts queue instead of talking over each other: the next clip
    // starts when the current one reports it has stopped (no sleeping threads, per Plugin Hub rules)
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Deque<String[]> queue = new ArrayDeque<>();
    private boolean playing;
    private Clip current;

    /**
     * @param volumePercent 0-100
     */
    public void speakAsync(VoiceLine line, VoicePersona persona, int volumePercent) {
        speakClipsAsync(persona, volumePercent, line.fileName);
    }

    /** Plays several clips back to back as one alert (nothing else can cut in between). */
    public void speakClipsAsync(VoicePersona persona, int volumePercent, String... fileNames) {
        synchronized (queue) {
            for (String fileName : fileNames) {
                queue.add(new String[]{"voices/" + persona.folder() + "/" + fileName + ".wav", String.valueOf(volumePercent)});
            }
            if (playing) {
                return;
            }
            playing = true;
        }
        executor.execute(this::playNext);
    }

    /** "Woodcutting level", then "45". */
    static String[] levelUpClips(Skill skill, int level) {
        return new String[]{"skill_" + skill.getName().toLowerCase(), "level_" + level};
    }

    private void playNext() {
        String[] next;
        synchronized (queue) {
            next = queue.poll();
            if (next == null) {
                playing = false;
                current = null;
                return;
            }
        }
        if (!start(next[0], Integer.parseInt(next[1]))) {
            playNext();
        }
    }

    // Starts the clip; when it stops, it closes itself and the next one in the queue starts
    private boolean start(String resource, int volumePercent) {
        try (InputStream raw = VoiceAlertService.class.getResourceAsStream(resource)) {
            if (raw == null) {
                log.warn("Missing voice clip {}", resource);
                return false;
            }
            try (AudioInputStream audio = toPcm(AudioSystem.getAudioInputStream(new BufferedInputStream(raw)))) {
                Clip clip = AudioSystem.getClip();
                clip.open(audio);
                setVolume(clip, volumePercent);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        executor.execute(this::playNext);
                    }
                });
                current = clip;
                clip.start();
                return true;
            }
        } catch (Exception e) {
            log.warn("Could not play voice clip {}: {}", resource, e.getMessage());
            return false;
        }
    }

    /** Clips are stored as 16 kHz mu-law to keep the plugin small; decode to 16-bit PCM for playback. */
    static AudioInputStream toPcm(AudioInputStream in) {
        AudioFormat f = in.getFormat();
        if (AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding())) {
            return in;
        }
        AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
            f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
        return AudioSystem.getAudioInputStream(pcm, in);
    }

    private static void setVolume(Clip clip, int volumePercent) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float linear = Math.max(0.0001f, Math.min(100, volumePercent) / 100f);
        float db = (float) (20 * Math.log10(linear));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    public void shutdown() {
        synchronized (queue) {
            queue.clear();
        }
        Clip clip = current;
        if (clip != null) {
            clip.stop();
        }
        executor.shutdown();
    }
}
