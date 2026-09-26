package me.lewisblackburn.metabase.movie;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.Movie;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MovieController {

    private final MovieRepository movieRepository;

    @QueryMapping
    public List<Movie> movies() {
        return movieRepository.findAll();
    }

    @QueryMapping
    public Movie movie(@Argument Long id) {
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
