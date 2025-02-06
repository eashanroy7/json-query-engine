package com.bigdata.json.query.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlanService {

    private final RedisTemplate<String, String> redisTemplate;

    public PlanService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String dataKey(String objectId) {
        return "plan:data:" + objectId;
    }

    private String etagKey(String objectId) {
        return "plan:etag:" + objectId;
    }

    /**
     * Saves the JSON string and associated ETag to Redis.
     */
    public void savePlan(String objectId, String json, String etag) {
        // We could store them in a Redis hash, but for simplicity store them in separate keys
        redisTemplate.opsForValue().set(dataKey(objectId), json);
        redisTemplate.opsForValue().set(etagKey(objectId), etag);
    }

    /**
     * Retrieves the JSON string by objectId, or null if not found.
     */
    public String getPlan(String objectId) {
        return redisTemplate.opsForValue().get(dataKey(objectId));
    }

    /**
     * Retrieves the ETag for the stored JSON, or null if not found.
     */
    public String getEtag(String objectId) {
        return redisTemplate.opsForValue().get(etagKey(objectId));
    }

    /**
     * Deletes the JSON and its ETag from Redis.
     */
    public void deletePlan(String objectId) {
        redisTemplate.delete(dataKey(objectId));
        redisTemplate.delete(etagKey(objectId));
    }
}
