package me.lewisblackburn.metabase.movie;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.CrewMember;
import me.lewisblackburn.metabase.movie.model.Movie;
import me.lewisblackburn.metabase.security.GraphQlRateLimiter;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.ServletRequestAttributes;

@Controller
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MovieController {

    private final GraphQlRateLimiter rateLimiter;
    private final MovieRepository movieRepository;

    @QueryMapping
    public List<Movie> movies(
        @Argument("offset") int offset,
        @Argument("limit") int limit,
        @ContextValue("http") ServletRequestAttributes http
    ) {
        rateLimiter.check("movies", http.getRequest().getRemoteAddr());
        return movieRepository.findAll(offset, limit);
    }

    @QueryMapping
    public Movie movie(@Argument Long id, @ContextValue("http") ServletRequestAttributes http) {
        rateLimiter.check("movie", http.getRequest().getRemoteAddr());
        return movieRepository.find(id);
    }

    @BatchMapping(typeName = "Movie", field = "cast")
    public Map<Movie, List<CastMember>> cast(List<Movie> movies) {
        Map<Long, List<CastMember>> castByMovie = movieRepository.findCastByMovieIds(
            movies.stream().map(Movie::id).toList()
        );

        return movies
            .stream()
            .collect(
                Collectors.toMap(
                    movie -> movie,
                    movie -> castByMovie.getOrDefault(movie.id(), List.of())
                )
            );
    }

    @BatchMapping(typeName = "Movie", field = "crew")
    public Map<Movie, List<CrewMember>> crew(List<Movie> movies) {
        Map<Long, List<CrewMember>> crewByMovie = movieRepository.findCrewByMovieIds(
            movies.stream().map(Movie::id).toList()
        );

        return movies
            .stream()
            .collect(
                Collectors.toMap(
                    movie -> movie,
                    movie -> crewByMovie.getOrDefault(movie.id(), List.of())
                )
            );
    }
}
