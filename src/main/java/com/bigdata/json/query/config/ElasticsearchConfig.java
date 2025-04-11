package com.bigdata.json.query.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories
public class ElasticsearchConfig {

    /**
     * Low‑level REST client
     */
    @Bean
    public RestClient restClient(@Value("${spring.elasticsearch.uris}") String uri) {
        return RestClient.builder(HttpHost.create(uri)).build();
    }

    /**
     * Transport layer that adapts the low‑level client to the Java API client.
     */
    @Bean
    public ElasticsearchTransport transport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    /**
     * Java API client to be injected wherever we need to call ES directly.
     * Spring‑Data repositories also auto‑detect this bean.
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
