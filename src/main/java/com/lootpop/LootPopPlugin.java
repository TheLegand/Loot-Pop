package com.lootpop;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.SoundEffectID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.http.api.loottracker.LootRecordType;

@PluginDescriptor(
    name = "Loot Pop",
    description = "Shows polished glass loot popups with value-tier effects",
    tags = {"loot", "drop", "popup", "hud", "overlay", "price", "rare"}
)
public class LootPopPlugin extends Plugin
{
    private static final long DUPLICATE_WINDOW_MILLIS = 500L;
    private static final Color LEGACY_LEGENDARY_COLOUR = new Color(255, 211, 78);
    private static final Color CRIMSON_LEGENDARY_COLOUR = new Color(255, 48, 61);

    @Inject
    private LootPopConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private LootPopOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

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
        migrateLegacyLegendaryColour();
        overlayManager.add(overlay);
    }

    private void migrateLegacyLegendaryColour()
    {
        Color savedColour = configManager.getConfiguration(
            LootPopConfig.GROUP,
            "legendaryValueColour",
            Color.class
        );
        if (LEGACY_LEGENDARY_COLOUR.equals(savedColour))
        {
            configManager.setConfiguration(
                LootPopConfig.GROUP,
                "legendaryValueColour",
                CRIMSON_LEGENDARY_COLOUR
            );
        }
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
        }

        items = LootDisplayItem.mergeDuplicates(items);
        long totalValue = 0L;
        for (LootDisplayItem item : items)
        {
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

        playDropSound(totalValue);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!LootPopConfig.GROUP.equals(event.getGroup())
            || !"showTestPopup".equals(event.getKey())
            || !config.showTestPopup())
        {
            return;
        }

        showTestPopup(config.testTier());
        configManager.setConfiguration(LootPopConfig.GROUP, "showTestPopup", false);
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

    private void showTestPopup(TestTier tier)
    {
        long targetValue = testValueFor(tier);
        long bonesUnitValue = Math.max(1L, targetValue / 100L);
        LootDisplayItem bones = new LootDisplayItem(ItemID.DRAGON_BONES, 21, bonesUnitValue);
        long plateValue = Math.max(1L, targetValue / 4L);
        LootDisplayItem platebody = new LootDisplayItem(ItemID.RUNE_PLATEBODY, 1, plateValue);
        long remainingValue = Math.max(1L, targetValue - bones.getTotalValue() - platebody.getTotalValue());
        LootDisplayItem rareItem = new LootDisplayItem(ItemID.TWISTED_BOW, 1, remainingValue);
        List<LootDisplayItem> items = new ArrayList<>();
        items.add(rareItem);
        items.add(platebody);
        items.add(bones);

        long totalValue = 0L;
        for (LootDisplayItem item : items)
        {
            totalValue += item.getTotalValue();
        }

        synchronized (this)
        {
            notifications.addFirst(new LootNotification(
                tier + " test",
                items,
                totalValue,
                System.currentTimeMillis()
            ));
            trimNotifications();
        }
        playDropSound(totalValue);
    }

    private long testValueFor(TestTier tier)
    {
        switch (tier)
        {
            case LOW:
                return Math.max(1L, config.lowValuePrice());
            case MEDIUM:
                return Math.max(1L, config.mediumValuePrice());
            case HIGH:
                return Math.max(1L, config.highValuePrice());
            case INSANE:
                return Math.max(1L, config.insaneValuePrice());
            case LEGENDARY:
            default:
                return Math.max(1L, config.legendaryValuePrice());
        }
    }

    private void playDropSound(long totalValue)
    {
        if (config.visualMode() != VisualMode.ENHANCED
            || !config.rareDropSounds()
            || config.dropSoundVolume() <= 0)
        {
            return;
        }

        int primarySound;
        int secondarySound = -1;
        int secondaryVolumePercent = 0;
        if (totalValue >= config.legendaryValuePrice())
        {
            primarySound = SoundEffectID.TOWN_CRIER_BELL_DONG;
            secondarySound = SoundEffectID.FIRE_WOOSH;
            secondaryVolumePercent = 68;
        }
        else if (totalValue >= config.insaneValuePrice())
        {
            primarySound = SoundEffectID.GE_ADD_OFFER_DINGALING;
            secondarySound = SoundEffectID.GE_COIN_TINKLE;
            secondaryVolumePercent = 62;
        }
        else if (totalValue >= config.highValuePrice())
        {
            primarySound = SoundEffectID.GE_COIN_TINKLE;
        }
        else
        {
            return;
        }

        int volume = config.dropSoundVolume();
        int extraSound = secondarySound;
        int extraVolume = Math.max(1, volume * secondaryVolumePercent / 100);
        clientThread.invokeLater(() ->
        {
            client.playSoundEffect(primarySound, volume);
            if (extraSound >= 0)
            {
                client.playSoundEffect(extraSound, extraVolume);
            }
        });
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
