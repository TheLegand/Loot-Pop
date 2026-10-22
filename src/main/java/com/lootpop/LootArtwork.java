package com.lootpop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.LinkedHashMap;

/** Preserve original sprite colours, with a cached one-pixel dark contour. */
final class LootArtwork
{
    static final int POPUP_WIDTH = 32;
    static final int POPUP_HEIGHT = 28;
    static final int OVERFLOW_WIDTH = 36;
    static final int OVERFLOW_HEIGHT = 32;

    static java.awt.Rectangle popupBounds(int slotX, int slotY)
    { return new java.awt.Rectangle(slotX + 2, slotY + 2, POPUP_WIDTH, POPUP_HEIGHT); }

    static void drawPopup(Graphics2D graphics, BufferedImage image, int slotX, int slotY)
    {
        java.awt.Rectangle bounds = popupBounds(slotX, slotY);
        draw(graphics, image, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /** The original compact +N badge; hidden sprites belong only in the hover. */
    static void drawOverflowBadge(Graphics2D graphics, int hiddenCount, int x, int y, Color accent)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        int badgeX = x + 4, badgeY = y + 3, size = 27;
        g.setColor(new Color(0, 0, 0, 145));
        g.fillRoundRect(badgeX, badgeY, size, size, 9, 9);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
        g.drawRoundRect(badgeX, badgeY, size - 1, size - 1, 9, 9);
        String label = "+" + hiddenCount;
        java.awt.FontMetrics metrics = g.getFontMetrics();
        int textX = badgeX + (size - metrics.stringWidth(label)) / 2;
        int textY = badgeY + (size + metrics.getAscent() - metrics.getDescent()) / 2;
        g.setColor(Color.BLACK); g.drawString(label, textX + 1, textY + 1);
        g.setColor(Color.WHITE); g.drawString(label, textX, textY);
        g.dispose();
    }

    private static final Map<BufferedImage, BufferedImage> OUTLINES = new LinkedHashMap<BufferedImage, BufferedImage>(32, .75f, true)
    {
        @Override protected boolean removeEldestEntry(Map.Entry<BufferedImage, BufferedImage> eldest)
        { return size() > 128; }
    };

    static void draw(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height)
    {
        if (image == null) { return; }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        BufferedImage outline = outline(image);
        if (outline != null) { g.drawImage(outline, x - 1, y - 1, width + 2, height + 2, null); }
        g.drawImage(image, x, y, width, height, null);
        g.dispose();
    }

    private static synchronized BufferedImage outline(BufferedImage image)
    {
        BufferedImage cached = OUTLINES.get(image);
        if (cached != null) { return cached; }
        BufferedImage result = new BufferedImage(image.getWidth() + 2, image.getHeight() + 2, BufferedImage.TYPE_INT_ARGB);
        boolean visible = false;
        int ink = new Color(5, 7, 10, 210).getRGB();
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if ((image.getRGB(x, y) >>> 24) > 64)
                {
                    visible = true;
                    for (int dy = 0; dy < 3; dy++)
                    { for (int dx = 0; dx < 3; dx++) { result.setRGB(x + dx, y + dy, ink); } }
                }
            }
        }
        // An AsyncBufferedImage may still be blank. Do not cache its loading state.
        if (visible) { OUTLINES.put(image, result); }
        return visible ? result : null;
    }

    private LootArtwork() {}
}
