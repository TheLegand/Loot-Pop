package com.lootpop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class JournalDirectoryMigrationTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void newInstallUsesPluginDirectory() throws Exception { probe("fresh"); }
    @Test public void migratesHistoryTrophiesProfilesAndBackups() throws Exception { probe("legacy"); }
    @Test public void existingDestinationDoesNotOverwriteEitherDirectory() throws Exception { probe("collision"); }
    @Test public void failedDirectoryAccessLeavesLegacyDataUntouched() throws Exception { probe("failure"); }
    @Test public void corruptLegacyJournalRemainsReadOnlyAfterMigration() throws Exception { probe("corrupt"); }

    private void probe(String scenario) throws Exception
    {
        // RuneLite derives its directories from user.home at class initialization. Use a
        // separate JVM so these integration tests cannot touch the user's actual data.
        Path home = temporary.newFolder().toPath();
        Path output = temporary.newFile().toPath();
        String classpath = System.getProperty("lootpop.testClasspath");
        assertNotNull("Gradle must supply the test runtime classpath", classpath);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Duser.home=" + home, "-Djava.awt.headless=true",
            "-cp", classpath, JournalMigrationProbe.class.getName(), scenario)
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try
        {
            assertTrue("Migration probe timed out", process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(Files.readString(output), 0, process.exitValue());
        }
        finally { if (process.isAlive()) { process.destroyForcibly(); } }
    }
}
