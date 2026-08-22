package dev.progressioncoach.ui;

import dev.progressioncoach.ProgressionCoachClient;
import dev.progressioncoach.api.ProfileSnapshot;
import dev.progressioncoach.planner.ProgressionGoal;
import dev.progressioncoach.planner.Recommendation;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only in-game dashboard for the current progression plan. */
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
        g.text(font, "Profile-driven goals • read-only Hypixel API client", 20, 33, MUTED, false);

        String status = runtime.status();
        int statusColor = status.equals("READY") ? ACCENT : status.equals("LOADING") ? AMBER : RED;
        g.fill(width - Math.max(76, status.length() * 6 + 24) - 18, 14,
                width - 18, 35, statusColor & 0x35FFFFFF | 0xFF000000);
        g.text(font, status, width - Math.max(76, status.length() * 6 + 24) - 8, 20, statusColor, true);

        int margin = 18;
        int gap = 10;
        int contentY = 70;
        int leftWidth = Math.max(250, (width - margin * 2 - gap) * 40 / 100);
        int rightX = margin + leftWidth + gap;
        int rightWidth = Math.max(260, width - rightX - margin);
        panel(g, margin, contentY, leftWidth, height - contentY - 58, ACCENT);
        panel(g, rightX, contentY, rightWidth, height - contentY - 58, 0xFF69B8D1);

        ProfileSnapshot profile = runtime.snapshot();
        ProgressionGoal goal = runtime.goal();
        g.text(font, "CURRENT GOAL", margin + 14, contentY + 14, ACCENT, true);
        g.text(font, goal.label(), margin + 14, contentY + 34, WHITE, true);
        g.text(font, goal.description(), margin + 14, contentY + 52, MUTED, false);
        divider(g, margin + 14, contentY + 72, leftWidth - 28);
        row(g, margin + 14, contentY + 92, "API key", runtime.apiKeyStatus(), runtime.apiKeyConfigured() ? ACCENT : AMBER);
        row(g, margin + 14, contentY + 112, "Last refresh", runtime.lastRefreshText(), profile == null ? DIM : ACCENT);
        row(g, margin + 14, contentY + 132, "Profile", profile == null ? "Not loaded" : trim(profile.displayProfile(), 33), WHITE);
        row(g, margin + 14, contentY + 152, "Purse", profile == null ? "—" : coins(profile.purse()), WHITE);
        row(g, margin + 14, contentY + 172, "Bank", profile == null ? "—" : coins(profile.bankBalance()), WHITE);
        row(g, margin + 14, contentY + 192, "Collections", profile == null ? "—" : Long.toString(profile.collectionCount()), WHITE);
        row(g, margin + 14, contentY + 212, "Bazaar products", profile == null ? "—" : Integer.toString(profile.bazaar().size()),
                profile != null && !profile.bazaar().isEmpty() ? ACCENT : DIM);
        if (!runtime.lastError().isBlank()) {
            divider(g, margin + 14, contentY + 232, leftWidth - 28);
            g.text(font, "LAST ERROR", margin + 14, contentY + 248, RED, true);
            g.text(font, trim(runtime.lastError(), Math.max(28, leftWidth / 6)), margin + 14, contentY + 268, RED, false);
        }

        g.text(font, "NEXT BEST STEPS", rightX + 14, contentY + 14, 0xFF69B8D1, true);
        g.text(font, "Recommendations change with your selected goal and profile", rightX + 14, contentY + 33, MUTED, false);
        divider(g, rightX + 14, contentY + 52, rightWidth - 28);
        List<Recommendation> recommendations = runtime.recommendations();
        if (recommendations.isEmpty()) {
            box(g, rightX + 14, contentY + 72, rightWidth - 28, 62, PANEL_ALT, BORDER);
            g.text(font, runtime.apiKeyConfigured() ? "Refresh your profile to generate a plan." :
                    "Add an API key, then run /coach refresh.", rightX + 26, contentY + 88, WHITE, true);
            g.text(font, "The coach never sends gameplay actions.", rightX + 26, contentY + 108, MUTED, false);
        } else {
            int y = contentY + 67;
            for (int index = 0; index < recommendations.size() && y < height - 82; index++) {
                Recommendation recommendation = recommendations.get(index);
                int boxHeight = 62;
                box(g, rightX + 14, y, rightWidth - 28, boxHeight, index == 0 ? PANEL_ALT : PANEL, index == 0 ? ACCENT : BORDER);
                g.text(font, (index + 1) + ". " + trim(recommendation.title(), 34), rightX + 26, y + 10,
                        index == 0 ? ACCENT : WHITE, true);
                g.text(font, trim(recommendation.reason(), Math.max(24, rightWidth / 6)), rightX + 26, y + 28, MUTED, false);
                g.text(font, "Action: " + trim(recommendation.action(), Math.max(24, rightWidth / 6)), rightX + 26, y + 45, WHITE, false);
                y += boxHeight + 8;
            }
        }
        g.text(font, "P opens this dashboard  •  /coach status  •  /coach goal <combat|dungeons|mining|wealth|accessories>",
                margin, height - 53, DIM, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void row(GuiGraphicsExtractor g, int x, int y, String label, String value, int color) {
        g.text(font, label, x, y, DIM, false);
        g.text(font, trim(value, 30), x + 112, y, color, false);
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

    private static String trim(String value, int max) {
        if (value == null) return "—";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}

