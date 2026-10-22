package com.lootpop;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class CleanSidebarTest
{
    private static LootPopPlugin fake(String notice, boolean pending)
    {
        long now = System.currentTimeMillis();
        LootHistoryItem reward = new LootHistoryItem(1, "Ancient blade", 1200000, 120000, 1200000,
            "1 / 512", DropRarity.RARE, SealedDropRendererTest.sword(java.awt.Color.CYAN)).withItemId(1);
        LootHistoryEntry best = LootInsightsTest.entry("Chambers of Xeric (Challenge Mode)", now, PriceMode.GRAND_EXCHANGE, reward);
        LootHistoryEntry common = LootInsightsTest.entry("Goblin", now, PriceMode.GRAND_EXCHANGE,
            new LootHistoryItem(20, "Coins", 1, 0, 20, null, DropRarity.COMMON, SealedDropRendererTest.coins()).withItemId(995));
        List<LootHistoryEntry> entries = Arrays.asList(best, common);
        return new LootPopPlugin()
        {
            @Override List<LootHistoryEntry> getJournalEntries() { return entries; }
            @Override synchronized List<LootHistoryEntry> getLootHistory() { return entries; }
            @Override synchronized List<String> getSessionSources() { return Arrays.asList(best.getSourceName(), "Goblin"); }
            @Override synchronized SessionSnapshot getSessionSnapshot(String source)
            { return new SessionSnapshot(2, 1200020, best.getSourceName(), 1200000, pending ? 1 : 0); }
            @Override String getJournalStatus() { return "Stored on this device / this character only"; }
            @Override String getJournalNotice() { return notice; }
        };
    }

    @Test public void oneCalmSummaryRetainsAllSessionMetricsAndLongNamesCannotCoverPrices() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake("", false), new LootPopConfig() {}, null);
            panel.setSize(242, 620); layout(panel);
            assertNotNull(named(panel, "loot-session-summary"));
            assertTrue(labels(named(panel, "loot-session-summary")).contains("Loot value"));
            assertTrue(labels(named(panel, "loot-session-summary")).contains("Drops"));
            assertTrue(labels(named(panel, "loot-session-summary")).contains("Average"));
            assertTrue(labels(named(panel, "loot-session-summary")).contains("Best"));
            JLabel source = (JLabel) named(panel, "loot-source-link");
            JLabel value = (JLabel) named(panel, "loot-drop-value");
            java.awt.Point sourceRight = SwingUtilities.convertPoint(source, source.getWidth(), 0, panel);
            java.awt.Point valueLeft = SwingUtilities.convertPoint(value, 0, 0, panel);
            assertTrue("Long source names must elide before, not underneath, the value", sourceRight.x < valueLeft.x);
            assertEquals(620, ((JScrollPane) panel.getComponent(0)).getHeight());
            assertEquals(0, panel.getPreferredSize().height); save(panel, "session-clean.png");
        });
    }

    @Test public void journalFiltersAreInitiallyClosedButPersistWhenOpenedAndRefreshed() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake("", false), new LootPopConfig() {}, null);
            panel.setSize(242, 620); tab(panel, "Journal").doClick(); layout(panel);
            assertFalse(named(panel, "loot-journal-filters").isVisible());
            assertFalse(named(panel, "loot-journal-notice").isVisible());
            JButton info = (JButton) named(panel, "loot-journal-info");
            assertTrue(info.getToolTipText().contains("history retention"));
            assertTrue(info.isFocusable()); save(panel, "journal-clean.png");
            JToggleButton toggle = (JToggleButton) named(panel, "loot-journal-filters-toggle");
            assertEquals("Filters", toggle.getText());
            assertEquals(java.awt.Font.BOLD, toggle.getFont().getStyle());
            assertTrue(toggle.getBorder() instanceof javax.swing.border.CompoundBorder);
            javax.swing.border.Border outline = ((javax.swing.border.CompoundBorder) toggle.getBorder()).getOutsideBorder();
            assertTrue(outline instanceof javax.swing.border.LineBorder);
            assertEquals(1, ((javax.swing.border.LineBorder) outline).getThickness());
            toggle.doClick(); layout(panel); assertTrue(named(panel, "loot-journal-filters").isVisible());
            panel.refresh(); layout(panel);
            assertTrue(toggle.isSelected()); assertTrue(named(panel, "loot-journal-filters").isVisible());
            save(panel, "journal-filters.png"); toggle.doClick();
            assertFalse(named(panel, "loot-journal-filters").isVisible());
        });
    }

    @Test public void savingFailuresStayVisibleInsteadOfBeingHiddenInInformation() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake("Journal save failed. Check local folder permissions.", false),
                new LootPopConfig() {}, null);
            tab(panel, "Journal").doClick();
            JLabel warning = (JLabel) named(panel, "loot-journal-notice");
            assertTrue(warning.isVisible()); assertTrue(warning.getText().contains("save failed"));
        });
    }

    @Test public void pendingPricesStayExplicitInTheCleanSessionSummary() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake("", true), new LootPopConfig() {}, null);
            String text = labels(named(panel, "loot-session-summary"));
            assertTrue(text.contains("Pending")); assertTrue(text.contains("+ gp"));
        });
    }

    @Test public void lootValueCaptionAndNumberAreCenteredHorizontallyWithoutChangingVerticalSpacing() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake("", false), new LootPopConfig() {}, null);
            for (int width : new int[]{228, 242, 300})
            {
                panel.setSize(width, 620); layout(panel);
                Component summary = named(panel, "loot-session-summary");
                JLabel caption = (JLabel) named(panel, "loot-value-caption");
                JLabel value = (JLabel) named(panel, "loot-value-total");
                for (JLabel label : new JLabel[]{caption, value})
                {
                    assertEquals(javax.swing.SwingConstants.CENTER, label.getHorizontalAlignment());
                    java.awt.Point center = SwingUtilities.convertPoint(label, label.getWidth() / 2, 0, summary);
                    assertEquals(summary.getWidth() / 2.0, center.x, 1.0);
                }
                assertEquals(caption.getY() + caption.getHeight() + 4, value.getY());
            }
        });
    }

    private static String labels(Component component)
    {
        StringBuilder text = new StringBuilder();
        if (component instanceof JLabel) { text.append(((JLabel) component).getText()).append('|'); }
        if (component instanceof Container)
        { for (Component c : ((Container) component).getComponents()) { text.append(labels(c)); } }
        return text.toString();
    }

    private static Component named(Container parent, String name)
    {
        for (Component c : parent.getComponents())
        {
            if (name.equals(c.getName())) { return c; }
            if (c instanceof Container) { Component found = named((Container) c, name); if (found != null) { return found; } }
        }
        return null;
    }

    private static JToggleButton tab(Container parent, String title)
    {
        for (Component c : parent.getComponents())
        {
            if (c instanceof JToggleButton && title.equals(((JToggleButton) c).getText())) { return (JToggleButton) c; }
            if (c instanceof Container) { JToggleButton found = tab((Container) c, title); if (found != null) { return found; } }
        }
        return null;
    }

    private static void layout(Container parent)
    { parent.doLayout(); for (Component c : parent.getComponents()) { if (c instanceof Container) { layout((Container) c); } } }

    private static void save(LootPopPanel panel, String name)
    {
        try
        {
            BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
            Path file = Path.of("build", "sidebar-preview", name); Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
}
