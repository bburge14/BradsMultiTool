package com.bradsmultitool;

import static org.junit.Assert.assertNotNull;

import net.runelite.client.config.ConfigItem;
import org.junit.Test;

public class AlertTypeTest {

    @Test
    public void everyAlertHasVoiceAndChatToggles() throws Exception {
        for (AlertType type : AlertType.values()) {
            for (String key : new String[]{type.voiceKey, type.chatKey}) {
                ConfigItem item = BradsMultiToolConfig.class.getMethod(key).getAnnotation(ConfigItem.class);
                assertNotNull(key, item);
                assertNotNull(key, key.equals(item.keyName()) ? item : null);
            }
        }
    }

    @Test
    public void everyVoiceLineMapsToAnAlert() {
        for (VoiceAlertService.VoiceLine line : VoiceAlertService.VoiceLine.values()) {
            assertNotNull(line.name(), AlertType.of(line));
        }
    }
}
