package dev.progressioncoach;

import dev.progressioncoach.api.ProfileSnapshot;
import dev.progressioncoach.planner.ProgressionGoal;
import dev.progressioncoach.planner.ProgressionPlanner;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionPlannerTest {
    private final ProgressionPlanner planner = new ProgressionPlanner();

    @Test void combatPlanExplainsLowLevelPriority() {
        ProfileSnapshot profile = profile(Map.of("combat", 12.0), 25_000, 4_000);
        var plan = planner.plan(profile, ProgressionGoal.COMBAT);
        assertFalse(plan.isEmpty());
        assertEquals("Build a Combat XP route", plan.get(0).title());
        assertTrue(plan.get(0).priority() <= 2);
    }

    @Test void wealthPlanIncludesLiquidCoins() {
        ProfileSnapshot profile = profile(Map.of(), 1_250_000, 3_750_000);
        var plan = planner.plan(profile, ProgressionGoal.WEALTH);
        assertTrue(plan.get(0).reason().contains("5.00m"));
    }

    @Test void goalPlansAreBounded() {
        ProfileSnapshot profile = profile(Map.of("mining", 30.0), 0, 0);
        for (ProgressionGoal goal : ProgressionGoal.values()) {
            assertTrue(planner.plan(profile, goal).size() <= 5);
        }
    }

    private static ProfileSnapshot profile(Map<String, Double> skills, double purse, double bank) {
        return new ProfileSnapshot("uuid", "profile", "Apple", purse, bank, skills,
                Map.of("wheat", 10L), 50_000, Map.of(), Instant.now());
    }
}

