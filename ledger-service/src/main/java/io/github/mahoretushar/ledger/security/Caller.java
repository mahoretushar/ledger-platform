package io.github.mahoretushar.ledger.security;

import io.github.mahoretushar.ledger.account.Account;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/** The authenticated user behind a request, reduced to what the domain needs. */
public record Caller(String username, boolean admin) {

    public static Caller from(Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_admin"));
        return new Caller(auth.getName(), admin);
    }

    /** Customers may only touch their own accounts; admins may touch any. */
    public void requireAccess(Account account) {
        if (!admin && !account.getOwnerUsername().equals(username)) {
            throw new AccessDeniedException("Account " + account.getId() + " does not belong to " + username);
        }
    }
}
