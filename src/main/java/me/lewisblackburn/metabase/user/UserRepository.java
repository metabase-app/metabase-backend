package me.lewisblackburn.metabase.user;

import java.util.List;
import me.lewisblackburn.metabase.user.model.User;

public interface UserRepository {
    List<User> findAll();

    User find(Long id);
}
