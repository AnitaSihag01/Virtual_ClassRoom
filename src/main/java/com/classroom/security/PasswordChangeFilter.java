package com.classroom.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** TCH-1 / STD-1: until the temporary password is changed, only a few endpoints are usable. */
public class PasswordChangeFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AppUserDetails u && u.isMustChangePassword()) {
            String p = req.getRequestURI();
            boolean guarded = p.startsWith("/api/") || p.startsWith("/ws");
            boolean allowed = p.equals("/api/common/change-password") || p.equals("/api/common/me")
                    || p.equals("/api/auth/logout");
            if (guarded && !allowed) {
                res.setStatus(403);
                res.setContentType("application/json");
                res.getWriter().write("{\"message\":\"You must change your temporary password first\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
