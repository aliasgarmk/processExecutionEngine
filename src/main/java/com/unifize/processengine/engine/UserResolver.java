package com.unifize.processengine.engine;

import com.unifize.processengine.model.User;

import java.util.Collection;
import java.util.List;

/**
 * Boundary interface for resolving user identifiers to full User objects.
 * Production implementations delegate to an external user directory.
 * The InMemoryUserResolver is provided as a stub for tests and in-process use.
 */
public interface UserResolver {
    /** Resolves a single user by ID. Returns a fallback User(id, id) if unknown. */
    User resolve(String userId);

    /** Resolves multiple users by ID, preserving order. */
    List<User> resolveAll(Collection<String> userIds);

    /** Returns all users assigned to a given role, or an empty list if none. */
    List<User> resolveByRole(String role);
}
