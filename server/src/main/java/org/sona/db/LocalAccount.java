package org.sona.db;

import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.pojos.Users;

/**
 * A user who logs in with a password Sona keeps.
 */
public record LocalAccount(Users user, Password password) implements Account {}
