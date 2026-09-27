package me.lewisblackburn.metabase.security;

import java.util.Optional;

public interface UserCredentialsRepository {
    Optional<UserPrincipal> findByUsername(String username);
}
