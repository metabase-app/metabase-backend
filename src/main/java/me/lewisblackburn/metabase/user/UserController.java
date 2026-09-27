package me.lewisblackburn.metabase.user;

import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.user.model.User;

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

}
