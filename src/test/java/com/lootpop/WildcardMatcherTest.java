package com.lootpop;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WildcardMatcherTest
{
    @Test
    public void matchesCaseInsensitiveWildcards()
    {
        assertTrue(WildcardMatcher.matchesAny("bones, dragon *", "Dragon dagger"));
        assertTrue(WildcardMatcher.matchesAny("*goblin", "Cave goblin"));
        assertFalse(WildcardMatcher.matchesAny("bones, dragon *", "Coins"));
    }

    @Test
    public void handlesEmptyPatterns()
    {
        assertFalse(WildcardMatcher.matchesAny("", "Coins"));
        assertFalse(WildcardMatcher.matchesAny(null, "Coins"));
    }
}
