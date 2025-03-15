package com.bigdata.json.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import java.util.Iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlanService {

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> hashOperations;
    private final ObjectMapper objectMapper;


    public PlanService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
        this.objectMapper = new ObjectMapper();
    }

    private String dataKey(String objectId) {
        return "plan:data:" + objectId;
    }

    /**
     * Saves the JSON string to Redis in a single hash
     */
    public void savePlan(String objectId, String json) {
        hashOperations.put(dataKey(objectId), "json", json);
    }

    /**
     * Retrieves the JSON string by objectId, or null if not found.
     */
    public String getPlan(String objectId) {
        return hashOperations.get(dataKey(objectId), "json");
    }

    /**
     * Deletes the entire hash for the given objectId from Redis.
     */
    public void deletePlan(String objectId) {
        redisTemplate.delete(dataKey(objectId));
    }

    /**
     * Returns all stored plans as a List of JSON Strings.
     */
    public List<String> getAllPlans() {
        Set<String> keys = redisTemplate.keys("plan:data:*");
        List<String> plans = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                String json = hashOperations.get(key, "json");
                if (json != null) {
                    plans.add(json);
                }
            }
        }
        return plans;
    }

    /**
     * Applies a patch (merge) update to the stored plan. Returns the new ETag.
     * If in the patch, an element in the "linkedPlanServices" array has an objectId
     * that does not match any existing element, that new object is added to the array.
     * Returns the updated JSON.
     */
    public String patchPlan(String objectId, String patchPayload) throws Exception {
        String existingJson = getPlan(objectId);
        if (existingJson == null) {
            throw new Exception("Plan not found");
        }
        JsonNode existingNode = objectMapper.readTree(existingJson);
        JsonNode patchNode = objectMapper.readTree(patchPayload);

        // Ensure both nodes are objects before merging
        if (existingNode instanceof ObjectNode && patchNode instanceof ObjectNode) {
            merge((ObjectNode) existingNode, (ObjectNode) patchNode);
        } else {
            throw new Exception("Invalid JSON structure for merging");
        }

        String updatedJson = objectMapper.writeValueAsString(existingNode);
        // Save the updated JSON
        savePlan(objectId, updatedJson);
        return updatedJson;
    }

    /**
     * Utility merge method for JSON merge patch.
     * For any array field that contains objects with an "objectId":
     * - If a patch element's "objectId" matches an existing element, merge them.
     * - If no matching element is found, append the patch element.
     */
    private void merge(ObjectNode targetNode, ObjectNode patchNode) {
        Iterator<Map.Entry<String, JsonNode>> fields = patchNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode patchValue = entry.getValue();
            if (patchValue.isNull()) {
                targetNode.remove(fieldName);
            } else {
                JsonNode targetValue = targetNode.get(fieldName);
                if (patchValue.isArray()) {
                    // If target has an array for this field, try to merge or append elements
                    if (targetValue != null && targetValue.isArray()) {
                        ArrayNode targetArray = (ArrayNode) targetValue;
                        // Check if patch array elements are objects with "objectId"
                        boolean patchElementsHaveId = true;
                        for (JsonNode patchElem : patchValue) {
                            if (!patchElem.isObject() || !patchElem.has("objectId")) {
                                patchElementsHaveId = false;
                                break;
                            }
                        }
                        if (patchElementsHaveId) {
                            // For each element in the patch array, merge with matching element or append new element
                            for (JsonNode patchElem : patchValue) {
                                String patchElemId = patchElem.get("objectId").asText();
                                boolean found = false;
                                for (JsonNode targetElem : targetArray) {
                                    if (targetElem.isObject() && targetElem.has("objectId")) {
                                        String targetElemId = targetElem.get("objectId").asText();
                                        if (targetElemId.equals(patchElemId)) {
                                            merge((ObjectNode) targetElem, (ObjectNode) patchElem);
                                            found = true;
                                            break;
                                        }
                                    }
                                }
                                if (!found) {
                                    // Append new element if no matching objectId found
                                    targetArray.add(patchElem);
                                }
                            }
                        } else {
                            // If patch elements don't have an "objectId", replace the target array entirely
                            targetNode.set(fieldName, patchValue);
                        }
                    } else {
                        // If target doesn't have an array, simply set the patch value
                        targetNode.set(fieldName, patchValue);
                    }
                } else if (patchValue.isObject() && targetValue != null && targetValue.isObject()) {
                    // For nested objects, merge recursively
                    merge((ObjectNode) targetValue, (ObjectNode) patchValue);
                } else {
                    // For all other cases, replace the target value with patch value
                    targetNode.set(fieldName, patchValue);
                }
            }
        }
    }
    /**
     * Utility: compute an MD5 ETag from the raw JSON string.
     */
    public String generateEtag(String jsonPayload) {
        return DigestUtils.md5DigestAsHex(jsonPayload.getBytes());
    }
}
