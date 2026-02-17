/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.skalholt;

import com.skalholt.chat.Gossip;
import com.skalholt.chat.Users;
import com.skalholt.events.SkalholtEventType;
import com.skalholt.events.DrawMapEvent;
import com.skalholt.events.KillNpcEvent;
import com.skalholt.events.PlayerUpdateHealthEvent;
import com.dialtone.skalholt.SkalholtSseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkalholtSseEvent")
class SkalholtSseEventTest {

    @Nested
    @DisplayName("JSON Parsing")
    class JsonParsing {

        @Test
        @DisplayName("Should parse valid JSON event with skalholtEventType")
        void shouldParseValidJson() throws JsonProcessingException {
            String json = """
                {
                    "uuid": "abc-123",
                    "skalholtEventType": "GOSSIP",
                    "payload": "{\\"message\\": \\"Hello world\\", \\"name\\": \\"Player1\\", \\"topic\\": \\"general\\"}",
                    "epochTimestamp": 1702900000000,
                    "playerId": "player-456",
                    "audience": "EVERYONE"
                }
                """;

            SkalholtSseEvent event = SkalholtSseEvent.fromJson(json);

            assertEquals("abc-123", event.getUuid());
            assertEquals(SkalholtEventType.GOSSIP, event.getSkalholtEventType());
            assertEquals("player-456", event.getPlayerId());
            assertEquals(1702900000000L, event.getEpochTimestamp());
            assertEquals("EVERYONE", event.getAudience());
        }

        @Test
        @DisplayName("Should ignore unknown fields")
        void shouldIgnoreUnknownFields() throws JsonProcessingException {
            String json = """
                {
                    "uuid": "abc-123",
                    "skalholtEventType": "GOSSIP",
                    "unknownField": "should be ignored",
                    "anotherUnknown": 12345
                }
                """;

            SkalholtSseEvent event = SkalholtSseEvent.fromJson(json);

            assertEquals("abc-123", event.getUuid());
            assertEquals(SkalholtEventType.GOSSIP, event.getSkalholtEventType());
        }

        @Test
        @DisplayName("Should handle minimal JSON")
        void shouldHandleMinimalJson() throws JsonProcessingException {
            String json = "{}";

            SkalholtSseEvent event = SkalholtSseEvent.fromJson(json);

            assertNull(event.getUuid());
            assertNull(event.getSkalholtEventType());
        }

        @Test
        @DisplayName("Should throw on invalid JSON")
        void shouldThrowOnInvalidJson() {
            String invalidJson = "not valid json";

            assertThrows(JsonProcessingException.class, () -> SkalholtSseEvent.fromJson(invalidJson));
        }
    }

    @Nested
    @DisplayName("Event Type Detection")
    class EventTypeDetection {

        @Test
        @DisplayName("isGossip should detect GOSSIP type")
        void isGossipShouldDetectGossipType() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.GOSSIP, null, 0L, null);
            assertTrue(event.isGossip());
            assertFalse(event.isPlayerData());
            assertFalse(event.isHealthUpdate());
        }

        @Test
        @DisplayName("isPlayerData should detect PLAYERDATA type")
        void isPlayerDataShouldDetectPlayerDataType() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.PLAYERDATA, null, 0L, null);
            assertTrue(event.isPlayerData());
        }

        @Test
        @DisplayName("isHealthUpdate should detect PLAYER_UPDATE_HEALTH type")
        void isHealthUpdateShouldDetectHealthType() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.PLAYER_UPDATE_HEALTH, null, 0L, null);
            assertTrue(event.isHealthUpdate());
        }

        @Test
        @DisplayName("isMapDraw should detect DRAW_MAP type")
        void isMapDrawShouldDetectMapType() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.DRAW_MAP, null, 0L, null);
            assertTrue(event.isMapDraw());
        }

        @Test
        @DisplayName("isNpcKill should detect NPC_KILL type")
        void isNpcKillShouldDetectKillType() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.NPC_KILL, null, 0L, null);
            assertTrue(event.isNpcKill());
        }

        @Test
        @DisplayName("isNpcDamage should detect NPC_DAMAGE type")
        void isNpcDamageShouldDetectDamageType() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.NPC_DAMAGE, null, 0L, null);
            assertTrue(event.isNpcDamage());
        }

        @Test
        @DisplayName("isUsers should detect USERS type")
        void isUsersShouldDetectUsersType() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.USERS, null, 0L, null);
            assertTrue(event.isUsers());
        }

        @Test
        @DisplayName("Type detection should return false for null type")
        void typeDetectionShouldReturnFalseForNullType() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", null, null, 0L, null);
            assertFalse(event.isGossip());
            assertFalse(event.isPlayerData());
            assertFalse(event.isHealthUpdate());
        }
    }

    @Nested
    @DisplayName("Payload Parsing")
    class PayloadParsing {

        @Test
        @DisplayName("Should parse Gossip payload")
        void shouldParseGossipPayload() {
            String payload = "{\"message\": \"Hello!\", \"name\": \"Player1\", \"topic\": \"general\", \"timestamp\": 12345}";
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.GOSSIP, payload, 0L, null);

            Optional<Gossip> gossip = event.getGossipPayload();

            assertTrue(gossip.isPresent());
            assertEquals("Hello!", gossip.get().getMessage());
            assertEquals("Player1", gossip.get().getName());
            assertEquals("general", gossip.get().getTopic());
        }

        @Test
        @DisplayName("Should parse PlayerUpdateHealthEvent payload")
        void shouldParseHealthPayload() {
            String payload = "{\"npcId\": \"goblin-1\", \"amount\": -15, \"playerName\": \"Hero\"}";
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.PLAYER_UPDATE_HEALTH, payload, 0L, null);

            Optional<PlayerUpdateHealthEvent> health = event.getHealthUpdatePayload();

            assertTrue(health.isPresent());
            assertEquals("goblin-1", health.get().getNpcId());
            assertEquals(-15, health.get().getAmount());
            assertEquals("Hero", health.get().getPlayerName());
            assertTrue(health.get().getAmount() < 0); // negative amount = damage
        }

        @Test
        @DisplayName("Should parse DrawMapEvent payload")
        void shouldParseMapPayload() {
            String payload = "{\"map\": \"###\\n#.#\\n###\"}";
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.DRAW_MAP, payload, 0L, null);

            Optional<DrawMapEvent> map = event.getDrawMapPayload();

            assertTrue(map.isPresent());
            assertEquals(3, map.get().getMap().split("\n").length);
        }

        @Test
        @DisplayName("Should parse KillNpcEvent payload")
        void shouldParseKillPayload() {
            String payload = "{\"playerId\": \"p1\", \"npcId\": \"n1\", \"xpEarned\": 100, \"name\": \"Goblin\"}";
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.NPC_KILL, payload, 0L, null);

            Optional<KillNpcEvent> kill = event.getNpcKillPayload();

            assertTrue(kill.isPresent());
            assertEquals("Goblin", kill.get().getName());
            assertEquals(100, kill.get().getXpEarned());
        }

        @Test
        @DisplayName("Should return empty for wrong event type")
        void shouldReturnEmptyForWrongType() {
            String payload = "{\"message\": \"Hello!\"}";
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.DRAW_MAP, payload, 0L, null);

            // Ask for gossip but event is DRAW_MAP
            Optional<Gossip> gossip = event.getGossipPayload();

            assertTrue(gossip.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for null payload")
        void shouldReturnEmptyForNullPayload() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.GOSSIP, null, 0L, null);

            Optional<Gossip> gossip = event.getGossipPayload();

            assertTrue(gossip.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for invalid payload JSON")
        void shouldReturnEmptyForInvalidPayload() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid", SkalholtEventType.GOSSIP, "not json", 0L, null);

            Optional<Gossip> gossip = event.getGossipPayload();

            assertTrue(gossip.isEmpty());
        }
    }

    @Nested
    @DisplayName("Audience")
    class Audience {

        @Test
        @DisplayName("isPlayerOnly should detect PLAYER_ONLY audience")
        void isPlayerOnlyShouldDetect() {
            SkalholtSseEvent event = new SkalholtSseEvent();
            event.setAudience("PLAYER_ONLY");

            assertTrue(event.isPlayerOnly());
        }

        @Test
        @DisplayName("isPlayerOnly should return false for EVERYONE")
        void isPlayerOnlyShouldReturnFalseForEveryone() {
            SkalholtSseEvent event = new SkalholtSseEvent();
            event.setAudience("EVERYONE");

            assertFalse(event.isPlayerOnly());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("Events with same UUID should be equal")
        void eventsWithSameUuidShouldBeEqual() {
            SkalholtSseEvent event1 = new SkalholtSseEvent("uuid-123", SkalholtEventType.GOSSIP, null, 0L, null);
            SkalholtSseEvent event2 = new SkalholtSseEvent("uuid-123", SkalholtEventType.PLAYERDATA, "payload", 999L, "player");

            assertEquals(event1, event2);
            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        @DisplayName("Events with different UUIDs should not be equal")
        void eventsWithDifferentUuidsShouldNotBeEqual() {
            SkalholtSseEvent event1 = new SkalholtSseEvent("uuid-123", SkalholtEventType.GOSSIP, null, 0L, null);
            SkalholtSseEvent event2 = new SkalholtSseEvent("uuid-456", SkalholtEventType.GOSSIP, null, 0L, null);

            assertNotEquals(event1, event2);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Should include key fields")
        void shouldIncludeKeyFields() {
            SkalholtSseEvent event = new SkalholtSseEvent("uuid-test", SkalholtEventType.GOSSIP, "payload", 12345L, "player-1");
            String str = event.toString();

            assertTrue(str.contains("uuid-test"));
            assertTrue(str.contains("GOSSIP"));
            assertTrue(str.contains("player-1"));
        }
    }

    @Nested
    @DisplayName("SkalholtEventType")
    class EventTypeTests {

        @Test
        @DisplayName("valueOf should parse valid type")
        void valueOfShouldParseValidType() {
            assertEquals(SkalholtEventType.GOSSIP, SkalholtEventType.valueOf("GOSSIP"));
            assertEquals(SkalholtEventType.PLAYERDATA, SkalholtEventType.valueOf("PLAYERDATA"));
        }

        @Test
        @DisplayName("valueOf should throw for invalid type")
        void valueOfShouldThrowForInvalid() {
            assertThrows(IllegalArgumentException.class, () -> SkalholtEventType.valueOf("INVALID_TYPE"));
        }

        @Test
        @DisplayName("all expected event types should exist")
        void allExpectedTypesExist() {
            assertNotNull(SkalholtEventType.GOSSIP);
            assertNotNull(SkalholtEventType.PLAYERDATA);
            assertNotNull(SkalholtEventType.PLAYER_UPDATE_HEALTH);
            assertNotNull(SkalholtEventType.NPC_DAMAGE);
            assertNotNull(SkalholtEventType.NPC_KILL);
            assertNotNull(SkalholtEventType.DRAW_MAP);
            assertNotNull(SkalholtEventType.USERS);
        }
    }
}
