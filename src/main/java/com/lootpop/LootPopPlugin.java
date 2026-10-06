package com.lootpop;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.http.api.loottracker.LootRecordType;

@PluginDescriptor(
    name = "Loot Pop",
    description = "Shows polished, value-coloured loot popups with legendary effects",
    tags = {"loot", "drop", "popup", "hud", "overlay", "price", "rare"}
)
public class LootPopPlugin extends Plugin
{
    private static final long DUPLICATE_WINDOW_MILLIS = 500L;

    @Inject
    private LootPopConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private LootPopOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    private final Deque<LootNotification> notifications = new ArrayDeque<>();
    private String lastEventHash = "";
    private long lastEventAtMillis;

    @Provides
    LootPopConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(LootPopConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
    }

    @Override
    protected synchronized void shutDown()
    {
        overlayManager.remove(overlay);
        notifications.clear();
        lastEventHash = "";
        lastEventAtMillis = 0L;
    }

    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        if (!isTypeEnabled(event.getType()) || WildcardMatcher.matchesAny(config.ignoredSources(), event.getName()))
        {
            return;
        }

        List<LootDisplayItem> items = new ArrayList<>();
        long totalValue = 0L;
        for (ItemStack itemStack : event.getItems())
        {
            if (itemStack.getQuantity() <= 0)
            {
                continue;
            }

            ItemComposition itemComposition = itemManager.getItemComposition(itemStack.getId());
            if (WildcardMatcher.matchesAny(config.ignoredItems(), itemComposition.getName()))
            {
                continue;
            }

            long unitPrice = getUnitPrice(itemStack.getId());
            if (unitPrice <= 0L && !config.showZeroValueItems())
            {
                continue;
            }

            LootDisplayItem item = new LootDisplayItem(itemStack.getId(), itemStack.getQuantity(), unitPrice);
            items.add(item);
            totalValue += item.getTotalValue();
        }

        if (items.isEmpty() || totalValue < config.minimumDropValue())
        {
            return;
        }

        if (config.sortByValue())
        {
            items.sort(Comparator.comparingLong(LootDisplayItem::getTotalValue).reversed());
        }

        String sourceName = event.getName() == null || event.getName().trim().isEmpty()
            ? "Unknown"
            : event.getName();
        String eventHash = createEventHash(sourceName, event.getType(), items);
        long now = System.currentTimeMillis();

        synchronized (this)
        {
            if (eventHash.equals(lastEventHash) && now - lastEventAtMillis < DUPLICATE_WINDOW_MILLIS)
            {
                return;
            }

            lastEventHash = eventHash;
            lastEventAtMillis = now;
            notifications.addFirst(new LootNotification(sourceName, items, totalValue, now));
            trimNotifications();
        }
    }

    synchronized List<LootNotification> getVisibleNotifications()
    {
        long now = System.currentTimeMillis();
        notifications.removeIf(notification -> notification.isExpired(
            now,
            config.displayDuration(),
            config.fadeDuration()
        ));
        trimNotifications();

        List<LootNotification> result = new ArrayList<>(notifications);
        if (!config.newestOnTop())
        {
            Collections.reverse(result);
        }
        return result;
    }

    private void trimNotifications()
    {
        while (notifications.size() > config.maxPopups())
        {
            notifications.removeLast();
        }
    }

    private long getUnitPrice(int itemId)
    {
        int canonicalId = itemManager.canonicalize(itemId);
        if (config.priceMode() == PriceMode.HIGH_ALCHEMY)
        {
            return Math.max(0, itemManager.getItemComposition(canonicalId).getHaPrice());
        }
        return Math.max(0, itemManager.getItemPrice(canonicalId));
    }

    private boolean isTypeEnabled(LootRecordType type)
    {
        if (type == null)
        {
            return true;
        }

        switch (type)
        {
            case NPC:
                return config.includeNpcLoot();
            case EVENT:
                return config.includeEventLoot();
            case PLAYER:
                return config.includePlayerLoot();
            case PICKPOCKET:
                return config.includePickpocketLoot();
            default:
                return true;
        }
    }

    private static String createEventHash(
        String sourceName,
        LootRecordType type,
        List<LootDisplayItem> items
    )
    {
        StringBuilder hash = new StringBuilder(sourceName).append('|').append(type).append('|');
        for (LootDisplayItem item : items)
        {
            hash.append(item.getItemId()).append(':').append(item.getQuantity()).append(';');
        }
        return hash.toString();
    }
}
