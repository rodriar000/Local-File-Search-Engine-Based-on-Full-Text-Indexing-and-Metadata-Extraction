package com.allende.filesearch.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.allende.filesearch.model.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Disabled("Cannot mock ElasticsearchClient on Java 25 due to final class restrictions")
@ExtendWith(MockitoExtension.class)
class IndexManagerTest {

    @Mock
    private ElasticsearchClient client;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private IndexManager indexManager;
    private Config config;

    @BeforeEach
    void setUp() {
        config = new Config();
        config.getElasticsearch().setRefreshInterval("1s");

        when(client.indices()).thenReturn(indicesClient);

        indexManager = new IndexManager(client, config);
    }

    @Test
    void createIndex_ShouldCreate_WhenIndexDoesNotExist() throws IOException {
        // Arrange
        String indexName = "new-index";
        when(indicesClient.exists(any(ExistsRequest.class)))
                .thenReturn(new BooleanResponse(false));

        when(indicesClient.create(any(CreateIndexRequest.class)))
                .thenReturn(new CreateIndexResponse.Builder()
                        .index(indexName)
                        .shardsAcknowledged(true)
                        .acknowledged(true)
                        .build());

        // Act
        indexManager.createIndex(indexName);

        // Assert
        verify(indicesClient).create(any(CreateIndexRequest.class));
    }

    @Test
    void createIndex_ShouldSkip_WhenIndexExists() throws IOException {
        // Arrange
        String indexName = "existing-index";
        when(indicesClient.exists(any(ExistsRequest.class)))
                .thenReturn(new BooleanResponse(true));

        // Act
        indexManager.createIndex(indexName);

        // Assert
        verify(indicesClient, never()).create(any(CreateIndexRequest.class));
    }
}
