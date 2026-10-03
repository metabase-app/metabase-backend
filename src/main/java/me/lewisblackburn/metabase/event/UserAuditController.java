package me.lewisblackburn.metabase.event;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.security.GraphQlRateLimiter;
import me.lewisblackburn.metabase.user.model.User;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.ServletRequestAttributes;

@Controller
@RequiredArgsConstructor
public class UserAuditController {
    private final UserAuditRepository auditRepository;
    private final GraphQlRateLimiter rateLimiter;

    @QueryMapping
    @PreAuthorize("@ownership.isOwner(#userId)")
    public List<UserAuditEvent> userLogs(@Argument Long userId,
            @Argument("offset") int offset, @Argument("limit") int limit,
            @ContextValue("http") ServletRequestAttributes http) {
        rateLimiter.check("userLogs", http.getRequest().getRemoteAddr());
        return auditRepository.findByActor(userId, offset, limit);
    }

    @SchemaMapping(typeName = "User", field = "logs")
    @PreAuthorize("@ownership.isOwner(#user.id())")
    public List<UserAuditEvent> logs(User user, @Argument("offset") int offset,
            @Argument("limit") int limit) {
        return auditRepository.findByActor(user.id(), offset, limit);
    }
}
