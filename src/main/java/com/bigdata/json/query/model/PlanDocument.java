package com.bigdata.json.query.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Routing;
import org.springframework.data.elasticsearch.core.join.JoinField;
import org.springframework.data.elasticsearch.annotations.JoinTypeRelations;
import org.springframework.data.elasticsearch.annotations.JoinTypeRelation;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "plans")
@Routing("objectId")   // children will use the parent’s id as routing key
public class PlanDocument {

    @Id
    private String objectId;

    /**
     * parent‑child join field.
     *   parent  = "plan"
     *   child   = "linkedPlanService"
     */
    @JoinTypeRelations(
            relations = @JoinTypeRelation(
                    parent = "plan",
                    children = "linkedPlanService")
    )
    private JoinField<String> relation;

    /** Entire JSON blob for flexible queries */
    private Map<String, Object> payload;
}
