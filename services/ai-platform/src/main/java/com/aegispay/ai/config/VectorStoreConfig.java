package com.aegispay.ai.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * Provides a no-op {@link VectorStore} bean for local development when
 * the pgvector auto-configuration is excluded (no OpenAI/Anthropic embedding key).
 *
 * <p>In production the real pgvector store is wired via
 * {@code spring.ai.vectorstore.pgvector.*} properties and the
 * {@code PgVectorStoreAutoConfiguration} takes precedence.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore noOpVectorStore() {
        return new VectorStore() {
            @Override
            public void add(List<Document> documents) {
                // no-op: no embedding API configured locally
            }

            @Override
            public void delete(List<String> idList) {
                // no-op
            }

            @Override
            public void delete(Filter.Expression filterExpression) {
                // no-op
            }

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                return Collections.emptyList();
            }
        };
    }
}
