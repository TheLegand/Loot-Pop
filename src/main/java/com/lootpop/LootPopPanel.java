package com.lootpop;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

final class LootPopPanel extends PluginPanel
{
    static final String ALL_SOURCES = "All sources";
    private static final Color PANEL_BACKGROUND = new Color(26, 28, 31);
    private static final Color CARD_BACKGROUND = new Color(36, 39, 43);
    private static final Color CARD_BORDER = new Color(61, 66, 73);
    private static final Color SLOT_BACKGROUND = new Color(20, 22, 25);
    private static final Color ACCENT = new Color(255, 77, 94);
    private static final Color MUTED = new Color(170, 176, 184);

    private final LootPopPlugin plugin;
    private final LootPopConfig config;
    private final ConfigManager configManager;
    private final JLabel dropsValue = valueLabel();
    private final JLabel totalValue = valueLabel();
    private final JLabel averageValue = valueLabel();
    private final JLabel bestValue = valueLabel();
    private final JPanel historyList = new JPanel();
    private final JComboBox<VisualMode> visualMode = new JComboBox<>(VisualMode.values());
    private final JComboBox<PopupStyle> popupStyle = new JComboBox<>(PopupStyle.values());
    private final JCheckBox adaptiveTime = new JCheckBox("Adaptive display time");
    private final JCheckBox screenshots = new JCheckBox("Automatic rare screenshots");
    private final JComboBox<String> sessionSource = new JComboBox<>();
    private JPanel quickControlsPanel;
    private boolean syncing;
    private boolean refreshing;

    LootPopPanel(
        LootPopPlugin plugin,
        LootPopConfig config,
        ConfigManager configManager
    )
    {
        super(false);
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;

        setLayout(new BorderLayout());
        setBackground(PANEL_BACKGROUND);
        add(buildContent(), BorderLayout.NORTH);
        wireQuickControls();
        syncControls();
        refresh();
    }

    private JPanel buildContent()
    {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(PANEL_BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 14, 8));

        JPanel sessionHeader = new JPanel(new BorderLayout());
        sessionHeader.setOpaque(false);
        sessionHeader.setAlignmentX(LEFT_ALIGNMENT);
        sessionHeader.add(sectionTitle("SESSION"), BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        JButton reset = new JButton("Reset");
        styleHeaderButton(reset);
        reset.addActionListener(event -> plugin.clearSession());
        actions.add(reset);

        JButton settingsToggle = new JButton("\u2699");
        styleHeaderButton(settingsToggle);
        settingsToggle.setToolTipText("Show or hide popup controls");
        settingsToggle.setPreferredSize(new Dimension(30, 24));
        settingsToggle.addActionListener(event ->
        {
            boolean visible = !quickControlsPanel.isVisible();
            quickControlsPanel.setVisible(visible);
            settingsToggle.setForeground(visible ? ACCENT : Color.WHITE);
            revalidate();
            repaint();
        });
        actions.add(settingsToggle);
        sessionHeader.add(actions, BorderLayout.EAST);
        content.add(sessionHeader);

        quickControlsPanel = buildQuickControls();
        quickControlsPanel.setVisible(false);
        content.add(quickControlsPanel);
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
        content.add(Box.createVerticalStrut(7));
        content.add(buildSessionGrid());
        content.add(Box.createVerticalStrut(14));

        content.add(sectionTitle("RECENT LOOT"));
        historyList.setLayout(new BoxLayout(historyList, BoxLayout.Y_AXIS));
        historyList.setOpaque(false);
        historyList.setAlignmentX(LEFT_ALIGNMENT);
        content.add(historyList);
        return content;
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
        JPanel grid = new JPanel(new GridLayout(2, 2, 7, 7));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        grid.add(statCard("DROPS", dropsValue, new Color(93, 173, 226)));
        grid.add(statCard("TOTAL", totalValue, new Color(105, 209, 125)));
        grid.add(statCard("AVERAGE", averageValue, new Color(199, 125, 255)));
        grid.add(statCard("BEST", bestValue, ACCENT));
        return grid;
    }

    private JPanel statCard(String title, JLabel value, Color colour)
    {
        JPanel panel = new RoundedPanel(CARD_BACKGROUND, CARD_BORDER, colour);
        panel.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 8));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setForeground(colour);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        panel.add(label);
        panel.add(Box.createVerticalStrut(3));
        panel.add(value);
        return panel;
    }

    private JPanel historyCard(LootHistoryEntry entry)
    {
        Color rarityColour = rarityColour(entry.getRarity());
        JPanel card = new RoundedPanel(CARD_BACKGROUND, CARD_BORDER, rarityColour);
        card.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 8));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);

        JPanel sourceGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sourceGroup.setOpaque(false);
        JLabel source = new JLabel(entry.getSourceName());
        source.setForeground(Color.WHITE);
        source.setFont(source.getFont().deriveFont(Font.BOLD));
        makeWikiLink(source, entry.getSourceName(), "Open NPC/source on OSRS Wiki");
        sourceGroup.add(source);
        if (entry.isCollectionLogUnlock())
        {
            JLabel newBadge = new JLabel(" NEW ");
            newBadge.setForeground(new Color(35, 28, 8));
            newBadge.setBackground(new Color(255, 205, 72));
            newBadge.setOpaque(true);
            newBadge.setFont(newBadge.getFont().deriveFont(Font.BOLD, 9f));
            newBadge.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
            sourceGroup.add(Box.createHorizontalStrut(6));
            sourceGroup.add(newBadge);
        }
        header.add(sourceGroup, BorderLayout.WEST);

        JLabel price = new JLabel(LootPopOverlay.formatGp(entry.getTotalValue()));
        price.setForeground(rarityColour);
        price.setFont(price.getFont().deriveFont(Font.BOLD, 12f));
        price.setHorizontalAlignment(SwingConstants.RIGHT);
        price.setToolTipText(LootPopOverlay.formatExactGp(entry.getTotalValue()));
        header.add(price, BorderLayout.EAST);
        card.add(header);
        card.add(Box.createVerticalStrut(5));

        JPanel itemRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        itemRow.setOpaque(false);
        itemRow.setAlignmentX(LEFT_ALIGNMENT);
        int visibleItems = Math.min(5, entry.getItems().size());
        for (int i = 0; i < visibleItems; i++)
        {
            LootHistoryItem item = entry.getItems().get(i);
            JPanel icon = createItemIcon(item);
            itemRow.add(icon);
        }
        if (entry.getItems().size() > visibleItems)
        {
            JLabel overflow = new JLabel("+" + (entry.getItems().size() - visibleItems));
            overflow.setForeground(Color.WHITE);
            overflow.setFont(overflow.getFont().deriveFont(Font.BOLD, 12f));
            overflow.setBorder(BorderFactory.createEmptyBorder(8, 4, 0, 0));
            overflow.setToolTipText(buildOverflowTooltip(entry, visibleItems));
            itemRow.add(overflow);
        }
        card.add(itemRow);
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
        card.add(source, BorderLayout.WEST);

        JLabel value = new JLabel(LootPopOverlay.formatGp(entry.getTotalValue()));
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
        Color colour = rarityColour(item.getRarity());
        ItemSlot slot = new ItemSlot(colour, item.getQuantity());
        slot.setLayout(new BorderLayout());
        slot.setPreferredSize(new Dimension(38, 36));
        slot.setMinimumSize(new Dimension(38, 36));
        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(36, 32));
        icon.setMinimumSize(new Dimension(36, 32));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        try
        {
            BufferedImage image = item.getImage();
            if (image != null)
            {
                icon.setIcon(new ImageIcon(image));
            }
        }
        catch (RuntimeException ignored)
        {
            icon.setText("?");
            icon.setForeground(MUTED);
        }
        String tooltip = buildItemTooltip(item);
        icon.setToolTipText(tooltip);
        slot.setToolTipText(tooltip);
        makeWikiLink(icon, item.getName(), tooltip);
        makeWikiLink(slot, item.getName(), tooltip);
        slot.add(icon, BorderLayout.CENTER);
        return slot;
    }

    private static void makeWikiLink(JComponent component, String searchTerm, String tooltip)
    {
        if (searchTerm == null || searchTerm.trim().isEmpty())
        {
            return;
        }
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.setToolTipText(tooltip);
        component.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                if (SwingUtilities.isLeftMouseButton(event))
                {
                    LinkBrowser.browse(wikiSearchUrl(searchTerm));
                }
            }
        });
    }

    static String wikiSearchUrl(String searchTerm)
    {
        return "https://oldschool.runescape.wiki/w/Special:Search?search="
            + URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
    }

    static String formatPanelQuantity(int quantity)
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
    {
        String itemName = item.getName();
        long gePrice = item.getGePrice();
        long highAlchemy = item.getHighAlchemyPrice();
        String dropChance = item.getDropRate() == null ? "N/A" : item.getDropRate();
        DropRarity rarity = item.getRarity();
        String rarityHex = colourHex(rarityColour(rarity));

        return "<html><b><font color='#" + rarityHex + "'>" + escapeHtml(itemName) + "</font></b>"
            + "<br><font color='#aaaaaa'>Qty:</font> " + item.getQuantity()
            + "<br><font color='#aaaaaa'>GE:</font> " + LootPopOverlay.formatExactGp(gePrice)
            + "<br><font color='#aaaaaa'>HA:</font> " + LootPopOverlay.formatExactGp(highAlchemy)
            + "<br><font color='#aaaaaa'>Stack:</font> " + LootPopOverlay.formatExactGp(item.getStackValue())
            + "<br><font color='#aaaaaa'>Chance:</font> <font color='#" + rarityHex + "'>" + escapeHtml(dropChance) + "</font>"
            + "<br><font color='#777777'>Click to open OSRS Wiki</font></html>";
    }

    private String buildOverflowTooltip(LootHistoryEntry entry, int startIndex)
    {
        StringBuilder tooltip = new StringBuilder("<html><b>Hidden items</b>");
        for (int i = startIndex; i < entry.getItems().size(); i++)
        {
            LootHistoryItem item = entry.getItems().get(i);
            String name = item.getName();
            tooltip.append("<br>").append(escapeHtml(name));
            if (item.getQuantity() > 1)
            {
                tooltip.append(" ×").append(item.getQuantity());
            }
        }
        return tooltip.append("</html>").toString();
    }

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
            List<String> sources = plugin.getSessionSources();
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
            totalValue.setText(LootPopOverlay.formatGp(session.getTotalValue()));
            averageValue.setText(LootPopOverlay.formatGp(session.getAverageValue()));
            bestValue.setText(session.getBestValue() <= 0L
                ? "—"
                : LootPopOverlay.formatGp(session.getBestValue()));
            bestValue.setToolTipText(session.getBestSource());

            historyList.removeAll();
            List<LootHistoryEntry> history = plugin.getLootHistory();
            if (history.isEmpty())
            {
                historyList.add(emptyHistoryCard());
            }
            else
            {
                for (LootHistoryEntry entry : history)
                {
                    try
                    {
                        historyList.add(historyCard(entry));
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
        }
        finally
        {
            refreshing = false;
        }
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
        syncing = false;
    }

    private void wireQuickControls()
    {
        visualMode.addActionListener(event -> setConfig("visualMode", visualMode.getSelectedItem()));
        popupStyle.addActionListener(event -> setConfig("popupStyle", popupStyle.getSelectedItem()));
        adaptiveTime.addActionListener(event -> setConfig("adaptiveDisplayTime", adaptiveTime.isSelected()));
        screenshots.addActionListener(event -> setConfig("automaticScreenshots", screenshots.isSelected()));
    }

    private void setConfig(String key, Object value)
    {
        if (!syncing)
        {
            configManager.setConfiguration(LootPopConfig.GROUP, key, value);
        }
    }

    private static JPanel controlRow(String label, java.awt.Component control)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
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
        label.setForeground(ACCENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 1, 6, 0));
        return label;
    }

    private static JLabel valueLabel()
    {
        JLabel label = new JLabel("0");
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        return label;
    }

    private static void styleCheckBox(JCheckBox checkBox)
    {
        checkBox.setOpaque(false);
        checkBox.setForeground(Color.WHITE);
        checkBox.setFocusable(false);
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
        return ImageUtil.resizeImage(brandIcon, 32, 32);
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
        private final int quantity;
        private boolean hovered;

        ItemSlot(Color accent, int quantity)
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
            g.setColor(new Color(0, 0, 0, 225));
            g.drawString(label, 3, 12);
            g.drawString(label, 2, 13);
            g.setColor(new Color(125, 255, 145));
            g.drawString(label, 2, 12);
            g.dispose();
        }
    }
}
