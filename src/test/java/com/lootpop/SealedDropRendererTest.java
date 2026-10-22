package com.lootpop;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** Offscreen motion fixture: production reveal painter, illustrative item sprites. No client/network needed. */
public class SealedDropRendererTest
{
    @Test
    public void paintsFullTimelineAndExportsLocalMotionPreview() throws Exception
    {
        Path directory = Path.of("build", "sealed-drop-preview");
        Files.createDirectories(directory);
        LootDisplayItem first = new LootDisplayItem(1, 1, 100000, "1 / 5,000", 5000);
        LootDisplayItem second = new LootDisplayItem(2, 1, 30000, "1 / 2,000", 2000);
        SealedDropSequence sequence = new SealedDropSequence(Arrays.asList(first, second,
            new LootDisplayItem(3, 15, 1, "1 / 2", 2)), Collections.singleton(1), 1000, true, false, 100, 10);
        SealedDropRenderer renderer = new SealedDropRenderer();
        BufferedImage[] sprites = {sword(new Color(227, 198, 99)), sword(new Color(137, 203, 210)), coins()};
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(directory.resolve("sealed-drop.gif").toFile()))
        {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int frame = 0; frame < 104; frame++)
            {
                BufferedImage image = frame(renderer, sequence, sprites, frame * 50L);
                IIOMetadata meta = writer.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image), writer.getDefaultWriteParam());
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                IIOMetadataNode control = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime", "5");
                control.setAttribute("disposalMethod", "none");
                if (frame == 0)
                {
                    IIOMetadataNode extensions = new IIOMetadataNode("ApplicationExtensions");
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    extensions.appendChild(loop);
                    root.appendChild(extensions);
                }
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(image, null, meta), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        }
        finally { writer.dispose(); }
        BufferedImage sheet = new BufferedImage(660, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(13, 15, 19));
        g.fillRect(0, 0, 660, 420);
        long[] times = {400, 1100, 2300, 4200};
        for (int i = 0; i < times.length; i++)
        {
            g.drawImage(frame(renderer, sequence, sprites, times[i]), 10 + (i % 2) * 330, 10 + (i / 2) * 210, null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", directory.resolve("stages.png").toFile());
        assertTrue(Files.size(directory.resolve("sealed-drop.gif")) > 10000);
    }

    private static BufferedImage frame(SealedDropRenderer renderer, SealedDropSequence sequence,
        BufferedImage[] sprites, long age)
    {
        BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(17, 20, 26));
        g.fillRect(0, 0, 320, 200);
        g.setColor(new Color(35, 39, 47));
        g.fillRoundRect(3, 3, 313, 177, 8, 8);
        g.setColor(new Color(123, 135, 158));
        g.drawRoundRect(3, 3, 313, 177, 8, 8);
        g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
        g.setColor(new Color(241, 223, 173));
        g.drawString("Rare drop test", 12, 24);
        g.setColor(new Color(210, 215, 223));
        g.drawString(age < sequence.getRevealEndMillis() ? "??? gp" : "130K gp", 246, 24);
        g.setColor(new Color(80, 88, 104));
        g.drawLine(12, 31, 306, 31);
        String[] names = {"Ancient blade", "Rune blade"};
        for (int i = 0; i < 3; i++)
        {
            boolean candidate = i < 2;
            if (candidate && sequence.progress(age, i) >= 1)
            {
                g.drawImage(sprites[i], 12 + i * 41, 38, null);
                if (i == 0) { SealedDropRenderer.drawBook(g, 35, 59, SealedDropRenderer.COLLECTION_GOLD); }
            }
            else if (!candidate)
            {
                Graphics2D normal = (Graphics2D) g.create();
                SealedDropRenderer.fade(normal, sequence.normalAlpha(age));
                normal.drawImage(sprites[i], 12 + i * 41, 38, null);
                normal.setFont(FontManager.getRunescapeSmallFont());
                normal.setColor(Color.WHITE);
                normal.drawString("x15", 94, 50);
                normal.dispose();
            }
            if (candidate)
            {
                renderer.draw(g, sequence.getItems().get(i), sprites[i], names[i], new Color(235, 73, 105),
                    i == 0, sequence.progress(age, i), 160, 105, 12 + i * 41, 38, 285, false, true);
            }
        }
        if (age < sequence.getRevealEndMillis())
        {
            g.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
            g.setColor(new Color(176, 182, 197));
            String label = "SEALED DROP  " + (sequence.progress(age, 1) < 0 ? "1 / 2" : "2 / 2");
            g.drawString(label, (320 - g.getFontMetrics().stringWidth(label)) / 2, 78);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.setColor(new Color(138, 145, 161));
        g.drawString("Motion preview / illustrative sprites", 65, 195);
        g.dispose();
        return image;
    }

    static BufferedImage sword(Color metal)
    {
        BufferedImage image = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(4, 5, 8));
        g.fillPolygon(new int[]{5, 8, 14, 29, 33, 32, 17, 22, 19, 13, 8},
            new int[]{27, 22, 19, 2, 1, 7, 23, 25, 29, 25, 31}, 11);
        g.setColor(metal);
        g.fillPolygon(new int[]{13, 28, 31, 30, 15}, new int[]{19, 4, 3, 7, 22}, 5);
        g.setColor(new Color(245, 247, 236));
        g.drawLine(15, 19, 29, 5);
        g.setColor(new Color(227, 172, 53));
        g.fillRect(10, 18, 4, 5);
        g.fillRect(14, 22, 6, 3);
        g.setColor(new Color(133, 75, 37));
        g.fillRect(7, 24, 6, 4);
        g.dispose();
        return image;
    }

    static BufferedImage coins()
    {
        BufferedImage image = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        for (int i = 0; i < 3; i++)
        {
            g.setColor(new Color(117, 76, 9));
            g.fillOval(3 + i * 9, 13 - i * 3, 13, 8);
            g.setColor(new Color(248, 192, 50));
            g.fillOval(4 + i * 9, 13 - i * 3, 11, 5);
        }
        g.dispose();
        return image;
    }
}
