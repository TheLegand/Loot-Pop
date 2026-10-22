package com.lootpop;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the real popup renderer, not a separately drawn mock popup. */
public class PopupRenderingTest
{
    private static LootPopConfig config(PopupStyle style, boolean reduced)
    {
        return new LootPopConfig()
        {
            @Override public PopupStyle popupStyle() { return style; }
            @Override public int maxItems() { return 10; }
            @Override public boolean showItemTooltips() { return false; }
            @Override public boolean legendaryEffects() { return false; }
            @Override public boolean reduceMotion() { return reduced; }
        };
    }

    private static LootPopOverlay overlay(LootPopConfig config, boolean hovered)
    {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> method.getName().equals("getMouseCanvasPosition")
                ? new Point(hovered ? 100 : -1, hovered ? 30 : -1) : null);
        LootPopPlugin plugin = new LootPopPlugin()
        {
            @Override int displayDurationFor(LootNotification notification) { return 20000; }
            @Override DropRarity rarityForRate(double chance)
            { return chance >= 5000 ? DropRarity.ULTRA_RARE : chance >= 2000 ? DropRarity.VERY_RARE : DropRarity.COMMON; }
        };
        BufferedImage sword = SealedDropRendererTest.sword(new Color(227, 198, 99));
        BufferedImage blade = SealedDropRendererTest.sword(new Color(137, 203, 210));
        BufferedImage coins = SealedDropRendererTest.coins();
        LootPopOverlay overlay = new LootPopOverlay(plugin, config, null, client, new TooltipManager(),
            item -> item.getItemId() % 3 == 0 ? coins : item.getItemId() % 2 == 0 ? blade : sword,
            item -> "Reward " + item.getItemId());
        overlay.setPreferredSize(new Dimension(360, 100));
        overlay.getBounds().setBounds(0, 0, 360, 600);
        return overlay;
    }

    private static LootNotification notification(LootPopConfig config, int count, boolean pending)
    {
        List<LootDisplayItem> items = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < count; i++)
        {
            LootDisplayItem item = new LootDisplayItem(i + 1, i < 2 ? 1 : i + 1, 1000000,
                "1 / 5,000", 5000, pending && i == 0);
            items.add(item); total += item.getTotalValue();
        }
        LootNotification n = new LootNotification("Legendary test", items, total, 1000);
        n.setSealedDrop(new SealedDropSequence(items, Collections.emptySet(), 1000, true,
            config.reduceMotion(), 100, LootPopOverlay.visibleItemCount(config, count)));
        return n;
    }

    private static Dimension render(LootPopOverlay overlay, LootNotification n, long age, Graphics2D g)
    {
        return overlay.renderAt(g, Collections.singletonList(n), 1000 + age);
    }

    @Test public void hoveredPopupAlwaysFinishesCollapsingInEveryStyle()
    {
        for (PopupStyle style : PopupStyle.values())
        {
            LootPopConfig config = config(style, false);
            LootPopOverlay overlay = overlay(config, true);
            LootNotification n = notification(config, 12, false);
            SealedDropSequence s = n.getSealedDrop();
            Graphics2D g = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
            Dimension open = render(overlay, n, 500, g);
            Dimension settling = render(overlay, n, s.getRevealEndMillis() + 40, g);
            Dimension closed = render(overlay, n, s.getDurationMillis() + 1, g);
            assertTrue(style + " must shrink while hovered", settling.height < open.height);
            assertTrue(style + " must finish shrinking", closed.height < settling.height);
            assertEquals(360, closed.width);
            assertTrue("No oversized appended reveal region", open.height < 300);
            long pausedAge = n.getAgeMillis(1000 + s.getDurationMillis() + 1);
            render(overlay, n, s.getDurationMillis() + 1000, g);
            assertEquals("Reading still pauses after transition", pausedAge, n.getAgeMillis(30000));
            g.dispose();
        }
    }

    @Test public void reducedMotionNeverAllocatesARevealRoom()
    {
        for (PopupStyle style : PopupStyle.values())
        {
            LootPopConfig config = config(style, true); LootPopOverlay overlay = overlay(config, false);
            LootNotification n = notification(config, 8, false);
            Graphics2D g = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
            Dimension before = render(overlay, n, 200, g);
            Dimension after = render(overlay, n, n.getSealedDrop().getDurationMillis() + 1, g);
            assertEquals(before, after); g.dispose();
        }
    }

    @Test public void hiddenSpritesAndTheirTooltipCannotSpoilAnActiveReveal()
    {
        LootPopConfig config = new LootPopConfig()
        {
            @Override public int maxItems() { return 10; }
            @Override public boolean showItemTooltips() { return true; }
            @Override public boolean legendaryEffects() { return false; }
        };
        LootPopPlugin plugin = new LootPopPlugin()
        {
            @Override int displayDurationFor(LootNotification n) { return 20000; }
            @Override DropRarity rarityForRate(double rate) { return DropRarity.ULTRA_RARE; }
        };
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> method.getName().equals("getMouseCanvasPosition") ? new Point(312, 139) : null);
        TooltipManager manager = new TooltipManager();
        List<Integer> requested = new ArrayList<>();
        BufferedImage sprite = SealedDropRendererTest.coins();
        LootPopOverlay overlay = new LootPopOverlay(plugin, config, null, client, manager,
            item -> { requested.add(item.getItemId()); return sprite; }, item -> "Reward " + item.getItemId());
        overlay.setPreferredSize(new Dimension(360, 100)); overlay.getBounds().setBounds(0, 0, 360, 600);
        LootNotification n = notification(config, 12, false);
        Graphics2D g = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
        render(overlay, n, 500, g);
        assertTrue(manager.getTooltips().isEmpty());
        assertFalse(requested.contains(10)); assertFalse(requested.contains(11)); assertFalse(requested.contains(12));
        render(overlay, n, n.getSealedDrop().getDurationMillis() + 20, g); g.dispose();
        assertTrue(requested.containsAll(java.util.Arrays.asList(10, 11, 12)));
        assertEquals(1, manager.getTooltips().size());
        assertTrue(manager.getTooltips().get(0).getComponent() instanceof HiddenItemsTooltip);
    }

    @Test public void pendingAndKnownTotalsUseIdenticalCompactItemGeometry()
    {
        for (PopupStyle style : PopupStyle.values())
        {
            LootPopConfig config = config(style, false);
            LootPopOverlay overlay = overlay(config, false);
            LootNotification n = notification(config, 2, true);
            Graphics2D g = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
            Dimension size = render(overlay, n, n.getSealedDrop().getDurationMillis() + 1, g);
            assertTrue("Pending popup stays compact", size.height < 120);
            assertTrue(n.hasPendingPrices()); assertFalse(n.isPersonalBest()); g.dispose();
        }
    }

    @Test public void ordinaryAndCollectionLogPopupsHaveTheSameSettledSize() throws Exception
    {
        for (PopupStyle style : new PopupStyle[]{PopupStyle.CLASSIC_FRAME, PopupStyle.MINIMAL_EDGE})
        {
            BufferedImage preview = new BufferedImage(400, 330, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = preview.createGraphics();
            g.setColor(new Color(22, 25, 31)); g.fillRect(0, 0, 400, 330);
            LootPopConfig config = config(style, false);
            LootPopOverlay overlay = overlay(config, false);
            LootNotification ordinary = new LootNotification("Goblin", java.util.Arrays.asList(
                new LootDisplayItem(1, 1, 40), new LootDisplayItem(3, 21, 3)), 103, 1000);
            LootNotification collection = notification(config, 3, false);
            collection.setCollectionLogUnlock(true);
            collection.markCollectionLogItem(1, 1000);
            long age = collection.getSealedDrop().getDurationMillis() + 10;
            Graphics2D top = (Graphics2D) g.create(); top.translate(20, 25);
            Dimension a = render(overlay, ordinary, age, top); top.dispose();
            Graphics2D bottom = (Graphics2D) g.create(); bottom.translate(20, 165);
            Dimension b = render(overlay, collection, age, bottom); bottom.dispose();
            assertEquals(style + " header/NEW badge must not shrink icons or frame", a, b);
            int referenceWidth = style == PopupStyle.CLASSIC_FRAME ? 205 : 218;
            int referenceHeight = style == PopupStyle.CLASSIC_FRAME ? 66 : 70;
            assertEquals("Restore the original ordinary popup proportions, not the smaller reveal proportions",
                (int) Math.ceil(referenceHeight * 360.0 / referenceWidth), a.height);
            g.dispose();
            Path file = Path.of("build", "popup-layout-preview", style == PopupStyle.CLASSIC_FRAME
                ? "consistent-size.png" : "consistent-size-rugged.png");
            Files.createDirectories(file.getParent()); ImageIO.write(preview, "png", file.toFile());
        }
    }

    @Test public void exportsRealRendererFramesForAllStylesAndAStack() throws Exception
    {
        Path directory = Path.of("build", "popup-layout-preview"); Files.createDirectories(directory);
        BufferedImage sheet = new BufferedImage(1200, 1100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics(); g.setColor(new Color(22, 25, 31)); g.fillRect(0, 0, 1200, 1100);
        int column = 0;
        for (PopupStyle style : PopupStyle.values())
        {
            LootPopConfig config = config(style, false);
            String[] captions = {"Single reveal / centered", "Multiple rares / last group", "Settled / centered rows", "Price pending / compact"};
            for (int row = 0; row < 4; row++)
            {
                LootNotification n = notification(config, row == 3 ? 2 : 12, row == 3);
                long age = row == 0 ? 850 : row == 1 ? n.getSealedDrop().getRevealEndMillis() - 700
                    : n.getSealedDrop().getDurationMillis() + 10;
                Graphics2D cell = (Graphics2D) g.create(); cell.translate(column * 400 + 20, row * 270 + 30);
                LootPopOverlay overlay = overlay(config, false); render(overlay, n, age, cell); cell.dispose();
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); g.setColor(new Color(185, 191, 204));
                g.drawString(style + " / " + captions[row], column * 400 + 20, row * 270 + 17);
            }
            column++;
        }
        g.dispose(); ImageIO.write(sheet, "png", directory.resolve("all-styles.png").toFile());

        LootPopConfig config = config(PopupStyle.CLASSIC_FRAME, false);
        LootPopOverlay overlay = overlay(config, false);
        LootNotification first = notification(config, 8, false);
        LootNotification second = notification(config, 2, true);
        BufferedImage stack = new BufferedImage(420, 460, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = stack.createGraphics(); sg.setColor(new Color(22, 25, 31)); sg.fillRect(0, 0, 420, 460);
        sg.translate(25, 30);
        Dimension bounds = overlay.renderAt(sg, java.util.Arrays.asList(first, second), 1000 + first.getSealedDrop().getDurationMillis() + 10);
        assertTrue(bounds.height < 350); sg.dispose();
        ImageIO.write(stack, "png", directory.resolve("stack.png").toFile());
    }

    @Test public void exportsOriginalOverflowBadgeBesideItsReadableTooltip() throws Exception
    {
        LootPopConfig config = config(PopupStyle.CLASSIC_FRAME, false);
        LootPopOverlay overlay = overlay(config, false);
        LootNotification notification = notification(config, 12, false);
        BufferedImage canvas = new BufferedImage(420, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics(); g.setColor(new Color(22, 25, 31)); g.fillRect(0, 0, 420, 360);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11)); g.setColor(new Color(194, 199, 207));
        g.drawString("Actual renderer / illustrative item sprites", 25, 16);
        Graphics2D popup = (Graphics2D) g.create(); popup.translate(25, 32);
        render(overlay, notification, notification.getSealedDrop().getDurationMillis() + 10, popup); popup.dispose();
        HiddenItemsTooltip tooltip = overlay.buildHiddenItemsTooltip(notification.getDisplayItems(), LootPopOverlay.visibleItemCount(config, 12));
        tooltip.setPreferredLocation(new java.awt.Point(25, 232));
        Dimension tooltipSize = tooltip.render(g); assertTrue(tooltipSize.width <= 280);
        g.dispose(); Path file = Path.of("build", "tooltip-preview", "overflow-original-badge.png");
        Files.createDirectories(file.getParent()); ImageIO.write(canvas, "png", file.toFile());
    }
}
