package com.lootpop;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LootNotificationTest
{
    @Test
    public void fadesOnlyAfterDisplayDuration()
    {
        LootNotification notification = new LootNotification(
            "Goblin",
            Collections.emptyList(),
            319,
            1_000
        );

        assertEquals(1.0f, notification.getAlpha(4_500, 3_500, 500), 0.001f);
        assertEquals(0.5f, notification.getAlpha(4_750, 3_500, 500), 0.001f);
        assertEquals(0.0f, notification.getAlpha(5_000, 3_500, 500), 0.001f);
        assertFalse(notification.isExpired(5_000, 3_500, 500));
        assertTrue(notification.isExpired(5_001, 3_500, 500));
    }

    @Test
    public void mergesRepeatedItemsIntoOneStack()
    {
        List<LootDisplayItem> merged = LootDisplayItem.mergeDuplicates(Arrays.asList(
            new LootDisplayItem(526, 1, 80),
            new LootDisplayItem(2446, 1, 120),
            new LootDisplayItem(526, 2, 80)
        ));

        assertEquals(2, merged.size());
        assertEquals(526, merged.get(0).getItemId());
        assertEquals(3, merged.get(0).getQuantity());
        assertEquals(240L, merged.get(0).getTotalValue());
        assertEquals(2446, merged.get(1).getItemId());
    }
}
