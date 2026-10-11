package org.sona.auth;

import org.sona.model.enums.UserRole;

import java.util.UUID;

import static org.sona.utils.IDGenerator.uuidv5;

/**
 * A user as an OIDC provider describes them when they sign in.
 *
 * @param providerId the configured provider's ID
 * @param issuer     the provider's issuer
 * @param subject    the user's ID at the provider
 * @param role       the role their groups at the provider give them
 */
public record ProviderUser(String providerId, String issuer, String subject, UserRole role) {

    /**
     * @return the user's Sona ID, the same every time they sign in, so no lookup is needed to find it
     */
    public UUID userId() {
        return uuidv5(issuer + "\n" + subject);
    }
}
