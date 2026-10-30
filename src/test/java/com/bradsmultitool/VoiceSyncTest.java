package com.bradsmultitool;

import static org.junit.Assert.assertEquals;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

/** Same Voice For All, with two of the separate plugins in one client (RuneLite's own settings code). */
public class VoiceSyncTest {

    private static final class Plugin {
        final CommonConfig config;
        final PluginIdentity identity;
        final PluginFamily family;
        final VoiceSync sync;

        Plugin(EventBus bus, ConfigManager configManager, Class<? extends CommonConfig> type, PluginIdentity identity) {
            this.config = configManager.getConfig(type);
            this.identity = identity;
            this.family = new PluginFamily(bus, identity);
            this.sync = new VoiceSync(bus, configManager, config, identity, family);
            // Saved defaults, as ConfigDefaults leaves them
            CommonConfigDefaults.store(new ConfigDefaults(configManager, identity), config);
            bus.register(sync);
            sync.start();
            family.start();
        }
    }

    @Test
    public void changingTheVoiceInOnePluginChangesItInTheOthers() throws Exception {
        EventBus bus = new EventBus();
        ConfigManager configManager = SettingsPanelTest.freshConfigManager(bus);
        Plugin skilling = new Plugin(bus, configManager, SkillTrackerConfig.class, PluginIdentity.SKILL_TRACKER);
        Plugin pvm = new Plugin(bus, configManager, PvmConfig.class, PluginIdentity.PVM);

        configManager.setConfiguration(skilling.identity.configGroup, "voicePersona", VoiceAlertService.VoicePersona.JOHN);
        configManager.setConfiguration(skilling.identity.configGroup, "voiceVolume", 40);
        assertEquals(VoiceAlertService.VoicePersona.JOHN, pvm.config.voicePersona());
        assertEquals(40, pvm.config.voiceVolume());

        // And back the other way
        configManager.setConfiguration(pvm.identity.configGroup, "voicePersona", VoiceAlertService.VoicePersona.RYAN);
        assertEquals(VoiceAlertService.VoicePersona.RYAN, skilling.config.voicePersona());
    }

    @Test
    public void aPluginWithSameVoiceOffKeepsItsOwn() throws Exception {
        EventBus bus = new EventBus();
        ConfigManager configManager = SettingsPanelTest.freshConfigManager(bus);
        Plugin skilling = new Plugin(bus, configManager, SkillTrackerConfig.class, PluginIdentity.SKILL_TRACKER);
        Plugin pvm = new Plugin(bus, configManager, PvmConfig.class, PluginIdentity.PVM);
        configManager.setConfiguration(pvm.identity.configGroup, "syncVoice", false);

        configManager.setConfiguration(skilling.identity.configGroup, "voicePersona", VoiceAlertService.VoicePersona.JOHN);
        assertEquals(VoiceAlertService.VoicePersona.AMY, pvm.config.voicePersona());
        // ...and its own changes stay its own
        configManager.setConfiguration(pvm.identity.configGroup, "voicePersona", VoiceAlertService.VoicePersona.GHOST);
        assertEquals(VoiceAlertService.VoicePersona.JOHN, skilling.config.voicePersona());
    }

    @Test
    public void aPluginStartedLaterTakesTheSharedVoice() throws Exception {
        EventBus bus = new EventBus();
        ConfigManager configManager = SettingsPanelTest.freshConfigManager(bus);
        Plugin skilling = new Plugin(bus, configManager, SkillTrackerConfig.class, PluginIdentity.SKILL_TRACKER);
        configManager.setConfiguration(skilling.identity.configGroup, "voicePersona", VoiceAlertService.VoicePersona.TZHAAR);

        Plugin gemCrab = new Plugin(bus, configManager, GemCrabConfig.class, PluginIdentity.GEM_CRAB);
        assertEquals(VoiceAlertService.VoicePersona.TZHAAR, gemCrab.config.voicePersona());
    }

    // RuneLite caches settings by method, not by plugin: each plugin's settings re-declare the shared ones
    // (generate_config_defaults.py) so separate plugins don't read each other's values
    @Test
    public void eachPluginReadsItsOwnSharedSettings() throws Exception {
        EventBus bus = new EventBus();
        ConfigManager configManager = SettingsPanelTest.freshConfigManager(bus);
        PvmConfig pvm = configManager.getConfig(PvmConfig.class);
        GemCrabConfig gemCrab = configManager.getConfig(GemCrabConfig.class);
        configManager.setConfiguration(PluginIdentity.PVM.configGroup, "characterIdleAlert", false);
        configManager.setConfiguration(PluginIdentity.GEM_CRAB.configGroup, "characterIdleAlert", true);
        configManager.setConfiguration(PluginIdentity.PVM.configGroup, "buffWarningSeconds", 30);
        configManager.setConfiguration(PluginIdentity.GEM_CRAB.configGroup, "buffWarningSeconds", 90);
        // Read one, then the other (the second used to get the first's cached value)
        CommonConfig pvmCommon = pvm;
        CommonConfig gemCrabCommon = gemCrab;
        BuffConfig pvmBuffs = pvm;
        BuffConfig gemCrabBuffs = gemCrab;
        assertEquals(false, pvmCommon.characterIdleAlert());
        assertEquals(true, gemCrabCommon.characterIdleAlert());
        assertEquals(30, pvmBuffs.buffWarningSeconds());
        assertEquals(90, gemCrabBuffs.buffWarningSeconds());
    }
}
