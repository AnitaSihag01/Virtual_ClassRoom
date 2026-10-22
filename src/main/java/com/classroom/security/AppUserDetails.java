package com.classroom.security;

import com.classroom.entity.Role;
import com.classroom.entity.User;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Getter
public class AppUserDetails implements UserDetails {
    private final Long id;
    private final String name;
    private final String email;
    private final String password;
    private final Role role;
    private final boolean active;
    private final Instant lockUntil;
    @Setter private boolean mustChangePassword;

    public AppUserDetails(User u) {
        this.id = u.getId();
        this.name = u.getName();
        this.email = u.getEmail();
        this.password = u.getPassword();
        this.role = u.getRole();
        this.active = u.isActive();
        this.lockUntil = u.getLockUntil();
        this.mustChangePassword = u.isMustChangePassword();
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return lockUntil == null || lockUntil.isBefore(Instant.now()); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }   // ADM-13: deactivated users cannot log in
}
