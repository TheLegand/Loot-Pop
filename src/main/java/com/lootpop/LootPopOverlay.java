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
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

final class LootPopOverlay extends Overlay
{
    private static final int ICON_WIDTH = 36;
    private static final int ICON_HEIGHT = 32;
    private static final int ICON_GAP = 1;
    private static final int GRID_COLUMNS = 5;
    private static final int GRID_ROW_GAP = 2;
    private static final int TWO_ROW_MIN_WIDTH = 205;
    private static final int CLASSIC_HEIGHT = 66;
    private static final int SURVIVAL_MIN_WIDTH = 218;
    private static final int SURVIVAL_HEIGHT = 70;
    private static final int LOOT_STRIP_MIN_WIDTH = 252;
    private static final int LOOT_STRIP_HEIGHT = 50;
    private static final int VALUE_TINT_STRENGTH = 38;
    private static final Color TOOLTIP_LABEL_COLOUR = new Color(180, 180, 180);

    private final LootPopPlugin plugin;
    private final LootPopConfig config;
    private final ItemManager itemManager;
    private final Client client;
    private final TooltipManager tooltipManager;
    private double hoverX = -1.0;
    private double hoverY = -1.0;
    private double tooltipHoverX = -1.0;
    private double tooltipHoverY = -1.0;
    private boolean tooltipEntryActive;

    @Inject
    private LootPopOverlay(
        LootPopPlugin plugin,
        LootPopConfig config,
        ItemManager itemManager,
        Client client,
        TooltipManager tooltipManager
    )
    {
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.client = client;
        this.tooltipManager = tooltipManager;

        setPosition(OverlayPosition.BOTTOM_RIGHT);
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

        Dimension preferredSize = getPreferredSize();
        double scale = resolveScale(config.scale() / 100.0, preferredSize, maxWidth);
        int outputWidth = (int) Math.ceil(maxWidth * scale);
        if (preferredSize != null && preferredSize.width > 0)
        {
            outputWidth = preferredSize.width;
        }
        int outputHeight = (int) Math.ceil(totalHeight * scale);
        int offsetX = (int) Math.round((outputWidth - maxWidth * scale) / 2.0);
        int offsetY = 0;

        updateHoverPosition(scale, offsetX, offsetY);

        g.translate(offsetX, offsetY);
        g.scale(scale, scale);

        long now = System.currentTimeMillis();
        int y = 0;
        for (int i = 0; i < notifications.size(); i++)
        {
            LootNotification notification = notifications.get(i);
            EntrySize entrySize = entrySizes.get(i);
            boolean hovered = hoverX >= 0
                && hoverX < entrySize.width
                && hoverY >= y
                && hoverY < y + entrySize.height;
            notification.setHovered(config.pauseOnHover() && hovered, now);
            float alpha = notification.getAlpha(now, plugin.displayDurationFor(notification), config.fadeDuration());
            Graphics2D entryGraphics = (Graphics2D) g.create();
            entryGraphics.setComposite(AlphaComposite.SrcOver.derive(alpha));
            int drawY = y;
            tooltipEntryActive = hovered;
            tooltipHoverX = hoverX;
            tooltipHoverY = hoverY;
            if (isEnhancedMode() && config.smoothEntrance() && !config.reduceMotion())
            {
                double progress = entranceProgress(notification.getAgeMillis(now));
                double entryScale = 0.92 + progress * 0.08;
                int slideX = (int) Math.round((1.0 - progress) * 24.0);
                tooltipHoverX = (hoverX - slideX) / entryScale;
                tooltipHoverY = (hoverY - y - entrySize.height / 2.0) / entryScale
                    + entrySize.height / 2.0;
                entryGraphics.translate(slideX, y + entrySize.height / 2.0);
                entryGraphics.scale(entryScale, entryScale);
                entryGraphics.translate(0, -entrySize.height / 2.0);
                drawY = 0;
            }
            if (config.popupStyle() == PopupStyle.ULTRA_COMPACT)
            {
                drawLootStrip(entryGraphics, notification, 0, drawY, entrySize.width, entrySize.height, ultraFont, now);
            }
            else
            {
                drawTwoRow(entryGraphics, notification, 0, drawY, entrySize.width, entrySize.height, headerFont, now);
            }
            entryGraphics.dispose();
            y += entrySize.height + config.popupSpacing();
        }
        tooltipEntryActive = false;

        g.dispose();
        return new Dimension(outputWidth, outputHeight);
    }

    static double resolveScale(double configuredScale, Dimension preferredSize, int contentWidth)
    {
        if (preferredSize == null || preferredSize.width <= 0 || contentWidth <= 0)
        {
            return configuredScale;
        }

        return preferredSize.width / (double) contentWidth;
    }

    static double entranceProgress(long ageMillis)
    {
        double linear = Math.max(0.0, Math.min(1.0, ageMillis / 280.0));
        return 1.0 - Math.pow(1.0 - linear, 3.0);
    }

    private EntrySize measureEntry(
        Graphics2D g,
        LootNotification notification,
        Font headerFont,
        Font ultraFont
    )
    {
        int itemCount = displayedItemCount(notification.getItems());
        int itemsWidth = measureItemsGridWidth(g, notification.getItems(), itemCount);
        String value = formatGp(notification.getTotalValue());

        if (config.popupStyle() == PopupStyle.ULTRA_COMPACT)
        {
            g.setFont(ultraFont);
            FontMetrics metrics = g.getFontMetrics();
            int nameWidth = Math.min(92, metrics.stringWidth(notification.getSourceName()));
            int collectionBadgeWidth = notification.isCollectionLogUnlock() ? 31 : 0;
            int width = 15 + nameWidth + collectionBadgeWidth + 14 + itemsWidth
                + 12 + metrics.stringWidth(value) + 13;
            return new EntrySize(Math.max(LOOT_STRIP_MIN_WIDTH, width), LOOT_STRIP_HEIGHT);
        }

        g.setFont(headerFont);
        FontMetrics metrics = g.getFontMetrics();
        int collectionBadgeWidth = notification.isCollectionLogUnlock() ? 31 : 0;
        int headerWidth = Math.min(145, metrics.stringWidth(notification.getSourceName()))
            + collectionBadgeWidth
            + metrics.stringWidth(value)
            + 30;
        boolean survival = config.popupStyle() == PopupStyle.MINIMAL_EDGE;
        int minimumWidth = survival ? SURVIVAL_MIN_WIDTH : TWO_ROW_MIN_WIDTH;
        int contentPadding = survival ? 24 : 14;
        int width = Math.max(minimumWidth, Math.max(itemsWidth + contentPadding, headerWidth));
        int occupiedSlots = itemCount + (notification.getItems().size() > itemCount ? 1 : 0);
        int rows = Math.max(1, (occupiedSlots + GRID_COLUMNS - 1) / GRID_COLUMNS);
        int baseHeight = survival ? SURVIVAL_HEIGHT : CLASSIC_HEIGHT;
        return new EntrySize(width, baseHeight + (rows - 1) * (ICON_HEIGHT + GRID_ROW_GAP));
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
        boolean survival = config.popupStyle() == PopupStyle.MINIMAL_EDGE;
        int leftPadding = survival ? 14 : 7;
        String value = formatGp(notification.getTotalValue());
        int rightPadding = survival ? 12 : 7;
        int valueX = x + width - rightPadding - metrics.stringWidth(value);
        boolean lucky = isLucky(notification.getItems());
        int nameX = x + leftPadding + (lucky ? 13 : 0);
        int collectionBadgeWidth = notification.isCollectionLogUnlock() ? 31 : 0;
        int maxNameWidth = Math.max(20, valueX - nameX - 8 - collectionBadgeWidth);
        String name = ellipsize(notification.getSourceName(), metrics, maxNameWidth);
        int headerBaseline = y + (survival ? 20 : 18);
        if (lucky)
        {
            drawLuckyStar(g, x + leftPadding + 5, headerBaseline - 5, rarityColour(rarestRarity(notification.getItems())));
        }
        drawText(g, name, nameX, headerBaseline, config.npcNameColour());
        if (notification.isCollectionLogUnlock())
        {
            drawCollectionLogBadge(g, nameX + metrics.stringWidth(name) + 5, headerBaseline - 12);
        }
        drawText(g, value, valueX, headerBaseline, tier.valueColour);
        addTotalValueTooltipIfHovered(
            notification.getTotalValue(),
            valueX,
            headerBaseline,
            metrics,
            tier.valueColour
        );

        if (config.showSeparator())
        {
            g.setColor(withAlpha(tier.accentColour, 92));
            g.setStroke(new BasicStroke(1.0f));
            int separatorY = y + (survival ? 27 : 24);
            g.drawLine(x + leftPadding, separatorY, x + width - rightPadding, separatorY);
        }

        int itemCount = displayedItemCount(notification.getItems());
        drawItemsGrid(
            g,
            notification.getItems(),
            itemCount,
            x + leftPadding,
            y + (survival ? 33 : 29),
            tier.accentColour,
            now,
            notification
        );
    }

    private void drawLootStrip(
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
        int valueWidth = metrics.stringWidth(value);
        int valueChipWidth = valueWidth + 20;
        int valueChipX = x + width - valueChipWidth;
        int valueX = x + width - 10 - valueWidth;
        boolean lucky = isLucky(notification.getItems());
        int nameX = x + 15 + (lucky ? 13 : 0);
        int collectionBadgeWidth = notification.isCollectionLogUnlock() ? 31 : 0;
        String name = ellipsize(notification.getSourceName(), metrics, 92 - collectionBadgeWidth);
        if (lucky)
        {
            drawLuckyStar(g, x + 19, y + 26, rarityColour(rarestRarity(notification.getItems())));
        }
        drawText(g, name, nameX, y + 31, config.npcNameColour());
        if (notification.isCollectionLogUnlock())
        {
            drawCollectionLogBadge(g, nameX + metrics.stringWidth(name) + 4, y + 18);
        }

        int itemCount = displayedItemCount(notification.getItems());
        int itemsX = nameX + metrics.stringWidth(name) + collectionBadgeWidth + 14;
        int itemsWidth = measureItemsGridWidth(g, notification.getItems(), itemCount);
        drawItemsGrid(g, notification.getItems(), itemCount, itemsX, y + 9, tier.accentColour, now, notification);

        if (config.showSeparator())
        {
            int nameSeparatorX = itemsX - 7;
            int valueSeparatorX = Math.min(valueChipX, itemsX + itemsWidth + 7);
            g.setColor(withAlpha(tier.accentColour, 92));
            g.setStroke(new BasicStroke(1.0f));
            g.drawLine(nameSeparatorX, y + 10, nameSeparatorX, y + height - 10);
            g.drawLine(valueSeparatorX, y + 8, valueSeparatorX, y + height - 8);
        }

        Path2D valueModule = new Path2D.Float();
        valueModule.moveTo(valueChipX + 7, y + 5);
        valueModule.lineTo(x + width - 1, y + 5);
        valueModule.lineTo(x + width - 1, y + height - 6);
        valueModule.lineTo(valueChipX, y + height - 6);
        valueModule.closePath();
        g.setColor(withAlpha(tier.accentColour, isEnhancedMode() ? 34 : 18));
        g.fill(valueModule);
        g.setColor(withAlpha(tier.accentColour, isEnhancedMode() ? 115 : 72));
        g.setStroke(new BasicStroke(1.0f));
        g.drawLine(valueChipX + 7, y + 5, valueChipX, y + height - 6);
        drawText(g, value, valueX, y + 31, tier.valueColour);
        addTotalValueTooltipIfHovered(
            notification.getTotalValue(),
            valueX,
            y + 31,
            metrics,
            tier.valueColour
        );
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
        int radius = 7;
        Shape panel = createPanelShape(style, x, y, width, height);

        boolean enhanced = isEnhancedMode();
        if (enhanced && !config.reduceMotion() && tier.tier == ValueTier.LEGENDARY && config.legendaryEffects())
        {
            drawLegendaryBurst(
                g,
                x,
                y,
                width,
                height,
                tier.accentColour,
                now,
                notification,
                config.effectIntensity() / 100.0
            );
        }

        if (config.showShadow() && config.backgroundMode() == BackgroundMode.SOLID)
        {
            g.setColor(new Color(0, 0, 0, 75));
            g.fill(createPanelShape(style, x + 2, y + 3, width, height));
        }

        g.setColor(background);
        g.fill(panel);

        if (enhanced)
        {
            int tintAlpha = VALUE_TINT_STRENGTH * 2;
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

            drawGlassLayer(g, panel, x, y, width, height);

            if (effectsEnabled(tier.tier))
            {
                drawTierEffects(g, panel, x, y, width, height, tier, now, notification);
            }
        }

        if (style == PopupStyle.CLASSIC_FRAME)
        {
            g.setColor(withAlpha(tier.accentColour, 220));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);

            if (enhanced)
            {
                g.setColor(new Color(255, 255, 255, 22));
                g.setStroke(new BasicStroke(1.0f));
                g.drawRoundRect(x + 2, y + 2, width - 5, height - 5, Math.max(2, radius - 2), Math.max(2, radius - 2));
            }
        }
        else if (style == PopupStyle.MINIMAL_EDGE)
        {
            drawSurvivalChrome(g, panel, x, y, width, height, tier.accentColour, enhanced);
        }
        else
        {
            drawLootStripChrome(g, panel, x, y, width, height, tier.accentColour, enhanced);
        }
    }

    private static Shape createPanelShape(
        PopupStyle style,
        int x,
        int y,
        int width,
        int height
    )
    {
        if (style == PopupStyle.CLASSIC_FRAME)
        {
            return new RoundRectangle2D.Float(x, y, width, height, 7, 7);
        }

        int right = x + width - 1;
        int bottom = y + height - 1;
        Path2D panel = new Path2D.Float();
        if (style == PopupStyle.MINIMAL_EDGE)
        {
            int cut = 8;
            panel.moveTo(x + cut, y);
            panel.lineTo(right - cut, y);
            panel.lineTo(right, y + cut);
            panel.lineTo(right, bottom - cut);
            panel.lineTo(right - cut, bottom);
            panel.lineTo(x + cut, bottom);
            panel.lineTo(x, bottom - cut);
            panel.lineTo(x, y + cut);
        }
        else
        {
            int cut = 7;
            panel.moveTo(x + cut, y);
            panel.lineTo(right, y);
            panel.lineTo(right, bottom - cut);
            panel.lineTo(right - cut, bottom);
            panel.lineTo(x, bottom);
            panel.lineTo(x, y + cut);
        }
        panel.closePath();
        return panel;
    }

    private static void drawSurvivalChrome(
        Graphics2D g,
        Shape panel,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        boolean enhanced
    )
    {
        Shape oldClip = g.getClip();
        Paint oldPaint = g.getPaint();
        g.clip(panel);

        g.setColor(new Color(0, 0, 0, enhanced ? 72 : 48));
        g.fillRect(x, y, width, 28);

        g.setPaint(new GradientPaint(
            x + 7,
            y,
            withAlpha(colour, enhanced ? 235 : 190),
            x + width - 10,
            y,
            withAlpha(colour, 12)
        ));
        g.fillRect(x + 5, y, width - 10, enhanced ? 3 : 2);

        Path2D leftRail = new Path2D.Float();
        leftRail.moveTo(x, y + 8);
        leftRail.lineTo(x + 5, y + 3);
        leftRail.lineTo(x + 5, y + height - 4);
        leftRail.lineTo(x, y + height - 9);
        leftRail.closePath();
        g.setPaint(new GradientPaint(
            x,
            y + 3,
            withAlpha(colour, 245),
            x,
            y + height,
            withAlpha(colour, 95)
        ));
        g.fill(leftRail);

        g.setPaint(oldPaint);
        g.setClip(oldClip);

        g.setColor(new Color(0, 0, 0, 185));
        g.setStroke(new BasicStroke(2.0f));
        g.draw(panel);
        g.setColor(withAlpha(colour, enhanced ? 112 : 76));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(panel);

        g.setColor(withAlpha(colour, enhanced ? 150 : 105));
        g.drawLine(x + width - 12, y + 5, x + width - 5, y + 12);
        g.drawLine(x + width - 5, y + height - 13, x + width - 12, y + height - 6);

        if (enhanced)
        {
            g.setColor(new Color(255, 255, 255, 75));
            g.fillRect(x + 9, y + 7, 2, 2);
            g.fillRect(x + width - 11, y + height - 10, 2, 2);
        }
    }

    private static void drawLootStripChrome(
        Graphics2D g,
        Shape panel,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        boolean enhanced
    )
    {
        Shape oldClip = g.getClip();
        Paint oldPaint = g.getPaint();
        g.clip(panel);

        g.setPaint(new GradientPaint(
            x,
            y + height,
            withAlpha(colour, enhanced ? 225 : 175),
            x + width * 3 / 4,
            y + height,
            withAlpha(colour, 18)
        ));
        g.fillRect(x, y + height - (enhanced ? 3 : 2), width, enhanced ? 3 : 2);

        Path2D rail = new Path2D.Float();
        rail.moveTo(x, y + 7);
        rail.lineTo(x + 5, y + 2);
        rail.lineTo(x + 5, y + height - 5);
        rail.lineTo(x, y + height - 1);
        rail.closePath();
        g.setColor(withAlpha(colour, 235));
        g.fill(rail);

        g.setPaint(oldPaint);
        g.setClip(oldClip);

        g.setColor(new Color(0, 0, 0, 180));
        g.setStroke(new BasicStroke(2.0f));
        g.draw(panel);
        g.setColor(withAlpha(colour, enhanced ? 95 : 64));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(panel);

        if (enhanced)
        {
            g.setColor(new Color(255, 255, 255, 34));
            g.drawLine(x + 11, y + 2, x + width - 9, y + 2);
        }
    }

    private static void drawLegendaryBurst(
        Graphics2D g,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        long now,
        LootNotification notification,
        double intensity
    )
    {
        long age = notification.getAgeMillis(now);
        double impactStrength = Math.max(0.0, 1.0 - age / 1500.0);
        double pulse = (Math.sin(now / 170.0) + 1.0) / 2.0;
        int radius = 14;
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        int seed = notification.getSourceName().hashCode() ^ (int) notification.getTotalValue();

        // A tall ceremonial light pillar makes a legendary drop visible well beyond the card.
        Paint oldPaint = g.getPaint();
        int pillarAlpha = scaledAlpha(
            42 + (int) Math.round(pulse * 24.0) + (int) Math.round(impactStrength * 72.0),
            intensity
        );
        int pillarTop = y - 72 - (int) Math.round(impactStrength * 22.0);
        int pillarBottom = y + height + 55 + (int) Math.round(impactStrength * 18.0);
        int pillarHalfWidth = Math.max(16, width / 5);
        g.setPaint(new GradientPaint(
            centerX,
            pillarTop,
            withAlpha(colour, 0),
            centerX,
            centerY,
            withAlpha(Color.WHITE, pillarAlpha)
        ));
        g.fillPolygon(
            new int[] {centerX - 7, centerX + 7, centerX + pillarHalfWidth, centerX - pillarHalfWidth},
            new int[] {pillarTop, pillarTop, centerY, centerY},
            4
        );
        g.setPaint(new GradientPaint(
            centerX,
            centerY,
            withAlpha(colour, pillarAlpha * 3 / 4),
            centerX,
            pillarBottom,
            withAlpha(colour, 0)
        ));
        g.fillPolygon(
            new int[] {centerX - pillarHalfWidth, centerX + pillarHalfWidth, centerX + 9, centerX - 9},
            new int[] {centerY, centerY, pillarBottom, pillarBottom},
            4
        );
        g.setPaint(oldPaint);

        // Crimson energy shards rain around the popup throughout its lifetime.
        int rainCount = scaledCount(24, intensity);
        int rainWidth = width + 96;
        for (int i = 0; i < rainCount; i++)
        {
            double progress = ((now / 18L + i * 17L) % 140L) / 140.0;
            int rainX = x - 48 + Math.floorMod(seed + i * 83, rainWidth);
            int rainY = y - 42 + (int) Math.round(progress * (height + 92.0));
            int alpha = scaledAlpha((int) Math.round(Math.sin(Math.PI * progress) * 190.0), intensity);
            int length = 3 + i % 5;
            g.setColor(withAlpha(i % 6 == 0 ? Color.WHITE : colour, alpha));
            g.setStroke(new BasicStroke(i % 7 == 0 ? 2.0f : 1.2f));
            g.drawLine(rainX - 1, rainY - length, rainX + 1, rainY + length);
            if (i % 5 == 0)
            {
                g.drawLine(rainX - 2, rainY, rainX + 2, rainY);
            }
        }

        // A crown-like eight-point star hovers above the source name.
        int crownY = y - 19 - (int) Math.round(pulse * 3.0);
        int crownArm = 7 + (int) Math.round(impactStrength * 5.0);
        int crownAlpha = scaledAlpha(
            185 + (int) Math.round(pulse * 45.0) + (int) Math.round(impactStrength * 25.0),
            intensity
        );
        g.setColor(withAlpha(Color.WHITE, crownAlpha));
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(centerX - crownArm, crownY, centerX + crownArm, crownY);
        g.drawLine(centerX, crownY - crownArm, centerX, crownY + crownArm);
        int diagonalArm = Math.max(3, crownArm / 2);
        g.setColor(withAlpha(colour, crownAlpha));
        g.drawLine(centerX - diagonalArm, crownY - diagonalArm, centerX + diagonalArm, crownY + diagonalArm);
        g.drawLine(centerX - diagonalArm, crownY + diagonalArm, centerX + diagonalArm, crownY - diagonalArm);
        g.fillOval(centerX - 3, crownY - 3, 7, 7);

        // A breathing aura remains for the full lifetime of the legendary popup.
        for (int ring = 0; ring < 5; ring++)
        {
            int expansion = 4 + ring * 4 + (int) Math.round(pulse * 5.0);
            int alpha = scaledAlpha(94 - ring * 16, intensity);
            g.setColor(withAlpha(ring == 0 ? Color.WHITE : colour, alpha));
            g.setStroke(new BasicStroke(ring < 2 ? 2.2f : 1.0f));
            g.drawRoundRect(
                x - expansion,
                y - expansion,
                width + expansion * 2 - 1,
                height + expansion * 2 - 1,
                radius + expansion,
                radius + expansion
            );
        }

        // Three one-shot shockwaves make the first second feel like an impact.
        for (int wave = 0; wave < 3; wave++)
        {
            long waveAge = age - wave * 170L;
            if (waveAge < 0L || waveAge > 1150L)
            {
                continue;
            }

            double progress = waveAge / 1150.0;
            int expansion = 7 + (int) Math.round(progress * 58.0);
            int alpha = scaledAlpha((int) Math.round((1.0 - progress) * (1.0 - progress) * 225.0), intensity);
            float stroke = (float) Math.max(1.0, 4.0 - progress * 3.0);
            g.setColor(withAlpha(wave == 0 ? Color.WHITE : colour, alpha));
            g.setStroke(new BasicStroke(stroke));
            g.drawRoundRect(
                x - expansion,
                y - expansion,
                width + expansion * 2 - 1,
                height + expansion * 2 - 1,
                radius + expansion,
                radius + expansion
            );
        }

        // Long rotating rays become dramatically larger during the impact.
        int rayCount = scaledCount(20, intensity);
        int rayAlpha = scaledAlpha(
            115 + (int) Math.round(pulse * 55.0) + (int) Math.round(impactStrength * 70.0),
            intensity
        );
        for (int i = 0; i < rayCount; i++)
        {
            double angle = Math.PI * 2.0 * i / rayCount + now / 3600.0;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int startX = centerX + (int) Math.round(cos * (width / 2.0 + 7.0));
            int startY = centerY + (int) Math.round(sin * (height / 2.0 + 7.0));
            int extra = 18 + (i % 4) * 5
                + (int) Math.round(pulse * 8.0)
                + (int) Math.round(impactStrength * 30.0);
            int endX = centerX + (int) Math.round(cos * (width / 2.0 + extra));
            int endY = centerY + (int) Math.round(sin * (height / 2.0 + extra));
            boolean majorRay = i % 5 == 0;
            g.setColor(withAlpha(majorRay ? Color.WHITE : colour, majorRay ? rayAlpha : rayAlpha * 3 / 4));
            g.setStroke(new BasicStroke(majorRay ? 2.4f : 1.2f));
            g.drawLine(startX, startY, endX, endY);
        }

        // A single outward explosion of fragments accompanies the shockwaves.
        if (age <= 1800L)
        {
            double progress = age / 1800.0;
            int fragmentCount = scaledCount(30, intensity);
            int alpha = scaledAlpha((int) Math.round((1.0 - progress) * 245.0), intensity);
            for (int i = 0; i < fragmentCount; i++)
            {
                double angle = Math.PI * 2.0 * Math.floorMod(seed + i * 79, 360) / 360.0;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double travel = 12.0 + progress * (45.0 + (i % 6) * 5.0);
                int fragmentX = centerX + (int) Math.round(cos * (width / 2.0 + travel));
                int fragmentY = centerY + (int) Math.round(sin * (height / 2.0 + travel * 0.72));
                int tail = 3 + i % 5;

                g.setColor(withAlpha(i % 4 == 0 ? Color.WHITE : colour, alpha));
                g.setStroke(new BasicStroke(i % 6 == 0 ? 2.0f : 1.0f));
                g.drawLine(
                    fragmentX - (int) Math.round(cos * tail),
                    fragmentY - (int) Math.round(sin * tail),
                    fragmentX,
                    fragmentY
                );
                if (i % 5 == 0)
                {
                    g.drawLine(fragmentX - 2, fragmentY, fragmentX + 2, fragmentY);
                    g.drawLine(fragmentX, fragmentY - 2, fragmentX, fragmentY + 2);
                }
            }
        }

        // Continuous comets and twinkles keep the drop alive after the impact.
        int starCount = scaledCount(22, intensity);
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < starCount; i++)
        {
            double progress = ((now / 20L + i * 11L) % 110L) / 110.0;
            double angle = Math.PI * 2.0 * Math.floorMod(seed + i * 67, 360) / 360.0;
            double distanceX = width / 2.0 + 8.0 + progress * 38.0;
            double distanceY = height / 2.0 + 7.0 + progress * 27.0;
            int starX = centerX + (int) Math.round(Math.cos(angle) * distanceX);
            int starY = centerY + (int) Math.round(Math.sin(angle) * distanceY);
            int alpha = scaledAlpha((int) Math.round(Math.sin(Math.PI * progress) * 230.0), intensity);
            int arm = i % 6 == 0 ? 4 : i % 3 == 0 ? 3 : i % 2 == 0 ? 2 : 1;

            g.setColor(withAlpha(i % 4 == 0 ? Color.WHITE : colour, alpha));
            g.drawLine(starX - arm, starY, starX + arm, starY);
            g.drawLine(starX, starY - arm, starX, starY + arm);
            if (arm >= 2)
            {
                g.drawLine(starX - 1, starY - 1, starX + 1, starY + 1);
                g.drawLine(starX - 1, starY + 1, starX + 1, starY - 1);
            }

            if (i % 4 == 1)
            {
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                g.drawLine(
                    starX - (int) Math.round(cos * 7.0),
                    starY - (int) Math.round(sin * 7.0),
                    starX,
                    starY
                );
            }
        }
    }

    private void drawGlassLayer(
        Graphics2D g,
        Shape panel,
        int x,
        int y,
        int width,
        int height
    )
    {
        Shape oldClip = g.getClip();
        Paint oldPaint = g.getPaint();
        g.clip(panel);

        g.setPaint(new GradientPaint(
            x,
            y,
            new Color(255, 255, 255, 34),
            x,
            y + Math.max(1, height * 2 / 3),
            new Color(255, 255, 255, 0)
        ));
        g.fillRect(x, y, width, height);

        g.setPaint(new GradientPaint(
            x,
            y,
            new Color(255, 255, 255, 18),
            x + Math.max(1, width * 3 / 5),
            y + height,
            new Color(255, 255, 255, 0)
        ));
        g.fillRect(x, y, width, height);

        g.setPaint(oldPaint);
        g.setColor(new Color(255, 255, 255, 26));
        g.drawLine(x + 6, y + 2, x + width - 7, y + 2);
        g.setClip(oldClip);
    }

    private boolean effectsEnabled(ValueTier tier)
    {
        if (config.reduceMotion())
        {
            return false;
        }
        switch (tier)
        {
            case LOW:
                return config.lowValueEffects();
            case MEDIUM:
                return config.mediumValueEffects();
            case HIGH:
                return config.highValueEffects();
            case INSANE:
                return config.insaneValueEffects();
            case LEGENDARY:
                return config.legendaryEffects();
            default:
                return false;
        }
    }

    private void drawTierEffects(
        Graphics2D g,
        Shape panel,
        int x,
        int y,
        int width,
        int height,
        TierVisual tier,
        long now,
        LootNotification notification
    )
    {
        Shape oldClip = g.getClip();
        g.clip(panel);

        int seed = notification.getSourceName().hashCode() ^ (int) notification.getTotalValue();
        double intensity = config.effectIntensity() / 100.0;
        switch (tier.tier)
        {
            case LOW:
                drawFloatingMotes(g, x, y, width, height, tier.accentColour, now, seed, intensity);
                break;
            case MEDIUM:
                drawRisingSparkles(g, x, y, width, height, tier.accentColour, now, seed, intensity);
                break;
            case HIGH:
                drawEmbers(g, x, y, width, height, tier.accentColour, now, seed, intensity);
                break;
            case INSANE:
                drawEnergyPulse(g, x, y, width, height, tier.accentColour, now, seed, intensity);
                break;
            case LEGENDARY:
                drawLegendaryEffects(
                    g,
                    x,
                    y,
                    width,
                    height,
                    tier.accentColour,
                    now,
                    seed,
                    intensity,
                    notification.getAgeMillis(now)
                );
                break;
            default:
                break;
        }

        g.setClip(oldClip);
    }

    private static void drawFloatingMotes(
        Graphics2D g,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        long now,
        int seed,
        double intensity
    )
    {
        int count = scaledCount(5, intensity);
        int horizontalRange = Math.max(1, width - 14);
        for (int i = 0; i < count; i++)
        {
            double progress = ((now / 58L + i * 23L) % 110L) / 110.0;
            int baseX = Math.floorMod(seed + i * 43, horizontalRange);
            int drift = (int) Math.round(Math.sin(now / 420.0 + i * 1.8) * 3.0);
            int particleX = x + 7 + baseX + drift;
            int particleY = y + height - 6 - (int) Math.round(progress * (height - 12));
            int alpha = scaledAlpha((int) Math.round(Math.sin(Math.PI * progress) * 72.0), intensity);
            int size = i % 4 == 0 ? 3 : 2;
            g.setColor(withAlpha(colour, alpha));
            g.fillOval(particleX, particleY, size, size);
        }
    }

    private static void drawRisingSparkles(
        Graphics2D g,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        long now,
        int seed,
        double intensity
    )
    {
        int count = scaledCount(7, intensity);
        int horizontalRange = Math.max(1, width - 18);
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < count; i++)
        {
            double progress = ((now / 45L + i * 29L) % 125L) / 125.0;
            int particleX = x + 9 + Math.floorMod(seed + i * 59, horizontalRange);
            int particleY = y + height - 6 - (int) Math.round(progress * (height - 12));
            int alpha = scaledAlpha((int) Math.round(Math.sin(Math.PI * progress) * 105.0), intensity);
            int arm = i % 3 == 0 ? 2 : 1;
            g.setColor(withAlpha(colour, alpha));
            g.drawLine(particleX - arm, particleY, particleX + arm, particleY);
            g.drawLine(particleX, particleY - arm, particleX, particleY + arm);
        }
    }

    private static void drawEmbers(
        Graphics2D g,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        long now,
        int seed,
        double intensity
    )
    {
        int count = scaledCount(9, intensity);
        int horizontalRange = Math.max(1, width - 18);
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < count; i++)
        {
            double progress = ((now / 28L + i * 17L) % 105L) / 105.0;
            int baseX = Math.floorMod(seed + i * 37, horizontalRange);
            int drift = (int) Math.round(Math.sin(now / 190.0 + i * 1.3) * 5.0);
            int particleX = x + 9 + baseX + drift;
            int particleY = y + height - 5 - (int) Math.round(progress * (height - 9));
            int alpha = scaledAlpha((int) Math.round(Math.sin(Math.PI * progress) * 145.0), intensity);
            g.setColor(withAlpha(colour, alpha));
            g.drawLine(particleX, particleY + 3, particleX, particleY);
            if (i % 3 == 0)
            {
                g.fillOval(particleX - 1, particleY - 1, 3, 3);
            }
        }
    }

    private static void drawEnergyPulse(
        Graphics2D g,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        long now,
        int seed,
        double intensity
    )
    {
        double pulse = (Math.sin(now / 175.0) + 1.0) / 2.0;
        int pulseAlpha = scaledAlpha(40 + (int) Math.round(pulse * 64.0), intensity);
        g.setColor(withAlpha(colour, pulseAlpha));
        g.setStroke(new BasicStroke(2.0f));
        g.drawRoundRect(x + 2, y + 2, width - 5, height - 5, 9, 9);

        int count = scaledCount(11, intensity);
        int horizontalRange = Math.max(1, width - 20);
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < count; i++)
        {
            double progress = ((now / 31L + i * 23L) % 115L) / 115.0;
            int particleX = x + 10 + Math.floorMod(seed + i * 61, horizontalRange);
            int particleY = y + 6 + Math.floorMod(seed / 3 + i * 29, Math.max(1, height - 12));
            int flicker = (int) Math.round((0.5 + 0.5 * Math.sin(now / 95.0 + i)) * 115.0);
            int alpha = scaledAlpha((int) Math.round(flicker * Math.sin(Math.PI * progress)), intensity);
            int arm = i % 4 == 0 ? 2 : 1;
            g.setColor(withAlpha(colour, alpha));
            g.drawLine(particleX - arm, particleY, particleX + arm, particleY);
            g.drawLine(particleX, particleY - arm, particleX, particleY + arm);
        }
    }

    private static void drawLegendaryEffects(
        Graphics2D g,
        int x,
        int y,
        int width,
        int height,
        Color colour,
        long now,
        int seed,
        double intensity,
        long age
    )
    {
        if (age < 850L)
        {
            double flash = 1.0 - age / 850.0;
            int flashAlpha = scaledAlpha((int) Math.round(flash * 125.0), intensity);
            g.setColor(new Color(255, 225, 228, flashAlpha));
            g.fillRoundRect(x, y, width, height, 10, 10);
        }

        int sweep = (int) ((now / 10L) % (width + 80)) - 40;
        g.setColor(withAlpha(colour, scaledAlpha(48, intensity)));
        g.setStroke(new BasicStroke(11.0f));
        g.drawLine(x + sweep, y + height + 8, x + sweep + 34, y - 8);

        int count = scaledCount(14, intensity);
        int horizontalRange = Math.max(1, width - 18);
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < count; i++)
        {
            double progress = ((now / 25L + i * 13L) % 95L) / 95.0;
            int baseX = Math.floorMod(seed + i * 47, horizontalRange);
            int drift = (int) Math.round(Math.sin(now / 230.0 + i * 1.7) * 5.0);
            int particleX = x + 9 + baseX + drift;
            int particleY = y + height - 5 - (int) Math.round(progress * (height - 8));
            int alpha = scaledAlpha((int) Math.round(Math.sin(Math.PI * progress) * 175.0), intensity);
            int size = i % 4 == 0 ? 3 : 2;
            g.setColor(withAlpha(colour, alpha));
            g.fillOval(particleX, particleY, size, size);
            if (i % 4 == 0)
            {
                g.drawLine(particleX - 2, particleY + 1, particleX + 4, particleY + 1);
                g.drawLine(particleX + 1, particleY - 2, particleX + 1, particleY + 4);
            }
        }
    }

    private static int scaledCount(int baseCount, double intensity)
    {
        return Math.max(1, Math.min(24, (int) Math.round(baseCount * intensity)));
    }

    private static int scaledAlpha(int alpha, double intensity)
    {
        return Math.max(0, Math.min(255, (int) Math.round(alpha * intensity)));
    }

    private void drawItemsGrid(
        Graphics2D g,
        List<LootDisplayItem> items,
        int itemCount,
        int x,
        int y,
        Color accentColour,
        long now,
        LootNotification notification
    )
    {
        Font oldFont = g.getFont();
        Font quantityFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 14f);
        int cursorX = x;
        int cursorY = y;
        for (int i = 0; i < itemCount; i++)
        {
            if (i > 0 && i % GRID_COLUMNS == 0)
            {
                cursorX = x;
                cursorY += ICON_HEIGHT + GRID_ROW_GAP;
            }
            LootDisplayItem item = items.get(i);
            Composite oldItemComposite = g.getComposite();
            float revealAlpha = itemRevealAlpha(notification.getAgeMillis(now), i);
            if (revealAlpha < 1.0f && oldItemComposite instanceof AlphaComposite)
            {
                float parentAlpha = ((AlphaComposite) oldItemComposite).getAlpha();
                g.setComposite(AlphaComposite.SrcOver.derive(parentAlpha * revealAlpha));
            }
            BufferedImage image = getItemImage(item);
            int slotWidth = itemSlotWidth(g, item, quantityFont);
            int itemX = cursorX + (slotWidth - ICON_WIDTH) / 2;
            if (image != null)
            {
                if (isEnhancedMode() && config.itemSpotlights())
                {
                    DropRarity rarity = rarityFor(item);
                    if (rarity == DropRarity.ULTRA_RARE)
                    {
                        drawUltraRareItemGlow(g, itemX, cursorY, rarityColour(rarity), now, i);
                    }
                }
                if (isEnhancedMode())
                {
                    TierVisual itemTier = tierForValue(item.getTotalValue());
                    if (itemTier.tier != ValueTier.BASE)
                    {
                        g.setColor(withAlpha(itemTier.accentColour, 24));
                        g.fillOval(itemX + 2, cursorY - 1, 32, 34);
                        g.setColor(withAlpha(itemTier.accentColour, 42));
                        g.fillOval(itemX + 7, cursorY + 4, 22, 24);
                    }
                }
                g.drawImage(image, itemX, cursorY, null);

                if (item.getQuantity() > 1)
                {
                    g.setFont(quantityFont);
                    drawStackText(
                        g,
                        formatQuantityLabel(item.getQuantity()),
                        cursorX + 1,
                        cursorY + 13,
                        quantityColourForValue(item.getTotalValue())
                    );
                }
            }

            g.setComposite(oldItemComposite);

            if (tooltipEntryActive
                && config.showItemTooltips()
                && tooltipHoverX >= itemX
                && tooltipHoverX < itemX + ICON_WIDTH
                && tooltipHoverY >= cursorY
                && tooltipHoverY < cursorY + ICON_HEIGHT)
            {
                tooltipManager.add(new Tooltip(buildItemTooltip(item)));
            }
            cursorX += slotWidth + ICON_GAP;
        }

        int hiddenCount = items.size() - itemCount;
        if (hiddenCount > 0)
        {
            drawOverflowIndicator(
                g,
                items,
                itemCount,
                cursorX,
                cursorY,
                accentColour
            );
        }
        g.setFont(oldFont);
    }

    private float itemRevealAlpha(long ageMillis, int itemIndex)
    {
        if (!isEnhancedMode() || !config.smoothEntrance() || config.reduceMotion())
        {
            return 1.0f;
        }
        long delay = 70L + itemIndex * 45L;
        return (float) Math.max(0.0, Math.min(1.0, (ageMillis - delay) / 150.0));
    }

    private static int itemSlotWidth(Graphics2D g, LootDisplayItem item, Font quantityFont)
    {
        if (item.getQuantity() <= 1)
        {
            return ICON_WIDTH;
        }

        Font oldFont = g.getFont();
        g.setFont(quantityFont);
        int labelWidth = g.getFontMetrics().stringWidth(formatQuantityLabel(item.getQuantity()));
        g.setFont(oldFont);
        return Math.max(ICON_WIDTH, labelWidth + 4);
    }

    private static int measureItemsGridWidth(Graphics2D g, List<LootDisplayItem> items, int itemCount)
    {
        Font quantityFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 14f);
        int rowWidth = 0;
        int maxWidth = 0;
        for (int i = 0; i < itemCount; i++)
        {
            if (i > 0 && i % GRID_COLUMNS == 0)
            {
                maxWidth = Math.max(maxWidth, rowWidth);
                rowWidth = 0;
            }
            if (rowWidth > 0)
            {
                rowWidth += ICON_GAP;
            }
            rowWidth += itemSlotWidth(g, items.get(i), quantityFont);
        }

        if (items.size() > itemCount)
        {
            if (rowWidth > 0)
            {
                rowWidth += ICON_GAP;
            }
            rowWidth += ICON_WIDTH;
        }
        return Math.max(maxWidth, rowWidth);
    }

    private void drawOverflowIndicator(
        Graphics2D g,
        List<LootDisplayItem> items,
        int itemCount,
        int x,
        int y,
        Color accentColour
    )
    {
        int hiddenCount = items.size() - itemCount;
        String label = "+" + hiddenCount;
        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 13f));
        FontMetrics metrics = g.getFontMetrics();
        int badgeSize = 27;
        int badgeX = x + 4;
        int badgeY = y + 3;
        int textX = badgeX + (badgeSize - metrics.stringWidth(label)) / 2;
        int textY = badgeY + (badgeSize + metrics.getAscent() - metrics.getDescent()) / 2;

        g.setColor(new Color(0, 0, 0, 145));
        g.fillRoundRect(badgeX, badgeY, badgeSize, badgeSize, 9, 9);
        g.setColor(withAlpha(accentColour, 150));
        g.drawRoundRect(badgeX, badgeY, badgeSize - 1, badgeSize - 1, 9, 9);
        drawText(g, label, textX, textY, Color.WHITE);
        if (tooltipEntryActive
            && config.showItemTooltips()
            && tooltipHoverX >= badgeX
            && tooltipHoverX < badgeX + badgeSize
            && tooltipHoverY >= badgeY
            && tooltipHoverY < badgeY + badgeSize)
        {
            tooltipManager.add(new Tooltip(buildHiddenItemsTooltip(items, itemCount)));
        }
        g.setFont(oldFont);
    }

    private int displayedItemCount(List<LootDisplayItem> items)
    {
        int capacity = config.popupStyle() == PopupStyle.ULTRA_COMPACT ? GRID_COLUMNS : GRID_COLUMNS * 2;
        int requested = Math.min(Math.min(config.maxItems(), capacity), items.size());
        if (items.size() > requested && requested > 0 && requested % GRID_COLUMNS == 0)
        {
            return requested - 1;
        }
        return requested;
    }

    private boolean isEnhancedMode()
    {
        return config.visualMode() == VisualMode.ENHANCED;
    }

    private void updateHoverPosition(double scale, int offsetX, int offsetY)
    {
        hoverX = -1.0;
        hoverY = -1.0;
        if (scale <= 0.0)
        {
            return;
        }

        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        Rectangle bounds = getBounds();
        if (mouse == null || bounds == null)
        {
            return;
        }

        hoverX = (mouse.getX() - bounds.x - offsetX) / scale;
        hoverY = (mouse.getY() - bounds.y - offsetY) / scale;
    }

    private String buildItemTooltip(LootDisplayItem item)
    {
        int canonicalId = itemManager.canonicalize(item.getItemId());
        ItemComposition composition = itemManager.getItemComposition(canonicalId);
        String itemName = composition == null ? "Unknown item" : composition.getName();
        Color tierColour = quantityColourForValue(item.getTotalValue());
        NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
        long gePrice = Math.max(0L, itemManager.getItemPrice(canonicalId));
        long highAlchemyPrice = composition == null ? 0L : Math.max(0L, composition.getHaPrice());
        String dropRate = item.getDropRate() == null ? "N/A" : item.getDropRate();
        DropRarity rarity = rarityFor(item);
        Color dropRateColour = item.getDropRate() == null
            ? TOOLTIP_LABEL_COLOUR
            : rarityColour(rarity);
        HoverDetailMode mode = config.hoverDetailMode();
        boolean showPrices = mode == HoverDetailMode.DETAILED
            || (mode == HoverDetailMode.CUSTOM && config.hoverShowPrices());
        boolean showStack = mode != HoverDetailMode.CUSTOM || config.hoverShowStackValue();
        boolean showChance = mode != HoverDetailMode.CUSTOM || config.hoverShowDropChance();

        StringBuilder tooltip = new StringBuilder(ColorUtil.wrapWithColorTag(itemName, tierColour));
        tooltip.append(tooltipRow("Qty", numbers.format(item.getQuantity()), Color.WHITE));
        if (showPrices)
        {
            tooltip.append(tooltipRow("GE", formatTooltipPrice(gePrice, numbers), Color.WHITE));
            tooltip.append(tooltipRow("HA", formatTooltipPrice(highAlchemyPrice, numbers), Color.WHITE));
        }
        if (showStack)
        {
            tooltip.append(tooltipRow("Stack", formatTooltipPrice(item.getTotalValue(), numbers), tierColour));
        }
        if (showChance)
        {
            tooltip.append(tooltipRow("Chance", dropRate, dropRateColour));
        }
        return tooltip.toString();
    }

    private void addTotalValueTooltipIfHovered(
        long totalValue,
        int textX,
        int baseline,
        FontMetrics metrics,
        Color valueColour
    )
    {
        String compactValue = formatGp(totalValue);
        int padding = 4;
        int left = textX - padding;
        int right = textX + metrics.stringWidth(compactValue) + padding;
        int top = baseline - metrics.getAscent() - padding;
        int bottom = baseline + metrics.getDescent() + padding;
        if (!tooltipEntryActive
            || tooltipHoverX < left || tooltipHoverX >= right
            || tooltipHoverY < top || tooltipHoverY >= bottom)
        {
            return;
        }

        String priceSource = config.priceMode() == PriceMode.HIGH_ALCHEMY
            ? "HA total"
            : "GE total";
        String tooltip = ColorUtil.wrapWithColorTag(priceSource, TOOLTIP_LABEL_COLOUR)
            + "<br>"
            + ColorUtil.wrapWithColorTag(formatExactGp(totalValue), valueColour);
        tooltipManager.add(new Tooltip(tooltip));
    }

    private String buildHiddenItemsTooltip(List<LootDisplayItem> items, int itemCount)
    {
        int hiddenCount = items.size() - itemCount;
        StringBuilder tooltip = new StringBuilder(ColorUtil.wrapWithColorTag(
            "Hidden items (" + hiddenCount + ")",
            Color.WHITE
        ));
        NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
        int shown = 0;
        for (int i = itemCount; i < items.size() && shown < 8; i++, shown++)
        {
            LootDisplayItem item = items.get(i);
            int canonicalId = itemManager.canonicalize(item.getItemId());
            ItemComposition composition = itemManager.getItemComposition(canonicalId);
            String name = composition == null ? "Unknown item" : composition.getName();
            String quantity = item.getQuantity() > 1 ? " x" + numbers.format(item.getQuantity()) : "";
            tooltip.append("<br>")
                .append(ColorUtil.wrapWithColorTag(name + quantity, rarityColour(rarityFor(item))))
                .append(" ")
                .append(ColorUtil.wrapWithColorTag(formatTooltipPrice(item.getTotalValue(), numbers), TOOLTIP_LABEL_COLOUR));
        }
        if (hiddenCount > shown)
        {
            tooltip.append("<br>")
                .append(ColorUtil.wrapWithColorTag("+" + (hiddenCount - shown) + " more", TOOLTIP_LABEL_COLOUR));
        }
        return tooltip.toString();
    }

    private static String tooltipRow(String label, String value, Color valueColour)
    {
        return "<br>" + ColorUtil.wrapWithColorTag(label + ":", TOOLTIP_LABEL_COLOUR)
            + " " + ColorUtil.wrapWithColorTag(value, valueColour);
    }

    private static String formatTooltipPrice(long value, NumberFormat numbers)
    {
        return value > 0L ? numbers.format(value) + " gp" : "N/A";
    }

    private BufferedImage getItemImage(LootDisplayItem item)
    {
        try
        {
            return itemManager.getImage(item.getItemId(), 1, false);
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }

    private TierVisual tierFor(LootNotification notification)
    {
        return tierForValue(notification.getTotalValue());
    }

    private TierVisual tierForValue(long value)
    {
        if (!config.enableValueColours())
        {
            return new TierVisual(config.accentColour(), config.totalValueColour(), ValueTier.BASE);
        }

        if (value >= config.legendaryValuePrice())
        {
            return new TierVisual(config.legendaryValueColour(), config.legendaryValueColour(), ValueTier.LEGENDARY);
        }
        if (value >= config.insaneValuePrice())
        {
            return new TierVisual(config.insaneValueColour(), config.insaneValueColour(), ValueTier.INSANE);
        }
        if (value >= config.highValuePrice())
        {
            return new TierVisual(config.highValueColour(), config.highValueColour(), ValueTier.HIGH);
        }
        if (value >= config.mediumValuePrice())
        {
            return new TierVisual(config.mediumValueColour(), config.mediumValueColour(), ValueTier.MEDIUM);
        }
        if (value >= config.lowValuePrice())
        {
            return new TierVisual(config.lowValueColour(), config.lowValueColour(), ValueTier.LOW);
        }
        return new TierVisual(config.accentColour(), config.totalValueColour(), ValueTier.BASE);
    }

    private DropRarity rarityFor(LootDisplayItem item)
    {
        return plugin.rarityForRate(item.getDropRateOneIn());
    }

    private Color rarityColour(DropRarity rarity)
    {
        boolean colourblind = config.rarityPalette() == RarityPalette.COLOURBLIND;
        switch (rarity)
        {
            case UNCOMMON:
                return colourblind ? new Color(0, 158, 115) : new Color(105, 209, 125);
            case RARE:
                return colourblind ? new Color(0, 114, 178) : new Color(93, 173, 226);
            case VERY_RARE:
                return colourblind ? new Color(230, 159, 0) : new Color(199, 125, 255);
            case ULTRA_RARE:
                return colourblind ? new Color(213, 94, 0) : new Color(255, 77, 94);
            case COMMON:
                return new Color(184, 192, 204);
            case UNKNOWN:
            default:
                return TOOLTIP_LABEL_COLOUR;
        }
    }

    private DropRarity rarestRarity(List<LootDisplayItem> items)
    {
        DropRarity result = DropRarity.UNKNOWN;
        double rarest = 0.0;
        for (LootDisplayItem item : items)
        {
            if (item.getDropRateOneIn() > rarest)
            {
                rarest = item.getDropRateOneIn();
                result = rarityFor(item);
            }
        }
        return result;
    }

    private boolean isLucky(List<LootDisplayItem> items)
    {
        if (!config.enableDropRarity() || !config.showLuckyIndicator())
        {
            return false;
        }
        for (LootDisplayItem item : items)
        {
            if (item.getDropRateOneIn() >= config.luckyDropRate())
            {
                return true;
            }
        }
        return false;
    }

    private static void drawLuckyStar(Graphics2D g, int centerX, int centerY, Color colour)
    {
        Path2D star = new Path2D.Float();
        for (int i = 0; i < 10; i++)
        {
            double angle = -Math.PI / 2.0 + i * Math.PI / 5.0;
            double radius = i % 2 == 0 ? 5.0 : 2.2;
            double px = centerX + Math.cos(angle) * radius;
            double py = centerY + Math.sin(angle) * radius;
            if (i == 0)
            {
                star.moveTo(px, py);
            }
            else
            {
                star.lineTo(px, py);
            }
        }
        star.closePath();
        g.setColor(new Color(0, 0, 0, 210));
        g.setStroke(new BasicStroke(2.4f));
        g.draw(star);
        g.setColor(colour);
        g.fill(star);
    }

    private void drawCollectionLogBadge(Graphics2D g, int x, int y)
    {
        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 10f));
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(x, y, 27, 13, 5, 5);
        g.setColor(new Color(255, 205, 72, 220));
        g.drawRoundRect(x, y, 26, 12, 5, 5);
        drawText(g, "NEW", x + 4, y + 10, new Color(255, 224, 112));
        g.setFont(oldFont);
    }

    private void drawUltraRareItemGlow(
        Graphics2D g,
        int itemX,
        int itemY,
        Color colour,
        long now,
        int itemIndex
    )
    {
        double pulse = config.reduceMotion()
            ? 0.55
            : (Math.sin(now / 180.0 + itemIndex * 0.9) + 1.0) / 2.0;
        int centerX = itemX + ICON_WIDTH / 2;
        int centerY = itemY + ICON_HEIGHT / 2;
        int outerAlpha = 24 + (int) Math.round(pulse * 22.0);
        int middleAlpha = 38 + (int) Math.round(pulse * 28.0);
        int coreAlpha = 52 + (int) Math.round(pulse * 34.0);

        // Layered translucent light creates a soft bloom with no visible border.
        g.setColor(withAlpha(colour, outerAlpha));
        g.fillOval(centerX - 27, centerY - 24, 54, 48);
        g.setColor(withAlpha(colour, middleAlpha));
        g.fillOval(centerX - 22, centerY - 20, 44, 40);
        g.setColor(withAlpha(Color.WHITE, coreAlpha));
        g.fillOval(centerX - 14, centerY - 13, 28, 26);

        if (config.reduceMotion())
        {
            return;
        }

        // Slow rotating light shards stay behind the icon and make 1/5000+ drops feel exceptional.
        double rotation = now / 520.0 + itemIndex * 0.7;
        g.setStroke(new BasicStroke(1.2f));
        for (int ray = 0; ray < 6; ray++)
        {
            double angle = rotation + ray * Math.PI / 3.0;
            int innerX = centerX + (int) Math.round(Math.cos(angle) * 16.0);
            int innerY = centerY + (int) Math.round(Math.sin(angle) * 14.0);
            int outerX = centerX + (int) Math.round(Math.cos(angle) * (22.0 + pulse * 4.0));
            int outerY = centerY + (int) Math.round(Math.sin(angle) * (20.0 + pulse * 3.0));
            g.setColor(withAlpha(ray % 2 == 0 ? Color.WHITE : colour, 70 + (int) Math.round(pulse * 50.0)));
            g.drawLine(innerX, innerY, outerX, outerY);
        }
    }

    private Color quantityColourForValue(long value)
    {
        TierVisual tier = tierForValue(value);
        return tier.tier == ValueTier.BASE ? Color.WHITE : tier.valueColour;
    }

    private void drawText(Graphics2D g, String text, int x, int baseline, Color colour)
    {
        if (config.highContrastText())
        {
            g.setColor(new Color(0, 0, 0, 245));
            g.drawString(text, x - 1, baseline);
            g.drawString(text, x + 1, baseline);
            g.drawString(text, x, baseline - 1);
            g.drawString(text, x, baseline + 1);
        }
        g.setColor(new Color(0, 0, 0, config.highContrastText() ? 245 : 190));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(colour);
        g.drawString(text, x, baseline);
    }

    private void drawStackText(Graphics2D g, String text, int x, int baseline, Color colour)
    {
        g.setColor(new Color(0, 0, 0, 235));
        g.drawString(text, x - 1, baseline);
        g.drawString(text, x + 1, baseline);
        g.drawString(text, x, baseline - 1);
        g.drawString(text, x, baseline + 1);
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

    static String formatExactGp(long value)
    {
        return NumberFormat.getIntegerInstance(Locale.US).format(value) + " gp";
    }

    static String formatQuantityLabel(int quantity)
    {
        if (quantity >= 1_000_000_000)
        {
            return "x" + formatCompact(quantity, 1_000_000_000L, "B");
        }
        if (quantity >= 1_000_000)
        {
            return "x" + formatCompact(quantity, 1_000_000L, "M");
        }
        if (quantity >= 1_000)
        {
            return "x" + formatCompact(quantity, 1_000L, "K");
        }
        return "x" + quantity;
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
        private final ValueTier tier;

        private TierVisual(Color accentColour, Color valueColour, ValueTier tier)
        {
            this.accentColour = accentColour;
            this.valueColour = valueColour;
            this.tier = tier;
        }
    }

    private enum ValueTier
    {
        BASE,
        LOW,
        MEDIUM,
        HIGH,
        INSANE,
        LEGENDARY
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
