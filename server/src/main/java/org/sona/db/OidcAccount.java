package org.sona.db;

import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.Users;

/**
 * A user who signs in through an OIDC provider, which manages their password.
 */
public record OidcAccount(Users user, OidcIdentity identity) implements Account {}
