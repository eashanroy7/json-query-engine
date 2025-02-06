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

            // Convert the entire JSON into a hashed ETag (e.g., MD5)
            String etag = generateEtag(jsonPayload);

            // Save to Redis
            planService.savePlan(objectId, jsonPayload, etag);

            // Return 201 Created with Location and ETag
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("Location", "/api/plans/" + objectId)
                    .eTag(etag)
                    .build();

        } catch (Exception e) {
            // Could be parsing or other errors
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

        // Retrieve the stored ETag
        String currentEtag = planService.getEtag(objectId);

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
