package com.lootpop;

import com.google.gson.Gson;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DropRateServiceTest
{
    private DropRateService service;

    @Before
    public void setUp()
    {
        service = new DropRateService(new Gson());
        service.load();
    }

    @Test
    public void findsWikiRateForNpcDrop()
    {
        assertEquals(
            "2 × 4 / 150",
            service.findRate("Vorkath", DropRateService.NO_NPC_ID, "Dragon bones")
        );
    }

    @Test
    public void usesNpcIdForVersionedDropTables()
    {
        assertEquals("1 / 512", service.findRate("Abyssal demon", 415, "Abyssal whip"));
        DropRateInfo info = service.findRateInfo("Abyssal demon", 415, "Abyssal whip");
        assertEquals(512.0, info.getOneIn(), 0.0001);
    }

    @Test
    public void resolvesRuneLiteEventAliases()
    {
        assertEquals(
            "1 / 360",
            service.findRate("Clue Scroll (Beginner)", DropRateService.NO_NPC_ID, "Bear feet")
        );
    }

    @Test
    public void formatsMultipleWikiRatesWithoutGuessing()
    {
        assertEquals(
            "1 / 64  |  1 / 128",
            DropRateService.formatRates(Arrays.asList("1/64", "1/128"))
        );
    }

    @Test
    public void convertsWikiFractionsIntoComparableOneInRates()
    {
        assertEquals(512.0, DropRateService.parseOneIn("1 / 512"), 0.0001);
        assertEquals(18.75, DropRateService.parseOneIn("2 x 4 / 150"), 0.0001);
        assertEquals(0.0, DropRateService.parseOneIn("Varies"), 0.0001);
    }

    @Test
    public void unknownDropHasNoInventedRate()
    {
        assertNull(service.findRate("Legendary test", DropRateService.NO_NPC_ID, "Twisted bow"));
    }
}
