package com.lootpop;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.event.MouseEvent;
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
import net.runelite.api.ChatMessageType;
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
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseAdapter;
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
    description = "Compact loot popups, rare-item reveals and a local loot journal with session and source highlights",
    tags = {"loot", "drop", "popup", "hud", "overlay", "price", "rare", "journal"}
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
    private Gson gson;

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

    @Inject
    private MouseManager mouseManager;

    private final MouseAdapter revealMouseListener = new MouseAdapter()
    {
        @Override
        public MouseEvent mousePressed(MouseEvent event)
        {
            if (event.getButton() == MouseEvent.BUTTON1 && !event.isAltDown() && !event.isControlDown()
                && !event.isShiftDown() && !event.isMetaDown() && overlay.skipRevealAt(event.getX(), event.getY()))
            {
                event.consume();
            }
            return event;
        }
    };

    private final Deque<LootNotification> notifications = new ArrayDeque<>();
    private final Deque<LootNotification> queuedNotifications = new ArrayDeque<>();
    private final Deque<LootHistoryEntry> lootHistory = new ArrayDeque<>();
    private final LootSessionRecap sessionRecap = new LootSessionRecap();
    private final Map<String, SessionAccumulator> sourceSessions = new LinkedHashMap<>();
    private String lastEventHash = "";
    private long lastEventAtMillis;
    private final Map<String, Long> pendingCollectionItems = new LinkedHashMap<>();
    private final Deque<LootNotification> recentDrops = new ArrayDeque<>();
    private int sessionDrops;
    private long sessionGeneration;
    private long sessionTotalValue;
    private int sessionPendingDrops;
    private String sessionBestSource = "";
    private long sessionBestValue;
    private LootPopPanel sidePanel;
    private NavigationButton navigationButton;
    private volatile LootJournalService journal;
    private String journalProfile;
    @Inject
    private java.util.concurrent.ScheduledExecutorService journalExecutor;

    @Provides
    LootPopConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(LootPopConfig.class);
    }

    @Override
    protected void startUp()
    {
        migrateLegacyLegendaryColour();
        journal = new LootJournalService(new LootJournalStore(RuneLite.RUNELITE_DIR.toPath().resolve("loot-pop-journal"), gson),
            this::refreshSidePanel, config::journalLimit, config::journalDays, journalExecutor, config::trophyLimit);
        switchJournalProfile();
        overlayManager.add(overlay);
        mouseManager.registerMouseListener(revealMouseListener);
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
        mouseManager.unregisterMouseListener(revealMouseListener);
        overlay.clearRevealTargets();
        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }
        sidePanel = null;
        if (journal != null) { journal.close(); journal = null; }
        journalProfile = null;
        notifications.clear();
        queuedNotifications.clear();
        lootHistory.clear();
        sessionRecap.clear();
        sourceSessions.clear();
        lastEventHash = "";
        lastEventAtMillis = 0L;
        pendingCollectionItems.clear();
        recentDrops.clear();
        sessionDrops = 0;
        sessionGeneration++;
        sessionTotalValue = 0L;
        sessionPendingDrops = 0;
        sessionBestSource = "";
        sessionBestValue = 0L;
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

            LootPriceQuote quote = quoteFor(itemStack.getId(), config.priceMode());
            long unitPrice = quote.value;
            if (!quote.shouldInclude(config.showZeroValueItems()))
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
                dropRateInfo == null ? 0.0 : dropRateInfo.getOneIn(),
                quote.pending
            );
            items.add(item);
        }

        items = LootDisplayItem.mergeDuplicates(items);
        long totalValue = 0L;
        for (LootDisplayItem item : items)
        {
            totalValue += item.getTotalValue();
        }

        if (items.isEmpty() || !LootPriceQuote.passesMinimum(totalValue, config.minimumDropValue(), items))
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
            notification.requestScreenshot();
            pendingCollectionItems.values().removeIf(expiry -> expiry < now);
            for (LootDisplayItem item : items)
            {
                String itemName = collectionItemName(item);
                if (pendingCollectionItems.remove(itemName) != null)
                {
                    notification.markCollectionLogItem(item.getItemId(), now);
                }
            }
            rememberDrop(notification);
            enqueueNotification(notification);
            recordLoot(notification, now);
        }

        refreshSidePanel();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) { return; }
        String itemName = collectionLogItemName(Text.removeTags(event.getMessage()));
        if (itemName == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (this)
        {
            boolean matched = false;
            for (LootNotification drop : recentDrops)
            {
                if (now - drop.getCreatedAtMillis() > 5_000L) { continue; }
                for (LootDisplayItem item : drop.getItems())
                {
                    if (collectionItemName(item).equals(itemName))
                    {
                        confirmCollectionItem(drop, item.getItemId(), now);
                        matched = true;
                        break;
                    }
                }
                if (matched) { break; }
            }
            if (!matched)
            {
                pendingCollectionItems.values().removeIf(expiry -> expiry < now);
                pendingCollectionItems.put(itemName, now + 5_000L);
                while (pendingCollectionItems.size() > 16)
                {
                    pendingCollectionItems.remove(pendingCollectionItems.keySet().iterator().next());
                }
            }
        }
        refreshSidePanel();
    }

    static String collectionLogItemName(String message)
    {
        String prefix = "New item added to your collection log: ";
        return message != null && message.startsWith(prefix) && message.length() > prefix.length()
            ? message.substring(prefix.length()).trim().toLowerCase(java.util.Locale.ENGLISH) : null;
    }

    private String collectionItemName(LootDisplayItem item)
    {
        ItemComposition composition = itemManager.getItemComposition(itemManager.canonicalize(item.getItemId()));
        return composition == null ? "" : composition.getName().trim().toLowerCase(java.util.Locale.ENGLISH);
    }

    private void rememberDrop(LootNotification notification)
    {
        recentDrops.addFirst(notification);
        while (recentDrops.size() > 32) { recentDrops.removeLast(); }
    }

    private void confirmCollectionItem(LootNotification drop, int itemId, long now)
    {
        if (!drop.markCollectionLogItem(itemId, now)) { return; }
        // Only rebuild before any reveal is visible. A late unlock must not move settled items.
        if (queuedNotifications.contains(drop) || drop.getAgeMillis(now) <= 150)
        {
            prepareSealedDrop(drop);
        }
        for (LootHistoryEntry entry : lootHistory)
        {
            if (entry.getTimestamp() == drop.getCreatedAtMillis()
                && entry.getSourceName().equals(drop.getSourceName()))
            {
                entry.setCollectionLogUnlock(true);
                    for (LootHistoryItem item : entry.getItems())
                    { if (item.getItemId() == itemId) { item.setCollectionLogItem(true); } }
                break;
            }
        }
        if (journal != null && config.savedLootHistory())
        {
            journal.confirmCollection(drop.getSourceName(), drop.getCreatedAtMillis(), itemId);
        }
        sessionRecap.confirmCollection(drop.getSourceName(), drop.getCreatedAtMillis(), itemId);
        refreshSidePanel();
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { switchJournalProfile(); }

    @Subscribe
    public void onProfileChanged(ProfileChanged event) { switchJournalProfile(); }

    private synchronized void switchJournalProfile()
    {
        String profile = configManager.getRSProfileKey();
        if (!java.util.Objects.equals(journalProfile, profile))
        {
            notifications.clear(); queuedNotifications.clear(); recentDrops.clear(); pendingCollectionItems.clear();
            lastEventHash = ""; lastEventAtMillis = 0;
            clearSession();
            journalProfile = profile;
        }
        if (journal != null) { journal.switchProfile(config.savedLootHistory() ? profile : null); }
        refreshSidePanel();
    }

    List<LootHistoryEntry> getJournalEntries()
    {
        LootJournalService current = journal;
        return current == null || !config.savedLootHistory() ? Collections.emptyList() : current.entries();
    }

    List<LootHistoryEntry> getTrophyEntries()
    {
        LootJournalService current = journal;
        return current == null || !config.savedLootHistory() ? Collections.emptyList() : current.trophies();
    }

    String getJournalStatus()
    {
        if (!config.savedLootHistory()) { return "Saving is off. Existing files are kept."; }
        LootJournalService current = journal;
        return current == null ? "Journal not running" : current.status();
    }

    boolean canClearJournal() { return journal != null && config.savedLootHistory() && journal.canClear(); }
    String getJournalNotice()
    {
        if (config == null) { return ""; }
        if (!config.savedLootHistory()) { return "Saving is off"; }
        LootJournalService current = journal;
        return current == null ? "Journal not running" : current.notice();
    }
    void clearJournal() { if (journal != null && config.savedLootHistory()) { journal.clear(); } }

    java.awt.image.BufferedImage getHistoryImage(LootHistoryItem item)
    {
        if (item.getImage() != null) { return item.getImage(); }
        return itemManager == null || item.getItemId() < 0 ? null : itemManager.getImage(item.getItemId(), item.getQuantity(), false);
    }

    private void prepareSealedDrop(LootNotification notification)
    {
        if (config.visualMode() != VisualMode.ENHANCED || !config.sealedDrops()) { return; }
        SealedDropSequence sequence = new SealedDropSequence(notification.getItems(),
            notification.getCollectionLogItems(), config.sealedDropRate(), config.collectionLogReveals(),
            config.reduceMotion(), config.sealedDropSpeed(),
            LootPopOverlay.visibleItemCount(config, notification.getItems().size()));
        if (sequence.getCandidateCount() > 0) { notification.setSealedDrop(sequence); }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!LootPopConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }
        if ("savedLootHistory".equals(event.getKey())) { switchJournalProfile(); }

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
        for (LootNotification notification : notifications)
        {
            SealedDropSequence prepared = notification.getSealedDrop();
            if (!config.sealedDrops() || config.visualMode() != VisualMode.ENHANCED
                || (config.reduceMotion() && prepared != null && !prepared.isReducedMotion()))
            {
                notification.skipReveal(now);
            }
            SealedDropSequence sequence = notification.getSealedDrop();
            long age = notification.getAgeMillis(now);
            if (sequence != null && age < sequence.getRevealEndMillis())
            {
                for (int i = 0; i < sequence.getCandidateCount(); i++)
                {
                    double progress = sequence.progress(age, i);
                    if (progress >= 0.3 && progress < 1 && notification.takeRevealSound(sequence.getStage(i)))
                    {
                        playRevealSound(notification.isCollectionLogItem(sequence.getItems().get(i).getItemId()));
                    }
                }
            }
            else if (notification.takeSound() && !notification.hasPendingPrices()) { playDropSound(notification.getTotalValue()); }
            if ((sequence == null || age >= sequence.getDurationMillis()) && notification.takeScreenshot())
            {
                clientThread.invokeLater(() -> takeAutomaticScreenshot(notification.getSourceName(),
                    notification.getTotalValue(), notification.getItems()));
            }
        }
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
        if (config.adaptiveDisplayTime())
        {
            switch (rarestRarity(notification.getItems()))
            {
                case ULTRA_RARE: base += config.ultraRareExtraTime(); break;
                case VERY_RARE: base += Math.max(config.rareExtraTime(), config.ultraRareExtraTime() / 2); break;
                case RARE: base += config.rareExtraTime(); break;
                case UNCOMMON: base += config.rareExtraTime() / 3; break;
                default: break;
            }
        }
        long duration = base + notification.getRevealDelayMillis();
        if (notification.getLateUnlockAtAge() >= 0)
        {
            duration = Math.max(duration, notification.getLateUnlockAtAge() + 1800);
        }
        return (int) Math.min(Integer.MAX_VALUE, duration);
    }

    private void enqueueNotification(LootNotification notification)
    {
        prepareSealedDrop(notification);
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
            sessionBestValue,
            sessionPendingDrops
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
        return new SessionSnapshot(source.drops, source.totalValue, sourceName, source.bestValue, source.pendingDrops);
    }

    synchronized List<String> getSessionSources()
    {
        return new ArrayList<>(sourceSessions.keySet());
    }

    synchronized List<LootHistoryEntry> getLootHistory()
    {
        return new ArrayList<>(lootHistory);
    }

    LootInsights.Summary getSessionRecap(PriceMode mode) { return sessionRecap.snapshot(mode); }

    synchronized long getSessionGeneration() { return sessionGeneration; }

    synchronized boolean clearSessionIfGeneration(long expected)
    {
        if (sessionGeneration != expected) { return false; }
        clearSession(); return true;
    }

    synchronized void clearSession()
    {
        sessionGeneration++;
        sessionDrops = 0;
        sessionTotalValue = 0L;
        sessionPendingDrops = 0;
        sessionBestSource = "";
        sessionBestValue = 0L;
        lootHistory.clear();
        sessionRecap.clear();
        sourceSessions.clear();
        refreshSidePanel();
    }

    private void recordLoot(LootNotification notification, long now)
    {
        sessionDrops++;
        sessionTotalValue += notification.getTotalValue();
        if (notification.hasPendingPrices()) { sessionPendingDrops++; }
        if (!notification.hasPendingPrices() && notification.getTotalValue() > sessionBestValue)
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
        if (notification.hasPendingPrices()) { source.pendingDrops++; }
        else { source.bestValue = Math.max(source.bestValue, notification.getTotalValue()); }

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
            LootHistoryItem historyItem = new LootHistoryItem(
                item.getQuantity(),
                itemName,
                gePrice,
                highAlchemy,
                item.getTotalValue(),
                item.getDropRate(),
                rarityForRate(item.getDropRateOneIn()),
                image
            ).withItemId(item.getItemId()).withPriceState(item.isPricePending(),
                LootPriceQuote.of(PriceMode.GRAND_EXCHANGE, composition != null,
                    composition != null && composition.isTradeable(), gePrice, highAlchemy).pending);
            historyItem.setCollectionLogItem(notification.isCollectionLogItem(item.getItemId()));
            historyItems.add(historyItem);
        }

        LootHistoryEntry entry = new LootHistoryEntry(
            notification.getSourceName(),
            notification.getTotalValue(),
            now,
            rarestRarity(notification.getItems()),
            historyItems
        );
        entry.setCollectionLogUnlock(notification.isCollectionLogUnlock());
        entry.setPriceMode(config.priceMode());
        if (journal != null && config.savedLootHistory())
        {
            journal.record(entry, notification, config.personalBestPopups(), config.personalBestMinimum());
        }
        lootHistory.addFirst(entry);
        sessionRecap.record(entry);
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
        boolean incomplete = items.stream().anyMatch(LootDisplayItem::isPricePending);
        boolean meetsValue = !incomplete && config.screenshotMinimumValue() > 0
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
            "Loot Pop - " + safeSource + " - " + (incomplete ? "price pending" : totalValue + " gp"),
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

        if (scenario == TestScenario.SEALED_DROP)
        {
            items.set(1, new LootDisplayItem(platebody.getItemId(), 1, plateValue, "1 / 128", 128));
        }
        if (scenario == TestScenario.PRICE_PENDING || scenario == TestScenario.PRICE_PENDING_ONLY)
        {
            items.clear();
            items.add(new LootDisplayItem(ItemID.TWISTED_BOW, 1, 0, "1 / 5,000", 5000, true));
            if (scenario == TestScenario.PRICE_PENDING) { items.add(bones); }
        }
        if (scenario == TestScenario.COLLECTION_LOG_COMMON || scenario == TestScenario.COLLECTION_LOG_LATE)
        {
            items.clear();
            items.add(new LootDisplayItem(ItemID.DRAGON_BONES, 21, bonesUnitValue, "1 / 2", 2));
            items.add(new LootDisplayItem(995, 1500, 1, null, 0));
        }
        if (scenario == TestScenario.MANY_ITEMS || scenario == TestScenario.SEALED_BATCH)
        {
            int[] itemIds = {995, 385, 4151, 11840, 12934, 6571, 3144, 1515};
            for (int i = 0; i < itemIds.length; i++)
            {
                items.add(new LootDisplayItem(
                    itemIds[i],
                    i + 2,
                    Math.max(1L, targetValue / (80L + i * 15L)),
                    "1 / " + (scenario == TestScenario.SEALED_BATCH ? 5000 : 128 * (i + 1)),
                    scenario == TestScenario.SEALED_BATCH ? 5000 : 128.0 * (i + 1)
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
                if (scenario == TestScenario.COLLECTION_LOG || scenario == TestScenario.COLLECTION_LOG_COMMON)
                {
                    notification.markCollectionLogItem(items.get(0).getItemId(), now);
                }
                if (scenario == TestScenario.PERSONAL_BEST)
                {
                    notification.markPersonalBest(Math.max(1, totalValue * 3 / 5));
                }
                rememberDrop(notification);
                enqueueNotification(notification);
                if (scenario == TestScenario.COLLECTION_LOG_LATE)
                {
                    final int unlockId = items.get(0).getItemId();
                    clientThread.invokeLater(() ->
                    {
                        if (System.currentTimeMillis() < now + 2000) { return false; }
                        synchronized (LootPopPlugin.this)
                        {
                            if (recentDrops.contains(notification))
                            {
                                confirmCollectionItem(notification, unlockId, System.currentTimeMillis());
                            }
                        }
                        return true;
                    });
                }
            }
        }
    }

    private boolean needsDropRateData()
    {
        if (config.savedLootHistory() || (config.sealedDrops() && config.visualMode() == VisualMode.ENHANCED)
            || config.enableDropRarity()
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

    private void playRevealSound(boolean collection)
    {
        if (!config.rareDropSounds() || config.dropSoundVolume() <= 0 || config.reduceMotion()) { return; }
        int sound = collection ? SoundEffectID.GE_ADD_OFFER_DINGALING : SoundEffectID.FIRE_WOOSH;
        clientThread.invokeLater(() -> client.playSoundEffect(sound, config.dropSoundVolume() * 65 / 100));
    }

    private LootPriceQuote quoteFor(int itemId, PriceMode mode)
    {
        int canonicalId = itemManager.canonicalize(itemId);
        ItemComposition composition = itemManager.getItemComposition(canonicalId);
        return LootPriceQuote.of(mode, composition != null, composition != null && composition.isTradeable(),
            itemManager.getItemPrice(canonicalId), composition == null ? 0 : composition.getHaPrice());
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
        private int pendingDrops;
    }
}
