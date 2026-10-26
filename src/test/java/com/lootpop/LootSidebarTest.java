package com.lootpop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.NavigationButton;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootSidebarTest
{
    private static final class Options implements LootPopConfig
    {
        boolean visible;
        @Override public boolean showSidePanel() { return visible; }
    }

    @Test public void pluginSettingTakesEffectLiveWithoutResettingTheSession() throws Exception
    {
        LootPopPlugin plugin = new LootPopPlugin();
        Options config = new Options();
        java.lang.reflect.Field configField = LootPopPlugin.class.getDeclaredField("config");
        configField.setAccessible(true); configField.set(plugin, config);
        java.lang.reflect.Field drops = LootPopPlugin.class.getDeclaredField("sessionDrops");
        drops.setAccessible(true); drops.setInt(plugin, 7);
        List<NavigationButton> added = new ArrayList<>(), removed = new ArrayList<>();
        LootSidebar sidebar = new LootSidebar(() -> new LootPopPanel(plugin, config, null), added::add, removed::add);
        java.lang.reflect.Field sidebarField = LootPopPlugin.class.getDeclaredField("sidebar");
        sidebarField.setAccessible(true); sidebarField.set(plugin, sidebar);
        ConfigChanged event = new ConfigChanged(); event.setGroup(LootPopConfig.GROUP); event.setKey("showSidePanel");
        plugin.onConfigChanged(event);
        SwingUtilities.invokeAndWait(() -> { assertTrue(added.isEmpty()); assertNull(sidebar.panel()); });
        config.visible = true; plugin.onConfigChanged(event);
        SwingUtilities.invokeAndWait(() -> { assertEquals(1, added.size()); assertNotNull(sidebar.panel()); });
        config.visible = false; plugin.onConfigChanged(event);
        SwingUtilities.invokeAndWait(() -> { assertEquals(1, removed.size()); assertNull(sidebar.panel()); });
        config.visible = true; plugin.onConfigChanged(event);
        SwingUtilities.invokeAndWait(() ->
        {
            assertEquals(2, added.size()); assertEquals(7, plugin.getSessionSnapshot().getDrops());
            sidebar.setVisible(false);
        });
    }

    @Test public void visibilityOptionDefaultsOnAndPrecedesEverySection() throws Exception
    {
        assertTrue(new LootPopConfig() {}.showSidePanel());
        ConfigItem option = LootPopConfig.class.getMethod("showSidePanel").getAnnotation(ConfigItem.class);
        assertEquals("showSidePanel", option.keyName());
        assertEquals("", option.section());
        for (java.lang.reflect.Field field : LootPopConfig.class.getFields())
        {
            net.runelite.client.config.ConfigSection section = field.getAnnotation(net.runelite.client.config.ConfigSection.class);
            if (section != null) { assertTrue(option.position() < section.position()); }
        }
    }

    @Test public void queuedSettingUpdateCannotRecreateADetachedSidebar() throws Exception
    {
        LootPopPlugin plugin = new LootPopPlugin();
        Options config = new Options(); config.visible = true;
        java.lang.reflect.Field configField = LootPopPlugin.class.getDeclaredField("config");
        configField.setAccessible(true); configField.set(plugin, config);
        AtomicInteger created = new AtomicInteger();
        LootSidebar sidebar = new LootSidebar(() ->
        {
            created.incrementAndGet(); return new LootPopPanel(plugin, config, null);
        }, button -> fail("Detached sidebar must not be added"), button -> {});
        java.lang.reflect.Field sidebarField = LootPopPlugin.class.getDeclaredField("sidebar");
        sidebarField.setAccessible(true);
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                sidebarField.set(plugin, sidebar);
                ConfigChanged event = new ConfigChanged();
                event.setGroup(LootPopConfig.GROUP); event.setKey("showSidePanel");
                plugin.onConfigChanged(event);
                sidebarField.set(plugin, null);
            }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        });
        SwingUtilities.invokeAndWait(() -> { assertEquals(0, created.get()); assertNull(sidebar.panel()); });
    }

    @Test public void hiddenStartupCreatesNoPanelAndTogglingIsIdempotent() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AtomicInteger created = new AtomicInteger();
            List<NavigationButton> added = new ArrayList<>(), removed = new ArrayList<>();
            LootPopPlugin plugin = new LootPopPlugin()
            {
                @Override String getJournalStatus() { return "Test history"; }
                @Override String getJournalNotice() { return ""; }
            };
            LootSidebar sidebar = new LootSidebar(() ->
            {
                created.incrementAndGet();
                return new LootPopPanel(plugin, new LootPopConfig() {}, null);
            }, added::add, removed::add);
            sidebar.setVisible(false); sidebar.refresh(); sidebar.setVisible(false);
            assertEquals(0, created.get()); assertNull(sidebar.panel()); assertTrue(added.isEmpty());
            sidebar.setVisible(true); sidebar.setVisible(true);
            assertEquals(1, created.get()); assertEquals(1, added.size());
            assertSame(sidebar.panel(), added.get(0).getPanel());
            sidebar.setVisible(false); sidebar.setVisible(false);
            assertEquals(1, removed.size()); assertSame(added.get(0), removed.get(0)); assertNull(sidebar.panel());
            sidebar.setVisible(true); sidebar.refresh();
            assertEquals(2, created.get()); assertEquals(2, added.size());
            assertNotSame(added.get(0).getPanel(), added.get(1).getPanel());
            assertEquals(0, plugin.getSessionSnapshot().getDrops());
            sidebar.setVisible(false);
            assertEquals(added, removed);
        });
    }
}
