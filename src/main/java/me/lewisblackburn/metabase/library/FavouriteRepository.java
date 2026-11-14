package me.lewisblackburn.metabase.library;

import java.util.List;
import me.lewisblackburn.metabase.entity.Entity;
import me.lewisblackburn.metabase.pagination.OrderBy;

public interface FavouriteRepository {
    List<Entity> findAll(Long userId, int offset, int limit, OrderBy orderBy);

    boolean favourite(Long userId, Long entityId);

    boolean unfavourite(Long userId, Long entityId);
}
