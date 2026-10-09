package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.UserDao;
import org.sona.model.tables.pojos.Users;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultPasswordService implements PasswordService {

    private final UserDao userDao;
    private final PasswordEncoder encoder;
    private final TokenService tokenService;

    @Override
    public PasswordChange change(final Users user, final String currentPassword, final String newPassword) {
        // Users who only sign in through an OIDC provider have no password to change.
        if (user.passwordHash() == null || !encoder.matches(currentPassword, user.passwordHash())) {
            return PasswordChange.INCORRECT_CURRENT_PASSWORD;
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.equals(currentPassword)) {
            return PasswordChange.INVALID_NEW_PASSWORD;
        }
        userDao.updatePassword(user.userId(), encoder.encode(newPassword));
        tokenService.revokeAll(user);
        return PasswordChange.CHANGED;
    }
}
