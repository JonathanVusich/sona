package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.LocalUserDao;
import org.sona.model.tables.pojos.LocalUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultPasswordService implements PasswordService {

    private final LocalUserDao localUserDao;
    private final PasswordEncoder encoder;
    private final TokenService tokenService;

    @Override
    public PasswordChange change(final LocalUser user, final String currentPassword, final String newPassword) {
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            return PasswordChange.INCORRECT_CURRENT_PASSWORD;
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.equals(currentPassword)) {
            return PasswordChange.INVALID_NEW_PASSWORD;
        }
        localUserDao.updatePassword(user.userId(), encoder.encode(newPassword));
        tokenService.revokeAll(user);
        return PasswordChange.CHANGED;
    }
}
