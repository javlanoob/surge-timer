package com.surgetimer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SurgeTimerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SurgeTimerPlugin.class);
		RuneLite.main(args);
	}
}
