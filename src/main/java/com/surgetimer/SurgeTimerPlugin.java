package com.surgetimer;

import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Surge Timer",
	description = "Grays out surge potions while they're on cooldown and shows when they can be drunk again",
	tags = {"surge", "potion", "special", "attack", "spec", "cooldown", "timer", "adrenaline"}
)
public class SurgeTimerPlugin extends Plugin
{
	// Five minutes
	static final int COOLDOWN_TICKS = 500;
	// The server lowers the timer varbit by one every 10 ticks
	private static final int TICKS_PER_STEP = 10;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SurgeTimerOverlay overlay;

	// Last value of the timer varbit, 0 when the potion can be drunk
	private int steps;
	// Ticks left on the cooldown, counted down locally between varbit changes
	@Getter
	private int ticksLeft;

	@Override
	protected void startUp()
	{
		steps = 0;
		ticksLeft = 0;
		overlayManager.add(overlay);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				sync();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlay.clearCache();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (sync() || steps == 0)
		{
			return;
		}

		// Never count below what the varbit allows. When the cooldown is paused the varbit stops
		// changing, so the timer stops here too instead of running out early.
		ticksLeft = Math.max(ticksLeft - 1, (steps - 1) * TICKS_PER_STEP + 1);
	}

	/**
	 * Reads the timer varbit, and restarts the local count if it changed. The varbit also
	 * covers the cooldown being removed, like on death or entering a raid.
	 *
	 * @return whether the varbit changed
	 */
	private boolean sync()
	{
		int value = client.getVarbitValue(VarbitID.SURGE_POTION_TIMER);
		if (value == steps)
		{
			return false;
		}

		steps = value;
		ticksLeft = value * TICKS_PER_STEP;
		return true;
	}

	boolean isOnCooldown()
	{
		return steps > 0;
	}
}
