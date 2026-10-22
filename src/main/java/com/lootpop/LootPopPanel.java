package com.lootpop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JToggleButton;
import javax.swing.ButtonGroup;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

final class LootPopPanel extends PluginPanel
{
    static final String ALL_SOURCES = "All sources";
    private static final Color PANEL_BACKGROUND = new Color(26, 28, 31);
    private static final Color CARD_BACKGROUND = new Color(33, 36, 40);
    private static final Color CARD_BORDER = new Color(49, 54, 61);
    private static final Color SLOT_BACKGROUND = new Color(20, 22, 25);
    private static final Color ACCENT = new Color(255, 77, 94);
    private static final Color MUTED = new Color(174, 182, 193);

    private final LootPopPlugin plugin;
    private final LootPopConfig config;
    private final ConfigManager configManager;
    private final LootPanelActions panelActions;
    private final JLabel dropsValue = valueLabel();
    private final JLabel totalValue = valueLabel();
    private final JLabel averageValue = valueLabel();
    private final JLabel bestValue = valueLabel();
    private final JPanel historyList = new JPanel();
    private final JComboBox<VisualMode> visualMode = new JComboBox<>(VisualMode.values());
    private final JComboBox<PopupStyle> popupStyle = new JComboBox<>(PopupStyle.values());
    private final JCheckBox adaptiveTime = new JCheckBox("Adaptive display time");
    private final JCheckBox screenshots = new JCheckBox("Automatic rare screenshots");
    private final JCheckBox sealedDrops = new JCheckBox("Sealed Drop reveals");
    private final JComboBox<String> sessionSource = new JComboBox<>();
    private JPanel quickControlsPanel;
    private JPanel quickControlsWrap;
    private boolean syncing;
    private boolean refreshing;
    private boolean journalMode;
    private int historyPageSize = 50;
    private java.time.LocalDate focusedDay;
    private final java.util.Set<java.time.LocalDate> expandedDays = new java.util.HashSet<>();
    private java.time.LocalDate latestDay;
    private boolean daySelectionInitialized;
    private final JComboBox<String> dateJump = new JComboBox<>();
    private final java.util.List<java.time.LocalDate> dateKeys = new java.util.ArrayList<>();
    private final JComboBox<JournalLayout> journalLayout = new JComboBox<>(JournalLayout.values());
    private final JLabel scopeTitle = sectionTitle("SESSION");
    private final JLabel historyTitle = sectionTitle("RECENT LOOT");
    private final JButton resetButton = new JButton("Reset");
    private final JButton settingsToggle = new JButton(new GearIcon());
    private final JButton olderButton = new JButton("Load older drops");
    private final JComboBox<String> journalView = new JComboBox<>(new String[]{"All drops", "Moments", "Records", "Trophy Shelf"});
    private final JComboBox<String> trophyKind = new JComboBox<>(new String[]{"All trophies", "Source bests", "Ultra rare", "Collection Log"});
    private final JComboBox<PriceMode> journalPrice = new JComboBox<>(PriceMode.values());
    private final JPanel journalSummary = new JPanel();
    private final JLabel journalTotal = valueLabel();
    private final JLabel journalCount = new JLabel();
    private final JLabel journalBest = new JLabel();
    private final JLabel journalNotice = new JLabel();
    private final JButton journalInfo = new JButton("i");
    private final JPanel journalFilters = new JPanel();
    private final JToggleButton journalFilterToggle = new JToggleButton("Filters");
    private JPanel sessionGrid;
    private JPanel mainContent;
    private final JPanel detailContent = new JPanel();
    private final JButton recapButton = new JButton("Session recap");
    private final JButton sourceProfileButton = new JButton("View source profile");
    private final JComboBox<PriceMode> detailPrice = new JComboBox<>(PriceMode.values());
    private String detailSource;
    private boolean showingRecap;
    private int returnScroll;

    LootPopPanel(
        LootPopPlugin plugin,
        LootPopConfig config,
        ConfigManager configManager
    )
    { this(plugin, config, configManager, LootPanelActions.desktop()); }

    LootPopPanel(LootPopPlugin plugin, LootPopConfig config, ConfigManager configManager, LootPanelActions panelActions)
    {
        super(false);
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        this.panelActions = java.util.Objects.requireNonNull(panelActions);
        for (JComboBox<?> combo : new JComboBox<?>[]{visualMode, popupStyle, sessionSource, journalView, journalPrice, trophyKind, journalLayout, dateJump, detailPrice})
        { styleCombo(combo); }

        setLayout(new BorderLayout());
        setBackground(PANEL_BACKGROUND);
        JScrollPane scroll = new JScrollPane(buildContent(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(242, 0));
        scroll.setMinimumSize(new Dimension(0, 0));
        scroll.getViewport().setBackground(PANEL_BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI()
        {
            @Override protected void configureScrollBarColors()
            { thumbColor = new Color(92, 98, 108); trackColor = PANEL_BACKGROUND; }
            @Override protected JButton createDecreaseButton(int orientation) { return emptyArrow(); }
            @Override protected JButton createIncreaseButton(int orientation) { return emptyArrow(); }
            private JButton emptyArrow()
            {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0)); button.setMaximumSize(new Dimension(0, 0));
                return button;
            }
            @Override protected void paintThumb(Graphics graphics, JComponent component, java.awt.Rectangle bounds)
            {
                graphics.setColor(thumbColor);
                graphics.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(1, bounds.width - 4),
                    Math.max(1, bounds.height - 4), 6, 6);
            }
        });
        add(scroll, BorderLayout.CENTER);
        bindSidebarEscape();
        wireQuickControls();
        syncControls();
        refresh();
    }

    private JPanel buildContent()
    {
        JPanel root = new ScrollColumn();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(PANEL_BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 14, 8));

        JPanel tabs = new JPanel(new GridLayout(1, 2, 4, 0));
        tabs.setOpaque(false);
        tabs.setAlignmentX(LEFT_ALIGNMENT);
        tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        ButtonGroup group = new ButtonGroup();
        for (String label : new String[]{"Session", "Journal"})
        {
            JToggleButton tab = new JToggleButton(label);
            tab.setFocusable(false);
            tab.setSelected(label.equals("Session"));
            tab.setForeground(Color.WHITE);
            tab.setBackground(CARD_BACKGROUND);
            tab.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
            tab.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI()
            {
                @Override protected void paintButtonPressed(Graphics graphics, javax.swing.AbstractButton button)
                {
                    graphics.setColor(new Color(48, 32, 38));
                    graphics.fillRect(0, 0, button.getWidth(), button.getHeight());
                }
            });
            tab.addChangeListener(event ->
            {
                tab.setForeground(tab.isSelected() ? ACCENT : MUTED);
                tab.setBackground(tab.isSelected() ? new Color(48, 32, 38) : CARD_BACKGROUND);
            });
            tab.setForeground(tab.isSelected() ? ACCENT : MUTED);
            tab.addActionListener(event ->
            {
                boolean enteringJournal = label.equals("Journal") && !journalMode;
                if (enteringJournal) { daySelectionInitialized = false; }
                journalMode = label.equals("Journal");
                detailSource = null; showingRecap = false;
                historyPageSize = 50;
                refresh();
                if (enteringJournal)
                {
                    SwingUtilities.invokeLater(() -> ((JScrollPane) SwingUtilities.getAncestorOfClass(
                        JScrollPane.class, mainContent)).getVerticalScrollBar().setValue(0));
                }
            });
            group.add(tab); tabs.add(tab);
        }
        root.add(tabs);
        root.add(Box.createVerticalStrut(12));
        mainContent = new JPanel(); mainContent.setOpaque(false);
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setAlignmentX(LEFT_ALIGNMENT);
        JPanel content = mainContent;
        root.add(mainContent);
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        detailContent.setOpaque(false); detailContent.setAlignmentX(LEFT_ALIGNMENT);
        detailContent.setName("loot-detail"); detailContent.setVisible(false);
        root.add(detailContent);
        detailPrice.setName("loot-detail-price");
        detailPrice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        detailPrice.addActionListener(event -> { if (!refreshing) { refresh(); } });

        JPanel sessionHeader = new JPanel(new BorderLayout());
        sessionHeader.setOpaque(false);
        sessionHeader.setAlignmentX(LEFT_ALIGNMENT);
        sessionHeader.add(scopeTitle, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        configureInformation(journalInfo, "loot-journal-info");
        actions.add(journalInfo);
        styleHeaderButton(resetButton);
        resetButton.setName("loot-reset");
        resetButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        resetButton.setPreferredSize(new Dimension(54, 30));
        resetButton.addActionListener(event ->
        {
            if (!journalMode)
            {
                long generation = plugin.getSessionGeneration();
                SessionSnapshot allSources = plugin.getSessionSnapshot();
                if (allSources.getDrops() == 0 || panelActions.confirmSessionReset(this, allSources.getDrops()))
                { plugin.clearSessionIfGeneration(generation); }
                return;
            }
            if (JOptionPane.showConfirmDialog(this,
                "Clear saved drops, trophies and personal records for this character?\nThe previous file is kept as a local .bak backup.",
                "Clear Loot Journal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
            { plugin.clearJournal(); }
        });
        actions.add(resetButton);

        styleHeaderButton(settingsToggle);
        settingsToggle.setName("loot-settings");
        settingsToggle.getAccessibleContext().setAccessibleName("Popup settings");
        settingsToggle.setToolTipText("Show or hide popup controls");
        settingsToggle.setPreferredSize(new Dimension(36, 30));
        settingsToggle.setHorizontalAlignment(SwingConstants.CENTER);
        settingsToggle.setVerticalAlignment(SwingConstants.CENTER);
        settingsToggle.addActionListener(event ->
        {
            boolean visible = !quickControlsPanel.isVisible();
            quickControlsPanel.setVisible(visible);
            quickControlsWrap.setVisible(visible);
            settingsToggle.setForeground(visible ? ACCENT : Color.WHITE);
            requestFocusInWindow();
            revalidate();
            repaint();
        });
        actions.add(settingsToggle);
        sessionHeader.add(actions, BorderLayout.EAST);
        content.add(sessionHeader);

        quickControlsPanel = buildQuickControls();
        quickControlsPanel.setName("popup-controls");
        quickControlsPanel.setVisible(false);
        quickControlsWrap = new JPanel(new BorderLayout());
        quickControlsWrap.setOpaque(false);
        quickControlsWrap.setAlignmentX(LEFT_ALIGNMENT);
        quickControlsWrap.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        quickControlsWrap.add(quickControlsPanel, BorderLayout.CENTER);
        quickControlsWrap.setVisible(false);
        content.add(quickControlsWrap);
        content.add(Box.createVerticalStrut(7));

        sessionSource.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        sessionSource.setAlignmentX(LEFT_ALIGNMENT);
        sessionSource.setToolTipText("Show the full session or one boss/NPC source");
        sessionSource.addActionListener(event ->
        {
            if (!refreshing)
            {
                refresh();
            }
        });
        content.add(sessionSource);
        styleHeaderButton(sourceProfileButton);
        sourceProfileButton.setName("loot-source-profile");
        sourceProfileButton.setAlignmentX(LEFT_ALIGNMENT);
        sourceProfileButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        sourceProfileButton.setText("Source profile  ›");
        sourceProfileButton.setToolTipText("Open saved observations for the selected source");
        sourceProfileButton.addActionListener(event -> openDetail((String) sessionSource.getSelectedItem()));
        content.add(Box.createVerticalStrut(4)); content.add(sourceProfileButton);
        content.add(Box.createVerticalStrut(7));
        sessionGrid = buildSessionGrid();
        content.add(sessionGrid);
        styleHeaderButton(recapButton); recapButton.setName("loot-recap");
        recapButton.setText("Session recap  ›");
        recapButton.setAlignmentX(LEFT_ALIGNMENT);
        recapButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        recapButton.setToolTipText("Highlights and totals since your first drop or last Reset");
        recapButton.addActionListener(event -> openDetail(null));
        content.add(Box.createVerticalStrut(7)); content.add(recapButton);
        journalSummary.setOpaque(false);
        journalSummary.setAlignmentX(LEFT_ALIGNMENT);
        journalSummary.setLayout(new BoxLayout(journalSummary, BoxLayout.Y_AXIS));
        journalSummary.setBorder(BorderFactory.createEmptyBorder(5, 2, 10, 2));
        for (JLabel label : new JLabel[]{journalTotal, journalCount, journalBest})
        { label.setAlignmentX(LEFT_ALIGNMENT); }
        for (JComboBox<?> combo : new JComboBox<?>[]{journalView, journalLayout, trophyKind})
        { combo.setAlignmentX(LEFT_ALIGNMENT); }
        journalTotal.setFont(journalTotal.getFont().deriveFont(Font.BOLD, 27f));
        journalSummary.add(journalTotal);
        journalCount.setForeground(MUTED); journalCount.setFont(journalCount.getFont().deriveFont(12f));
        journalSummary.add(journalCount);
        journalSummary.add(Box.createVerticalStrut(8));
        journalBest.setForeground(MUTED);
        journalBest.setFont(journalBest.getFont().deriveFont(Font.PLAIN, 12f));
        journalSummary.add(journalBest);
        journalNotice.setName("loot-journal-notice"); journalNotice.setAlignmentX(LEFT_ALIGNMENT);
        journalNotice.setForeground(new Color(230, 198, 131));
        journalNotice.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        journalSummary.add(journalNotice);
        journalSummary.add(Box.createVerticalStrut(10));
        styleFilterButton(journalFilterToggle);
        journalFilterToggle.setName("loot-journal-filters-toggle");
        journalFilterToggle.setToolTipText("Show prices, saved-drop views and grouping");
        journalFilterToggle.getAccessibleContext().setAccessibleDescription("Expand or collapse Journal filters");
        journalFilterToggle.addActionListener(event ->
        {
            boolean open = journalFilterToggle.isSelected(); journalFilters.setVisible(open);
            journalFilterToggle.setToolTipText(open ? "Hide filters" : "Show prices, saved-drop views and grouping");
            revalidate(); repaint();
        });
        journalSummary.add(journalFilterToggle);
        journalFilters.setOpaque(false); journalFilters.setAlignmentX(LEFT_ALIGNMENT);
        journalFilters.setName("loot-journal-filters");
        journalFilters.setLayout(new BoxLayout(journalFilters, BoxLayout.Y_AXIS));
        journalFilters.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        journalFilters.setVisible(false); journalSummary.add(journalFilters);
        journalPrice.setSelectedItem(config.priceMode());
        journalFilters.add(controlRow("Prices", journalPrice));
        journalFilters.add(Box.createVerticalStrut(6));
        journalFilters.add(journalView);
        journalView.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
        journalView.addActionListener(event -> { if (!refreshing) { historyPageSize = 50; refresh(); } });
        journalFilters.add(Box.createVerticalStrut(6));
        trophyKind.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
        trophyKind.setVisible(false);
        trophyKind.addActionListener(event -> { if (!refreshing) { historyPageSize = 50; refresh(); } });
        journalFilters.add(trophyKind);
        journalFilters.add(Box.createVerticalStrut(6));
        journalLayout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        journalLayout.setSelectedItem(config.journalLayout());
        journalLayout.setToolTipText("Separate drop cards or grouped daily loot; uses recorded values");
        journalLayout.addActionListener(event ->
        {
            if (!refreshing && !syncing)
            {
                setConfig("journalLayout", journalLayout.getSelectedItem());
                historyPageSize = 50; refresh();
            }
        });
        journalFilters.add(journalLayout);
        journalPrice.addActionListener(event -> { if (!refreshing) { historyPageSize = 50; refresh(); } });
        journalSummary.setVisible(false);
        content.add(journalSummary);
        content.add(Box.createVerticalStrut(14));

        JPanel historyHeader = new JPanel(new BorderLayout(6, 0));
        historyHeader.setOpaque(false); historyHeader.setAlignmentX(LEFT_ALIGNMENT);
        historyHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        historyHeader.add(historyTitle, BorderLayout.WEST);
        dateJump.setName("journal-date-jump");
        dateJump.setPreferredSize(new Dimension(132, 27));
        dateJump.setToolTipText("Jump directly to a saved date");
        dateJump.setVisible(false);
        dateJump.addActionListener(event ->
        {
            if (!refreshing && dateJump.getSelectedIndex() > 0)
            {
                focusedDay = dateKeys.get(dateJump.getSelectedIndex() - 1);
                expandedDays.add(focusedDay);
                historyPageSize = 50; refresh(); scrollToExpandedDate();
            }
        });
        historyHeader.add(dateJump, BorderLayout.EAST);
        content.add(historyHeader); content.add(Box.createVerticalStrut(5));
        historyList.setLayout(new BoxLayout(historyList, BoxLayout.Y_AXIS));
        historyList.setOpaque(false);
        historyList.setAlignmentX(LEFT_ALIGNMENT);
        content.add(historyList);
        styleHeaderButton(olderButton);
        olderButton.setAlignmentX(LEFT_ALIGNMENT);
        olderButton.addActionListener(event -> { historyPageSize += 50; refresh(); });
        olderButton.setVisible(false);
        content.add(olderButton);
        return root;
    }

    private JPanel buildQuickControls()
    {
        JPanel panel = cardPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("POPUP CONTROLS");
        title.setForeground(ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 10f));
        panel.add(title);
        panel.add(Box.createVerticalStrut(8));
        panel.add(controlRow("Experience", visualMode));
        panel.add(Box.createVerticalStrut(7));
        panel.add(controlRow("Style", popupStyle));
        panel.add(Box.createVerticalStrut(7));
        styleCheckBox(adaptiveTime);
        styleCheckBox(screenshots);
        styleCheckBox(sealedDrops);
        sealedDrops.setToolTipText("Reveal rare rewards before ordinary loot (Enhanced mode)");
        panel.add(sealedDrops);
        panel.add(adaptiveTime);
        panel.add(screenshots);
        panel.add(Box.createVerticalStrut(7));
        JLabel hint = new JLabel("More options in plugin settings");
        hint.setForeground(new Color(126, 132, 141));
        hint.setFont(hint.getFont().deriveFont(10f));
        panel.add(hint);
        return panel;
    }

    private JPanel buildSessionGrid()
    {
        JPanel grid = new JPanel();
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.setName("loot-session-summary");
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
        grid.setBorder(BorderFactory.createEmptyBorder(9, 2, 8, 2));
        JLabel caption = new JLabel("Loot value"); caption.setForeground(MUTED);
        caption.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); caption.setAlignmentX(LEFT_ALIGNMENT);
        caption.setName("loot-value-caption"); caption.setHorizontalAlignment(SwingConstants.CENTER);
        caption.setMaximumSize(new Dimension(Integer.MAX_VALUE, caption.getPreferredSize().height));
        grid.add(caption); grid.add(Box.createVerticalStrut(4));
        totalValue.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28)); totalValue.setAlignmentX(LEFT_ALIGNMENT);
        totalValue.setName("loot-value-total"); totalValue.setHorizontalAlignment(SwingConstants.CENTER);
        totalValue.setMaximumSize(new Dimension(Integer.MAX_VALUE, totalValue.getPreferredSize().height));
        grid.add(totalValue); grid.add(Box.createVerticalStrut(12));
        JPanel metrics = new JPanel(new GridLayout(1, 3, 8, 0)); metrics.setOpaque(false);
        metrics.setAlignmentX(LEFT_ALIGNMENT); metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        metrics.add(smallMetric("Drops", dropsValue)); metrics.add(smallMetric("Average", averageValue));
        metrics.add(smallMetric("Best", bestValue)); grid.add(metrics);
        return grid;
    }

    private JPanel smallMetric(String title, JLabel value)
    {
        JPanel panel = new JPanel(); panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.add(label);
        panel.add(Box.createVerticalStrut(3));
        panel.add(value);
        return panel;
    }

    private boolean trophyMatches(LootHistoryEntry entry)
    {
        switch (trophyKind.getSelectedIndex())
        {
            case 1: return entry.isSourceBest();
            case 2: return entry.getRarity() == DropRarity.ULTRA_RARE;
            case 3: return entry.isCollectionLogUnlock();
            default: return true;
        }
    }

    static LootHistoryItem trophyHero(LootHistoryEntry entry, int kind)
    {
        return entry.getItems().stream().max(java.util.Comparator
            .comparingInt((LootHistoryItem item) -> kind == 1 ? 0 : item.isCollectionLogItem() ? 3
                : item.getRarity() == DropRarity.ULTRA_RARE ? 2 : 1)
            .thenComparingLong(LootHistoryItem::getStackValue)).orElse(null);
    }

    private JPanel trophyCard(LootHistoryEntry entry)
    {
        Color gold = new Color(230, 198, 131);
        JPanel card = new RoundedPanel(new Color(35, 34, 32), new Color(88, 76, 52), gold);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 10));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        String reason = trophyKind.getSelectedIndex() == 1 ? "SOURCE BEST" : trophyKind.getSelectedIndex() == 2
            ? "ULTRA RARE" : entry.isCollectionLogUnlock() ? "COLLECTION LOG" : entry.getRarity() == DropRarity.ULTRA_RARE
                ? "ULTRA RARE" : "SOURCE BEST";
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false); heading.setAlignmentX(LEFT_ALIGNMENT);
        JLabel title = new JLabel(reason); title.setForeground(gold); title.setFont(title.getFont().deriveFont(Font.BOLD, 9f));
        title.setToolTipText((entry.isSourceBest() ? "Source best / " : "")
            + (entry.getRarity() == DropRarity.ULTRA_RARE ? "Ultra rare / " : "")
            + (entry.isCollectionLogUnlock() ? "New Collection Log slot" : "Observed drop"));
        JLabel basis = new JLabel(entry.getPriceMode() == PriceMode.HIGH_ALCHEMY ? "HA" : "GE");
        basis.setForeground(MUTED); basis.setFont(basis.getFont().deriveFont(9f));
        heading.add(title, BorderLayout.WEST); heading.add(basis, BorderLayout.EAST);
        card.add(heading); card.add(Box.createVerticalStrut(7));

        LootHistoryItem hero = trophyHero(entry, trophyKind.getSelectedIndex());
        if (hero != null)
        {
            BufferedImage image = plugin.getHistoryImage(hero);
            boolean[] hovered = {false};
            JComponent artwork = new JComponent()
            {
                @Override protected void paintComponent(Graphics graphics)
                {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.setPaint(new java.awt.RadialGradientPaint(32f, 28f, 31f,
                        new float[]{0, 1}, new Color[]{new Color(230, 198, 131, hovered[0] ? 58 : 27), new Color(230, 198, 131, 0)}));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    if (image != null) { LootArtwork.draw(g, image, 5, 4, 54, 48); }
                    else { g.setFont(getFont().deriveFont(Font.BOLD, 24f)); g.setColor(MUTED); g.drawString("?", 24, 37); }
                    if (hero.getQuantity() > 1)
                    {
                        g.setFont(getFont().deriveFont(Font.BOLD, 10f));
                        g.setColor(Color.BLACK); g.drawString(formatPanelQuantity(hero.getQuantity()), 3, 12);
                        g.setColor(new Color(125, 255, 145)); g.drawString(formatPanelQuantity(hero.getQuantity()), 2, 11);
                    }
                    g.dispose();
                }
            };
            artwork.setPreferredSize(new Dimension(64, 56));
            artwork.addMouseListener(new MouseAdapter()
            {
                @Override public void mouseEntered(MouseEvent e) { hovered[0] = true; artwork.repaint(); }
                @Override public void mouseExited(MouseEvent e) { hovered[0] = false; artwork.repaint(); }
            });
            if (image instanceof net.runelite.client.util.AsyncBufferedImage)
            { ((net.runelite.client.util.AsyncBufferedImage) image).onLoaded(() -> SwingUtilities.invokeLater(artwork::repaint)); }
            makeItemLink(artwork, hero.getName(), buildItemTooltip(hero));
            JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(false); row.setAlignmentX(LEFT_ALIGNMENT);
            row.add(artwork, BorderLayout.WEST);
            JPanel copy = new JPanel(); copy.setOpaque(false); copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
            JLabel itemName = new JLabel("<html><body style='width:112px'>" + escapeHtml(hero.getName()) + "</body></html>");
            itemName.setForeground(Color.WHITE); itemName.setFont(itemName.getFont().deriveFont(Font.BOLD, 13f));
            makeItemLink(itemName, hero.getName(), buildItemTooltip(hero)); copy.add(itemName); copy.add(Box.createVerticalStrut(3));
            JLabel value = new JLabel(hero.isPricePending() ? "Price pending" : LootPopOverlay.formatGp(hero.getStackValue()));
            value.setForeground(gold); value.setFont(value.getFont().deriveFont(Font.BOLD, 12f)); copy.add(value);
            row.add(copy, BorderLayout.CENTER); card.add(row); card.add(Box.createVerticalStrut(6));
        }
        JLabel source = new JLabel(entry.getSourceName()); source.setForeground(Color.WHITE);
        source.setFont(source.getFont().deriveFont(Font.BOLD, 11f)); source.setAlignmentX(LEFT_ALIGNMENT);
        makeSourceLink(source, entry.getSourceName(), entry.getPriceMode()); card.add(source);
        JLabel total = new JLabel("Drop: " + LootValueText.compact(entry.getTotalValue(), entry.hasPendingPrices()));
        total.setForeground(MUTED); total.setFont(total.getFont().deriveFont(10f)); total.setAlignmentX(LEFT_ALIGNMENT);
        total.setToolTipText(LootValueText.detail(entry.getTotalValue(), entry.hasPendingPrices())); card.add(total);
        JLabel date = new JLabel(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy / HH:mm", Locale.ENGLISH)
            .format(java.time.Instant.ofEpochMilli(entry.getTimestamp()).atZone(java.time.ZoneId.systemDefault())));
        date.setForeground(MUTED); date.setFont(date.getFont().deriveFont(9f)); date.setAlignmentX(LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(5)); card.add(date);
        return card;
    }

    private JPanel historyCard(LootHistoryEntry entry)
    {
        Color rarityColour = journalMode && (entry.isPersonalBest() || entry.isCollectionLogUnlock())
            ? new Color(230, 198, 131) : rarityColour(entry.getRarity());
        JPanel card = new RoundedPanel(CARD_BACKGROUND, CARD_BORDER, rarityColour);
        card.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 8));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, journalMode || entry.isPersonalBest() ? 104 : 84));

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);

        JPanel sourceGroup = new JPanel(new BorderLayout(4, 0));
        sourceGroup.setOpaque(false);
        sourceGroup.setMinimumSize(new Dimension(0, 18));
        JLabel source = new JLabel(entry.getSourceName());
        source.setMinimumSize(new Dimension(0, 18));
        source.setForeground(Color.WHITE);
        source.setFont(source.getFont().deriveFont(Font.BOLD, 13f));
        makeSourceLink(source, entry.getSourceName(), entry.getPriceMode());
        sourceGroup.add(source, BorderLayout.CENTER);
        if (entry.isCollectionLogUnlock())
        {
            JLabel newBadge = new JLabel(new javax.swing.Icon()
            {
                @Override public int getIconWidth() { return 33; }
                @Override public int getIconHeight() { return 17; }
                @Override public void paintIcon(Component component, Graphics graphics, int x, int y)
                {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.translate(x, y); g.scale(1.2, 1.2); LootLogBadge.draw(g, 0, 0); g.dispose();
                }
            });
            newBadge.setToolTipText("New Collection Log slot");
            sourceGroup.add(newBadge, BorderLayout.EAST);
        }
        header.add(sourceGroup, BorderLayout.CENTER);

        JLabel price = new JLabel(LootValueText.compact(entry.getTotalValue(), entry.hasPendingPrices()));
        price.setName("loot-drop-value");
        price.setForeground(entry.hasPendingPrices() ? MUTED : rarityColour);
        price.setFont(price.getFont().deriveFont(Font.BOLD, 12f));
        price.setHorizontalAlignment(SwingConstants.RIGHT);
        price.setToolTipText(LootValueText.detail(entry.getTotalValue(), entry.hasPendingPrices()));
        header.add(price, BorderLayout.EAST);
        card.add(header);
        card.add(Box.createVerticalStrut(5));

        JPanel itemRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        itemRow.setOpaque(false);
        itemRow.setAlignmentX(LEFT_ALIGNMENT);
        // Reserve the fifth slot for the original compact +N indicator.
        int visibleItems = entry.getItems().size() > 5 ? 4 : Math.min(5, entry.getItems().size());
        for (int i = 0; i < visibleItems; i++)
        {
            LootHistoryItem item = entry.getItems().get(i);
            JPanel icon = createItemIcon(item);
            itemRow.add(icon);
        }
        if (entry.getItems().size() > visibleItems)
        {
            itemRow.add(historyOverflow(entry, visibleItems));
        }
        card.add(itemRow);
        if (journalMode || entry.isPersonalBest())
        {
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false); footer.setAlignmentX(LEFT_ALIGNMENT);
            String left = entry.isPersonalBest() ? "RECORD  +" + LootPopOverlay.formatGp(entry.getTotalValue() - entry.getPreviousBest())
                : entry.isCollectionLogUnlock() ? "COLLECTION LOG" : "";
            JLabel mark = new JLabel(left);
            mark.setForeground(new Color(230, 198, 131)); mark.setFont(mark.getFont().deriveFont(Font.BOLD, 9f));
            if (entry.isPersonalBest())
            {
                mark.setToolTipText("Previous: " + LootPopOverlay.formatExactGp(entry.getPreviousBest())
                    + " / New: " + LootPopOverlay.formatExactGp(entry.getTotalValue()));
            }
            footer.add(mark, BorderLayout.WEST);
            if (journalMode)
            {
                JLabel time = new JLabel(java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    .format(java.time.Instant.ofEpochMilli(entry.getTimestamp()).atZone(java.time.ZoneId.systemDefault())));
                time.setForeground(MUTED); time.setFont(time.getFont().deriveFont(9f));
                footer.add(time, BorderLayout.EAST);
            }
            card.add(Box.createVerticalStrut(4)); card.add(footer);
        }
        return card;
    }

    private JPanel historyFallbackCard(LootHistoryEntry entry)
    {
        JPanel card = new RoundedPanel(CARD_BACKGROUND, CARD_BORDER, rarityColour(entry.getRarity()));
        card.setLayout(new BorderLayout(8, 0));
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 9));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));

        JLabel source = new JLabel(entry.getSourceName());
        source.setForeground(Color.WHITE);
        source.setFont(source.getFont().deriveFont(Font.BOLD));
        makeSourceLink(source, entry.getSourceName(), entry.getPriceMode());
        card.add(source, BorderLayout.WEST);

        JLabel value = new JLabel(LootValueText.compact(entry.getTotalValue(), entry.hasPendingPrices()));
        value.setForeground(rarityColour(entry.getRarity()));
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        card.add(value, BorderLayout.EAST);
        card.setToolTipText("This drop was kept, but one of its item previews could not be drawn.");
        return card;
    }

    private JPanel emptyHistoryCard()
    {
        JPanel empty = new RoundedPanel(CARD_BACKGROUND, CARD_BORDER, null);
        empty.setLayout(new BorderLayout(10, 0));
        empty.setBorder(BorderFactory.createEmptyBorder(13, 12, 13, 12));
        empty.setAlignmentX(LEFT_ALIGNMENT);
        empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));

        JLabel mark = new JLabel("+");
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setForeground(ACCENT);
        mark.setFont(mark.getFont().deriveFont(Font.BOLD, 22f));
        mark.setPreferredSize(new Dimension(30, 30));
        empty.add(mark, BorderLayout.WEST);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Waiting for loot");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        JLabel subtitle = new JLabel("Your next drops will appear here");
        subtitle.setForeground(MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(10f));
        copy.add(title);
        copy.add(Box.createVerticalStrut(2));
        copy.add(subtitle);
        empty.add(copy, BorderLayout.CENTER);
        return empty;
    }

    private JPanel createItemIcon(LootHistoryItem item)
    {
        return createItemIcon(item, item.getQuantity(), buildItemTooltip(item));
    }

    private JPanel createItemIcon(LootHistoryItem item, long quantity, String tooltip)
    {
        Color colour = rarityColour(item.getRarity());
        ItemSlot slot = new ItemSlot(colour, quantity);
        slot.setLayout(new BorderLayout());
        slot.setPreferredSize(new Dimension(38, 36));
        slot.setMinimumSize(new Dimension(38, 36));
        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(36, 32));
        icon.setMinimumSize(new Dimension(36, 32));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        try
        {
            BufferedImage image = plugin.getHistoryImage(item);
            if (image != null)
            {
                icon.setIcon(new javax.swing.Icon()
                {
                    @Override public int getIconWidth() { return 36; }
                    @Override public int getIconHeight() { return 32; }
                    @Override public void paintIcon(Component component, Graphics graphics, int x, int y)
                    { LootArtwork.draw((Graphics2D) graphics, image, x, y, 36, 32); }
                });
                if (image instanceof net.runelite.client.util.AsyncBufferedImage)
                { ((net.runelite.client.util.AsyncBufferedImage) image).onLoaded(icon::repaint); }
            }
            else
            {
                icon.setText("?"); icon.setForeground(MUTED);
            }
        }
        catch (RuntimeException ignored)
        {
            icon.setText("?");
            icon.setForeground(MUTED);
        }
        icon.setToolTipText(tooltip);
        slot.setToolTipText(tooltip);
        makeItemLink(icon, item.getName(), tooltip);
        makeItemLink(slot, item.getName(), tooltip);
        slot.add(icon, BorderLayout.CENTER);
        return slot;
    }

    private void makeWikiLink(JComponent component, String searchTerm, String tooltip)
    {
        if (searchTerm == null || searchTerm.trim().isEmpty())
        {
            return;
        }
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.setToolTipText(tooltip);
        if (component instanceof javax.swing.AbstractButton)
        {
            ((javax.swing.AbstractButton) component).addActionListener(event -> panelActions.openWiki(searchTerm));
            return;
        }
        component.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                if (SwingUtilities.isLeftMouseButton(event))
                {
                    panelActions.openWiki(searchTerm);
                }
            }
        });
    }

    private void makeItemLink(JComponent component, String name, String tooltip)
    {
        if (name == null || name.trim().isEmpty()) { return; }
        makeWikiLink(component, name, tooltip); component.setName("loot-item-link");
        JPopupMenu menu = contextMenu();
        menu.add(menuItem("Copy name", "loot-copy-name", () -> panelActions.copyName(component, name)));
        menu.add(menuItem("Open Wiki", "loot-open-wiki", () -> panelActions.openWiki(name)));
        component.setComponentPopupMenu(menu);
    }

    private static JPopupMenu contextMenu()
    {
        JPopupMenu menu = new JPopupMenu(); menu.setBackground(CARD_BACKGROUND);
        menu.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(3, 3, 3, 3))); return menu;
    }

    private static JMenuItem menuItem(String label, String name, Runnable action)
    {
        JMenuItem item = new JMenuItem(label); item.setName(name);
        item.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        item.setBackground(CARD_BACKGROUND); item.setForeground(Color.WHITE);
        item.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
        item.addActionListener(event -> action.run()); return item;
    }

    private void makeSourceLink(JComponent component, String source)
    { makeSourceLink(component, source, (PriceMode) detailPrice.getSelectedItem()); }

    private void makeSourceLink(JComponent component, String source, PriceMode mode)
    {
        component.setName("loot-source-link");
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.setToolTipText("View " + source + " source profile");
        JPopupMenu menu = contextMenu();
        menu.add(menuItem("View profile", "loot-view-profile", () -> openDetail(source, mode)));
        menu.add(menuItem("Open Wiki", "loot-open-wiki", () -> panelActions.openWiki(source)));
        component.setComponentPopupMenu(menu);
        component.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseEntered(MouseEvent event) { component.setForeground(ACCENT); }
            @Override public void mouseExited(MouseEvent event) { component.setForeground(Color.WHITE); }
            @Override public void mouseClicked(MouseEvent event)
            { if (SwingUtilities.isLeftMouseButton(event)) { openDetail(source, mode); } }
        });
    }

    private void openDetail(String source)
    { openDetail(source, journalMode ? (PriceMode) journalPrice.getSelectedItem() : config.priceMode()); }

    private void openDetail(String source, PriceMode mode)
    {
        if (ALL_SOURCES.equals(source)) { return; }
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, mainContent);
        if (!showingRecap && detailSource == null) { returnScroll = scroll.getVerticalScrollBar().getValue(); }
        showingRecap = source == null; detailSource = source;
        refreshing = true;
        detailPrice.setSelectedItem(mode);
        refreshing = false;
        refresh();
        requestFocusInWindow();
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    private void closeDetail()
    {
        showingRecap = false; detailSource = null; refresh();
        requestFocusInWindow();
        SwingUtilities.invokeLater(() ->
        {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, mainContent);
            scroll.getVerticalScrollBar().setValue(returnScroll);
        });
    }

    private void bindSidebarEscape()
    {
        setFocusable(true);
        KeyStroke escape = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
        String action = "loot-sidebar-escape";
        // Do not use WHEN_IN_FOCUSED_WINDOW: the game canvas and other plugins keep their own Esc behavior.
        getInputMap(WHEN_FOCUSED).put(escape, action);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(escape, action);
        getActionMap().put(action, new AbstractAction()
        {
            @Override public void actionPerformed(java.awt.event.ActionEvent event)
            {
                if (javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length > 0)
                { javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath(); return; }
                for (JComboBox<?> combo : new JComboBox<?>[]{visualMode, popupStyle, sessionSource, journalView,
                    journalPrice, trophyKind, journalLayout, dateJump, detailPrice})
                { if (combo.isPopupVisible()) { combo.hidePopup(); return; } }
                if (showingRecap || detailSource != null) { closeDetail(); }
                else if (journalMode && journalFilterToggle.isSelected()) { journalFilterToggle.doClick(); }
                else if (quickControlsPanel.isVisible()) { settingsToggle.doClick(); }
            }
        });
    }

    private void refreshDetail()
    {
        boolean open = showingRecap || detailSource != null;
        mainContent.setVisible(!open); detailContent.setVisible(open);
        detailContent.removeAll();
        if (!open) { return; }

        JPanel heading = new JPanel(new BorderLayout(6, 0));
        heading.setOpaque(false); heading.setAlignmentX(LEFT_ALIGNMENT);
        JLabel title = sectionTitle(showingRecap ? "SESSION RECAP" : "SOURCE PROFILE");
        JButton back = new JButton("Back"); styleHeaderButton(back);
        back.setName("loot-detail-back"); back.addActionListener(event -> closeDetail());
        back.setPreferredSize(new Dimension(54, 29));
        JButton info = new JButton("i"); configureInformation(info, "loot-detail-info");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); actions.setOpaque(false);
        actions.add(info); actions.add(back);
        heading.add(title, BorderLayout.CENTER); heading.add(actions, BorderLayout.EAST);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); detailContent.add(heading);
        detailContent.add(Box.createVerticalStrut(12));
        detailCopy(showingRecap ? "Your session" : detailSource, Color.WHITE, 19, true);
        detailContent.add(Box.createVerticalStrut(7));
        detailPrice.setAlignmentX(LEFT_ALIGNMENT); detailContent.add(detailPrice);
        detailContent.add(Box.createVerticalStrut(12));

        PriceMode mode = (PriceMode) detailPrice.getSelectedItem();
        List<LootHistoryEntry> observed = showingRecap ? java.util.Collections.emptyList()
            : plugin.getJournalEntries().stream()
                .filter(e -> e.getPriceMode() == mode && detailSource.equals(e.getSourceName()))
                .sorted(java.util.Comparator.comparingLong(LootHistoryEntry::getTimestamp).reversed())
                .collect(java.util.stream.Collectors.toList());
        LootInsights.Summary summary = showingRecap ? plugin.getSessionRecap(mode)
            : LootInsights.source(observed, detailSource, mode);
        String basis = mode == PriceMode.HIGH_ALCHEMY ? "HA" : "GE";
        detailCopy(LootValueText.compact(summary.total, summary.pendingDrops > 0), Color.WHITE, 27, true)
            .setToolTipText(LootValueText.detail(summary.total, summary.pendingDrops > 0));
        detailCopy(summary.drops + (summary.drops == 1 ? " drop" : " drops") + "  ·  " + basis, MUTED, 12, false)
            .setToolTipText("Recorded drops, not a kill count. Values captured at drop time; not net profit.");
        updateInformation(info, "Recorded loot value, not net profit. GE and HA are kept separate.\n"
            + (showingRecap ? "All session sources. Elapsed time starts with the first drop in this price mode and includes idle time.\n"
                + "The recap is not saved after Reset, a character change or plugin restart."
                : "Saved observations only; history retention applies. Not a kill count or an all-time total.\n"
                + plugin.getJournalStatus())
            + "\nMissing prices are marked as pending. Only fully priced drops can win Best drop.");
        if (summary.pendingDrops > 0)
        { detailCopy(summary.pendingDrops + " drops have missing prices", new Color(230, 198, 131), 11, false); }
        if (!showingRecap && !plugin.getJournalNotice().isEmpty())
        { detailCopy(plugin.getJournalNotice(), new Color(230, 198, 131), 12, false); }
        detailContent.add(Box.createVerticalStrut(12));
        if (summary.drops == 0)
        {
            detailCopy(showingRecap ? "Your first observed drop starts this recap."
                : "No saved drops for this source and price mode.", MUTED, 12, false);
        }
        else
        {
            if (showingRecap)
            {
                detailCopy(elapsed(summary.firstAt, System.currentTimeMillis()) + " elapsed", MUTED, 12, false)
                    .setToolTipText("Since the first observed drop in this price mode; includes idle time");
            }
            else
            {
                String first = shortDate(summary.firstAt), last = shortDate(summary.lastAt);
                detailCopy(first.equals(last) ? first : first + " — " + last, MUTED, 12, false);
            }
            detailContent.add(Box.createVerticalStrut(16));
            detailSection(showingRecap ? "BEST DROP" : "BEST SAVED DROP");
            if (summary.bestDrop == null) { detailCopy("Waiting for a fully priced drop", MUTED, 12, false); }
            else
            {
                JPanel best = insightDrop(summary.bestDrop); best.setName("loot-insight-best");
                detailContent.add(best);
            }
            detailContent.add(Box.createVerticalStrut(12));
            int start = detailContent.getComponentCount();
            insightItem(summary.valuable, summary.valuable == null ? null
                : LootPopOverlay.formatExactGp(summary.valuable.item.getStackValue()), "No priced item yet");
            addDetailSection("top-item", "Top item", start);
            start = detailContent.getComponentCount();
            insightItem(summary.rarest, summary.rarest == null ? null : summary.rarest.item.getDropRate(),
                "No single known drop rate recorded");
            addDetailSection("rarest", "Rarest drop", start);
            start = detailContent.getComponentCount();
            if (summary.unlocks.isEmpty()) { detailCopy("No confirmed new log slots", MUTED, 12, false); }
            else
            {
                for (LootInsights.Highlight unlock : summary.unlocks) { insightItem(unlock, "New log slot", ""); }
                if (summary.logSlots() > summary.unlocks.size())
                { detailCopy("+" + (summary.logSlots() - summary.unlocks.size()) + " earlier slots", MUTED, 11, false); }
            }
            addDetailSection("collection", "Collection log  ·  " + summary.logSlots(), start);
            if (!showingRecap)
            {
                start = detailContent.getComponentCount();
                List<LootHistoryEntry> moments = observed.stream().filter(LootHistoryEntry::isMoment)
                    .limit(5).collect(java.util.stream.Collectors.toList());
                if (moments.isEmpty()) { detailCopy("No rare, log or record moments saved yet", MUTED, 12, false); }
                for (LootHistoryEntry entry : moments)
                {
                    detailCopy(shortDate(entry.getTimestamp()), MUTED, 11, false);
                    detailContent.add(insightDrop(entry)); detailContent.add(Box.createVerticalStrut(7));
                }
                addDetailSection("moments", "Recent moments", start);
            }
        }
        detailContent.add(Box.createVerticalStrut(16));
        if (!showingRecap)
        {
            JButton wiki = new JButton("Open source on OSRS Wiki"); styleHeaderButton(wiki);
            wiki.setName("loot-source-wiki");
            wiki.setAlignmentX(LEFT_ALIGNMENT); wiki.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            makeWikiLink(wiki, detailSource, "Open source on OSRS Wiki"); detailContent.add(wiki);
        }
    }

    /** Detail highlights are always visible; these headers are not disclosure controls. */
    private void addDetailSection(String key, String title, int start)
    {
        JPanel body = new JPanel(); body.setOpaque(false); body.setAlignmentX(LEFT_ALIGNMENT);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(9, 0, 6, 0));
        while (detailContent.getComponentCount() > start) { body.add(detailContent.getComponent(start)); }
        body.setName("loot-detail-body-" + key);
        JLabel header = new JLabel(title); header.setName("loot-detail-section-" + key);
        header.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); header.setForeground(MUTED);
        header.setOpaque(true); header.setBackground(CARD_BACKGROUND); header.setAlignmentX(LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 7));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        detailContent.add(header); detailContent.add(body);
    }

    private void detailSection(String text)
    {
        JLabel label = sectionTitle(text); label.setAlignmentX(LEFT_ALIGNMENT);
        detailContent.add(label); detailContent.add(Box.createVerticalStrut(4));
    }

    private JPanel insightDrop(LootHistoryEntry entry)
    {
        JPanel drop = new JPanel(); drop.setOpaque(false); drop.setAlignmentX(LEFT_ALIGNMENT);
        drop.setLayout(new BoxLayout(drop, BoxLayout.Y_AXIS));
        drop.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
            BorderFactory.createEmptyBorder(0, 0, 9, 0)));
        JLabel value = new JLabel(LootValueText.compact(entry.getTotalValue(), entry.hasPendingPrices()));
        value.setName("loot-insight-drop-value"); value.setForeground(Color.WHITE);
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19)); value.setAlignmentX(LEFT_ALIGNMENT);
        value.setToolTipText(LootValueText.detail(entry.getTotalValue(), entry.hasPendingPrices())); drop.add(value);
        JLabel source = new JLabel("<html><body style='width:145px'>" + escapeHtml(entry.getSourceName()) + "</body></html>");
        source.setForeground(MUTED); source.setAlignmentX(LEFT_ALIGNMENT);
        makeSourceLink(source, entry.getSourceName(), entry.getPriceMode());
        drop.add(Box.createVerticalStrut(3)); drop.add(source); drop.add(Box.createVerticalStrut(6));
        JPanel items = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        items.setOpaque(false); items.setAlignmentX(LEFT_ALIGNMENT);
        int visible = entry.getItems().size() > 5 ? 4 : Math.min(5, entry.getItems().size());
        for (int i = 0; i < visible; i++) { items.add(createItemIcon(entry.getItems().get(i))); }
        if (visible < entry.getItems().size()) { items.add(historyOverflow(entry, visible)); }
        drop.add(items);
        drop.setMaximumSize(new Dimension(Integer.MAX_VALUE, drop.getPreferredSize().height));
        return drop;
    }

    private JLabel detailCopy(String text, Color colour, int size, boolean bold)
    {
        JLabel label = new JLabel("<html><body style='width:145px'>" + escapeHtml(text) + "</body></html>");
        label.setForeground(colour); label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        label.setAlignmentX(LEFT_ALIGNMENT); detailContent.add(label); return label;
    }

    private void insightItem(LootInsights.Highlight highlight, String value, String empty)
    {
        if (highlight == null) { detailCopy(empty, MUTED, 12, false); return; }
        JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(false); row.setAlignmentX(LEFT_ALIGNMENT);
        JPanel icon = createItemIcon(highlight.item); row.add(icon, BorderLayout.WEST);
        JPanel copy = new JPanel(); copy.setOpaque(false); copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("<html><body style='width:112px'>" + escapeHtml(highlight.item.getName()) + "</body></html>");
        name.setForeground(Color.WHITE); name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        makeItemLink(name, highlight.item.getName(), buildItemTooltip(highlight.item)); copy.add(name);
        JLabel detail = new JLabel("<html><body style='width:112px'>" + escapeHtml(value) + "</body></html>");
        detail.setForeground(new Color(230, 198, 131)); detail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        copy.add(detail);
        JLabel source = new JLabel("<html><body style='width:112px'>" + escapeHtml(highlight.source) + "</body></html>");
        source.setForeground(MUTED); makeSourceLink(source, highlight.source);
        if (showingRecap || !highlight.source.equals(detailSource)) { copy.add(source); }
        row.add(copy, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        detailContent.add(row); detailContent.add(Box.createVerticalStrut(7));
    }

    static String elapsed(long firstAt, long now)
    {
        long minutes = Math.max(0, now - firstAt) / 60_000;
        return minutes < 1 ? "<1m" : minutes < 60 ? minutes + "m" : minutes / 60 + "h " + minutes % 60 + "m";
    }

    private static String shortDate(long timestamp)
    {
        return java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
            .format(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()));
    }

    static String wikiSearchUrl(String searchTerm)
    {
        return "https://oldschool.runescape.wiki/w/Special:Search?search="
            + URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
    }

    static String formatPanelQuantity(long quantity)
    {
        if (quantity < 1_000)
        {
            return "x" + quantity;
        }
        if (quantity < 1_000_000)
        {
            return quantity < 10_000
                ? String.format(Locale.US, "x%.1fK", quantity / 1_000.0)
                : "x" + quantity / 1_000 + "K";
        }
        if (quantity < 1_000_000_000)
        {
            return quantity < 10_000_000
                ? String.format(Locale.US, "x%.1fM", quantity / 1_000_000.0)
                : "x" + quantity / 1_000_000 + "M";
        }
        return "x" + quantity / 1_000_000_000 + "B";
    }

    private String buildItemTooltip(LootHistoryItem item)
    { return itemTooltip(item, config.rarityPalette()); }

    static String itemTooltip(LootHistoryItem item, RarityPalette palette)
    {
        String itemName = item.getName();
        long gePrice = item.getGePrice();
        long highAlchemy = item.getHighAlchemyPrice();
        String dropChance = item.getDropRate() == null ? "N/A" : item.getDropRate();
        DropRarity rarity = item.getRarity();
        Color rarityColour = LootTooltipStyle.rarity(rarity, palette);
        String rarityHex = colourHex(rarityColour);

        StringBuilder text = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px'><b><font color='#"
            + rarityHex + "'>" + escapeHtml(itemName) + "</font></b>");
        if (item.getQuantity() > 1) { text.append(htmlRow("Qty", String.format(Locale.US, "%,d", item.getQuantity()), Color.WHITE)); }
        text.append(htmlRow("GE", item.isGePending() ? "Price pending" : LootPopOverlay.formatExactGp(gePrice),
            item.isGePending() ? MUTED : rarityColour));
        text.append(htmlRow("HA", LootPopOverlay.formatExactGp(highAlchemy),
            LootTooltipStyle.highAlchemy(gePrice, highAlchemy, item.isGePending())));
        if (item.getQuantity() > 1)
        { text.append(htmlRow("Stack", item.isPricePending() ? "Price pending" : LootPopOverlay.formatExactGp(item.getStackValue()), Color.WHITE)); }
        text.append(htmlRow("Chance", dropChance, rarityColour));
        return text.append("<br><font color='#bfc5cf'>Click to open OSRS Wiki</font></body></html>").toString();
    }

    private JLabel historyOverflow(LootHistoryEntry entry, int startIndex)
    {
        JLabel overflow = new JLabel("+" + (entry.getItems().size() - startIndex), SwingConstants.CENTER)
        {
            @Override public javax.swing.JToolTip createToolTip()
            {
                java.util.List<HiddenItemsTooltip.ItemRow> rows = new java.util.ArrayList<>();
                for (int i = startIndex; i < entry.getItems().size() && rows.size() < 8; i++)
                {
                    LootHistoryItem item = entry.getItems().get(i);
                    rows.add(new HiddenItemsTooltip.ItemRow(historyImage(item), item.getName()
                        + (item.getQuantity() > 1 ? " x" + String.format(Locale.US, "%,d", item.getQuantity()) : ""),
                        item.isPricePending() ? "Price pending" : LootPopOverlay.formatExactGp(item.getStackValue()),
                        LootTooltipStyle.rarity(item.getRarity(), config.rarityPalette())));
                }
                return new HiddenItemsTooltip(entry.getItems().size() - startIndex, rows,
                    new Font(Font.SANS_SERIF, Font.PLAIN, 12)).swingTip(this);
            }
        };
        overflow.setName("loot-overflow"); overflow.setPreferredSize(new Dimension(LootArtwork.OVERFLOW_WIDTH, 36));
        overflow.setVerticalAlignment(SwingConstants.CENTER); overflow.setForeground(Color.WHITE);
        overflow.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12)); overflow.setToolTipText("Hidden items");
        return overflow;
    }

    private BufferedImage historyImage(LootHistoryItem item)
    { try { return plugin.getHistoryImage(item); } catch (RuntimeException ignored) { return null; } }

    private static String htmlRow(String label, String value, Color colour)
    { return "<br><font color='#aaaaaa'>" + label + ":</font> <font color='#" + colourHex(colour) + "'>" + escapeHtml(value) + "</font>"; }

    private static String escapeHtml(String value)
    {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String colourHex(Color colour)
    {
        return String.format("%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        refreshing = true;
        try
        {
            String selectedSource = (String) sessionSource.getSelectedItem();
            boolean shelf = journalMode && journalView.getSelectedIndex() == 3;
            List<LootHistoryEntry> archive = shelf ? plugin.getTrophyEntries() : plugin.getJournalEntries();
            PriceMode basis = (PriceMode) journalPrice.getSelectedItem();
            List<String> sources = journalMode ? archive.stream().filter(e -> e.getPriceMode() == basis)
                .map(LootHistoryEntry::getSourceName).distinct().sorted().collect(java.util.stream.Collectors.toList())
                : plugin.getSessionSources();
            boolean sourceListChanged = sessionSource.getItemCount() != sources.size() + 1;
            if (!sourceListChanged)
            {
                for (int i = 0; i < sources.size(); i++)
                {
                    if (!sources.get(i).equals(sessionSource.getItemAt(i + 1)))
                    {
                        sourceListChanged = true;
                        break;
                    }
                }
            }
            if (sourceListChanged)
            {
                sessionSource.removeAllItems();
                sessionSource.addItem(ALL_SOURCES);
                for (String source : sources)
                {
                    sessionSource.addItem(source);
                }
                sessionSource.setSelectedItem(selectedSource == null ? ALL_SOURCES : selectedSource);
                if (sessionSource.getSelectedIndex() < 0)
                {
                    sessionSource.setSelectedIndex(0);
                }
            }

            SessionSnapshot session = plugin.getSessionSnapshot((String) sessionSource.getSelectedItem());
            dropsValue.setText(Integer.toString(session.getDrops()));
            totalValue.setText(LootValueText.compact(session.getTotalValue(), session.getPendingDrops() > 0));
            totalValue.setToolTipText(LootValueText.detail(session.getTotalValue(), session.getPendingDrops() > 0));
            averageValue.setText(session.getPendingDrops() > 0 ? "Pending" : LootPopOverlay.formatGp(session.getAverageValue()).replace(" gp", ""));
            averageValue.setToolTipText(session.getPendingDrops() > 0 ? "Average unavailable: " + session.getPendingDrops() + " drops have incomplete prices"
                : LootPopOverlay.formatExactGp(session.getAverageValue()));
            bestValue.setText(session.getBestValue() <= 0L
                ? "—"
                : LootPopOverlay.formatGp(session.getBestValue()).replace(" gp", ""));
            bestValue.setToolTipText(session.getBestSource() + " / " + LootPopOverlay.formatExactGp(session.getBestValue()));

            sessionGrid.setVisible(!journalMode);
            recapButton.setVisible(!journalMode);
            sourceProfileButton.setVisible(sessionSource.getSelectedItem() != null
                && !ALL_SOURCES.equals(sessionSource.getSelectedItem()));
            journalSummary.setVisible(journalMode);
            scopeTitle.setText(journalMode ? "LOOT JOURNAL" : "SESSION");
            resetButton.setText(journalMode ? "Clear" : "Reset");
            resetButton.setEnabled(!journalMode || plugin.canClearJournal());
            historyTitle.setText(shelf ? "TROPHY SHELF" : journalMode ? "TIMELINE" : "RECENT LOOT");
            trophyKind.setVisible(shelf);
            journalLayout.setVisible(!shelf);
            String sourceFilter = (String) sessionSource.getSelectedItem();
            List<LootHistoryEntry> scoped = archive.stream().filter(e -> e.getPriceMode() == basis)
                .filter(e -> sourceFilter == null || ALL_SOURCES.equals(sourceFilter) || sourceFilter.equals(e.getSourceName()))
                .collect(java.util.stream.Collectors.toList());
            long sum = scoped.stream().mapToLong(LootHistoryEntry::getTotalValue).sum();
            boolean incomplete = scoped.stream().anyMatch(LootHistoryEntry::hasPendingPrices);
            journalTotal.setText(shelf ? Integer.toString(scoped.size()) : LootValueText.compact(sum, incomplete));
            journalTotal.setToolTipText(shelf ? "Trophies use a separate retention limit and do not expire with regular history"
                : LootValueText.detail(sum, incomplete) + " / values when the drops were observed");
            long moments = scoped.stream().filter(LootHistoryEntry::isMoment).count();
            journalCount.setText(shelf ? "saved trophies / no age expiry" : scoped.size() + " drops  \u00b7  " + moments + " moments");
            LootHistoryEntry top = scoped.stream().filter(e -> !e.hasPendingPrices())
                .max(java.util.Comparator.comparingLong(LootHistoryEntry::getTotalValue)).orElse(null);
            journalBest.setText(top == null ? "No saved record yet" : "Best  " + LootPopOverlay.formatGp(top.getTotalValue()));
            journalBest.setToolTipText(top == null ? null : top.getSourceName());
            journalInfo.setVisible(journalMode);
            updateInformation(journalInfo, plugin.getJournalStatus()
                + "\nObserved drops only; history retention applies. Prices are captured at drop time, not revalued live."
                + "\nGE and HA are separate. Totals show loot value, not net profit.");
            String notice = plugin.getJournalNotice();
            journalNotice.setVisible(!notice.isEmpty());
            journalNotice.setText(notice.isEmpty() ? "" : "<html><body style='width:145px'>" + escapeHtml(notice) + "</body></html>");

            historyList.removeAll();
            List<LootHistoryEntry> history = journalMode ? scoped.stream()
                .filter(e -> shelf ? trophyMatches(e) : journalView.getSelectedIndex() == 0
                    || (journalView.getSelectedIndex() == 1 ? e.isMoment() : e.isPersonalBest()))
                .collect(java.util.stream.Collectors.toList()) : plugin.getLootHistory().stream()
                .filter(e -> sourceFilter == null || ALL_SOURCES.equals(sourceFilter) || sourceFilter.equals(e.getSourceName()))
                .collect(java.util.stream.Collectors.toList());
            dateJump.setVisible(journalMode && !shelf && !history.isEmpty());
            if (history.isEmpty())
            {
                if (journalMode)
                {
                    if (!shelf)
                    {
                        daySelectionInitialized = false; latestDay = null; focusedDay = null; expandedDays.clear();
                        dateKeys.clear(); dateJump.removeAllItems();
                    }
                    JLabel empty = new JLabel(shelf ? "<html>Your shelf starts with source bests,<br>Ultra rare drops and new log slots.</html>"
                        : "<html>No saved drops in this view.<br>Only observed loot is recorded.</html>");
                    empty.setForeground(MUTED); empty.setBorder(BorderFactory.createEmptyBorder(12, 2, 16, 2));
                    historyList.add(empty);
                }
                else { historyList.add(emptyHistoryCard()); }
            }
            else if (journalMode && !shelf)
            {
                addDateGroups(history);
            }
            else
            {
                for (LootHistoryEntry entry : history.subList(0, Math.min(historyPageSize, history.size())))
                {
                    try
                    {
                        historyList.add(shelf ? trophyCard(entry) : historyCard(entry));
                    }
                    catch (RuntimeException ignored)
                    {
                        historyList.add(historyFallbackCard(entry));
                    }
                    historyList.add(Box.createVerticalStrut(7));
                }
            }
            historyList.revalidate();
            historyList.repaint();
            olderButton.setVisible((!journalMode || shelf) && history.size() > historyPageSize);
            refreshDetail();
            readableFonts(this);
            revalidate(); repaint();
        }
        finally
        {
            refreshing = false;
        }
    }

    private void addDateGroups(List<LootHistoryEntry> history)
    {
        java.util.Map<java.time.LocalDate, List<LootHistoryEntry>> days = LootDaySummary.group(history,
            java.time.ZoneId.systemDefault());
        java.time.LocalDate newest = days.keySet().iterator().next();
        if (!daySelectionInitialized)
        {
            expandedDays.clear(); expandedDays.add(newest); focusedDay = newest;
            daySelectionInitialized = true;
        }
        else
        {
            expandedDays.retainAll(days.keySet());
            if (!newest.equals(latestDay)) { expandedDays.add(newest); focusedDay = newest; }
            if (focusedDay != null && !days.containsKey(focusedDay))
            { focusedDay = expandedDays.stream().max(java.util.Comparator.naturalOrder()).orElse(null); }
        }
        latestDay = newest;
        java.util.List<java.time.LocalDate> nextKeys = new java.util.ArrayList<>(days.keySet());
        if (!dateKeys.equals(nextKeys))
        {
            dateKeys.clear(); dateKeys.addAll(nextKeys);
            dateJump.removeAllItems(); dateJump.addItem("Jump to date...");
            for (java.time.LocalDate day : dateKeys)
            { dateJump.addItem(day.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))); }
        }
        int selectedDate = focusedDay == null ? 0 : dateKeys.indexOf(focusedDay) + 1;
        if (dateJump.getSelectedIndex() != selectedDate) { dateJump.setSelectedIndex(selectedDate); }
        days.forEach((day, entries) ->
        {
            boolean open = expandedDays.contains(day);
            JToggleButton date = new JToggleButton(day.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
                + "  \u00b7 " + entries.size(), open)
            {
                @Override protected void paintComponent(Graphics graphics)
                {
                    super.paintComponent(graphics);
                    Graphics copy = graphics.create();
                    copy.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                    copy.setColor(getModel().isRollover() ? Color.WHITE : MUTED);
                    copy.drawString(isSelected() ? "\u2212" : "+", getWidth() - 22, 23);
                    copy.dispose();
                }
            };
            date.setName("journal-date-" + day);
            date.setHorizontalAlignment(SwingConstants.LEFT);
            date.setAlignmentX(LEFT_ALIGNMENT);
            date.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            date.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            date.setPreferredSize(new Dimension(200, 34));
            date.setFocusable(false);
            date.setRolloverEnabled(true);
            date.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            date.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI()
            {
                @Override protected void paintButtonPressed(Graphics graphics, javax.swing.AbstractButton button)
                {
                    graphics.setColor(button.getModel().isRollover() ? new Color(73, 45, 52) : new Color(55, 37, 43));
                    graphics.fillRect(0, 0, button.getWidth(), button.getHeight());
                }
            });
            date.setForeground(open ? Color.WHITE : MUTED);
            date.setBackground(open ? new Color(55, 37, 43) : CARD_BACKGROUND);
            date.addChangeListener(event -> date.setBackground(date.getModel().isRollover()
                ? new Color(53, 57, 64) : open ? new Color(55, 37, 43) : CARD_BACKGROUND));
            date.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(
                open ? new Color(127, 61, 72) : new Color(82, 88, 98)),
                BorderFactory.createEmptyBorder(6, 8, 6, 30)));
            long total = entries.stream().mapToLong(LootHistoryEntry::getTotalValue).sum();
            date.setToolTipText("Click to " + (open ? "collapse" : "expand") + " this date / " + entries.size() + " drops / " + LootValueText.detail(total,
                entries.stream().anyMatch(LootHistoryEntry::hasPendingPrices)));
            date.addActionListener(event ->
            {
                if (open) { expandedDays.remove(day); } else { expandedDays.add(day); }
                focusedDay = day;
                historyPageSize = 50; refresh(); scrollToExpandedDate();
            });
            historyList.add(date); historyList.add(Box.createVerticalStrut(7));
            if (open)
            {
                if (journalLayout.getSelectedItem() == JournalLayout.DAILY) { addDailyItems(entries); }
                else
                {
                    for (LootHistoryEntry entry : entries.subList(0, Math.min(historyPageSize, entries.size())))
                    {
                        try { historyList.add(historyCard(entry)); }
                        catch (RuntimeException ignored) { historyList.add(historyFallbackCard(entry)); }
                        historyList.add(Box.createVerticalStrut(7));
                    }
                    if (entries.size() > historyPageSize) { addDayMore("Load more drops"); }
                }
            }
        });
    }

    private void addDailyItems(List<LootHistoryEntry> entries)
    {
        JPanel daily = cardPanel();
        daily.setName("daily-loot-grid");
        daily.setLayout(new BoxLayout(daily, BoxLayout.Y_AXIS));
        long total = entries.stream().mapToLong(LootHistoryEntry::getTotalValue).sum();
        boolean pending = entries.stream().anyMatch(LootHistoryEntry::hasPendingPrices);
        JPanel heading = new JPanel(new BorderLayout(3, 0));
        heading.setOpaque(false); heading.setAlignmentX(LEFT_ALIGNMENT);
        JLabel count = new JLabel(entries.size() + (entries.size() == 1 ? " drop" : " drops")); count.setForeground(MUTED);
        JLabel value = new JLabel(LootValueText.compact(total, pending)); value.setForeground(Color.WHITE);
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        value.setToolTipText(LootValueText.detail(total, pending) + " / recorded at drop time");
        heading.add(count, BorderLayout.WEST); heading.add(value, BorderLayout.EAST);
        daily.add(heading); daily.add(Box.createVerticalStrut(5));
        long sources = entries.stream().map(LootHistoryEntry::getSourceName).distinct().count();
        JLabel basis = new JLabel(sources + (sources == 1 ? " source" : " sources") + " \u00b7 "
            + (entries.get(0).getPriceMode() == PriceMode.HIGH_ALCHEMY ? "HA" : "GE") + " at drop time");
        basis.setForeground(MUTED); basis.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        daily.add(basis); daily.add(Box.createVerticalStrut(10));
        List<LootDaySummary.Item> items = LootDaySummary.items(entries);
        int visible = Math.min(historyPageSize, items.size());
        for (int start = 0; start < visible; start += 4)
        {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            row.setOpaque(false); row.setAlignmentX(LEFT_ALIGNMENT);
            for (int index = start; index < Math.min(start + 4, visible); index++)
            {
                LootDaySummary.Item item = items.get(index);
                JPanel icon = createItemIcon(item.sample, item.quantity, dailyItemTooltip(item));
                icon.setPreferredSize(new Dimension(42, 40)); row.add(icon);
            }
            daily.add(row); daily.add(Box.createVerticalStrut(4));
        }
        historyList.add(daily); historyList.add(Box.createVerticalStrut(9));
        if (items.size() > visible) { addDayMore("Load more items"); }
    }

    private void scrollToExpandedDate()
    {
        SwingUtilities.invokeLater(() ->
        {
            for (Component component : historyList.getComponents())
            {
                if (component instanceof JToggleButton && focusedDay != null
                    && ("journal-date-" + focusedDay).equals(component.getName()))
                {
                    ((JToggleButton) component).scrollRectToVisible(new java.awt.Rectangle(0, 0,
                        component.getWidth(), component.getHeight() + 150));
                    return;
                }
            }
        });
    }

    private void addDayMore(String label)
    {
        JButton more = new JButton(label); styleHeaderButton(more); more.setAlignmentX(LEFT_ALIGNMENT);
        more.addActionListener(event -> { historyPageSize += 50; refresh(); });
        historyList.add(more); historyList.add(Box.createVerticalStrut(7));
    }

    private String dailyItemTooltip(LootDaySummary.Item item)
    { return dailyTooltip(item, config.rarityPalette()); }

    static String dailyTooltip(LootDaySummary.Item item, RarityPalette palette)
    {
        Color rarity = LootTooltipStyle.rarity(item.sample.getRarity(), palette);
        StringBuilder text = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px'><b><font color='#"
            + colourHex(rarity) + "'>" + escapeHtml(item.sample.getName()) + "</font></b>");
        if (item.quantity > 1) { text.append(htmlRow("Qty", String.format(Locale.US, "%,d", item.quantity), Color.WHITE)); }
        text.append(htmlRow("GE", priceRange(item.geMin, item.geMax) + (item.gePending ? " / prices pending" : ""), item.gePending ? MUTED : rarity));
        text.append(htmlRow("HA", priceRange(item.haMin, item.haMax), item.allHaAboveGe ? LootTooltipStyle.ALCH_PROFIT : Color.WHITE));
        if (item.quantity > 1) { text.append(htmlRow("Stack", LootValueText.detail(item.value, item.pending), Color.WHITE)); }
        text.append(htmlRow("Chance", item.chance(), rarity));
        return text.append("<br><font color='#bfc5cf'>Recorded prices, not live prices<br>Click to open OSRS Wiki</font></body></html>").toString();
    }

    private static String priceRange(long min, long max)
    {
        return min == Long.MAX_VALUE ? "N/A" : min == max ? LootPopOverlay.formatExactGp(min)
            : LootPopOverlay.formatExactGp(min) + " \u2013 " + LootPopOverlay.formatExactGp(max);
    }

    private static void readableFonts(java.awt.Container container)
    {
        for (Component component : container.getComponents())
        {
            Font font = component.getFont();
            if (font != null) { component.setFont(new Font(Font.SANS_SERIF, font.getStyle(), Math.max(11, font.getSize()))); }
            if (component instanceof java.awt.Container) { readableFonts((java.awt.Container) component); }
        }
    }

    @Override public void paint(Graphics graphics)
    {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        super.paint(copy); copy.dispose();
    }

    private static final class ScrollColumn extends JPanel implements Scrollable
    {
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(242, 0); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int axis, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int axis, int direction)
        { return Math.max(24, r.height - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    void syncControls()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::syncControls);
            return;
        }
        syncing = true;
        visualMode.setSelectedItem(config.visualMode());
        popupStyle.setSelectedItem(config.popupStyle());
        adaptiveTime.setSelected(config.adaptiveDisplayTime());
        screenshots.setSelected(config.automaticScreenshots());
        sealedDrops.setSelected(config.sealedDrops());
        sealedDrops.setEnabled(config.visualMode() == VisualMode.ENHANCED);
        journalLayout.setSelectedItem(config.journalLayout());
        syncing = false;
    }

    private void wireQuickControls()
    {
        visualMode.addActionListener(event -> setConfig("visualMode", visualMode.getSelectedItem()));
        popupStyle.addActionListener(event -> setConfig("popupStyle", popupStyle.getSelectedItem()));
        adaptiveTime.addActionListener(event -> setConfig("adaptiveDisplayTime", adaptiveTime.isSelected()));
        screenshots.addActionListener(event -> setConfig("automaticScreenshots", screenshots.isSelected()));
        sealedDrops.addActionListener(event -> setConfig("sealedDrops", sealedDrops.isSelected()));
    }

    private void setConfig(String key, Object value)
    {
        if (!syncing && configManager != null)
        {
            configManager.setConfiguration(LootPopConfig.GROUP, key, value);
        }
    }

    private static JPanel controlRow(String label, java.awt.Component control)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setOpaque(false);
        JLabel text = new JLabel(label);
        text.setForeground(MUTED);
        row.add(text, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        return row;
    }

    private static JPanel cardPanel()
    {
        JPanel panel = new RoundedPanel(CARD_BACKGROUND, CARD_BORDER, null);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static JLabel sectionTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 1, 6, 0));
        return label;
    }

    private static JLabel valueLabel()
    {
        JLabel label = new JLabel("0");
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        return label;
    }

    private static void styleCheckBox(JCheckBox checkBox)
    {
        checkBox.setOpaque(false);
        checkBox.setForeground(Color.WHITE);
        checkBox.setFocusable(false);
    }

    private static void styleCombo(JComboBox<?> combo)
    {
        combo.setBackground(CARD_BACKGROUND); combo.setForeground(Color.WHITE);
        combo.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        combo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        combo.setRenderer(new javax.swing.DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                boolean selected, boolean focused)
            {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.setFont(combo.getFont());
                label.setBackground(selected ? new Color(63, 43, 49) : CARD_BACKGROUND);
                label.setForeground(Color.WHITE);
                label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                return label;
            }
        });
        combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI()
        {
            @Override public void paintCurrentValueBackground(Graphics graphics, java.awt.Rectangle bounds, boolean focused)
            {
                graphics.setColor(CARD_BACKGROUND);
                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            @Override public void paintCurrentValue(Graphics graphics, java.awt.Rectangle bounds, boolean focused)
            {
                Graphics2D g = (Graphics2D) graphics.create();
                g.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g.setFont(combo.getFont()); g.setColor(combo.isEnabled() ? Color.WHITE : MUTED);
                Object selected = combo.getSelectedItem();
                if (selected != null)
                {
                    int baseline = bounds.y + (bounds.height - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
                    g.drawString(selected.toString(), bounds.x + 6, baseline);
                }
                g.dispose();
            }
            @Override protected JButton createArrowButton()
            {
                JButton button = new JButton("\u25be");
                button.setFocusable(false); button.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                button.setBackground(CARD_BACKGROUND); button.setForeground(MUTED);
                return button;
            }
        });
    }

    private static void styleFilterButton(JToggleButton button)
    {
        button.setAlignmentX(LEFT_ALIGNMENT); button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(200, 34));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        button.setBackground(CARD_BACKGROUND); button.setForeground(Color.WHITE);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(77, 84, 94)), BorderFactory.createEmptyBorder(6, 8, 6, 30)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setRolloverEnabled(true);
        button.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI()
        {
            @Override public void paint(Graphics graphics, JComponent component)
            {
                super.paint(graphics, component);
                Graphics copy = graphics.create(); copy.setColor(button.getForeground());
                copy.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                int baseline = (component.getHeight() - copy.getFontMetrics().getHeight()) / 2 + copy.getFontMetrics().getAscent();
                copy.drawString(button.isSelected() ? "−" : "+", component.getWidth() - 23, baseline); copy.dispose();
            }
            @Override protected void paintButtonPressed(Graphics graphics, javax.swing.AbstractButton component)
            { graphics.setColor(new Color(43, 48, 55)); graphics.fillRect(0, 0, component.getWidth(), component.getHeight()); }
        });
        button.addChangeListener(event -> button.setBackground(button.getModel().isRollover()
            ? new Color(45, 50, 57) : CARD_BACKGROUND));
    }

    private static void configureInformation(JButton button, String name)
    {
        styleHeaderButton(button); button.setName(name); button.setFocusable(true);
        button.setPreferredSize(new Dimension(26, 28));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.getAccessibleContext().setAccessibleName("About these values");
        button.addActionListener(event -> JOptionPane.showMessageDialog(button,
            button.getClientProperty("loot-info-text"), "About these values", JOptionPane.INFORMATION_MESSAGE));
    }

    private static void updateInformation(JButton button, String text)
    {
        button.putClientProperty("loot-info-text", text);
        button.getAccessibleContext().setAccessibleDescription(text);
        button.setToolTipText("<html><body style='width:190px'>" + escapeHtml(text).replace("\n", "<br><br>") + "</body></html>");
    }

    private static void styleHeaderButton(JButton button)
    {
        button.setFocusable(false);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(43, 46, 51));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(3, 7, 3, 7)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static Color rarityColour(DropRarity rarity)
    {
        switch (rarity)
        {
            case UNCOMMON:
                return new Color(105, 209, 125);
            case RARE:
                return new Color(93, 173, 226);
            case VERY_RARE:
                return new Color(199, 125, 255);
            case ULTRA_RARE:
                return ACCENT;
            default:
                return MUTED;
        }
    }

    static BufferedImage createNavigationIcon()
    {
        BufferedImage brandIcon = ImageUtil.loadImageResource(LootPopPanel.class, "/loot-pop-nav.png");
        return ImageUtil.resizeImage(trimTransparentPadding(brandIcon), 32, 32);
    }

    static BufferedImage trimTransparentPadding(BufferedImage image)
    {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if ((image.getRGB(x, y) >>> 24) > 8)
                {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY)
        {
            return image;
        }

        int contentWidth = maxX - minX + 1;
        int contentHeight = maxY - minY + 1;
        int squareSize = Math.max(contentWidth, contentHeight);
        int centerX = (minX + maxX) / 2;
        int centerY = (minY + maxY) / 2;
        int cropX = Math.max(0, Math.min(centerX - squareSize / 2, image.getWidth() - squareSize));
        int cropY = Math.max(0, Math.min(centerY - squareSize / 2, image.getHeight() - squareSize));

        return image.getSubimage(cropX, cropY, squareSize, squareSize);
    }

    private static final class GearIcon implements javax.swing.Icon
    {
        @Override public int getIconWidth() { return 20; }
        @Override public int getIconHeight() { return 20; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.geom.Path2D gear = new java.awt.geom.Path2D.Double(java.awt.geom.Path2D.WIND_EVEN_ODD);
            for (int i = 0; i < 32; i++)
            {
                double angle = i * Math.PI / 16;
                double radius = i % 4 < 2 ? 9 : 7;
                double px = x + 10 + Math.cos(angle) * radius;
                double py = y + 10 + Math.sin(angle) * radius;
                if (i == 0) { gear.moveTo(px, py); } else { gear.lineTo(px, py); }
            }
            gear.closePath();
            gear.append(new java.awt.geom.Ellipse2D.Double(x + 7, y + 7, 6, 6), false);
            g.setColor(component.getForeground()); g.fill(gear); g.dispose();
        }
    }

    private static class RoundedPanel extends JPanel
    {
        private final Color fill;
        private final Color border;
        private final Color rail;

        RoundedPanel(Color fill, Color border, Color rail)
        {
            this.fill = fill;
            this.border = border;
            this.rail = rail;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth() - 1;
            int height = getHeight() - 1;
            g.setColor(fill);
            g.fillRoundRect(0, 0, width, height, 10, 10);
            if (rail != null)
            {
                Shape previousClip = g.getClip();
                g.clip(new RoundRectangle2D.Float(0, 0, width, height, 10, 10));
                g.setColor(rail);
                g.fillRect(0, 0, 4, height);
                g.setClip(previousClip);
            }
            g.setColor(border);
            g.drawRoundRect(0, 0, width, height, 10, 10);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ItemSlot extends JPanel
    {
        private final Color accent;
        private final long quantity;
        private boolean hovered;

        ItemSlot(Color accent, long quantity)
        {
            this.accent = accent;
            this.quantity = quantity;
            setOpaque(false);
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent event)
                {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event)
                {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth() - 1;
            int height = getHeight() - 1;
            g.setColor(hovered ? new Color(48, 51, 56) : SLOT_BACKGROUND);
            g.fillRoundRect(0, 0, width, height, 8, 8);
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), hovered ? 205 : 85));
            g.drawRoundRect(0, 0, width, height, 8, 8);
            if (hovered)
            {
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 28));
                g.fillRoundRect(2, 2, width - 3, height - 3, 6, 6);
            }
            g.dispose();
            super.paintComponent(graphics);
        }

        @Override
        protected void paintChildren(Graphics graphics)
        {
            super.paintChildren(graphics);
            if (quantity <= 1)
            {
                return;
            }

            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont().deriveFont(Font.BOLD, 11f));
            String label = formatPanelQuantity(quantity);
            g.setColor(new Color(8, 10, 13, 220));
            g.fillRoundRect(1, 1, g.getFontMetrics().stringWidth(label) + 3, 13, 3, 3);
            g.setColor(new Color(0, 0, 0, 225));
            g.drawString(label, 3, 12);
            g.drawString(label, 2, 13);
            g.setColor(new Color(125, 255, 145));
            g.drawString(label, 2, 12);
            g.dispose();
        }
    }
}
