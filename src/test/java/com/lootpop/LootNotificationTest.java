package com.lootpop;

import java.util.Collections;
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
}
