package me.lewisblackburn.metabase.library;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.security.CurrentUser;
import me.lewisblackburn.metabase.security.GraphQlRateLimiter;
import me.lewisblackburn.metabase.security.UserPrincipal;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.ServletRequestAttributes;

@Controller
@RequiredArgsConstructor
public class FavouriteController {

    private final GraphQlRateLimiter rateLimiter;
    private final FavouriteService favouriteService;
    private final CurrentUser currentUser;

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean favourite(
        @Argument Long entityId,
        @AuthenticationPrincipal UserPrincipal principal,
        @ContextValue("http") ServletRequestAttributes http
    ) {
        rateLimiter.check("favourite", http.getRequest().getRemoteAddr());
        return favouriteService.favourite(currentUser.requireUserId(principal), entityId);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean unfavourite(
        @Argument Long entityId,
        @AuthenticationPrincipal UserPrincipal principal,
        @ContextValue("http") ServletRequestAttributes http
    ) {
        rateLimiter.check("unfavourite", http.getRequest().getRemoteAddr());
        return favouriteService.unfavourite(currentUser.requireUserId(principal), entityId);
    }
}
