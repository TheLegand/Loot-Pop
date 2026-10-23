package com.lootpop;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Path;
import net.runelite.client.util.Filepath;

/** Test-only filesystem roots; production obtains its root exclusively from Plugin. */
final class JournalTestFiles
{
    private JournalTestFiles() { }

    static LootJournalStore store(Path directory, Gson gson)
    {
        return new LootJournalStore(() -> Filepath.Unchecked.getRooted(directory), gson);
    }

    static Path pathFor(LootJournalStore store, String profile) throws IOException
    {
        return Path.of(store.fileFor(profile).toString());
    }
}
