package dev.progressioncoach.planner;

import dev.progressioncoach.api.ProfileSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure recommendation engine. It has no Minecraft or network dependency, so it is easy to test. */
public final class ProgressionPlanner {
    public List<Recommendation> plan(ProfileSnapshot profile, ProgressionGoal goal) {
        if (profile == null) return List.of();
        ProgressionGoal selected = goal == null ? ProgressionGoal.COMBAT : goal;
        List<Recommendation> recommendations = switch (selected) {
            case COMBAT -> combat(profile);
            case DUNGEONS -> dungeons(profile);
            case MINING -> mining(profile);
            case WEALTH -> wealth(profile);
            case ACCESSORIES -> accessories(profile);
        };
        return recommendations.stream()
                .sorted(Comparator.comparingInt(Recommendation::priority))
                .limit(5)
                .toList();
    }

    private List<Recommendation> combat(ProfileSnapshot profile) {
        double level = profile.skillLevel("combat");
        List<Recommendation> result = new ArrayList<>();
        if (level < 20) result.add(new Recommendation("Build a Combat XP route",
                "Your Combat level is " + whole(level) + ". Early unlocks usually outperform expensive gear.",
                "Run the strongest combat content you can clear consistently.", 1, 0));
        else result.add(new Recommendation("Audit damage before buying gear",
                "Your Combat level is " + whole(level) + ". Check accessories, weapon upgrades, and combat pets together.",
                "Compare the next damage upgrade by coins per damage gained.", 2, 0));
        result.add(new Recommendation("Close the survivability gap",
                "A damage-only upgrade is wasted if your current setup cannot clear its target consistently.",
                "Test one higher-difficulty area and record deaths, clear time, and healing usage.", 3, 0));
        result.add(new Recommendation("Use the market snapshot as a price check",
                profile.bazaar().isEmpty() ? "Bazaar data was unavailable in the last refresh." :
                        profile.bazaar().size() + " Bazaar products were available for price context.",
                "Recheck prices immediately before committing coins.", 4, 0));
        return result;
    }

    private List<Recommendation> dungeons(ProfileSnapshot profile) {
        List<Recommendation> result = new ArrayList<>();
        result.add(new Recommendation("Raise Catacombs experience",
                "The profile reports " + compact(profile.catacombsExperience()) + " Catacombs XP.",
                "Choose the highest floor you can complete without slowing the party down.",
                profile.catacombsExperience() < 100_000 ? 1 : 2, 0));
        result.add(new Recommendation("Prepare a dungeon-specific loadout",
                "Dungeon readiness depends on survivability and clear consistency, not just profile net worth.",
                "Keep a dedicated weapon, healing option, and utility slot ready before queueing.", 2, 0));
        result.add(new Recommendation("Review party-role bottlenecks",
                "A balanced role and a repeatable clear usually beat an untested high-cost upgrade.",
                "Track the first failed room or boss phase on your next three runs.", 3, 0));
        return result;
    }

    private List<Recommendation> mining(ProfileSnapshot profile) {
        double level = profile.skillLevel("mining");
        List<Recommendation> result = new ArrayList<>();
        result.add(new Recommendation("Build Mining skill first",
                "Your Mining level is " + whole(level) + ". Skill milestones unlock better routes and tools.",
                "Farm the best consistent ore route available to your current gear.", level < 30 ? 1 : 2, 0));
        result.add(new Recommendation("Turn collections into permanent upgrades",
                "The profile has " + profile.collectionCount() + " non-empty collections.",
                "Check mining collections for recipe or upgrade unlocks before buying a replacement item.", 2, 0));
        result.add(new Recommendation("Compare mining setup cost per hour",
                profile.bazaar().isEmpty() ? "No Bazaar snapshot is available for a price comparison." :
                        "The current Bazaar snapshot contains " + profile.bazaar().size() + " products.",
                "Record a ten-minute baseline, then price only the upgrade that changes your route.", 3, 0));
        return result;
    }

    private List<Recommendation> wealth(ProfileSnapshot profile) {
        List<Recommendation> result = new ArrayList<>();
        double liquid = profile.purse() + profile.bankBalance();
        result.add(new Recommendation("Protect your liquid-cash runway",
                "Your visible purse plus bank balance is approximately " + compact(liquid) + " coins.",
                "Keep enough liquid coins for one planned upgrade before experimenting with a new method.", 1, 0));
        result.add(new Recommendation("Choose a measurable money method",
                profile.bazaar().isEmpty() ? "A Bazaar snapshot was not available." :
                        "Live price context is available for " + profile.bazaar().size() + " products.",
                "Measure coins per hour over a full sample instead of following a single price spike.", 2, 0));
        result.add(new Recommendation("Set a purchase threshold",
                "A goal is easier to reach when an upgrade has a fixed maximum price.",
                "Write down the item, maximum spend, and expected improvement before shopping.", 3, 0));
        return result;
    }

    private List<Recommendation> accessories(ProfileSnapshot profile) {
        return List.of(
                new Recommendation("Audit accessory progression",
                        "The profile reports " + profile.collectionCount() + " non-empty collections; collection unlocks can be cheaper than gear.",
                        "Compare your missing accessory recipes against the next combat milestone.", 1, 0),
                new Recommendation("Prioritize useful magical power",
                        "More accessories are not automatically better if the upgrade does not change your build.",
                        "Choose a power that supports your selected combat or dungeon setup.", 2, 0),
                new Recommendation("Buy only after checking the market",
                        profile.bazaar().isEmpty() ? "Bazaar data was unavailable." : "The latest refresh contains live Bazaar context.",
                        "Set a ceiling price and wait for a normal market window.", 3, 0));
    }

    private static String whole(double value) { return Long.toString(Math.round(Math.max(0, value))); }

    private static String compact(double value) {
        if (value >= 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.2fb", value / 1_000_000_000);
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.2fm", value / 1_000_000);
        if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000);
        return Long.toString(Math.round(Math.max(0, value)));
    }
}

