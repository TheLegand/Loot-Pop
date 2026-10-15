package com.lootpop;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

@PluginDescriptor(
    name = "Loot Pop",
    description = "Shows polished loot popups with drop rarity, hover details and value-tier effects",
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

    @Inject
    private DropRateService dropRateService;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ImageCapture imageCapture;

    private final Deque<LootNotification> notifications = new ArrayDeque<>();
    private final Deque<LootNotification> queuedNotifications = new ArrayDeque<>();
    private final Deque<LootHistoryEntry> lootHistory = new ArrayDeque<>();
    private final Map<String, SessionAccumulator> sourceSessions = new LinkedHashMap<>();
    private String lastEventHash = "";
    private long lastEventAtMillis;
    private long collectionLogPendingUntil;
    private int sessionDrops;
    private long sessionTotalValue;
    private String sessionBestSource = "";
    private long sessionBestValue;
    private LootNotification mostRecentLootNotification;
    private long mostRecentLootAt;
    private LootPopPanel sidePanel;
    private NavigationButton navigationButton;

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
        sidePanel = new LootPopPanel(this, config, configManager);
        navigationButton = NavigationButton.builder()
            .tooltip("Loot Pop")
            .icon(LootPopPanel.createNavigationIcon())
            .priority(8)
            .panel(sidePanel)
            .build();
        clientToolbar.addNavigation(navigationButton);
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
        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }
        sidePanel = null;
        notifications.clear();
        queuedNotifications.clear();
        lootHistory.clear();
        sourceSessions.clear();
        lastEventHash = "";
        lastEventAtMillis = 0L;
        collectionLogPendingUntil = 0L;
        sessionDrops = 0;
        sessionTotalValue = 0L;
        sessionBestSource = "";
        sessionBestValue = 0L;
        mostRecentLootNotification = null;
        mostRecentLootAt = 0L;
    }

    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        if (event.getType() == LootRecordType.NPC)
        {
            return;
        }

        handleLoot(event.getName(), event.getType(), event.getItems(), DropRateService.NO_NPC_ID);
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        NPC npc = event.getNpc();
        if (npc != null)
        {
            handleLoot(npc.getName(), LootRecordType.NPC, event.getItems(), npc.getId());
        }
    }

    @Subscribe
    public void onServerNpcLoot(ServerNpcLoot event)
    {
        NPCComposition composition = event.getComposition();
        if (composition != null)
        {
            handleLoot(composition.getName(), LootRecordType.NPC, event.getItems(), composition.getId());
        }
    }

    private void handleLoot(
        String eventName,
        LootRecordType type,
        Collection<ItemStack> itemStacks,
        int npcId
    )
    {
        if (!isTypeEnabled(type) || WildcardMatcher.matchesAny(config.ignoredSources(), eventName))
        {
            return;
        }

        String sourceName = eventName == null || eventName.trim().isEmpty()
            ? "Unknown"
            : eventName;

        List<LootDisplayItem> items = new ArrayList<>();
        for (ItemStack itemStack : itemStacks)
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

            DropRateInfo dropRateInfo = needsDropRateData()
                ? dropRateService.findRateInfo(sourceName, npcId, itemComposition.getName())
                : null;
            LootDisplayItem item = new LootDisplayItem(
                itemStack.getId(),
                itemStack.getQuantity(),
                unitPrice,
                dropRateInfo == null ? null : dropRateInfo.getDisplayRate(),
                dropRateInfo == null ? 0.0 : dropRateInfo.getOneIn()
            );
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

        String eventHash = createEventHash(sourceName, type, items);
        long now = System.currentTimeMillis();

        synchronized (this)
        {
            if (eventHash.equals(lastEventHash) && now - lastEventAtMillis < DUPLICATE_WINDOW_MILLIS)
            {
                return;
            }

            lastEventHash = eventHash;
            lastEventAtMillis = now;
            LootNotification notification = new LootNotification(sourceName, items, totalValue, now);
            mostRecentLootNotification = notification;
            mostRecentLootAt = now;
            if (now <= collectionLogPendingUntil)
            {
                notification.setCollectionLogUnlock(true);
                collectionLogPendingUntil = 0L;
            }
            enqueueNotification(notification);
            recordLoot(notification, now);
        }

        playDropSound(totalValue);
        takeAutomaticScreenshot(sourceName, totalValue, items);
        refreshSidePanel();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        String message = Text.removeTags(event.getMessage());
        if (message == null || !message.toLowerCase(java.util.Locale.ENGLISH)
            .contains("new item added to your collection log"))
        {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (this)
        {
            LootNotification newest = mostRecentLootNotification;
            LootHistoryEntry history = lootHistory.peekFirst();
            if (newest != null && now - mostRecentLootAt <= 3_000L)
            {
                newest.setCollectionLogUnlock(true);
                if (history != null)
                {
                    history.setCollectionLogUnlock(true);
                }
            }
            else
            {
                collectionLogPendingUntil = now + 3_000L;
            }
        }
        refreshSidePanel();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!LootPopConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        if (sidePanel != null)
        {
            sidePanel.syncControls();
        }

        if (!"showTestPopup".equals(event.getKey()) || !config.showTestPopup())
        {
            return;
        }

        showTestPopup(config.testTier(), config.testScenario());
        configManager.setConfiguration(LootPopConfig.GROUP, "showTestPopup", false);
    }

    synchronized List<LootNotification> getVisibleNotifications()
    {
        long now = System.currentTimeMillis();
        notifications.removeIf(notification -> notification.isExpired(
            now,
            displayDurationFor(notification),
            config.fadeDuration()
        ));
        fitActiveCapacity(now);

        List<LootNotification> result = new ArrayList<>(notifications);
        if (!config.newestOnTop())
        {
            Collections.reverse(result);
        }
        return result;
    }

    int displayDurationFor(LootNotification notification)
    {
        int base = config.displayDuration();
        if (!config.adaptiveDisplayTime())
        {
            return base;
        }

        DropRarity rarity = rarestRarity(notification.getItems());
        switch (rarity)
        {
            case ULTRA_RARE:
                return base + config.ultraRareExtraTime();
            case VERY_RARE:
                return base + Math.max(config.rareExtraTime(), config.ultraRareExtraTime() / 2);
            case RARE:
                return base + config.rareExtraTime();
            case UNCOMMON:
                return base + config.rareExtraTime() / 3;
            default:
                return base;
        }
    }

    private void enqueueNotification(LootNotification notification)
    {
        if (notifications.size() < config.maxPopups())
        {
            notifications.addFirst(notification);
        }
        else if (config.queuedPopups() > 0)
        {
            queuedNotifications.addLast(notification);
            trimQueue();
        }
    }

    private void fitActiveCapacity(long now)
    {
        while (notifications.size() > config.maxPopups())
        {
            LootNotification displaced = notifications.removeLast();
            displaced.setHovered(false, now);
            queuedNotifications.addFirst(displaced);
        }
        trimQueue();

        while (notifications.size() < config.maxPopups() && !queuedNotifications.isEmpty())
        {
            LootNotification next = queuedNotifications.removeFirst();
            next.restartAt(now);
            notifications.addFirst(next);
        }
    }

    private void trimQueue()
    {
        while (queuedNotifications.size() > config.queuedPopups())
        {
            queuedNotifications.removeLast();
        }
    }

    synchronized SessionSnapshot getSessionSnapshot()
    {
        return new SessionSnapshot(
            sessionDrops,
            sessionTotalValue,
            sessionBestSource,
            sessionBestValue
        );
    }

    synchronized SessionSnapshot getSessionSnapshot(String sourceName)
    {
        if (sourceName == null || LootPopPanel.ALL_SOURCES.equals(sourceName))
        {
            return getSessionSnapshot();
        }
        SessionAccumulator source = sourceSessions.get(sourceName);
        if (source == null)
        {
            return new SessionSnapshot(0, 0L, "", 0L);
        }
        return new SessionSnapshot(source.drops, source.totalValue, sourceName, source.bestValue);
    }

    synchronized List<String> getSessionSources()
    {
        return new ArrayList<>(sourceSessions.keySet());
    }

    synchronized List<LootHistoryEntry> getLootHistory()
    {
        return new ArrayList<>(lootHistory);
    }

    synchronized void clearSession()
    {
        sessionDrops = 0;
        sessionTotalValue = 0L;
        sessionBestSource = "";
        sessionBestValue = 0L;
        lootHistory.clear();
        sourceSessions.clear();
        refreshSidePanel();
    }

    private void recordLoot(LootNotification notification, long now)
    {
        sessionDrops++;
        sessionTotalValue += notification.getTotalValue();
        if (notification.getTotalValue() > sessionBestValue)
        {
            sessionBestValue = notification.getTotalValue();
            sessionBestSource = notification.getSourceName();
        }

        SessionAccumulator source = sourceSessions.computeIfAbsent(
            notification.getSourceName(),
            ignored -> new SessionAccumulator()
        );
        source.drops++;
        source.totalValue += notification.getTotalValue();
        source.bestValue = Math.max(source.bestValue, notification.getTotalValue());

        List<LootHistoryItem> historyItems = new ArrayList<>();
        for (LootDisplayItem item : notification.getItems())
        {
            int canonicalId = itemManager.canonicalize(item.getItemId());
            ItemComposition composition = itemManager.getItemComposition(canonicalId);
            String itemName = composition == null ? "Unknown item" : composition.getName();
            long gePrice = Math.max(0L, itemManager.getItemPrice(canonicalId));
            long highAlchemy = composition == null ? 0L : Math.max(0L, composition.getHaPrice());
            java.awt.image.BufferedImage image = null;
            try
            {
                image = itemManager.getImage(item.getItemId(), item.getQuantity(), false);
            }
            catch (RuntimeException ignored)
            {
                // The text-only fallback keeps history usable if an image is temporarily unavailable.
            }
            historyItems.add(new LootHistoryItem(
                item.getQuantity(),
                itemName,
                gePrice,
                highAlchemy,
                item.getTotalValue(),
                item.getDropRate(),
                rarityForRate(item.getDropRateOneIn()),
                image
            ));
        }

        LootHistoryEntry entry = new LootHistoryEntry(
            notification.getSourceName(),
            notification.getTotalValue(),
            now,
            rarestRarity(notification.getItems()),
            historyItems
        );
        entry.setCollectionLogUnlock(notification.isCollectionLogUnlock());
        lootHistory.addFirst(entry);
        while (lootHistory.size() > config.historySize())
        {
            lootHistory.removeLast();
        }
    }

    private void refreshSidePanel()
    {
        LootPopPanel panel = sidePanel;
        if (panel != null)
        {
            SwingUtilities.invokeLater(panel::refresh);
        }
    }

    private void takeAutomaticScreenshot(
        String sourceName,
        long totalValue,
        List<LootDisplayItem> items
    )
    {
        if (!config.automaticScreenshots())
        {
            return;
        }

        DropRarity rarity = rarestRarity(items);
        boolean meetsRarity = rarity.ordinal() >= screenshotRarityThreshold().ordinal();
        boolean meetsValue = config.screenshotMinimumValue() > 0
            && totalValue >= config.screenshotMinimumValue();
        if (!meetsRarity && !meetsValue)
        {
            return;
        }

        String safeSource = sourceName.replaceAll("[^a-zA-Z0-9 _-]", "").trim();
        if (safeSource.isEmpty())
        {
            safeSource = "Loot";
        }
        imageCapture.takeScreenshot(
            "Loot Pop - " + safeSource + " - " + totalValue + " gp",
            "Loot Pop",
            true,
            false,
            false
        );
    }

    private DropRarity screenshotRarityThreshold()
    {
        switch (config.screenshotRarity())
        {
            case RARE:
                return DropRarity.RARE;
            case VERY_RARE:
                return DropRarity.VERY_RARE;
            case ULTRA_RARE:
            default:
                return DropRarity.ULTRA_RARE;
        }
    }

    DropRarity rarityForRate(double oneIn)
    {
        if (oneIn <= 0.0)
        {
            return DropRarity.UNKNOWN;
        }
        if (oneIn >= config.ultraRareRate())
        {
            return DropRarity.ULTRA_RARE;
        }
        if (oneIn >= config.veryRareRate())
        {
            return DropRarity.VERY_RARE;
        }
        if (oneIn >= config.rareRate())
        {
            return DropRarity.RARE;
        }
        if (oneIn >= config.uncommonRate())
        {
            return DropRarity.UNCOMMON;
        }
        return DropRarity.COMMON;
    }

    private DropRarity rarestRarity(List<LootDisplayItem> items)
    {
        DropRarity rarity = DropRarity.UNKNOWN;
        for (LootDisplayItem item : items)
        {
            DropRarity candidate = rarityForRate(item.getDropRateOneIn());
            if (candidate.ordinal() > rarity.ordinal())
            {
                rarity = candidate;
            }
        }
        return rarity;
    }

    private void showTestPopup(TestTier tier, TestScenario scenario)
    {
        long targetValue = testValueFor(tier);
        long bonesUnitValue = Math.max(1L, targetValue / 100L);
        LootDisplayItem bones = new LootDisplayItem(
            ItemID.DRAGON_BONES,
            21,
            bonesUnitValue,
            "1 / 512",
            512.0
        );
        long plateValue = Math.max(1L, targetValue / 4L);
        LootDisplayItem platebody = new LootDisplayItem(
            ItemID.RUNE_PLATEBODY,
            1,
            plateValue,
            "1 / 2,000",
            2_000.0
        );
        long remainingValue = Math.max(1L, targetValue - bones.getTotalValue() - platebody.getTotalValue());
        LootDisplayItem rareItem = new LootDisplayItem(
            ItemID.TWISTED_BOW,
            1,
            remainingValue,
            "1 / 5,000",
            5_000.0
        );
        List<LootDisplayItem> items = new ArrayList<>();
        items.add(rareItem);
        items.add(platebody);
        items.add(bones);

        if (scenario == TestScenario.MANY_ITEMS)
        {
            int[] itemIds = {995, 385, 4151, 11840, 12934, 6571, 3144, 1515};
            for (int i = 0; i < itemIds.length; i++)
            {
                items.add(new LootDisplayItem(
                    itemIds[i],
                    i + 2,
                    Math.max(1L, targetValue / (80L + i * 15L)),
                    "1 / " + (128 * (i + 1)),
                    128.0 * (i + 1)
                ));
            }
        }

        long totalValue = 0L;
        for (LootDisplayItem item : items)
        {
            totalValue += item.getTotalValue();
        }

        synchronized (this)
        {
            int popupCount = scenario == TestScenario.POPUP_STACK ? 3 : 1;
            long now = System.currentTimeMillis();
            for (int i = 0; i < popupCount; i++)
            {
                LootNotification notification = new LootNotification(
                    popupCount == 1 ? tier + " test" : tier + " kill " + (i + 1),
                    items,
                    totalValue,
                    now - (popupCount - 1L - i) * 140L
                );
                notification.setCollectionLogUnlock(scenario == TestScenario.COLLECTION_LOG);
                enqueueNotification(notification);
            }
        }
        playDropSound(totalValue);
    }

    private boolean needsDropRateData()
    {
        if (config.enableDropRarity()
            || config.adaptiveDisplayTime()
            || config.automaticScreenshots())
        {
            return true;
        }
        if (!config.showItemTooltips())
        {
            return false;
        }
        return config.hoverDetailMode() != HoverDetailMode.CUSTOM
            || config.hoverShowDropChance();
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

    private static final class SessionAccumulator
    {
        private int drops;
        private long totalValue;
        private long bestValue;
    }
}
