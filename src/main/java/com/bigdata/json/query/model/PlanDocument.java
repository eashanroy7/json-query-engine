package com.bigdata.json.query.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.data.elasticsearch.core.join.JoinField;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "plans")
@Routing("routing")
public class PlanDocument {

    @Id
    private String objectId;

    /**
     * parent‑child join field.
     *   parent  = "plan"
     *   child   = "linkedPlanService"
     */
    @JoinTypeRelations(
            relations = {
                    /* level‑1  : plan → linkedPlanService + planCostShare */
                    @JoinTypeRelation(parent = "plan",
                            children = { "linkedPlanService", "planCostShare" }),

                    /* level‑2  : linkedPlanService → planserviceCostShare + linkedService */
                    @JoinTypeRelation(parent = "linkedPlanService",
                            children = { "planserviceCostShare", "linkedService" })
            }
    )
    private JoinField<String> relation;

    /** holds the routing key */
    @Field(type = FieldType.Keyword)
    private String routing;

    /** Entire JSON blob for flexible queries */
    private Map<String, Object> payload;
}
