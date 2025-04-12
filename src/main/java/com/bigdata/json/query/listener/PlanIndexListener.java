package com.bigdata.json.query.listener;

import com.bigdata.json.query.messaging.PlanIndexMessage;
import com.bigdata.json.query.model.PlanDocument;
import com.bigdata.json.query.repository.PlanEsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.elasticsearch.core.join.JoinField;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PlanIndexListener {

    private final PlanEsRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    @RabbitListener(queues = "plan-queue")
    public void onMessage(PlanIndexMessage msg) throws IOException {
        switch (msg.getOp()) {
            case DELETE -> repo.deleteById(msg.getObjectId());
            default      -> index(msg);
        }
    }

    /* ───────── index / re‑index ───────── */

    private void index(PlanIndexMessage msg) throws IOException {
        JsonNode root = mapper.readTree(msg.getJson());
        String planId = msg.getObjectId();

        /* ---------- parent ---------- */
        PlanDocument parent = new PlanDocument();
        parent.setObjectId(planId);
        parent.setRelation(new JoinField<>("plan"));
        parent.setRouting(planId);
        parent.setPayload(asMap(root));
        repo.save(parent);

        /* ---------- linkedPlanServices children ---------- */
        for (JsonNode lpsNode : root.withArray("linkedPlanServices")) {

            String lpsId = lpsNode.get("objectId").asText();

            /* 2‑A  save the linkedPlanService child (parent = plan) */
            PlanDocument lpsChild = new PlanDocument();
            lpsChild.setObjectId(lpsId);
            lpsChild.setRelation(new JoinField<>("linkedPlanService", planId));
            lpsChild.setRouting(planId);                 // always route to the plan
            lpsChild.setPayload(asMap(lpsNode));
            repo.save(lpsChild);

            /* 2‑B  grand‑child : planserviceCostShare */
            JsonNode pscsNode = lpsNode.path("planserviceCostShares");
            if (pscsNode.isObject()) {
                String pscsId = pscsNode.get("objectId").asText();
                PlanDocument pscsDoc = new PlanDocument();
                pscsDoc.setObjectId(pscsId);
                pscsDoc.setRelation(new JoinField<>("planserviceCostShare", lpsId));
                pscsDoc.setRouting(planId);              // still route by top plan
                pscsDoc.setPayload(asMap(pscsNode));
                repo.save(pscsDoc);
            }

            /* 2‑C  grand‑child : linkedService */
            JsonNode lsNode = lpsNode.path("linkedService");
            if (lsNode.isObject()) {
                String lsId = lsNode.get("objectId").asText();
                PlanDocument lsDoc = new PlanDocument();
                lsDoc.setObjectId(lsId);
                lsDoc.setRelation(new JoinField<>("linkedService", lpsId));
                lsDoc.setRouting(planId);
                lsDoc.setPayload(asMap(lsNode));
                repo.save(lsDoc);
            }
        }

        /* ---------- planCostShare child ---------- */
        JsonNode pcsNode = root.path("planCostShares");
        if (pcsNode.isObject()) {
            String pcsId = pcsNode.get("objectId").asText();   // or planId + "-pcs"
            PlanDocument pcsChild = new PlanDocument();
            pcsChild.setObjectId(pcsId);
            pcsChild.setRelation(new JoinField<>("planCostShare", planId));
            pcsChild.setRouting(planId);
            pcsChild.setPayload(asMap(pcsNode));
            repo.save(pcsChild);
        }
    }

    /* helper: JsonNode -> Map<String,Object> */
    private Map<String, Object> asMap(JsonNode node) {
        return mapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }
}
