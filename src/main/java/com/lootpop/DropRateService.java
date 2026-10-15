package com.lootpop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

final class DropRateService
{
    static final int NO_NPC_ID = -1;
    private static final int MAX_DISPLAYED_RATES = 2;
    private static final Pattern FRACTION_PATTERN = Pattern.compile(
        "~?(?:(\\d+(?:\\.\\d+)?)\\s*[x×]\\s*)?(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)"
    );

    private final Gson gson;
    private final Map<String, Map<String, List<String>>> dropsBySource = new HashMap<>();
    private final Map<Integer, Map<String, List<String>>> dropsByNpcId = new HashMap<>();
    private final Map<String, Map<String, String>> rareTableByItem = new HashMap<>();
    private final Map<String, List<String>> sourceAliases = new HashMap<>();
    private final Map<String, DropRateInfo> lookupCache = new HashMap<>();
    private boolean loaded;

    @Inject
    DropRateService(Gson gson)
    {
        this.gson = gson;
    }

    synchronized void load()
    {
        dropsBySource.clear();
        dropsByNpcId.clear();
        rareTableByItem.clear();
        sourceAliases.clear();
        lookupCache.clear();

        mergeDropTable("/droprates_clean.json", true);
        mergeDropTable("/minigame_droprates.json", false);
        mergeDropTable("/special_droprates.json", false);
        mergeDropTable("/clue_droprates.json", false);
        loadNpcVersions("/npc_versions.json");
        loadRareDropTable("/rare_drop_table.json");
        loadAliases();
        loaded = true;
    }

    String findRate(String sourceName, int npcId, String itemName)
    {
        DropRateInfo info = findRateInfo(sourceName, npcId, itemName);
        return info == null ? null : info.getDisplayRate();
    }

    synchronized DropRateInfo findRateInfo(String sourceName, int npcId, String itemName)
    {
        ensureLoaded();
        String itemKey = normalise(itemName);
        if (itemKey.isEmpty())
        {
            return null;
        }

        String cacheKey = npcId + "|" + normalise(sourceName) + "|" + itemKey;
        if (lookupCache.containsKey(cacheKey))
        {
            return lookupCache.get(cacheKey);
        }

        DropRateInfo result = findRateInfoUncached(sourceName, npcId, itemKey);
        lookupCache.put(cacheKey, result);
        return result;
    }

    private DropRateInfo findRateInfoUncached(String sourceName, int npcId, String itemKey)
    {

        if (npcId != NO_NPC_ID)
        {
            List<String> exactRates = findRatesInTable(dropsByNpcId.get(npcId), itemKey);
            if (exactRates != null)
            {
                return createInfo(exactRates);
            }
        }

        String sourceKey = normalise(sourceName);
        List<String> candidateSources = new ArrayList<>();
        candidateSources.add(sourceKey);
        candidateSources.addAll(sourceAliases.getOrDefault(sourceKey, Collections.emptyList()));

        for (String candidate : candidateSources)
        {
            List<String> primaryRates = findRatesInTable(dropsBySource.get(candidate), itemKey);
            if (primaryRates != null)
            {
                return createInfo(primaryRates);
            }
        }

        Map<String, String> rareSources = rareTableByItem.get(itemKey);
        if (rareSources != null)
        {
            for (String candidate : candidateSources)
            {
                String rate = rareSources.get(candidate);
                if (rate != null)
                {
                    return new DropRateInfo(formatRate(rate), parseOneIn(rate));
                }
            }
        }

        return null;
    }

    private void ensureLoaded()
    {
        if (!loaded)
        {
            load();
        }
    }

    private static List<String> findRatesInTable(Map<String, List<String>> table, String itemKey)
    {
        if (table == null)
        {
            return null;
        }
        return table.get(itemKey);
    }

    private static DropRateInfo createInfo(List<String> rates)
    {
        String display = formatRates(rates);
        if (display == null)
        {
            return null;
        }
        double oneIn = 0.0;
        for (String rate : rates)
        {
            oneIn = parseOneIn(rate);
            if (oneIn > 0.0)
            {
                break;
            }
        }
        return new DropRateInfo(display, oneIn);
    }

    static double parseOneIn(String rate)
    {
        if (rate == null)
        {
            return 0.0;
        }
        Matcher matcher = FRACTION_PATTERN.matcher(rate);
        if (!matcher.find())
        {
            return 0.0;
        }
        try
        {
            double rolls = matcher.group(1) == null ? 1.0 : Double.parseDouble(matcher.group(1));
            double numerator = Double.parseDouble(matcher.group(2));
            double denominator = Double.parseDouble(matcher.group(3));
            double probability = rolls * numerator / denominator;
            return probability > 0.0 ? 1.0 / probability : 0.0;
        }
        catch (NumberFormatException ex)
        {
            return 0.0;
        }
    }

    private void mergeDropTable(String resourcePath, boolean required)
    {
        Map<String, Map<String, JsonElement>> raw = readResource(
            resourcePath,
            new TypeToken<Map<String, Map<String, JsonElement>>>() {}.getType(),
            required
        );
        if (raw == null)
        {
            return;
        }

        for (Map.Entry<String, Map<String, JsonElement>> source : raw.entrySet())
        {
            Map<String, List<String>> drops = dropsBySource.computeIfAbsent(
                normalise(source.getKey()),
                ignored -> new HashMap<>()
            );
            mergeDrops(drops, source.getValue());
        }
    }

    private void loadNpcVersions(String resourcePath)
    {
        Map<String, List<RawNpcVersion>> raw = readResource(
            resourcePath,
            new TypeToken<Map<String, List<RawNpcVersion>>>() {}.getType(),
            false
        );
        if (raw == null)
        {
            return;
        }

        for (List<RawNpcVersion> versions : raw.values())
        {
            for (RawNpcVersion version : versions)
            {
                if (version == null || version.ids == null || version.drops == null)
                {
                    continue;
                }

                Map<String, List<String>> drops = new HashMap<>();
                mergeDrops(drops, version.drops);
                for (Integer npcId : version.ids)
                {
                    if (npcId != null)
                    {
                        dropsByNpcId.put(npcId, drops);
                    }
                }
            }
        }
    }

    private void loadRareDropTable(String resourcePath)
    {
        Map<String, Map<String, String>> raw = readResource(
            resourcePath,
            new TypeToken<Map<String, Map<String, String>>>() {}.getType(),
            false
        );
        if (raw == null)
        {
            return;
        }

        for (Map.Entry<String, Map<String, String>> item : raw.entrySet())
        {
            Map<String, String> sources = new HashMap<>();
            for (Map.Entry<String, String> source : item.getValue().entrySet())
            {
                sources.put(normalise(source.getKey()), source.getValue());
            }
            rareTableByItem.put(normalise(item.getKey()), sources);
        }
    }

    private <T> T readResource(String path, java.lang.reflect.Type type, boolean required)
    {
        InputStream stream = getClass().getResourceAsStream(path);
        if (stream == null)
        {
            if (required)
            {
                throw new IllegalStateException("Missing drop-rate resource: " + path);
            }
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
        {
            return gson.fromJson(reader, type);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Unable to read drop-rate resource: " + path, ex);
        }
    }

    private static void mergeDrops(Map<String, List<String>> output, Map<String, JsonElement> rawDrops)
    {
        for (Map.Entry<String, JsonElement> drop : rawDrops.entrySet())
        {
            List<String> rates = jsonStrings(drop.getValue());
            if (!rates.isEmpty())
            {
                output.put(normalise(drop.getKey()), rates);
            }
        }
    }

    private static List<String> jsonStrings(JsonElement element)
    {
        if (element == null)
        {
            return Collections.emptyList();
        }
        if (element.isJsonPrimitive())
        {
            return Collections.singletonList(element.getAsString());
        }
        if (!element.isJsonArray())
        {
            return Collections.emptyList();
        }

        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement value : array)
        {
            if (value.isJsonPrimitive())
            {
                values.add(value.getAsString());
            }
        }
        return values;
    }

    private void loadAliases()
    {
        addAliases("Clue Scroll (Beginner)", "Beginner clue");
        addAliases("Clue Scroll (Easy)", "Easy clue");
        addAliases("Clue Scroll (Medium)", "Medium clue");
        addAliases("Clue Scroll (Hard)", "Hard clue");
        addAliases("Clue Scroll (Elite)", "Elite clue");
        addAliases("Clue Scroll (Master)", "Master clue");
        addAliases("Dawn", "Grotesque Guardians");
        addAliases("Dusk", "Grotesque Guardians");
        addAliases("Maggot King Corpse", "Maggot King");
    }

    private void addAliases(String source, String... aliases)
    {
        List<String> normalisedAliases = new ArrayList<>(aliases.length);
        for (String alias : aliases)
        {
            normalisedAliases.add(normalise(alias));
        }
        sourceAliases.put(normalise(source), normalisedAliases);
    }

    static String formatRates(List<String> rates)
    {
        if (rates == null || rates.isEmpty())
        {
            return null;
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String rate : rates)
        {
            if (rate != null && !rate.trim().isEmpty())
            {
                unique.add(formatRate(rate));
            }
        }
        if (unique.isEmpty())
        {
            return null;
        }

        List<String> displayed = new ArrayList<>(unique);
        if (displayed.size() > MAX_DISPLAYED_RATES)
        {
            displayed = displayed.subList(0, MAX_DISPLAYED_RATES);
        }
        String suffix = unique.size() > MAX_DISPLAYED_RATES ? " + more" : "";
        return String.join("  |  ", displayed) + suffix;
    }

    private static String formatRate(String rate)
    {
        return rate.trim()
            .replace(" x ", " × ")
            .replaceAll("\\s*/\\s*", " / ")
            .replaceAll("\\s+", " ");
    }

    private static String normalise(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("<[^>]*>", "")
            .trim()
            .toLowerCase(Locale.ENGLISH);
    }

    private static final class RawNpcVersion
    {
        private List<Integer> ids;
        private Map<String, JsonElement> drops;
    }
}
