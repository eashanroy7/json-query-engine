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

    private void index(PlanIndexMessage msg) throws IOException {
        JsonNode root = mapper.readTree(msg.getJson());

        PlanDocument parent = new PlanDocument(
                msg.getObjectId(),
                new JoinField<>("plan"),
                mapper.convertValue(root, new TypeReference<Map<String, Object>>() {})
        );
        repo.save(parent);

        for (JsonNode childNode : root.withArray("linkedPlanServices")) {
            String childId = childNode.get("objectId").asText();
            PlanDocument child = new PlanDocument(
                    childId,
                    new JoinField<>("linkedPlanService", msg.getObjectId()),
                    mapper.convertValue(childNode, new TypeReference<Map<String, Object>>() {})
            );
            repo.save(child);
        }
    }
}
