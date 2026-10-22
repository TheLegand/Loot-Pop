package com.lootpop;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import org.junit.Test;
import static org.junit.Assert.*;

public class SealedDropPluginTest
{
    private static LootPopPlugin plugin(LootPopConfig config) throws Exception
    {
        LootPopPlugin p = new LootPopPlugin();
        Field field = LootPopPlugin.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(p, config);
        return p;
    }

    @Test
    public void fullReadingTimeIsAddedAfterRevealEvenWithoutAdaptiveTime() throws Exception
    {
        LootPopConfig config = new LootPopConfig()
        {
            @Override public boolean adaptiveDisplayTime() { return false; }
            @Override public int displayDuration() { return 4000; }
        };
        LootNotification n = new LootNotification("Boss", Collections.singletonList(
            new LootDisplayItem(1, 1, 100, "1 / 5000", 5000)), 100, 0);
        SealedDropSequence s = new SealedDropSequence(n.getItems(), Collections.emptySet(), 1000, true, false, 100, 10);
        n.setSealedDrop(s);
        assertEquals(4000 + s.getDurationMillis(), plugin(config).displayDurationFor(n));
        assertFalse(n.isExpired(s.getDurationMillis() + 3900, plugin(config).displayDurationFor(n), 500));
    }

    @Test
    public void performanceAndDisabledRevealDoNotPrepareASequence() throws Exception
    {
        for (LootPopConfig config : Arrays.asList(new LootPopConfig()
        {
            @Override public VisualMode visualMode() { return VisualMode.PERFORMANCE; }
        }, new LootPopConfig() { @Override public boolean sealedDrops() { return false; } }))
        {
            LootPopPlugin p = plugin(config);
            LootNotification n = new LootNotification("Boss", Collections.singletonList(
                new LootDisplayItem(1, 1, 100, "1 / 5000", 5000)), 100, 0);
            java.lang.reflect.Method prepare = LootPopPlugin.class.getDeclaredMethod("prepareSealedDrop", LootNotification.class);
            prepare.setAccessible(true);
            prepare.invoke(p, n);
            assertNull(n.getSealedDrop());
            assertEquals(0, n.getRevealDelayMillis());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void queueRestartsRevealClockRatherThanExpiringWhileWaiting() throws Exception
    {
        LootPopConfig config = new LootPopConfig()
        {
            @Override public boolean rareDropSounds() { return false; }
        };
        LootPopPlugin p = plugin(config);
        Field queue = LootPopPlugin.class.getDeclaredField("queuedNotifications");
        queue.setAccessible(true);
        LootNotification n = new LootNotification("Queued boss", Collections.singletonList(
            new LootDisplayItem(1, 1, 100, "1 / 5000", 5000)), 100, 0);
        n.setSealedDrop(new SealedDropSequence(n.getItems(), Collections.emptySet(), 1000, true, false, 100, 10));
        ((Deque<LootNotification>) queue.get(p)).add(n);
        assertTrue(p.getVisibleNotifications().contains(n));
        assertTrue(n.getAgeMillis(System.currentTimeMillis()) < 1000);
        assertEquals(0f, n.getSealedDrop().normalAlpha(n.getAgeMillis(System.currentTimeMillis())), 0.001f);
    }
}
