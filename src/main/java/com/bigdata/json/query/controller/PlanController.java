package com.bigdata.json.query.controller;

import com.bigdata.json.query.service.PlanService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;
    private final JsonSchema schema;
    private final ObjectMapper objectMapper;

    public PlanController(PlanService planService) throws Exception {
        this.planService = planService;
        this.objectMapper = new ObjectMapper();

        // Load the JSON schema from the classpath resource: schemas/plan-schema.json
        try (InputStream schemaStream =
                     getClass().getResourceAsStream("/schemas/plan-schema.json")) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
            this.schema = factory.getSchema(schemaStream);
        }
    }

    /**
     * CREATE a Plan (POST /api/plans)
     * If the same objectId + same JSON already exists, return 304 Not Modified.
     * If the same objectId but different JSON, return  201 Created.
     * Otherwise, create new plan with 201 Created.
     */
    @PostMapping
    public ResponseEntity<?> createPlan(@RequestBody String jsonPayload) {
        try {
            // Parse input into a Jackson JsonNode
            JsonNode jsonNode = objectMapper.readTree(jsonPayload);

            // Validate against JSON schema
            Set<ValidationMessage> validationErrors = schema.validate(jsonNode);
            if (!validationErrors.isEmpty()) {
                // Return 400 with details
                return ResponseEntity.badRequest()
                        .body("JSON Schema validation failed: " + validationErrors);
            }

            // Assuming top-level "objectId" is mandatory.
            // We have to ensure it is in the JSON per schema
            String objectId = jsonNode.get("objectId").asText();
            if (objectId == null || objectId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("objectId is missing or empty in JSON");
            }

            // Compute ETag for the incoming JSON payload
            String incomingEtag = generateEtag(jsonPayload);

            // Check if a plan with the given objectId already exists
            String existingJson = planService.getPlan(objectId);
            if (existingJson == null) {
                // No existing resource: create new and return 201 Created
                planService.savePlan(objectId, jsonPayload);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .header("Location", "/api/plans/" + objectId)
                        .eTag(incomingEtag)
                        .build();
            } else {
                // Resource exists: compute its ETag on the fly
                String storedEtag = generateEtag(existingJson);
                if (incomingEtag.equals(storedEtag)) {
                    // Same content: return 304 Not Modified
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                            .body("Plan already exists with the same content.");
                } else {
                    // Different content: overwrite the existing resource
                    planService.savePlan(objectId, jsonPayload);
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .header("Location", "/api/plans/" + objectId)
                            .eTag(incomingEtag)
                            .body("Plan updated with new content.");
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * READ a Plan (GET /api/plans/{objectId})
     * Supports conditional read with ETag/If-None-Match
     */
    @GetMapping("/{objectId}")
    public ResponseEntity<?> getPlan(
            @PathVariable String objectId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        // Fetch from Redis
        String planJson = planService.getPlan(objectId);
        if (planJson == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No plan found with objectId = " + objectId);
        }

        // Compute the ETag from the stored JSON
        String currentEtag = generateEtag(planJson);

        // If the client sends If-None-Match = currentEtag, respond 304
        if (ifNoneMatch != null && ifNoneMatch.equals(currentEtag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        // Otherwise respond with 200 and the content
        return ResponseEntity.ok()
                .eTag(currentEtag)
                .body(planJson);
    }

    /**
     * GET all Plans (GET /api/plans)
     */
    @GetMapping
    public ResponseEntity<?> getAllPlans() {
        List<String> plans = planService.getAllPlans();
        return ResponseEntity.ok(plans);
    }

    /**
     * PATCH a Plan (PATCH /api/plans/{objectId})
     * Allows partial updates via JSON merge patch.
     * Requires If-Match header for conditional update.
     */
    @PatchMapping("/{objectId}")
    public ResponseEntity<?> patchPlan(
            @PathVariable String objectId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody String patchPayload) {

        try {
            String existingJson = planService.getPlan(objectId);
            if (existingJson == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No plan found with objectId = " + objectId);
            }
            // ETag check
            String currentEtag = generateEtag(existingJson);
            if (ifMatch == null || !ifMatch.equals(currentEtag)) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                        .body("ETag mismatch: resource has been modified");
            }
            // Apply Patch
            String updatedJson = planService.patchPlan(objectId, patchPayload);
            JsonNode mergedNode = objectMapper.readTree(updatedJson);
            Set<ValidationMessage> validationErrors = schema.validate(mergedNode);
            if (!validationErrors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("JSON Schema validation failed after patch: " + validationErrors);
            }
            // Check if content is actually changed after patch
            String newEtag = generateEtag(updatedJson);
            if(newEtag.equals(currentEtag)){
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
            }
            return ResponseEntity.ok().eTag(newEtag).body(updatedJson);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Failed to apply patch: " + ex.getMessage());
        }
    }

    /**
     * DELETE a Plan (DELETE /api/plans/{objectId})
     */
    @DeleteMapping("/{objectId}")
    public ResponseEntity<?> deletePlan(@PathVariable String objectId) {
        String existing = planService.getPlan(objectId);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No plan found with objectId = " + objectId);
        }

        // Delete from Redis
        planService.deletePlan(objectId);

        // Return 204
        return ResponseEntity.noContent().build();
    }

    /**
     * Utility: compute an MD5 ETag from the raw JSON string.
     */
    private String generateEtag(String jsonPayload) {
        return DigestUtils.md5DigestAsHex(jsonPayload.getBytes());
    }
}
