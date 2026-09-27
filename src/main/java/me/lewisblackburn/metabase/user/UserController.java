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
import org.springframework.data.domain.Window;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.Subrange;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@PreAuthorize("isAuthenticated()")
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
    public Window<User> followers(User user, Subrange<ScrollPosition> page) {
        return userRepository.findFollowers(user.id(), page);
    }

    @SchemaMapping(typeName = "User", field = "following")
    public Window<User> following(User user, Subrange<ScrollPosition> page) {
        return userRepository.findFollowing(user.id(), page);
    }

    @SchemaMapping(typeName = "User", field = "email")
    @PreAuthorize("@ownership.isOwner(#user.id())")
    public String email(User user) {
        return user.email();
    }
}
