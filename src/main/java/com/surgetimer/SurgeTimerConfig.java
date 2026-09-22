package com.surgetimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("surgetimer")
public interface SurgeTimerConfig extends Config
{
	enum TimeFormat
	{
		SECONDS,
		TICKS
	}

	enum FillDirection
	{
		BOTTOM_UP,
		TOP_DOWN
	}

	@ConfigItem(
		keyName = "fillDirection",
		name = "Fill direction",
		description = "Which way the potion gets its color back as the cooldown runs out",
		position = 1
	)
	default FillDirection fillDirection()
	{
		return FillDirection.BOTTOM_UP;
	}

	@ConfigItem(
		keyName = "timeFormat",
		name = "Time format",
		description = "Show the time left in minutes and seconds, or in game ticks",
		position = 2
	)
	default TimeFormat timeFormat()
	{
		return TimeFormat.SECONDS;
	}
}
