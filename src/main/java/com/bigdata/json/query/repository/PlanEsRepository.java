package com.bigdata.json.query.repository;

import com.bigdata.json.query.model.PlanDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PlanEsRepository extends ElasticsearchRepository<PlanDocument,String> {}

