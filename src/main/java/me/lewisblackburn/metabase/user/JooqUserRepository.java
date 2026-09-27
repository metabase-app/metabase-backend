package me.lewisblackburn.metabase.user;

import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;

import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.user.model.User;

@Repository
@RequiredArgsConstructor
public class JooqUserRepository implements UserRepository {
    private final DSLContext dsl;

    @Override
    public List<User> findAll() {
        return dsl
                .select(USERS.ID, USERS.USERNAME, USERS.EMAIL, USERS.CREATED_AT, USERS.UPDATED_AT,
                        USERS.LAST_SEEN_AT, USERS.DELETED_AT)
                .from(USERS).orderBy(USERS.USERNAME)
                .fetch(record -> User.builder()
                        .id(record.get(USERS.ID))
                        .username(record.get(USERS.USERNAME))
                        .email(record.get(USERS.EMAIL))
                        .roles(List.of())
                        .following(List.of())
                        .followers(List.of())
                        .createdAt(record.get(USERS.CREATED_AT))
                        .updatedAt(record.get(USERS.UPDATED_AT))
                        .lastSeenAt(record.get(USERS.LAST_SEEN_AT))
                        .deletedAt(record.get(USERS.DELETED_AT))
                        .build());
    }

    @Override
    public User find(Long id) {
        throw new UnsupportedOperationException("Unimplemented method 'find'");
    }

}
