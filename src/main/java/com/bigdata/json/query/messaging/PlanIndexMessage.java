package com.bigdata.json.query.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlanIndexMessage {
    private String objectId;
    private String json;          // full plan json
    private Operation op;         // CREATE, PATCH, DELETE

    public enum Operation { CREATE, PATCH, DELETE }
}

