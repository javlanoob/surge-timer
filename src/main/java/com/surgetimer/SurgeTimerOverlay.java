package com.surgetimer;

import com.google.common.collect.ImmutableSet;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.AsyncBufferedImage;

class SurgeTimerOverlay extends WidgetItemOverlay
{
	private static final Set<Integer> SURGE_POTIONS = ImmutableSet.of(
		ItemID._4DOSESURGE, ItemID._3DOSESURGE, ItemID._2DOSESURGE, ItemID._1DOSESURGE);
	private static final float SHADE_OPACITY = 0.6f;

	private final SurgeTimerPlugin plugin;
	private final ItemManager itemManager;
	private final SurgeTimerConfig config;
	private final Map<Integer, BufferedImage> shades = new HashMap<>();

	@Inject
	SurgeTimerOverlay(SurgeTimerPlugin plugin, ItemManager itemManager, SurgeTimerConfig config)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.config = config;
		showOnInventory();
	}

	static boolean isSurgePotion(int itemId)
	{
		return SURGE_POTIONS.contains(itemId);
	}

	void clearCache()
	{
		shades.clear();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!plugin.isOnCooldown() || !SURGE_POTIONS.contains(itemId))
		{
			return;
		}

		int ticksLeft = plugin.getTicksLeft();
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage shade = getShade(itemId);

		// The potion gets its color back from the top down as the cooldown runs out, so the shade
		// drawn over it only covers the part below that line
		int height = shade.getHeight();
		int shadeFrom = height - Math.min(height, height * ticksLeft / SurgeTimerPlugin.COOLDOWN_TICKS);
		graphics.drawImage(shade,
			bounds.x, bounds.y + shadeFrom, bounds.x + shade.getWidth(), bounds.y + height,
			0, shadeFrom, shade.getWidth(), height, null);

		String text = config.timeFormat() == SurgeTimerConfig.TimeFormat.TICKS
			? Integer.toString(ticksLeft)
			: formatTime(ticksLeft);
		graphics.setFont(FontManager.getRunescapeFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int x = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
		int y = bounds.y + (bounds.height + metrics.getAscent() - metrics.getDescent()) / 2;

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, x, y);
	}

	private BufferedImage getShade(int itemId)
	{
		return shades.computeIfAbsent(itemId, id ->
		{
			// Item images load asynchronously, so the shade is filled in once it's ready
			AsyncBufferedImage image = itemManager.getImage(id);
			BufferedImage shade = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			image.onLoaded(() ->
			{
				// Black in the shape of the potion, keeping its edges' transparency
				for (int y = 0; y < image.getHeight(); y++)
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						int alpha = (int) ((image.getRGB(x, y) >>> 24) * SHADE_OPACITY);
						shade.setRGB(x, y, alpha << 24);
					}
				}
			});
			return shade;
		});
	}

	private static String formatTime(int ticks)
	{
		// A tick is 0.6 seconds, rounded up so it never says 0:00 while still on cooldown
		int seconds = (ticks * 6 + 9) / 10;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
