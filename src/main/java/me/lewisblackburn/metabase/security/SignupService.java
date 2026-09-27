package me.lewisblackburn.metabase.security;

import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;
import static me.lewisblackburn.metabase.jooq.tables.UserRoles.USER_ROLES;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.user.model.Role;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {
    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(SignupRequest request) {
        // BCrypt limits passwords by UTF-8 bytes, not Java character count.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        String passwordHash = passwordEncoder.encode(request.password());
        String email = request.email().strip().toLowerCase(Locale.ROOT);

        try {
            Long userId = dsl.insertInto(USERS)
                    .set(USERS.USERNAME, request.username())
                    .set(USERS.DISPLAY_NAME, request.username())
                    .set(USERS.EMAIL, email)
                    .set(USERS.PASSWORD_HASH, passwordHash)
                    .returningResult(USERS.ID)
                    .fetchSingle(USERS.ID);

            // Account and default role are created in the same transaction.
            dsl.insertInto(USER_ROLES)
                    .set(USER_ROLES.USER_ID, userId)
                    .set(USER_ROLES.ROLE, Role.USER.name())
                    .execute();

        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Username or email is already in use");
        }
    }
}
