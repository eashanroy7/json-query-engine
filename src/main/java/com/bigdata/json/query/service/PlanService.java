package com.bigdata.json.query.service;

import com.bigdata.json.query.config.RabbitConfig;
import com.bigdata.json.query.messaging.PlanIndexMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.*;

@Service
public class PlanService {

    private final RedisTemplate<String, String> redisTemplate;
    private final AmqpTemplate amqp;

    private HashOperations<String, String, String> hashOps;
    private final ObjectMapper mapper = new ObjectMapper();

    /** constructor used by Lombok; we init hashOps here */
    public PlanService(RedisTemplate<String, String> redisTemplate,
                       AmqpTemplate amqp) {
        this.redisTemplate = redisTemplate;
        this.amqp          = amqp;
        this.hashOps       = redisTemplate.opsForHash();
    }

    /* ─────────────────── helpers ─────────────────── */

    private static final String DATA_PREFIX = "plan:data:";

    private String dataKey(String id) {
        return DATA_PREFIX + id;
    }

    /**
     * Saves the JSON string to Redis in a single hash
     */
    public void savePlan(String objectId, String json) {
        hashOps.put(dataKey(objectId), "json", json);
        publish(PlanIndexMessage.Operation.CREATE, objectId, json);
    }

    /**
     * Retrieves the JSON string by objectId, or null if not found.
     */
    public String getPlan(String objectId) {
        return hashOps.get(dataKey(objectId), "json");
    }

    /**
     * Deletes the entire hash for the given objectId from Redis.
     */
    public void deletePlan(String objectId) {
        redisTemplate.delete(dataKey(objectId));
        publish(PlanIndexMessage.Operation.DELETE, objectId, null);
    }

    /**
     * Returns all stored plans as a List of JSON Strings.
     */
    public List<String> getAllPlans() {
        Set<String> keys = Optional.ofNullable(redisTemplate.keys(DATA_PREFIX + "*"))
                .orElse(Collections.emptySet());
        List<String> out = new ArrayList<>(keys.size());
        for (String k : keys) {
            String j = hashOps.get(k, "json");
            if (j != null) out.add(j);
        }
        return out;
    }

    /**
     * Applies a patch (merge) update to the stored plan. Returns the new ETag.
     * If in the patch, an element in the "linkedPlanServices" array has an objectId
     * that does not match any existing element, that new object is added to the array.
     * Returns the updated JSON.
     */
    public String patchPlan(String id, String patchPayload) throws Exception {
        String current = getPlan(id);
        if (current == null) throw new Exception("Plan not found");

        JsonNode target = mapper.readTree(current);
        JsonNode patch  = mapper.readTree(patchPayload);

        if (!(target instanceof ObjectNode) || !(patch instanceof ObjectNode))
            throw new Exception("Invalid JSON structure for merging");

        merge((ObjectNode) target, (ObjectNode) patch);

        String merged = mapper.writeValueAsString(target);
        savePlan(id, merged);                              // also publishes CREATE
        publish(PlanIndexMessage.Operation.PATCH, id, merged);
        return merged;
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

    private void publish(PlanIndexMessage.Operation op, String id, String json) {
        amqp.convertAndSend(RabbitConfig.PLAN_EXCHANGE,
                "plan." + op.name().toLowerCase(),
                new PlanIndexMessage(id, json, op));
    }
}
