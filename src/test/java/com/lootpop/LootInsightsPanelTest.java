package com.lootpop;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootInsightsPanelTest
{
    private static List<LootHistoryEntry> samples()
    {
        long now = System.currentTimeMillis();
        LootHistoryItem bow = new LootHistoryItem(1, "Twisted bow", 100000000, 2400000, 100000000,
            "1 / 5,000", DropRarity.ULTRA_RARE, SealedDropRendererTest.sword(java.awt.Color.MAGENTA)).withItemId(20997);
        bow.setCollectionLogItem(true);
        LootHistoryEntry rare = LootInsightsTest.entry("Chambers of Xeric", now - 3600000, PriceMode.GRAND_EXCHANGE, bow);
        rare.setCollectionLogUnlock(true); rare.setSourceBest(true);
        LootHistoryItem coins = new LootHistoryItem(10, "Coins", 1, 0, 10, null, DropRarity.COMMON,
            SealedDropRendererTest.coins()).withItemId(995);
        LootHistoryEntry recent = LootInsightsTest.entry("Chambers of Xeric", now, PriceMode.GRAND_EXCHANGE, coins);
        LootHistoryEntry ha = LootInsightsTest.entry("Chambers of Xeric", now, PriceMode.HIGH_ALCHEMY,
            LootInsightsTest.item(20997, "Twisted bow", 2400000, "1 / 5,000"));
        return new ArrayList<>(Arrays.asList(recent, rare, ha));
    }

    private static LootPopPlugin fake(List<LootHistoryEntry> history, LootSessionRecap recap)
    {
        return new LootPopPlugin()
        {
            @Override List<LootHistoryEntry> getJournalEntries() { return history; }
            @Override synchronized List<LootHistoryEntry> getLootHistory() { return history; }
            @Override synchronized List<String> getSessionSources() { return Arrays.asList("Chambers of Xeric"); }
            @Override LootInsights.Summary getSessionRecap(PriceMode mode) { return recap.snapshot(mode); }
            @Override String getJournalStatus() { return "Stored on this device / this character only"; }
        };
    }

    @Test public void recapIsOptionalScrollableAndPreservesModeTotals() throws Exception
    {
        List<LootHistoryEntry> history = samples(); LootSessionRecap recap = new LootSessionRecap(); history.forEach(recap::record);
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake(history, recap), new LootPopConfig() {}, null);
            panel.setSize(242, 600); layout(panel);
            assertFalse(named(panel, "loot-detail").isVisible());
            ((JButton) named(panel, "loot-recap")).doClick(); layout(panel);
            assertTrue(named(panel, "loot-detail").isVisible());
            assertTrue(hasText(named(panel, "loot-detail"), "SESSION RECAP"));
            assertTrue(hasText(named(panel, "loot-detail"), "100M gp"));
            assertTrue(hasText(named(panel, "loot-detail"), "2 drops"));
            assertTrue(((JLabel) named(panel, "loot-detail-section-collection")).getText().contains("1"));
            assertTrue(named(panel, "loot-detail-body-top-item").isVisible());
            assertTrue(named(panel, "loot-detail-body-rarest").isVisible());
            assertTrue(named(panel, "loot-detail-body-collection").isVisible());
            assertNull(named(panel, "loot-fold-top-item"));
            save(panel, "session-recap.png");
            JScrollPane scroll = (JScrollPane) panel.getComponent(0);
            assertEquals(600, scroll.getHeight()); assertEquals(0, panel.getPreferredSize().height);
            panel.setSize(242, 450); layout(panel);
            assertTrue(scroll.getVerticalScrollBar().getMaximum() > scroll.getViewport().getHeight());
            save(panel, "session-recap-expanded.png");
            ((JComboBox<?>) named(panel, "loot-detail-price")).setSelectedItem(PriceMode.HIGH_ALCHEMY); layout(panel);
            assertTrue(hasText(named(panel, "loot-detail"), "2.4M gp"));
            assertTrue(hasText(named(panel, "loot-detail"), "1 drop"));
            recap.clear(); panel.refresh(); layout(panel);
            assertTrue(hasText(named(panel, "loot-detail"), "Your first observed drop"));
            ((JButton) named(panel, "loot-detail-back")).doClick();
            assertFalse(named(panel, "loot-detail").isVisible());
            assertTrue(named(panel, "loot-recap").isShowing() || named(panel, "loot-recap").isVisible());
        });
    }

    @Test public void sourceNameOpensLocalProfileAndTabsReturnToTheMainView() throws Exception
    {
        List<LootHistoryEntry> history = samples();
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake(history, new LootSessionRecap()), new LootPopConfig() {}, null);
            panel.setSize(242, 620); layout(panel);
            JLabel source = (JLabel) named(panel, "loot-source-link");
            java.awt.event.MouseEvent click = new java.awt.event.MouseEvent(source,
                java.awt.event.MouseEvent.MOUSE_CLICKED, 1, 0, 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
            for (java.awt.event.MouseListener listener : source.getMouseListeners()) { listener.mouseClicked(click); }
            layout(panel);
            assertTrue(hasText(named(panel, "loot-detail"), "SOURCE PROFILE"));
            assertTrue(hasText(named(panel, "loot-detail"), "Chambers of Xeric"));
            assertTrue(((JButton) named(panel, "loot-detail-info")).getToolTipText().contains("Not a kill count"));
            assertTrue(named(panel, "loot-detail-body-moments").isVisible());
            assertTrue(named(panel, "loot-detail-body-rarest").isVisible());
            save(panel, "source-profile.png");
            JScrollPane scroll = (JScrollPane) panel.getComponent(0);
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()); layout(panel);
            save(panel, "source-profile-bottom.png");
            tab(panel, "Journal").doClick(); layout(panel);
            assertFalse(named(panel, "loot-detail").isVisible());
            assertFalse(named(panel, "loot-recap").isVisible());
            JComboBox sourceFilter = combos(panel).stream().filter(c -> c.getItemCount() > 0
                && LootPopPanel.ALL_SOURCES.equals(c.getItemAt(0))).findFirst().get();
            sourceFilter.setSelectedItem("Chambers of Xeric");
            JButton profile = (JButton) named(panel, "loot-source-profile"); assertTrue(profile.isVisible());
            profile.doClick(); layout(panel); assertTrue(named(panel, "loot-detail").isVisible());
            assertEquals(620, ((JScrollPane) panel.getComponent(0)).getHeight());
        });
    }

    @Test public void emptyProfilesAndAllUnknownPricesAreExplicit() throws Exception
    {
        List<LootHistoryEntry> history = samples(); LootSessionRecap recap = new LootSessionRecap();
        recap.record(LootInsightsTest.entry("New boss", 100, PriceMode.GRAND_EXCHANGE,
            LootInsightsTest.item(1, "New item", 0, null).withPriceState(true, true)));
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake(history, recap), new LootPopConfig() {}, null);
            ((JButton) named(panel, "loot-recap")).doClick();
            assertTrue(hasText(named(panel, "loot-detail"), "Price pending"));
            assertTrue(hasText(named(panel, "loot-detail"), "Waiting for a fully priced drop"));
            ((JButton) named(panel, "loot-detail-back")).doClick();
            JComboBox source = combos(panel).stream().filter(c -> c.getItemCount() > 0
                && LootPopPanel.ALL_SOURCES.equals(c.getItemAt(0))).findFirst().get();
            source.setSelectedItem("Chambers of Xeric"); history.clear();
            ((JButton) named(panel, "loot-source-profile")).doClick();
            assertTrue(hasText(named(panel, "loot-detail"), "No saved drops"));
        });
    }

    @Test public void sourceClickUsesTheDropsPriceBasisRatherThanTheCurrentConfig() throws Exception
    {
        List<LootHistoryEntry> history = samples(); java.util.Collections.reverse(history);
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake(history, new LootSessionRecap()), new LootPopConfig() {}, null);
            JLabel source = (JLabel) named(panel, "loot-source-link");
            java.awt.event.MouseEvent event = new java.awt.event.MouseEvent(source,
                java.awt.event.MouseEvent.MOUSE_CLICKED, 1, 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1);
            for (java.awt.event.MouseListener listener : source.getMouseListeners()) { listener.mouseClicked(event); }
            assertEquals(PriceMode.HIGH_ALCHEMY, ((JComboBox<?>) named(panel, "loot-detail-price")).getSelectedItem());
            assertTrue(hasText(named(panel, "loot-detail"), "2.4M gp"));
        });
    }

    @Test public void liveRefreshKeepsDetailPageAndScrollPosition() throws Exception
    {
        List<LootHistoryEntry> history = samples(); LootSessionRecap recap = new LootSessionRecap(); history.forEach(recap::record);
        LootPopPanel[] panels = new LootPopPanel[1];
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake(history, recap), new LootPopConfig() {}, null); panels[0] = panel;
            panel.setSize(242, 450); ((JButton) named(panel, "loot-recap")).doClick(); layout(panel);
        });
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = panels[0]; JScrollPane scroll = (JScrollPane) panel.getComponent(0);
            scroll.getVerticalScrollBar().setValue(150); layout(panel);
            int position = scroll.getVerticalScrollBar().getValue(); assertTrue(position > 0);
            panel.refresh(); layout(panel);
            assertTrue(named(panel, "loot-detail").isVisible());
            assertTrue("Detail sections remain visible during live refresh",
                named(panel, "loot-detail-body-top-item").isVisible());
            assertEquals(position, scroll.getVerticalScrollBar().getValue());
            assertTrue(hasText(named(panel, "loot-detail"), "SESSION RECAP"));
        });
    }

    private static void save(LootPopPanel panel, String filename)
    {
        try
        {
            BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
            Path file = Path.of("build", "insights-preview", filename); Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private static void layout(Container parent)
    { parent.doLayout(); for (Component c : parent.getComponents()) { if (c instanceof Container) { layout((Container) c); } } }

    private static Component named(Container parent, String name)
    {
        for (Component c : parent.getComponents())
        {
            if (name.equals(c.getName())) { return c; }
            if (c instanceof Container) { Component found = named((Container) c, name); if (found != null) { return found; } }
        }
        return null;
    }

    private static boolean hasText(Component component, String text)
    {
        if (component instanceof JLabel && ((JLabel) component).getText() != null
            && ((JLabel) component).getText().contains(text)) { return true; }
        if (component instanceof Container)
        { for (Component child : ((Container) component).getComponents()) { if (hasText(child, text)) { return true; } } }
        return false;
    }

    private static JToggleButton tab(Container parent, String label)
    {
        for (Component c : parent.getComponents())
        {
            if (c instanceof JToggleButton && label.equals(((JToggleButton) c).getText())) { return (JToggleButton) c; }
            if (c instanceof Container) { JToggleButton found = tab((Container) c, label); if (found != null) { return found; } }
        }
        return null;
    }

    private static List<JComboBox> combos(Container parent)
    {
        List<JComboBox> found = new ArrayList<>();
        for (Component c : parent.getComponents())
        { if (c instanceof JComboBox) { found.add((JComboBox) c); } else if (c instanceof Container) { found.addAll(combos((Container) c)); } }
        return found;
    }
}
