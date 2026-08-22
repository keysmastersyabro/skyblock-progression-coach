package dev.progressioncoach;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.progressioncoach.api.HypixelApiClient;
import dev.progressioncoach.api.ProfileSnapshot;
import dev.progressioncoach.config.CoachConfig;
import dev.progressioncoach.planner.ProgressionGoal;
import dev.progressioncoach.planner.ProgressionPlanner;
import dev.progressioncoach.planner.Recommendation;
import dev.progressioncoach.ui.CoachScreen;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main client entrypoint for the read-only progression coach. */
public final class ProgressionCoachClient implements ClientModInitializer {
    public static final String MOD_ID = "progressioncoach";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final ProgressionPlanner planner = new ProgressionPlanner();
    private CoachConfig config;
    private HypixelApiClient api;
    private volatile ProfileSnapshot snapshot;
    private volatile List<Recommendation> recommendations = List.of();
    private volatile String state = "NOT CONFIGURED";
    private volatile String lastError = "";
    private volatile Instant lastRefresh;
    private KeyMapping openKey;

    @Override public void onInitializeClient() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json");
        config = CoachConfig.load(path);
        api = new HypixelApiClient(config);
        registerKeybind();
        registerCommands();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> api.close(), "progressioncoach-shutdown"));
        LOGGER.info("SkyBlock Progression Coach initialized; API key configured: {}", apiKeyConfigured());
    }

    private void registerKeybind() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.progressioncoach.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) open(client.screen);
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommands.literal("coach")
                        .executes(context -> { open(context.getSource().getClient().screen); return 1; })
                        .then(ClientCommands.literal("refresh").executes(context -> {
                            refresh();
                            feedback(context.getSource().getClient(), "Progression Coach refresh requested.");
                            return 1;
                        }))
                        .then(ClientCommands.literal("status").executes(context -> {
                            feedback(context.getSource().getClient(), statusLine());
                            return 1;
                        }))
                        .then(ClientCommands.literal("goal")
                                .then(ClientCommands.argument("name", StringArgumentType.word()).executes(context -> {
                                    ProgressionGoal goal = ProgressionGoal.from(
                                            StringArgumentType.getString(context, "name"));
                                    setGoal(goal);
                                    feedback(context.getSource().getClient(), "Goal set to " + goal.label() + ".");
                                    return 1;
                                })))
                        .then(ClientCommands.literal("key-status").executes(context -> {
                            feedback(context.getSource().getClient(), "API key: " + apiKeyStatus()
                                    + " • edit config/progressioncoach/config.json to change it");
                            return 1;
                        }))));
    }

    public void open(net.minecraft.client.gui.screens.Screen parent) {
        Minecraft.getInstance().setScreen(new CoachScreen(parent, this));
    }

    public void refresh() {
        if (!apiKeyConfigured()) {
            state = "API KEY NEEDED";
            lastError = "Add apiKey to config/progressioncoach/config.json";
            return;
        }
        if (lastRefresh != null && lastRefresh.plusSeconds(config.safeRefreshSeconds()).isAfter(Instant.now())) {
            state = "RATE LIMITED";
            lastError = "Refresh interval is " + config.safeRefreshSeconds() + " seconds";
            return;
        }
        Minecraft client = Minecraft.getInstance();
        String uuid = client.getUser() == null || client.getUser().getProfileId() == null
                ? "" : client.getUser().getProfileId().toString();
        state = "LOADING";
        lastError = "";
        api.fetchSnapshot(uuid).whenComplete((value, error) -> client.execute(() -> {
            if (error != null) {
                state = "ERROR";
                lastError = rootMessage(error);
                LOGGER.warn("Progression refresh failed: {}", lastError);
                return;
            }
            snapshot = value;
            recommendations = planner.plan(value, goal());
            lastRefresh = Instant.now();
            state = "READY";
            lastError = "";
        }));
    }

    public void setGoal(ProgressionGoal next) {
        ProgressionGoal selected = next == null ? ProgressionGoal.COMBAT : next;
        config = config.withGoal(selected);
        CoachConfig.save(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json"), config);
        api.updateConfig(config);
        recommendations = planner.plan(snapshot, selected);
    }

    public boolean apiKeyConfigured() { return config != null && !config.safeApiKey().isBlank(); }
    public String apiKeyStatus() { return apiKeyConfigured() ? "configured (" + mask(config.safeApiKey()) + ")" : "missing"; }
    public String status() { return state; }
    public String lastError() { return lastError == null ? "" : lastError; }
    public ProfileSnapshot snapshot() { return snapshot; }
    public List<Recommendation> recommendations() { return recommendations; }
    public ProgressionGoal goal() { return config == null ? ProgressionGoal.COMBAT : config.parsedGoal(); }

    public String lastRefreshText() {
        return lastRefresh == null ? "never" : lastRefresh.toString().replace('T', ' ').replace('Z', ' ');
    }

    public String statusLine() {
        String profile = snapshot == null ? "no profile" : snapshot.displayProfile();
        return "[Coach] " + state + " • goal " + goal().label() + " • " + profile
                + (lastError().isBlank() ? "" : " • " + lastError());
    }

    private static void feedback(Minecraft client, String message) {
        if (client.gui != null) client.gui.getChat().addClientSystemMessage(Component.literal("§b[Coach] §f" + message));
    }

    private static String mask(String key) {
        if (key.length() <= 6) return "******";
        return key.substring(0, 3) + "…" + key.substring(key.length() - 3);
    }

    private static String rootMessage(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }
}

