package dev.freitas.delve.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the optional web interface, bound under {@code config.web}. The web layer
 * (Spring Security + Discord OAuth login + REST API) only activates when {@link #isEnabled()} is true,
 * so the bot can run headless by default.
 */
@ConfigurationProperties("config.web")
public class WebProps {

    /** Master switch for the web interface. Off by default — the bot runs headless. */
    private boolean enabled = false;

    /**
     * Discord user ids permitted to use the web interface. Empty means any logged-in Discord user is
     * allowed; populate it to restrict a public instance to specific people.
     */
    private List<String> allowedUserIds = List.of();

    /** Discord user ids permitted to use administrator commands (like undo/redo). */
    private List<String> adminUserIds = List.of();

    /** Per-principal request budget for {@code /api/**}, per minute (basic abuse guard). */
    private int requestsPerMinute = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(List<String> allowedUserIds) {
        this.allowedUserIds = allowedUserIds == null ? List.of() : allowedUserIds;
    }

    public List<String> getAdminUserIds() {
        return adminUserIds;
    }

    public void setAdminUserIds(List<String> adminUserIds) {
        this.adminUserIds = adminUserIds == null ? List.of() : adminUserIds;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    /** Whether the given Discord user id may use the web interface under the current allowlist. */
    public boolean isAllowed(String discordUserId) {
        return allowedUserIds.isEmpty() || allowedUserIds.contains(discordUserId);
    }

    /** Whether the given Discord user id is an administrator. */
    public boolean isAdmin(String discordUserId) {
        return adminUserIds.contains(discordUserId);
    }
}
