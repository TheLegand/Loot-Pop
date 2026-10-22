package com.lootpop;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A quiet, readable Collection Log tag shared by the overlay and sidebar. */
final class LootLogBadge
{
    static final int WIDTH = 27, HEIGHT = 14;

    static void draw(Graphics2D graphics, int x, int y)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(35, 29, 17, 245));
        g.fillRoundRect(x, y, WIDTH, HEIGHT, 4, 4);
        g.setColor(new Color(242, 197, 93));
        g.fillRoundRect(x, y + 2, 2, HEIGHT - 4, 2, 2);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        String label = "NEW";
        int textX = x + 3 + (WIDTH - 3 - g.getFontMetrics().stringWidth(label)) / 2;
        int baseline = y + (HEIGHT - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
        g.setColor(new Color(255, 225, 156));
        g.drawString(label, textX, baseline);
        g.dispose();
    }

    private LootLogBadge() {}
}
