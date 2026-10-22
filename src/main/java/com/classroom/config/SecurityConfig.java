package com.classroom.config;

import com.classroom.security.PasswordChangeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }          // SYS-3

    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository repo) throws Exception {
        http
            // Demo setting: CSRF disabled so a plain JS frontend can call the JSON API easily.
            .csrf(csrf -> csrf.disable())
            .securityContext(sc -> sc.securityContextRepository(repo))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/login.html", "/login", "/favicon.ico", "/css/**", "/js/**", "/images/**",
                                 "/error", "/error/**", "/api/auth/login").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")                // SYS-6
                .requestMatchers("/teacher/**", "/api/teacher/**").hasRole("TEACHER")
                .requestMatchers("/student/**", "/api/student/**").hasRole("STUDENT")
                .anyRequest().authenticated())                                                // incl. /ws/**, /api/common/**
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    if (req.getRequestURI().startsWith("/api")) res.sendError(401, "Login required");
                    else res.sendRedirect("/");
                })
                .accessDeniedHandler((req, res, ex) -> res.sendError(403, "Access Denied")))  // STD-13 (403 page)
            .logout(l -> l.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(200)))
            .addFilterAfter(new PasswordChangeFilter(), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
