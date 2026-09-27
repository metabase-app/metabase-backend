package me.lewisblackburn.metabase.movie;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.security.GraphQlRateLimiter;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.web.context.request.ServletRequestAttributes;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.Movie;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MovieController {

    private final GraphQlRateLimiter rateLimiter;
    private final MovieRepository movieRepository;

    @QueryMapping
    public List<Movie> movies(@ContextValue("http") ServletRequestAttributes http) {
        rateLimiter.check("movies", http.getRequest().getRemoteAddr());
        return movieRepository.findAll();
    }

    @QueryMapping
    public Movie movie(@Argument Long id, @ContextValue("http") ServletRequestAttributes http) {
        rateLimiter.check("movie", http.getRequest().getRemoteAddr());
        return movieRepository.find(id);
    }

    @BatchMapping(typeName = "Movie", field = "cast")
    public Map<Movie, List<CastMember>> cast(List<Movie> movies) {
        Map<Long, List<CastMember>> castByMovie =
                movieRepository.findCastByMovieIds(movies.stream().map(Movie::id).toList());

        return movies.stream().collect(Collectors.toMap(movie -> movie,
                movie -> castByMovie.getOrDefault(movie.id(), List.of())));
    }
}
