package com.lootpop;

import java.awt.Rectangle;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootGridLayoutTest
{
    @Test public void shortRowsAndOverflowAreCenteredIndependently()
    {
        List<Rectangle> slots = LootGridLayout.slots(new int[]{36,36,36,36,36,36,36,36,36}, 10, 29, 330, 5);
        assertCentered(slots, 0, 5, 10, 330);
        assertCentered(slots, 5, 9, 10, 330);
        assertEquals(63, slots.get(8).y);
    }

    @Test public void twoItemsAreNotLeftAlignedInAWidePopup()
    {
        List<Rectangle> slots = LootGridLayout.slots(new int[]{36,36}, 7, 29, 346, 5);
        assertCentered(slots, 0, 2, 7, 346);
        assertEquals(8, slots.get(1).x - slots.get(0).x - slots.get(0).width);
    }

    @Test public void largeQuantitySlotsAndFullRowsStayInsideTheContentBounds()
    {
        List<Rectangle> slots = LootGridLayout.slots(new int[]{60,36,36,36,36,36}, 10, 29, 210, 5);
        assertCentered(slots, 0, 5, 10, 210);
        assertCentered(slots, 5, 6, 10, 210);
        for (Rectangle slot : slots) { assertTrue(slot.x >= 10); assertTrue(slot.x + slot.width <= 220); }
    }

    private static void assertCentered(List<Rectangle> slots, int from, int to, int x, int width)
    {
        int left = slots.get(from).x;
        Rectangle last = slots.get(to - 1);
        int right = last.x + last.width;
        assertTrue(Math.abs((left - x) - (x + width - right)) <= 1);
    }
}
