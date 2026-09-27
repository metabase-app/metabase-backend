package me.lewisblackburn.metabase.movie;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.security.GraphQlRateLimiter;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.extern.slf4j.Slf4j;
import me.lewisblackburn.metabase.integrations.tmdb.TmdbMovieImportService;
import me.lewisblackburn.metabase.movie.model.Movie;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Slf4j
public class MovieImportController {

    private final GraphQlRateLimiter rateLimiter;
    private final TmdbMovieImportService tmdbImportService;

    @MutationMapping
    public Movie importMovie(@Argument MovieProvider provider, @Argument String externalId,
            @ContextValue("http") ServletRequestAttributes http) {
        rateLimiter.check("importMovie", http.getRequest().getRemoteAddr());
        return switch (provider) {
            case TMDB -> tmdbImportService.importMovie(parseTmdbId(externalId));
        };
    }

    private long parseTmdbId(String externalId) {
        try {
            long id = Long.parseLong(externalId);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            log.warn(
                    "Rejected TMDB movie ID: expected an integer within the supported 64-bit range");
        }
        throw new IllegalArgumentException("TMDB movie ID must be a positive integer");
    }
}
