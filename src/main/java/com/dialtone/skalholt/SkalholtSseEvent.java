/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.skalholt;

import com.skalholt.chat.Gossip;
import com.skalholt.chat.Users;
import com.skalholt.events.SkalholtEventType;
import com.skalholt.events.DrawMapEvent;
import com.skalholt.events.KillNpcEvent;
import com.skalholt.events.NpcDamageTakenEvent;
import com.skalholt.events.PlayerUpdateHealthEvent;
import com.dialtone.utils.JacksonConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an event received from the Skalholt MUD EventStream (SSE).
 * This is a JSON-deserializable wrapper for events from the /api/events endpoint.
 *
 * <p>Event types include:
 * <ul>
 *   <li>GOSSIP - Chat/gossip messages</li>
 *   <li>PLAYERDATA - Full player data snapshot</li>
 *   <li>PLAYER_UPDATE_HEALTH - Health/mana/stamina updates</li>
 *   <li>DRAW_MAP - Map rendering data</li>
 *   <li>NPC_DAMAGE - Damage dealt to NPCs</li>
 *   <li>NPC_KILL - NPC defeat events</li>
 *   <li>USERS - User list updates</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkalholtSseEvent {

    private static final ObjectMapper MAPPER = JacksonConfig.mapper();

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("skalholtEventType")
    private SkalholtEventType skalholtEventType;

    @JsonProperty("payload")
    private String payload;

    @JsonProperty("epochTimestamp")
    private Long epochTimestamp;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("audience")
    private String audience;

    /**
     * Default constructor for Jackson deserialization.
     */
    public SkalholtSseEvent() {
    }

    /**
     * Creates a new SkalholtSseEvent.
     *
     * @param uuid event unique identifier
     * @param skalholtEventType event type enum
     * @param payload JSON payload string
     * @param epochTimestamp event timestamp
     * @param playerId associated player ID
     */
    public SkalholtSseEvent(String uuid, SkalholtEventType skalholtEventType, String payload,
                        Long epochTimestamp, String playerId) {
        this.uuid = uuid;
        this.skalholtEventType = skalholtEventType;
        this.payload = payload;
        this.epochTimestamp = epochTimestamp;
        this.playerId = playerId;
    }

    /**
     * Parses a SkalholtSseEvent from JSON.
     *
     * @param json the JSON string to parse
     * @return the parsed SkalholtSseEvent
     * @throws JsonProcessingException if parsing fails
     */
    public static SkalholtSseEvent fromJson(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, SkalholtSseEvent.class);
    }

    /**
     * Gets the shared ObjectMapper instance for parsing payloads.
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public SkalholtEventType getSkalholtEventType() {
        return skalholtEventType;
    }

    public void setSkalholtEventType(SkalholtEventType skalholtEventType) {
        this.skalholtEventType = skalholtEventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Long getEpochTimestamp() {
        return epochTimestamp;
    }

    public void setEpochTimestamp(Long epochTimestamp) {
        this.epochTimestamp = epochTimestamp;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    /**
     * Checks if this event is for a specific player only.
     */
    public boolean isPlayerOnly() {
        return "PLAYER_ONLY".equals(audience);
    }

    /**
     * Checks if this is a gossip/chat event.
     */
    public boolean isGossip() {
        return skalholtEventType == SkalholtEventType.GOSSIP;
    }

    /**
     * Checks if this is a player data event.
     */
    public boolean isPlayerData() {
        return skalholtEventType == SkalholtEventType.PLAYERDATA;
    }

    /**
     * Checks if this is a health update event.
     */
    public boolean isHealthUpdate() {
        return skalholtEventType == SkalholtEventType.PLAYER_UPDATE_HEALTH;
    }

    /**
     * Checks if this is a map draw event.
     */
    public boolean isMapDraw() {
        return skalholtEventType == SkalholtEventType.DRAW_MAP;
    }

    /**
     * Checks if this is an NPC kill event.
     */
    public boolean isNpcKill() {
        return skalholtEventType == SkalholtEventType.NPC_KILL;
    }

    /**
     * Checks if this is an NPC damage event.
     */
    public boolean isNpcDamage() {
        return skalholtEventType == SkalholtEventType.NPC_DAMAGE;
    }

    /**
     * Checks if this is a users list event.
     */
    public boolean isUsers() {
        return skalholtEventType == SkalholtEventType.USERS;
    }

    /**
     * Parses the payload as a Gossip event.
     *
     * @return Optional containing the parsed Gossip, empty if parsing fails or wrong type
     */
    public Optional<Gossip> getGossipPayload() {
        if (!isGossip() || payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(payload, Gossip.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses the payload as a PlayerUpdateHealthEvent.
     *
     * @return Optional containing the parsed event, empty if parsing fails or wrong type
     */
    public Optional<PlayerUpdateHealthEvent> getHealthUpdatePayload() {
        if (!isHealthUpdate() || payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(payload, PlayerUpdateHealthEvent.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses the payload as a DrawMapEvent.
     *
     * @return Optional containing the parsed event, empty if parsing fails or wrong type
     */
    public Optional<DrawMapEvent> getDrawMapPayload() {
        if (!isMapDraw() || payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(payload, DrawMapEvent.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses the payload as a KillNpcEvent.
     *
     * @return Optional containing the parsed event, empty if parsing fails or wrong type
     */
    public Optional<KillNpcEvent> getNpcKillPayload() {
        if (!isNpcKill() || payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(payload, KillNpcEvent.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses the payload as a NpcDamageTakenEvent.
     *
     * @return Optional containing the parsed event, empty if parsing fails or wrong type
     */
    public Optional<NpcDamageTakenEvent> getNpcDamagePayload() {
        if (!isNpcDamage() || payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(payload, NpcDamageTakenEvent.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses the payload as a Users event.
     *
     * @return Optional containing the parsed event, empty if parsing fails or wrong type
     */
    public Optional<Users> getUsersPayload() {
        if (!isUsers() || payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(payload, Users.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkalholtSseEvent that = (SkalholtSseEvent) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "SkalholtSseEvent{" +
                "uuid='" + uuid + '\'' +
                ", type=" + skalholtEventType +
                ", playerId='" + playerId + '\'' +
                ", epochTimestamp=" + epochTimestamp +
                ", audience='" + audience + '\'' +
                '}';
    }
}
