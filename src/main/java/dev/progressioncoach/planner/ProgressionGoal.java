package dev.progressioncoach.planner;

import java.util.Locale;

public enum ProgressionGoal {
    COMBAT("Combat", "Damage, survivability, and combat unlocks"),
    DUNGEONS("Dungeons", "Catacombs experience and dungeon readiness"),
    MINING("Mining", "Mining skill, collections, and mining setup"),
    WEALTH("Wealth", "Purse growth and profitable next steps"),
    ACCESSORIES("Accessories", "Accessory progression and missing collections");

    private final String label;
    private final String description;

    ProgressionGoal(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }

    public static ProgressionGoal from(String raw) {
        if (raw == null) return COMBAT;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("DUNGEON")) normalized = "DUNGEONS";
        if (normalized.equals("MONEY") || normalized.equals("COINS")) normalized = "WEALTH";
        try { return valueOf(normalized); }
        catch (IllegalArgumentException ignored) { return COMBAT; }
    }
}

