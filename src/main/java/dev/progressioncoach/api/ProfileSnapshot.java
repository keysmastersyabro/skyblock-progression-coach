package dev.progressioncoach.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small, stable projection of the Hypixel response used by the planner and screen. */
public record ProfileSnapshot(
        String uuid,
        String profileId,
        String profileName,
        double purse,
        double bankBalance,
        Map<String, Double> skillLevels,
        Map<String, Double> skillExperience,
        Map<String, Long> collections,
        Map<String, Long> slayerExperience,
        long catacombsExperience,
        boolean catacombsDataAvailable,
        Map<String, MarketQuote> bazaar,
        int accessoryCount,
        boolean accessoryDataAvailable,
        Instant fetchedAt) {

    /** Compatibility constructor for small callers and tests that only need the original projection. */
    public ProfileSnapshot(String uuid, String profileId, String profileName, double purse, double bankBalance,
                           Map<String, Double> skillLevels, Map<String, Long> collections,
                           long catacombsExperience, Map<String, MarketQuote> bazaar, Instant fetchedAt) {
        this(uuid, profileId, profileName, purse, bankBalance, skillLevels, Map.of(), collections, Map.of(),
                catacombsExperience, false, bazaar, -1, false, fetchedAt);
    }

    public ProfileSnapshot {
        skillLevels = immutableCopy(skillLevels);
        skillExperience = immutableCopy(skillExperience);
        collections = immutableCopy(collections);
        slayerExperience = immutableCopy(slayerExperience);
        bazaar = immutableCopy(bazaar);
        accessoryCount = Math.max(-1, accessoryCount);
        fetchedAt = fetchedAt == null ? Instant.EPOCH : fetchedAt;
    }

    public double skillLevel(String skill) {
        return skillLevels.getOrDefault(normalize(skill), 0.0);
    }

    public double skillXp(String skill) {
        return skillExperience.getOrDefault(normalize(skill), 0.0);
    }

    public long collection(String id) {
        if (id == null) return 0;
        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        return collections.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getKey().toLowerCase(java.util.Locale.ROOT).equals(normalized))
                .mapToLong(entry -> Math.max(0, entry.getValue() == null ? 0 : entry.getValue()))
                .findFirst().orElse(0);
    }

    public long collectionAny(String... ids) {
        if (ids == null) return 0;
        for (String id : ids) {
            long value = collection(id);
            if (value > 0) return value;
        }
        return 0;
    }

    public long slayerXp(String boss) {
        if (boss == null) return 0;
        return slayerExperience.getOrDefault(boss.toLowerCase(java.util.Locale.ROOT), 0L);
    }

    public long totalSlayerXp() {
        return slayerExperience.values().stream()
                .mapToLong(value -> Math.max(0, value == null ? 0 : value)).sum();
    }

    public double liquidCoins() {
        return Math.max(0, purse) + Math.max(0, bankBalance);
    }

    public ProfileSnapshot withBazaar(Map<String, MarketQuote> nextBazaar, Instant refreshedAt) {
        return new ProfileSnapshot(uuid, profileId, profileName, purse, bankBalance, skillLevels, skillExperience,
                collections, slayerExperience, catacombsExperience, catacombsDataAvailable, nextBazaar, accessoryCount,
                accessoryDataAvailable, refreshedAt);
    }

    public long collectionCount() {
        return collections.values().stream().filter(value -> value != null && value > 0).count();
    }

    public String displayProfile() {
        if (profileName == null || profileName.isBlank()) return profileId;
        return profileName + " (" + profileId + ")";
    }

    public record MarketQuote(double buyPrice, double sellPrice, long buyVolume, long sellVolume) {
        public double spread() { return Math.max(0, buyPrice - sellPrice); }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> value) {
        return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
