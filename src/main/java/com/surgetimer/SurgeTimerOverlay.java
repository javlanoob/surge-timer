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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.widgets.WidgetUtil;
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
	private final SurgeTimerConfig config;
	private final ItemManager itemManager;
	private final Map<Integer, BufferedImage> grayImages = new HashMap<>();

	private boolean showInBank;
	private Color timerColor;
	private Color pausedColor;

	@Inject
	SurgeTimerOverlay(SurgeTimerPlugin plugin, SurgeTimerConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInventory();
		showOnBank();
	}

	void loadConfig()
	{
		showInBank = config.showInBank();
		timerColor = config.timerColor();
		pausedColor = config.pausedColor();
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

		if (!showInBank && WidgetUtil.componentToInterface(widgetItem.getWidget().getId()) == InterfaceID.BANKMAIN)
		{
			return;
		}

		// Drawn over the potion itself, so the whole potion looks grayed out
		Rectangle bounds = widgetItem.getDraggingCanvasBounds();
		graphics.drawImage(getGrayImage(itemId), bounds.x, bounds.y, null);

		String text = formatTime(plugin.getTicksLeft());
		graphics.setFont(FontManager.getRunescapeFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int x = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
		int y = bounds.y + (bounds.height + metrics.getAscent() - metrics.getDescent()) / 2;

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(plugin.isPaused() ? pausedColor : timerColor);
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
