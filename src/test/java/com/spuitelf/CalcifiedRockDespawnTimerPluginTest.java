package com.spuitelf;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CalcifiedRockDespawnTimerPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(CalcifiedRockDespawnTimerPlugin.class);
        RuneLite.main(args);
    }
}
