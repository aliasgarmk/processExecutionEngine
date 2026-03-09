package com.unifize.processengine.engine;

import com.unifize.processengine.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Stub UserResolver backed by in-memory maps.
 * Register users with {@link #register(User)} before starting instances.
 * Unknown user IDs fall back to User(id, id) rather than throwing.
 */
public final class InMemoryUserResolver implements UserResolver {
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, List<String>> usersByRole = new ConcurrentHashMap<>();

    public void register(User user) {
        usersById.put(user.userId(), user);
    }

    public void assignRole(String userId, String role) {
        usersByRole.computeIfAbsent(role, ignored -> new CopyOnWriteArrayList<>()).add(userId);
    }

    @Override
    public User resolve(String userId) {
        return usersById.getOrDefault(userId, new User(userId, userId));
    }

    @Override
    public List<User> resolveAll(Collection<String> userIds) {
        return userIds.stream().map(this::resolve).collect(Collectors.toList());
    }

    @Override
    public List<User> resolveByRole(String role) {
        return resolveAll(usersByRole.getOrDefault(role, List.of()));
    }
}
