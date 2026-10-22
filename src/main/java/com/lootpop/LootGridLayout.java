package com.lootpop;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** One set of slots for settled items, reveal destinations, quantities, overflow and hit testing. */
final class LootGridLayout
{
    static List<Rectangle> slots(int[] widths, int x, int y, int availableWidth, int columns)
    {
        List<Rectangle> result = new ArrayList<>();
        for (int start = 0; start < widths.length; start += columns)
        {
            int end = Math.min(widths.length, start + columns);
            int occupied = 0;
            for (int i = start; i < end; i++) { occupied += widths[i]; }
            int gaps = end - start - 1;
            int gap = gaps == 0 ? 0 : Math.max(1, Math.min(8, (availableWidth - occupied) / gaps));
            int rowWidth = occupied + gap * gaps;
            int cursor = x + (availableWidth - rowWidth) / 2;
            for (int i = start; i < end; i++)
            {
                result.add(new Rectangle(cursor, y + (start / columns) * 34, widths[i], 32));
                cursor += widths[i] + gap;
            }
        }
        return result;
    }
}
