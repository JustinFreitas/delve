package dev.freitas.delve.web;

import dev.freitas.delve.data.GameStateService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Holds the invoking player's action lock ({@link GameStateService#userLock}) for the duration of any
 * {@code /api/**} request — the web-side counterpart of {@code CommandManager}'s per-command lock, so
 * a web action and a Discord command for the same player can never interleave their
 * load → mutate → save cycles and silently lose one side's changes.
 */
@Component
@ConditionalOnProperty(name = "config.web.enabled", havingValue = "true")
public class UserActionLockFilter extends OncePerRequestFilter {

    private final GameStateService gameState;

    public UserActionLockFilter(GameStateService gameState) {
        this.gameState = gameState;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = principalUserId(request);
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }
        ReentrantLock lock = gameState.userLock(userId);
        lock.lock();
        try {
            chain.doFilter(request, response);
        } finally {
            lock.unlock();
        }
    }

    /** The authenticated Discord user id (the save key, same parse {@code GameController} does), or
        null for anything that isn't an authenticated {@code /api/**} request. */
    private static Long principalUserId(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return null;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
