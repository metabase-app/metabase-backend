package me.lewisblackburn.metabase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.method.annotation.support.SubrangeMethodArgumentResolver;
import org.springframework.graphql.data.pagination.CursorStrategy;

@Configuration(proxyBeanMethods = false)
public class GraphQlPaginationConfiguration {

    // Preserve negative counts so the shared pagination helper can reject them.
    @Bean
    SubrangeMethodArgumentResolver<ScrollPosition> paginationArguments(
            CursorStrategy<ScrollPosition> cursorStrategy) {
        return new SubrangeMethodArgumentResolver<>(cursorStrategy);
    }
}
