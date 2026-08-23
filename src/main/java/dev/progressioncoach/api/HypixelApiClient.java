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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded, read-only client for the official Hypixel API. */
public final class HypixelApiClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("progressioncoach-api");
    private static final List<String> SKILLS = List.of(
            "farming", "mining", "combat", "foraging", "fishing",
            "enchanting", "alchemy", "taming", "carpentry", "runecrafting",
            "social", "hunting");
    private final HttpClient http;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile CoachConfig config;
    private volatile Map<String, List<SkillTier>> skillThresholds = Map.of();
    private volatile Instant skillThresholdsFetchedAt = Instant.EPOCH;

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
        ProfileSnapshot base = parseProfile(profileResponse, uuid, loadSkillThresholds(current));
        Map<String, ProfileSnapshot.MarketQuote> bazaar = Map.of();
        try {
            bazaar = parseBazaar(request("/v2/skyblock/bazaar", current));
        } catch (RuntimeException error) {
            LOGGER.warn("Bazaar refresh failed; profile data will still be shown: {}", error.getMessage());
        }
        return base.withBazaar(bazaar, Instant.now());
    }

    private Map<String, List<SkillTier>> loadSkillThresholds(CoachConfig current) {
        Instant now = Instant.now();
        Map<String, List<SkillTier>> cached = skillThresholds;
        if (!cached.isEmpty() || skillThresholdsFetchedAt.plus(Duration.ofHours(6)).isAfter(now)) return cached;

        skillThresholdsFetchedAt = now;
        try {
            Map<String, List<SkillTier>> loaded = parseSkillResources(
                    request("/v2/resources/skyblock/skills", current));
            if (!loaded.isEmpty()) skillThresholds = loaded;
        } catch (RuntimeException error) {
            LOGGER.warn("Skill resource refresh failed; raw experience will still be shown: {}", error.getMessage());
        }
        return skillThresholds;
    }

    private JsonObject request(String path, CoachConfig current) {
        URI uri = URI.create(current.safeApiBaseUrl() + path);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("API-Key", current.safeApiKey())
                .header("User-Agent", "SkyBlockProgressionCoach/0.2.0")
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

    static ProfileSnapshot parseProfile(JsonObject response, String uuid) {
        return parseProfile(response, uuid, Map.of());
    }

    static ProfileSnapshot parseProfile(JsonObject response, String uuid,
                                                Map<String, List<SkillTier>> thresholds) {
        JsonArray profiles = array(response, "profiles");
        if (profiles == null || profiles.size() == 0) throw new IllegalStateException("No SkyBlock profiles were returned");

        JsonObject selected = profiles.get(0).getAsJsonObject();
        for (JsonElement element : profiles) {
            JsonObject candidate = object(element);
            if (candidate != null && bool(candidate, "selected")) {
                selected = candidate;
                break;
            }
        }

        JsonObject members = firstObject(selected, "members");
        JsonObject member = selectMember(members, uuid);
        if (member == null) throw new IllegalStateException("The selected profile has no member data");

        JsonObject playerData = firstObject(member, "player_data");
        SkillData skillData = parseSkills(member, playerData, thresholds);
        Map<String, Long> collections = parseLongMap(firstObject(member, "collection", "collections"));
        Map<String, Long> slayers = parseSlayers(firstObject(member, "slayer_bosses", "slayers"));
        JsonObject dungeonTypes = object(object(member, "dungeons"), "dungeon_types");
        JsonObject catacombsObject = objectByName(dungeonTypes, "catacombs");
        Double catacombsValue = firstNumber(catacombsObject, "experience", "xp");
        boolean catacombsAvailable = catacombsValue != null;
        long catacombs = catacombsValue == null ? 0 : Math.max(0, Math.round(catacombsValue));
        if (!catacombsAvailable) {
            Double directCatacombs = firstNumber(member, "catacombs_experience");
            if (directCatacombs != null) {
                catacombs = Math.max(0, Math.round(directCatacombs));
                catacombsAvailable = true;
            }
        }

        AccessoryData accessories = parseAccessories(member);
        String profileId = text(selected, "profile_id", "unknown");
        String profileName = text(selected, "cute_name", "Profile");
        double purse = number(member, "currencies", "coin_purse");
        if (purse == 0) purse = firstNumber(member, "coin_purse");
        double bank = number(selected, "banking", "balance");
        return new ProfileSnapshot(uuid, profileId, profileName, purse, bank, skillData.levels,
                skillData.experience, collections, slayers, catacombs, catacombsAvailable, Map.of(),
                accessories.count, accessories.available, Instant.now());
    }

    private static JsonObject selectMember(JsonObject members, String uuid) {
        if (members == null) return null;
        String normalizedUuid = normalizeUuid(uuid);

        JsonObject direct = objectByName(members, uuid);
        if (direct == null) direct = objectByName(members, uuid == null ? "" : uuid.replace("-", ""));
        if (direct != null) return direct;

        if (normalizedUuid.equals(normalizeUuid(text(members, "player_id", "")))) {
            JsonObject nested = firstObject(members, "profile");
            if (nested != null) return nested;
            return members;
        }

        JsonObject profile = firstObject(members, "profile");
        if (profile != null && (normalizedUuid.isBlank()
                || normalizedUuid.equals(normalizeUuid(text(profile, "player_id", ""))))) return profile;

        for (Map.Entry<String, JsonElement> entry : members.entrySet()) {
            JsonObject candidate = object(entry.getValue());
            if (candidate == null) continue;
            if (normalizedUuid.equals(normalizeUuid(entry.getKey()))
                    || normalizedUuid.equals(normalizeUuid(text(candidate, "player_id", "")))) return unwrapMember(candidate);
            JsonObject nested = firstObject(candidate, "profile");
            if (nested != null && normalizedUuid.equals(normalizeUuid(text(nested, "player_id", ""))))
                return nested;
        }

        if (profile != null) return profile;
        for (JsonElement value : members.entrySet().stream().map(Map.Entry::getValue).toList()) {
            JsonObject candidate = object(value);
            if (candidate != null) return unwrapMember(candidate);
        }
        return null;
    }

    private static JsonObject unwrapMember(JsonObject candidate) {
        JsonObject nested = firstObject(candidate, "profile");
        return nested == null ? candidate : nested;
    }

    private static SkillData parseSkills(JsonObject member, JsonObject playerData,
                                         Map<String, List<SkillTier>> thresholds) {
        Map<String, Double> levels = new LinkedHashMap<>();
        Map<String, Double> experience = new LinkedHashMap<>();
        JsonObject skillsObject = firstObject(member, "skills");
        if (skillsObject == null && playerData != null) skillsObject = firstObject(playerData, "skills");
        JsonObject experienceObject = firstObject(member, "experience");
        if (experienceObject == null && playerData != null) experienceObject = firstObject(playerData, "experience");

        for (String name : SKILLS) {
            JsonObject skillObject = objectByName(skillsObject, name);
            Double level = firstNumber(skillObject, "level", "current_level");
            Double xp = firstNumber(skillObject, "experience", "xp", "total_experience", "totalExp");

            if (xp == null) xp = firstNumber(member, "experience_skill_" + name, "skill_experience_" + name);
            if (xp == null && playerData != null)
                xp = firstNumber(playerData, "experience_skill_" + name, "skill_experience_" + name);
            if (xp == null) xp = firstNumber(experienceObject, "experience_skill_" + name, "skill_" + name,
                    name + "_experience", name);

            if (level == null) {
                Double candidate = firstNumber(member, "skill_" + name, name + "_level", "level_" + name);
                if (candidate == null && playerData != null)
                    candidate = firstNumber(playerData, "skill_" + name, name + "_level", "level_" + name);
                if (candidate != null) {
                    if (candidate <= 100) level = candidate;
                    else if (xp == null) xp = candidate;
                }
            }

            if (level == null && xp != null) level = levelFromExperience(name, xp, thresholds);
            if (level != null) levels.put(name, Math.max(0, level));
            if (xp != null) experience.put(name, Math.max(0, xp));
        }
        return new SkillData(levels, experience);
    }

    private static Map<String, List<SkillTier>> parseSkillResources(JsonObject response) {
        JsonObject skills = firstObject(response, "skills");
        if (skills == null) return Map.of();
        Map<String, List<SkillTier>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : skills.entrySet()) {
            JsonObject skill = object(entry.getValue());
            JsonArray levels = array(skill, "levels");
            if (levels == null) continue;
            List<SkillTier> tiers = new ArrayList<>();
            double runningExperience = 0;
            for (JsonElement element : levels) {
                JsonObject level = object(element);
                if (level == null) continue;
                Double total = firstNumber(level, "totalExpRequired", "total_exp_required",
                        "totalExperienceRequired", "total_experience_required");
                if (total == null) {
                    Double required = firstNumber(level, "expRequired", "xpRequired", "xp_required");
                    if (required != null) {
                        runningExperience += required;
                        total = runningExperience;
                    }
                } else {
                    runningExperience = total;
                }
                Double levelNumber = firstNumber(level, "level");
                if (total != null && levelNumber != null) tiers.add(new SkillTier(total, levelNumber));
            }
            if (!tiers.isEmpty()) result.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(tiers));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Double levelFromExperience(String name, double xp,
                                              Map<String, List<SkillTier>> thresholds) {
        List<SkillTier> tiers = thresholds.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
        if (tiers.isEmpty()) return null;
        double level = 1;
        for (SkillTier tier : tiers) {
            if (xp < tier.requiredExperience()) break;
            level = tier.level();
        }
        return level;
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

    private static Map<String, Long> parseSlayers(JsonObject slayers) {
        if (slayers == null) return Map.of();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : slayers.entrySet()) {
            JsonObject boss = object(entry.getValue());
            if (boss == null) continue;
            long xp = longNumber(boss, "xp");
            if (xp == 0) xp = longNumber(boss, "experience");
            if (xp > 0) result.put(entry.getKey().toLowerCase(Locale.ROOT), xp);
        }
        return result;
    }

    private static AccessoryData parseAccessories(JsonObject member) {
        JsonObject inventory = firstObject(member, "inventory");
        if (inventory == null) return new AccessoryData(-1, false);
        JsonObject bags = firstObject(inventory, "bag_contents", "bags");

        for (String field : List.of("talisman_bag", "accessory_bag", "accessory_bag_contents")) {
            JsonElement value = elementByName(inventory, field);
            if (value == null) value = elementByName(bags, field);
            String encoded = encodedValue(value);
            if (encoded != null) {
                ProfileInventoryParser.Result result = ProfileInventoryParser.countAccessories(encoded);
                if (result.available()) return new AccessoryData(result.count(), true);
            }
            int directCount = jsonItemCount(value);
            if (directCount >= 0) return new AccessoryData(directCount, true);
        }
        return new AccessoryData(-1, false);
    }

    private static String encodedValue(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString();
        JsonObject object = object(value);
        if (object == null) return null;
        for (String key : List.of("data", "bytes", "value")) {
            JsonElement nested = elementByName(object, key);
            if (nested != null && nested.isJsonPrimitive() && nested.getAsJsonPrimitive().isString())
                return nested.getAsString();
        }
        return null;
    }

    private static int jsonItemCount(JsonElement value) {
        if (value == null || value.isJsonNull()) return -1;
        if (value.isJsonArray()) return jsonArrayCount(value.getAsJsonArray());
        JsonObject object = object(value);
        if (object == null) return -1;
        for (String key : List.of("items", "contents", "data")) {
            JsonElement nested = elementByName(object, key);
            if (nested != null && nested.isJsonArray()) return jsonItemCount(nested);
        }
        return -1;
    }

    private static int jsonArrayCount(JsonArray array) {
        int count = 0;
        for (JsonElement element : array) if (!element.isJsonNull()) count++;
        return count;
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
            JsonObject value = objectByName(root, name);
            if (value != null) return value;
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

    private static JsonObject objectByName(JsonObject root, String name) {
        JsonElement value = elementByName(root, name);
        return object(value);
    }

    private static JsonElement elementByName(JsonObject root, String name) {
        if (root == null || name == null) return null;
        JsonElement direct = root.get(name);
        if (direct != null) return direct;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = elementByName(root, name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static boolean bool(JsonObject root, String name) {
        try {
            JsonElement value = elementByName(root, name);
            return value != null && value.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static double number(JsonObject root, String... path) {
        JsonObject parent = root;
        for (int i = 0; i < path.length - 1; i++) parent = objectByName(parent, path[i]);
        if (parent == null) return 0;
        return firstNumber(parent, path[path.length - 1]) == null
                ? 0 : firstNumber(parent, path[path.length - 1]);
    }

    private static Double firstNumber(JsonObject root, String... names) {
        if (root == null) return null;
        for (String name : names) {
            JsonElement value = elementByName(root, name);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
            try {
                return value.getAsDouble();
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static long longNumber(JsonObject root, String... path) {
        return Math.max(0, Math.round(number(root, path)));
    }

    private static String text(JsonObject root, String name, String fallback) {
        try {
            JsonElement value = elementByName(root, name);
            return value == null ? fallback : value.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String normalizeUuid(String value) {
        return value == null ? "" : value.replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record SkillData(Map<String, Double> levels, Map<String, Double> experience) {}
    record SkillTier(double requiredExperience, double level) {}
    record AccessoryData(int count, boolean available) {}

    @Override public void close() {
        if (executor instanceof java.util.concurrent.ExecutorService service) service.close();
    }
}
