package com.lootpop;

import com.google.gson.Gson;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemClient;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Exercises real event subscriptions, popup creation, totals, recap and persisted history. */
public class NpcLootPipelineTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void bonecrusherGroundThenServerLootIsRecordedOnlyOnce() throws Exception { bonecrusher(false); }
    @Test public void bonecrusherServerThenGroundLootIsRecordedOnlyOnce() throws Exception { bonecrusher(true); }

    private void bonecrusher(boolean serverFirst) throws Exception
    {
        try (Harness h = new Harness(new LootPopConfig() {}))
        {
            NPC npc = npc("Shellbane Gryphon", 999);
            NpcLootReceived ground = new NpcLootReceived(npc, Collections.singletonList(new ItemStack(995, 100)));
            ServerNpcLoot server = new ServerNpcLoot(composition("Shellbane Gryphon", 999),
                Arrays.asList(new ItemStack(995, 100), new ItemStack(536, 1)));
            h.events.post(serverFirst ? server : ground);
            h.events.post(serverFirst ? ground : server);
            assertEquals(1, h.plugin.getVisibleNotifications().size());
            assertEquals(2, h.plugin.getVisibleNotifications().get(0).getItems().size());
            assertEquals(1, h.plugin.getSessionSnapshot().getDrops());
            assertEquals(110, h.plugin.getSessionSnapshot().getTotalValue());
            assertEquals(1, h.plugin.getSessionRecap(PriceMode.HIGH_ALCHEMY).drops);
            assertEquals(1, h.plugin.getLootHistory().size());
            LootJournal saved = h.saved();
            assertEquals(1, saved.snapshot().size());
            assertEquals(110, saved.snapshot().get(0).getTotalValue());
            assertEquals(2, saved.snapshot().get(0).getItems().size());
        }
    }

    @Test public void separateIdenticalRapidKillsBothReachPopupSessionAndJournal() throws Exception
    {
        try (Harness h = new Harness(new LootPopConfig() {}))
        {
            NPCComposition npc = composition("Goblin", 100);
            h.events.post(new ServerNpcLoot(npc, Collections.singletonList(new ItemStack(995, 100))));
            h.events.post(new ServerNpcLoot(npc, Collections.singletonList(new ItemStack(995, 100))));
            assertEquals(2, h.plugin.getVisibleNotifications().size());
            assertEquals(2, h.plugin.getSessionSnapshot().getDrops());
            assertEquals(200, h.plugin.getSessionSnapshot().getTotalValue());
            assertEquals(2, h.plugin.getSessionRecap(PriceMode.HIGH_ALCHEMY).drops);
            assertEquals(2, h.saved().snapshot().size());
        }
    }

    @Test public void disablingSidebarDoesNotStopLootOrSavedHistory() throws Exception
    {
        try (Harness h = new Harness(new LootPopConfig() { @Override public boolean showSidePanel() { return false; } }))
        {
            h.events.post(new ServerNpcLoot(composition("Goblin", 100), Collections.singletonList(new ItemStack(995, 25))));
            assertEquals(1, h.plugin.getVisibleNotifications().size());
            assertEquals(25, h.plugin.getSessionSnapshot().getTotalValue());
            assertEquals(25, h.saved().snapshot().get(0).getTotalValue());
        }
    }

    @Test public void npcFilterStillDisablesAuthoritativeNpcRewards() throws Exception
    {
        try (Harness h = new Harness(new LootPopConfig() { @Override public boolean includeNpcLoot() { return false; } }))
        {
            h.events.post(new ServerNpcLoot(composition("Goblin", 100), Collections.singletonList(new ItemStack(995, 25))));
            assertTrue(h.plugin.getVisibleNotifications().isEmpty());
            assertEquals(0, h.plugin.getSessionSnapshot().getDrops());
            assertTrue(h.saved().snapshot().isEmpty());
        }
    }

    private final class Harness implements AutoCloseable
    {
        final LootPopPlugin plugin = new LootPopPlugin();
        final EventBus events = new EventBus();
        final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();
        final LootJournalStore store;
        final LootJournalService journal;

        Harness(LootPopConfig options) throws Exception
        {
            Gson gson = new Gson();
            LootPopConfig config = new LootPopConfig()
            {
                @Override public VisualMode visualMode() { return VisualMode.PERFORMANCE; }
                @Override public PriceMode priceMode() { return PriceMode.HIGH_ALCHEMY; }
                @Override public int minimumDropValue() { return 0; }
                @Override public boolean showSidePanel() { return options.showSidePanel(); }
                @Override public boolean includeNpcLoot() { return options.includeNpcLoot(); }
                @Override public boolean automaticScreenshots() { return false; }
                @Override public boolean rareDropSounds() { return false; }
            };
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> method.getName().equals("getItemDefinition")
                    ? item((int) args[0]) : defaultValue(method.getReturnType()));
            ClientThread thread = new ClientThread();
            inject(thread, "client", client);
            // Never execute ItemManager's background HTTP fetches in this offline fixture.
            ScheduledExecutorService noNetwork = (ScheduledExecutorService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ScheduledExecutorService.class}, (p, m, a) -> null);
            Constructor<ItemManager> constructor = ItemManager.class.getDeclaredConstructor(Client.class,
                ScheduledExecutorService.class, ClientThread.class, EventBus.class, ItemClient.class, RuneLiteConfig.class);
            constructor.setAccessible(true);
            ItemManager manager = constructor.newInstance(client, noNetwork, thread, new EventBus(), null, new RuneLiteConfig() {});
            inject(plugin, "config", config);
            inject(plugin, "client", client);
            inject(plugin, "clientThread", thread);
            inject(plugin, "itemManager", manager);
            inject(plugin, "dropRateService", new DropRateService(gson));
            store = JournalTestFiles.store(temporary.newFolder().toPath(), gson);
            journal = new LootJournalService(store, () -> {}, () -> 100, () -> 90, io);
            inject(plugin, "journal", journal);
            journal.switchProfile("test-character");
            events.register(plugin);
        }

        LootJournal saved() throws Exception
        {
            journal.close();
            Field tail = LootJournalService.class.getDeclaredField("tail"); tail.setAccessible(true);
            ((CompletableFuture<?>) tail.get(journal)).get(5, TimeUnit.SECONDS);
            return store.load("test-character");
        }

        @Override public void close() throws Exception
        {
            try { saved(); }
            finally { events.unregister(plugin); io.shutdownNow(); }
        }
    }

    private static ItemComposition item(int id)
    {
        return (ItemComposition) Proxy.newProxyInstance(ItemComposition.class.getClassLoader(), new Class<?>[]{ItemComposition.class},
            (p, m, a) ->
            {
                switch (m.getName())
                {
                    case "getId": return id;
                    case "getName": return id == 995 ? "Coins" : "Dragon bones";
                    case "getNote": case "getPlaceholderTemplateId": return -1;
                    case "getHaPrice": return id == 995 ? 1 : 10;
                    case "isTradeable": return true;
                    default: return defaultValue(m.getReturnType());
                }
            });
    }

    private static NPCComposition composition(String name, int id)
    {
        return (NPCComposition) Proxy.newProxyInstance(NPCComposition.class.getClassLoader(), new Class<?>[]{NPCComposition.class},
            (p, m, a) -> m.getName().equals("getName") ? name : m.getName().equals("getId") ? id : defaultValue(m.getReturnType()));
    }

    private static NPC npc(String name, int id)
    {
        return (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[]{NPC.class},
            (p, m, a) -> m.getName().equals("getName") ? name : m.getName().equals("getId") ? id : defaultValue(m.getReturnType()));
    }

    private static Object defaultValue(Class<?> type)
    {
        if (type == boolean.class) { return false; }
        if (type == int.class) { return 0; }
        if (type == long.class) { return 0L; }
        return null;
    }

    private static void inject(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
