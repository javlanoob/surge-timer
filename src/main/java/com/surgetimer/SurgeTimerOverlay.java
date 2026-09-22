package com.surgetimer;

import com.google.common.collect.ImmutableSet;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
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
	private static final int FADE_ROWS = 4;

	private final SurgeTimerPlugin plugin;
	private final ItemManager itemManager;
	private final SurgeTimerConfig config;
	private final Map<Integer, Shade> shades = new HashMap<>();

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
		Shade shade = getShade(itemId);

		// The potion gets its color back from one end as the cooldown runs out, so the shade only
		// covers the part past the edge. The edge moves over the potion itself rather than the whole
		// item image, which has empty space around it, and fades out over a few rows.
		boolean bottomUp = config.fillDirection() == SurgeTimerConfig.FillDirection.BOTTOM_UP;
		float left = (float) ticksLeft / SurgeTimerPlugin.COOLDOWN_TICKS;
		// How far the shade reaches into the potion, counted from the end it's anchored to
		float reach = (shade.bottom - shade.top + FADE_ROWS) * left - FADE_ROWS / 2f;
		int width = shade.image.getWidth();
		Composite composite = graphics.getComposite();
		for (int row = shade.top; row < shade.bottom; row++)
		{
			float depth = bottomUp ? row - shade.top : shade.bottom - 1 - row;
			float strength = Math.min(1, (reach - depth) / FADE_ROWS + 0.5f);
			if (strength <= 0)
			{
				continue;
			}

			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, strength));
			graphics.drawImage(shade.image,
				bounds.x, bounds.y + row, bounds.x + width, bounds.y + row + 1,
				0, row, width, row + 1, null);
		}
		graphics.setComposite(composite);

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

	private Shade getShade(int itemId)
	{
		return shades.computeIfAbsent(itemId, id ->
		{
			// Item images load asynchronously, so the shade is filled in once it's ready
			AsyncBufferedImage image = itemManager.getImage(id);
			Shade shade = new Shade(new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB));
			image.onLoaded(() ->
			{
				// Black in the shape of the potion, keeping its edges' transparency
				int top = image.getHeight();
				int bottom = 0;
				for (int y = 0; y < image.getHeight(); y++)
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						int alpha = image.getRGB(x, y) >>> 24;
						shade.image.setRGB(x, y, (int) (alpha * SHADE_OPACITY) << 24);
						if (alpha > 0)
						{
							top = Math.min(top, y);
							bottom = Math.max(bottom, y + 1);
						}
					}
				}

				if (top < bottom)
				{
					shade.top = top;
					shade.bottom = bottom;
				}
			});
			return shade;
		});
	}

	private static class Shade
	{
		private final BufferedImage image;
		// Rows the potion itself takes up in the image
		private int top;
		private int bottom;

		Shade(BufferedImage image)
		{
			this.image = image;
			this.bottom = image.getHeight();
		}
	}

	private static String formatTime(int ticks)
	{
		// A tick is 0.6 seconds, rounded up so it never says 0:00 while still on cooldown
		int seconds = (ticks * 6 + 9) / 10;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
