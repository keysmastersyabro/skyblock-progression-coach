package dev.progressioncoach.ui;

import dev.progressioncoach.ProgressionCoachClient;
import dev.progressioncoach.api.ProfileSnapshot;
import dev.progressioncoach.planner.Milestone;
import dev.progressioncoach.planner.ProgressionGoal;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only in-game dashboard for the current profile audit. */
public final class CoachScreen extends Screen {
    private static final int BG = 0xFF09111B;
    private static final int PANEL = 0xFF121F2C;
    private static final int PANEL_ALT = 0xFF172A38;
    private static final int BORDER = 0xFF2B4858;
    private static final int ACCENT = 0xFF6DE3C0;
    private static final int WHITE = 0xFFF2F7FA;
    private static final int MUTED = 0xFFA9BCC7;
    private static final int DIM = 0xFF718895;
    private static final int AMBER = 0xFFF0C66C;
    private static final int RED = 0xFFFF7B89;
    private final Screen parent;
    private final ProgressionCoachClient runtime;

    public CoachScreen(Screen parent, ProgressionCoachClient runtime) {
        super(Component.literal("SkyBlock Progression Coach"));
        this.parent = parent;
        this.runtime = runtime;
    }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("REFRESH PROFILE"), button -> runtime.refresh())
                .bounds(18, height - 34, 142, 22).build());
        int x = 170;
        for (ProgressionGoal goal : ProgressionGoal.values()) {
            ProgressionGoal selected = goal;
            addRenderableWidget(Button.builder(Component.literal(goal.label().toUpperCase()), button -> runtime.setGoal(selected))
                    .bounds(x, height - 34, 92, 22).build());
            x += 98;
            if (x + 92 > width - 100) break;
        }
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), button -> onClose())
                .bounds(width - 86, height - 34, 68, 22).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
        g.fill(0, 0, width, 58, 0xFF102230);
        g.fill(0, 56, width, 59, 0xFF245E5A);
        g.text(font, "PROGRESSION COACH", 20, 13, ACCENT, true);
        g.text(font, "A measurable profile audit | read-only Hypixel API client", 20, 33, MUTED, false);

        String status = runtime.status();
        int statusColor = status.equals("READY") ? ACCENT : status.equals("LOADING") ? AMBER : RED;
        int statusWidth = Math.max(76, status.length() * 6 + 24);
        g.fill(width - statusWidth - 18, 14, width - 18, 35, statusColor & 0x35FFFFFF | 0xFF000000);
        g.text(font, status, width - statusWidth - 8, 20, statusColor, true);

        int margin = 18;
        int gap = 10;
        int contentY = 70;
        int panelHeight = Math.max(80, height - contentY - 58);
        int leftWidth = Math.max(250, (width - margin * 2 - gap) * 40 / 100);
        int rightX = margin + leftWidth + gap;
        int rightWidth = Math.max(260, width - rightX - margin);
        panel(g, margin, contentY, leftWidth, panelHeight, ACCENT);
        panel(g, rightX, contentY, rightWidth, panelHeight, 0xFF69B8D1);

        ProfileSnapshot profile = runtime.snapshot();
        ProgressionGoal goal = runtime.goal();
        g.text(font, "CURRENT GOAL", margin + 14, contentY + 14, ACCENT, true);
        g.text(font, goal.label(), margin + 14, contentY + 34, WHITE, true);
        g.text(font, goal.description(), margin + 14, contentY + 52, MUTED, false);
        divider(g, margin + 14, contentY + 72, leftWidth - 28);

        row(g, margin + 14, contentY + 88, "API key", runtime.apiKeyStatus(),
                runtime.apiKeyConfigured() ? ACCENT : AMBER);
        row(g, margin + 14, contentY + 106, "Last refresh", runtime.lastRefreshText(),
                profile == null ? DIM : ACCENT);
        row(g, margin + 14, contentY + 124, "Profile", profile == null ? "Not loaded" : trim(profile.displayProfile(), 29), WHITE);
        row(g, margin + 14, contentY + 142, "Liquid coins", profile == null ? "-" : coins(profile.liquidCoins()), WHITE);
        row(g, margin + 14, contentY + 160, "Bazaar", profile == null ? "-" : bazaarStatus(profile),
                profile != null && !profile.bazaar().isEmpty() ? ACCENT : DIM);

        divider(g, margin + 14, contentY + 178, leftWidth - 28);
        g.text(font, "PROFILE SIGNALS", margin + 14, contentY + 192, ACCENT, true);
        if (profile == null) {
            g.text(font, "Refresh to load real profile fields.", margin + 14, contentY + 213, MUTED, false);
        } else {
            int col = Math.max(112, leftWidth / 2);
            signal(g, margin + 14, contentY + 211, "Combat", skill(profile, "combat"));
            signal(g, margin + 14 + col, contentY + 211, "Mining", skill(profile, "mining"));
            signal(g, margin + 14, contentY + 230, "Enchant", skill(profile, "enchanting"));
            signal(g, margin + 14 + col, contentY + 230, "Catacombs", profile.catacombsDataAvailable() ? compact(profile.catacombsExperience()) + " XP" : "API data off");
            signal(g, margin + 14, contentY + 249, "Slayer", compact(profile.totalSlayerXp()) + " XP");
            signal(g, margin + 14 + col, contentY + 249, "Accessories",
                    profile.accessoryDataAvailable() ? profile.accessoryCount() + " counted" : "API data off");
        }
        if (!runtime.lastError().isBlank()) {
            int errorY = Math.min(contentY + panelHeight - 40, contentY + 270);
            divider(g, margin + 14, errorY, leftWidth - 28);
            g.text(font, "LAST ERROR", margin + 14, errorY + 14, RED, true);
            g.text(font, trim(runtime.lastError(), Math.max(28, leftWidth / 6)), margin + 14, errorY + 30, RED, false);
        }

        g.text(font, "MEASURABLE CHECKPOINTS", rightX + 14, contentY + 14, 0xFF69B8D1, true);
        g.text(font, profile == null ? "Refresh to build a profile-specific plan."
                : "Every bar below comes from fields returned by this profile.", rightX + 14, contentY + 33, MUTED, false);
        divider(g, rightX + 14, contentY + 52, rightWidth - 28);

        List<Milestone> milestones = runtime.milestones();
        if (milestones.isEmpty()) {
            box(g, rightX + 14, contentY + 72, rightWidth - 28, 62, PANEL_ALT, BORDER);
            g.text(font, runtime.apiKeyConfigured() ? "Refresh your profile to generate checkpoints." :
                    "Add an API key, then run /coach refresh.", rightX + 26, contentY + 88, WHITE, true);
            g.text(font, "The coach never sends gameplay actions.", rightX + 26, contentY + 108, MUTED, false);
        } else {
            int y = contentY + 67;
            int cardHeight = 47;
            for (int index = 0; index < milestones.size() && y + cardHeight < contentY + panelHeight - 7; index++) {
                milestone(g, rightX + 14, y, rightWidth - 28, cardHeight, milestones.get(index));
                y += cardHeight + 6;
            }
        }

        g.text(font, "P opens this dashboard | /coach refresh | /coach status | /coach key-status",
                margin, height - 53, DIM, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void milestone(GuiGraphicsExtractor g, int x, int y, int width, int height, Milestone milestone) {
        int color = !milestone.dataAvailable() ? AMBER : milestone.complete() ? ACCENT : BORDER;
        box(g, x, y, width, height, milestone.complete() ? 0xFF142B2B : PANEL_ALT, color);
        String title = trim(milestone.title(), Math.max(20, width / 7));
        g.text(font, title, x + 10, y + 7, milestone.complete() ? ACCENT : WHITE, true);

        String value = !milestone.dataAvailable() ? "DATA UNAVAILABLE"
                : format(milestone.current()) + " / " + format(milestone.target()) + " " + milestone.unit();
        int valueWidth = Math.max(92, value.length() * 6);
        g.text(font, trim(value, Math.max(15, width / 5)), x + width - valueWidth - 10, y + 7,
                !milestone.dataAvailable() ? AMBER : MUTED, false);

        int barWidth = width - 20;
        g.fill(x + 10, y + 22, x + 10 + barWidth, y + 27, 0xFF243A45);
        int filled = (int) Math.round(barWidth * milestone.progress());
        if (filled > 0) g.fill(x + 10, y + 22, x + 10 + filled, y + 27, color);
        g.text(font, trim(milestone.action(), Math.max(22, width / 6)), x + 10, y + 32, DIM, false);
    }

    private void signal(GuiGraphicsExtractor g, int x, int y, String label, String value) {
        g.text(font, label, x, y, DIM, false);
        g.text(font, trim(value, 16), x + 52, y, WHITE, false);
    }

    private void row(GuiGraphicsExtractor g, int x, int y, String label, String value, int color) {
        g.text(font, label, x, y, DIM, false);
        g.text(font, trim(value, 30), x + 112, y, color, false);
    }

    private static String skill(ProfileSnapshot profile, String name) {
        if (profile.skillLevels().containsKey(name)) return "Lv " + whole(profile.skillLevel(name));
        if (profile.skillExperience().containsKey(name)) return compact(profile.skillXp(name)) + " XP";
        return "-";
    }

    private static String bazaarStatus(ProfileSnapshot profile) {
        if (profile.bazaar().isEmpty()) return "unavailable";
        return profile.bazaar().size() + " products";
    }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, int accent) {
        if (width <= 0 || height <= 0) return;
        g.fill(x + 3, y + 4, x + width, y + height, 0xFF05090D);
        g.fill(x, y, x + width - 3, y + height - 4, BORDER);
        g.fill(x + 2, y + 2, x + width - 5, y + height - 6, PANEL);
        g.fill(x + 12, y + 2, x + width - 15, y + 5, accent);
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int width, int height, int fill, int border) {
        g.fill(x, y, x + width, y + height, border);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    private static void divider(GuiGraphicsExtractor g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFF2A4250);
    }

    private static String coins(double value) {
        return String.format(java.util.Locale.ROOT, "%,.0f", Math.max(0, value));
    }

    private static String compact(double value) {
        double safe = Math.max(0, value);
        if (safe >= 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.2fb", safe / 1_000_000_000);
        if (safe >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.2fm", safe / 1_000_000);
        if (safe >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", safe / 1_000);
        return Long.toString(Math.round(safe));
    }

    private static String format(double value) {
        return value >= 1000 ? compact(value) : whole(value);
    }

    private static String whole(double value) {
        return Long.toString(Math.round(Math.max(0, value)));
    }

    private static String trim(String value, int max) {
        if (value == null) return "-";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 3)) + "...";
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
