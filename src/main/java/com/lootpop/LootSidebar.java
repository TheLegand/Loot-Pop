package com.lootpop;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.NavigationButton;

/** Owns only the optional sidebar UI, never the session or persisted journal. */
final class LootSidebar
{
    private final Supplier<LootPopPanel> panelFactory;
    private final Consumer<NavigationButton> add;
    private final Consumer<NavigationButton> remove;
    private volatile LootPopPanel panel;
    private NavigationButton navigation;

    LootSidebar(Supplier<LootPopPanel> panelFactory, Consumer<NavigationButton> add,
        Consumer<NavigationButton> remove)
    {
        this.panelFactory = panelFactory;
        this.add = add;
        this.remove = remove;
    }

    void setVisible(boolean visible)
    {
        assert SwingUtilities.isEventDispatchThread() : "Sidebar changes must run on EDT";
        if (!visible)
        {
            if (navigation != null) { remove.accept(navigation); }
            navigation = null;
            panel = null;
        }
        else if (navigation == null)
        {
            panel = panelFactory.get();
            navigation = NavigationButton.builder()
                .tooltip("Loot Pop")
                .icon(LootPopPanel.createNavigationIcon())
                .priority(8)
                .panel(panel)
                .build();
            add.accept(navigation);
        }
    }

    LootPopPanel panel() { return panel; }

    void refresh()
    {
        assert SwingUtilities.isEventDispatchThread() : "Sidebar refresh must run on EDT";
        if (panel != null) { panel.refresh(); }
    }
}
