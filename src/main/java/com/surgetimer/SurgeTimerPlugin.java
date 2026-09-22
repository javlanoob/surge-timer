package com.surgetimer;

import com.google.inject.Provides;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Surge Timer",
	description = "Darkens surge potions while they're on cooldown and shows when they can be drunk again",
	tags = {"surge", "potion", "special", "attack", "spec", "cooldown", "timer", "adrenaline"}
)
public class SurgeTimerPlugin extends Plugin
{
	// Five minutes
	static final int COOLDOWN_TICKS = 500;
	// The server lowers the timer varbit by one every 10 ticks
	private static final int TICKS_PER_STEP = 10;

	private static final String COOLDOWN_MESSAGE = "You are still full of adrenaline.";
	private static final String READY_MESSAGE = "You now feel capable of drinking another dose of surge potion.";
	private static final Pattern MINUTES = Pattern.compile("(\\d+) minutes?\\.");
	private static final Pattern SECONDS = Pattern.compile("(\\d+) seconds?\\.");

	// The cooldown pauses when one of these ends, until the next one starts
	private static final Pattern COLOSSEUM_WAVE_END = Pattern.compile("Wave \\d+ completed! Wave duration:.*");
	private static final String INFERNO_WAVE_END = "Wave completed!";
	private static final Pattern TOB_ROOM_END = Pattern.compile("Wave '.+' .*complete!.*");
	private static final Pattern DOOM_LEVEL_END = Pattern.compile("Delve level: .+ duration:.*");
	private static final Pattern WAVE_START = Pattern.compile("Wave: \\d+");
	private static final String SOL_HEREDIT_START = "Sol Heredit jumps down from his seat";
	private static final int INFERNO_REGION = 9043;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SurgeTimerOverlay overlay;

	// Last value of the timer varbit
	private int varbit;
	// Ticks left on the cooldown, counted down locally between varbit changes
	@Getter
	private int ticksLeft;
	// Lowest the local count can go before the varbit changes again
	private int floor;
	// Whether the cooldown is paused, like between Colosseum waves
	private boolean paused;

	@Provides
	SurgeTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SurgeTimerConfig.class);
	}

	@Override
	protected void startUp()
	{
		varbit = 0;
		ticksLeft = 0;
		floor = 0;
		paused = false;
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
		if (sync() || ticksLeft == 0 || paused)
		{
			return;
		}

		// Never count below what the varbit allows. If the cooldown pauses without it being
		// noticed, the varbit stops changing, so the timer stops here instead of running out.
		ticksLeft = Math.max(ticksLeft - 1, floor);
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
		if (value == varbit)
		{
			return false;
		}

		// The varbit only counts down one step at a time. A bigger drop means the cooldown was
		// removed, like by drinking from a house pool, which leaves it on 1 instead of 0.
		boolean removed = value > 0 && value < varbit - 1;
		varbit = value;
		// The varbit only changes while the cooldown is running
		paused = false;
		if (removed)
		{
			ticksLeft = 0;
			floor = 0;
			return true;
		}

		ticksLeft = value * TICKS_PER_STEP;
		floor = value > 0 ? ticksLeft - TICKS_PER_STEP + 1 : 0;
		return true;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		if (message.startsWith(COOLDOWN_MESSAGE))
		{
			resync(message);
		}
		else if (message.equals(READY_MESSAGE))
		{
			ticksLeft = 0;
			floor = 0;
			paused = false;
		}
		else if (COLOSSEUM_WAVE_END.matcher(message).matches()
			|| TOB_ROOM_END.matcher(message).matches()
			|| DOOM_LEVEL_END.matcher(message).matches()
			// The Fight Caves say the same, but the cooldown doesn't pause there
			|| (message.equals(INFERNO_WAVE_END) && inInferno()))
		{
			paused = true;
		}
		else if (WAVE_START.matcher(message).matches() || message.contains(SOL_HEREDIT_START))
		{
			paused = false;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// On while the fight in a Theatre of Blood room is going. It also turns off when leaving,
		// but leaving removes the cooldown anyway.
		if (event.getVarbitId() == VarbitID.TOB_CLIENT_WAVEPROGRESS_TYPE)
		{
			paused = event.getValue() == 0;
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		// Mokhaiotl appears when the next delve level starts
		if (event.getNpc().getId() == NpcID.DOM_BOSS)
		{
			paused = false;
		}
	}

	/**
	 * Trying to drink while on cooldown says how long is left, like "3 minutes." or "12 seconds.".
	 * The minutes are rounded, so they only give a range. The local count is moved into that
	 * range, and if the varbit says there's no cooldown at all, the message is trusted on its own.
	 */
	private void resync(String message)
	{
		int low;
		int high;
		Matcher seconds = SECONDS.matcher(message);
		Matcher minutes = MINUTES.matcher(message);
		if (seconds.find())
		{
			int s = Integer.parseInt(seconds.group(1));
			low = toTicks(s - 1) + 1;
			high = toTicks(s);
		}
		else if (minutes.find())
		{
			// Could be rounded up or to the nearest minute, so allow for both
			int m = Integer.parseInt(minutes.group(1));
			low = toTicks((m - 1) * 60) + 1;
			high = toTicks(m * 60 + 29);
		}
		else
		{
			return;
		}

		if (varbit > 0)
		{
			// The varbit is exact to 10 ticks, so it narrows the range further
			low = Math.max(low, floor);
			high = Math.min(high, varbit * TICKS_PER_STEP);
			if (low > high)
			{
				return;
			}
		}
		else
		{
			floor = 0;
		}

		ticksLeft = ticksLeft == 0 ? high : Math.max(low, Math.min(high, ticksLeft));
	}

	private boolean inInferno()
	{
		Player player = client.getLocalPlayer();
		return player != null
			&& WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID() == INFERNO_REGION;
	}

	private static int toTicks(int seconds)
	{
		// A tick is 0.6 seconds
		return Math.max(0, (seconds * 10 + 5) / 6);
	}

	boolean isOnCooldown()
	{
		return ticksLeft > 0;
	}
}
