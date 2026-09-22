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

	@ConfigItem(
		keyName = "timeFormat",
		name = "Time format",
		description = "Show the time left in minutes and seconds, or in game ticks"
	)
	default TimeFormat timeFormat()
	{
		return TimeFormat.SECONDS;
	}
}
