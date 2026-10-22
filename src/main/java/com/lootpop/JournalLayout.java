package com.lootpop;

public enum JournalLayout
{
    INDIVIDUAL("Separate"), DAILY("Grouped");

    private final String label;
    JournalLayout(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
