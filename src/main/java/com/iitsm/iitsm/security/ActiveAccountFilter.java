package com.iitsm.iitsm.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iitsm.iitsm.repository.AppUserRepository;

/**
 * Deactivating an account must take effect immediately, not only at the next login: on every
 * request of a signed-in user we check the account is still active, and otherwise end the session.
 */
@Component
public class ActiveAccountFilter extends OncePerRequestFilter {
    private final AppUserRepository users;

    public ActiveAccountFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && !isStillActive(auth.getName())) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login?deactivated");
            return;
        }
        chain.doFilter(request, response);
    }

    /** True when the account exists and is not deactivated. */
    public boolean isStillActive(String username) {
        return users.findByUsername(username).map(u -> u.isEnabled()).orElse(false);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/login") || path.startsWith("/register") || path.startsWith("/error");
    }
}
