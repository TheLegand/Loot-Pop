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
    private static final int TWO_ROW_MIN_WIDTH = 205;
    private static final int CLASSIC_HEIGHT = 66;
    private static final int SURVIVAL_MIN_WIDTH = 218;
    private static final int SURVIVAL_HEIGHT = 70;
    private static final int LOOT_STRIP_MIN_WIDTH = 252;
    private static final int LOOT_STRIP_HEIGHT = 50;
    private static final int VALUE_TINT_STRENGTH = 38;

    private final LootPopPlugin plugin;
    private final LootPopConfig config;
    private final ItemManager itemManager;
    private final Client client;
    private final TooltipManager tooltipManager;
    private double hoverX = -1.0;
    private double hoverY = -1.0;

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

        updateHoverPosition(scale, offsetX, offsetY);

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
                drawLootStrip(g, notification, 0, y, entrySize.width, entrySize.height, ultraFont, now);
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
        int itemsWidth = measureItemsWidth(g, notification.getItems(), itemCount);
        String value = formatGp(notification.getTotalValue());

        if (config.popupStyle() == PopupStyle.ULTRA_COMPACT)
        {
            g.setFont(ultraFont);
            FontMetrics metrics = g.getFontMetrics();
            int nameWidth = Math.min(92, metrics.stringWidth(notification.getSourceName()));
            int width = 15 + nameWidth + 14 + itemsWidth
                + 12 + metrics.stringWidth(value) + 13;
            return new EntrySize(Math.max(LOOT_STRIP_MIN_WIDTH, width), LOOT_STRIP_HEIGHT);
        }

        g.setFont(headerFont);
        FontMetrics metrics = g.getFontMetrics();
        int headerWidth = Math.min(145, metrics.stringWidth(notification.getSourceName()))
            + metrics.stringWidth(value)
            + 30;
        boolean survival = config.popupStyle() == PopupStyle.MINIMAL_EDGE;
        int minimumWidth = survival ? SURVIVAL_MIN_WIDTH : TWO_ROW_MIN_WIDTH;
        int contentPadding = survival ? 24 : 14;
        int width = Math.max(minimumWidth, Math.max(itemsWidth + contentPadding, headerWidth));
        return new EntrySize(width, survival ? SURVIVAL_HEIGHT : CLASSIC_HEIGHT);
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
        int maxNameWidth = Math.max(20, valueX - (x + leftPadding) - 8);
        String name = ellipsize(notification.getSourceName(), metrics, maxNameWidth);
        int headerBaseline = y + (survival ? 20 : 18);
        drawText(g, name, x + leftPadding, headerBaseline, config.npcNameColour());
        drawText(g, value, valueX, headerBaseline, tier.valueColour);

        if (config.showSeparator())
        {
            g.setColor(withAlpha(tier.accentColour, 92));
            g.setStroke(new BasicStroke(1.0f));
            int separatorY = y + (survival ? 27 : 24);
            g.drawLine(x + leftPadding, separatorY, x + width - rightPadding, separatorY);
        }

        int itemCount = Math.min(config.maxItems(), notification.getItems().size());
        drawItems(
            g,
            notification.getItems(),
            itemCount,
            x + leftPadding,
            y + (survival ? 33 : 29),
            tier.accentColour
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
        int nameX = x + 15;
        String name = ellipsize(notification.getSourceName(), metrics, 92);
        drawText(g, name, nameX, y + 31, config.npcNameColour());

        int itemCount = Math.min(config.maxItems(), notification.getItems().size());
        int itemsX = nameX + metrics.stringWidth(name) + 14;
        int itemsWidth = measureItemsWidth(g, notification.getItems(), itemCount);
        drawItems(g, notification.getItems(), itemCount, itemsX, y + 9, tier.accentColour);

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
        if (enhanced && tier.tier == ValueTier.LEGENDARY && config.legendaryEffects())
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

    private void drawItems(
        Graphics2D g,
        List<LootDisplayItem> items,
        int itemCount,
        int x,
        int y,
        Color accentColour
    )
    {
        Font oldFont = g.getFont();
        Font quantityFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 14f);
        int cursorX = x;
        for (int i = 0; i < itemCount; i++)
        {
            LootDisplayItem item = items.get(i);
            BufferedImage image = getItemImage(item);
            int slotWidth = itemSlotWidth(g, item, quantityFont);
            int itemX = cursorX + (slotWidth - ICON_WIDTH) / 2;
            if (image != null)
            {
                if (isEnhancedMode())
                {
                    TierVisual itemTier = tierForValue(item.getTotalValue());
                    if (itemTier.tier != ValueTier.BASE)
                    {
                        g.setColor(withAlpha(itemTier.accentColour, 24));
                        g.fillOval(itemX + 2, y - 1, 32, 34);
                        g.setColor(withAlpha(itemTier.accentColour, 42));
                        g.fillOval(itemX + 7, y + 4, 22, 24);
                    }
                }
                g.drawImage(image, itemX, y, null);

                if (item.getQuantity() > 1)
                {
                    g.setFont(quantityFont);
                    drawStackText(
                        g,
                        formatQuantityLabel(item.getQuantity()),
                        cursorX + 1,
                        y + 13,
                        quantityColourForValue(item.getTotalValue())
                    );
                }
            }

            if (config.showItemTooltips()
                && hoverX >= itemX
                && hoverX < itemX + ICON_WIDTH
                && hoverY >= y
                && hoverY < y + ICON_HEIGHT)
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
                hiddenCount,
                cursorX,
                y,
                accentColour
            );
        }
        g.setFont(oldFont);
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

    private static int measureItemsWidth(Graphics2D g, List<LootDisplayItem> items, int itemCount)
    {
        Font quantityFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 14f);
        int width = 0;
        for (int i = 0; i < itemCount; i++)
        {
            if (width > 0)
            {
                width += ICON_GAP;
            }
            width += itemSlotWidth(g, items.get(i), quantityFont);
        }

        if (items.size() > itemCount)
        {
            if (width > 0)
            {
                width += ICON_GAP;
            }
            width += ICON_WIDTH;
        }
        return width;
    }

    private static void drawOverflowIndicator(
        Graphics2D g,
        int hiddenCount,
        int x,
        int y,
        Color accentColour
    )
    {
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
        g.setFont(oldFont);
    }

    private boolean isEnhancedMode()
    {
        return config.visualMode() == VisualMode.ENHANCED;
    }

    private void updateHoverPosition(double scale, int offsetX, int offsetY)
    {
        hoverX = -1.0;
        hoverY = -1.0;
        if (!config.showItemTooltips() || scale <= 0.0)
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
        ItemComposition composition = itemManager.getItemComposition(item.getItemId());
        String itemName = composition == null ? "Unknown item" : composition.getName();
        Color tierColour = quantityColourForValue(item.getTotalValue());
        NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);

        return ColorUtil.wrapWithColorTag(itemName, tierColour)
            + "<br>Quantity: " + ColorUtil.wrapWithColorTag(numbers.format(item.getQuantity()), Color.WHITE)
            + "<br>Unit price: " + ColorUtil.wrapWithColorTag(numbers.format(item.getUnitPrice()) + " gp", Color.WHITE)
            + "<br>Stack value: " + ColorUtil.wrapWithColorTag(numbers.format(item.getTotalValue()) + " gp", tierColour);
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

    private Color quantityColourForValue(long value)
    {
        TierVisual tier = tierForValue(value);
        return tier.tier == ValueTier.BASE ? Color.WHITE : tier.valueColour;
    }

    private static void drawText(Graphics2D g, String text, int x, int baseline, Color colour)
    {
        g.setColor(new Color(0, 0, 0, 190));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(colour);
        g.drawString(text, x, baseline);
    }

    private static void drawStackText(Graphics2D g, String text, int x, int baseline, Color colour)
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
