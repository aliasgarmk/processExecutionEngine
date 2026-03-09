package com.unifize.processengine.model;

public record User(String userId, String displayName) {

    /** Sentinel user ID used for system-initiated actions such as escalations. */
    public static final String SYSTEM_USER_ID = "SYSTEM";

    /** Canonical system actor. Use wherever a User is required for automated operations. */
    public static final User SYSTEM = new User(SYSTEM_USER_ID, "System");
}
