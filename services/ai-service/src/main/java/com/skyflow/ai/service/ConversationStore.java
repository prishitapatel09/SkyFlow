package com.skyflow.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflow.ai.config.ClaudeProperties;
import com.skyflow.ai.dto.ChatTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversation history in Redis, keyed per conversation and scoped to the owning user.
 *
 * <p>Redis rather than a database on purpose: chat history is short-lived, read on every turn, and
 * worth nothing an hour later - and a TTL is a simpler retention policy than a cleanup job.
 */
@Component
public class ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationStore.class);
    private static final String KEY_PREFIX = "ai:conversation:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ClaudeProperties properties;

    public ConversationStore(StringRedisTemplate redis, ObjectMapper objectMapper,
                             ClaudeProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** The user id is part of the key, so one traveller cannot resume another's conversation. */
    private static String key(String userId, String conversationId) {
        return KEY_PREFIX + userId + ":" + conversationId;
    }

    public List<ChatTurn> load(String userId, String conversationId) {
        String stored = redis.opsForValue().get(key(userId, conversationId));
        if (stored == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stored, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, ChatTurn.class));
        } catch (JsonProcessingException ex) {
            log.warn("Discarding unreadable history for conversation {}", conversationId);
            return List.of();
        }
    }

    public void append(String userId, String conversationId, ChatTurn... turns) {
        List<ChatTurn> history = new ArrayList<>(load(userId, conversationId));
        history.addAll(List.of(turns));

        // Keep the tail: the newest turns are the ones that carry the thread of the conversation.
        int limit = Math.max(2, properties.getHistoryTurns());
        if (history.size() > limit) {
            history = new ArrayList<>(history.subList(history.size() - limit, history.size()));
        }

        try {
            redis.opsForValue().set(key(userId, conversationId),
                    objectMapper.writeValueAsString(history), properties.getConversationTtl());
        } catch (JsonProcessingException ex) {
            log.error("Could not persist conversation {}", conversationId, ex);
        }
    }

    public void clear(String userId, String conversationId) {
        redis.delete(key(userId, conversationId));
    }
}
