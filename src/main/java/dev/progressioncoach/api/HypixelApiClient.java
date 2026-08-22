package dev.progressioncoach.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.progressioncoach.config.CoachConfig;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded, read-only client for the official Hypixel API. */
public final class HypixelApiClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("progressioncoach-api");
    private final HttpClient http;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile CoachConfig config;

    public HypixelApiClient(CoachConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public void updateConfig(CoachConfig next) {
        this.config = next;
    }

    public CompletableFuture<ProfileSnapshot> fetchSnapshot(String uuid) {
        return CompletableFuture.supplyAsync(() -> fetchSnapshotBlocking(uuid), executor);
    }

    private ProfileSnapshot fetchSnapshotBlocking(String uuid) {
        CoachConfig current = config;
        if (current.safeApiKey().isBlank()) throw new IllegalStateException("No Hypixel API key configured");
        if (uuid == null || uuid.isBlank()) throw new IllegalStateException("Minecraft UUID is unavailable");

        JsonObject profileResponse = request("/v2/skyblock/profiles?uuid=" + encode(uuid), current);
        ProfileSnapshot base = parseProfile(profileResponse, uuid);
        Map<String, ProfileSnapshot.MarketQuote> bazaar = Map.of();
        try {
            bazaar = parseBazaar(request("/v2/skyblock/bazaar", current));
        } catch (RuntimeException error) {
            LOGGER.warn("Bazaar refresh failed; profile data will still be shown: {}", error.getMessage());
        }
        return new ProfileSnapshot(base.uuid(), base.profileId(), base.profileName(), base.purse(), base.bankBalance(),
                base.skillLevels(), base.collections(), base.catacombsExperience(), bazaar, Instant.now());
    }

    private JsonObject request(String path, CoachConfig current) {
        String separator = path.contains("?") ? "&" : "?";
        URI uri = URI.create(current.safeApiBaseUrl() + path + separator
                + "key=" + encode(current.safeApiKey()));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "SkyBlockProgressionCoach/0.1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IllegalStateException("Hypixel API returned HTTP " + response.statusCode());
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (body.has("success") && !body.get("success").getAsBoolean()) {
                String cause = text(body, "cause", "Hypixel rejected the request");
                throw new IllegalStateException(cause);
            }
            return body;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hypixel API request was interrupted");
        } catch (Exception error) {
            if (error instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Hypixel API request failed", error);
        }
    }

    private static ProfileSnapshot parseProfile(JsonObject response, String uuid) {
        JsonArray profiles = array(response, "profiles");
        if (profiles == null || profiles.size() == 0) throw new IllegalStateException("No SkyBlock profiles were returned");

        JsonObject selected = profiles.get(0).getAsJsonObject();
        for (JsonElement element : profiles) {
            JsonObject candidate = object(element);
            if (candidate != null && bool(candidate, "selected")) { selected = candidate; break; }
        }

        JsonObject members = object(selected, "members");
        JsonObject member = members == null ? null : object(members.get(uuid));
        if (member == null && members != null) member = object(members.get(uuid.replace("-", "")));
        if (member == null && members != null && !members.entrySet().isEmpty())
            member = members.entrySet().iterator().next().getValue().getAsJsonObject();
        if (member == null) throw new IllegalStateException("The selected profile has no member data");

        JsonObject playerData = object(member, "player_data");
        Map<String, Double> skills = parseSkills(member, playerData);
        Map<String, Long> collections = parseLongMap(firstObject(member, "collection", "collections"));
        long catacombs = longNumber(object(object(member, "dungeons"), "dungeon_types"), "catacombs", "experience");
        if (catacombs == 0) catacombs = longNumber(member, "catacombs_experience");

        String profileId = text(selected, "profile_id", "unknown");
        String profileName = text(selected, "cute_name", "Profile");
        double purse = number(member, "currencies", "coin_purse");
        double bank = number(selected, "banking", "balance");
        return new ProfileSnapshot(uuid, profileId, profileName, purse, bank, skills, collections,
                catacombs, Map.of(), Instant.now());
    }

    private static Map<String, Double> parseSkills(JsonObject member, JsonObject playerData) {
        Map<String, Double> result = new LinkedHashMap<>();
        String[] names = {"farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry", "runecrafting"};
        JsonObject experience = firstObject(member, "experience", "skills");
        if (experience == null && playerData != null) experience = firstObject(playerData, "experience", "skills");
        for (String name : names) {
            Double level = firstNumber(member, "skill_" + name, name, "experience_skill_" + name);
            if (level == null && playerData != null) level = firstNumber(playerData, "skill_" + name, name, "experience_skill_" + name);
            if (level == null && experience != null) level = firstNumber(experience, "skill_" + name, name, "experience_skill_" + name);
            if (level != null) result.put(name, level);
        }
        return result;
    }

    private static Map<String, Long> parseLongMap(JsonObject object) {
        if (object == null) return Map.of();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber())
                result.put(entry.getKey(), Math.max(0, entry.getValue().getAsLong()));
        }
        return result;
    }

    private static Map<String, ProfileSnapshot.MarketQuote> parseBazaar(JsonObject response) {
        JsonObject products = object(response, "products");
        if (products == null) return Map.of();
        Map<String, ProfileSnapshot.MarketQuote> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
            JsonObject product = object(entry.getValue());
            JsonObject quick = product == null ? null : object(product, "quick_status");
            if (quick == null) continue;
            result.put(entry.getKey(), new ProfileSnapshot.MarketQuote(
                    number(quick, "buyPrice"), number(quick, "sellPrice"),
                    longNumber(quick, "buyVolume"), longNumber(quick, "sellVolume")));
        }
        return result;
    }

    private static JsonObject firstObject(JsonObject root, String... names) {
        if (root == null) return null;
        for (String name : names) {
            JsonObject object = object(root, name);
            if (object != null) return object;
        }
        return null;
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonObject object(JsonObject root, String... path) {
        JsonObject current = root;
        for (String part : path) {
            current = current == null ? null : object(current.get(part));
            if (current == null) return null;
        }
        return current;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static boolean bool(JsonObject root, String name) {
        try { return root != null && root.has(name) && root.get(name).getAsBoolean(); }
        catch (Exception ignored) { return false; }
    }

    private static double number(JsonObject root, String... path) {
        JsonObject parent = root;
        for (int i = 0; i < path.length - 1; i++) parent = object(parent, path[i]);
        if (parent == null) return 0;
        try { return parent.has(path[path.length - 1]) ? parent.get(path[path.length - 1]).getAsDouble() : 0; }
        catch (Exception ignored) { return 0; }
    }

    private static Double firstNumber(JsonObject root, String... names) {
        if (root == null) return null;
        for (String name : names) {
            try {
                if (root.has(name) && root.get(name).isJsonPrimitive() && root.get(name).getAsJsonPrimitive().isNumber())
                    return root.get(name).getAsDouble();
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static long longNumber(JsonObject root, String... path) {
        return Math.max(0, Math.round(number(root, path)));
    }

    private static String text(JsonObject root, String name, String fallback) {
        try { return root != null && root.has(name) ? root.get(name).getAsString() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override public void close() {
        if (executor instanceof java.util.concurrent.ExecutorService service) service.close();
    }
}

