package com.lootpop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SealedDropSequenceTest
{
    private static LootDisplayItem item(int id, double rate)
    {
        return new LootDisplayItem(id, 1, 100, rate == 0 ? null : "1 / " + rate, rate);
    }

    private static SealedDropSequence sequence(List<LootDisplayItem> items)
    {
        return new SealedDropSequence(items, Collections.emptySet(), 1000, true, false, 100, 10);
    }

    @Test
    public void settlesFirstRareBeforeSecondAndOnlyThenFadesOrdinaryLoot()
    {
        LootDisplayItem common = item(1, 128);
        SealedDropSequence s = sequence(Arrays.asList(common, item(2, 5000), item(3, 2000)));
        assertEquals(2, s.getCandidateCount());
        assertEquals(2, s.getItems().get(0).getItemId());
        assertSame(common, s.getItems().get(2));
        assertTrue(s.progress(1800, 0) >= 1);
        assertTrue(s.progress(1800, 1) > 0 && s.progress(1800, 1) < 1);
        assertEquals(0f, s.normalAlpha(1800), 0.001f);
        assertEquals(0f, s.normalAlpha(s.getRevealEndMillis()), 0.001f);
        assertEquals(0.5f, s.normalAlpha(s.getRevealEndMillis() + 90), 0.001f);
        assertEquals(1f, s.normalAlpha(s.getDurationMillis()), 0.001f);
    }

    @Test
    public void manyRaresHaveOnlyThreeBoundedStagesAndRetainAllItems()
    {
        List<LootDisplayItem> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) { items.add(item(i, 5000)); }
        SealedDropSequence s = sequence(items);
        assertEquals(3, s.getStageCount());
        assertEquals(10, s.getCandidateCount());
        assertEquals(20, s.getItems().size());
        assertEquals(2, s.getStage(9));
        assertTrue(s.getDurationMillis() < 6000);
    }

    @Test
    public void onlyConfirmedUnlocksTriggerCommonItemReveals()
    {
        List<LootDisplayItem> items = Arrays.asList(item(1, 0), item(2, 2));
        assertEquals(0, sequence(items).getCandidateCount());
        SealedDropSequence confirmed = new SealedDropSequence(items, Collections.singleton(2),
            1000, true, false, 100, 10);
        assertEquals(1, confirmed.getCandidateCount());
        assertEquals(2, confirmed.getItems().get(0).getItemId());
        assertEquals(0, new SealedDropSequence(items, Collections.singleton(2),
            1000, false, false, 100, 10).getCandidateCount());
    }

    @Test
    public void reducedMotionUsesOneBriefFadeAndUnknownRatesDoNotGuessRarity()
    {
        SealedDropSequence s = new SealedDropSequence(Arrays.asList(item(1, 0), item(2, 5000), item(3, 2000)),
            Collections.emptySet(), 1000, true, true, 100, 10);
        assertTrue(s.isReducedMotion());
        assertEquals(1, s.getStageCount());
        assertEquals(2, s.getCandidateCount());
        assertTrue(s.getDurationMillis() < 1000);
    }

    @Test
    public void visibleCapacityReservesOverflowBadge()
    {
        LootPopConfig config = new LootPopConfig() { @Override public int maxItems() { return 10; } };
        assertEquals(9, LootPopOverlay.visibleItemCount(config, 20));
        assertEquals(10, LootPopOverlay.visibleItemCount(config, 10));
    }

    @Test
    public void skipPreservesReadingTimeAndItemOrderWithoutReplaying()
    {
        LootNotification n = new LootNotification("Boss", Arrays.asList(item(1, 128), item(2, 5000)), 200, 1000);
        n.setSealedDrop(sequence(n.getItems()));
        n.skipReveal(1800);
        assertNull(n.getSealedDrop());
        assertEquals(800, n.getRevealDelayMillis());
        assertEquals(2, n.getDisplayItems().get(0).getItemId());
        assertFalse(n.isExpired(4800, (int) (3000 + n.getRevealDelayMillis()), 500));
        n.skipReveal(3000);
        assertEquals(800, n.getRevealDelayMillis());
    }

    @Test
    public void lateCollectionUnlockDoesNotReplaceTimelineAndDuplicatesAreIgnored()
    {
        LootNotification n = new LootNotification("Boss", Arrays.asList(item(1, 128), item(2, 5000)), 200, 1000);
        SealedDropSequence s = sequence(n.getItems());
        n.setSealedDrop(s);
        assertTrue(n.markCollectionLogItem(1, 5000));
        assertEquals(4000, n.getLateUnlockAtAge());
        assertSame(s, n.getSealedDrop());
        assertTrue(n.isCollectionLogItem(1));
        assertFalse(n.isCollectionLogItem(2));
        assertFalse(n.markCollectionLogItem(1, 5500));
        assertEquals(4000, n.getLateUnlockAtAge());
    }

    @Test
    public void revealSoundsAreTakenOnlyOncePerStage()
    {
        LootNotification n = new LootNotification("Boss", Collections.emptyList(), 0, 0);
        assertTrue(n.takeRevealSound(0));
        assertFalse(n.takeRevealSound(0));
        assertTrue(n.takeRevealSound(1));
        assertFalse(n.takeRevealSound(0));
        assertTrue(n.takeSound());
        assertFalse(n.takeSound());
    }

    @Test
    public void syntheticPreviewsNeverRequestAutomaticScreenshots()
    {
        LootNotification n = new LootNotification("Test", Collections.emptyList(), 0, 0);
        assertFalse(n.takeScreenshot());
        n.requestScreenshot();
        assertTrue(n.takeScreenshot());
        assertFalse(n.takeScreenshot());
    }

    @Test
    public void collectionMessagesRequireTheExactServerPrefixAndItemName()
    {
        assertEquals("twisted bow", LootPopPlugin.collectionLogItemName("New item added to your collection log: Twisted bow"));
        assertNull(LootPopPlugin.collectionLogItemName("Player says New item added to your collection log: Twisted bow"));
        assertNull(LootPopPlugin.collectionLogItemName("New item added to your collection log: "));
        assertNull(LootPopPlugin.collectionLogItemName(null));
    }

    @Test
    public void hoverCannotFreezeTheFinalFadeButPausesReadingAfterItCompletes()
    {
        LootNotification n = new LootNotification("Boss", Collections.singletonList(item(1, 5000)), 100, 1000);
        SealedDropSequence s = sequence(n.getItems()); n.setSealedDrop(s);
        long revealEnd = 1000 + s.getRevealEndMillis();
        n.setHovered(true, revealEnd + 20);
        assertEquals(s.getDurationMillis(), n.getAgeMillis(1000 + s.getDurationMillis()));
        assertEquals(1f, s.normalAlpha(n.getAgeMillis(1000 + s.getDurationMillis())), 0.001f);
        n.setHovered(true, 1000 + s.getDurationMillis());
        assertEquals(s.getDurationMillis(), n.getAgeMillis(20000));
    }

    @Test
    public void slowRevealSpeedDoesNotCreateALongEmptyTransitionBetweenStagesAndNormalLoot()
    {
        SealedDropSequence s = new SealedDropSequence(Collections.singletonList(item(1, 5000)),
            Collections.emptySet(), 1000, true, false, 50, 10);
        assertTrue(s.getDurationMillis() - s.getRevealEndMillis() <= 200);
        assertEquals(1f, s.normalAlpha(s.getRevealEndMillis() + 200), 0.001f);
    }
}
