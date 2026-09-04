package com.patientcase.security;

import com.patientcase.user.UserRepository;
import com.patientcase.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts authenticated requests for users who have mustChangePassword=true.
 * Redirects them to /profile/change-password until they successfully change it.
 *
 * Always allowed (even when mustChangePassword=true):
 *   /profile/change-password   (exact match, with or without query string)
 *   /logout
 *   /favicon.ico
 *   Anything starting with: /css/  /js/  /fonts/  /images/  /actuator/  /error
 *
 * Does NOT affect unauthenticated or anonymous requests.
 * Does NOT modify authorization rules for any endpoint.
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    static final String CHANGE_PASSWORD_PATH = "/profile/change-password";

    private final UserRepository userRepository;

    public MustChangePasswordFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Only act on fully authenticated (non-anonymous) sessions
        if (!isFullyAuthenticated(auth)) {
            chain.doFilter(request, response);
            return;
        }

        String path = resolvePath(request);

        if (isAllowed(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Check DB flag; if user not found, default to false (don't block)
        boolean mustChange = userRepository.findByUsername(auth.getName())
                .map(User::isMustChangePassword)
                .orElse(false);

        if (mustChange) {
            response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_PATH + "?forced");
            return;
        }

        chain.doFilter(request, response);
    }

    /** Extracts the path without query string, trying multiple sources for reliability. */
    static String resolvePath(HttpServletRequest request) {
        // requestURI minus context path is the most reliable source across environments
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        // Strip query string (defensive — requestURI should not include it, but guard anyway)
        int q = uri.indexOf('?');
        if (q >= 0) uri = uri.substring(0, q);
        return uri.isEmpty() ? "/" : uri;
    }

    static boolean isAllowed(String path) {
        if (path == null) return false;

        // Exact match for the change-password endpoint
        if (path.equals(CHANGE_PASSWORD_PATH)) return true;

        // Static assets and system paths — prefix match
        if (path.startsWith("/css/"))      return true;
        if (path.startsWith("/js/"))       return true;
        if (path.startsWith("/fonts/"))    return true;
        if (path.startsWith("/images/"))   return true;
        if (path.startsWith("/actuator/")) return true;
        if (path.startsWith("/error"))     return true;
        if (path.equals("/favicon.ico"))   return true;

        // Logout — exact and prefix (Spring Security maps /logout)
        if (path.equals("/logout") || path.startsWith("/logout/")) return true;

        return false;
    }

    private boolean isFullyAuthenticated(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
    }
}
