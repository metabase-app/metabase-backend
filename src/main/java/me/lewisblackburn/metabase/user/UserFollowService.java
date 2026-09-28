package me.lewisblackburn.metabase.user;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.event.ActionEvent;
import me.lewisblackburn.metabase.event.AuditLog;
import me.lewisblackburn.metabase.event.EventAction;
import me.lewisblackburn.metabase.event.EventActorType;
import me.lewisblackburn.metabase.event.EventAudience;
import me.lewisblackburn.metabase.event.EventOutcome;
import me.lewisblackburn.metabase.user.model.User;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class UserFollowService {
    private final UserRepository userRepository;
    private final UserLookup userLookup;
    private final AuditLog auditLog;
    private final JsonMapper mapper;

    @Transactional
    public User follow(Long followerId, Long followedId) {
        User target = validateUsers(followerId, followedId);
        if (userRepository.follow(followerId, followedId)) {
            recordFollowEvent(followerId, followedId, EventAction.USER_FOLLOWED);
        }
        return target;
    }

    @Transactional
    public User unfollow(Long followerId, Long followedId) {
        User target = validateUsers(followerId, followedId);
        if (userRepository.unfollow(followerId, followedId)) {
            recordFollowEvent(followerId, followedId, EventAction.USER_UNFOLLOWED);
        }
        return target;
    }

    private void recordFollowEvent(Long followerId, Long followedId, EventAction action) {
        UUID id = UUID.randomUUID();
        auditLog.record(ActionEvent.builder()
                .id(id)
                .operationId(id)
                .occurredAt(OffsetDateTime.now())
                .action(action)
                .actorType(EventActorType.USER)
                .actorUserId(followerId)
                .audience(EventAudience.USER_ACTIVITY)
                .outcome(EventOutcome.SUCCESS)
                .details(JSONB.valueOf(mapper.writeValueAsString(
                        Map.of("followedUserId", followedId))))
                .build());
    }

    private User validateUsers(Long followerId, Long followedId) {
        if (followerId != null && followerId.equals(followedId)) {
            throw new IllegalArgumentException("You cannot follow or unfollow yourself");
        }
        // Recheck the acting account too: it may have been deleted since the session started.
        userLookup.requireActiveUser(followerId);
        return userLookup.requireActiveUser(followedId);
    }
}
