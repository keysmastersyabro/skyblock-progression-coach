package dev.progressioncoach.planner;

import dev.progressioncoach.api.ProfileSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure profile-audit engine. Every recommendation is derived from a measurable field
 * in the loaded profile or from the live Bazaar snapshot.
 */
public final class ProgressionPlanner {
    private static final double[] COIN_CHECKPOINTS = {
            1_000_000, 5_000_000, 10_000_000, 25_000_000, 50_000_000,
            100_000_000, 250_000_000, 500_000_000, 1_000_000_000
    };
    private static final double[] XP_CHECKPOINTS = {
            100_000, 500_000, 1_000_000, 5_000_000, 10_000_000,
            25_000_000, 50_000_000, 100_000_000
    };

    public List<Milestone> milestones(ProfileSnapshot profile, ProgressionGoal goal) {
        if (profile == null) return List.of();
        ProgressionGoal selected = goal == null ? ProgressionGoal.COMBAT : goal;
        return switch (selected) {
            case COMBAT -> combatMilestones(profile);
            case DUNGEONS -> dungeonMilestones(profile);
            case MINING -> miningMilestones(profile);
            case WEALTH -> wealthMilestones(profile);
            case ACCESSORIES -> accessoryMilestones(profile);
        };
    }

    public List<Recommendation> plan(ProfileSnapshot profile, ProgressionGoal goal) {
        if (profile == null) return List.of();
        List<Milestone> milestones = milestones(profile, goal);
        List<Recommendation> recommendations = new ArrayList<>();
        int priority = 1;
        for (Milestone milestone : milestones) {
            if (milestone.complete()) continue;
            recommendations.add(new Recommendation(milestone.title(), reason(milestone),
                    milestone.action(), priority++, 0));
        }

        ProgressionGoal selected = goal == null ? ProgressionGoal.COMBAT : goal;
        if (selected == ProgressionGoal.WEALTH) {
            Recommendation market = marketRecommendation(profile, priority);
            if (market != null) recommendations.add(market);
        }
        if (recommendations.isEmpty()) {
            recommendations.add(new Recommendation("Goal checkpoint reached",
                    "All measurable checkpoints for " + selected.label() + " are complete.",
                    "Pick the next goal or raise your own target before spending coins.", 1, 0));
        }
        return recommendations.stream()
                .sorted(Comparator.comparingInt(Recommendation::priority))
                .limit(5)
                .toList();
    }

    private List<Milestone> combatMilestones(ProfileSnapshot profile) {
        double level = profile.skillLevel("combat");
        boolean levelAvailable = profile.skillLevels().containsKey("combat");
        double target = nextSkillTarget(level, 60);
        return List.of(
                new Milestone("Build a Combat XP route", level, target, "Combat level",
                        "Earn Combat XP until level " + whole(target) + ", then reassess your damage breakpoint.",
                        levelAvailable),
                new Milestone("Raise total Slayer XP", profile.totalSlayerXp(),
                        nextCheckpoint(profile.totalSlayerXp(), XP_CHECKPOINTS), "Slayer XP",
                        "Run the highest Slayer tier you can clear consistently and keep the boss-specific XP visible.",
                        !profile.slayerExperience().isEmpty()),
                accessoryMilestone(profile, "Add combat-useful accessories", 15,
                        "Check missing accessory recipes before buying another weapon upgrade."));
    }

    private List<Milestone> dungeonMilestones(ProfileSnapshot profile) {
        double catacombs = profile.catacombsExperience();
        double target = nextCheckpoint(catacombs, XP_CHECKPOINTS);
        double enchanting = profile.skillLevel("enchanting");
        boolean enchantingAvailable = profile.skillLevels().containsKey("enchanting");
        return List.of(
                new Milestone("Reach the next Catacombs XP checkpoint", catacombs, target, "Catacombs XP",
                        "Repeat the highest floor you can clear without slowing the party, and stop when the run becomes inconsistent.",
                        profile.catacombsDataAvailable()),
                new Milestone("Raise Enchanting for dungeon readiness", enchanting,
                        nextSkillTarget(enchanting, 60), "Enchanting level",
                        "Use the profile's next Enchanting milestone to improve weapon and utility options before chasing expensive gear.",
                        enchantingAvailable),
                accessoryMilestone(profile, "Add dungeon-useful accessories", 15,
                        "Compare magical-power gains against the cost of another dungeon item."));
    }

    private List<Milestone> miningMilestones(ProfileSnapshot profile) {
        double level = profile.skillLevel("mining");
        boolean levelAvailable = profile.skillLevels().containsKey("mining");
        long mithril = profile.collectionAny("MITHRIL", "MITHRIL_ORE");
        long titanium = profile.collectionAny("TITANIUM");
        boolean collectionsAvailable = !profile.collections().isEmpty();
        return List.of(
                new Milestone("Reach the next Mining level", level, nextSkillTarget(level, 60), "Mining level",
                        "Mine the strongest route you can sustain; a level milestone is more reliable than buying a tool blindly.",
                        levelAvailable),
                new Milestone("Build Mithril collection", mithril, nextCollectionTarget(mithril), "Mithril",
                        "Use Mithril collection milestones to unlock permanent options before replacing the whole setup.",
                        collectionsAvailable),
                new Milestone("Build Titanium collection", titanium, nextCollectionTarget(titanium), "Titanium",
                        "Track Titanium separately; it is a concrete upgrade gate rather than a vague 'mine more' goal.",
                        collectionsAvailable));
    }

    private List<Milestone> wealthMilestones(ProfileSnapshot profile) {
        double liquid = profile.liquidCoins();
        double target = nextCheckpoint(liquid, COIN_CHECKPOINTS);
        double reserveRatio = liquid <= 0 ? 0 : profile.bankBalance() / liquid;
        return List.of(
                new Milestone("Reach the next liquid-coin checkpoint", liquid, target, "coins",
                        "Measure one money method for a complete sample, then keep the next upgrade below your reserve threshold.",
                        true),
                new Milestone("Keep a bank reserve", reserveRatio * 100, 25, "% in bank",
                        "Keep at least 25% of visible coins in the bank before testing a new method or purchase.",
                        liquid > 0),
                new Milestone("Load a live Bazaar snapshot", profile.bazaar().isEmpty() ? 0 : 1, 1, "market",
                        "Refresh before making a purchase; the coach only uses Bazaar data as price context.",
                        true));
    }

    private List<Milestone> accessoryMilestones(ProfileSnapshot profile) {
        if (!profile.accessoryDataAvailable()) {
            return List.of(
                    new Milestone("Expose accessory-bag data", 0, 1, "API data",
                            "Enable inventory data for your Hypixel API key, then refresh; the coach will count the actual accessory bag.",
                            false),
                    new Milestone("Raise Combat for accessory value", profile.skillLevel("combat"),
                            nextSkillTarget(profile.skillLevel("combat"), 60), "Combat level",
                            "Use the next Combat milestone to make each accessory upgrade more valuable.",
                            profile.skillLevels().containsKey("combat")));
        }
        return List.of(
                accessoryMilestone(profile, "Reach the next accessory count", nextAccessoryTarget(profile.accessoryCount()),
                        "Buy or craft only the missing accessory that improves your selected build; the count comes from your actual bag."),
                new Milestone("Raise Combat for accessory value", profile.skillLevel("combat"),
                        nextSkillTarget(profile.skillLevel("combat"), 60), "Combat level",
                        "Pair accessory count with a real damage breakpoint instead of collecting items with no build purpose.",
                        profile.skillLevels().containsKey("combat")));
    }

    private static Milestone accessoryMilestone(ProfileSnapshot profile, String title, int target, String action) {
        return new Milestone(title, Math.max(0, profile.accessoryCount()), target, "accessories", action,
                profile.accessoryDataAvailable());
    }

    private static Recommendation marketRecommendation(ProfileSnapshot profile, int priority) {
        if (profile.bazaar().isEmpty()) {
            return new Recommendation("Refresh before using market prices",
                    "The profile loaded, but no live Bazaar products were returned.",
                    "Run /coach refresh after the rate-limit window; do not treat missing prices as zero.", priority, 0);
        }

        Map.Entry<String, ProfileSnapshot.MarketQuote> best = profile.bazaar().entrySet().stream()
                .filter(entry -> entry.getValue().buyPrice() > 0 && entry.getValue().sellPrice() > 0
                        && entry.getValue().buyVolume() > 0 && entry.getValue().sellVolume() > 0)
                .max(Comparator.comparingDouble(entry -> entry.getValue().spread()
                        / Math.max(1, entry.getValue().sellPrice())))
                .orElse(null);
        if (best == null) return null;
        ProfileSnapshot.MarketQuote quote = best.getValue();
        return new Recommendation("Check the largest liquid Bazaar spread",
                best.getKey() + " is showing about " + String.format(Locale.ROOT, "%.1f%%", 100
                        * quote.spread() / Math.max(1, quote.sellPrice()))
                        + " between the current weighted sell and buy prices.",
                "Verify order-book depth and taxes in-game before committing coins; this is a lead, not an auto-trade.",
                priority, 0);
    }

    private static String reason(Milestone milestone) {
        if (!milestone.dataAvailable()) {
            return "This checkpoint needs a field that was not present in the API response. The dashboard will not guess it.";
        }
        if (milestone.complete()) {
            return "Current " + value(milestone.current(), milestone.unit()) + " meets the checkpoint.";
        }
        return "Current " + value(milestone.current(), milestone.unit()) + "; "
                + value(milestone.remaining(), milestone.unit()) + " remains to reach "
                + value(milestone.target(), milestone.unit()) + ".";
    }

    private static double nextSkillTarget(double level, int maxLevel) {
        if (level <= 0) return 10;
        int rounded = (int) Math.ceil(level);
        for (int checkpoint : new int[]{10, 20, 30, 40, 50, maxLevel}) {
            if (rounded < checkpoint) return checkpoint;
        }
        return maxLevel;
    }

    private static double nextCheckpoint(double current, double[] checkpoints) {
        for (double checkpoint : checkpoints) {
            if (current < checkpoint) return checkpoint;
        }
        return Math.max(current + Math.max(1, current * 0.25), current + 1);
    }

    private static long nextCollectionTarget(long current) {
        if (current < 1_000) return 1_000;
        if (current < 10_000) return 10_000;
        if (current < 100_000) return 100_000;
        if (current < 1_000_000) return 1_000_000;
        return current + Math.max(100_000, current / 4);
    }

    private static int nextAccessoryTarget(int current) {
        for (int checkpoint : new int[]{15, 30, 45, 60, 75, 100}) {
            if (current < checkpoint) return checkpoint;
        }
        return current;
    }

    private static String value(double number, String unit) {
        if (unit.contains("level") || unit.contains("%") || unit.equals("accessories") || unit.equals("market"))
            return whole(number) + (unit.equals("% in bank") ? "%" : "");
        if (unit.contains("coins")) return compact(number);
        return compact(number) + " " + unit;
    }

    private static String whole(double value) {
        return Long.toString(Math.round(Math.max(0, value)));
    }

    private static String compact(double value) {
        double safe = Math.max(0, value);
        if (safe >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fb", safe / 1_000_000_000);
        if (safe >= 1_000_000) return String.format(Locale.ROOT, "%.2fm", safe / 1_000_000);
        if (safe >= 1_000) return String.format(Locale.ROOT, "%.1fk", safe / 1_000);
        return Long.toString(Math.round(safe));
    }
}
