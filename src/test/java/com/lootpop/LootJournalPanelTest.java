package com.lootpop;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootJournalPanelTest
{
    @Test public void dateJumpBringsAnOlderDayIntoViewAndReopeningReturnsToNewest() throws Exception
    {
        long now = System.currentTimeMillis();
        LootHistoryItem coins = new LootHistoryItem(20, "Coins", 1, 0, 20,
            null, DropRarity.COMMON, SealedDropRendererTest.coins()).withItemId(995);
        List<LootHistoryEntry> history = new java.util.ArrayList<>();
        for (int day = 0; day < 40; day++)
        { history.add(new LootHistoryEntry("Goblin", 20, now - day * 86400000L, DropRarity.COMMON, Collections.singletonList(coins))); }
        LootPopPlugin fake = new LootPopPlugin()
        {
            @Override List<LootHistoryEntry> getJournalEntries() { return history; }
            @Override String getJournalStatus() { return "Navigation preview"; }
            @Override synchronized SessionSnapshot getSessionSnapshot(String source) { return new SessionSnapshot(0, 0, null, 0); }
        };
        LootPopPanel[] panels = new LootPopPanel[1];
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake, new LootPopConfig() {}, null); panels[0] = panel;
            findTab(panel, "Journal").doClick(); panel.setSize(242, 500); layout(panel); layout(panel);
            javax.swing.JComboBox<?> jump = (javax.swing.JComboBox<?>) named(panel, "journal-date-jump");
            final int[] changes = {0};
            jump.getModel().addListDataListener(new javax.swing.event.ListDataListener()
            {
                @Override public void intervalAdded(javax.swing.event.ListDataEvent e) { changes[0]++; }
                @Override public void intervalRemoved(javax.swing.event.ListDataEvent e) { changes[0]++; }
                @Override public void contentsChanged(javax.swing.event.ListDataEvent e) { changes[0]++; }
            });
            panel.refresh(); assertEquals("Live refresh must not rebuild an unchanged date picker", 0, changes[0]);
            jump.setSelectedIndex(35); layout(panel); layout(panel);
        });
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = panels[0];
            javax.swing.JScrollPane scroll = findScroll(panel);
            assertTrue(scroll.getViewport().getViewPosition().y > 0);
            List<JToggleButton> dates = new java.util.ArrayList<>(); findDates(panel, dates);
            assertEquals("Jumping to a date must keep the newest one open", 2,
                dates.stream().filter(JToggleButton::isSelected).count());
            JToggleButton selected = dates.get(34);
            assertTrue(selected.isSelected());
            java.awt.Rectangle bounds = SwingUtilities.convertRectangle(selected.getParent(), selected.getBounds(), scroll.getViewport().getView());
            assertTrue(scroll.getViewport().getViewRect().intersects(bounds));
            findTab(panel, "Session").doClick(); findTab(panel, "Journal").doClick(); layout(panel); layout(panel);
        });
        SwingUtilities.invokeAndWait(() ->
        {
            assertEquals(0, findScroll(panels[0]).getViewport().getViewPosition().y);
            List<JToggleButton> dates = new java.util.ArrayList<>(); findDates(panels[0], dates);
            assertTrue(dates.get(0).isSelected());
        });
    }

    @Test public void longJournalScrollsInsideThePanelAndDatesOpenIndependently() throws Exception
    {
        long now = System.currentTimeMillis();
        LootHistoryItem coins = new LootHistoryItem(20, "Coins", 1, 0, 20,
            null, DropRarity.COMMON, SealedDropRendererTest.coins()).withItemId(995);
        List<LootHistoryItem> rewards = new java.util.ArrayList<>(); rewards.add(coins);
        for (int i = 1; i <= 6; i++)
        {
            rewards.add(new LootHistoryItem(1, "Reward " + i, i, i, i, "1 / 128", DropRarity.COMMON,
                SealedDropRendererTest.sword(new Color(140 + i * 12, 180, 200))).withItemId(i));
        }
        List<LootHistoryEntry> history = new java.util.ArrayList<>();
        for (int i = 0; i < 180; i++)
        {
            history.add(new LootHistoryEntry(i % 2 == 0 ? "Goblin" : "Outlaw", 41, now - (i / 60) * 86400000L,
                DropRarity.COMMON, rewards));
        }
        LootPopPlugin fake = new LootPopPlugin()
        {
            @Override List<LootHistoryEntry> getJournalEntries() { return history; }
            @Override String getJournalStatus() { return "Stored on this device / this character only"; }
            @Override synchronized SessionSnapshot getSessionSnapshot(String source)
            { return new SessionSnapshot(0, 0, null, 0); }
        };
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = new LootPopPanel(fake, new LootPopConfig() {}, null);
            findTab(panel, "Journal").doClick(); panel.setSize(242, 620); layout(panel); layout(panel);
            javax.swing.JScrollPane scroll = findScroll(panel);
            assertNotNull(scroll);
            assertEquals(0, panel.getPreferredSize().height);
            assertEquals(0, panel.getMinimumSize().height);
            assertEquals(620, scroll.getHeight());
            javax.swing.JLabel overflow = (javax.swing.JLabel) named(panel, "loot-overflow");
            assertNotNull(overflow);
            javax.swing.JToolTip itemTip = overflow.createToolTip();
            assertTrue(itemTip.getPreferredSize().width <= 280);
            assertTrue(itemTip.getPreferredSize().height > 40);
            javax.swing.JButton settings = (javax.swing.JButton) named(panel, "loot-settings");
            javax.swing.JButton clear = (javax.swing.JButton) named(panel, "loot-reset");
            assertEquals(new java.awt.Dimension(36, 30), settings.getSize());
            assertEquals(clear.getHeight(), settings.getHeight());
            assertTrue(settings.getWidth() < clear.getWidth());
            assertEquals(20, settings.getIcon().getIconWidth());
            assertEquals(javax.swing.SwingConstants.CENTER, settings.getHorizontalAlignment());
            assertEquals("Separate", JournalLayout.INDIVIDUAL.toString());
            assertEquals("Grouped", JournalLayout.DAILY.toString());
            settings.doClick(); layout(panel); layout(panel);
            Component controls = named(panel, "popup-controls");
            int controlsTop = SwingUtilities.convertPoint(controls, 0, 0, panel).y;
            int settingsBottom = SwingUtilities.convertPoint(settings, 0, settings.getHeight(), panel).y;
            assertTrue("Settings card must have an external top gap", controlsTop - settingsBottom >= 10);
            savePanel(panel, "settings-spacing.png");
            settings.doClick(); layout(panel); layout(panel);
            assertTrue(scroll.getVerticalScrollBar().isVisible());
            assertTrue(scroll.getVerticalScrollBar().getMaximum() > scroll.getViewport().getHeight());
            scroll.getVerticalScrollBar().setValue(200); layout(panel);
            assertTrue(scroll.getViewport().getViewPosition().y > 0);
            assertEquals(620, panel.getHeight());
            scroll.getVerticalScrollBar().setValue(0);
            panel.setSize(242, 450); layout(panel); layout(panel);
            assertEquals(450, scroll.getHeight());
            panel.setSize(242, 620); layout(panel); layout(panel);
            savePanel(panel, "journal-scroll.png");
            List<JToggleButton> dates = new java.util.ArrayList<>(); findDates(panel, dates);
            assertEquals(3, dates.size()); assertTrue(dates.get(0).isSelected());
            assertFalse(dates.get(1).isSelected());
            dates.get(1).doClick(); dates.clear(); findDates(panel, dates);
            assertTrue(dates.get(0).isSelected()); assertTrue(dates.get(1).isSelected());
            assertEquals(2, dates.stream().filter(JToggleButton::isSelected).count());
            dates.get(0).doClick(); dates.clear(); findDates(panel, dates);
            assertFalse(dates.get(0).isSelected()); assertTrue(dates.get(1).isSelected());
            dates.get(1).doClick(); dates.clear(); findDates(panel, dates);
            assertEquals(0, dates.stream().filter(JToggleButton::isSelected).count());
            panel.refresh(); dates.clear(); findDates(panel, dates);
            assertEquals("A live refresh must not reopen manually collapsed dates", 0,
                dates.stream().filter(JToggleButton::isSelected).count());
            dates.get(0).doClick();
            findLayout(panel).setSelectedItem(JournalLayout.DAILY); layout(panel); layout(panel);
            assertTrue(hasLabel(panel, "60 drops"));
            assertTrue(hasLabel(panel, LootPopOverlay.formatGp(2460)));
            assertTrue(hasLabel(panel, "2 sources \u00b7 GE at drop time"));
            savePanel(panel, "daily-totals.png");
            javax.swing.JComboBox<?> source = findSource(panel);
            assertEquals(java.awt.Font.SANS_SERIF, source.getFont().getFamily());
            assertTrue(source.getFont().getSize() >= 12);
            source.setSelectedItem("Goblin");
            assertTrue(hasLabel(panel, "30 drops"));
            assertTrue(hasLabel(panel, LootPopOverlay.formatGp(1230)));
            assertTrue(hasLabel(panel, "1 source \u00b7 GE at drop time"));
            source.setSelectedItem(LootPopPanel.ALL_SOURCES);
            dates.clear(); findDates(panel, dates); dates.get(1).doClick();
            panel.refresh(); dates.clear(); findDates(panel, dates);
            assertTrue("Live refresh must not interrupt browsing an older date", dates.get(1).isSelected());
            assertTrue("Opening an older date does not close the newest", dates.get(0).isSelected());
            findTab(panel, "Session").doClick(); findTab(panel, "Journal").doClick();
            dates.clear(); findDates(panel, dates);
            assertTrue("Entering Journal opens the newest date", dates.get(0).isSelected());
            dates.get(1).doClick();
            history.add(new LootHistoryEntry("Goblin", 20, now + 86400000L,
                DropRarity.COMMON, Collections.singletonList(coins)));
            panel.refresh(); layout(panel); layout(panel);
            dates.clear(); findDates(panel, dates);
            assertEquals(4, dates.size());
            assertTrue("A new day becomes the default open date", dates.get(0).isSelected());
            assertEquals("A new date must not collapse existing open dates", 3,
                dates.stream().filter(JToggleButton::isSelected).count());
            assertEquals(java.awt.Cursor.HAND_CURSOR, dates.get(1).getCursor().getType());
            assertTrue(dates.get(1).getToolTipText().startsWith("Click to collapse"));
            assertTrue(dates.get(3).getToolTipText().startsWith("Click to expand"));
            javax.swing.JComboBox<?> jump = (javax.swing.JComboBox<?>) named(panel, "journal-date-jump");
            assertEquals(5, jump.getItemCount());
            savePanel(panel, "timeline-navigation.png");
            jump.setSelectedIndex(3);
            dates.clear(); findDates(panel, dates);
            assertTrue("Date picker directly opens the chosen older date", dates.get(2).isSelected());
            assertEquals(3, dates.stream().filter(JToggleButton::isSelected).count());
        });
    }

    private static void savePanel(LootPopPanel panel, String name)
    {
        try
        {
            BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
            Path file = Path.of("build", "journal-preview", name);
            Files.createDirectories(file.getParent()); ImageIO.write(image, "png", file.toFile());
        }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private static javax.swing.JScrollPane findScroll(Container parent)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof javax.swing.JScrollPane) { return (javax.swing.JScrollPane) child; }
            if (child instanceof Container)
            { javax.swing.JScrollPane result = findScroll((Container) child); if (result != null) { return result; } }
        }
        return null;
    }

    private static Component named(Container parent, String name)
    {
        for (Component component : parent.getComponents())
        {
            if (name.equals(component.getName())) { return component; }
            if (component instanceof Container)
            { Component result = named((Container) component, name); if (result != null) { return result; } }
        }
        return null;
    }

    private static void findDates(Container parent, List<JToggleButton> dates)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JToggleButton && child.getName() != null && child.getName().startsWith("journal-date-"))
            { dates.add((JToggleButton) child); }
            if (child instanceof Container) { findDates((Container) child, dates); }
        }
    }

    private static javax.swing.JComboBox<?> findLayout(Container parent)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>) child).getItemAt(0) instanceof JournalLayout)
            { return (javax.swing.JComboBox<?>) child; }
            if (child instanceof Container)
            { javax.swing.JComboBox<?> result = findLayout((Container) child); if (result != null) { return result; } }
        }
        return null;
    }

    private static javax.swing.JComboBox<?> findSource(Container parent)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof javax.swing.JComboBox)
            {
                javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) child;
                if (combo.getItemCount() > 0 && LootPopPanel.ALL_SOURCES.equals(combo.getItemAt(0))) { return combo; }
            }
            if (child instanceof Container)
            { javax.swing.JComboBox<?> result = findSource((Container) child); if (result != null) { return result; } }
        }
        return null;
    }

    @Test
    public void rendersActualJournalPanelAndSwitchesViewsWithoutClientOrNetwork() throws Exception
    {
        long now = System.currentTimeMillis();
        LootHistoryItem rare = new LootHistoryItem(1, "Ancient blade", 24000000, 120000, 24000000,
            "1 / 5,000", DropRarity.ULTRA_RARE, SealedDropRendererTest.sword(new Color(227, 198, 99))).withItemId(1);
        LootHistoryItem common = new LootHistoryItem(2500, "Coins", 1, 0, 2500,
            null, DropRarity.UNKNOWN, SealedDropRendererTest.coins()).withItemId(995);
        LootHistoryEntry record = new LootHistoryEntry("Phantom Muspah", 24002500, now,
            DropRarity.ULTRA_RARE, Arrays.asList(rare, common));
        record.setPreviousBest(4200000); record.setCollectionLogUnlock(true); record.setSourceBest(true);
        rare.setCollectionLogItem(true);
        LootHistoryEntry second = new LootHistoryEntry("Vorkath", 165000, now - 86400000,
            DropRarity.RARE, Collections.singletonList(new LootHistoryItem(3, "Rune blade", 55000, 30000,
                165000, "1 / 512", DropRarity.RARE,
                SealedDropRendererTest.sword(new Color(137, 203, 210))).withItemId(2)));
        LootHistoryEntry third = new LootHistoryEntry("Phantom Muspah", 2500, now - 172800000,
            DropRarity.COMMON, Collections.singletonList(common));
        second.setSourceBest(true);
        LootHistoryItem unknown = new LootHistoryItem(1, "Unpriced relic", 0, 120000, 0,
            "1 / 5,000", DropRarity.ULTRA_RARE, SealedDropRendererTest.sword(new Color(137, 203, 210)))
            .withItemId(3).withPriceState(true, true);
        unknown.setCollectionLogItem(true);
        LootHistoryEntry unpriced = new LootHistoryEntry("New creature", 0, now - 3600000,
            DropRarity.ULTRA_RARE, Collections.singletonList(unknown));
        unpriced.setCollectionLogUnlock(true);
        List<LootHistoryEntry> entries = Arrays.asList(record, unpriced, second, third);
        assertSame(unknown, LootPopPanel.trophyHero(unpriced, 0));
        LootPopPlugin fake = new LootPopPlugin()
        {
            @Override List<LootHistoryEntry> getJournalEntries() { return entries; }
            @Override List<LootHistoryEntry> getTrophyEntries() { return Arrays.asList(record, unpriced, second); }
            @Override String getJournalStatus() { return "UI preview / illustrative items"; }
            @Override boolean canClearJournal() { return true; }
            @Override synchronized SessionSnapshot getSessionSnapshot(String source)
            { return new SessionSnapshot(4, 24170000, "Phantom Muspah", 24002500, 1); }
            @Override synchronized List<LootHistoryEntry> getLootHistory() { return entries; }
        };
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                LootPopPanel panel = new LootPopPanel(fake, new LootPopConfig() {}, null);
                findTab(panel, "Journal").doClick();
                panel.setSize(242, 850);
                layout(panel);
                BufferedImage image = new BufferedImage(242, 850, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
                Path file = Path.of("build", "journal-preview", "loot-journal.png");
                Files.createDirectories(file.getParent()); ImageIO.write(image, "png", file.toFile());
                assertTrue(findTab(panel, "Journal").isSelected());
                findView(panel).setSelectedItem("Trophy Shelf");
                panel.setSize(242, 1000); layout(panel);
                BufferedImage shelf = new BufferedImage(242, 1000, BufferedImage.TYPE_INT_RGB);
                Graphics2D shelfGraphics = shelf.createGraphics(); panel.printAll(shelfGraphics); shelfGraphics.dispose();
                ImageIO.write(shelf, "png", file.resolveSibling("trophy-shelf.png").toFile());
                assertTrue(hasLabel(panel, "Price pending"));
                findTab(panel, "Session").doClick();
                assertTrue(findTab(panel, "Session").isSelected());
                assertTrue(hasLabel(panel, "Pending"));
                panel.setSize(242, 900); layout(panel);
                BufferedImage pending = new BufferedImage(242, 900, BufferedImage.TYPE_INT_RGB);
                Graphics2D pg = pending.createGraphics(); panel.printAll(pg); pg.dispose();
                ImageIO.write(pending, "png", file.resolveSibling("price-pending.png").toFile());
            }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }

    private static JToggleButton findTab(Container parent, String name)
    {
        for (Component component : parent.getComponents())
        {
            if (component instanceof JToggleButton && ((JToggleButton) component).getText().equals(name))
            { return (JToggleButton) component; }
            if (component instanceof Container)
            {
                JToggleButton result = findTab((Container) component, name);
                if (result != null) { return result; }
            }
        }
        return null;
    }

    private static javax.swing.JComboBox<?> findView(Container parent)
    {
        for (Component component : parent.getComponents())
        {
            if (component instanceof javax.swing.JComboBox)
            {
                javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) component;
                for (int i = 0; i < combo.getItemCount(); i++)
                { if ("Trophy Shelf".equals(combo.getItemAt(i))) { return combo; } }
            }
            if (component instanceof Container)
            { javax.swing.JComboBox<?> result = findView((Container) component); if (result != null) { return result; } }
        }
        return null;
    }

    private static boolean hasLabel(Container parent, String text)
    {
        for (Component component : parent.getComponents())
        {
            if (component instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) component).getText())) { return true; }
            if (component instanceof Container && hasLabel((Container) component, text)) { return true; }
        }
        return false;
    }

    private static void layout(Container component)
    {
        invalidateTree(component);
        layoutTree(component);
    }

    private static void invalidateTree(Container component)
    {
        for (Component child : component.getComponents())
        { if (child instanceof Container) { invalidateTree((Container) child); } }
        component.invalidate();
    }

    private static void layoutTree(Container component)
    {
        component.doLayout();
        for (Component child : component.getComponents())
        { if (child instanceof Container) { layoutTree((Container) child); } }
    }
}
