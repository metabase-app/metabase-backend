package me.lewisblackburn.metabase.config;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@ControllerAdvice
@Slf4j
public class GraphQlExceptionHandlerAdvice {

    @GraphQlExceptionHandler
    public GraphQLError handleInvalidInput(IllegalArgumentException exception, DataFetchingEnvironment environment) {
        log.warn("Invalid GraphQL input at path {}: {}", environment.getExecutionStepInfo().getPath(), exception.getMessage(), exception);
        return GraphqlErrorBuilder.newError(environment)
                .errorType(ErrorType.BAD_REQUEST)
                .message(exception.getMessage())
                .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleExternalServiceFailure(RestClientException exception, DataFetchingEnvironment environment) {
        String detail = exception instanceof RestClientResponseException response
                ? "HTTP status " + response.getStatusCode().value()
                : exception.getClass().getSimpleName();
        log.error("External service request failed at GraphQL path {} ({})",
                environment.getExecutionStepInfo().getPath(), detail, exception);
        if (exception instanceof RestClientResponseException response
                && response.getStatusCode().value() == 404) {
            return GraphqlErrorBuilder.newError(environment)
                    .errorType(ErrorType.NOT_FOUND)
                    .message("The external service could not find the requested resource")
                    .build();
        }
        return GraphqlErrorBuilder.newError(environment)
                .errorType(ErrorType.INTERNAL_ERROR)
                .message("An external service request failed")
                .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleUnexpectedFailure(Exception exception, DataFetchingEnvironment environment) {
        log.error("Unhandled GraphQL exception at path {}", environment.getExecutionStepInfo().getPath(), exception);
        return GraphqlErrorBuilder.newError(environment)
                .errorType(ErrorType.INTERNAL_ERROR)
                .message("An internal error occurred")
                .build();
    }
}
