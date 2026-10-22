package com.lootpop;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.ui.FontManager;

/** Paints only the reveal; layout and timing remain owned by the popup. */
final class SealedDropRenderer
{
    static final Color COLLECTION_GOLD = new Color(255, 218, 126);
    private final Map<BufferedImage, BufferedImage> silhouettes = new LinkedHashMap<BufferedImage, BufferedImage>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BufferedImage, BufferedImage> eldest)
        {
            return size() > 64;
        }
    };

    void draw(Graphics2D parent, LootDisplayItem item, BufferedImage image, String name,
        Color rarityColour, boolean collection, double progress, int sceneX, int sceneY,
        int slotX, int slotY, int labelWidth, boolean reducedMotion, boolean detailed)
    {
        if (progress < 0 || progress >= 1) { return; }
        Graphics2D g = (Graphics2D) parent.create();
        if (reducedMotion)
        {
            fade(g, (float) SealedDropSequence.clamp(progress));
            if (image != null) { LootArtwork.drawPopup(g, image, slotX, slotY); }
            g.dispose();
            return;
        }

        Color colour = collection ? COLLECTION_GOLD : rarityColour;
        double travel = SealedDropSequence.ease((progress - 0.74) / 0.26);
        double cx = sceneX + (slotX + 18 - sceneX) * travel;
        double cy = sceneY + (slotY + 16 - sceneY) * travel;
        double startScale = detailed ? 1.60 : 1.10;
        double endScale = LootArtwork.POPUP_WIDTH / 36.0;
        double scale = startScale + travel * (endScale - startScale);
        double reveal = SealedDropSequence.ease((progress - 0.26) / 0.22);
        double burst = SealedDropSequence.clamp((progress - 0.42) / 0.32);

        // A single soft light source keeps the seal legible against the game world.
        float glowAlpha = (float) ((0.32 + 0.13 * Math.sin(progress * 9)) * (1 - travel));
        glow(g, (float) cx, (float) cy, (float) ((detailed ? 39 : 25) + burst * 7), colour, glowAlpha);
        if (progress < 0.53)
        {
            Graphics2D seal = (Graphics2D) g.create();
            fade(seal, (float) (1 - SealedDropSequence.clamp((progress - 0.36) / 0.17)));
            int shake = progress > 0.15 ? (int) Math.round(Math.sin(progress * 95) * 1.4) : 0;
            seal.translate(cx + shake, cy);
            if (!detailed) { seal.scale(0.72, 0.72); }
            drawSeal(seal, 0, 0, colour, progress);
            seal.dispose();
        }

        int w = image == null ? 36 : image.getWidth();
        int h = image == null ? 32 : image.getHeight();
        int drawW = (int) Math.round(w * scale);
        int drawH = (int) Math.round(h * scale);
        int ix = (int) Math.round(cx - drawW / 2.0);
        int iy = (int) Math.round(cy - drawH / 2.0);
        if (image != null)
        {
            BufferedImage silhouette = silhouettes.get(image);
            if (silhouette == null && hasVisiblePixel(image))
            {
                silhouette = silhouette(image);
                silhouettes.put(image, silhouette);
            }
            if (silhouette != null) { g.drawImage(silhouette, ix, iy, drawW, drawH, null); }
            if (reveal > 0)
            {
                Graphics2D sweep = (Graphics2D) g.create();
                sweep.clip(new Rectangle(ix, iy, (int) Math.ceil(drawW * reveal), drawH));
                LootArtwork.draw(sweep, image, ix, iy, drawW, drawH);
                sweep.dispose();
                if (reveal < 1)
                {
                    g.setColor(new Color(255, 246, 222, 220));
                    int beamX = ix + (int) (drawW * reveal);
                    g.drawLine(beamX, iy - 3, beamX, iy + drawH + 3);
                }
            }
        }
        if (progress < 0.32)
        {
            Graphics2D question = (Graphics2D) g.create();
            fade(question, (float) (1 - SealedDropSequence.clamp((progress - 0.24) / 0.08)));
            question.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, detailed ? 27f : 21f));
            centerText(question, "?", (int) cx, (int) cy + 9, Color.WHITE);
            question.dispose();
        }
        if (burst > 0 && burst < 1)
        {
            Graphics2D sparks = (Graphics2D) g.create();
            fade(sparks, (float) ((1 - burst) * (1 - travel)));
            for (int i = 0; i < 10; i++)
            {
                double angle = i * Math.PI / 5;
                double radius = 23 + burst * 30;
                int sx = (int) Math.round(cx + Math.cos(angle) * radius);
                int sy = (int) Math.round(cy + Math.sin(angle) * radius * 0.65);
                sparks.setColor(i % 3 == 0 ? Color.WHITE : colour);
                sparks.fillRect(sx, sy, 2, 2);
            }
            sparks.dispose();
        }
        if (detailed && reveal >= 0.99)
        {
            Graphics2D caption = (Graphics2D) g.create();
            fade(caption, (float) (1 - travel));
            caption.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 12f));
            centerText(caption, fit(caption, name, labelWidth), sceneX, sceneY + 30, Color.WHITE);
            caption.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
            centerText(caption, collection ? "NEW COLLECTION LOG SLOT" : item.getDropRate() == null
                ? "RARE REWARD" : item.getDropRate(), sceneX, sceneY + 44, colour);
            caption.dispose();
        }
        g.dispose();
    }

    static void drawBook(Graphics2D g, int x, int y, Color colour)
    {
        Graphics2D book = (Graphics2D) g.create();
        book.setStroke(new BasicStroke(1f));
        // A small bound journal with a ribbon, not a grid laid over the item.
        book.setColor(new Color(9, 10, 13, 240));
        book.fillRoundRect(x - 1, y - 2, 12, 15, 3, 3);
        book.setColor(new Color(96, 65, 30));
        book.fillRoundRect(x, y - 1, 10, 12, 2, 2);
        book.setColor(colour);
        book.drawRoundRect(x, y - 1, 9, 12, 2, 2);
        book.drawLine(x + 2, y, x + 2, y + 10);
        book.setColor(new Color(255, 238, 184));
        book.drawLine(x + 4, y + 9, x + 8, y + 9);
        book.setColor(new Color(227, 91, 73));
        book.fillRect(x + 6, y, 2, 6);
        book.dispose();
    }

    private static void drawSeal(Graphics2D g, int x, int y, Color colour, double progress)
    {
        Path2D seal = new Path2D.Double();
        seal.moveTo(x, y - 29);
        seal.lineTo(x + 29, y);
        seal.lineTo(x, y + 29);
        seal.lineTo(x - 29, y);
        seal.closePath();
        g.setColor(new Color(5, 8, 13, 240));
        g.fill(seal);
        g.setStroke(new BasicStroke(1.3f));
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 190));
        g.draw(seal);
        g.setColor(new Color(255, 255, 255, 35));
        g.drawLine(x - 21, y - 1, x - 1, y - 21);
        g.setColor(colour);
        for (int i = 0; i < 4; i++)
        {
            double angle = i * Math.PI / 2;
            int px = x + (int) (Math.cos(angle) * 32);
            int py = y + (int) (Math.sin(angle) * 32);
            g.fillRect(px - 1, py - 1, 3, 3);
        }
    }

    private static void glow(Graphics2D g, float x, float y, float radius, Color colour, float alpha)
    {
        Graphics2D light = (Graphics2D) g.create();
        fade(light, Math.max(0, Math.min(1, alpha)));
        light.setPaint(new RadialGradientPaint(x, y, radius, new float[]{0, 0.45f, 1},
            new Color[]{colour, new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 90),
                new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0)}));
        light.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        light.dispose();
    }

    static void fade(Graphics2D g, float alpha)
    {
        float parent = g.getComposite() instanceof AlphaComposite ? ((AlphaComposite) g.getComposite()).getAlpha() : 1f;
        g.setComposite(AlphaComposite.SrcOver.derive(parent * Math.max(0, Math.min(1, alpha))));
    }

    private static BufferedImage silhouette(BufferedImage image)
    {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                result.setRGB(x, y, (image.getRGB(x, y) & 0xff000000) | 0x030407);
            }
        }
        return result;
    }

    private static boolean hasVisiblePixel(BufferedImage image)
    {
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if ((image.getRGB(x, y) >>> 24) != 0) { return true; }
            }
        }
        return false;
    }

    private static void centerText(Graphics2D g, String text, int centerX, int baseline, Color colour)
    {
        int x = centerX - g.getFontMetrics().stringWidth(text) / 2;
        g.setColor(new Color(0, 0, 0, 235));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(colour);
        g.drawString(text, x, baseline);
    }

    private static String fit(Graphics2D g, String text, int width)
    {
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= width) { return text; }
        while (text.length() > 1 && metrics.stringWidth(text + "...") > width)
        {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}
