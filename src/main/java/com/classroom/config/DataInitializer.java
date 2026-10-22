package com.classroom.config;

import com.classroom.entity.Role;
import com.classroom.entity.User;
import com.classroom.repository.LiveSessionRepository;
import com.classroom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final LiveSessionRepository sessions;
    private final PasswordEncoder encoder;
    @Value("${app.admin.email}") private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;

    @Override
    public void run(String... args) {
        // ADM-1: default admin on first start
        if (!users.existsByRole(Role.ADMIN)) {
            User a = new User();
            a.setName("Administrator");
            a.setEmail(adminEmail.toLowerCase());
            a.setPassword(encoder.encode(adminPassword));
            a.setRole(Role.ADMIN);
            a.setMustChangePassword(false);
            users.save(a);
        }
        // Rooms live in memory; after a restart close any session that was left open.
        sessions.findByEndedAtIsNull().forEach(s -> { s.setEndedAt(Instant.now()); sessions.save(s); });
    }
}
