package com.snuffys.launcher;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Starts RuneLite with Snuffy's plugins loaded (the launcher passes their classes in snuffys.classes, comma separated).
 */
public class SnuffysRunner {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        List<Class<? extends Plugin>> plugins = new ArrayList<>();
        for (String name : System.getProperty("snuffys.classes", "").split(",")) {
            if (!name.trim().isEmpty()) {
                plugins.add((Class<? extends Plugin>) Class.forName(name.trim()));
            }
        }
        System.out.println("Loading Snuffy's: " + plugins);
        ExternalPluginManager.loadBuiltin(plugins.toArray(new Class[0]));
        RuneLite.main(args);
    }
}
