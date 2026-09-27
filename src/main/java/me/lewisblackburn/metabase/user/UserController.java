package me.lewisblackburn.metabase.user;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.user.model.Role;
import me.lewisblackburn.metabase.user.model.User;
import graphql.relay.Connection;
import me.lewisblackburn.metabase.pagination.CursorPageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;

    @QueryMapping
    public User user(@Argument Long id) {
        return userRepository.find(id);
    }

    @QueryMapping
    public List<User> users() {
        return userRepository.findAll();
    }

    @BatchMapping(typeName = "User", field = "roles")
    public Map<User, List<Role>> roles(List<User> users) {
        Map<Long, List<Role>> rolesByUserId =
                userRepository.findRolesByUserIds(
                        users.stream().map(User::id).toList());

        return users.stream().collect(Collectors.toMap(
                user -> user,
                user -> rolesByUserId.getOrDefault(user.id(), List.of())));
    }

    @SchemaMapping(typeName = "User", field = "followers")
    public Connection<User> followers(User user, @Argument Integer first, @Argument String after) {
        return userRepository.findFollowers(user.id(), CursorPageRequest.builder()
                .first(first)
                .after(after)
                .build());
    }

    @SchemaMapping(typeName = "User", field = "following")
    public Connection<User> following(User user, @Argument Integer first, @Argument String after) {
        return userRepository.findFollowing(user.id(), CursorPageRequest.builder()
                .first(first)
                .after(after)
                .build());
    }

    @SchemaMapping(typeName = "User", field = "email")
    @PreAuthorize("@ownership.isOwner(#user.id())")
    public String email(User user) {
        return user.email();
    }
}
