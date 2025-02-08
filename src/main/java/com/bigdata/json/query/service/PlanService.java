package com.bigdata.json.query.service;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PlanService {

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> hashOperations;

    public PlanService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    private String dataKey(String objectId) {
        return "plan:data:" + objectId;
    }

    /**
     * Saves the JSON string and associated ETag to Redis in a single hash
     */
    public void savePlan(String objectId, String json, String etag) {
        hashOperations.putAll(dataKey(objectId), Map.of(
                "json", json,
                "etag", etag
        ));
    }

    /**
     * Retrieves the JSON string by objectId, or null if not found.
     */
    public String getPlan(String objectId) {
        return hashOperations.get(dataKey(objectId), "json");
    }

    /**
     * Retrieves the ETag for the stored JSON, or null if not found.
     */
    public String getEtag(String objectId) {
        return hashOperations.get(dataKey(objectId), "etag");
    }

    /**
     * Deletes the entire hash for the given objectId from Redis.
     */
    public void deletePlan(String objectId) {
        redisTemplate.delete(dataKey(objectId));
    }
}
