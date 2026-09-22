package com.surgetimer;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SurgeTimerConfig.GROUP)
public interface SurgeTimerConfig extends Config
{
	String GROUP = "surgetimer";

	@ConfigItem(
		keyName = "showInBank",
		name = "Show in bank",
		description = "Also gray out surge potions in the bank",
		position = 0
	)
	default boolean showInBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "timerColor",
		name = "Timer color",
		description = "Color of the timer while the cooldown is counting down",
		position = 1
	)
	default Color timerColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "pausedColor",
		name = "Paused color",
		description = "Color of the timer while the cooldown is paused, like between Inferno waves or Theatre of Blood rooms",
		position = 2
	)
	default Color pausedColor()
	{
		return Color.YELLOW;
	}
}
