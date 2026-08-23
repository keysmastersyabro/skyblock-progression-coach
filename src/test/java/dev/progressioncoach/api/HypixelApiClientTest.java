package dev.progressioncoach.api;

import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypixelApiClientTest {
    private static final String UUID = "11111111-1111-1111-1111-111111111111";

    @Test void parsesCurrentMemberShapeAndConvertsSkillExperience() {
        String json = """
                {
                  "profiles": [{
                    "profile_id": "profile-two",
                    "cute_name": "Apple",
                    "selected": true,
                    "banking": {"balance": 9000},
                    "members": {
                      "player_id": "11111111111111111111111111111111",
                      "profile": {
                        "currencies": {"coin_purse": 1234.5},
                        "player_data": {"experience_skill_combat": 9925.0},
                        "collection": {"MITHRIL": 42},
                        "slayer_bosses": {"zombie": {"xp": 1000}},
                        "dungeons": {"dungeon_types": {"catacombs": {"experience": 500000}}}
                      }
                    }
                  }]
                }
                """;
        Map<String, List<HypixelApiClient.SkillTier>> thresholds = Map.of(
                "combat", List.of(
                        new HypixelApiClient.SkillTier(50, 1),
                        new HypixelApiClient.SkillTier(9925, 10),
                        new HypixelApiClient.SkillTier(14925, 11)));

        ProfileSnapshot profile = HypixelApiClient.parseProfile(
                JsonParser.parseString(json).getAsJsonObject(), UUID, thresholds);

        assertEquals("profile-two", profile.profileId());
        assertEquals(1234.5, profile.purse());
        assertEquals(9000, profile.bankBalance());
        assertEquals(9925, profile.skillXp("combat"));
        assertEquals(10, profile.skillLevel("combat"));
        assertEquals(42, profile.collection("mithril"));
        assertEquals(1000, profile.slayerXp("zombie"));
        assertEquals(500000, profile.catacombsExperience());
        assertFalse(profile.accessoryDataAvailable());
    }

    @Test void countsOnlyItemsWithExtraAttributesIds() throws Exception {
        String encoded = encodedInventory("AOTE", "BAT_TALISMAN");
        ProfileInventoryParser.Result result = ProfileInventoryParser.countAccessories(encoded);
        assertTrue(result.available());
        assertEquals(2, result.count());
    }

    private static String encodedInventory(String... ids) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(9);
            out.writeUTF("i");
            out.writeByte(10);
            out.writeInt(ids.length);
            for (String id : ids) writeItem(out, id);
            out.writeByte(0);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static void writeItem(DataOutputStream out, String id) throws Exception {
        out.writeByte(10);
        out.writeUTF("tag");
        out.writeByte(10);
        out.writeUTF("ExtraAttributes");
        out.writeByte(8);
        out.writeUTF("id");
        out.writeUTF(id);
        out.writeByte(0);
        out.writeByte(0);
        out.writeByte(0);
    }
}
