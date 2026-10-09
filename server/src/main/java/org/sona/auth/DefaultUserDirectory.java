package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.UserDao;
import org.sona.model.tables.pojos.Users;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultUserDirectory implements UserDirectory {

    private final UserDao userDao;

    @Override
    public Users resolve(final SignedInUser user) {
        return userDao.find(user.userId()).orElseThrow();
    }
}
