package com.lootpop;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootInsightsPluginTest
{
    private static LootPopPlugin plugin() throws Exception
    {
        LootPopPlugin plugin = new LootPopPlugin();
        Field config = LootPopPlugin.class.getDeclaredField("config"); config.setAccessible(true);
        config.set(plugin, new LootPopConfig() {}); return plugin;
    }

    @Test public void recordedLootFeedsRecapAndSessionResetClearsBothLiveViews() throws Exception
    {
        LootPopPlugin plugin = plugin();
        Method record = LootPopPlugin.class.getDeclaredMethod("recordLoot", LootNotification.class, long.class);
        record.setAccessible(true);
        record.invoke(plugin, new LootNotification("Boss", Collections.emptyList(), 100, 1000), 1000L);
        assertEquals(100, plugin.getSessionRecap(PriceMode.GRAND_EXCHANGE).total);
        assertEquals(1, plugin.getSessionRecap(PriceMode.GRAND_EXCHANGE).drops);
        plugin.clearSession();
        assertEquals(0, plugin.getSessionRecap(PriceMode.GRAND_EXCHANGE).drops);
        assertTrue(plugin.getLootHistory().isEmpty());
    }

    @Test public void collectionEventUpdatesRecapEvenIfEntryHasLeftSidebarHistory() throws Exception
    {
        LootPopPlugin plugin = plugin();
        Field tracker = LootPopPlugin.class.getDeclaredField("sessionRecap"); tracker.setAccessible(true);
        ((LootSessionRecap) tracker.get(plugin)).record(LootInsightsTest.entry("Boss", 1000, PriceMode.GRAND_EXCHANGE,
            LootInsightsTest.item(1, "Reward", 100, "1 / 512")));
        LootNotification drop = new LootNotification("Boss", Collections.singletonList(
            new LootDisplayItem(1, 1, 100, "1 / 512", 512)), 100, 1000);
        Method confirm = LootPopPlugin.class.getDeclaredMethod("confirmCollectionItem", LootNotification.class, int.class, long.class);
        confirm.setAccessible(true); confirm.invoke(plugin, drop, 1, 5000L);
        assertEquals(1, plugin.getSessionRecap(PriceMode.GRAND_EXCHANGE).logSlots());
        assertTrue(plugin.getLootHistory().isEmpty());
    }
}
