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
        Map<String, Long> collections,
        long catacombsExperience,
        Map<String, MarketQuote> bazaar,
        Instant fetchedAt) {

    public ProfileSnapshot {
        skillLevels = immutableCopy(skillLevels);
        collections = immutableCopy(collections);
        bazaar = immutableCopy(bazaar);
        fetchedAt = fetchedAt == null ? Instant.EPOCH : fetchedAt;
    }

    public double skillLevel(String skill) {
        return skillLevels.getOrDefault(skill.toLowerCase(), 0.0);
    }

    public long collectionCount() {
        return collections.values().stream().filter(value -> value != null && value > 0).count();
    }

    public String displayProfile() {
        if (profileName == null || profileName.isBlank()) return profileId;
        return profileName + " (" + profileId + ")";
    }

    public record MarketQuote(double buyPrice, double sellPrice, long buyVolume, long sellVolume) {
        public double spread() { return Math.max(0, sellPrice - buyPrice); }
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> value) {
        return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}

