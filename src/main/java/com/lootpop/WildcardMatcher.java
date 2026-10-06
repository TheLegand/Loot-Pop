package com.lootpop;

import java.util.Locale;

final class WildcardMatcher
{
    private WildcardMatcher()
    {
    }

    static boolean matchesAny(String commaSeparatedPatterns, String value)
    {
        if (commaSeparatedPatterns == null || commaSeparatedPatterns.trim().isEmpty() || value == null)
        {
            return false;
        }

        for (String rawPattern : commaSeparatedPatterns.split(","))
        {
            String pattern = rawPattern.trim();
            if (!pattern.isEmpty() && matches(pattern, value))
            {
                return true;
            }
        }
        return false;
    }

    static boolean matches(String pattern, String value)
    {
        String p = pattern.toLowerCase(Locale.ENGLISH);
        String v = value.toLowerCase(Locale.ENGLISH);
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int valueCheckpoint = -1;

        while (valueIndex < v.length())
        {
            if (patternIndex < p.length() && p.charAt(patternIndex) == v.charAt(valueIndex))
            {
                patternIndex++;
                valueIndex++;
            }
            else if (patternIndex < p.length() && p.charAt(patternIndex) == '*')
            {
                starIndex = patternIndex++;
                valueCheckpoint = valueIndex;
            }
            else if (starIndex >= 0)
            {
                patternIndex = starIndex + 1;
                valueIndex = ++valueCheckpoint;
            }
            else
            {
                return false;
            }
        }

        while (patternIndex < p.length() && p.charAt(patternIndex) == '*')
        {
            patternIndex++;
        }
        return patternIndex == p.length();
    }
}
