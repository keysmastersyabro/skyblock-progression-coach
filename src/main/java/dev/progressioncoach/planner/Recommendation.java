package dev.progressioncoach.planner;

public record Recommendation(String title, String reason, String action, int priority, double estimatedCost) {
    public Recommendation {
        if (title == null) title = "Untitled recommendation";
        if (reason == null) reason = "No reason available";
        if (action == null) action = "Review your profile";
        priority = Math.max(1, Math.min(5, priority));
        estimatedCost = Math.max(0, estimatedCost);
    }
}

