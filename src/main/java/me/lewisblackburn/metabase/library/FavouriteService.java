package me.lewisblackburn.metabase.library;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.event.AuditContext;
import me.lewisblackburn.metabase.user.UserLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavouriteService {

    private final FavouriteRepository favouriteRepository;
    private final UserLookup userLookup;
    private final AuditContext auditContext;

    @Transactional
    public boolean favourite(Long userId, Long entityId) {
        userLookup.requireActiveUser(userId);
        auditContext.setUserAuditContext(userId);
        return favouriteRepository.favourite(userId, entityId);
    }

    @Transactional
    public boolean unfavourite(Long userId, Long entityId) {
        userLookup.requireActiveUser(userId);
        auditContext.setUserAuditContext(userId);
        return favouriteRepository.unfavourite(userId, entityId);
    }
}
