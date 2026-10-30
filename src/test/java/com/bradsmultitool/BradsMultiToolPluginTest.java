package com.bradsmultitool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Launches RuneLite with Snuffy's plugins side-loaded (gradlew runBrad). Which ones: -Pload= on the Gradle
 * command line (the snuffys.plugins property here), names joined by commas or +; toolbelt by default.
 *   runBrad                            the Toolbelt
 *   runBrad "-Pload=skilling,tick"  just those separate plugins, as players installing them would have
 *   runBrad -Pload=separate         all the separate ones together (not the Toolbelt)
 * These run from src/ like the Toolbelt; split_plugins.py --verify checks each also builds on its own.
 */
public class BradsMultiToolPluginTest {

    private static final Map<String, Class<? extends Plugin>> PLUGINS = new LinkedHashMap<>();

    static {
        PLUGINS.put("toolbelt", BradsMultiToolPlugin.class);
        PLUGINS.put("skilling", SkillTrackerPlugin.class);
        PLUGINS.put("pvm", PvmPlugin.class);
        PLUGINS.put("gemcrab", GemCrabPlugin.class);
        PLUGINS.put("tick", TickCounterPlugin.class);
        PLUGINS.put("alerts", VoicesPlugin.class);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        List<Class<? extends Plugin>> chosen = new ArrayList<>();
        for (String name : System.getProperty("snuffys.plugins", "toolbelt").toLowerCase().split("[,+ ]+")) {
            if (name.equals("separate")) {
                PLUGINS.forEach((key, plugin) -> {
                    if (!key.equals("toolbelt")) {
                        chosen.add(plugin);
                    }
                });
            } else if (PLUGINS.containsKey(name)) {
                chosen.add(PLUGINS.get(name));
            } else if (!name.isEmpty()) {
                throw new IllegalArgumentException("Unknown plugin \"" + name + "\": use " + String.join(", ", PLUGINS.keySet()) + " or separate");
            }
        }
        if (chosen.contains(BradsMultiToolPlugin.class) && chosen.size() > 1) {
            System.err.println("Note: the Toolbelt together with separate plugins shows everything twice");
        }
        System.out.println("Loading Snuffy's: " + chosen);
        ExternalPluginManager.loadBuiltin(chosen.toArray(new Class[0]));
        RuneLite.main(args);
    }
}
