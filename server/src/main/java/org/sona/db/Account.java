package org.sona.db;

import org.sona.model.tables.pojos.Users;

/**
 * A user with how they sign in: a password Sona keeps, or an identity at an OIDC provider.
 */
public sealed interface Account permits LocalAccount, OidcAccount {

    Users user();
}
