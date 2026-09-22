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
import net.runelite.client.util.ImageUtil;

class SurgeTimerOverlay extends WidgetItemOverlay
{
	private static final Set<Integer> SURGE_POTIONS = ImmutableSet.of(
		ItemID._4DOSESURGE, ItemID._3DOSESURGE, ItemID._2DOSESURGE, ItemID._1DOSESURGE);

	private final SurgeTimerPlugin plugin;
	private final ItemManager itemManager;
	private final Map<Integer, BufferedImage> grayImages = new HashMap<>();

	@Inject
	SurgeTimerOverlay(SurgeTimerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		showOnInventory();
	}

	void clearCache()
	{
		grayImages.clear();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!plugin.isOnCooldown() || !SURGE_POTIONS.contains(itemId))
		{
			return;
		}

		int ticksLeft = plugin.getTicksLeft();
		Rectangle bounds = widgetItem.getDraggingCanvasBounds();
		BufferedImage gray = getGrayImage(itemId);

		// The potion gets its color back from the top down as the cooldown runs out, so the gray
		// copy drawn over it only covers the part below that line
		int height = gray.getHeight();
		int grayFrom = height - Math.min(height, height * ticksLeft / SurgeTimerPlugin.COOLDOWN_TICKS);
		graphics.drawImage(gray,
			bounds.x, bounds.y + grayFrom, bounds.x + gray.getWidth(), bounds.y + height,
			0, grayFrom, gray.getWidth(), height, null);

		String text = formatTime(ticksLeft);
		graphics.setFont(FontManager.getRunescapeFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int x = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
		int y = bounds.y + (bounds.height + metrics.getAscent() - metrics.getDescent()) / 2;

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, x, y);
	}

	private BufferedImage getGrayImage(int itemId)
	{
		return grayImages.computeIfAbsent(itemId, id ->
		{
			// Item images load asynchronously, so the gray copy is filled in once it's ready
			AsyncBufferedImage image = itemManager.getImage(id);
			BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			image.onLoaded(() ->
			{
				Graphics2D g = gray.createGraphics();
				g.drawImage(ImageUtil.grayscaleImage(image), 0, 0, null);
				g.dispose();
			});
			return gray;
		});
	}

	private static String formatTime(int ticks)
	{
		// A tick is 0.6 seconds, rounded up so it never says 0:00 while still on cooldown
		int seconds = (ticks * 6 + 9) / 10;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
