package com.lootpop;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class LootPopOverlay extends Overlay
{
    private static final int ICON_WIDTH = 36;
    private static final int ICON_HEIGHT = 32;
    private static final int ICON_GAP = 1;
    private static final int TWO_ROW_MIN_WIDTH = 205;
    private static final int TWO_ROW_HEIGHT = 66;
    private static final int ULTRA_HEIGHT = 48;

    private final LootPopPlugin plugin;
    private final LootPopConfig config;
    private final ItemManager itemManager;

    @Inject
    private LootPopOverlay(
        LootPopPlugin plugin,
        LootPopConfig config,
        ItemManager itemManager
    )
    {
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
        setMovable(true);
        setResizable(true);
        setMinimumSize(48);
        setSnappable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        List<LootNotification> notifications = plugin.getVisibleNotifications();
        if (notifications.isEmpty())
        {
            return null;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        Font headerFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f);
        Font ultraFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 15f);
        List<EntrySize> entrySizes = new ArrayList<>(notifications.size());
        int maxWidth = 0;
        int totalHeight = 0;

        for (LootNotification notification : notifications)
        {
            EntrySize entrySize = measureEntry(g, notification, headerFont, ultraFont);
            entrySizes.add(entrySize);
            maxWidth = Math.max(maxWidth, entrySize.width);
            totalHeight += entrySize.height;
        }
        totalHeight += Math.max(0, notifications.size() - 1) * config.popupSpacing();

        double scale = config.scale() / 100.0;
        int outputWidth = (int) Math.ceil(maxWidth * scale);
        int outputHeight = (int) Math.ceil(totalHeight * scale);
        int offsetX = 0;
        int offsetY = 0;
        Dimension preferredSize = getPreferredSize();
        if (preferredSize != null && preferredSize.width > 0 && preferredSize.height > 0)
        {
            outputWidth = preferredSize.width;
            outputHeight = preferredSize.height;
            scale = Math.min(
                outputWidth / (double) maxWidth,
                outputHeight / (double) totalHeight
            );
            offsetX = (int) Math.round((outputWidth - maxWidth * scale) / 2.0);
            offsetY = (int) Math.round((outputHeight - totalHeight * scale) / 2.0);
        }

        g.translate(offsetX, offsetY);
        g.scale(scale, scale);

        long now = System.currentTimeMillis();
        int y = 0;
        for (int i = 0; i < notifications.size(); i++)
        {
            LootNotification notification = notifications.get(i);
            EntrySize entrySize = entrySizes.get(i);
            float alpha = notification.getAlpha(now, config.displayDuration(), config.fadeDuration());
            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));

            if (config.popupStyle() == PopupStyle.ULTRA_COMPACT)
            {
                drawUltraCompact(g, notification, 0, y, entrySize.width, entrySize.height, ultraFont, now);
            }
            else
            {
                drawTwoRow(g, notification, 0, y, entrySize.width, entrySize.height, headerFont, now);
            }

            g.setComposite(oldComposite);
            y += entrySize.height + config.popupSpacing();
        }

        g.dispose();
        return new Dimension(outputWidth, outputHeight);
    }

    private EntrySize measureEntry(
        Graphics2D g,
        LootNotification notification,
        Font headerFont,
        Font ultraFont
    )
    {
        int itemCount = Math.min(config.maxItems(), notification.getItems().size());
        int itemsWidth = itemCount * ICON_WIDTH + Math.max(0, itemCount - 1) * ICON_GAP;
        String value = formatGp(notification.getTotalValue());

        if (config.popupStyle() == PopupStyle.ULTRA_COMPACT)
        {
            g.setFont(ultraFont);
            FontMetrics metrics = g.getFontMetrics();
            int nameWidth = Math.min(92, metrics.stringWidth(notification.getSourceName()));
            int width = 10 + 3 + 8 + nameWidth + 8 + itemsWidth
                + 10 + metrics.stringWidth(value) + 10;
            return new EntrySize(Math.max(245, width), ULTRA_HEIGHT);
        }

        g.setFont(headerFont);
        FontMetrics metrics = g.getFontMetrics();
        int headerWidth = Math.min(145, metrics.stringWidth(notification.getSourceName()))
            + metrics.stringWidth(value)
            + 30;
        int width = Math.max(TWO_ROW_MIN_WIDTH, Math.max(itemsWidth + 14, headerWidth));
        return new EntrySize(width, TWO_ROW_HEIGHT);
    }

    private void drawTwoRow(
        Graphics2D g,
        LootNotification notification,
        int x,
        int y,
        int width,
        int height,
        Font font,
        long now
    )
    {
        TierVisual tier = tierFor(notification);
        drawBackground(g, x, y, width, height, config.popupStyle(), tier, now, notification);

        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        String value = formatGp(notification.getTotalValue());
        int valueX = x + width - 7 - metrics.stringWidth(value);
        int maxNameWidth = Math.max(20, valueX - (x + 7) - 8);
        String name = ellipsize(notification.getSourceName(), metrics, maxNameWidth);
        drawText(g, name, x + 7, y + 18, config.npcNameColour());
        drawText(g, value, valueX, y + 18, tier.valueColour);

        if (config.showSeparator())
        {
            g.setColor(withAlpha(tier.accentColour, 92));
            g.setStroke(new BasicStroke(1.0f));
            g.drawLine(x + 7, y + 24, x + width - 7, y + 24);
        }

        int itemCount = Math.min(config.maxItems(), notification.getItems().size());
        drawItems(g, notification.getItems(), itemCount, x + 7, y + 29);
    }

    private void drawUltraCompact(
        Graphics2D g,
        LootNotification notification,
        int x,
        int y,
        int width,
        int height,
        Font font,
        long now
    )
    {
        TierVisual tier = tierFor(notification);
        drawBackground(g, x, y, width, height, PopupStyle.ULTRA_COMPACT, tier, now, notification);

        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        String value = formatGp(notification.getTotalValue());
        int valueX = x + width - 9 - metrics.stringWidth(value);
        int nameX = x + 15;
        String name = ellipsize(notification.getSourceName(), metrics, 92);
        drawText(g, name, nameX, y + 29, config.npcNameColour());

        int itemCount = Math.min(config.maxItems(), notification.getItems().size());
        int itemsX = nameX + metrics.stringWidth(name) + 8;
        int itemsWidth = itemCount * ICON_WIDTH + Math.max(0, itemCount - 1) * ICON_GAP;
        drawItems(g, notification.getItems(), itemCount, itemsX, y + 8);

        if (config.showSeparator())
        {
            int separatorX = Math.min(valueX - 6, itemsX + itemsWidth + 4);
            g.setColor(withAlpha(tier.accentColour, 105));
            g.setStroke(new BasicStroke(1.0f));
            g.drawLine(separatorX, y + 9, separatorX, y + height - 9);
        }
        drawText(g, value, valueX, y + 29, tier.valueColour);
    }

    private void drawBackground(
        Graphics2D g,
        int x,
        int y,
        int width,
        int height,
        PopupStyle style,
        TierVisual tier,
        long now,
        LootNotification notification
    )
    {
        int opacity = config.backgroundMode() == BackgroundMode.SOLID
            ? config.solidOpacity()
            : config.transparentOpacity();
        Color background = withAlpha(config.backgroundColour(), opacity);
        int radius = style == PopupStyle.ULTRA_COMPACT ? 16 : style == PopupStyle.CLASSIC_FRAME ? 7 : 4;
        Shape panel = new RoundRectangle2D.Float(x, y, width, height, radius, radius);

        if (config.showShadow() && config.backgroundMode() == BackgroundMode.SOLID)
        {
            g.setColor(new Color(0, 0, 0, 75));
            g.fillRoundRect(x + 2, y + 3, width, height, radius, radius);
        }

        g.setColor(background);
        g.fill(panel);

        int tintAlpha = config.valueTintStrength() * 2;
        if (config.backgroundMode() == BackgroundMode.TRANSPARENT)
        {
            tintAlpha = tintAlpha * 3 / 4;
        }
        Paint oldPaint = g.getPaint();
        g.setPaint(new GradientPaint(
            x,
            y,
            withAlpha(tier.accentColour, tintAlpha),
            x + width,
            y + height,
            withAlpha(tier.accentColour, 0)
        ));
        g.fill(panel);
        g.setPaint(oldPaint);

        if (tier.legendary && config.legendaryEffects())
        {
            drawLegendaryEffects(g, panel, x, y, width, height, tier.accentColour, now, notification);
        }

        if (style == PopupStyle.CLASSIC_FRAME)
        {
            g.setColor(withAlpha(tier.accentColour, 220));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }
        else if (style == PopupStyle.MINIMAL_EDGE)
        {
            g.setColor(withAlpha(tier.accentColour, 225));
            g.fillRect(x, y, width, 2);
            g.fillRect(x, y, 3, 4);
        }
        else
        {
            g.setColor(withAlpha(tier.accentColour, 235));
            g.fillRoundRect(x + 4, y + 8, 3, height - 16, 3, 3);
        }
    }

    private void drawLegendaryEffects(
        Graphics2D g,
        Shape panel,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        long now,
        LootNotification notification
    )
    {
        Shape oldClip = g.getClip();
        g.clip(panel);

        int sweep = (int) ((now / 12L) % (width + 70)) - 35;
        g.setColor(new Color(255, 248, 196, 24));
        g.setStroke(new BasicStroke(11.0f));
        g.drawLine(x + sweep, y + height + 8, x + sweep + 34, y - 8);

        int seed = notification.getSourceName().hashCode() ^ (int) notification.getTotalValue();
        int horizontalRange = Math.max(1, width - 18);
        for (int i = 0; i < 9; i++)
        {
            double progress = ((now / 34L + i * 13L) % 90L) / 90.0;
            int baseX = Math.floorMod(seed + i * 47, horizontalRange);
            int drift = (int) Math.round(Math.sin(now / 280.0 + i * 1.7) * 4.0);
            int particleX = x + 9 + baseX + drift;
            int particleY = y + height - 5 - (int) Math.round(progress * (height - 8));
            int alpha = (int) Math.round(Math.sin(Math.PI * progress) * 145.0);
            int size = i % 3 == 0 ? 3 : 2;
            g.setColor(withAlpha(colour, alpha));
            g.fillOval(particleX, particleY, size, size);
        }

        g.setClip(oldClip);
    }

    private void drawItems(
        Graphics2D g,
        List<LootDisplayItem> items,
        int itemCount,
        int x,
        int y
    )
    {
        for (int i = 0; i < itemCount; i++)
        {
            LootDisplayItem item = items.get(i);
            BufferedImage image = getItemImage(item);
            if (image != null)
            {
                int itemX = x + i * (ICON_WIDTH + ICON_GAP);
                g.drawImage(image, itemX, y, null);
            }
        }
    }

    private BufferedImage getItemImage(LootDisplayItem item)
    {
        try
        {
            ItemComposition composition = itemManager.getItemComposition(item.getItemId());
            boolean showQuantity = composition.isStackable() || item.getQuantity() > 1;
            return itemManager.getImage(item.getItemId(), item.getQuantity(), showQuantity);
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }

    private TierVisual tierFor(LootNotification notification)
    {
        if (!config.enableValueColours())
        {
            return new TierVisual(config.accentColour(), config.totalValueColour(), false);
        }

        long value = notification.getTotalValue();
        if (value >= config.legendaryValuePrice())
        {
            return new TierVisual(config.legendaryValueColour(), config.legendaryValueColour(), true);
        }
        if (value >= config.insaneValuePrice())
        {
            return new TierVisual(config.insaneValueColour(), config.insaneValueColour(), false);
        }
        if (value >= config.highValuePrice())
        {
            return new TierVisual(config.highValueColour(), config.highValueColour(), false);
        }
        if (value >= config.mediumValuePrice())
        {
            return new TierVisual(config.mediumValueColour(), config.mediumValueColour(), false);
        }
        if (value >= config.lowValuePrice())
        {
            return new TierVisual(config.lowValueColour(), config.lowValueColour(), false);
        }
        return new TierVisual(config.accentColour(), config.totalValueColour(), false);
    }

    private static void drawText(Graphics2D g, String text, int x, int baseline, Color colour)
    {
        g.setColor(new Color(0, 0, 0, 190));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(colour);
        g.drawString(text, x, baseline);
    }

    private static Color withAlpha(Color colour, int alpha)
    {
        int combinedAlpha = Math.min(255, Math.max(0, alpha * colour.getAlpha() / 255));
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), combinedAlpha);
    }

    private static String ellipsize(String text, FontMetrics metrics, int maxWidth)
    {
        if (metrics.stringWidth(text) <= maxWidth)
        {
            return text;
        }

        String suffix = "...";
        int suffixWidth = metrics.stringWidth(suffix);
        int length = text.length();
        while (length > 0 && metrics.stringWidth(text.substring(0, length)) + suffixWidth > maxWidth)
        {
            length--;
        }
        return length == 0 ? suffix : text.substring(0, length) + suffix;
    }

    static String formatGp(long value)
    {
        if (value >= 1_000_000_000L)
        {
            return formatCompact(value, 1_000_000_000L, "B") + " gp";
        }
        if (value >= 1_000_000L)
        {
            return formatCompact(value, 1_000_000L, "M") + " gp";
        }
        if (value >= 10_000L)
        {
            return formatCompact(value, 1_000L, "K") + " gp";
        }
        return value + " gp";
    }

    private static String formatCompact(long value, long divisor, String suffix)
    {
        long tenths = value * 10L / divisor;
        if (tenths % 10L == 0L)
        {
            return (tenths / 10L) + suffix;
        }
        return (tenths / 10L) + "." + (tenths % 10L) + suffix;
    }

    private static final class TierVisual
    {
        private final Color accentColour;
        private final Color valueColour;
        private final boolean legendary;

        private TierVisual(Color accentColour, Color valueColour, boolean legendary)
        {
            this.accentColour = accentColour;
            this.valueColour = valueColour;
            this.legendary = legendary;
        }
    }

    private static final class EntrySize
    {
        private final int width;
        private final int height;

        private EntrySize(int width, int height)
        {
            this.width = width;
            this.height = height;
        }
    }
}
