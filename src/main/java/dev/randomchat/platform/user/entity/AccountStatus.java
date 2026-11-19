package dev.randomchat.platform.user.entity;

/**
 * Account-level status for a user.
 * <p>
 * ACTIVE    — normal operational state
 * SUSPENDED — temporarily restricted by moderation
 * DISABLED  — permanently deactivated
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED
}
