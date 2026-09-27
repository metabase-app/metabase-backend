package me.lewisblackburn.metabase.security;

import static me.lewisblackburn.metabase.jooq.tables.UserRoles.USER_ROLES;
import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.lower;

import java.util.Locale;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JooqUserCredentialsRepository implements UserCredentialsRepository {
    private final DSLContext dsl;

    @Override
    public Optional<UserPrincipal> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        return dsl
                .select(USERS.ID, USERS.USERNAME, USERS.PASSWORD_HASH)
                .from(USERS)
                .where(lower(USERS.USERNAME).eq(username.toLowerCase(Locale.ROOT)))
                .and(USERS.DELETED_AT.isNull())
                .and(USERS.PASSWORD_HASH.isNotNull())
                .fetchOptional(record -> UserPrincipal.principalBuilder()
                        .id(record.get(USERS.ID))
                        .username(record.get(USERS.USERNAME))
                        .passwordHash(record.get(USERS.PASSWORD_HASH))
                        .authorities(findAuthorities(record.get(USERS.ID)))
                        .build());
    }

    private List<SimpleGrantedAuthority> findAuthorities(Long userId) {
        return dsl
                .select(USER_ROLES.ROLE)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .orderBy(USER_ROLES.ROLE)
                .fetch(role -> new SimpleGrantedAuthority("ROLE_" + role.get(USER_ROLES.ROLE)));
    }
}
