package me.lewisblackburn.metabase.movie;

import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.Movie;

public interface MovieRepository {
    List<Movie> findAll();

    Movie find(Long id);

    Map<Long, List<CastMember>> findCastByMovieIds(List<Long> movieIds);
}
