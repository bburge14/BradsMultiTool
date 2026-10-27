package com.bradsmultitool;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BradsMultiToolPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(BradsMultiToolPlugin.class);
        RuneLite.main(args);
    }
}
