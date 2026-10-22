package com.lootpop;

import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;
import javax.swing.JOptionPane;
import net.runelite.client.util.LinkBrowser;

/** Desktop side effects stay behind explicit user actions and can be tested without a browser or clipboard. */
interface LootPanelActions
{
    boolean confirmSessionReset(Component owner, int drops);
    void copyName(Component owner, String name);
    void openWiki(String name);

    static LootPanelActions desktop()
    {
        return new LootPanelActions()
        {
            @Override public boolean confirmSessionReset(Component owner, int drops)
            {
                Object[] options = {"Cancel", "Reset session"};
                return JOptionPane.showOptionDialog(owner,
                    "Reset all session sources?\n" + String.format(Locale.US, "%,d", drops)
                        + " recorded drops and the session recap will be cleared.\nSaved Journal history and trophies will not be changed.",
                    "Reset Loot Pop session", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]) == 1;
            }

            @Override public void copyName(Component owner, String name)
            {
                try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(name), null); }
                catch (IllegalStateException | HeadlessException | SecurityException unavailable)
                {
                    JOptionPane.showMessageDialog(owner, "The clipboard is unavailable. Please try Copy name again.",
                        "Copy name", JOptionPane.INFORMATION_MESSAGE);
                }
            }

            @Override public void openWiki(String name) { LinkBrowser.browse(LootPopPanel.wikiSearchUrl(name)); }
        };
    }
}
