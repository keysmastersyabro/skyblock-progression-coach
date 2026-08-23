package dev.progressioncoach.planner;

/** A measurable target derived from the currently loaded profile. */
public record Milestone(String title, double current, double target, String unit, String action,
                        boolean dataAvailable) {
    public Milestone {
        title = title == null || title.isBlank() ? "Untitled milestone" : title;
        unit = unit == null || unit.isBlank() ? "value" : unit;
        action = action == null || action.isBlank() ? "Review your profile" : action;
        current = Math.max(0, current);
        target = Math.max(0, target);
    }

    public boolean complete() {
        return dataAvailable && current >= target;
    }

    public double progress() {
        if (!dataAvailable) return 0;
        if (target <= 0) return 1;
        return Math.max(0, Math.min(1, current / target));
    }

    public double remaining() {
        return Math.max(0, target - current);
    }
}
