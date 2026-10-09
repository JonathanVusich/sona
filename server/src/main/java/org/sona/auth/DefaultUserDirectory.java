package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.LocalUserDao;
import org.sona.model.tables.pojos.LocalUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultUserDirectory implements UserDirectory {

    private final LocalUserDao localUserDao;

    @Override
    public LocalUser resolve(final SignedInUser user) {
        return localUserDao.find(user.userId()).orElseThrow();
    }
}
