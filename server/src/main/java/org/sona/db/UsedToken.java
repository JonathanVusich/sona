package org.sona.db;

import org.sona.model.tables.pojos.RefreshToken;

/**
 * A refresh token a refresh or logout has just revoked.
 *
 * @param providerRefreshToken the OIDC provider's refresh token behind the token's session, null for a local user's
 *                             session, which has none
 */
public record UsedToken(RefreshToken token, String providerRefreshToken) {}
