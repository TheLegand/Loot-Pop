package com.lootpop;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelQolTest
{
    private static final class RecordingActions implements LootPanelActions
    {
        boolean confirm;
        int prompts, promptDrops;
        Runnable duringConfirmation = () -> { };
        final List<String> copied = new ArrayList<>();
        final List<String> opened = new ArrayList<>();

        @Override public boolean confirmSessionReset(Component owner, int drops)
        { prompts++; promptDrops = drops; duringConfirmation.run(); return confirm; }
        @Override public void copyName(Component owner, String name) { copied.add(name); }
        @Override public void openWiki(String name) { opened.add(name); }
    }

    private static final class FakePlugin extends LootPopPlugin
    {
        int drops = 3, clears, journalClears;
        final List<LootHistoryEntry> saved;
        final List<LootHistoryEntry> live;

        FakePlugin() { this(PriceMode.GRAND_EXCHANGE); }
        FakePlugin(PriceMode mode)
        {
            LootHistoryItem item = new LootHistoryItem(1, "Goblin mail", 173, 60, 173, "1 / 128",
                DropRarity.UNCOMMON, SealedDropRendererTest.coins()).withItemId(1);
            saved = Collections.singletonList(LootInsightsTest.entry("Boss & friends", System.currentTimeMillis(), mode, item));
            live = new ArrayList<>(saved);
        }

        @Override synchronized SessionSnapshot getSessionSnapshot() { return new SessionSnapshot(drops, 173, "Boss & friends", 173); }
        @Override synchronized SessionSnapshot getSessionSnapshot(String source)
        { return "Empty source".equals(source) ? new SessionSnapshot(0, 0, "", 0) : getSessionSnapshot(); }
        @Override synchronized List<String> getSessionSources() { return Arrays.asList("Boss & friends", "Empty source"); }
        @Override synchronized List<LootHistoryEntry> getLootHistory() { return new ArrayList<>(live); }
        @Override List<LootHistoryEntry> getJournalEntries() { return saved; }
        @Override String getJournalStatus() { return "Stored on this device / this character only"; }
        @Override String getJournalNotice() { return ""; }
        @Override synchronized void clearSession() { clears++; drops = 0; live.clear(); super.clearSession(); }
        @Override void clearJournal() { journalClears++; }
    }

    @Test public void cancellingNonemptySessionResetKeepsAllLiveAndSavedData() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            FakePlugin plugin = new FakePlugin(); RecordingActions actions = new RecordingActions();
            LootPopPanel panel = panel(plugin, actions);
            button(panel, "loot-reset").doClick();
            assertEquals(1, actions.prompts); assertEquals(3, actions.promptDrops);
            assertEquals(0, plugin.clears); assertEquals(3, plugin.drops); assertEquals(1, plugin.live.size());
            assertEquals(0, plugin.journalClears); assertEquals(1, plugin.saved.size());
        });
    }

    @Test public void confirmingResetUsesAllSourcesRatherThanTheFilteredEmptySource() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            FakePlugin plugin = new FakePlugin(); RecordingActions actions = new RecordingActions(); actions.confirm = true;
            LootPopPanel panel = panel(plugin, actions); sourceCombo(panel).setSelectedItem("Empty source");
            button(panel, "loot-reset").doClick();
            assertEquals(1, actions.prompts); assertEquals(3, actions.promptDrops);
            assertEquals(1, plugin.clears); assertEquals(0, plugin.drops); assertTrue(plugin.live.isEmpty());
            assertEquals(0, plugin.journalClears); assertEquals(1, plugin.saved.size());
        });
    }

    @Test public void emptySessionResetDoesNotOpenAConfirmation() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            FakePlugin plugin = new FakePlugin(); plugin.drops = 0; plugin.live.clear();
            RecordingActions actions = new RecordingActions(); LootPopPanel panel = panel(plugin, actions);
            button(panel, "loot-reset").doClick(); assertEquals(0, actions.prompts); assertEquals(1, plugin.clears);
        });
    }

    @Test public void oldConfirmationCannotResetANewSessionAfterCharacterOrSessionChange() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            FakePlugin plugin = new FakePlugin(); RecordingActions actions = new RecordingActions(); actions.confirm = true;
            actions.duringConfirmation = () -> { plugin.clearSession(); plugin.drops = 1; };
            LootPopPanel panel = panel(plugin, actions); button(panel, "loot-reset").doClick();
            assertEquals(1, actions.prompts); assertEquals("Only the simulated profile change may reset", 1, plugin.clears);
            assertEquals(1, plugin.drops);
        });
    }

    @Test public void itemContextMenuCopiesOnlyOnCommandAndPreservesLeftClickWiki() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            RecordingActions actions = new RecordingActions(); LootPopPanel panel = panel(new FakePlugin(), actions);
            JComponent item = (JComponent) named(panel, "loot-item-link"); JPopupMenu menu = item.getComponentPopupMenu();
            assertNotNull(menu); assertEquals(2, menu.getComponentCount());
            assertEquals("Copy name", ((JMenuItem) menu.getComponent(0)).getText());
            assertTrue(actions.copied.isEmpty()); assertTrue(actions.opened.isEmpty());
            mouseClick(item, MouseEvent.BUTTON3); assertTrue(actions.copied.isEmpty()); assertTrue(actions.opened.isEmpty());
            ((JMenuItem) menu.getComponent(0)).doClick(); assertEquals(Collections.singletonList("Goblin mail"), actions.copied);
            assertTrue(actions.opened.isEmpty());
            ((JMenuItem) menu.getComponent(1)).doClick(); mouseClick(item, MouseEvent.BUTTON1);
            assertEquals(Arrays.asList("Goblin mail", "Goblin mail"), actions.opened);
            for (Component child : menu.getComponents())
            { assertEquals(java.awt.Font.SANS_SERIF, child.getFont().getFamily()); assertTrue(child.getFont().getSize() >= 12); }
        });
    }

    @Test public void sourceContextMenuOpensMatchingPriceProfileOrWikiOnlyAsRequested() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            RecordingActions actions = new RecordingActions(); LootPopPanel panel = panel(new FakePlugin(PriceMode.HIGH_ALCHEMY), actions);
            JComponent source = (JComponent) named(panel, "loot-source-link"); JPopupMenu menu = source.getComponentPopupMenu();
            assertEquals("View profile", ((JMenuItem) menu.getComponent(0)).getText());
            mouseClick(source, MouseEvent.BUTTON3); assertFalse(named(panel, "loot-detail").isVisible());
            ((JMenuItem) menu.getComponent(0)).doClick(); assertTrue(named(panel, "loot-detail").isVisible());
            assertEquals(PriceMode.HIGH_ALCHEMY, ((JComboBox<?>) named(panel, "loot-detail-price")).getSelectedItem());
            assertTrue(actions.opened.isEmpty());
            ((JMenuItem) menu.getComponent(1)).doClick(); assertEquals(Collections.singletonList("Boss & friends"), actions.opened);
            button(panel, "loot-source-wiki").doClick();
            assertEquals(Arrays.asList("Boss & friends", "Boss & friends"), actions.opened);
            assertTrue(actions.copied.isEmpty());
        });
    }

    @Test public void escapeBindingIsPanelScopedAndOnlyClosesOneLayerPerPress() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            FakePlugin plugin = new FakePlugin(); LootPopPanel panel = panel(plugin, new RecordingActions());
            KeyStroke escape = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
            assertNull("Esc must never become a game-wide shortcut", panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(escape));
            assertEquals("loot-sidebar-escape", panel.getInputMap(JComponent.WHEN_FOCUSED).get(escape));
            assertEquals("loot-sidebar-escape", panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(escape));
            tab(panel, "Journal").doClick();
            JToggleButton filters = (JToggleButton) named(panel, "loot-journal-filters-toggle"); filters.doClick();
            JComponent source = (JComponent) named(panel, "loot-source-link");
            ((JMenuItem) source.getComponentPopupMenu().getComponent(0)).doClick();
            pressEscape(panel); assertFalse(named(panel, "loot-detail").isVisible()); assertTrue(filters.isSelected());
            pressEscape(panel); assertFalse(filters.isSelected()); assertFalse(named(panel, "loot-journal-filters").isVisible());
            button(panel, "loot-settings").doClick(); assertTrue(named(panel, "popup-controls").isVisible());
            pressEscape(panel); assertFalse(named(panel, "popup-controls").isVisible());
            assertEquals(java.awt.Color.WHITE, button(panel, "loot-settings").getForeground());
            pressEscape(panel); assertEquals(0, plugin.clears); assertEquals(0, plugin.journalClears);
        });
    }

    @Test public void escapeReturnsFromRecapWithoutResettingIt() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            FakePlugin plugin = new FakePlugin(); LootPopPanel panel = panel(plugin, new RecordingActions());
            button(panel, "loot-recap").doClick(); assertTrue(named(panel, "loot-detail").isVisible());
            pressEscape(panel); assertFalse(named(panel, "loot-detail").isVisible());
            assertEquals(3, plugin.drops); assertEquals(0, plugin.clears);
        });
    }

    @Test public void contextMenusRenderCompactlyWithoutOpeningNativeWindows() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            LootPopPanel panel = panel(new FakePlugin(), new RecordingActions());
            JPopupMenu item = ((JComponent) named(panel, "loot-item-link")).getComponentPopupMenu();
            JPopupMenu source = ((JComponent) named(panel, "loot-source-link")).getComponentPopupMenu();
            java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(300, 120, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = canvas.createGraphics(); graphics.setColor(new java.awt.Color(26, 28, 31));
            graphics.fillRect(0, 0, 300, 120); graphics.setColor(java.awt.Color.WHITE);
            graphics.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
            graphics.drawString("Item", 12, 19); graphics.drawString("Source", 155, 19);
            int x = 12;
            for (JPopupMenu menu : new JPopupMenu[]{item, source})
            {
                menu.setSize(menu.getPreferredSize()); menu.doLayout();
                assertTrue(menu.getWidth() < 140); assertTrue(menu.getHeight() < 90);
                java.awt.Graphics2D cell = (java.awt.Graphics2D) graphics.create(); cell.translate(x, 30);
                menu.printAll(cell); cell.dispose(); x = 155;
            }
            graphics.dispose();
            try
            {
                java.nio.file.Path file = java.nio.file.Path.of("build", "sidebar-preview", "context-menus.png");
                java.nio.file.Files.createDirectories(file.getParent()); javax.imageio.ImageIO.write(canvas, "png", file.toFile());
            }
            catch (java.io.IOException error) { throw new AssertionError(error); }
        });
    }

    private static LootPopPanel panel(FakePlugin plugin, RecordingActions actions)
    { return new LootPopPanel(plugin, new LootPopConfig() {}, null, actions); }

    private static void pressEscape(LootPopPanel panel)
    { panel.getActionMap().get("loot-sidebar-escape").actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "escape")); }

    private static void mouseClick(JComponent component, int button)
    {
        MouseEvent event = new MouseEvent(component, MouseEvent.MOUSE_CLICKED, 1, 0, 1, 1, 1, false, button);
        for (java.awt.event.MouseListener listener : component.getMouseListeners()) { listener.mouseClicked(event); }
    }

    private static JButton button(Container parent, String name) { return (JButton) named(parent, name); }

    private static Component named(Container parent, String name)
    {
        for (Component child : parent.getComponents())
        {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container) { Component result = named((Container) child, name); if (result != null) { return result; } }
        }
        return null;
    }

    private static JComboBox sourceCombo(Container parent)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JComboBox && ((JComboBox<?>) child).getItemCount() > 0
                && LootPopPanel.ALL_SOURCES.equals(((JComboBox<?>) child).getItemAt(0))) { return (JComboBox) child; }
            if (child instanceof Container) { JComboBox result = sourceCombo((Container) child); if (result != null) { return result; } }
        }
        return null;
    }

    private static JToggleButton tab(Container parent, String title)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JToggleButton && title.equals(((JToggleButton) child).getText())) { return (JToggleButton) child; }
            if (child instanceof Container) { JToggleButton result = tab((Container) child, title); if (result != null) { return result; } }
        }
        return null;
    }
}
