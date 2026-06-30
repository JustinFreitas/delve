package dev.freitas.delve.web;

import dev.freitas.delve.config.WebProps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /api/**} on the (authenticated) request: enforces the Discord-id allowlist (403) and a
 * simple per-principal per-minute rate limit (429). Registered after Spring Security's authorization
 * filter, so it only sees requests that already passed authentication.
 */
@Component
@ConditionalOnProperty(name = "config.web.enabled", havingValue = "true")
public class ApiGuardFilter extends OncePerRequestFilter {

    private final WebProps webProps;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public ApiGuardFilter(WebProps webProps) {
        this.webProps = webProps;
    }

    private record Window(long minute, int count) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null ? auth.getName() : null;

        if (userId == null || !webProps.isAllowed(userId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not permitted to use this instance.");
            return;
        }
        if (!withinRateLimit(userId)) {
            response.setHeader("Retry-After", "60");
            response.sendError(429, "Rate limit exceeded; slow down.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean withinRateLimit(String userId) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window updated = windows.compute(userId, (k, w) ->
                (w == null || w.minute() != minute) ? new Window(minute, 1) : new Window(minute, w.count() + 1));
        return updated.count() <= webProps.getRequestsPerMinute();
    }
}
