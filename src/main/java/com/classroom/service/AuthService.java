package com.classroom.service;

import com.classroom.entity.LoginHistory;
import com.classroom.entity.Role;
import com.classroom.entity.User;
import com.classroom.exception.ApiException;
import com.classroom.repository.LoginHistoryRepository;
import com.classroom.repository.UserRepository;
import com.classroom.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** SYS-2, SYS-4, ADM-11, ADM-13. (Not @Transactional on purpose: failures must still be recorded.) */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepo;
    private final UserRepository users;
    private final LoginHistoryRepository history;
    @Value("${app.login.max-attempts}") private int maxAttempts;
    @Value("${app.login.lock-minutes}") private long lockMinutes;

    public AppUserDetails login(String rawEmail, String password, Role role,
                                HttpServletRequest rq, HttpServletResponse rs) {
        String email = rawEmail.trim().toLowerCase();
        User user = users.findByEmail(email).orElse(null);
        Authentication auth;
        try {
            auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (LockedException e) {
            record(user, email, false);
            throw new ApiException(HttpStatus.LOCKED, "Too many failed attempts. Try again in a few minutes.");
        } catch (DisabledException e) {
            record(user, email, false);
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is deactivated");
        } catch (AuthenticationException e) {
            fail(user, email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        AppUserDetails d = (AppUserDetails) auth.getPrincipal();
        if (d.getRole() != role) {                       // SYS-2: chosen role must match the account
            fail(user, email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password for the selected role");
        }
        user.setFailedAttempts(0);
        user.setLockUntil(null);
        users.save(user);
        record(user, email, true);

        if (rq.getSession(false) != null) rq.changeSessionId();   // prevent session fixation
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, rq, rs);                      // SYS-5: idle timeout set in application.properties
        return d;
    }

    private void fail(User user, String email) {
        if (user != null) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= maxAttempts) {         // SYS-4
                user.setLockUntil(Instant.now().plus(lockMinutes, ChronoUnit.MINUTES));
                user.setFailedAttempts(0);
            }
            users.save(user);
        }
        record(user, email, false);
    }

    private void record(User user, String email, boolean success) {
        history.save(new LoginHistory(user, email, Instant.now(), success));
    }
}
