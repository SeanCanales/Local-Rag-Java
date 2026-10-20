package com.reader.demo;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

	@Bean
    EmbeddingModel embeddingModel(@Value("${ollama.base-url}") String baseUrl,
                                  @Value("${ollama.embedding-model}") String modelName) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(2))
                .build();
    }
	
	@Bean
    ChatModel chatModel(@Value("${ollama.base-url}") String baseUrl,
                        @Value("${ollama.chat-model}") String modelName) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.2)
                .timeout(Duration.ofMinutes(5))
                .build();
    }
	
	@Bean
    EmbeddingStore<TextSegment> embeddingStore(
            @Value("${pgvector.host}") String host,
            @Value("${pgvector.port}") int port,
            @Value("${pgvector.database}") String database,
            @Value("${pgvector.user}") String user,
            @Value("${pgvector.password}") String password,
            @Value("${pgvector.table}") String table,
            @Value("${rag.embedding-dimension}") int dimension) {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(table)
                .dimension(dimension)
                .createTable(true)
                .build();
    }
	
	@Bean
    ContentRetriever contentRetriever(EmbeddingStore<TextSegment> store,
                                      EmbeddingModel embeddingModel,
                                      @Value("${rag.max-results}") int maxResults,
                                      @Value("${rag.min-score}") double minScore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }
	
	@Bean
    Assistant assistant(ChatModel chatModel, ContentRetriever retriever) {
        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(retriever)
                .build();
    }
}
