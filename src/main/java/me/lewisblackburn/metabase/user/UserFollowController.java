package me.lewisblackburn.metabase.user;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.security.UserPrincipal;
import me.lewisblackburn.metabase.security.CurrentUser;
import me.lewisblackburn.metabase.user.model.User;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class UserFollowController {
    private final UserFollowService followService;
    private final CurrentUser currentUser;

    // userId is the target account; the acting account always comes from the login principal.
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public User followUser(@Argument Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return followService.follow(currentUser.requireUserId(principal), userId);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public User unfollowUser(@Argument Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return followService.unfollow(currentUser.requireUserId(principal), userId);
    }

}
